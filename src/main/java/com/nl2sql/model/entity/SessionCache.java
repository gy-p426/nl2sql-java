package com.nl2sql.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Session 缓存实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCache {
    
    private String sessionId;
    private String question;
    private String windowId;
    private Long timestamp;
    private LocalDateTime createdAt;
    
    public static SessionCache create(String sessionId, String question, String windowId) {
        long timestamp = System.currentTimeMillis();
        return SessionCache.builder()
            .sessionId(sessionId)
            .question(question)
            .windowId(windowId)
            .timestamp(timestamp)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
