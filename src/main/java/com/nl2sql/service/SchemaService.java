package com.nl2sql.service;

import com.nl2sql.config.NL2SQLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final DatabaseService databaseService;
    private final AnnotationService annotationService;
    private final NL2SQLProperties properties;

    /**
     * 导出所有数据库的 Schema
     */
    public void exportAllSchemas(boolean forceRefresh) {
        log.info("正在导出多数据库表结构...");
        
        List<String> databases = databaseService.getAllDatabases();
        
        for (String dbName : databases) {
            exportDatabaseSchema(dbName, forceRefresh);
        }
        
        log.info("✅ 数据库结构导出完成");
    }

    /**
     * 导出单个数据库的 Schema
     */
    public void exportDatabaseSchema(String dbName, boolean forceRefresh) {
        String schemaFile = properties.getFiles().getSchemaDir() + "/" + dbName + ".txt";
        
        if (!forceRefresh && Files.exists(Paths.get(schemaFile))) {
            log.debug("Schema 文件已存在，跳过: {}", schemaFile);
            return;
        }
        
        try (Connection conn = databaseService.getConnection(dbName);
             Statement stmt = conn.createStatement()) {
            
            // 获取表和列信息
            String sql = String.format("""
                SELECT 
                    t.TABLE_NAME,
                    t.TABLE_COMMENT,
                    c.COLUMN_NAME,
                    c.COLUMN_COMMENT,
                    c.COLUMN_TYPE,
                    c.DATA_TYPE,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLES t
                LEFT JOIN INFORMATION_SCHEMA.COLUMNS c 
                    ON t.TABLE_NAME = c.TABLE_NAME 
                    AND t.TABLE_SCHEMA = c.TABLE_SCHEMA
                WHERE t.TABLE_SCHEMA = '%s'
                ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION
                """, dbName);
            
            ResultSet rs = stmt.executeQuery(sql);
            
            Map<String, TableInfo> tables = new LinkedHashMap<>();
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableComment = rs.getString("TABLE_COMMENT");
                String columnName = rs.getString("COLUMN_NAME");
                String columnComment = rs.getString("COLUMN_COMMENT");
                String columnType = rs.getString("COLUMN_TYPE");
                
                tables.putIfAbsent(tableName, new TableInfo(tableName, tableComment));
                
                if (columnName != null) {
                    tables.get(tableName).addColumn(columnName, columnComment, columnType);
                }
            }
            
            // 获取主键信息
            Map<String, List<String>> primaryKeys = getPrimaryKeys(dbName, stmt);
            
            // 获取外键信息
            Map<String, List<ForeignKey>> foreignKeys = getForeignKeys(dbName, stmt);
            
            // 写入文件
            writeSchemaFile(schemaFile, dbName, tables, primaryKeys, foreignKeys);
            
            log.info("📝 已导出数据库 {} 的表结构到 {}", dbName, schemaFile);
            
        } catch (Exception e) {
            log.error("❌ 导出数据库 {} 结构时出错: {}", dbName, e.getMessage());
        }
    }

    /**
     * 选择候选表
     */
    public List<String> selectCandidateTables(
            Map<String, Map<String, List<String>>> databaseKeywords,
            List<String> selectedDatabases,
            int maxTables) {
        
        List<String> allCandidateTables = new ArrayList<>();
        
        for (String dbName : selectedDatabases) {
            Map<String, List<String>> keywords = databaseKeywords.getOrDefault(
                dbName, Map.of("keywords_cn", List.of(), "keywords_en", List.of())
            );
            
            List<String> dbTables = selectTablesForDatabase(dbName, keywords, maxTables);
            allCandidateTables.addAll(dbTables);
        }
        
        return allCandidateTables;
    }

    /**
     * 为单个数据库选择候选表
     */
    private List<String> selectTablesForDatabase(
            String dbName, 
            Map<String, List<String>> keywords, 
            int maxTables) {
        
        String schemaFile = properties.getFiles().getSchemaDir() + "/" + dbName + ".txt";
        
        if (!Files.exists(Paths.get(schemaFile))) {
            log.warn("⚠️ Schema 文件不存在: {}", schemaFile);
            return Collections.emptyList();
        }
        
        try {
            List<String> lines = Files.readAllLines(Paths.get(schemaFile));
            
            // 计算每个表的得分
            Map<String, Double> tableScores = new HashMap<>();
            List<String> processedKeywords = preprocessKeywords(keywords);
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\\|\\|");
                if (parts.length < 4) continue;
                
                String tableName = parts[0];
                double score = calculateTableScore(line, processedKeywords);
                
                if (score > 0) {
                    tableScores.put(line, score);
                }
            }
            
            // 按得分排序
            List<Map.Entry<String, Double>> sortedTables = tableScores.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            
            // 筛选高分表（分数 > 15）
            List<Map.Entry<String, Double>> highScoreTables = sortedTables.stream()
                .filter(entry -> entry.getValue() > 15)
                .collect(Collectors.toList());
            
            // 计算返回数量：高分表数量的 1/4，最少 5 个，最多 10 个
            int targetCount;
            if (!highScoreTables.isEmpty()) {
                targetCount = Math.max(5, Math.min(10, highScoreTables.size() / 4));
                log.info("📊 数据库 {} - 高分表(>15分): {}个, 返回: {}个", 
                    dbName, highScoreTables.size(), targetCount);
            } else {
                targetCount = Math.min(5, sortedTables.size());
                log.info("📊 数据库 {} - 无高分表，返回前{}个得分最高的表", dbName, targetCount);
            }
            
            return (highScoreTables.isEmpty() ? sortedTables : highScoreTables)
                .stream()
                .limit(targetCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
        } catch (IOException e) {
            log.error("❌ 读取 Schema 文件错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 预处理关键词
     */
    private List<String> preprocessKeywords(Map<String, List<String>> keywords) {
        List<String> processed = new ArrayList<>();
        
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(keywords.getOrDefault("keywords_cn", Collections.emptyList()));
        allKeywords.addAll(keywords.getOrDefault("keywords_en", Collections.emptyList()));
        
        for (String kw : allKeywords) {
            String kwLower = kw.toLowerCase();
            processed.add(kwLower);
            if (kwLower.length() > 3) {
                processed.add(kwLower.substring(0, kwLower.length() - 1));
            }
        }
        
        return processed.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 计算表得分
     */
    private double calculateTableScore(String tableLine, List<String> keywords) {
        String[] parts = tableLine.split("\\|\\|");
        List<String> allFields = Arrays.asList(parts);
        
        double score = 0.0;
        
        // 关键词完全匹配
        for (String kw : keywords) {
            for (String field : allFields) {
                if (field.toLowerCase().contains(kw.toLowerCase())) {
                    score += 3.0;
                }
            }
        }
        
        // 相似度匹配
        for (String kw : keywords) {
            for (String field : allFields) {
                double similarity = calculateSimilarity(kw.toLowerCase(), field.toLowerCase());
                if (similarity > 0.6) {
                    score += similarity * 2.0;
                }
            }
        }
        
        return score;
    }

    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    // 辅助方法
    
    private Map<String, List<String>> getPrimaryKeys(String dbName, Statement stmt) throws Exception {
        String sql = String.format("""
            SELECT tc.TABLE_NAME, kcu.COLUMN_NAME
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
            JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu 
                ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME 
                AND tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA
                AND tc.TABLE_NAME = kcu.TABLE_NAME
            WHERE tc.CONSTRAINT_TYPE = 'PRIMARY KEY' 
                AND tc.TABLE_SCHEMA = '%s'
            ORDER BY tc.TABLE_NAME, kcu.ORDINAL_POSITION
            """, dbName);
        
        ResultSet rs = stmt.executeQuery(sql);
        Map<String, List<String>> primaryKeys = new HashMap<>();
        
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String columnName = rs.getString("COLUMN_NAME");
            primaryKeys.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
        }
        
        return primaryKeys;
    }

    private Map<String, List<ForeignKey>> getForeignKeys(String dbName, Statement stmt) throws Exception {
        String sql = String.format("""
            SELECT DISTINCT
                kcu.TABLE_NAME,
                kcu.COLUMN_NAME,
                kcu.REFERENCED_TABLE_NAME,
                kcu.REFERENCED_COLUMN_NAME
            FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu
            WHERE kcu.REFERENCED_TABLE_NAME IS NOT NULL 
                AND kcu.TABLE_SCHEMA = '%s'
                AND kcu.REFERENCED_TABLE_SCHEMA = '%s'
            ORDER BY kcu.TABLE_NAME, kcu.COLUMN_NAME
            """, dbName, dbName);
        
        ResultSet rs = stmt.executeQuery(sql);
        Map<String, List<ForeignKey>> foreignKeys = new HashMap<>();
        
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            ForeignKey fk = new ForeignKey(
                rs.getString("COLUMN_NAME"),
                rs.getString("REFERENCED_TABLE_NAME"),
                rs.getString("REFERENCED_COLUMN_NAME")
            );
            foreignKeys.computeIfAbsent(tableName, k -> new ArrayList<>()).add(fk);
        }
        
        return foreignKeys;
    }

    private void writeSchemaFile(
            String schemaFile,
            String dbName,
            Map<String, TableInfo> tables,
            Map<String, List<String>> primaryKeys,
            Map<String, List<ForeignKey>> foreignKeys) throws IOException {
        
        StringBuilder content = new StringBuilder();
        
        for (TableInfo table : tables.values()) {
            String fullTableName = dbName + "." + table.name;
            String pkStr = String.join(",", primaryKeys.getOrDefault(table.name, Collections.emptyList()));
            
            List<String> fkStrs = new ArrayList<>();
            for (ForeignKey fk : foreignKeys.getOrDefault(table.name, Collections.emptyList())) {
                fkStrs.add(fk.column + "->" + fk.refTable + "." + fk.refColumn);
            }
            String fkStr = String.join(";", fkStrs);
            
            content.append(fullTableName)
                .append("||").append(table.comment != null ? table.comment : "无注释")
                .append("||PK:").append(pkStr)
                .append("||FK:").append(fkStr);
            
            for (ColumnInfo col : table.columns) {
                content.append("||").append(col.name)
                    .append("||").append(col.comment != null ? col.comment : "无注释")
                    .append("||").append(col.type);
            }
            
            content.append("\n");
        }
        
        Files.writeString(Paths.get(schemaFile), content.toString());
    }

    // 内部类
    
    private static class TableInfo {
        String name;
        String comment;
        List<ColumnInfo> columns = new ArrayList<>();
        
        TableInfo(String name, String comment) {
            this.name = name;
            this.comment = comment;
        }
        
        void addColumn(String name, String comment, String type) {
            columns.add(new ColumnInfo(name, comment, type));
        }
    }

    private static class ColumnInfo {
        String name;
        String comment;
        String type;
        
        ColumnInfo(String name, String comment, String type) {
            this.name = name;
            this.comment = comment;
            this.type = type;
        }
    }

    private static class ForeignKey {
        String column;
        String refTable;
        String refColumn;
        
        ForeignKey(String column, String refTable, String refColumn) {
            this.column = column;
            this.refTable = refTable;
            this.refColumn = refColumn;
        }
    }
}
