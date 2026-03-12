-- 元数据作用域隔离迁移（幂等）
-- 目标：database_overview / database_schema / table_columns 按 owner_user_id + host_config_id + database_name 隔离。

SET @schema_name = DATABASE();

-- ===== 1) database_overview =====
SET @add_overview_owner_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_overview` ADD COLUMN `owner_user_id` INT DEFAULT NULL AFTER `id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_overview'
      AND COLUMN_NAME = 'owner_user_id'
);
PREPARE stmt FROM @add_overview_owner_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_overview_host_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_overview` ADD COLUMN `host_config_id` INT DEFAULT NULL AFTER `owner_user_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_overview'
      AND COLUMN_NAME = 'host_config_id'
);
PREPARE stmt FROM @add_overview_host_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填 overview 归属（按 database_name 在 host.databases JSON 中匹配）
UPDATE `database_overview` o
SET
  o.owner_user_id = (
    SELECT h.owner_user_id
    FROM `database_host_config` h
    WHERE h.is_active = 1
      AND JSON_VALID(h.databases)
      AND JSON_CONTAINS(h.databases, JSON_QUOTE(o.database_name))
    ORDER BY h.id
    LIMIT 1
  ),
  o.host_config_id = (
    SELECT h.id
    FROM `database_host_config` h
    WHERE h.is_active = 1
      AND JSON_VALID(h.databases)
      AND JSON_CONTAINS(h.databases, JSON_QUOTE(o.database_name))
    ORDER BY h.id
    LIMIT 1
  )
WHERE o.owner_user_id IS NULL OR o.host_config_id IS NULL;

SET @drop_overview_old_unique_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `database_overview` DROP INDEX `database_name`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_overview'
      AND INDEX_NAME = 'database_name'
      AND NON_UNIQUE = 0
);
PREPARE stmt FROM @drop_overview_old_unique_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_overview_scope_uk_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_overview` ADD UNIQUE KEY `uk_owner_host_db_overview` (`owner_user_id`, `host_config_id`, `database_name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_overview'
      AND INDEX_NAME = 'uk_owner_host_db_overview'
);
PREPARE stmt FROM @add_overview_scope_uk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_overview_scope_idx_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_overview` ADD INDEX `idx_owner_host_active` (`owner_user_id`, `host_config_id`, `is_active`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_overview'
      AND INDEX_NAME = 'idx_owner_host_active'
);
PREPARE stmt FROM @add_overview_scope_idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 2) database_schema =====
SET @add_schema_owner_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_schema` ADD COLUMN `owner_user_id` INT DEFAULT NULL AFTER `id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_schema'
      AND COLUMN_NAME = 'owner_user_id'
);
PREPARE stmt FROM @add_schema_owner_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_schema_host_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_schema` ADD COLUMN `host_config_id` INT DEFAULT NULL AFTER `owner_user_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_schema'
      AND COLUMN_NAME = 'host_config_id'
);
PREPARE stmt FROM @add_schema_host_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `database_schema` s
SET
  s.owner_user_id = (
    SELECT h.owner_user_id
    FROM `database_host_config` h
    WHERE h.is_active = 1
      AND JSON_VALID(h.databases)
      AND JSON_CONTAINS(h.databases, JSON_QUOTE(s.database_name))
    ORDER BY h.id
    LIMIT 1
  ),
  s.host_config_id = (
    SELECT h.id
    FROM `database_host_config` h
    WHERE h.is_active = 1
      AND JSON_VALID(h.databases)
      AND JSON_CONTAINS(h.databases, JSON_QUOTE(s.database_name))
    ORDER BY h.id
    LIMIT 1
  )
WHERE s.owner_user_id IS NULL OR s.host_config_id IS NULL;

SET @drop_schema_old_uk_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `database_schema` DROP INDEX `uk_db_table`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_schema'
      AND INDEX_NAME = 'uk_db_table'
);
PREPARE stmt FROM @drop_schema_old_uk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_schema_scope_uk_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_schema` ADD UNIQUE KEY `uk_owner_host_db_table` (`owner_user_id`, `host_config_id`, `database_name`, `table_name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_schema'
      AND INDEX_NAME = 'uk_owner_host_db_table'
);
PREPARE stmt FROM @add_schema_scope_uk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 3) table_columns =====
SET @add_columns_owner_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `table_columns` ADD COLUMN `owner_user_id` INT DEFAULT NULL AFTER `schema_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'table_columns'
      AND COLUMN_NAME = 'owner_user_id'
);
PREPARE stmt FROM @add_columns_owner_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_columns_host_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `table_columns` ADD COLUMN `host_config_id` INT DEFAULT NULL AFTER `owner_user_id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'table_columns'
      AND COLUMN_NAME = 'host_config_id'
);
PREPARE stmt FROM @add_columns_host_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `table_columns` c
JOIN `database_schema` s ON s.id = c.schema_id
SET c.owner_user_id = s.owner_user_id,
    c.host_config_id = s.host_config_id
WHERE c.owner_user_id IS NULL OR c.host_config_id IS NULL;

SET @drop_columns_old_uk_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `table_columns` DROP INDEX `uk_db_table_column`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'table_columns'
      AND INDEX_NAME = 'uk_db_table_column'
);
PREPARE stmt FROM @drop_columns_old_uk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_columns_scope_uk_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `table_columns` ADD UNIQUE KEY `uk_owner_host_db_table_column` (`owner_user_id`, `host_config_id`, `database_name`, `table_name`, `column_name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'table_columns'
      AND INDEX_NAME = 'uk_owner_host_db_table_column'
);
PREPARE stmt FROM @add_columns_scope_uk_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

