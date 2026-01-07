package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流式响应服务 - 完全按照Python实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {

    private final SessionService sessionService;
    private final DatabaseService databaseService;
    private final KeywordExtractorService keywordExtractorService;
    private final SchemaService schemaService;
    private final SQLGeneratorService sqlGeneratorService;
    private final ObjectMapper objectMapper;

    /**
     * 流式处理查询 - 完全按照Python的process_query_stream实现
     */
    public void processQueryStream(String question, String windowId, String sessionId, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 步骤0: 判断连续问题并合并
            sendProgress(emitter, "intent_analysis", "processing", Map.of(
                "message", "正在解析问题意图..."
            ), startTime);
            
            Map<String, Object> mergeResult = sessionService.mergeContinuousQuestion(
                question, windowId, sessionId
            );
            
            boolean isContinuous = (boolean) mergeResult.getOrDefault("is_continuous", false);
            String mergedQuestion = (String) mergeResult.getOrDefault("merged_question", question);
            String previousQuestion = (String) mergeResult.get("previous_question");
            
            String intentMessage = String.format(
                "用户上一个问题为：%s，本次问题为：%s，%s",
                previousQuestion != null ? previousQuestion : "无",
                question,
                isContinuous ? 
                    String.format("是对上一个问题的追问，故用户的问题为：%s", mergedQuestion) :
                    String.format("不是对上一个问题的追问，故用户的问题为：%s", mergedQuestion)
            );
            
            sendProgress(emitter, "intent_analysis", "completed", Map.of(
                "message", intentMessage,
                "is_continuous", isContinuous,
                "merged_question", mergedQuestion,
                "previous_question", previousQuestion != null ? previousQuestion : ""
            ), startTime);
            
            String workingQuestion = mergedQuestion;
            
            // 步骤1: 选择相关数据库
            sendProgress(emitter, "database_selection", "processing", Map.of(
                "message", "正在定位数据库..."
            ), startTime);
            
            List<String> selectedDatabases = databaseService.selectDatabases(workingQuestion);
            
            if (selectedDatabases == null || selectedDatabases.isEmpty()) {
                sendProgress(emitter, "database_selection", "error", Map.of(
                    "message", "未找到相关数据库",
                    "error", "未找到相关数据库"
                ), startTime);
                emitter.complete();
                return;
            }
            
            String dbMessage = String.format("根据用户问题已定位到数据库：%s", 
                String.join(", ", selectedDatabases));
            sendProgress(emitter, "database_selection", "completed", Map.of(
                "message", dbMessage,
                "selected_databases", selectedDatabases
            ), startTime);
            
            // 步骤2: 提取关键词（内部步骤，不向前端展示）
            Map<String, Map<String, List<String>>> databaseKeywords = 
                keywordExtractorService.extractKeywordsForDatabases(workingQuestion, selectedDatabases);
            
            // 步骤3: 选择候选表
            sendProgress(emitter, "table_selection", "processing", Map.of(
                "message", "正在定位数据库表..."
            ), startTime);
            
            List<String> candidateTables = schemaService.selectCandidateTablesForDatabases(
                databaseKeywords, selectedDatabases
            );
            
            if (candidateTables == null || candidateTables.isEmpty()) {
                sendProgress(emitter, "table_selection", "error", Map.of(
                    "message", "未找到相关表结构",
                    "error", "未找到相关表结构"
                ), startTime);
                emitter.complete();
                return;
            }
            
            // 格式化表信息
            List<String> tableInfoList = new ArrayList<>();
            for (int i = 0; i < Math.min(candidateTables.size(), 10); i++) {
                String tableLine = candidateTables.get(i);
                String[] parts = tableLine.split("\\|\\|");
                if (parts.length >= 2) {
                    String tableName = parts[0];
                    String tableComment = parts[1];
                    tableInfoList.add(String.format("%s（%s）", tableName, tableComment));
                }
            }
            
            String tableMessage = String.format("已定位到%d个相关表：%s", 
                tableInfoList.size(), String.join(", ", tableInfoList));
            
            List<String> tableNames = candidateTables.stream()
                .limit(10)
                .map(t -> t.split("\\|\\|")[0])
                .collect(Collectors.toList());
            
            sendProgress(emitter, "table_selection", "completed", Map.of(
                "message", tableMessage,
                "candidate_tables", tableNames
            ), startTime);
            
            // 合并关键词
            Map<String, List<String>> mergedKeywords = new HashMap<>();
            mergedKeywords.put("keywords_cn", new ArrayList<>());
//            mergedKeywords.put("keywords_en", new ArrayList<>());
            
            for (Map<String, List<String>> dbKeywords : databaseKeywords.values()) {
                mergedKeywords.get("keywords_cn").addAll(dbKeywords.getOrDefault("keywords_cn", new ArrayList<>()));
//                mergedKeywords.get("keywords_en").addAll(dbKeywords.getOrDefault("keywords_en", new ArrayList<>()));
            }
            
            // 去重
            mergedKeywords.put("keywords_cn", new ArrayList<>(new HashSet<>(mergedKeywords.get("keywords_cn"))));
//            mergedKeywords.put("keywords_en", new ArrayList<>(new HashSet<>(mergedKeywords.get("keywords_en"))));
            
            // 步骤4: 生成SQL
            sendProgress(emitter, "sql_generation", "processing", Map.of(
                "message", "正在生成SQL语句..."
            ), startTime);
            
            List<String> generatedSqls = sqlGeneratorService.generateSQL(
                workingQuestion, candidateTables, mergedKeywords
            );
            
            if (generatedSqls == null || generatedSqls.isEmpty()) {
                sendProgress(emitter, "sql_generation", "error", Map.of(
                    "message", "未能生成有效的SQL语句",
                    "error", "未能生成有效的SQL语句"
                ), startTime);
                emitter.complete();
                return;
            }
            
            String firstSql = generatedSqls.get(0);
            String sqlPreview = firstSql.length() > 100 ? 
                firstSql.substring(0, 100) + "..." : firstSql;
            String sqlMessage = String.format("成功生成SQL语句：%s", sqlPreview);
            
            sendProgress(emitter, "sql_generation", "completed", Map.of(
                "message", sqlMessage,
                "generated_sql", firstSql
            ), startTime);
            
            // 步骤5: 执行SQL
            sendProgress(emitter, "sql_execution", "processing", Map.of(
                "message", "正在执行SQL查询..."
            ), startTime);
            
            String successfulSql = null;
            List<Map<String, Object>> successfulResults = new ArrayList<>();
            
            for (String sql : generatedSqls) {
                Map<String, Object> execResult = sqlGeneratorService.executeSQLEnhanced(sql, 10000);
                successfulSql = sql;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results = (List<Map<String, Object>>) execResult.get("results");
                if (results != null && !results.isEmpty()) {
                    successfulResults = results;
                    break;
                }
            }
            
            String execMessage = String.format("SQL执行完成，返回 %d 行数据", successfulResults.size());
            sendProgress(emitter, "sql_execution", "completed", Map.of(
                "message", execMessage,
                "row_count", successfulResults.size(),
                "sql_results", successfulResults
            ), startTime);
            
            // 步骤6: 保存session
            String newSessionId = sessionService.saveQuestionToSession(mergedQuestion, windowId);
            
            // 计算处理时间
            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // 最终结果
            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("message", "查询处理完成");
            finalResult.put("original_question", question);
            finalResult.put("merged_question", mergedQuestion);
            finalResult.put("is_continuous", isContinuous);
            finalResult.put("previous_question", previousQuestion != null ? previousQuestion : "");
            finalResult.put("selected_databases", selectedDatabases);
            finalResult.put("candidate_tables", tableNames.stream().limit(5).collect(Collectors.toList()));
            finalResult.put("generated_sql", successfulSql);
            finalResult.put("sql_results", successfulResults);
            finalResult.put("processing_time", processingTime);
            finalResult.put("session_id", newSessionId);
            finalResult.put("used_session_id", sessionId != null ? sessionId : "");
            
            sendProgress(emitter, "completed", "success", finalResult, startTime);
            
            emitter.complete();
            log.info("✅ 流式查询处理完成 - 耗时: {}秒", processingTime);
            
        } catch (Exception e) {
            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            log.error("❌ 流式查询处理失败: {}", e.getMessage(), e);
            try {
                sendProgress(emitter, "error", "failed", Map.of(
                    "message", String.format("查询处理错误: %s", e.getMessage()),
                    "error", e.getMessage(),
                    "processing_time", processingTime
                ), startTime);
            } catch (Exception ex) {
                log.error("发送错误事件失败", ex);
            }
            emitter.completeWithError(e);
        }
    }

    /**
     * 发送进度信息 - 完全按照Python格式
     */
    private void sendProgress(SseEmitter emitter, String step, String status, 
                              Map<String, Object> data, long startTime) throws IOException {
        Map<String, Object> progress = new HashMap<>();
        progress.put("step", step);
        progress.put("status", status);
        progress.put("timestamp", (System.currentTimeMillis() - startTime) / 1000.0);
        
        if (data != null) {
            progress.putAll(data);
        }
        
        String jsonData = objectMapper.writeValueAsString(progress);
        emitter.send(SseEmitter.event()
            .data(jsonData));
    }
}
