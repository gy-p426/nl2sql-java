package com.nl2sql.service;

import com.nl2sql.config.NL2SQLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 初始化服务 - 应用启动时执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitializationService implements CommandLineRunner {

    private final DatabaseService databaseService;
    private final SchemaService schemaService;
    private final NL2SQLProperties properties;

    @Override
    public void run(String... args) {
        log.info("🚀 开始初始化 NL2SQL 服务...");
        
        try {
            // 1. 检查数据库连接
            checkDatabaseConnections();
            
            // 2. 初始化 Schema 文件
            initializeSchemas();
            
            // 3. 生成数据库概览
            generateDatabaseOverview();
            
            log.info("✅ NL2SQL 服务初始化完成");
            
        } catch (Exception e) {
            log.error("❌ 初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查数据库连接
     */
    private void checkDatabaseConnections() {
        log.info("📡 检查数据库连接...");
        
        List<String> databases = databaseService.getAllDatabases();
        int successCount = 0;
        
        for (String dbName : databases) {
            if (databaseService.testConnection(dbName)) {
                successCount++;
                log.info("  ✅ {} 连接成功", dbName);
            } else {
                log.warn("  ⚠️ {} 连接失败", dbName);
            }
        }
        
        log.info("📊 数据库连接检查完成: {}/{} 成功", successCount, databases.size());
    }

    /**
     * 初始化 Schema 文件
     */
    private void initializeSchemas() {
        log.info("📝 初始化数据库 Schema...");
        
        boolean forceRefresh = properties.getSettings().getRefreshSchema();
        schemaService.exportAllSchemas(forceRefresh);
        
        log.info("✅ Schema 初始化完成");
    }

    /**
     * 生成数据库概览
     */
    private void generateDatabaseOverview() {
        log.info("📋 生成数据库概览...");
        
        String dbOverviewFile = properties.getFiles().getDbOverview();
        
        try {
            StringBuilder overview = new StringBuilder();
            List<String> databases = databaseService.getAllDatabases();
            
            for (String dbName : databases) {
                String schemaFile = properties.getFiles().getSchemaDir() + "/" + dbName + ".txt";
                
                if (Files.exists(Paths.get(schemaFile))) {
                    List<String> lines = Files.readAllLines(Paths.get(schemaFile));
                    
                    StringBuilder tables = new StringBuilder();
                    for (String line : lines) {
                        if (line.trim().isEmpty()) continue;
                        
                        String[] parts = line.split("\\|\\|");
                        if (parts.length >= 2) {
                            String tableName = parts[0].split("\\.")[1]; // 去掉数据库前缀
                            String tableComment = parts[1];
                            tables.append(String.format("%s(%s), ", tableName, tableComment));
                        }
                    }
                    
                    if (tables.length() > 0) {
                        tables.setLength(tables.length() - 2); // 移除最后的逗号和空格
                    }
                    
                    overview.append(String.format("数据库名：%s。表名表注释：%s。\n", dbName, tables.toString()));
                }
            }
            
            Files.writeString(Paths.get(dbOverviewFile), overview.toString());
            log.info("✅ 数据库概览已保存到: {}", dbOverviewFile);
            
        } catch (IOException e) {
            log.error("❌ 生成数据库概览失败: {}", e.getMessage());
        }
    }
}
