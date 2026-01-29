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
    private final NL2SQLService nl2sqlService;
    private final DatabaseService databaseService;
    private final KeywordExtractorService keywordExtractorService;
    private final SchemaService schemaService;
    private final SQLGeneratorService sqlGeneratorService;
    private final ObjectMapper objectMapper;

    /**
     * 流式处理查询 - 完全按照Python的process_query_stream实现
     * 支持用户通过关闭连接来取消查询
     */
    public void processQueryStream(String question, String windowId, String sessionId, Integer userId, SseEmitter emitter) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 步骤0: 判断连续问题并合并
            sendProgress(emitter, "intent_analysis", "processing", Map.of(
                "message", "正在解析问题意图..."
            ), startTime);
            
            // 调用 NL2SQLService 的追问判断方法（真正调用 LLM）
//            Map<String, Object> mergeResult = nl2sqlService.mergeContinuousQuestion(
//                question, windowId, sessionId
//            );
//
//            boolean isContinuous = (boolean) mergeResult.getOrDefault("is_continuous", false);
//            String mergedQuestion = (String) mergeResult.getOrDefault("merged_question", question);
//            String previousQuestion = (String) mergeResult.get("previous_question");
            
//            String intentMessage = String.format(
//                "用户上一个问题为：%s，本次问题为：%s，%s",
//                previousQuestion != null ? previousQuestion : "无",
//                question,
//                isContinuous ?
//                    String.format("是对上一个问题的追问，故用户的问题为：%s", mergedQuestion) :
//                    String.format("不是对上一个问题的追问，故用户的问题为：%s", mergedQuestion)
//            );
//
//            sendProgress(emitter, "intent_analysis", "completed", Map.of(
//                "message", intentMessage,
//                "is_continuous", isContinuous,
//                "merged_question", mergedQuestion,
//                "previous_question", previousQuestion != null ? previousQuestion : ""
//            ), startTime);
            
            String workingQuestion = question;
            
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
//                mergedKeywords.get("keywords_en").addAll(dbKeywords.getOrDefault("keywords_en", new ArrayList<>());
            }
            
            // 去重
            mergedKeywords.put("keywords_cn", new ArrayList<>(new HashSet<>(mergedKeywords.get("keywords_cn"))));
//            mergedKeywords.put("keywords_en", new ArrayList<>(new HashSet<>(mergedKeywords.get("keywords_en"))));
            
            // 步骤4: 生成SQL（包含解释）
            sendProgress(emitter, "sql_generation", "processing", Map.of(
                "message", "正在生成查询语句..."
            ), startTime);
            
            com.nl2sql.model.dto.SQLResult sqlResult = sqlGeneratorService.generateSQLWithExplanation(
                workingQuestion, candidateTables, mergedKeywords
            );
            
            if (sqlResult == null || sqlResult.getSql() == null) {
                sendProgress(emitter, "sql_generation", "error", Map.of(
                    "message", "未能生成有效的查询语句",
                    "error", "未能生成有效的查询语句"
                ), startTime);
                emitter.complete();
                return;
            }
            
            String firstSql = sqlResult.getSql();
            String sqlExplanation = sqlResult.getExplanation();
            String model = sqlResult.getModel();
            
            String sqlPreview = firstSql;
            String sqlMessage = String.format("成功生成查询语句：%s", sqlPreview);
            
            Map<String, Object> sqlGenerationData = new HashMap<>();
            sqlGenerationData.put("message", sqlMessage);
            sqlGenerationData.put("generated_sql", firstSql);
            sqlGenerationData.put("sql_explanation", sqlExplanation != null ? sqlExplanation : "");
            sqlGenerationData.put("model", model != null ? model : "");
            
            sendProgress(emitter, "sql_generation", "completed", sqlGenerationData, startTime);
            
            // 步骤5: 执行SQL
            sendProgress(emitter, "sql_execution", "processing", Map.of(
                "message", "正在执行查询语句..."
            ), startTime);
            
            String successfulSql = firstSql;
            List<Map<String, Object>> successfulResults = new ArrayList<>();
            
            Map<String, Object> execResult = sqlGeneratorService.executeSQLEnhanced(firstSql, 10000);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) execResult.get("results");
            if (results != null) {
                successfulResults = results;
            }
            
            String execMessage = String.format("查询语句执行完成，返回 %d 行数据", successfulResults.size());
            sendProgress(emitter, "sql_execution", "completed", Map.of(
                "message", execMessage,
                "row_count", successfulResults.size(),
                "sql_results", successfulResults
            ), startTime);
            
            // 步骤6: 保存session
            String newSessionId = sessionService.saveQuestionToSession(workingQuestion, windowId, userId);

            // 计算处理时间
            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // 最终结果
            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("message", "查询处理完成");
            finalResult.put("question", question);
            finalResult.put("selected_databases", selectedDatabases);
            finalResult.put("candidate_tables", tableNames.stream().limit(5).collect(Collectors.toList()));
            finalResult.put("generated_sql", successfulSql);
            finalResult.put("sql_explanation", sqlExplanation != null ? sqlExplanation : "");
            finalResult.put("model", model != null ? model : "");
            finalResult.put("sql_results", successfulResults);
            finalResult.put("processing_time", processingTime);
            finalResult.put("session_id", newSessionId);
            finalResult.put("used_session_id", sessionId != null ? sessionId : "");
            
            sendProgress(emitter, "completed", "success", finalResult, startTime);
            
            emitter.complete();
            log.info("✅ 流式查询处理完成 - 耗时: {}秒\n\n", processingTime);
            
        } catch (IOException e) {
            // 捕获连接断开异常（用户取消查询）
            log.info("🛑 客户端已断开连接，停止处理 - {}", e.getMessage());
            // 不需要做任何处理，直接返回
            return;
        } catch (Exception e) {
            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            log.error("❌ 流式查询处理失败: {}", e.getMessage(), e);
            try {
                sendProgress(emitter, "error", "failed", Map.of(
                    "message", String.format("查询处理错误: %s", e.getMessage()),
                    "error", e.getMessage(),
                    "processing_time", processingTime
                ), startTime);
            } catch (IOException ex) {
                log.warn("⚠️ 发送错误事件失败，连接已断开");
            }
            emitter.completeWithError(e);
        }
    }

    /**
     * 发送进度信息 - 完全按照Python格式
     * 如果连接已断开，会抛出IOException，由调用者处理
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
        
        try {
            emitter.send(SseEmitter.event().data(jsonData));
        } catch (IOException e) {
            // 连接已断开，抛出异常让调用者处理
            throw e;
        }
    }
}
