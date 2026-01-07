package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据库查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryDbResponse {
    
    private Boolean success;
    private String sessionId;
    private String question;
    private List<String> selectedDatabases;
    private Map<String, List<String>> keywords;
    private List<String> candidateTables;
    private String error;
    private Long executionTime;
    
    // 手动添加builder方法以解决lombok问题
    public static QueryDbResponseBuilder builder() {
        return new QueryDbResponseBuilder();
    }
    
    public static class QueryDbResponseBuilder {
        private Boolean success;
        private String sessionId;
        private String question;
        private List<String> selectedDatabases;
        private Map<String, List<String>> keywords;
        private List<String> candidateTables;
        private String error;
        private Long executionTime;
        
        public QueryDbResponseBuilder success(Boolean success) {
            this.success = success;
            return this;
        }
        
        public QueryDbResponseBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public QueryDbResponseBuilder question(String question) {
            this.question = question;
            return this;
        }
        
        public QueryDbResponseBuilder selectedDatabases(List<String> selectedDatabases) {
            this.selectedDatabases = selectedDatabases;
            return this;
        }
        
        public QueryDbResponseBuilder keywords(Map<String, List<String>> keywords) {
            this.keywords = keywords;
            return this;
        }
        
        public QueryDbResponseBuilder candidateTables(List<String> candidateTables) {
            this.candidateTables = candidateTables;
            return this;
        }
        
        public QueryDbResponseBuilder error(String error) {
            this.error = error;
            return this;
        }
        
        public QueryDbResponseBuilder executionTime(Long executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public QueryDbResponse build() {
            return new QueryDbResponse(success, sessionId, question, selectedDatabases, 
                                     keywords, candidateTables, error, executionTime);
        }
    }
}