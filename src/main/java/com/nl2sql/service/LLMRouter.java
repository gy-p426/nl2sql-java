package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.client.GenericVolcanoClient;
import com.nl2sql.client.LLMClient;
import com.nl2sql.config.LLMProperties;
import com.nl2sql.enums.LLMTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM路由器
 * 根据任务类型选择合适的模型
 */
@Slf4j
@Service
public class LLMRouter {

    private final LLMProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, LLMClient> clientCache = new HashMap<>();

    public LLMRouter(LLMProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        initializeClients();
    }

    /**
     * 初始化所有LLM客户端
     */
    private void initializeClients() {
        if (llmProperties.getModels() == null) {
            log.warn("⚠️ 未配置LLM模型");
            return;
        }

        llmProperties.getModels().forEach((key, config) -> {
            if (config.getEnabled() != null && config.getEnabled()) {
                LLMClient client = new GenericVolcanoClient(key, config, objectMapper);
                clientCache.put(key, client);
                log.info("✅ 初始化LLM客户端: {} - {}", key, config.getModel());
            }
        });
    }

    /**
     * 根据任务类型路由到对应的模型
     */
    public Map<String, Object> route(LLMTaskType taskType, String prompt, Double temperature) {
        String modelKey = getModelKeyForTask(taskType);
        
        if (modelKey == null) {
            log.error("❌ 未找到任务类型 {} 的模型配置", taskType);
            return Map.of("error", "未配置模型");
        }

        LLMClient client = clientCache.get(modelKey);
        
        if (client == null || !client.isAvailable()) {
            log.error("❌ 模型 {} 不可用", modelKey);
            return Map.of("error", "模型不可用");
        }

        log.info("🎯 任务类型: {} -> 使用模型: {} ({})", taskType, modelKey, client.getModelName());
        return client.generate(prompt, temperature);
    }

    /**
     * 获取指定模型的客户端
     */
    public LLMClient getClient(String modelKey) {
        return clientCache.get(modelKey);
    }

    /**
     * 根据任务类型获取模型key
     */
    private String getModelKeyForTask(LLMTaskType taskType) {
        if (llmProperties.getRouting() == null) {
            return null;
        }

        return switch (taskType) {
            case CONTINUOUS_QUESTION -> llmProperties.getRouting().get("continuous-question");
            case DATABASE_SELECTION -> llmProperties.getRouting().get("database-selection");
            case KEYWORD_EXTRACTION -> llmProperties.getRouting().get("keyword-extraction");
            case SQL_GENERATION -> llmProperties.getRouting().get("sql-generation");
//            case SQL_EVALUATION -> "glm-4";  // SQL评判固定使用glm-4
            case SQL_EVALUATION -> "deepseek-v3-2-sql";
        };
    }

    /**
     * 获取所有SQL生成模型的客户端
     */
    public Map<String, LLMClient> getSQLGenerationClients() {
        Map<String, LLMClient> sqlClients = new HashMap<>();
        sqlClients.put("deepseek-v3-1", clientCache.get("deepseek-v3-1"));
        sqlClients.put("deepseek-v3-2-sql", clientCache.get("deepseek-v3-2-sql"));
//        sqlClients.put("doubao-code", clientCache.get("doubao-code"));
        return sqlClients;
    }
}
