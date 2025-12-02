package com.nl2sql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.config.NL2SQLProperties;
import com.nl2sql.model.entity.SessionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Session 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final NL2SQLProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 保存问题到 session
     */
    public String saveQuestionToSession(String question, String windowId) {
        try {
            String sessionId = UUID.randomUUID().toString();
            SessionCache cache = SessionCache.create(sessionId, question, windowId);
            
            String sessionFile = properties.getFiles().getSession();
            String json = objectMapper.writeValueAsString(cache);
            
            Files.write(
                Paths.get(sessionFile),
                (json + "\n").getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
            
            log.debug("💾 问题已保存到session: {}", sessionId);
            return sessionId;
        } catch (Exception e) {
            log.error("❌ 保存问题到session错误: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取窗口的所有 session（返回 SessionCache 对象）
     */
    private List<SessionCache> getWindowSessionsInternal(String windowId, int limit) {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return Collections.emptyList();
            }

            return Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .filter(cache -> windowId.equals(cache.getWindowId()))
                .sorted(Comparator.comparing(SessionCache::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ 获取窗口session列表错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定 session 的问题
     */
    public String getSessionQuestion(String sessionId, String windowId) {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return null;
            }

            return Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .filter(cache -> sessionId.equals(cache.getSessionId()))
                .filter(cache -> windowId.equals(cache.getWindowId()))
                .map(SessionCache::getQuestion)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            log.error("❌ 获取session问题错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取窗口的最新问题
     */
    public String getLatestQuestionFromWindow(String windowId) {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return null;
            }

            return Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .filter(cache -> windowId.equals(cache.getWindowId()))
                .max(Comparator.comparing(SessionCache::getTimestamp))
                .map(SessionCache::getQuestion)
                .orElse(null);
        } catch (Exception e) {
            log.error("❌ 从文件获取最新问题错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清除 session 缓存历史
     */
    public void clearSessionCache() {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (Files.exists(Paths.get(sessionFile))) {
                Files.delete(Paths.get(sessionFile));
            }
            log.info("🧹 已清除所有session缓存历史");
        } catch (Exception e) {
            log.error("❌ 清除session缓存历史错误: {}", e.getMessage());
        }
    }

    /**
     * 获取 session 缓存列表
     */
    public List<Map<String, Object>> getSessionCacheList(int limit) {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return Collections.emptyList();
            }

            return Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SessionCache::getTimestamp).reversed())
                .limit(limit)
                .map(this::sessionCacheToMap)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ 获取session缓存列表错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取所有窗口列表
     */
    public List<String> getWindowList() {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return Collections.emptyList();
            }

            return Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .map(SessionCache::getWindowId)
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ 获取窗口列表错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取窗口信息
     */
    public Map<String, Object> getWindowInfo(String windowId) {
        try {
            String sessionFile = properties.getFiles().getSession();
            Map<String, Object> info = new HashMap<>();
            
            if (!Files.exists(Paths.get(sessionFile))) {
                info.put("exists", false);
                info.put("message_count", 0);
                info.put("sessions", Collections.emptyList());
                return info;
            }

            List<SessionCache> sessions = Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .map(this::parseSessionCache)
                .filter(Objects::nonNull)
                .filter(cache -> windowId.equals(cache.getWindowId()))
                .collect(Collectors.toList());

            info.put("exists", !sessions.isEmpty());
            info.put("message_count", sessions.size());
            info.put("sessions", sessions.stream()
                .sorted(Comparator.comparing(SessionCache::getTimestamp).reversed())
                .limit(10)
                .map(this::sessionCacheToMap)
                .collect(Collectors.toList()));
            
            return info;
        } catch (Exception e) {
            log.error("❌ 获取窗口信息错误: {}", e.getMessage());
            Map<String, Object> info = new HashMap<>();
            info.put("exists", false);
            info.put("message_count", 0);
            info.put("sessions", Collections.emptyList());
            return info;
        }
    }

    /**
     * 清除指定窗口的上下文
     */
    public void clearWindowContext(String windowId) {
        try {
            String sessionFile = properties.getFiles().getSession();
            if (!Files.exists(Paths.get(sessionFile))) {
                return;
            }

            List<String> lines = Files.lines(Paths.get(sessionFile))
                .filter(line -> !line.trim().isEmpty())
                .filter(line -> {
                    SessionCache cache = parseSessionCache(line);
                    return cache == null || !windowId.equals(cache.getWindowId());
                })
                .collect(Collectors.toList());

            Files.write(Paths.get(sessionFile), lines);
            log.info("🧹 已清除窗口 {} 的上下文缓存", windowId);
        } catch (Exception e) {
            log.error("❌ 清除窗口上下文错误: {}", e.getMessage());
        }
    }

    /**
     * 清除所有窗口的上下文
     */
    public void clearAllWindows() {
        clearSessionCache();
        log.info("🧹 已清除所有窗口的上下文缓存");
    }

    /**
     * 获取窗口的所有 session（返回 Map 格式）
     */
    public List<Map<String, Object>> getWindowSessions(String windowId, int limit) {
        return getWindowSessionsInternal(windowId, limit).stream()
            .map(this::sessionCacheToMap)
            .collect(Collectors.toList());
    }

    private SessionCache parseSessionCache(String line) {
        try {
            return objectMapper.readValue(line, SessionCache.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> sessionCacheToMap(SessionCache cache) {
        Map<String, Object> map = new HashMap<>();
        map.put("session_id", cache.getSessionId());
        map.put("question", cache.getQuestion());
        map.put("window_id", cache.getWindowId());
        map.put("timestamp", cache.getTimestamp());
        map.put("created_at", cache.getCreatedAt());
        return map;
    }
}
