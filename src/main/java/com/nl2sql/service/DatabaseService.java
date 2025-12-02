package com.nl2sql.service;

import com.nl2sql.config.NL2SQLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * 数据库服务 - 多数据库管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final Map<String, Map<String, DataSource>> databasePools;
    private final NL2SQLProperties properties;

    /**
     * 获取所有数据库名称
     */
    public List<String> getAllDatabases() {
        List<String> databases = new ArrayList<>();
        databasePools.values().forEach(hostPools -> 
            databases.addAll(hostPools.keySet())
        );
        return databases;
    }

    /**
     * 获取指定数据库的连接
     */
    public Connection getConnection(String dbName) throws SQLException {
        for (Map<String, DataSource> hostPools : databasePools.values()) {
            DataSource dataSource = hostPools.get(dbName);
            if (dataSource != null) {
                return dataSource.getConnection();
            }
        }
        throw new SQLException("数据库 " + dbName + " 不存在");
    }

    /**
     * 执行SQL查询
     */
    public List<Map<String, Object>> executeQuery(String sql, int limit) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        
        // 检测SQL中使用的数据库
        String dbName = detectDatabaseFromSql(sql);
        if (dbName == null) {
            throw new SQLException("无法从SQL中检测到数据库名");
        }

        try (Connection conn = getConnection(dbName);
             Statement stmt = conn.createStatement()) {
            
            // 添加LIMIT限制
            String limitedSql = sql;
            if (!sql.toUpperCase().contains("LIMIT")) {
                limitedSql = sql + " LIMIT " + limit;
            }
            
            log.info("🔍 执行SQL: {}", limitedSql);
            
            try (ResultSet rs = stmt.executeQuery(limitedSql)) {
                int columnCount = rs.getMetaData().getColumnCount();
                
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rs.getMetaData().getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }
        }
        
        log.info("✅ 查询完成，返回 {} 条记录", results.size());
        return results;
    }

    /**
     * 从SQL中检测数据库名
     */
    public String detectDatabaseFromSql(String sql) {
        List<String> allDatabases = getAllDatabases();
        
        for (String dbName : allDatabases) {
            if (sql.contains(dbName + ".")) {
                return dbName;
            }
        }
        
        return allDatabases.isEmpty() ? null : allDatabases.get(0);
    }

    /**
     * 测试数据库连接
     */
    public boolean testConnection(String dbName) {
        try (Connection conn = getConnection(dbName)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("❌ 数据库 {} 连接测试失败: {}", dbName, e.getMessage());
            return false;
        }
    }
}
