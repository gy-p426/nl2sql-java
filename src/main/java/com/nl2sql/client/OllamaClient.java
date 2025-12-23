package com.nl2sql.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.service.DynamicConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ollama客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DynamicConfigProvider configProvider;

    /**
     * 生成文本
     */
    public Map<String, Object> generate(String prompt, Double temperature) {
        try {
            if (!configProvider.getOllamaEnabled()) {
                throw new RuntimeException("Ollama未启用");
            }

            String host = configProvider.getOllamaHost();
            String model = configProvider.getOllamaModel();

            log.info("🤖 调用Ollama API - 模型: {}, 主机: {}", model, host);

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("stream", false);
            
            if (temperature != null) {
                Map<String, Object> options = new HashMap<>();
                options.put("temperature", temperature);
                request.put("options", options);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            String url = host + "/api/generate";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                String content = (String) result.get("response");
                
                if (content == null || content.trim().isEmpty()) {
                    throw new RuntimeException("Ollama返回空响应");
                }

                Map<String, Object> standardResponse = new HashMap<>();
                standardResponse.put("content", content);
                standardResponse.put("model", model);
                standardResponse.put("done", result.get("done"));

                log.info("✅ Ollama调用成功 - 响应长度: {}", content.length());
                return standardResponse;
            } else {
                throw new RuntimeException("Ollama API调用失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Ollama调用失败: {}", e.getMessage());
            throw new RuntimeException("Ollama调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection(String host, String model) {
        try {
            String testHost = host != null ? host : configProvider.getOllamaHost();
            String testModel = model != null ? model : configProvider.getOllamaModel();

            log.info("🔌 测试Ollama连接 - 主机: {}, 模型: {}", testHost, testModel);

            String tagsUrl = testHost + "/api/tags";
            ResponseEntity<Map> tagsResponse = restTemplate.getForEntity(tagsUrl, Map.class);
            
            if (!tagsResponse.getStatusCode().is2xxSuccessful()) {
                log.error("❌ Ollama服务不可达: {}", tagsResponse.getStatusCode());
                return false;
            }

            Map<String, Object> testRequest = new HashMap<>();
            testRequest.put("model", testModel);
            testRequest.put("prompt", "Hello");
            testRequest.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testRequest, headers);

            String generateUrl = testHost + "/api/generate";
            ResponseEntity<Map> generateResponse = restTemplate.postForEntity(generateUrl, entity, Map.class);

            if (generateResponse.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Ollama连接测试成功");
                return true;
            } else {
                log.error("❌ Ollama生成测试失败: {}", generateResponse.getStatusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Ollama连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取可用模型列表
     */
    public List<String> getAvailableModels() {
        try {
            String host = configProvider.getOllamaHost();
            String url = host + "/api/tags";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                if (result.containsKey("models")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> models = (List<Map<String, Object>>) result.get("models");
                    
                    return models.stream()
                        .map(m -> (String) m.get("name"))
                        .toList();
                }
            }
            
            return List.of();
            
        } catch (Exception e) {
            log.error("❌ 获取Ollama模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 流式生成文本（使用WebClient）
     */
    public void generateStreamReactive(String prompt, Double temperature, Consumer<String> onChunk, Runnable onComplete, Consumer<Throwable> onError) {
        try {
            if (!configProvider.getOllamaEnabled()) {
                onError.accept(new RuntimeException("Ollama未启用"));
                return;
            }

            String host = configProvider.getOllamaHost();
            String model = configProvider.getOllamaModel();

            log.info("🌊 调用Ollama流式API - 模型: {}, 主机: {}", model, host);

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("stream", true);
            
            if (temperature != null) {
                Map<String, Object> options = new HashMap<>();
                options.put("temperature", temperature);
                request.put("options", options);
            }

            String url = host + "/api/generate";

            webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                    chunk -> {
                        try {
                            // 解析每个chunk
                            @SuppressWarnings("unchecked")
                            Map<String, Object> chunkData = objectMapper.readValue(chunk, Map.class);
                            String response = (String) chunkData.get("response");
                            if (response != null && !response.isEmpty()) {
                                onChunk.accept(response);
                            }
                            
                            // 检查是否完成
                            Boolean done = (Boolean) chunkData.get("done");
                            if (Boolean.TRUE.equals(done)) {
                                onComplete.run();
                                log.info("✅ Ollama流式调用完成");
                            }
                        } catch (Exception e) {
                            log.error("❌ 解析Ollama响应失败: {}", e.getMessage());
                        }
                    },
                    error -> {
                        log.error("❌ Ollama流式调用失败: {}", error.getMessage());
                        onError.accept(error);
                    }
                );

        } catch (Exception e) {
            log.error("❌ Ollama流式调用失败: {}", e.getMessage());
            onError.accept(e);
        }
    }

    /**
     * 获取流式响应的Flux
     */
    public Flux<String> generateStreamFlux(String prompt, Double temperature) {
        if (!configProvider.getOllamaEnabled()) {
            return Flux.error(new RuntimeException("Ollama未启用"));
        }

        String host = configProvider.getOllamaHost();
        String model = configProvider.getOllamaModel();

        log.info("🌊 调用Ollama流式API (Flux) - 模型: {}, 主机: {}", model, host);

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("stream", true);
        
        if (temperature != null) {
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature);
            request.put("options", options);
        }

        String url = host + "/api/generate";

        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(String.class)
            .mapNotNull(chunk -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunkData = objectMapper.readValue(chunk, Map.class);
                    return (String) chunkData.get("response");
                } catch (Exception e) {
                    log.error("❌ 解析Ollama响应失败: {}", e.getMessage());
                    return null;
                }
            })
            .filter(response -> response != null && !response.isEmpty());
    }
}
