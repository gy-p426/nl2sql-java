package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 测试记录实体
 */
@Data
@Entity
@Table(name = "test_records", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created", columnList = "created_at")
})
public class TestRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "test_id", nullable = false, unique = true, length = 50)
    private String testId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;
    
    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;
    
    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;
    
    @Column(length = 20)
    private String status = "pending";
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(columnDefinition = "JSON")
    private String images;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
