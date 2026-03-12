-- NL2SQL 数据库表结构
-- 注意：使用JPA的ddl-auto=update会自动创建表，此文件仅供参考

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `nl2sql` 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `nl2sql`;

-- 1. 训练数据表
CREATE TABLE IF NOT EXISTS `training_data` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `question` LONGTEXT NOT NULL COMMENT '用户问题',
  `sql` LONGTEXT NOT NULL COMMENT '对应的SQL语句',
  `database_name` VARCHAR(100) DEFAULT NULL COMMENT '所属数据库',
  `description` TEXT COMMENT '描述说明',
  `usage_count` INT DEFAULT 0 COMMENT '使用次数',
  `success_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '成功率',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_database (`database_name`),
  INDEX idx_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练数据表';

-- 2. 数据库Schema表
CREATE TABLE IF NOT EXISTS `database_schema` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID',
  `host_config_id` INT DEFAULT NULL COMMENT '所属主机配置ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
  `table_comment` VARCHAR(500) COMMENT '表注释',
  `primary_keys` VARCHAR(500) COMMENT '主键列（逗号分隔）',
  `foreign_keys` TEXT COMMENT '外键信息（JSON格式）',
  `table_rows` BIGINT DEFAULT 0 COMMENT '表行数',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_owner_host_db_table (`owner_user_id`, `host_config_id`, `database_name`, `table_name`),
  INDEX idx_owner_host_db (`owner_user_id`, `host_config_id`, `database_name`),
  INDEX idx_database (`database_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库表结构信息';

-- 3. 表列信息表
CREATE TABLE IF NOT EXISTS `table_columns` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `schema_id` INT NOT NULL COMMENT '关联database_schema表ID',
  `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID',
  `host_config_id` INT DEFAULT NULL COMMENT '所属主机配置ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
  `column_name` VARCHAR(100) NOT NULL COMMENT '列名',
  `column_type` VARCHAR(100) COMMENT '列类型',
  `data_type` VARCHAR(50) COMMENT '数据类型',
  `column_comment` VARCHAR(500) COMMENT '列注释',
  `is_nullable` TINYINT(1) DEFAULT 1 COMMENT '是否可为空',
  `column_default` VARCHAR(500) COMMENT '默认值',
  `column_key` VARCHAR(10) COMMENT '键类型（PRI/UNI/MUL）',
  `ordinal_position` INT COMMENT '列顺序',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_owner_host_db_table_column (`owner_user_id`, `host_config_id`, `database_name`, `table_name`, `column_name`),
  INDEX idx_schema (`schema_id`),
  INDEX idx_db_table (`database_name`, `table_name`),
  INDEX idx_owner_host_table (`owner_user_id`, `host_config_id`, `database_name`, `table_name`),
  CONSTRAINT fk_column_schema FOREIGN KEY (`schema_id`) REFERENCES `database_schema`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表列信息';

-- 4. 自定义注释表
CREATE TABLE IF NOT EXISTS `custom_annotations` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `owner_user_id` INT NOT NULL COMMENT '所属用户ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
  `column_name` VARCHAR(100) DEFAULT NULL COMMENT '列名（NULL表示表注释）',
  `custom_comment` TEXT COMMENT '自定义注释',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_annotation (`owner_user_id`, `database_name`, `table_name`, `column_name`),
  INDEX idx_database (`database_name`),
  INDEX idx_owner_database (`owner_user_id`, `database_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义注释表';

-- 5. 会话历史表
CREATE TABLE IF NOT EXISTS `session_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
  `window_id` VARCHAR(100) NOT NULL COMMENT '窗口ID',
  `question` TEXT NOT NULL COMMENT '用户问题',
  `timestamp` BIGINT NOT NULL COMMENT '时间戳',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_session (`session_id`),
  INDEX idx_window (`window_id`),
  INDEX idx_timestamp (`timestamp`),
  INDEX idx_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话历史表';

-- 6. 数据库概览表
CREATE TABLE IF NOT EXISTS `database_overview` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID',
  `host_config_id` INT DEFAULT NULL COMMENT '所属主机配置ID',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `description` TEXT COMMENT '数据库描述',
  `table_summary` TEXT COMMENT '表摘要信息',
  `table_count` INT DEFAULT 0 COMMENT '表数量',
  `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否激活',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_owner_host_db_overview (`owner_user_id`, `host_config_id`, `database_name`),
  INDEX idx_owner_host_active (`owner_user_id`, `host_config_id`, `is_active`),
  INDEX idx_active (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库概览表';

-- 7. 测试记录表
CREATE TABLE IF NOT EXISTS `test_records` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `test_id` VARCHAR(50) NOT NULL UNIQUE COMMENT '测试ID',
  `question` TEXT NOT NULL COMMENT '测试问题',
  `expected_result` TEXT COMMENT '期望结果',
  `actual_result` TEXT COMMENT '实际结果',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending/passed/failed',
  `notes` TEXT COMMENT '备注',
  `images` JSON COMMENT '图片文件名列表',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_status (`status`),
  INDEX idx_created (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试记录表';

-- 8. 数据库主机配置表
CREATE TABLE IF NOT EXISTS `database_host_config` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID（迁移完成后应为非空）',
  `name` VARCHAR(50) NOT NULL COMMENT '配置名称（同一用户内唯一）',
  `host` VARCHAR(100) NOT NULL COMMENT '主机地址',
  `port` INT DEFAULT 3306 COMMENT '端口号',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(200) NOT NULL COMMENT '密码（加密）',
  `databases` TEXT COMMENT '数据库列表（JSON格式）',
  `pool_max_size` INT DEFAULT 10 COMMENT '连接池最大连接数',
  `pool_min_idle` INT DEFAULT 5 COMMENT '连接池最小空闲连接数',
  `pool_timeout` BIGINT DEFAULT 30000 COMMENT '连接超时时间（毫秒）',
  `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否激活',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_owner_name (`owner_user_id`, `name`),
  INDEX idx_owner_active (`owner_user_id`, `is_active`),
  INDEX idx_active (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库主机配置表';

-- 9. 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `description` VARCHAR(500) COMMENT '配置描述',
  `config_type` VARCHAR(20) DEFAULT 'string' COMMENT '配置类型：string/number/boolean/json',
  `is_encrypted` TINYINT(1) DEFAULT 0 COMMENT '是否加密',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_key (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 10. 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `account` VARCHAR(50) NOT NULL UNIQUE COMMENT '账号',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(200) NOT NULL COMMENT '密码（加密）',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(20) COMMENT '手机号',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_account (`account`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
