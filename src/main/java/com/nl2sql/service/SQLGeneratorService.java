package com.nl2sql.service;

import com.nl2sql.client.VolcanoEngineClient;
import com.nl2sql.model.entity.TrainingData;
import com.nl2sql.repository.TrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SQLGeneratorService {

    private final VolcanoEngineClient volcanoEngineClient;
    private final TrainingDataRepository trainingDataRepository;
    private final DatabaseService databaseService;

    /**
     * 生成 SQL
     */
    public List<String> generateSQL(
            String question,
            List<String> candidateTables,
            Map<String, List<String>> keywords) {
        
        try {
            log.info("🎯 开始SQL生成 - 候选表数量: {}", candidateTables.size());
            
            if (candidateTables.isEmpty()) {
                log.error("❌ 候选表列表为空，无法生成SQL");
                return Collections.emptyList();
            }
            
            // 记录候选表信息
            for (int i = 0; i < Math.min(5, candidateTables.size()); i++) {
                String tableName = candidateTables.get(i).split("\\|\\|")[0];
                log.info("📋 候选表{}: {}", i + 1, tableName);
            }
            
            // 检索相关历史数据
            List<TrainingPair> relevantPairs = retrieveRelevantTrainingData(question, keywords, 5);
            log.info("🔍 检索到 {} 个相关历史示例", relevantPairs.size());
            
            // 构建提示词
            String prompt = buildSQLPrompt(question, candidateTables, relevantPairs);
            log.debug("📝 SQL生成提示词长度: {}字符", prompt.length());
            
            // 调用 AI 生成 SQL
            Map<String, Object> response = volcanoEngineClient.generate(prompt, 0.1);
            
            if (response.containsKey("error")) {
                log.warn("⚠️ SQL生成API调用失败，使用降级策略");
                return generateSQLFallback(question, candidateTables, keywords);
            }
            
            String rawResponse = (String) response.get("response");
            
            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                log.warn("⚠️ AI响应为空，使用降级策略");
                return generateSQLFallback(question, candidateTables, keywords);
            }
            
            log.info("📥 AI响应长度: {}字符", rawResponse.length());
            
            // 提取 SQL 语句
            List<String> sqls = extractSQLStatements(rawResponse);
            log.info("🔧 提取到 {} 个SQL语句", sqls.size());
            
            // SQL 验证和过滤
            List<String> validSqls = sqls.stream()
                .filter(this::validateSQLSecurity)
                .collect(Collectors.toList());
            
            log.info("✅ 安全验证通过 {} 个SQL语句", validSqls.size());
            
            if (validSqls.isEmpty()) {
                log.error("❌ 没有通过安全验证的SQL语句");
            }
            
            return validSqls.stream().limit(8).collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("❌ 生成SQL错误: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建 SQL 生成提示词
     */
    private String buildSQLPrompt(
            String question,
            List<String> candidateTables,
            List<TrainingPair> relevantPairs) {
        
        // 获取当前时间信息
        LocalDateTime now = LocalDateTime.now();
        String currentTimeInfo = String.format("""
            【当前时间信息】
            - 当前日期: %s
            - 当前年份: %d年
            - 当前月份: %d月
            - 当前星期: 星期%s
            
            ⚠️ 重要提示：
            - 如果用户问题涉及"今年"、"本年"，请使用 %d 年
            - 如果用户问题涉及"今天"、"本月"，请使用 %d年%d月
            - 时间范围查询请使用 BETWEEN 或 >= 和 <= 组合
            """,
            now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
            now.getYear(),
            now.getMonthValue(),
            getChineseWeekday(now.getDayOfWeek().getValue()),
            now.getYear(),
            now.getYear(),
            now.getMonthValue()
        );
        
        // 构建历史示例
        StringBuilder historyExamples = new StringBuilder();
        if (!relevantPairs.isEmpty()) {
            historyExamples.append("\n【参考：历史训练数据】\n");
            for (int i = 0; i < Math.min(5, relevantPairs.size()); i++) {
                TrainingPair pair = relevantPairs.get(i);
                historyExamples.append(String.format("示例 %d:\n问题: %s\nSQL: %s\n\n", 
                    i + 1, pair.question, pair.sql));
            }
            log.info("📚 构建了 {} 个历史示例", Math.min(5, relevantPairs.size()));
        }
        
        // 构建表信息
        StringBuilder tablesInfo = new StringBuilder();
        int tableCount = 0;
        
        for (int i = 0; i < Math.min(15, candidateTables.size()); i++) {
            String tableLine = candidateTables.get(i);
            String[] parts = tableLine.split("\\|\\|");
            
            if (parts.length >= 4) {
                String tableName = parts[0];
                String tableComment = parts[1];
                
                tablesInfo.append(String.format("\n表名: %s\n表注释: %s\n", tableName, tableComment));
                tablesInfo.append("列信息:\n");
                
                int colCount = 0;
                for (int j = 4; j < parts.length; j += 3) {
                    if (j + 2 < parts.length) {
                        String colName = parts[j];
                        String colComment = parts[j + 1];
                        String colType = parts[j + 2];
                        tablesInfo.append(String.format("  - %s (%s): %s\n", colName, colType, colComment));
                        colCount++;
                    }
                }
                tablesInfo.append("\n");
                tableCount++;
            }
        }
        
        if (tablesInfo.length() == 0) {
            log.error("❌ 无法解析候选表信息");
            return "";
        }
        
        log.info("📊 构建提示词完成 - 包含 {} 个表", tableCount);
        
        return String.format("""
            请根据用户问题生成SQL查询语句。
            
            %s
            
            %s
            
            【当前用户问题】
            %s
            
            【可用的数据库表结构】
            %s
            
            【SQL生成规则】
            1. 优先参考【历史训练数据】中的SQL模式
            2. 必须使用上面提供的真实表名和列名（表名格式：数据库名.表名）
            3. 生成1个高质量SQL方案，用```sql```包围
            4. 列名使用中文别名
            5. 注意有的表中有软删除条件（deleted = 0），有的表中没有软删除条件
            6. ⚠️ 如果问题涉及时间（如"今年"、"本月"、"上个月"等），请参考【当前时间信息】生成准确的时间条件
            7. ⚠️ 如果用户问题指定返回的列数，请你严格按照指定的列生成sql
            8. ⚠️ 如果用户问题指定返回的列名，请你严格按照指定的列名生成sql，如问题为“获取2025年8月每天的历史出车次数的日期、出车次数，共2列数据”，生成sql的列名一定为“日期、出车次数”
            
            请基于以上信息生成SQL：
            """,
            currentTimeInfo,
            historyExamples.toString(),
            question,
            tablesInfo.toString()
        );
    }

    /**
     * 提取 SQL 语句
     */
    private List<String> extractSQLStatements(String response) {
        List<String> sqls = new ArrayList<>();
        
        Pattern codeBlockPattern = Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = codeBlockPattern.matcher(response);
        
        while (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (sql.isEmpty()) continue;
            
            // 移除注释行
            String[] lines = sql.split("\n");
            List<String> cleanLines = new ArrayList<>();
            
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("--")) {
                    cleanLines.add(line);
                }
            }
            
            if (!cleanLines.isEmpty()) {
                String cleanSql = String.join("\n", cleanLines);
                String sqlUpper = cleanSql.toUpperCase().trim();
                
                // 检查是否以 SELECT 或 WITH 开头
                if (sqlUpper.startsWith("SELECT") || sqlUpper.startsWith("WITH")) {
                    sqls.add(cleanSql);
                    String sqlType = sqlUpper.startsWith("WITH") ? "WITH CTE" : "SELECT";
                    log.debug("✅ 提取有效{}语句: {}...", sqlType, 
                        cleanSql.substring(0, Math.min(50, cleanSql.length())));
                } else {
                    log.debug("❌ 非查询语句: {}...", 
                        cleanSql.substring(0, Math.min(50, cleanSql.length())));
                }
            }
        }
        
        return sqls;
    }

    /**
     * 验证 SQL 安全性
     */
    private boolean validateSQLSecurity(String sql) {
        String sqlUpper = sql.toUpperCase();
        
        // 移除字符串内容
        String cleanSql = sqlUpper.replaceAll("'[^']*'", "''");
        cleanSql = cleanSql.replaceAll("\"[^\"]*\"", "\"\"");
        
        // 危险操作检查
        String[] dangerousKeywords = {
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE",
            "TRUNCATE", "REPLACE", "LOAD_FILE", "INTO OUTFILE"
        };
        
        for (String keyword : dangerousKeywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = pattern.matcher(cleanSql);
            
            if (matcher.find()) {
                // 特殊处理：DELETED 字段不算危险
                if (keyword.equals("DELETE") && cleanSql.contains("DELETED")) {
                    continue;
                }
                log.warn("⚠️ SQL包含危险关键词: {}", keyword);
                return false;
            }
        }
        
        return true;
    }

    /**
     * 降级策略：基于规则的 SQL 生成
     */
    private List<String> generateSQLFallback(
            String question,
            List<String> candidateTables,
            Map<String, List<String>> keywords) {
        
        List<String> sqls = new ArrayList<>();
        
        if (candidateTables.isEmpty()) {
            return sqls;
        }
        
        // 获取第一个表作为主表
        String mainTableLine = candidateTables.get(0);
        String[] tableParts = mainTableLine.split("\\|\\|");
        
        if (tableParts.length < 4) {
            return sqls;
        }
        
        String tableName = tableParts[0];
        
        // 基于问题内容生成简单的 SQL
        if (question.matches(".*[多少|数量|总数|统计|计算].*")) {
            // 生成 COUNT 查询
            sqls.add(String.format("SELECT COUNT(*) AS '总数' FROM %s", tableName));
        } else {
            // 生成基本的 SELECT 查询
            sqls.add(String.format("SELECT * FROM %s LIMIT 10", tableName));
        }
        
        log.info("🔄 降级策略生成SQL - 表: {}, SQL数量: {}", tableName, sqls.size());
        
        return sqls;
    }

    /**
     * 执行SQL（增强版，带LIMIT）- 完全按照Python实现
     */
    public Map<String, Object> executeSQLEnhanced(String sql, int limit) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 添加LIMIT限制
            String originalSql = sql;
            if (!sql.toUpperCase().contains("LIMIT")) {
                sql = sql.replaceAll(";\\s*$", "") + " LIMIT " + limit;
                log.debug("🔧 添加LIMIT限制: {}", limit);
            }

            // 2. 检测涉及的数据库
            List<String> databases = detectDatabasesInSql(sql);
            log.debug("🎯 检测到数据库: {}", databases);

            if (databases.isEmpty()) {
                log.warn("⚠️ 未检测到数据库，SQL执行失败");
                result.put("results", new ArrayList<>());
                result.put("execution_time", (System.currentTimeMillis() - startTime) / 1000.0);
                result.put("success", false);
                result.put("error", "未检测到数据库");
                return result;
            }

            // 3. 使用第一个数据库的连接执行SQL
            String dbName = databases.get(0);
            log.info("💾 使用数据库 {} 执行SQL", dbName);

            try (Connection conn = databaseService.getConnection(dbName);
                 Statement stmt = conn.createStatement()) {

                log.debug("📝 执行SQL: {}", sql);

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    List<Map<String, Object>> results = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = rs.getObject(i);

                            // 转换为JSON可序列化格式
                            if (value == null) {
                                row.put(columnName, null);
                            } else if (value instanceof Number || value instanceof String || value instanceof Boolean) {
                                row.put(columnName, value);
                            } else {
                                row.put(columnName, value.toString());
                            }
                        }
                        results.add(row);
                    }

                    double executionTime = (System.currentTimeMillis() - startTime) / 1000.0;

                    log.info("📊 查询返回 {} 行数据", results.size());
                    log.info("✅ SQL执行完成 - 数据库: {}, 耗时: {:.3f}秒, 返回: {}行",
                            dbName, executionTime, results.size());

                    result.put("results", results);
                    result.put("execution_time", executionTime);
                    result.put("success", true);
                    result.put("database", dbName);

                    return result;
                }
            }

        } catch (Exception e) {
            double executionTime = (System.currentTimeMillis() - startTime) / 1000.0;
            log.error("❌ SQL执行错误: {}, 耗时: {:.3f}秒", e.getMessage(), executionTime);
            log.debug("💥 出错的SQL: {}", sql);

            result.put("results", new ArrayList<>());
            result.put("execution_time", executionTime);
            result.put("success", false);
            result.put("error", e.getMessage());

            return result;
        }
    }

    /**
     * 检测SQL中涉及的数据库 - 完全按照Python实现
     */
    private List<String> detectDatabasesInSql(String sql) {
        List<String> databases = new ArrayList<>();

        // 查找形如 database.table 的模式
        Pattern pattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.[a-zA-Z_][a-zA-Z0-9_]*");
        Matcher matcher = pattern.matcher(sql);

        List<String> availableDbs = databaseService.getAllDatabases();

        while (matcher.find()) {
            String match = matcher.group(1);
            // 只有当匹配到的名称在已知数据库列表中时才认为是数据库名
            if (availableDbs.contains(match) && !databases.contains(match)) {
                databases.add(match);
            }
        }

        return databases;
    }


    /**
     * 检索相关训练数据
     */
    private List<TrainingPair> retrieveRelevantTrainingData(
            String question,
            Map<String, List<String>> keywords,
            int topN) {
        
        try {
            // 从数据库读取所有训练数据
            List<TrainingData> trainingDataList = trainingDataRepository.findAll();
            
            if (trainingDataList.isEmpty()) {
                log.debug("数据库中没有训练数据");
                return Collections.emptyList();
            }
            
            // 转换为TrainingPair
            List<TrainingPair> pairs = trainingDataList.stream()
                .map(td -> new TrainingPair(td.getQuestion(), td.getSql()))
                .collect(Collectors.toList());
            
            // 基础相似度检索
            return basicSimilarityRetrieval(question, keywords, pairs, topN);
            
        } catch (Exception e) {
            log.error("❌ 检索历史数据时发生错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 基础相似度检索
     */
    private List<TrainingPair> basicSimilarityRetrieval(
            String question,
            Map<String, List<String>> keywords,
            List<TrainingPair> pairs,
            int topN) {
        
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(keywords.getOrDefault("keywords_cn", Collections.emptyList()));
        allKeywords.addAll(keywords.getOrDefault("keywords_en", Collections.emptyList()));
        
        List<ScoredPair> scoredPairs = new ArrayList<>();
        
        for (TrainingPair pair : pairs) {
            // 关键词匹配得分
            long keywordScore = allKeywords.stream()
                .filter(kw -> pair.question.contains(kw))
                .count();
            
            // 字符串相似度得分
            double similarity = calculateSimilarity(
                question.toLowerCase(), 
                pair.question.toLowerCase()
            );
            
            double totalScore = keywordScore * 2 + similarity * 5;
            scoredPairs.add(new ScoredPair(pair, totalScore));
        }
        
        scoredPairs.sort(Comparator.comparingDouble(sp -> -sp.score));
        
        log.info("🔍 基础检索完成，返回前{}个相似问题", topN);
        
        return scoredPairs.stream()
            .limit(topN)
            .map(sp -> sp.pair)
            .collect(Collectors.toList());
    }

    /**
     * 计算字符串相似度
     */
    private double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * 计算编辑距离
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }

    private String getChineseWeekday(int dayOfWeek) {
        String[] weekdays = {"一", "二", "三", "四", "五", "六", "日"};
        return weekdays[dayOfWeek - 1];
    }

    // 内部类
    
    private static class TrainingPair {
        String question;
        String sql;
        
        TrainingPair(String question, String sql) {
            this.question = question;
            this.sql = sql;
        }
    }

    private static class ScoredPair {
        TrainingPair pair;
        double score;
        
        ScoredPair(TrainingPair pair, double score) {
            this.pair = pair;
            this.score = score;
        }
    }
}
