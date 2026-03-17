package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.TrainingDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 训练数据管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/training-data")
@RequiredArgsConstructor
@Tag(name = "训练数据管理", description = "训练数据CRUD接口")
public class TrainingDataController {

    private final TrainingDataService trainingDataService;

    @GetMapping
    @Operation(summary = "获取训练数据列表")
    public ApiResponse<Map<String, Object>> getTrainingData(
            @RequestParam Integer userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String database,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            Map<String, Object> result = trainingDataService.getTrainingData(userId, search, database, page, pageSize);
            return ApiResponse.success("获取训练数据成功", result);
        } catch (Exception e) {
            log.error("❌ 获取训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "新增训练数据")
    public ApiResponse<Map<String, Object>> addTrainingData(
            @RequestParam Integer userId,
            @RequestBody Map<String, String> request) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            String question = request.get("question");
            String sql = request.get("sql");
            String database = request.get("database");
            String description = request.get("description");
            
            if (question == null || question.trim().isEmpty()) {
                return ApiResponse.error("问题不能为空");
            }
            if (sql == null || sql.trim().isEmpty()) {
                return ApiResponse.error("SQL不能为空");
            }
            
            Map<String, Object> result = trainingDataService.addTrainingData(userId, question, sql, database, description);
            return ApiResponse.success("新增成功", result);
        } catch (Exception e) {
            log.error("❌ 新增训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "批量新增训练数据")
    public ApiResponse<Map<String, Object>> addTrainingDataBatch(
            @RequestParam Integer userId,
            @RequestBody Map<String, Object> request) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> dataList = (List<Map<String, String>>) request.get("data");
            
            if (dataList == null || dataList.isEmpty()) {
                return ApiResponse.error("数据列表不能为空");
            }
            
            Map<String, Object> result = trainingDataService.addTrainingDataBatch(userId, dataList);
            return ApiResponse.success("批量新增成功", result);
        } catch (Exception e) {
            log.error("❌ 批量新增训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件批量导入训练数据")
    public ApiResponse<Map<String, Object>> uploadTrainingData(
            @RequestParam Integer userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String database) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            
            Map<String, Object> result = trainingDataService.uploadTrainingData(userId, file, database);
            return ApiResponse.success("上传成功", result);
        } catch (Exception e) {
            log.error("❌ 上传训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改训练数据")
    public ApiResponse<Map<String, Object>> updateTrainingData(
            @RequestParam Integer userId,
            @PathVariable int id,
            @RequestBody Map<String, String> request) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            String question = request.get("question");
            String sql = request.get("sql");
            String database = request.get("database");
            String description = request.get("description");
            
            Map<String, Object> result = trainingDataService.updateTrainingData(userId, id, question, sql, database, description);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("修改成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 修改训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除训练数据")
    public ApiResponse<Map<String, Object>> deleteTrainingData(
            @RequestParam Integer userId,
            @PathVariable int id) {
        try {
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            Map<String, Object> result = trainingDataService.deleteTrainingData(userId, id);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("删除成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 删除训练数据错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
