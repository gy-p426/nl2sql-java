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
    private List<Map<String, Object>> results;
    private Integer resultCount;
    private String error;
    private Long executionTime;
    
    // 手动添加builder方法以解决lombok问题
    public static QueryResponseBuilder builder() {
        return new QueryResponseBuilder();
    }
    
    public static class QueryResponseBuilder {
        private Boolean success;
        private String sessionId;
        private String question;
        private String mergedQuestion;
        private Boolean isContinuous;
        private List<String> selectedDatabases;
        private Map<String, List<String>> keywords;
        private String sql;
        private List<Map<String, Object>> results;
        private Integer resultCount;
        private String error;
        private Long executionTime;
        
        public QueryResponseBuilder success(Boolean success) {
            this.success = success;
            return this;
        }
        
        public QueryResponseBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public QueryResponseBuilder question(String question) {
            this.question = question;
            return this;
        }
        
        public QueryResponseBuilder mergedQuestion(String mergedQuestion) {
            this.mergedQuestion = mergedQuestion;
            return this;
        }
        
        public QueryResponseBuilder isContinuous(Boolean isContinuous) {
            this.isContinuous = isContinuous;
            return this;
        }
        
        public QueryResponseBuilder selectedDatabases(List<String> selectedDatabases) {
            this.selectedDatabases = selectedDatabases;
            return this;
        }
        
        public QueryResponseBuilder keywords(Map<String, List<String>> keywords) {
            this.keywords = keywords;
            return this;
        }
        
        public QueryResponseBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }
        
        public QueryResponseBuilder results(List<Map<String, Object>> results) {
            this.results = results;
            return this;
        }
        
        public QueryResponseBuilder resultCount(Integer resultCount) {
            this.resultCount = resultCount;
            return this;
        }
        
        public QueryResponseBuilder error(String error) {
            this.error = error;
            return this;
        }
        
        public QueryResponseBuilder executionTime(Long executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public QueryResponse build() {
            return new QueryResponse(success, sessionId, question, mergedQuestion, 
                                   isContinuous, selectedDatabases, keywords, sql, 
                                   results, resultCount, error, executionTime);
        }
    }
}
