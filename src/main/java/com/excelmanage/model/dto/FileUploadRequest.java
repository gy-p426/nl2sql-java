package com.excelmanage.model.dto;

import lombok.Data;

/**
 * 文件上传请求DTO
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Data
public class FileUploadRequest {
    
    /**
     * 表名前缀（可选，如果不提供则使用文件名）
     * 注意：如果指定了tableNames，则此参数无效
     */
    private String tableNamePrefix;
    
    /**
     * 自定义表名（可选）
     * - 单Sheet文件：直接使用此表名
     * - 多Sheet文件：可以指定多个表名，用逗号分隔，按Sheet顺序对应
     *   例如："用户表,订单表,商品表" 对应 Sheet1, Sheet2, Sheet3
     *   如果表名数量少于Sheet数量，多余的Sheet使用tableNamePrefix规则生成表名
     */
    private String tableNames;
    
    /**
     * 是否覆盖已存在的表（默认false）
     */
    private Boolean overwrite = false;
    
    /**
     * 第一行是否为列名（默认true）
     */
    private Boolean firstRowAsHeader = true;
    
    /**
     * 跳过的行数（从文件开头，默认0）
     */
    private Integer skipRows = 0;
    
    /**
     * 最大行数限制（可选，0表示不限制）
     */
    private Integer maxRows = 0;
    
    /**
     * 数据库名称（默认exceldatabase）
     */
    private String databaseName = "exceldatabase";

    /**
     * 用户ID（用于数据库访问权限校验）
     */
    private Integer userId;
}
