package com.nl2sql.repository;

import com.nl2sql.model.entity.SessionHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话历史Repository
 */
@Repository
public interface SessionHistoryRepository extends JpaRepository<SessionHistory, Long> {
    
    /**
     * 按窗口ID查找会话
     */
    List<SessionHistory> findByWindowIdOrderByTimestampDesc(String windowId, Pageable pageable);
    
    /**
     * 按用户ID和窗口ID查找会话
     */
    List<SessionHistory> findByUserIdAndWindowIdOrderByTimestampDesc(Integer userId, String windowId, Pageable pageable);
    
    /**
     * 按会话ID和窗口ID查找
     */
    Optional<SessionHistory> findBySessionIdAndWindowId(String sessionId, String windowId);
    
    /**
     * 按会话ID、窗口ID和用户ID查找
     */
    Optional<SessionHistory> findBySessionIdAndWindowIdAndUserId(String sessionId, String windowId, Integer userId);
    
    /**
     * 获取窗口的最新会话
     */
    Optional<SessionHistory> findFirstByWindowIdOrderByTimestampDesc(String windowId);
    
    /**
     * 获取用户在窗口的最新会话
     */
    Optional<SessionHistory> findFirstByUserIdAndWindowIdOrderByTimestampDesc(Integer userId, String windowId);
    
    /**
     * 获取所有窗口ID
     */
    @Query("SELECT DISTINCT s.windowId FROM SessionHistory s")
    List<String> findAllWindowIds();
    
    /**
     * 获取用户的所有窗口ID
     */
    @Query("SELECT DISTINCT s.windowId FROM SessionHistory s WHERE s.userId = :userId")
    List<String> findAllWindowIdsByUserId(@Param("userId") Integer userId);
    
    /**
     * 删除窗口的所有会话
     */
    void deleteByWindowId(String windowId);
    
    /**
     * 删除用户在窗口的所有会话
     */
    void deleteByUserIdAndWindowId(Integer userId, String windowId);
    
    /**
     * 统计窗口的会话数量
     */
    long countByWindowId(String windowId);
    
    /**
     * 统计用户在窗口的会话数量
     */
    long countByUserIdAndWindowId(Integer userId, String windowId);
    
    /**
     * 获取最近的会话列表
     */
    List<SessionHistory> findAllByOrderByTimestampDesc(Pageable pageable);
    
    /**
     * 获取用户的最近会话列表
     */
    List<SessionHistory> findByUserIdOrderByTimestampDesc(Integer userId, Pageable pageable);
}
