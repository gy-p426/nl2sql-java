-- 用户信息表
CREATE TABLE IF NOT EXISTS `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `account` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户账号',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色：ADMIN/USER',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
  `security_question` VARCHAR(200) COMMENT '找回密码的问题',
  `security_answer` VARCHAR(200) COMMENT '找回密码问题的答案（加密存储）',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(20) COMMENT '手机号',
  `status` TINYINT(1) DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_account (`account`),
  INDEX idx_username (`username`),
  INDEX idx_role (`role`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 修改会话历史表，添加用户ID字段
ALTER TABLE `session_history` 
ADD COLUMN `user_id` INT DEFAULT NULL COMMENT '用户ID' AFTER `window_id`,
ADD INDEX idx_user (`user_id`);
