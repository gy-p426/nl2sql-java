package com.nl2sql.service;

import com.nl2sql.repository.DatabaseSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Qualifier;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema 管理服务 - 支持向量嵌入语义检索提升找表正确率
 */
@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    @Autowired
    private DatabaseService databaseService;
    
    @Autowired
    private DatabaseSchemaRepository schemaRepository;
    
    @Autowired
    private DataSource primaryDataSource;

    @Autowired
    private EmbeddingService embeddingService;

    @Value("${nl2sql.embedding.score-weight:10.0}")
    private double embeddingScoreWeight;

    /**
     * 导出所有数据库的 Schema - 基于 database_host_config 表
     */
    public void exportAllSchemas(boolean forceRefresh) {
        log.info("正在导出多数据库表结构...");
        
        List<String> databases = databaseService.getAllDatabases();
        
        if (databases.isEmpty()) {
            log.warn("⚠️ 未找到配置的数据库，跳过 Schema 导出");
            return;
        }
        
        for (String dbName : databases) {
            exportDatabaseSchema(dbName, forceRefresh);
        }
        
        log.info("✅ 数据库结构导出完成");
    }

    /**
     * 导出单个数据库的 Schema到数据库 - 使用原生SQL避免Lombok问题
     */
    @Transactional
    public void exportDatabaseSchema(String dbName, boolean forceRefresh) {
        exportDatabaseSchema(null, null, dbName, forceRefresh);
    }

    @Transactional
    public void exportDatabaseSchema(Integer ownerUserId, Integer hostConfigId, String dbName, boolean forceRefresh) {
        try {
            // 检查是否需要刷新
            if (!forceRefresh) {
                String countSql = ownerUserId == null
                    ? "SELECT COUNT(*) FROM database_schema WHERE database_name = ?"
                    : "SELECT COUNT(*) FROM database_schema WHERE owner_user_id = ? AND host_config_id = ? AND database_name = ?";

                try (Connection conn = primaryDataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(countSql)) {
                    if (ownerUserId == null) {
                        ps.setString(1, dbName);
                    } else {
                        ps.setInt(1, ownerUserId);
                        ps.setInt(2, hostConfigId);
                        ps.setString(3, dbName);
                    }
                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        log.debug("Schema 数据已存在，跳过: {}", dbName);
                        return;
                    }
                }
            }
            
            // 如果强制刷新，先删除旧数据
            if (forceRefresh) {
                try (Connection conn = primaryDataSource.getConnection()) {
                    String deleteColumnsSql = ownerUserId == null
                        ? "DELETE FROM table_columns WHERE database_name = ?"
                        : "DELETE FROM table_columns WHERE owner_user_id = ? AND host_config_id = ? AND database_name = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteColumnsSql)) {
                        if (ownerUserId == null) {
                            ps.setString(1, dbName);
                        } else {
                            ps.setInt(1, ownerUserId);
                            ps.setInt(2, hostConfigId);
                            ps.setString(3, dbName);
                        }
                        ps.executeUpdate();
                    }

                    String deleteSchemaSql = ownerUserId == null
                        ? "DELETE FROM database_schema WHERE database_name = ?"
                        : "DELETE FROM database_schema WHERE owner_user_id = ? AND host_config_id = ? AND database_name = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSchemaSql)) {
                        if (ownerUserId == null) {
                            ps.setString(1, dbName);
                        } else {
                            ps.setInt(1, ownerUserId);
                            ps.setInt(2, hostConfigId);
                            ps.setString(3, dbName);
                        }
                        ps.executeUpdate();
                    }
                    log.info("🗑️ 已删除数据库 {} 的旧Schema数据（owner={}, host={}）", dbName, ownerUserId, hostConfigId);
                }
            }
            
            // 导出Schema数据
            exportSchemaWithNativeSQL(ownerUserId, hostConfigId, dbName);
            
        } catch (Exception e) {
            log.error("❌ 导出数据库 {} 结构时出错: {}", dbName, e.getMessage(), e);
        }
    }
    
    /**
     * 使用原生SQL导出Schema数据，避免Lombok setter问题
     */
    private void exportSchemaWithNativeSQL(Integer ownerUserId, Integer hostConfigId, String dbName) throws Exception {
        try (Connection sourceConn = ownerUserId == null
                ? databaseService.getConnection(dbName)
                : databaseService.getConnection(ownerUserId, dbName);
             Connection targetConn = primaryDataSource.getConnection()) {
            
            // 获取主键信息
            Map<String, List<String>> primaryKeys = getPrimaryKeysMap(sourceConn, dbName);
            
            // 获取表和列信息
            String sql = String.format("""
                SELECT 
                    t.TABLE_NAME,
                    t.TABLE_COMMENT,
                    t.TABLE_ROWS,
                    c.COLUMN_NAME,
                    c.COLUMN_COMMENT,
                    c.COLUMN_TYPE,
                    c.DATA_TYPE,
                    c.IS_NULLABLE,
                    c.COLUMN_DEFAULT,
                    c.COLUMN_KEY,
                    c.ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.TABLES t
                LEFT JOIN INFORMATION_SCHEMA.COLUMNS c 
                    ON t.TABLE_NAME = c.TABLE_NAME 
                    AND t.TABLE_SCHEMA = c.TABLE_SCHEMA
                WHERE t.TABLE_SCHEMA = '%s'
                ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION
                """, dbName);
            
            try (Statement stmt = sourceConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                // 准备插入语句 - 使用RETURN_GENERATED_KEYS获取生成的ID
                String insertSchemaSql = """
                    INSERT INTO database_schema 
                    (owner_user_id, host_config_id, database_name, table_name, table_comment, primary_keys, table_rows, created_at, updated_at) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """;
                
                String insertColumnSql = """
                    INSERT INTO table_columns 
                    (schema_id, owner_user_id, host_config_id, database_name, table_name, column_name, column_type, data_type, column_comment, 
                     is_nullable, column_default, column_key, ordinal_position, created_at, updated_at) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """;
                
                try (PreparedStatement schemaPs = targetConn.prepareStatement(insertSchemaSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement columnPs = targetConn.prepareStatement(insertColumnSql)) {
                    
                    Map<String, Integer> tableSchemaIds = new HashMap<>(); // 存储表名到schema_id的映射
                    int tableCount = 0;
                    int columnCount = 0;
                    
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        
                        // 插入表信息（每个表只插入一次）
                        if (!tableSchemaIds.containsKey(tableName)) {
                            if (ownerUserId == null) {
                                schemaPs.setNull(1, java.sql.Types.INTEGER);
                                schemaPs.setNull(2, java.sql.Types.INTEGER);
                            } else {
                                schemaPs.setInt(1, ownerUserId);
                                schemaPs.setInt(2, hostConfigId);
                            }
                            schemaPs.setString(3, dbName);
                            schemaPs.setString(4, tableName);
                            schemaPs.setString(5, rs.getString("TABLE_COMMENT"));
                            
                            // 设置主键
                            List<String> pks = primaryKeys.get(tableName);
                            schemaPs.setString(6, pks != null ? String.join(",", pks) : null);
                            
                            schemaPs.setLong(7, rs.getLong("TABLE_ROWS"));
                            schemaPs.executeUpdate();
                            
                            // 获取生成的schema_id
                            try (ResultSet generatedKeys = schemaPs.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    int schemaId = generatedKeys.getInt(1);
                                    tableSchemaIds.put(tableName, schemaId);
                                }
                            }
                            
                            tableCount++;
                        }
                        
                        // 插入列信息
                        String columnName = rs.getString("COLUMN_NAME");
                        String columnComment = rs.getString("COLUMN_COMMENT");
                        
                        if (columnName != null) {
                            // 过滤逻辑：排除列注释为空或为"无用"的列，但保留"deleted"列
                            boolean shouldSkip = false;
                            if (!"deleted".equalsIgnoreCase(columnName)) {
                                if (columnComment == null || columnComment.trim().isEmpty() || "无用".equals(columnComment.trim())) {
                                    shouldSkip = true;
                                }
                            }
                            
                            if (!shouldSkip) {
                                Integer schemaId = tableSchemaIds.get(tableName);
                                if (schemaId != null) {
                                    columnPs.setInt(1, schemaId);  // schema_id
                                    if (ownerUserId == null) {
                                        columnPs.setNull(2, java.sql.Types.INTEGER);
                                        columnPs.setNull(3, java.sql.Types.INTEGER);
                                    } else {
                                        columnPs.setInt(2, ownerUserId);
                                        columnPs.setInt(3, hostConfigId);
                                    }
                                    columnPs.setString(4, dbName);
                                    columnPs.setString(5, tableName);
                                    columnPs.setString(6, columnName);
                                    columnPs.setString(7, rs.getString("COLUMN_TYPE"));
                                    columnPs.setString(8, rs.getString("DATA_TYPE"));
                                    columnPs.setString(9, columnComment);
                                    columnPs.setBoolean(10, "YES".equals(rs.getString("IS_NULLABLE")));
                                    columnPs.setString(11, rs.getString("COLUMN_DEFAULT"));
                                    columnPs.setString(12, rs.getString("COLUMN_KEY"));
                                    columnPs.setInt(13, rs.getInt("ORDINAL_POSITION"));
                                    columnPs.executeUpdate();
                                    
                                    columnCount++;
                                }
                            }
                        }
                    }
                    
                    log.info("📝 已导出数据库 {} 的表结构: {} 个表, {} 个列", dbName, tableCount, columnCount);
                }
            }
        }
    }

    /**
     * 选择候选表（用于多数据库场景）
     */
    public List<String> selectCandidateTablesForDatabases(
            Map<String, Map<String, List<String>>> databaseKeywords,
            List<String> selectedDatabases) {
        return selectCandidateTablesForDatabases(databaseKeywords, selectedDatabases, null);
    }

    public List<String> selectCandidateTablesForDatabases(
            Map<String, Map<String, List<String>>> databaseKeywords,
            List<String> selectedDatabases,
            Integer userId) {
        
        List<String> allCandidateTables = new ArrayList<>();
        
        for (String dbName : selectedDatabases) {
            Map<String, List<String>> keywords = databaseKeywords.getOrDefault(
//                dbName, Map.of("keywords_cn", List.of(), "keywords_en", List.of())
                    dbName, Map.of("keywords_cn", List.of())
            );
            
            List<String> dbTables = selectTablesForDatabase(dbName, keywords, 10, userId);
            allCandidateTables.addAll(dbTables);
        }
        
        return allCandidateTables;
    }
    
    /**
     * 选择候选表（带maxTables参数）
     */
    public List<String> selectCandidateTables(
            Map<String, Map<String, List<String>>> databaseKeywords,
            List<String> selectedDatabases,
            int maxTables) {
        return selectCandidateTables(databaseKeywords, selectedDatabases, maxTables, null);
    }

    public List<String> selectCandidateTables(
            Map<String, Map<String, List<String>>> databaseKeywords,
            List<String> selectedDatabases,
            int maxTables,
            Integer userId) {
        
        List<String> allCandidateTables = new ArrayList<>();
        
        for (String dbName : selectedDatabases) {
            Map<String, List<String>> keywords = databaseKeywords.getOrDefault(
//                dbName, Map.of("keywords_cn", List.of(), "keywords_en", List.of())
                    dbName, Map.of("keywords_cn", List.of())
            );
            
            List<String> dbTables = selectTablesForDatabase(dbName, keywords, maxTables, userId);
            allCandidateTables.addAll(dbTables);
        }
        
        return allCandidateTables;
    }

    /**
     * 为单个数据库选择候选表 - 集成向量嵌入语义检索
     */
    private List<String> selectTablesForDatabase(
            String dbName, 
            Map<String, List<String>> keywords, 
            int maxTables,
            Integer userId) {
        
        try {
            // 直接从数据库构建表信息字符串，避免复杂的实体关系
            List<String> tableLines = buildTableLinesDirectly(dbName, userId);
            
            if (tableLines.isEmpty()) {
                log.warn("⚠️ 数据库 {} 的Schema数据不存在", dbName);
                return Collections.emptyList();
            }
            
            // 计算每个表的得分
            Map<String, Double> tableScores = new HashMap<>();
            List<String> processedKeywords = preprocessKeywords(keywords);
            
            // 构建查询文本用于向量检索（将关键词拼接为一段描述文本）
            String queryText = String.join(" ", processedKeywords);
            
            // 获取查询文本的向量
            double[] queryEmbedding = null;
            Map<String, double[]> tableEmbeddingsMap = Collections.emptyMap();
            if (embeddingService.isAvailable() && !queryText.isBlank()) {
                queryEmbedding = embeddingService.getEmbedding(queryText);

                // 批量获取所有表描述的向量
                List<String> tableDescriptions = new ArrayList<>();
                Map<String, String> tableLineToDesc = new LinkedHashMap<>();
                for (String tableLine : tableLines) {
                    String desc = buildTableDescription(tableLine);
                    tableDescriptions.add(desc);
                    tableLineToDesc.put(tableLine, desc);
                }
                tableEmbeddingsMap = embeddingService.getEmbeddings(tableDescriptions);

                // 建立 tableLine -> embedding 映射
                Map<String, double[]> lineEmbeddings = new HashMap<>();
                for (Map.Entry<String, String> entry : tableLineToDesc.entrySet()) {
                    double[] emb = tableEmbeddingsMap.get(entry.getValue());
                    if (emb != null) {
                        lineEmbeddings.put(entry.getKey(), emb);
                    }
                }
                tableEmbeddingsMap = lineEmbeddings;
            }
            
            for (String tableLine : tableLines) {
                // 关键词文本匹配得分
                double keywordScore = calculateKeywordScore(tableLine, processedKeywords);
                
                // 向量语义相似度得分
                double vectorScore = 0.0;
                if (queryEmbedding != null) {
                    double[] tableEmb = tableEmbeddingsMap.get(tableLine);
                    if (tableEmb != null) {
                        double similarity = embeddingService.cosineSimilarity(queryEmbedding, tableEmb);
                        // 余弦相似度范围 [-1, 1]，取正值部分乘以权重
                        vectorScore = Math.max(0, similarity) * embeddingScoreWeight;
                    }
                }
                
                double totalScore = keywordScore + vectorScore;
                if (totalScore > 0) {
                    tableScores.put(tableLine, totalScore);
                }
                
                if (vectorScore > 0) {
                    // 提取表名（格式：dbName.tableName||...）
                    int separatorIdx = tableLine.indexOf("||");
                    String tableName = separatorIdx > 0 ? tableLine.substring(0, separatorIdx) : tableLine;
                    log.debug("📊 表 {} - 关键词得分: {}, 向量得分: {}, 总分: {}",
                            tableName, String.format("%.1f", keywordScore),
                            String.format("%.1f", vectorScore), String.format("%.1f", totalScore));
                }
            }
            
            // 按得分排序
            List<Map.Entry<String, Double>> sortedTables = tableScores.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            
            // 筛选高分表（分数 > 15）
            List<Map.Entry<String, Double>> highScorTables = sortedTables.stream()
                .filter(entry -> entry.getValue() > 15)
                .collect(Collectors.toList());
            
            // 计算返回数量：高分表数量的 1/4，最少 5 个，最多 10 个
            int targetCount;
            if (!highScorTables.isEmpty()) {
                targetCount = Math.max(5, Math.min(10, highScorTables.size() / 4));
                log.info("📊 数据库 {} - 高分表(>15分): {}个, 返回: {}个", dbName, highScorTables.size(), targetCount);
            } else {
                targetCount = Math.min(5, sortedTables.size());
                log.info("📊 数据库 {} - 无高分表，返回前{}个得分最高的表", dbName, targetCount);
            }
            
            return (highScorTables.isEmpty() ? sortedTables : highScorTables)
                .stream()
                .limit(targetCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("❌ 读取 Schema 数据错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 直接从数据库构建表信息字符串列表 - 修复ResultSet关闭问题
     */
    private List<String> buildTableLinesDirectly(String dbName, Integer userId) {
        List<String> tableLines = new ArrayList<>();
        
        try (Connection conn = userId == null
                ? databaseService.getConnection(dbName)
                : databaseService.getConnection(userId, dbName)) {
            
            // 分别获取不同的信息，避免ResultSet冲突
            Map<String, String> tableComments = getTableComments(conn, dbName);
            Map<String, List<String>> primaryKeys = getPrimaryKeysMap(conn, dbName);
            Map<String, List<String>> columnInfo = getColumnInfo(conn, dbName);
            
            // 构建表信息字符串
            for (String tableName : tableComments.keySet()) {
                StringBuilder line = new StringBuilder();
                
                // 表名
                line.append(dbName).append(".").append(tableName);
                line.append("||");
                
                // 表注释
                line.append(tableComments.getOrDefault(tableName, "无注释"));
                line.append("||");
                
                // 主键
                List<String> pks = primaryKeys.get(tableName);
                line.append("PK:").append(pks != null ? String.join(",", pks) : "");
                line.append("||");
                
                // 外键 - 简化处理
                line.append("FK:");
                line.append("||");
                
                // 列信息
                List<String> columns = columnInfo.get(tableName);
                if (columns != null) {
                    for (String columnStr : columns) {
                        line.append(columnStr);
                    }
                }
                
                tableLines.add(line.toString());
            }
            
        } catch (Exception e) {
            System.err.println("❌ 构建表信息字符串时出错: " + e.getMessage());
        }
        
        return tableLines;
    }

    /**
     * 获取表注释信息
     */
    private Map<String, String> getTableComments(Connection conn, String dbName) throws Exception {
        Map<String, String> tableComments = new HashMap<>();
        
        try (Statement stmt = conn.createStatement()) {
            String sql = String.format("""
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM INFORMATION_SCHEMA.TABLES 
                WHERE TABLE_SCHEMA = '%s'
                """, dbName);
            
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                tableComments.put(rs.getString("TABLE_NAME"), rs.getString("TABLE_COMMENT"));
            }
        }
        
        return tableComments;
    }

    /**
     * 获取主键信息 - 重载方法支持传入Connection
     */
    private Map<String, List<String>> getPrimaryKeysMap(Connection conn, String dbName) throws Exception {
        Map<String, List<String>> primaryKeys = new HashMap<>();
        
        try (Statement stmt = conn.createStatement()) {
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
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                primaryKeys.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
            }
        }
        
        return primaryKeys;
    }

    /**
     * 获取列信息
     */
    private Map<String, List<String>> getColumnInfo(Connection conn, String dbName) throws Exception {
        Map<String, List<String>> columnInfo = new HashMap<>();
        
        try (Statement stmt = conn.createStatement()) {
            String sql = String.format("""
                SELECT 
                    TABLE_NAME,
                    COLUMN_NAME,
                    COLUMN_COMMENT,
                    COLUMN_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_SCHEMA = '%s'
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """, dbName);
            
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                String columnComment = rs.getString("COLUMN_COMMENT");
                
                // 过滤逻辑：排除列注释为空或为"无用"的列，但保留"deleted"列
                boolean shouldSkip = false;
                if (!"deleted".equalsIgnoreCase(columnName)) {
                    if (columnComment == null || columnComment.trim().isEmpty() || "无用".equals(columnComment.trim())) {
                        shouldSkip = true;
                    }
                }
                
                if (!shouldSkip) {
                    String columnStr = "||" + columnName + 
                                     "||" + (columnComment != null ? columnComment : "无注释") +
                                     "||" + (rs.getString("COLUMN_TYPE") != null ? rs.getString("COLUMN_TYPE") : "");
                    
                    columnInfo.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnStr);
                }
            }
        }
        
        return columnInfo;
    }



    /**
     * 预处理关键词，删除了英文关键词
     * 该方法接收一个包含中英文关键词的Map，处理后返回处理过的关键词列表
     * 处理过程包括：
     * 1. 提取中文关键词
     * 2. 去重后返回结果列表
     *
     * @param keywords 包含中英文关键词的Map，键为"keywords_cn"
     * @return 处理后的关键词列表，已转换为小写并去重
     */
    private List<String> preprocessKeywords(Map<String, List<String>> keywords) {
        List<String> processed = new ArrayList<>();
        
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(keywords.getOrDefault("keywords_cn", Collections.emptyList()));
//        allKeywords.addAll(keywords.getOrDefault("keywords_en", Collections.emptyList()));
        
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
     * 构建表的文本描述用于向量嵌入
     * 将表名、注释、列名和列注释组合成自然语言描述
     */
    private String buildTableDescription(String tableLine) {
        String[] parts = tableLine.split("\\|\\|");
        StringBuilder desc = new StringBuilder();

        // 表名（database.tableName）
        if (parts.length > 0) {
            desc.append(parts[0]);
        }
        // 表注释
        if (parts.length > 1 && !"无注释".equals(parts[1])) {
            desc.append(" ").append(parts[1]);
        }
        // 列名和列注释（跳过PK/FK字段，每对列名+注释）
        // 格式: tableName||tableComment||PK:xxx||FK:||col1||comment1||type1||col2||comment2||type2
        for (int i = 4; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.isEmpty() && !part.startsWith("PK:") && !part.startsWith("FK:")) {
                desc.append(" ").append(part);
            }
        }

        return desc.toString();
    }

    /**
     * 计算表的关键词文本匹配得分
     */
    private double calculateKeywordScore(String tableLine, List<String> keywords) {
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
}