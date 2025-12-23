package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.SystemConfig;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理服务 - 支持动态配置修改
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        log.info("🔄 重新加载配置");
        // 配置存储在数据库中，无需特殊操作
        log.info("✅ 配置已从数据库加载");
    }

    /**
     * 获取所有数据库主机配置
     */
    public Map<String, Object> getDatabaseHosts() {
        log.info("📋 获取数据库主机配置");
        try {
            List<DatabaseHostConfig> hosts = databaseHostConfigRepository.findByIsActiveTrue();
            List<Map<String, Object>> hostList = hosts.stream()
                .map(this::databaseHostToMap)
                .collect(Collectors.toList());
            
            return Map.of("hosts", hostList);
        } catch (Exception e) {
            log.error("❌ 获取数据库主机配置失败: {}", e.getMessage());
            return Map.of("hosts", List.of(), "error", e.getMessage());
        }
    }

    /**
     * 添加或更新数据库主机配置
     */
    @Transactional
    public Map<String, Object> addOrUpdateDatabaseHost(String name, String host, String user, 
                                                        String password, List<String> databases) {
        log.info("💾 添加/更新数据库主机配置: {}", name);
        try {
            DatabaseHostConfig config = databaseHostConfigRepository.findByName(name)
                .orElse(new DatabaseHostConfig());
            
            config.setName(name);
            config.setHost(host);
            config.setUsername(user);
            config.setPassword(password); // TODO: 加密存储
            config.setDatabases(objectMapper.writeValueAsString(databases));
            config.setIsActive(true);
            
            databaseHostConfigRepository.save(config);
            
            return Map.of(
                "success", true,
                "message", "数据库主机配置已保存",
                "name", name
            );
        } catch (Exception e) {
            log.error("❌ 添加/更新数据库主机配置失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 删除数据库主机配置
     */
    @Transactional
    public Map<String, Object> deleteDatabaseHost(String name) {
        log.info("🗑️ 删除数据库主机配置: {}", name);
        try {
            databaseHostConfigRepository.deleteByName(name);
            
            return Map.of(
                "success", true,
                "message", "数据库主机配置已删除"
            );
        } catch (Exception e) {
            log.error("❌ 删除数据库主机配置失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 向主机添加数据库
     */
    @Transactional
    public Map<String, Object> addDatabaseToHost(String name, String database) {
        log.info("➕ 向主机 {} 添加数据库: {}", name, database);
        try {
            DatabaseHostConfig config = databaseHostConfigRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("数据库主机配置不存在"));
            
            List<String> databases = objectMapper.readValue(
                config.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            
            if (!databases.contains(database)) {
                databases.add(database);
                config.setDatabases(objectMapper.writeValueAsString(databases));
                databaseHostConfigRepository.save(config);
            }
            
            return Map.of(
                "success", true,
                "message", "数据库已添加"
            );
        } catch (Exception e) {
            log.error("❌ 添加数据库失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 从主机移除数据库
     */
    @Transactional
    public Map<String, Object> removeDatabaseFromHost(String name, String database) {
        log.info("➖ 从主机 {} 移除数据库: {}", name, database);
        try {
            DatabaseHostConfig config = databaseHostConfigRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("数据库主机配置不存在"));
            
            List<String> databases = objectMapper.readValue(
                config.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            
            databases.remove(database);
            config.setDatabases(objectMapper.writeValueAsString(databases));
            databaseHostConfigRepository.save(config);
            
            return Map.of(
                "success", true,
                "message", "数据库已移除"
            );
        } catch (Exception e) {
            log.error("❌ 移除数据库失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 测试新连接
     */
    public Map<String, Object> testNewConnection(String host, String user, String password) {
        log.info("🔌 测试新连接: {}", host);
        try {
            String url = "jdbc:mysql://" + host + "?useSSL=false&serverTimezone=UTC";
            List<String> databases = new ArrayList<>();
            
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
                
                while (rs.next()) {
                    String dbName = rs.getString(1);
                    if (!dbName.equals("information_schema") && 
                        !dbName.equals("mysql") && 
                        !dbName.equals("performance_schema") && 
                        !dbName.equals("sys")) {
                        databases.add(dbName);
                    }
                }
            }
            
            return Map.of(
                "success", true,
                "message", "连接成功",
                "databases", databases
            );
        } catch (Exception e) {
            log.error("❌ 测试连接失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 测试已保存的连接
     */
    public Map<String, Object> testSavedConnection(String name, String database) {
        log.info("🔌 测试已保存连接: {} - {}", name, database);
        try {
            DatabaseHostConfig config = databaseHostConfigRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("数据库主机配置不存在"));
            
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                config.getHost(), config.getPort(), database != null ? database : "");
            
            try (Connection conn = DriverManager.getConnection(url, config.getUsername(), config.getPassword())) {
                boolean valid = conn.isValid(5);
                return Map.of(
                    "success", valid,
                    "message", valid ? "连接成功" : "连接失败"
                );
            }
        } catch (Exception e) {
            log.error("❌ 测试已保存连接失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取火山引擎配置
     */
    public Map<String, Object> getVolcanoEngineConfig() {
        log.info("⚙️ 获取火山引擎配置");
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("api_key", maskApiKey(getConfigValue("volcano_engine.api_key", "")));
            config.put("model", getConfigValue("volcano_engine.model", "deepseek-v3-1-250821"));
            config.put("timeout", Long.parseLong(getConfigValue("volcano_engine.timeout", "1800000")));
            config.put("base_url", getConfigValue("volcano_engine.base_url", "https://ark.cn-beijing.volces.com/api/v3"));
            return config;
        } catch (Exception e) {
            log.error("❌ 获取火山引擎配置失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 更新火山引擎配置
     */
    @Transactional
    public Map<String, Object> updateVolcanoEngineConfig(String apiKey, String model, Integer timeout) {
        log.info("💾 更新火山引擎配置");
        try {
            if (apiKey != null) {
                setConfigValue("volcano_engine.api_key", apiKey);
            }
            if (model != null) {
                setConfigValue("volcano_engine.model", model);
            }
            if (timeout != null) {
                setConfigValue("volcano_engine.timeout", String.valueOf(timeout));
            }
            
            return Map.of(
                "success", true,
                "message", "火山引擎配置已更新"
            );
        } catch (Exception e) {
            log.error("❌ 更新火山引擎配置失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取Ollama配置
     */
    public Map<String, Object> getOllamaConfig() {
        log.info("⚙️ 获取Ollama配置");
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", Boolean.parseBoolean(getConfigValue("ollama.enabled", "false")));
            config.put("host", getConfigValue("ollama.host", "http://localhost:11435"));
            config.put("model", getConfigValue("ollama.model", "deepseek-r1:14b"));
            config.put("timeout", Long.parseLong(getConfigValue("ollama.timeout", "1800000")));
            return config;
        } catch (Exception e) {
            log.error("❌ 获取Ollama配置失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 更新Ollama配置
     */
    @Transactional
    public Map<String, Object> updateOllamaConfig(Boolean enabled, String host, String model, Integer timeout) {
        log.info("💾 更新Ollama配置");
        try {
            if (enabled != null) {
                setConfigValue("ollama.enabled", String.valueOf(enabled));
            }
            if (host != null) {
                setConfigValue("ollama.host", host);
            }
            if (model != null) {
                setConfigValue("ollama.model", model);
            }
            if (timeout != null) {
                setConfigValue("ollama.timeout", String.valueOf(timeout));
            }
            
            return Map.of(
                "success", true,
                "message", "Ollama配置已更新"
            );
        } catch (Exception e) {
            log.error("❌ 更新Ollama配置失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 测试Ollama连接
     */
    public Map<String, Object> testOllamaConnection(String host, String model) {
        log.info("🔌 测试Ollama连接");
        try {
            if (host == null) {
                host = getConfigValue("ollama.host", "http://localhost:11435");
            }
            if (model == null) {
                model = getConfigValue("ollama.model", "deepseek-r1:14b");
            }
            
            // TODO: 实现Ollama连接测试
            
            return Map.of(
                "success", true,
                "message", "Ollama连接测试功能待实现"
            );
        } catch (Exception e) {
            log.error("❌ 测试Ollama连接失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取模型提供商
     */
    public Map<String, Object> getModelProvider() {
        log.info("⚙️ 获取模型提供商");
        try {
            String provider = getConfigValue("model.provider", "volcano_engine");
            return Map.of("provider", provider);
        } catch (Exception e) {
            log.error("❌ 获取模型提供商失败: {}", e.getMessage());
            return Map.of("provider", "volcano_engine", "error", e.getMessage());
        }
    }

    /**
     * 设置模型提供商
     */
    @Transactional
    public Map<String, Object> setModelProvider(String provider) {
        log.info("💾 设置模型提供商: {}", provider);
        try {
            setConfigValue("model.provider", provider);
            
            return Map.of(
                "success", true,
                "message", "模型提供商已设置"
            );
        } catch (Exception e) {
            log.error("❌ 设置模型提供商失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取系统设置
     */
    public Map<String, Object> getSettings() {
        log.info("⚙️ 获取系统设置");
        try {
            Map<String, Object> settings = new HashMap<>();
            settings.put("refresh_db", Boolean.parseBoolean(getConfigValue("settings.refresh_db", "true")));
            settings.put("refresh_schema", Boolean.parseBoolean(getConfigValue("settings.refresh_schema", "true")));
            settings.put("save_results", Boolean.parseBoolean(getConfigValue("settings.save_results", "false")));
            settings.put("max_retries", Integer.parseInt(getConfigValue("settings.max_retries", "5")));
            settings.put("cache_max_size", Integer.parseInt(getConfigValue("settings.cache_max_size", "1000")));
            settings.put("cache_expire_minutes", Integer.parseInt(getConfigValue("settings.cache_expire_minutes", "60")));
            return settings;
        } catch (Exception e) {
            log.error("❌ 获取系统设置失败: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 更新系统设置
     */
    @Transactional
    public Map<String, Object> updateSettings(Map<String, String> settings) {
        log.info("💾 更新系统设置");
        try {
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                setConfigValue("settings." + entry.getKey(), entry.getValue());
            }
            
            return Map.of(
                "success", true,
                "message", "系统设置已更新"
            );
        } catch (Exception e) {
            log.error("❌ 更新系统设置失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取配置值
     */
    private String getConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
            .map(SystemConfig::getConfigValue)
            .orElse(defaultValue);
    }

    /**
     * 设置配置值
     */
    private void setConfigValue(String key, String value) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
            .orElse(new SystemConfig());
        
        config.setConfigKey(key);
        config.setConfigValue(value);
        
        // 自动设置分类
        if (key.startsWith("volcano_engine.")) {
            config.setCategory("volcano_engine");
        } else if (key.startsWith("ollama.")) {
            config.setCategory("ollama");
        } else if (key.startsWith("model.")) {
            config.setCategory("model");
        } else if (key.startsWith("settings.")) {
            config.setCategory("settings");
        }
        
        systemConfigRepository.save(config);
    }

    /**
     * 隐藏API密钥
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 4) {
            return "***";
        }
        return "***" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 将DatabaseHostConfig转换为Map
     */
    private Map<String, Object> databaseHostToMap(DatabaseHostConfig host) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("name", host.getName());
            map.put("host", host.getHost());
            map.put("port", host.getPort());
            map.put("username", host.getUsername());
            map.put("password", "***"); // 隐藏密码
            
            List<String> databases = objectMapper.readValue(
                host.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            map.put("databases", databases);
            
            Map<String, Object> pool = new HashMap<>();
            pool.put("maximum_pool_size", host.getPoolMaxSize());
            pool.put("minimum_idle", host.getPoolMinIdle());
            pool.put("connection_timeout", host.getPoolTimeout());
            map.put("pool", pool);
            
            return map;
        } catch (Exception e) {
            log.error("转换DatabaseHostConfig失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
