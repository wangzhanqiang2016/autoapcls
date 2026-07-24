-- V6: 添加 ebs_phase_code 字段，记录 EBS 并发请求的 phase 状态
ALTER TABLE ap_close_task ADD COLUMN IF NOT EXISTS ebs_phase_code VARCHAR(20);
