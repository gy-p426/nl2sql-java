package com.excelmanage.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 数据类型智能检测服务
 * 
 * @author NL2SQL Team
 * @version 1.0
 */
@Slf4j
@Service
public class DataTypeDetectionService {

    // 日期时间格式模式
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[\\sT]\\d{2}:\\d{2}:\\d{2}");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}");

    /**
     * 检测列的数据类型
     * 
     * @param columnData 列的所有数据
     * @param columnName 列名
     * @return 数据类型信息
     */
    public ColumnTypeInfo detectColumnType(List<Object> columnData, String columnName) {
        if (columnData == null || columnData.isEmpty()) {
            // 如果列数据为空，默认使用VARCHAR
            return ColumnTypeInfo.builder()
                    .dataType("VARCHAR")
                    .length(255)
                    .nullable(true)
                    .build();
        }

        // 统计非空值
        long nonNullCount = columnData.stream().filter(v -> v != null && !v.toString().trim().isEmpty()).count();
        boolean nullable = nonNullCount < columnData.size();

        // 尝试检测数据类型
        DataType detectedType = detectType(columnData);
        
        ColumnTypeInfo.ColumnTypeInfoBuilder builder = ColumnTypeInfo.builder()
                .nullable(nullable);

        switch (detectedType) {
            case INTEGER:
                builder.dataType("INT")
                       .length(null);
                break;
            case BIGINT:
                builder.dataType("BIGINT")
                       .length(null);
                break;
            case DECIMAL:
                // 计算小数位数和总位数
                int[] precision = calculateDecimalPrecision(columnData);
                builder.dataType("DECIMAL")
                       .length(precision[0])  // 总位数
                       .decimalPlaces(precision[1]);  // 小数位数
                break;
            case DOUBLE:
                builder.dataType("DOUBLE")
                       .length(null);
                break;
            case DATE:
                builder.dataType("DATE")
                       .length(null);
                break;
            case DATETIME:
                builder.dataType("DATETIME")
                       .length(null);
                break;
            case TIME:
                builder.dataType("TIME")
                       .length(null);
                break;
            case BOOLEAN:
                builder.dataType("TINYINT")
                       .length(1);
                break;
            case VARCHAR:
            default:
                // 计算最大长度
                int maxLength = calculateMaxLength(columnData);
                // 如果长度超过65535，使用TEXT
                if (maxLength > 65535) {
                    builder.dataType("TEXT")
                           .length(null);
                } else {
                    // 预留一些空间，向上取整到100的倍数
                    int varcharLength = Math.max(255, ((maxLength / 100) + 1) * 100);
                    builder.dataType("VARCHAR")
                           .length(Math.min(varcharLength, 65535));
                }
                break;
        }

        return builder.build();
    }

    /**
     * 检测数据类型
     */
    private DataType detectType(List<Object> columnData) {
        boolean allInteger = true;
        boolean allNumeric = true;
        boolean allBoolean = true;
        boolean allDate = true;
        boolean allDateTime = true;
        boolean allTime = true;

        int sampleSize = Math.min(columnData.size(), 100); // 采样前100条数据

        for (int i = 0; i < sampleSize; i++) {
            Object value = columnData.get(i);
            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }

            String strValue = value.toString().trim();

            // 检查整数
            if (allInteger && !isInteger(strValue)) {
                allInteger = false;
            }

            // 检查数字
            if (allNumeric && !isNumeric(strValue)) {
                allNumeric = false;
            }

            // 检查布尔值
            if (allBoolean && !isBoolean(strValue)) {
                allBoolean = false;
            }

            // 检查日期
            if (allDate && !isDate(strValue)) {
                allDate = false;
            }

            // 检查日期时间
            if (allDateTime && !isDateTime(strValue)) {
                allDateTime = false;
            }

            // 检查时间
            if (allTime && !isTime(strValue)) {
                allTime = false;
            }
        }

        // 按优先级返回类型
        if (allBoolean) {
            return DataType.BOOLEAN;
        }
        if (allInteger) {
            // 检查是否超过INT范围
            if (isWithinIntRange(columnData)) {
                return DataType.INTEGER;
            } else {
                return DataType.BIGINT;
            }
        }
        if (allDateTime) {
            return DataType.DATETIME;
        }
        if (allDate) {
            return DataType.DATE;
        }
        if (allTime) {
            return DataType.TIME;
        }
        if (allNumeric) {
            return DataType.DECIMAL;
        }

        return DataType.VARCHAR;
    }

    /**
     * 判断是否为整数
     */
    private boolean isInteger(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断是否为数字
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
     * 判断是否为布尔值
     */
    private boolean isBoolean(String value) {
        String lower = value.toLowerCase();
        return lower.equals("true") || lower.equals("false") || 
               lower.equals("1") || lower.equals("0") ||
               lower.equals("是") || lower.equals("否") ||
               lower.equals("yes") || lower.equals("no");
    }

    /**
     * 判断是否为日期
     */
    private boolean isDate(String value) {
        if (DATE_PATTERN.matcher(value).matches()) {
            try {
                LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 判断是否为日期时间
     */
    private boolean isDateTime(String value) {
        if (DATETIME_PATTERN.matcher(value).matches()) {
            try {
                LocalDateTime.parse(value.replace(" ", "T"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 判断是否为时间
     */
    private boolean isTime(String value) {
        return TIME_PATTERN.matcher(value).matches();
    }

    /**
     * 检查是否在INT范围内
     */
    private boolean isWithinIntRange(List<Object> columnData) {
        for (Object value : columnData) {
            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }
            try {
                long longValue = Long.parseLong(value.toString().trim());
                if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算小数精度
     */
    private int[] calculateDecimalPrecision(List<Object> columnData) {
        int maxIntegerDigits = 0;
        int maxDecimalPlaces = 0;

        for (Object value : columnData) {
            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }

            try {
                BigDecimal decimal = new BigDecimal(value.toString().trim());
                String[] parts = decimal.toPlainString().split("\\.");
                
                int integerDigits = parts[0].length();
                int decimalPlaces = parts.length > 1 ? parts[1].length() : 0;

                maxIntegerDigits = Math.max(maxIntegerDigits, integerDigits);
                maxDecimalPlaces = Math.max(maxDecimalPlaces, decimalPlaces);
            } catch (NumberFormatException e) {
                // 忽略非数字值
            }
        }

        // 总位数 = 整数位数 + 小数位数
        int totalDigits = maxIntegerDigits + maxDecimalPlaces;
        // 至少保证10位总位数，2位小数
        totalDigits = Math.max(totalDigits, 10);
        maxDecimalPlaces = Math.max(maxDecimalPlaces, 2);

        return new int[]{totalDigits, maxDecimalPlaces};
    }

    /**
     * 计算最大长度
     */
    private int calculateMaxLength(List<Object> columnData) {
        int maxLength = 0;
        for (Object value : columnData) {
            if (value != null) {
                int length = value.toString().length();
                maxLength = Math.max(maxLength, length);
            }
        }
        return maxLength;
    }

    /**
     * 数据类型枚举
     */
    private enum DataType {
        INTEGER, BIGINT, DECIMAL, DOUBLE, DATE, DATETIME, TIME, BOOLEAN, VARCHAR
    }

    /**
     * 列类型信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ColumnTypeInfo {
        private String dataType;
        private Integer length;
        private Integer decimalPlaces;
        private Boolean nullable;
    }
}
