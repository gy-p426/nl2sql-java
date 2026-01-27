package com.excelmanage.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件上传响应DTO
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 创建的表信息列表
     */
    private List<TableInfo> tables;
    
    /**
     * 错误详情（如果有）
     */
    private List<String> errors;
    
    /**
     * 警告信息
     */
    private List<String> warnings;
    
    /**
     * 表信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * Sheet名称（Excel）或文件名（CSV）
         */
        private String sheetName;
        
        /**
         * 行数
         */
        private Integer rowCount;
        
        /**
         * 列数
         */
        private Integer columnCount;
        
        /**
         * 列信息
         */
        private List<ColumnInfo> columns;
    }
    
    /**
     * 列信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        /**
         * 列名（Excel中的原始列名，保留中文）
         */
        private String columnName;
        
        /**
         * 数据库中的实际列名（清理后的列名）
         */
        private String dbColumnName;
        
        /**
         * 数据类型
         */
        private String dataType;
        
        /**
         * 长度
         */
        private Integer length;
        
        /**
         * 是否可为空
         */
        private Boolean nullable;
    }
}
