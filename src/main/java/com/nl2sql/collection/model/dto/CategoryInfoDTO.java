package com.nl2sql.collection.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * <p>
 * 目录结构 DTO
 * </p>
 *
 * @author Ayaki
 * @since 2026/1/29 15:36
 */
@Data
public class CategoryInfoDTO {

    /**
     * 目录ID（仅修改时需要）
     */
    private Long categoryId;

    private Integer userId; // 用户ID

    /**
     * 目录名称（必填）
     */
    @NotBlank(message = "目录名称不能为空")
    private String categoryName;

    /**
     * 目录级别（必填，非负）
     */
    @NotNull(message = "目录级别不能为空")
    @PositiveOrZero(message = "目录级别不能为负数")
    private Integer categoryLevel;

    /**
     * 父目录ID（必填，非负）
     */
    @NotNull(message = "父目录ID不能为空")
    @PositiveOrZero(message = "父目录ID不能为负数")
    private Long parentCategoryId;

    /**
     * 目录组别（必填，只能是favorite或file）
     */
    @NotBlank(message = "目录组别不能为空")
    private String categoryGroup;
}