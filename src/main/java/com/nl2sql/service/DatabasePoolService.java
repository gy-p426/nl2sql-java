package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.repository.DatabaseHostConfigRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接池管理服务
 * 基于 database_host_config 表动态管理数据库连接池
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabasePoolService {

    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final ObjectMapper objectMapper;
    
    // 数据库连接池缓存 Map<数据库名, DataSource>
    private final Map<String, DataSource> databasePools = new ConcurrentHashMap<>();
    
    /**
     * 初始化所有配置的数据库连接池
     */
    public void initializeAllPools() {
        log.info("🔗 初始化数据库连接池...");
        
        try {
            List<DatabaseHostConfig> activeHosts = databaseHostConfigRepository.findByIsActiveTrue();
            
            for (DatabaseHostConfig hostConfig : activeHosts) {
                initializeHostPools(hostConfig);
            }
            
            log.info("✅ 数据库连接池初始化完成，共 {} 个数据库", databasePools.size());
            
        } catch (Exception e) {
            log.error("❌ 初始化数据库连接池失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 为指定主机初始化连接池
     */
    public void initializeHostPools(DatabaseHostConfig hostConfig) {
        try {
            List<String> databases = objectMapper.readValue(
                hostConfig.getDatabases(), 
                new TypeReference<List<String>>() {}
            );
            
            for (String dbName : databases) {
                createDatabasePool(hostConfig, dbName);
            }
            
            log.info("✅ 主机 {} 连接池初始化完成，包含 {} 个数据库", 
                hostConfig.getName(), databases.size());
            
        } catch (Exception e) {
            log.error("❌ 初始化主机 {} 连接池失败: {}", hostConfig.getName(), e.getMessage());
        }
    }
    
    /**
     * 创建单个数据库的连接池
     */
    private void createDatabasePool(DatabaseHostConfig hostConfig, String dbName) {
        try {
            // 如果连接池已存在，先关闭旧的
            if (databasePools.containsKey(dbName)) {
                DataSource oldDataSource = databasePools.get(dbName);
                if (oldDataSource instanceof HikariDataSource) {
                    ((HikariDataSource) oldDataSource).close();
                }
            }
            
            // 创建新的连接池配置
            HikariConfig config = new HikariConfig();
            
            // 处理 localhost 解析问题和端口号
            String host = hostConfig.getHost();
            int port = hostConfig.getPort();
            
            if (host != null && (host.equals("localhost") || host.startsWith("localhost:"))) {
                // 如果是 localhost:3306 格式，只替换 localhost 部分
                if (host.startsWith("localhost:")) {
                    host = host.replace("localhost:", "127.0.0.1:");
                    log.info("🔄 将 localhost:port 转换为 127.0.0.1:port 以避免 DNS 解析问题: {}", host);
                } else {
                    host = "127.0.0.1";
                    log.info("🔄 将 localhost 转换为 127.0.0.1 以避免 DNS 解析问题");
                }
            }
            
            // 构建 JDBC URL - 如果 host 已经包含端口号，就不再添加
            String jdbcUrl;
            if (host.contains(":")) {
                jdbcUrl = String.format("jdbc:mysql://%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8",
                    host, dbName);
            } else {
                jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8",
                    host, port, dbName);
            }
            
            config.setJdbcUrl(jdbcUrl);
            log.info("📡 创建数据库连接: {}", jdbcUrl);
            config.setUsername(hostConfig.getUsername());
            config.setPassword(hostConfig.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // 连接池配置
            config.setMaximumPoolSize(hostConfig.getPoolMaxSize());
            config.setMinimumIdle(hostConfig.getPoolMinIdle());
            config.setConnectionTimeout(hostConfig.getPoolTimeout());
            config.setIdleTimeout(600000); // 10分钟
            config.setMaxLifetime(1800000); // 30分钟
            config.setLeakDetectionThreshold(60000); // 1分钟
            
            // 连接池名称
            config.setPoolName("HikariPool-" + dbName);
            
            // 创建数据源
            HikariDataSource dataSource = new HikariDataSource(config);
            
            // 测试连接
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    databasePools.put(dbName, dataSource);
                    log.info("✅ 数据库 {} 连接池创建成功", dbName);
                } else {
                    dataSource.close();
                    log.error("❌ 数据库 {} 连接测试失败", dbName);
                }
            }
            
        } catch (Exception e) {
            log.error("❌ 创建数据库 {} 连接池失败: {}", dbName, e.getMessage());
            
            // 提供更详细的错误信息和建议
            if (e.getMessage().contains("UnknownHostException")) {
                log.error("💡 建议检查：");
                log.error("   1. MySQL 服务是否正在运行");
                log.error("   2. 主机名 {} 是否可以解析", hostConfig.getHost());
                log.error("   3. 端口 {} 是否正确", hostConfig.getPort());
                log.error("   4. 防火墙是否阻止了连接");
            } else if (e.getMessage().contains("Access denied")) {
                log.error("💡 建议检查用户名和密码是否正确");
            } else if (e.getMessage().contains("Unknown database")) {
                log.error("💡 建议检查数据库 {} 是否存在", dbName);
            }
        }
    }
    
    /**
     * 获取数据库连接
     */
    public Connection getConnection(String dbName) throws SQLException {
        DataSource dataSource = databasePools.get(dbName);
        if (dataSource == null) {
            // 尝试动态创建连接池
            initializeDatabasePool(dbName);
            dataSource = databasePools.get(dbName);
            
            if (dataSource == null) {
                throw new SQLException("数据库 " + dbName + " 的连接池不存在");
            }
        }
        
        return dataSource.getConnection();
    }
    
    /**
     * 动态初始化单个数据库的连接池
     */
    private void initializeDatabasePool(String dbName) {
        try {
            List<DatabaseHostConfig> activeHosts = databaseHostConfigRepository.findByIsActiveTrue();
            
            for (DatabaseHostConfig hostConfig : activeHosts) {
                List<String> databases = objectMapper.readValue(
                    hostConfig.getDatabases(), 
                    new TypeReference<List<String>>() {}
                );
                
                if (databases.contains(dbName)) {
                    createDatabasePool(hostConfig, dbName);
                    return;
                }
            }
            
            log.warn("⚠️ 未找到数据库 {} 的配置", dbName);
            
        } catch (Exception e) {
            log.error("❌ 动态初始化数据库 {} 连接池失败: {}", dbName, e.getMessage());
        }
    }
    
    /**
     * 测试数据库连接
     */
    public boolean testConnection(String dbName) {
        try (Connection conn = getConnection(dbName)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            log.error("❌ 数据库 {} 连接测试失败: {}", dbName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 刷新连接池（重新加载配置）
     */
    public void refreshPools() {
        log.info("🔄 刷新数据库连接池...");
        
        // 关闭所有现有连接池
        for (Map.Entry<String, DataSource> entry : databasePools.entrySet()) {
            DataSource dataSource = entry.getValue();
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
                log.info("🔒 关闭数据库 {} 的连接池", entry.getKey());
            }
        }
        
        databasePools.clear();
        
        // 重新初始化
        initializeAllPools();
    }
    
    /**
     * 关闭所有连接池
     */
    public void closeAllPools() {
        log.info("🔒 关闭所有数据库连接池...");
        
        for (Map.Entry<String, DataSource> entry : databasePools.entrySet()) {
            DataSource dataSource = entry.getValue();
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
                log.info("🔒 关闭数据库 {} 的连接池", entry.getKey());
            }
        }
        
        databasePools.clear();
        log.info("✅ 所有连接池已关闭");
    }
    
    /**
     * 获取连接池状态
     */
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, DataSource> entry : databasePools.entrySet()) {
            String dbName = entry.getKey();
            DataSource dataSource = entry.getValue();
            
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDS = (HikariDataSource) dataSource;
                Map<String, Object> poolInfo = Map.of(
                    "active", hikariDS.getHikariPoolMXBean().getActiveConnections(),
                    "idle", hikariDS.getHikariPoolMXBean().getIdleConnections(),
                    "total", hikariDS.getHikariPoolMXBean().getTotalConnections(),
                    "waiting", hikariDS.getHikariPoolMXBean().getThreadsAwaitingConnection()
                );
                status.put(dbName, poolInfo);
            }
        }
        
        return status;
    }
}