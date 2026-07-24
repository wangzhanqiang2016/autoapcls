-- V2: 种子数据 — 14步定义 + 测试用户映射 + 跨职责账号配置

-- 14步月结步骤定义
INSERT INTO ap_close_step_def (step_no, step_name, step_type, ebs_program, ebs_resp_type, description, sort_order) VALUES
(1,  '日常业务处理确认',       'MANUAL_CONFIRM', NULL,                           'AP', '确认本月发票和付款凭证已全部录入系统', 1),
(2,  '检查未验证发票',         'AUTO_CHECK',     'SQL:AP_INVOICES_UTILITY_PKG',  'AP', '查询状态为从未验证和需要重新验证的发票', 2),
(3,  '检查暂挂发票',           'AUTO_CHECK',     'SQL:AP_HOLDS_PKG',             'AP', '查询状态为暂挂的发票及暂挂原因', 3),
(4,  '导出应付发票信息报表',    'REPORT_EXPORT',  'CUX:应付发票信息报表',          'AP', '导出当月全部应付发票数据', 4),
(5,  '导出应付票据数据',        'REPORT_EXPORT',  'CUX:应付票据报表',              'AP', '导出应付票据信息，查看远期付款到期日', 5),
(6,  '对远期付款进行到期支付',   'EBS_REQUEST',   '更新到期应付票据状态',           'AP', '对远期付款执行到期支付', 6),
(7,  '检查未过账事务处理',      'REPORT_EXPORT',  '未入帐事务处理报表 (XML)',       'AP', '检查是否存在未过账的事务处理', 7),
(8,  '创建会计科目(首次)',      'EBS_REQUEST',   '创建会计科目',                   'AP', '创建会计科目并传送至总账(首次)', 8),
(9,  '传送日记账分录至GL(首次)','EBS_REQUEST',   '将日记帐分录传送至GL',           'AP', '将最终会计分录传至总账', 9),
(10, '核对子模块与总账余额',    'REPORT_EXPORT',  'CUX:供应商帐户余额汇总表',       'AP', '检查子模块余额与总账是否一致', 10),
(11, '核查应付暂估数据',        'REPORT_EXPORT',  'CUX:应付暂估汇总表',             'AP', '核对应付暂估业务数、财务数和总账数(三报表联动)', 11),
(12, '核对供应商账龄',          'REPORT_EXPORT',  'CUX:供应商帐龄报表',             'AP', '核对供应商账龄报表', 12),
(13, '创建会计科目+传送GL(最终)','EBS_REQUEST',   '创建会计科目',                   'AP', '再次创建会计科目并传送至总账(最终)', 13),
(14, '关闭应付会计期间',        'EBS_REQUEST',    '子分类帐期间关闭',              'AP', '关闭当月应付期间，打开下月期间', 14);

-- 测试用户：飞书 open_id → EBS 账号映射
INSERT INTO sys_user_ebs_mapping (feishu_open_id, feishu_name, ebs_user_name, ebs_user_id, is_active) VALUES
('ou_test_admin_001', '测试会计', 'AP_ACCOUNTANT', 1001, TRUE),
('ou_test_admin_002', '成本会计', 'CST_ACCOUNTANT', 1002, TRUE),
('ou_test_admin_003', '总账会计', 'GL_ACCOUNTANT', 1003, TRUE);

-- 跨职责 EBS 账号配置（用于步骤11三报表提交）
INSERT INTO sys_ebs_account_config (resp_type, ebs_user_name, ebs_password, description) VALUES
('AP',  'AP_ACCOUNTANT',  'encrypted_ap_pwd',  '应付会计 EBS 账号'),
('CST', 'CST_ACCOUNTANT', 'encrypted_cst_pwd', '成本会计 EBS 账号'),
('GL',  'GL_ACCOUNTANT',  'encrypted_gl_pwd',  '总账会计 EBS 账号');
