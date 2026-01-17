package com.nl2sql.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎 DeepSeek-V3 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VolcanoEngineClient {

    private final NL2SQLProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 生成文本（无上下文）
     */
    public Map<String, Object> generate(String prompt, Double temperature) {
        try {
            log.info("📤 向AI模型发送请求 - 模型: {}, 温度: {}", 
                properties.getVolcanoEngine().getModel(), temperature);
//            log.debug("📝 发送的提示词: {}",
//                prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt);
            log.debug("📝 发送的提示词: {}", prompt);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", properties.getVolcanoEngine().getModel());
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", temperature != null ? temperature : 0.5);

            String response = sendRequest(requestBody);
            
            log.info("📥 收到AI模型响应 - 长度: {}字符", response.length());
            log.debug("📄 AI响应内容: {}", response);

            return Map.of(
                "response", response,
                "model", properties.getVolcanoEngine().getModel()
            );
        } catch (Exception e) {
            log.error("❌ 火山引擎API调用错误: {}", e.getMessage());
            return Map.of(
                "response", "",
                "model", properties.getVolcanoEngine().getModel(),
                "error", e.getMessage()
            );
        }
    }

    /**
     * 发送HTTP请求到火山引擎API
     */
    private String sendRequest(Map<String, Object> requestBody) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = properties.getVolcanoEngine().getBaseUrl() + "/chat/completions";
            HttpPost httpPost = new HttpPost(url);
            
            httpPost.setHeader("Authorization", "Bearer " + properties.getVolcanoEngine().getApiKey());
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
}
