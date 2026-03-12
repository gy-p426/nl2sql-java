-- 用户数据库主机配置隔离迁移脚本（数据库级权限）
-- 适用场景：将历史 database_host_config 统一归属到管理员账号。
-- 执行前请先确认管理员账号存在于 users 表（默认 account='admin'）。

USE `nl2sql`;

-- 1) 增加 owner_user_id（过渡期允许为空）
ALTER TABLE `database_host_config`
ADD COLUMN IF NOT EXISTS `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID（迁移完成后应为非空）' AFTER `id`;

-- 2) 先删除旧的 name 唯一约束（约束名在不同环境可能不同，请按实际调整）
-- 常见可能是 `name` 或 `database_host_config_ibfk_*` 之外的 unique index。
-- 如果下面语句报错，请先 SHOW INDEX FROM database_host_config; 再替换索引名执行。
DROP INDEX `name` ON `database_host_config`;

-- 3) 新增“同一用户内唯一”约束与索引
ALTER TABLE `database_host_config`
ADD UNIQUE KEY `uk_owner_name` (`owner_user_id`, `name`),
ADD INDEX `idx_owner_active` (`owner_user_id`, `is_active`);

-- 4) 回填历史配置归属管理员
SET @admin_user_id = (
  SELECT id FROM users WHERE account = 'admin' LIMIT 1
);

UPDATE `database_host_config`
SET `owner_user_id` = @admin_user_id
WHERE `owner_user_id` IS NULL;

-- 5) 迁移完成后再执行：将 owner_user_id 设为非空
-- ALTER TABLE `database_host_config` MODIFY COLUMN `owner_user_id` INT NOT NULL;

-- 6) 可选：增加外键（仅在确认 users 表结构稳定后启用）
-- ALTER TABLE `database_host_config`
--   ADD CONSTRAINT `fk_db_host_owner_user`
--   FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`)
--   ON UPDATE RESTRICT ON DELETE RESTRICT;

