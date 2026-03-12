-- users 表角色迁移（幂等）
-- 目标：引入 role 字段，历史 admin 账号标记为 ADMIN，其余用户默认为 USER。

SET @schema_name = DATABASE();

-- 1) 新增 role 字段（兼容低版本 MySQL）
SET @add_role_column_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `users` ADD COLUMN `role` VARCHAR(20) DEFAULT ''USER'' COMMENT ''用户角色：ADMIN/USER'' AFTER `account`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'role'
);
PREPARE stmt FROM @add_role_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 回填空角色
UPDATE `users`
SET `role` = 'USER'
WHERE `role` IS NULL OR TRIM(`role`) = '';

-- 3) 管理员账号标记为 ADMIN
UPDATE `users`
SET `role` = 'ADMIN'
WHERE LOWER(`account`) = 'admin';

-- 4) 强化 role 列约束（存在时执行）
SET @normalize_role_column_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `users` MODIFY COLUMN `role` VARCHAR(20) NOT NULL DEFAULT ''USER'' COMMENT ''用户角色：ADMIN/USER''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'role'
);
PREPARE stmt FROM @normalize_role_column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 增加 role 索引（如果不存在）
SET @add_role_index_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `users` ADD INDEX `idx_role` (`role`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_role'
);
PREPARE stmt FROM @add_role_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

