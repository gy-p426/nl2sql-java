package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import com.nl2sql.model.entity.CustomAnnotation;
import com.nl2sql.model.entity.SessionHistory;
import com.nl2sql.model.entity.TrainingData;
import com.nl2sql.repository.CustomAnnotationRepository;
import com.nl2sql.repository.SessionHistoryRepository;
import com.nl2sql.repository.TrainingDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据迁移服务 - 将文件数据导入数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationService {

    private final NL2SQLProperties properties;
    private final TrainingDataRepository trainingDataRepository;
    private final CustomAnnotationRepository annotationRepository;
    private final SessionHistoryRepository sessionHistoryRepository;
    private final ObjectMapper objectMapper;

    /**
     * 执行数据迁移
     */
    public void migrateAllData() {
        log.info("🔄 开始数据迁移...");
        
        try {
            // 1. 迁移训练数据
            migrateTrainingData();
            
            // 2. 迁移注释数据
            migrateAnnotations();
            
            // 3. 迁移会话数据
            migrateSessions();
            
            log.info("✅ 数据迁移完成");
        } catch (Exception e) {
            log.error("❌ 数据迁移失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 迁移训练数据
     */
    private void migrateTrainingData() {
        String trainFile = properties.getFiles().getSchemaDir() + "/train-m.txt";
        File file = new File(trainFile);
        
        if (!file.exists()) {
            log.info("📝 训练数据文件不存在，跳过迁移");
            return;
        }
        
        // 检查数据库中是否已有数据
        long count = trainingDataRepository.count();
        if (count > 0) {
            log.info("📝 数据库中已有 {} 条训练数据，跳过迁移", count);
            return;
        }
        
        try {
            log.info("📝 开始迁移训练数据...");
            
            String content = Files.readString(Paths.get(trainFile));
            List<TrainingData> trainingDataList = new ArrayList<>();
            
            // 使用正则表达式处理跨平台换行符（\r\n 或 \n）
            // 分割标准：连续两个或以上的换行符
            String[] blocks = content.split("(?:\\r?\\n){2,}");
            
            log.info("📊 解析到 {} 个数据块", blocks.length);
            
            for (String block : blocks) {
                if (block.trim().isEmpty()) continue;
                
                // 按行分割，兼容 \r\n 和 \n
                String[] lines = block.split("\\r?\\n");
                String question = null;
                StringBuilder sql = new StringBuilder();
                
                for (String line : lines) {
                    line = line.trim();
                    if (line.startsWith("问题：")) {
                        question = line.substring(3).trim();
                    } else if (line.startsWith("SQL：")) {
                        sql.append(line.substring(4).trim());
                    } else if (!line.isEmpty() && sql.length() > 0) {
                        sql.append("\n").append(line);
                    }
                }
                
                if (question != null && sql.length() > 0) {
                    TrainingData data = new TrainingData();
                    data.setQuestion(question);
                    data.setSql(sql.toString().trim());
                    trainingDataList.add(data);
                }
            }
            
            if (!trainingDataList.isEmpty()) {
                trainingDataRepository.saveAll(trainingDataList);
                log.info("✅ 成功迁移 {} 条训练数据", trainingDataList.size());
                
                // 备份原文件
                backupFile(trainFile);
            }
        } catch (Exception e) {
            log.error("❌ 迁移训练数据失败: {}", e.getMessage());
            // 不抛出异常，允许其他迁移继续
        }
    }

    /**
     * 迁移注释数据
     */
    @SuppressWarnings("unchecked")
    private void migrateAnnotations() {
        String annotationFile = properties.getFiles().getAnnotation();
        File file = new File(annotationFile);
        
        if (!file.exists()) {
            log.info("📝 注释文件不存在，跳过迁移");
            return;
        }
        
        // 检查数据库中是否已有数据
        long count = annotationRepository.count();
        if (count > 0) {
            log.info("📝 数据库中已有 {} 条注释数据，跳过迁移", count);
            return;
        }
        
        try {
            log.info("📝 开始迁移注释数据...");
            
            String content = Files.readString(Paths.get(annotationFile));
            Map<String, Object> annotations = objectMapper.readValue(content, Map.class);
            
            List<CustomAnnotation> annotationList = new ArrayList<>();
            
            for (Map.Entry<String, Object> dbEntry : annotations.entrySet()) {
                String dbName = dbEntry.getKey();
                Map<String, Object> dbAnnotations = (Map<String, Object>) dbEntry.getValue();
                
                for (Map.Entry<String, Object> tableEntry : dbAnnotations.entrySet()) {
                    String tableName = tableEntry.getKey();
                    Map<String, Object> tableData = (Map<String, Object>) tableEntry.getValue();
                    
                    // 表注释
                    if (tableData.containsKey("table_comment")) {
                        String tableComment = (String) tableData.get("table_comment");
                        if (tableComment != null && !tableComment.isEmpty()) {
                            CustomAnnotation annotation = new CustomAnnotation();
                            annotation.setDatabaseName(dbName);
                            annotation.setTableName(tableName);
                            annotation.setColumnName(null);
                            annotation.setCustomComment(tableComment);
                            annotationList.add(annotation);
                        }
                    }
                    
                    // 列注释
                    if (tableData.containsKey("columns")) {
                        Map<String, String> columns = (Map<String, String>) tableData.get("columns");
                        for (Map.Entry<String, String> colEntry : columns.entrySet()) {
                            String columnName = colEntry.getKey();
                            String columnComment = colEntry.getValue();
                            
                            if (columnComment != null && !columnComment.isEmpty()) {
                                CustomAnnotation annotation = new CustomAnnotation();
                                annotation.setDatabaseName(dbName);
                                annotation.setTableName(tableName);
                                annotation.setColumnName(columnName);
                                annotation.setCustomComment(columnComment);
                                annotationList.add(annotation);
                            }
                        }
                    }
                }
            }
            
            if (!annotationList.isEmpty()) {
                annotationRepository.saveAll(annotationList);
                log.info("✅ 成功迁移 {} 条注释数据", annotationList.size());
                
                // 备份原文件
                backupFile(annotationFile);
            }
        } catch (Exception e) {
            log.error("❌ 迁移注释数据失败: {}", e.getMessage());
            // 不抛出异常，允许其他迁移继续
        }
    }

    /**
     * 迁移会话数据
     */
    private void migrateSessions() {
        String sessionFile = properties.getFiles().getSession();
        File file = new File(sessionFile);
        
        if (!file.exists()) {
            log.info("📝 会话文件不存在，跳过迁移");
            return;
        }
        
        // 检查数据库中是否已有数据
        long count = sessionHistoryRepository.count();
        if (count > 0) {
            log.info("📝 数据库中已有 {} 条会话数据，跳过迁移", count);
            return;
        }
        
        try {
            log.info("📝 开始迁移会话数据...");
            
            List<SessionHistory> sessionList = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    try {
                        Map<String, Object> sessionData = objectMapper.readValue(line, Map.class);
                        
                        // 支持两种命名方式：驼峰和下划线
                        String sessionId = (String) sessionData.get("sessionId");
                        if (sessionId == null) {
                            sessionId = (String) sessionData.get("session_id");
                        }
                        
                        String windowId = (String) sessionData.get("windowId");
                        if (windowId == null) {
                            windowId = (String) sessionData.get("window_id");
                        }
                        
                        String question = (String) sessionData.get("question");
                        
                        // 跳过空数据
                        if (sessionId == null || windowId == null || question == null) {
                            log.warn("⚠️ 会话数据不完整，跳过: sessionId={}, windowId={}, question={}", 
                                sessionId, windowId, question != null ? "存在" : "null");
                            continue;
                        }
                        
                        SessionHistory session = new SessionHistory();
                        session.setSessionId(sessionId);
                        session.setWindowId(windowId);
                        session.setQuestion(question);
                        
                        Object timestampObj = sessionData.get("timestamp");
                        if (timestampObj instanceof Number) {
                            session.setTimestamp(((Number) timestampObj).longValue());
                        } else {
                            session.setTimestamp(System.currentTimeMillis());
                        }
                        
                        sessionList.add(session);
                    } catch (Exception e) {
                        log.warn("⚠️ 解析会话数据失败: {}", e.getMessage());
                    }
                }
            }
            
            if (!sessionList.isEmpty()) {
                sessionHistoryRepository.saveAll(sessionList);
                log.info("✅ 成功迁移 {} 条会话数据", sessionList.size());
                
                // 备份原文件
                backupFile(sessionFile);
            }
        } catch (Exception e) {
            log.error("❌ 迁移会话数据失败: {}", e.getMessage());
            // 不抛出异常，允许其他迁移继续
        }
    }

    /**
     * 备份文件
     */
    private void backupFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                String backupPath = filePath + ".backup";
                Files.copy(file.toPath(), Paths.get(backupPath));
                log.info("📦 已备份文件: {} -> {}", filePath, backupPath);
            }
        } catch (Exception e) {
            log.warn("⚠️ 备份文件失败: {}", e.getMessage());
        }
    }
}
