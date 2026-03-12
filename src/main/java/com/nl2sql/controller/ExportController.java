package com.nl2sql.controller;

import com.nl2sql.model.dto.ExportRequest;
import com.nl2sql.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/export")
public class ExportController {
    
    private static final Logger log = LoggerFactory.getLogger(ExportController.class);
    
    private final ExportService exportService;
    
    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }
    
    /**
     * 导出SQL结果
     * 
     * @param request 导出请求
     * @param response HTTP响应
     */
    @PostMapping
    public void exportSql(
            @Valid @RequestBody ExportRequest request,
            HttpServletResponse response) throws Exception {
        
        log.info("📤 收到导出请求 - 格式: {}, 最大行数: {}", 
            request.getFormat(), request.getMaxRows());
        log.debug("SQL: {}", request.getSql());

        if (request.getUserId() == null) {
            throw new IllegalArgumentException("用户账号信息不能为空");
        }
        
        // 设置响应头
        String filename = generateFilename(request.getFormat());
        String contentType = getContentType(request.getFormat());
        
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, 
            "attachment; filename=\"" + filename + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        
        try {
            // 执行导出
            long startTime = System.currentTimeMillis();
            int exportedRows = exportService.exportSql(
                request.getUserId(),
                request.getSql(),
                request.getFormat(),
                request.getMaxRows(),
                response.getOutputStream()
            );
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("✅ 导出成功 - 行数: {}, 耗时: {}ms", exportedRows, duration);
            
        } catch (Exception e) {
            log.error("❌ 导出失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 生成文件名
     */
    private String generateFilename(String format) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Excel格式使用.xlsx后缀，其他格式使用原格式名
        String extension = "excel".equalsIgnoreCase(format) ? "xlsx" : format.toLowerCase();
        
        return "export_" + timestamp + "." + extension;
    }
    
    /**
     * 获取Content-Type
     */
    private String getContentType(String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> "text/csv; charset=utf-8";
            case "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
}
