-- 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT COMMENT '配置值',
  `config_type` VARCHAR(20) DEFAULT 'string' COMMENT '配置类型：string/number/boolean/json',
  `category` VARCHAR(50) COMMENT '配置分类：database/volcano_engine/ollama/model/settings',
  `description` VARCHAR(500) COMMENT '配置描述',
  `is_encrypted` TINYINT(1) DEFAULT 0 COMMENT '是否加密',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_category (`category`),
  INDEX idx_key (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 数据库主机配置表
CREATE TABLE IF NOT EXISTS `database_host_config` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `owner_user_id` INT DEFAULT NULL COMMENT '所属用户ID（迁移完成后应为非空）',
  `name` VARCHAR(50) NOT NULL COMMENT '主机名称（同一用户内唯一）',
  `host` VARCHAR(100) NOT NULL COMMENT '主机地址',
  `port` INT DEFAULT 3306 COMMENT '端口',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(200) NOT NULL COMMENT '密码（加密）',
  `databases` TEXT COMMENT '数据库列表（JSON数组）',
  `pool_max_size` INT DEFAULT 10 COMMENT '连接池最大大小',
  `pool_min_idle` INT DEFAULT 5 COMMENT '连接池最小空闲',
  `pool_timeout` BIGINT DEFAULT 30000 COMMENT '连接超时（毫秒）',
  `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否激活',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_owner_name (`owner_user_id`, `name`),
  INDEX idx_owner_active (`owner_user_id`, `is_active`),
  INDEX idx_active (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库主机配置表';

-- 初始化默认配置
INSERT INTO `system_config` (`config_key`, `config_value`, `config_type`, `category`, `description`) VALUES
-- 火山引擎配置
('volcano_engine.api_key', '', 'string', 'volcano_engine', '火山引擎API密钥'),
('volcano_engine.model', 'deepseek-v3-1-250821', 'string', 'volcano_engine', '火山引擎模型'),
('volcano_engine.timeout', '1800000', 'number', 'volcano_engine', '火山引擎超时时间（毫秒）'),
('volcano_engine.base_url', 'https://ark.cn-beijing.volces.com/api/v3', 'string', 'volcano_engine', '火山引擎API地址'),

-- Ollama配置
('ollama.enabled', 'false', 'boolean', 'ollama', 'Ollama是否启用'),
('ollama.host', 'http://localhost:11435', 'string', 'ollama', 'Ollama主机地址'),
('ollama.model', 'deepseek-r1:14b', 'string', 'ollama', 'Ollama模型'),
('ollama.timeout', '1800000', 'number', 'ollama', 'Ollama超时时间（毫秒）'),

-- 模型提供商
('model.provider', 'volcano_engine', 'string', 'model', '模型提供商：volcano_engine或ollama'),

-- 系统设置
('settings.refresh_db', 'true', 'boolean', 'settings', '是否刷新数据库'),
('settings.refresh_schema', 'true', 'boolean', 'settings', '是否刷新Schema'),
('settings.save_results', 'false', 'boolean', 'settings', '是否保存结果'),
('settings.enforce_db_ownership', 'false', 'boolean', 'settings', '是否强制按用户隔离数据库访问权限'),
('settings.max_retries', '5', 'number', 'settings', '最大重试次数'),
('settings.cache_max_size', '1000', 'number', 'settings', '缓存最大大小'),
('settings.cache_expire_minutes', '60', 'number', 'settings', '缓存过期时间（分钟）')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);
