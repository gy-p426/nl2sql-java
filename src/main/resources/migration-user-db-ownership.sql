-- 用户数据库主机配置隔离迁移脚本（幂等）
-- 目标：将历史 database_host_config 归属到管理员，并启用 (owner_user_id, name) 唯一约束。

SET @schema_name = DATABASE();

-- 1) 增加 owner_user_id（过渡期允许为空，兼容低版本 MySQL）
SET @add_owner_column_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_host_config` ADD COLUMN `owner_user_id` INT DEFAULT NULL COMMENT ''所属用户ID（迁移完成后应为非空）'' AFTER `id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_host_config'
      AND COLUMN_NAME = 'owner_user_id'
);
PREPARE stmt FROM @add_owner_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 删除旧的 name 唯一索引（如果存在）
SET @drop_old_unique_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `database_host_config` DROP INDEX `name`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_host_config'
      AND INDEX_NAME = 'name'
      AND NON_UNIQUE = 0
);
PREPARE stmt FROM @drop_old_unique_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 新增“同一用户内唯一”约束（如果不存在）
SET @add_owner_unique_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_host_config` ADD UNIQUE KEY `uk_owner_name` (`owner_user_id`, `name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_host_config'
      AND INDEX_NAME = 'uk_owner_name'
);
PREPARE stmt FROM @add_owner_unique_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 新增按 owner 激活索引（如果不存在）
SET @add_owner_active_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `database_host_config` ADD INDEX `idx_owner_active` (`owner_user_id`, `is_active`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'database_host_config'
      AND INDEX_NAME = 'idx_owner_active'
);
PREPARE stmt FROM @add_owner_active_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 回填历史配置归属管理员（管理员不存在时回退最小用户ID）
SET @admin_user_id = (
    SELECT id
    FROM users
    WHERE LOWER(account) = 'admin'
    ORDER BY id
    LIMIT 1
);
SET @admin_user_id = COALESCE(@admin_user_id, (SELECT MIN(id) FROM users));

UPDATE `database_host_config`
SET `owner_user_id` = @admin_user_id
WHERE `owner_user_id` IS NULL
  AND @admin_user_id IS NOT NULL;

-- 如需强制非空，可在确认历史数据后手工执行：
-- ALTER TABLE `database_host_config` MODIFY COLUMN `owner_user_id` INT NOT NULL;


