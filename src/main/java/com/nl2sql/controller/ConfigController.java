package com.nl2sql.controller;

import com.nl2sql.model.dto.ApiResponse;
import com.nl2sql.service.ConfigService;
import com.nl2sql.service.DatabaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "配置管理", description = "系统配置相关接口")
public class ConfigController {

    private final DatabaseService databaseService;
    private final ConfigService configService;

    @GetMapping
    @Operation(summary = "获取系统配置")
    public ApiResponse<Map<String, Object>> getConfig(@RequestParam Integer userId) {
        try {
            Map<String, Object> config = new HashMap<>();
            
            // 数据库配置
            config.put("databases", configService.getDatabaseHosts(userId));
            
            // 火山引擎配置
            config.put("volcano_engine", configService.getVolcanoEngineConfig());
            
            // Ollama配置
            config.put("ollama", configService.getOllamaConfig());
            
            // 模型提供商
            config.put("model_provider", configService.getModelProvider());
            
            // 系统设置
            config.put("settings", configService.getSettings());
            
            return ApiResponse.success(config);
        } catch (Exception e) {
            log.error("❌ 获取配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/databases")
    @Operation(summary = "获取所有配置的数据库列表")
    public ApiResponse<java.util.List<String>> getDatabases(@RequestParam Integer userId) {
        try {
            java.util.List<String> databases = databaseService.getAllDatabases(userId);
            return ApiResponse.success(databases);
        } catch (Exception e) {
            log.error("❌ 获取数据库列表错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/test-connection/{dbName}")
    @Operation(summary = "测试数据库连接")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable String dbName,
                                                           @RequestParam Integer userId) {
        try {
            boolean success = databaseService.testConnection(userId, dbName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("database", dbName);
            result.put("connected", success);
            result.put("message", success ? "连接成功" : "连接失败");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("❌ 测试连接错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取系统统计信息")
    public ApiResponse<Map<String, Object>> getStats(@RequestParam Integer userId) {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            java.util.List<String> databases = databaseService.getAllDatabases(userId);
            stats.put("total_databases", databases.size());
            stats.put("databases", databases);
            
            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.error("❌ 获取统计信息错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reload")
    @Operation(summary = "热重载配置")
    public ApiResponse<Map<String, Object>> reloadConfig() {
        try {
            configService.reloadConfig();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "配置已重新加载");
            
            return ApiResponse.success("重新加载成功", result);
        } catch (Exception e) {
            log.error("❌ 重新加载配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/database-hosts")
    @Operation(summary = "获取所有数据库主机配置")
    public ApiResponse<Map<String, Object>> getDatabaseHosts(@RequestParam Integer userId) {
        try {
            Map<String, Object> hosts = configService.getDatabaseHosts(userId);
            return ApiResponse.success("获取数据库主机配置成功", hosts);
        } catch (Exception e) {
            log.error("❌ 获取数据库主机配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/database-hosts")
    @Operation(summary = "添加或更新数据库主机配置")
    public ApiResponse<Map<String, Object>> addOrUpdateDatabaseHost(@RequestBody Map<String, Object> request) {
        try {
            Integer userId = request.containsKey("userId") ? ((Number) request.get("userId")).intValue() : null;
            String section = (String) request.get("section");
            String host = (String) request.get("host");
            String user = (String) request.get("user");
            String password = (String) request.get("password");
            @SuppressWarnings("unchecked")
            java.util.List<String> databases = (java.util.List<String>) request.get("databases");
            String dbType = (String) request.getOrDefault("dbType", "mysql");
            
            if (userId == null || section == null || host == null || user == null || password == null) {
                return ApiResponse.error("缺少必要参数");
            }
            
            Map<String, Object> result = configService.addOrUpdateDatabaseHost(userId, section, host, user, password, databases, dbType);
            return ApiResponse.success("保存成功", result);
        } catch (Exception e) {
            log.error("❌ 添加/更新数据库主机配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/database-hosts/{section}")
    @Operation(summary = "删除数据库主机配置")
    public ApiResponse<Map<String, Object>> deleteDatabaseHost(@PathVariable String section,
                                                               @RequestBody Map<String, Object> request) {
        try {
            Integer userId = request.containsKey("userId") ? ((Number) request.get("userId")).intValue() : null;
            
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }
            
            Map<String, Object> result = configService.deleteDatabaseHost(userId, section);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("删除成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 删除数据库主机配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/database-hosts/{section}/databases")
    @Operation(summary = "向指定主机添加数据库")
    public ApiResponse<Map<String, Object>> addDatabaseToHost(
            @PathVariable String section,
            @RequestBody Map<String, String> request) {
        try {
            String database = request.get("database");
            Integer userId = request.containsKey("userId") ? Integer.valueOf(request.get("userId")) : null;
            
            if (userId == null || database == null || database.trim().isEmpty()) {
                return ApiResponse.error("数据库名不能为空");
            }
            
            Map<String, Object> result = configService.addDatabaseToHost(userId, section, database);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("添加成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 添加数据库错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/database-hosts/{section}/databases/{databaseKey}")
    @Operation(summary = "从指定主机移除数据库")
    public ApiResponse<Map<String, Object>> removeDatabaseFromHost(
            @PathVariable String section,
            @PathVariable String databaseKey,
            @RequestBody Map<String, Object> request) {
        try {
            Integer userId = request.containsKey("userId") ? ((Number) request.get("userId")).intValue() : null;
            
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }
            
            Map<String, Object> result = configService.removeDatabaseFromHost(userId, section, databaseKey);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("移除成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 移除数据库错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/test-connection")
    @Operation(summary = "测试新连接并获取可用数据库列表")
    public ApiResponse<Map<String, Object>> testNewConnection(@RequestBody Map<String, String> request) {
        try {
            String host = request.get("host");
            String user = request.get("user");
            String password = request.get("password");
            String dbType = request.getOrDefault("dbType", "mysql");
            String sid = request.getOrDefault("sid", "ORCL");
            
            if (host == null || user == null || password == null) {
                return ApiResponse.error("缺少必要参数");
            }
            
            Map<String, Object> result = configService.testNewConnection(host, user, password, dbType, sid);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("连接成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 测试连接错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/database-hosts/{section}/test")
    @Operation(summary = "测试已保存配置的数据库连接")
    public ApiResponse<Map<String, Object>> testSavedConnection(
            @PathVariable String section,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String database = request != null ? request.get("database") : null;
            Integer userId = request != null && request.containsKey("userId") ? Integer.valueOf(request.get("userId")) : null;
            if (userId == null) {
                return ApiResponse.error("用户账号信息不能为空");
            }

            Map<String, Object> result = configService.testSavedConnection(userId, section, database);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("连接成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 测试连接错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/volcano-engine")
    @Operation(summary = "获取火山引擎配置")
    public ApiResponse<Map<String, Object>> getVolcanoEngineConfig() {
        try {
            Map<String, Object> config = configService.getVolcanoEngineConfig();
            return ApiResponse.success("获取火山引擎配置成功", config);
        } catch (Exception e) {
            log.error("❌ 获取火山引擎配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/volcano-engine")
    @Operation(summary = "更新火山引擎配置")
    public ApiResponse<Map<String, Object>> updateVolcanoEngineConfig(@RequestBody Map<String, Object> request) {
        try {
            String apiKey = (String) request.get("api_key");
            String model = (String) request.get("model");
            Integer timeout = request.containsKey("timeout") ? (Integer) request.get("timeout") : null;
            
            Map<String, Object> result = configService.updateVolcanoEngineConfig(apiKey, model, timeout);
            return ApiResponse.success("更新成功", result);
        } catch (Exception e) {
            log.error("❌ 更新火山引擎配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/ollama")
    @Operation(summary = "获取Ollama配置")
    public ApiResponse<Map<String, Object>> getOllamaConfig() {
        try {
            Map<String, Object> config = configService.getOllamaConfig();
            return ApiResponse.success("获取Ollama配置成功", config);
        } catch (Exception e) {
            log.error("❌ 获取Ollama配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/ollama")
    @Operation(summary = "更新Ollama配置")
    public ApiResponse<Map<String, Object>> updateOllamaConfig(@RequestBody Map<String, Object> request) {
        try {
            Boolean enabled = request.containsKey("enabled") ? (Boolean) request.get("enabled") : null;
            String host = (String) request.get("host");
            String model = (String) request.get("model");
            Integer timeout = request.containsKey("timeout") ? (Integer) request.get("timeout") : null;
            
            Map<String, Object> result = configService.updateOllamaConfig(enabled, host, model, timeout);
            return ApiResponse.success("更新成功", result);
        } catch (Exception e) {
            log.error("❌ 更新Ollama配置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/ollama/test")
    @Operation(summary = "测试Ollama连接")
    public ApiResponse<Map<String, Object>> testOllamaConnection(@RequestBody(required = false) Map<String, String> request) {
        try {
            String host = request != null ? request.get("host") : null;
            String model = request != null ? request.get("model") : null;
            
            Map<String, Object> result = configService.testOllamaConnection(host, model);
            
            if ((Boolean) result.getOrDefault("success", false)) {
                return ApiResponse.success("连接成功", result);
            } else {
                return ApiResponse.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("❌ 测试Ollama连接错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/model-provider")
    @Operation(summary = "获取当前模型提供商")
    public ApiResponse<Map<String, Object>> getModelProvider() {
        try {
            Map<String, Object> result = configService.getModelProvider();
            return ApiResponse.success("获取模型提供商成功", result);
        } catch (Exception e) {
            log.error("❌ 获取模型提供商错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/model-provider")
    @Operation(summary = "设置模型提供商")
    public ApiResponse<Map<String, Object>> setModelProvider(@RequestBody Map<String, String> request) {
        try {
            String provider = request.get("provider");
            
            if (provider == null || provider.trim().isEmpty()) {
                return ApiResponse.error("提供商不能为空");
            }
            
            if (!provider.equals("volcano_engine") && !provider.equals("ollama")) {
                return ApiResponse.error("无效的提供商，必须是 volcano_engine 或 ollama");
            }
            
            Map<String, Object> result = configService.setModelProvider(provider);
            return ApiResponse.success("设置成功", result);
        } catch (Exception e) {
            log.error("❌ 设置模型提供商错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/settings")
    @Operation(summary = "获取系统设置")
    public ApiResponse<Map<String, Object>> getSettings() {
        try {
            Map<String, Object> settings = configService.getSettings();
            return ApiResponse.success("获取系统设置成功", settings);
        } catch (Exception e) {
            log.error("❌ 获取系统设置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/settings")
    @Operation(summary = "更新系统设置")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, String> request) {
        try {
            Map<String, Object> result = configService.updateSettings(request);
            return ApiResponse.success("更新成功", result);
        } catch (Exception e) {
            log.error("❌ 更新系统设置错误: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }
}
