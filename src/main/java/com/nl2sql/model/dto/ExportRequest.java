package com.nl2sql.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 导出请求DTO
 */
public class ExportRequest {
    
    /**
     * SQL语句（必需）
     */
    @NotBlank(message = "SQL不能为空")
    private String sql;
    
    /**
     * 导出格式（必需）
     * - csv
     * - excel
     */
    @NotBlank(message = "导出格式不能为空")
    private String format;
    
    /**
     * 最大行数限制（可选）
     * 默认100万行
     */
    private Integer maxRows = 1000000;
    
    // Getter and Setter methods
    public String getSql() {
        return sql;
    }
    
    public void setSql(String sql) {
        this.sql = sql;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public Integer getMaxRows() {
        return maxRows;
    }
    
    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }
}
