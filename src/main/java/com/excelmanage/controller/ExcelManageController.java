package com.excelmanage.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.excelmanage.model.dto.FileUploadRequest;
import com.excelmanage.model.dto.FileUploadResponse;
import com.excelmanage.service.ExcelManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Excel管理控制器
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/excel-manage")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Excel管理接口", description = "Excel文件管理相关接口")
public class ExcelManageController {

    private final ExcelManageService excelManageService;

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public ApiResponse<String> health() {
        return ApiResponse.success("Excel管理模块运行正常");
    }

    /**
     * 获取Excel列表
     */
    @GetMapping
    @Operation(summary = "获取Excel列表（示例）")
    public ApiResponse<Map<String, Object>> getExcelList() {
        log.info("📝 收到获取Excel列表请求");
        Map<String, Object> result = excelManageService.getExcelList();
        return ApiResponse.success(result);
    }

    /**
     * 上传Excel/CSV文件并创建数据库表
     */
    @PostMapping("/upload")
    @Operation(summary = "上传Excel/CSV文件并创建数据库表")
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tableNamePrefix", required = false) String tableNamePrefix,
            @RequestParam(value = "tableNames", required = false) String tableNames,
            @RequestParam(value = "overwrite", required = false, defaultValue = "false") Boolean overwrite,
            @RequestParam(value = "firstRowAsHeader", required = false, defaultValue = "true") Boolean firstRowAsHeader,
            @RequestParam(value = "skipRows", required = false, defaultValue = "0") Integer skipRows,
            @RequestParam(value = "maxRows", required = false, defaultValue = "0") Integer maxRows,
            @RequestParam(value = "databaseName", required = false, defaultValue = "exceldatabase") String databaseName) {
        
        log.info("📤 收到文件上传请求 - 文件名: {}, 大小: {} bytes", 
                file.getOriginalFilename(), file.getSize());
        
        try {
            // 构建请求对象
            FileUploadRequest request = new FileUploadRequest();
            request.setTableNamePrefix(tableNamePrefix);
            request.setTableNames(tableNames);
            request.setOverwrite(overwrite);
            request.setFirstRowAsHeader(firstRowAsHeader);
            request.setSkipRows(skipRows);
            request.setMaxRows(maxRows);
            request.setDatabaseName(databaseName);
            
            // 处理文件上传
            FileUploadResponse response = excelManageService.uploadAndCreateTables(file, request);
            
            if (response.getSuccess()) {
                return ApiResponse.success(response.getMessage(), response);
            } else {
                // 返回详细的错误信息
                String errorMessage = response.getMessage();
                if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                    errorMessage = String.join("；", response.getErrors());
                }
                return ApiResponse.error(errorMessage);
            }
            
        } catch (IllegalArgumentException e) {
            log.error("❌ 文件上传参数错误: {}", e.getMessage(), e);
            return ApiResponse.error("参数错误：" + e.getMessage());
        } catch (Exception e) {
            log.error("❌ 文件上传处理失败: {}", e.getMessage(), e);
            String friendlyMessage = getFriendlyErrorMessage(e);
            return ApiResponse.error("文件上传处理失败：" + friendlyMessage);
        }
    }

    /**
     * 获取友好的错误信息
     */
    private String getFriendlyErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "未知错误，请查看日志获取详细信息";
        }
        
        // 文件相关错误
        if (message.contains("文件不能为空")) {
            return "请选择要上传的文件";
        }
        
        if (message.contains("不支持的文件格式")) {
            return message;
        }
        
        if (message.contains("无法读取")) {
            return message;
        }
        
        // 文件大小错误
        if (message.contains("Maximum upload size exceeded") || 
            message.contains("文件大小超出限制") ||
            message.contains("max.*size")) {
            return "文件大小超出限制，最大支持 100MB，请压缩文件后重试";
        }
        
        // 数据库相关错误
        if (message.contains("数据库") && message.contains("不存在")) {
            return message;
        }
        
        if (message.contains("表") && message.contains("已存在")) {
            return message;
        }
        
        return message;
    }

    /**
     * 上传Excel文件（JSON格式参数，兼容旧接口）
     */
    @PostMapping
    @Operation(summary = "上传Excel文件（示例，已废弃，请使用/upload接口）")
    @Deprecated
    public ApiResponse<Map<String, Object>> uploadExcel(@RequestBody Map<String, Object> request) {
        log.warn("⚠️ 使用了已废弃的接口，请使用 /upload 接口");
        log.info("📝 收到上传Excel文件请求");
        Map<String, Object> result = excelManageService.getExcelList();
        return ApiResponse.success(result);
    }
}
