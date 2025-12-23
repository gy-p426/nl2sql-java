package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库概览实体
 */
@Data
@Entity
@Table(name = "database_overview")
public class DatabaseOverview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "database_name", nullable = false, unique = true, length = 100)
    private String databaseName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "table_summary", columnDefinition = "TEXT")
    private String tableSummary;

    @Column(name = "table_count")
    private Integer tableCount = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

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
