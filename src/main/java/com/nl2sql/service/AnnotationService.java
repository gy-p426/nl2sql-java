package com.nl2sql.service;

import com.nl2sql.model.entity.CustomAnnotation;
import com.nl2sql.repository.CustomAnnotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * 注释管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final DatabaseService databaseService;
    private final CustomAnnotationRepository annotationRepository;

    /**
     * 获取数据库 Schema（包含自定义注释）
     */
    public Map<String, Object> getDatabaseSchema(String dbName, Integer userId) {
        try (Connection conn = databaseService.getConnection(userId, dbName);
             Statement stmt = conn.createStatement()) {
            
            Map<String, Object> result = new HashMap<>();
            result.put("database", dbName);
            
            List<Map<String, Object>> tables = new ArrayList<>();
            
            // 获取数据库类型
            String dbType = databaseService.getDatabaseType(dbName);
            
            // 根据数据库类型构建查询语句
            String sql;
            if ("oracle".equals(dbType)) {
                // Oracle 查询 - 使用ALL_TAB_COMMENTS获取表注释
                // 对于Oracle，使用dbName作为OWNER，这样可以查询指定用户的表
                sql = "SELECT a.TABLE_NAME, b.COMMENTS AS TABLE_COMMENT, a.NUM_ROWS AS TABLE_ROWS " +
                      "FROM ALL_TABLES a " +
                      "LEFT JOIN ALL_TAB_COMMENTS b ON a.OWNER = b.OWNER AND a.TABLE_NAME = b.TABLE_NAME " +
                      "WHERE a.OWNER = '" + dbName.toUpperCase() + "' " +
                      "ORDER BY a.TABLE_NAME";
            } else {
                // MySQL 查询
                sql = String.format("""
                    SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, CREATE_TIME, UPDATE_TIME
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = '%s'
                    ORDER BY TABLE_NAME
                    """, dbName);
            }
            
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("table_name", tableName);
                tableInfo.put("db_comment", rs.getString("TABLE_COMMENT"));
                tableInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, null, userId));
                
                // 获取表行数
                int tableRows = rs.getInt("TABLE_ROWS");
                
                // 对于Oracle，如果NUM_ROWS为0，使用COUNT(*)实时计算
                if ("oracle".equals(dbType) && tableRows == 0) {
                    try {
                        // 使用新的Statement来执行COUNT查询，避免关闭之前的ResultSet
                        try (Statement countStmt = conn.createStatement()) {
                            String countSql = "SELECT COUNT(*) FROM " + tableName;
                            try (ResultSet countRs = countStmt.executeQuery(countSql)) {
                                if (countRs.next()) {
                                    tableRows = countRs.getInt(1);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ 计算表 {} 行数时出错: {}", tableName, e.getMessage());
                        // 如果计算失败，保持NUM_ROWS的值
                    }
                }
                
                tableInfo.put("table_rows", tableRows);
                
                // 尝试获取CREATE_TIME和UPDATE_TIME，Oracle可能没有这些字段
                try {
                    tableInfo.put("create_time", rs.getTimestamp("CREATE_TIME"));
                } catch (Exception e) {
                    // Oracle没有CREATE_TIME字段，设置为null
                    tableInfo.put("create_time", null);
                }
                
                try {
                    tableInfo.put("update_time", rs.getTimestamp("UPDATE_TIME"));
                } catch (Exception e) {
                    // Oracle没有UPDATE_TIME字段，设置为null
                    tableInfo.put("update_time", null);
                }
                
                // 获取列信息
                tableInfo.put("columns", getTableColumns(dbName, tableName, userId));
                
                tables.add(tableInfo);
            }
            
            result.put("tables", tables);
            result.put("total_tables", tables.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 获取数据库 {} 结构错误: {}", dbName, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 获取表的列信息
     */
    private List<Map<String, Object>> getTableColumns(String dbName, String tableName) 
            throws Exception {
        return getTableColumns(dbName, tableName, null);
    }

    private List<Map<String, Object>> getTableColumns(String dbName, String tableName, Integer userId)
            throws Exception {
        
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (Connection conn = userId != null
                ? databaseService.getConnection(userId, dbName)
                : databaseService.getConnection(dbName);
             Statement stmt = conn.createStatement()) {
            
            // 获取数据库类型
            String dbType = databaseService.getDatabaseType(dbName);
            
            // 根据数据库类型构建查询语句
            String sql;
            if ("oracle".equals(dbType)) {
                // Oracle 查询
                sql = "SELECT a.COLUMN_NAME, b.COMMENTS AS COLUMN_COMMENT, a.DATA_TYPE AS COLUMN_TYPE, a.DATA_TYPE, " +
                      "a.NULLABLE AS IS_NULLABLE, a.DATA_DEFAULT AS COLUMN_DEFAULT, " +
                      "CASE WHEN a.COLUMN_NAME IN (SELECT COLUMN_NAME FROM ALL_CONSTRAINTS c, ALL_CONS_COLUMNS cc " +
                      "WHERE c.OWNER = '" + dbName.toUpperCase() + "' AND c.TABLE_NAME = '" + tableName.toUpperCase() + "' " +
                      "AND c.CONSTRAINT_TYPE = 'P' AND c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME " +
                      "AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME) THEN 'PRI' ELSE '' END AS COLUMN_KEY " +
                      "FROM ALL_TAB_COLUMNS a " +
                      "LEFT JOIN ALL_COL_COMMENTS b ON a.OWNER = b.OWNER AND a.TABLE_NAME = b.TABLE_NAME AND a.COLUMN_NAME = b.COLUMN_NAME " +
                      "WHERE a.OWNER = '" + dbName.toUpperCase() + "' AND a.TABLE_NAME = '" + tableName.toUpperCase() + "' " +
                      "ORDER BY a.COLUMN_ID";
            } else {
                // MySQL 查询
                sql = String.format("""
                    SELECT COLUMN_NAME, COLUMN_COMMENT, COLUMN_TYPE, DATA_TYPE,
                           IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                    ORDER BY ORDINAL_POSITION
                    """, dbName, tableName);
            }
            
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                Map<String, Object> columnInfo = new HashMap<>();
                columnInfo.put("column_name", columnName);
                columnInfo.put("db_comment", rs.getString("COLUMN_COMMENT"));
                columnInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, columnName, userId));
                columnInfo.put("column_type", rs.getString("COLUMN_TYPE"));
                columnInfo.put("data_type", rs.getString("DATA_TYPE"));
                columnInfo.put("is_nullable", "YES".equals(rs.getString("IS_NULLABLE")) || "Y".equals(rs.getString("IS_NULLABLE")));
                columnInfo.put("column_default", rs.getString("COLUMN_DEFAULT"));
                columnInfo.put("column_key", rs.getString("COLUMN_KEY"));
                
                columns.add(columnInfo);
            }
        }
        
        return columns;
    }

    /**
     * 获取自定义注释
     */
    public String getCustomAnnotation(String dbName, String tableName, String columnName) {
        return getCustomAnnotation(dbName, tableName, columnName, null);
    }

    public String getCustomAnnotation(String dbName, String tableName, String columnName, Integer userId) {
        try {
            Optional<CustomAnnotation> annotation;
            
            if (columnName == null) {
                // 查找表注释
                annotation = userId != null
                    ? annotationRepository.findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnNameIsNull(
                        userId, dbName, tableName)
                    : annotationRepository.findByDatabaseNameAndTableNameAndColumnNameIsNull(dbName, tableName);
            } else {
                // 查找列注释
                annotation = userId != null
                    ? annotationRepository.findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnName(
                        userId, dbName, tableName, columnName)
                    : annotationRepository.findByDatabaseNameAndTableNameAndColumnName(dbName, tableName, columnName);
            }
            
            return annotation.map(CustomAnnotation::getCustomComment).orElse("");
        } catch (Exception e) {
            log.error("❌ 获取自定义注释错误: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 更新注释
     */
    @Transactional
    public Map<String, Object> updateAnnotation(
            String dbName, String tableName, String columnName, String comment) {
        return updateAnnotation(dbName, tableName, columnName, comment, null);
    }

    @Transactional
    public Map<String, Object> updateAnnotation(
            String dbName, String tableName, String columnName, String comment, Integer userId) {
        
        try {
            if (userId != null) {
                try (Connection ignored = databaseService.getConnection(userId, dbName)) {
                    // 仅用于校验用户是否有该数据库访问权限
                }
            }

            CustomAnnotation annotation;
            
            if (columnName == null) {
                // 更新表注释
                annotation = userId != null
                    ? annotationRepository.findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnNameIsNull(
                        userId, dbName, tableName).orElse(new CustomAnnotation())
                    : annotationRepository.findByDatabaseNameAndTableNameAndColumnNameIsNull(
                        dbName, tableName).orElse(new CustomAnnotation());
                log.info("✏️ 更新表注释: {}.{}", dbName, tableName);
            } else {
                // 更新列注释
                annotation = userId != null
                    ? annotationRepository.findByOwnerUserIdAndDatabaseNameAndTableNameAndColumnName(
                        userId, dbName, tableName, columnName).orElse(new CustomAnnotation())
                    : annotationRepository.findByDatabaseNameAndTableNameAndColumnName(
                        dbName, tableName, columnName).orElse(new CustomAnnotation());
                log.info("✏️ 更新列注释: {}.{}.{}", dbName, tableName, columnName);
            }
            
            if (userId != null) {
                annotation.setOwnerUserId(userId);
            }
            annotation.setDatabaseName(dbName);
            annotation.setTableName(tableName);
            annotation.setColumnName(columnName);
            annotation.setCustomComment(comment);
            
            annotationRepository.save(annotation);
            
            return Map.of(
                "success", true,
                "message", "注释更新成功",
                "database", dbName,
                "table", tableName,
                "column", columnName != null ? columnName : "",
                "comment", comment
            );
            
        } catch (Exception e) {
            log.error("❌ 更新注释错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 重新加载注释状态
     *
     * 当前注释已持久化在数据库中，此方法保留为控制器触发入口。
     */
    public void reloadAnnotationsState() {
        log.info("🔄 重新加载注释状态（数据库模式，无需文件加载）");
    }

    /**
     * 加载注释（已废弃，保留用于兼容）
     */
    @Deprecated
    public void loadAnnotations() {
        reloadAnnotationsState();
    }
}
