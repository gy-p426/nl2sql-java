package com.nl2sql.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库配置 - 多数据源支持
 * 注意：此配置已被 DatabasePoolService 替代，基于 database_host_config 表进行动态管理
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final NL2SQLProperties properties;

    /**
     * 创建空的数据库连接池 Map，实际的连接池由 DatabasePoolService 管理
     * 保留此 Bean 是为了满足依赖注入的需要
     */
    @Bean
    public Map<String, Map<String, DataSource>> databasePools() {
        log.info("📋 使用 DatabasePoolService 进行动态数据库连接池管理");
        return new HashMap<>();
    }
    
    /* 
     * 原有的基于配置文件的连接池创建逻辑已被注释，
     * 现在使用 DatabasePoolService 基于 database_host_config 表进行动态管理
     */
    /*
    @Bean
    public Map<String, Map<String, DataSource>> databasePools() {
        Map<String, Map<String, DataSource>> pools = new HashMap<>();
        
        for (NL2SQLProperties.DatabaseHost host : properties.getDatabases()) {
            Map<String, DataSource> hostPools = new HashMap<>();
            
            for (String dbName : host.getDatabases()) {
                try {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai",
                        host.getHost(), host.getPort(), dbName));
                    config.setUsername(host.getUsername());
                    config.setPassword(host.getPassword());
                    config.setMaximumPoolSize(host.getPool().getMaximumPoolSize());
                    config.setMinimumIdle(host.getPool().getMinimumIdle());
                    config.setConnectionTimeout(host.getPool().getConnectionTimeout());
                    config.setPoolName("HikariPool-" + dbName);
                    
                    HikariDataSource dataSource = new HikariDataSource(config);
                    hostPools.put(dbName, dataSource);
                    
                    log.info("✅ 初始化数据库连接池: {} @ {}:{} ({})", 
                        dbName, host.getHost(), host.getPort(), host.getName());
                } catch (Exception e) {
                    log.error("❌ 初始化数据库 {} 连接池失败: {}", dbName, e.getMessage());
                }
            }
            
            pools.put(host.getName(), hostPools);
        }
        
        return pools;
    }
    */
}
