package com.excelmanage.service;

import com.excelmanage.model.dto.FileUploadRequest;
import com.excelmanage.model.dto.FileUploadResponse;
import com.nl2sql.service.DatabasePoolService;
import com.nl2sql.service.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel管理服务
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelManageService {

    private final FileParseService fileParseService;
    private final DataTypeDetectionService dataTypeDetectionService;
    private final DatabaseTableService databaseTableService;
    private final DatabasePoolService databasePoolService;
    private final SchemaService schemaService;

    /**
     * 获取Excel列表
     * 
     * @return 结果Map
     */
    public Map<String, Object> getExcelList() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // TODO: 实现获取Excel列表的业务逻辑
            log.info("📋 获取Excel列表");
            
            result.put("success", true);
            result.put("message", "功能待实现");
            result.put("data", new HashMap<>());
            
        } catch (Exception e) {
            log.error("❌ 获取Excel列表失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "获取Excel列表失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 上传并解析Excel/CSV文件，创建数据库表
     * 
     * @param file 上传的文件
     * @param request 请求参数
     * @return 处理结果
     */
    @Transactional
    public FileUploadResponse uploadAndCreateTables(MultipartFile file, FileUploadRequest request) {
        List<FileUploadResponse.TableInfo> tables = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            // 1. 验证文件
            validateFile(file);
            
            // 2. 获取数据库名
            String databaseName = request.getDatabaseName() != null ?
                    request.getDatabaseName() : "exceldatabase";
            
            // 验证数据库是否存在
            if (!checkDatabaseExists(databaseName)) {
                errors.add("数据库 " + databaseName + " 不存在");
                return buildErrorResponse(errors);
            }
            
            // 3. 解析文件
            Map<String, FileParseService.SheetData> sheetDataMap;
            String fileName = file.getOriginalFilename();
            if (fileName == null) {
                errors.add("文件名不能为空");
                return buildErrorResponse(errors);
            }
            String fileExtension = getFileExtension(fileName);
            
            if ("csv".equalsIgnoreCase(fileExtension)) {
                // 解析CSV
                FileParseService.SheetData csvData = fileParseService.parseCsv(
                    file, 
                    request.getFirstRowAsHeader(), 
                    request.getSkipRows(), 
                    request.getMaxRows()
                );
                sheetDataMap = new LinkedHashMap<>();
                sheetDataMap.put(fileName, csvData);
            } else {
                // 解析Excel
                sheetDataMap = fileParseService.parseExcel(
                    file, 
                    request.getFirstRowAsHeader(), 
                    request.getSkipRows(), 
                    request.getMaxRows()
                );
            }
            
            if (sheetDataMap.isEmpty()) {
                errors.add("文件解析失败：未找到有效的数据。请检查文件是否为空或格式是否正确");
                return buildErrorResponse(errors, "文件解析失败：未找到有效的数据");
            }
            
            // 4. 解析自定义表名（如果提供）
            List<String> customTableNames = parseCustomTableNames(request.getTableNames(), sheetDataMap.size());
            
            // 5. 为每个Sheet创建表
            String tableNamePrefix = request.getTableNamePrefix();
            if (tableNamePrefix == null || tableNamePrefix.trim().isEmpty()) {
                // 使用文件名作为前缀（去掉扩展名）
                tableNamePrefix = fileName.substring(0, fileName.lastIndexOf('.'));
            }
            
            int sheetIndex = 0;
            for (Map.Entry<String, FileParseService.SheetData> entry : sheetDataMap.entrySet()) {
                String sheetName = entry.getKey();
                FileParseService.SheetData sheetData = entry.getValue();
                
                try {
                    // 验证数据格式
                    List<String> formatErrors = validateSheetData(sheetData, sheetName);
                    if (!formatErrors.isEmpty()) {
                        errors.addAll(formatErrors);
                        sheetIndex++;
                        continue;
                    }
                    
                    // 生成表名（优先使用自定义表名）
                    String tableName;
                    if (customTableNames != null && sheetIndex < customTableNames.size() 
                        && customTableNames.get(sheetIndex) != null 
                        && !customTableNames.get(sheetIndex).trim().isEmpty()) {
                        // 使用自定义表名（不添加时间戳，允许覆盖）
                        tableName = customTableNames.get(sheetIndex).trim();
                    } else {
                        // 使用默认规则生成表名（不添加时间戳，允许追加或覆盖）
                        tableName = generateTableName(tableNamePrefix, sheetName, sheetDataMap.size() > 1, false);
                    }
                    
                    // 检查表是否存在
                    boolean tableExists = databaseTableService.checkTableExists(databaseName, tableName);
                    
                    FileUploadResponse.TableInfo tableInfo;
                    
                    if (tableExists && !request.getOverwrite()) {
                        // 表已存在且不允许覆盖，尝试追加数据
                        log.info("📋 表 '{}' 已存在，检查结构是否匹配以追加数据...", tableName);
                        
                        // 检查表结构是否匹配
                        DatabaseTableService.TableStructureMatchResult matchResult = 
                            databaseTableService.checkTableStructureMatch(
                                databaseName, 
                                tableName, 
                                sheetData.getHeaders()
                            );
                        
                        if (!matchResult.getMatch()) {
                            // 结构不匹配，返回友好提示
                            String errorMsg = String.format(
                                "表 '%s' 已存在，但结构不匹配，无法追加数据。%s。如需覆盖，请设置参数 overwrite=true",
                                tableName, matchResult.getMessage());
                            errors.add(errorMsg);
                            log.warn("⚠️ {}", errorMsg);
                            sheetIndex++;
                            continue;
                        }
                        
                        // 结构匹配，追加数据
                        int appendedRows = databaseTableService.appendData(
                            databaseName,
                            tableName,
                            sheetData.getHeaders(),
                            sheetData.getRows()
                        );
                        
                        // 获取表的列信息用于响应
                        Map<String, String> tableColumns = databaseTableService.getTableColumns(databaseName, tableName);
                        List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos = 
                            detectColumnTypes(sheetData);
                        
                        tableInfo = FileUploadResponse.TableInfo.builder()
                                .tableName(tableName)
                                .sheetName(sheetName)
                                .rowCount(appendedRows)
                                .columnCount(tableColumns.size())
                                .columns(buildColumnInfos(sheetData.getHeaders(), columnTypeInfos))
                                .build();
                        
                        tables.add(tableInfo);
                        log.info("✅ 成功追加 {} 行数据到表: {} (Sheet: {})", appendedRows, tableName, sheetName);
                        
                    } else {
                        // 表不存在或允许覆盖，创建新表或覆盖
                        // 检测列类型
                        List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos = 
                            detectColumnTypes(sheetData);
                        
                        // 创建表（如果overwrite=true会先删除再创建）
                        DatabaseTableService.CreateTableResult createResult = 
                            databaseTableService.createTable(
                                databaseName,
                                tableName,
                                sheetData.getHeaders(),
                                sheetData.getRows(),
                                columnTypeInfos,
                                request.getOverwrite()
                            );
                        
                        // 构建表信息
                        tableInfo = FileUploadResponse.TableInfo.builder()
                                .tableName(createResult.getTableName())
                                .sheetName(sheetName)
                                .rowCount(createResult.getRowCount())
                                .columnCount(createResult.getColumnCount())
                                .columns(buildColumnInfos(sheetData.getHeaders(), columnTypeInfos))
                                .build();
                        
                        tables.add(tableInfo);
                        if (tableExists && request.getOverwrite()) {
                            log.info("✅ 成功覆盖表: {} (Sheet: {})", tableName, sheetName);
                        } else {
                            log.info("✅ 成功创建表: {} (Sheet: {})", tableName, sheetName);
                        }
                    }
                    
                } catch (IllegalArgumentException e) {
                    // 参数错误，返回友好提示
                    String errorMsg = String.format("Sheet '%s' 处理失败：%s", sheetName, getFriendlyErrorMessage(e));
                    errors.add(errorMsg);
                    log.error("❌ " + errorMsg, e);
                } catch (IllegalStateException e) {
                    // 状态错误（如表已存在），返回友好提示
                    String errorMsg = String.format("Sheet '%s' 处理失败：%s", sheetName, getFriendlyErrorMessage(e));
                    errors.add(errorMsg);
                    log.error("❌ " + errorMsg, e);
                } catch (Exception e) {
                    // 其他错误，返回友好提示
                    String errorMsg = String.format("Sheet '%s' 处理失败：%s", sheetName, getFriendlyErrorMessage(e));
                    errors.add(errorMsg);
                    log.error("❌ " + errorMsg, e);
                }
                sheetIndex++;
            }
            
            // 6. 构建响应
            if (tables.isEmpty() && !errors.isEmpty()) {
                return buildErrorResponse(errors, "所有Sheet处理失败，未创建或更新任何表");
            }
            
            String message;
            if (errors.isEmpty()) {
                message = String.format("成功处理 %d 个表", tables.size());
            } else {
                message = String.format("成功处理 %d 个表，%d 个Sheet处理失败", tables.size(), errors.size());
            }

            // 7. 自动刷新 NL2SQL 的 Schema 元数据（database_schema / table_columns）
            // 这样新创建的表可以被 NL2SQL 功能感知到
            if (!tables.isEmpty()) {
                try {
                    log.info("🔄 正在刷新数据库 {} 的 Schema 元数据……", databaseName);
                    // 只刷新当前发生变化的数据库，避免全量刷新带来的开销
                    schemaService.exportDatabaseSchema(databaseName, true);
                    log.info("✅ 数据库 {} 的 Schema 元数据刷新完成", databaseName);
                } catch (Exception e) {
                    String warnMsg = String.format(
                            "表已成功创建，但刷新数据库 %s 的 Schema 元数据失败：%s，请稍后手动调用 /refresh-schema 接口或重启应用刷新。",
                            databaseName, e.getMessage());
                    log.warn("⚠️ {}", warnMsg, e);
                    warnings.add(warnMsg);
                }
            }

            return FileUploadResponse.builder()
                    .success(tables.size() > 0)
                    .message(message)
                    .tables(tables)
                    .errors(errors.isEmpty() ? null : errors)
                    .warnings(warnings.isEmpty() ? null : warnings)
                    .build();
                    
        } catch (IllegalArgumentException e) {
            // 参数错误
            log.error("❌ 文件上传参数错误: {}", e.getMessage(), e);
            errors.add("参数错误：" + getFriendlyErrorMessage(e));
            return buildErrorResponse(errors, "文件上传失败：参数错误");
        } catch (Exception e) {
            // 其他错误
            log.error("❌ 上传文件处理失败: {}", e.getMessage(), e);
            errors.add("处理文件失败：" + getFriendlyErrorMessage(e));
            return buildErrorResponse(errors, "文件上传失败");
        }
    }

    /**
     * 解析自定义表名
     * 
     * @param tableNamesStr 表名字符串，多个用逗号分隔
     * @param sheetCount Sheet数量
     * @return 表名列表
     */
    private List<String> parseCustomTableNames(String tableNamesStr, int sheetCount) {
        if (tableNamesStr == null || tableNamesStr.trim().isEmpty()) {
            return null;
        }
        
        List<String> tableNames = new ArrayList<>();
        String[] names = tableNamesStr.split(",");
        
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                tableNames.add(trimmed);
            }
        }
        
        if (tableNames.isEmpty()) {
            return null;
        }
        
        // 如果表名数量少于Sheet数量，用null填充
        while (tableNames.size() < sheetCount) {
            tableNames.add(null);
        }
        
        return tableNames;
    }

    /**
     * 获取友好的错误信息
     */
    private String getFriendlyErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "未知错误，请查看日志获取详细信息";
        }
        
        // 数据库相关错误
        if (message.contains("Unknown database")) {
            return "数据库不存在，请检查数据库名称是否正确";
        }
        
        if (message.contains("Access denied")) {
            return "数据库访问被拒绝，请检查用户名和密码";
        }
        
        if (message.contains("连接池不存在")) {
            return "数据库连接失败，请检查数据库配置是否正确";
        }
        
        // 表相关错误
        if (message.contains("已存在") && message.contains("overwrite")) {
            return message; // 已经是友好提示
        }
        
        if (message.contains("Duplicate column name")) {
            return "列名重复，请检查Excel文件中的列名是否有重复";
        }
        
        if (message.contains("表名不合法")) {
            return "表名不合法，表名只能包含字母、数字、下划线和中文，且不能以数字开头";
        }
        
        // 文件解析错误
        if (message.contains("不支持的Excel文件格式")) {
            return "不支持的文件格式，仅支持 .xlsx、.xls 和 .csv 格式";
        }
        
        if (message.contains("文件不能为空")) {
            return "请选择要上传的文件";
        }
        
        if (message.contains("未找到有效的数据")) {
            return "文件中没有有效的数据，请检查文件内容";
        }
        
        // 文件大小错误
        if (message.contains("Maximum upload size exceeded") || 
            message.contains("文件大小超出限制") ||
            message.contains("max.*size")) {
            return "文件大小超出限制，最大支持 100MB，请压缩文件后重试";
        }
        
        // SQL错误
        if (message.contains("SQLSyntaxErrorException")) {
            return "SQL语法错误，可能是数据格式问题，请检查Excel文件内容";
        }
        
        // 返回原始错误信息（如果无法识别）
        return message;
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        String extension = getFileExtension(fileName);
        if (!isSupportedFileType(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension + "，仅支持 .xlsx, .xls, .csv");
        }
    }

    /**
     * 验证Sheet数据格式
     */
    private List<String> validateSheetData(FileParseService.SheetData sheetData, String sheetName) {
        List<String> errors = new ArrayList<>();
        
        if (sheetData.getHeaders() == null || sheetData.getHeaders().isEmpty()) {
            errors.add(String.format("Sheet '%s': 未找到列名", sheetName));
            return errors;
        }
        
        if (sheetData.getRows() == null || sheetData.getRows().isEmpty()) {
            errors.add(String.format("Sheet '%s': 未找到数据行", sheetName));
            return errors;
        }
        
        // 检查列名是否重复
        Set<String> headerSet = new HashSet<>();
        for (String header : sheetData.getHeaders()) {
            if (headerSet.contains(header)) {
                errors.add(String.format("Sheet '%s': 发现重复的列名: %s", sheetName, header));
            }
            headerSet.add(header);
        }
        
        return errors;
    }

    /**
     * 检测列类型
     */
    private List<DataTypeDetectionService.ColumnTypeInfo> detectColumnTypes(FileParseService.SheetData sheetData) {
        List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos = new ArrayList<>();
        
        int columnCount = sheetData.getHeaders().size();
        
        for (int i = 0; i < columnCount; i++) {
            final int columnIndex = i;
            // 提取该列的所有数据
            List<Object> columnData = sheetData.getRows().stream()
                    .map(row -> columnIndex < row.size() ? row.get(columnIndex) : null)
                    .collect(Collectors.toList());
            
            String columnName = sheetData.getHeaders().get(i);
            DataTypeDetectionService.ColumnTypeInfo typeInfo = 
                dataTypeDetectionService.detectColumnType(columnData, columnName);
            
            columnTypeInfos.add(typeInfo);
        }
        
        return columnTypeInfos;
    }

    /**
     * 构建列信息
     */
    private List<FileUploadResponse.ColumnInfo> buildColumnInfos(
            List<String> headers, 
            List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos) {
        
        List<FileUploadResponse.ColumnInfo> columnInfos = new ArrayList<>();
        
        for (int i = 0; i < headers.size(); i++) {
            String originalColumnName = headers.get(i);
            String dbColumnName = sanitizeColumnNameForDb(originalColumnName, i);
            DataTypeDetectionService.ColumnTypeInfo typeInfo = columnTypeInfos.get(i);
            
            FileUploadResponse.ColumnInfo columnInfo = FileUploadResponse.ColumnInfo.builder()
                    .columnName(originalColumnName)  // Excel中的原始列名
                    .dbColumnName(dbColumnName)       // 数据库中的实际列名
                    .dataType(typeInfo.getDataType())
                    .length(typeInfo.getLength())
                    .nullable(typeInfo.getNullable())
                    .build();
            
            columnInfos.add(columnInfo);
        }
        
        return columnInfos;
    }

    /**
     * 清理列名用于数据库（与DatabaseTableService中的逻辑一致）
     */
    private String sanitizeColumnNameForDb(String columnName, int index) {
        if (columnName == null || columnName.trim().isEmpty()) {
            return "column_" + (index + 1);
        }
        
        // 去除首尾空格
        String sanitized = columnName.trim();
        
        // 移除MySQL保留关键字和特殊字符（保留中文、字母、数字、下划线）
        // 只移除真正有问题的字符：空格、引号、反引号、分号等
        sanitized = sanitized.replaceAll("[\\s'\"`;]", "_");
        
        // 移除连续的下划线
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        // 移除首尾下划线
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // 如果清理后为空，使用默认名称
        if (sanitized.isEmpty()) {
            return "column_" + (index + 1);
        }
        
        // 限制长度
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        
        return sanitized;
    }

    /**
     * 生成表名
     * 
     * @param prefix 表名前缀
     * @param sheetName Sheet名称
     * @param multipleSheets 是否有多个Sheet
     * @param addTimestamp 是否添加时间戳（已废弃，保留参数以兼容，但不再使用）
     */
    private String generateTableName(String prefix, String sheetName, boolean multipleSheets, boolean addTimestamp) {
        String baseName = prefix;
        
        if (multipleSheets) {
            // 如果有多个Sheet，添加Sheet名称
            baseName = baseName + "_" + sanitizeForTableName(sheetName);
        }
        
        // 不再添加时间戳，使用固定表名以支持追加数据
        
        // 清理表名
        return sanitizeForTableName(baseName);
    }

    /**
     * 清理字符串用于表名
     */
    private String sanitizeForTableName(String name) {
        if (name == null) {
            return "";
        }
        
        // 移除非法字符，只保留字母、数字、下划线
        return name.replaceAll("[^a-zA-Z0-9_]", "_")
                   .replaceAll("_{2,}", "_")  // 多个下划线合并为一个
                   .replaceAll("^_|_$", "");  // 移除开头和结尾的下划线
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 检查是否为支持的文件类型
     */
    private boolean isSupportedFileType(String extension) {
        return "xlsx".equals(extension) || "xls".equals(extension) || "csv".equals(extension);
    }

    /**
     * 检查数据库是否存在
     */
    private boolean checkDatabaseExists(String databaseName) {
        try {
            Connection conn = databasePoolService.getConnection(databaseName);
            if (conn != null) {
                conn.close();
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ 数据库 {} 不存在或无法连接: {}", databaseName, e.getMessage());
        }
        return false;
    }

    /**
     * 构建错误响应
     */
    private FileUploadResponse buildErrorResponse(List<String> errors, String defaultMessage) {
        String message = defaultMessage != null ? defaultMessage : "处理失败";
        if (errors.size() == 1) {
            message = errors.get(0);
        }
        
        return FileUploadResponse.builder()
                .success(false)
                .message(message)
                .errors(errors)
                .tables(new ArrayList<>())
                .build();
    }
    
    private FileUploadResponse buildErrorResponse(List<String> errors) {
        return buildErrorResponse(errors, null);
    }
}
