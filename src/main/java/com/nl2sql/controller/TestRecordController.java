package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.TestRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试记录管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/test-records")
@RequiredArgsConstructor
@Tag(name = "测试记录管理", description = "测试记录CRUD接口")
public class TestRecordController {

    private final TestRecordService testRecordService;

    @GetMapping
    @Operation(summary = "获取测试记录列表")
    public ApiResponse<Map<String, Object>> getTestRecords(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            Map<String, Object> result = testRecordService.getTestRecords(search, status, page, pageSize);
            return ApiResponse.success("获取测试记录成功", result);
        } catch (Exception e) {
            log.error("❌ 获取测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "新增测试记录")
    public ApiResponse<Map<String, Object>> addTestRecord(
            @RequestParam("question") String question,
            @RequestParam("expected_result") String expectedResult,
            @RequestParam(value = "actual_result", required = false) String actualResult,
            @RequestParam(value = "status", defaultValue = "pending") String status,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        try {
            Map<String, Object> result = testRecordService.addTestRecord(
                question, expectedResult, actualResult, status, notes, images);
            return ApiResponse.success("新增成功", result);
        } catch (Exception e) {
            log.error("❌ 新增测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{testId}")
    @Operation(summary = "获取单个测试记录详情")
    public ApiResponse<Map<String, Object>> getTestRecord(@PathVariable String testId) {
        try {
            Map<String, Object> result = testRecordService.getTestRecord(testId);
            
            if (result.containsKey("error")) {
                return ApiResponse.error((String) result.get("error"));
            }
            
            return ApiResponse.success("获取测试记录成功", result);
        } catch (Exception e) {
            log.error("❌ 获取测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{testId}")
    @Operation(summary = "更新测试记录")
    public ApiResponse<Map<String, Object>> updateTestRecord(
            @PathVariable String testId,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) String expectedResult,
            @RequestParam(required = false) String actualResult,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "delete_images", required = false) List<String> deleteImages) {
        try {
            Map<String, Object> result = testRecordService.updateTestRecord(
                testId, question, expectedResult, actualResult, status, notes, images, deleteImages);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("更新成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 更新测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{testId}")
    @Operation(summary = "删除测试记录")
    public ApiResponse<Map<String, Object>> deleteTestRecord(@PathVariable String testId) {
        try {
            Map<String, Object> result = testRecordService.deleteTestRecord(testId);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("删除成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 删除测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/images/{filename}")
    @Operation(summary = "获取测试记录的图片")
    public ResponseEntity<Resource> getTestImage(@PathVariable String filename) {
        try {
            Resource resource = testRecordService.getTestImage(filename);
            
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
        } catch (Exception e) {
            log.error("❌ 获取测试图片错误: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/backup")
    @Operation(summary = "备份测试记录")
    public ApiResponse<Map<String, Object>> backupTestRecords() {
        try {
            Map<String, Object> result = testRecordService.backupTestRecords();
            return ApiResponse.success("备份成功", result);
        } catch (Exception e) {
            log.error("❌ 备份测试记录错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取测试统计信息")
    public ApiResponse<Map<String, Object>> getTestStats() {
        try {
            Map<String, Object> result = testRecordService.getTestStats();
            return ApiResponse.success("获取统计信息成功", result);
        } catch (Exception e) {
            log.error("❌ 获取测试统计信息错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
