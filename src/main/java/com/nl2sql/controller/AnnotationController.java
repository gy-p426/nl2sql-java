package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.AnnotationService;
import com.nl2sql.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 注释管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/annotations")
@RequiredArgsConstructor
@Tag(name = "注释管理", description = "数据库表和列注释管理接口")
public class AnnotationController {

    private final AnnotationService annotationService;
    private final DatabaseService databaseService;

    @GetMapping("/databases")
    @Operation(summary = "获取所有数据库列表")
    public ApiResponse<List<String>> getDatabases(@RequestParam Integer userId) {
        try {
            List<String> databases = databaseService.getAllDatabases(userId);
            return ApiResponse.success(databases);
        } catch (Exception e) {
            log.error("❌ 获取数据库列表错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/databases/{dbName}")
    @Operation(summary = "获取指定数据库的完整结构")
    public ApiResponse<Map<String, Object>> getDatabaseSchema(@PathVariable String dbName,
                                                              @RequestParam Integer userId) {
        try {
            Map<String, Object> schema = annotationService.getDatabaseSchema(dbName, userId);
            
            if (schema.containsKey("error")) {
                return ApiResponse.error((String) schema.get("error"));
            }
            
            return ApiResponse.success(schema);
        } catch (Exception e) {
            log.error("❌ 获取数据库结构错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "更新单个注释")
    public ApiResponse<Map<String, Object>> updateAnnotation(
            @RequestBody Map<String, String> request) {
        
        try {
            String dbName = request.get("database");
            String tableName = request.get("table");
            String columnName = request.get("column");
            String comment = request.getOrDefault("comment", "");
            Integer userId = request.containsKey("userId") ? Integer.valueOf(request.get("userId")) : null;
            
            if (dbName == null || tableName == null || userId == null) {
                return ApiResponse.error("缺少必要参数: database, table, userId");
            }
            
            Map<String, Object> result = annotationService.updateAnnotation(
                dbName, tableName, columnName, comment, userId
            );
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
            
        } catch (Exception e) {
            log.error("❌ 更新注释错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
