package com.nl2sql.client;

import java.util.Map;

/**
 * LLM客户端接口
 */
public interface LLMClient {
    
    /**
     * 生成文本
     * 
     * @param prompt 提示词
     * @param temperature 温度参数
     * @return 响应结果
     */
    Map<String, Object> generate(String prompt, Double temperature);
    
    /**
     * 获取模型名称
     * 
     * @return 模型名称
     */
    String getModelName();
    
    /**
     * 检查模型是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();
}
