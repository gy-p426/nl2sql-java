package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL查询请求
 */
@Data
public class QuerySqlRequest {
    
    @NotBlank(message = "问题不能为空")
    private String question;
    
    @NotEmpty(message = "候选表不能为空")
    private List<String> candidateTables;
    
    private Map<String, List<String>> mergedKeywords;
    
    private String windowId = "default";
    
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotNull(message = "用户ID不能为空")
    private Integer userId;
    
    // 手动添加getter方法以解决lombok问题
    public String getQuestion() {
        return question;
    }
    
    public List<String> getCandidateTables() {
        return candidateTables;
    }
    
    public Map<String, List<String>> getMergedKeywords() {
        return mergedKeywords;
    }
    
    public String getWindowId() {
        return windowId;
    }
    
    public String getSessionId() {
        return sessionId;
    }

    public Integer getUserId() {
        return userId;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public void setCandidateTables(List<String> candidateTables) {
        this.candidateTables = candidateTables;
    }
    
    public void setMergedKeywords(Map<String, List<String>> mergedKeywords) {
        this.mergedKeywords = mergedKeywords;
    }
    
    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}