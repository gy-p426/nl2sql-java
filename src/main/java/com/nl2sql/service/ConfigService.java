package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.DatabaseOverview;
import com.nl2sql.model.entity.SystemConfig;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.DatabaseOverviewRepository;
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
    private final DatabaseOverviewRepository databaseOverviewRepository;
    private final ObjectMapper objectMapper;
    private final SchemaService schemaService;
    private final DatabasePoolService databasePoolService;
    private final DatabaseAccessScopeService databaseAccessScopeService;

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
        return getDatabaseHosts(null);
    }

    public Map<String, Object> getDatabaseHosts(Integer userId) {
        log.info("📋 获取数据库主机配置");
        try {
            List<DatabaseHostConfig> hosts = userId != null
                ? databaseHostConfigRepository.findByOwnerUserIdAndIsActiveTrue(userId)
                : databaseHostConfigRepository.findByIsActiveTrue();
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
        return addOrUpdateDatabaseHost(null, name, host, user, password, databases, "mysql", "ORCL", null);
    }
    
    @Transactional
    public Map<String, Object> addOrUpdateDatabaseHost(String name, String host, String user, 
                                                        String password, List<String> databases, String dbType) {
        return addOrUpdateDatabaseHost(null, name, host, user, password, databases, dbType, "ORCL", null);
    }

    @Transactional
    public Map<String, Object> addOrUpdateDatabaseHost(Integer userId, String name, String host, String user,
                                                        String password, List<String> databases, String dbType, String sid, String pdbName) {
        log.info("💾 添加/更新数据库主机配置: {}", name);
        try {
            DatabaseHostConfig config = userId != null
                ? databaseHostConfigRepository.findByOwnerUserIdAndName(userId, name).orElse(new DatabaseHostConfig())
                : databaseHostConfigRepository.findByName(name).orElse(new DatabaseHostConfig());
            
            // 获取旧的数据库列表用于比较
            List<String> oldDatabases = new ArrayList<>();
            if (config.getId() != null && config.getDatabases() != null) {
                try {
                    oldDatabases = objectMapper.readValue(
                        config.getDatabases(), 
                        new TypeReference<List<String>>() {}
                    );
                } catch (Exception e) {
                    log.warn("解析旧数据库列表失败: {}", e.getMessage());
                }
            }
            
            config.setName(name);
            if (userId != null) {
                config.setOwnerUserId(userId);
            }
            
            // 解析主机和端口
            if (host != null && host.contains(":")) {
                String[] parts = host.split(":");
                config.setHost(parts[0]);
                try {
                    config.setPort(Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    // 端口解析失败，使用默认值
                    config.setPort("oracle".equals(dbType) ? 1521 : 3306);
                }
            } else {
                config.setHost(host);
                config.setPort("oracle".equals(dbType) ? 1521 : 3306);
            }
            
            config.setUsername(user);
            config.setPassword(password); // TODO: 加密存储
            config.setDatabases(objectMapper.writeValueAsString(databases));
            config.setDbType(dbType != null ? dbType : "mysql");
            config.setSid(sid != null ? sid : "ORCL");
            config.setPdbName(pdbName);
            config.setIsActive(true);
            
            databaseHostConfigRepository.save(config);
            
            // 🔄 同步更新 database_overview 和 database_schema 表
            syncDatabaseOverviews();
            
            // 🆕 自动为新添加的数据库生成 Schema 和概览
            autoGenerateForNewDatabases(oldDatabases, databases);
            
            // 🔄 刷新数据库连接池
            databasePoolService.refreshPools();
            
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
        return deleteDatabaseHost(null, name);
    }

    @Transactional
    public Map<String, Object> deleteDatabaseHost(Integer userId, String name) {
        log.info("🗑️ 删除数据库主机配置: {}", name);
        try {
            if (userId != null) {
                databaseAccessScopeService.assertOwnsHostConfig(userId, name);
                databaseHostConfigRepository.deleteByOwnerUserIdAndName(userId, name);
            } else {
                databaseHostConfigRepository.deleteByName(name);
            }
            
            // 🔄 同步更新 database_overview 表
            syncDatabaseOverviews();
            
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
        return addDatabaseToHost(null, name, database);
    }

    @Transactional
    public Map<String, Object> addDatabaseToHost(Integer userId, String name, String database) {
        log.info("➕ 向主机 {} 添加数据库: {}", name, database);
        try {
            DatabaseHostConfig config = getOwnedHostConfig(userId, name);
            
            List<String> databases = objectMapper.readValue(
                config.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            
            if (!databases.contains(database)) {
                databases.add(database);
                config.setDatabases(objectMapper.writeValueAsString(databases));
                databaseHostConfigRepository.save(config);
                
                // 🔄 同步更新 database_overview 表
                syncDatabaseOverviews();
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
        return removeDatabaseFromHost(null, name, database);
    }

    @Transactional
    public Map<String, Object> removeDatabaseFromHost(Integer userId, String name, String database) {
        log.info("➖ 从主机 {} 移除数据库: {}", name, database);
        try {
            DatabaseHostConfig config = getOwnedHostConfig(userId, name);
            
            List<String> databases = objectMapper.readValue(
                config.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            
            databases.remove(database);
            config.setDatabases(objectMapper.writeValueAsString(databases));
            databaseHostConfigRepository.save(config);
            
            // 🔄 同步更新 database_overview 表
            syncDatabaseOverviews();
            
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
     * 🔄 同步数据库概览表
     * 根据 database_host_config 表的配置，更新 database_overview 表的激活状态
     */
    @Transactional
    public void syncDatabaseOverviews() {
        log.info("🔄 开始同步数据库概览表");
        try {
            // 1. 获取所有激活的数据库主机配置
            List<DatabaseHostConfig> activeHosts = databaseHostConfigRepository.findByIsActiveTrue();

            // 2. 收集所有配置中的“用户+主机+数据库”作用域键
            Set<String> configuredScopeKeys = new HashSet<>();
            for (DatabaseHostConfig host : activeHosts) {
                try {
                    List<String> databases = objectMapper.readValue(
                        host.getDatabases(),
                        new TypeReference<List<String>>() {}
                    );

                    for (String dbName : databases) {
                        String scopeKey = buildOverviewScopeKey(host.getOwnerUserId(), host.getId(), dbName);
                        configuredScopeKeys.add(scopeKey);

                        DatabaseOverview overview = databaseOverviewRepository
                            .findByOwnerUserIdAndHostConfigIdAndDatabaseName(host.getOwnerUserId(), host.getId(), dbName)
                            .orElse(new DatabaseOverview());

                        overview.setOwnerUserId(host.getOwnerUserId());
                        overview.setHostConfigId(host.getId());
                        overview.setDatabaseName(dbName);
                        if (overview.getDescription() == null || overview.getDescription().isBlank()) {
                            overview.setDescription("自动创建的数据库概览");
                        }
                        if (overview.getTableSummary() == null) {
                            overview.setTableSummary("");
                        }
                        overview.setIsActive(true);
                        databaseOverviewRepository.save(overview);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ 解析主机 {} 的数据库配置失败: {}", host.getName(), e.getMessage());
                }
            }

            // 3. 将不在当前激活配置中的作用域记录标记为 inactive
            List<DatabaseOverview> allOverviews = databaseOverviewRepository.findAll();
            for (DatabaseOverview overview : allOverviews) {
                if (overview.getOwnerUserId() == null || overview.getHostConfigId() == null) {
                    continue;
                }

                String scopeKey = buildOverviewScopeKey(
                    overview.getOwnerUserId(), overview.getHostConfigId(), overview.getDatabaseName());
                boolean shouldBeActive = configuredScopeKeys.contains(scopeKey);
                if (!Objects.equals(overview.getIsActive(), shouldBeActive)) {
                    overview.setIsActive(shouldBeActive);
                    databaseOverviewRepository.save(overview);
                }
            }
            
            log.info("✅ 数据库概览表同步完成");
            
        } catch (Exception e) {
            log.error("❌ 同步数据库概览表失败: {}", e.getMessage(), e);
        }
    }

    private String buildOverviewScopeKey(Integer ownerUserId, Integer hostConfigId, String dbName) {
        return String.format("%s|%s|%s", ownerUserId, hostConfigId, dbName);
    }

    /**
     * 🆕 自动为新添加的数据库生成 Schema 和概览
     */
    private void autoGenerateForNewDatabases(List<String> oldDatabases, List<String> newDatabases) {
        try {
            // 找出新添加的数据库
            Set<String> oldSet = new HashSet<>(oldDatabases);
            List<String> addedDatabases = newDatabases.stream()
                .filter(db -> !oldSet.contains(db))
                .collect(Collectors.toList());
            
            if (!addedDatabases.isEmpty()) {
                log.info("🆕 检测到新添加的数据库: {}", addedDatabases);
                
                for (String dbName : addedDatabases) {
                    try {
                        // 自动生成 Schema
                        log.info("📝 为数据库 {} 生成 Schema", dbName);
                        schemaService.exportDatabaseSchema(dbName, true);
                        
                        log.info("✅ 数据库 {} 的 Schema 和概览已自动生成", dbName);
                    } catch (Exception e) {
                        log.error("❌ 为数据库 {} 生成 Schema 失败: {}", dbName, e.getMessage());
                    }
                }
            }
            
            // 找出删除的数据库并清理相关数据
            Set<String> newSet = new HashSet<>(newDatabases);
            List<String> removedDatabases = oldDatabases.stream()
                .filter(db -> !newSet.contains(db))
                .collect(Collectors.toList());
            
            if (!removedDatabases.isEmpty()) {
                log.info("🗑️ 检测到删除的数据库: {}", removedDatabases);
                // 删除操作在 syncDatabaseOverviews 中通过设置 isActive=false 来处理
            }
            
        } catch (Exception e) {
            log.error("❌ 自动生成新数据库 Schema 失败: {}", e.getMessage());
        }
    }

    /**
     * 测试新连接
     */
    public Map<String, Object> testNewConnection(String host, String user, String password) {
        return testNewConnection(host, user, password, "mysql");
    }
    
    public Map<String, Object> testNewConnection(String host, String user, String password, String dbType) {
        return testNewConnection(host, user, password, dbType, "ORCL", null);
    }
    
    public Map<String, Object> testNewConnection(String host, String user, String password, String dbType, String sid) {
        return testNewConnection(host, user, password, dbType, sid, null);
    }
    
    public Map<String, Object> testNewConnection(String host, String user, String password, String dbType, String sid, String pdbName) {
        log.info("🔌 测试新连接: {} ({} - {} - SID: {} - PDB: {})", host, dbType, user, sid, pdbName);
        try {
            // 处理 localhost 解析问题
            if (host != null && (host.equals("localhost") || host.startsWith("localhost:"))) {
                if (host.startsWith("localhost:")) {
                    host = host.replace("localhost:", "127.0.0.1:");
                } else {
                    host = "127.0.0.1";
                }
                log.info("🔄 将 localhost 转换为 127.0.0.1 以避免 DNS 解析问题: {}", host);
            }
            
            // 构建连接 URL - 根据数据库类型
            String url;
            if ("oracle".equals(dbType)) {
                // Oracle 连接 URL
                if (pdbName != null && !pdbName.isEmpty()) {
                    // 使用PDB服务名连接方式
                    if (host.contains(":")) {
                        url = "jdbc:oracle:thin:@//" + host + "/" + pdbName;
                    } else {
                        url = "jdbc:oracle:thin:@//" + host + ":1521/" + pdbName;
                    }
                } else {
                    // 使用传统SID连接方式
                    if (host.contains(":")) {
                        url = "jdbc:oracle:thin:@" + host + ":" + sid;
                    } else {
                        url = "jdbc:oracle:thin:@" + host + ":1521:" + sid;
                    }
                }
            } else {
                // MySQL 连接 URL
                if (host.contains(":")) {
                    url = "jdbc:mysql://" + host + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
                } else {
                    url = "jdbc:mysql://" + host + ":3306?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
                }
            }
            
            List<String> databases = new ArrayList<>();
            
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()) {
                
                ResultSet rs;
                if ("oracle".equals(dbType)) {
                    // Oracle 查询所有用户，排除部分系统用户（保留SYSTEM）
                    rs = stmt.executeQuery("SELECT USERNAME FROM ALL_USERS WHERE USERNAME NOT IN ('SYS', 'SYSMAN', 'DBSNMP', 'OUTLN', 'VECSYS', 'DBSFWUSER', 'AUDSYS', 'APPQOSSYS', 'GSMADMIN_INTERNAL', 'XDB', 'WMSYS', 'OJVMSYS', 'CTXSYS', 'OLAPSYS', 'MDSYS', 'LBACSYS', 'DVSYS')");
                    while (rs.next()) {
                        String dbName = rs.getString("USERNAME");
                        databases.add(dbName);
                    }
                } else {
                    // MySQL 查询数据库
                    rs = stmt.executeQuery("SHOW DATABASES");
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
            }
            
            return Map.of(
                "success", true,
                "message", "连接成功",
                "databases", databases
            );
        } catch (Exception e) {
            log.error("❌ 测试连接失败: {}", e.getMessage());
            
            // 提供更详细的错误信息
            String errorMessage = e.getMessage();
            if (e.getMessage().contains("UnknownHostException")) {
                errorMessage = "无法解析主机名，请检查数据库服务是否运行或尝试使用 IP 地址";
            } else if (e.getMessage().contains("Access denied") || e.getMessage().contains("invalid username/password")) {
                errorMessage = "用户名或密码错误";
            } else if (e.getMessage().contains("Connection refused")) {
                errorMessage = "连接被拒绝，请检查数据库服务是否运行在指定端口";
            }
            
            return Map.of("success", false, "error", errorMessage);
        }
    }

    /**
     * 测试已保存的连接
     */
    public Map<String, Object> testSavedConnection(String name, String database) {
        return testSavedConnection(null, name, database);
    }

    public Map<String, Object> testSavedConnection(Integer userId, String name, String database) {
        log.info("🔌 测试已保存连接: {} - {}", name, database);
        try {
            DatabaseHostConfig config = getOwnedHostConfig(userId, name);
            
            // 根据数据库类型构建连接 URL
            String url;
            String dbType = config.getDbType() != null ? config.getDbType() : "mysql";
            
            if ("oracle".equals(dbType)) {
                // Oracle 连接 URL
                if (config.getPdbName() != null && !config.getPdbName().isEmpty()) {
                    // 使用PDB服务名连接方式
                    url = String.format("jdbc:oracle:thin:@//%s:%d/%s",
                        config.getHost(), config.getPort(), config.getPdbName());
                } else {
                    // 注意：对于 Oracle，database 参数实际上是 SID
                    String sid = database != null ? database : "ORCL";
                    url = String.format("jdbc:oracle:thin:@%s:%d:%s",
                        config.getHost(), config.getPort(), sid);
                }
            } else {
                // MySQL 连接 URL
                url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                    config.getHost(), config.getPort(), database != null ? database : "");
            }
            
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
            config.put("model", getConfigValue("volcano_engine.model", "deepseek-v3-2-251201"));
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
            map.put("owner_user_id", host.getOwnerUserId());
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
            
            // 添加数据库类型、SID和PDB名称
            map.put("dbType", host.getDbType() != null ? host.getDbType() : "mysql");
            map.put("sid", host.getSid() != null ? host.getSid() : "ORCL");
            map.put("pdbName", host.getPdbName());
            
            return map;
        } catch (Exception e) {
            log.error("转换DatabaseHostConfig失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private DatabaseHostConfig getOwnedHostConfig(Integer userId, String name) {
        if (userId == null) {
            return databaseHostConfigRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("数据库主机配置不存在"));
        }

        databaseAccessScopeService.assertOwnsHostConfig(userId, name);
        return databaseHostConfigRepository.findByOwnerUserIdAndName(userId, name)
            .orElseThrow(() -> new RuntimeException("数据库主机配置不存在或无权限访问"));
    }
}
