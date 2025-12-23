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
import java.util.stream.Collectors;

/**
 * 训练数据管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataService {

    private final TrainingDataRepository trainingDataRepository;

    public Map<String, Object> getTrainingData(String search, String database, int page, int pageSize) {
        log.info("📋 获取训练数据列表 - 搜索: {}, 数据库: {}, 页码: {}", search, database, page);
        
        try {
            Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<TrainingData> dataPage;
            
            if (search != null && !search.trim().isEmpty()) {
                if (database != null && !database.trim().isEmpty()) {
                    dataPage = trainingDataRepository.searchByDatabaseAndQuestionOrSql(database, search, pageable);
                } else {
                    dataPage = trainingDataRepository.searchByQuestionOrSql(search, pageable);
                }
            } else if (database != null && !database.trim().isEmpty()) {
                dataPage = trainingDataRepository.findByDatabaseName(database, pageable);
            } else {
                dataPage = trainingDataRepository.findAll(pageable);
            }
            
            List<Map<String, Object>> dataList = dataPage.getContent().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", dataList);
            result.put("total", dataPage.getTotalElements());
            result.put("page", page);
            result.put("page_size", pageSize);
            result.put("total_pages", dataPage.getTotalPages());
            
            return result;
        } catch (Exception e) {
            log.error("❌ 获取训练数据列表错误: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("data", List.of());
            result.put("total", 0);
            result.put("page", page);
            result.put("page_size", pageSize);
            return result;
        }
    }

    @Transactional
    public Map<String, Object> addTrainingData(String question, String sql, String database, String description) {
        log.info("➕ 新增训练数据 - 问题: {}", question);
        
        try {
            TrainingData data = new TrainingData();
            data.setQuestion(question);
            data.setSql(sql);
            data.setDatabaseName(database);
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
    public Map<String, Object> addTrainingDataBatch(List<Map<String, String>> dataList) {
        log.info("➕ 批量新增训练数据 - 数量: {}", dataList.size());
        
        try {
            List<TrainingData> trainingDataList = dataList.stream()
                .map(item -> {
                    TrainingData data = new TrainingData();
                    data.setQuestion(item.get("question"));
                    data.setSql(item.get("sql"));
                    data.setDatabaseName(item.get("database"));
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
    public Map<String, Object> uploadTrainingData(MultipartFile file, String database) {
        log.info("📤 上传训练数据文件 - 文件名: {}", file.getOriginalFilename());
        
        try {
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
                            data.setDatabaseName(database);
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
                    data.setDatabaseName(database);
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
    public Map<String, Object> updateTrainingData(int id, String question, String sql, String database, String description) {
        log.info("✏️ 修改训练数据 - ID: {}", id);
        
        try {
            Optional<TrainingData> optionalData = trainingDataRepository.findById(id);
            
            if (optionalData.isEmpty()) {
                return Map.of("success", false, "error", "训练数据不存在");
            }
            
            TrainingData data = optionalData.get();
            
            if (question != null) data.setQuestion(question);
            if (sql != null) data.setSql(sql);
            if (database != null) data.setDatabaseName(database);
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
    public Map<String, Object> deleteTrainingData(int id) {
        log.info("🗑️ 删除训练数据 - ID: {}", id);
        
        try {
            if (!trainingDataRepository.existsById(id)) {
                return Map.of("success", false, "error", "训练数据不存在");
            }
            
            trainingDataRepository.deleteById(id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "训练数据删除成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 删除训练数据错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
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
