package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 训练数据实体
 */
@Data
@Entity
@Table(name = "training_data", indexes = {
    @Index(name = "idx_database", columnList = "database_name"),
    @Index(name = "idx_created", columnList = "created_at")
})
public class TrainingData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String question;
    
    @Column(name = "`sql`", nullable = false, columnDefinition = "LONGTEXT")
    private String sql;
    
    @Column(name = "database_name", length = 100)
    private String databaseName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "usage_count")
    private Integer usageCount = 0;
    
    @Column(name = "success_rate", precision = 5, scale = 2)
    private BigDecimal successRate = BigDecimal.ZERO;
    
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
