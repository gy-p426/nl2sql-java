package com.nl2sql.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存问题响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存问题到Session响应")
public class SaveQuestionResponse {
    
    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
    
    @Schema(description = "问题内容", example = "查询所有用户信息")
    private String question;
    
    @Schema(description = "窗口ID", example = "analysis_window_1769851225616_tqk9teevb")
    private String windowId;
    
    @Schema(description = "用户ID", example = "2")
    private Integer userId;
    
    @Schema(description = "保存时间戳", example = "1738320520000")
    private Long timestamp;
}
