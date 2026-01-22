package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.client.OllamaClient;
import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.DatabaseOverview;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.nl2sql.repository.DatabaseOverviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库服务 - 基于 database_host_config 表的多数据库管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseService {

    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final DatabaseOverviewRepository databaseOverviewRepository;
    private final DatabasePoolService databasePoolService;
    private final VolcanoEngineClient volcanoEngineClient;
    private final OllamaClient ollamaClient;
    private final DynamicConfigProvider configProvider;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有数据库名称 - 基于 database_host_config 表
     */
    public List<String> getAllDatabases() {
        List<String> databases = new ArrayList<>();
        
        try {
            // 从 database_host_config 表获取所有激活的主机配置
            List<DatabaseHostConfig> activeHosts = databaseHostConfigRepository.findByIsActiveTrue();
            
            for (DatabaseHostConfig host : activeHosts) {
                try {
                    List<String> hostDatabases = objectMapper.readValue(
                        host.getDatabases(), 
                        new TypeReference<List<String>>() {}
                    );
                    databases.addAll(hostDatabases);
                } catch (Exception e) {
                    log.warn("⚠️ 解析主机 {} 的数据库配置失败: {}", host.getName(), e.getMessage());
                }
            }
            
            log.debug("📋 从配置表获取到 {} 个数据库: {}", databases.size(), databases);
            
        } catch (Exception e) {
            log.error("❌ 获取数据库列表失败: {}", e.getMessage());
        }
        
        return databases;
    }

    /**
     * 获取指定数据库的连接
     */
    public Connection getConnection(String dbName) throws SQLException {
        return databasePoolService.getConnection(dbName);
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
                // 去掉末尾的分号，然后添加LIMIT，最后加上分号
                limitedSql = sql.trim();
                if (limitedSql.endsWith(";")) {
                    limitedSql = limitedSql.substring(0, limitedSql.length() - 1);
                }
                limitedSql = limitedSql + " LIMIT " + limit + ";";
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
        return databasePoolService.testConnection(dbName);
    }

    /**
     * 智能选择数据库 - 完全按照Python实现
     */
    public List<String> selectDatabases(String question) {
        try {
            // 1. 获取数据库概览
            List<DatabaseOverview> overviews = databaseOverviewRepository.findByIsActiveTrue();
            
            if (overviews.isEmpty()) {
                log.warn("⚠️ 没有数据库概览，返回所有数据库");
                return getAllDatabases();
            }
            
            // 2. 构建数据库概览文本
            StringBuilder dbOverview = new StringBuilder();
            for (DatabaseOverview overview : overviews) {
                dbOverview.append(String.format("数据库名：%s。表名表注释：%s。\n",
                    overview.getDatabaseName(),
                    overview.getTableSummary() != null ? overview.getTableSummary() : ""
                ));
            }
            
            // 3. 构建提示词
            String prompt = String.format("""
                你是一个智能数据库选择助手。请根据用户问题和数据库概览，选择最相关的一个或多个数据库。
                
                [数据库概览]
                %s
                
                [数据库概览格式说明]
                每行代表一个数据库及其包含的表信息，格式为：
                数据库名：xxx。表名表注释：table1(注释1), table2(注释2), ...。
                
                重要提示：
                - "数据库名："后面的内容才是真正的数据库名
                - "表名表注释："后面的内容是该数据库包含的表信息，不是数据库名
                - 员工信息只保存在yibin2数据库中，若问题中涉及到员工信息，请一定包含yibin2数据库
                
                [选择规则]
                1. 仔细分析用户问题涉及的业务领域和数据需求
                2. 根据表名和表注释判断哪些数据库包含相关数据
                3. 如果问题涉及多个业务领域，可以选择多个数据库
                4. 优先选择包含与问题最相关表的数据库
                5. **必须只返回"数据库名："后面的数据库名，绝不能返回表名**
                6. 多个数据库名用逗号分隔，不要包含任何其他文字和标点符号
                
                [输出示例]
                如果选择yibin2数据库，只输出：yibin2
                如果选择多个数据库，只输出：yibin2,yibinhumanresources
                
                [用户问题]
                %s
                
                请仔细分析后，只返回与问题相关的数据库名（从"数据库名："后面提取，多个数据库用逗号分隔）：
                """, dbOverview, question);
            
            // 4. 调用AI模型
            Map<String, Object> response = callAIModel(prompt, 0.1);
            
            if (response.containsKey("error")) {
                log.warn("⚠️ API调用失败，使用降级策略选择所有数据库");
                return getAllDatabases();
            }
            
            String selectedDbsStr = ((String) response.get("response")).trim();
            
            if (selectedDbsStr == null || selectedDbsStr.isEmpty()) {
                log.warn("⚠️ AI响应为空，使用降级策略选择所有数据库");
                return getAllDatabases();
            }
            
            // 5. 解析数据库列表
            List<String> selectedDbs = Arrays.stream(selectedDbsStr.split(","))
                .map(String::trim)
                .filter(db -> !db.isEmpty())
                .collect(Collectors.toList());
            
            // 6. 验证数据库名是否有效
            List<String> availableDbs = getAllDatabases();
            List<String> validDbs = selectedDbs.stream()
                .filter(availableDbs::contains)
                .collect(Collectors.toList());
            
            if (validDbs.isEmpty()) {
                log.warn("⚠️ 未找到有效数据库，使用所有可用数据库");
                validDbs = availableDbs;
            }
            
            log.info("🎯 数据库选择结果 - 输入: {}..., 选择: {}", 
                question.substring(0, Math.min(50, question.length())), validDbs);
            
            return validDbs;
            
        } catch (Exception e) {
            log.error("❌ 数据库选择错误: {}", e.getMessage());
            log.warn("⚠️ 使用降级策略选择所有数据库");
            return getAllDatabases();
        }
    }

    /**
     * 调用AI模型（支持多种模型）
     */
    private Map<String, Object> callAIModel(String prompt, Double temperature) {
        String provider = configProvider.getModelProvider();
        
        try {
            switch (provider.toLowerCase()) {
                case "ollama":
                    if (configProvider.getOllamaEnabled()) {
                        log.debug("🤖 使用Ollama模型");
                        return ollamaClient.generate(prompt, temperature);
                    } else {
                        log.debug("⚠️ Ollama未启用，切换到火山引擎");
                        return volcanoEngineClient.generate(prompt, temperature);
                    }
                case "volcano_engine":
                default:
                    log.debug("🌋 使用火山引擎模型");
                    return volcanoEngineClient.generate(prompt, temperature);
            }
        } catch (Exception e) {
            log.error("❌ {}模型调用失败: {}", provider, e.getMessage());
            
            // 容错：如果当前模型失败，尝试备用模型
            if ("ollama".equals(provider.toLowerCase())) {
                log.info("🔄 Ollama失败，尝试火山引擎");
                try {
                    return volcanoEngineClient.generate(prompt, temperature);
                } catch (Exception e2) {
                    log.error("❌ 火山引擎备用调用也失败: {}", e2.getMessage());
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "所有AI模型都不可用");
                    return errorResponse;
                }
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                return errorResponse;
            }
        }
    }
}
