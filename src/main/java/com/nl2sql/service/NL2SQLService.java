package com.nl2sql.service;

import com.nl2sql.client.OllamaClient;
import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.model.dto.QueryResponse;
import com.nl2sql.model.entity.DatabaseOverview;
import com.nl2sql.repository.DatabaseOverviewRepository;
import com.nl2sql.util.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NL2SQL 核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NL2SQLService {

    private final VolcanoEngineClient volcanoEngineClient;
    private final OllamaClient ollamaClient;
    private final DatabaseService databaseService;
    private final SessionService sessionService;
    private final KeywordExtractorService keywordExtractorService;
    private final SchemaService schemaService;
    private final SQLGeneratorService sqlGeneratorService;
    private final CacheService cacheService;
    private final DatabaseOverviewRepository databaseOverviewRepository;
    private final JsonParser jsonParser;
    private final DynamicConfigProvider configProvider;
    private final LLMRouter llmRouter;

    /**
     * 处理查询
     */
        public QueryResponse processQuery(String question, String windowId, String sessionId, Integer userId) {

            if(userId == null) {
                return QueryResponse.builder()
                        .success(false)
                        .error("用户账号信息为空")
                        .build();
            }

        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 保存问题到 session
            String newSessionId = sessionService.saveQuestionToSession(question, windowId, userId);
            
            // 2. 判断是否为连续问题
            Map<String, Object> continuousResult = mergeContinuousQuestion(
                question, windowId, userId, sessionId
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
                databaseKeywords, selectedDatabases, 10
            );
            
            if (candidateTables.isEmpty()) {
                return QueryResponse.builder()
                    .success(false)
                    .error("未找到相关表结构")
                    .selectedDatabases(selectedDatabases)
                    .keywords(flattenKeywords(databaseKeywords))
                    .build();
            }
            
            // 6. 生成 SQL（包含解释）
            log.info("🔍 步骤6: 生成SQL");
            Map<String, List<String>> mergedKeywords = flattenKeywords(databaseKeywords);
            com.nl2sql.model.dto.SQLResult sqlResult = sqlGeneratorService.generateSQLWithExplanation(
                mergedQuestion, candidateTables, mergedKeywords
            );
            
            if (sqlResult == null || sqlResult.getSql() == null) {
                return QueryResponse.builder()
                    .success(false)
                    .error("未能生成有效的SQL语句")
                    .selectedDatabases(selectedDatabases)
                    .keywords(mergedKeywords)
                    .build();
            }
            
            // 7. 执行 SQL
            log.info("🔍 步骤7: 执行SQL");
            List<Map<String, Object>> results = Collections.emptyList();
            
            try {
                results = databaseService.executeQuery(sqlResult.getSql(), 100);
                log.info("✅ SQL执行成功，返回 {} 行", results.size());
            } catch (Exception e) {
                log.warn("⚠️ SQL执行失败: {}", e.getMessage());
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
                .sql(sqlResult.getSql())
                .sqlExplanation(sqlResult.getExplanation())
                .model(sqlResult.getModel())
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
    public Map<String, Object> mergeContinuousQuestion(
            String newQuestion, String windowId, Integer userId, String sessionId) {
        
        String previousQuestion = null;
        boolean isSessionReferenced = false;
        
        if (sessionId != null) {
            previousQuestion = sessionService.getSessionQuestion(sessionId, windowId);
            isSessionReferenced = true;
        } else {
            previousQuestion = sessionService.getLatestQuestionFromWindow(windowId, userId);
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
        String prompt = buildContinuousQuestionPrompt(previousQuestion, newQuestion, isSessionReferenced);
        Map<String, Object> response = llmRouter.route(
            com.nl2sql.enums.LLMTaskType.CONTINUOUS_QUESTION, prompt, 0.1
        );
        
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
        Map<String, Object> parsed = jsonParser.extractJsonFromResponse(rawResponse);
        
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
        try {
            // 1. 获取所有激活的数据库概览
            List<DatabaseOverview> overviews = databaseOverviewRepository.findByIsActiveTrue();
            
            log.info("📋 数据库概览表中激活的数据库数量: {}", overviews.size());
            List<String> activeDbNames = overviews.stream()
                .map(DatabaseOverview::getDatabaseName)
                .collect(Collectors.toList());
            log.info("📋 激活的数据库列表: {}", activeDbNames);
            
            if (overviews.isEmpty()) {
                log.warn("⚠️ 没有可用的数据库概览，返回所有数据库");
                return databaseService.getAllDatabases();
            }
            
            // 2. 构建数据库选择提示词
            String prompt = buildDatabaseSelectionPrompt(question, overviews);
            
            // 3. 调用AI模型
            Map<String, Object> response = llmRouter.route(
                com.nl2sql.enums.LLMTaskType.DATABASE_SELECTION, prompt, 0.1
            );
            
            // 4. 解析响应
            String content = (String) response.get("response");
            if (content == null || content.trim().isEmpty()) {
                log.warn("⚠️ AI未返回有效响应，使用所有激活数据库");
                return activeDbNames;
            }
            
            // 5. 提取数据库列表
            Map<String, Object> parsed = jsonParser.extractJsonFromResponse(content);
            @SuppressWarnings("unchecked")
            List<String> selectedDatabases = (List<String>) parsed.get("databases");
            
            if (selectedDatabases == null || selectedDatabases.isEmpty()) {
                log.warn("⚠️ AI未返回有效的数据库列表，使用所有激活数据库");
                return activeDbNames;
            }
            
            // 6. 验证数据库是否在激活列表中
            selectedDatabases = selectedDatabases.stream()
                .filter(activeDbNames::contains)
                .collect(Collectors.toList());
            
            if (selectedDatabases.isEmpty()) {
                log.warn("⚠️ AI选择的数据库都不在激活列表中，使用所有激活数据库");
                log.warn("⚠️ AI选择: {}, 激活列表: {}", 
                    parsed.get("databases"), activeDbNames);
                return activeDbNames;
            }
            
            log.info("✅ 智能选择数据库: {} (从激活列表: {})", selectedDatabases, activeDbNames);
            return selectedDatabases;
            
        } catch (Exception e) {
            log.error("❌ 数据库选择失败: {}", e.getMessage());
            return databaseService.getAllDatabases();
        }
    }
    
    /**
     * 构建数据库选择提示词
     * 该方法用于生成一个结构化的提示词，帮助AI系统根据用户问题选择最相关的数据库
     *
     * @param question 用户提出的业务问题
     * @param overviews 可用数据库的概览信息列表
     * @return 返回格式化的提示词字符串，包含用户问题、数据库选项和输出要求
     */
    private String buildDatabaseSelectionPrompt(String question, List<DatabaseOverview> overviews) {
        // 使用StringBuilder高效构建提示词字符串
        StringBuilder prompt = new StringBuilder();
        // 添加系统角色说明
        prompt.append("你是一个数据库选择专家。根据用户问题，从以下数据库中选择最相关的一个或多个个数据库。\n\n");
        // 添加用户问题部分标题
        prompt.append("【用户问题】\n");
        // 添加实际的用户问题内容
        prompt.append(question).append("\n\n");
        // 添加可用数据库部分标题
        prompt.append("【可用数据库】\n");
        
        // 遍历所有数据库概览信息，构建数据库选项列表
        for (DatabaseOverview overview : overviews) {
            // 添加数据库名称
            prompt.append("- ").append(overview.getDatabaseName());
            // 如果数据库有描述信息，则添加描述
            if (overview.getDescription() != null && !overview.getDescription().isEmpty()) {
                prompt.append(": ").append(overview.getDescription());
            }
            // 换行
            prompt.append("\n");
            
            // 如果数据库有表摘要信息，则添加表摘要
            if (overview.getTableSummary() != null && !overview.getTableSummary().isEmpty()) {
                prompt.append("  表摘要: ").append(overview.getTableSummary()).append("\n");
            }
        }

        prompt.append("\n【要求】\n");
        prompt.append("1. 仔细分析用户问题涉及的业务领域\n");
        prompt.append("2. 根据每个数据库的表名和表注释判断哪些数据库包含相关数据\n");
        prompt.append("3. 如果问题涉及多个业务领域，可以选择多个数据库\n");
        prompt.append("4. 如果不确定，优先选择最可能相关的数据库\n\n");
        prompt.append("【输出格式】\n");
        prompt.append("请以JSON格式返回，格式如下：\n");
        prompt.append("{\"databases\": [\"数据库1\", \"数据库2\"]}\n");
        
        return prompt.toString();
    }

    private String buildContinuousQuestionPrompt(String previous, String current, boolean isSessionReferenced) {
        String sessionHint = "";
        if (isSessionReferenced) {
            sessionHint = """
                
                【重要提示】
                ⚠️ 用户明确引用了之前的会话（sessionId），这表明新问题一定与上一个问题相关。
                在这种情况下，你应该更倾向于判断为连续问题，并生成合并后的完整问题。
                """;
        }
        
        return String.format("""
            你是一个智能问题分析助手。请判断新问题是否是对上一个问题的追问（连续问题）。
            
            【上一个问题】
            %s
            
            【新问题】
            %s%s
            
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
            """, previous, current, sessionHint);
    }

    /**
     * 处理数据库查询（第一阶段）
     */
    public Map<String, Object> processQueryDb(String question, String windowId, Integer userId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 保存问题到 session
            String newSessionId = sessionService.saveQuestionToSession(question, windowId, userId);
            
            // 3. 选择数据库
            List<String> selectedDatabases = selectDatabases(question);
            log.info("📊 选择数据库: {}", selectedDatabases);
            
            // 4. 提取关键词
            Map<String, Map<String, List<String>>> databaseKeywords = 
                keywordExtractorService.extractKeywords(question, selectedDatabases);
            
            // 5. 选择候选表
            log.info("🔍 步骤5: 选择候选表");
            List<String> candidateTables = schemaService.selectCandidateTables(
                databaseKeywords, selectedDatabases, 10
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, List<String>> mergedKeywords = flattenKeywords(databaseKeywords);
            
            return Map.of(
                "success", true,
                "sessionId", newSessionId,
                "question", question,
                "selectedDatabases", selectedDatabases,
                "keywords", mergedKeywords,
                "candidateTables", candidateTables,
                "executionTime", executionTime
            );
                
        } catch (Exception e) {
            log.error("❌ 数据库查询处理错误: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 处理SQL查询（第二阶段）
     */
    public Map<String, Object> processQuerySql(String question, List<String> candidateTables, 
                                               Map<String, List<String>> mergedKeywords) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (candidateTables.isEmpty()) {
                return Map.of(
                    "success", false,
                    "error", "未找到相关表结构"
                );
            }
            
            // 6. 生成 SQL（包含解释）
            log.info("🔍 步骤6: 生成SQL");
            com.nl2sql.model.dto.SQLResult sqlResult = sqlGeneratorService.generateSQLWithExplanation(
                question, candidateTables, mergedKeywords
            );
            
            if (sqlResult == null || sqlResult.getSql() == null) {
                return Map.of(
                    "success", false,
                    "error", "未能生成有效的SQL语句"
                );
            }
            
            // 7. 执行 SQL
            log.info("🔍 步骤7: 执行SQL");
            List<Map<String, Object>> results = Collections.emptyList();
            
            try {
                results = databaseService.executeQuery(sqlResult.getSql(), 100);
                log.info("✅ SQL执行成功，返回 {} 行", results.size());
            } catch (Exception e) {
                log.warn("⚠️ SQL执行失败: {}", e.getMessage());
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            return Map.of(
                "success", true,
                "question", question,
                "sql", sqlResult.getSql(),
                "sqlExplanation", sqlResult.getExplanation(),
                "model", sqlResult.getModel(),
                "results", results,
                "resultCount", results.size(),
                "executionTime", executionTime
            );
                
        } catch (Exception e) {
            log.error("❌ SQL查询处理错误: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
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
