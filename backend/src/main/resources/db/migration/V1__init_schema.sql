-- V1: 初始化核心表结构
-- 对应技术设计文档 2.1、2.2、2.5、9.3 节

-- 1. 飞书用户与 EBS 账号映射表
CREATE TABLE sys_user_ebs_mapping (
    id              BIGSERIAL PRIMARY KEY,
    feishu_open_id  VARCHAR(128) NOT NULL UNIQUE,
    feishu_name     VARCHAR(128),
    ebs_user_name   VARCHAR(128) NOT NULL,
    ebs_user_id     INTEGER,
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 用户登录会话表
CREATE TABLE sys_user_session (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    feishu_open_id      VARCHAR(128),
    selected_resp_id    INTEGER,
    selected_resp_name  VARCHAR(256),
    selected_org_id     INTEGER,
    selected_org_code   VARCHAR(64),
    default_ou_id       INTEGER,
    default_ou_name     VARCHAR(256),
    default_ledger_id   INTEGER,
    default_ledger_name VARCHAR(256),
    period_name         VARCHAR(64),
    login_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_at           TIMESTAMP
);

-- 3. EBS 跨职责账号配置表
CREATE TABLE sys_ebs_account_config (
    id              SERIAL PRIMARY KEY,
    resp_type       VARCHAR(32) NOT NULL UNIQUE,
    ebs_user_name   VARCHAR(128) NOT NULL,
    ebs_password    VARCHAR(256) NOT NULL,
    description     VARCHAR(256)
);

-- 4. 月结步骤定义表
CREATE TABLE ap_close_step_def (
    id              SERIAL PRIMARY KEY,
    step_no         INTEGER NOT NULL,
    step_name       VARCHAR(256) NOT NULL,
    step_type       VARCHAR(32) NOT NULL,
    ebs_program     VARCHAR(256),
    ebs_resp_type   VARCHAR(64),
    description     TEXT,
    sort_order      INTEGER DEFAULT 0
);

-- 5. 月结任务实例表
CREATE TABLE ap_close_task (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    step_no             INTEGER NOT NULL,
    period_name         VARCHAR(64) NOT NULL,
    org_id              INTEGER NOT NULL,
    ou_id               INTEGER NOT NULL,
    ledger_id           INTEGER,
    status              VARCHAR(32) DEFAULT 'PENDING',
    ebs_request_id      BIGINT,
    ebs_request_status  VARCHAR(32),
    output_file_path    VARCHAR(512),
    error_message       TEXT,
    params_json         JSONB,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_session ON ap_close_task(session_id);
CREATE INDEX idx_task_period ON ap_close_task(period_name, org_id);

-- 6. 文件元数据表
CREATE TABLE sys_file_record (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(512) NOT NULL,
    file_path       VARCHAR(1024) NOT NULL,
    file_size       BIGINT,
    file_type       VARCHAR(64),
    org_code        VARCHAR(64),
    period_name     VARCHAR(64),
    step_no         INTEGER,
    task_id         BIGINT,
    ebs_request_id  BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_org_period ON sys_file_record(org_code, period_name);
CREATE INDEX idx_file_type ON sys_file_record(file_type);
