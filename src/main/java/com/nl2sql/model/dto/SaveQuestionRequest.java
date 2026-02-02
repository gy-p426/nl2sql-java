package com.nl2sql.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存问题请求DTO
 */
@Data
@Schema(description = "保存问题到Session请求")
public class SaveQuestionRequest {
    
    @NotBlank(message = "问题不能为空")
    @Schema(description = "用户问题", example = "查询所有用户信息")
    private String question;
    
    @NotBlank(message = "窗口ID不能为空")
    @Schema(description = "窗口ID", example = "analysis_window_1769851225616_tqk9teevb")
    private String windowId;
    
    @Schema(description = "用户ID", example = "2")
    private Integer userId;
}
