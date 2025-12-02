package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL执行控制器
 */
@Slf4j
@RestController
@RequestMapping("/execute-sql")
@RequiredArgsConstructor
@Tag(name = "SQL执行", description = "SQL语句执行接口")
public class SqlExecutionController {

    private final DatabaseService databaseService;

    @PostMapping
    @Operation(summary = "执行SQL语句")
    public ApiResponse<Map<String, Object>> executeSql(@RequestBody Map<String, Object> request) {
        try {
            String sql = (String) request.get("sql");
            Integer limit = request.containsKey("limit") ? (Integer) request.get("limit") : 100;
            
            if (sql == null || sql.trim().isEmpty()) {
                return ApiResponse.error("SQL语句不能为空");
            }
            
            // 检测数据库
            String dbName = databaseService.detectDatabaseFromSql(sql);
            if (dbName == null) {
                return ApiResponse.error("无法从SQL中检测到数据库");
            }
            
            // 执行SQL
            List<Map<String, Object>> results = databaseService.executeQuery(sql, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("database", dbName);
            response.put("sql", sql);
            response.put("results", results);
            response.put("count", results.size());
            
            return ApiResponse.success("执行成功", response);
        } catch (Exception e) {
            log.error("❌ 执行SQL错误: {}", e.getMessage());
            return ApiResponse.error("执行失败: " + e.getMessage());
        }
    }
}
