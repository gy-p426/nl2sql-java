package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库主机配置实体
 */
@Data
@Entity
@Table(name = "database_host_config")
public class DatabaseHostConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "host", nullable = false, length = 100)
    private String host;

    @Column(name = "port")
    private Integer port = 3306;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 200)
    private String password; // 加密存储

    @Column(name = "`databases`", columnDefinition = "TEXT")
    private String databases; // JSON数组格式

    @Column(name = "pool_max_size")
    private Integer poolMaxSize = 10;

    @Column(name = "pool_min_idle")
    private Integer poolMinIdle = 5;

    @Column(name = "pool_timeout")
    private Long poolTimeout = 30000L;

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
