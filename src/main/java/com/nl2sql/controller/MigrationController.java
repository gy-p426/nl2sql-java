package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.DataMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
}
