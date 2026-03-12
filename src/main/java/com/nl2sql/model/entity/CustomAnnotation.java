package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 自定义注释实体
 */
@Data
@Entity
@Table(name = "custom_annotations",
    uniqueConstraints = @UniqueConstraint(name = "uk_annotation", 
        columnNames = {"owner_user_id", "database_name", "table_name", "column_name"}),
    indexes = {
        @Index(name = "idx_database", columnList = "database_name"),
        @Index(name = "idx_owner_database", columnList = "owner_user_id,database_name")
    }
)
public class CustomAnnotation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "owner_user_id", nullable = false)
    private Integer ownerUserId;
    
    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;
    
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;
    
    @Column(name = "column_name", length = 100)
    private String columnName;
    
    @Column(name = "custom_comment", columnDefinition = "TEXT")
    private String customComment;
    
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
