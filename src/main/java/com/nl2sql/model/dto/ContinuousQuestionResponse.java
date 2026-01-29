package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 判断追问响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContinuousQuestionResponse {
    
    /**
     * 是否为连续问题
     */
    private Boolean isContinuous;
    
    /**
     * 合并后的问题（如果是连续问题）或原问题（如果不是连续问题）
     */
    private String mergedQuestion;
    
    /**
     * 原始问题
     */
    private String originalQuestion;
    
    /**
     * 上一个问题
     */
    private String previousQuestion;
    
    /**
     * 判断理由
     */
    private String reason;
    
    // 手动添加getter方法以解决lombok问题
    public Boolean getIsContinuous() {
        return isContinuous;
    }
    
    public String getMergedQuestion() {
        return mergedQuestion;
    }
    
    public String getOriginalQuestion() {
        return originalQuestion;
    }
    
    public String getPreviousQuestion() {
        return previousQuestion;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setIsContinuous(Boolean isContinuous) {
        this.isContinuous = isContinuous;
    }
    
    public void setMergedQuestion(String mergedQuestion) {
        this.mergedQuestion = mergedQuestion;
    }
    
    public void setOriginalQuestion(String originalQuestion) {
        this.originalQuestion = originalQuestion;
    }
    
    public void setPreviousQuestion(String previousQuestion) {
        this.previousQuestion = previousQuestion;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
