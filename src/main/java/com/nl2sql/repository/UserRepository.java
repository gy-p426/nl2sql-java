package com.nl2sql.repository;

import com.nl2sql.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    
    /**
     * 根据账号查找用户
     */
    Optional<User> findByAccount(String account);
    
    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 根据账号和密码查找用户（用于登录）
     */
    Optional<User> findByAccountAndPassword(String account, String password);
    
    /**
     * 检查账号是否存在
     */
    boolean existsByAccount(String account);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 搜索用户
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.username LIKE %:search% OR u.account LIKE %:search% OR u.email LIKE %:search%")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
    
    /**
     * 按状态查找用户
     */
    Page<User> findByStatus(Boolean status, Pageable pageable);
}
