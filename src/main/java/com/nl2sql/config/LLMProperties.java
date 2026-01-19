package com.nl2sql.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "nl2sql.llm")
public class LLMProperties {
    
    /**
     * 任务路由配置
     */
    private Map<String, String> routing;
    
    /**
     * 模型配置
     */
    private Map<String, ModelConfig> models;
    
    @Data
    public static class ModelConfig {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeout;
        private Boolean enabled;
    }
}
