-- 自定义注释按用户隔离迁移脚本（幂等）
-- 目标：custom_annotations 增加 owner_user_id，并按用户维度唯一。

SET @schema_name = DATABASE();

-- 1) 增加 owner 字段（过渡期允许为空，兼容低版本 MySQL）
SET @add_owner_column_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `custom_annotations` ADD COLUMN `owner_user_id` INT DEFAULT NULL COMMENT ''所属用户ID'' AFTER `id`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'custom_annotations'
      AND COLUMN_NAME = 'owner_user_id'
);
PREPARE stmt FROM @add_owner_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 删除旧 uk_annotation（如果存在）
SET @drop_old_uk_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `custom_annotations` DROP INDEX `uk_annotation`',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'custom_annotations'
      AND INDEX_NAME = 'uk_annotation'
);
PREPARE stmt FROM @drop_old_uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 新增按用户隔离的唯一索引（如果不存在）
SET @add_owner_uk_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `custom_annotations` ADD UNIQUE KEY `uk_annotation` (`owner_user_id`, `database_name`, `table_name`, `column_name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'custom_annotations'
      AND INDEX_NAME = 'uk_annotation'
);
PREPARE stmt FROM @add_owner_uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 新增查询索引（如果不存在）
SET @add_owner_idx_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `custom_annotations` ADD INDEX `idx_owner_database` (`owner_user_id`, `database_name`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'custom_annotations'
      AND INDEX_NAME = 'idx_owner_database'
);
PREPARE stmt FROM @add_owner_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 历史数据归管理员（默认账号 admin，不存在则回退到最小用户ID）
SET @admin_user_id = (
    SELECT id
    FROM users
    WHERE account = 'admin'
    ORDER BY id
    LIMIT 1
);
SET @admin_user_id = COALESCE(@admin_user_id, (SELECT MIN(id) FROM users));

UPDATE `custom_annotations`
SET `owner_user_id` = @admin_user_id
WHERE `owner_user_id` IS NULL
  AND @admin_user_id IS NOT NULL;

-- 6) 如需强制非空，可在确认历史数据完成后手动执行：
-- ALTER TABLE `custom_annotations` MODIFY COLUMN `owner_user_id` INT NOT NULL;

