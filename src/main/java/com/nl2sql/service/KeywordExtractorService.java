package com.nl2sql.service;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.util.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词提取服务
 */
@Service
public class KeywordExtractorService {

    private static final Logger log = LoggerFactory.getLogger(KeywordExtractorService.class);

    private final VolcanoEngineClient volcanoEngineClient;
    private final JsonParser jsonParser;
    private final DatabaseService databaseService;
    
    // 数据库关键词缓存
    private final Map<String, Map<String, List<String>>> databaseKeywords = new HashMap<>();
    
    public KeywordExtractorService(VolcanoEngineClient volcanoEngineClient, 
                                 JsonParser jsonParser, 
                                 DatabaseService databaseService) {
        this.volcanoEngineClient = volcanoEngineClient;
        this.jsonParser = jsonParser;
        this.databaseService = databaseService;
    }

    /**
     * 提取关键词（改进版）
     */
    public Map<String, Map<String, List<String>>> extractKeywords(
            String question, 
            List<String> selectedDatabases) {
        
        Map<String, Map<String, List<String>>> result = new HashMap<>();
        
        for (String dbName : selectedDatabases) {
            Map<String, List<String>> keywords = extractKeywordsForDatabase(question, dbName);
            result.put(dbName, keywords);
        }
        
        return result;
    }

    /**
     * 为多个数据库提取关键词 - 完全按照Python实现
     */
    public Map<String, Map<String, List<String>>> extractKeywordsForDatabases(
            String question, 
            List<String> selectedDatabases) {
        return extractKeywords(question, selectedDatabases);
    }

    /**
     * 为单个数据库提取关键词
     */
    private Map<String, List<String>> extractKeywordsForDatabase(String question, String dbName) {
        String prompt = buildKeywordExtractionPrompt(question, dbName);
        
        Map<String, Object> response = volcanoEngineClient.generate(prompt, 0.1);
        
        if (response.containsKey("error")) {
            log.warn("⚠️ AI关键词提取失败，使用回退方法");
            return extractKeywordsFallback(question);
        }
        
        String responseText = (String) response.get("response");
        Map<String, Object> parsed = jsonParser.extractJsonFromResponse(responseText);
        
        if (parsed != null) {
            return convertToKeywordMap(parsed);
        }
        
        return extractKeywordsFallback(question);
    }

    /**
     * 构建关键词提取提示词 - 参考sql-b-v4.py的_build_database_specific_prompt_with_context方法
     */
    private String buildKeywordExtractionPrompt(String question, String dbName) {
        // 懒加载数据库关键词
        if (databaseKeywords.isEmpty()) {
            initializeDatabaseKeywords();
        }
        
        Map<String, List<String>> dbKeywords = databaseKeywords.get(dbName);
        if (dbKeywords == null) {
            // 如果没有找到数据库关键词，使用简化版提示词
            return buildSimplePrompt(question, dbName);
        }
        
        List<String> keywordsCn = dbKeywords.get("keywords_cn");
        List<String> keywordsEn = dbKeywords.get("keywords_en");
        
        // 限制显示的关键词数量，避免提示词过长
        int maxDisplay = 1000;
        List<String> chineseDisplay = keywordsCn.subList(0, Math.min(keywordsCn.size(), maxDisplay));
        List<String> englishDisplay = keywordsEn.subList(0, Math.min(keywordsEn.size(), maxDisplay));
        
        return String.format("""
            请分析问题，从数据库%s的关键词库中提取与问题相关的关键词：

            [数据库%s中文关键词库]
            %s

            [数据库%s英文关键词库]
            %s

            [输入参数]
            - question: %s
            - selected_databases: [%s]

            [输出格式]
            严格按照JSON格式输出：
            {"keywords_cn": ["关键词1", "关键词2"], "keywords_en": ["keyword1", "keyword2"]}

            [问题]
            %s
            """, 
            dbName, 
            dbName, 
            String.join(", ", chineseDisplay),
            dbName,
            String.join(", ", englishDisplay),
            question,
            dbName,
            question);
    }
    
    /**
     * 构建简化版提示词（当没有数据库关键词时使用）
     */
    private String buildSimplePrompt(String question, String dbName) {
        return String.format("""
            请分析问题并提取关键词：

            [输入参数]
            - question: %s
            - selected_databases: [%s]

            [输出格式]
            严格按照JSON格式输出：
            {"keywords_cn": ["关键词1", "关键词2"], "keywords_en": ["keyword1", "keyword2"]}

            [问题]
            %s
            """, question, dbName, question);
    }

    /**
     * 回退方法：使用 HanLP 分词
     */
    private Map<String, List<String>> extractKeywordsFallback(String question) {
        List<String> keywordsCn = new ArrayList<>();
        List<String> keywordsEn = new ArrayList<>();
        
        // 使用 HanLP 分词
        List<Term> terms = HanLP.segment(question);
        for (Term term : terms) {
            String word = term.word;
            String nature = term.nature.toString();
            
            // 提取名词、动词等
            if (nature.startsWith("n") || nature.startsWith("v")) {
                if (word.length() >= 2) {
                    keywordsCn.add(word);
                }
            }
        }
        
        // 提取英文单词
        Pattern pattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
        Matcher matcher = pattern.matcher(question);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 3) {
                keywordsEn.add(word.toLowerCase());
            }
        }
        
        Map<String, List<String>> keywords = new HashMap<>();
        keywords.put("keywords_cn", keywordsCn.stream().distinct().limit(10).toList());
        keywords.put("keywords_en", keywordsEn.stream().distinct().limit(10).toList());
        
        return keywords;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> convertToKeywordMap(Map<String, Object> parsed) {
        Map<String, List<String>> result = new HashMap<>();
        
        Object cnObj = parsed.get("keywords_cn");
        Object enObj = parsed.get("keywords_en");
        
        result.put("keywords_cn", cnObj instanceof List ? (List<String>) cnObj : Collections.emptyList());
        result.put("keywords_en", enObj instanceof List ? (List<String>) enObj : Collections.emptyList());
        
        return result;
    }
    
    /**
     * 初始化数据库关键词 - 参考sql-b-v4.py的_extract_keywords_by_database方法
     */
    private void initializeDatabaseKeywords() {
        log.info("🔄 开始初始化数据库关键词库...");
        
        List<String> allDatabases = databaseService.getAllDatabases();
        
        for (String dbName : allDatabases) {
            try {
                Map<String, List<String>> keywords = extractKeywordsFromDatabase(dbName);
                databaseKeywords.put(dbName, keywords);
                
                int totalKeywords = keywords.get("keywords_cn").size() + keywords.get("keywords_en").size();
                log.info("✅ 数据库 {} 关键词提取完成，共 {} 个关键词", dbName, totalKeywords);
                
            } catch (Exception e) {
                log.error("❌ 提取数据库 {} 关键词时出错: {}", dbName, e.getMessage());
                // 设置空的关键词列表
                Map<String, List<String>> emptyKeywords = new HashMap<>();
                emptyKeywords.put("keywords_cn", new ArrayList<>());
                emptyKeywords.put("keywords_en", new ArrayList<>());
                databaseKeywords.put(dbName, emptyKeywords);
            }
        }
        
        log.info("🎉 数据库关键词库初始化完成，共处理 {} 个数据库", allDatabases.size());
    }
    
    /**
     * 从数据库中提取关键词（表名、表注释、列名、列注释）
     */
    private Map<String, List<String>> extractKeywordsFromDatabase(String dbName) throws SQLException {
        Set<String> chineseKeywords = new HashSet<>();
        Set<String> englishKeywords = new HashSet<>();
        
        try (Connection conn = databaseService.getConnection(dbName);
             Statement stmt = conn.createStatement()) {
            
            // 获取表信息和列信息
            String sql = String.format("""
                SELECT 
                    t.TABLE_NAME,
                    t.TABLE_COMMENT,
                    c.COLUMN_NAME,
                    c.COLUMN_COMMENT
                FROM INFORMATION_SCHEMA.TABLES t
                LEFT JOIN INFORMATION_SCHEMA.COLUMNS c ON t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_SCHEMA = c.TABLE_SCHEMA
                WHERE t.TABLE_SCHEMA = '%s'
                ORDER BY t.TABLE_NAME, c.ORDINAL_POSITION
                """, dbName);
            
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableComment = rs.getString("TABLE_COMMENT");
                    String columnName = rs.getString("COLUMN_NAME");
                    String columnComment = rs.getString("COLUMN_COMMENT");
                    
                    // 添加表名作为英文关键词
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        englishKeywords.add(tableName);
                    }
                    
                    // 添加表注释作为中文关键词
                    if (tableComment != null && !tableComment.trim().isEmpty() && !"无注释".equals(tableComment)) {
                        chineseKeywords.add(tableComment);
                    }
                    
                    // 添加列名作为英文关键词
                    if (columnName != null && !columnName.trim().isEmpty()) {
                        englishKeywords.add(columnName);
                    }
                    
                    // 添加列注释作为中文关键词
                    if (columnComment != null && !columnComment.trim().isEmpty() && !"无注释".equals(columnComment)) {
                        chineseKeywords.add(columnComment);
                    }
                }
            }
        }
        
        Map<String, List<String>> keywords = new HashMap<>();
        keywords.put("keywords_cn", new ArrayList<>(chineseKeywords));
        keywords.put("keywords_en", new ArrayList<>(englishKeywords));
        
        return keywords;
    }
    
    /**
     * 清除数据库关键词缓存（用于重新加载）
     */
    public void clearDatabaseKeywordsCache() {
        databaseKeywords.clear();
        log.info("🧹 已清除数据库关键词缓存");
    }
}
