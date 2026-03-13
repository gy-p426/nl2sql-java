package com.nl2sql.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量嵌入服务
 * 调用 doubao-embedding-large-text-250515 模型获取文本向量，
 * 支持降维和L2归一化，用于语义相似度计算提升找表正确率。
 */
@Slf4j
@Service
public class EmbeddingService {

    private final ObjectMapper objectMapper;

    @Value("${nl2sql.embedding.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${nl2sql.embedding.api-key:${VOLCANO_API_KEY:}}")
    private String apiKey;

    @Value("${nl2sql.embedding.model:doubao-embedding-large-text-250515}")
    private String model;

    @Value("${nl2sql.embedding.dimension:256}")
    private int dimension;

    @Value("${nl2sql.embedding.enabled:true}")
    private boolean enabled;

    /**
     * 表向量缓存：key=表描述文本, value=归一化后的向量
     */
    private final ConcurrentHashMap<String, double[]> embeddingCache = new ConcurrentHashMap<>();

    public EmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 检查嵌入服务是否可用
     */
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 获取单个文本的向量（带缓存）
     */
    public double[] getEmbedding(String text) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return null;
        }

        return embeddingCache.computeIfAbsent(text, k -> {
            List<double[]> embeddings = fetchEmbeddings(List.of(k));
            return embeddings != null && !embeddings.isEmpty() ? embeddings.get(0) : null;
        });
    }

    /**
     * 批量获取文本向量（带缓存），每批最多4条文本
     */
    public Map<String, double[]> getEmbeddings(List<String> texts) {
        if (!isAvailable() || texts == null || texts.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, double[]> results = new LinkedHashMap<>();
        List<String> uncached = new ArrayList<>();

        // 检查缓存
        for (String text : texts) {
            double[] cached = embeddingCache.get(text);
            if (cached != null) {
                results.put(text, cached);
            } else {
                uncached.add(text);
            }
        }

        // 批量获取未缓存的向量，每批最多4条
        if (!uncached.isEmpty()) {
            for (int i = 0; i < uncached.size(); i += 4) {
                List<String> batch = uncached.subList(i, Math.min(i + 4, uncached.size()));
                List<double[]> embeddings = fetchEmbeddings(batch);
                if (embeddings != null) {
                    for (int j = 0; j < batch.size() && j < embeddings.size(); j++) {
                        double[] emb = embeddings.get(j);
                        if (emb != null) {
                            embeddingCache.put(batch.get(j), emb);
                            results.put(batch.get(j), emb);
                        }
                    }
                }
            }
        }

        return results;
    }

    /**
     * 计算两个向量的余弦相似度
     * 向量已L2归一化，所以余弦相似度等于点积
     */
    public double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA == null || vecB == null) {
            return 0.0;
        }
        int len = Math.min(vecA.length, vecB.length);
        double dotProduct = 0.0;
        for (int i = 0; i < len; i++) {
            dotProduct += vecA[i] * vecB[i];
        }
        return dotProduct;
    }

    /**
     * 清空嵌入缓存
     */
    public void clearCache() {
        embeddingCache.clear();
        log.info("🧹 向量嵌入缓存已清空");
    }

    /**
     * 调用火山引擎 embedding API 获取向量
     */
    private List<double[]> fetchEmbeddings(List<String> inputs) {
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(30))
                    .setResponseTimeout(Timeout.ofSeconds(60))
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                String url = baseUrl + "/embeddings";
                HttpPost httpPost = new HttpPost(url);
                httpPost.setHeader("Authorization", "Bearer " + apiKey);
                httpPost.setHeader("Content-Type", "application/json");

                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", model);
                requestBody.put("input", inputs);
                requestBody.put("encoding_format", "float");

                String jsonBody = objectMapper.writeValueAsString(requestBody);
                httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

                log.debug("📤 向量嵌入请求: {} 条文本, 模型: {}", inputs.size(), model);

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    String responseBody = new String(response.getEntity().getContent().readAllBytes());
                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                    // 检查错误
                    if (jsonNode.has("error")) {
                        log.error("❌ Embedding API 错误: {}", jsonNode.get("error"));
                        return null;
                    }

                    JsonNode dataArray = jsonNode.get("data");
                    if (dataArray == null || !dataArray.isArray()) {
                        log.error("❌ Embedding API 响应格式异常: {}", responseBody);
                        return null;
                    }

                    List<double[]> embeddings = new ArrayList<>();
                    for (JsonNode item : dataArray) {
                        JsonNode embeddingNode = item.get("embedding");
                        if (embeddingNode != null && embeddingNode.isArray()) {
                            // 降维：截取前 dimension 维度
                            int dim = Math.min(dimension, embeddingNode.size());
                            double[] vector = new double[dim];
                            for (int i = 0; i < dim; i++) {
                                vector[i] = embeddingNode.get(i).asDouble();
                            }
                            // L2归一化
                            l2Normalize(vector);
                            embeddings.add(vector);
                        }
                    }

                    log.debug("📥 成功获取 {} 条向量, 维度: {}", embeddings.size(), dimension);
                    return embeddings;
                }
            }
        } catch (Exception e) {
            log.error("❌ 调用 Embedding API 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * L2归一化：将向量长度归一化为1
     * 归一化后余弦相似度 = 向量点积
     */
    private void l2Normalize(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
