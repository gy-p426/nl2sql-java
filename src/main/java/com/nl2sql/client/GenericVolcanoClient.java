package com.nl2sql.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.LLMProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用火山引擎客户端
 * 支持配置不同的模型
 */
@Slf4j
public class GenericVolcanoClient implements LLMClient {

    private final LLMProperties.ModelConfig config;
    private final ObjectMapper objectMapper;
    private final String modelKey;

    public GenericVolcanoClient(String modelKey, LLMProperties.ModelConfig config, ObjectMapper objectMapper) {
        this.modelKey = modelKey;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> generate(String prompt, Double temperature) {
        try {
            log.info("📤 [{}] 向AI模型发送请求 - 模型: {}, 温度: {}", 
                modelKey, config.getModel(), temperature);
//            log.debug("📝 [{}] 发送的提示词: {}", modelKey, prompt);
            log.debug("📝 [{}]发送的提示词: {}", modelKey,
                    prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", config.getModel());
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", temperature != null ? temperature : 0.5);

            String response = sendRequest(requestBody);
            
            log.info("📥 [{}] 收到AI模型响应 - 长度: {}字符", modelKey, response.length());
            log.debug("📄 [{}] AI响应内容: {}", modelKey, response);

            return Map.of(
                "response", response,
                "model", config.getModel(),
                "modelKey", modelKey
            );
        } catch (Exception e) {
            log.error("❌ [{}] 火山引擎API调用错误: {}", modelKey, e.getMessage());
            return Map.of(
                "response", "",
                "model", config.getModel(),
                "modelKey", modelKey,
                "error", e.getMessage()
            );
        }
    }

    private String sendRequest(Map<String, Object> requestBody) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = config.getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(url);
            
            httpPost.setHeader("Authorization", "Bearer " + config.getApiKey());
            httpPost.setHeader("Content-Type", "application/json");
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes());
                
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                JsonNode choices = jsonNode.get("choices");
                
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null) {
                        return message.get("content").asText();
                    }
                }
                
                return "";
            }
        }
    }

    @Override
    public String getModelName() {
        return config.getModel();
    }

    @Override
    public boolean isAvailable() {
        return config.getEnabled() != null && config.getEnabled()
            && config.getApiKey() != null && !config.getApiKey().isEmpty();
    }
}
