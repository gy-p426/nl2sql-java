package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {
    
    private Boolean success;
    private String sessionId;
    private String question;
    private String mergedQuestion;
    private Boolean isContinuous;
    private List<String> selectedDatabases;
    private Map<String, List<String>> keywords;
    private String sql;
    private String sqlExplanation;  // SQL解释
    private String model;  // 生成SQL的模型
    private List<Map<String, Object>> results;
    private Integer resultCount;
    private String error;
    private Long executionTime;
}
