package com.nl2sql.util;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL LIMIT移除工具类
 */
public class SQLLimitRemover {
    
    private static final Logger log = LoggerFactory.getLogger(SQLLimitRemover.class);
    
    /**
     * 移除SQL中的LIMIT和OFFSET
     * 
     * @param sql 原始SQL
     * @return 移除LIMIT后的SQL
     */
    public static String removeLimit(String sql) {
        try {
            // 移除末尾分号
            sql = sql.replaceAll(";\\s*$", "").trim();
            
            // 使用JSqlParser解析SQL
            Statement statement = CCJSqlParserUtil.parse(sql);
            
            if (statement instanceof Select) {
                Select select = (Select) statement;
                
                // 处理PlainSelect
                if (select.getPlainSelect() != null) {
                    PlainSelect plainSelect = select.getPlainSelect();
                    plainSelect.setLimit(null);  // 移除LIMIT
                    log.debug("✅ 成功移除LIMIT");
                }
                
                // 移除顶层LIMIT（适用于UNION等）
                select.setLimit(null);
                
                return select.toString();
            }
            
            // 非SELECT语句，降级为字符串替换
            log.warn("⚠️ 非SELECT语句，使用字符串替换");
            return removeLimitByString(sql);
            
        } catch (Exception e) {
            // 解析失败，降级为字符串替换
            log.warn("⚠️ JSqlParser解析失败，降级为字符串替换: {}", e.getMessage());
            return removeLimitByString(sql);
        }
    }
    
    /**
     * 字符串替换方式移除LIMIT（降级方案）
     */
    private static String removeLimitByString(String sql) {
        sql = sql.replaceAll(";\\s*$", "").trim();
        
        // 移除LIMIT和OFFSET
        sql = sql.replaceAll("(?i)\\s+LIMIT\\s+\\d+", "");
        sql = sql.replaceAll("(?i)\\s+OFFSET\\s+\\d+", "");
        
        return sql;
    }
    
    /**
     * 检查SQL是否包含LIMIT
     */
    public static boolean hasLimit(String sql) {
        return sql.toUpperCase().contains("LIMIT");
    }
}
