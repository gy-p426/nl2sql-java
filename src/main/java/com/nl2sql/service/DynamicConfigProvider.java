package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.SystemConfig;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 动态配置提供者
 * 为其他服务提供实时配置读取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicConfigProvider {

    private final SystemConfigRepository systemConfigRepository;
    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * 获取配置值（字符串）
     */
    @Cacheable(value = "configCache", key = "#key")
    public String getString(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
            .map(SystemConfig::getConfigValue)
            .orElse(defaultValue);
    }

    /**
     * 获取配置值（整数）
     */
    public Integer getInt(String key, Integer defaultValue) {
        try {
            String value = getString(key, null);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("配置值转换失败: {} = {}", key, getString(key, null));
            return defaultValue;
        }
    }

    /**
     * 获取配置值（长整数）
     */
    public Long getLong(String key, Long defaultValue) {
        try {
            String value = getString(key, null);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("配置值转换失败: {} = {}", key, getString(key, null));
            return defaultValue;
        }
    }

    /**
     * 获取配置值（布尔）
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        String value = getString(key, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * 获取火山引擎API密钥
     */
    public String getVolcanoEngineApiKey() {
        return getString("volcano_engine.api_key", "");
    }

    /**
     * 获取火山引擎模型
     */
    public String getVolcanoEngineModel() {
        return getString("volcano_engine.model", "deepseek-v3-2-251201");
    }

    /**
     * 获取火山引擎超时时间
     */
    public Long getVolcanoEngineTimeout() {
        return getLong("volcano_engine.timeout", 1800000L);
    }

    /**
     * 获取火山引擎Base URL
     */
    public String getVolcanoEngineBaseUrl() {
        return getString("volcano_engine.base_url", "https://ark.cn-beijing.volces.com/api/v3");
    }

    /**
     * 获取Ollama是否启用
     */
    public Boolean getOllamaEnabled() {
        return getBoolean("ollama.enabled", false);
    }

    /**
     * 获取Ollama主机地址
     */
    public String getOllamaHost() {
        return getString("ollama.host", "http://localhost:11435");
    }

    /**
     * 获取Ollama模型
     */
    public String getOllamaModel() {
        return getString("ollama.model", "deepseek-r1:14b");
    }

    /**
     * 获取Ollama超时时间
     */
    public Long getOllamaTimeout() {
        return getLong("ollama.timeout", 1800000L);
    }

    /**
     * 获取模型提供商
     */
    public String getModelProvider() {
        return getString("model.provider", "volcano_engine");
    }

    /**
     * 获取是否刷新数据库
     */
    public Boolean getRefreshDb() {
        return getBoolean("settings.refresh_db", true);
    }

    /**
     * 获取是否刷新Schema
     */
    public Boolean getRefreshSchema() {
        return getBoolean("settings.refresh_schema", true);
    }

    /**
     * 获取最大重试次数
     */
    public Integer getMaxRetries() {
        return getInt("settings.max_retries", 5);
    }

    /**
     * 获取缓存最大大小
     */
    public Integer getCacheMaxSize() {
        return getInt("settings.cache_max_size", 1000);
    }

    /**
     * 获取缓存过期时间（分钟）
     */
    public Integer getCacheExpireMinutes() {
        return getInt("settings.cache_expire_minutes", 60);
    }

    /**
     * 获取所有激活的数据库主机配置
     */
    public List<DatabaseHostConfig> getActiveDatabaseHosts() {
        return databaseHostConfigRepository.findByIsActiveTrue();
    }

    /**
     * 根据名称获取数据库主机配置
     */
    public Optional<DatabaseHostConfig> getDatabaseHost(String name) {
        return databaseHostConfigRepository.findByName(name);
    }

    /**
     * 获取数据库主机的数据库列表
     */
    public List<String> getDatabaseList(String hostName) {
        try {
            return databaseHostConfigRepository.findByName(hostName)
                .map(config -> {
                    try {
                        return objectMapper.readValue(
                            config.getDatabases(),
                            new TypeReference<List<String>>() {}
                        );
                    } catch (Exception e) {
                        log.error("解析数据库列表失败: {}", e.getMessage());
                        return List.<String>of();
                    }
                })
                .orElse(List.of());
        } catch (Exception e) {
            log.error("获取数据库列表失败: {}", e.getMessage());
            return List.of();
        }
    }
}
