package com.nl2sql.service;

import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.model.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * NL2SQL 核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NL2SQLService {

    private final VolcanoEngineClient volcanoEngineClient;
    private final DatabaseService databaseService;
    private final SessionService sessionService;
    private final KeywordExtractorService keywordExtractorService;
    private final SchemaService schemaService;
    private final SQLGeneratorService sqlGeneratorService;
    private final CacheService cacheService;

    /**
     * 处理查询
     */
    public QueryResponse processQuery(String question, String windowId, String sessionId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 保存问题到 session
            String newSessionId = sessionService.saveQuestionToSession(question, windowId);
            
            // 2. 判断是否为连续问题
            Map<String, Object> continuousResult = mergeContinuousQuestion(
                question, windowId, sessionId
            );
            
            boolean isContinuous = (boolean) continuousResult.getOrDefault("is_continuous", false);
            String mergedQuestion = (String) continuousResult.getOrDefault("merged_question", question);
            
            log.info("{} 问题类型: {}", 
                isContinuous ? "🔗" : "🆕", 
                isContinuous ? "连续问题" : "独立问题");
            
            // 3. 选择数据库
            List<String> selectedDatabases = selectDatabases(mergedQuestion);
            log.info("📊 选择数据库: {}", selectedDatabases);
            
            // 4. 提取关键词
            Map<String, Map<String, List<String>>> databaseKeywords = 
                keywordExtractorService.extractKeywords(mergedQuestion, selectedDatabases);
            
            // 5. 选择候选表
            log.info("🔍 步骤5: 选择候选表");
            List<String> candidateTables = schemaService.selectCandidateTables(
                databaseKeywords, selectedDatabases, 20
            );
            
            if (candidateTables.isEmpty()) {
                return QueryResponse.builder()
                    .success(false)
                    .error("未找到相关表结构")
                    .selectedDatabases(selectedDatabases)
                    .keywords(flattenKeywords(databaseKeywords))
                    .build();
            }
            
            // 6. 生成 SQL
            log.info("🔍 步骤6: 生成SQL");
            Map<String, List<String>> mergedKeywords = flattenKeywords(databaseKeywords);
            List<String> generatedSqls = sqlGeneratorService.generateSQL(
                mergedQuestion, candidateTables, mergedKeywords
            );
            
            if (generatedSqls.isEmpty()) {
                return QueryResponse.builder()
                    .success(false)
                    .error("未能生成有效的SQL语句")
                    .selectedDatabases(selectedDatabases)
                    .keywords(mergedKeywords)
                    .build();
            }
            
            // 7. 执行 SQL
            log.info("🔍 步骤7: 执行SQL");
            String successfulSql = null;
            List<Map<String, Object>> results = Collections.emptyList();
            
            // 执行第一个成功的 SQL
            for (int i = 0; i < generatedSqls.size(); i++) {
                String sql = generatedSqls.get(i);
                try {
                    results = databaseService.executeQuery(sql, 100);
                    successfulSql = sql;
                    if (!results.isEmpty()) {
                        log.info("✅ 找到第一个执行成功的SQL (第{}个)", i + 1);
                        break;
                    }
                } catch (Exception e) {
                    log.warn("⚠️ SQL执行失败 (第{}个): {}", i + 1, e.getMessage());
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return QueryResponse.builder()
                .success(true)
                .sessionId(newSessionId)
                .question(question)
                .mergedQuestion(mergedQuestion)
                .isContinuous(isContinuous)
                .selectedDatabases(selectedDatabases)
                .keywords(mergedKeywords)
                .sql(successfulSql)
                .results(results)
                .resultCount(results.size())
                .executionTime(executionTime)
                .build();
                
        } catch (Exception e) {
            log.error("❌ 查询处理错误: {}", e.getMessage(), e);
            return QueryResponse.builder()
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }

    /**
     * 合并连续问题
     */
    private Map<String, Object> mergeContinuousQuestion(
            String newQuestion, String windowId, String sessionId) {
        
        String previousQuestion = null;
        
        if (sessionId != null) {
            previousQuestion = sessionService.getSessionQuestion(sessionId, windowId);
        } else {
            previousQuestion = sessionService.getLatestQuestionFromWindow(windowId);
        }
        
        if (previousQuestion == null) {
            log.info("📝 窗口 {} 无历史问题，直接使用新问题", windowId);
            return Map.of(
                "is_continuous", false,
                "merged_question", newQuestion,
                "original_question", newQuestion
            );
        }
        
        log.info("🔍 窗口 {} 检测连续问题 - 上一问: {}..., 新问题: {}...", 
            windowId, 
            previousQuestion.substring(0, Math.min(50, previousQuestion.length())),
            newQuestion.substring(0, Math.min(50, newQuestion.length())));
        
        // 使用 AI 判断是否连续
        String prompt = buildContinuousQuestionPrompt(previousQuestion, newQuestion);
        Map<String, Object> response = volcanoEngineClient.generate(prompt, 0.1);
        
        if (response.containsKey("error")) {
            log.warn("⚠️ 连续问题判断失败，默认为非连续问题");
            return Map.of(
                "is_continuous", false,
                "merged_question", newQuestion,
                "original_question", newQuestion,
                "previous_question", previousQuestion
            );
        }
        
        // 解析 JSON 响应
        String rawResponse = (String) response.get("response");
        Map<String, Object> parsed = com.nl2sql.util.JsonParser.extractJsonFromResponse(rawResponse);
        
        if (parsed != null && parsed.containsKey("is_continuous")) {
            boolean isContinuous = (Boolean) parsed.getOrDefault("is_continuous", false);
            String mergedQuestion = (String) parsed.getOrDefault("merged_question", newQuestion);
            String reason = (String) parsed.getOrDefault("reason", "");
            
            log.info("{} AI判断结果: {} - {}", 
                isContinuous ? "🔗" : "🆕",
                isContinuous ? "连续问题" : "独立问题",
                reason);
            log.info("📝 {}问题: {}", isContinuous ? "合并后" : "最终", mergedQuestion);
            
            return Map.of(
                "is_continuous", isContinuous,
                "merged_question", mergedQuestion,
                "original_question", newQuestion,
                "previous_question", previousQuestion,
                "reason", reason
            );
        }
        
        log.warn("⚠️ 无法解析JSON响应，默认为非连续问题");
        return Map.of(
            "is_continuous", false,
            "merged_question", newQuestion,
            "original_question", newQuestion,
            "previous_question", previousQuestion
        );
    }

    /**
     * 选择数据库
     */
    private List<String> selectDatabases(String question) {
        // TODO: 实现数据库选择逻辑
        // 当前简化版：返回所有数据库
        List<String> allDatabases = databaseService.getAllDatabases();
        log.info("📊 选择数据库（简化版）: {}", allDatabases);
        return allDatabases;
    }

    private String buildContinuousQuestionPrompt(String previous, String current) {
        return String.format("""
            你是一个智能问题分析助手。请判断新问题是否是对上一个问题的追问（连续问题）。
            
            【上一个问题】
            %s
            
            【新问题】
            %s
            
            【判断规则 - 连续问题的特征】
            1. 新问题包含指代词（"他们"、"这些"、"其中"、"那些"、"以上"等）
            2. 新问题缺少明确的查询主体（人名、部门名、表名等）
            3. 新问题是对上一个问题结果的进一步筛选、统计或查询
            
            【判断规则 - 非连续问题的特征（优先级更高）】
            ⚠️ 以下情况必须判定为非连续问题：
            1. 新问题已经包含明确的查询主体（人名、部门名、组织名等）
            2. 新问题的查询目标与上一问不同或更具体
            3. 新问题是对上一问的缩小范围或细化，而不是追问
            4. 新问题与上一问高度相似（相似度>70%%），可能是重复或细化问题
            5. 新问题的长度和完整性与上一问相当，且不以指代词开头
            
            【输出格式】
            严格按照以下JSON格式输出：
            {
                "is_continuous": true/false,
                "merged_question": "合并后的独立问题（如果是连续问题）或新问题（如果不是连续问题）",
                "reason": "判断理由"
            }
            
            请仔细分析并输出：
            """, previous, current);
    }

    private Map<String, List<String>> flattenKeywords(
            Map<String, Map<String, List<String>>> databaseKeywords) {
        
        Map<String, List<String>> flattened = new HashMap<>();
        databaseKeywords.values().forEach(keywords -> {
            keywords.forEach((key, value) -> {
                flattened.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value);
            });
        });
        return flattened;
    }
}
