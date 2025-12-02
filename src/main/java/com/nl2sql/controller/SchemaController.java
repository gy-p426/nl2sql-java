package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.AnnotationService;
import com.nl2sql.service.SchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema管理控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Schema管理", description = "数据库Schema管理接口")
public class SchemaController {

    private final SchemaService schemaService;
    private final AnnotationService annotationService;

    @PostMapping("/refresh-schema")
    @Operation(summary = "刷新数据库Schema")
    public ApiResponse<Map<String, Object>> refreshSchema(
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        try {
            schemaService.exportAllSchemas(forceRefresh);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Schema刷新成功");
            result.put("force_refresh", forceRefresh);
            
            return ApiResponse.success("刷新成功", result);
        } catch (Exception e) {
            log.error("❌ 刷新Schema错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reload-annotations")
    @Operation(summary = "重新加载自定义注释")
    public ApiResponse<Map<String, Object>> reloadAnnotations() {
        try {
            annotationService.loadAnnotations();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "注释重新加载成功");
            
            return ApiResponse.success("重新加载成功", result);
        } catch (Exception e) {
            log.error("❌ 重新加载注释错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
