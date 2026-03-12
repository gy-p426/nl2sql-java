package com.nl2sql.service;

import com.nl2sql.util.SQLLimitRemover;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * 数据导出服务
 */
@Service
public class ExportService {
    
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    
    private final DatabaseService databaseService;
    
    public ExportService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }
    
    /**
     * 导出SQL结果
     * 
     * @param sql SQL语句
     * @param format 导出格式（csv/excel）
     * @param maxRows 最大行数限制（防止OOM）
     * @param output 输出流
     * @return 导出的行数
     */
    public int exportSql(String sql, String format, 
                         Integer maxRows, OutputStream output) throws Exception {
        return exportSql(null, sql, format, maxRows, output);
    }

    public int exportSql(Integer userId, String sql, String format,
                         Integer maxRows, OutputStream output) throws Exception {
        
        log.info("📤 开始导出 - 格式: {}, 最大行数: {}", format, maxRows);
        log.debug("原始SQL: {}", sql);
        
        // 1. 移除LIMIT限制
        String unlimitedSql = SQLLimitRemover.removeLimit(sql);
        log.info("✅ 已移除LIMIT限制");
        log.debug("无限制SQL: {}", unlimitedSql);
        
        // 2. 根据格式导出
        int exportedRows;
        if ("csv".equalsIgnoreCase(format)) {
            exportedRows = exportToCsv(userId, unlimitedSql, maxRows, output);
        } else if ("excel".equalsIgnoreCase(format)) {
            exportedRows = exportToExcel(userId, unlimitedSql, maxRows, output);
        } else {
            throw new IllegalArgumentException("不支持的格式: " + format);
        }
        
        log.info("✅ 导出完成 - 共导出 {} 行", exportedRows);
        return exportedRows;
    }
    
    /**
     * 导出为CSV
     */
    private int exportToCsv(Integer userId, String sql, Integer maxRows, OutputStream output) throws Exception {
        int rowCount = 0;
        
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            
            // 写入BOM（Excel识别UTF-8）
            writer.write('\uFEFF');
            
            // 检测数据库并获取连接
            String dbName = detectDatabaseFromSql(userId, sql);
            
            try (Connection conn = userId != null
                    ? databaseService.getConnection(userId, dbName)
                    : databaseService.getConnection(dbName);
                 Statement stmt = conn.createStatement()) {
                
                // 配置流式查询
                stmt.setFetchSize(1000);
                
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    // 写入表头
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        writer.write(escapeCsv(columnName));
                        if (i < columnCount) {
                            writer.write(",");
                        }
                    }
                    writer.write("\n");
                    
                    // 写入数据行
                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            String strValue = value != null ? value.toString() : "";
                            writer.write(escapeCsv(strValue));
                            if (i < columnCount) {
                                writer.write(",");
                            }
                        }
                        writer.write("\n");
                        
                        rowCount++;
                        
                        // 每1000行刷新一次
                        if (rowCount % 1000 == 0) {
                            writer.flush();
                            log.debug("已导出 {} 行", rowCount);
                        }
                        
                        // 检查最大行数限制
                        if (maxRows != null && rowCount >= maxRows) {
                            log.warn("⚠️ 已达到最大行数限制: {}", maxRows);
                            break;
                        }
                    }
                    
                    writer.flush();
                }
            }
        }
        
        return rowCount;
    }
    
    /**
     * 导出为Excel
     */
    private int exportToExcel(Integer userId, String sql, Integer maxRows, OutputStream output) throws Exception {
        int rowCount = 0;
        
        // 使用SXSSFWorkbook（流式写入，内存占用小）
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(1000)) {
            Sheet sheet = workbook.createSheet("数据");
            
            // 检测数据库并获取连接
            String dbName = detectDatabaseFromSql(userId, sql);
            
            try (Connection conn = userId != null
                    ? databaseService.getConnection(userId, dbName)
                    : databaseService.getConnection(dbName);
                 Statement stmt = conn.createStatement()) {
                
                // 配置流式查询
                stmt.setFetchSize(1000);
                
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    // 创建表头样式
                    CellStyle headerStyle = createHeaderStyle(workbook);
                    
                    // 写入表头
                    Row headerRow = sheet.createRow(0);
                    for (int i = 1; i <= columnCount; i++) {
                        Cell cell = headerRow.createCell(i - 1);
                        cell.setCellValue(metaData.getColumnLabel(i));
                        cell.setCellStyle(headerStyle);
                    }
                    
                    // 写入数据行
                    int excelRowNum = 1;
                    while (rs.next()) {
                        Row dataRow = sheet.createRow(excelRowNum);
                        
                        for (int i = 1; i <= columnCount; i++) {
                            Cell cell = dataRow.createCell(i - 1);
                            Object value = rs.getObject(i);
                            
                            if (value == null) {
                                cell.setCellValue("");
                            } else if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else if (value instanceof java.util.Date) {
                                cell.setCellValue((java.util.Date) value);
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                        
                        excelRowNum++;
                        rowCount++;
                        
                        // 每1000行日志一次
                        if (rowCount % 1000 == 0) {
                            log.debug("已导出 {} 行", rowCount);
                        }
                        
                        // 检查最大行数限制
                        if (maxRows != null && rowCount >= maxRows) {
                            log.warn("⚠️ 已达到最大行数限制: {}", maxRows);
                            break;
                        }
                        
                        // Excel单个sheet最大行数限制
                        if (excelRowNum >= 1048576) {
                            log.warn("⚠️ 已达到Excel单sheet最大行数限制");
                            break;
                        }
                    }
                    
                    // 设置固定列宽（避免SXSSFWorkbook的autoSizeColumn问题）
                    // 256 = 1个字符宽度，20*256 = 20个字符宽度
                    for (int i = 0; i < columnCount; i++) {
                        sheet.setColumnWidth(i, 20 * 256);
                    }
                }
            }
            
            // 写入输出流
            workbook.write(output);
            output.flush();
        }
        
        return rowCount;
    }
    
    /**
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // 字体
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        // 背景色
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // 边框
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // 对齐
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * CSV转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        
        // 如果包含逗号、引号或换行符，需要用引号包围
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // 引号需要转义为两个引号
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        
        return value;
    }
    
    /**
     * 从SQL中检测数据库名
     */
    private String detectDatabaseFromSql(Integer userId, String sql) {
        String dbName = userId != null
            ? databaseService.detectDatabaseFromSql(userId, sql)
            : databaseService.detectDatabaseFromSql(sql);
        if (dbName == null) {
            throw new RuntimeException("无法从SQL中检测到数据库名");
        }
        return dbName;
    }
}
