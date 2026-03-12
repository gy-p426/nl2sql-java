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
            
            // 获取所有表
            String sql = String.format("""
                SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, CREATE_TIME, UPDATE_TIME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = '%s'
                ORDER BY TABLE_NAME
                """, dbName);
            
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                Map<String, Object> tableInfo = new HashMap<>();
                tableInfo.put("table_name", tableName);
                tableInfo.put("db_comment", rs.getString("TABLE_COMMENT"));
                tableInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, null, userId));
                tableInfo.put("table_rows", rs.getInt("TABLE_ROWS"));
                
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
            
            String sql = String.format("""
                SELECT COLUMN_NAME, COLUMN_COMMENT, COLUMN_TYPE, DATA_TYPE,
                       IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'
                ORDER BY ORDINAL_POSITION
                """, dbName, tableName);
            
            ResultSet rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                Map<String, Object> columnInfo = new HashMap<>();
                columnInfo.put("column_name", columnName);
                columnInfo.put("db_comment", rs.getString("COLUMN_COMMENT"));
                columnInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, columnName, userId));
                columnInfo.put("column_type", rs.getString("COLUMN_TYPE"));
                columnInfo.put("data_type", rs.getString("DATA_TYPE"));
                columnInfo.put("is_nullable", "YES".equals(rs.getString("IS_NULLABLE")));
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
