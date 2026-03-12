package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库概览实体
 */
@Data
@Entity
@Table(
    name = "database_overview",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_owner_host_db_overview",
        columnNames = {"owner_user_id", "host_config_id", "database_name"}
    ),
    indexes = {
        @Index(name = "idx_owner_host_active", columnList = "owner_user_id,host_config_id,is_active"),
        @Index(name = "idx_owner_db", columnList = "owner_user_id,database_name")
    }
)
public class DatabaseOverview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "owner_user_id")
    private Integer ownerUserId;

    @Column(name = "host_config_id")
    private Integer hostConfigId;

    @Column(name = "database_name", nullable = false, length = 100)
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
