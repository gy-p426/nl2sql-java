package com.nl2sql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.model.entity.DatabaseHostConfig;
import com.nl2sql.model.entity.DatabaseOverview;
import com.nl2sql.model.entity.DatabaseSchema;
import com.nl2sql.repository.DatabaseHostConfigRepository;
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
 * 基于 database_host_config 表进行初始化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitializationService implements CommandLineRunner {

    private final DatabaseHostConfigRepository databaseHostConfigRepository;
    private final SchemaService schemaService;
    private final DatabaseOverviewRepository overviewRepository;
    private final DatabaseSchemaRepository schemaRepository;
    private final ConfigService configService;
    private final DatabasePoolService databasePoolService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        log.info("🚀 开始初始化 NL2SQL 服务...");
        
        try {
            // 1. 检测 database_host_config 表中已存在的数据库主机配置
            List<DatabaseHostConfig> hostConfigs = databaseHostConfigRepository.findByIsActiveTrue();
            
            if (hostConfigs.isEmpty()) {
                log.warn("⚠️ 未找到激活的数据库主机配置，跳过初始化");
                return;
            }
            
            // 2. 自动初始化每个数据库主机配置中的数据库连接池
            initializeDatabasePools(hostConfigs);
            
            // 3. 自动导出每个数据库的 Schema 数据
            exportAllDatabaseSchemas(hostConfigs);
            
            // 4. 自动生成并保存每个数据库的概览
            generateDatabaseOverviews(hostConfigs);
            
            log.info("✅ NL2SQL 服务初始化完成");
            
        } catch (Exception e) {
            log.error("❌ 初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化数据库连接池
     */
    private void initializeDatabasePools(List<DatabaseHostConfig> hostConfigs) {
        log.info("🔗 初始化数据库连接池...");
        
        // 使用 DatabasePoolService 来初始化连接池
        databasePoolService.initializeAllPools();
        
        log.info("✅ 数据库连接池初始化完成");
    }

    /**
     * 导出所有配置的数据库的 Schema 数据
     */
    private void exportAllDatabaseSchemas(List<DatabaseHostConfig> hostConfigs) {
        log.info("📝 导出数据库 Schema...");
        
        for (DatabaseHostConfig hostConfig : hostConfigs) {
            try {
                List<String> databases = objectMapper.readValue(
                    hostConfig.getDatabases(), 
                    new TypeReference<List<String>>() {}
                );
                
                for (String dbName : databases) {
                    log.info("📋 导出数据库 {} 的 Schema", dbName);
                    schemaService.exportDatabaseSchema(hostConfig.getOwnerUserId(), hostConfig.getId(), dbName, true); // 强制刷新
                }
                
            } catch (Exception e) {
                log.error("❌ 导出主机 {} Schema 失败: {}", hostConfig.getName(), e.getMessage());
            }
        }
        
        log.info("✅ Schema 导出完成");
    }

    /**
     * 生成数据库概览
     * 只保留表注释，不加入表名
     */
    @Transactional
    private void generateDatabaseOverviews(List<DatabaseHostConfig> hostConfigs) {
        log.info("📋 生成数据库概览...");
        
        try {
            for (DatabaseHostConfig hostConfig : hostConfigs) {
                List<String> databases = objectMapper.readValue(
                    hostConfig.getDatabases(), 
                    new TypeReference<List<String>>() {}
                );
                
                for (String dbName : databases) {
                    // 从数据库读取Schema信息
                    List<DatabaseSchema> schemas = schemaRepository.findByOwnerUserIdAndHostConfigIdAndDatabaseName(
                        hostConfig.getOwnerUserId(),
                        hostConfig.getId(),
                        dbName
                    );
                    
                    if (!schemas.isEmpty()) {
                        StringBuilder tableSummary = new StringBuilder();
                        
                        for (DatabaseSchema schema : schemas) {
                            String tableName = schema.getTableName();
                            String tableComment = schema.getTableComment() != null ? schema.getTableComment() : "无注释";
//                            tableSummary.append(String.format("%s(%s), ", tableName, tableComment));
                            tableSummary.append(String.format("%s, ", tableComment)); // 只显示表注释
                        }
                        
                        if (tableSummary.length() > 0) {
                            tableSummary.setLength(tableSummary.length() - 2); // 移除最后的逗号和空格
                        }
                        
                        // 保存或更新数据库概览
                        DatabaseOverview overview = overviewRepository.findByOwnerUserIdAndHostConfigIdAndDatabaseName(
                                hostConfig.getOwnerUserId(), hostConfig.getId(), dbName)
                            .orElse(new DatabaseOverview());

                        overview.setOwnerUserId(hostConfig.getOwnerUserId());
                        overview.setHostConfigId(hostConfig.getId());
                        overview.setDatabaseName(dbName);
                        overview.setDescription(String.format("数据库名：%s", dbName));
                        overview.setTableSummary(tableSummary.toString());
                        overview.setTableCount(schemas.size());
                        overview.setIsActive(true);
                        
                        overviewRepository.save(overview);
                        
                        log.info("✅ 数据库 {} 概览已保存，共 {} 个表", dbName, schemas.size());
                    }
                }
            }
            
            log.info("✅ 数据库概览生成完成");
            
        } catch (Exception e) {
            log.error("❌ 生成数据库概览失败: {}", e.getMessage(), e);
        }
    }
}
