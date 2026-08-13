# 说需求文案生成 P0-A 用户端身份与安全隔离实现计划

> **面向 AI（人工智能）代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（子代理驱动开发，推荐）或 superpowers:executing-plans（分批执行计划）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 先于工作区、额度、知识和文案业务，交付一套可独立部署、登录、管理、审计并通过双向越权测试的创作端账号与安全底座；创作端身份只来自独立 `app_*` 事实源和 `StpLogic("app")`，运营端继续只使用现有 `sys_*` 事实源与默认登录类型。

**架构：** 在固定包根 `org.dromara.aivideo` 下建立 `ai-video-core`、`ai-video-infra`、`ai-video-user`、`ai-video-platform` 四个模块。核心模块持有创作端身份实体、Mapper、事务服务、独立 Sa-Token 逻辑、修订失效、会话和审计；基础设施模块只适配短信、邮件、社交和小程序提供商；用户模块只装配 `/api/auth/**` 与 `app` 安全链；平台模块只装配 `/api/admin/app-*` 管理入口并把现有 `sys_user` 明确转换成 `actorType = sys_user`。`ai-video-user-api` 与 `ruoyi-admin` 通过依赖边界实现双启动隔离，不靠运行时隐藏 Controller。

**技术栈：** Java 21（后端编程语言）、Spring Boot（Java 应用框架）、RuoYi-Vue-Plus 6.x（若依增强版）、MyBatis-Plus / `BaseMapperPlus`（数据访问增强工具）、Sa-Token 1.45（认证授权框架）、MySQL 8（关系型数据库）、Redis 7（缓存数据库）、JUnit 5（Java 测试框架）、本机受控集成测试（直接连接本机 MySQL/Redis，不使用容器或虚拟化环境）、React 19（前端视图库）、TypeScript（带类型的 JavaScript 语言）、Umi Max 4（前端应用框架）、Ant Design 6.5.x（蚂蚁设计组件库）、ProComponents 3（中后台高级组件库）、React Query 5（服务端状态查询库）、Vitest 4（前端单元测试）。

**阅读约定：** 正文中的 API（应用编程接口）、SQL（结构化查询语言）、DDL（数据定义语言）、IT（集成测试）、Header（请求头）、Token（令牌）、Mapper（数据映射器）、Web（网页端）等英文术语首次出现时附中文含义；反引号中的类名、字段名、文件名、命令和接口路径是必须原样使用的程序标识符，不翻译其拼写，但由相邻中文解释其用途。

**本机 IT 命令约定：** 本计划内每条执行 `*IT`（集成测试）的 Maven 命令都必须带 `'-Plocal-integration-test'`；为保留外部双启动器的测试源集，还要带 `'-Pexternal-http-it'`，并在其前保留 `'-Pdev'`（开发配置）。下文早期命令片段若只展示类选择器，实际执行时按本约定补齐配置；`LocalIntegrationEnvironment` 未通过本机服务与隔离目标校验时不得启动任何 `*IT`。

> **2026-08-01 RuoYi 分层硬约束：** 历史提交中出现过的 `identity.application`、`identity.port`、`identity.model`、`application.command` 等路径不再可执行或可扩展。身份业务聚合必须改用 `domain`、与其平级的 `dto`、`mapper`、`service/I...Service`、`service.impl/...ServiceImpl`；端侧 HTTP 模块另使用 `domain.bo`、`domain.vo`、`controller`。`security` 仅保留 Sa-Token（认证授权框架）直接技术职责；AI 视频业务专属的稳定跨模块 DTO 归 `ai-video-core` 对应身份聚合的 `dto` 包，不得迁入全局 `ruoyi-api`。禁止以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 的贫血 Entity（实体）加 Service（业务服务）编排。目录整改已经完成，正文与命令均以整改后的真实路径为准，旧路径只用于识别禁止模式和追溯历史提交。
>
> 目录整改与 DTO 归属以 `docs/superpowers/specs/2026-08-01-p0a-ruoyi-layering-remediation-design.md` 为准。`AppPrincipalSnapshotDTO`、`AppWorkspaceSessionSnapshotDTO` 以及在线索引内部类型 `AppSessionServiceImpl$AppOnlineSession` 的包名已变化；项目 Redis 序列化配置会为非 final 类型写入完整类名，因此迁移前载荷存在旧 FQCN（全限定类名）。负责人已批准发布时受控一次性使旧 App 会话失效，不实现旧 FQCN 兼容读取；只能按设计规格中的维护窗口处理 `Authorization:app:*` 与 `aivideo:app:online:*`，不得影响 sys 会话或执行全库清理。
>
> **2026-08-01 IT 实现核查（最终）：** 根 POM 的 `local-integration-test` Profile、`LocalIntegrationEnvironment`、core 与双启动器夹具迁移均已落地，且 Testcontainers 已从 P0-A 测试/POM 中移除。夹具默认读取用户端 `application-dev.yml` 的标准数据源和 Redis 配置，规定的 7 个 `AI_VIDEO_IT_*` 环境变量仅用于可选覆盖，并在任何连接前校验本机地址、`ai_video_test`、非零 Redis DB、当前运行前缀和 Redisson 伴生键归属；配置缺失或不安全时 Failsafe 明确失败，不会回退到容器或其他连接。本机专用 MySQL/Redis 环境已配置，最终 `clean verify` 精确执行既定 16 个 Failsafe 类、70 tests，0 failures、0 errors、0 skipped；`DualTokenIsolationIT` 的 4 个真实双 starter HTTP 场景全部通过，结束后外部 JVM 与 Redis DB 15 均零残留。

---

## 模块契约输入

> **2026-07-30 范围调整：** 用户注册功能暂不纳入本轮 P0-A 实施和验收。P0-A 继续验证既有或测试夹具预置的创作端账号登录、会话、权限、运营管理和安全隔离；注册相关页面、接口、注册验证码场景与人工注册验收移交后续身份阶段。本调整只延期注册，不删除已冻结的其他认证与安全会话契约。

- 规格唯一输入：`docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md` 的第 3.1、10.2、10.5、10.6、11.0、11.5、12、17.2、19、20 节。
- 本阶段固定服务名：
  - `identity.service.IAppIdentityService`
  - `identity.service.IAppSessionService`
  - `identity.service.IAppPermissionService`
- 本阶段固定安全类型：
  - `identity.security.AppLoginHelper`
  - `identity.security.AppActorContext`
- 本阶段与 `P0-B` 共用且不可改名的中立模型：
  - `identity.dto.AppPrincipalSnapshotDTO`
  - `identity.dto.AppWorkspaceSessionSnapshotDTO`
  - `identity.domain.AppSessionInvalidationReason`
  - `identity.dto.AppSecurityAuditDTO`
- `IAppSessionService` 除 P0-A 的分页、本人会话和精确撤销外，固定暴露 `replaceWorkspace`、`invalidateUserSessions`、`invalidateOrganizationSessions` 三个跨阶段方法；`IAppSecurityAuditService` 固定只暴露 `append(AppSecurityAuditDTO)` 写入口。
- 本阶段固定迁移文件：`docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql`。
- `P0-A` 创建并管理 `app_user`、`app_auth_client`、`app_social_identity`、`app_permission`、`app_role`、`app_role_permission`、`app_user_role`、`app_login_log`、`app_security_audit`；不创建 `app_organization`、`app_org_member` 或业务资源授权表。
- `P0-A` 可重复登记 15 个稳定创作权限和 4 个内置角色定义，但不预置业务角色到权限的映射；第 10.6 节规定的四套初始 `app_role_permission` 映射由 `P0-B` 写入。测试按用例显式插入映射，缺失映射必须拒绝而非兜底。
- 后续注册事务用 `app_user.personal_tenant_id` 表达个人租户，并以 `HMAC(personalTenantId)` 生成不透明个人 `workspaceKey`；P0-A 对既有或测试夹具预置账号只构造个人 `AppWorkspaceSessionSnapshotDTO`，其中 `workspaceRevision` 等于当次 `identityRevision`、`membershipRevision` 为 `null`。`P0-B` 复用同一中立类型构造组织快照，不在 P0-A 提前创建组织表。
- `app_auth_client.client_key` 是请求头 `clientid` 的稳定公开键；浏览器/桌面公共客户端不把密钥当作用户凭据。`client_secret_hash` 只供受信客户端授权与运营轮换，任何创建/轮换响应仅显示一次。无论客户端是否使用密钥，停用、策略变化或换密都递增 `client_revision` 并撤销该客户端的全部 `app` 会话。
- 受保护创作请求只接受一个 `Authorization: Bearer <token>` 和一个 `clientid` 请求头。Sa-Token 的 Body、Query、Cookie 取令牌均关闭；重复头、逗号拼接值、Cookie/Query 与 Header 混合值，以及同时携带 `app`/`sys` 令牌，都在身份解析前返回 `46132`。
- 创作端实体不继承 `BaseEntity`，不使用默认 `LoginHelper` 自动填充。可变记录显式保存 `created_by_type/created_by_id/updated_by_type/updated_by_id`；安全审计另存不可变 `actor_type + actor_id`。
- 创作端 Controller 不使用默认 `@Log`，避免其读取运营身份或记录凭据；只通过 `IAppSecurityAuditService.append(AppSecurityAuditDTO)` 写脱敏审计。平台 Controller 可以使用默认 `@Log`，但密码重置、客户端创建/换密接口必须关闭请求和响应正文记录，同时写 `app_security_audit`。
- 旧 `ai_user + userType + 默认 StpUtil` 方案是禁止实现，不是兼容路径。不得建立双写、回填、同号映射或迁移开关。

## 数据库精确结构

迁移必须使用 `CREATE TABLE IF NOT EXISTS` 和带唯一键冲突更新子句的插入语句，重复执行两次后行数、约束和种子值不变。

| 表 | 必需列与约束 |
| --- | --- |
| `app_user` | `user_id BIGINT` 主键；`username VARCHAR(64)`；`username_normalized VARCHAR(64)` 唯一；`password_hash VARCHAR(100)`；可空 `phone_normalized VARCHAR(32)` 唯一；可空 `email_normalized VARCHAR(128)` 唯一；`personal_tenant_id BIGINT` 唯一；`display_name VARCHAR(64)`；`status VARCHAR(16)`；`must_change_password TINYINT`；`credential_revision BIGINT`、`identity_revision BIGINT`、`permission_revision BIGINT` 均默认 1；显式四个 typed actor 列；`create_time/update_time`；`del_flag CHAR(1)`。不得含 `sys_user_id` 或 `user_type`。 |
| `app_auth_client` | `id BIGINT` 主键；`client_id VARCHAR(64)` 唯一；`client_key VARCHAR(64)` 唯一；`client_secret_hash VARCHAR(100)`；逗号分隔并由服务校验的 `grant_types/access_paths/ip_whitelist`；`token_timeout BIGINT`；`active_timeout BIGINT`；`client_revision BIGINT` 默认 1；`status VARCHAR(16)`；typed actor、时间、逻辑删除列。 |
| `app_social_identity` | `social_identity_id BIGINT` 主键；`user_id BIGINT`；`provider VARCHAR(32)`；`provider_subject VARCHAR(128)`；`status VARCHAR(16)`；typed actor 与时间列；唯一键 `(provider, provider_subject)` 和 `(user_id, provider)`；只允许外键指向 `app_user`，不得指向 `sys_social`。 |
| `app_permission` | `permission_id BIGINT` 主键；`permission_code VARCHAR(100)` 唯一；`permission_name VARCHAR(100)`；`resource_type VARCHAR(32)`；`action VARCHAR(32)`；`permission_revision BIGINT` 默认 1；`status VARCHAR(16)`；typed actor 与时间列。 |
| `app_role` | `role_id BIGINT` 主键；`role_code VARCHAR(64)` 唯一；`role_name VARCHAR(64)`；`scope_type VARCHAR(16)`；`built_in TINYINT`；`role_revision BIGINT` 默认 1；`status VARCHAR(16)`；typed actor、时间、逻辑删除列。 |
| `app_role_permission` | `id BIGINT` 主键；`role_id BIGINT`；`permission_id BIGINT`；`status VARCHAR(16)`；typed actor 与时间列；唯一键 `(role_id, permission_id)`；只引用 `app_role` 和 `app_permission`。 |
| `app_user_role` | `id BIGINT` 主键；`user_id BIGINT`；`role_id BIGINT`；`status VARCHAR(16)`；可空 `valid_from/valid_until`；typed actor 与时间列；唯一键 `(user_id, role_id)`；只引用 `app_user` 和 `app_role`。 |
| `app_login_log` | `login_log_id BIGINT` 主键；`auth_method VARCHAR(32)`；`masked_identifier VARCHAR(128)`；`client_id VARCHAR(64)`；`result_code INT`；`failure_category VARCHAR(32)`；可空 `user_id/session_id`；`ip_address VARCHAR(64)`；`device_summary VARCHAR(255)`；`request_id VARCHAR(64)`；`occurred_at DATETIME`。只追加，不含密码、验证码、客户端密钥或令牌原文。 |
| `app_security_audit` | `audit_id BIGINT` 主键；`resource_type VARCHAR(64)`；`resource_id VARCHAR(64)`；`action VARCHAR(64)`；`actor_type VARCHAR(16)`；`actor_id BIGINT`；`before_digest/after_digest VARCHAR(128)`；`reason VARCHAR(500)`；`request_id VARCHAR(64)`；`ip_address VARCHAR(64)`；`occurred_at DATETIME`。只追加。 |

15 个 `app_permission` 稳定代码固定为：`aivideo:studio:query`、`aivideo:studio:create`、`aivideo:studio:edit`、`aivideo:studio:generate`、`aivideo:script:query`、`aivideo:script:edit`、`aivideo:script:confirm`、`aivideo:script:remove`、`aivideo:task:query`、`aivideo:task:cancel`、`aivideo:quota:query`、`aivideo:quota:use`、`aivideo:quota:organization-query`、`aivideo:notification:query`、`aivideo:notification:edit`。

4 个内置角色定义固定为：`personal_creator/personal`、`organization_owner/organization`、`organization_admin/organization`、`organization_member/organization`。

## 十类安全测试追踪

| 编号 | 必须证明的事实 | 主测试 |
| --- | --- | --- |
| S1 | 相同数字编号、用户名和密码的 `app_user`/`sys_user` 仍是不同账号类型、角色和权限 | `AppIdentityIsolationIT` |
| S2 | 有效 sys 令牌不能访问 `/api/**`，有效 app 令牌不能访问 `/api/admin/**` 或 `/system/**` | `DualTokenIsolationIT` |
| S3 | sys 侧同名权限不满足 `@SaCheckPermission(type = "app")` | `AppPermissionTypeIT` |
| S4 | 凭据、身份、权限和客户端四类修订任一变化都使旧 app 会话失效 | `AppSessionRevisionIT` |
| S5 | app 停用、改密/找回、客户端换密、社交解绑和角色变化只撤销 app 会话；sys 侧对应操作也不撤销 app 会话 | `AppMutationIsolationIT` |
| S6 | 交换客户端键、错误授权类型、路径不匹配和 IP 不在白名单均拒绝且不回退 `sys_client` | `DualTokenIsolationIT`（客户端策略场景） |
| S7 | 重复/逗号拼接 Authorization、Cookie/Query 混合令牌和多个 clientid 在解析身份前确定性拒绝，不泄露账号存在性 | `StrictCredentialIngressTest` |
| S8 | 用户启动上下文没有运营登录/用户/角色/菜单 Controller，运营启动上下文没有创作端认证 Controller | `UserStarterAssemblyIT` + `PlatformStarterAssemblyIT` |
| S9 | 即使原始 token 相同，`Authorization:app:*` 与默认命名空间也不能互读 | `AppSessionNamespaceIT` |
| S10 | typed actor 消除同号歧义；sys 数据权限不扩大 app 自助查询；退出、强踢和重置只影响指定登录类型 | `AppActorAndScopeIT` |

## 文件结构

### 公共契约与入口

- 修改 `docs/API_CONTRACT.md`
- 修改 `docs/DOMAIN_MODEL.md`
- 修改 `docs/ARCHITECTURE.md`
- 修改 `docs/BACKEND_GUIDE.md`
- 修改 `ai-video-ui/ai-video-webapp/PRD.md`
- 修改 `docs/superpowers/specs/2026-07-07-user-auth-login-design.md`
- 修改 `docs/superpowers/plans/2026-07-07-user-auth-login-implementation.md`

### Maven（Java 项目构建工具）与启动模块

- 修改 `ai-video-api/pom.xml`
- 修改 `ai-video-api/ruoyi-modules/pom.xml`
- 新建 `ai-video-api/ruoyi-modules/ai-video/pom.xml`
- 新建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 新建 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`
- 新建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/pom.xml`
- 新建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/pom.xml`
- 修改 `ai-video-api/ai-video-user-api/pom.xml`
- 修改 `ai-video-api/ruoyi-admin/pom.xml`
- 修改 `ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 修改 `ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/AuthController.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/CaptchaController.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/IndexController.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/domain/vo/LoginVo.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/event/UserLoginSuccessEvent.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserActionListener.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserLoginSuccessListener.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/IAuthStrategy.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysLoginService.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysRegisterService.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SmsAuthStrategy.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/EmailAuthStrategy.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SocialAuthStrategy.java`
- 删除 `ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/XcxAuthStrategy.java`

### SQL（结构化查询语言）

- 新建 `docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql`

### `ai-video-core`

- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppUser.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppAuthClient.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppSocialIdentity.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppPermission.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppRole.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppRolePermission.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppUserRole.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppLoginLog.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppSecurityAudit.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppIdentityStatus.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppActorType.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppAuthMethod.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppUserMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppAuthClientMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppSocialIdentityMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppPermissionMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppRoleMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppRolePermissionMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppUserRoleMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppLoginLogMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/mapper/AppSecurityAuditMapper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppIdentityService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppSessionService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppPermissionService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppAuthClientService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppSecurityAuditService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppVerificationCodeService.java`
- 新建目录 `src/main/java/org/dromara/aivideo/identity/dto/`，每个稳定跨模块 DTO 独立为 `*DTO.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/event/AppSessionInvalidationEvent.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppIdentityServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppPermissionServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppAuthClientServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppSecurityAuditServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/impl/AppVerificationCodeServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/dto/AppPrincipalSnapshotDTO.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/dto/AppWorkspaceSessionSnapshotDTO.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/domain/AppSessionInvalidationReason.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/dto/AppSecurityAuditDTO.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppLoginUser.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppLoginHelper.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppActorContext.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppStpLogic.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppStpLogicRegistrar.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppSaTokenProperties.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppSessionRevisionGuard.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppPersonalWorkspaceSnapshotProvider.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/security/AppPasswordPolicy.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppVerificationDeliveryService.java`
- 新建 `src/main/java/org/dromara/aivideo/identity/service/IAppExternalIdentityService.java`
- 新建 `src/test/java/org/dromara/aivideo/testsupport/LocalIntegrationEnvironment.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppIdentitySchemaIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppPermissionTypeIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppSessionNamespaceIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppMutationIsolationIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java`
- 新建 `src/test/java/org/dromara/aivideo/identity/IdentityPackageBoundaryTest.java`

以上本小节所列 `src` 路径均相对于 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/`。

### `ai-video-infra`

- 新建 `src/main/java/org/dromara/aivideo/infra/verification/provider/AppSmsVerificationProvider.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/verification/provider/AppMailVerificationProvider.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppSmsVerificationDeliveryServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppMailVerificationDeliveryServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/identity/provider/AppSocialIdentityProvider.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/identity/provider/AppMiniProgramIdentityProvider.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppSocialExternalIdentityServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppMiniProgramExternalIdentityServiceImpl.java`
- 新建 `src/test/java/org/dromara/aivideo/infra/identity/provider/AppExternalIdentityProviderTest.java`
- 新建 `src/test/java/org/dromara/aivideo/infra/verification/provider/AppVerificationProviderTest.java`

以上路径均相对于 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/`。

### `ai-video-user`

- 新建 `src/main/java/org/dromara/aivideo/user/auth/controller/AppAuthController.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordLoginBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppVerificationCodeBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppCodeLoginBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialLoginBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppMiniProgramLoginBo.java`
- 延期：`src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppRegisterBo.java`（注册请求对象不纳入本轮）
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordResetBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordChangeBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialBindingBo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppLoginVo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppMeVo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppVerificationChallengeVo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppSessionVo.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/service/IAppAuthApplicationService.java`
- 新建 `src/main/java/org/dromara/aivideo/user/auth/service/impl/AppAuthApplicationServiceImpl.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/StrictCredentialHeaders.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppCredentialIngressFilter.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppClientPolicyService.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppAuthenticationInterceptor.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppSecurityConfig.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppSecurityExceptionHandler.java`
- 新建 `src/main/java/org/dromara/aivideo/user/security/AppAuthErrorCodes.java`
- 新建 `src/test/java/org/dromara/aivideo/user/security/StrictCredentialIngressTest.java`
- 新建 `src/test/java/org/dromara/aivideo/user/security/AppClientPolicyServiceTest.java`
- 新建 `src/test/java/org/dromara/aivideo/user/auth/controller/AppAuthControllerTest.java`

以上路径均相对于 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/`。

### 运营端默认安全链 Header（请求头）门禁

- 修改 `ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/resources/common-satoken.yml`
- 修改 `ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/config/SecurityConfig.java`
- 新建 `ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/filter/StrictHeaderCredentialFilter.java`
- 新建 `ai-video-api/ruoyi-common/ruoyi-common-security/src/test/java/org/dromara/common/security/filter/StrictHeaderCredentialFilterTest.java`

### `ai-video-platform`

- 新建 `src/main/java/org/dromara/aivideo/platform/identity/controller/AppUserAdminController.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/controller/AppRoleAdminController.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/controller/AppAuthClientAdminController.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/controller/AppSessionAdminController.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/controller/AppSecurityLogAdminController.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/domain/bo/AppIdentityAdminBos.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/domain/vo/AppIdentityAdminVos.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/service/IAppIdentityAdminService.java`
- 新建 `src/main/java/org/dromara/aivideo/platform/identity/service/impl/AppIdentityAdminServiceImpl.java`
- 新建 `src/test/java/org/dromara/aivideo/platform/identity/AppIdentityAdminControllerIT.java`

以上路径均相对于 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/`。

### 双启动集成测试

- 新建 `ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java`
- 新建 `ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/bootstrap/PlatformStarterAssemblyIT.java`
- 新建 `ai-video-api/ai-video-integration-tests/pom.xml`
- 新建 `ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterJarAssemblyIT.java`
- 新建 `ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterProcessCleanupIT.java`
- 新建 `ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualStarterHttpFixture.java`
- 新建 `ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualTokenIsolationIT.java`

### 用户端 Web（网页端）

- 修改 `ai-video-ui/ai-video-webapp/config/config.ts`
- 修改 `ai-video-ui/ai-video-webapp/config/proxy.ts`
- 修改 `ai-video-ui/ai-video-webapp/config/routes.ts`
- 修改 `ai-video-ui/ai-video-webapp/src/app.tsx`
- 修改 `ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts`
- 修改 `ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`
- 删除 `ai-video-ui/ai-video-webapp/src/pages/user/login/login.test.tsx`
- 删除 `ai-video-ui/ai-video-webapp/src/pages/user/login/__snapshots__/login.test.tsx.snap`
- 修改 `ai-video-ui/ai-video-webapp/vitest.config.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/session.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`
- 延期：`ai-video-ui/ai-video-webapp/src/pages/user/register/index.tsx`（注册页不纳入本轮）
- 延期：`ai-video-ui/ai-video-webapp/src/pages/user/register/index.test.tsx`（注册页测试不纳入本轮）
- 新建 `ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.tsx`
- 新建 `ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.test.tsx`
- 新建 `ai-video-ui/ai-video-webapp/src/pages/user/security/index.tsx`
- 新建 `ai-video-ui/ai-video-webapp/src/pages/user/security/index.test.tsx`
- 新建 `ai-video-ui/ai-video-webapp/src/locales/zh-CN/identity.ts`
- 新建 `ai-video-ui/ai-video-webapp/src/locales/en-US/identity.ts`

### 运营端 Web（网页端）

- 修改 `ai-video-ui/ai-video-platform-ui/package.json`
- 修改 `ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml`
- 新建 `ai-video-ui/ai-video-platform-ui/vitest.config.ts`
- 新建 `ai-video-ui/ai-video-platform-ui/tests/setupTests.ts`
- 新建 `ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/types.ts`
- 新建 `ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.ts`
- 新建 `ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.test.ts`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.test.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserFormModal.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserSecurityDrawer.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-role/index.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-auth-client/index.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-session/index.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-login-log/index.tsx`
- 新建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-security-audit/index.tsx`

## 任务 1：冻结 P0-A 公共契约并废止旧身份方案

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`docs/BACKEND_GUIDE.md`
- 修改：`ai-video-ui/ai-video-webapp/PRD.md`
- 修改：`docs/superpowers/specs/2026-07-07-user-auth-login-design.md`
- 修改：`docs/superpowers/plans/2026-07-07-user-auth-login-implementation.md`

- [ ] **步骤 1（2–5 分钟）：先运行旧方案门禁并记录失败**

运行：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$matches = @(rg -n "ai_user|userType|StpUtil|LoginHelper" docs/superpowers/specs/2026-07-07-user-auth-login-design.md docs/superpowers/plans/2026-07-07-user-auth-login-implementation.md)
$scanExitCode = $LASTEXITCODE
if ($scanExitCode -gt 1) { throw '旧身份方案扫描命令执行失败' }
$redExitCode = if ($matches.Count -gt 0) { 1 } else { 0 }
$matches
if ($redExitCode -eq 0) { throw '旧身份方案门禁意外通过，未观察到预期红灯' }
```

预期：两份旧文档仍出现可执行的 `ai_user + userType + StpUtil/LoginHelper` 步骤，门禁失败。

- [ ] **步骤 2（2–5 分钟）：把身份、Header、端点和错误码写入公共契约**

在 `API_CONTRACT.md` 固定：

```http
Authorization: Bearer <app access_token>
clientid: <app_auth_client.client_key>
content-language: <current locale>
```

并逐项登记 `/api/auth/verification-codes`、`register`、`login`、`sms-logins`、`email-logins`、`social-logins`、`mini-program-logins`、`password-resets`、`me`、`password`、`logout`、`sessions`，以及 `46128` 至 `46134`。明确 `POST /api/auth/social-bindings` 与 `DELETE /api/auth/social-bindings/{socialIdentityId}` 是第三方绑定/解绑入口，解绑递增 `identityRevision`。

- [ ] **步骤 3（2–5 分钟）：写清模块装配和 P0-B 边界**

在 `ARCHITECTURE.md`/`BACKEND_GUIDE.md` 写入四模块及双启动依赖方向；在 `DOMAIN_MODEL.md` 写入本计划“数据库精确结构”、用户三类修订、客户端修订、workspace/membership 修订与 typed actor 规则；在 Web PRD 写入登录、找回、安全会话页的加载、倒计时、提交中、统一凭据错误、停用、客户端不可用、网络失败和成功跳转状态，并把注册页明确标为延期。

- [ ] **步骤 4（2–5 分钟）：给旧规格和旧计划加不可误读的废止首屏**

两份 2026-07-07 文档标题下第一段必须是：

```md
> **已废止，禁止执行：** 本文的 `ai_user + userType + 默认 StpUtil/LoginHelper` 方案已由
> `2026-07-28-say-requirements-copy-generation-design.md` 的 `P0-A-identity-security`
> 完整替代，仅保留用于历史追溯。
```

删除旧计划中的未完成复选框，防止执行器继续把它当作活动计划。

- [ ] **步骤 5（2–5 分钟）：运行文档门禁**

运行：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '开发规范检查失败' }
rg -n "已废止，禁止执行" docs/superpowers/specs/2026-07-07-user-auth-login-design.md docs/superpowers/plans/2026-07-07-user-auth-login-implementation.md
if ($LASTEXITCODE -ne 0) { throw '旧身份方案废止声明检查失败' }
```

预期：规范校验退出码为 0，第二条命令恰好命中两份旧文档的废止声明。

- [ ] **步骤 6（2–5 分钟）：提交契约**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'docs/API_CONTRACT.md'
  'docs/DOMAIN_MODEL.md'
  'docs/ARCHITECTURE.md'
  'docs/BACKEND_GUIDE.md'
  'ai-video-ui/ai-video-webapp/PRD.md'
  'docs/superpowers/specs/2026-07-07-user-auth-login-design.md'
  'docs/superpowers/plans/2026-07-07-user-auth-login-implementation.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "docs(identity): 冻结创作端独立身份安全契约"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 2：建立四模块与双启动编译边界

**文件：**

- 修改：`ai-video-api/pom.xml`
- 修改：`ai-video-api/ruoyi-modules/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 修改：`ai-video-api/ruoyi-admin/pom.xml`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/AuthController.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/CaptchaController.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/IndexController.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/domain/vo/LoginVo.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/event/UserLoginSuccessEvent.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserActionListener.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserLoginSuccessListener.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/IAuthStrategy.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysLoginService.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysRegisterService.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SmsAuthStrategy.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/EmailAuthStrategy.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SocialAuthStrategy.java`
- 删除：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/XcxAuthStrategy.java`
- 新建：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java`

- [ ] **步骤 1（2–5 分钟）：先写会失败的用户启动依赖边界测试**

```java
package org.dromara.aivideo.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class UserStarterAssemblyIT {
    @Test
    void userStarterMustNotContainPlatformOrGeneratorImplementations() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.dromara.system.mapper.SysUserMapper"));
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.dromara.web.service.SysLoginService"));
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.dromara.gen.controller.GenController"));
    }
}
```

- [ ] **步骤 2（2–5 分钟）：先在根 POM 建立统一集成测试入口**

`ai-video-api/pom.xml` 增加 `<skipITs>true</skipITs>` 和 `local-integration-test`（本机集成测试）配置；Surefire（单元测试插件）排除 `**/*IT.java`，Failsafe（集成测试插件）只包含 `**/*IT.java`。该 Profile 向测试进程传递 `aivideo.local-integration-test=true` 和用户端 `application-dev.yml` 路径；共享数据源、Redis 和凭据直接保存在该开发配置中，`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖。`LocalIntegrationEnvironment` 必须验证启用标志以及最终合并配置，缺失或不安全即拒绝运行：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven-surefire-plugin.version}</version>
    <configuration>
        <skipITs>${skipITs}</skipITs>
        <groups>${profiles.active}</groups>
        <systemPropertyVariables>
            <aivideo.local-integration-test>${aivideo.local-integration-test}</aivideo.local-integration-test>
        </systemPropertyVariables>
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<profile>
    <id>local-integration-test</id>
    <properties>
        <aivideo.local-integration-test>true</aivideo.local-integration-test>
    </properties>
</profile>
```

P0-B 及后续计划只验证或扩展该配置，不再建立第二套集成测试约定。

- [ ] **步骤 3（2–5 分钟）：运行边界测试并确认真实红灯**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ai-video-user-api -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dfailsafe.failIfNoSpecifiedTests=false' `
  '-Dit.test=UserStarterAssemblyIT' verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) {
  throw 'UserStarterAssemblyIT 应因仍可加载 sys 身份实现而失败'
}
$report = Get-ChildItem `
    -Path .\ai-video-user-api\target\failsafe-reports `
    -Filter 'TEST-*UserStarterAssemblyIT.xml' |
  Select-Object -First 1
if ($null -eq $report) {
  throw 'UserStarterAssemblyIT 没有产生报告，当前失败不是有效红灯'
}
[xml]$xml = Get-Content -LiteralPath $report.FullName
if ([int]$xml.testsuite.tests -le 0) {
  throw 'UserStarterAssemblyIT 执行数为 0'
}
```

预期：`UserStarterAssemblyIT` 的 `Tests run` 大于 0，并因用户启动模块仍能加载 `SysUserMapper` 或 `SysLoginService` 而失败。

- [ ] **步骤 4（2–5 分钟）：创建固定四模块聚合 POM**

`ruoyi-modules/ai-video/pom.xml` 只声明：

```xml
<packaging>pom</packaging>
<modules>
    <module>ai-video-core</module>
    <module>ai-video-infra</module>
    <module>ai-video-user</module>
    <module>ai-video-platform</module>
</modules>
```

依赖方向固定为 `infra -> core`、`user -> core`、`platform -> core`；只有两个启动模块负责同时装配接口适配模块与 `infra`。`core` 不依赖 `user/platform`，两个适配模块互不依赖。

- [ ] **步骤 5（2–5 分钟）：收紧两个启动模块依赖**

`ai-video-user-api` 移除 `ruoyi-system`、`ruoyi-job`、`ruoyi-ai`、`ruoyi-demo`、`ruoyi-workflow` 和 generator profile（代码生成器构建配置）依赖，新增 `ai-video-user` 与 `ai-video-infra`；保留 Web、Sa-Token、Redis、加密、短信、邮件、社交等中性依赖。必须删除用户启动器中默认启用的 `gen` profile 及其 `ruoyi-gen` 依赖，而不是仅关闭默认激活；同时从 `src/main/resources/application.yml` 的 SpringDoc 扫描列表移除 `org.dromara.demo`、`org.dromara.system`、`org.dromara.workflow`、`org.dromara.gen`，后续仅由创作端实际装配的接口包提供文档。`ruoyi-admin` 新增 `ai-video-platform` 与 `ai-video-infra`，保留现有运营模块。

- [ ] **步骤 6（2–5 分钟）：删除用户启动模块中的 sys 身份复制代码**

删除本任务“文件”段逐项列出的旧 Controller（控制器）、Service（服务）、策略、监听器和 VO（视图对象）。不得移动到 `org.dromara.aivideo`，因为其实现仍查询 `sys_user/sys_client`。

- [ ] **步骤 7（2–5 分钟）：运行边界测试和 Reactor（Maven 多模块构建）编译**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ai-video-user-api,ruoyi-admin -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dfailsafe.failIfNoSpecifiedTests=false' `
  '-Dit.test=UserStarterAssemblyIT' verify
if ($LASTEXITCODE -ne 0) { throw '启动模块边界测试或多模块编译失败' }
$report = Get-ChildItem `
    -Path .\ai-video-user-api\target\failsafe-reports `
    -Filter 'TEST-*UserStarterAssemblyIT.xml' |
  Select-Object -First 1
if ($null -eq $report) { throw 'UserStarterAssemblyIT 未产生报告' }
[xml]$xml = Get-Content -LiteralPath $report.FullName
if ([int]$xml.testsuite.tests -le 0 -or
    [int]$xml.testsuite.failures -ne 0 -or
    [int]$xml.testsuite.errors -ne 0) {
  throw 'UserStarterAssemblyIT 未执行或存在失败'
}
$userPom = Get-Content -Raw -LiteralPath .\ai-video-user-api\pom.xml
if ($userPom -match 'ruoyi-gen' -or $userPom -match '<id>gen</id>') {
  throw '用户启动器仍保留代码生成器 profile 或依赖'
}
$userApplication = Get-Content -Raw -LiteralPath .\ai-video-user-api\src\main\resources\application.yml
$legacySpringDocPackages = @(
  'org.dromara.demo',
  'org.dromara.system',
  'org.dromara.workflow',
  'org.dromara.gen'
)
foreach ($legacyPackage in $legacySpringDocPackages) {
  if ($userApplication.Contains($legacyPackage)) {
    throw "用户启动器 SpringDoc 仍扫描运营包：$legacyPackage"
  }
}
```

预期：四模块进入 Reactor；`UserStarterAssemblyIT` 的 `Tests run` 大于 0 并通过；运营启动模块原有认证代码仍编译。

- [ ] **步骤 8（2–5 分钟）：提交模块边界**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/pom.xml'
  'ai-video-api/ruoyi-modules/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/pom.xml'
  'ai-video-api/ai-video-user-api/pom.xml'
  'ai-video-api/ai-video-user-api/src/main/resources/application.yml'
  'ai-video-api/ruoyi-admin/pom.xml'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/AuthController.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/CaptchaController.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/controller/IndexController.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/domain/vo/LoginVo.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/event/UserLoginSuccessEvent.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserActionListener.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/listener/UserLoginSuccessListener.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/IAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysLoginService.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/SysRegisterService.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SmsAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/EmailAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/SocialAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/main/java/org/dromara/web/service/impl/XcxAuthStrategy.java'
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "refactor(identity): 隔离用户与运营启动模块"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 3：以重复执行测试驱动身份表和权限种子

**文件：**

- 新建：`docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/testsupport/LocalIntegrationEnvironment.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentitySchemaIT.java`

- [ ] **步骤 1（2–5 分钟）：加入 JUnit、本机 MySQL/Redis 集成测试支持并导出 test-jar**

保留 `spring-boot-starter-test`、MySQL JDBC（Java 数据库连接）和 Redis 测试所需依赖；不得加入任何容器测试依赖。用 `maven-jar-plugin:test-jar` 导出 `LocalIntegrationEnvironment` 给适配模块测试复用。该夹具默认从用户端 `application-dev.yml` 读取本机连接和凭据，接受 `AI_VIDEO_IT_*` 环境变量可选覆盖，在连接、迁移或清理前验证 `localhost`/`127.0.0.1`/`::1`、MySQL `ai_video_test`、Redis 独立逻辑库与 `aivideo:it:<runId>:` 前缀；不安全或缺失配置立即失败，且禁止 `FLUSHALL`。

- [ ] **步骤 2（2–5 分钟）：先写迁移重复执行失败测试**

```java
@Tag("dev")
class AppIdentitySchemaIT {
    private static final LocalIntegrationEnvironment ENV =
        LocalIntegrationEnvironment.requireFromEnvironment();

    @BeforeEach
    void resetDedicatedLocalState() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        ENV.clearCurrentRunRedisKeys();
    }

    @Test
    void migrationIsRepeatableAndHasNoSysForeignKeys() throws Exception {
        Path sql = Path.of("..", "..", "..", "script", "sql", "ai-video", "mysql",
            "20260728_01_p0a_identity_security.sql").normalize();
        execute(sql);
        execute(sql);
        assertThat(tableNames("app_%")).containsExactlyInAnyOrder(
            "app_user", "app_auth_client", "app_social_identity", "app_permission",
            "app_role", "app_role_permission", "app_user_role", "app_login_log",
            "app_security_audit");
        assertThat(count("app_permission")).isEqualTo(15);
        assertThat(count("app_role")).isEqualTo(4);
        assertThat(count("app_role_permission")).isZero();
        assertThat(sysForeignKeyCount()).isZero();
    }

    @Test
    void everyTypedActorColumnHasAnEnforcedIdentityDomainCheck() throws Exception {
        Path sql = Path.of("..", "..", "..", "script", "sql", "ai-video", "mysql",
            "20260728_01_p0a_identity_security.sql").normalize();
        execute(sql);

        try (Connection connection = openConnection()) {
            assertActorTypeCheck(connection, "ck_app_user_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_auth_client_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_social_identity_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_permission_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_role_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_role_permission_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_user_role_actor_types",
                "created_by_type", "updated_by_type");
            assertActorTypeCheck(connection, "ck_app_security_audit_actor_type",
                "actor_type");

            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_user (
                    user_id, username, username_normalized, password_hash,
                    personal_tenant_id, display_name,
                    created_by_type, created_by_id,
                    updated_by_type, updated_by_id
                ) VALUES (
                    99001, 'invalid-actor', 'invalid-actor', 'not-a-real-hash',
                    99001, '非法主体',
                    'service', 1, 'sys_user', 1
                )
                """))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_security_audit (
                    audit_id, resource_type, resource_id, action,
                    actor_type, actor_id, reason, request_id, ip_address, occurred_at
                ) VALUES (
                    99001, 'app_user', '99001', 'test',
                    'service', 1, '非法主体', 'schema-it', '127.0.0.1', CURRENT_TIMESTAMP
                )
                """))
                .isInstanceOf(SQLException.class);
        }
    }
}
```

`assertActorTypeCheck` 从 `information_schema.check_constraints` 读取当前 schema
中的准确约束，断言检查表达式同时包含传入列、`app_user` 和 `sys_user`；
`executeUpdate` 使用同一 JDBC（Java 数据库连接）连接直接执行固定测试 SQL。测试不仅
检查约束名称，还实际证明 MySQL 拒绝第三种主体值 `service`。

示例中的 `execute`、`tableNames`、`count`、`sysForeignKeyCount` 与 `openConnection` 辅助方法都必须委托给 `LocalIntegrationEnvironment` 或其 `ENV.openMySqlConnection()` 连接；不得自行创建数据源、硬编码连接串或回退到任何容器实现。

本计划其余所有 `*IT.java` 也必须在类级标注 `@Tag("dev")`，保证 P0-A 固定的 Failsafe 分组确实执行；测试报告中目标类的 `Tests run` 必须大于 0。

- [ ] **步骤 3（2–5 分钟）：运行并确认因 SQL 不存在而失败**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentitySchemaIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：`NoSuchFileException` 指向固定 SQL 路径。

- [ ] **步骤 4（2–5 分钟）：写完整 DDL 与可重复种子**

`app_user` 的关键 DDL 必须直接体现禁止项：

```sql
CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    username_normalized VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone_normalized VARCHAR(32) NULL,
    email_normalized VARCHAR(128) NULL,
    personal_tenant_id BIGINT NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    must_change_password TINYINT NOT NULL DEFAULT 0,
    credential_revision BIGINT NOT NULL DEFAULT 1,
    identity_revision BIGINT NOT NULL DEFAULT 1,
    permission_revision BIGINT NOT NULL DEFAULT 1,
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_username_normalized (username_normalized),
    UNIQUE KEY uk_app_user_phone_normalized (phone_normalized),
    UNIQUE KEY uk_app_user_email_normalized (email_normalized),
    UNIQUE KEY uk_app_user_personal_tenant (personal_tenant_id),
    CONSTRAINT ck_app_user_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

同一脚本继续写出另外八张表的完整 DDL（数据定义语言），不得留给执行者按描述猜测：

```sql
CREATE TABLE IF NOT EXISTS app_auth_client (
    id BIGINT NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    client_key VARCHAR(64) NOT NULL,
    client_secret_hash VARCHAR(100) NULL,
    grant_types VARCHAR(500) NOT NULL,
    access_paths VARCHAR(1000) NOT NULL,
    ip_whitelist VARCHAR(1000) NULL,
    token_timeout BIGINT NOT NULL,
    active_timeout BIGINT NOT NULL,
    client_revision BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_auth_client_id (client_id),
    UNIQUE KEY uk_app_auth_client_key (client_key),
    CONSTRAINT ck_app_auth_client_revision CHECK (client_revision > 0),
    CONSTRAINT ck_app_auth_client_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_social_identity (
    social_identity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (social_identity_id),
    UNIQUE KEY uk_app_social_provider_subject (provider, provider_subject),
    UNIQUE KEY uk_app_social_user_provider (user_id, provider),
    CONSTRAINT fk_app_social_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_app_social_identity_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_permission (
    permission_id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    permission_revision BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_app_permission_code (permission_code),
    CONSTRAINT ck_app_permission_revision CHECK (permission_revision > 0),
    CONSTRAINT ck_app_permission_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role (
    role_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    role_revision BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_app_role_code (role_code),
    CONSTRAINT ck_app_role_scope
        CHECK (scope_type IN ('personal', 'organization')),
    CONSTRAINT ck_app_role_revision CHECK (role_revision > 0),
    CONSTRAINT ck_app_role_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role_permission (
    id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_role_permission (role_id, permission_id),
    CONSTRAINT fk_app_role_permission_role
        FOREIGN KEY (role_id) REFERENCES app_role (role_id),
    CONSTRAINT fk_app_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES app_permission (permission_id),
    CONSTRAINT ck_app_role_permission_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_user_role (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    valid_from DATETIME NULL,
    valid_until DATETIME NULL,
    created_by_type VARCHAR(16) NOT NULL,
    created_by_id BIGINT NOT NULL,
    updated_by_type VARCHAR(16) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_role (user_id, role_id),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_app_user_role_role
        FOREIGN KEY (role_id) REFERENCES app_role (role_id),
    CONSTRAINT ck_app_user_role_validity
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from),
    CONSTRAINT ck_app_user_role_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_login_log (
    login_log_id BIGINT NOT NULL,
    auth_method VARCHAR(32) NOT NULL,
    masked_identifier VARCHAR(128) NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    result_code INT NOT NULL,
    failure_category VARCHAR(32) NULL,
    user_id BIGINT NULL,
    session_id VARCHAR(128) NULL,
    ip_address VARCHAR(64) NOT NULL,
    device_summary VARCHAR(255) NULL,
    request_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME NOT NULL,
    PRIMARY KEY (login_log_id),
    KEY idx_app_login_user_time (user_id, occurred_at),
    KEY idx_app_login_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_security_audit (
    audit_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    before_digest VARCHAR(128) NULL,
    after_digest VARCHAR(128) NULL,
    reason VARCHAR(500) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    occurred_at DATETIME NOT NULL,
    PRIMARY KEY (audit_id),
    KEY idx_app_audit_resource
        (resource_type, resource_id, occurred_at),
    KEY idx_app_audit_actor (actor_type, actor_id, occurred_at),
    CONSTRAINT ck_app_security_audit_actor_type
        CHECK (actor_type IN ('app_user', 'sys_user'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`app_login_log` 与 `app_security_audit` 不设置逻辑删除列，也不允许更新或删除业务接口。15 个权限按本计划“数据库精确结构”后的固定代码清单顺序使用稳定主键区间，四个角色使用稳定主键并分别写入 `personal_creator/personal`、`organization_owner/organization`、`organization_admin/organization`、`organization_member/organization`；种子使用唯一代码冲突更新名称、范围、状态和修订，不改变稳定主键。`app_role_permission` 在 P0-A 必须保持零行。

- [ ] **步骤 5（2–5 分钟）：插入运营菜单与精确权限**

在同一迁移中可重复插入一个“创作端身份安全”父菜单和用户、角色、权限、客户端、会话、登录日志、安全审计六个页面菜单，按钮权限严格使用规格第 12.2 节的 `aivideo:app-user:*`、`aivideo:app-role:*`、`aivideo:app-auth-client:*`、`aivideo:app-session:*`、`aivideo:app-login-log:query`、`aivideo:app-security-audit:query`。不得插入“冒充登录”“签发令牌”或“继承运营角色”按钮。

- [ ] **步骤 6（2–5 分钟）：再次运行迁移测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentitySchemaIT verify
if ($LASTEXITCODE -ne 0) { throw '身份数据库重复执行测试失败' }
```

预期：两次执行均成功，9 张表、15 个权限、4 个角色、0 个初始角色权限映射，且没有指向 `sys_*` 的外键。

- [ ] **步骤 7（2–5 分钟）：提交数据库基线**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/testsupport/LocalIntegrationEnvironment.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentitySchemaIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 新增独立创作端身份表"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 4：实现独立身份、密码和 typed actor（强类型操作者）审计（注册延期）

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppUser.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppAuthClient.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSocialIdentity.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppPermission.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppRole.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppRolePermission.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppUserRole.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppLoginLog.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSecurityAudit.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppIdentityStatus.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppActorType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppAuthMethod.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppUserMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppAuthClientMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppSocialIdentityMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppPermissionMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppRoleMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppRolePermissionMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppUserRoleMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppLoginLogMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppSecurityAuditMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppIdentityService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppSecurityAuditService.java`
- 新建目录：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/`（每个稳定 DTO 独立为 `*DTO.java`）
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppIdentityServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSecurityAuditServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSecurityAuditDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppActorContext.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppPasswordPolicy.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java`

- [ ] **步骤 1（2–5 分钟）：先写 S1 与 S10 的失败测试**

测试夹具创建相同 `user_id = 1001`、相同用户名 `same-user`、相同明文密码的 `sys_user` 与 `app_user`，然后断言：

```java
assertThat(appIdentityService.authenticatePassword(
    new AuthenticatePasswordDTO("same-user", "Same#Pass123", "desktop"), appClient))
    .extracting(AppAuthenticatedIdentityDTO::userId)
    .isEqualTo(1001L);
assertThat(jdbc.queryForObject(
    "select count(*) from sys_user where user_id = 1001", Integer.class)).isEqualTo(1);
assertThat(appSecurityAuditMapper.selectList(null))
    .allMatch(row -> row.getActorType() != null && row.getActorId() != null);
```

另写 Mapper 源码边界断言：`org.dromara.aivideo.identity` 不得出现 `@DataPermission`、`BaseEntity`、`org.dromara.system`、默认 `LoginHelper`。

- [ ] **步骤 2（2–5 分钟）：运行测试确认缺少服务和实体**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentityIsolationIT,AppActorAndScopeIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：测试编译因 `IAppIdentityService`、`AppActorContext` 和实体不存在而失败。

- [ ] **步骤 3（2–5 分钟）：创建不继承 BaseEntity 的实体和标准 Mapper**

实体显式声明列；Mapper 仍复用框架能力：

```java
public interface AppUserMapper extends BaseMapperPlus<AppUser, AppUser> {
}
```

`AppActorContext` 必须消除同号歧义：

```java
public record AppActorContext(AppActorType actorType, long actorId) {
    public static AppActorContext appUser(long userId) {
        return new AppActorContext(AppActorType.APP_USER, userId);
    }

    public static AppActorContext sysUser(long userId) {
        return new AppActorContext(AppActorType.SYS_USER, userId);
    }
}
```

- [ ] **步骤 4（2–5 分钟）：实现固定 `IAppIdentityService` 接口**

接口至少包含精确能力：

```java
public interface IAppIdentityService {
    // 预留给后续身份阶段；P0-A 不暴露注册 HTTP（超文本传输协议）入口。
    AppRegisteredIdentityDTO register(RegisterAppUserDTO command, AppActorContext actor);
    AppAuthenticatedIdentityDTO authenticatePassword(
        AuthenticatePasswordDTO command, AppAuthClientSnapshotDTO client);
    AppIdentitySnapshotDTO requireActive(long userId);
    void changePassword(ChangeAppPasswordDTO command, AppActorContext actor);
    void resetPassword(ResetAppPasswordDTO command, AppActorContext actor);
    void changeStatus(ChangeAppUserStatusDTO command, AppActorContext actor);
    void bindSocialIdentity(BindSocialIdentityDTO command, AppActorContext actor);
    void unbindSocialIdentity(long userId, long socialIdentityId, AppActorContext actor);
}
```

注册事务的规范化、唯一性检查、BCrypt 散列、`personal_tenant_id` 生成、`personal_creator` 角色分配和安全审计设计保留为后续契约；P0-A 不暴露注册 HTTP、注册页或注册验证码场景，本轮使用迁移或测试夹具预置的创作端账号。任何身份查询仍只用 `AppUserMapper`/`AppSocialIdentityMapper`。

- [ ] **步骤 5（2–5 分钟）：实现修订写入规则**

- 改密、找回、运营重置：`credential_revision = credential_revision + 1`。
- 用户停用、用户名/联系方式变化、社交绑定/解绑：`identity_revision = identity_revision + 1`。
- 用户角色变化只递增 `permission_revision`，不混入身份修订。
- 更新 SQL 必须带当前修订条件，受影响行不是 1 时返回对应冲突，不能读后无条件写。

- [ ] **步骤 6（2–5 分钟）：冻结安全审计命令和服务签名**

```java
public record AppSecurityAuditDTO(
    String resourceType,
    String resourceId,
    String action,
    AppActorType actorType,
    Long actorId,
    String beforeDigest,
    String afterDigest,
    String reason,
    String requestId,
    String ipAddress
) {
}

public interface IAppSecurityAuditService {
    void append(AppSecurityAuditDTO command);
}
```

`append` 只追加 `app_security_audit`；密码、验证码、token、客户端密钥不得进入摘要输入或日志参数。用户链构造 `actorType = APP_USER`，平台适配层构造 `actorType = SYS_USER`，两者都不把裸数字 actor 作为唯一语义。

- [ ] **步骤 7（2–5 分钟）：运行身份与 actor 测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=IdentityPackageBoundaryTest test
if ($LASTEXITCODE -ne 0) { throw '身份包边界测试失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentityIsolationIT,AppActorAndScopeIT verify
if ($LASTEXITCODE -ne 0) { throw '身份隔离与操作者测试失败' }
```

预期：同号 sys 行不变；app 查询不受 sys 部门/角色数据范围影响；审计可按 `(actor_type, actor_id)` 区分同号主体；静态边界测试通过。

- [ ] **步骤 8（2–5 分钟）：提交身份服务**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppUser.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppAuthClient.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSocialIdentity.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppPermission.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppRole.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppRolePermission.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppUserRole.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppLoginLog.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSecurityAudit.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppIdentityStatus.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppActorType.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppAuthMethod.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppUserMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppAuthClientMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppSocialIdentityMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppPermissionMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppRoleMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppRolePermissionMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppUserRoleMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppLoginLogMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/mapper/AppSecurityAuditMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppIdentityService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppSecurityAuditService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppIdentityServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSecurityAuditServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSecurityAuditDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppActorContext.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppPasswordPolicy.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 实现独立创作端身份与审计"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 5：实现 `app`（创作端）角色权限解析与权限修订失效

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppPermissionService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionInvalidationEvent.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppPermissionServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSessionInvalidationReason.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppPermissionTypeIT.java`
- 修改测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java`

- [ ] **步骤 1（2–5 分钟）：先写 S3 与角色并发失败测试**

```java
@Test
void sysPermissionNeverGrantsAppPermission() {
    insertSysPermission(1001L, "aivideo:studio:create");
    assertThat(appPermissionService.permissionCodes(1001L)).isEmpty();
    insertAppRolePermission(1001L, "personal_creator", "aivideo:studio:create");
    assertThat(appPermissionService.permissionCodes(1001L))
        .containsExactly("aivideo:studio:create");
}

@Test
void staleRoleRevisionIsRejected() {
    assertThatThrownBy(() -> appPermissionService.replaceRolePermissions(
        roleId, 0L, Set.of("aivideo:studio:create"), AppActorContext.sysUser(1001L)))
        .isInstanceOf(AppRoleRevisionConflictException.class);
}
```

在测试配置中增加一个仅供测试的入口：

```java
@RestController
static class AppPermissionProbeController {
    @SaCheckPermission(value = "aivideo:studio:create", type = "app")
    @GetMapping("/api/test/app-permission")
    R<Void> probe() {
        return R.ok();
    }
}
```

用只有 sys 同名权限的 token 请求时断言 401，用具有 app 映射的 app token 请求时断言 200，直接证明注解按 `app` 类型解析。

- [ ] **步骤 2（2–5 分钟）：运行并确认服务不存在**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppPermissionTypeIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：编译失败，缺少固定 `IAppPermissionService`。

- [ ] **步骤 3（2–5 分钟）：实现权限唯一事实源**

```java
public interface IAppPermissionService {
    Set<String> roleCodes(long userId);
    Set<String> permissionCodes(long userId);
    void replaceUserRoles(long userId, long expectedPermissionRevision,
        Set<Long> roleIds, AppActorContext actor);
    void replaceRolePermissions(long roleId, long expectedRoleRevision,
        Set<Long> permissionIds, AppActorContext actor);
}
```

查询只连接 `app_user_role -> app_role -> app_role_permission -> app_permission`，同时过滤状态和有效期；没有映射返回空集合。严禁读取 `sys_role/sys_menu`。

- [ ] **步骤 4（2–5 分钟）：实现事务内修订、事务后 app-only 下线**

替换用户角色时锁定用户并递增其 `permission_revision`。替换角色权限时按 `role_revision` 乐观锁更新角色，再批量递增所有有效被分配用户的 `permission_revision`。事务内发布 `AppSessionInvalidationEvent.forUsers(affectedUserIds, AppSessionInvalidationReason.PERMISSION_CHANGED)`；`@TransactionalEventListener(phase = AFTER_COMMIT)` 对每个用户调用 `IAppSessionService.invalidateUserSessions(userId, AppSessionInvalidationReason.PERMISSION_CHANGED)`，不得调用默认 `StpUtil.logout/kickout`。

- [ ] **步骤 5（2–5 分钟）：运行权限测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppPermissionTypeIT,AppSessionRevisionIT verify
if ($LASTEXITCODE -ne 0) { throw '创作权限与会话修订测试失败' }
```

预期：sys 同名权限无效；app 映射生效；旧角色修订返回 `46134`；用户权限修订只在 app 表递增。

- [ ] **步骤 6（2–5 分钟）：提交角色权限**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppPermissionService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionInvalidationEvent.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppPermissionServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/domain/AppSessionInvalidationReason.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppPermissionTypeIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 增加创作端角色权限解析"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 6：注册独立 `StpLogic`、`AppLoginHelper` 和 Redis（缓存数据库）会话命名空间

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppLoginUser.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppLoginHelper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppStpLogic.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppStpLogicRegistrar.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSaTokenProperties.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSessionRevisionGuard.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppPersonalWorkspaceSnapshotProvider.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppSessionService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionEstablishedEvent.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionEndedEvent.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppPrincipalSnapshotDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSessionQueryDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSessionSummaryDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppWorkspaceSessionSnapshotDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSessionTokenReference.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 修改：`ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/core/dao/PlusSaTokenDao.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppIdentityServiceImpl.java`
- 修改测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java`
- 修改测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionNamespaceIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppMutationIsolationIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionWorkspaceInvalidationIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionIntegrationTestFixture.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionInvalidationListenerContractTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSaTokenDaoNamespaceIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionLogoutIT.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionModelTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionComponentIsolationTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionRevisionGuardTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppStpLogicBehaviorTest.java`

`AppSessionInvalidationReason` 与 `AppSessionInvalidationEvent` 的类型由任务 5 提交；本任务不重定义它们，但必须由 `AppIdentityServiceImpl` 在身份事务内发布，并由会话服务在事务提交后消费。

- [ ] **步骤 1（2–5 分钟）：先写 S4、S5、S9 的失败测试**

测试用 `SaLoginParameter().setToken("same-raw-token")` 分别创建默认 sys 登录和 app 登录，断言：

```java
assertThat(StpUtil.getLoginType()).isEqualTo("login");
assertThat(SaManager.getStpLogic("app").getLoginType()).isEqualTo("app");
assertThat(appLoginHelper.getLoginUser().userId()).isEqualTo(1001L);
assertThat(redisKeys()).contains(
    "Authorization:app:token:same-raw-token",
    "Authorization:login:token:same-raw-token");
appSessionService.invalidateUserSessions(
    1001L, AppSessionInvalidationReason.CREDENTIAL_CHANGED);
assertThat(appLoginHelper.isLogin()).isFalse();
assertThat(StpUtil.isLogin()).isTrue();
```

同一测试类再固定跨阶段行为：

```java
assertThatThrownBy(() -> appSessionService.replaceWorkspace(organizationWorkspace))
    .isInstanceOfSatisfying(ServiceException.class,
        exception -> assertThat(exception.getCode()).isEqualTo(46126));
AppPrincipalSnapshotDTO replaced = appSessionService.replaceWorkspace(forgedPersonalWorkspace);
assertThat(replaced.workspace()).isEqualTo(canonicalPersonalWorkspace);
assertThat(replaced.workspace().permissions()).doesNotContain("forged:permission");
assertThat(replaced.appUserId()).isEqualTo(1001L);
appSessionService.invalidateOrganizationSessions(
    2001L, AppSessionInvalidationReason.ORGANIZATION_DISABLED);
assertThat(appLoginHelper.isLogin()).isFalse();
assertThat(EnumSet.allOf(AppSessionInvalidationReason.class)).containsExactlyInAnyOrder(
    AppSessionInvalidationReason.USER_DISABLED,
    AppSessionInvalidationReason.CREDENTIAL_CHANGED,
    AppSessionInvalidationReason.IDENTITY_CHANGED,
    AppSessionInvalidationReason.PERMISSION_CHANGED,
    AppSessionInvalidationReason.CLIENT_CHANGED,
    AppSessionInvalidationReason.MEMBERSHIP_CHANGED,
    AppSessionInvalidationReason.ORGANIZATION_DISABLED,
    AppSessionInvalidationReason.ADMIN_KICKOUT);
```

P0-A 的 `organizationWorkspace` 只是用于断言拒绝行为的中立快照，不要求 P0-A 建组织表。`forgedPersonalWorkspace` 只允许携带与当前有效 `app_user` 相符的 `workspaceKey` 作为选择器；它的租户、所有者、计费主体、角色、权限和修订字段均不得成为授权依据，必须被事实源重建的 `canonicalPersonalWorkspace` 覆盖。组织 A/B 的失效隔离测试只能通过受信测试夹具模拟后续 P0-B 的内部授权结果，不能经 `replaceWorkspace` 注入组织快照。

- [ ] **步骤 2（2–5 分钟）：运行并确认独立逻辑不存在**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppSessionNamespaceIT,AppSessionRevisionIT,AppMutationIsolationIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：缺少 `AppLoginHelper/IAppSessionService`，测试编译失败。

- [ ] **步骤 3（2–5 分钟）：定义跨 P0-A/P0-B 共用的中立 principal 与 workspace 快照**

```java
public record AppPrincipalSnapshotDTO(
    Long appUserId,
    String username,
    String clientId,
    Long credentialRevision,
    Long identityRevision,
    Long permissionRevision,
    Long clientRevision,
    AppWorkspaceSessionSnapshotDTO workspace
) implements Serializable {
}

public record AppWorkspaceSessionSnapshotDTO(
    String workspaceKey,
    String workspaceType,
    Long tenantId,
    String ownerType,
    Long ownerId,
    String billingSubjectType,
    Long billingSubjectId,
    String roleCode,
    Set<String> permissions,
    Long workspaceRevision,
    Long membershipRevision
) implements Serializable {
    public AppWorkspaceSessionSnapshotDTO {
        permissions = Set.copyOf(permissions);
    }
}

public record AppLoginUser(
    AppPrincipalSnapshotDTO principal,
    String sessionId
) implements Serializable {
    public long userId() {
        return principal.appUserId();
    }
}
```

三个类型不得继承或包含 `org.dromara.system.api.model.LoginUser`。`AppWorkspaceSessionSnapshotDTO` 的 11 个字段和顺序是跨阶段固定契约，不增加 P0-A 私有字段。

- [ ] **步骤 4（2–5 分钟）：实现 P0-A 个人默认 workspace 快照**

`AppPersonalWorkspaceSnapshotProvider` 固定构造：

```java
return new AppWorkspaceSessionSnapshotDTO(
    hmacWorkspaceKey(user.personalTenantId()),
    "personal",
    user.personalTenantId(),
    "app_user",
    user.userId(),
    "personal",
    user.userId(),
    "personal_creator",
    appPermissionService.permissionCodes(user.userId()),
    user.identityRevision(),
    null
);
```

P0-A 不创建组织或成员记录；`P0-B` 只负责从组织/成员事实源构造 `workspaceType = "organization"` 的同类型实例。

- [ ] **步骤 5（2–5 分钟）：实现只从当前 app session（会话）快照取权限的 AppStpLogic**

```java
final class AppStpLogic extends StpLogicJwtForSimple {
    AppStpLogic() {
        super("app");
    }

    @Override
    public String splicingKeyJustCreatedSave() {
        return SaTokenConsts.JUST_CREATED + getLoginType();
    }

    @Override
    public List<String> getPermissionList(Object loginId) {
        AppLoginUser loginUser = currentLoginUser(loginId);
        return loginUser == null ? List.of()
            : List.copyOf(loginUser.principal().workspace().permissions());
    }

    @Override
    public List<String> getRoleList(Object loginId) {
        AppLoginUser loginUser = currentLoginUser(loginId);
        return loginUser == null || loginUser.principal().workspace().roleCode().isBlank()
            ? List.of() : List.of(loginUser.principal().workspace().roleCode());
    }
}
```

Sa-Token 1.45 的这两个权限覆盖签名仅接收 `Object loginId`。`currentLoginUser(loginId)` 必须读取当前 `app` token session（令牌会话）的 `app:login-user:v1`，并校验 session 快照中的 `appUserId` 与传入编号一致；缺会话或不一致返回空集合，绝不回退到运营端 `LoginHelper`、默认 `StpUtil` 或系统权限实现。

`splicingKeyJustCreatedSave()` 必须追加登录类型，否则 Sa-Token 默认使用未分类型的 `JUST_CREATED` 请求存储键，会让同一请求中的 `app` 登录、登出覆盖默认 `login` 登录刚创建的令牌。集成测试同时断言 `JUST_CREATED` 和 `JUST_CREATEDapp` 两个槽位，以及同一原始令牌的两个物理 Redis 键。

`AppStpLogic` 还必须覆盖 `checkActiveTimeoutByConfig`。默认框架的 `TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY` 不区分登录类型；实现必须使用 `TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY + "app"`（等价于 `+ getLoginType()`）作为 app 专属请求标记。测试必须先触发 sys 令牌活跃检查，再将 app 活跃时间设为过期并断言 app 被拒绝、sys 仍保持登录，防止同请求中 sys 的检查结果跳过 app 校验。

- [ ] **步骤 6（2–5 分钟）：避免第二个 Spring StpLogic Bean 覆盖默认逻辑**

`AppStpLogicRegistrar` 是普通组件，内部执行 `new AppStpLogic()`、设置独立 `SaTokenConfig` 并向 `SaManager` 注册，但不声明任何返回 `StpLogic`/`AppStpLogic` 的 `@Bean`。配置固定：

```java
config.setTokenName("Authorization");
config.setTokenPrefix("Bearer");
config.setIsReadHeader(true);
config.setIsReadBody(false);
config.setIsReadCookie(false);
config.setIsConcurrent(true);
config.setIsShare(false);
config.setDynamicActiveTimeout(true);
config.setJwtSecretKey(properties.jwtSecret());
```

`app.security.token.jwt-secret` 的共享开发值直接写入并提交在两端 `application-dev.yml`，`APP_SA_TOKEN_JWT_SECRET` 可选覆盖；生产部署按目标环境覆盖开发值。注册器必须在启动时移除意外先创建的 generic（通用）`app` 逻辑、随后仅通过 `SaManager.putStpLogic` 注册独立逻辑；不能声明第二个 Spring `StpLogic` Bean。所有 app 会话组件受 `app.security.token.enabled=true` 约束，关闭时不得创建 properties、注册器、登录助手、个人工作区提供器、会话服务、修订守卫或 `app` 逻辑。

`dynamicActiveTimeout` 必须开启。`AppStpLogic` 必须覆盖活跃超时检查，并以 `SaTokenConsts.TOKEN_ACTIVE_TIMEOUT_CHECKED_KEY + getLoginType()` 保存请求内检查标记；同一请求即使默认 `login` 已完成检查，`app` 令牌仍必须独立校验和续期。登录前还必须从有效 `app_auth_client` 重新读取并校验状态、逻辑删除标记、`clientRevision` 与超时策略：`tokenTimeout > 0`、`activeTimeout > 0` 且 `activeTimeout <= tokenTimeout`；客户端不存在、停用、修订不一致或超时策略非法均统一返回 `46130`，不得先签发 app 令牌。

- [ ] **步骤 7（2–5 分钟）：实现非静态 AppLoginHelper 和修订守卫**

`AppLoginHelper` 只包装 `registrar.logic()`；session key 固定 `app:login-user:v1`，并提供当前 `AppPrincipalSnapshotDTO`。它在登录成功后写入 session 快照、同步发布 `AppSessionEstablishedEvent`，由会话服务写入在线索引；索引写入失败必须回滚刚创建的 app 登录，且不得影响默认 sys 登录。在线索引仅保存服务端不透明 `AppSessionTokenReference`，公开会话 DTO 不得含令牌原文。

`AppLoginHelper.login` 只能在重新查询到有效 `app_auth_client` 后调用 `SaLoginParameter`，超时值只能来自该事实源，不能信任 principal 传入值。`AppLoginHelper.logout` 只能注销当前 `app` 登录、清理 app 专属活跃超时请求标记，并发布只含随机 `sessionId` 的 `AppSessionEndedEvent`；不得触及默认 `StpUtil`。会话服务同步消费该事件，仅删除 `aivideo:app:online:<sessionId>` 在线索引。即使索引删除监听异常，app 令牌也必须已经注销，异常可以向上抛出供调用方记录。

每次受保护请求由 `AppSessionRevisionGuard` 比较用户的 `credential/identity/permission`、客户端的 `clientRevision` 和 workspace 的 `workspaceRevision/membershipRevision`。任一不等时，先以对应的 `AppSessionInvalidationReason` 调用 app-only `invalidateUserSessions` 清理 app 令牌和在线索引，再清理当前 app 请求状态并抛 `46131`；不得触及默认 `StpUtil`。

- [ ] **步骤 8（2–5 分钟）：冻结跨阶段会话失效原因**

```java
public enum AppSessionInvalidationReason {
    USER_DISABLED,
    CREDENTIAL_CHANGED,
    IDENTITY_CHANGED,
    PERMISSION_CHANGED,
    CLIENT_CHANGED,
    MEMBERSHIP_CHANGED,
    ORGANIZATION_DISABLED,
    ADMIN_KICKOUT
}
```

密码修改/找回/重置使用 `CREDENTIAL_CHANGED`，联系方式或社交身份变化使用 `IDENTITY_CHANGED`，角色权限变化使用 `PERMISSION_CHANGED`，客户端停用/换密使用 `CLIENT_CHANGED`。`MEMBERSHIP_CHANGED` 与 `ORGANIZATION_DISABLED` 由 P0-B 使用，P0-A 只定义且测试稳定枚举值。

`AppIdentityServiceImpl` 必须在身份事务内、审计记录成功后发布失效事件：修改密码和重置密码使用 `CREDENTIAL_CHANGED`；停用使用 `USER_DISABLED`；其他状态变化、社交身份绑定和解绑使用 `IDENTITY_CHANGED`。`AppSessionServiceImpl` 必须使用 `@TransactionalEventListener(AFTER_COMMIT)` 消费事件，因此外层事务回滚时不得撤销任何 app 会话；提交后只撤销 app 会话与在线索引，默认 sys 登录不得受影响。`AppActorAndScopeIT` 覆盖事件精确发布和回滚，`AppMutationIsolationIT` 覆盖真实 MySQL/Redis 下的提交后下线与 sys 隔离。

- [ ] **步骤 9（2–5 分钟）：实现固定 IAppSessionService**

```java
public interface IAppSessionService {
    PageResult<AppSessionSummaryDTO> page(AppSessionQueryDTO query);
    List<AppSessionSummaryDTO> currentUserSessions(long userId);
    void revokeSession(long actorUserId, String sessionId, AppActorContext actor, String reason);
    AppPrincipalSnapshotDTO replaceWorkspace(AppWorkspaceSessionSnapshotDTO workspace);
    void invalidateUserSessions(
        Long appUserId, AppSessionInvalidationReason reason);
    void invalidateOrganizationSessions(
        Long organizationId, AppSessionInvalidationReason reason);
}
```

`replaceWorkspace` 只替换当前 `app` token session 中 `AppPrincipalSnapshotDTO.workspace`，保留用户、客户端和四个 principal 修订，并返回替换后的完整 principal。P0-A 只把调用方提供的快照视为个人工作区选择器：仅接受 `workspaceType = "personal"`、`membershipRevision = null` 且 `workspaceKey` 与当前有效 `app_user` 的 HMAC 结果一致的输入；租户、所有者、计费主体、角色、权限和修订字段一律不得作为授权依据，必须从 app 用户事实源重建规范个人快照。组织快照、成员修订或错误 key 一律返回 `46126`。P0-B 不得把客户端提交的 `AppWorkspaceSessionSnapshotDTO` 直接传入此方法；接入组织后必须由服务端成员事实源与授权服务生成规范组织快照，并经受限内部边界切换。`invalidateOrganizationSessions` 不查询 P0-A 尚不存在的组织表，而是按 namespaced 在线索引中的 `workspaceType/ownerId` 撤销组织会话，供 P0-B 直接调用。客户端失效由 `AppSessionServiceImpl` 的包内事件处理方法按 `clientId` 完成，不增加跨阶段公共接口。

公开 DTO 只返回随机 `sessionId`、脱敏设备、客户端和最近活动，不返回 token。在线索引键固定 `aivideo:app:online:<sessionId>`；登录失败计数固定 `aivideo:app:login-fail:<clientId>:<identifierDigest>`，不得使用 sys 的 `CacheNames.ONLINE_TOKEN_KEY` 或密码错误键。`AppSessionServiceImpl` 必须以 `@TransactionalEventListener(AFTER_COMMIT)` 消费任务 5 的 `AppSessionInvalidationEvent`：角色权限变更提交后才使 app 会话失效，外层事务回滚时不得失效。

`Authorization:app:*` 是安全敏感会话命名空间，`PlusSaTokenDao` 的单键读取、对象读取和前缀搜索必须绕过进程内 Caffeine 缓存，保证另一节点撤销后立刻从 Redis 读取到失效结果；默认 `Authorization:login:*` 命名空间继续沿用既有缓存策略。集成测试必须以 Redisson 直接删除模拟另一 JVM，断言 app 的单键读取与 `searchData` 立即失效，同时证明 sys 缓存行为未改变。

- [ ] **步骤 10（2–5 分钟）：运行会话单元测试与真实 Redis/MySQL 集成测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppSessionModelTest,AppSessionComponentIsolationTest,AppStpLogicBehaviorTest,AppSessionRevisionGuardTest,AppSessionInvalidationListenerContractTest test
if ($LASTEXITCODE -ne 0) { throw 'Task6 会话单元和契约测试失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentityIsolationIT,AppActorAndScopeIT,AppSessionNamespaceIT,AppSessionRevisionIT,AppMutationIsolationIT,AppSessionWorkspaceInvalidationIT,AppSaTokenDaoNamespaceIT,AppSessionLogoutIT verify
if ($LASTEXITCODE -ne 0) { throw 'Task6 Redis/MySQL 会话集成测试失败' }
```

集成夹具必须使用真实 `PlusSaTokenDao`，且只在 `SpringUtils` 已绑定到存活的 shared（共享）RedissonClient 后安装；JSON（JavaScript 对象表示法）编解码应与生产 Redis 配置对齐。清理当前 `runId` 的 Redis 原始键后可以为默认 sys 测试隔离清理 `PlusSaTokenDao` 的 Caffeine 进程缓存，但 app 会话正确性不得依赖该清理：`Authorization:app:*` 必须直读本机 Redis。`SaTokenDaoDefaultImpl` 仅允许纯单元测试，禁止用于本机 MySQL/Redis 受控集成测试。

预期：默认 `StpUtil` 仍为 `login`；同 token 两个命名空间互不读取；sys 已检查活跃超时时 app 过期令牌仍被拒绝；四类修订均使 app 会话失效且清理在线索引，未变更修订仍可使用；app/sys 的退出、强踢、改密互不影响；正常 app 退出立即删除其在线索引；组织 A 的失效不影响组织 B、个人工作区和 sys 登录；另一 JVM 撤销 app 会话后本 JVM 的读取和搜索立即失效，而 sys 缓存策略不变。

- [ ] **步骤 11（2–5 分钟）：提交认证运行时**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ai-video-user-api/src/main/resources/application.yml'
  'ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/java/org/dromara/common/satoken/core/dao/PlusSaTokenDao.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppLoginUser.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppLoginHelper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppStpLogic.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppStpLogicRegistrar.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSaTokenProperties.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSessionRevisionGuard.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppPersonalWorkspaceSnapshotProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppSessionTokenReference.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppSessionService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionEstablishedEvent.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/event/AppSessionEndedEvent.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppIdentityServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppPrincipalSnapshotDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSessionQueryDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppSessionSummaryDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/AppWorkspaceSessionSnapshotDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionNamespaceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppMutationIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionWorkspaceInvalidationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionIntegrationTestFixture.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionInvalidationListenerContractTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSaTokenDaoNamespaceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionLogoutIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionModelTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionComponentIsolationTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppSessionRevisionGuardTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/security/AppStpLogicBehaviorTest.java'
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0a-identity-security.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 增加独立 app 登录与会话"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 7：实现 Header-only（仅请求头）解析、客户端策略和双拦截器顺序

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/StrictCredentialHeaders.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppCredentialIngressFilter.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppClientPolicyService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppAuthenticationInterceptor.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppSecurityConfig.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppSecurityExceptionHandler.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppAuthErrorCodes.java`
- 修改：`ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/resources/common-satoken.yml`
- 修改：`ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/config/SecurityConfig.java`
- 新建：`ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/filter/StrictHeaderCredentialFilter.java`
- 新建：`ai-video-api/ruoyi-common/ruoyi-common-security/src/test/java/org/dromara/common/security/filter/StrictHeaderCredentialFilterTest.java`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/StrictCredentialIngressTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/AppClientPolicyServiceTest.java`

- [ ] **步骤 1（2–5 分钟）：先写 S6、S7 的参数化失败测试**

至少覆盖这些 MockMvc 请求：

```java
static Stream<Arguments> invalidCredentials() {
    return Stream.of(
        Arguments.of(headers("Authorization", "Bearer app", "Bearer sys")),
        Arguments.of(headers("Authorization", "Bearer app,Bearer sys")),
        Arguments.of(headers("Authorization", "Bearer app").cookie(
            new Cookie("Authorization", "sys"))),
        Arguments.of(headers("Authorization", "Bearer app").queryParam(
            "Authorization", "Bearer sys")),
        Arguments.of(headers("clientid", "desktop", "admin-client"))
    );
}
```

断言所有响应在 `IAppIdentityService` 零调用时返回 `code = 46132`。

- [ ] **步骤 2（2–5 分钟）：运行并确认过滤器测试失败**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StrictCredentialIngressTest test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppClientPolicyServiceTest test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：缺少过滤器和客户端策略，测试编译失败。

- [ ] **步骤 3（2–5 分钟）：实现只读 Header 的原始凭据过滤器**

`AppCredentialIngressFilter` 对 `/api/**` 执行以下顺序：

1. 枚举所有 `Authorization` 和 `clientid` header 值；
2. 任一值数量大于 1、包含逗号、空白或格式不是单个 `Bearer <token>` 时拒绝；
3. 任一 Cookie 或 Query/Form 参数名为 `Authorization`、`token`、`clientid` 时拒绝；
4. 公共认证入口必须没有 Authorization，受保护入口必须恰好一个 Authorization；
5. 校验通过后只把不可变 `StrictCredentialHeaders` 放入 request attribute，不查用户。

- [ ] **步骤 4（2–5 分钟）：先把默认 sys 安全链也改成 Header-only**

`common-satoken.yml` 设置 `is-read-body: false` 和 `is-read-cookie: false`。`StrictHeaderCredentialFilter` 在解析默认 sys 身份前执行同样的重复头、逗号拼接、Cookie/Query 混合和多个 clientid 检查；它只检查原始输入，不导入 app 包。`SecurityConfig` 删除 `request.getParameter("clientid")` fallback，只允许 token 中 client ID 与唯一 `clientid` header 相等。

- [ ] **步骤 5（2–5 分钟）：运行默认 sys Header 过滤测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-common/ruoyi-common-security -am "-Dtest=StrictHeaderCredentialFilterTest" "-Dmaven.test.skip=false" "-DskipTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：合法单 Header 继续通过；Query/Cookie token、Query clientid、重复或拼接 Header 在 `StpUtil` 读取身份前拒绝。

- [ ] **步骤 6（2–5 分钟）：实现客户端唯一事实源和策略**

`AppClientPolicyService` 只用 `AppAuthClientMapper` 按 `client_key` 查询，校验 `status`、grant type、Ant 风格允许路径、IP/CIDR 白名单和 token 中冻结的 `clientId/clientRevision`。相同 key 的 `sys_client` 不得作为 fallback。所有不存在、停用、换密、路径和 IP 失败都映射 `46130`，响应不说明具体哪一项。

- [ ] **步骤 7（2–5 分钟）：按固定顺序注册两个拦截器**

`ai-video-user-api` 的 `security.excludes` 增加 `/api/**`，避免默认 sys `SecurityConfig` 处理创作路由。`AppSecurityConfig` 注册：

1. order `-200` 的 `AppAuthenticationInterceptor`：app 登录、客户端策略、修订守卫；
2. order `-100` 的 `new SaInterceptor()`：只执行 Controller 注解。

不得把两步合并进一个 `SaInterceptor` 回调，因为 Sa-Token 会先执行注解再执行回调，导致修订守卫晚于权限判断。

- [ ] **步骤 8（2–5 分钟）：锁定用户端公开路径**

公开路径精确为验证码申请、五种登录和密码找回；注册路径及注册验证码场景延期，不在本轮暴露。`me/password/logout/sessions/social-bindings` 均受保护。`must_change_password = true` 时只允许 `me/password/logout/sessions`。

- [ ] **步骤 9（2–5 分钟）：运行过滤器和客户端测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StrictCredentialIngressTest test
if ($LASTEXITCODE -ne 0) { throw '严格凭据入口单元测试失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppClientPolicyServiceTest test
if ($LASTEXITCODE -ne 0) { throw '创作客户端策略单元测试失败' }
```

预期：重复/混合凭据均为 `46132` 且身份服务零调用；交换 client key、grant、路径和 IP 均为 `46130`；正确 app client 通过。

- [ ] **步骤 10（2–5 分钟）：提交入口安全**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/StrictCredentialHeaders.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppCredentialIngressFilter.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppClientPolicyService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppAuthenticationInterceptor.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppSecurityConfig.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppSecurityExceptionHandler.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/security/AppAuthErrorCodes.java'
  'ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/resources/common-satoken.yml'
  'ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/config/SecurityConfig.java'
  'ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/filter/StrictHeaderCredentialFilter.java'
  'ai-video-api/ruoyi-common/ruoyi-common-security/src/test/java/org/dromara/common/security/filter/StrictHeaderCredentialFilterTest.java'
  'ai-video-api/ai-video-user-api/src/main/resources/application.yml'
  'ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/StrictCredentialIngressTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/AppClientPolicyServiceTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 强制单一请求头与客户端隔离"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 8：实现登录、找回、改密、退出和会话 API（应用编程接口）（注册延期）

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppVerificationCodeService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppVerificationCodeServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppVerificationDeliveryService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppExternalIdentityService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/provider/AppSmsVerificationProvider.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/provider/AppMailVerificationProvider.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppSmsVerificationDeliveryServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppMailVerificationDeliveryServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/provider/AppSocialIdentityProvider.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/provider/AppMiniProgramIdentityProvider.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppSocialExternalIdentityServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppMiniProgramExternalIdentityServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/identity/provider/AppExternalIdentityProviderTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/verification/provider/AppVerificationProviderTest.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/controller/AppAuthController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordLoginBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppVerificationCodeBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppCodeLoginBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialLoginBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppMiniProgramLoginBo.java`
- 延期：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppRegisterBo.java`（注册请求对象）
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordResetBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordChangeBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialBindingBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppLoginVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppMeVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppVerificationChallengeVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppSessionVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/service/IAppAuthApplicationService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/service/impl/AppAuthApplicationServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/auth/controller/AppAuthControllerTest.java`

**跨任务会话边界（任务 6 已冻结）：**

- 任务 6 的 `AppSessionRevisionGuard` 只是核心组件，尚未成为 HTTP（超文本传输协议）入口保护。任务 7 必须先将 app 路由排除出默认 sys `StpUtil` 安全链，再在 Controller 处理前以 `AppLoginHelper` / `StpLogic("app")` 完成 app 认证、客户端策略和修订守卫；此顺序必须早于 `SaInterceptor` 的权限注解检查。
- `AppLoginHelper.login` 只返回不含原始令牌的 `AppLoginUser`。本任务的登录接口虽然必须一次性返回 `access_token`，但不得给 `AppLoginUser`、`AppPrincipalSnapshotDTO`、`AppSessionSummaryDTO` 或 Redis 在线索引 DTO 增加令牌 getter（读取方法）。应在认证边界设计受限的签发结果，或由登录助手在同一受控调用内立即组装响应；业务层和会话查询层永远不可取得原始令牌。
- `AppSessionTokenReference` 当前只是不向 Java 调用方公开 getter；其 `tokenValue` 仍会随 Redis 在线索引序列化，不能描述为已加密的不透明值。任务 7/8 必须禁止它进入 HTTP 响应、日志、审计和公开 DTO；P0-B 在扩大在线会话查询或组织会话能力前，必须以受保护的服务端引用替代原始令牌持久化，并配置 Redis 最小权限访问控制和回归测试。
- 用户端真实 Spring 上下文必须显式启用 `app.security.token.enabled=true` 并注入两个密钥；不能依赖任务 6 的手工测试上下文。首次加载 `RedisUtils` 前，必须存在已绑定的 `SpringUtils` 和存活的 RedissonClient；测试类间不得关闭共享客户端。
- 登录在线索引或登录审计写入失败时，不得发放可用 app 令牌；补偿必须只注销 app 登录，绝不触碰默认 `StpUtil`。P0-A 只允许个人工作区，不得伪造组织或成员事实；动态活跃超时已由 `AppStpLogic` 的 app 专属请求标记实现并必须保持开启。P0-B 再提供组织事实源和受限的组织工作区切换边界。
- 当前本机受控集成夹具可用反射清理 `PlusSaTokenDao` 的 Caffeine 缓存以隔离默认 sys 测试；升级 RuoYi 或 `PlusSaTokenDao` 后必须复核该测试钩子。app 命名空间正确性不得依赖此清理，而应始终直读 Redis。
- 任务 8 的“我的会话”只能按当前 `AppLoginHelper.getLoginUser().userId()` 查询，不能接受任意用户编号；`page` 和运营端撤销只能在任务 9 的 sys 权限边界内调用。

- [ ] **步骤 1（2–5 分钟）：先写端点契约失败测试**

用 MockMvc 覆盖完整路径表，重点断言：

```java
mockMvc.perform(post("/api/auth/login")
        .header("clientid", "desktop")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"identifier":"missing","password":"wrong"}"""))
    .andExpect(status().isUnauthorized())
    .andExpect(jsonPath("$.code").value(46128))
    .andExpect(jsonPath("$.msg").value("账号或凭据不正确"));

mockMvc.perform(post("/api/auth/logout")
        .header("clientid", "desktop")
        .header("Authorization", "Bearer " + appToken))
    .andExpect(status().isOk());
assertThat(StpUtil.isLogin()).isTrue();
```

注册重试按 `Idempotency-Key` 只创建一个 app 用户的断言随注册功能延期；密码摘要和验证码/令牌原文从不出现在响应或日志表的安全要求仍须保留。

- [ ] **步骤 2（2–5 分钟）：运行并确认 Controller 不存在**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppAuthControllerTest test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：测试编译或 Spring 上下文因 `AppAuthController` 不存在而失败。

- [ ] **步骤 3（2–5 分钟）：实现验证码挑战而不泄露账号**

`IAppVerificationCodeService` 的 Redis key 固定 `aivideo:app:verification:<challengeId>`，只保存 HMAC 后的验证码、场景、渠道、目标摘要、客户端、10 分钟过期和最多 5 次尝试。响应只返回 `challengeId`、脱敏目标、`expiresIn = 600`；无论账号是否存在，申请找回验证码的 HTTP/R 结果和文案一致。

- [ ] **步骤 4（2–5 分钟）：实现短信、邮件、社交和小程序中性适配**

供应商 SDK（软件开发工具包）类型只允许出现在 `infra/**/provider`；四个 `service.impl` 薄门面实现 core 的 `IAppExternalIdentityService` / `IAppVerificationDeliveryService`，并显式保留原有 Spring Bean 名称。不得把 JustAuth、sms4j 或 Mail 原始类型泄漏到 core。

短信适配只调用 `SmsFactory.getSmsBlend(configId)`，邮件适配只调用 `MailBuilder.of()`；社交适配只用 `SocialUtils` 换取 `(provider, providerSubject)`，小程序适配只换取 `(wechat_mini_program, openId)`。四个适配器不导入 `org.dromara.system`，不查任何用户表。外部授权码、`state`（状态令牌）和验证码均一次性消费；未绑定第三方身份统一返回 `46128`，本阶段不隐式注册账号。

- [ ] **步骤 5（2–5 分钟）：实现统一认证应用服务**

`IAppAuthApplicationService` 分别处理 password/sms/email/social/mini-program，但最终都必须：

1. 只从 `IAppIdentityService` 获得 active app identity；
2. 只从 `IAppAuthClientService` 获得 app client；
3. 用 `AppPersonalWorkspaceSnapshotProvider` 构造个人 `AppWorkspaceSessionSnapshotDTO`，再组合四个 principal 修订、用户和客户端生成 `AppPrincipalSnapshotDTO` 与 `AppLoginUser`；
4. 经 `AppLoginHelper.login` 创建 `app` token；
5. 追加一条 `app_login_log`；
6. 成功返回 `access_token`、`expire_in`、`client_id`、脱敏用户和默认个人工作区。

- [ ] **步骤 6（2–5 分钟）：实现精确 Controller 映射**

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AppAuthController {
    private final IAppAuthApplicationService auth;
    private final AppLoginHelper loginHelper;

    @PostMapping("/login")
    public R<AppLoginVo> login(@Valid @RequestBody AppPasswordLoginBo body,
                               HttpServletRequest request) {
        return R.ok(auth.passwordLogin(body, StrictCredentialHeaders.from(request)));
    }

    @GetMapping("/me")
    public R<AppMeVo> me() {
        return R.ok(auth.me(loginHelper.getLoginUser().userId()));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        auth.logoutCurrent();
        return R.ok();
    }
}
```

`AppAuthController` 必须逐项实现下列非注册映射，不得省略或自行改名；注册行只保留后续契约，本轮不得实现或暴露：

| 方法与路径 | 控制器委托 |
| --- | --- |
| `POST /api/auth/verification-codes` | `auth.requestVerificationCode(...)` |
| `POST /api/auth/register` | 延期：后续 `auth.register(...)`，P0-A 不暴露 |
| `POST /api/auth/login` | `auth.passwordLogin(...)` |
| `POST /api/auth/sms-logins` | `auth.smsLogin(...)` |
| `POST /api/auth/email-logins` | `auth.emailLogin(...)` |
| `POST /api/auth/social-logins` | `auth.socialLogin(...)` |
| `POST /api/auth/mini-program-logins` | `auth.miniProgramLogin(...)` |
| `POST /api/auth/password-resets` | `auth.resetPassword(...)` |
| `GET /api/auth/me` | `auth.me(loginHelper.getLoginUser().userId())` |
| `PUT /api/auth/password` | `auth.changePassword(...)` |
| `POST /api/auth/logout` | `auth.logoutCurrent()` |
| `GET /api/auth/sessions` | `auth.listCurrentUserSessions()` |
| `DELETE /api/auth/sessions/{sessionId}` | `auth.revokeOwnSession(sessionId)` |
| `POST /api/auth/social-bindings` | `auth.bindSocialIdentity(...)` |
| `DELETE /api/auth/social-bindings/{socialIdentityId}` | `auth.unbindSocialIdentity(socialIdentityId)` |

公开路径仅包括验证码申请、五种登录和密码找回；注册路径及注册验证码场景延期。其余映射必须经过严格凭据请求头过滤、`app` 会话校验和修订守卫。所有请求体禁止出现 `userId`、`tenantId`、`ownerId` 或计费主体编号；这些值只能来自服务端登录上下文和工作区快照。

- [ ] **步骤 7（2–5 分钟）：实现密码与社交修订后的 app-only 失效**

改密、找回和运营重置在提交后调用 `invalidateUserSessions(appUserId, CREDENTIAL_CHANGED)`；停用调用 `USER_DISABLED`；联系方式或社交绑定变化调用 `IDENTITY_CHANGED`；角色变化调用 `PERMISSION_CHANGED`。社交解绑前先保证仍有至少一种可用登录方式。所有路径均不调用 `StpUtil`。

- [ ] **步骤 8（2–5 分钟）：运行认证、外部适配和敏感数据测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppExternalIdentityProviderTest test
if ($LASTEXITCODE -ne 0) { throw '外部身份适配测试失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AppAuthControllerTest test
if ($LASTEXITCODE -ne 0) { throw '创作端认证接口单元测试失败' }
```

预期：除注册外的五种登录、找回、me、改密、退出、会话撤销和社交绑定/解绑测试通过；未知账号和错误凭据响应不可区分。注册测试移交后续身份阶段。

- [ ] **步骤 9（2–5 分钟）：提交用户认证 API**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppVerificationCodeService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppVerificationCodeServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppVerificationDeliveryService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/IAppExternalIdentityService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/provider/AppSmsVerificationProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/provider/AppMailVerificationProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppSmsVerificationDeliveryServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/service/impl/AppMailVerificationDeliveryServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/provider/AppSocialIdentityProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/provider/AppMiniProgramIdentityProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppSocialExternalIdentityServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/service/impl/AppMiniProgramExternalIdentityServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/identity/provider/AppExternalIdentityProviderTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/verification/provider/AppVerificationProviderTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/controller/AppAuthController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordLoginBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppVerificationCodeBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppCodeLoginBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialLoginBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppMiniProgramLoginBo.java'
  # AppRegisterBo 随注册延期，不在 P0-A 暂存清单中。
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordResetBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppPasswordChangeBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/bo/AppSocialBindingBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppLoginVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppMeVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppVerificationChallengeVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/domain/vo/AppSessionVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/service/IAppAuthApplicationService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/auth/service/impl/AppAuthApplicationServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/auth/controller/AppAuthControllerTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 完成创作端认证与安全会话接口"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 9：实现运营端 `app`（创作端）用户、角色、客户端、会话和日志管理

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppUserAdminController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppRoleAdminController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppAuthClientAdminController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppSessionAdminController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppSecurityLogAdminController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/domain/bo/AppIdentityAdminBos.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/domain/vo/AppIdentityAdminVos.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/IAppIdentityAdminService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/impl/AppIdentityAdminServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/identity/AppIdentityAdminControllerIT.java`

- [ ] **步骤 1（2–5 分钟）：先写精确 sys 权限和禁止冒充测试**

测试分别使用无权限 sys token、有准确权限 sys token 和 app token，断言：

```java
mockMvc.perform(get("/api/admin/app-users").header("Authorization", bearer(sysNoPermission)))
    .andExpect(status().isForbidden());
mockMvc.perform(get("/api/admin/app-users").header("Authorization", bearer(sysAllowed)))
    .andExpect(status().isOk());
mockMvc.perform(get("/api/admin/app-users").header("Authorization", bearer(appToken)))
    .andExpect(status().isUnauthorized());
assertThat(allHandlerPaths()).doesNotContain(
    "/api/admin/app-users/{id}/impersonations",
    "/api/admin/app-users/{id}/tokens");
```

- [ ] **步骤 2（2–5 分钟）：运行并确认管理 Controller 不存在**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentityAdminControllerIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：测试编译失败，缺少平台身份 Controller。

- [ ] **步骤 3（2–5 分钟）：实现平台适配服务和 typed sys actor**

在 P0-A 独立交付阶段，只有 `AppIdentityAdminServiceImpl` 可以调用默认
`LoginHelper.getUserId()`，并立即转换为 `AppActorContext.sysUser(id)`；核心服务永远
不接收 sys `LoginUser`。P0-B 增加的唯一运营主体边界
`SysAuthorizationActorAdapter` 也只能执行同样的单步转换；P0-C 的
`SysTaskInitiatorResolver` 必须复用该 adapter，不能新增第三个默认登录助手调用点。
分页查询复用 `PageQuery/PageResult`，联系方式默认脱敏。

- [ ] **步骤 4（2–5 分钟）：实现用户与角色端点及权限**

按规格第 12.1 节实现 `/api/admin/app-users`、状态变更、密码重置、强踢、角色替换、`/api/admin/app-roles` 和 `/api/admin/app-permissions`。每个 Controller 使用默认 sys 权限注解，例如：

```java
@SaCheckPermission("aivideo:app-user:query")
@GetMapping("/api/admin/app-users")
public R<PageResult<AppUserAdminVo>> page(AppUserQueryBo query, PageQuery pageQuery) {
    return R.ok(service.pageUsers(query, pageQuery));
}
```

不得写 `type = "app"`，因为这些是运营入口。

停用、密码重置、角色替换、人工强踢在事务提交后分别调用 `invalidateUserSessions(appUserId, USER_DISABLED)`、`invalidateUserSessions(appUserId, CREDENTIAL_CHANGED)`、`invalidateUserSessions(appUserId, PERMISSION_CHANGED)`、`invalidateUserSessions(appUserId, ADMIN_KICKOUT)`。平台层不直接操作 Sa-Token key。

- [ ] **步骤 5（2–5 分钟）：实现客户端、会话、登录日志和安全审计端点**

实现第 12.1 节的 app client CRUD/换密、session page/revoke、login log page、security audit page。客户端停用、策略变化或换密发布带 `CLIENT_CHANGED` 的客户端范围失效事件，由 `AppSessionServiceImpl` 包内 listener 按 `clientId` 撤销 app 会话。新密码和新客户端密钥只在成功响应对象中出现一次，后续详情返回 `hasPassword/hasSecret` 布尔值，不返回摘要。

- [ ] **步骤 6（2–5 分钟）：保护敏感管理日志**

密码重置、客户端创建和换密方法如使用 `@Log`，必须设置 `isSaveRequestData = false`、`isSaveResponseData = false`；同时由核心审计服务写 `actorType = sys_user`、目标 app 资源、原因和摘要。禁止记录一次性明文。

- [ ] **步骤 7（2–5 分钟）：运行管理接口测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentityAdminControllerIT verify
if ($LASTEXITCODE -ne 0) { throw '创作身份运营接口测试失败' }
```

预期：权限不足为 403；准确 sys 权限可管理；app token 为 401；停用/重置/换密/换角色只撤销 app 会话；没有冒充或签发 app token 的 handler。

- [ ] **步骤 8（2–5 分钟）：提交运营管理 API**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppUserAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppRoleAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppAuthClientAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppSessionAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/AppSecurityLogAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/domain/bo/AppIdentityAdminBos.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/domain/vo/AppIdentityAdminVos.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/IAppIdentityAdminService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/impl/AppIdentityAdminServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/identity/AppIdentityAdminControllerIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity): 增加创作端身份运营管理接口"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 10：完成双启动装配和十类安全集成门禁

**文件：**

- 修改：`ai-video-api/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/pom.xml`
- 修改：`ai-video-api/ruoyi-admin/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java`
- 新建：`ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/bootstrap/PlatformStarterAssemblyIT.java`
- 新建：`ai-video-api/ai-video-integration-tests/pom.xml`
- 新建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterJarAssemblyIT.java`
- 新建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterProcessCleanupIT.java`
- 新建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualStarterHttpFixture.java`
- 新建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualTokenIsolationIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppPermissionTypeIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppMutationIsolationIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionNamespaceIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/StrictCredentialIngressTest.java`

**2026-07-30 实现对齐说明：**原计划中的 `AppClientPolicyIT` 与 `AppAuthControllerIT` 所覆盖的跨端 HTTP 场景，现统一收敛到外部双启动器测试 `DualTokenIsolationIT`；控制器和策略的单元测试保留为 `AppClientPolicyServiceTest` 与 `AppAuthControllerTest`。S6 客户端策略、S2 跨端令牌隔离和同号用户会话撤销均不得因本次收敛而减少覆盖。

**当前执行状态（2026-08-01 最终）：** `DualTokenIsolationIT`、core MySQL/Redis IT、两个 starter 装配类、平台 HTTP 装配类和进程回收类已在本机受控环境共同通过。精确 16 类报告共 70 tests，0 failures、0 errors、0 skipped；S2/S6 双向 Token、严格客户端绑定、同号账号撤销隔离均已有真实 HTTP 证据。P0-A 自动化代码门禁已闭环，实际发布仍须按分层整改设计规格在维护窗口一次性失效旧 App 会话并反向验证 sys 会话。

- [ ] **步骤 1（2–5 分钟）：先写两个启动上下文 Controller 清单测试**

用户上下文断言存在 `AppAuthController`，且 bean 类型名不包含 `SysUserController`、`SysRoleController`、`SysMenuController`、运营 `AuthController`；平台上下文断言存在五个 app 管理 Controller，且不存在 `AppAuthController`。

- [ ] **步骤 2（2–5 分钟）：写真实 HTTP 双向令牌测试**

在两个随机端口启动模块上分别获取一个有效 sys token 和 app token，覆盖：

- sys token 请求 `/api/auth/me`；
- app token 请求 `/api/admin/app-users` 与 `/system/user/list`；
- 交换 `clientid`；
- 两个 token 作为重复 Authorization；
- 原始 token 跨端互用；两种登录类型使用相同原始 token 字符串时的命名空间隔离由 `AppSessionNamespaceIT` 覆盖；
- 同号用户分别 logout、kickout、reset password。

所有跨端请求必须在不查询另一端用户是否存在的前提下返回统一 401 或 `46132/46130`。

- [ ] **步骤 3（2–5 分钟）：先运行外部启动器自检并修复任何漏装配**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd '-Pdev,external-http-it' -pl :ai-video-integration-tests -am '-Dit.test=ExternalStarterJarAssemblyIT,ExternalStarterProcessCleanupIT' verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：该命令只验证外部 starter JAR 装配和启动失败后的子进程回收；它会暴露扫描或依赖泄漏。根 POM 已实现 `local-integration-test`，但这两个不连接数据库/Redis 的装配自检仍只启用 `dev,external-http-it`，不能替代本机集成门禁。按测试只调整 POM、扫描范围和配置，不用 `@SaIgnore` 掩盖错误 Controller。真实双启动器 HTTP 验证由 `DualTokenIsolationIT` 在本机 MySQL/Redis 通过夹具安全校验后执行。

- [ ] **步骤 4（2–5 分钟）：运行 S1-S10 全套安全测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ai-video-user-api,ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StrictCredentialIngressTest test
if ($LASTEXITCODE -ne 0) { throw '聚合严格凭据测试失败' }
.\mvnw.cmd '-Pdev,local-integration-test,external-http-it' -pl :ai-video-user-api,:ruoyi-admin,:ai-video-integration-tests -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dit.test=AppIdentityIsolationIT,AppPermissionTypeIT,AppSessionRevisionIT,AppMutationIsolationIT,UserStarterAssemblyIT,PlatformStarterAssemblyIT,AppSessionNamespaceIT,AppSaTokenDaoNamespaceIT,AppSessionLogoutIT,AppSessionWorkspaceInvalidationIT,AppActorAndScopeIT,ExternalStarterJarAssemblyIT,ExternalStarterProcessCleanupIT,DualTokenIsolationIT' verify
if ($LASTEXITCODE -ne 0) { throw 'S1-S10 安全集成测试失败' }
```

预期：十类测试全部执行且为 0 failure/0 error；不能因 `maven.test.skip` 或 “no tests” 被跳过。

- [ ] **步骤 5（2–5 分钟）：运行禁止依赖静态扫描**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$forbidden = 'ai_user|userType|cn\.dev33\.satoken\.stp\.StpUtil|org\.dromara\.common\.satoken\.utils\.LoginHelper|org\.dromara\.system|@DataPermission|extends BaseEntity'
$forbiddenHits = @(rg -n $forbidden ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main)
$scanExitCode = $LASTEXITCODE
if ($scanExitCode -gt 1) { throw '禁止依赖扫描命令执行失败' }
if ($forbiddenHits) { $forbiddenHits; throw '发现禁止依赖' }
```

预期：零命中。本步骤是 P0-A 独立阶段门禁，此时 `ai-video-platform` 仅允许
`AppIdentityAdminServiceImpl` 命中默认 `LoginHelper`，并立即构造
`AppActorContext.sysUser`。累计交付到 P0-B/P0-C 后，最终精确白名单只能是
`AppIdentityAdminServiceImpl` 与 `SysAuthorizationActorAdapter`；
`SysTaskInitiatorResolver` 只依赖后者，不得直接命中默认 `LoginHelper`。

- [ ] **步骤 6（2–5 分钟）：提交安全门禁**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-api/pom.xml'
  'ai-video-api/ai-video-user-api/pom.xml'
  'ai-video-api/ruoyi-admin/pom.xml'
  'ai-video-api/ai-video-integration-tests/pom.xml'
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java'
  'ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/bootstrap/PlatformStarterAssemblyIT.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterJarAssemblyIT.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/ExternalStarterProcessCleanupIT.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualStarterHttpFixture.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualTokenIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppIdentityIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppPermissionTypeIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionRevisionIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppMutationIsolationIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionNamespaceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppActorAndScopeIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/security/StrictCredentialIngressTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "test(identity): 锁定双端身份安全边界"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 11：重建用户端请求适配器与认证状态

**文件：**

- 修改：`ai-video-ui/ai-video-webapp/config/config.ts`
- 修改：`ai-video-ui/ai-video-webapp/config/proxy.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts`
- 修改：`ai-video-ui/ai-video-webapp/vitest.config.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/session.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts`

- [ ] **步骤 1（2–5 分钟）：先写请求适配器失败测试**

```ts
it('只由集中层附加一个 app token 和一个 clientid', async () => {
  authSession.save({ accessToken: 'app-token', persistent: false });
  await authApi.me();
  expect(requestSpy).toHaveBeenCalledWith(
    '/api/auth/me',
    expect.objectContaining({
      headers: expect.objectContaining({
        Authorization: 'Bearer app-token',
        clientid: 'desktop-web',
      }),
    }),
  );
});

it('401 只清理并跳转一次，403 不跳登录', async () => {
  await expect(normalizeFailure({ code: 401 })).rejects.toMatchObject({ code: 401 });
  await expect(normalizeFailure({ code: 403 })).rejects.toMatchObject({ code: 403 });
  expect(loginRedirectSpy).toHaveBeenCalledTimes(1);
});
```

- [ ] **步骤 2（2–5 分钟）：运行并确认仍使用演示协议**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
npm.cmd test -- src/services/ai-video/auth/api.test.ts
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：测试因 `services/ai-video` 不存在，或当前 `success/errorCode/errorMessage` 演示协议不符合 `R<T>` 而失败。

- [ ] **步骤 3（2–5 分钟）：实现 RuoYi 响应包装和安全请求头适配器**

`core/types.ts` 定义 `R<T> = { code: number; msg: string; data: T }` 和 `PageResult<T>`；`core/errors.ts` 定义 `ApiError`；`core/ruoyiAdapter.ts` 提供 `requestR<T>`。调用方不能传 `Authorization/clientid`；适配器从 `auth/session.ts` 和 `APP_AUTH_CLIENT_ID` 生成唯一请求头，并加入当前语言。业务编号全部为字符串。

```ts
export interface AiVideoRequestOptions extends RequestOptionsInit {
  authenticated?: boolean;
}

export async function requestR<T>(
  url: string,
  options: AiVideoRequestOptions = {},
): Promise<T> {
  const headers = buildAppHeaders(options.authenticated !== false);
  const response = await request<R<T>>(url, {
    ...options,
    headers: mergeAllowedHeaders(headers, options.headers),
    skipErrorHandler: true,
  });
  return unwrapR(response);
}
```

`mergeAllowedHeaders` 接受业务幂等键和内容类型，但发现调用方传入 `Authorization` 或 `clientid` 时立即拒绝，避免页面覆盖集中认证头。

- [ ] **步骤 4（2–5 分钟）：替换演示 baseURL 与错误处理**

`app.tsx` 使用 `APP_API_BASE_URL`，`requestErrorConfig.ts` 删除 `success/errorCode/showType/any`。HTTP 或业务 401 原子清理 token 并只跳一次 `/user/login`；403 抛标准 `ApiError` 给页面显示权限状态；取消请求不 toast。

- [ ] **步骤 5（2–5 分钟）：实现身份 service 的准确方法**

```ts
export const authApi = {
  login: (input: PasswordLoginInput) =>
    requestR<AppLogin>('/api/auth/login', {
      method: 'POST', data: input, authenticated: false,
    }),
  // 注册适配器随注册延期；P0-A 的 authApi 不暴露 register。
  me: () => requestR<AppMe>('/api/auth/me'),
  changePassword: (input: ChangePasswordInput) =>
    requestR<void>('/api/auth/password', { method: 'PUT', data: input }),
  logout: () => requestR<void>('/api/auth/logout', { method: 'POST' }),
  sessions: () => requestR<AppSession[]>('/api/auth/sessions'),
  revokeSession: (sessionId: string) =>
    requestR<void>(
      `/api/auth/sessions/${encodeURIComponent(sessionId)}`,
      { method: 'DELETE' },
    ),
};
```

- [ ] **步骤 6（2–5 分钟）：让手写 service 进入测试和覆盖率**

移除 `vitest.config.ts` 对旧 login 和 `src/services/ant-design-pro/**` 的身份相关排除；`passWithNoTests` 改为 `false`。删除旧快照测试，后续使用 Testing Library 行为测试。

- [ ] **步骤 7（2–5 分钟）：运行适配器测试、类型检查和代码检查**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
npm.cmd test -- src/services/ai-video/auth/api.test.ts
if ($LASTEXITCODE -ne 0) { throw '创作端认证适配器测试失败' }
npm.cmd run tsc
if ($LASTEXITCODE -ne 0) { throw '创作端类型检查失败' }
npm.cmd run biome:lint
if ($LASTEXITCODE -ne 0) { throw '创作端代码检查失败' }
```

预期：适配器测试通过；无 `any`、无页面散写 `Authorization`（认证头）/`clientid`（客户端键）、无演示协议。

- [ ] **步骤 8（2–5 分钟）：提交用户端请求适配器**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-ui/ai-video-webapp/config/config.ts'
  'ai-video-ui/ai-video-webapp/config/proxy.ts'
  'ai-video-ui/ai-video-webapp/src/app.tsx'
  'ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts'
  'ai-video-ui/ai-video-webapp/vitest.config.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/auth/session.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "refactor(identity-ui): 接入创作端认证协议"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 12：实现用户登录、找回和安全会话页面（注册延期）

**文件：**

- 修改：`ai-video-ui/ai-video-webapp/config/routes.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`
- 延期：`ai-video-ui/ai-video-webapp/src/pages/user/register/index.tsx`（注册页）
- 延期：`ai-video-ui/ai-video-webapp/src/pages/user/register/index.test.tsx`（注册页测试）
- 新建：`ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.test.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/user/security/index.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/user/security/index.test.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/locales/zh-CN/identity.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/locales/en-US/identity.ts`

- [ ] **步骤 1（2–5 分钟）：先写四页用户可观察行为测试**

登录页至少覆盖密码、短信、邮件 tab；统一错误只显示“账号或凭据不正确”；提交中按钮 disabled；客户端不可用显示固定 Alert。注册页的验证码倒计时和重复提交测试延期。找回页成功后跳登录。安全页覆盖加载、空、失败、当前会话标记、撤销确认和改密后清理登录态。

- [ ] **步骤 2（2–5 分钟）：运行并确认新页面测试失败**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
npm.cmd test -- src/pages/user/login/index.test.tsx src/pages/user/password-reset/index.test.tsx src/pages/user/security/index.test.tsx
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：新路由和页面不存在，测试失败。

- [ ] **步骤 3（2–5 分钟）：更新路由与初始登录恢复**

匿名路由固定 `/user/login`、`/user/password-reset`；`/user/register` 随注册延期。受保护路由 `/user/security`。`getInitialState` 只通过 `authApi.me()` 恢复 `AppMe`，不再调用 `services/ant-design-pro/currentUser`。`redirect`（回跳地址）继续只允许同源相对路径。

- [ ] **步骤 4（2–5 分钟）：实现登录/找回表单（注册延期）**

使用已按 Ant Design CLI 6.5.1 核验的 `Form`、`Tabs.items`、`Alert.title/type/showIcon` API，以及现有 ProComponents `LoginForm/ProFormText/ProFormCaptcha` 模式。页面只调用 `authApi`；验证码倒计时由组件状态驱动并在卸载时清理计时器。错误分支只按数字代码，不按中文消息。注册表单不在本轮实现。

- [ ] **步骤 5（2–5 分钟）：实现安全会话页**

用 `Descriptions` 展示脱敏账号与修订状态，用 `ProList` 或 `List` 展示会话；撤销前 `Modal.confirm`，服务成功后 invalidate `['app-sessions']`。页面没有 token 原文、sys 角色或“切换为管理员”入口。

- [ ] **步骤 6（2–5 分钟）：补齐加载、空、权限和失败状态**

四页都必须有 skeleton/spin、空数据、网络失败重试、401 跳转、403 Result、提交中、成功反馈；状态不能只靠颜色。所有输入有 label/可访问名称，焦点错误回到首个无效字段。

- [ ] **步骤 7（2–5 分钟）：运行行为、Ant Design、类型和构建验证**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
npm.cmd test -- src/pages/user src/services/ai-video
if ($LASTEXITCODE -ne 0) { throw '创作端身份页面行为测试失败' }
.\node_modules\.bin\antd.cmd lint .\src\pages\user --format json
if ($LASTEXITCODE -ne 0) { throw 'Ant Design 组件用法检查失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '创作端代码检查失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '创作端生产构建失败' }
```

预期：测试非零数量且全部通过；Ant Design lint 无 error；类型、Biome 和生产构建通过。

- [ ] **步骤 8（2–5 分钟）：提交用户身份页面**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-ui/ai-video-webapp/config/routes.ts'
  'ai-video-ui/ai-video-webapp/src/app.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx'
  # 注册页与其测试随注册延期，不在 P0-A 暂存清单中。
  'ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/user/password-reset/index.test.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/user/security/index.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/user/security/index.test.tsx'
  'ai-video-ui/ai-video-webapp/src/locales/zh-CN/identity.ts'
  'ai-video-ui/ai-video-webapp/src/locales/en-US/identity.ts'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity-ui): 完成用户认证与安全会话页面"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 13：实现运营端独立 `app`（创作端）身份管理页面

**文件：**

- 修改：`ai-video-ui/ai-video-platform-ui/package.json`
- 修改：`ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml`
- 新建：`ai-video-ui/ai-video-platform-ui/vitest.config.ts`
- 新建：`ai-video-ui/ai-video-platform-ui/tests/setupTests.ts`
- 新建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/types.ts`
- 新建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.ts`
- 新建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.test.ts`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.test.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserFormModal.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserSecurityDrawer.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-role/index.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-auth-client/index.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-session/index.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-login-log/index.tsx`
- 新建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-security-audit/index.tsx`

- [ ] **步骤 1（2–5 分钟）：先建立平台 Vitest 门禁**

用 pnpm 增加 `vitest@4.1.10`、`@testing-library/react@16.3.2`、`@testing-library/jest-dom@6.9.1`、`happy-dom@20.10.6`，增加 `"test": "vitest run"`，并创建 `vitest.config.ts` 与 `tests/setupTests.ts`；只更新 `pnpm-lock.yaml`。

- [ ] **步骤 2（2–5 分钟）：先写 API 与用户管理页失败测试**

API（应用程序接口）测试断言分页适配器只访问 `/api/admin/app-*`；页面测试断言无准确 `sys`（运营端）权限时隐藏操作但仍由后端兜底，并断言 DOM（页面文档结构）不含“冒充”“以用户身份登录”“签发令牌”。

- [ ] **步骤 3（2–5 分钟）：运行并确认页面不存在**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
pnpm.cmd test -- src/api/aivideo/identity/index.test.ts src/pages/aivideo/app-user/index.test.tsx
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：测试因 API 和页面不存在而失败。

- [ ] **步骤 4（2–5 分钟）：实现集中 API 与 string ID 类型**

`src/api/aivideo/identity/index.ts` 精确封装规格第 12.1 节 P0-A 接口，复用现有 `request<R<T>>`、`PageResult`、`toPageQuery/toTableData`；页面不拼 URL、不读取 `rows`。所有 user/role/client/session/audit ID 为 string。

- [ ] **步骤 5（2–5 分钟）：实现六个 ProComponents 管理页**

用户、角色、客户端、会话、登录日志、安全审计均使用现有 `PageContainer + ProTable` 模式，具备筛选、分页、loading、空、失败、权限不足。用户表提供创建、详情、编辑、启停、一次性密码、强踢、角色分配；角色表提供权限替换并携带 `expectedRoleRevision`；客户端表提供一次性密钥和换密；会话只提供精确撤销。

- [ ] **步骤 6（2–5 分钟）：实现一次性敏感值展示**

创建用户/重置密码/创建客户端/换密的明文只存在当前 Modal state；关闭后立即清空，刷新和详情不能恢复。Modal 明确提示“关闭后不可再次查看”，不得把值写入 URL、localStorage、日志或表格行。

- [ ] **步骤 7（2–5 分钟）：验证权限、测试、lint 和构建**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
pnpm.cmd test
if ($LASTEXITCODE -ne 0) { throw '运营端身份页面测试失败' }
pnpm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '运营端代码检查失败' }
pnpm.cmd run build:prod
if ($LASTEXITCODE -ne 0) { throw '运营端生产构建失败' }
```

预期：测试实际执行且全部通过；Oxlint/TypeScript 和生产构建通过；动态 sys 菜单能解析 SQL 中的六个组件路径。

- [ ] **步骤 8（2–5 分钟）：提交运营身份页面**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$expected = @(
  'ai-video-ui/ai-video-platform-ui/package.json'
  'ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml'
  'ai-video-ui/ai-video-platform-ui/vitest.config.ts'
  'ai-video-ui/ai-video-platform-ui/tests/setupTests.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/identity/index.test.ts'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserFormModal.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-user/components/AppUserSecurityDrawer.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-role/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-auth-client/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-session/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-login-log/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/app-security-audit/index.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat(identity-ui): 增加创作端身份运营管理页"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 14：P0-A 独立部署验收与最终文档校验

**文件：**

- 验证/按失败定向修改：`docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform`
- 验证/按失败定向修改：`ai-video-api/ai-video-user-api`
- 验证/按失败定向修改：`ai-video-api/ruoyi-admin`
- 验证/按失败定向修改：`ai-video-ui/ai-video-webapp`
- 验证/按失败定向修改：`ai-video-ui/ai-video-platform-ui`

- [ ] **步骤 1（2–5 分钟）：从空库启动 MySQL/Redis 并执行迁移两次**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=AppIdentitySchemaIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：两次迁移均成功，种子数量不变。

- [ ] **步骤 2（2–5 分钟）：运行后端全 reactor 测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$unitGateStartedAt = Get-Date
.\mvnw.cmd -pl ai-video-user-api,ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$unitReports = @(Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' |
  Where-Object {
    $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' -and
    $_.LastWriteTime -ge $unitGateStartedAt
  })
if (-not $unitReports) { throw '本次单元测试未产生 Surefire XML' }
$unitTotals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
foreach ($report in $unitReports) {
  [xml]$suiteXml = Get-Content -LiteralPath $report.FullName
  $unitTotals.tests += [int]$suiteXml.testsuite.tests
  $unitTotals.failures += [int]$suiteXml.testsuite.failures
  $unitTotals.errors += [int]$suiteXml.testsuite.errors
  $unitTotals.skipped += [int]$suiteXml.testsuite.skipped
}
if ($unitTotals.tests -le 0 -or
    $unitTotals.failures -ne 0 -or
    $unitTotals.errors -ne 0 -or
    $unitTotals.skipped -ge $unitTotals.tests) {
  throw "Surefire 门禁失败：$($unitTotals | ConvertTo-Json -Compress)"
}

$expectedItClasses = @(
  'AppIdentitySchemaIT'
  'AppIdentityIsolationIT'
  'AppPermissionTypeIT'
  'AppSessionRevisionIT'
  'AppMutationIsolationIT'
  'AppIdentityAdminControllerIT'
  'UserStarterAssemblyIT'
  'PlatformStarterAssemblyIT'
  'AppSessionNamespaceIT'
  'AppSaTokenDaoNamespaceIT'
  'AppSessionLogoutIT'
  'AppSessionWorkspaceInvalidationIT'
  'AppActorAndScopeIT'
  'ExternalStarterJarAssemblyIT'
  'ExternalStarterProcessCleanupIT'
  'DualTokenIsolationIT'
)
$itGateStartedAt = Get-Date
$itSelector = $expectedItClasses -join ','
.\mvnw.cmd '-Pdev,local-integration-test,external-http-it' -pl :ai-video-user-api,:ruoyi-admin,:ai-video-integration-tests -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' "-Dit.test=$itSelector" verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itReports = @(Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' |
  Where-Object {
    $_.FullName -match '[\\/]target(?:-http-it)?[\\/]failsafe-reports(?:-external-http-it)?[\\/]' -and
    $_.LastWriteTime -ge $itGateStartedAt
  })
foreach ($className in $expectedItClasses) {
  $classReports = @($itReports |
    Where-Object { $_.Name -like "TEST-*$className.xml" })
  if (-not $classReports) {
    throw "$className 未产生本次 Failsafe XML"
  }
  $totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
  foreach ($report in $classReports) {
    [xml]$suiteXml = Get-Content -LiteralPath $report.FullName
    $totals.tests += [int]$suiteXml.testsuite.tests
    $totals.failures += [int]$suiteXml.testsuite.failures
    $totals.errors += [int]$suiteXml.testsuite.errors
    $totals.skipped += [int]$suiteXml.testsuite.skipped
  }
  if ($totals.tests -le 0 -or
      $totals.failures -ne 0 -or
      $totals.errors -ne 0 -or
      $totals.skipped -ge $totals.tests) {
    throw "$className Failsafe 门禁失败：$($totals | ConvertTo-Json -Compress)"
  }
}
```

预期：两条命令均为 0 failure（零失败）、0 error（零错误）；本次 Surefire 总执行数大于 0，16 个准确 Failsafe 目标类逐个有本次报告、执行数大于 0 且不能全 skipped（跳过）。外部 HTTP 测试模块对“未选中任何目标测试”强制失败；`-am` 带入的上游模块仍保持各自默认跳过策略。

- [ ] **步骤 3（2–5 分钟）：运行两端前端测试、静态检查和构建**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
npm.cmd test
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
pnpm.cmd test
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
pnpm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
pnpm.cmd run build:prod
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：两个前端包都执行非零测试，类型检查和生产构建通过。

- [ ] **步骤 4（2–5 分钟）：手工执行最小独立验收**

按以下顺序使用两个独立客户端：

1. 使用迁移或测试夹具预置的创作端账号登录并获得 `app` token；用户注册不在本轮验收范围；
2. 查询 `/api/auth/me` 与 `/api/auth/sessions`；
3. 用该 token 请求 `/api/admin/app-users`，确认 401；
4. 运营端登录后查询/停用/启用该 app 用户、分配测试角色、重置密码、查看 app 登录日志和安全审计；
5. 用 sys token 请求 `/api/auth/me`，确认 401；
6. 确认停用、角色变更、重置密码和客户端换密均只撤销 app 会话；
7. 确认页面和响应从未出现密码摘要、客户端密钥摘要或 token 原文。

- [ ] **步骤 5（2–5 分钟）：运行禁止项与规范自检**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$forbidden = 'ai_user|userType|cn\.dev33\.satoken\.stp\.StpUtil|org\.dromara\.common\.satoken\.utils\.LoginHelper|org\.dromara\.system|@DataPermission|extends BaseEntity'
$forbiddenHits = @(rg -n $forbidden ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main)
$scanExitCode = $LASTEXITCODE
if ($scanExitCode -gt 1) { throw '禁止依赖扫描命令执行失败' }
if ($forbiddenHits) { $forbiddenHits; throw '发现禁止依赖' }
powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '工作树差异检查失败' }
```

预期：禁止项零命中；开发规范校验和 `git diff --check` 退出码均为 0。

- [ ] **步骤 6（2–5 分钟）：确认阶段门禁**

确认 `P0-A` 可在没有 `app_organization/app_org_member`、额度、知识、文案表的环境启动和验收；若依赖任何后续阶段才可登录、管理或审计，则不得宣告完成。

若门禁审查产生修正，必须回到产生问题的任务，重新执行该任务的红灯、绿灯和验证步骤，并使用该任务已经列出的精确 `git add` 清单提交；禁止在此处用项目根目录或大目录执行补漏提交。没有修正时不创建空提交。

## 完成定义

- 两个启动模块可独立启动；用户端只暴露创作认证和未来用户业务入口，运营端只暴露 sys 认证与 app 资源管理入口。
- 本轮要求既有或测试夹具预置账号的密码、短信、邮件、第三方和小程序五种登录，登录/找回验证码申请、密码找回、`me`、改密、退出、本人会话查询与撤销、第三方绑定与解绑接口可用；未知账号与错误凭据不可区分。用户注册、注册验证码场景、注册页面与人工注册验收移交后续身份阶段；其余已冻结的认证契约不因本次注册延期而删除。
- `AppLoginHelper` 全链路只访问 `StpLogic("app")`；默认 `StpUtil/LoginHelper/SaPermissionImpl` 仍只服务运营端。
- `identity.domain` 与 `identity.dto` 中对应类型可被 P0-B 直接依赖；`AppWorkspaceSessionSnapshotDTO` 保持固定 11 字段，P0-A 只生产个人默认快照。
- `IAppSessionService` 同时保留分页、本人会话、精确撤销，并提供固定 `replaceWorkspace/invalidateUserSessions/invalidateOrganizationSessions`；安全审计只经 `append(AppSecurityAuditDTO)` 写入。
- 受保护 app 请求每次校验凭据、身份、权限、客户端和个人工作区修订；对应变更只撤销 app 会话。
- 用户 Controller 上的业务权限注解全部显式 `type = "app"`；权限只来自 `app_role_permission/app_permission`。
- Header-only、客户端路径/IP、双向 token、同 raw token Redis 命名空间、typed actor 和启动装配门禁全部有自动化证据。
- 运营端可以按准确 sys 权限管理 app 用户、角色、权限、客户端、会话、登录日志和安全审计，但不存在冒充或签发 app token 的接口与按钮。
- 两端页面覆盖加载、空、失败、权限、提交中和成功反馈；敏感明文只显示一次且不持久化。
- 十类安全测试全部执行并通过；旧 `ai_user + userType + 默认 StpUtil` 文档已明确废止，生产代码零命中。
