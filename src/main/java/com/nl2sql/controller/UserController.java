package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String account = request.get("account");
        String password = request.get("password");
        
        if (account == null || password == null) {
            return ApiResponse.error("账号和密码不能为空");
        }
        
        Map<String, Object> result = userService.login(account, password);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 添加用户
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> addUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String account = request.get("account");
        String password = request.get("password");
        String securityQuestion = request.get("securityQuestion");
        String securityAnswer = request.get("securityAnswer");
        String email = request.get("email");
        String phone = request.get("phone");
        
        if (username == null || account == null || password == null) {
            return ApiResponse.error("用户名、账号和密码不能为空");
        }
        
        Map<String, Object> result = userService.addUser(username, account, password,
                securityQuestion, securityAnswer, email, phone);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateUser(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> request) {
        
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String securityQuestion = (String) request.get("securityQuestion");
        String securityAnswer = (String) request.get("securityAnswer");
        String email = (String) request.get("email");
        String phone = (String) request.get("phone");
        Boolean status = (Boolean) request.get("status");
        
        Map<String, Object> result = userService.updateUser(id, username, password,
                securityQuestion, securityAnswer, email, phone, status);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteUser(@PathVariable Integer id) {
        Map<String, Object> result = userService.deleteUser(id);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getUser(@PathVariable Integer id) {
        Map<String, Object> result = userService.getUser(id);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 获取用户列表
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        
        Map<String, Object> result = userService.getUsers(search, status, page, pageSize);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 从外部数据库导入用户
     */
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importUsers(@RequestBody Map<String, String> request) {
        String dbName = request.get("dbName");
        String tableName = request.get("tableName");
        String usernameColumn = request.get("usernameColumn");
        String accountColumn = request.get("accountColumn");
        String passwordColumn = request.get("passwordColumn");
        String emailColumn = request.get("emailColumn");
        String phoneColumn = request.get("phoneColumn");
        
        if (dbName == null || tableName == null || usernameColumn == null || accountColumn == null) {
            return ApiResponse.error("数据库名、表名、用户名列和账号列不能为空");
        }
        
        Map<String, Object> result = userService.importUsersFromDatabase(dbName, tableName,
                usernameColumn, accountColumn, passwordColumn, emailColumn, phoneColumn);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 验证安全问题答案
     */
    @PostMapping("/verify-security")
    public ApiResponse<Map<String, Object>> verifySecurityAnswer(@RequestBody Map<String, String> request) {
        String account = request.get("account");
        String answer = request.get("answer");
        
        if (account == null || answer == null) {
            return ApiResponse.error("账号和答案不能为空");
        }
        
        Map<String, Object> result = userService.verifySecurityAnswer(account, answer);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String account = request.get("account");
        String newPassword = request.get("newPassword");
        
        if (account == null || newPassword == null) {
            return ApiResponse.error("账号和新密码不能为空");
        }
        
        Map<String, Object> result = userService.resetPassword(account, newPassword);
        
        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error((String) result.get("message"));
        }
    }
    
    /**
     * 获取用户的安全问题
     */
    @GetMapping("/security-question/{account}")
    public ApiResponse<Map<String, Object>> getSecurityQuestion(@PathVariable String account) {
        Map<String, Object> result = userService.getUser(null);
        // 这里需要根据account查询，简化处理
        return ApiResponse.success(result);
    }
}
