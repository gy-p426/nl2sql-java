package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.model.dto.QueryRequest;
import com.nl2sql.model.dto.QueryResponse;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
@Tag(name = "查询接口", description = "NL2SQL 查询相关接口")
public class QueryController {

    private final NL2SQLService nl2sqlService;
    private final StreamingService streamingService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping
    @Operation(summary = "处理自然语言查询")
    public ApiResponse<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        try {
            log.info("📝 收到查询请求 - 窗口: {}, 问题: {}", 
                request.getWindowId(), request.getQuestion());
            
            QueryResponse response = nl2sqlService.processQuery(
                request.getQuestion(),
                request.getWindowId(),
                request.getSessionId()
            );
            
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("❌ 查询处理错误: {}", e.getMessage(), e);
            return ApiResponse.error("查询处理失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/query-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "处理自然语言查询（流式返回）")
    public SseEmitter queryStream(@Valid @RequestBody QueryRequest request) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时
        
        executorService.execute(() -> {
            log.info("🌊 收到流式查询请求 - 窗口: {}, 问题: {}", 
                request.getWindowId(), request.getQuestion());
            
            streamingService.processQueryStream(
                request.getQuestion(),
                request.getWindowId(),
                request.getSessionId(),
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
}
