package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.model.dto.SaveQuestionRequest;
import com.nl2sql.model.dto.SaveQuestionResponse;
import com.nl2sql.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/session-cache")
@RequiredArgsConstructor
@Tag(name = "Session管理", description = "Session缓存管理接口")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "获取session缓存列表")
    public ApiResponse<Map<String, Object>> getSessionCache(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<Map<String, Object>> sessions = sessionService.getSessionCacheList(limit);
            
            Map<String, Object> result = new HashMap<>();
            result.put("sessions", sessions);
            result.put("total", sessions.size());
            
            return ApiResponse.success("获取session缓存成功", result);
        } catch (Exception e) {
            log.error("❌ 获取session缓存错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(summary = "清除session缓存")
    public ApiResponse<Map<String, Object>> clearSessionCache() {
        try {
            sessionService.clearSessionCache();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Session缓存已清除");
            
            return ApiResponse.success("清除成功", result);
        } catch (Exception e) {
            log.error("❌ 清除session缓存错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/save-question")
    @Operation(summary = "保存问题到Session", description = "将用户问题保存到Session历史记录中，用于后续的连续问题判断")
    public ApiResponse<SaveQuestionResponse> saveQuestion(@Valid @RequestBody SaveQuestionRequest request) {
        try {
            log.info("💾 收到保存问题请求 - 窗口: {}, 用户: {}, 问题: {}", 
                request.getWindowId(), request.getUserId(), request.getQuestion());
            
            // 保存问题到session
            String sessionId = sessionService.saveQuestionToSession(
                request.getQuestion(),
                request.getWindowId(),
                request.getUserId()
            );
            
            // 构建响应
            SaveQuestionResponse response = SaveQuestionResponse.builder()
                .sessionId(sessionId)
                .question(request.getQuestion())
                .windowId(request.getWindowId())
                .userId(request.getUserId())
                .timestamp(System.currentTimeMillis())
                .build();
            
            return ApiResponse.success("问题保存成功", response);
        } catch (Exception e) {
            log.error("❌ 保存问题错误: {}", e.getMessage(), e);
            return ApiResponse.error("保存问题失败: " + e.getMessage());
        }
    }
}
