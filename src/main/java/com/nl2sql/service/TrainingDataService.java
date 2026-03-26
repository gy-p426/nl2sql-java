package com.nl2sql.service;

import com.nl2sql.model.entity.TrainingData;
import com.nl2sql.repository.TrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * 训练数据管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataService {

    private final TrainingDataRepository trainingDataRepository;
    private final DatabaseAccessScopeService databaseAccessScopeService;

    public Map<String, Object> getTrainingData(Integer userId, String search, String database, int page, int pageSize) {
        log.info("📋 获取训练数据列表 - userId: {}, 搜索: {}, 数据库: {}, 页码: {}", userId, search, database, page);
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);

            String scopedDatabase = normalizeDatabase(database);
            if (scopedDatabase != null && !allowedDatabases.contains(scopedDatabase)) {
                throw new IllegalArgumentException("用户无权访问数据库: " + scopedDatabase);
            }

            String normalizedSearch = search == null ? null : search.trim().toLowerCase();

            List<TrainingData> scopedData = trainingDataRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(data -> canAccessTrainingDataScope(data.getDatabaseName(), allowedDatabases))
                .filter(data -> scopedDatabase == null || containsDatabase(data.getDatabaseName(), scopedDatabase))
                .filter(data -> normalizedSearch == null || normalizedSearch.isEmpty() || matchesSearch(data, normalizedSearch))
                .collect(Collectors.toList());

            int safePage = Math.max(page, 1);
            int safePageSize = Math.max(pageSize, 1);
            int fromIndex = Math.min((safePage - 1) * safePageSize, scopedData.size());
            int toIndex = Math.min(fromIndex + safePageSize, scopedData.size());

            List<Map<String, Object>> dataList = scopedData.subList(fromIndex, toIndex).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", dataList);
            result.put("total", scopedData.size());
            result.put("page", safePage);
            result.put("page_size", safePageSize);
            result.put("total_pages", (int) Math.ceil((double) scopedData.size() / safePageSize));
            
            return result;
        } catch (Exception e) {
            log.error("❌ 获取训练数据列表错误: {}", e.getMessage());
            return emptyPagedResult(page, pageSize);
        }
    }

    @Transactional
    public Map<String, Object> addTrainingData(Integer userId, String question, String sql, String database, String description) {
        log.info("➕ 新增训练数据 - userId: {}, 问题: {}", userId, question);
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);
            String scopedDatabase = normalizeAndValidateDatabaseScope(database, allowedDatabases);

            TrainingData data = new TrainingData();
            data.setQuestion(question);
            data.setSql(sql);
            data.setDatabaseName(scopedDatabase);
            data.setDescription(description);
            
            TrainingData saved = trainingDataRepository.save(data);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("id", saved.getId());
            result.put("message", "训练数据添加成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 新增训练数据错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> addTrainingDataBatch(Integer userId, List<Map<String, String>> dataList) {
        log.info("➕ 批量新增训练数据 - userId: {}, 数量: {}", userId, dataList.size());
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);

            List<TrainingData> trainingDataList = dataList.stream()
                .map(item -> {
                    TrainingData data = new TrainingData();
                    data.setQuestion(item.get("question"));
                    data.setSql(item.get("sql"));
                    data.setDatabaseName(normalizeAndValidateDatabaseScope(item.get("database"), allowedDatabases));
                    data.setDescription(item.get("description"));
                    return data;
                })
                .collect(Collectors.toList());
            
            trainingDataRepository.saveAll(trainingDataList);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("added", trainingDataList.size());
            result.put("message", "批量添加成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 批量新增训练数据错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> uploadTrainingData(Integer userId, MultipartFile file, String database) {
        log.info("📤 上传训练数据文件 - userId: {}, 文件名: {}", userId, file.getOriginalFilename());
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);
            String scopedDatabase = normalizeAndValidateDatabaseScope(database, allowedDatabases);

            List<TrainingData> trainingDataList = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                String currentQuestion = null;
                StringBuilder currentSql = new StringBuilder();
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    if (line.startsWith("问题：")) {
                        // 保存上一个问题-SQL对
                        if (currentQuestion != null && currentSql.length() > 0) {
                            TrainingData data = new TrainingData();
                            data.setQuestion(currentQuestion);
                            data.setSql(currentSql.toString().trim());
                            data.setDatabaseName(scopedDatabase);
                            trainingDataList.add(data);
                        }
                        
                        // 开始新的问题
                        currentQuestion = line.substring(3).trim();
                        currentSql = new StringBuilder();
                    } else if (line.startsWith("SQL：")) {
                        currentSql.append(line.substring(4).trim());
                    } else if (!line.isEmpty() && currentSql.length() > 0) {
                        currentSql.append("\n").append(line);
                    }
                }
                
                // 保存最后一个问题-SQL对
                if (currentQuestion != null && currentSql.length() > 0) {
                    TrainingData data = new TrainingData();
                    data.setQuestion(currentQuestion);
                    data.setSql(currentSql.toString().trim());
                    data.setDatabaseName(scopedDatabase);
                    trainingDataList.add(data);
                }
            }
            
            if (!trainingDataList.isEmpty()) {
                trainingDataRepository.saveAll(trainingDataList);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("added", trainingDataList.size());
            result.put("message", "文件上传成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 上传训练数据文件错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> updateTrainingData(Integer userId, int id, String question, String sql, String database, String description) {
        log.info("✏️ 修改训练数据 - userId: {}, ID: {}", userId, id);
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);

            Optional<TrainingData> optionalData = trainingDataRepository.findById(id);
            
            if (optionalData.isEmpty()) {
                return Map.of("success", false, "error", "训练数据不存在");
            }

            TrainingData data = optionalData.get();
            if (!canAccessTrainingDataScope(data.getDatabaseName(), allowedDatabases)) {
                return Map.of("success", false, "error", "无权限修改该训练数据");
            }
            
            if (question != null) data.setQuestion(question);
            if (sql != null) data.setSql(sql);
            if (database != null) {
                data.setDatabaseName(normalizeAndValidateDatabaseScope(database, allowedDatabases));
            }
            if (description != null) data.setDescription(description);
            
            trainingDataRepository.save(data);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "训练数据更新成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 修改训练数据错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> deleteTrainingData(Integer userId, int id) {
        log.info("🗑️ 删除训练数据 - userId: {}, ID: {}", userId, id);
        
        try {
            List<String> allowedDatabases = getAllowedDatabases(userId);

            Optional<TrainingData> scopedData = trainingDataRepository.findById(id);
            if (scopedData.isEmpty()) {
                return Map.of("success", false, "error", "训练数据不存在");
            }

            if (!canAccessTrainingDataScope(scopedData.get().getDatabaseName(), allowedDatabases)) {
                return Map.of("success", false, "error", "无权限删除该训练数据");
            }
            
            trainingDataRepository.delete(scopedData.get());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "训练数据删除成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 删除训练数据错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private Map<String, Object> emptyPagedResult(int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        result.put("data", List.of());
        result.put("total", 0);
        result.put("page", page);
        result.put("page_size", pageSize);
        result.put("total_pages", 0);
        return result;
    }

    private List<String> getAllowedDatabases(Integer userId) {
        List<String> allowedDatabases = databaseAccessScopeService.getAllowedDatabases(userId).stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(db -> !db.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        if (allowedDatabases.isEmpty()) {
            throw new IllegalArgumentException("当前用户未配置可访问数据库");
        }

        return allowedDatabases;
    }

    private String normalizeAndValidateDatabaseScope(String databaseScope, List<String> allowedDatabases) {
        List<String> databases = parseDatabaseScope(databaseScope);
        if (databases.isEmpty()) {
            throw new IllegalArgumentException("数据库不能为空");
        }

        for (String db : databases) {
            if (!allowedDatabases.contains(db)) {
                throw new IllegalArgumentException("用户无权访问数据库: " + db);
            }
        }

        return String.join(",", databases);
    }

    private String normalizeDatabase(String database) {
        if (database == null) {
            return null;
        }

        String trimmed = database.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> parseDatabaseScope(String databaseScope) {
        if (databaseScope == null || databaseScope.isBlank()) {
            return Collections.emptyList();
        }

        return Stream.of(databaseScope.split("[,;，\\s]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    private boolean canAccessTrainingDataScope(String databaseScope, List<String> allowedDatabases) {
        List<String> requiredDatabases = parseDatabaseScope(databaseScope);
        if (requiredDatabases.isEmpty()) {
            return false;
        }
        return allowedDatabases.containsAll(requiredDatabases);
    }

    private boolean containsDatabase(String databaseScope, String database) {
        return parseDatabaseScope(databaseScope).contains(database);
    }

    private boolean matchesSearch(TrainingData data, String normalizedSearch) {
        String question = data.getQuestion() == null ? "" : data.getQuestion().toLowerCase();
        String sql = data.getSql() == null ? "" : data.getSql().toLowerCase();
        return question.contains(normalizedSearch) || sql.contains(normalizedSearch);
    }
    
    private Map<String, Object> toMap(TrainingData data) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", data.getId());
        map.put("question", data.getQuestion());
        map.put("sql", data.getSql());
        map.put("database", data.getDatabaseName());
        map.put("description", data.getDescription());
        map.put("usage_count", data.getUsageCount());
        map.put("success_rate", data.getSuccessRate());
        map.put("created_at", data.getCreatedAt());
        map.put("updated_at", data.getUpdatedAt());
        return map;
    }
}
