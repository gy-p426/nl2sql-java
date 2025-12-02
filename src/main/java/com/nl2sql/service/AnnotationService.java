package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 注释管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final DatabaseService databaseService;
    private final NL2SQLProperties properties;
    private final ObjectMapper objectMapper;
    
    private Map<String, Object> annotations = new HashMap<>();

    /**
     * 获取数据库 Schema（包含自定义注释）
     */
    public Map<String, Object> getDatabaseSchema(String dbName) {
        try (Connection conn = databaseService.getConnection(dbName);
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
                tableInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, null));
                tableInfo.put("table_rows", rs.getInt("TABLE_ROWS"));
                
                // 获取列信息
                tableInfo.put("columns", getTableColumns(dbName, tableName));
                
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
        
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (Connection conn = databaseService.getConnection(dbName);
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
                columnInfo.put("custom_comment", getCustomAnnotation(dbName, tableName, columnName));
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
    @SuppressWarnings("unchecked")
    private String getCustomAnnotation(String dbName, String tableName, String columnName) {
        loadAnnotations();
        
        if (!annotations.containsKey(dbName)) {
            return "";
        }
        
        Map<String, Object> dbAnnotations = (Map<String, Object>) annotations.get(dbName);
        if (!dbAnnotations.containsKey(tableName)) {
            return "";
        }
        
        Map<String, Object> tableData = (Map<String, Object>) dbAnnotations.get(tableName);
        
        if (columnName == null) {
            return (String) tableData.getOrDefault("table_comment", "");
        } else {
            Map<String, String> columns = (Map<String, String>) tableData.get("columns");
            return columns != null ? columns.getOrDefault(columnName, "") : "";
        }
    }

    /**
     * 更新注释
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateAnnotation(
            String dbName, String tableName, String columnName, String comment) {
        
        try {
            loadAnnotations();
            
            annotations.putIfAbsent(dbName, new HashMap<>());
            Map<String, Object> dbAnnotations = (Map<String, Object>) annotations.get(dbName);
            
            dbAnnotations.putIfAbsent(tableName, new HashMap<>());
            Map<String, Object> tableData = (Map<String, Object>) dbAnnotations.get(tableName);
            
            if (columnName == null) {
                tableData.put("table_comment", comment);
                log.info("✏️ 更新表注释: {}.{}", dbName, tableName);
            } else {
                tableData.putIfAbsent("columns", new HashMap<>());
                Map<String, String> columns = (Map<String, String>) tableData.get("columns");
                columns.put(columnName, comment);
                log.info("✏️ 更新列注释: {}.{}.{}", dbName, tableName, columnName);
            }
            
            tableData.put("updated_at", LocalDateTime.now().toString());
            
            saveAnnotations();
            
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
     * 加载注释
     */
    @SuppressWarnings("unchecked")
    public void loadAnnotations() {
        try {
            String annotationFile = properties.getFiles().getAnnotation();
            File file = new File(annotationFile);
            
            if (file.exists()) {
                String content = Files.readString(Paths.get(annotationFile));
                annotations = objectMapper.readValue(content, Map.class);
                log.debug("📂 加载注释文件: {}", annotationFile);
            } else {
                annotations = new HashMap<>();
                log.debug("📝 创建新的注释文件");
            }
        } catch (Exception e) {
            log.error("❌ 加载注释文件错误: {}", e.getMessage());
            annotations = new HashMap<>();
        }
    }

    /**
     * 保存注释
     */
    private void saveAnnotations() {
        try {
            String annotationFile = properties.getFiles().getAnnotation();
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(annotations);
            Files.writeString(Paths.get(annotationFile), json);
            log.info("💾 保存注释文件成功");
        } catch (Exception e) {
            log.error("❌ 保存注释文件错误: {}", e.getMessage());
        }
    }
}
