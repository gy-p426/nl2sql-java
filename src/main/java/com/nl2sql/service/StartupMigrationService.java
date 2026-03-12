package com.nl2sql.service;

import com.nl2sql.model.entity.User;
import com.nl2sql.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 启动迁移服务。
 * 负责执行仅一次的数据库迁移脚本，并记录执行版本。
 */
@Slf4j
@Service
@Order(0)
@RequiredArgsConstructor
public class StartupMigrationService implements CommandLineRunner {

    private static final String CREATE_MIGRATION_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS startup_migration_history (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              migration_version VARCHAR(100) NOT NULL UNIQUE,
              description VARCHAR(255) NULL,
              script_name VARCHAR(255) NOT NULL,
              executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        log.info("🧩 开始执行启动迁移检查...");

        try {
            ensureMigrationTable();

            List<MigrationDefinition> migrations = List.of(
                    new MigrationDefinition(
                            "20260312_user_db_ownership",
                            "database_host_config 历史数据归属管理员并启用用户维度唯一约束",
                            "migration-user-db-ownership.sql"
                    ),
                    new MigrationDefinition(
                            "20260312_metadata_scope",
                            "database_overview/database_schema/table_columns 元数据按用户+主机+库隔离",
                            "migration-metadata-scope.sql"
                    ),
                    new MigrationDefinition(
                            "20260312_user_role_admin",
                            "users 增加 role 字段并回填管理员角色",
                            "migration-user-role.sql"
                    ),
                    new MigrationDefinition(
                            "20260312_custom_annotation_ownership",
                            "custom_annotations 增加 owner_user_id 并改为按用户隔离唯一约束",
                            "migration-custom-annotation-ownership.sql"
                    ),
                    new MigrationDefinition(
                            "20260312_favorite_id_bigint",
                            "修复 user_favorites 与 favorite_execution_history 外键类型不一致",
                            "migration-favorite-id-bigint.sql"
                    )
            );

            for (MigrationDefinition migration : migrations) {
                applyMigrationIfNeeded(migration);
            }

            log.info("✅ 启动迁移检查完成");
        } catch (Exception e) {
            log.error("❌ 启动迁移执行失败: {}", e.getMessage(), e);
            throw new IllegalStateException("启动迁移执行失败，请先修复数据库迁移问题", e);
        }
    }

    private void ensureMigrationTable() {
        jdbcTemplate.execute(CREATE_MIGRATION_TABLE_SQL);
    }

    private void applyMigrationIfNeeded(MigrationDefinition migration) throws Exception {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM startup_migration_history WHERE migration_version = ?",
                Integer.class,
                migration.version()
        );

        if (count != null && count > 0) {
            log.info("⏭️ 跳过已执行迁移: {}", migration.version());
            return;
        }

        log.info("🚀 执行迁移: {} -> {}", migration.version(), migration.scriptName());

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(migration.scriptName()));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        jdbcTemplate.update(
                "INSERT INTO startup_migration_history(migration_version, description, script_name) VALUES(?,?,?)",
                migration.version(),
                migration.description(),
                migration.scriptName()
        );

        log.info("✅ 迁移执行完成: {}", migration.version());
    }

    public List<Map<String, Object>> getMigrationHistory(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("仅管理员可查看迁移历史");
        }

        return jdbcTemplate.queryForList(
                "SELECT migration_version AS migrationVersion, description, script_name AS scriptName, executed_at AS executedAt " +
                        "FROM startup_migration_history ORDER BY id DESC"
        );
    }

    private record MigrationDefinition(String version, String description, String scriptName) {
    }
}
