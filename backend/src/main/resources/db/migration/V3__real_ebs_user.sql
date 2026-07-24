-- V3: EBS 8010 真实用户映射

-- 王占强 — 飞书 OAuth 授权返回的应用级 open_id（真实飞书登录用）
INSERT INTO sys_user_ebs_mapping (feishu_open_id, feishu_name, ebs_user_name, is_active) VALUES
('cEElz7yeaKw348af9xFEIxwAdFLF4LCA', '王占强', 'WANGZHANQIANG', TRUE);

-- 王占强 — 通讯录 open_id（Mock 模式用，与上面是同一个人的不同 open_id）
INSERT INTO sys_user_ebs_mapping (feishu_open_id, feishu_name, ebs_user_name, is_active) VALUES
('ou_f4a2b2ae8d61a4207f8e63e0749c202f', '王占强', 'WANGZHANQIANG', TRUE);

-- 标记原有测试账号为非活跃（可选，注释掉以保留）
-- UPDATE sys_user_ebs_mapping SET is_active = FALSE WHERE feishu_open_id LIKE 'ou_test_%';
