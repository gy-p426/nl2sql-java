package com.nl2sql.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.dto.*;
import com.nl2sql.service.NL2SQLService;
import com.nl2sql.service.StreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "查询接口", description = "NL2SQL 查询相关接口")
public class QueryController {

    private final NL2SQLService nl2sqlService;
    private final StreamingService streamingService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper;

    @PostMapping("/query-db")
    @Operation(summary = "处理自然语言查询 - 第一阶段（数据库选择和表选择）")
    public ApiResponse<QueryDbResponse> queryDb(@Valid @RequestBody QueryDbRequest request) {
        try {
            log.info("📝 收到数据库查询请求 - 窗口: {}, 问题: {}", 
                request.getWindowId(), request.getQuestion());
            
            Map<String, Object> result = nl2sqlService.processQueryDb(
                request.getQuestion(),
                request.getWindowId(),
                request.getUserId()
            );
            
            if (!(Boolean) result.get("success")) {
                return ApiResponse.error((String) result.get("error"));
            }
            
            @SuppressWarnings("unchecked")
            QueryDbResponse response = QueryDbResponse.builder()
                .success((Boolean) result.get("success"))
                .sessionId((String) result.get("sessionId"))
                .question((String) result.get("question"))
                .selectedDatabases((List<String>) result.get("selectedDatabases"))
                .keywords((Map<String, List<String>>) result.get("keywords"))
                .candidateTables((List<String>) result.get("candidateTables"))
                .executionTime((Long) result.get("executionTime"))
                .build();
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("❌ 数据库查询处理错误: {}", e.getMessage(), e);
            return ApiResponse.error("数据库查询处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/query-sql")
    @Operation(summary = "处理自然语言查询 - 第二阶段（SQL生成和执行）")
    public ApiResponse<QuerySqlResponse> querySql(@Valid @RequestBody QuerySqlRequest request) {
        try {
            log.info("📝 收到SQL查询请求 - 窗口: {}, 问题: {}, 候选表数量: {}", 
                request.getWindowId(), request.getQuestion(), request.getCandidateTables().size());
            
            Map<String, Object> result = nl2sqlService.processQuerySql(
                request.getQuestion(),
                request.getCandidateTables(),
                request.getMergedKeywords()
            );
            
            if (!(Boolean) result.get("success")) {
                return ApiResponse.error((String) result.get("error"));
            }
            
            @SuppressWarnings("unchecked")
            QuerySqlResponse response = QuerySqlResponse.builder()
                .success((Boolean) result.get("success"))
                .question((String) result.get("question"))
                .sql((String) result.get("sql"))
                .sqlExplanation((String) result.get("sqlExplanation"))
                .model((String) result.get("model"))
                .results((List<Map<String, Object>>) result.get("results"))
                .resultCount((Integer) result.get("resultCount"))
                .executionTime((Long) result.get("executionTime"))
                .build();
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("❌ SQL查询处理错误: {}", e.getMessage(), e);
            return ApiResponse.error("SQL查询处理失败: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "处理自然语言查询")
    public ApiResponse<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        if(request.getUserId() == null) {
            ApiResponse.error("用户账号信息为空");
        }
        try {
            log.info("📝 收到查询请求 - 窗口: {}, 问题: {}", 
                request.getWindowId(), request.getQuestion());
            
            QueryResponse response = nl2sqlService.processQuery(
                request.getQuestion(),
                request.getWindowId(),
                request.getSessionId(),
                request.getUserId()
            );
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("❌ 查询处理错误: {}", e.getMessage(), e);
            return ApiResponse.error("查询处理失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/query-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "处理自然语言查询（流式返回 - POST）")
    public SseEmitter queryStream(@Valid @RequestBody QueryRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        Integer userId = request.getUserId();
        if (userId == null) {
            sendEmitterErrorMessage(emitter, "用户账号信息不能为空");
            // 直接返回 emitter，不再执行后续异步任务
            return emitter;
        }

        executorService.execute(() -> {
            log.info("🌊 收到流式查询请求 - 窗口: {}, 问题: {}", 
                request.getWindowId(), request.getQuestion());
            
            streamingService.processQueryStream(
                request.getQuestion(),
                request.getWindowId(),
                request.getSessionId(),
                request.getUserId(),
                emitter
            );
        });
        
        return emitter;
    }

    @GetMapping(value = "/query-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "处理自然语言查询（流式返回 - GET）")
    public SseEmitter queryStreamGet(
            @RequestParam String question,
            @RequestParam(defaultValue = "default-window") String windowId,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String sessionId) {
        
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        if (userId == null) {
            sendEmitterErrorMessage(emitter, "用户账号信息不能为空");
            // 直接返回 emitter，不再执行后续异步任务
            return emitter;
        }

        executorService.execute(() -> {
            log.info("🌊 收到GET流式查询请求 - 窗口: {}, 问题: {}", windowId, question);
            
            streamingService.processQueryStream(
                question,
                windowId,
                sessionId,
                userId,
                emitter
            );
        });
        
        return emitter;
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public ApiResponse<String> health() {
        return ApiResponse.success("NL2SQL Service is running");
    }

    private void sendEmitterErrorMessage(SseEmitter emitter, String errorMessage) {
        try {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("step", "error");
            errorData.put("status", "failed");
            errorData.put("message", errorMessage);
            String errJsonData = objectMapper.writeValueAsString(errorData);

            emitter.send(SseEmitter.event().data(errJsonData));
            // 关闭SSE连接，避免前端一直等待
            emitter.complete();
        } catch (IOException e) {
            // 捕获推送失败的异常，避免程序崩溃
            log.error("推送用户未登录错误信息失败", e);
            emitter.completeWithError(e);
        }
    }
}
