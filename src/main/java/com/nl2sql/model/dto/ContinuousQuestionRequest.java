package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 判断追问请求
 */
@Data
public class ContinuousQuestionRequest {
    
    @NotBlank(message = "问题不能为空")
    private String question;
    
    private String windowId = "default";
    
    private String sessionId;
    
    // 手动添加getter方法以解决lombok问题
    public String getQuestion() {
        return question;
    }
    
    public String getWindowId() {
        return windowId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
