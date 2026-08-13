# “创作—说需求”P0-B 工作区与组织授权实现计划

> **2026-08-02 可执行分层基线：** 本计划已按 RuoYi 分层整改。工作区业务聚合只使用 `domain`、与其平级的 `dto`、`mapper`、`service/I...Service`、`service.impl/...ServiceImpl`；端侧 HTTP 模块另使用 `domain.bo`、`domain.vo`、`controller`，端侧登录主体转换器只放在直接 `security` 边界。AI 视频业务专属的稳定跨模块 DTO 归 `ai-video-core` 对应聚合的 `dto` 包，不得迁入全局 `ruoyi-api`。禁止以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 贫血 Entity（实体）加 Service（业务服务）编排。

> **面向 AI（人工智能）代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（子代理驱动开发，推荐）或 superpowers:executing-plans（分批执行计划）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在已通过门禁的 P0-A 独立创作端账号与会话之上，交付个人/组织工作区、组织成员与角色、工作区切换、对象动作授权、SQL 数据范围及修订失效的完整闭环，不实现任何登录、注册、令牌解析或身份表能力。

**架构：** `ai-video-core` 的 `authorization` 聚合只持有组织、成员、工作区和对象授权事实，并以 `IWorkspaceAuthorizationService` 作为下游唯一稳定授权入口；内部管理与资源归属编排分别由 `IOrganizationAdminService`、`IResourceOwnershipService` 承担。角色、权限、角色权限映射和个人用户角色继续由 P0-A 的 `identity` 聚合与三张身份表唯一持有。用户端与运营端控制器分别放在 `ai-video-user` 和 `ai-video-platform`。每次受保护请求先调用现行 `AppSessionRevisionGuard.checkCurrentSession()`，再解析工作区和组织成员修订，然后通过 P0-A `IAppPermissionService` 与 `identity.mapper` 读取角色权限，按工作区权限、资源所有权／隐式范围／对象授权的固定顺序判定；列表范围直接进入 Mapper SQL。

**技术栈：** Java 21（后端编程语言）、Spring Boot 4.1.0（Java 应用框架）、RuoYi-Vue-Plus 6.0.0-BETA（若依增强版测试版本）、MyBatis-Plus（数据访问增强工具）、Sa-Token（认证授权框架）`app` 账号类型、MySQL 8（关系型数据库）、Redis 7（缓存数据库）、JUnit 5（Java 测试框架）、Mockito（模拟测试框架）、本机受控集成测试（直接连接本机 MySQL/Redis，不使用容器或虚拟化环境）、React 19（前端视图库）、Umi Max 4（前端应用框架）、Ant Design 6（蚂蚁设计组件库）、React Query 5（服务端状态查询库）、Vitest（前端单元测试）。

**阅读约定：** 正文中的 API（应用编程接口）、SQL（结构化查询语言）、DDL（数据定义语言）、IT（集成测试）、Mapper（数据映射器）、Token（令牌）等英文术语首次出现时附中文含义；反引号中的类名、字段名、文件名、命令和接口路径是必须原样使用的程序标识符，不翻译其拼写，但由相邻中文解释其用途。

**本机 IT 命令约定：** 本计划中的每条 `*IT`（集成测试）Maven 命令必须带 `'-Pdev,local-integration-test'`。全部集成测试复用 P0-A 的 `LocalIntegrationEnvironment`（本机受控集成环境夹具）；它默认读取用户端 `application-dev.yml` 的标准数据源和 Redis 配置，固定派生本机 `ai_video_test` 与 Redis 隔离逻辑库／当前运行前缀，`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖；任何缺失或不安全配置均立即失败，不得使用容器、虚拟化环境或非测试数据源。每次精确 GREEN／最终门禁先删除目标 Surefire／Failsafe XML（单元／集成测试报告），记录 UTC 开始时间，再断言本次报告 `tests > 0`、`failures = 0`、`errors = 0`、`skipped = 0`。

---

## 规格来源、前置门禁与排除项

- 唯一业务规格：`docs\superpowers\specs\2026-07-28-say-requirements-copy-generation-design.md`，重点执行第 2.4、6.1、8.1、10.2、10.5、10.6、11.1、11.2、11.5、12.1、12.2、17.1、17.2 和 19 节。
- 前置实施包：P0-A 已在 `main` 落地；F0 必须引用其双向令牌隔离、会话修订、运营审计和本机集成测试的最终证据，不重复实施身份代码。
- 本包消费下列 P0-A 现行类型与服务；只允许按本节声明最小扩展 `AppSessionServiceImpl` 的组织快照规范化，不修改公开签名、P0-A 表结构或身份事实归属：
  - `org.dromara.aivideo.identity.service.IAppIdentityService`
  - `org.dromara.aivideo.identity.service.IAppSessionService`
  - `org.dromara.aivideo.identity.service.IAppPermissionService`
  - `org.dromara.aivideo.identity.service.IAppSecurityAuditService`
  - `org.dromara.aivideo.identity.security.AppLoginHelper`
  - `org.dromara.aivideo.identity.security.AppActorContext`
  - `org.dromara.aivideo.identity.security.AppSessionRevisionGuard`
  - `org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO`
  - `org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO`
  - `org.dromara.aivideo.identity.domain.AppSessionInvalidationReason`
  - `org.dromara.aivideo.identity.dto.AppSecurityAuditDTO`
- 已按 P0-A 现行源码精确核对：公开角色权限服务名是 `org.dromara.aivideo.identity.service.IAppPermissionService`。固定公开方法为 `roleCodes(long userId)`、`permissionCodes(long userId)`、`replaceUserRoles(...)` 和 `replaceRolePermissions(...)`；P0-B 不创建同义权限服务。
- 组织成员角色是 `app_org_member.role_code` 的组织内事实，不能塞入无组织维度的 `app_user_role`。P0-B 对个人角色／权限只调用 `IAppPermissionService`；对组织角色代码到权限代码的只读解析，复用 P0-A 已创建的 `org.dromara.aivideo.identity.mapper.AppRoleMapper`、`AppRolePermissionMapper`、`AppPermissionMapper` 及 `identity.domain` 实体。不得增加同义服务、同名实体、同名 Mapper 或第二份 XML。
- `AppWorkspaceSessionSnapshotDTO` 的字段固定为 `workspaceKey, workspaceType, tenantId, ownerType, ownerId, billingSubjectType, billingSubjectId, roleCode, permissions, workspaceRevision, membershipRevision`。
- `IAppSessionService` 的 P0-B 可调用方法固定为：
  - `AppPrincipalSnapshotDTO replaceWorkspace(AppWorkspaceSessionSnapshotDTO workspace)`
  - `void invalidateUserSessions(Long appUserId, AppSessionInvalidationReason reason)`
  - `void invalidateOrganizationSessions(Long organizationId, AppSessionInvalidationReason reason)`
- `IAppSecurityAuditService` 的写入口固定为 `void append(AppSecurityAuditDTO command)`；`AppSecurityAuditDTO` 恰好八个字段且顺序固定为 `resourceType`、`resourceId`、`action`、`actorType`、`actorId`、`beforeDigest`、`afterDigest`、`reason`，不得增加请求体追踪字段。
- `AppSessionRevisionGuard.checkCurrentSession()` 与 `AppLoginHelper.getPrincipal()` 是现行修订校验／身份读取入口；每个受保护请求必须先校验已提交修订，再读取 principal，不得复活已移除的旧方法。
- Java 内部编号使用 `Long`，HTTP 和 TypeScript 边界使用 `string`；修订号在 Java 内部使用 `Long`。
- P0-A 独占 `app_user`、`app_auth_client`、`app_social_identity`、`app_permission`、`app_role`、`app_role_permission`、`app_user_role`、`app_login_log`、`app_security_audit` 的 DDL（数据定义语言）和 Java 持久化类型。P0-B 不创建、不修改这九张身份表的结构；P0-B 迁移只允许对 P0-A 已建的 `app_role` 和 `app_role_permission` 做可重复 DML（数据操作语言）种子写入，不改列、索引、约束或表归属。
- 本包不创建登录接口；`ai-video-core` 与 `ai-video-user` 不调用默认 `StpUtil` 或运营端
  `LoginHelper`。`ai-video-platform` 只有
  `SysAuthorizationActorResolver` 可以调用默认 `LoginHelper.getUserId()`，并立即转换为
  `AppActorContext.sysUser(id)`；其他 P0-B 代码不得出现第二个调用点。
- 本包不创建草稿、任务、额度账户、价格、账单或模型调用；这些属于 P0-C。
- `docs\ASYNC_TASKS.md` 在本包不修改，因为 P0-B 不引入异步任务；P0-C 会正式加入任务契约。

### F0／P0-B candidate／完整 F1 与三人并行边界

- **F0：** P0-A 现行四个 `IApp...Service`、三个 DTO、失效枚举、会话签名、八字段审计 DTO 和安全测试证据可追溯；本计划的 `IWorkspaceAuthorizationService`、五个稳定 DTO、组织会话最小扩展、HTTP 只接收 `workspaceKey` 的信任边界冻结。
- **P0-B candidate：** `20260728_02_p0b_workspace_authorization.sql`、个人／组织切换、成员与组织修订、对象授权、跨账号、SQL 数据范围、双启动边界和两端前端状态全部通过后，本计划只冻结 P0-B 候选提交；它不是完整 F1，也不创建 P0-C 草稿、任务或额度。P0-C 完成后，按批准规格与主计划 Task 4 的 F1 退出门禁冻结唯一完整 F1；随后 P1／P2／P3 分别按主计划 Tasks 5–7 在各自分支 rebase 同一 F1，任何一步都不得由 P0-B Task 13 提前执行。
- **开发 A：** 独占 P0-B 本计划列出的后端、SQL、用户端与平台端文件，完成实施与修复；同一红色任务只允许开发 B 作为独立安全／契约 reviewer 加入，并发上限 2。
- **开发 B：** 并行实施 P1 的纯逻辑、类型、独立 Mock、局部平台页和 `05` 设计；对 P0-B 只能以只读方式担任独立主审，不得编辑、暂存或提交 P0-B 文件；P1／P2／P3 不得在 P0-C complete F1 前执行下游 rebase 或消费完整 F1。
- **开发 C：** 并行实施 P2 的答案规范化、分支策略、类型、独立 Mock 和局部组件；对 P0-B 只能做主审未覆盖的只读专项核对，不得编辑、暂存或提交 P0-B 文件，不得在 F2 前接真实知识、收费任务或外部调用。
- P0-B、P1、P2 三个切片不得共享文件 owner、公共契约写窗口、状态机或数据库迁移；P3 只保留已批准的独立前置设计，不得提前消费 P0-B 未冻结事实。跨模块字段变化先由契约 owner 串行更新公共契约。

### 未来业务实施 worktree 启动门禁

本轮计划文档整改可在 `main` 只读运行 reconciliation 扫描；下面门禁只用于未来 P0-B 业务实施。契约 owner 必须先在 F0 交接记录给出完整 40 位 `AI_VIDEO_F0_HEAD`、`AI_VIDEO_F0_OWNER` 和 `AI_VIDEO_F0_HANDOFF`，开发 A 再从该 F0 **精确提交**创建分配的 `codex/*` 分支和独立 worktree，并在该 worktree 根目录单独执行本块。`93c27e38d` 只作为已知 P0-A 分层基线的祖先下限，绝不等同于未来冻结的完整 F0。门禁把基线与 F0 记录在当前 worktree 的 Git 元数据中；后续每个 PowerShell 块都重新解析仓库根并调用同一门禁，不依赖前一块的进程变量。禁止从共享 `main` 工作区执行任何业务实现命令。

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$currentDirectory = [System.IO.Path]::GetFullPath((Get-Location).Path)
if ($currentDirectory -ne $repoRoot) { throw '启动门禁必须在分配的 worktree 根目录执行' }
$currentBranch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or $currentBranch -eq 'main' -or $currentBranch -notlike 'codex/*') {
  throw "P0-B 业务实施必须位于分配的 codex/* 分支，当前为：$currentBranch"
}
$dirty = @(git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) { $dirty; throw '记录 P0-B 基线前 worktree 必须干净' }
$providedF0Head = $env:AI_VIDEO_F0_HEAD
$f0Owner = $env:AI_VIDEO_F0_OWNER
$f0Handoff = $env:AI_VIDEO_F0_HANDOFF
if ($providedF0Head -notmatch '^[0-9a-fA-F]{40}$' `
    -or [string]::IsNullOrWhiteSpace($f0Owner) `
    -or [string]::IsNullOrWhiteSpace($f0Handoff)) {
  throw '必须从契约 owner 的 F0 交接记录提供完整 AI_VIDEO_F0_HEAD/OWNER/HANDOFF'
}
$f0Head = (& git rev-parse "$providedF0Head^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $f0Head -ne $providedF0Head.ToLowerInvariant()) {
  throw '无法精确解析契约 owner 交接的完整 F0 SHA'
}
$minimumP0AHead = (& git rev-parse '93c27e38d^{commit}').Trim()
if ($LASTEXITCODE -ne 0) { throw '无法解析 P0-A 分层祖先下限' }
git merge-base --is-ancestor $minimumP0AHead $f0Head
if ($LASTEXITCODE -ne 0) { throw '交接的完整 F0 不包含 P0-A 分层祖先下限' }
$baselineHead = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $baselineHead -ne $f0Head) {
  throw '新 worktree 的 baseline HEAD 必须精确等于契约 owner 交接的完整 F0 SHA'
}
$baselineRecordPath = (& git rev-parse --git-path 'p0b-f0-baseline.json').Trim()
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
[pscustomobject]@{
  branch = $currentBranch
  worktreeRoot = $repoRoot
  baselineHead = $baselineHead
  f0Head = $f0Head
  minimumP0AHead = $minimumP0AHead
  f0Owner = $f0Owner
  f0Handoff = $f0Handoff
  owner = '开发 A'
  capturedAtUtc = [DateTime]::UtcNow.ToString('o')
} | ConvertTo-Json | Set-Content -LiteralPath $baselineRecordPath -Encoding UTF8
$gateScript = @'
param([Parameter(Mandatory = $true)][string] $RepoRoot)
$resolvedRoot = [System.IO.Path]::GetFullPath($RepoRoot)
if ([System.IO.Path]::GetFullPath((Get-Location).Path) -ne $resolvedRoot) {
  throw '当前执行目录不是已登记的 worktree 根目录'
}
$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -eq 'main' -or $branch -notlike 'codex/*') {
  throw "禁止在 main 或未分配分支实施 P0-B：$branch"
}
$recordPath = (& git rev-parse --git-path 'p0b-f0-baseline.json').Trim()
if (-not (Test-Path -LiteralPath $recordPath -PathType Leaf)) { throw '缺少 P0-B 基线记录' }
$record = Get-Content -Raw -LiteralPath $recordPath | ConvertFrom-Json
if ($record.branch -ne $branch -or [System.IO.Path]::GetFullPath($record.worktreeRoot) -ne $resolvedRoot) {
  throw '当前 worktree/分支与 P0-B 基线记录不一致'
}
$baseline = (& git rev-parse "$($record.baselineHead)^{commit}").Trim()
$f0 = (& git rev-parse "$($record.f0Head)^{commit}").Trim()
$minimumP0A = (& git rev-parse "$($record.minimumP0AHead)^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $baseline -ne $record.baselineHead `
    -or $f0 -ne $record.f0Head -or $minimumP0A -ne $record.minimumP0AHead) {
  throw 'P0-B 基线或 F0 HEAD 与记录不一致'
}
if ($baseline -ne $f0) { throw '登记的 P0-B baseline HEAD 不等于完整 F0 HEAD' }
if ([string]::IsNullOrWhiteSpace($record.f0Owner) `
    -or [string]::IsNullOrWhiteSpace($record.f0Handoff)) {
  throw 'P0-B 基线记录缺少契约 owner 或 F0 交接记录'
}
git merge-base --is-ancestor $minimumP0A $f0
if ($LASTEXITCODE -ne 0) { throw '登记的完整 F0 不包含 P0-A 分层祖先下限' }
git merge-base --is-ancestor $baseline HEAD
if ($LASTEXITCODE -ne 0) { throw '当前 HEAD 已脱离登记的 P0-B 基线' }
'@
$gateScript | Set-Content -LiteralPath $gateScriptPath -Encoding UTF8
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 启动门禁自检失败' }
[pscustomobject]@{
  branch = $currentBranch
  baselineHead = $baselineHead
  f0Head = $f0Head
  f0Owner = $f0Owner
  f0Handoff = $f0Handoff
  worktreeRoot = $repoRoot
}
```

预期：当前分支匹配 `codex/*` 且不是 `main`，当前目录就是该 worktree 根；`baselineHead` 精确等于契约 owner 交接的完整 `f0Head`，且该 F0 包含 `93c27e38d` 祖先下限；输出 F0 owner／交接记录并且门禁脚本能从 Git 元数据恢复同一记录。若协调者在进入实施前调整批准 F0，必须删除旧记录、从新 F0 创建干净分支／worktree 重新执行本块并把新 SHA 写入任务卡；不得手改 JSON 绕过校验。

## 文件结构

### 数据库与公共契约

- 创建：`ai-video-api\script\sql\ai-video\mysql\20260728_02_p0b_workspace_authorization.sql`
  - 只创建 `app_organization`、`app_org_member`、`av_resource_grant`。
  - 复用 P0-A 已创建的 `app_role` 与 `app_role_permission`，按 `role_code`、`permission_code` 选择 P0-A 主键后，可重复更新四个内置角色并写入准确角色权限映射。
  - 禁止出现 `app_role`、`app_role_permission`、`app_user_role` 的 `CREATE TABLE`、`ALTER TABLE`、`DROP TABLE` 或重复视图/临时表 DDL。
  - 不写测试账号、生产组织或固定工作区。
- 修改：`docs\API_CONTRACT.md`
  - 固定工作区查询/切换和组织/成员管理接口、数字错误码 `46126`/`46127`。
- 修改：`docs\DOMAIN_MODEL.md`
  - 固定工作区、成员修订、角色权限、资源授权、隐式范围及授权判定顺序。
- 修改：`docs\ARCHITECTURE.md`
  - 固定 P0-A 身份服务与 P0-B 授权服务的单向依赖。
- 修改：`docs\BACKEND_GUIDE.md`
  - 登记 `org.dromara.aivideo.authorization` 分层和双启动模块控制器边界。
- 修改：`docs\FRONTEND_GUIDE.md`
  - 登记工作区上下文、切换失效和不自动重放规则。

### `ai-video-core` 授权领域

- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppSessionService.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\dto\AppWorkspaceSessionSnapshotDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\security\AppWorkspaceSwitchAdmissionConsumer.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImpl.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppRole.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppRolePermission.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppPermission.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRoleMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRolePermissionMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppPermissionMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\WorkspaceType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\OwnerType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\BillingSubjectType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\ResourceAction.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\WorkspaceContextDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\WorkspaceSummaryDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\ResourceOwnershipDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\ResourceDataScopeDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\SwitchWorkspaceDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IWorkspaceAuthorizationService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IOrganizationAdminService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IResourceOwnershipService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceAuthorizationServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceSwitchAdmissionProofStore.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\OrganizationAdminServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\event\OrganizationSessionInvalidationRequested.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\listener\OrganizationSessionInvalidationListener.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\ResourceOwnershipServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\security\WorkspaceKeyService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AppOrganization.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AppOrgMember.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AvResourceGrant.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AppOrganizationMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AppOrgMemberMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AvResourceGrantMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\resources\mapper\aivideo\authorization\AppOrgMemberMapper.xml`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\resources\mapper\aivideo\authorization\AvResourceGrantMapper.xml`

### 后端接口模块

- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\security\AppAuthorizationActorResolver.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\controller\WorkspaceController.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\domain\bo\SwitchWorkspaceBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\domain\vo\WorkspaceVo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\security\SysAuthorizationActorResolver.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\controller\AppOrganizationController.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\OrganizationQueryBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\CreateOrganizationBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\UpdateOrganizationBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\UpsertOrgMemberBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\vo\AppOrganizationVo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\vo\AppOrgMemberVo.java`

### 后端测试

- 验证：`ai-video-api\pom.xml`
  - 必须已经由 P0-A 配置 Failsafe（集成测试插件），约定 `*Test` 为单元测试、`*IT` 为集成测试；缺失时停止 P0-B 并回到 P0-A 修复。
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\pom.xml`
  - 加入 Mockito、MySQL JDBC 测试依赖，并通过 P0-A test-jar 复用 `LocalIntegrationEnvironment`；不得加入容器测试依赖。
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceSchemaIT.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationServiceTest.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImplTest.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionIntegrationTestFixture.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionWorkspaceInvalidationIT.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationIT.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\OrganizationAdminServiceIT.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization\WorkspaceControllerIT.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\test\java\org\dromara\aivideo\platform\authorization\AppOrganizationControllerIT.java`
- 创建：`ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo\assembly\UserAuthorizationBoundaryIT.java`
- 创建：`ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo\assembly\PlatformAuthorizationBoundaryIT.java`

本计划创建的每个 JUnit 测试类都在类级标注 `@Tag("dev")`，与根 POM 的 `${profiles.active}` 分组一致；`*Test` 只由 Surefire 执行，`*IT` 只由 Failsafe 执行。

### 用户端前端

- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\types.ts`
- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\errors.ts`
- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\ruoyiAdapter.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\types.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\api.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\queryKeys.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\useWorkspace.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\api.test.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\useWorkspace.test.tsx`
- 创建：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.tsx`
- 创建：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.test.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\StudioTopbar.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\index.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\model.ts`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\style.css`

### 运营端前端

- 创建：`ai-video-ui\ai-video-platform-ui\src\api\aivideo\organization\types.ts`
- 创建：`ai-video-ui\ai-video-platform-ui\src\api\aivideo\organization\index.ts`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\index.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\components\MemberDrawer.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\index.test.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\components\MemberDrawer.test.tsx`

## 固定领域签名

实现者不得在任务中改名或把同一语义复制到第二个服务：

```java
package org.dromara.aivideo.authorization.service;

import org.dromara.aivideo.authorization.dto.ResourceDataScopeDTO;
import org.dromara.aivideo.authorization.dto.ResourceOwnershipDTO;
import org.dromara.aivideo.authorization.dto.SwitchWorkspaceDTO;
import org.dromara.aivideo.authorization.dto.WorkspaceContextDTO;
import org.dromara.aivideo.authorization.dto.WorkspaceSummaryDTO;
import org.dromara.aivideo.identity.security.AppActorContext;

import java.util.List;

public interface IWorkspaceAuthorizationService {
    WorkspaceContextDTO resolveCurrentWorkspace();
    List<WorkspaceSummaryDTO> listAvailableWorkspaces();
    WorkspaceSummaryDTO switchCurrentWorkspace(
        SwitchWorkspaceDTO request,
        AppActorContext actor);
    void requireWorkspacePermission(String permissionCode);
    void requireResourceAction(ResourceOwnershipDTO resource, String action);
    ResourceDataScopeDTO resolveDataScope(String resourceType, String action);
    void initializeCreatorGrant(ResourceOwnershipDTO resource, AppActorContext actor);
    void inheritResourceGrants(
        ResourceOwnershipDTO source,
        ResourceOwnershipDTO target,
        AppActorContext actor);
}
```

`IOrganizationAdminService` 是核心内部组织事务入口，`IResourceOwnershipService` 是核心内部资源归属查询入口；二者与实现分别位于 `authorization/service` 和 `authorization/service.impl`，不得形成第二套公开稳定接口或让核心依赖端侧 BO／VO。平台 Controller 把 `domain.bo` 映射为核心 Entity／明确标量参数，并把核心查询结果映射为平台 `domain.vo`；Entity 不直接暴露到 HTTP。`IOrganizationAdminService` 的所有写方法都显式接收 `AppActorContext` 和运营端提交的预期组织／成员修订号，查询方法不伪造操作者。`IWorkspaceAuthorizationService` 的两个授权写方法也显式接收 actor。核心
`authorization` 包不得从默认 `LoginHelper`、`AppLoginHelper`、`StpUtil` 或线程变量
推导写入主体；两端 `security/*ActorResolver` 是把 Web 登录转换为 typed actor 的唯一边界。Controller
完成身份域转换并不替代核心服务校验：`OrganizationAdminServiceImpl` 的
`create/update/upsertMember/leaveMember` 必须把
`requireSysAdminActor(actor)` 作为进入方法后的第一条业务语句，显式要求
`actorType=sys_user`；直接从测试、批处理或未来新入口传入同号
`AppActorContext.appUser` 也必须在任何查询、加锁、写入、审计或提交后回调之前被拒绝。

### 组织工作区会话最小扩展与信任边界

- `IAppSessionService` 与 `AppWorkspaceSessionSnapshotDTO` 的公开签名保持不变；P0-B 只修改现行 `AppSessionServiceImpl.replaceWorkspace` 及其 `AppSessionServiceImplTest`。
- 当前实现的 `canonicalPersonalWorkspace` 只接受个人工作区，是 F0 的真实阻塞。P0-B 保留个人规范化原行为，并增加组织快照分支；组织分支只接受已经由 `IWorkspaceAuthorizationService` 从数据库交叉验证账号、候选工作区、组织状态、成员状态、角色权限、`workspaceRevision` 与 `membershipRevision` 后构造的服务端快照。
- 用户切换请求只允许不透明 `workspaceKey`。客户端不得提交 `expectedMembershipRevision`、tenant、owner、billing、role、permissions 或任何会话快照字段；服务端在同一请求内重新读取并比对最新组织与成员修订。
- 伪造键、跨账号 actor、成员已退出／禁用／过期、组织停用、成员修订或组织修订过期时固定返回 `46126`，且 `IAppSessionService.replaceWorkspace` 必须从未被调用；不得以失败后自动切个人并重放原请求掩盖拒绝。
- `AppSessionServiceImplTest` 的 GREEN 场景必须证明个人行为不变、合法规范组织快照可替换；缺少 `membershipRevision`、owner／billing 不一致、个人或组织权限集合异常的快照仍被拒绝。组织／成员修订变化继续通过 `AppSessionRevisionGuard.checkCurrentSession()` 使旧会话失效。

P0-A 角色权限依赖固定为：

```java
private final IAppPermissionService appPermissionService;
private final org.dromara.aivideo.identity.mapper.AppRoleMapper identityRoleMapper;
private final org.dromara.aivideo.identity.mapper.AppRolePermissionMapper
    identityRolePermissionMapper;
private final org.dromara.aivideo.identity.mapper.AppPermissionMapper
    identityPermissionMapper;
```

个人工作区调用 `appPermissionService.roleCodes(actorUserId)` 和
`appPermissionService.permissionCodes(actorUserId)`；组织工作区以
`app_org_member.role_code` 为输入，使用上述三个 P0-A Mapper 读取
`identity.domain.AppRole/AppRolePermission/AppPermission`。组织角色查询必须同时过滤
`app_role.scope_type='organization'`、角色/映射/权限的 `status='active'` 和
`app_role.del_flag='0'`，没有角色或映射时返回空权限集合并由调用方拒绝。禁止把组织
角色写入无组织维度的 `app_user_role`，也禁止在 `authorization` 包新增
`AppRole`、`AppRolePermission`、`AppUserRole` 或对应 Mapper。

每个受保护请求的授权顺序固定为：

```text
AppSessionRevisionGuard.checkCurrentSession() 重新读取已提交修订并失败关闭
→ AppLoginHelper.getPrincipal() 当前创作身份
→ 当前工作区与租户
→ 组织场景重新读取有效成员、组织状态和修订号
→ 个人工作区调用 P0-A IAppPermissionService；组织工作区经 P0-A identity Mapper
  读取 app_role → app_role_permission → app_permission
→ 所有权 / owner-admin 隐式范围 / av_resource_grant
→ 收费操作另查 aivideo:quota:use
```

## 红绿门禁统一约定

- 红灯步骤必须让指定测试源码成功编译并真正执行，随后由业务断言失败；`testCompile`（测试编译）、依赖解析、应用装配或测试发现失败都不是有效红灯。
- 若测试引用本任务才会创建的类型，步骤 1 先在本任务已列出的准确生产文件中加入“只保证编译”的签名骨架：Java 方法统一抛出 `UnsupportedOperationException("red phase")`，React 组件返回 `null`，TypeScript 接口函数返回拒绝的 `Promise`。骨架不得包含业务判断，并在步骤 3 被同一文件中的最小真实实现替换。
- Maven 绿灯在命令前删除目标 Surefire/Failsafe XML，命令成功后只接受本次生成且满足 `tests > 0`、`failures = 0`、`errors = 0`、`skipped = 0` 的报告。
- 每个提交步骤都把“提交前暂存区为空”和“暂存集合精确等于本任务文件清单”作为硬门禁；任何不属于本任务的既有暂存内容都必须先停止并交由开发者处理。

## 任务 1：验证 P0-A 门禁并冻结公共契约

**最小任务卡：**

- **单一目标／不做：** 核验 P0-A 已落地事实并冻结 P0-B 公共契约；不重复实现 P0-A，不修改 P0-A 身份表或公开签名。
- **风险／触发：** 红色；命中身份、授权、公共契约和双端隔离。
- **权威来源：** P0-A 现行源码与最终测试证据、原业务规格、并行交付规格 F0、本计划 P0-A 精确消费映射。
- **成功／反向验收：** 现行四个 `IApp...Service`、三个 DTO、失效枚举、会话方法与八字段审计 DTO 全部精确命中；旧包、旧简单类名、旧审计字段零命中；`docs/DOMAIN_MODEL.md` 只登记三个 P0-B `BaseEntity` 最小例外，并具备项目负责人批准证据与可撤销条件。
- **所有权／数据范围：** 仅本任务 `文件` 列出的五份公共文档及其工作区／组织授权契约；P0-A 源码与数据只读。
- **依赖／人员／并发：** 依赖 P0-A 提交；开发 A 实施、开发 B 独立契约／安全审查，同一任务最多 2 人。
- **验证／检查点：** 先 review P0-A 类型与信任边界，再运行契约扫描和 `validate-development-standards.ps1`；修复后只复核差异。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 修改：`docs\API_CONTRACT.md`
- 修改：`docs\DOMAIN_MODEL.md`
- 修改：`docs\ARCHITECTURE.md`
- 修改：`docs\BACKEND_GUIDE.md`
- 修改：`docs\FRONTEND_GUIDE.md`

- [ ] **步骤 1（2–5 分钟）：运行 P0-A 产物检查并确认当前失败点**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$required = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppIdentityService.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppSessionService.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppSecurityAuditService.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppRole.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppRolePermission.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppPermission.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRoleMapper.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRolePermissionMapper.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppPermissionMapper.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\security\AppLoginHelper.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\security\AppSessionRevisionGuard.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\dto\AppPrincipalSnapshotDTO.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\dto\AppWorkspaceSessionSnapshotDTO.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\dto\AppSecurityAuditDTO.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\domain\AppSessionInvalidationReason.java',
  'ai-video-api\script\sql\ai-video\mysql\20260728_01_p0a_identity_security.sql'
)
$missing = $required | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; exit 1 }
rg -n "replaceWorkspace|invalidateUserSessions|invalidateOrganizationSessions" `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppSessionService.java
if ($LASTEXITCODE -ne 0) { throw 'IAppSessionService 前置方法检查失败' }
rg -n "roleCodes|permissionCodes|replaceUserRoles|replaceRolePermissions" `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java
if ($LASTEXITCODE -ne 0) { throw 'IAppPermissionService 前置方法检查失败' }
```

预期：P0-A 未合入时命令退出 `1` 并列出准确缺失文件；P0-A 合入后输出
`IAppSessionService` 的三个冻结方法和 `IAppPermissionService` 的四个固定方法。门禁未通过时停止本计划，不在 P0-B 补写身份实体、Mapper、角色权限服务或登录实现。

- [ ] **步骤 2（2–5 分钟）：为公共契约编写失败扫描**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$checks = @(
  @{ File = 'docs\API_CONTRACT.md'; Pattern = 'GET /api/auth/workspaces' },
  @{ File = 'docs\API_CONTRACT.md'; Pattern = 'PUT /api/auth/current-workspace' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = 'av_resource_grant' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = 'AppOrganization' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = 'AppOrgMember' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = 'AvResourceGrant' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = 'BaseEntity 最小例外' },
  @{ File = 'docs\DOMAIN_MODEL.md'; Pattern = '项目负责人批准证据' },
  @{ File = 'docs\ARCHITECTURE.md'; Pattern = 'IWorkspaceAuthorizationService' },
  @{ File = 'docs\FRONTEND_GUIDE.md'; Pattern = '46126' }
)
$failed = $checks | Where-Object { -not (Select-String -LiteralPath $_.File -SimpleMatch $_.Pattern -Quiet) }
if ($failed) { $failed | ForEach-Object { "$($_.File) => $($_.Pattern)" }; exit 1 }
```

预期：至少一个契约项缺失并退出 `1`。

- [ ] **步骤 3（2–5 分钟）：写入 P0-B 公共契约**

在五份文档中写明：

```text
用户接口：
GET /api/auth/workspaces
PUT /api/auth/current-workspace

运营接口：
GET/POST/PUT /api/admin/app-organizations
GET/POST/PUT/DELETE /api/admin/app-organizations/{id}/members

稳定错误：
46126 WORKSPACE_NOT_AVAILABLE
46127 WORKSPACE_ACTION_FORBIDDEN

数据范围：
个人所有者隐式完整动作；
组织 owner/admin 隐式访问组织资源；
普通成员只访问本人创建且已初始化授权或显式授权的资源；
use_billing 永远来自工作区角色权限，不来自对象授权。
```

同时登记 P0-A → P0-B → P0-C 的单向依赖，明确 `app_role`、
`app_role_permission`、`app_user_role` 的表结构与 Java 持久化类型只属于 P0-A，
P0-B 仅消费现有 `IAppPermissionService`／`identity.mapper`；切换工作区不改变已创建资源的所有者或计费主体。

`docs/DOMAIN_MODEL.md` 必须单独登记且只登记 `AppOrganization`、`AppOrgMember`、`AvResourceGrant` 三个“不继承 `BaseEntity`”的最小例外。登记内容逐项包含：例外原因（运营端自动填充无法表达 `sys_user`／`app_user` typed actor）、最小字段范围、以 `createdActorType/createdActorId` 和 `updatedActorType/updatedActorId` 取代默认审计主体的控制措施、禁止扩散到第四个实体的回归条件，以及项目负责人姓名／批准时间／关联评审记录。`AvResourceGrant` 另保留不可变的 `grantedByType/grantedById`。批准证据为空时任务 2–13 全部阻塞，不得以计划文本代替负责人确认；若框架后续提供等价 typed actor 自动填充，则触发撤销例外并回归 `BaseEntity` 的专项评审。

- [ ] **步骤 4（2–5 分钟）：运行契约与规范校验**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '开发规范校验失败' }
$domainModel = Get-Content -Raw -LiteralPath docs\DOMAIN_MODEL.md
$exceptionTypes = @('AppOrganization', 'AppOrgMember', 'AvResourceGrant')
foreach ($exceptionType in $exceptionTypes) {
  if ($domainModel -notmatch [regex]::Escape($exceptionType)) {
    throw "BaseEntity 最小例外登记缺少：$exceptionType"
  }
}
foreach ($requiredEvidence in @(
    'BaseEntity 最小例外',
    'createdActorType/createdActorId',
    'updatedActorType/updatedActorId',
    '项目负责人批准证据',
    '批准人',
    '批准时间',
    '关联评审记录',
    '撤销条件')) {
  if ($domainModel -notmatch [regex]::Escape($requiredEvidence)) {
    throw "BaseEntity 例外缺少批准或回归控制：$requiredEvidence"
  }
}
Write-Output "BASEENTITY_EXCEPTION_OK count=$($exceptionTypes.Count)"
```

预期：输出 `DEVELOPMENT_STANDARDS_OK` 与 `BASEENTITY_EXCEPTION_OK count=3`。

- [ ] **步骤 5（2–5 分钟）：提交契约**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'docs/API_CONTRACT.md',
  'docs/DOMAIN_MODEL.md',
  'docs/ARCHITECTURE.md',
  'docs/BACKEND_GUIDE.md',
  'docs/FRONTEND_GUIDE.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存契约文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "docs: 冻结工作区与组织授权契约"
if ($LASTEXITCODE -ne 0) { throw '提交契约失败' }
```

## 任务 2：复用单元/集成测试分界并建立 P0-B 数据库结构

**最小任务卡：**

- **单一目标／不做：** 建立 P0-B 三张授权事实表和幂等角色权限种子；不重建或变更 P0-A 三张角色表，不接非本机测试库。
- **风险／触发：** 红色；命中数据库结构、权限事实、迁移和恢复边界。
- **权威来源：** 原业务规格表约束、P0-A `01` 脚本、并行规格迁移顺序、本机集成测试规则。
- **成功／反向验收：** `01 → 02 → 02` 可重复；新表恰好三张，58 个映射准确，第三种 actor 被数据库拒绝，非本机／非 `ai_video_test` 立即失败。
- **所有权／数据范围：** 仅本任务 `文件` 列出的 core 测试依赖、`02` 脚本和 `WorkspaceSchemaIT`；只清理专用库与当前 Redis 运行前缀。
- **依赖／人员／并发：** 依赖任务 1／F0；开发 A 实施、开发 B 独立迁移／安全审查，同一任务最多 2 人。
- **验证／检查点：** 先 review DDL 归属和回退原则，再以 `'-Pdev,local-integration-test'` 运行 RED／GREEN 并核对本次 Failsafe XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 验证：`ai-video-api\pom.xml`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\pom.xml`
- 只读前置迁移：`ai-video-api\script\sql\ai-video\mysql\20260728_01_p0a_identity_security.sql`
- 创建：`ai-video-api\script\sql\ai-video\mysql\20260728_02_p0b_workspace_authorization.sql`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceSchemaIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的数据库归属与种子集成测试**

测试完整固定为“空库 → P0-A 一次 → P0-B 两次”的顺序；P0-B 不能单独在空库运行，也不能靠 `IF NOT EXISTS` 掩盖身份表 DDL：

```java
@Tag("dev")
class WorkspaceSchemaIT {
    private static final LocalIntegrationEnvironment ENV =
        LocalIntegrationEnvironment.requireFromEnvironment();

    @BeforeEach
    void resetDedicatedLocalState() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        ENV.clearCurrentRunRedisKeys();
    }

    @Test
    void migrationCreatesAuthorizationFactsAndIdempotentRoleSeeds() throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            Path sqlRoot = Path.of(
                System.getProperty("maven.multiModuleProjectDirectory"),
                "script", "sql", "ai-video", "mysql");
            Path identitySql =
                sqlRoot.resolve("20260728_01_p0a_identity_security.sql");
            Path authorizationSql =
                sqlRoot.resolve("20260728_02_p0b_workspace_authorization.sql");
            String p0bSql = Files.readString(
                authorizationSql, StandardCharsets.UTF_8);

            assertThat(p0bSql).doesNotContainPattern(
                "(?is)\\b(?:CREATE\\s+(?:TEMPORARY\\s+)?TABLE"
                    + "(?:\\s+IF\\s+NOT\\s+EXISTS)?|ALTER\\s+TABLE|"
                    + "DROP\\s+TABLE(?:\\s+IF\\s+EXISTS)?)\\s+`?"
                    + "(?:app_role|app_role_permission|app_user_role)`?\\b");
            assertThat(tableCount(connection, "app_role")).isZero();
            assertThat(tableCount(connection, "app_role_permission")).isZero();
            assertThat(tableCount(connection, "app_user_role")).isZero();

            ScriptUtils.executeSqlScript(
                connection, new FileSystemResource(identitySql));

            assertThat(tableCount(connection, "app_role")).isEqualTo(1);
            assertThat(tableCount(connection, "app_role_permission")).isEqualTo(1);
            assertThat(tableCount(connection, "app_user_role")).isEqualTo(1);
            assertThat(columnNames(connection, "app_role"))
                .contains("role_id", "role_code", "built_in", "role_revision")
                .doesNotContain("system_builtin");
            assertThat(columnNames(connection, "app_role_permission"))
                .contains("id", "role_id", "permission_id", "status")
                .doesNotContain("role_code", "permission_code");
            assertThat(columnNames(connection, "app_user_role"))
                .contains("id", "user_id", "role_id", "status",
                    "valid_from", "valid_until");
            assertThat(rowCount(connection, "app_role", "built_in = 1"))
                .isEqualTo(4);
            assertThat(rowCount(connection, "app_role_permission", "1 = 1"))
                .isZero();
            assertThat(rowCount(connection, "app_user_role", "1 = 1"))
                .isZero();

            Map<String, String> p0aRoleDdl = Map.of(
                "app_role", showCreateTable(connection, "app_role"),
                "app_role_permission",
                    showCreateTable(connection, "app_role_permission"),
                "app_user_role",
                    showCreateTable(connection, "app_user_role"));

            Resource authorizationMigration =
                new FileSystemResource(authorizationSql);
            ScriptUtils.executeSqlScript(connection, authorizationMigration);
            List<String> firstRolePermissionPairs =
                activeRolePermissionPairs(connection);
            assertThat(firstRolePermissionPairs).hasSize(58);
            ScriptUtils.executeSqlScript(connection, authorizationMigration);

            assertThat(tableCount(connection, "app_organization")).isEqualTo(1);
            assertThat(tableCount(connection, "app_org_member")).isEqualTo(1);
            assertThat(tableCount(connection, "av_resource_grant")).isEqualTo(1);
            assertThat(tableCount(connection, "app_role")).isEqualTo(1);
            assertThat(tableCount(connection, "app_role_permission")).isEqualTo(1);
            assertThat(tableCount(connection, "app_user_role")).isEqualTo(1);
            assertThat(showCreateTable(connection, "app_role"))
                .isEqualTo(p0aRoleDdl.get("app_role"));
            assertThat(showCreateTable(connection, "app_role_permission"))
                .isEqualTo(p0aRoleDdl.get("app_role_permission"));
            assertThat(showCreateTable(connection, "app_user_role"))
                .isEqualTo(p0aRoleDdl.get("app_user_role"));
            assertThat(rowCount(connection, "app_role", "built_in = 1"))
                .isEqualTo(4);
            assertThat(joinedRolePermissionCount(
                connection, "organization_member", "aivideo:quota:use"))
                .isEqualTo(1);
            assertThat(joinedRolePermissionCount(
                connection, "organization_owner",
                "aivideo:quota:organization-query")).isEqualTo(1);
            assertThat(joinedRolePermissionCount(
                connection, "organization_member",
                "aivideo:quota:organization-query")).isZero();
            assertThat(rolePermissionCount(connection, "personal_creator"))
                .isEqualTo(14);
            assertThat(rolePermissionCount(connection, "organization_owner"))
                .isEqualTo(15);
            assertThat(rolePermissionCount(connection, "organization_admin"))
                .isEqualTo(15);
            assertThat(rolePermissionCount(connection, "organization_member"))
                .isEqualTo(14);
            assertThat(rowCount(connection, "app_role_permission", "1 = 1"))
                .isEqualTo(58);
            assertThat(activeRolePermissionPairs(connection))
                .containsExactlyElementsOf(firstRolePermissionPairs);
            assertThat(duplicateRolePermissionPairCount(connection)).isZero();
            assertThat(rowCount(connection, "app_user_role", "1 = 1"))
                .isZero();

            assertActorTypeCheck(
                connection,
                "ck_app_organization_actor_types",
                "created_actor_type",
                "updated_actor_type");
            assertActorTypeCheck(
                connection,
                "ck_app_org_member_actor_types",
                "created_actor_type",
                "updated_actor_type");
            assertActorTypeCheck(
                connection,
                "ck_av_resource_grant_actor_types",
                "granted_by_type",
                "created_actor_type",
                "updated_actor_type");

            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_organization (
                    organization_id, tenant_id, display_name, status,
                    organization_revision,
                    created_actor_type, created_actor_id, created_at,
                    updated_actor_type, updated_actor_id, updated_at
                ) VALUES (
                    99001, 99001, '非法组织主体', 'active', 1,
                    'service', 1, CURRENT_TIMESTAMP,
                    'sys_user', 1, CURRENT_TIMESTAMP
                )
                """))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_org_member (
                    id, tenant_id, organization_id, app_user_id,
                    role_code, status, membership_revision,
                    created_actor_type, created_actor_id, created_at,
                    updated_actor_type, updated_actor_id, updated_at
                ) VALUES (
                    99001, 99001, 99001, 1001,
                    'organization_member', 'active', 1,
                    'app_user', 1001, CURRENT_TIMESTAMP,
                    'service', 1, CURRENT_TIMESTAMP
                )
                """))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO av_resource_grant (
                    id, tenant_id, resource_type, resource_id,
                    grantee_type, grantee_key, action_code,
                    source_resource_type, source_resource_id,
                    granted_by_type, granted_by_id,
                    status, created_actor_type, created_actor_id, created_at,
                    updated_actor_type, updated_actor_id, updated_at
                ) VALUES (
                    99001, 99001, 'script_draft', 99001,
                    'user', '1001', 'query',
                    'direct', 0,
                    'service', 1001,
                    'active', 'app_user', 1001, CURRENT_TIMESTAMP,
                    'app_user', 1001, CURRENT_TIMESTAMP
                )
                """))
                .isInstanceOf(SQLException.class);
        }
    }
}
```

`columnNames` 查询 `information_schema.columns`；`showCreateTable` 读取
`SHOW CREATE TABLE` 的第二列；`joinedRolePermissionCount` 和
`rolePermissionCount` 都执行
`app_role r JOIN app_role_permission rp ON rp.role_id=r.role_id JOIN app_permission p ON p.permission_id=rp.permission_id`
并绑定角色代码/权限代码参数，同时过滤
`r.status='active' AND r.del_flag='0' AND rp.status='active' AND p.status='active'`。
`duplicateRolePermissionPairCount` 按
`(role_id, permission_id)` 分组统计 `COUNT(*) > 1`。`tableCount` 查询
`information_schema.tables`；`rowCount` 只接收本测试类中的三个固定条件，不接收 HTTP 输入。
`activeRolePermissionPairs` 使用相同三表 JOIN，按
`r.role_code, p.permission_code` 排序并返回 `role_code + "|" + permission_code`，
用于证明第二次 P0-B 迁移没有增加、删除或改写逻辑种子。
`assertActorTypeCheck` 查询 `information_schema.check_constraints`，断言准确约束同时
包含所有传入主体列、`app_user` 和 `sys_user`；`executeUpdate` 只执行上方固定 SQL。
三个非法插入实际证明 MySQL 拒绝未声明的第三种主体 `service`，不能只靠 Java 枚举。

- [ ] **步骤 2（2–5 分钟）：运行集成测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceSchemaIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=WorkspaceSchemaIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 WorkspaceSchemaIT 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 WorkspaceSchemaIT 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 WorkspaceSchemaIT 至少一项真实执行并失败'
}
```

预期：命令非零退出，且本次 `WorkspaceSchemaIT` 报告满足 `tests > 0`、`failures + errors > 0`、`skipped < tests`；测试编译失败、装配失败、没有新报告或集成测试被跳过都不是有效红灯。

- [ ] **步骤 3（2–5 分钟）：验证测试插件并加入测试依赖**

先确认根 POM 已由 P0-A 提供以下 Failsafe 配置；缺少任一项都停止本实施包，不在 P0-B 另建测试约定：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven-surefire-plugin.version}</version>
    <configuration>
        <skipITs>${skipITs}</skipITs>
        <groups>${profiles.active}</groups>
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
```

同时确认根属性已有 `<skipITs>true</skipITs>`，Surefire 配置已有：

```xml
<excludes>
    <exclude>**/*IT.java</exclude>
</excludes>
```

`ai-video-core/pom.xml` 增加以下测试依赖；版本由 Spring Boot BOM 管理：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

`LocalIntegrationEnvironment` 已在同一 `ai-video-core` 模块的 P0-A 测试源码中可用；MySQL JDBC 驱动由既有依赖管理提供，适配模块再通过 P0-A 导出的 test-jar（测试归档）复用它。测试复用已提交的用户端 `application-dev.yml` 账号和密码，不得绕过夹具使用非本机地址。

- [ ] **步骤 4（2–5 分钟）：只创建三张 P0-B 表**

迁移使用 MySQL 8 DDL，且只包含以下三张新表：

```sql
CREATE TABLE IF NOT EXISTS app_organization (
    organization_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    organization_revision BIGINT NOT NULL DEFAULT 1,
    created_actor_type VARCHAR(32) NOT NULL,
    created_actor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_actor_type VARCHAR(32) NOT NULL,
    updated_actor_id BIGINT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (organization_id),
    UNIQUE KEY uk_app_organization_tenant (tenant_id),
    KEY idx_app_organization_status (status),
    CONSTRAINT ck_app_organization_status
        CHECK (status IN ('active', 'disabled')),
    CONSTRAINT ck_app_organization_actor_types CHECK (
        created_actor_type IN ('app_user', 'sys_user')
        AND updated_actor_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_org_member (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    app_user_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_from DATETIME NULL,
    valid_until DATETIME NULL,
    membership_revision BIGINT NOT NULL DEFAULT 1,
    created_actor_type VARCHAR(32) NOT NULL,
    created_actor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_actor_type VARCHAR(32) NOT NULL,
    updated_actor_id BIGINT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_org_member_org_user (organization_id, app_user_id),
    KEY idx_app_org_member_user_status (app_user_id, status),
    CONSTRAINT ck_app_org_member_status
        CHECK (status IN ('active', 'suspended', 'left')),
    CONSTRAINT ck_app_org_member_actor_types CHECK (
        created_actor_type IN ('app_user', 'sys_user')
        AND updated_actor_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_resource_grant (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    grantee_type VARCHAR(32) NOT NULL,
    grantee_key VARCHAR(128) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    source_resource_type VARCHAR(64) NOT NULL DEFAULT 'direct',
    source_resource_id BIGINT NOT NULL DEFAULT 0,
    granted_by_type VARCHAR(32) NOT NULL,
    granted_by_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    valid_until DATETIME NULL,
    created_actor_type VARCHAR(32) NOT NULL,
    created_actor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_actor_type VARCHAR(32) NOT NULL,
    updated_actor_id BIGINT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_resource_grant_action (
        tenant_id, resource_type, resource_id,
        grantee_type, grantee_key, action_code,
        source_resource_type, source_resource_id
    ),
    KEY idx_av_resource_grant_lookup (
        tenant_id, resource_type, resource_id, status, valid_until
    ),
    CONSTRAINT ck_av_resource_grant_action
        CHECK (action_code IN ('query', 'edit', 'generate', 'confirm', 'remove')),
    CONSTRAINT ck_av_resource_grant_actor_types CHECK (
        granted_by_type IN ('app_user', 'sys_user')
        AND created_actor_type IN ('app_user', 'sys_user')
        AND updated_actor_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

创建者授权使用 `source_resource_type='creator'`、`source_resource_id=0`；直接授权使用 `direct/0`；继承授权保存真实来源。来源列非空，确保上述唯一键在 MySQL 中能真正阻止重复授权。迁移中不得加入任何第四张新表，也不得加入 P0-A 身份表的 DDL。
`granted_by_type + granted_by_id` 保存发出本次授权的强类型主体；创建和更新 actor
列保存行级审计。首次插入时三组主体一致；唯一键冲突导致状态/有效期更新时只更新
`updated_actor_type/updated_actor_id/updated_at`，不得覆盖最初
`granted_by_type/granted_by_id`。相同数字编号的 app 用户与 sys 用户必须保留不同
类型，禁止以编号推断身份域。

- [ ] **步骤 5（2–5 分钟）：按 P0-A 主键写入 58 条角色权限种子**

先只对 P0-A 已有四个角色行做幂等元数据修正；缺少任一角色时由
`WorkspaceSchemaIT` 的角色计数和映射计数失败，不在 P0-B 创建替代角色表：

```sql
UPDATE app_role AS r
JOIN JSON_TABLE(
    '[
      {"roleCode":"personal_creator","roleName":"个人创作者","scopeType":"personal"},
      {"roleCode":"organization_owner","roleName":"组织所有者","scopeType":"organization"},
      {"roleCode":"organization_admin","roleName":"组织管理员","scopeType":"organization"},
      {"roleCode":"organization_member","roleName":"组织成员","scopeType":"organization"}
    ]',
    '$[*]' COLUMNS (
        role_code VARCHAR(64) PATH '$.roleCode',
        role_name VARCHAR(64) PATH '$.roleName',
        scope_type VARCHAR(16) PATH '$.scopeType'
    )
) AS seed ON seed.role_code = r.role_code
SET r.role_name = seed.role_name,
    r.scope_type = seed.scope_type,
    r.built_in = 1,
    r.status = 'active',
    r.updated_by_type = 'sys_user',
    r.updated_by_id = 1,
    r.update_time = CURRENT_TIMESTAMP
WHERE r.role_name <> seed.role_name
   OR r.scope_type <> seed.scope_type
   OR r.built_in <> 1
   OR r.status <> 'active';
```

角色权限映射必须以角色代码、权限代码连接 P0-A 表后选择
`r.role_id/p.permission_id`，绝不能向映射表写不存在的
`role_code/permission_code` 列：

```sql
INSERT INTO app_role_permission (
    id, role_id, permission_id, status,
    created_by_type, created_by_id,
    updated_by_type, updated_by_id,
    create_time, update_time
)
SELECT
    2026072802000000 + numbered.seed_no,
    r.role_id,
    p.permission_id,
    'active',
    'sys_user',
    1,
    'sys_user',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        role_code,
        permission_code,
        ROW_NUMBER() OVER (
            ORDER BY role_code, permission_code
        ) AS seed_no
    FROM (
        SELECT roles.role_code, permissions.permission_code
        FROM JSON_TABLE(
            '["personal_creator","organization_owner",
              "organization_admin","organization_member"]',
            '$[*]' COLUMNS (
                role_code VARCHAR(64) PATH '$'
            )
        ) AS roles
        CROSS JOIN JSON_TABLE(
            '[
              "aivideo:studio:query","aivideo:studio:create",
              "aivideo:studio:edit","aivideo:studio:generate",
              "aivideo:script:query","aivideo:script:edit",
              "aivideo:script:confirm","aivideo:script:remove",
              "aivideo:task:query","aivideo:task:cancel",
              "aivideo:quota:query","aivideo:quota:use",
              "aivideo:notification:query","aivideo:notification:edit"
            ]',
            '$[*]' COLUMNS (
                permission_code VARCHAR(100) PATH '$'
            )
        ) AS permissions
        UNION ALL
        SELECT elevated_roles.role_code,
               'aivideo:quota:organization-query'
        FROM JSON_TABLE(
            '["organization_owner","organization_admin"]',
            '$[*]' COLUMNS (
                role_code VARCHAR(64) PATH '$'
            )
        ) AS elevated_roles
    ) AS exact_role_permission_seed
) AS numbered
JOIN app_role AS r
  ON r.role_code = numbered.role_code
 AND r.built_in = 1
 AND r.status = 'active'
 AND r.del_flag = '0'
JOIN app_permission AS p
  ON p.permission_code = numbered.permission_code
 AND p.status = 'active'
ON DUPLICATE KEY UPDATE
    status = 'active',
    updated_by_type = 'sys_user',
    updated_by_id = 1;
```

该选择恰好产生 `14 + 15 + 15 + 14 = 58` 个 `(role_id, permission_id)` 唯一对，并固定占用
`2026072802000001`～`2026072802000058` 这 58 个迁移种子编号。禁止 `REPLACE INTO`；不得写 `app_user_role`，个人角色分配仍由 P0-A 注册/身份管理事务负责。

- [ ] **步骤 6（2–5 分钟）：重新运行集成测试并检查非零报告**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceSchemaIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=WorkspaceSchemaIT verify
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceSchemaIT 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'WorkspaceSchemaIT 未生成本次报告' }
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw 'WorkspaceSchemaIT 报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failures = [int]$report.testsuite.failures
$errors = [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
  throw 'WorkspaceSchemaIT 不是本次非零全绿报告'
}
$report.testsuite | Select-Object name, tests, failures, errors, skipped
```

预期：`WorkspaceSchemaIT` 报告 `tests > 0, failures=0, errors=0, skipped=0`，并且 Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 7（2–5 分钟）：提交数据库与测试基线**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml',
  'docs/sql/ai-video/mysql/20260728_02_p0b_workspace_authorization.sql',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceSchemaIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存数据库基线文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 建立工作区授权表与集成测试"
if ($LASTEXITCODE -ne 0) { throw '提交数据库基线失败' }
```

## 任务 3：实现 P0-B 领域实体、Mapper（数据映射器）与不透明工作区键

**最小任务卡：**

- **单一目标／不做：** 按 RuoYi 标准层建立授权 Entity、Mapper、五个稳定 DTO、内部资源归属 Service 和不透明键；不建立平行业务层、HTTP BO／VO 或第二套角色事实。
- **风险／触发：** 红色；命中授权凭据、公共 DTO、资源归属与密钥边界。
- **权威来源：** 主计划 P0-B 注册表、RuoYi skill／backend reference、原业务规格、本计划固定签名。
- **成功／反向验收：** 五个 DTO 与三个 `I...Service` 名称准确；键不可解码且绑定 actor；对象动作不含计费；core 禁止路径和重复角色类型零命中。
- **所有权／数据范围：** 仅本任务 `文件` 列出的 authorization domain／dto／mapper／service／security、XML 和测试；不修改 identity 事实。
- **依赖／人员／并发：** 依赖任务 2；开发 A 实施、开发 B 独立架构／授权审查，同一任务最多 2 人。
- **验证／检查点：** 先 review 五 DTO 与内部 Service 边界，再运行 RED／GREEN、精确 Surefire XML 和强负向包扫描。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AppOrganization.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AppOrgMember.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\AvResourceGrant.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AppOrganizationMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AppOrgMemberMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\mapper\AvResourceGrantMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\resources\mapper\aivideo\authorization\AppOrgMemberMapper.xml`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\resources\mapper\aivideo\authorization\AvResourceGrantMapper.xml`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\WorkspaceType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\OwnerType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\BillingSubjectType.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\domain\ResourceAction.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\WorkspaceContextDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\WorkspaceSummaryDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\ResourceOwnershipDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\ResourceDataScopeDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\dto\SwitchWorkspaceDTO.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\security\WorkspaceKeyService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IResourceOwnershipService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\ResourceOwnershipServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationServiceTest.java`

- [ ] **步骤 1（2–5 分钟）：编写工作区键和模型失败测试**

```java
@Tag("dev")
class WorkspaceAuthorizationServiceTest {
    private final WorkspaceKeyService keys =
        new WorkspaceKeyService("unit-test-workspace-key-secret-32-bytes");

    @Test
    void workspaceKeyIsOpaqueStableAndBoundToActor() {
        String first = keys.create(101L, WorkspaceType.ORGANIZATION, 9001L);
        String retry = keys.create(101L, WorkspaceType.ORGANIZATION, 9001L);
        String otherActor = keys.create(102L, WorkspaceType.ORGANIZATION, 9001L);

        assertThat(first).isEqualTo(retry);
        assertThat(first).isNotEqualTo(otherActor);
        assertThat(first).doesNotContain("101", "9001", "organization");
    }

    @Test
    void resourceActionNeverContainsBillingPermission() {
        assertThat(Arrays.stream(ResourceAction.values()).map(ResourceAction::code))
            .containsExactly("query", "edit", "generate", "confirm", "remove");
    }
}
```

- [ ] **步骤 2（2–5 分钟）：运行单元测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=WorkspaceAuthorizationServiceTest test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 WorkspaceAuthorizationServiceTest 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 WorkspaceAuthorizationServiceTest 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 WorkspaceAuthorizationServiceTest 至少一项真实执行并失败'
}
```

预期：先按统一约定建立签名骨架，测试成功编译并真正执行；至少一个工作区键或动作枚举业务断言失败。没有本次 Surefire XML 的测试编译失败不算红灯。

- [ ] **步骤 3（2–5 分钟）：实现最小领域类型和键服务**

核心记录使用不可变值：

```java
public record WorkspaceContextDTO(
    String workspaceKey,
    WorkspaceType workspaceType,
    Long tenantId,
    OwnerType ownerType,
    Long ownerId,
    Long actorUserId,
    BillingSubjectType billingSubjectType,
    Long billingSubjectId,
    String roleCode,
    Set<String> permissions,
    Long workspaceRevision,
    Long membershipRevision
) {
    public WorkspaceContextDTO {
        permissions = Set.copyOf(permissions);
    }

    public String organizationRoleGrantKey() {
        return workspaceType == WorkspaceType.ORGANIZATION
            ? ownerId + ":" + roleCode
            : "";
    }
}

public record ResourceOwnershipDTO(
    String resourceType,
    Long resourceId,
    Long tenantId,
    OwnerType ownerType,
    Long ownerId,
    Long createdByUserId
) {}

public interface IResourceOwnershipService {
    ResourceOwnershipDTO requireOwnership(String resourceType, Long resourceId);
}
```

`WorkspaceKeyService` 使用 `HmacSHA256` 计算
`v1|actorUserId|workspaceType|ownerId`，截取前 24 字节并 Base64 URL 无填充编码；切换时只与当前用户重新查询出的候选键做常量时间比较，不解码客户端输入。密钥从 `AIVIDEO_WORKSPACE_KEY_SECRET` 注入，生产环境为空时启动失败。`IResourceOwnershipService` 通过本聚合 Mapper 查询并返回 `ResourceOwnershipDTO`，不得创建 repository／resolver 平行业务层。

本任务只实现 `AppOrganization`、`AppOrgMember`、`AvResourceGrant` 三个实体；它们不继承依赖运营端 `LoginHelper` 自动填充的 `BaseEntity`，明确保存 `createdActorType/createdActorId` 与 `updatedActorType/updatedActorId`。`AvResourceGrant` 另存不可变的 `grantedByType/grantedById`，禁止退化为无法区分同号主体的 `grantedByUserId`。角色校验导入 P0-A 的 `identity.domain.AppRole/AppRolePermission/AppPermission`，不得在 `authorization.domain` 生成同名类型。

- [ ] **步骤 4（2–5 分钟）：运行单元测试与编译**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=WorkspaceAuthorizationServiceTest test
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceAuthorizationServiceTest 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'WorkspaceAuthorizationServiceTest 未生成本次报告' }
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw 'WorkspaceAuthorizationServiceTest 报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failures = [int]$report.testsuite.failures
$errors = [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
  throw 'WorkspaceAuthorizationServiceTest 不是本次非零全绿报告'
}
$report.testsuite | Select-Object name, tests, failures, errors, skipped
```

预期：两个测试通过，Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 5（2–5 分钟）：提交领域和持久层**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/AppOrganization.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/AppOrgMember.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/AvResourceGrant.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/mapper/AppOrganizationMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/mapper/AppOrgMemberMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/mapper/AvResourceGrantMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/authorization/AppOrgMemberMapper.xml',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/authorization/AvResourceGrantMapper.xml',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/WorkspaceType.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/OwnerType.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/BillingSubjectType.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/domain/ResourceAction.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/dto/WorkspaceContextDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/dto/WorkspaceSummaryDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/dto/ResourceOwnershipDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/dto/ResourceDataScopeDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/dto/SwitchWorkspaceDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/security/WorkspaceKeyService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/IResourceOwnershipService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/impl/ResourceOwnershipServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceAuthorizationServiceTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存授权实体与服务文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 建立工作区授权实体与服务契约"
if ($LASTEXITCODE -ne 0) { throw '提交授权实体与服务契约失败' }
```

### Task4 跨计划同步硬前置

开始任务 4 的任何业务实现前，契约 owner 必须以独立文档同步提交更新主计划 Task 3，并经独立 reviewer 验收：同时登记 `WorkspaceSwitchAdmissionProofStore`、`AppWorkspaceSwitchAdmissionConsumer`、`AppSessionIntegrationTestFixture.java`、既有 `AppSessionWorkspaceInvalidationIT.java`，固定总计八个 IT，并同步对应报告选择器、静态证据选择器、文件 owner 与精确暂存清单。本轮六计划最终一致性收口必须验证该同步提交已体现在最终计划；任一项缺失时任务 4 必须阻塞，不得先写业务代码、测试骨架或暂存文件。当前 P0-B 提交不得越权修改主计划。

## 任务 4：实现个人/组织工作区解析、切换与修订失效

**最小任务卡：**

- **单一目标／不做：** 让服务端规范的个人／组织快照安全切换现行会话；不改变 `IAppSessionService` 签名，不接受客户端会话事实，不在拒绝后调用 `replaceWorkspace`。
- **风险／触发：** 红色；命中会话、身份、组织授权、伪造凭据和跨账号。
- **权威来源：** P0-A `IAppSessionService`／DTO／`AppSessionServiceImpl` 现状、并行规格 F0、本计划组织会话信任边界。
- **成功／反向验收：** 个人行为保持；合法组织快照只能经一次性准入证明替换；无证明、伪造／篡改证明、跨账号、跨会话、过期、并发重复消费、退出／禁用／过期成员、组织停用、修订过期全部返回 `46126` 且会话零副作用；共享 Redis 下节点 A 签发、节点 B 仅能消费一次。
- **所有权／数据范围：** 本任务 `文件` 列出的 authorization Service／证明存储／测试，以及精确的 identity 消费端、`AppSessionServiceImpl.java`、`AppSessionServiceImplTest.java`、`AppSessionIntegrationTestFixture.java`、既有 `AppSessionWorkspaceInvalidationIT.java`；不改 P0-A 表、`IAppSessionService` 或既有 DTO 公开签名。
- **依赖／人员／并发：** 依赖任务 3 和 F0；开发 A 实施、开发 B 独立会话／授权安全审查，同一任务最多 2 人。
- **验证／检查点：** RED 先证明当前组织快照受阻和全部拒绝零副作用；GREEN 再证明服务端组织快照与修订失效；核对本次 Surefire／Failsafe XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRoleMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRolePermissionMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppPermissionMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IWorkspaceAuthorizationService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceAuthorizationServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceSwitchAdmissionProofStore.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\security\AppWorkspaceSwitchAdmissionConsumer.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImpl.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImplTest.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionIntegrationTestFixture.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationServiceTest.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationIT.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionWorkspaceInvalidationIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的解析和切换测试**

```java
@Test
void organizationSnapshotIsRejectedBeforeP0bExtension() {
    AppWorkspaceSessionSnapshotDTO organization = validOrganizationSnapshot();

    assertThatThrownBy(() -> appSessionService.replaceWorkspace(organization))
        .isInstanceOf(ServiceException.class);
}

@Test
void switchRejectsForgedWorkspaceKeyAndDoesNotReplaceSession() {
    when(appLoginHelper.getPrincipal()).thenReturn(principalFor(101L));

    assertWorkspaceUnavailable(() -> service.switchCurrentWorkspace(
        new SwitchWorkspaceDTO("forged-key"), AppActorContext.appUser(101L)));

    verify(appSessionService, never()).replaceWorkspace(any());
}

@Test
void switchRejectsCrossAccountActorAndDoesNotReplaceSession() {
    when(appLoginHelper.getPrincipal()).thenReturn(principalFor(101L));
    String key = validOrganizationKeyFor(101L, 7L);

    assertWorkspaceUnavailable(() -> service.switchCurrentWorkspace(
        new SwitchWorkspaceDTO(key), AppActorContext.appUser(102L)));

    verify(appSessionService, never()).replaceWorkspace(any());
}

@ParameterizedTest
@MethodSource("unavailableMemberships")
void switchRejectsLeftDisabledOrExpiredMembershipAndDoesNotReplaceSession(
    AppOrgMember member) {
    when(appLoginHelper.getPrincipal()).thenReturn(principalFor(101L));
    when(memberMapper.selectByOrganizationAndUser(7L, 101L)).thenReturn(member);

    assertWorkspaceUnavailable(() -> service.switchCurrentWorkspace(
        new SwitchWorkspaceDTO(validOrganizationKeyFor(101L, 7L)),
        AppActorContext.appUser(101L)));

    verify(appSessionService, never()).replaceWorkspace(any());
}

@Test
void switchRejectsStaleMembershipAndDoesNotReplaceSession() {
    when(appLoginHelper.getPrincipal())
        .thenReturn(principalForOrganization(101L, 7L, 11L));
    when(memberMapper.selectByOrganizationAndUserForUpdate(7L, 101L))
        .thenReturn(activeMemberWithRevision(12L));

    assertWorkspaceUnavailable(() -> service.switchCurrentWorkspace(
        new SwitchWorkspaceDTO(validOrganizationKeyFor(101L, 7L)),
        AppActorContext.appUser(101L)));

    verify(proofStore, never()).issue(any(), any(), any());
    verify(appSessionService, never()).replaceWorkspace(any());
}

@Test
void resolveListAndSwitchCheckRevisionBeforeReadingPrincipalOrFacts() {
    RuntimeException stale = staleSession();
    doThrow(stale).when(appSessionRevisionGuard).checkCurrentSession();

    assertThatThrownBy(service::resolveCurrentWorkspace).isSameAs(stale);
    assertThatThrownBy(service::listAvailableWorkspaces).isSameAs(stale);
    assertThatThrownBy(() -> service.switchCurrentWorkspace(validSwitch(), appActor()))
        .isSameAs(stale);

    InOrder order = inOrder(appSessionRevisionGuard, appLoginHelper);
    order.verify(appSessionRevisionGuard, times(3)).checkCurrentSession();
    verifyNoInteractions(appLoginHelper, organizationMapper, memberMapper,
        identityRoleMapper, identityRolePermissionMapper, identityPermissionMapper,
        proofStore, appSessionService);
}

@Test
void personalWorkspaceConsumesP0aPermissionService() {
    when(appPermissionService.roleCodes(101L))
        .thenReturn(Set.of("personal_creator"));
    when(appPermissionService.permissionCodes(101L))
        .thenReturn(Set.of("aivideo:studio:query"));

    WorkspaceSummaryDTO personal = service.listAvailableWorkspaces().getFirst();

    assertThat(personal.roleCode()).isEqualTo("personal_creator");
    assertThat(personal.permissions()).containsExactly("aivideo:studio:query");
    verify(appPermissionService).roleCodes(101L);
    verify(appPermissionService).permissionCodes(101L);
}

@Test
void organizationWorkspaceConsumesP0aIdentityMappers() {
    when(identityRoleMapper.selectOne(any()))
        .thenReturn(identityRole(31L, "organization_member"));
    when(identityRolePermissionMapper.selectList(any()))
        .thenReturn(List.of(identityRolePermission(31L, 411L)));
    when(identityPermissionMapper.selectList(any()))
        .thenReturn(List.of(identityPermission(
            411L, "aivideo:studio:query")));

    WorkspaceSummaryDTO organization = service.listAvailableWorkspaces().stream()
        .filter(item -> item.workspaceType() == WorkspaceType.ORGANIZATION)
        .findFirst()
        .orElseThrow();

    assertThat(organization.permissions())
        .containsExactly("aivideo:studio:query");
}
```

`AppSessionServiceImplTest` 先证明现状只接受 `canonicalPersonalWorkspace`，形成组织快照扩展的有效 RED。`WorkspaceAuthorizationServiceTest` 的 `unavailableMemberships` 只提供 `left`、`suspended`、超过 `validUntil` 三类成员；修订过期由独立的 `switchRejectsStaleMembershipAndDoesNotReplaceSession` 覆盖，所有拒绝用例都显式验证证明零签发、`replaceWorkspace` 零调用。三个入口各自增加 Mockito `InOrder` 测试，严格证明 `checkCurrentSession()` 是第一条调用，且 guard 失败时 principal、工作区 Mapper、证明存储和会话服务零交互。`WorkspaceAuthorizationIT` 与既有 `AppSessionWorkspaceInvalidationIT` 均在类级显式标注 `@Tag("dev")`，真实 SQL 数据使用
P0-A 的 `identity` 三个 Mapper；测试源码不得声明
`authorization.mapper.AppRoleMapper/AppRolePermissionMapper/AppUserRoleMapper`。

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reports = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.identity.service.impl.AppSessionServiceImplTest.xml'
)
$reports | ForEach-Object { Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue }
$redStartedAt = (Get-Date).ToUniversalTime()
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  '-Dtest=WorkspaceAuthorizationServiceTest,AppSessionServiceImplTest' test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到组织会话／授权目标测试真实失败' }
$failed = 0
foreach ($reportPath in $reports) {
  if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) { throw "红灯目标报告缺失：$reportPath" }
  $reportItem = Get-Item -LiteralPath $reportPath
  if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw "红灯报告不是本次生成：$reportPath" }
  [xml]$report = Get-Content -Raw -LiteralPath $reportPath
  $tests = [int]$report.testsuite.tests
  $reportFailed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $skipped -ge $tests) { throw "红灯目标没有真实执行：$reportPath" }
  $failed += $reportFailed
}
if ($failed -lt 1) { throw '红灯必须至少有一个组织会话／授权业务断言真实失败' }
```

预期：两个测试类均真正执行，至少一个因组织快照扩展或服务端成员事实校验尚未完成而失败；测试编译、依赖解析或测试发现失败不算有效 RED。

- [ ] **步骤 3（2–5 分钟）：实现最小工作区闭环**

`resolveCurrentWorkspace()` 的首段固定为：

```java
@Override
public WorkspaceContextDTO resolveCurrentWorkspace() {
    appSessionRevisionGuard.checkCurrentSession();
    AppPrincipalSnapshotDTO principal = appLoginHelper.getPrincipal();
    AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
    if ("organization".equals(workspace.workspaceType())) {
        AppOrganization organization =
            organizationMapper.selectActiveById(workspace.ownerId());
        AppOrgMember member = memberMapper.selectActive(
            workspace.ownerId(), principal.appUserId(), LocalDateTime.now());
        if (organization == null
            || member == null
            || !Objects.equals(organization.getOrganizationRevision(),
                workspace.workspaceRevision())
            || !Objects.equals(member.getMembershipRevision(),
                workspace.membershipRevision())) {
            throw new ServiceException("当前组织工作区已失效，请重新选择工作区", 46126);
        }
    }
    return toContext(principal, workspace);
}
```

`resolveCurrentWorkspace()`、`listAvailableWorkspaces()`、`switchCurrentWorkspace(...)` 都必须把 `appSessionRevisionGuard.checkCurrentSession()` 作为第一条可观察调用，之后才读取 principal、工作区或 Mapper；guard 失败立即透传既有会话失效错误，后续交互为零。`listAvailableWorkspaces()` 每次从 P0-A 当前用户、有效组织、有效成员和角色权限重新构造个人项及组织项。`switchCurrentWorkspace(SwitchWorkspaceDTO request, AppActorContext actor)` 只接受 `actorType=app_user`，并在任何写入前交叉校验 `actorId` 等于当前 `AppPrincipalSnapshotDTO.appUserId`；随后只在本次服务端重新生成的候选列表中以常量时间匹配 `request.workspaceKey()`。组织候选必须在同一短事务内按固定顺序锁定并重读 active 组织、active 且未过期成员、组织角色、角色权限映射与权限事实，校验最新 `organizationRevision` 和 `membershipRevision`，再构造完整规范 `AppWorkspaceSessionSnapshotDTO`。任何不一致先抛 `46126`，不得签发证明或调用会话服务。

组织切换采用共享 Redis 的 **5 秒 TTL 一次性 admission proof（准入证明）**，公开 `IAppSessionService.replaceWorkspace(...)` 与 `AppWorkspaceSessionSnapshotDTO` 签名保持不变：

1. `identity/security/AppWorkspaceSwitchAdmissionConsumer` 只公开 `consumeOrThrow(AppLoginUser, AppPrincipalSnapshotDTO, AppWorkspaceSessionSnapshotDTO)`；identity 侧看不到签发能力。
2. `authorization/service/impl/WorkspaceSwitchAdmissionProofStore` 是 package-private `final class`，package-private `issue(...)`／`discard(...)` 只能由同包 `WorkspaceAuthorizationServiceImpl` 调用，同时实现上述 consumer；它只依赖 `RedissonClient` 和从现有工作区密钥做域隔离派生的证明密钥，不建立反向业务依赖。
3. 证明绑定当前 `sessionId`、`appUserId`、principal 的 `credentialRevision`／`identityRevision`／`permissionRevision`／`clientRevision`，以及完整组织快照摘要；摘要覆盖全部字段，权限排序后编码，所有变长字段采用长度前缀，禁止歧义字符串拼接。Redis key 使用绑定内容摘要，value 包含随机 nonce、绝对过期时刻和 HMAC；签发使用 `SET NX PX 5000`。
4. 授权服务在锁定事实并构造快照后 `issue`，立即调用一次 `replaceWorkspace`，并在 `finally` 执行 `discard` 清除未消费证明；Redis 失败一律失败关闭，绝不退化为结构校验。
5. `AppSessionServiceImpl` 的组织分支先通过 Redis `GETDEL` 原子取出并销毁证明，再以常量时间验证 HMAC、会话、用户、四类主体修订和完整快照摘要。无证明、过期、篡改、跨账号、跨会话或重放统一抛 `46126`，且登录态和在线索引零修改；证明成功消费后若会话写入失败也不得恢复，重试必须重新走授权服务。

bean 图固定为 `WorkspaceAuthorizationServiceImpl -> IAppSessionService + WorkspaceSwitchAdmissionProofStore`、`AppSessionServiceImpl -> AppWorkspaceSwitchAdmissionConsumer`、`WorkspaceSwitchAdmissionProofStore -> RedissonClient/密钥配置`，不得形成 Spring 循环依赖。生产静态边界要求 `issue(...)` 调用点恰好一个，user/platform Controller 不得注入 `IAppSessionService` 或接收完整工作区快照 DTO。

`AppSessionServiceImpl.replaceWorkspace` 的个人分支继续调用既有 `canonicalPersonalWorkspace`；组织分支的结构校验只保留为纵深防御，不能替代一次性证明。`AppSessionServiceImplTest` 同时保留原个人回归，并新增缺少证明、证明字段篡改、跨会话、重复消费、消费后会话写失败不可恢复和个人路径不受影响测试。既有 `AppSessionWorkspaceInvalidationIT` 必须保留“直接构造组织 DTO 调用会话服务返回 `46126`”的防伪断言，并增加经 `IWorkspaceAuthorizationService` 的唯一合法路径；再覆盖过期证明、同一证明并发消费仅一次成功、节点 A 签发／节点 B 消费、成功后任一节点重放失败。禁止 `ThreadLocal` 或本机内存保存证明，多实例共享状态只在 Redis。

`AppSessionIntegrationTestFixture` 不得继续以缺少 consumer 的旧构造器手工创建 `AppSessionServiceImpl`。fixture 必须连接 `LocalIntegrationEnvironment` 提供的真实 Redis，装配同一域隔离密钥下的 `WorkspaceSwitchAdmissionProofStore` bean，把它按 `AppWorkspaceSwitchAdmissionConsumer` 注入会话服务，并暴露真实 `IWorkspaceAuthorizationService` 合法入口；测试只能经授权服务触发 package-private `issue`，不得 mock、反射调用或自造证明。跨节点用两个独立 fixture／应用上下文和不同本机对象、同一 Redis／密钥，证明 A 签发可由 B 原子消费。该 fixture 变更和既有 IT 都属于 Task4 精确所有权；契约 owner 必须在未来业务 Task 4 开始前，以独立文档同步提交更新主计划 Task 3 的所有权、八个 IT、新增证明存储／消费端、fixture、既有 IT、报告选择器、静态证据选择器和暂存清单，并由独立 reviewer 验收。本轮六计划最终一致性收口只验证该提交已经体现在最终计划；缺失即阻塞。当前 P0-B 提交不得越权修改主计划。

个人项的角色和权限分别调用 `appPermissionService.roleCodes(userId)` 与
`appPermissionService.permissionCodes(userId)`。组织项以
`AppOrgMember.getRoleCode()` 查询 P0-A `identityRoleMapper`，再用返回的
`roleId` 查询 `identityRolePermissionMapper` 的有效映射，最后按
`permissionId` 批量查询 `identityPermissionMapper` 的有效权限代码；角色作用域必须是
`organization`。任一阶段为空即返回空权限集，`requireWorkspacePermission` 稳定抛
`46127`，不能以个人权限、运营权限或写死映射兜底。

本包不加授权读缓存；每次请求读取修订事实，因此成员/组织变更即时生效。

- [ ] **步骤 4（2–5 分钟）：运行单元和数据库集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$unitReports = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.identity.service.impl.AppSessionServiceImplTest.xml'
)
$unitReports | ForEach-Object { Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue }
$unitStartedAt = (Get-Date).ToUniversalTime()
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  '-Dtest=WorkspaceAuthorizationServiceTest,AppSessionServiceImplTest' test
if ($LASTEXITCODE -ne 0) { throw 'P0-B 工作区／会话精确单元测试失败' }
foreach ($unitReportPath in $unitReports) {
  if (-not (Test-Path -LiteralPath $unitReportPath -PathType Leaf)) { throw "单元测试未生成本次报告：$unitReportPath" }
  $unitReportItem = Get-Item -LiteralPath $unitReportPath
  if ($unitReportItem.LastWriteTimeUtc -lt $unitStartedAt) { throw "单元测试报告不是本次生成：$unitReportPath" }
  [xml]$unitReport = Get-Content -Raw -LiteralPath $unitReportPath
  $unitTests = [int]$unitReport.testsuite.tests
  $unitFailures = [int]$unitReport.testsuite.failures
  $unitErrors = [int]$unitReport.testsuite.errors
  $unitSkipped = [int]$unitReport.testsuite.skipped
  if ($unitTests -lt 1 -or $unitFailures -ne 0 -or $unitErrors -ne 0 -or $unitSkipped -ne 0) {
    throw "单元测试不是本次非零全绿报告：$unitReportPath"
  }
}
$itReportPaths = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.identity.AppSessionWorkspaceInvalidationIT.xml'
)
$itReportPaths | ForEach-Object { Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue }
$itStartedAt = (Get-Date).ToUniversalTime()
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test='WorkspaceAuthorizationIT,AppSessionWorkspaceInvalidationIT' verify
if ($LASTEXITCODE -ne 0) { throw '工作区授权／会话防伪 IT 绿灯命令失败' }
foreach ($itReportPath in $itReportPaths) {
  if (-not (Test-Path -LiteralPath $itReportPath)) { throw "集成测试未生成本次报告：$itReportPath" }
  $itReportItem = Get-Item -LiteralPath $itReportPath
  if ($itReportItem.LastWriteTimeUtc -lt $itStartedAt) { throw "集成测试报告不是本次命令生成：$itReportPath" }
  [xml]$itReport = Get-Content -Raw -LiteralPath $itReportPath
  $itTests = [int]$itReport.testsuite.tests
  $itFailures = [int]$itReport.testsuite.failures
  $itErrors = [int]$itReport.testsuite.errors
  $itSkipped = [int]$itReport.testsuite.skipped
  if ($itTests -lt 1 -or $itFailures -ne 0 -or $itErrors -ne 0 -or $itSkipped -ne 0) {
    throw "工作区授权／会话防伪 IT 不是本次非零全绿报告：$itReportPath"
  }
  $itReport.testsuite | Select-Object name, tests, failures, errors, skipped
}
```

预期：单元测试覆盖 guard-first、个人默认、P0-A 服务／Mapper 复用、合法组织快照、伪造键、跨账号、退出／禁用／过期／过时成员、异常组织快照、组织停用和证明消费失败；授权拒绝路径证明证明零签发且 `replaceWorkspace` 零调用。两份 IT 报告均为 `tests > 0, failures=0, errors=0, skipped=0`，并覆盖共享 Redis 的并发、重放和跨节点行为。

- [ ] **步骤 5（2–5 分钟）：提交工作区服务**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/IWorkspaceAuthorizationService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/impl/WorkspaceAuthorizationServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/impl/WorkspaceSwitchAdmissionProofStore.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/security/AppWorkspaceSwitchAdmissionConsumer.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImplTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionIntegrationTestFixture.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceAuthorizationServiceTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceAuthorizationIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/AppSessionWorkspaceInvalidationIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存工作区服务文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 实现个人与组织工作区切换"
if ($LASTEXITCODE -ne 0) { throw '提交工作区服务失败' }
```

## 任务 5：实现角色权限、对象动作授权和 SQL（结构化查询语言）数据范围

**最小任务卡：**

- **单一目标／不做：** 实现工作区权限、对象动作与 SQL 数据范围；不把对象授权当计费权限，不在 Java 全量查询后过滤。
- **风险／触发：** 红色；命中授权、跨账号数据、资源归属和 SQL 数据范围。
- **权威来源：** 原业务规格授权顺序、主计划 P0-B 注册表、本计划固定 Service／DTO 与角色权限事实源。
- **成功／反向验收：** personal／owner／admin／member 范围准确；显式授权、过期授权、跨租户和组织 A／B 隔离通过；`generate` 不授予 `aivideo:quota:use`。
- **所有权／数据范围：** 仅本任务 `文件` 列出的 Service 实现、Mapper XML 和授权测试；不修改 P0-A 权限表结构。
- **依赖／人员／并发：** 依赖任务 4；开发 A 实施、开发 B 独立授权／数据审查，同一任务最多 2 人。
- **验证／检查点：** 先 review 固定判定顺序与 SQL 条件，再运行 RED／GREEN 和本机 IT；核对跨账号与跨组织反向证据。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRoleMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRolePermissionMapper.java`
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppPermissionMapper.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceAuthorizationServiceImpl.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\resources\mapper\aivideo\authorization\AvResourceGrantMapper.xml`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationServiceTest.java`
- 修改：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的授权矩阵测试**

```java
@ParameterizedTest
@CsvSource({
    "personal,personal_creator,false,false",
    "organization,organization_owner,true,false",
    "organization,organization_admin,true,false",
    "organization,organization_member,false,true"
})
void implicitScopeAndGrantRequirementAreFixed(
    String workspaceType, String roleCode,
    boolean organizationWide, boolean creatorGrantRequired) {
    WorkspaceContextDTO context = context(workspaceType, roleCode);
    ResourceDataScopeDTO scope = service.resolveDataScope("script_draft", "query");
    assertThat(scope.organizationWide()).isEqualTo(organizationWide);
    assertThat(scope.creatorGrantRequired()).isEqualTo(creatorGrantRequired);
}

@Test
void objectGenerateGrantDoesNotConferBillingPermission() {
    when(grantMapper.existsActiveGrant(any(), eq("generate"))).thenReturn(true);
    when(identityRoleMapper.selectOne(any()))
        .thenReturn(identityRole(31L, "organization_member"));
    when(identityRolePermissionMapper.selectList(any()))
        .thenReturn(List.of(identityRolePermission(31L, 410L)));
    when(identityPermissionMapper.selectList(any()))
        .thenReturn(List.of(identityPermission(
            410L, "aivideo:studio:generate")));

    service.requireResourceAction(
        organizationResource("script_draft", 81L, 7L, 1001L), "generate");
    assertThatThrownBy(() ->
        service.requireWorkspacePermission("aivideo:quota:use"))
        .isInstanceOf(ServiceException.class)
        .extracting("code")
        .isEqualTo(46127);
}

@Test
void creatorGrantKeepsTypedAppActorWhenSysUserHasSameNumericId() {
    AppActorContext appActor = AppActorContext.appUser(1001L);
    ResourceOwnershipDTO resource = organizationResource(
        "script_draft", 81L, 7L, 1001L);

    service.initializeCreatorGrant(resource, appActor);

    ArgumentCaptor<AvResourceGrant> captor =
        ArgumentCaptor.forClass(AvResourceGrant.class);
    verify(resourceGrantMapper, times(5)).insertIgnore(captor.capture());
    assertThat(captor.getAllValues()).allSatisfy(grant -> {
        assertThat(grant.getGrantedByType()).isEqualTo("app_user");
        assertThat(grant.getGrantedById()).isEqualTo(1001L);
        assertThat(grant.getCreatedActorType()).isEqualTo("app_user");
        assertThat(grant.getCreatedActorId()).isEqualTo(1001L);
        assertThat(grant.getUpdatedActorType()).isEqualTo("app_user");
        assertThat(grant.getUpdatedActorId()).isEqualTo(1001L);
    });
}

@Test
void creatorGrantRejectsSysActorAndMismatchedAppActorBeforeWriting() {
    ResourceOwnershipDTO resource = organizationResource(
        "script_draft", 81L, 7L, 1001L);

    assertThatThrownBy(() -> service.initializeCreatorGrant(
        resource, AppActorContext.sysUser(1001L)))
        .isInstanceOf(ServiceException.class)
        .extracting("code")
        .isEqualTo(46127);
    assertThatThrownBy(() -> service.initializeCreatorGrant(
        resource, AppActorContext.appUser(1002L)))
        .isInstanceOf(ServiceException.class)
        .extracting("code")
        .isEqualTo(46127);

    verifyNoInteractions(resourceGrantMapper);
}
```

该测试只模拟 P0-A `identity` Mapper；测试字段名不得出现本地
`rolePermissionMapper` 或 `userRoleMapper`。

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=WorkspaceAuthorizationServiceTest test
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 WorkspaceAuthorizationServiceTest 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 WorkspaceAuthorizationServiceTest 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 WorkspaceAuthorizationServiceTest 至少一项真实执行并失败'
}
```

预期：隐式范围、独立计费权限或对象授权断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小授权算法和 SQL**

`requireResourceAction` 必须按以下分支实现：

调用方先通过同聚合内部 `IResourceOwnershipService.requireOwnership(resourceType, resourceId)` 取得服务端 `ResourceOwnershipDTO`，再把 DTO 传给稳定授权入口；Controller 不得从请求体拼装该 DTO。

```java
WorkspaceContextDTO workspace = resolveCurrentWorkspace();
ResourceOwnershipDTO ownership = resource;
requireSameTenant(workspace, ownership);

boolean allowed = ownership.ownerType() == OwnerType.PERSONAL
    ? Objects.equals(ownership.ownerId(), workspace.actorUserId())
    : Objects.equals(ownership.ownerId(), workspace.ownerId())
        && Set.of("organization_owner", "organization_admin")
            .contains(workspace.roleCode());

if (!allowed) {
    allowed = resourceGrantMapper.existsActiveGrant(
        workspace.tenantId(),
        ownership.resourceType(),
        ownership.resourceId(),
        Long.toString(workspace.actorUserId()),
        workspace.organizationRoleGrantKey(),
        action,
        LocalDateTime.now());
}
if (!allowed) {
    throw new ServiceException("当前用户没有该资源操作权限", 46102);
}
```

`AvResourceGrantMapper.xml` 的列表片段直接附加：

```xml
<sql id="visibleResourcePredicate">
    AND r.tenant_id = #{scope.tenantId}
    AND (
        (r.owner_type = 'personal' AND r.owner_id = #{scope.actorUserId})
        OR (
            r.owner_type = 'organization'
            AND r.owner_id = #{scope.ownerId}
            AND #{scope.organizationWide} = TRUE
        )
        OR EXISTS (
            SELECT 1
            FROM av_resource_grant g
            WHERE g.tenant_id = r.tenant_id
              AND g.resource_type = #{scope.resourceType}
              AND g.resource_id = r.id
              AND g.action_code = #{scope.actionCode}
              AND g.status = 'active'
              AND (g.valid_until IS NULL OR g.valid_until > CURRENT_TIMESTAMP)
              AND (
                  (g.grantee_type = 'user'
                   AND g.grantee_key = CAST(#{scope.actorUserId} AS CHAR))
                  OR
                   (g.grantee_type = 'organization_role'
                    AND g.grantee_key = #{scope.organizationRoleGrantKey})
              )
        )
    )
</sql>
```

组织角色授权的 `grantee_key` 固定为 `${organizationId}:${roleCode}`，绝不能只存全局角色代码；用户授权则存十进制用户编号。`initializeCreatorGrant(resource, actor)` 只接受 `actorType=app_user` 且 `actorId=resource.createdByUserId`，仅为组织资源的实际创建用户逐项写入五个动作，唯一键冲突读取原记录；个人所有者不写冗余授权。`inheritResourceGrants(source, target, actor)` 同样显式接收并验证当前强类型 actor，只复制当前有效动作，并保存来源资源。每次插入同时写准确 `grantedByType/grantedById`、创建 actor 和更新 actor；重复授权只刷新更新 actor，不覆盖首次授权人。SQL 集成测试另建同租户第二个组织，断言相同 `organization_member` 角色不能借用第一组织的角色授权；同号 `app_user=1001` 与 `sys_user=1001` 的授权/组织审计按 actor type 可区分。

`requireWorkspacePermission` 不持有 P0-B 角色权限 Mapper：个人工作区重新调用
`IAppPermissionService.permissionCodes(actorUserId)`；组织工作区重新执行任务 4 的
P0-A `identity` 三 Mapper 查询。`WorkspaceAuthorizationIT` 保持类级
`@Tag("dev")`，并断言从 `app_role_permission` 删除 `organization_member` 的
`aivideo:quota:use` ID 映射后，对象 `generate` 授权仍通过而计费权限返回 `46127`。

- [ ] **步骤 4（2–5 分钟）：运行授权单元与 SQL 集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$unitReportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml'
Remove-Item -LiteralPath $unitReportPath -Force -ErrorAction SilentlyContinue
$unitStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=WorkspaceAuthorizationServiceTest test
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceAuthorizationServiceTest 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $unitReportPath)) { throw '单元测试未生成本次报告' }
$unitReportItem = Get-Item -LiteralPath $unitReportPath
if ($unitReportItem.LastWriteTimeUtc -lt $unitStartedAt) { throw '单元测试报告不是本次命令生成' }
[xml]$unitReport = Get-Content -Raw -LiteralPath $unitReportPath
$unitTests = [int]$unitReport.testsuite.tests
$unitFailures = [int]$unitReport.testsuite.failures
$unitErrors = [int]$unitReport.testsuite.errors
$unitSkipped = [int]$unitReport.testsuite.skipped
if ($unitTests -lt 1 -or $unitFailures -ne 0 -or $unitErrors -ne 0 -or $unitSkipped -ne 0) {
  throw 'WorkspaceAuthorizationServiceTest 不是本次非零全绿报告'
}
$itReportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationIT.xml'
Remove-Item -LiteralPath $itReportPath -Force -ErrorAction SilentlyContinue
$itStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=WorkspaceAuthorizationIT verify
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceAuthorizationIT 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $itReportPath)) { throw '集成测试未生成本次报告' }
$itReportItem = Get-Item -LiteralPath $itReportPath
if ($itReportItem.LastWriteTimeUtc -lt $itStartedAt) { throw '集成测试报告不是本次命令生成' }
[xml]$itReport = Get-Content -Raw -LiteralPath $itReportPath
$itTests = [int]$itReport.testsuite.tests
$itFailures = [int]$itReport.testsuite.failures
$itErrors = [int]$itReport.testsuite.errors
$itSkipped = [int]$itReport.testsuite.skipped
if ($itTests -lt 1 -or $itFailures -ne 0 -or $itErrors -ne 0 -or $itSkipped -ne 0) {
  throw 'WorkspaceAuthorizationIT 不是本次非零全绿报告'
}
$unitReport.testsuite, $itReport.testsuite |
  Select-Object name, tests, failures, errors, skipped
```

预期：个人、owner、admin、member、显式授权、授权过期、跨租户和 `generate`/`use_billing` 分离矩阵全部通过；Failsafe 报告 `tests > 0, failures=0, errors=0, skipped=0`。

- [ ] **步骤 5（2–5 分钟）：提交对象授权**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/impl/WorkspaceAuthorizationServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/authorization/AvResourceGrantMapper.xml',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceAuthorizationServiceTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/WorkspaceAuthorizationIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存对象授权文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 实现对象授权与工作区数据范围"
if ($LASTEXITCODE -ne 0) { throw '提交对象授权失败' }
```

## 任务 6：实现运营端组织与成员事务

**最小任务卡：**

- **单一目标／不做：** 实现运营端组织／成员事务、乐观修订、八字段安全审计，以及提交后会话失效与有界恢复；不让 core 依赖平台 BO／VO，不物理删除成员历史，不把 Redis 调用放进数据库事务。
- **风险／触发：** 红色；命中高权限运营、跨用户写入、审计、会话与并发修订。
- **权威来源：** 原业务规格、P0-A `IAppSecurityAuditService`／`AppSecurityAuditDTO`、本计划 `IOrganizationAdminService` 边界。
- **成功／反向验收：** sys actor 成功且同号 app actor 在查询／锁／写前拒绝；最后 owner 受保护；组织／成员修订递增；审计字段恰好八个；事务内审计异常使数据库完整回滚；提交后监听器失效失败不回滚已提交数据库，而是记录结构化失败、让 guard 在下一次受保护请求拒绝旧 app token，并可经有界恢复清理在线索引；同号 sys session 不受影响。
- **所有权／数据范围：** 本任务 `文件` 列出的 core Service／测试；平台 BO／VO 由任务 8 拥有；只影响目标组织、成员和安全审计追加行。
- **依赖／人员／并发：** 依赖任务 5；开发 A 实施、开发 B 独立高权限／并发审查，同一任务最多 2 人。
- **验证／检查点：** 先 review actor 第一语句守卫和审计 DTO，再运行 `OrganizationAdminServiceIT` RED／GREEN 与本次 Failsafe XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 只读复用：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper\AppRoleMapper.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IOrganizationAdminService.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\OrganizationAdminServiceImpl.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\event\OrganizationSessionInvalidationRequested.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\listener\OrganizationSessionInvalidationListener.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\OrganizationAdminServiceIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的组织事务与失效测试**

```java
@Test
void createOrganizationCreatesOneOwnerMembershipAndAudit() {
    AppActorContext actor = AppActorContext.sysUser(1001L);
    Long organizationId = service.create(
        "北辰内容工作室", 101L, "受控初始化组织", actor);

    assertThat(organizationMapper.selectById(organizationId))
        .satisfies(organization -> {
            assertThat(organization.getCreatedActorType()).isEqualTo("sys_user");
            assertThat(organization.getCreatedActorId()).isEqualTo(1001L);
            assertThat(organization.getUpdatedActorType()).isEqualTo("sys_user");
            assertThat(organization.getUpdatedActorId()).isEqualTo(1001L);
        });
    assertThat(memberMapper.selectByOrganizationId(organizationId))
        .singleElement()
        .satisfies(member -> {
            assertThat(member.getAppUserId()).isEqualTo(101L);
            assertThat(member.getRoleCode()).isEqualTo("organization_owner");
            assertThat(member.getStatus()).isEqualTo("active");
            assertThat(member.getMembershipRevision()).isEqualTo(1L);
            assertThat(member.getCreatedActorType()).isEqualTo("sys_user");
            assertThat(member.getCreatedActorId()).isEqualTo(1001L);
        });
    verify(appSecurityAuditService).append(argThat(command ->
        "app_organization".equals(command.resourceType())
            && "create".equals(command.action())
            && command.actorType() == AppActorType.SYS_USER
            && command.actorId().equals(1001L)));
}

@Test
void memberRoleChangeCommitsThenAfterCommitInvalidatesOrgSessions() {
    service.upsertMember(
        7L, 102L, "organization_admin", "active", null, null, 4L,
        "成员角色调整",
        AppActorContext.sysUser(1001L));
    assertThat(memberMapper.selectActive(7L, 102L, clock.now())
        .getMembershipRevision()).isEqualTo(5L);
    verify(appSessionService).invalidateOrganizationSessions(
        7L, AppSessionInvalidationReason.MEMBERSHIP_CHANGED);
}

@Test
void auditFailureRollsBackOrganizationMemberGrantAndAudit() {
    DatabaseCounts before = snapshotCounts(
        "app_organization", "app_org_member",
        "av_resource_grant", "app_security_audit");
    doThrow(new IllegalStateException("audit unavailable"))
        .when(appSecurityAuditService).append(any());

    assertThatThrownBy(() -> service.create(
        "不可残留组织", 101L, "审计失败回滚", AppActorContext.sysUser(1001L)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(snapshotCounts(
        "app_organization", "app_org_member",
        "av_resource_grant", "app_security_audit"))
        .isEqualTo(before);
    verifyNoInteractions(applicationEventPublisher, appSessionService);
}

@Test
void listenerFailureKeepsCommittedRevisionRecordsFailureAndGuardRejectsOldToken() {
    AppToken oldAppToken = issueAppToken(102L, 4L);
    SysToken sameNumericSysToken = issueSysToken(102L);
    doThrow(new IllegalStateException("session store unavailable"))
        .when(appSessionService).invalidateOrganizationSessions(
            7L, AppSessionInvalidationReason.MEMBERSHIP_CHANGED);

    service.upsertMember(
        7L, 102L, "organization_admin", "active", null, null, 4L,
        "会话失效提交后恢复", AppActorContext.sysUser(1001L));

    assertThat(memberMapper.selectActive(7L, 102L, clock.now())
        .getMembershipRevision()).isEqualTo(5L);
    assertThat(securityAuditActions(7L))
        .contains("upsert_member", "session_invalidation_failed");
    verify(appSessionService, times(3)).invalidateOrganizationSessions(
        7L, AppSessionInvalidationReason.MEMBERSHIP_CHANGED);
    assertThatThrownBy(() -> invokeProtectedAppRequest(oldAppToken))
        .hasFieldOrPropertyWithValue("code", 46126);
    assertThat(invokeProtectedSysRequest(sameNumericSysToken)).isSuccessful();

    reset(appSessionService);
    invalidationListener.recover(lastInvalidationFailureFor(7L));
    verify(appSessionService).invalidateOrganizationSessions(
        7L, AppSessionInvalidationReason.MEMBERSHIP_CHANGED);
    assertThat(appOnlineIndexContainsOrganization(7L)).isFalse();
    assertThat(securityAuditActions(7L)).contains("session_invalidation_recovered");
}

@Test
void everyAdminWriteRejectsAppActorWithSameNumericIdWithoutSideEffects() {
    Long organizationId = insertOrganizationWithOwner(7L, 101L);
    DatabaseCounts before = snapshotCounts(
        "app_organization", "app_org_member",
        "av_resource_grant", "app_security_audit");
    AppActorContext wrongDomainActor = AppActorContext.appUser(1001L);

    assertAll(
        () -> assertAdminActorForbidden(() -> service.create(
            "不应创建的组织", 101L, "身份域防御测试",
            wrongDomainActor)),
        () -> assertAdminActorForbidden(() -> service.update(
            organizationId,
            "启用后的组织", "active", organizationRevision(organizationId),
            "身份域防御测试",
            wrongDomainActor)),
        () -> assertAdminActorForbidden(() -> service.upsertMember(
            organizationId, 102L,
            "organization_member", "active", null, null, null,
            "身份域防御测试",
            wrongDomainActor)),
        () -> assertAdminActorForbidden(() -> service.leaveMember(
            organizationId, 101L, 1L,
            "身份域防御测试", wrongDomainActor))
    );

    assertThat(snapshotCounts(
        "app_organization", "app_org_member",
        "av_resource_grant", "app_security_audit"))
        .isEqualTo(before);
    verifyNoInteractions(appSecurityAuditService, appSessionService);
}

private void assertAdminActorForbidden(ThrowingCallable invocation) {
    assertThatThrownBy(invocation)
        .isInstanceOf(ServiceException.class)
        .extracting("code")
        .isEqualTo(46127);
}
```

- [ ] **步骤 2（2–5 分钟）：运行集成测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.OrganizationAdminServiceIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=OrganizationAdminServiceIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 OrganizationAdminServiceIT 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 OrganizationAdminServiceIT 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 OrganizationAdminServiceIT 至少一项真实执行并失败'
}
```

预期：先按统一约定建立服务、请求/响应类型的签名骨架，测试成功编译并真正执行；至少一个组织/成员事务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小管理事务**

规则固定为：

```java
private static final Set<String> ORGANIZATION_ROLES = Set.of(
    "organization_owner", "organization_admin", "organization_member");

private static void requireSysAdminActor(AppActorContext actor) {
    if (actor == null || actor.actorType() != AppActorType.SYS_USER) {
        throw new ServiceException("仅运营用户可执行组织管理写操作", 46127);
    }
}

@Transactional(rollbackFor = Exception.class)
public void leaveMember(Long organizationId, Long userId, Long expectedRevision,
                        String reason, AppActorContext actor) {
    requireSysAdminActor(actor);
    AppOrgMember member = memberMapper.selectForUpdate(organizationId, userId);
    requireRevision(member.getMembershipRevision(), expectedRevision);
    if ("organization_owner".equals(member.getRoleCode())
        && memberMapper.countActiveOwners(organizationId) == 1L) {
        throw new ServiceException("组织必须保留至少一名有效所有者", 46127);
    }
    member.setStatus("left");
    member.setValidUntil(LocalDateTime.now());
    member.setMembershipRevision(member.getMembershipRevision() + 1L);
    member.setUpdatedActorType(actor.actorType().getValue());
    member.setUpdatedActorId(actor.actorId());
    memberMapper.updateById(member);
    appendAudit(member, "leave_member", actor, reason);
    applicationEventPublisher.publishEvent(new OrganizationSessionInvalidationRequested(
        organizationId, AppSessionInvalidationReason.MEMBERSHIP_CHANGED,
        actor.actorType(), actor.actorId(), reason));
}
```

创建组织、启停组织、添加成员、改角色／状态／有效期、标记离开的每一个 Service 写签名都显式接收非空 `AppActorContext actor`，并使用准确预期修订号；禁止注入默认 `LoginHelper`、`AppLoginHelper`、`StpUtil` 或自造 `requireActor()` 线程上下文。每次变更都调用现行安全审计接口并构造现行八字段审计 DTO，从同一个 actor 严格依次填满且只填满 `resourceType/resourceId/action/actorType/actorId/beforeDigest/afterDigest/reason`。前后摘要来自排序字段的规范 JSON，原因使用已校验的中文请求原因，不增加请求追踪扩展字段或请求体字段。组织停用使用 `ORGANIZATION_DISABLED`，成员变更使用 `MEMBERSHIP_CHANGED`。成员历史不物理删除。

四个公开写方法全部使用 `@Transactional(rollbackFor = Exception.class)`。组织／成员数据库修改、对应修订递增和现行八字段安全审计追加都留在该事务内；审计异常必须向上抛出，并完整回滚组织、成员、对象授权和审计行。事务内只通过 `ApplicationEventPublisher` 发布 `OrganizationSessionInvalidationRequested`，严禁直接调用 Redis 或 `invalidateOrganizationSessions(...)`。

`OrganizationSessionInvalidationListener` 使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`，仅在数据库成功提交后调用 `invalidateOrganizationSessions(...)`。监听器以同一事件最多尝试三次；耗尽后使用 `TransactionTemplate` 的 `PROPAGATION_REQUIRES_NEW` 向现有 `app_security_audit` 追加八字段结构化失败记录，`action=session_invalidation_failed`，并保留组织、失效原因、actor 和可恢复事件摘要；恢复入口对同一事件幂等，成功清理在线索引后追加 `action=session_invalidation_recovered`。这不是第四张 P0-B 表，也不引入异步任务。提交后 Redis／监听器失败不得宣称数据库回滚：组织／成员事实、修订和业务审计已经提交，失败记录必须存在。安全性由每个受保护请求最先执行的 `AppSessionRevisionGuard.checkCurrentSession()` 重新读取已提交修订保证；Redis 读取或比较失败一律失败关闭，因此旧 app token 下一次请求必被拒绝，同一数值编号的 sys session 不参与 app 组织会话失效。

`OrganizationAdminServiceIT` 必须分别证明：事务内审计异常使四张表完整回滚且不发布可执行的提交后失效；监听器连续失败时数据库、业务审计与修订均已提交，`session_invalidation_failed` 记录存在，旧 app token 下一次受保护请求由 guard 拒绝，同号 sys session 仍有效，随后有界恢复清除 app 在线索引并写入 recovered 记录。

`requireSysAdminActor(actor)` 是 `OrganizationAdminServiceImpl` 四个公开写方法的第一条
业务语句，必须早于参数所指资源的查询、角色校验、行锁、Mapper 写入、审计和
会话失效调用；不能只依赖 `AppOrganizationController` 已使用
`SysAuthorizationActorResolver`。同号 `app_user=1001` 与 `sys_user=1001` 是两个不同
主体：前者直调任一管理写方法固定返回 `46127`，并保持组织、成员、对象授权和安全审计
行数完全不变，也不触发会话失效。创建者授权保持相反且更窄的约束：
`initializeCreatorGrant` 只接受 `actorType=app_user` 且
`actorId=resource.createdByUserId`；同号 `sys_user` 或其他 app 用户都在写入前拒绝。

`OrganizationAdminServiceImpl` 先以 `ORGANIZATION_ROLES` 拒绝非组织角色，再通过
P0-A `identityRoleMapper` 验证对应 `app_role` 行满足
`scope_type='organization'`、`status='active'`、`del_flag='0'`。成员角色只写
`app_org_member.role_code`；绝不调用 `IAppPermissionService.replaceUserRoles`，也不读写
`app_user_role`。`OrganizationAdminServiceIT` 在类级标注 `@Tag("dev")`，并断言同一用户在两个组织可以具有不同角色，两个成员变更均不新增 `app_user_role` 行。

为任务 8 的薄 Controller 冻结三个只读签名：`PageResult<AppOrganization> page(String displayName, String status, PageQuery pageQuery)`、`AppOrganization requireOrganization(Long organizationId)`、`PageResult<AppOrgMember> pageMembers(Long organizationId, PageQuery pageQuery)`。它们只接收 core 标量／分页对象并返回 core Entity 给端侧映射，不接收平台 BO／VO，不绕过 Mapper 分页；写签名仍为 `create`、`update`、`upsertMember`、`leaveMember`，且统一显式 actor 与修订参数。

- [ ] **步骤 4（2–5 分钟）：运行组织集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.OrganizationAdminServiceIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-core -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=OrganizationAdminServiceIT verify
if ($LASTEXITCODE -ne 0) { throw 'OrganizationAdminServiceIT 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'OrganizationAdminServiceIT 未生成本次报告' }
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw 'OrganizationAdminServiceIT 报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failures = [int]$report.testsuite.failures
$errors = [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
  throw 'OrganizationAdminServiceIT 不是本次非零全绿报告'
}
$report.testsuite | Select-Object name, tests, failures, errors, skipped
```

预期：创建、重复成员、最后所有者保护、组织内角色隔离、`app_user_role` 零新增、修订冲突、成员离开、组织停用、事务内审计回滚和提交后会话失效恢复全部通过；审计异常完整回滚，监听器异常不回滚已提交数据库且产生失败记录，旧 app token 由 guard 拒绝、同号 sys session 不受影响，恢复后 app 在线索引清除；四个管理写入口对同号 `app_user` 均在副作用前拒绝，组织、成员、授权、审计零新增且不触发会话失效；Failsafe 报告 `tests > 0, failures=0, errors=0, skipped=0`。

- [ ] **步骤 5（2–5 分钟）：提交组织管理领域**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/IOrganizationAdminService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/impl/OrganizationAdminServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/event/OrganizationSessionInvalidationRequested.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/listener/OrganizationSessionInvalidationListener.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/authorization/OrganizationAdminServiceIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存组织事务文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 实现组织与成员修订事务"
if ($LASTEXITCODE -ne 0) { throw '提交组织事务失败' }
```

## 任务 7：暴露用户工作区接口并验证 `app` 权限类型

**最小任务卡：**

- **单一目标／不做：** 暴露创作端工作区列表／切换薄接口；不接受 `workspaceKey` 之外的切换字段，不回退 sys 身份，不直接暴露 Entity。
- **风险／触发：** 红色；命中会话切换、身份域隔离和直接接口访问。
- **权威来源：** API 契约、主计划 `IWorkspaceAuthorizationService`／五 DTO、本计划组织会话信任边界。
- **成功／反向验收：** app token 正常；sys token、无凭据、未知字段、伪造／跨账号／失效工作区被拒；响应用 `R.data` 且 ID／修订字符串化。
- **所有权／数据范围：** 仅本任务 `文件` 列出的 user `security`／controller／BO／VO／测试；不修改核心会话事实。
- **依赖／人员／并发：** 依赖任务 4；开发 A 实施、开发 B 独立身份／接口审查，同一任务最多 2 人。
- **验证／检查点：** 先 review 请求体唯一字段和 actor 域，再以 `'-Pdev,local-integration-test'` 运行接口 RED／GREEN并核对 XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\security\AppAuthorizationActorResolver.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\controller\WorkspaceController.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\domain\bo\SwitchWorkspaceBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\domain\vo\WorkspaceVo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization\WorkspaceControllerIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的接口测试**

```java
@Tag("dev")
class WorkspaceControllerIT {
    @Test
    void appActorResolverKeepsAppTypeAndNeverFallsBackToSysLogin() {
        withAppLogin(1001L, () ->
            assertThat(appAuthorizationActorResolver.requireActor())
                .isEqualTo(AppActorContext.appUser(1001L)));

        assertThatThrownBy(() ->
            withOnlySysLogin(1001L, appAuthorizationActorResolver::requireActor))
            .isInstanceOf(NotLoginException.class);
    }

    @Test
    void switchRequestRejectsUnknownExpectedMembershipRevision() throws Exception {
        mockMvc.perform(put("/api/auth/current-workspace")
                .header("Authorization", appBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceKey": "opaque-workspace-key",
                      "expectedMembershipRevision": "7"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void switchRequestRejectsIllegalOwnershipFields() throws Exception {
        mockMvc.perform(put("/api/auth/current-workspace")
                .header("Authorization", appBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceKey": "opaque-workspace-key",
                      "tenantId": "999",
                      "ownerType": "organization",
                      "ownerId": "999",
                      "billingSubjectId": "999"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void legalListAndSwitchSerializeAllIdsAndRevisionsAsStrings() throws Exception {
        seedPersonalThenOneOrganizationWorkspace(1001L, 7L);
        mockMvc.perform(get("/api/auth/workspaces")
                .header("Authorization", appBearerToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[1].tenantId").isString())
            .andExpect(jsonPath("$.data[1].ownerId").isString())
            .andExpect(jsonPath("$.data[1].billingSubjectId").isString())
            .andExpect(jsonPath("$.data[1].workspaceRevision").isString())
            .andExpect(jsonPath("$.data[1].membershipRevision").isString());
        mockMvc.perform(put("/api/auth/current-workspace")
                .header("Authorization", appBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{ "workspaceKey": "opaque-workspace-key" }"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerId").isString())
            .andExpect(jsonPath("$.data.workspaceRevision").isString())
            .andExpect(jsonPath("$.data.membershipRevision").isString());
    }

    @Test
    void appActorResolverChecksRevisionBeforePrincipalAndStopsOnFailure() {
        RuntimeException stale = staleSession();
        doThrow(stale).when(appSessionRevisionGuard).checkCurrentSession();

        assertThatThrownBy(appAuthorizationActorResolver::requireActor)
            .isSameAs(stale);

        InOrder order = inOrder(appSessionRevisionGuard, appLoginHelper);
        order.verify(appSessionRevisionGuard).checkCurrentSession();
        verifyNoInteractions(appLoginHelper);
    }

    @Test
    void staleServerMembershipRevisionReturns46126() throws Exception {
        seedOrganizationWorkspaceWithStaleMembershipRevision(
            1001L, "opaque-workspace-key");
        mockMvc.perform(put("/api/auth/current-workspace")
                .header("Authorization", appBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "workspaceKey": "opaque-workspace-key" }
                    """))
            .andExpect(jsonPath("$.code").value(46126));
    }
}
```

- [ ] **步骤 2（2–5 分钟）：运行接口测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-user\target\failsafe-reports\TEST-org.dromara.aivideo.user.authorization.WorkspaceControllerIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-user -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=WorkspaceControllerIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 WorkspaceControllerIT 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 WorkspaceControllerIT 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 WorkspaceControllerIT 至少一项真实执行并失败'
}
```

预期：路由不存在或响应契约不匹配而失败。

- [ ] **步骤 3（2–5 分钟）：实现薄控制器**

```java
@Component
@RequiredArgsConstructor
public final class AppAuthorizationActorResolver {
    private final AppSessionRevisionGuard appSessionRevisionGuard;
    private final AppLoginHelper appLoginHelper;

    public AppActorContext requireActor() {
        appSessionRevisionGuard.checkCurrentSession();
        long appUserId = appLoginHelper.getPrincipal().appUserId();
        return AppActorContext.appUser(appUserId);
    }
}

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class WorkspaceController {
    private final IWorkspaceAuthorizationService workspaceAuthorizationService;
    private final AppAuthorizationActorResolver actorResolver;

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/workspaces")
    public R<List<WorkspaceVo>> list() {
        return R.data(workspaceAuthorizationService.listAvailableWorkspaces()
            .stream().map(WorkspaceVo::from).toList());
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @PutMapping("/current-workspace")
    public R<WorkspaceVo> switchCurrent(
        @Validated @RequestBody SwitchWorkspaceBo request) {
        return R.data(WorkspaceVo.from(
            workspaceAuthorizationService.switchCurrentWorkspace(
                new SwitchWorkspaceDTO(request.getWorkspaceKey()),
                actorResolver.requireActor())));
    }
}

@Data
public class SwitchWorkspaceBo {
    @NotBlank(message = "工作区键不能为空")
    private String workspaceKey;
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("不支持的请求字段：" + fieldName);
    }
}
```

Jackson 将 `@JsonAnySetter` 抛出的异常包装成请求体读取错误；`SwitchWorkspaceBo` 因而拒绝未知字段。`WorkspaceVo` 的所有编号与修订号显式转为字符串。
`AppAuthorizationActorResolver` 是用户模块唯一把 `AppLoginHelper` 转换为
`AppActorContext.appUser` 的写边界；它不导入默认 `LoginHelper`、`StpUtil` 或 sys
Mapper。`requireActor()` 的第一条可观察调用固定为 `appSessionRevisionGuard.checkCurrentSession()`，成功后才读 principal；Mockito `InOrder` 和 guard 异常下 `verifyNoInteractions(appLoginHelper)` 都必须成立。P0-C 的 `AppTaskInitiatorResolver` 必须复用该 resolver，不得再次直接读取
登录助手。

- [ ] **步骤 4（2–5 分钟）：重新运行接口测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-user\target\failsafe-reports\TEST-org.dromara.aivideo.user.authorization.WorkspaceControllerIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-user -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=WorkspaceControllerIT verify
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceControllerIT 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'WorkspaceControllerIT 未生成本次报告' }
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw 'WorkspaceControllerIT 报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failures = [int]$report.testsuite.failures
$errors = [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
  throw 'WorkspaceControllerIT 不是本次非零全绿报告'
}
$report.testsuite | Select-Object name, tests, failures, errors, skipped
```

预期：工作区列表、切换、未知修订字段、非法 ownership 字段、过期修订、guard-first 和运营令牌拒绝用例通过；合法列表／切换响应中的 ID 与修订均为 JSON 字符串；Failsafe 报告 `tests > 0, failures=0, errors=0, skipped=0`。

- [ ] **步骤 5（2–5 分钟）：提交用户接口**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/authorization/security/AppAuthorizationActorResolver.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/authorization/controller/WorkspaceController.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/authorization/domain/bo/SwitchWorkspaceBo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/authorization/domain/vo/WorkspaceVo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/authorization/WorkspaceControllerIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存用户端接口文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 暴露创作端工作区接口"
if ($LASTEXITCODE -ne 0) { throw '提交用户端接口失败' }
```

## 任务 8：暴露运营端组织/成员管理接口

**最小任务卡：**

- **单一目标／不做：** 暴露运营端组织／成员 BO、VO 与薄 Controller；不让 app token 冒充 sys actor，不把端侧 BO／VO放进 core。
- **风险／触发：** 红色；命中管理员权限、跨用户操作、审计与修订冲突。
- **权威来源：** API 契约、RuoYi Controller／BO／VO 规则、本计划 `IOrganizationAdminService` 和 actor resolver 边界。
- **成功／反向验收：** 组织分页／详情、成员分页、创建／更新、成员新增／更新／离开签名完整；独立 query／edit 权限准确；同号 app actor 与缺少权限对完全合法请求均精确返回固定错误且数据库、审计、会话零副作用；授权对照成功；字符串响应使用 `R.data`。
- **所有权／数据范围：** 仅本任务 `文件` 列出的 platform `security`／controller／BO／VO／测试；业务事务仍在 core Service。
- **依赖／人员／并发：** 依赖任务 6；开发 A 实施、开发 B 独立高权限／接口审查，同一任务最多 2 人。
- **验证／检查点：** 先 review 权限、日志与双身份隔离，再运行平台接口 RED／GREEN 和本次 Failsafe XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\security\SysAuthorizationActorResolver.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\controller\AppOrganizationController.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\OrganizationQueryBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\CreateOrganizationBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\UpdateOrganizationBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\bo\UpsertOrgMemberBo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\vo\AppOrganizationVo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\domain\vo\AppOrgMemberVo.java`
- 创建：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\test\java\org\dromara\aivideo\platform\authorization\AppOrganizationControllerIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的权限和审计接口测试**

```java
@Tag("dev")
class AppOrganizationControllerIT {
    @Test
    void sysActorResolverAndAuditKeepSysTypeWhenAppUserHasSameNumericId()
        throws Exception {
        seedAppUser(1001L);
        mockMvc.perform(post("/api/admin/app-organizations")
                .header("Authorization",
                    sysTokenWithUserAndPermission(
                        1001L, "aivideo:app-organization:edit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "同号主体隔离组织",
                      "ownerAppUserId": "1001",
                      "reason": "验证同号主体隔离"
                    }
                    """))
            .andExpect(jsonPath("$.code").value(200));

        assertThat(organizationMapper.selectLatest())
            .extracting(
                "createdActorType", "createdActorId",
                "updatedActorType", "updatedActorId")
            .containsExactly("sys_user", 1001L, "sys_user", 1001L);
        assertThat(securityAuditMapper.selectLatest())
            .extracting("actorType", "actorId")
            .containsExactly("sys_user", 1001L);
        withOnlyAppLogin(1001L, () ->
            assertThatThrownBy(sysAuthorizationActorResolver::requireActor)
                .isInstanceOf(NotLoginException.class));
    }

    @Test
    void memberMutationRequiresDedicatedPermission() throws Exception {
        DatabaseState before = snapshotAuthorizationState(7L, 102L);
        mockMvc.perform(post("/api/admin/app-organizations/7/members")
                .header("Authorization",
                    sysTokenWithout("aivideo:app-member:edit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "appUserId": "102",
                      "roleCode": "organization_member",
                      "status": "active",
                      "validFrom": "2026-07-28T12:00:00",
                      "validUntil": "2027-07-28T12:00:00",
                      "expectedMembershipRevision": "4",
                      "reason": "验证成员权限拒绝零副作用"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.msg")
                .value("没有访问权限，请联系管理员授权"));

        assertThat(snapshotAuthorizationState(7L, 102L)).isEqualTo(before);
        verifyNoInteractions(organizationAdminService, appSecurityAuditService,
            appSessionService);
    }

    @Test
    void authorizedMemberMutationWithSameValidBodySucceeds() throws Exception {
        mockMvc.perform(post("/api/admin/app-organizations/7/members")
                .header("Authorization",
                    sysTokenWith("aivideo:app-member:edit"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "appUserId": "102",
                      "roleCode": "organization_member",
                      "status": "active",
                      "validFrom": "2026-07-28T12:00:00",
                      "validUntil": "2027-07-28T12:00:00",
                      "expectedMembershipRevision": "4",
                      "reason": "验证成员授权成功对照"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        assertThat(memberMapper.selectByOrganizationAndUser(7L, 102L))
            .extracting("roleCode", "membershipRevision")
            .containsExactly("organization_member", 5L);
        verify(appSecurityAuditService).append(any());
        verify(appSessionService).invalidateOrganizationSessions(
            7L, AppSessionInvalidationReason.MEMBERSHIP_CHANGED);
    }

    @Test
    void deleteMarksMemberLeftInsteadOfRemovingHistory() throws Exception {
        mockMvc.perform(delete("/api/admin/app-organizations/7/members/102")
                .header("Authorization",
                    sysTokenWith("aivideo:app-member:edit"))
                .param("expectedMembershipRevision", "4")
                .param("reason", "成员离开组织"))
            .andExpect(jsonPath("$.code").value(200));
        assertThat(memberMapper.selectByOrganizationAndUser(7L, 102L).getStatus())
            .isEqualTo("left");
    }
}
```

- [ ] **步骤 2（2–5 分钟）：运行接口测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\target\failsafe-reports\TEST-org.dromara.aivideo.platform.authorization.AppOrganizationControllerIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-platform -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=AppOrganizationControllerIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 AppOrganizationControllerIT 真实失败' }
if (-not (Test-Path -LiteralPath $reportPath)) {
  throw '红灯无 AppOrganizationControllerIT 报告；测试编译或装配失败不算目标测试失败'
}
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw '红灯报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failed -lt 1 -or $skipped -ge $tests) {
  throw '红灯必须是 AppOrganizationControllerIT 至少一项真实执行并失败'
}
```

预期：控制器不存在而失败。

- [ ] **步骤 3（2–5 分钟）：实现控制器和精确权限**

控制器路径与权限固定为：

```java
@Component
public final class SysAuthorizationActorResolver {
    public AppActorContext requireActor() {
        return AppActorContext.sysUser(LoginHelper.getUserId());
    }
}

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/app-organizations")
public class AppOrganizationController {
    private final IOrganizationAdminService organizationAdminService;
    private final SysAuthorizationActorResolver actorResolver;

    @GetMapping
    @SaCheckPermission("aivideo:app-organization:query")
    public R<PageResult<AppOrganizationVo>> page(
        OrganizationQueryBo query, PageQuery pageQuery) {
        PageResult<AppOrganization> page = organizationAdminService.page(
            query.getDisplayName(), query.getStatus(), pageQuery);
        return R.data(mapOrganizationPage(page));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("aivideo:app-organization:query")
    public R<AppOrganizationVo> detail(@PathVariable Long id) {
        return R.data(AppOrganizationVo.from(
            organizationAdminService.requireOrganization(id)));
    }

    @GetMapping("/{id}/members")
    @SaCheckPermission("aivideo:app-organization:query")
    public R<PageResult<AppOrgMemberVo>> members(
        @PathVariable Long id, PageQuery pageQuery) {
        return R.data(mapMemberPage(
            organizationAdminService.pageMembers(id, pageQuery)));
    }

    @PostMapping
    @SaCheckPermission("aivideo:app-organization:edit")
    @Log(title = "创作组织", businessType = BusinessType.INSERT)
    public R<String> create(@Validated @RequestBody CreateOrganizationBo body) {
        Long id = organizationAdminService.create(
            body.getDisplayName(), body.getOwnerAppUserId(), body.getReason(),
            actorResolver.requireActor());
        return R.data(Long.toString(id));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("aivideo:app-organization:edit")
    @Log(title = "创作组织", businessType = BusinessType.UPDATE)
    public R<Void> update(
        @PathVariable Long id,
        @Validated @RequestBody UpdateOrganizationBo body) {
        organizationAdminService.update(
            id, body.getDisplayName(), body.getStatus(),
            body.getExpectedOrganizationRevision(), body.getReason(),
            actorResolver.requireActor());
        return R.ok();
    }

    @PostMapping("/{id}/members")
    @SaCheckPermission("aivideo:app-member:edit")
    @Log(title = "创作组织成员", businessType = BusinessType.INSERT)
    public R<Void> addMember(
        @PathVariable Long id,
        @Validated @RequestBody UpsertOrgMemberBo body) {
        organizationAdminService.upsertMember(
            id, body.getAppUserId(), body.getRoleCode(), body.getStatus(),
            body.getValidFrom(), body.getValidUntil(),
            body.getExpectedMembershipRevision(),
            body.getReason(), actorResolver.requireActor());
        return R.ok();
    }

    @PutMapping("/{id}/members/{userId}")
    @SaCheckPermission("aivideo:app-member:edit")
    @Log(title = "创作组织成员", businessType = BusinessType.UPDATE)
    public R<Void> updateMember(
        @PathVariable Long id,
        @PathVariable Long userId,
        @Validated @RequestBody UpsertOrgMemberBo body) {
        organizationAdminService.upsertMember(
            id, userId, body.getRoleCode(), body.getStatus(),
            body.getValidFrom(), body.getValidUntil(),
            body.getExpectedMembershipRevision(),
            body.getReason(), actorResolver.requireActor());
        return R.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @SaCheckPermission("aivideo:app-member:edit")
    @Log(title = "创作组织成员", businessType = BusinessType.DELETE)
    public R<Void> leaveMember(
        @PathVariable Long id,
        @PathVariable Long userId,
        @RequestParam Long expectedMembershipRevision,
        @RequestParam String reason) {
        organizationAdminService.leaveMember(
            id, userId, expectedMembershipRevision, reason,
            actorResolver.requireActor());
        return R.ok();
    }
}

```

分页使用 `PageQuery`／`PageResult`；Controller 不直接调用 Mapper。八类入口完整固定为：组织分页 `GET /api/admin/app-organizations`、组织详情 `GET /{id}`、成员分页 `GET /{id}/members`、创建 `POST`、更新／启停 `PUT /{id}`、成员新增 `POST /{id}/members`、成员更新 `PUT /{id}/members/{userId}`、成员离开 `DELETE /{id}/members/{userId}`。所有写请求携带中文原因和相应预期修订号。创建、更新、启停、添加／修改／移除成员的每个 Controller 写方法都先调用唯一 `SysAuthorizationActorResolver.requireActor()`，再把平台 BO 映射为明确标量参数，把返回的 `AppActorContext.sysUser` 显式传给 `IOrganizationAdminService`；查询 Service 只接收标量与 `PageQuery`，结果由 Controller 映射为平台 VO，core 不依赖端侧 BO／VO，Entity 不直接序列化。反向映射测试逐端点使用 captor 断言 BO 实例从未传入 core、Long ID／修订在 VO 中变为字符串。该 resolver 是 `ai-video-platform` 的两个累计允许默认 `LoginHelper` 调用点之一（另一个是 P0-A `AppIdentityAdminServiceImpl`），不得导入 `AppLoginHelper` 或查询 `app_user` 来推断登录主体；业务目标 app 用户只能作为显式资源编号传给 Service。

- [ ] **步骤 4（2–5 分钟）：重新运行平台接口测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reportPath = 'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\target\failsafe-reports\TEST-org.dromara.aivideo.platform.authorization.AppOrganizationControllerIT.xml'
Remove-Item -LiteralPath $reportPath -Force -ErrorAction SilentlyContinue
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl ruoyi-modules/ai-video/ai-video-platform -am `
  '-Pdev,local-integration-test' `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=AppOrganizationControllerIT verify
if ($LASTEXITCODE -ne 0) { throw 'AppOrganizationControllerIT 绿灯命令失败' }
if (-not (Test-Path -LiteralPath $reportPath)) { throw 'AppOrganizationControllerIT 未生成本次报告' }
$reportItem = Get-Item -LiteralPath $reportPath
if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw 'AppOrganizationControllerIT 报告不是本次命令生成' }
[xml]$report = Get-Content -Raw -LiteralPath $reportPath
$tests = [int]$report.testsuite.tests
$failures = [int]$report.testsuite.failures
$errors = [int]$report.testsuite.errors
$skipped = [int]$report.testsuite.skipped
if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
  throw 'AppOrganizationControllerIT 不是本次非零全绿报告'
}
$report.testsuite | Select-Object name, tests, failures, errors, skipped
```

预期：组织分页／详情、成员分页、创建、启停、成员新增／更新／离开、BO→标量→VO 反向映射、权限不足、授权成功对照、修订冲突和只追加审计用例通过；权限不足精确返回 `403`／`没有访问权限，请联系管理员授权` 且组织、成员、授权、审计、会话零副作用；Failsafe 报告 `tests > 0, failures=0, errors=0, skipped=0`。

- [ ] **步骤 5（2–5 分钟）：提交运营接口**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/security/SysAuthorizationActorResolver.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/controller/AppOrganizationController.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/bo/OrganizationQueryBo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/bo/CreateOrganizationBo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/bo/UpdateOrganizationBo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/bo/UpsertOrgMemberBo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/vo/AppOrganizationVo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/domain/vo/AppOrgMemberVo.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/authorization/AppOrganizationControllerIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存运营端接口文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 提供创作组织与成员管理接口"
if ($LASTEXITCODE -ne 0) { throw '提交运营端接口失败' }
```

## 任务 9：验证两个启动模块的控制器隔离

**最小任务卡：**

- **单一目标／不做：** 锁定 user／platform Controller 和依赖装配隔离；不引入对侧生产模块依赖，不扩大安全排除。
- **风险／触发：** 红色；命中双端身份隔离、路由暴露与可用性。
- **权威来源：** RULES 双启动边界、P0-A 双向令牌隔离、本计划两端 Controller／resolver 所有权。
- **成功／反向验收：** 用户应用仅有用户 Controller，运营应用仅有平台 Controller；真实启动端口上 sys token→用户 URL、app token→运营 URL 都精确拒绝且 Service／数据库／审计／会话零副作用；默认 `LoginHelper` 白名单准确。
- **所有权／数据范围：** 仅本任务 `文件` 列出的两个启动 POM（必要时）和两个装配 IT；不改业务表。
- **依赖／人员／并发：** 依赖任务 7、8；开发 A 实施、开发 B 独立装配／安全审查，同一任务最多 2 人。
- **验证／检查点：** 先 review 依赖图与身份工具扫描，再运行两个启动应用的 `'-Pdev,local-integration-test'` RED／GREEN 和 XML。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 验证/条件修改：`ai-video-api\ai-video-user-api\pom.xml`
- 验证/条件修改：`ai-video-api\ruoyi-admin\pom.xml`
- 创建：`ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo\assembly\UserAuthorizationBoundaryIT.java`
- 创建：`ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo\assembly\PlatformAuthorizationBoundaryIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的装配测试**

```java
@Tag("dev")
@SpringBootTest(
    classes = AiVideoUserApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAuthorizationBoundaryIT {
    @Autowired
    ApplicationContext context;

    @Test
    void userApplicationHasWorkspaceControllerButNoPlatformController() {
        assertThat(context.getBeansOfType(WorkspaceController.class)).hasSize(1);
        assertThat(context.getBeansWithAnnotation(RestController.class).values())
            .noneMatch(bean -> AopUtils.getTargetClass(bean).getPackageName()
                .startsWith("org.dromara.aivideo.platform"));
    }

    @Test
    void sysTokenIsRejectedByRealUserUrlWithoutAnySideEffect() {
        AuthorizationState before = snapshotAuthorizationState();

        ResponseEntity<JsonNode> response = rest.exchange(
            userBaseUrl("/api/auth/workspaces"), HttpMethod.GET,
            bearer(sysToken(1001L)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(401);
        assertThat(response.getBody().path("msg").asText())
            .isEqualTo("登录状态异常，请重新登录");
        assertThat(snapshotAuthorizationState()).isEqualTo(before);
        verifyNoInteractions(workspaceAuthorizationService,
            organizationAdminService, appSecurityAuditService, appSessionService);
    }
}
```

平台测试同样在类级标注 `@Tag("dev")`，以 `RuoYiApplication` 和 `RANDOM_PORT` 启动真实运营应用，使用相同的包名扫描反向断言 `AppOrganizationController` 存在，且没有来自 `org.dromara.aivideo.user` 的控制器；再用 app token 对真实 `/api/admin/app-organizations` 发出字段完整且本可成功的创建请求，精确断言 JSON `code=401`、`msg=登录状态异常，请重新登录`，并对组织／成员／授权／审计表快照、`IOrganizationAdminService`、安全审计和会话服务逐项证明零副作用。两个边界 IT 都通过真实 HTTP 端口验证 filter／Sa-Token／Controller 链，不得用 MockMvc slice、直接 resolver 调用或仅检查 bean 代替；也不得为断言引入对侧生产模块依赖，token／URL 由共同测试 fixture 以字符串提供。

- [ ] **步骤 2（2–5 分钟）：运行装配测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reports = @(
  'ai-video-api\ai-video-user-api\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.UserAuthorizationBoundaryIT.xml',
  'ai-video-api\ruoyi-admin\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.PlatformAuthorizationBoundaryIT.xml'
)
$reports | ForEach-Object {
  Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue
}
$redStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl 'ai-video-user-api,ruoyi-admin' -am `
  '-Pdev,local-integration-test' `
  --fail-at-end `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Dit.test=UserAuthorizationBoundaryIT,PlatformAuthorizationBoundaryIT' verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到装配边界测试真实失败' }
$failed = 0
foreach ($reportPath in $reports) {
  if (-not (Test-Path -LiteralPath $reportPath)) {
    throw "$reportPath 不存在；测试编译或装配失败不算目标测试失败"
  }
  $reportItem = Get-Item -LiteralPath $reportPath
  if ($reportItem.LastWriteTimeUtc -lt $redStartedAt) { throw "$reportPath 不是本次命令生成" }
  [xml]$report = Get-Content -Raw -LiteralPath $reportPath
  $tests = [int]$report.testsuite.tests
  $reportFailed = [int]$report.testsuite.failures + [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $skipped -ge $tests) { throw "$reportPath 没有真实执行测试" }
  $failed += $reportFailed
}
if ($failed -lt 1) { throw '红灯必须是两个装配边界目标之一真实失败' }
```

预期：依赖或扫描边界未配置时至少一个断言失败。

- [ ] **步骤 3（2–5 分钟）：最小化启动模块依赖**

`ai-video-user-api/pom.xml` 只依赖 `ai-video-user` 及其传递依赖，不直接依赖 `ai-video-platform`；`ruoyi-admin/pom.xml` 只依赖 `ai-video-platform` 及其传递依赖，不直接依赖 `ai-video-user`。若 P0-A 已完成该装配，只保留测试，不重复修改 POM。

同时运行静态禁用检查：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
rg -n "\bStpUtil\b|\bLoginHelper\b" `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization `
  ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization
$boundaryScanExitCode = $LASTEXITCODE
if ($boundaryScanExitCode -eq 0) { throw '授权包出现禁止身份工具引用' }
if ($boundaryScanExitCode -gt 1) { throw '授权包边界扫描执行失败' }
$duplicateIdentityTypes = Get-ChildItem -File -Recurse `
  -LiteralPath ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization |
  Where-Object {
    $_.Name -in @(
      'AppRole.java',
      'AppRolePermission.java',
      'AppUserRole.java',
      'AppRoleMapper.java',
      'AppRolePermissionMapper.java',
      'AppUserRoleMapper.java'
    )
  }
if ($duplicateIdentityTypes) { $duplicateIdentityTypes.FullName; exit 1 }
$appActorResolver = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\security\AppAuthorizationActorResolver.java'
$sysActorResolver = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\security\SysAuthorizationActorResolver.java'
if (-not (Select-String -LiteralPath $appActorResolver `
    -SimpleMatch 'AppLoginHelper' -Quiet)) {
  throw 'AppAuthorizationActorResolver 未使用 AppLoginHelper'
}
if (Select-String -LiteralPath $appActorResolver `
    -Pattern '\bLoginHelper\b|\bStpUtil\b|org\.dromara\.system' -Quiet) {
  throw 'AppAuthorizationActorResolver 触碰运营身份域'
}
if (-not (Select-String -LiteralPath $sysActorResolver `
    -SimpleMatch 'org.dromara.common.satoken.utils.LoginHelper' -Quiet)) {
  throw 'SysAuthorizationActorResolver 未使用默认运营 LoginHelper'
}
if (Select-String -LiteralPath $sysActorResolver `
    -Pattern 'AppLoginHelper|AppUserMapper|IAppIdentityService' -Quiet) {
  throw 'SysAuthorizationActorResolver 触碰创作身份域'
}
$platformRoot = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform'
$defaultLoginHelperActual = @(
  Get-ChildItem -LiteralPath $platformRoot -Recurse -File -Filter '*.java' |
    Select-String -SimpleMatch `
      'org.dromara.common.satoken.utils.LoginHelper' |
    Select-Object -ExpandProperty Path -Unique |
    ForEach-Object { [System.IO.Path]::GetFullPath($_) }
)
$defaultLoginHelperExpected = @(
  [System.IO.Path]::GetFullPath(
    'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\identity\service\impl\AppIdentityAdminServiceImpl.java'),
  [System.IO.Path]::GetFullPath($sysActorResolver)
)
$loginHelperDiff = Compare-Object `
  ($defaultLoginHelperExpected | Sort-Object -Unique) `
  ($defaultLoginHelperActual | Sort-Object -Unique)
if ($loginHelperDiff) {
  $loginHelperDiff
  throw '平台默认 LoginHelper 调用点不等于累计精确白名单'
}
```

预期：身份重复类型扫描无输出；`AppLoginHelper` 不会被第一条正则误判。用户 actor
resolver 只依赖 `AppLoginHelper`，运营 actor resolver 只依赖默认 `LoginHelper`；截至
P0-B，平台默认 `LoginHelper` 累计精确白名单只有
`AppIdentityAdminServiceImpl` 与 `SysAuthorizationActorResolver`。

- [ ] **步骤 4（2–5 分钟）：重新运行装配测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$reports = @(
  'ai-video-api\ai-video-user-api\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.UserAuthorizationBoundaryIT.xml',
  'ai-video-api\ruoyi-admin\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.PlatformAuthorizationBoundaryIT.xml'
)
$reports | ForEach-Object {
  Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue
}
$greenStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl 'ai-video-user-api,ruoyi-admin' -am `
  '-Pdev,local-integration-test' `
  --fail-at-end `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Dit.test=UserAuthorizationBoundaryIT,PlatformAuthorizationBoundaryIT' verify
if ($LASTEXITCODE -ne 0) { throw '装配边界绿灯命令失败' }
foreach ($reportPath in $reports) {
  if (-not (Test-Path -LiteralPath $reportPath)) { throw "$reportPath 未生成本次报告" }
  $reportItem = Get-Item -LiteralPath $reportPath
  if ($reportItem.LastWriteTimeUtc -lt $greenStartedAt) { throw "$reportPath 不是本次命令生成" }
  [xml]$report = Get-Content -Raw -LiteralPath $reportPath
  $tests = [int]$report.testsuite.tests
  $failures = [int]$report.testsuite.failures
  $errors = [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "$reportPath 不是本次非零全绿报告"
  }
  $report.testsuite | Select-Object name, tests, failures, errors, skipped
}
```

预期：两个真实启动装配 IT 的 Failsafe 报告均为 `tests > 0, failures=0, errors=0, skipped=0`；两次交叉 token 请求精确拒绝且 Service／数据库／审计／会话零副作用，Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 5（2–5 分钟）：提交装配边界**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$required = @(
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/assembly/UserAuthorizationBoundaryIT.java',
  'ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/assembly/PlatformAuthorizationBoundaryIT.java'
)
$optional = @(
  'ai-video-api/ai-video-user-api/pom.xml',
  'ai-video-api/ruoyi-admin/pom.xml'
)
foreach ($requiredPath in $required) {
  if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
    throw "缺少 Task9 必需 IT：$requiredPath"
  }
}
$changedOptional = @(
  foreach ($optionalPath in $optional) {
    $status = @(git status --porcelain=v1 -- $optionalPath)
    if ($LASTEXITCODE -ne 0) { throw "读取可选 POM 状态失败：$optionalPath" }
    if ($status.Count -gt 0) { $optionalPath }
  }
)
$expected = @($required + $changedOptional)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存装配边界文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "test: 锁定工作区接口装配边界"
if ($LASTEXITCODE -ne 0) { throw '提交装配边界失败' }
```

## 任务 10：建立用户端工作区类型、接口与 `46126` 恢复

**最小任务卡：**

- **单一目标／不做：** 建立用户端工作区类型、`R.data` 适配和 `46126` 恢复；切换请求不发送服务端事实，不自动重放失败请求。
- **风险／触发：** 红色；命中前后端契约、会话失效、失败恢复与全局登录副作用。
- **权威来源：** API 契约、`WorkspaceSummaryDTO`／`SwitchWorkspaceDTO` 和任务 7 的单字段请求边界。
- **成功／反向验收：** 只发送 `workspaceKey`；ID／修订均为字符串；`46126` 刷新列表并回个人工作区，原请求只执行一次。
- **所有权／数据范围：** 开发 A 是本任务 workspace domain 文件的唯一写 owner；现有 `core/types.ts`、`core/errors.ts`、`core/ruoyiAdapter.ts` 仅作只读依赖，禁止本任务改写公共响应／错误适配；不修改页面草稿归属。开发 B／C 对本任务文件均只读。
- **依赖／人员／并发：** 依赖任务 7；开发 A 实施，开发 B 做独立契约／失败恢复审查；开发 B 继续实施自己的 P1，不能编辑 P0-B。同一红色任务最多 2 人。
- **验证／检查点：** 先 review 请求负载和错误适配，再运行目标 Vitest 与 TypeScript 检查。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\types.ts`
- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\errors.ts`
- 只读复用：`ai-video-ui\ai-video-webapp\src\services\ai-video\core\ruoyiAdapter.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\types.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\api.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\queryKeys.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\useWorkspace.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\api.test.ts`
- 创建：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\useWorkspace.test.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的响应适配和失效测试**

```typescript
it('只发送工作区键', async () => {
  requestMock.mockResolvedValue({
    code: 200,
    msg: '操作成功',
    data: organizationWorkspace,
  });

  await switchWorkspace({ workspaceKey: 'opaque-key' });

  expect(requestMock).toHaveBeenCalledWith('/api/auth/current-workspace', {
    method: 'PUT',
    data: { workspaceKey: 'opaque-key' },
  });
});

it('切换恰好一次并将 46126 保留为数字业务错误', async () => {
  requestMock.mockResolvedValue({
    code: 46126,
    msg: '当前工作区不可用',
    data: null,
  });

  await expect(switchWorkspace({ workspaceKey: 'expired-org' }))
    .rejects.toMatchObject({ name: 'ApiError', code: 46126 });
  expect(requestMock).toHaveBeenCalledTimes(1);
});

it('46126 只刷新一次列表并只选择一次个人项，不重放或触发全局登出', async () => {
  switchWorkspaceMock.mockRejectedValueOnce(
    new ApiError({ code: 46126, msg: '当前工作区不可用' }));
  listWorkspacesMock.mockResolvedValueOnce([
    personalWorkspace,
    organizationWorkspace,
  ]);

  await recoverUnavailableWorkspace('expired-org');

  expect(switchWorkspaceMock).toHaveBeenCalledTimes(1);
  expect(listWorkspacesMock).toHaveBeenCalledTimes(1);
  expect(selectWorkspaceMock).toHaveBeenCalledTimes(1);
  expect(selectWorkspaceMock).toHaveBeenCalledWith(personalWorkspace);
  expect(rebindDraftMock).not.toHaveBeenCalled();
  expect(clearSessionMock).not.toHaveBeenCalled();
  expect(redirectToLoginMock).not.toHaveBeenCalled();
});
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
$redReport = Join-Path ([System.IO.Path]::GetTempPath()) 'p0b-workspace-api-red.json'
Remove-Item -LiteralPath $redReport -Force -ErrorAction SilentlyContinue
npm.cmd test -- src/services/ai-video/workspace/api.test.ts `
  src/services/ai-video/workspace/useWorkspace.test.tsx `
  --reporter=json --outputFile=$redReport
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 workspace api 目标测试真实失败' }
if (-not (Test-Path -LiteralPath $redReport)) {
  throw '红灯没有生成目标测试 JSON 报告'
}
$redResult = Get-Content -Raw -LiteralPath $redReport | ConvertFrom-Json
if ([int]$redResult.numTotalTests -lt 1 -or [int]$redResult.numFailedTests -lt 1) {
  throw '红灯必须是 workspace api 至少一项测试真实执行并失败'
}
```

预期：先按统一约定建立工作区 TypeScript 导出骨架，目标测试真正执行，并因适配、失效或“不重放”业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小类型与接口**

```typescript
import { ApiError } from '../core/errors';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';

export function createWorkspaceApi(adapter: RuoYiAdapter) {
  return {
    listWorkspaces: () =>
      adapter.request<Workspace[]>('/api/auth/workspaces'),
    switchWorkspace: (input: SwitchWorkspaceInput) =>
      adapter.request<Workspace>('/api/auth/current-workspace', {
        method: 'PUT',
        data: { workspaceKey: input.workspaceKey },
      }),
  };
}

export function isWorkspaceUnavailable(error: unknown): boolean {
  return error instanceof ApiError && error.code === 46126;
}
```

本任务必须直接消费现有 `R<T>`／`RuoYiResponse<T>`、`ApiError` 和 `createRuoYiAdapter` 产物；不得创建第二个响应包装、错误类、解包函数或修改全局会话失效码集合。workspace API 只接收已配置好的 `RuoYiAdapter`，由现有适配器把非 `200` 数字业务码转为 `ApiError`。

工作区类型固定为：

```typescript
export type WorkspaceType = 'personal' | 'organization';

export interface Workspace {
  workspaceKey: string;
  workspaceType: WorkspaceType;
  displayName: string;
  tenantId: string;
  ownerType: 'app_user' | 'organization';
  ownerId: string;
  billingSubjectType: 'app_user' | 'organization';
  billingSubjectId: string;
  roleCode: string;
  permissions: string[];
  workspaceRevision: string;
  membershipRevision: string | null;
  current: boolean;
}

export interface SwitchWorkspaceInput {
  workspaceKey: string;
}
```

`useWorkspace` 遇到 `46126` 时只刷新一次工作区列表并在本地选择一次服务端返回的个人项；失败的原组织切换总调用次数保持一次，不发起个人切换请求，不触发全局 logout／清理 token／登录重定向，也不把当前草稿重绑到个人工作区。`api.test.ts` 锁定适配与单字段负载，`useWorkspace.test.tsx` 锁定刷新一次、选择一次、不重放、草稿零重绑和全局登录零副作用。

- [ ] **步骤 4（2–5 分钟）：运行服务测试和类型检查**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- src/services/ai-video/workspace/api.test.ts `
  src/services/ai-video/workspace/useWorkspace.test.tsx
if ($LASTEXITCODE -ne 0) { throw 'workspace api 测试失败' }
npm.cmd run tsc
if ($LASTEXITCODE -ne 0) { throw '用户端类型检查失败' }
```

预期：服务测试通过，TypeScript 无错误。

- [ ] **步骤 5（2–5 分钟）：提交用户端工作区服务**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/types.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/api.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/queryKeys.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/useWorkspace.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/api.test.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/useWorkspace.test.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存用户端工作区服务文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 建立用户端工作区接口适配"
if ($LASTEXITCODE -ne 0) { throw '提交用户端工作区服务失败' }
```

## 任务 11：实现工作区切换器和未提交输入保护

**最小任务卡：**

- **单一目标／不做：** 接入工作区切换器和未提交输入保护；不重绑已打开草稿，不静默丢弃输入。
- **风险／触发：** 红色；命中用户输入保护、工作区切换、草稿所有权和失败请求重放。
- **权威来源：** 前端指南、任务 10 的 `useWorkspace` 契约和原业务规格。
- **成功／反向验收：** 加载／空／失败／切换中状态完整；取消不切换；确认只清理未提交 UI 输入；已打开草稿保持原工作区。
- **所有权／数据范围：** 开发 A 是本任务工作台组件、模型和样式的唯一写 owner；任务 10 的 workspace service 只读消费，不改服务端事实或草稿归属字段。开发 B／C 对本任务文件均只读。
- **依赖／人员／并发：** 依赖任务 10；开发 A 实施，开发 B 做独立交互／错误恢复审查；开发 B 继续实施自己的 P1，不能编辑 P0-B。同一红色任务最多 2 人。
- **验证／检查点：** 先 review 未提交确认和草稿不重绑，再运行组件测试、Lint、构建。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.tsx`
- 创建：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.test.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\StudioTopbar.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\index.tsx`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\model.ts`
- 修改：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\style.css`

- [ ] **步骤 1（2–5 分钟）：编写失败的交互测试**

```tsx
it('存在未提交输入时先确认，取消后不切换', async () => {
  const user = userEvent.setup();
  const switchCurrent = vi.fn();
  render(
    <WorkspaceSwitcher
      workspaces={[personalWorkspace, organizationWorkspace]}
      current={personalWorkspace}
      hasUnsavedInput
      activeDraftWorkspaceKey={null}
      loading={false}
      onSwitch={switchCurrent}
    />,
  );

  await user.click(screen.getByRole('combobox', { name: '当前工作区' }));
  await user.click(screen.getByText('北辰内容工作室'));
  expect(await screen.findByText('保存或放弃未提交内容')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '继续编辑' }));
  expect(switchCurrent).not.toHaveBeenCalled();
});

it('切换后不改写已打开草稿并提示切回原工作区', async () => {
  const user = userEvent.setup();
  renderSwitcher({ activeDraftWorkspaceKey: 'personal-key' });
  await selectWorkspace(user, '北辰内容工作室');
  expect(await screen.findByRole('alert')).toHaveTextContent(
    '当前草稿仍属于个人工作区，请切回原工作区继续编辑',
  );
  expect(updateDraftOwner).not.toHaveBeenCalled();
});
```

- [ ] **步骤 2（2–5 分钟）：运行组件测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
$redReport = Join-Path ([System.IO.Path]::GetTempPath()) 'p0b-workspace-switcher-red.json'
Remove-Item -LiteralPath $redReport -Force -ErrorAction SilentlyContinue
npm.cmd test -- `
  src/pages/digital-human-studio/components/WorkspaceSwitcher.test.tsx `
  --reporter=json --outputFile=$redReport
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到 WorkspaceSwitcher 目标测试真实失败' }
if (-not (Test-Path -LiteralPath $redReport)) {
  throw '红灯没有生成 WorkspaceSwitcher 测试 JSON 报告'
}
$redResult = Get-Content -Raw -LiteralPath $redReport | ConvertFrom-Json
if ([int]$redResult.numTotalTests -lt 1 -or [int]$redResult.numFailedTests -lt 1) {
  throw '红灯必须是 WorkspaceSwitcher 至少一项测试真实执行并失败'
}
```

预期：先按统一约定建立返回 `null` 的 `WorkspaceSwitcher` 编译骨架，目标测试真正执行，并因交互断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小组件并接入顶部栏**

使用已通过 Ant Design CLI 6.5.1 查询的 `Select` `options/loading/value/onChange` 和 `Modal` `open/onOk/onCancel/confirmLoading/destroyOnHidden`：

```tsx
<Select
  aria-label="当前工作区"
  loading={loading}
  value={current?.workspaceKey}
  options={workspaces.map((workspace) => ({
    value: workspace.workspaceKey,
    label: workspace.displayName,
  }))}
  onChange={requestSwitch}
/>
<Modal
  open={Boolean(pendingWorkspace)}
  title="保存或放弃未提交内容"
  okText="放弃并切换"
  cancelText="继续编辑"
  confirmLoading={switching}
  destroyOnHidden
  onOk={confirmSwitch}
  onCancel={cancelSwitch}
>
  当前页面有尚未提交的输入。切换只影响之后新建或查询的资源。
</Modal>
```

`StudioTopbar` 展示当前工作区名称。`Studio` 只清理未提交的界面草稿，不修改已打开草稿编号、归属摘要或计费主体；发现草稿工作区不匹配时展示 `Alert` 并提供“切回草稿工作区”按钮。

- [ ] **步骤 4（2–5 分钟）：运行组件测试、Lint 和构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- src/pages/digital-human-studio/components/WorkspaceSwitcher.test.tsx
if ($LASTEXITCODE -ne 0) { throw 'WorkspaceSwitcher 测试失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '用户端 Lint 失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '用户端构建失败' }
```

预期：未提交确认、取消、确认、草稿不重绑、`46126` 回个人且不重放均通过；Lint 和构建退出码为 `0`。

- [ ] **步骤 5（2–5 分钟）：提交工作区界面**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/WorkspaceSwitcher.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/WorkspaceSwitcher.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioTopbar.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存工作区切换界面文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 接入创作工作区切换器"
if ($LASTEXITCODE -ne 0) { throw '提交工作区切换界面失败' }
```

## 任务 12：实现运营端组织和成员管理页

**最小任务卡：**

- **单一目标／不做：** 实现运营端组织／成员管理页与完整管理状态；不绕过权限、修订冲突或删除确认。
- **风险／触发：** 红色；命中高权限管理、成员修订、删除确认和管理页完整状态。
- **权威来源：** 前端指南、API 契约、任务 8 的平台 BO／VO 和运营端权限标识。
- **成功／反向验收：** 加载／空／无搜索结果／失败／403／分页完整；管理员写请求保留预期组织／成员修订和中文原因；冲突只刷新准确行。
- **所有权／数据范围：** 开发 A 是本任务 platform UI API、页面、抽屉和测试的唯一写 owner；任务 8 的平台 HTTP 契约只读消费，不修改用户切换接口。开发 B／C 对本任务文件均只读。
- **依赖／人员／并发：** 依赖任务 8；开发 A 实施，开发 B 做独立权限／状态矩阵审查；开发 B／C 分别继续实施自己的 P1／P2，不能编辑 P0-B。同一红色任务最多 2 人。
- **验证／检查点：** 先 review 权限态、确认态和管理员修订字段，再运行页面测试、Lint、生产构建。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui\ai-video-platform-ui\src\api\aivideo\organization\types.ts`
- 创建：`ai-video-ui\ai-video-platform-ui\src\api\aivideo\organization\index.ts`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\index.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\components\MemberDrawer.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\index.test.tsx`
- 创建：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization\components\MemberDrawer.test.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的管理页状态测试**

```tsx
it.each([
  ['空列表', { rows: [], total: 0 }, '暂无创作组织'],
  ['无权限', Promise.reject(apiError(403, '权限不足')), '无权查看创作组织'],
  ['接口失败', Promise.reject(apiError(500, '服务暂不可用')), '重新加载'],
])('%s 状态可见', async (_name, result, expectedText) => {
  listOrganizationsMock.mockImplementation(() =>
    result instanceof Promise ? result : Promise.resolve(okPage(result)),
  );
  renderOrganizationPage();
  expect(await screen.findByText(expectedText)).toBeInTheDocument();
});

it('移出成员要求原因和当前成员修订号', async () => {
  renderMemberDrawer();
  await removeMember(user, '王晨');
  expect(leaveMemberMock).toHaveBeenCalledWith('7', '102', {
    expectedMembershipRevision: '4',
    reason: '成员离开项目组',
  });
});
```

- [ ] **步骤 2（2–5 分钟）：运行页面测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
$redReport = Join-Path ([System.IO.Path]::GetTempPath()) 'p0b-organization-pages-red.json'
Remove-Item -LiteralPath $redReport -Force -ErrorAction SilentlyContinue
pnpm exec vitest run `
  src/pages/aivideo/organization/index.test.tsx `
  src/pages/aivideo/organization/components/MemberDrawer.test.tsx `
  --reporter=json --outputFile=$redReport
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到组织页面目标测试真实失败' }
if (-not (Test-Path -LiteralPath $redReport)) {
  throw '红灯没有生成组织页面测试 JSON 报告'
}
$redResult = Get-Content -Raw -LiteralPath $redReport | ConvertFrom-Json
if ([int]$redResult.numTotalTests -lt 1 -or [int]$redResult.numFailedTests -lt 1) {
  throw '红灯必须是组织页面至少一项测试真实执行并失败'
}
```

预期：先按统一约定建立页面、抽屉和接口导出骨架，目标测试真正执行，并因页面状态或成员操作断言失败。若 P0-A 未建立平台 Vitest 配置，回到 P0-A 门禁补齐其测试产物，不在此任务另造第二套配置。

- [ ] **步骤 3（2–5 分钟）：实现 API、ProTable 和成员抽屉**

API 路径固定为：

```typescript
export const listOrganizations = (query: OrganizationQuery) =>
  request<R<PageResult<OrganizationVO>>>({
    url: '/api/admin/app-organizations',
    method: 'get',
    params: query,
  });

export const listMembers = (organizationId: string, query: MemberQuery) =>
  request<R<PageResult<OrgMemberVO>>>({
    url: `/api/admin/app-organizations/${organizationId}/members`,
    method: 'get',
    params: query,
  });
```

页面复用现有 `PageContainer`、`ProTable`、`ModalForm`、`RowActions`、`hasPermi`、`toPageQuery` 和 `toTableData`。组织页覆盖加载、空、无搜索结果、失败、权限不足、分页；成员抽屉覆盖添加、改角色、停用、恢复、设置有效期和标记离开。所有写操作展示修订冲突并刷新准确行，不覆盖用户刚输入的原因。

- [ ] **步骤 4（2–5 分钟）：运行页面测试、Lint 和生产构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run `
  src/pages/aivideo/organization/index.test.tsx `
  src/pages/aivideo/organization/components/MemberDrawer.test.tsx
if ($LASTEXITCODE -ne 0) { throw '运营端组织页面测试失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '运营端 Lint 失败' }
pnpm run build:prod
if ($LASTEXITCODE -ne 0) { throw '运营端生产构建失败' }
```

预期：两份测试通过，Lint 和生产构建退出码为 `0`。

- [ ] **步骤 5（2–5 分钟）：提交运营端页面**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区失败' }
if ($stagedBefore.Count -ne 0) {
  throw "提交前暂存区必须为空：$($stagedBefore -join ', ')"
}
$expected = @(
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/organization/types.ts',
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/organization/index.ts',
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/organization/index.tsx',
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/organization/components/MemberDrawer.tsx',
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/organization/index.test.tsx',
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/organization/components/MemberDrawer.test.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存运营端组织页面文件失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存结果失败' }
$stagingDiff = Compare-Object `
  ($expected | Sort-Object -Unique) `
  ($actual | Sort-Object -Unique)
if ($stagingDiff) { $stagingDiff; throw '暂存文件集合与任务文件不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式检查失败' }
git commit -m "feat: 提供创作组织与成员管理页"
if ($LASTEXITCODE -ne 0) { throw '提交运营端组织页面失败' }
```

## 任务 13：执行 P0-B 全量门禁与三视角审查

**最小任务卡：**

- **单一目标／不做：** 执行 P0-B 全量后端／前端／文档门禁并记录三视角审查；不顺手扩展 P0-C 或 P1–P3 实现。
- **风险／触发：** 红色；命中跨模块集成、安全边界、迁移、构建和最终验收。
- **权威来源：** 本 P0-B 计划、AI Agent 治理、开发规范、任务 1–12 的固定验收证据，以及批准规格与主计划 Tasks 4–7 对未来完整 F1 冻结和各分支 rebase 的职责边界。
- **成功／反向验收：** 单元和八个 IT 报告均为本次非零、零跳过全绿；一次性证明、guard-first、事务内审计回滚、提交后会话失效恢复、双启动真实 HTTP、强负向扫描、PowerShell AST、前端测试／Lint／构建和规范校验全部通过；仅冻结完整 40 位 P0-B candidate SHA 与本 worktree 专属候选交接记录，不读取或修改下游 worktree，不执行 rebase，不宣称完整 F1 已就绪。
- **所有权／数据范围：** 只验证 P0-B 冻结文件集并记录审查；修正回到原任务精确文件集，不大目录补提。
- **依赖／人员／并发：** 依赖任务 1–12；开发 A 只提交证据并按意见修复，不得给出审查通过结论。开发 B 是独立主审，负责后端授权、迁移、会话 provenance、事务与全量门禁；开发 C 仅在 B 主审关闭后进入不重叠的只读专项窗口，核对前端未提交输入／管理状态矩阵以及候选交接字段和完整 F1 阻塞声明。单一 reviewer 无法同时提供后端安全事务与前端交互／阶段移交两种独立专业判断，因此需要该串行专项；任一时刻只能是 A+B 或 A+C，同一红色审查最多 2 人，B／C 都不得编辑 P0-B。
- **验证／检查点：** P0-B candidate 冻结前逐项核对 XML UTC 新鲜度、禁用路径／旧契约／用户额外字段、三视角签字，以及候选是 F0 的非空后继。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`；另附 P0-B candidate 完整 SHA／摘要、owner A、reviewers B/C、F0、目标 P0-C、`fullF1Ready=false` 与 `downstreamRebaseBlockedUntil='P0-C complete F1'`。

**文件：**
- 验证：`ai-video-api\script\sql\ai-video\mysql\20260728_01_p0a_identity_security.sql`
- 验证：`ai-video-api\script\sql\ai-video\mysql\20260728_02_p0b_workspace_authorization.sql`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\IAppPermissionService.java`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImpl.java`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImplTest.java`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionIntegrationTestFixture.java`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionWorkspaceInvalidationIT.java`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\mapper`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization`
- 验证：`ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization`
- 验证：`ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo\assembly`
- 验证：`ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo\assembly`
- 验证：`ai-video-ui\ai-video-webapp\src\services\ai-video\workspace`
- 验证：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.tsx`
- 验证：`ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\WorkspaceSwitcher.test.tsx`
- 验证：`ai-video-ui\ai-video-platform-ui\src\api\aivideo\organization`
- 验证：`ai-video-ui\ai-video-platform-ui\src\pages\aivideo\organization`
- 验证：`docs\superpowers\specs\2026-07-28-say-requirements-copy-generation-design.md`

- [ ] **步骤 1（2–5 分钟）：运行后端单元测试，显式关闭集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$unitReportDirs = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\target\surefire-reports',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\target\surefire-reports',
  'ai-video-api\ai-video-user-api\target\surefire-reports',
  'ai-video-api\ruoyi-admin\target\surefire-reports'
)
$requiredUnitReports = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationServiceTest.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\surefire-reports\TEST-org.dromara.aivideo.identity.service.impl.AppSessionServiceImplTest.xml'
)
foreach ($reportDir in $unitReportDirs) {
  Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File `
    -ErrorAction SilentlyContinue | Remove-Item -Force
}
$unitGateStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl `
  ruoyi-modules/ai-video/ai-video-core,`
  ruoyi-modules/ai-video/ai-video-user,`
  ruoyi-modules/ai-video/ai-video-platform,`
  ai-video-user-api,ruoyi-admin -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
if ($LASTEXITCODE -ne 0) { throw 'P0-B 后端单元测试门禁失败' }
$unitReports = @(
  foreach ($reportDir in $unitReportDirs) {
    Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File `
      -ErrorAction SilentlyContinue
  }
)
if ($unitReports.Count -lt 1) { throw 'P0-B 单元测试门禁没有生成 Surefire XML' }
foreach ($reportItem in $unitReports) {
  if ($reportItem.LastWriteTimeUtc -lt $unitGateStartedAt) {
    throw "$($reportItem.FullName) 不是本次单元测试门禁生成"
  }
  [xml]$report = Get-Content -Raw -LiteralPath $reportItem.FullName
  $tests = [int]$report.testsuite.tests
  $failures = [int]$report.testsuite.failures
  $errors = [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "$($reportItem.FullName) 不是本次非零全绿报告"
  }
}
foreach ($requiredReportPath in $requiredUnitReports) {
  if (-not (Test-Path -LiteralPath $requiredReportPath)) {
    throw "P0-B 缺少必需单元测试报告：$requiredReportPath"
  }
  $requiredReportItem = Get-Item -LiteralPath $requiredReportPath
  if ($requiredReportItem.LastWriteTimeUtc -lt $unitGateStartedAt) {
    throw "必需单元测试报告不是本次门禁生成：$requiredReportPath"
  }
}
```

预期：Surefire 执行单元测试；日志不含 `Tests are skipped`；Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 2（2–5 分钟）：运行后端集成测试，显式启用单元与集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$itReports = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceSchemaIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.identity.AppSessionWorkspaceInvalidationIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.OrganizationAdminServiceIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\target\failsafe-reports\TEST-org.dromara.aivideo.user.authorization.WorkspaceControllerIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\target\failsafe-reports\TEST-org.dromara.aivideo.platform.authorization.AppOrganizationControllerIT.xml',
  'ai-video-api\ai-video-user-api\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.UserAuthorizationBoundaryIT.xml',
  'ai-video-api\ruoyi-admin\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.PlatformAuthorizationBoundaryIT.xml'
)
$itReports | ForEach-Object {
  Remove-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue
}
$itGateStartedAt = [DateTime]::UtcNow
& (Join-Path $repoRoot 'ai-video-api\mvnw.cmd') -f (Join-Path $repoRoot 'ai-video-api\pom.xml') -pl `
  ruoyi-modules/ai-video/ai-video-core,`
  ruoyi-modules/ai-video/ai-video-user,`
  ruoyi-modules/ai-video/ai-video-platform,`
  ai-video-user-api,ruoyi-admin -am `
  '-Pdev,local-integration-test' `
  --fail-at-end `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test='WorkspaceSchemaIT,WorkspaceAuthorizationIT,AppSessionWorkspaceInvalidationIT,OrganizationAdminServiceIT,WorkspaceControllerIT,AppOrganizationControllerIT,UserAuthorizationBoundaryIT,PlatformAuthorizationBoundaryIT' verify
if ($LASTEXITCODE -ne 0) { throw 'P0-B 后端集成测试门禁失败' }
foreach ($reportPath in $itReports) {
  if (-not (Test-Path -LiteralPath $reportPath)) { throw "$reportPath 未生成本次报告" }
  $reportItem = Get-Item -LiteralPath $reportPath
  if ($reportItem.LastWriteTimeUtc -lt $itGateStartedAt) {
    throw "$reportPath 不是本次集成测试门禁生成"
  }
  [xml]$report = Get-Content -Raw -LiteralPath $reportPath
  $tests = [int]$report.testsuite.tests
  $failures = [int]$report.testsuite.failures
  $errors = [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "$reportPath 不是本次非零全绿报告"
  }
}
```

预期：Surefire 和 Failsafe 都有非零执行数量；数据库、接口和装配测试通过；Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 3（2–5 分钟）：核对所有 IT 标签与非零 Failsafe 报告**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$itSources = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceSchemaIT.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationIT.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionWorkspaceInvalidationIT.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\OrganizationAdminServiceIT.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization\WorkspaceControllerIT.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\test\java\org\dromara\aivideo\platform\authorization\AppOrganizationControllerIT.java',
  'ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo\assembly\UserAuthorizationBoundaryIT.java',
  'ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo\assembly\PlatformAuthorizationBoundaryIT.java'
)
foreach ($source in $itSources) {
  if (-not (Select-String -LiteralPath $source -SimpleMatch '@Tag("dev")' -Quiet)) {
    throw "$source 缺少类级 @Tag(`"dev`")"
  }
}
$reports = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceSchemaIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.WorkspaceAuthorizationIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.identity.AppSessionWorkspaceInvalidationIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports\TEST-org.dromara.aivideo.authorization.OrganizationAdminServiceIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\target\failsafe-reports\TEST-org.dromara.aivideo.user.authorization.WorkspaceControllerIT.xml',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\target\failsafe-reports\TEST-org.dromara.aivideo.platform.authorization.AppOrganizationControllerIT.xml',
  'ai-video-api\ai-video-user-api\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.UserAuthorizationBoundaryIT.xml',
  'ai-video-api\ruoyi-admin\target\failsafe-reports\TEST-org.dromara.aivideo.assembly.PlatformAuthorizationBoundaryIT.xml'
)
foreach ($reportPath in $reports) {
  [xml]$report = Get-Content -Raw -LiteralPath $reportPath
  $tests = [int]$report.testsuite.tests
  $failures = [int]$report.testsuite.failures
  $errors = [int]$report.testsuite.errors
  $skipped = [int]$report.testsuite.skipped
  if ($tests -lt 1 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "$reportPath 不是非零全绿报告"
  }
  $report.testsuite | Select-Object name, tests, failures, errors, skipped
}
```

预期：八个 IT 源文件均有类级 `@Tag("dev")`；八份报告均为
`tests > 0, failures=0, errors=0, skipped=0`。

- [ ] **步骤 4（2–5 分钟）：运行两端前端与文档验证**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test
if ($LASTEXITCODE -ne 0) { throw '用户端全量测试失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '用户端全量 Lint 失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '用户端生产构建失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run
if ($LASTEXITCODE -ne 0) { throw '运营端全量测试失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '运营端全量 Lint 失败' }
pnpm run build:prod
if ($LASTEXITCODE -ne 0) { throw '运营端生产构建失败' }
Set-Location -LiteralPath $repoRoot
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '开发规范校验失败' }
```

预期：两端测试、Lint、构建全部成功，文档校验输出 `DEVELOPMENT_STANDARDS_OK`。

- [ ] **步骤 5（2–5 分钟）：执行范围、重复事实源、占位和敏感边界扫描**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$redFlags = @(
  ('TO' + 'DO'),
  ('待' + '定'),
  ('后续' + '实现'),
  ('补充' + '细节'),
  ('添加' + '适当'),
  ('处理' + '边界'),
  ('类似' + '任务')
)
$hits = Select-String `
  -LiteralPath docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md `
  -Pattern $redFlags
if ($hits) { $hits; exit 1 }
rg -n "\bStpUtil\b|\bLoginHelper\b|app_auth_client|app_social_identity|POST /api/auth/login" `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization `
  ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization
$sensitiveScanExitCode = $LASTEXITCODE
if ($sensitiveScanExitCode -eq 0) { throw '敏感身份边界扫描存在命中' }
if ($sensitiveScanExitCode -gt 1) { throw '敏感身份边界扫描执行失败' }
$duplicateTypeHits = rg -n `
  '\b(?:class|interface|record)\s+(?:AppRole|AppRolePermission|AppUserRole|AppRoleMapper|AppRolePermissionMapper|AppUserRoleMapper)\b' `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization
if ($LASTEXITCODE -eq 0) { $duplicateTypeHits; exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
$duplicateDdlHits = rg -n `
  '(?i)\b(?:CREATE\s+(?:TEMPORARY\s+)?TABLE(?:\s+IF\s+NOT\s+EXISTS)?|ALTER\s+TABLE|DROP\s+TABLE(?:\s+IF\s+EXISTS)?)\s+`?(?:app_role|app_role_permission|app_user_role)`?\b' `
  ai-video-api\script\sql\ai-video\mysql\20260728_02_p0b_workspace_authorization.sql
if ($LASTEXITCODE -eq 0) { $duplicateDdlHits; exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
$sql = Get-Content -Raw -LiteralPath `
  ai-video-api\script\sql\ai-video\mysql\20260728_02_p0b_workspace_authorization.sql
$ddlTables = [regex]::Matches(
  $sql,
  '(?im)^\s*CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?([a-z0-9_]+)`?'
) | ForEach-Object { $_.Groups[1].Value }
$expectedTables = @(
  'app_organization',
  'app_org_member',
  'av_resource_grant'
)
if (Compare-Object ($ddlTables | Sort-Object) ($expectedTables | Sort-Object)) {
  throw "P0-B DDL 表集合错误：$($ddlTables -join ',')"
}
if ($sql -match '(?i)\bgranted_by_user_id\b') {
  throw '资源授权仍使用无法区分身份域的 granted_by_user_id'
}
$requiredActorSql = @(
  'granted_by_type',
  'granted_by_id',
  'created_actor_type',
  'created_actor_id',
  'updated_actor_type',
  'updated_actor_id',
  'ck_app_organization_actor_types',
  'ck_app_org_member_actor_types',
  'ck_av_resource_grant_actor_types'
)
foreach ($requiredText in $requiredActorSql) {
  if ($sql -notmatch [regex]::Escape($requiredText)) {
    throw "P0-B DDL 缺少 typed actor 契约：$requiredText"
  }
}
$organizationAdminService = Get-Content -Raw -LiteralPath `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\IOrganizationAdminService.java
$requiredActorSignatures = @(
  '(?s)\bcreate\s*\([^;]*AppActorContext\s+actor\s*\)',
  '(?s)\bupdate\s*\([^;]*AppActorContext\s+actor\s*\)',
  '(?s)\bupsertMember\s*\([^;]*AppActorContext\s+actor\s*\)',
  '(?s)\bleaveMember\s*\([^;]*AppActorContext\s+actor\s*\)'
)
foreach ($signature in $requiredActorSignatures) {
  if ($organizationAdminService -notmatch $signature) {
    throw "IOrganizationAdminService 写方法缺少显式 actor：$signature"
  }
}
$organizationAdminImpl = Get-Content -Raw -LiteralPath `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\OrganizationAdminServiceImpl.java
$organizationInvalidationListener = Get-Content -Raw -LiteralPath `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\listener\OrganizationSessionInvalidationListener.java
$requiredFirstLineGuards = @(
  '(?s)\bcreate\s*\([^)]*AppActorContext\s+actor\s*\)\s*\{\s*requireSysAdminActor\s*\(\s*actor\s*\)\s*;',
  '(?s)\bupdate\s*\([^)]*AppActorContext\s+actor\s*\)\s*\{\s*requireSysAdminActor\s*\(\s*actor\s*\)\s*;',
  '(?s)\bupsertMember\s*\([^)]*AppActorContext\s+actor\s*\)\s*\{\s*requireSysAdminActor\s*\(\s*actor\s*\)\s*;',
  '(?s)\bleaveMember\s*\([^)]*AppActorContext\s+actor\s*\)\s*\{\s*requireSysAdminActor\s*\(\s*actor\s*\)\s*;'
)
foreach ($guard in $requiredFirstLineGuards) {
  if ($organizationAdminImpl -notmatch $guard) {
    throw "OrganizationAdminServiceImpl 写入口未把 sys_user 校验放在第一条业务语句：$guard"
  }
}
if ($organizationAdminImpl -notmatch
    '(?s)requireSysAdminActor\s*\([^)]*\).*AppActorType\.SYS_USER') {
  throw 'requireSysAdminActor 未显式要求 actorType=sys_user'
}
$organizationAdminTest = Get-Content -Raw -LiteralPath `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\OrganizationAdminServiceIT.java
foreach ($requiredAssertion in @(
    'everyAdminWriteRejectsAppActorWithSameNumericIdWithoutSideEffects',
    'AppActorContext.appUser(1001L)',
    '"av_resource_grant"',
    '"app_security_audit"',
    'verifyNoInteractions(appSecurityAuditService, appSessionService)')) {
  if ($organizationAdminTest -notmatch [regex]::Escape($requiredAssertion)) {
    throw "OrganizationAdminServiceIT 缺少同号跨身份域零副作用断言：$requiredAssertion"
  }
}
$appActorResolver = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\security\AppAuthorizationActorResolver.java'
$sysActorResolver = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization\security\SysAuthorizationActorResolver.java'
if (Select-String -LiteralPath $appActorResolver `
    -Pattern '\bLoginHelper\b|\bStpUtil\b|org\.dromara\.system' -Quiet) {
  throw '用户 actor resolver 触碰运营身份域'
}
if (Select-String -LiteralPath $sysActorResolver `
    -Pattern 'AppLoginHelper|AppUserMapper|IAppIdentityService' -Quiet) {
  throw '运营 actor resolver 触碰创作身份域'
}
$platformRoot = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform'
$defaultLoginHelperActual = @(
  Get-ChildItem -LiteralPath $platformRoot -Recurse -File -Filter '*.java' |
    Select-String -SimpleMatch `
      'org.dromara.common.satoken.utils.LoginHelper' |
    Select-Object -ExpandProperty Path -Unique |
    ForEach-Object { [System.IO.Path]::GetFullPath($_) }
)
$defaultLoginHelperExpected = @(
  [System.IO.Path]::GetFullPath(
    'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\identity\service\impl\AppIdentityAdminServiceImpl.java'),
  [System.IO.Path]::GetFullPath($sysActorResolver)
)
if (Compare-Object `
    ($defaultLoginHelperExpected | Sort-Object -Unique) `
    ($defaultLoginHelperActual | Sort-Object -Unique)) {
  throw 'P0-B 累计默认 LoginHelper 调用点偏离精确白名单'
}
$coreAuthorizationRoot = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization'
$coreIdentityRoot = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity'
$coreAuthorizationRoots = @(
  $coreAuthorizationRoot,
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization'
)
$endpointAuthorizationRoots = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\test\java\org\dromara\aivideo\platform\authorization'
)
$coreFiles = @(
  foreach ($root in $coreAuthorizationRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File
  }
)
$coreForbiddenPathPattern = `
  '\\(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra|client|provider)\\'
$coreForbiddenPaths = @($coreFiles | Where-Object {
    $_.FullName -match $coreForbiddenPathPattern -or
    $_.FullName -match '\\domain\\(?:bo|vo|enums)\\'
  })
if ($coreForbiddenPaths) {
  $coreForbiddenPaths.FullName
  throw 'core authorization 出现禁止分层目录或 core BO/VO/domain/enums'
}
$endpointFiles = @(
  foreach ($root in $endpointAuthorizationRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File
  }
)
$endpointForbiddenPaths = @($endpointFiles | Where-Object {
    $_.FullName -match `
      '\\(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra|client|provider)\\'
  })
if ($endpointForbiddenPaths) {
  $endpointForbiddenPaths.FullName
  throw '端侧 authorization 出现禁止分层目录'
}
$authorizationJavaFiles = @($coreFiles + $endpointFiles | Where-Object Extension -eq '.java')
$startupBoundaryTestRoots = @(
  'ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo',
  'ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo'
)
$startupBoundaryTestFiles = @(
  foreach ($root in $startupBoundaryTestRoots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.java'
  }
)
$authorizationContentFiles = @($authorizationJavaFiles + $startupBoundaryTestFiles)
$dottedForbiddenHits = Select-String -LiteralPath $authorizationContentFiles.FullName `
  -Pattern 'org\.dromara\.aivideo\.(?:authorization|user\.authorization|platform\.authorization)\.(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra|client|provider)\.' `
  -CaseSensitive
if ($dottedForbiddenHits) { $dottedForbiddenHits; throw '出现禁止的点分包名' }
$legacyScanRoots = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java\org\dromara\aivideo\platform\authorization',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\test\java\org\dromara\aivideo\platform\authorization',
  'ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo',
  'ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo'
)
$legacyContractFiles = @(
  foreach ($legacyRoot in $legacyScanRoots) {
    Get-ChildItem -LiteralPath $legacyRoot -Recurse -File -Filter '*.java'
  }
)
$legacySimpleNames = @(
  ('AppIdentity' + 'Service'),
  ('AppSession' + 'Service'),
  ('AppPermission' + 'Service'),
  ('AppSecurityAudit' + 'Service'),
  ('WorkspaceAuthorization' + 'Service'),
  ('OrganizationAdmin' + 'Service'),
  ('ResourceOwnership' + 'Service'),
  ('SwitchWorkspace' + 'Command')
)
$legacyMethods = @(
  ('require' + 'Principal'),
  ('current' + 'Principal')
)
$legacyAuditFields = @(
  ('before' + 'Hash'),
  ('after' + 'Hash'),
  ('trace' + 'Id')
)
$legacyNamePattern = '(?<!I)\b(?:' +
  (($legacySimpleNames | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\b'
$legacyMethodPattern = '\b(?:' +
  (($legacyMethods | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\s*\('
$legacyAuditPattern = '\b(?:' +
  (($legacyAuditFields | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')\b'
$legacyPattern = "$legacyNamePattern|$legacyMethodPattern|$legacyAuditPattern"
$legacyContractHits = Select-String -LiteralPath $legacyContractFiles.FullName `
  -Pattern $legacyPattern -CaseSensitive
if ($legacyContractHits) { $legacyContractHits; throw '出现旧 P0-A/P0-B 类型、方法或审计字段' }
$legalLowerCamelFixture = 'appPermissionService workspaceAuthorizationService'
if (Select-String -InputObject $legalLowerCamelFixture `
    -Pattern $legacyPattern -CaseSensitive -Quiet) {
  throw '旧契约扫描器误伤合法 lowerCamel 变量'
}
$realLegacyTypeFixture = ('AppPermission' + 'Service')
if (-not (Select-String -InputObject $realLegacyTypeFixture `
    -Pattern $legacyPattern -CaseSensitive -Quiet)) {
  throw '旧契约扫描器未命中真实旧类型正向夹具'
}
$stableDtoFiles = @(
  'WorkspaceContextDTO.java',
  'WorkspaceSummaryDTO.java',
  'ResourceOwnershipDTO.java',
  'ResourceDataScopeDTO.java',
  'SwitchWorkspaceDTO.java'
)
foreach ($dtoFile in $stableDtoFiles) {
  $dtoPath = Join-Path $coreAuthorizationRoot "dto\$dtoFile"
  if (-not (Test-Path -LiteralPath $dtoPath)) { throw "缺少稳定 DTO：$dtoPath" }
}
$auditDtoPath = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\dto\AppSecurityAuditDTO.java'
$auditDtoText = Get-Content -Raw -LiteralPath $auditDtoPath
$auditHeader = [regex]::Match(
  $auditDtoText,
  '(?s)public\s+record\s+AppSecurityAuditDTO\s*\((?<parameters>.*?)\)\s*\{')
if (-not $auditHeader.Success) { throw 'AppSecurityAuditDTO 不是可核验的 record 契约' }
$auditFieldNames = @(
  [regex]::Matches(
    $auditHeader.Groups['parameters'].Value,
    '\b(?:String|Long|AppActorType)\s+([A-Za-z][A-Za-z0-9_]*)') |
    ForEach-Object { $_.Groups[1].Value }
)
$expectedAuditFields = @(
  'resourceType', 'resourceId', 'action', 'actorType',
  'actorId', 'beforeDigest', 'afterDigest', 'reason'
)
if (Compare-Object $expectedAuditFields $auditFieldNames -SyncWindow 0) {
  throw "AppSecurityAuditDTO 字段必须恰好八个且顺序固定：$($auditFieldNames -join ',')"
}
$userSwitchFiles = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\controller\WorkspaceController.java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java\org\dromara\aivideo\user\authorization\domain\bo\SwitchWorkspaceBo.java',
  'ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\types.ts',
  'ai-video-ui\ai-video-webapp\src\services\ai-video\workspace\api.ts'
)
$userRevisionHits = Select-String -LiteralPath $userSwitchFiles `
  -SimpleMatch 'expectedMembershipRevision'
if ($userRevisionHits) {
  $userRevisionHits
  throw '用户切换请求泄露服务端 expectedMembershipRevision；管理员成员修订字段不受此扫描影响'
}
$workspaceControllerPath = $userSwitchFiles[0]
if (Select-String -LiteralPath $workspaceControllerPath -Pattern '\bR\.ok\s*\(' -Quiet) {
  throw 'WorkspaceController 响应必须使用 R.data'
}
$proofStorePath = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceSwitchAdmissionProofStore.java'
$proofConsumerPath = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\security\AppWorkspaceSwitchAdmissionConsumer.java'
$workspaceAuthorizationImplPath = `
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\authorization\service\impl\WorkspaceAuthorizationServiceImpl.java'
$proofStoreText = Get-Content -Raw -LiteralPath $proofStorePath
if ($proofStoreText -match '\bpublic\s+(?:final\s+)?class\s+WorkspaceSwitchAdmissionProofStore\b' `
    -or $proofStoreText -match '\bpublic\s+[^;{]*\b(?:issue|discard)\s*\(') {
  throw '一次性证明 store 或签发／丢弃能力不得公开'
}
$proofConsumerText = Get-Content -Raw -LiteralPath $proofConsumerPath
if ([regex]::Matches($proofConsumerText, '\bconsumeOrThrow\s*\(').Count -ne 1 `
    -or $proofConsumerText -match '\b(?:issue|discard)\s*\(') {
  throw 'identity 证明 consumer 必须只暴露 consumeOrThrow'
}
$workspaceAuthorizationImplText = Get-Content -Raw -LiteralPath $workspaceAuthorizationImplPath
if ([regex]::Matches(
    $workspaceAuthorizationImplText,
    '\bproofStore\.issue\s*\(').Count -ne 1) {
  throw '生产代码必须恰好由 WorkspaceAuthorizationServiceImpl 签发一次证明'
}
$productionRoots = @(
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\main\java',
  'ai-video-api\ruoyi-modules\ai-video\ai-video-platform\src\main\java'
)
$allProductionJava = @(
  foreach ($productionRoot in $productionRoots) {
    Get-ChildItem -LiteralPath $productionRoot -Recurse -File -Filter '*.java'
  }
)
$unexpectedIssueCalls = Select-String -LiteralPath `
  ($allProductionJava.FullName | Where-Object { $_ -ne [System.IO.Path]::GetFullPath($workspaceAuthorizationImplPath) }) `
  -Pattern '\bproofStore\.issue\s*\(' -CaseSensitive
if ($unexpectedIssueCalls) { $unexpectedIssueCalls; throw '出现第二个生产证明签发调用点' }
$controllerFiles = @($allProductionJava | Where-Object { $_.Name -like '*Controller.java' })
$controllerProofBoundaryHits = Select-String -LiteralPath $controllerFiles.FullName `
  -Pattern '\bIAppSessionService\b|\bAppWorkspaceSessionSnapshotDTO\b' -CaseSensitive
if ($controllerProofBoundaryHits) {
  $controllerProofBoundaryHits
  throw 'Controller 不得直连会话服务或接收完整工作区快照 DTO'
}
$proofRelatedFiles = @(
  $proofStorePath,
  $proofConsumerPath,
  $workspaceAuthorizationImplPath,
  'ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImpl.java'
)
if (Select-String -LiteralPath $proofRelatedFiles -Pattern '\bThreadLocal\b' -CaseSensitive -Quiet) {
  throw '一次性证明不得保存于 ThreadLocal 或本机请求上下文'
}
$sessionProofIt = Get-Content -Raw -LiteralPath `
  ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\identity\AppSessionWorkspaceInvalidationIT.java
foreach ($requiredProofAssertion in @(
    'directOrganizationSnapshotWithoutAdmissionProofReturns46126',
    'legalOrganizationSwitchThroughAuthorizationService',
    'expiredTamperedOrCrossSessionProofReturns46126WithoutSideEffects',
    'concurrentProofConsumptionAllowsExactlyOneSuccess',
    'consumedProofIsNotRestoredWhenSessionWriteFails',
    'nodeAIssuesAndNodeBConsumesThenReplayFails')) {
  if ($sessionProofIt -notmatch [regex]::Escape($requiredProofAssertion)) {
    throw "会话防伪 IT 缺少：$requiredProofAssertion"
  }
}
$guardTests = @(
  (Get-Content -Raw -LiteralPath `
    ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\test\java\org\dromara\aivideo\authorization\WorkspaceAuthorizationServiceTest.java),
  (Get-Content -Raw -LiteralPath `
    ai-video-api\ruoyi-modules\ai-video\ai-video-user\src\test\java\org\dromara\aivideo\user\authorization\WorkspaceControllerIT.java)
) -join "`n"
foreach ($guardAssertion in @(
    'resolveListAndSwitchCheckRevisionBeforeReadingPrincipalOrFacts',
    'appActorResolverChecksRevisionBeforePrincipalAndStopsOnFailure',
    'InOrder',
    'verifyNoInteractions')) {
  if ($guardTests -notmatch [regex]::Escape($guardAssertion)) {
    throw "缺少 guard-first 顺序／零后续交互断言：$guardAssertion"
  }
}
$transactionAnnotationCount = [regex]::Matches(
  $organizationAdminImpl,
  '@Transactional\s*\(\s*rollbackFor\s*=\s*Exception\.class\s*\)').Count
if ($transactionAnnotationCount -lt 4) {
  throw '四个高权限组织写入口必须显式 rollbackFor=Exception'
}
if ($organizationAdminImpl -match '\binvalidateOrganizationSessions\s*\(') {
  throw '组织数据库事务内不得直接执行会话失效'
}
foreach ($eventPublisherAssertion in @(
    'ApplicationEventPublisher',
    'OrganizationSessionInvalidationRequested',
    'publishEvent')) {
  if ($organizationAdminImpl -notmatch [regex]::Escape($eventPublisherAssertion)) {
    throw "组织数据库事务缺少提交后失效事件：$eventPublisherAssertion"
  }
}
foreach ($listenerAssertion in @(
    '@TransactionalEventListener',
    'TransactionPhase.AFTER_COMMIT',
    'invalidateOrganizationSessions',
    'PROPAGATION_REQUIRES_NEW',
    'session_invalidation_failed',
    'session_invalidation_recovered')) {
  if ($organizationInvalidationListener -notmatch [regex]::Escape($listenerAssertion)) {
    throw "提交后组织会话失效监听器缺少：$listenerAssertion"
  }
}
if ($organizationInvalidationListener -notmatch '(?m)MAX_ATTEMPTS\s*=\s*3\s*;') {
  throw '提交后组织会话失效监听器必须把最大尝试次数固定为 3'
}
foreach ($transactionAssertion in @(
    'auditFailureRollsBackOrganizationMemberGrantAndAudit',
    'listenerFailureKeepsCommittedRevisionRecordsFailureAndGuardRejectsOldToken',
    'session_invalidation_failed',
    'session_invalidation_recovered')) {
  if ($organizationAdminTest -notmatch [regex]::Escape($transactionAssertion)) {
    throw "组织事务 IT 缺少回滚／提交后恢复断言：$transactionAssertion"
  }
}
$startupBoundaryTests = @(
  (Get-Content -Raw -LiteralPath `
    ai-video-api\ai-video-user-api\src\test\java\org\dromara\aivideo\assembly\UserAuthorizationBoundaryIT.java),
  (Get-Content -Raw -LiteralPath `
    ai-video-api\ruoyi-admin\src\test\java\org\dromara\aivideo\assembly\PlatformAuthorizationBoundaryIT.java)
) -join "`n"
foreach ($startupAssertion in @(
    'WebEnvironment.RANDOM_PORT',
    'sysTokenIsRejectedByRealUserUrlWithoutAnySideEffect',
    'appTokenIsRejectedByRealAdminUrlWithoutAnySideEffect',
    '登录状态异常，请重新登录')) {
  if ($startupBoundaryTests -notmatch [regex]::Escape($startupAssertion)) {
    throw "真实启动双向令牌隔离 IT 缺少：$startupAssertion"
  }
}
$baselineRecord = Get-Content -Raw -LiteralPath `
  ((& git rev-parse --git-path 'p0b-f0-baseline.json').Trim()) | ConvertFrom-Json
$readOnlyCoreFiles = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts',
  'ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts'
)
$coreAdapterChanges = @(git diff --name-only "$($baselineRecord.baselineHead)..HEAD" -- $readOnlyCoreFiles)
if ($LASTEXITCODE -ne 0 -or $coreAdapterChanges.Count -ne 0) {
  $coreAdapterChanges
  throw 'Task10 修改了只读公共响应／错误适配文件'
}
$planPath = `
  'docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md'
$planText = Get-Content -Raw -LiteralPath $planPath
if ([regex]::Matches($planText, '(?m)^## 任务 (?:[1-9]|1[0-3])：').Count -ne 13) {
  throw 'P0-B 计划必须恰好包含 13 个任务'
}
if ([regex]::Matches($planText, '(?m)^\*\*最小任务卡：\*\*$').Count -ne 13) {
  throw 'P0-B 计划必须为 13 个任务分别提供最小任务卡'
}
$powerShellBlocks = [regex]::Matches(
  $planText,
  '(?ms)^```powershell\s*\r?\n(?<script>.*?)^```\s*$')
if ($powerShellBlocks.Count -lt 1) { throw '计划未发现 PowerShell 可执行块' }
foreach ($block in $powerShellBlocks) {
  $tokens = $null
  $parseErrors = $null
  [void][System.Management.Automation.Language.Parser]::ParseInput(
    $block.Groups['script'].Value, [ref]$tokens, [ref]$parseErrors)
  if ($parseErrors.Count -ne 0) {
    $parseErrors | ForEach-Object { Write-Error $_.Message }
    throw '计划中的 PowerShell 代码块存在 AST 语法错误'
  }
  if ($block.Groups['script'].Value -match '-Dit\.test=' -and
      $block.Groups['script'].Value -notmatch '-Pdev,local-integration-test') {
    throw "存在未携带 '-Pdev,local-integration-test' 的 IT Maven 命令"
  }
}
git diff --check
if ($LASTEXITCODE -ne 0) { throw '工作树差异格式检查失败' }
```

预期：占位、敏感边界、重复身份类型、重复身份表 DDL 扫描均退出 `0` 且无命中；
P0-B DDL 集合恰好是三张新表，所有 actor 列和 CHECK 完整且不存在
`granted_by_user_id`；组织写方法全部显式接收 actor，且实现的第一条业务语句再次要求
`sys_user`，同号 `app_user` 零副作用测试存在；两端 resolver 不跨域，累计默认
`LoginHelper` 调用点严格等于两项白名单；`git diff --check` 无输出。

- [ ] **步骤 6（2–5 分钟）：记录三视角门禁并提交最终修正**

审查记录必须逐项写入拉取请求：

```text
前端：工作区加载/空/失败/46126/未提交确认/草稿不重绑；组织页加载/空/失败/403/分页。
后端：P0-A 单向依赖；P0-B 只建三张表；角色/权限只经 P0-A IAppPermissionService 与 identity Mapper；成员修订、对象授权、SQL 数据范围、审计、会话失效、双启动隔离。
联调：个人→组织→成员失效→回个人；失败请求不重放；同一草稿归属和计费主体不随切换变化。
```

如审查产生修正，必须回到产生问题的任务，重新执行该任务的红灯、绿灯和验证步骤，并使用该任务已经列出的精确 `git add` 清单提交；禁止在此处用项目根目录或大目录执行补漏提交。没有修正时不创建空提交。

开发 B 的主审结论关闭且开发 C 的串行专项签字完成后，开发 A 只冻结当前 worktree 的 P0-B candidate 并写入 worktree 专属 Git 元数据。该步骤不得读取或修改 P1／P2／P3 worktree，不得执行实际 rebase，也不得把候选误报为完整 F1：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0b-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-B worktree 门禁；先执行“未来业务实施 worktree 启动门禁”'
}
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-B worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$dirty = @(git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) {
  $dirty
  throw '冻结 P0-B candidate 前 worktree 必须干净，所有审查修正必须已按原任务提交'
}
$baselineRecordPath = (& git rev-parse --git-path 'p0b-f0-baseline.json').Trim()
$baselineRecordExists = Test-Path -LiteralPath $baselineRecordPath -PathType Leaf
if (-not $baselineRecordExists) { throw '缺少当前 worktree 的完整 F0 基线记录' }
$baselineRecord = Get-Content -Raw -LiteralPath $baselineRecordPath | ConvertFrom-Json
$f0Head = (& git rev-parse "$($baselineRecord.f0Head)^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $f0Head -notmatch '^[0-9a-f]{40}$') {
  throw '无法解析完整 F0 SHA'
}
$p0bCandidateHead = (& git rev-parse 'HEAD^{commit}').Trim()
if ($LASTEXITCODE -ne 0 -or $p0bCandidateHead -notmatch '^[0-9a-f]{40}$') {
  throw '无法解析完整 P0-B candidate SHA'
}
git merge-base --is-ancestor $f0Head $p0bCandidateHead
if ($LASTEXITCODE -ne 0 -or $p0bCandidateHead -eq $f0Head) {
  throw 'P0-B candidate 必须是完整 F0 的非空后继提交'
}
$p0bCandidateSummary = (& git show -s --format='%H %s' $p0bCandidateHead).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($p0bCandidateSummary)) {
  throw '无法生成 P0-B candidate 提交摘要'
}
$p0bChangedFiles = @(git diff --name-only "$f0Head..$p0bCandidateHead")
if ($LASTEXITCODE -ne 0 -or $p0bChangedFiles.Count -lt 1) {
  throw 'P0-B candidate 相对 F0 必须包含至少一个变更文件'
}
$handoffRecord = [pscustomobject]@{
  p0bCandidateHead = $p0bCandidateHead
  p0bCandidateSummary = $p0bCandidateSummary
  owner = '开发 A'
  reviewers = @('开发 B', '开发 C')
  f0Head = $f0Head
  target = 'P0-C'
  fullF1Ready = $false
  downstreamRebaseBlockedUntil = 'P0-C complete F1'
  changedFiles = $p0bChangedFiles
  capturedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$handoffRecordPath = (& git rev-parse --git-path 'p0b-candidate-handoff.json').Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($handoffRecordPath)) {
  throw '无法解析当前 worktree 的 P0-B candidate 交接记录路径'
}
$handoffRecord | ConvertTo-Json -Depth 6 | Set-Content `
  -LiteralPath $handoffRecordPath -Encoding UTF8
$savedHandoff = Get-Content -Raw -LiteralPath $handoffRecordPath | ConvertFrom-Json
if ($savedHandoff.p0bCandidateHead -ne $p0bCandidateHead `
    -or $savedHandoff.f0Head -ne $f0Head `
    -or $savedHandoff.fullF1Ready -ne $false `
    -or $savedHandoff.downstreamRebaseBlockedUntil -ne 'P0-C complete F1') {
  throw 'P0-B candidate 交接记录回读校验失败'
}
$savedHandoff | ConvertTo-Json -Depth 6
```

预期：当前 worktree 专属 Git 元数据 `p0b-candidate-handoff.json` 输出并回读验证完整 40 位 P0-B candidate SHA／摘要、owner A、reviewers B/C、F0、目标 P0-C、`fullF1Ready=false` 与 `downstreamRebaseBlockedUntil='P0-C complete F1'`；候选是 F0 的非空后继且包含变更。该记录不进入业务提交。本步骤不访问 P1／P2／P3 worktree，也不执行 rebase。P0-C 完成后，由主计划 Task 4 按批准规格与 F1 退出门禁冻结唯一完整 F1；P1／P2／P3 再分别按主计划 Tasks 5–7 在各自分支 rebase 同一 F1。本 P0-B Task 13 不得提前执行其中任何一步，也不得越权宣称完整 F1 已完成。

## P0-B 完成定义

- P0-A 独占九张身份表结构；P0-B 迁移只新建 `app_organization`、`app_org_member`、`av_resource_grant`，P0-A 角色三表的 `SHOW CREATE TABLE` 在 P0-B 两次迁移前后完全一致。
- P0-B 通过 P0-A 固定的 `IAppPermissionService` 和 `identity` Mapper 消费角色权限；`authorization` 包没有 `AppRole/AppRolePermission/AppUserRole` 或对应 Mapper 的第二套类型。
- 四个内置角色使用 `built_in=1`；`app_role_permission` 只按 `role_id + permission_id` 写入 58 个唯一映射，测试通过三表 JOIN 断言准确代码组合。
- 新用户默认个人工作区；有效组织成员能看到并切换组织工作区。
- 伪造键、无／过期／篡改／跨会话／重放的一次性证明、过期成员修订、停用组织、离开／过期成员稳定返回 `46126` 且会话零副作用；合法组织切换只能经授权服务签发并由共享 Redis 原子消费一次。
- 角色缺少工作区动作稳定返回 `46127`。
- `generate` 对象动作与 `aivideo:quota:use` 工作区权限分别判定。
- owner/admin、普通成员和个人所有者的数据范围由 SQL 实现，没有 Java 全量查询后过滤。
- 创建者授权与继承授权幂等；对象授权不能改变资源所有者或计费主体。
- `av_resource_grant` 使用 `granted_by_type + granted_by_id`，并与组织、成员共同保存
  typed create/update actor；数据库 CHECK 拒绝 `app_user/sys_user` 之外的主体。
- 组织/成员全部写方法显式接收 `AppActorContext`；用户/运营 resolver 分别只从
  `AppLoginHelper`/默认 `LoginHelper` 构造 actor，同号主体仍在行审计和安全审计中
  可区分。`OrganizationAdminServiceImpl` 四个管理写入口独立要求 `sys_user`；同号
  `app_user` 直调在任何查询或副作用前返回 `46127`，且组织、成员、授权、审计均无
  写入。创建者授权只接受编号匹配资源创建人的 `app_user`。变更递增修订；事务内审计异常
  完整回滚。提交后会话失效异常不回滚已提交数据库，而是留下结构化失败记录；旧 app token
  由每次请求最先执行并重读已提交修订的 guard 失败关闭，同号 sys session 不受影响，有界恢复
  最终清理 app 在线索引。
- 用户工作区切换不重绑已打开草稿；`46126` 回个人工作区但不重放原请求。
- 运营端组织/成员页具备加载、空、失败、权限不足、分页、修订冲突和二次确认状态。
- P0-B 只冻结 F0 的非空后继 candidate，并在当前 worktree 专属 `p0b-candidate-handoff.json` 记录 owner A、reviewers B/C、目标 P0-C、`fullF1Ready=false` 和 `downstreamRebaseBlockedUntil='P0-C complete F1'`；不访问下游 worktree、不执行 rebase。P0-C 完成后由主计划 Task 4 冻结唯一完整 F1，P1／P2／P3 随后分别按主计划 Tasks 5–7 在各自分支 rebase 同一 F1，P0-B Task 13 不得提前执行。
- 单元与集成测试分别被 Maven 显式启用；八个 IT 都有类级 `@Tag("dev")`，八份 Failsafe 报告都满足 `tests > 0, failures=0, errors=0, skipped=0`。

## 自检结果

- 规格覆盖：P0-B 的个人/组织工作区、组织/成员/角色、切换、对象授权、SQL 数据范围、修订失效、运营管理、前端异常状态和启动隔离均有对应任务。
- 范围隔离：未纳入登录、注册、令牌解析、P0-A 身份表结构、草稿、任务、额度和模型实现。
- 类型一致：`IWorkspaceAuthorizationService`、P0-A `IAppPermissionService`/`identity.mapper`、`WorkspaceType`、字符串动作、`46126`、`46127`、Java `Long` 与 HTTP/TypeScript `string` 在全文一致。
- 单一事实源：P0-B 无角色/角色权限/用户角色实体或 Mapper 清单，无三张 P0-A 角色表 DDL；种子只把代码连接到 P0-A 的 ID 列。
- 路径与提交：文件清单使用仓库相对路径；每个命令块从当前 worktree 动态解析根目录并通过门禁，每个 `git add` 只列本任务准确文件。
- 可执行性：每个实现任务都包含失败测试、失败命令、最小实现、通过命令和提交步骤；Maven 命令显式设置 `maven.test.skip`、`skipTests` 与 `skipITs`。
