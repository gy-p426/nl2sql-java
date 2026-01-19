package com.nl2sql.enums;

/**
 * LLM任务类型枚举
 */
public enum LLMTaskType {
    /**
     * 连续问题判断
     */
    CONTINUOUS_QUESTION,
    
    /**
     * 数据库选择
     */
    DATABASE_SELECTION,
    
    /**
     * 关键词提取
     */
    KEYWORD_EXTRACTION,
    
    /**
     * SQL生成
     */
    SQL_GENERATION,
    
    /**
     * SQL评判
     */
    SQL_EVALUATION
}
