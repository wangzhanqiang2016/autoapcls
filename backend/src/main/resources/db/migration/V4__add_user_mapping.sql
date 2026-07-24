-- V4: 飞书真实用户 Open ID 映射补充

-- ou_9409695854fdd36e10bf1b0638b6d293 — 真实飞书 OAuth 登录返回的 open_id
INSERT INTO sys_user_ebs_mapping (feishu_open_id, feishu_name, ebs_user_name, is_active) VALUES
('ou_9409695854fdd36e10bf1b0638b6d293', '真实飞书用户', 'WANGZHANQIANG', TRUE);
