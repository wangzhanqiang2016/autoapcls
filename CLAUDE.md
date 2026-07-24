# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

应付自动结账系统 — 为丽珠医药集团开发，实现 Oracle EBS 应付模块月末结账全流程自动化。用户通过飞书 OAuth 登录，关联 Oracle EBS 账号与职责后，按 14 步月结清单顺序引导完成月末结账。

## 项目状态

**前后端框架完整，API 可用。飞书 OAuth 和 EBS JDBC 集成的真实代码已就绪，但月结步骤执行流程当前即时标记 COMPLETED 而非真实异步轮询。** 开发默认使用 H2 内存数据库，无需外部依赖即可启动前端页面（但 EBS 数据源不可用时后端会启动失败，见下方说明）。

- **飞书 OAuth**: `application.yml` 中 `feishu.mock-enabled: false`（真实模式），`FeishuOAuthService` 实现了完整 OIDC 流程（app_access_token → user_info）。设置为 `true` 则绕过飞书 API，直接使用 code 参数作为 open_id 查找本地用户映射。
- **EBS 集成**: `EBSIntegrationService` 通过 `NamedParameterJdbcTemplate` 直连 Oracle EBS，实现了 `FND_REQUEST.SUBMIT_REQUEST`（`CallableStatement`）、`fnd_concurrent_requests` 状态查询、`fnd_concurrent_output` 文件获取。若 EBS Oracle 数据库不可达，应用启动会因 `ebsNamedJdbcTemplate` Bean 创建失败而崩溃，除非临时移除 `ebs.datasource` 配置或将 EBS 相关 Bean 标记为 `@ConditionalOnProperty`。
- **月结执行**: `APCloseService.executeStep()` 调用真实的 EBS 服务方法，但 `REPORT_EXPORT` 和 `EBS_REQUEST` 类型提交请求后立即设为 COMPLETED，没有实现异步轮询。AUTO_CHECK 类型执行查询后将状态回退为 PENDING（需人工确认）。
- **测试**: 当前无任何单元测试或集成测试。`spring-boot-starter-test` 已在 pom.xml 依赖中，但 `src/test` 目录不存在。
- **Spring Profile**: `application.yml` 中 `spring.profiles.active: dev`，可添加 `application-dev.yml` / `application-prod.yml` 覆盖默认配置。
- **数据源配置特殊点**: 主数据源使用 `spring.datasource.jdbc-url`（非标准 `url`），因为双数据源下 Spring Boot 自动配置 `url` 会冲突。EBS 数据源同理使用 `ebs.datasource.jdbc-url`。

## 常用命令

```bash
# 前端依赖安装（首次运行或 package.json 变更后）
cd frontend && npm install

# 后端（Spring Boot 3 + Java 17，端口 8080）
# 注意：无 spring-boot-devtools，Java 代码变更后需手动重启
cd backend && mvn spring-boot:run

# 前端（Vue 3 + Vite，端口 3001，/api 代理到 localhost:8080，支持 HMR）
cd frontend && npm run dev

# 前端构建
cd frontend && npm run build

# 运行测试（当前 src/test 目录不存在，测试待编写）
cd backend && mvn test

# H2 控制台（开发调试用）
# 启动后端后访问 http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:autoapcls
# 用户名: sa，密码留空
```

启动后访问 `http://localhost:3001`，使用种子数据中的测试用户 open ID 登录（如 `ou_test_admin_001`、`ou_test_admin_002`、`ou_test_admin_003`）。

## 两种开发模式

| 场景 | `feishu.mock-enabled` | `ebs.datasource` 配置 | 说明 |
|------|----------------------|----------------------|------|
| **纯离线开发** | `true` | 注释掉整个 `ebs.datasource` 块 | 绕过飞书 API + EBS，用 code 参数直接匹配 open_id |
| **EBS 联调** | `false`（默认） | 保持配置 | 真实飞书 OIDC + Oracle EBS JDBC 查询 |

纯离线模式下，`ebs.datasource` 必须移除或注释，否则应用启动时会因 Oracle 不可达而崩溃（`ebsNamedJdbcTemplate` Bean 创建失败）。EBS 相关 Bean 当前未加 `@ConditionalOnProperty`。

## 技术选型

| 层次 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue 3 + Element Plus + Pinia + Vue Router + Axios | Vue 3.5+, Element Plus 2.14+ |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Security | Spring Boot 3.2.7 |
| 开发数据库 | H2 (内存模式, PostgreSQL 兼容) | — |
| 生产数据库 | PostgreSQL 15+ | — |
| 数据库迁移 | Flyway | — |
| 认证 | JWT (jjwt 0.12.6) | — |
| EBS 集成 | JDBC 直连 + FND_REQUEST.SUBMIT_REQUEST | Oracle 19c |

## 架构关键决策

### 1. 双数据源

项目使用两个独立的 JDBC 数据源（`DataSourceConfig`）：

- **主数据源 (H2/PostgreSQL, `@Primary`)**: 业务数据，由 Flyway 管理迁移。Bean 名 `dataSource`，MyBatis-Plus 自动使用。
- **EBS 数据源 (Oracle, `ebsDataSource`)**: 只读 JDBC 查询和 `CallableStatement` 执行。Flyway 不管此数据源。通过 `@Qualifier("ebsNamedJdbcTemplate")` 注入 `EBSIntegrationService`。

**重要**: EBS 数据源不可达时应用启动会崩溃，因为 `ebsDataSource` 和 `ebsNamedJdbcTemplate` Bean 无条件创建。如需纯离线开发，要么设置 `feishu.mock-enabled=true` 并注释掉 `ebs.datasource` 配置块，要么在 `DataSourceConfig` 的 EBS Bean 上加 `@ConditionalOnProperty`。

### 2. 月结流程是严格顺序的 14 步状态机

每步状态：`PENDING → RUNNING → COMPLETED/FAILED`。步骤定义在 `ap_close_step_def` 表（V2 种子数据初始化），实例在 `ap_close_task` 表（用户初始化月结时创建 14 条记录）。

**4 种步骤类型**（`stepType` 字段）驱动前端面板渲染：
| stepType | 前端面板 | 说明 |
|----------|---------|------|
| `MANUAL_CONFIRM` | `StepManualPanel` | 提示文字 + 确认按钮（步骤1） |
| `AUTO_CHECK` | `StepCheckPanel` | JDBC 直查 EBS，展示结果列表（步骤2、3） |
| `REPORT_EXPORT` | `StepReportPanel` | 提交并发程序 → 获取输出文件（步骤4、5、7、10、11、12） |
| `EBS_REQUEST` | `StepRequestPanel` | 提交并发程序 + 参数表单（步骤6、8、9、13、14） |

**`ebsRespType` 字段**（`ApCloseStepDef` 实体）：标识步骤需要哪种 EBS 职责（如 `AP`/`CST`/`GL`），主要用于步骤11（跨职责报表核查暂估数据），该步骤需用三种职责分别提交报表。其他步骤该字段通常为空。

`APCloseService.executeStep()` 中的 switch 分支根据 `stepType` 执行不同逻辑，当前除 AUTO_CHECK 外其他类型都即时标记 COMPLETED（无真实异步轮询）。

### 3. 认证与鉴权流程

```
用户访问 → /login/callback → GET /api/auth/login?code=任意值
  → FeishuOAuthService.authenticate()
    → [mock] 返回第一个匹配的测试用户
    → [real] 飞书 OIDC 流程 → app_access_token → user_info (open_id/name)
  → 查 sys_user_ebs_mapping 表匹配 EBS 账号
  → JwtTokenProvider 生成 JWT（含 userId, feishuOpenId, 2h 过期）
  → 前端存入 localStorage
```

JWT 过滤器 (`SecurityConfig.jwtAuthenticationFilter()`) 从 `Authorization: Bearer` 头提取 token，验证后将 `userId` 和 `feishuOpenId` 注入 `HttpServletRequest` attribute，并设置 Spring Security Context（`ROLE_USER`）。

**豁免路径**: `/api/auth/**` 和 `/h2-console/**` 无需认证。

### 4. 会话选择与 EBS 配置加载

用户登录后必须选择职责和组织（`POST /api/auth/select-session`），`AuthService.selectSession()` 会：
1. 从 EBS 查询 `ORG_ID` 配置文件获取默认 OU（`hr_operating_units`）
2. 从 EBS 查询 `GL_SET_OF_BKS_ID` 配置文件获取默认账套（`gl_ledgers`）
3. EBS 查询失败时使用硬编码兜底值（OU=201, Ledger=301）

后续所有月结 API 调用通过 `APCloseController.getCurrentSession()` 从 `request.getAttribute("userId")` 查找活跃会话（`AuthService.getActiveSession()` 取最近一条）。

### 5. EBS 查询的容错机制

`AuthService` 查询职责和组织列表时，EBS 查询失败不抛异常，而是返回硬编码的 fallback 数据：
- 职责兜底：应付会计/总账会计/成本会计
- 组织兜底：丽珠制药厂/合成厂/试剂厂

Oracle JDBC 返回的 `UPPER_SNAKE_CASE` 键名通过 `normalizeKeys()` 方法转为 camelCase（如 `RESPONSIBILITY_ID → respId`）。

### 6. 统一 API 响应格式

所有接口返回 `Result<T>`：
```json
{"code": 200, "message": "success", "data": {...}}
```

**前端 Axios 拦截器自动拆包**（`frontend/src/api/request.js`）：成功时（`code === 200`），拦截器返回 `response.data.data` 而非完整 `{code, message, data}`，因此前端 API 函数（如 `getTasks()`）的返回值已经是 `data` 字段的内容（数组或对象）。401 时清除 token 跳转登录页。`GlobalExceptionHandler` 捕获未处理异常并包装为 `Result.error()`。

**`StepExecuteRequest` DTO**：`POST /api/ap-close/tasks/{stepNo}/execute` 的请求体结构为 `{"params": {...}}`，即参数包裹在 `params` 键内。前端 `executeStep(stepNo, params)` 发送 `{params: params}`。

### 7. 步骤11 跨职责报表

核查应付暂估数据需要三种职责（AP/CST/GL）分别提交报表。备用 EBS 账号凭据存储在 `sys_ebs_account_config` 表（密码加密）。当前种子数据包含三个测试账号。

### 8. 文件存储

报表输出文件按 `{base_path}/{org_code}/{period_name}/` 分层存储（本地文件系统）。默认路径 `/data/apclose/files`，由 `FileStorageConfig` 在 `@PostConstruct` 时自动创建目录。文件元数据记录在 `sys_file_record` 表。

## 核心表关系

```
sys_user_ebs_mapping (飞书用户↔EBS账号)
        │ 1:N
        ▼
sys_user_session (登录会话, 含职责/组织/OU/账套)
        │ 1:N
        ▼
ap_close_task (月结任务实例, 14条/会话/期间)
        │ 1:N
        ▼
sys_file_record (输出文件记录)
```

加上 `ap_close_step_def`（步骤定义）和 `sys_ebs_account_config`（跨职责账号配置），共 6 张核心业务表。

## Flyway 迁移历史

| 版本 | 内容 |
|------|------|
| V1 | 6 张核心业务表 DDL |
| V2 | 14 步月结定义 + 3 个测试用户 + 3 个跨职责 EBS 账号 |
| V3 | 真实 EBS 用户王占强的飞书 open_id 映射 |
| V4 | 补充真实飞书用户 open_id 映射 |

## 后端包结构

```
com.autoapcls
├── common/          Result, GlobalExceptionHandler
├── config/          DataSourceConfig, SecurityConfig, FileStorageConfig
├── controller/      APCloseController, AuthController, FileController
├── mapper/          MyBatis-Plus Mapper 接口（6个）
├── model/
│   ├── entity/      实体类（对应 6 张表）
│   └── dto/         LoginRequest, SessionSelectRequest, StepExecuteRequest
├── security/        JwtTokenProvider, FeishuOAuthService
└── service/         APCloseService, AuthService, EBSIntegrationService, FileStorageService
```

## 前端路由

| 路径 | 组件 | 说明 |
|------|------|------|
| `/login` | Login.vue | 账户密码登录页（`meta: { noAuth: true }`，无需 JWT） |
| `/select-responsibility` | SelectSession.vue | 职责与组织选择 |
| `/dashboard` | Dashboard.vue | 月结工作台（核心页面：左侧步骤列表+右侧操作区） |
| `/files` | Files.vue | 文件管理 |

Dashboard 通过 `stepType` 动态渲染 4 种步骤面板组件（`StepManualPanel`/`StepCheckPanel`/`StepReportPanel`/`StepRequestPanel`）。路由守卫 `router.beforeEach` 对非 `noAuth` 页面检查 localStorage token。

## 前端状态管理 (Pinia)

- **`useUserStore`** (`store/user.js`): token、用户信息、会话信息（sessionId/orgCode/periodName）、login/logout/setSession 操作。
- **`useApCloseStore`** (`store/apClose.js`): tasks 列表、当前步骤、加载状态，`loadTasks()` 从 API 拉取，`updateTaskStatus()` 本地更新单步状态。

## 核心文档

- **[需求说明书](docs/requirements.md)** — V1.1，所有功能需求的唯一权威来源
- **[技术设计文档](docs/technical-design.md)** — V1.0，架构、模块设计、数据库 DDL、API 清单、部署方案
