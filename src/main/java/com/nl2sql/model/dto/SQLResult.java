package com.nl2sql.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL结果（包含SQL和解释）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SQLResult {
    
    /**
     * SQL语句
     */
    private String sql;
    
    /**
     * SQL解释
     */
    private String explanation;
    
    /**
     * 生成该SQL的模型
     */
    private String model;
}
