package com.nl2sql.service;

import com.nl2sql.model.entity.SessionHistory;
import com.nl2sql.repository.SessionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Session 管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionHistoryRepository sessionHistoryRepository;

    /**
     * 保存问题到 session
     */
    @Transactional
    public String saveQuestionToSession(String question, String windowId) {
        return saveQuestionToSession(question, windowId, null);
    }
    
    /**
     * 保存问题到 session（带用户ID）
     */
    @Transactional
    public String saveQuestionToSession(String question, String windowId, Integer userId) {
        try {
            String sessionId = UUID.randomUUID().toString();
            
            SessionHistory session = new SessionHistory();
            session.setSessionId(sessionId);
            session.setQuestion(question);
            session.setWindowId(windowId);
            session.setUserId(userId);
            session.setTimestamp(System.currentTimeMillis());
            
            sessionHistoryRepository.save(session);
            
            log.debug("💾 问题已保存到session: {} (用户: {})", sessionId, userId);
            return sessionId;
        } catch (Exception e) {
            log.error("❌ 保存问题到session错误: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取窗口的所有 session（返回 SessionHistory 对象）
     */
    private List<SessionHistory> getWindowSessionsInternal(String windowId, int limit) {
        return getWindowSessionsInternal(windowId, limit, null);
    }
    
    /**
     * 获取窗口的所有 session（返回 SessionHistory 对象，支持用户过滤）
     */
    private List<SessionHistory> getWindowSessionsInternal(String windowId, int limit, Integer userId) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            if (userId != null) {
                return sessionHistoryRepository.findByUserIdAndWindowIdOrderByTimestampDesc(userId, windowId, pageable);
            } else {
                return sessionHistoryRepository.findByWindowIdOrderByTimestampDesc(windowId, pageable);
            }
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
            return sessionHistoryRepository.findBySessionIdAndWindowId(sessionId, windowId)
                .map(SessionHistory::getQuestion)
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
        return getLatestQuestionFromWindow(windowId, null);
    }
    
    /**
     * 获取窗口的最新问题（支持用户过滤）
     */
    public String getLatestQuestionFromWindow(String windowId, Integer userId) {
        try {
            if (userId != null) {
                return sessionHistoryRepository.findFirstByUserIdAndWindowIdOrderByTimestampDesc(userId, windowId)
                    .map(SessionHistory::getQuestion)
                    .orElse(null);
            } else {
                return sessionHistoryRepository.findFirstByWindowIdOrderByTimestampDesc(windowId)
                    .map(SessionHistory::getQuestion)
                    .orElse(null);
            }
        } catch (Exception e) {
            log.error("❌ 从数据库获取最新问题错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清除 session 缓存历史
     */
    @Transactional
    public void clearSessionCache() {
        try {
            sessionHistoryRepository.deleteAll();
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
            Pageable pageable = PageRequest.of(0, limit);
            return sessionHistoryRepository.findAllByOrderByTimestampDesc(pageable)
                .stream()
                .map(this::sessionHistoryToMap)
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
        return getWindowList(null);
    }
    
    /**
     * 获取窗口列表（支持用户过滤）
     */
    public List<String> getWindowList(Integer userId) {
        try {
            if (userId != null) {
                return sessionHistoryRepository.findAllWindowIdsByUserId(userId);
            } else {
                return sessionHistoryRepository.findAllWindowIds();
            }
        } catch (Exception e) {
            log.error("❌ 获取窗口列表错误: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取窗口信息
     */
    public Map<String, Object> getWindowInfo(String windowId) {
        return getWindowInfo(windowId, null);
    }
    
    /**
     * 获取窗口信息（支持用户过滤）
     */
    public Map<String, Object> getWindowInfo(String windowId, Integer userId) {
        try {
            long messageCount;
            List<SessionHistory> sessions;
            Pageable pageable = PageRequest.of(0, 10);
            
            if (userId != null) {
                messageCount = sessionHistoryRepository.countByUserIdAndWindowId(userId, windowId);
                sessions = sessionHistoryRepository.findByUserIdAndWindowIdOrderByTimestampDesc(userId, windowId, pageable);
            } else {
                messageCount = sessionHistoryRepository.countByWindowId(windowId);
                sessions = sessionHistoryRepository.findByWindowIdOrderByTimestampDesc(windowId, pageable);
            }
            
            Map<String, Object> info = new HashMap<>();
            info.put("exists", messageCount > 0);
            info.put("message_count", messageCount);
            info.put("sessions", sessions.stream()
                .map(this::sessionHistoryToMap)
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
    @Transactional
    public void clearWindowContext(String windowId) {
        clearWindowContext(windowId, null);
    }
    
    /**
     * 清除指定窗口的上下文（支持用户过滤）
     */
    @Transactional
    public void clearWindowContext(String windowId, Integer userId) {
        try {
            if (userId != null) {
                sessionHistoryRepository.deleteByUserIdAndWindowId(userId, windowId);
                log.info("🧹 已清除用户 {} 在窗口 {} 的上下文缓存", userId, windowId);
            } else {
                sessionHistoryRepository.deleteByWindowId(windowId);
                log.info("🧹 已清除窗口 {} 的上下文缓存", windowId);
            }
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
        return getWindowSessions(windowId, limit, null);
    }
    
    /**
     * 获取窗口的所有 session（返回 Map 格式，支持用户过滤）
     */
    public List<Map<String, Object>> getWindowSessions(String windowId, int limit, Integer userId) {
        return getWindowSessionsInternal(windowId, limit, userId).stream()
            .map(this::sessionHistoryToMap)
            .collect(Collectors.toList());
    }

    private Map<String, Object> sessionHistoryToMap(SessionHistory session) {
        Map<String, Object> map = new HashMap<>();
        map.put("session_id", session.getSessionId());
        map.put("question", session.getQuestion());
        map.put("window_id", session.getWindowId());
        map.put("user_id", session.getUserId());
        map.put("timestamp", session.getTimestamp());
        map.put("created_at", session.getCreatedAt());
        return map;
    }

    /**
     * 判断并合并连续问题 - 完全按照Python实现
     */
    public Map<String, Object> mergeContinuousQuestion(String newQuestion, String windowId, String selectedSessionId) {
        try {
            String previousQuestion = null;
            
            // 如果指定了session_id，从该session获取问题
            if (selectedSessionId != null && !selectedSessionId.isEmpty()) {
                previousQuestion = getSessionQuestion(selectedSessionId, windowId);
                if (previousQuestion != null) {
                    log.info("📝 窗口 {} 使用指定session {}... 的问题", windowId, 
                        selectedSessionId.substring(0, Math.min(8, selectedSessionId.length())));
                } else {
                    log.warn("⚠️ 窗口 {} 未找到session {}...", windowId, 
                        selectedSessionId.substring(0, Math.min(8, selectedSessionId.length())));
                    Map<String, Object> result = new HashMap<>();
                    result.put("is_continuous", false);
                    result.put("merged_question", newQuestion);
                    result.put("original_question", newQuestion);
                    result.put("previous_question", null);
                    result.put("error", String.format("Session %s 不存在或不属于窗口 %s", selectedSessionId, windowId));
                    return result;
                }
            } else {
                // 获取指定窗口的最近一个问题
                previousQuestion = getLatestQuestionFromWindow(windowId);
            }
            
            if (previousQuestion == null || previousQuestion.isEmpty()) {
                // 没有历史问题，直接返回新问题
                log.info("📝 窗口 {} 无历史问题，直接使用新问题", windowId);
                Map<String, Object> result = new HashMap<>();
                result.put("is_continuous", false);
                result.put("merged_question", newQuestion);
                result.put("original_question", newQuestion);
                result.put("previous_question", null);
                return result;
            }
            
            log.info("🔍 窗口 {} 检测连续问题 - 上一问: {}..., 新问题: {}...", 
                windowId, 
                previousQuestion.substring(0, Math.min(50, previousQuestion.length())),
                newQuestion.substring(0, Math.min(50, newQuestion.length())));
            
            // 调用AI判断（这里简化实现，实际应该调用AI服务）
            // 暂时返回非连续问题
            Map<String, Object> result = new HashMap<>();
            result.put("is_continuous", false);
            result.put("merged_question", newQuestion);
            result.put("original_question", newQuestion);
            result.put("previous_question", previousQuestion);
            result.put("reason", "暂未实现AI判断逻辑");
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ 连续问题判断错误: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("is_continuous", false);
            result.put("merged_question", newQuestion);
            result.put("original_question", newQuestion);
            result.put("previous_question", null);
            return result;
        }
    }
}
