package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuerySqlResponse {
    
    private Boolean success;
    private String question;
    private String sql;
    private List<Map<String, Object>> results;
    private Integer resultCount;
    private String error;
    private Long executionTime;
    
    // 手动添加builder方法以解决lombok问题
    public static QuerySqlResponseBuilder builder() {
        return new QuerySqlResponseBuilder();
    }
    
    public static class QuerySqlResponseBuilder {
        private Boolean success;
        private String question;
        private String sql;
        private List<Map<String, Object>> results;
        private Integer resultCount;
        private String error;
        private Long executionTime;
        
        public QuerySqlResponseBuilder success(Boolean success) {
            this.success = success;
            return this;
        }
        
        public QuerySqlResponseBuilder question(String question) {
            this.question = question;
            return this;
        }
        
        public QuerySqlResponseBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }
        
        public QuerySqlResponseBuilder results(List<Map<String, Object>> results) {
            this.results = results;
            return this;
        }
        
        public QuerySqlResponseBuilder resultCount(Integer resultCount) {
            this.resultCount = resultCount;
            return this;
        }
        
        public QuerySqlResponseBuilder error(String error) {
            this.error = error;
            return this;
        }
        
        public QuerySqlResponseBuilder executionTime(Long executionTime) {
            this.executionTime = executionTime;
            return this;
        }
        
        public QuerySqlResponse build() {
            return new QuerySqlResponse(success, question, sql, results, 
                                      resultCount, error, executionTime);
        }
    }
}