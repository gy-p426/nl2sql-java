package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.SystemConfig;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 配置初始化服务
 * 在应用启动时将application.yml的配置同步到数据库
 */
@Slf4j
@Service
@Order(1) // 优先级最高，最先执行
@RequiredArgsConstructor
public class ConfigInitializationService implements CommandLineRunner {

    private final NL2SQLProperties properties;
    private final SystemConfigRepository systemConfigRepository;
    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("🔧 开始初始化配置...");
        
        // 同步数据库主机配置
        syncDatabaseHosts();
        
        // 同步系统配置
        syncSystemConfigs();
        
        log.info("✅ 配置初始化完成");
    }

    /**
     * 同步数据库主机配置
     */
    private void syncDatabaseHosts() {
        if (properties.getDatabases() == null || properties.getDatabases().isEmpty()) {
            log.warn("⚠️ application.yml中没有配置数据库主机");
            return;
        }
        
        for (NL2SQLProperties.DatabaseHost host : properties.getDatabases()) {
            try {
                DatabaseHostConfig config = databaseHostConfigRepository.findByName(host.getName())
                    .orElse(new DatabaseHostConfig());
                
                // 只在数据库中不存在时才同步，避免覆盖用户修改
                if (config.getId() == null) {
                    config.setName(host.getName());
                    config.setHost(host.getHost());
                    config.setPort(host.getPort());
                    config.setUsername(host.getUsername());
                    config.setPassword(host.getPassword());
                    config.setDatabases(objectMapper.writeValueAsString(host.getDatabases()));
                    config.setDbType(host.getDbType());
                    
                    if (host.getPool() != null) {
                        config.setPoolMaxSize(host.getPool().getMaximumPoolSize());
                        config.setPoolMinIdle(host.getPool().getMinimumIdle());
                        config.setPoolTimeout(host.getPool().getConnectionTimeout());
                    }
                    
                    config.setIsActive(true);
                    databaseHostConfigRepository.save(config);
                    
                    log.info("✅ 同步数据库主机配置: {}", host.getName());
                }
            } catch (Exception e) {
                log.error("❌ 同步数据库主机配置失败: {} - {}", host.getName(), e.getMessage());
            }
        }
    }

    /**
     * 同步系统配置
     */
    private void syncSystemConfigs() {
        // 火山引擎配置
        if (properties.getVolcanoEngine() != null) {
            syncConfig("volcano_engine.api_key", properties.getVolcanoEngine().getApiKey(), "volcano_engine");
            syncConfig("volcano_engine.model", properties.getVolcanoEngine().getModel(), "volcano_engine");
            syncConfig("volcano_engine.timeout", String.valueOf(properties.getVolcanoEngine().getTimeout()), "volcano_engine");
            syncConfig("volcano_engine.base_url", properties.getVolcanoEngine().getBaseUrl(), "volcano_engine");
        }
        
        // Ollama配置
        if (properties.getOllama() != null) {
            syncConfig("ollama.enabled", String.valueOf(properties.getOllama().getEnabled()), "ollama");
            syncConfig("ollama.host", properties.getOllama().getHost(), "ollama");
            syncConfig("ollama.model", properties.getOllama().getModel(), "ollama");
            syncConfig("ollama.timeout", String.valueOf(properties.getOllama().getTimeout()), "ollama");
        }
        
        // 模型提供商
        if (properties.getModel() != null) {
            syncConfig("model.provider", properties.getModel().getProvider(), "model");
        }
        
        // 系统设置
        if (properties.getSettings() != null) {
            syncConfig("settings.refresh_db", String.valueOf(properties.getSettings().getRefreshDb()), "settings");
            syncConfig("settings.refresh_schema", String.valueOf(properties.getSettings().getRefreshSchema()), "settings");
            syncConfig("settings.save_results", String.valueOf(properties.getSettings().getSaveResults()), "settings");
            syncConfig("settings.max_retries", String.valueOf(properties.getSettings().getMaxRetries()), "settings");
            syncConfig("settings.cache_max_size", String.valueOf(properties.getSettings().getCacheMaxSize()), "settings");
            syncConfig("settings.cache_expire_minutes", String.valueOf(properties.getSettings().getCacheExpireMinutes()), "settings");
        }
    }

    /**
     * 同步单个配置项
     */
    private void syncConfig(String key, String value, String category) {
        if (value == null) {
            return;
        }
        
        SystemConfig config = systemConfigRepository.findByConfigKey(key)
            .orElse(new SystemConfig());
        
        // 只在数据库中不存在时才同步
        if (config.getId() == null) {
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setCategory(category);
            systemConfigRepository.save(config);
            log.debug("✅ 同步配置: {} = {}", key, key.contains("key") || key.contains("password") ? "***" : value);
        }
    }
}
