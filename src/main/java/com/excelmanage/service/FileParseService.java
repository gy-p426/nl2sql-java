package com.excelmanage.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 文件解析服务
 * 支持Excel和CSV文件解析
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Slf4j
@Service
public class FileParseService {

    /**
     * 解析Excel文件
     * 
     * @param file 文件
     * @param firstRowAsHeader 第一行是否为列名
     * @param skipRows 跳过的行数
     * @param maxRows 最大行数（0表示不限制）
     * @return Map<Sheet名称, Sheet数据>
     */
    public Map<String, SheetData> parseExcel(MultipartFile file, 
                                             Boolean firstRowAsHeader, 
                                             Integer skipRows, 
                                             Integer maxRows) throws Exception {
        Map<String, SheetData> result = new LinkedHashMap<>();
        
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        Workbook workbook = null;
        try (InputStream is = file.getInputStream()) {
            // 根据文件扩展名选择不同的Workbook实现
            try {
                if (fileName.endsWith(".xlsx")) {
                    workbook = new XSSFWorkbook(is);
                } else if (fileName.endsWith(".xls")) {
                    workbook = new HSSFWorkbook(is);
                } else {
                    throw new IllegalArgumentException(
                        String.format("不支持的文件格式 '%s'，仅支持 .xlsx 和 .xls 格式", 
                            fileName.substring(fileName.lastIndexOf('.'))));
                }
            } catch (Exception e) {
                if (e instanceof IllegalArgumentException) {
                    throw e;
                }
                throw new IllegalArgumentException(
                    String.format("无法读取Excel文件 '%s'，文件可能已损坏或格式不正确：%s", fileName, e.getMessage()), e);
            }
            
            // 遍历所有Sheet
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    log.warn("⚠️ Sheet '{}' 为空，跳过", sheetName);
                    continue;
                }
                
                try {
                    SheetData sheetData = parseSheet(sheet, firstRowAsHeader, skipRows, maxRows);
                    
                    // 验证Sheet数据
                    if (sheetData.getHeaders() == null || sheetData.getHeaders().isEmpty()) {
                        throw new IllegalArgumentException(
                            String.format("Sheet '%s' 中没有找到列名，请检查文件格式或设置 firstRowAsHeader=false", sheetName));
                    }
                    
                    if (sheetData.getRows() == null || sheetData.getRows().isEmpty()) {
                        throw new IllegalArgumentException(
                            String.format("Sheet '%s' 中没有数据行，请检查文件内容", sheetName));
                    }
                    
                    result.put(sheetName, sheetData);
                } catch (Exception e) {
                    if (e instanceof IllegalArgumentException) {
                        throw new IllegalArgumentException(
                            String.format("解析Sheet '%s' 失败：%s", sheetName, e.getMessage()), e);
                    }
                    throw new Exception(
                        String.format("解析Sheet '%s' 时发生错误：%s", sheetName, e.getMessage()), e);
                }
            }
            
            if (result.isEmpty()) {
                if (workbook != null) {
                    workbook.close();
                }
                throw new IllegalArgumentException("Excel文件中没有有效的Sheet，所有Sheet都为空或无法解析");
            }
            
        } catch (IllegalArgumentException e) {
            if (workbook != null) {
                workbook.close();
            }
            throw e; // 重新抛出友好错误
        } catch (Exception e) {
            if (workbook != null) {
                workbook.close();
            }
            throw new Exception(
                String.format("解析Excel文件 '%s' 时发生错误：%s。请检查文件是否损坏或格式是否正确", fileName, e.getMessage()), e);
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
        
        return result;
    }

    /**
     * 解析CSV文件
     * 
     * @param file 文件
     * @param firstRowAsHeader 第一行是否为列名
     * @param skipRows 跳过的行数
     * @param maxRows 最大行数（0表示不限制）
     * @return Sheet数据
     */
    public SheetData parseCsv(MultipartFile file, 
                              Boolean firstRowAsHeader, 
                              Integer skipRows, 
                              Integer maxRows) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            fileName = "未知文件";
        }
        
        try (InputStream is = file.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            
            CSVFormat.Builder formatBuilder = CSVFormat.DEFAULT.builder();
            if (firstRowAsHeader) {
                formatBuilder.setHeader().setSkipHeaderRecord(true);
            }
            CSVFormat format = formatBuilder.build();
            
            CSVParser parser = new CSVParser(reader, format);
            List<CSVRecord> records = parser.getRecords();
            
            List<String> headers = new ArrayList<>();
            List<List<Object>> rows = new ArrayList<>();
            
            // 处理列名
            if (firstRowAsHeader && !records.isEmpty()) {
                CSVRecord headerRecord = records.get(0);
                for (int i = 0; i < headerRecord.size(); i++) {
                    String header = headerRecord.get(i);
                    headers.add(header != null ? header.trim() : "Column" + (i + 1));
                }
            } else {
                // 如果没有列名，使用默认列名
                if (!records.isEmpty()) {
                    CSVRecord firstRecord = records.get(0);
                    for (int i = 0; i < firstRecord.size(); i++) {
                        headers.add("Column" + (i + 1));
                    }
                }
            }
            
            // 跳过指定行数
            int startIndex = firstRowAsHeader ? 1 + skipRows : skipRows;
            int endIndex = maxRows > 0 ? Math.min(startIndex + maxRows, records.size()) : records.size();
            
            // 解析数据行
            for (int i = startIndex; i < endIndex; i++) {
                CSVRecord record = records.get(i);
                List<Object> row = new ArrayList<>();
                
                for (int j = 0; j < headers.size(); j++) {
                    String value = j < record.size() ? record.get(j) : "";
                    row.add(value != null ? value.trim() : null);
                }
                
                rows.add(row);
            }
            
            parser.close();
            
            return new SheetData(headers, rows);
        }
    }

    /**
     * 解析Sheet
     */
    private SheetData parseSheet(Sheet sheet, Boolean firstRowAsHeader, Integer skipRows, Integer maxRows) {
        List<String> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        
        int startRow = skipRows;
        if (firstRowAsHeader && sheet.getRow(0) != null) {
            // 读取第一行作为列名
            Row headerRow = sheet.getRow(0);
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String header = getCellValueAsString(cell);
                headers.add(header != null && !header.isEmpty() ? header : "Column" + (i + 1));
            }
            startRow = 1 + skipRows;
        } else {
            // 使用默认列名
            if (sheet.getRow(skipRows) != null) {
                Row firstDataRow = sheet.getRow(skipRows);
                for (int i = 0; i < firstDataRow.getLastCellNum(); i++) {
                    headers.add("Column" + (i + 1));
                }
            }
        }
        
        // 解析数据行
        int endRow = maxRows > 0 ? Math.min(startRow + maxRows, sheet.getLastRowNum() + 1) : sheet.getLastRowNum() + 1;
        
        for (int i = startRow; i < endRow; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            
            List<Object> rowData = new ArrayList<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                Object value = getCellValue(cell);
                rowData.add(value);
            }
            
            rows.add(rowData);
        }
        
        return new SheetData(headers, rows);
    }

    /**
     * 获取单元格值
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    double numValue = cell.getNumericCellValue();
                    // 判断是否为整数
                    if (numValue == Math.floor(numValue)) {
                        return (long) numValue;
                    } else {
                        return numValue;
                    }
                }
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return getCellValueAsString(cell);
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return null;
            default:
                return cell.toString();
        }
    }

    /**
     * 获取单元格值（字符串）
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return null;
            default:
                return cell.toString();
        }
    }

    /**
     * Sheet数据封装类
     */
    public static class SheetData {
        private final List<String> headers;
        private final List<List<Object>> rows;

        public SheetData(List<String> headers, List<List<Object>> rows) {
            this.headers = headers;
            this.rows = rows;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<List<Object>> getRows() {
            return rows;
        }
    }
}
