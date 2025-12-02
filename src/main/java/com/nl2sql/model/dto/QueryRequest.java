package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 查询请求
 */
@Data
public class QueryRequest {
    
    @NotBlank(message = "问题不能为空")
    private String question;
    
    private String windowId = "default";
    
    private String sessionId;
    
    private Boolean stream = false;
}
