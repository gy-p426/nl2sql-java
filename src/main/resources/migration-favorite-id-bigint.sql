-- Align legacy favorite FK types with current JPA mapping (user_favorites.id = BIGINT).
-- Safe to run multiple times.

SET @schema_name = DATABASE();

-- 1) Drop legacy FK first, otherwise parent column type change may fail.
SET @drop_fk_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `favorite_execution_history` DROP FOREIGN KEY `favorite_execution_history_ibfk_1`',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND TABLE_NAME = 'favorite_execution_history'
      AND CONSTRAINT_NAME = 'favorite_execution_history_ibfk_1'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) Upgrade child FK column to BIGINT if table/column exists.
SET @alter_child_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `favorite_execution_history` MODIFY COLUMN `favorite_id` BIGINT NOT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'favorite_execution_history'
      AND COLUMN_NAME = 'favorite_id'
      AND DATA_TYPE <> 'bigint'
);
PREPARE stmt FROM @alter_child_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) Ensure parent PK column matches entity type (Long -> BIGINT AUTO_INCREMENT).
SET @alter_parent_sql = (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE `user_favorites` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'user_favorites'
      AND COLUMN_NAME = 'id'
      AND (DATA_TYPE <> 'bigint' OR EXTRA NOT LIKE '%auto_increment%')
);
PREPARE stmt FROM @alter_parent_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) Recreate FK if missing.
SET @add_fk_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `favorite_execution_history` ADD CONSTRAINT `favorite_execution_history_ibfk_1` FOREIGN KEY (`favorite_id`) REFERENCES `user_favorites` (`id`)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @schema_name
      AND TABLE_NAME = 'favorite_execution_history'
      AND CONSTRAINT_NAME = 'favorite_execution_history_ibfk_1'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

