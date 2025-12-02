package com.nl2sql.service;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.util.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词提取服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordExtractorService {

    private final VolcanoEngineClient volcanoEngineClient;

    /**
     * 提取关键词（改进版）
     */
    public Map<String, Map<String, List<String>>> extractKeywords(
            String question, 
            List<String> selectedDatabases) {
        
        Map<String, Map<String, List<String>>> databaseKeywords = new HashMap<>();
        
        for (String dbName : selectedDatabases) {
            Map<String, List<String>> keywords = extractKeywordsForDatabase(question, dbName);
            databaseKeywords.put(dbName, keywords);
        }
        
        return databaseKeywords;
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
        Map<String, Object> parsed = JsonParser.extractJsonFromResponse(responseText);
        
        if (parsed != null) {
            return convertToKeywordMap(parsed);
        }
        
        return extractKeywordsFallback(question);
    }

    /**
     * 构建关键词提取提示词
     */
    private String buildKeywordExtractionPrompt(String question, String dbName) {
        return String.format("""
            你是一个数据库查询助手。请从用户问题中提取关键词，用于匹配数据库表名和列名。
            
            【用户问题】
            %s
            
            【目标数据库】
            %s
            
            【提取规则】
            1. 提取中文关键词（keywords_cn）：人名、部门名、组织名、业务术语等
            2. 提取英文关键词（keywords_en）：可能的表名、字段名（如 employee, salary, department）
            3. 关键词应该简洁、准确，避免冗余
            4. 每类关键词不超过10个
            
            【输出格式】
            严格按照以下JSON格式输出：
            {
                "keywords_cn": ["关键词1", "关键词2"],
                "keywords_en": ["keyword1", "keyword2"]
            }
            
            请输出：
            """, question, dbName);
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
}
