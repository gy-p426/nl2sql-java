package com.nl2sql.service;

import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.model.entity.TrainingData;
import com.nl2sql.repository.TrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SQLGeneratorService {

    private final VolcanoEngineClient volcanoEngineClient;
    private final TrainingDataRepository trainingDataRepository;
    private final DatabaseService databaseService;
    private final LLMRouter llmRouter;

    /**
     * 生成 SQL - 使用多模型并行生成
     * 返回包含SQL和解释的结果
     */
    public com.nl2sql.model.dto.SQLResult generateSQLWithExplanation(
            String question,
            List<String> candidateTables,
            Map<String, List<String>> keywords) {
        
        try {
            log.info("🎯 开始SQL生成 - 候选表数量: {}", candidateTables.size());
            
            if (candidateTables.isEmpty()) {
                log.error("❌ 候选表列表为空，无法生成SQL");
                return null;
            }
            
            // 记录候选表信息
            for (int i = 0; i < Math.min(5, candidateTables.size()); i++) {
                String tableName = candidateTables.get(i).split("\\|\\|")[0];
                log.info("📋 候选表{}: {}", i + 1, tableName);
            }
            
            // 检索相关历史数据
            List<TrainingPair> relevantPairs = retrieveRelevantTrainingData(question, keywords, 5);
            log.info("🔍 检索到 {} 个相关历史示例", relevantPairs.size());
            
            // 构建提示词
            String prompt = buildSQLPrompt(question, candidateTables, relevantPairs);
            log.debug("📝 SQL生成提示词长度: {}字符", prompt.length());
            
            // 使用3个模型并行生成SQL（包含解释）
            Map<String, SQLWithExplanation> modelSQLs = generateSQLWithMultipleModels(prompt);
            
            if (modelSQLs.isEmpty()) {
                log.warn("⚠️ 所有模型都未能生成SQL，使用降级策略");
                List<String> fallbackSQLs = generateSQLFallback(question, candidateTables, keywords);
                if (!fallbackSQLs.isEmpty()) {
                    return com.nl2sql.model.dto.SQLResult.builder()
                        .sql(fallbackSQLs.get(0))
                        .explanation("降级策略生成的简单SQL")
                        .model("fallback")
                        .build();
                }
                return null;
            }
            
            // 执行SQL并过滤出成功的
            Map<String, SQLExecutionResult> validSQLs = executeSQLs(modelSQLs);
            
            if (validSQLs.isEmpty()) {
                log.error("❌ 没有SQL执行成功");
                return null;
            }
            
            // 使用GLM-4评判最优SQL
            String bestModelKey = evaluateBestSQL(question, candidateTables, validSQLs);
            
            if (bestModelKey != null && validSQLs.containsKey(bestModelKey)) {
                SQLExecutionResult bestResult = validSQLs.get(bestModelKey);
                log.info("✅ 选出最优SQL，来自模型: {}", bestModelKey);
                return com.nl2sql.model.dto.SQLResult.builder()
                    .sql(bestResult.sql)
                    .explanation(bestResult.explanation)
                    .model(bestModelKey)
                    .build();
            }
            
            // 如果评判失败，返回第一个成功的SQL
            Map.Entry<String, SQLExecutionResult> firstEntry = validSQLs.entrySet().iterator().next();
            log.info("⚠️ SQL评判失败，返回第一个成功的SQL，来自模型: {}", firstEntry.getKey());
            return com.nl2sql.model.dto.SQLResult.builder()
                .sql(firstEntry.getValue().sql)
                .explanation(firstEntry.getValue().explanation)
                .model(firstEntry.getKey())
                .build();
            
        } catch (Exception e) {
            log.error("❌ 生成SQL错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成 SQL - 兼容旧接口
     */
    public List<String> generateSQL(
            String question,
            List<String> candidateTables,
            Map<String, List<String>> keywords) {
        
        com.nl2sql.model.dto.SQLResult result = generateSQLWithExplanation(question, candidateTables, keywords);
        if (result != null && result.getSql() != null) {
            return List.of(result.getSql());
        }
        return Collections.emptyList();
    }
    
    /**
     * SQL和解释的内部类
     */
    private static class SQLWithExplanation {
        String sql;
        String explanation;
        
        SQLWithExplanation(String sql, String explanation) {
            this.sql = sql;
            this.explanation = explanation;
        }
    }
    
    /**
     * 使用多个模型并行生成SQL（包含解释）
     */
    private Map<String, SQLWithExplanation> generateSQLWithMultipleModels(String prompt) {
        Map<String, com.nl2sql.client.LLMClient> sqlClients = llmRouter.getSQLGenerationClients();
        
        log.info("🔧 使用 {} 个模型并行生成SQL", sqlClients.size());
        
        // 使用CompletableFuture实现真正的并行执行
        List<java.util.concurrent.CompletableFuture<Map.Entry<String, SQLWithExplanation>>> futures = new ArrayList<>();
        
        for (Map.Entry<String, com.nl2sql.client.LLMClient> entry : sqlClients.entrySet()) {
            String modelKey = entry.getKey();
            com.nl2sql.client.LLMClient client = entry.getValue();
            
            if (client == null || !client.isAvailable()) {
                log.warn("⚠️ 模型 {} 不可用，跳过", modelKey);
                continue;
            }
            
            // 为每个模型创建异步任务
            java.util.concurrent.CompletableFuture<Map.Entry<String, SQLWithExplanation>> future = 
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("📤 [{}] 开始生成SQL", modelKey);
                        long startTime = System.currentTimeMillis();
                        
                        Map<String, Object> response = client.generate(prompt, 0.1);
                        
                        long duration = System.currentTimeMillis() - startTime;
                        log.info("⏱️ [{}] 生成耗时: {}ms", modelKey, duration);
                        
                        if (response.containsKey("error")) {
                            log.warn("⚠️ [{}] SQL生成失败: {}", modelKey, response.get("error"));
                            return null;
                        }
                        
                        String rawResponse = (String) response.get("response");
                        if (rawResponse == null || rawResponse.trim().isEmpty()) {
                            log.warn("⚠️ [{}] AI响应为空", modelKey);
                            return null;
                        }
                        
//                        log.debug("📄 [{}] 原始响应: {}", modelKey, rawResponse);
                        
                        // 提取SQL和解释
                        SQLWithExplanation sqlWithExplanation = extractSQLAndExplanation(rawResponse);
                        
                        if (sqlWithExplanation != null && sqlWithExplanation.sql != null) {
                            if (validateSQLSecurity(sqlWithExplanation.sql)) {
                                log.info("✅ [{}] 成功生成SQL和解释", modelKey);
                                return new java.util.AbstractMap.SimpleEntry<>(modelKey, sqlWithExplanation);
                            } else {
                                log.warn("⚠️ [{}] SQL未通过安全验证", modelKey);
                            }
                        } else {
                            log.warn("⚠️ [{}] 未能提取SQL", modelKey);
                        }
                        
                    } catch (Exception e) {
                        log.error("❌ [{}] 生成SQL异常: {}", modelKey, e.getMessage());
                    }
                    return null;
                });
            
            futures.add(future);
        }
        
        // 如果没有任务，直接返回空结果
        if (futures.isEmpty()) {
            log.warn("⚠️ 没有可用的SQL生成模型");
            return new HashMap<>();
        }
        
        // 等待所有任务完成或超时
        java.util.concurrent.CompletableFuture<Void> allFutures = 
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]));
        
        boolean completedSuccessfully = false;
        try {
            // 设置超时时间为3分钟
            allFutures.get(180, java.util.concurrent.TimeUnit.SECONDS);
            completedSuccessfully = true;
            log.info("✅ 所有模型生成任务已完成");
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("⚠️ 部分模型生成超时（超过180秒），取消未完成的任务");
            // 取消所有未完成的任务
            for (java.util.concurrent.CompletableFuture<Map.Entry<String, SQLWithExplanation>> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                    log.debug("🛑 已取消未完成的任务");
                }
            }
        } catch (java.util.concurrent.CancellationException e) {
            log.warn("⚠️ 任务被取消: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ 等待模型生成时出错: {}", e.getMessage());
            // 发生异常时，取消所有未完成的任务
            for (java.util.concurrent.CompletableFuture<Map.Entry<String, SQLWithExplanation>> future : futures) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
        
        // 收集结果 - 只收集已完成且成功的任务
        Map<String, SQLWithExplanation> results = new HashMap<>();
        for (java.util.concurrent.CompletableFuture<Map.Entry<String, SQLWithExplanation>> future : futures) {
            try {
                // 检查任务是否已完成且没有异常
                if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                    // 使用 getNow(null) 避免阻塞，因为任务已完成
                    Map.Entry<String, SQLWithExplanation> result = future.getNow(null);
                    if (result != null) {
                        results.put(result.getKey(), result.getValue());
                        log.debug("✅ 收集到模型 {} 的结果", result.getKey());
                    }
                } else if (future.isCompletedExceptionally()) {
                    log.debug("⚠️ 任务异常完成，跳过");
                } else if (future.isCancelled()) {
                    log.debug("⚠️ 任务被取消，跳过");
                }
            } catch (Exception e) {
                log.debug("⚠️ 收集结果时出错: {}", e.getMessage());
            }
        }
        
        log.info("📊 共生成 {} 个有效SQL", results.size());
        return results;
    }
    
    /**
     * 从响应中提取SQL和解释
     * 格式：```sql\nSELECT...\n```\n```解释\n...\n```
     */
    private SQLWithExplanation extractSQLAndExplanation(String response) {
        try {
            // 提取SQL
            List<String> sqls = extractSQLStatements(response);
            if (sqls.isEmpty()) {
                return null;
            }
            String sql = sqls.get(0);
            
            // 提取解释（在```解释```代码块中）
            String explanation = "";
            Pattern explanationPattern = Pattern.compile("```解释\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
            Matcher matcher = explanationPattern.matcher(response);
            if (matcher.find()) {
                explanation = matcher.group(1).trim();
                log.debug("✅ 提取到解释: {}", explanation.substring(0, Math.min(100, explanation.length())));
            } else {
                // 如果没有```解释```块，尝试提取SQL代码块后的文本
                int sqlEndIndex = response.lastIndexOf("```");
                if (sqlEndIndex > 0 && sqlEndIndex < response.length() - 3) {
                    String afterSQL = response.substring(sqlEndIndex + 3).trim();
                    if (!afterSQL.isEmpty() && afterSQL.length() < 1000) {
                        explanation = afterSQL;
                        log.debug("✅ 从SQL后提取到解释: {}", explanation.substring(0, Math.min(100, explanation.length())));
                    }
                }
            }
            
            return new SQLWithExplanation(sql, explanation);
            
        } catch (Exception e) {
            log.error("❌ 提取SQL和解释时出错: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 执行多个SQL并返回成功的结果
     */
    private Map<String, SQLExecutionResult> executeSQLs(Map<String, SQLWithExplanation> modelSQLs) {
        Map<String, SQLExecutionResult> validResults = new HashMap<>();
        
        for (Map.Entry<String, SQLWithExplanation> entry : modelSQLs.entrySet()) {
            String modelKey = entry.getKey();
            SQLWithExplanation sqlWithExplanation = entry.getValue();
            String sql = sqlWithExplanation.sql;
            String explanation = sqlWithExplanation.explanation;
            
            try {
                log.info("🔍 [{}] 执行SQL测试", modelKey);
                Map<String, Object> result = executeSQLEnhanced(sql, 10);  // 只取10条测试
                
                if (Boolean.TRUE.equals(result.get("success"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
                    validResults.put(modelKey, new SQLExecutionResult(sql, explanation, results));
                    log.info("✅ [{}] SQL执行成功，返回 {} 行", modelKey, results.size());
                } else {
                    log.warn("⚠️ [{}] SQL执行失败: {}", modelKey, result.get("error"));
                }
                
            } catch (Exception e) {
                log.error("❌ [{}] SQL执行异常: {}", modelKey, e.getMessage());
            }
        }
        
        return validResults;
    }
    
    /**
     * 使用GLM-4评判最优SQL
     * 返回最优SQL的模型key
     */
    private String evaluateBestSQL(String question, List<String> candidateTables, 
                                   Map<String, SQLExecutionResult> validSQLs) {
        try {
            String prompt = buildSQLEvaluationPrompt(question, candidateTables, validSQLs);
            
            Map<String, Object> response = llmRouter.route(
                com.nl2sql.enums.LLMTaskType.SQL_EVALUATION, prompt, 0.1
            );
            
            if (response.containsKey("error")) {
                log.warn("⚠️ SQL评判失败: {}", response.get("error"));
                return null;
            }
            
            String rawResponse = (String) response.get("response");
            log.debug("📄 GLM-4评判响应: {}", rawResponse);
            
            // 从响应中提取最优SQL的key
            String bestModelKey = extractBestModelKey(rawResponse, validSQLs.keySet());
            
            if (bestModelKey != null) {
                log.info("🏆 GLM-4选择的最优SQL来自: {}", bestModelKey);
                return bestModelKey;
            }
            
            log.warn("⚠️ 无法从评判结果中提取最优SQL");
            return null;
            
        } catch (Exception e) {
            log.error("❌ SQL评判异常: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建SQL评判提示词
     */
    private String buildSQLEvaluationPrompt(String question, List<String> candidateTables,
                                           Map<String, SQLExecutionResult> validSQLs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个SQL专家。请根据用户问题和候选表结构，从以下几个SQL中选择最合适的一个。\n\n");
        
        prompt.append("【用户问题】\n");
        prompt.append(question).append("\n\n");
        
        // 构建完整的表结构信息（参考buildSQLPrompt）
        prompt.append("【可用的数据库表结构】\n");
        int tableCount = 0;
        
        for (int i = 0; i < Math.min(15, candidateTables.size()); i++) {
            String tableLine = candidateTables.get(i);
            String[] parts = tableLine.split("\\|\\|");
            
            if (parts.length >= 4) {
                String tableName = parts[0];
                String tableComment = parts[1];
                
                prompt.append(String.format("\n表名: %s\n表注释: %s\n", tableName, tableComment));
                prompt.append("列信息:\n");
                
                for (int j = 4; j < parts.length; j += 3) {
                    if (j + 2 < parts.length) {
                        String colName = parts[j];
                        String colComment = parts[j + 1];
                        String colType = parts[j + 2];
                        prompt.append(String.format("  - %s (%s): %s\n", colName, colType, colComment));
                    }
                }
                prompt.append("\n");
                tableCount++;
            }
        }
        
        if (tableCount == 0) {
            log.warn("⚠️ 无法解析候选表信息");
        }
        
        prompt.append("【候选SQL】\n");
        int index = 1;
        for (Map.Entry<String, SQLExecutionResult> entry : validSQLs.entrySet()) {
            prompt.append(String.format("SQL%d (%s):\n", index, entry.getKey()));
            prompt.append(entry.getValue().sql).append("\n");
            prompt.append(String.format("执行结果：返回 %d 行数据\n\n", entry.getValue().results.size()));
            index++;
        }
        
        prompt.append("【评判标准】\n");
        prompt.append("1. SQL语法是否正确\n");
        prompt.append("2. 是否准确回答了用户问题\n");
        prompt.append("3. 是否使用了正确的表和列\n");
        prompt.append("4. 查询逻辑是否合理\n");
        prompt.append("5. 是否有不必要的复杂度\n\n");
        
        prompt.append("【输出格式】\n");
        prompt.append("请直接输出最优SQL的编号和模型名称，格式如下：\n");
        prompt.append("最优SQL: SQL1 (deepseek-v3-1)\n");
        prompt.append("理由: [简要说明选择理由]\n");
        
        return prompt.toString();
    }
    
    /**
     * 从评判响应中提取最优模型key
     */
    private String extractBestModelKey(String response, Set<String> modelKeys) {
        // 尝试匹配模型key
        for (String key : modelKeys) {
            if (response.contains(key)) {
                return key;
            }
        }
        
        // 尝试匹配SQL编号
        Pattern pattern = Pattern.compile("SQL(\\d+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            int sqlIndex = Integer.parseInt(matcher.group(1)) - 1;
            List<String> keyList = new ArrayList<>(modelKeys);
            if (sqlIndex >= 0 && sqlIndex < keyList.size()) {
                return keyList.get(sqlIndex);
            }
        }
        
        return null;
    }
    
    /**
     * SQL执行结果内部类
     */
    private static class SQLExecutionResult {
        String sql;
        String explanation;  // SQL解释
        List<Map<String, Object>> results;
        
        SQLExecutionResult(String sql, String explanation, List<Map<String, Object>> results) {
            this.sql = sql;
            this.explanation = explanation;
            this.results = results;
        }
    }

    /**
     * 构建 SQL 生成提示词
     */
    private String buildSQLPrompt(
            String question,
            List<String> candidateTables,
            List<TrainingPair> relevantPairs) {
        
        // 获取当前时间信息
        LocalDateTime now = LocalDateTime.now();
        String currentTimeInfo = String.format("""
            【当前时间信息】
            - 当前日期: %s
            - 当前年份: %d年
            - 当前月份: %d月
            - 当前星期: 星期%s
            
            ⚠️ 重要提示：
            - 如果用户问题涉及"今年"、"本年"，请使用 %d 年
            - 如果用户问题涉及"今天"、"本月"，请使用 %d年%d月
            - 时间范围查询请使用 BETWEEN 或 >= 和 <= 组合
            """,
            now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
            now.getYear(),
            now.getMonthValue(),
            getChineseWeekday(now.getDayOfWeek().getValue()),
            now.getYear(),
            now.getYear(),
            now.getMonthValue()
        );
        
        // 构建历史示例
        StringBuilder historyExamples = new StringBuilder();
        if (!relevantPairs.isEmpty()) {
            historyExamples.append("\n【参考：历史训练数据】\n");
            for (int i = 0; i < Math.min(5, relevantPairs.size()); i++) {
                TrainingPair pair = relevantPairs.get(i);
                historyExamples.append(String.format("示例 %d:\n问题: %s\nSQL: %s\n\n", 
                    i + 1, pair.question, pair.sql));
            }
            log.info("📚 构建了 {} 个历史示例", Math.min(5, relevantPairs.size()));
        }
        
        // 构建表信息
        StringBuilder tablesInfo = new StringBuilder();
        int tableCount = 0;
        
        for (int i = 0; i < Math.min(15, candidateTables.size()); i++) {
            String tableLine = candidateTables.get(i);
            String[] parts = tableLine.split("\\|\\|");
            
            if (parts.length >= 4) {
                String tableName = parts[0];
                String tableComment = parts[1];
                
                tablesInfo.append(String.format("\n表名: %s\n表注释: %s\n", tableName, tableComment));
                tablesInfo.append("列信息:\n");
                
                int colCount = 0;
                for (int j = 4; j < parts.length; j += 3) {
                    if (j + 2 < parts.length) {
                        String colName = parts[j];
                        String colComment = parts[j + 1];
                        String colType = parts[j + 2];
                        tablesInfo.append(String.format("  - %s (%s): %s\n", colName, colType, colComment));
                        colCount++;
                    }
                }
                tablesInfo.append("\n");
                tableCount++;
            }
        }
        
        if (tablesInfo.length() == 0) {
            log.error("❌ 无法解析候选表信息");
            return "";
        }
        
        log.info("📊 构建提示词完成 - 包含 {} 个表", tableCount);
        
        return String.format("""
            请根据用户问题生成SQL查询语句。
            
            %s
            
            %s
            
            【当前用户问题】
            %s
            
            【可用的数据库表结构】
            %s
            
            【SQL生成规则】
            1. 优先参考【历史训练数据】中的SQL模式
            2. 必须使用上面提供的真实表名和列名（表名格式：数据库名.表名）
            3. 生成1个高质量SQL方案，用```sql```包围
            4. 同时生成markdown格式的sql的描述，用```解释```包围，并且描述中不要包含sql语句或sql片段，只用中纯文进行解释
            4. 列名使用中文别名
            5. 注意有的表中有软删除条件（deleted = 0），有的表中没有软删除条件
            6. ⚠️ 如果问题涉及时间（如"今年"、"本月"、"上个月"等），请参考【当前时间信息】生成准确的时间条件
            7. ⚠️ 如果用户问题指定返回的列数，请你严格按照指定的列生成sql
            8. ⚠️ 如果用户问题指定返回的列名，请你严格按照指定的列名生成sql，如问题为“获取2025年8月每天的历史出车次数的日期、出车次数，共2列数据”，生成sql的列名一定为“日期、出车次数”
            
            请基于以上信息生成SQL：
            """,
            currentTimeInfo,
            historyExamples.toString(),
            question,
            tablesInfo.toString()
        );
    }

    /**
     * 提取 SQL 语句
     */
    private List<String> extractSQLStatements(String response) {
        List<String> sqls = new ArrayList<>();
        
        Pattern codeBlockPattern = Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = codeBlockPattern.matcher(response);
        
        while (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (sql.isEmpty()) continue;
            
            // 移除注释行
            String[] lines = sql.split("\n");
            List<String> cleanLines = new ArrayList<>();
            
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("--")) {
                    cleanLines.add(line);
                }
            }
            
            if (!cleanLines.isEmpty()) {
                String cleanSql = String.join("\n", cleanLines);
                String sqlUpper = cleanSql.toUpperCase().trim();
                
                // 检查是否以 SELECT 或 WITH 开头
                if (sqlUpper.startsWith("SELECT") || sqlUpper.startsWith("WITH")) {
                    sqls.add(cleanSql);
                    String sqlType = sqlUpper.startsWith("WITH") ? "WITH CTE" : "SELECT";
                    log.debug("✅ 提取有效{}语句: {}...", sqlType, 
                        cleanSql.substring(0, Math.min(50, cleanSql.length())));
                } else {
                    log.debug("❌ 非查询语句: {}...", 
                        cleanSql.substring(0, Math.min(50, cleanSql.length())));
                }
            }
        }
        
        return sqls;
    }

    /**
     * 验证 SQL 安全性
     */
    private boolean validateSQLSecurity(String sql) {
        String sqlUpper = sql.toUpperCase();
        
        // 移除字符串内容
        String cleanSql = sqlUpper.replaceAll("'[^']*'", "''");
        cleanSql = cleanSql.replaceAll("\"[^\"]*\"", "\"\"");
        
        // 危险操作检查
        String[] dangerousKeywords = {
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE",
            "TRUNCATE", "REPLACE", "LOAD_FILE", "INTO OUTFILE"
        };
        
        for (String keyword : dangerousKeywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = pattern.matcher(cleanSql);
            
            if (matcher.find()) {
                // 特殊处理：DELETED 字段不算危险
                if (keyword.equals("DELETE") && cleanSql.contains("DELETED")) {
                    continue;
                }
                log.warn("⚠️ SQL包含危险关键词: {}", keyword);
                return false;
            }
        }
        
        return true;
    }

    /**
     * 降级策略：基于规则的 SQL 生成
     */
    private List<String> generateSQLFallback(
            String question,
            List<String> candidateTables,
            Map<String, List<String>> keywords) {
        
        List<String> sqls = new ArrayList<>();
        
        if (candidateTables.isEmpty()) {
            return sqls;
        }
        
        // 获取第一个表作为主表
        String mainTableLine = candidateTables.get(0);
        String[] tableParts = mainTableLine.split("\\|\\|");
        
        if (tableParts.length < 4) {
            return sqls;
        }
        
        String tableName = tableParts[0];
        
        // 基于问题内容生成简单的 SQL
        if (question.matches(".*[多少|数量|总数|统计|计算].*")) {
            // 生成 COUNT 查询
            sqls.add(String.format("SELECT COUNT(*) AS '总数' FROM %s", tableName));
        } else {
            // 生成基本的 SELECT 查询
            sqls.add(String.format("SELECT * FROM %s LIMIT 10", tableName));
        }
        
        log.info("🔄 降级策略生成SQL - 表: {}, SQL数量: {}", tableName, sqls.size());
        
        return sqls;
    }

    /**
     * 执行SQL（增强版，带LIMIT）- 完全按照Python实现
     */
    public Map<String, Object> executeSQLEnhanced(String sql, int limit) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 添加LIMIT限制
            if (!sql.toUpperCase().contains("LIMIT")) {
                sql = sql.replaceAll(";\\s*$", "") + " LIMIT " + limit;
                log.debug("🔧 添加LIMIT限制: {}", limit);
            }

            // 2. 检测涉及的数据库
            List<String> databases = detectDatabasesInSql(sql);
            log.debug("🎯 检测到数据库: {}", databases);

            if (databases.isEmpty()) {
                log.warn("⚠️ 未检测到数据库，SQL执行失败");
                result.put("results", new ArrayList<>());
                result.put("execution_time", (System.currentTimeMillis() - startTime) / 1000.0);
                result.put("success", false);
                result.put("error", "未检测到数据库");
                return result;
            }

            // 3. 使用第一个数据库的连接执行SQL
            String dbName = databases.get(0);
            log.info("💾 使用数据库 {} 执行SQL", dbName);

            try (Connection conn = databaseService.getConnection(dbName);
                 Statement stmt = conn.createStatement()) {

                log.debug("📝 执行SQL: {}", sql);

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    List<Map<String, Object>> results = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);

                            // 转换为JSON可序列化格式
                            if (value == null) {
                                row.put(columnName, null);
                            } else if (value instanceof Number || value instanceof String || value instanceof Boolean) {
                                row.put(columnName, value);
                            } else {
                                row.put(columnName, value.toString());
                            }
                        }
                        results.add(row);
                    }

                    double executionTime = (System.currentTimeMillis() - startTime) / 1000.0;

                    log.info("📊 查询返回 {} 行数据", results.size());
                    log.info("✅ SQL执行完成 - 数据库: {}, 耗时: {:.3f}秒, 返回: {}行",
                            dbName, executionTime, results.size());

                    result.put("results", results);
                    result.put("execution_time", executionTime);
                    result.put("success", true);
                    result.put("database", dbName);

                    return result;
                }
            }

        } catch (Exception e) {
            double executionTime = (System.currentTimeMillis() - startTime) / 1000.0;
            log.error("❌ SQL执行错误: {}, 耗时: {:.3f}秒", e.getMessage(), executionTime);
            log.debug("💥 出错的SQL: {}", sql);

            result.put("results", new ArrayList<>());
            result.put("execution_time", executionTime);
            result.put("success", false);
            result.put("error", e.getMessage());

            return result;
        }
    }

    /**
     * 检测SQL中涉及的数据库 - 完全按照Python实现
     */
    private List<String> detectDatabasesInSql(String sql) {
        List<String> databases = new ArrayList<>();

        // 查找形如 database.table 的模式
        Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.[a-zA-Z_][a-zA-Z0-9_]*");
        Matcher matcher = pattern.matcher(sql);

        List<String> availableDbs = databaseService.getAllDatabases();

        while (matcher.find()) {
            String match = matcher.group(1);
            // 只有当匹配到的名称在已知数据库列表中时才认为是数据库名
            if (availableDbs.contains(match) && !databases.contains(match)) {
                databases.add(match);
            }
        }

        return databases;
    }


    /**
     * 检索相关训练数据
     */
    private List<TrainingPair> retrieveRelevantTrainingData(
            String question,
            Map<String, List<String>> keywords,
            int topN) {
        
        try {
            // 从数据库读取所有训练数据
            List<TrainingData> trainingDataList = trainingDataRepository.findAll();
            
            if (trainingDataList.isEmpty()) {
                log.debug("数据库中没有训练数据");
                return Collections.emptyList();
            }
            
            // 转换为TrainingPair
            List<TrainingPair> pairs = trainingDataList.stream()
                .map(td -> new TrainingPair(td.getQuestion(), td.getSql()))
                .collect(Collectors.toList());
            
            // 基础相似度检索
            return basicSimilarityRetrieval(question, keywords, pairs, topN);
            
        } catch (Exception e) {
            log.error("❌ 检索历史数据时发生错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 基础相似度检索
     */
    private List<TrainingPair> basicSimilarityRetrieval(
            String question,
            Map<String, List<String>> keywords,
            List<TrainingPair> pairs,
            int topN) {
        
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(keywords.getOrDefault("keywords_cn", Collections.emptyList()));
        allKeywords.addAll(keywords.getOrDefault("keywords_en", Collections.emptyList()));
        
        List<ScoredPair> scoredPairs = new ArrayList<>();
        
        for (TrainingPair pair : pairs) {
            // 关键词匹配得分
            long keywordScore = allKeywords.stream()
                .filter(kw -> pair.question.contains(kw))
                .count();
            
            // 字符串相似度得分
            double similarity = calculateSimilarity(
                question.toLowerCase(), 
                pair.question.toLowerCase()
            );
            
            double totalScore = keywordScore * 2 + similarity * 5;
            scoredPairs.add(new ScoredPair(pair, totalScore));
        }
        
        scoredPairs.sort(Comparator.comparingDouble(sp -> -sp.score));
        
        log.info("🔍 基础检索完成，返回前{}个相似问题", topN);
        
        return scoredPairs.stream()
            .limit(topN)
            .map(sp -> sp.pair)
            .collect(Collectors.toList());
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

    private String getChineseWeekday(int dayOfWeek) {
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        return weekdays[dayOfWeek - 1];
    }

    // 内部类
    
    private static class TrainingPair {
        String question;
        String sql;
        
        TrainingPair(String question, String sql) {
            this.question = question;
            this.sql = sql;
        }
    }

    private static class ScoredPair {
        TrainingPair pair;
        double score;
        
        ScoredPair(TrainingPair pair, double score) {
            this.pair = pair;
            this.score = score;
        }
    }
}
