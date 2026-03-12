package com.nl2sql.service;

import com.nl2sql.model.entity.User;
import com.nl2sql.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";
    
    private final UserRepository userRepository;
    private final DatabaseService databaseService;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * 密码加密
     */
    private String encryptPassword(String password) {
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 添加用户
     */
    @Transactional
    public Map<String, Object> addUser(String username, String account, String password,
                                       String securityQuestion, String securityAnswer,
                                       String email, String phone) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查账号是否已存在
            if (userRepository.existsByAccount(account)) {
                result.put("success", false);
                result.put("message", "账号已存在");
                return result;
            }
            
            // 检查用户名是否已存在
            if (userRepository.existsByUsername(username)) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return result;
            }
            
            User user = new User();
            user.setUsername(username);
            user.setAccount(account);
            user.setRole("USER");
            user.setPassword(encryptPassword(password));
            user.setSecurityQuestion(securityQuestion);
            user.setSecurityAnswer(securityAnswer != null ? encryptPassword(securityAnswer) : null);
            user.setEmail(email);
            user.setPhone(phone);
            user.setStatus(true);
            
            userRepository.save(user);
            
            result.put("success", true);
            result.put("message", "用户添加成功");
            result.put("user", toMap(user));
            
        } catch (Exception e) {
            log.error("添加用户失败", e);
            result.put("success", false);
            result.put("message", "添加用户失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 更新用户
     */
    @Transactional
    public Map<String, Object> updateUser(Integer id, String username, String password,
                                          String securityQuestion, String securityAnswer,
                                          String email, String phone, Boolean status) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (!userOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }
            
            User user = userOpt.get();
            
            // 检查用户名是否被其他用户使用
            if (username != null && !username.equals(user.getUsername())) {
                if (userRepository.existsByUsername(username)) {
                    result.put("success", false);
                    result.put("message", "用户名已被使用");
                    return result;
                }
                user.setUsername(username);
            }
            
            if (password != null && !password.isEmpty()) {
                user.setPassword(encryptPassword(password));
            }
            
            if (securityQuestion != null) {
                user.setSecurityQuestion(securityQuestion);
            }
            
            if (securityAnswer != null && !securityAnswer.isEmpty()) {
                user.setSecurityAnswer(encryptPassword(securityAnswer));
            }
            
            if (email != null) {
                user.setEmail(email);
            }
            
            if (phone != null) {
                user.setPhone(phone);
            }
            
            if (status != null) {
                user.setStatus(status);
            }
            
            userRepository.save(user);
            
            result.put("success", true);
            result.put("message", "用户更新成功");
            result.put("user", toMap(user));
            
        } catch (Exception e) {
            log.error("更新用户失败", e);
            result.put("success", false);
            result.put("message", "更新用户失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public Map<String, Object> deleteUser(Integer id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (!userRepository.existsById(id)) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }
            
            userRepository.deleteById(id);
            
            result.put("success", true);
            result.put("message", "用户删除成功");
            
        } catch (Exception e) {
            log.error("删除用户失败", e);
            result.put("success", false);
            result.put("message", "删除用户失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取用户详情
     */
    public Map<String, Object> getUser(Integer id) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findById(id);
            if (!userOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }
            
            result.put("success", true);
            result.put("user", toMap(userOpt.get()));
            
        } catch (Exception e) {
            log.error("获取用户失败", e);
            result.put("success", false);
            result.put("message", "获取用户失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取用户列表
     */
    public Map<String, Object> getUsers(String search, Boolean status, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<User> userPage;
            
            if (search != null && !search.isEmpty()) {
                userPage = userRepository.searchUsers(search, pageable);
            } else if (status != null) {
                userPage = userRepository.findByStatus(status, pageable);
            } else {
                userPage = userRepository.findAll(pageable);
            }
            
            List<Map<String, Object>> users = new ArrayList<>();
            for (User user : userPage.getContent()) {
                users.add(toMap(user));
            }
            
            result.put("success", true);
            result.put("users", users);
            result.put("total", userPage.getTotalElements());
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("totalPages", userPage.getTotalPages());
            
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            result.put("success", false);
            result.put("message", "获取用户列表失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 从外部数据库导入用户
     */
    @Transactional
    public Map<String, Object> importUsersFromDatabase(String dbName, String tableName,
                                                       String usernameColumn, String accountColumn,
                                                       String passwordColumn, String emailColumn,
                                                       String phoneColumn) {
        Map<String, Object> result = new HashMap<>();
        
        try (Connection conn = databaseService.getConnection(dbName);
             Statement stmt = conn.createStatement()) {
            
            if (conn == null) {
                result.put("success", false);
                result.put("message", "无法连接到数据库: " + dbName);
                return result;
            }
            
            // 构建查询SQL
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(usernameColumn).append(" as username, ");
            sql.append(accountColumn).append(" as account");
            
            if (passwordColumn != null && !passwordColumn.isEmpty()) {
                sql.append(", ").append(passwordColumn).append(" as password");
            }
            if (emailColumn != null && !emailColumn.isEmpty()) {
                sql.append(", ").append(emailColumn).append(" as email");
            }
            if (phoneColumn != null && !phoneColumn.isEmpty()) {
                sql.append(", ").append(phoneColumn).append(" as phone");
            }
            
            sql.append(" FROM ").append(tableName);
            
            log.info("导入用户SQL: {}", sql);
            
            ResultSet rs = stmt.executeQuery(sql.toString());
            
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            while (rs.next()) {
                String username = null;
                String account = null;
                String password = null;
                String email = null;
                String phone = null;
                
                try {
                    username = rs.getString("username");
                    account = rs.getString("account");
                    
                    if (username == null || account == null) {
                        failCount++;
                        errors.add("用户名或账号为空，跳过");
                        continue;
                    }
                    
                    // 密码处理
                    try {
                        password = rs.getString("password");
                    } catch (SQLException ignored) {}
                    
                    // 邮箱
                    try {
                        email = rs.getString("email");
                    } catch (SQLException ignored) {}
                    
                    // 手机号
                    try {
                        phone = rs.getString("phone");
                    } catch (SQLException ignored) {}
                    
                    // 检查是否已存在
                    if (userRepository.existsByAccount(account)) {
                        failCount++;
                        errors.add("账号 " + account + " 已存在，跳过");
                        continue;
                    }
                    
                    // 创建并保存用户
                    User user = new User();
                    user.setUsername(username);
                    user.setAccount(account);
                    user.setRole("USER");
                    
                    if (password != null && !password.isEmpty()) {
                        // 假设导入的密码已经是加密的，如果不是，需要加密
                        user.setPassword(password);
                    } else {
                        // 设置默认密码
                        user.setPassword(encryptPassword("123456"));
                    }
                    
                    user.setEmail(email);
                    user.setPhone(phone);
                    user.setStatus(true);
                    
                    userRepository.save(user);
                    // 立即刷新并清理，避免session中积累未持久化的对象
                    entityManager.flush();
                    entityManager.clear();
                    successCount++;
                    
                } catch (Exception e) {
                    failCount++;
                    String errorMsg = "导入用户失败";
                    if (account != null) {
                        errorMsg += " (账号: " + account + ")";
                    }
                    errorMsg += ": " + e.getMessage();
                    errors.add(errorMsg);
                    log.error("导入用户失败", e);
                    // 清理session，避免影响后续操作
                    entityManager.clear();
                }
            }
            
            rs.close();
            
            result.put("success", true);
            result.put("message", "导入完成");
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            
        } catch (Exception e) {
            log.error("从数据库导入用户失败", e);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 用户登录验证
     */
    public Map<String, Object> login(String account, String password) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String encryptedPassword = encryptPassword(password);
            Optional<User> userOpt = userRepository.findByAccountAndPassword(account, encryptedPassword);
            
            if (!userOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "账号或密码错误");
                return result;
            }
            
            User user = userOpt.get();
            
            if (!user.getStatus()) {
                result.put("success", false);
                result.put("message", "账号已被禁用");
                return result;
            }
            
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("user", toMap(user));
            
        } catch (Exception e) {
            log.error("登录失败", e);
            result.put("success", false);
            result.put("message", "登录失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证安全问题答案
     */
    public Map<String, Object> verifySecurityAnswer(String account, String answer) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByAccount(account);
            
            if (!userOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "账号不存在");
                return result;
            }
            
            User user = userOpt.get();
            
            if (user.getSecurityAnswer() == null) {
                result.put("success", false);
                result.put("message", "未设置安全问题");
                return result;
            }
            
            String encryptedAnswer = encryptPassword(answer);
            
            if (encryptedAnswer.equals(user.getSecurityAnswer())) {
                result.put("success", true);
                result.put("message", "验证成功");
                result.put("userId", user.getId());
            } else {
                result.put("success", false);
                result.put("message", "答案错误");
            }
            
        } catch (Exception e) {
            log.error("验证安全问题失败", e);
            result.put("success", false);
            result.put("message", "验证失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 重置密码
     */
    @Transactional
    public Map<String, Object> resetPassword(String account, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByAccount(account);
            
            if (!userOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "账号不存在");
                return result;
            }
            
            User user = userOpt.get();
            user.setPassword(encryptPassword(newPassword));
            userRepository.save(user);
            
            result.put("success", true);
            result.put("message", "密码重置成功");
            
        } catch (Exception e) {
            log.error("重置密码失败", e);
            result.put("success", false);
            result.put("message", "重置密码失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 管理员更新用户角色
     */
    @Transactional
    public Map<String, Object> updateUserRole(Integer operatorUserId, Integer targetUserId, String role) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (operatorUserId == null || targetUserId == null || role == null || role.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "operatorUserId、targetUserId 和 role 不能为空");
                return result;
            }

            String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
            if (!ROLE_ADMIN.equals(normalizedRole) && !ROLE_USER.equals(normalizedRole)) {
                result.put("success", false);
                result.put("message", "角色仅支持 ADMIN 或 USER");
                return result;
            }

            Optional<User> operatorOpt = userRepository.findById(operatorUserId);
            if (operatorOpt.isEmpty()) {
                result.put("success", false);
                result.put("message", "操作用户不存在");
                return result;
            }

            User operator = operatorOpt.get();
            if (!ROLE_ADMIN.equalsIgnoreCase(operator.getRole())) {
                result.put("success", false);
                result.put("message", "仅管理员可修改用户角色");
                return result;
            }

            Optional<User> targetOpt = userRepository.findById(targetUserId);
            if (targetOpt.isEmpty()) {
                result.put("success", false);
                result.put("message", "目标用户不存在");
                return result;
            }

            User target = targetOpt.get();
            target.setRole(normalizedRole);
            userRepository.save(target);

            result.put("success", true);
            result.put("message", "用户角色更新成功");
            result.put("user", toMap(target));
        } catch (Exception e) {
            log.error("更新用户角色失败", e);
            result.put("success", false);
            result.put("message", "更新用户角色失败: " + e.getMessage());
        }

        return result;
    }
    
    /**
     * 转换为Map（不包含敏感信息）
     */
    private Map<String, Object> toMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("account", user.getAccount());
        map.put("role", user.getRole());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("securityQuestion", user.getSecurityQuestion());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }
}
