package com.nl2sql.fileinfo.model.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传请求 DTO
 * @author Ayaki
 * @since 2026/01/29
 */
@Data
@Schema(description = "文件上传请求DTO")
public class FileUploadDTO {

    /**
     * 上传文件（前端上传的文件流）
     */
    @Parameter(
            description = "待上传的文件（支持docx、png、jpg等格式）",
            required = true, // 标记为必填项
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "multipart/form-data" // 指定媒体类型为表单文件上传
            )
    )
    private MultipartFile file;

    /**
     * 上传用户ID（前端传递）
     */
    private Long uploadUserId;

    /**
     * 文件备注（可选，前端传递）
     */
    private String remark = "";
}