package com.nl2sql.service;

import com.nl2sql.config.NL2SQLProperties;
import com.nl2sql.model.entity.DatabaseOverview;
import com.nl2sql.model.entity.DatabaseSchema;
import com.nl2sql.repository.DatabaseOverviewRepository;
import com.nl2sql.repository.DatabaseSchemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final DatabaseOverviewRepository overviewRepository;
    private final DatabaseSchemaRepository schemaRepository;
    private final DataMigrationService dataMigrationService;

    @Override
    public void run(String... args) {
        log.info("🚀 开始初始化 NL2SQL 服务...");
        
        try {
            // 0. 数据迁移（首次启动时从文件导入数据）
            dataMigrationService.migrateAllData();
            
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
    @Transactional
    private void generateDatabaseOverview() {
        log.info("📋 生成数据库概览...");
        
        try {
            List<String> databases = databaseService.getAllDatabases();
            
            for (String dbName : databases) {
                // 从数据库读取Schema信息
                List<DatabaseSchema> schemas = schemaRepository.findByDatabaseName(dbName);
                
                if (!schemas.isEmpty()) {
                    StringBuilder tableSummary = new StringBuilder();
                    
                    for (DatabaseSchema schema : schemas) {
                        String tableName = schema.getTableName();
                        String tableComment = schema.getTableComment() != null ? schema.getTableComment() : "无注释";
                        tableSummary.append(String.format("%s(%s), ", tableName, tableComment));
                    }
                    
                    if (tableSummary.length() > 0) {
                        tableSummary.setLength(tableSummary.length() - 2); // 移除最后的逗号和空格
                    }
                    
                    // 保存或更新数据库概览
                    DatabaseOverview overview = overviewRepository.findByDatabaseName(dbName)
                        .orElse(new DatabaseOverview());
                    
                    overview.setDatabaseName(dbName);
                    overview.setDescription(String.format("数据库名：%s", dbName));
                    overview.setTableSummary(tableSummary.toString());
                    overview.setTableCount(schemas.size());
                    overview.setIsActive(true);
                    
                    overviewRepository.save(overview);
                    
                    log.info("✅ 数据库 {} 概览已保存，共 {} 个表", dbName, schemas.size());
                }
            }
            
            log.info("✅ 数据库概览生成完成");
            
        } catch (Exception e) {
            log.error("❌ 生成数据库概览失败: {}", e.getMessage(), e);
        }
    }
}
