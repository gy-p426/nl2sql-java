package com.excelmanage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Excel管理DTO
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExcelManageDTO {
    
    /**
     * ID
     */
    private Integer id;

    /**
     * Excel文件名称
     */
    private String name;

    /**
     * Excel文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 状态
     */
    private Boolean status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
