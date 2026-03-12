package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.DataMigrationService;
import com.nl2sql.service.StartupMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据迁移控制器
 */
@Slf4j
@RestController
@RequestMapping("/migration")
@RequiredArgsConstructor
@Tag(name = "数据迁移", description = "文件数据迁移到数据库")
public class MigrationController {

    private final DataMigrationService dataMigrationService;
    private final StartupMigrationService startupMigrationService;

    @PostMapping("/migrate-all")
    @Operation(summary = "执行所有数据迁移")
    public ApiResponse<Map<String, Object>> migrateAll() {
        try {
            log.info("🔄 开始执行数据迁移...");
            dataMigrationService.migrateAllData();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "数据迁移完成");
            
            return ApiResponse.success("迁移成功", result);
        } catch (Exception e) {
            log.error("❌ 数据迁移错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/history")
    @Operation(summary = "查看启动迁移历史（仅管理员）")
    public ApiResponse<Map<String, Object>> getMigrationHistory(@RequestParam Integer userId) {
        try {
            List<Map<String, Object>> items = startupMigrationService.getMigrationHistory(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("items", items);
            result.put("count", items.size());

            return ApiResponse.success("查询成功", result);
        } catch (Exception e) {
            log.error("❌ 查询启动迁移历史错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
