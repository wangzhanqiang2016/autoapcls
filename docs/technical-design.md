# 应付自动结账系统 — 技术设计文档

> 版本：1.0 | 日期：2026/07/21 | 基于需求说明书 V1.1

---

## 1. 系统架构总览

### 1.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     飞书统一认证 (OAuth 2.0)                   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  前端 SPA (Vue 3 + Element Plus)             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ 登录/授权 │ │ 职责选择  │ │ 月结工作台 │ │ 报表/文件管理  │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │ HTTPS (JWT Token)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   后端服务 (Spring Boot 3)                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ 认证服务  │ │ 月结工作流│ │ 报表服务  │ │ 文件存储服务   │  │
│  │ AuthSvc  │ │CloseSvc  │ │ReportSvc │ │ FileStoreSvc  │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │            Oracle EBS 集成层 (EBSIntegration)         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐  │   │
│  │  │JDBC查询  │ │并发请求API│ │ 请求状态轮询/结果导出 │  │   │
│  │  └──────────┘ └──────────┘ └──────────────────────┘  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
    ┌──────────┐        ┌──────────┐        ┌──────────────┐
    │ PostgreSQL│        │ Oracle EBS│        │  文件存储     │
    │(业务数据) │        │ (EBS库)   │        │ (本地/NAS)    │
    └──────────┘        └──────────┘        └──────────────┘
```

### 1.2 技术选型

| 层次 | 技术 | 版本 | 选型理由 |
|------|------|------|---------|
| 前端框架 | Vue 3 | 3.4+ | 组合式API，生态成熟 |
| UI 组件库 | Element Plus | 2.7+ | 企业级中文社区支持，开源 MIT 协议 |
| 图标库 | Element Plus Icons | — | 与组件库配套，开源免费 |
| 状态管理 | Pinia | 2.x | Vue 3 官方推荐 |
| 路由 | Vue Router | 4.x | Vue 3 官方路由 |
| HTTP 客户端 | Axios | 1.x | 拦截器支持 JWT 刷新 |
| 后端框架 | Spring Boot | 3.x | 企业级 Java 生态 |
| ORM | MyBatis-Plus | 3.5+ | 灵活 SQL 映射，适合复杂查询 |
| 业务数据库 | PostgreSQL | 15+ | 开源，支持 JSON/全文检索/大对象存储 |
| 连接池 | HikariCP | — | Spring Boot 默认，高性能 |
| 飞书 SDK | Lark OAuth 2.0 | — | 飞书开放平台标准协议 |
| EBS 集成 | JDBC + Oracle REST | — | 直接查询 + 并发请求提交 |

---

## 2. 模块设计

### 2.1 认证与权限模块 (对应需求：一.1)

#### 2.1.1 飞书登录流程

```
用户浏览器                    Spring Boot                  飞书开放平台
    │                            │                            │
    │  1. GET /api/auth/login    │                            │
    │─────────────────────────▶│                            │
    │                            │  2. 构造OAuth URL           │
    │  3. 302 跳转飞书授权页      │──────────────────────────▶│
    │◀─────────────────────────│                            │
    │                            │                            │
    │  4. 用户授权后回调          │                            │
    │─────────────────────────▶│                            │
    │  (带 code)                 │  5. 用code换access_token    │
    │                            │──────────────────────────▶│
    │                            │  6. 返回 access_token       │
    │                            │◀─────────────────────────│
    │                            │  7. 获取用户信息             │
    │                            │──────────────────────────▶│
    │                            │  8. 返回 user_info          │
    │                            │◀─────────────────────────│
    │                            │                            │
    │                            │  9. 查询飞书用户↔EBS账号映射  │
    │                            │  10. 生成JWT Token          │
    │  11. 返回 JWT + 用户信息    │                            │
    │◀─────────────────────────│                            │
```

#### 2.1.2 用户-EBS账号映射表

```sql
CREATE TABLE sys_user_ebs_mapping (
    id              BIGSERIAL PRIMARY KEY,
    feishu_open_id  VARCHAR(128) NOT NULL UNIQUE,   -- 飞书用户唯一标识
    feishu_name     VARCHAR(128),                    -- 飞书姓名
    ebs_user_name   VARCHAR(128) NOT NULL,           -- Oracle EBS 用户名
    ebs_user_id     INTEGER,                         -- EBS FND_USER.USER_ID
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 2.1.3 职责与库存组织选择

- 登录后调用 EBS 接口查询 `FND_USER_RESP_GROUPS` 获取用户职责列表
- 调用 `ORG_ORGANIZATION_DEFINITIONS` 查询用户可访问的库存组织
- 用户选择后，前端持久化至 `localStorage`，后端记录至 `sys_user_session` 表

```sql
CREATE TABLE sys_user_session (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    feishu_open_id      VARCHAR(128),
    selected_resp_id    INTEGER,          -- 用户选择的职责ID
    selected_resp_name  VARCHAR(256),     -- 职责名称
    selected_org_id     INTEGER,          -- 选择的库存组织ID
    selected_org_code   VARCHAR(64),      -- 库存组织代码
    default_ou_id       INTEGER,          -- 默认OU ID
    default_ou_name     VARCHAR(256),     -- 默认OU名称
    default_ledger_id   INTEGER,          -- 默认账套ID
    default_ledger_name VARCHAR(256),     -- 默认账套名称
    period_name         VARCHAR(64),      -- 当前结账期间
    login_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_at           TIMESTAMP
);
```

#### 2.1.4 配置参数加载

职责对应的默认配置（OU、账套等）从 EBS 配置文件 `FND_PROFILE_OPTION_VALUES` 查询：

| 配置项 | 配置文件名称 | 用途 |
|--------|-------------|------|
| 默认业务实体 | `ORG_ID` / `MO: Default Operating Unit` | 确定当前 OU |
| 默认分类账 | `GL_SET_OF_BKS_ID` | 确定账套 |
| 应付账龄时段 | `AP_AGING_PERIOD_SET` | 步骤12 账龄报表参数 |

---

### 2.2 应付月结工作流模块 (对应需求：应付模块 1-14)

#### 2.2.1 工作流状态机

月结流程按 14 个步骤顺序执行，每个步骤有 4 种状态：

```
状态流转： 等待中 ──▶ 进行中 ──▶ 已完成
                        │
                        └──▶ 异常(需人工处理)
```

#### 2.2.2 工作流步骤定义

```sql
CREATE TABLE ap_close_step_def (
    id              SERIAL PRIMARY KEY,
    step_no         INTEGER NOT NULL,          -- 步骤编号 1-14
    step_name       VARCHAR(256) NOT NULL,     -- 步骤名称
    step_type       VARCHAR(32) NOT NULL,      -- AUTO_CHECK / MANUAL_CONFIRM / REPORT_EXPORT / EBS_REQUEST
    ebs_program     VARCHAR(256),              -- EBS 并发程序简称
    ebs_resp_type   VARCHAR(64),               -- 提交所需职责类型: AP / GL / CST
    description     TEXT,
    sort_order      INTEGER DEFAULT 0
);
```

步骤定义数据：

| 步骤 | 名称 | 类型 | EBS 程序 | 职责 |
|------|------|------|---------|------|
| 1 | 日常业务处理确认 | MANUAL_CONFIRM | — | AP |
| 2 | 检查未验证发票 | AUTO_CHECK | SQL:AP_INVOICES_UTILITY_PKG | AP |
| 3 | 检查暂挂发票 | AUTO_CHECK | SQL:AP_HOLDS_PKG | AP |
| 4 | 导出应付发票信息报表 | REPORT_EXPORT | CUX:应付发票信息报表 | AP |
| 5 | 导出应付票据报表 | REPORT_EXPORT | CUX:应付票据报表 | AP |
| 6 | 更新到期应付票据状态 | EBS_REQUEST | 更新到期应付票据状态 | AP |
| 7 | 检查未过账事务处理 | REPORT_EXPORT | 未入帐事务处理报表 (XML) | AP |
| 8 | 创建会计科目(首次) | EBS_REQUEST | 创建会计科目 | AP |
| 9 | 传送日记账分录至GL(首次) | EBS_REQUEST | 将日记帐分录传送至GL | AP |
| 10 | 核对子模块与总账余额 | REPORT_EXPORT | CUX:供应商帐户余额汇总表 | AP |
| 11 | 核查应付暂估数据 | REPORT_EXPORT | 三个报表(见2.2.12) | AP/CST/GL |
| 12 | 核对供应商账龄 | REPORT_EXPORT | CUX:供应商帐龄报表 | AP |
| 13 | 创建会计科目+传送GL(最终) | EBS_REQUEST | 创建会计科目+传送GL | AP |
| 14 | 关闭应付会计期间 | EBS_REQUEST | 子分类帐期间关闭 | AP |

#### 2.2.3 月结任务实例表

```sql
CREATE TABLE ap_close_task (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL,           -- 关联用户会话
    step_no             INTEGER NOT NULL,
    period_name         VARCHAR(64) NOT NULL,      -- 结账期间, 如 '2026-07'
    org_id              INTEGER NOT NULL,          -- 库存组织ID
    ou_id               INTEGER NOT NULL,          -- 业务实体ID
    ledger_id           INTEGER,                   -- 账套ID
    status              VARCHAR(32) DEFAULT 'PENDING',
    -- PENDING / RUNNING / COMPLETED / FAILED / SKIPPED
    ebs_request_id      BIGINT,                    -- EBS 并发请求ID
    ebs_request_status  VARCHAR(32),               -- EBS 请求状态
    output_file_path    VARCHAR(512),              -- 输出文件路径
    error_message       TEXT,                      -- 错误信息
    params_json         JSONB,                     -- 请求参数快照
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_session ON ap_close_task(session_id);
CREATE INDEX idx_task_period ON ap_close_task(period_name, org_id);
```

---

### 2.3 各步骤详细设计

#### 步骤1：日常业务处理确认 (MANUAL_CONFIRM)

- 前端展示提示信息，提醒应付会计完成发票录入和付款凭证录入
- 用户点击"确认完成"按钮后，状态变为 COMPLETED
- 无需调用 EBS 接口

#### 步骤2：检查未验证发票 (AUTO_CHECK)

**实现方式：JDBC 直连 Oracle EBS 查询**

```sql
-- 查询从未验证的发票
SELECT ai.invoice_id, ai.invoice_num, ai.invoice_date,
       ai.vendor_id, ai.invoice_amount, ai.status
FROM ap_invoices_all ai
WHERE ai.org_id = :orgId
  AND ai.status = 'NEVER_VALIDATED';

-- 查询需要重新验证的发票
SELECT ai.invoice_id, ai.invoice_num, ai.invoice_date,
       ai.vendor_id, ai.invoice_amount, ai.status
FROM ap_invoices_all ai
WHERE ai.org_id = :orgId
  AND ai.status = 'REQUIRES_REVALIDATION';
```

- 后端执行查询，统计各状态发票数量
- 前端展示查询结果，如有未验证发票则标记为"异常"，列出明细
- 用户在 EBS 中处理后，可重新执行检查

#### 步骤3：检查暂挂发票 (AUTO_CHECK)

**实现方式：JDBC 直连 Oracle EBS 查询**

```sql
SELECT ai.invoice_id, ai.invoice_num, ai.invoice_date,
       ai.vendor_id, ai.invoice_amount,
       ah.hold_reason, ah.hold_lookup_code
FROM ap_invoices_all ai
JOIN ap_holds_all ah ON ai.invoice_id = ah.invoice_id
WHERE ai.org_id = :orgId
  AND ah.hold_flag = 'Y'
  AND ah.release_flag = 'N';
```

- 后端执行查询，返回暂挂发票列表及暂挂原因
- 前端展示明细，用户根据原因去 EBS 处理

#### 步骤4：导出应付发票信息报表 (REPORT_EXPORT)

**EBS 并发程序：CUX:应付发票信息报表**

请求参数构造：

```
起始GL日期 = 当前期间第一天 (如 '2026-07-01')
终止GL日期 = 当前期间最后一天 (如 '2026-07-31')
包含已取消发票 = 'N'
```

处理流程：
1. 调用 EBS `FND_REQUEST.SUBMIT_REQUEST` 提交并发程序
2. 轮询 `FND_CONCURRENT_REQUESTS` 查询请求状态 (间隔 5s，最大超时 30min)
3. 请求完成 (PHASE='C' 且 STATUS='C') 后，通过 `FND_CONC_REQ_OUTPUTS` 获取输出文件
4. 下载输出文件至本地文件存储目录：`{base_path}/{org_code}/{period_name}/`
5. 保存文件路径至 `ap_close_task.output_file_path`

#### 步骤5：导出应付票据报表 (REPORT_EXPORT)

**EBS 并发程序：CUX:应付票据报表**

请求参数：

```
业务实体 = OU_ID
票据状态 = 'ISSUED'
是否显示现金流 = 'N'
```

处理流程同步骤4。

#### 步骤6：更新到期应付票据状态 (EBS_REQUEST)

**EBS 并发程序：更新到期应付票据状态**

请求参数：

```
到期日 = 用户在前端指定的日期 (DatePicker)
```

- 前端提供日期选择器，用户选定日期后提交
- 后端提交 EBS 并发请求，等待完成

#### 步骤7：检查未过账事务处理 (REPORT_EXPORT)

**EBS 并发程序：未入帐事务处理报表 (XML)**

请求参数：

```
申报级别 = '分类帐'
申请环境 = 当前OU对应的子分类账名称 (从 EBS 配置文件获取)
期间名称 = 选择的期间
```

- 请求完成后获取 RTF 格式输出
- 解析 RTF（或直接查询 EBS 未过账事务表）判断是否有未过账数据
- 如果有未过账事务，前端标记为"需处理"状态，用户确认后进入步骤8

**辅助验证 SQL (JDBC 直查)：**

```sql
SELECT COUNT(*) unposted_count
FROM xla_events xe
JOIN xla_transaction_entities xte ON xe.entity_id = xte.entity_id
WHERE xe.event_status_code = 'U'
  AND xte.application_id = 200
  AND xe.application_id = 200;
```

#### 步骤8：创建会计科目 (EBS_REQUEST)

**EBS 并发程序：创建会计科目**

请求参数：

```
分类帐                      = 当前OU对应的账套名称
流程类别                    = NULL (空)
终止日期                    = 当前期间的最后一天
模式                        = 'FINAL' (最终)
仅显示错误                  = 'N' (否)
报表                        = 'DETAIL' (明细)
传送至 General Ledger       = 'Y' (是)
在 GL 中过帐                = 'Y' (是)
总帐批名                    = NULL (空)
包括用户事务处理标识         = 'N' (否)
```

#### 步骤9：传送日记账分录至GL (EBS_REQUEST)

**EBS 并发程序：将日记帐分录传送至GL**

请求参数：无额外参数（使用步骤8创建的会计分录）

#### 步骤10：核对子模块与总账余额 (REPORT_EXPORT)

**EBS 并发程序：CUX:供应商帐户余额汇总表**

- 提交请求，导出报表文件
- 前端展示报表内容，由会计人员核对应付子模块余额与总账余额是否一致
- 用户人工判断并确认"一致"或"不一致需处理"

#### 步骤11：核查应付暂估数据 (REPORT_EXPORT)

此步骤需提交 **三个报表**，分别由不同职责提交：

**报表1：科目余额表 (GL 职责)**

```
来源模块：总账 (GL)
EBS 程序：CUX:科目余额表
参数：科目编码 = 应付暂估科目 (从 EBS 配置文件获取)
```

**报表2：OPM子分类帐明细表 (CST 职责)**

```
来源模块：成本 (CST)
EBS 程序：CUX:OPM子分类帐明细表
参数：期间 = 当前期间
```

**报表3：应付暂估汇总表 (AP 职责)**

```
EBS 程序：CUX:应付暂估汇总表
参数：
  起始日期            = '2019-01'
  结束日期            = 当前期间最后一天
  供应商类型          = NULL
  供应商              = NULL
  物料类别            = NULL
  物料编码            = NULL
  输出方式            = 'D'
  发票匹配截止日期     = 用户指定(当前日期)
  是否显示匹配完的数据  = 'Y'
```

前端展示三个报表的结果摘要，会计人员验证公式：
> 科目余额表(应付暂估科目期末余额) + OPM子分类帐报表(本期借方-贷方) ≈ 应付暂估余额表(剩余暂估余额)

#### 步骤12：核对供应商账龄 (REPORT_EXPORT)

**EBS 并发程序：CUX:供应商帐龄报表**

请求参数：

```
业务实体          = 当前OU_ID
截止日期          = 当前期间最后一天
供应商编号下限     = NULL
供应商编号上限     = NULL
应付帐龄期间       = '丽珠应付账龄时段' (固定值)
经办人            = NULL
明细/汇总         = 'DETAIL'
帐龄类型          = 'ALL'
```

#### 步骤13：再次创建会计科目+传送GL (EBS_REQUEST)

执行内容同步骤8和步骤9的合并。参数与步骤8一致（模式='FINAL'）。

#### 步骤14：关闭应付会计期间 (EBS_REQUEST)

**实现方式：**

1. 调用 EBS API 关闭当前 AP 期间：
```sql
-- 检查期间关闭例外
-- 提交"子分类帐期间关闭例外报表"检查是否具备关闭条件

-- 调用 Oracle EBS API 关闭期间
-- BEGIN
--   AP_PERIOD_CLOSE_PKG.CLOSE_PERIOD(:periodName, :ledgerId);
-- END;
```

2. 若关闭失败：
   - 自动提交"期间关闭例外报表 (XML)"和"子分类帐期间关闭例外报表"
   - 解析输出列出具体原因（未验证发票、未创建会计科目等）
   - 前端展示失败原因明细，引导用户逐项处理

3. 打开下一期间：
```sql
-- 调用 EBS API 打开下一期间
-- BEGIN
--   AP_PERIOD_CLOSE_PKG.OPEN_PERIOD(:nextPeriodName, :ledgerId);
-- END;
```

---

### 2.4 Oracle EBS 集成层设计

#### 2.4.1 数据源配置

```yaml
# application.yml
ebs:
  datasource:
    jdbc-url: jdbc:oracle:thin:@//${EBS_DB_HOST}:${EBS_DB_PORT}/${EBS_DB_SERVICE}
    username: ${EBS_APPS_USER}
    password: ${EBS_APPS_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
  concurrent:
    # 并发请求提交后的轮询配置
    poll-interval-ms: 5000       # 轮询间隔 5 秒
    poll-max-attempts: 360       # 最大轮询次数 (30分钟)
    poll-timeout-minutes: 30
```

#### 2.4.2 EBS 集成接口

```java
// 核心接口定义
public interface EBSIntegrationService {

    // 提交并发请求
    Long submitConcurrentRequest(String programShortName,
                                  String respApplication,
                                  List<NameValuePair> params);

    // 查询请求状态
    ConcurrentRequestStatus getRequestStatus(Long requestId);

    // 获取请求输出文件
    byte[] getRequestOutput(Long requestId, String outputType);

    // 执行SQL查询（用于步骤2、3的发票检查）
    List<Map<String, Object>> executeQuery(String sql, Map<String, Object> params);
}
```

#### 2.4.3 并发请求提交实现

通过调用 Oracle EBS 的 `FND_REQUEST.SUBMIT_REQUEST` PL/SQL 函数：

```java
// 通过 JDBC CallableStatement 调用
String plsql = """
    BEGIN
        :requestId := FND_REQUEST.SUBMIT_REQUEST(
            application => :appName,
            program     => :programName,
            description => :description,
            start_time  => SYSDATE,
            sub_request => FALSE,
            argument1   => :arg1,
            argument2   => :arg2,
            ...
        );
        COMMIT;
    END;
""";
```

#### 2.4.4 请求状态轮询

```java
// 轮询查询
String pollSql = """
    SELECT request_id, phase_code, status_code, completion_text
    FROM apps.fnd_concurrent_requests
    WHERE request_id = :requestId
""";

// 状态判断
// phase_code='C' AND status_code='C' → 正常完成
// phase_code='C' AND status_code='E' → 错误
// phase_code='C' AND status_code='W' → 警告
// 其他 → 仍在运行
```

---

### 2.5 文件存储模块 (对应需求：一.3)

#### 2.5.1 存储方案

| 方案 | 说明 |
|------|------|
| 文件系统 | 报表文件存储在服务器文件系统，按 `{base_path}/{org_code}/{period_name}/` 组织 |
| 数据库 | PostgreSQL 存储文件元数据（文件名、路径、大小、类型、上传时间） |

```
文件存储目录结构：
/data/apclose/files/
├── ORG001/                    # 库存组织代码
│   ├── 2026-07/               # 期间
│   │   ├── 应付发票信息报表_20260701_20260731.xlsx
│   │   ├── 应付票据报表_ISSUED.xlsx
│   │   ├── 未入帐事务处理报表.xml
│   │   └── ...
│   └── 2026-08/
└── ORG002/
```

#### 2.5.2 文件元数据表

```sql
CREATE TABLE sys_file_record (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(512) NOT NULL,
    file_path       VARCHAR(1024) NOT NULL,
    file_size       BIGINT,                      -- 字节
    file_type       VARCHAR(64),                 -- XLSX / RTF / PDF / XML
    org_code        VARCHAR(64),
    period_name     VARCHAR(64),
    step_no         INTEGER,                     -- 关联月结步骤
    task_id         BIGINT,                      -- 关联月结任务
    ebs_request_id  BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_org_period ON sys_file_record(org_code, period_name);
CREATE INDEX idx_file_type ON sys_file_record(file_type);
```

#### 2.5.3 文件检索与导出

- 前端提供文件列表页，支持按组织、期间、文件类型、步骤筛选
- 支持单个文件下载
- 支持按筛选条件批量导出（打包为 ZIP）

---

## 3. 前端页面设计

### 3.1 页面路由

```
/login/callback          飞书OAuth回调页
/select-responsibility   职责与库存组织选择页
/dashboard               月结工作台主页
/dashboard/step/:no      步骤详情页
/files                   文件管理页
```

### 3.2 月结工作台页面 (核心页面)

**布局：** 左侧步骤列表 + 右侧步骤操作区

```
┌──────────────────────────────┬──────────────────────────────────┐
│  应付月末结账工作台            │  步骤4：导出应付发票信息报表        │
│  期间：2026-07  组织：ORG001  │                                    │
│  ┌─────────────────────────┐ │  ┌─────────────────────────────┐  │
│  │ ✅ 1. 日常业务处理      │ │  │ 请求参数                      │  │
│  │ ✅ 2. 检查未验证发票    │ │  │ 起始GL日期: 2026-07-01      │  │
│  │ ✅ 3. 检查暂挂发票      │ │  │ 终止GL日期: 2026-07-31      │  │
│  │ ⏳ 4. 导出应付发票信息   │◀│  │ 包含已取消发票: 否           │  │
│  │ ⬜ 5. 导出应付票据数据   │ │  │                              │  │
│  │ ⬜ 6. 到期支付处理      │ │  │ [ 提交请求 ]                 │  │
│  │ ⬜ 7. 检查未过账事务    │ │  └─────────────────────────────┘  │
│  │ ...                     │ │  ┌─────────────────────────────┐  │
│  └─────────────────────────┘ │  │ 输出文件                      │  │
│                              │  │ 📄 应付发票信息报表.xlsx     │  │
│                              │  │ [ 下载 ]                     │  │
│                              │  └─────────────────────────────┘  │
└──────────────────────────────┴──────────────────────────────────┘
```

### 3.3 步骤操作区通用组件

根据不同步骤类型渲染对应组件：

| 步骤类型 | 组件 | 说明 |
|---------|------|------|
| MANUAL_CONFIRM | `ManualConfirmPanel` | 提示文本 + 确认按钮 |
| AUTO_CHECK | `CheckResultPanel` | 查询结果列表/统计 + 重新检查按钮 |
| REPORT_EXPORT | `ReportExportPanel` | 参数展示 + 提交按钮 + 输出下载 |
| EBS_REQUEST | `EBSRequestPanel` | 参数表单 + 提交按钮 + 状态轮询 |

### 3.4 图标使用规范

统一使用 Element Plus Icons (开源 MIT 协议)：

| 场景 | 图标 | 组件名 |
|------|------|--------|
| 步骤-等待中 | 空心圆 | `<CircleCheck>` variant |
| 步骤-进行中 | 加载旋转 | `<Loading>` |
| 步骤-已完成 | 绿色对勾 | `<CircleCheckFilled>` |
| 步骤-异常 | 红色感叹号 | `<WarningFilled>` |
| 文件下载 | 下载图标 | `<Download>` |
| 文件列表 | 文件夹图标 | `<Folder>` |
| 重新检查 | 刷新图标 | `<Refresh>` |
| 提交请求 | 发送图标 | `<Promotion>` |

---

## 4. API 接口设计

### 4.1 接口规范

- RESTful 风格
- 统一响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- 认证方式：Header `Authorization: Bearer {JWT_TOKEN}`

### 4.2 接口清单

#### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/login` | 跳转飞书OAuth授权页 |
| GET | `/api/auth/callback` | 飞书OAuth回调处理 |
| GET | `/api/auth/user-info` | 获取当前登录用户信息 |
| POST | `/api/auth/logout` | 登出 |

#### 职责/组织选择

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/responsibilities` | 获取用户职责列表 |
| GET | `/api/auth/organizations` | 获取用户库存组织列表 |
| POST | `/api/auth/select-session` | 保存用户选择的职责和组织 |

#### 月结工作流

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ap-close/periods` | 获取可用期间列表 |
| POST | `/api/ap-close/init` | 初始化月结任务(创建14步) |
| GET | `/api/ap-close/tasks` | 获取当前期间所有步骤状态 |
| GET | `/api/ap-close/tasks/{stepNo}` | 获取某步骤详情 |
| POST | `/api/ap-close/tasks/{stepNo}/execute` | 执行某步骤 |
| POST | `/api/ap-close/tasks/{stepNo}/confirm` | 人工确认步骤完成 |
| GET | `/api/ap-close/tasks/{stepNo}/status` | 轮询步骤执行状态 |

#### 文件管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/files` | 文件列表(支持筛选) |
| GET | `/api/files/{id}/download` | 下载单个文件 |
| POST | `/api/files/batch-download` | 批量下载(ZIP) |

#### EBS 请求工具

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ebs/request/{requestId}/status` | 查询EBS请求状态 |
| GET | `/api/ebs/request/{requestId}/output` | 获取EBS请求输出 |

---

## 5. 数据库设计总览

### 5.1 ER 图 (核心表关系)

```
sys_user_ebs_mapping (飞书用户↔EBS账号)
        │
        │ 1:N
        ▼
sys_user_session (登录会话, 含职责/组织/OU/账套配置)
        │
        │ 1:N
        ▼
ap_close_task (月结任务实例, 14条/会话/期间)
        │
        │ 1:N
        ▼
sys_file_record (输出文件记录)
```

### 5.2 核心表清单

| 表名 | 说明 | 对应需求 |
|------|------|---------|
| `sys_user_ebs_mapping` | 飞书用户与EBS账号映射 | 一.1 |
| `sys_user_session` | 用户登录会话 | 一.1 |
| `ap_close_step_def` | 月结步骤定义 | 应付模块 |
| `ap_close_task` | 月结任务实例 | 应付模块 |
| `sys_file_record` | 文件元数据 | 一.3 |

---

## 6. 安全设计

### 6.1 认证安全

- 飞书 OAuth 2.0 授权码模式 (Authorization Code + PKCE)
- JWT Token 有效期 2 小时，支持 Refresh Token 续期
- 所有 API 请求(除登录回调外)必须携带有效 JWT

### 6.2 数据安全

- Oracle EBS 数据库凭据通过环境变量注入，不硬编码
- 敏感配置使用 Jasypt 加密存储
- 报表文件访问需校验用户所属组织权限
- 操作日志记录（AOP 切面）：记录用户、时间、步骤、参数、结果

### 6.3 前端安全

- 路由守卫：未登录跳转登录页
- Token 存储于 httpOnly cookie（优先）或 localStorage
- 防止 XSS：Vue 默认输出转义 + CSP 头

---

## 7. 部署架构

### 7.1 服务组件

```
┌─────────────────────────────────────────────────────┐
│                   Nginx (反向代理)                    │
│          静态资源 / → Vue前端    /api/* → 后端       │
└─────────────────────────────────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────┐          ┌──────────────────────┐
│  Vue 3 静态文件   │          │  Spring Boot 3 JAR   │
│  (dist/ 目录)    │          │  端口: 8080          │
└──────────────────┘          └──────────────────────┘
                                        │
                          ┌─────────────┼─────────────┐
                          ▼             ▼             ▼
                   ┌──────────┐ ┌──────────┐ ┌──────────┐
                   │PostgreSQL│ │Oracle EBS│ │ 文件存储  │
                   │  :5432   │ │  :1521   │ │ /data/   │
                   └──────────┘ └──────────┘ └──────────┘
```

### 7.2 环境变量

```bash
# 飞书应用配置
FEISHU_APP_ID=xxx
FEISHU_APP_SECRET=xxx
FEISHU_REDIRECT_URI=https://apclose.example.com/api/auth/callback

# 业务数据库
DB_HOST=localhost
DB_PORT=5432
DB_NAME=apclose
DB_USER=apclose_user
DB_PASSWORD=xxx

# Oracle EBS 数据库(只读查询)
EBS_DB_HOST=ebs-db.example.com
EBS_DB_PORT=1521
EBS_DB_SERVICE=EBSDEV
EBS_APPS_USER=apps_user
EBS_APPS_PASSWORD=xxx

# 文件存储
FILE_STORAGE_PATH=/data/apclose/files

# JWT
JWT_SECRET=xxx
JWT_EXPIRATION_MS=7200000
```

---

## 8. 项目目录结构

```
autoapcls/
├── frontend/                          # Vue 3 前端
│   ├── public/
│   ├── src/
│   │   ├── api/                       # API 接口封装
│   │   │   ├── auth.js
│   │   │   ├── apClose.js
│   │   │   └── file.js
│   │   ├── components/                # 通用组件
│   │   │   ├── StepCheckPanel.vue     # AUTO_CHECK 步骤面板
│   │   │   ├── StepManualPanel.vue    # MANUAL_CONFIRM 步骤面板
│   │   │   ├── StepReportPanel.vue    # REPORT_EXPORT 步骤面板
│   │   │   └── StepRequestPanel.vue   # EBS_REQUEST 步骤面板
│   │   ├── views/
│   │   │   ├── LoginCallback.vue      # 飞书登录回调
│   │   │   ├── SelectSession.vue      # 职责/组织选择
│   │   │   ├── Dashboard.vue          # 月结工作台
│   │   │   └── Files.vue              # 文件管理
│   │   ├── router/
│   │   │   └── index.js
│   │   ├── store/
│   │   │   ├── user.js                # 用户状态(Pinia)
│   │   │   └── apClose.js             # 月结状态
│   │   └── utils/
│   │       ├── request.js             # Axios 封装
│   │       └── auth.js                # Token 管理
│   ├── package.json
│   └── vite.config.js
├── backend/                           # Spring Boot 后端
│   ├── src/main/java/com/autoapcls/
│   │   ├── AutoApclsApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── EBSDataSourceConfig.java
│   │   │   └── FileStorageConfig.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── APCloseController.java
│   │   │   └── FileController.java
│   │   ├── service/
│   │   │   ├── AuthService.java
│   │   │   ├── APCloseService.java
│   │   │   ├── EBSIntegrationService.java
│   │   │   └── FileStorageService.java
│   │   ├── repository/
│   │   │   ├── UserSessionRepository.java
│   │   │   ├── APCloseTaskRepository.java
│   │   │   └── FileRecordRepository.java
│   │   ├── model/
│   │   │   ├── entity/                # 数据库实体
│   │   │   └── dto/                   # 数据传输对象
│   │   └── security/
│   │       ├── JwtTokenProvider.java
│   │       └── FeishuOAuthService.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/              # Flyway 数据库迁移脚本
│   └── pom.xml
└── docs/
    ├── requirements.md                # 需求文档 (从docx提取)
    └── technical-design.md            # 技术设计文档 (本文档)
```

---

## 9. 关键技术要点

### 9.1 EBS 并发请求参数传递

Oracle EBS 并发程序参数通过位置参数传入 `FND_REQUEST.SUBMIT_REQUEST`，每个程序的参数定义查询：

```sql
SELECT argument_number, argument_name, default_value
FROM apps.fnd_descr_flex_col_usage_vl
WHERE application_id = :appId
  AND descr_flex_name = :programName
ORDER BY argument_number;
```

### 9.2 报告输出获取

并发请求完成后，通过以下方式获取输出文件：

```sql
SELECT fcr.request_id, fco.file_name, fco.file_data
FROM apps.fnd_concurrent_requests fcr
JOIN apps.fnd_concurrent_output fco ON fcr.request_id = fco.request_id
WHERE fcr.request_id = :requestId;
```

### 9.3 跨职责报表提交

步骤11需要三种不同职责（AP、CST、GL）提交报表。系统使用每个职责对应的 EBS 用户凭据提交对应报表：

| 报表 | 提交职责 | EBS 用户 |
|------|---------|---------|
| CUX:应付暂估汇总表 | AP | 应付会计关联的EBS账号 |
| CUX:OPM子分类帐明细表 | CST | 成本会计EBS账号(配置) |
| CUX:科目余额表 | GL | 总账会计EBS账号(配置) |

这些备用账号配置在系统配置表中：

```sql
CREATE TABLE sys_ebs_account_config (
    id              SERIAL PRIMARY KEY,
    resp_type       VARCHAR(32) NOT NULL UNIQUE,  -- AP / CST / GL
    ebs_user_name   VARCHAR(128) NOT NULL,
    ebs_password    VARCHAR(256) NOT NULL,          -- 加密存储
    description     VARCHAR(256)
);
```

### 9.4 AP 期间打开/关闭

Oracle EBS AP 期间控制表：`AP_PERIODS_ALL`

```sql
-- 查询当前打开的AP期间
SELECT period_name, period_year, period_num,
       start_date, end_date, status
FROM apps.ap_periods_all
WHERE status = 'O'
  AND ledger_id = :ledgerId;

-- 期间关闭通过调用标准API
-- AP_PERIOD_CLOSE_PKG.CLOSE_PERIOD
```

---

## 10. 需求追溯矩阵

| 需求编号 | 需求描述 | 技术实现 | 状态 |
|---------|---------|---------|------|
| 一.1 | 飞书登录+EBS账号关联 | 2.1 认证与权限模块 | ✅ |
| 一.1 | 职责/组织选择 | 2.1.3 职责与库存组织选择 | ✅ |
| 一.1 | OU/账套参数加载 | 2.1.4 配置参数加载 | ✅ |
| 一.2 | 现代化UI+分区布局 | 3.2 月结工作台页面 | ✅ |
| 一.3 | 文件存储/检索/导出 | 2.5 文件存储模块 | ✅ |
| 应付.1 | 日常业务处理 | 步骤1 MANUAL_CONFIRM | ✅ |
| 应付.2 | 检查未验证发票 | 步骤2 JDBC直查EBS | ✅ |
| 应付.3 | 检查暂挂发票 | 步骤3 JDBC直查EBS | ✅ |
| 应付.4 | 导出应付发票数据 | 步骤4 并发请求+轮询 | ✅ |
| 应付.5 | 导出应付票据数据 | 步骤5 并发请求+轮询 | ✅ |
| 应付.6 | 远期付款到期支付 | 步骤6 并发请求 | ✅ |
| 应付.7 | 检查未过账事务处理 | 步骤7 并发请求+XML解析 | ✅ |
| 应付.8 | 创建会计科目 | 步骤8 并发请求 | ✅ |
| 应付.9 | 传送日记账至GL | 步骤9 并发请求 | ✅ |
| 应付.10 | 核对子模块余额 | 步骤10 并发请求+人工确认 | ✅ |
| 应付.11 | 核查应付暂估数据 | 步骤11 三报表联动 | ✅ |
| 应付.12 | 核对供应商账龄 | 步骤12 并发请求 | ✅ |
| 应付.13 | 再次创建会计科目+传送GL | 步骤13 并发请求 | ✅ |
| 应付.14 | 关闭应付会计期间 | 步骤14 期间关闭+例外处理 | ✅ |
