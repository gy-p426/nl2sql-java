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
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    private final NL2SQLProperties properties;

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
}
