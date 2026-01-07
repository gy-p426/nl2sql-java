package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.TestRecord;
import com.nl2sql.repository.TestRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试记录管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestRecordService {

    private final TestRecordRepository testRecordRepository;
    private final ObjectMapper objectMapper;

    @Value("${nl2sql.test-images-dir:test_images}")
    private String testImagesDir;

    /**
     * 获取测试记录列表
     */
    public Map<String, Object> getTestRecords(String search, String status, int page, int pageSize) {
        log.info("📋 获取测试记录列表 - 搜索: {}, 状态: {}, 页码: {}", search, status, page);
        
        try {
            Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<TestRecord> recordPage;
            
            // 根据条件查询
            if (search != null && !search.trim().isEmpty()) {
                if (status != null && !status.trim().isEmpty()) {
                    recordPage = testRecordRepository.searchByStatusAndQuestionOrNotes(status, search, pageable);
                } else {
                    recordPage = testRecordRepository.searchByQuestionOrNotes(search, pageable);
                }
            } else if (status != null && !status.trim().isEmpty()) {
                recordPage = testRecordRepository.findByStatus(status, pageable);
            } else {
                recordPage = testRecordRepository.findAll(pageable);
            }
            
            List<Map<String, Object>> records = recordPage.getContent().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", recordPage.getTotalElements());
            result.put("page", page);
            result.put("page_size", pageSize);
            result.put("total_pages", recordPage.getTotalPages());
            
            return result;
        } catch (Exception e) {
            log.error("❌ 获取测试记录列表错误: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("records", List.of());
            result.put("total", 0);
            result.put("page", page);
            result.put("page_size", pageSize);
            return result;
        }
    }

    /**
     * 新增测试记录
     */
    @Transactional
    public Map<String, Object> addTestRecord(String question, String expectedResult, String actualResult,
                                             String status, String notes, List<MultipartFile> images) {
        log.info("➕ 新增测试记录 - 问题: {}", question);
        
        try {
            // 生成测试ID
            String testId = "TEST_" + System.currentTimeMillis();
            
            // 保存图片
            List<String> imageFilenames = saveImages(testId, images);
            
            // 创建测试记录
            TestRecord record = new TestRecord();
            record.setTestId(testId);
            record.setQuestion(question);
            record.setExpectedResult(expectedResult);
            record.setActualResult(actualResult);
            record.setStatus(status != null ? status : "pending");
            record.setNotes(notes);
            record.setImages(objectMapper.writeValueAsString(imageFilenames));
            
            testRecordRepository.save(record);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("test_id", testId);
            result.put("message", "测试记录添加成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 新增测试记录错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取单个测试记录详情
     */
    public Map<String, Object> getTestRecord(String testId) {
        log.info("📄 获取测试记录详情 - ID: {}", testId);
        
        try {
            TestRecord record = testRecordRepository.findByTestId(testId)
                .orElseThrow(() -> new RuntimeException("测试记录不存在"));
            
            return toMap(record);
        } catch (Exception e) {
            log.error("❌ 获取测试记录详情错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 更新测试记录
     */
    @Transactional
    public Map<String, Object> updateTestRecord(String testId, String question, String expectedResult,
                                                String actualResult, String status, String notes,
                                                List<MultipartFile> images, List<String> deleteImages) {
        log.info("✏️ 更新测试记录 - ID: {}", testId);
        
        try {
            TestRecord record = testRecordRepository.findByTestId(testId)
                .orElseThrow(() -> new RuntimeException("测试记录不存在"));
            
            // 更新字段
            if (question != null) record.setQuestion(question);
            if (expectedResult != null) record.setExpectedResult(expectedResult);
            if (actualResult != null) record.setActualResult(actualResult);
            if (status != null) record.setStatus(status);
            if (notes != null) record.setNotes(notes);
            
            // 处理图片
            List<String> currentImages = parseImages(record.getImages());
            
            // 删除指定图片
            if (deleteImages != null && !deleteImages.isEmpty()) {
                for (String filename : deleteImages) {
                    deleteImage(filename);
                    currentImages.remove(filename);
                }
            }
            
            // 添加新图片
            if (images != null && !images.isEmpty()) {
                List<String> newImages = saveImages(testId, images);
                currentImages.addAll(newImages);
            }
            
            record.setImages(objectMapper.writeValueAsString(currentImages));
            testRecordRepository.save(record);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "测试记录更新成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 更新测试记录错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 删除测试记录
     */
    @Transactional
    public Map<String, Object> deleteTestRecord(String testId) {
        log.info("🗑️ 删除测试记录 - ID: {}", testId);
        
        try {
            TestRecord record = testRecordRepository.findByTestId(testId)
                .orElseThrow(() -> new RuntimeException("测试记录不存在"));
            
            // 删除关联的图片
            List<String> images = parseImages(record.getImages());
            for (String filename : images) {
                deleteImage(filename);
            }
            
            // 删除记录
            testRecordRepository.deleteByTestId(testId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "测试记录删除成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 删除测试记录错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取测试图片
     */
    public Resource getTestImage(String filename) {
        log.info("🖼️ 获取测试图片 - 文件名: {}", filename);
        
        try {
            Path imagePath = Paths.get(testImagesDir, filename);
            Resource resource = new UrlResource(imagePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("图片不存在或不可读");
            }
        } catch (Exception e) {
            log.error("❌ 获取测试图片错误: {}", e.getMessage());
            throw new RuntimeException("获取测试图片失败: " + e.getMessage());
        }
    }

    /**
     * 备份测试记录
     */
    public Map<String, Object> backupTestRecords() {
        log.info("💾 备份测试记录");
        
        try {
            List<TestRecord> records = testRecordRepository.findAll();
            
            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFilename = "test_records_backup_" + timestamp + ".json";
            Path backupPath = Paths.get("backups", backupFilename);
            
            // 确保备份目录存在
            Files.createDirectories(backupPath.getParent());
            
            // 转换为Map列表
            List<Map<String, Object>> recordMaps = records.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
            
            // 写入文件
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupPath.toFile(), Map.of("records", recordMaps));
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("backup_file", backupFilename);
            result.put("total_records", records.size());
            result.put("message", "测试记录备份成功");
            
            return result;
        } catch (Exception e) {
            log.error("❌ 备份测试记录错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取测试统计信息
     */
    public Map<String, Object> getTestStats() {
        log.info("📊 获取测试统计信息");
        
        try {
            long total = testRecordRepository.count();
            long pending = testRecordRepository.countByStatus("pending");
            long passed = testRecordRepository.countByStatus("passed");
            long failed = testRecordRepository.countByStatus("failed");
            
            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("pending", pending);
            result.put("passed", passed);
            result.put("failed", failed);
            
            return result;
        } catch (Exception e) {
            log.error("❌ 获取测试统计信息错误: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("pending", 0);
            result.put("passed", 0);
            result.put("failed", 0);
            return result;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 保存图片文件
     */
    private List<String> saveImages(String testId, List<MultipartFile> images) throws IOException {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> filenames = new ArrayList<>();
        Path uploadDir = Paths.get(testImagesDir);
        
        // 确保目录存在
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        
        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);
            if (image.isEmpty()) continue;
            
            // 生成文件名
            String originalFilename = image.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".png";
            String filename = testId + "_" + i + extension;
            
            // 保存文件
            Path filePath = uploadDir.resolve(filename);
            Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            filenames.add(filename);
            log.info("✅ 保存图片: {}", filename);
        }
        
        return filenames;
    }

    /**
     * 删除图片文件
     */
    private void deleteImage(String filename) {
        try {
            Path imagePath = Paths.get(testImagesDir, filename);
            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
                log.info("✅ 删除图片: {}", filename);
            }
        } catch (IOException e) {
            log.error("❌ 删除图片失败: {} - {}", filename, e.getMessage());
        }
    }

    /**
     * 解析图片JSON字符串
     */
    private List<String> parseImages(String imagesJson) {
        try {
            if (imagesJson == null || imagesJson.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(imagesJson, List.class);
        } catch (Exception e) {
            log.error("解析图片列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将TestRecord转换为Map
     */
    private Map<String, Object> toMap(TestRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("test_id", record.getTestId());
        map.put("question", record.getQuestion());
        map.put("expected_result", record.getExpectedResult());
        map.put("actual_result", record.getActualResult());
        map.put("status", record.getStatus());
        map.put("notes", record.getNotes());
        
        // 构建完整的图片访问URL
        List<String> imageFilenames = parseImages(record.getImages());
        List<String> imageUrls = imageFilenames.stream()
            .map(filename -> "/test-records/images/" + filename)
            .collect(Collectors.toList());
        map.put("images", imageUrls);
        
        map.put("created_at", record.getCreatedAt());
        map.put("updated_at", record.getUpdatedAt());
        return map;
    }
}
