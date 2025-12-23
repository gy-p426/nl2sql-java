package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 窗口管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/windows")
@RequiredArgsConstructor
@Tag(name = "窗口管理", description = "多窗口上下文管理接口")
public class WindowController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "获取所有窗口列表")
    public ApiResponse<Map<String, Object>> getWindows(
            @RequestParam(required = false) Integer userId) {
        try {
            List<String> windows = sessionService.getWindowList(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("windows", windows);
            result.put("total", windows.size());
            if (userId != null) {
                result.put("user_id", userId);
            }
            
            return ApiResponse.success("获取窗口列表成功", result);
        } catch (Exception e) {
            log.error("❌ 获取窗口列表错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{windowId}")
    @Operation(summary = "获取指定窗口的详细信息")
    public ApiResponse<Map<String, Object>> getWindowInfo(
            @PathVariable String windowId,
            @RequestParam(required = false) Integer userId) {
        try {
            Map<String, Object> info = sessionService.getWindowInfo(windowId, userId);
            return ApiResponse.success("获取窗口信息成功", info);
        } catch (Exception e) {
            log.error("❌ 获取窗口信息错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{windowId}")
    @Operation(summary = "清除指定窗口的上下文")
    public ApiResponse<Map<String, Object>> clearWindowContext(
            @PathVariable String windowId,
            @RequestParam(required = false) Integer userId) {
        try {
            sessionService.clearWindowContext(windowId, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "窗口上下文已清除");
            result.put("window_id", windowId);
            if (userId != null) {
                result.put("user_id", userId);
            }
            
            return ApiResponse.success("清除成功", result);
        } catch (Exception e) {
            log.error("❌ 清除窗口上下文错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping
    @Operation(summary = "清除所有窗口的上下文")
    public ApiResponse<Map<String, Object>> clearAllWindows() {
        try {
            sessionService.clearAllWindows();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "所有窗口上下文已清除");
            
            return ApiResponse.success("清除成功", result);
        } catch (Exception e) {
            log.error("❌ 清除所有窗口错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{windowId}/sessions")
    @Operation(summary = "获取指定窗口的所有session列表")
    public ApiResponse<Map<String, Object>> getWindowSessions(
            @PathVariable String windowId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Integer userId) {
        try {
            List<Map<String, Object>> sessions = sessionService.getWindowSessions(windowId, limit, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("window_id", windowId);
            result.put("sessions", sessions);
            result.put("total", sessions.size());
            if (userId != null) {
                result.put("user_id", userId);
            }
            
            return ApiResponse.success("获取窗口session列表成功", result);
        } catch (Exception e) {
            log.error("❌ 获取窗口session列表错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
