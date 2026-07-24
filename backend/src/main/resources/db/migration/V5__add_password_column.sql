-- V5: 添加密码字段，支持账户密码登录
ALTER TABLE sys_user_ebs_mapping ADD COLUMN IF NOT EXISTS password VARCHAR(255);
