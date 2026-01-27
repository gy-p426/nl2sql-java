package com.excelmanage.service;

import com.nl2sql.service.DatabasePoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据库表创建服务
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseTableService {

    private final DatabasePoolService databasePoolService;
    
    // 表名和列名的合法字符模式
    private static final Pattern VALID_TABLE_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern VALID_COLUMN_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 创建数据库表
     * 
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param headers 列名列表
     * @param rows 数据行
     * @param columnTypeInfos 列类型信息列表
     * @param overwrite 是否覆盖已存在的表
     * @return 创建结果
     */
    public CreateTableResult createTable(String databaseName, 
                                         String tableName, 
                                         List<String> headers, 
                                         List<List<Object>> rows,
                                         List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos,
                                         Boolean overwrite) throws Exception {
        
        // 验证表名
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        
        String validTableName = sanitizeTableName(tableName);
        if (!isValidTableName(validTableName)) {
            throw new IllegalArgumentException(
                String.format("表名 '%s' 不合法。表名只能包含字母、数字、下划线和中文，且不能以数字开头。清理后的表名：%s", 
                    tableName, validTableName));
        }

        try (Connection conn = databasePoolService.getConnection(databaseName);
             Statement stmt = conn.createStatement()) {
            
            // 检查表是否存在
            boolean tableExists = checkTableExists(stmt, validTableName);
            
            if (tableExists && !overwrite) {
                throw new IllegalStateException(
                    String.format("表 '%s' 已存在。如需覆盖，请设置参数 overwrite=true", validTableName));
            }
            
            // 如果表存在且需要覆盖，先删除
            if (tableExists && overwrite) {
                stmt.executeUpdate("DROP TABLE IF EXISTS `" + validTableName + "`");
                log.info("🗑️ 删除已存在的表: {}", validTableName);
            }
            
            // 构建CREATE TABLE语句
            String createTableSql = buildCreateTableSql(validTableName, headers, columnTypeInfos);
            log.info("📝 创建表SQL: {}", createTableSql);
            
            // 执行创建表
            try {
                stmt.executeUpdate(createTableSql);
                log.info("✅ 成功创建表: {}", validTableName);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null) {
                    if (errorMsg.contains("Duplicate column name")) {
                        throw new IllegalArgumentException(
                            String.format("创建表 '%s' 失败：列名重复。请检查Excel文件中的列名是否有重复", validTableName));
                    } else if (errorMsg.contains("You have an error in your SQL syntax")) {
                        throw new IllegalArgumentException(
                            String.format("创建表 '%s' 失败：SQL语法错误。可能是列名或数据类型有问题，请检查Excel文件格式", validTableName));
                    }
                }
                throw new Exception(
                    String.format("创建表 '%s' 失败：%s", validTableName, getFriendlyDbErrorMessage(e)), e);
            }
            
            // 插入数据
            int insertedRows = 0;
            try {
                insertedRows = insertData(stmt, validTableName, headers, rows);
                log.info("✅ 成功插入 {} 行数据到表: {}", insertedRows, validTableName);
            } catch (Exception e) {
                // 如果插入失败，尝试删除已创建的表
                try {
                    stmt.executeUpdate("DROP TABLE IF EXISTS `" + validTableName + "`");
                    log.warn("⚠️ 插入数据失败，已删除表: {}", validTableName);
                } catch (Exception dropError) {
                    log.error("❌ 删除表失败: {}", dropError.getMessage());
                }
                throw new Exception(
                    String.format("向表 '%s' 插入数据失败：%s。表已创建但未插入数据", validTableName, getFriendlyDbErrorMessage(e)), e);
            }
            
            return CreateTableResult.builder()
                    .success(true)
                    .tableName(validTableName)
                    .rowCount(insertedRows)
                    .columnCount(headers.size())
                    .build();
                    
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 参数或状态错误，直接抛出（已经是友好提示）
            throw e;
        } catch (Exception e) {
            log.error("❌ 创建表失败: {}", e.getMessage(), e);
            throw new Exception(
                String.format("创建表 '%s' 失败：%s", tableName, getFriendlyDbErrorMessage(e)), e);
        }
    }

    /**
     * 获取友好的数据库错误信息
     */
    private String getFriendlyDbErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "数据库操作失败，请查看日志获取详细信息";
        }
        
        if (message.contains("Unknown database")) {
            return "数据库不存在，请检查数据库名称是否正确";
        }
        
        if (message.contains("Access denied")) {
            return "数据库访问被拒绝，请检查用户名和密码";
        }
        
        if (message.contains("Table") && message.contains("doesn't exist")) {
            return "表不存在";
        }
        
        if (message.contains("Duplicate entry")) {
            // 提取主键冲突的详细信息
            if (message.contains("for key")) {
                // 格式：Duplicate entry 'xxx' for key 'PRIMARY'
                return "主键冲突：数据已存在，无法重复插入。如果是追加数据，请确保主键值不重复";
            }
            return "数据重复，可能是主键冲突。如果是追加数据，请确保主键值不重复";
        }
        
        if (message.contains("Data too long")) {
            return "数据长度超过列定义的最大长度";
        }
        
        if (message.contains("Incorrect date value")) {
            return "日期格式不正确，请检查日期数据格式";
        }
        
        if (message.contains("Incorrect integer value")) {
            return "整数格式不正确，请检查数值数据";
        }
        
        return message;
    }

    /**
     * 构建CREATE TABLE SQL语句
     */
    private String buildCreateTableSql(String tableName, 
                                      List<String> headers, 
                                      List<DataTypeDetectionService.ColumnTypeInfo> columnTypeInfos) {
        StringBuilder sql = new StringBuilder("CREATE TABLE `");
        sql.append(tableName).append("` (\n");
        
        // 检查是否已有id列（不区分大小写）
        boolean hasIdColumn = false;
        for (int i = 0; i < headers.size(); i++) {
            String sanitized = sanitizeColumnName(headers.get(i), i);
            if (sanitized.equalsIgnoreCase("id")) {
                hasIdColumn = true;
                break;
            }
        }
        
        // 如果没有id列，添加主键ID列
        if (!hasIdColumn) {
            sql.append("  `id` INT AUTO_INCREMENT PRIMARY KEY,\n");
        }
        
        // 添加数据列
        for (int i = 0; i < headers.size(); i++) {
            String columnName = sanitizeColumnName(headers.get(i), i);
            DataTypeDetectionService.ColumnTypeInfo typeInfo = columnTypeInfos.get(i);
            
            // 使用反引号包裹列名，支持中文列名
            sql.append("  `").append(columnName).append("` ");
            
            // 构建数据类型
            String dataType = typeInfo.getDataType();
            if ("VARCHAR".equals(dataType)) {
                sql.append("VARCHAR(").append(typeInfo.getLength()).append(")");
            } else if ("DECIMAL".equals(dataType)) {
                sql.append("DECIMAL(").append(typeInfo.getLength())
                   .append(",").append(typeInfo.getDecimalPlaces()).append(")");
            } else if ("TEXT".equals(dataType)) {
                sql.append("TEXT");
            } else {
                sql.append(dataType);
            }
            
            // 如果列名是id（不区分大小写），且Excel中已有id列，则设置为主键和自增
            if (columnName.equalsIgnoreCase("id") && hasIdColumn) {
                sql.append(" AUTO_INCREMENT PRIMARY KEY");
            }
            
            // 添加NULL约束
            if (!typeInfo.getNullable()) {
                sql.append(" NOT NULL");
            }
            
            sql.append(",\n");
        }
        
        // 移除最后一个逗号
        sql.setLength(sql.length() - 2);
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
        
        return sql.toString();
    }

    /**
     * 插入数据
     */
    private int insertData(Statement stmt, 
                          String tableName, 
                          List<String> headers, 
                          List<List<Object>> rows) throws Exception {
        if (rows.isEmpty()) {
            return 0;
        }
        
        // 构建列名
        List<String> validColumnNames = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            validColumnNames.add(sanitizeColumnName(headers.get(i), i));
        }
        
        StringBuilder columnNames = new StringBuilder("(");
        for (String colName : validColumnNames) {
            columnNames.append("`").append(colName).append("`, ");
        }
        columnNames.setLength(columnNames.length() - 2);
        columnNames.append(")");
        
        int insertedCount = 0;
        int batchSize = 1000; // 批量插入大小
        
        for (int i = 0; i < rows.size(); i += batchSize) {
            StringBuilder values = new StringBuilder();
            int endIndex = Math.min(i + batchSize, rows.size());
            
            for (int j = i; j < endIndex; j++) {
                List<Object> row = rows.get(j);
                values.append("(");
                
                for (int k = 0; k < validColumnNames.size(); k++) {
                    Object value = k < row.size() ? row.get(k) : null;
                    values.append(formatValue(value)).append(", ");
                }
                
                values.setLength(values.length() - 2);
                values.append("), ");
            }
            
            values.setLength(values.length() - 2);
            
            String insertSql = "INSERT INTO `" + tableName + "` " + columnNames + " VALUES " + values;
            int count = stmt.executeUpdate(insertSql);
            insertedCount += count;
        }
        
        return insertedCount;
    }

    /**
     * 格式化值用于SQL插入
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 转义单引号
        strValue = strValue.replace("'", "''");
        
        // 如果是数字或布尔值，不需要引号
        if (isNumeric(strValue) || isBoolean(strValue)) {
            return strValue;
        }
        
        return "'" + strValue + "'";
    }

    /**
     * 检查是否为数字
     */
    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查是否为布尔值
     */
    private boolean isBoolean(String value) {
        String lower = value.toLowerCase();
        return lower.equals("true") || lower.equals("false") || 
               lower.equals("1") || lower.equals("0");
    }

    /**
     * 检查表是否存在
     */
    public boolean checkTableExists(String databaseName, String tableName) throws Exception {
        try (Connection conn = databasePoolService.getConnection(databaseName);
             Statement stmt = conn.createStatement()) {
            String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                         "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查表是否存在（使用已有连接）
     */
    private boolean checkTableExists(Statement stmt, String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                     "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }
    
    /**
     * 获取表的列信息
     * 
     * @return Map<列名（清理后）, 原始列名>
     */
    public Map<String, String> getTableColumns(String databaseName, String tableName) throws Exception {
        Map<String, String> columns = new LinkedHashMap<>();
        try (Connection conn = databasePoolService.getConnection(databaseName);
             Statement stmt = conn.createStatement()) {
            String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                         "ORDER BY ORDINAL_POSITION";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    columns.put(columnName, columnName);
                }
            }
        }
        return columns;
    }
    
    /**
     * 检查表结构是否匹配（列名和顺序）
     * 
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param expectedHeaders Excel中的列名列表
     * @return 结构匹配结果
     */
    public TableStructureMatchResult checkTableStructureMatch(String databaseName, 
                                                               String tableName,
                                                               List<String> expectedHeaders) throws Exception {
        try (Connection conn = databasePoolService.getConnection(databaseName);
             Statement stmt = conn.createStatement()) {
            
            // 获取表的列信息（排除id列）
            List<String> tableColumns = new ArrayList<>();
            String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                         "AND COLUMN_NAME != 'id' " +
                         "ORDER BY ORDINAL_POSITION";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    tableColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
            
            // 清理Excel列名（排除id列，因为id列可能是自动添加的）
            List<String> sanitizedExpectedHeaders = new ArrayList<>();
            for (String header : expectedHeaders) {
                String sanitized = sanitizeColumnName(header, 0);
                // 排除id列（不区分大小写），因为id列可能是自动添加的主键
                if (!sanitized.equalsIgnoreCase("id")) {
                    sanitizedExpectedHeaders.add(sanitized);
                }
            }
            
            // 比较列名和顺序
            if (tableColumns.size() != sanitizedExpectedHeaders.size()) {
                return TableStructureMatchResult.builder()
                        .match(false)
                        .message(String.format("列数量不匹配：表中有 %d 列（排除id列），Excel中有 %d 列（排除id列）", 
                                tableColumns.size(), sanitizedExpectedHeaders.size()))
                        .build();
            }
            
            // 检查列名是否一致（按顺序）
            List<String> mismatchedColumns = new ArrayList<>();
            for (int i = 0; i < tableColumns.size(); i++) {
                String tableCol = tableColumns.get(i);
                String expectedCol = sanitizedExpectedHeaders.get(i);
                if (!tableCol.equalsIgnoreCase(expectedCol)) {
                    mismatchedColumns.add(String.format("第%d列：表中有 '%s'，Excel中有 '%s'", 
                            i + 1, tableCol, expectedCol));
                }
            }
            
            if (!mismatchedColumns.isEmpty()) {
                return TableStructureMatchResult.builder()
                        .match(false)
                        .message("列名或顺序不匹配：" + String.join("；", mismatchedColumns))
                        .build();
            }
            
            return TableStructureMatchResult.builder()
                    .match(true)
                    .message("表结构匹配，可以追加数据")
                    .build();
        }
    }
    
    /**
     * 追加数据到已存在的表
     * 
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param headers 列名列表（Excel中的原始列名）
     * @param rows 数据行
     * @return 追加的行数
     */
    public int appendData(String databaseName, 
                          String tableName,
                          List<String> headers,
                          List<List<Object>> rows) throws Exception {
        if (rows.isEmpty()) {
            return 0;
        }
        
        try (Connection conn = databasePoolService.getConnection(databaseName);
             Statement stmt = conn.createStatement()) {
            
            // 获取表的列信息（排除 AUTO_INCREMENT 的主键列）
            List<String> tableColumns = new ArrayList<>();
            List<String> primaryKeyColumns = new ArrayList<>(); // 主键列列表
            List<String> nonPrimaryKeyColumns = new ArrayList<>(); // 非主键列列表
            
            // 查询所有列信息
            String sql = "SELECT COLUMN_NAME, COLUMN_KEY, EXTRA " +
                         "FROM information_schema.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                         "ORDER BY ORDINAL_POSITION";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String columnKey = rs.getString("COLUMN_KEY");
                    String extra = rs.getString("EXTRA");
                    
                    // 排除 AUTO_INCREMENT 的主键列（通常是 id 列）
                    // 这些列由数据库自动生成，不需要从 Excel 插入
                    if ("PRI".equals(columnKey) && extra != null && extra.contains("auto_increment")) {
                        log.debug("排除 AUTO_INCREMENT 主键列: {}", columnName);
                        continue;
                    }
                    
                    tableColumns.add(columnName);
                    
                    // 记录主键列和非主键列
                    if ("PRI".equals(columnKey)) {
                        primaryKeyColumns.add(columnName);
                    } else {
                        nonPrimaryKeyColumns.add(columnName);
                    }
                }
            }
            
            // 清理Excel列名，建立映射
            Map<String, Integer> headerIndexMap = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                String sanitized = sanitizeColumnName(headers.get(i), i);
                headerIndexMap.put(sanitized, i);
            }
            
            // 构建列名SQL（按表的列顺序）
            StringBuilder columnNames = new StringBuilder("(");
            for (String colName : tableColumns) {
                columnNames.append("`").append(colName).append("`, ");
            }
            columnNames.setLength(columnNames.length() - 2);
            columnNames.append(")");
            
            // 批量插入数据
            int insertedCount = 0;
            int batchSize = 1000;
            
            try {
                for (int i = 0; i < rows.size(); i += batchSize) {
                    StringBuilder values = new StringBuilder();
                    int endIndex = Math.min(i + batchSize, rows.size());
                    
                    for (int j = i; j < endIndex; j++) {
                        List<Object> row = rows.get(j);
                        values.append("(");
                        
                        // 按表的列顺序插入数据
                        for (int k = 0; k < tableColumns.size(); k++) {
                            String tableCol = tableColumns.get(k);
                            Integer headerIndex = headerIndexMap.get(tableCol);
                            
                            Object value = null;
                            if (headerIndex != null && headerIndex < row.size()) {
                                value = row.get(headerIndex);
                            }
                            
                            values.append(formatValue(value)).append(", ");
                        }
                        
                        values.setLength(values.length() - 2);
                        values.append("), ");
                    }
                    
                    values.setLength(values.length() - 2);
                    
                    // 构建 INSERT ... ON DUPLICATE KEY UPDATE SQL
                    // 如果主键冲突，自动更新非主键列
                    String insertSql;
                    if (!primaryKeyColumns.isEmpty() && !nonPrimaryKeyColumns.isEmpty()) {
                        // 有主键列和非主键列，使用 ON DUPLICATE KEY UPDATE
                        StringBuilder updateClause = new StringBuilder(" ON DUPLICATE KEY UPDATE ");
                        for (String col : nonPrimaryKeyColumns) {
                            updateClause.append("`").append(col).append("`=VALUES(`").append(col).append("`), ");
                        }
                        updateClause.setLength(updateClause.length() - 2); // 移除最后的 ", "
                        
                        insertSql = "INSERT INTO `" + tableName + "` " + columnNames + " VALUES " + values + updateClause;
                        log.debug("使用 INSERT ... ON DUPLICATE KEY UPDATE: {}", insertSql);
                    } else {
                        // 没有主键列或没有非主键列，使用普通 INSERT
                        insertSql = "INSERT INTO `" + tableName + "` " + columnNames + " VALUES " + values;
                    }
                    
                    int count = stmt.executeUpdate(insertSql);
                    insertedCount += count;
                }
                
                log.info("✅ 成功追加 {} 行数据到表: {}", insertedCount, tableName);
                return insertedCount;
            } catch (Exception e) {
                String errorMsg = getFriendlyDbErrorMessage(e);
                log.error("❌ 追加数据到表 '{}' 失败: {}", tableName, errorMsg, e);
                throw new Exception(
                    String.format("向表 '%s' 追加数据失败：%s", tableName, errorMsg), e);
            }
        }
    }
    
    /**
     * 表结构匹配结果
     */
    @lombok.Data
    @lombok.Builder
    public static class TableStructureMatchResult {
        private Boolean match;
        private String message;
    }

    /**
     * 清理表名
     */
    private String sanitizeTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return "table_" + System.currentTimeMillis();
        }
        
        // 移除非法字符，只保留字母、数字、下划线
        String sanitized = tableName.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // 确保以字母或下划线开头
        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "t_" + sanitized;
        }
        
        // 限制长度（MySQL表名最大64字符）
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        
        return sanitized;
    }

    /**
     * 清理列名
     * MySQL 支持中文列名（使用 utf8mb4 字符集），只需清理特殊字符，保留中文
     */
    private String sanitizeColumnName(String columnName, int index) {
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
        
        // 限制长度（MySQL列名最大64字符，但中文字符可能占用多个字节）
        // 为了安全，限制字符数而不是字节数
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        
        return sanitized;
    }

    /**
     * 验证表名是否合法
     */
    private boolean isValidTableName(String tableName) {
        return VALID_TABLE_NAME.matcher(tableName).matches();
    }

    /**
     * 创建表结果
     */
    @lombok.Data
    @lombok.Builder
    public static class CreateTableResult {
        private Boolean success;
        private String tableName;
        private Integer rowCount;
        private Integer columnCount;
        private String error;
    }
}
