package com.nl2sql.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会话历史实体
 */
@Data
@Entity
@Table(name = "session_history", indexes = {
    @Index(name = "idx_session", columnList = "session_id"),
    @Index(name = "idx_window", columnList = "window_id"),
    @Index(name = "idx_user", columnList = "user_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_created", columnList = "created_at")
})
public class SessionHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;
    
    @Column(name = "window_id", nullable = false, length = 100)
    private String windowId;
    
    @Column(name = "user_id")
    private Integer userId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;
    
    @Column(nullable = false)
    private Long timestamp;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
