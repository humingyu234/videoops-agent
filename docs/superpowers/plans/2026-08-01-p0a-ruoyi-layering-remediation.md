# P0-A 行为保持的 RuoYi 分层整改实施计划

> **执行方式：** 在当前 `codex/say-requirements-p0a` 分支按 TDD 顺序执行；先得到分层边界测试的预期失败，再逐批迁移。每批只做包、类型名和调用点调整，不顺手修改业务逻辑。

## 任务卡

- **单一目标：** 将已实现的 P0-A 身份与安全代码从 `application`、`application.impl`、`port`、`command`、`model` 整改为 RuoYi 的 `domain`、同级 `dto`、`mapper`、`service`、`service.impl`，并保持现有 HTTP、鉴权、事务、数据库和错误语义。
- **不做范围：** 不恢复注册，不进入 P0-B，不改数据库脚本，不改 API 路径、请求字段、响应字段、状态码、权限码、前端页面或供应商协议。
- **风险等级：** RED；命中身份认证、授权、会话、外部身份、共享契约和并发事务边界。
- **权威来源：** `RULES.md`、`docs/DOMAIN_MODEL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/API_CONTRACT.md`、`docs/ASYNC_TASKS.md`、`docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`、`docs/superpowers/specs/2026-08-01-p0a-ruoyi-layering-remediation-design.md`、`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0a-identity-security.md`。
- **影响模块：** `ai-video-core`、`ai-video-infra`、`ai-video-user`、`ai-video-platform` 及其 P0-A 测试；启动器只做装配回归。
- **协作上限：** 同时最多 2 个 Agent：主实施者 1 个、只读独立审查者 1 个；审查者不得派生 Agent。
- **独立审查：** 先做规格/契约审查，再做身份安全专项审查；修复后只复核差异。
- **固定输出：** 完成项、风险、实际验证证据、未验证/阻塞项。

## 已建立的基线

- Maven 必须使用本机 Temurin 21：`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`。系统当前默认 `JAVA_HOME` 是 JDK 17，只在命令子进程临时覆盖，禁止修改项目 Java 版本。
- reactor 上游无测试模块会因父 POM 的 Surefire `groups/excludedGroups` 报缺少测试引擎。无侵入执行方式是先以 `maven.test.skip=true` 安装依赖快照，再脱离 `-am` 单独测试目标模块；不得为本次整改修改父 POM。
- 2026-08-01 已按上述方式运行核心身份单元基线：44 tests，0 failures，0 errors，0 skipped。
- 本机已配置专用 `ai_video_test`、最小权限 MySQL 测试用户和 Redis DB 15；连接值和凭据默认读取已提交的用户端 `application-dev.yml`，`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖，仍不得绕过 `LocalIntegrationEnvironment` 的本机与隔离库校验。
- 根目录未跟踪的 `package-lock.json` 不属于本任务，禁止修改、暂存或提交。

## 最终分类与命名

### 1. Core 状态与事件

| 现有声明 | 目标声明 |
| --- | --- |
| `identity.model.AppSessionInvalidationReason` | `identity.domain.AppSessionInvalidationReason` |
| `identity.port.AppExternalIdentityChannel` | `identity.domain.AppExternalIdentityChannel` |
| `identity.application.event.AppClientSessionInvalidationEvent` | `identity.event.AppClientSessionInvalidationEvent` |
| `identity.application.event.AppSessionEndedEvent` | `identity.event.AppSessionEndedEvent` |
| `identity.application.event.AppSessionEstablishedEvent` | `identity.event.AppSessionEstablishedEvent` |
| `identity.application.event.AppSessionInvalidationEvent` | `identity.event.AppSessionInvalidationEvent` |

`AppPrincipalSnapshot` 与 `AppWorkspaceSessionSnapshot` 同时跨越 core/user Service 边界，按稳定数据契约迁为 `AppPrincipalSnapshotDTO`、`AppWorkspaceSessionSnapshotDTO`。它们仍是 Sa-Token 会话载荷，必须保留字段、`Serializable` 和 `serialVersionUID`，并通过会话序列化、namespace、revision 与双 Token 隔离测试。它们以及 `AppSessionServiceImpl$AppOnlineSession` 的包名变化会影响 Redis 中包含 FQCN 的旧载荷；负责人已批准发布时受控一次性使迁移前 App 会话失效，不实现旧 FQCN 兼容读取。维护窗口、精确键空间和反向验证必须执行 `docs/superpowers/specs/2026-08-01-p0a-ruoyi-layering-remediation-design.md` 的“会话缓存发布决策”。

### 2. Core DTO

以下类型全部置于 `ai-video-core/src/main/java/org/dromara/aivideo/identity/dto/`，每个公开类型单独成文件并以 `DTO` 结尾：

- `CreateAppAuthClientDTO`、`UpdateAppAuthClientDTO`、`RotateAppAuthClientSecretDTO`
- `RegisterAppUserDTO`、`AuthenticatePasswordDTO`、`ChangeAppPasswordDTO`、`ResetAppPasswordDTO`、`RecoverAppPasswordDTO`、`ChangeAppUserStatusDTO`、`UpdateAppUserProfileDTO`、`BindSocialIdentityDTO`
- `CreateAppRoleDTO`、`UpdateAppRoleDTO`
- `AppVerificationCodeRequestDTO`、`AppVerificationChallengeDTO`
- `AppRegisteredIdentityDTO`、`AppAuthenticatedIdentityDTO`、`AppIdentitySnapshotDTO`、`AppAuthClientSnapshotDTO`、`AppAuthClientSecretDTO`
- `AppSecurityAuditDTO`、`AppSessionQueryDTO`、`AppSessionSummaryDTO`、`AppPrincipalSnapshotDTO`、`AppWorkspaceSessionSnapshotDTO`
- `AppRoleDTO`
- `AppExternalIdentityRequestDTO`、`AppMiniProgramAuthorizationDTO`、`AppSocialIdentityAuthorizationDTO`、`AppExternalIdentityDTO`、`AppVerificationDeliveryDTO`

迁移时逐字段复制现有 record/class 的构造校验、`Serializable`、`serialVersionUID`、脱敏 `toString()`、分页基类和集合不可变约束；禁止借重命名改变异常消息或默认值。

### 3. Core Service 与安全契约

| 现有声明 | 目标声明 |
| --- | --- |
| `identity.application.AppAuthClientService` | `identity.service.IAppAuthClientService` |
| `identity.application.AppIdentityService` | `identity.service.IAppIdentityService` |
| `identity.application.AppPermissionService` | `identity.service.IAppPermissionService` |
| `identity.application.AppSecurityAuditService` | `identity.service.IAppSecurityAuditService` |
| `identity.application.AppSessionService` | `identity.service.IAppSessionService` |
| `identity.application.AppVerificationCodeService` | `identity.service.IAppVerificationCodeService` |
| `identity.port.AppExternalIdentityPort` | `identity.service.IAppExternalIdentityService` |
| `identity.port.AppVerificationDeliveryPort` | `identity.service.IAppVerificationDeliveryService` |

六个现有业务实现原样迁入 `identity.service.impl`，保持 `...ServiceImpl` 类名、`@Service`、构造参数、`@Transactional`、隔离级别、事件发布时机和异常转换。`IAppPermissionService#createRole` 不再跨模块返回 `AppRole` Entity，而是按相同字段返回 `AppRoleDTO`；platform 的最终 VO 与 HTTP JSON 保持不变。

直接安全协作接口保留在 `identity.security`，但统一使用 RuoYi Service 命名：

- `AppIdentityOperationAuthorizationPort` → `IAppIdentityOperationAuthorizationService`
- `AppLoginVerificationPort` → `IAppLoginVerificationService`
- `AppPasswordRecoveryVerificationPort` → `IAppPasswordRecoveryVerificationService`
- `AppSelfRegistrationVerificationPort` → `IAppSelfRegistrationVerificationService`

`AppSessionTokenRevoker`、`AppSessionRequestAccess` 等纯 Sa-Token 技术 SPI 不扩展成业务 Service，也不在本次顺手改名。

### 4. 端侧与基础设施 Service

| 模块 | 现有声明 | 目标声明 |
| --- | --- | --- |
| user | `auth.service.AppAuthApplicationService` | `auth.service.IAppAuthApplicationService` |
| user | `auth.service.AppAuthApplicationServiceImpl` | `auth.service.impl.AppAuthApplicationServiceImpl` |
| platform | `identity.service.AppIdentityAdminService` | `identity.service.IAppIdentityAdminService` |
| platform | `identity.service.impl.AppIdentityAdminServiceImpl` | 保留类名和目录，实现新接口 |
| infra | `infra.identity.AppMiniProgramIdentityGateway` | SDK 代码迁入 `infra.identity.provider.AppMiniProgramIdentityProvider`；新增 `infra.identity.service.impl.AppMiniProgramExternalIdentityServiceImpl` |
| infra | `infra.identity.AppSocialIdentityGateway` | SDK 代码迁入 `infra.identity.provider.AppSocialIdentityProvider`；新增 `infra.identity.service.impl.AppSocialExternalIdentityServiceImpl` |
| infra | `infra.verification.AppSmsVerificationDelivery` | SDK 代码迁入 `infra.verification.provider.AppSmsVerificationProvider`；新增 `infra.verification.service.impl.AppSmsVerificationDeliveryServiceImpl` |
| infra | `infra.verification.AppMailVerificationDelivery` | SDK 代码迁入 `infra.verification.provider.AppMailVerificationProvider`；新增 `infra.verification.service.impl.AppMailVerificationDeliveryServiceImpl` |

端侧 `domain.bo`、`domain.vo` 与 Controller 的 HTTP 契约保持不变。infra 的 `service.impl` 只实现 core `I...Service`、选择 provider 并映射 core DTO；JustAuth、sms4j、Mail 等 SDK 类型只存在于 `provider`，不能进入 core DTO。四个 ServiceImpl 显式保留原 Bean 名 `appMiniProgramIdentityGateway`、`appSocialIdentityGateway`、`appSmsVerificationDelivery`、`appMailVerificationDelivery`。

## Task 1：用分层边界测试建立 RED

**Files:**

- Create: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/IdentityPackageBoundaryTest.java`

**Step 1：写失败测试**

测试从当前模块的 `src/main/java/org/dromara/aivideo/identity` 读取生产源码，至少断言：

```java
@Test
void shouldUseRuoyiIdentityPackagesAndNames() throws IOException {
    assertThat(javaFiles("application")).isEmpty();
    assertThat(javaFiles("port")).isEmpty();
    assertThat(javaFiles("model")).isEmpty();
    assertThat(javaFileNames("dto")).allMatch(name -> name.endsWith("DTO.java"));
    assertThat(directJavaFileNames("service")).allMatch(name -> name.startsWith("I") && name.endsWith("Service.java"));
    assertThat(javaFileNames("service/impl")).allMatch(name -> name.endsWith("ServiceImpl.java"));
}
```

同一测试再检查 `../../../ruoyi-api/src/main/java` 下不存在 `org/dromara/aivideo/**/dto/*DTO.java`。

**Step 2：运行并确认预期失败**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dtest=IdentityPackageBoundaryTest test
```

Expected: FAIL，且失败仅来自当前 `application`、`port`、`model` 生产目录或缺失的新目录；若测试因路径错误失败，先修正测试，不能把假失败当 RED。

**Step 3：提交测试 RED**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/IdentityPackageBoundaryTest.java
git commit -m "test: 锁定 P0-A RuoYi 分层边界"
```

## Task 2：迁移 Core 状态、事件、DTO 和 Service

**Files:**

- Move/update: `ai-video-core/src/main/java/org/dromara/aivideo/identity/application/event/*.java`
- Move/update: `ai-video-core/src/main/java/org/dromara/aivideo/identity/model/*.java`
- Replace: `ai-video-core/src/main/java/org/dromara/aivideo/identity/application/command/*.java`
- Replace: `ai-video-core/src/main/java/org/dromara/aivideo/identity/application/model/*.java`
- Replace: `ai-video-core/src/main/java/org/dromara/aivideo/identity/port/*.java`
- Move/update: `ai-video-core/src/main/java/org/dromara/aivideo/identity/application/*.java`
- Move/update: `ai-video-core/src/main/java/org/dromara/aivideo/identity/application/impl/*.java`
- Update: `ai-video-core/src/main/java/org/dromara/aivideo/identity/security/*.java`
- Update/move: `ai-video-core/src/test/java/org/dromara/aivideo/identity/application/**`
- Update/move: `ai-video-core/src/test/java/org/dromara/aivideo/identity/port/**`
- Update: remaining `ai-video-core/src/test/java/org/dromara/aivideo/identity/**/*.java` imports

**Step 1：先迁低耦合类型**

迁移两个 enum 和四个 event；只改 package/import。编译 core，确认没有字段或构造变化。

**Step 2：将 grouped command/model 拆成 DTO**

逐个创建“最终分类与命名”列出的 DTO，复制原实现后更新 core 的 Service 签名、实现和测试。确认所有敏感 DTO 的 `toString()` 仍屏蔽密码、验证码、授权码、state、client secret 和联系地址；确认 `createRole` 只将 Entity 映射为 `AppRoleDTO`，不改变持久化和 VO。

**Step 3：迁 Service 接口与实现**

将六个接口改为 `I...Service`，六个实现迁入 `service.impl`；替换实现类的 `implements` 和所有 core 注入类型。安全协作接口按清单改名，不改变 `Optional`、bean 多实现选择或延期注册行为。

**Step 4：先编译，再运行 core 回归**

```powershell
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dtest=IdentityPackageBoundaryTest,AppIdentityDTOTest,AppExternalIdentityDTOTest,AppAuthClientServiceImplTest,AppIdentityServiceImplTest,AppVerificationCodeServiceImplTest,AppSessionServiceImplTest,AppSecurityAuditServiceImplTest,AppSessionModelTest,AppSessionRevisionGuardTest,AppStpLogicBehaviorTest test
```

Expected: boundary GREEN；相关业务/安全测试全部通过。随后运行 core 全部 unit tests（`-DskipITs=true test`），不得只跑新测试。

**Step 5：提交 core 迁移**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core
git commit -m "refactor: 整改 P0-A core RuoYi 分层"
```

## Task 3：迁移 User、Platform 与 Infra 调用边界

**Files:**

- Move/update: `ai-video-user/src/main/java/org/dromara/aivideo/user/auth/service/AppAuthApplicationService*.java`
- Update: `ai-video-user/src/main/java/org/dromara/aivideo/user/auth/controller/AppAuthController.java`
- Update: `ai-video-user/src/main/java/org/dromara/aivideo/user/security/*.java`
- Update: `ai-video-user/src/main/java/org/dromara/aivideo/identity/security/*.java`
- Move/update: `ai-video-user/src/test/java/org/dromara/aivideo/user/auth/service/AppAuthApplicationServiceImplTest.java`
- Update: remaining `ai-video-user/src/test/java/**/*.java` imports
- Update: `ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/AppIdentityAdminService.java`
- Update: `ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/impl/AppIdentityAdminServiceImpl.java`
- Update: `ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/controller/*.java`
- Update: `ai-video-platform/src/test/java/**/*.java` imports
- Move/update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/AppMiniProgramIdentityGateway.java`
- Move/update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/AppSocialIdentityGateway.java`
- Move/update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/AppSmsVerificationDelivery.java`
- Move/update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/AppMailVerificationDelivery.java`
- Update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/identity/AppExternalIdentityConfiguration.java`
- Update: `ai-video-infra/src/main/java/org/dromara/aivideo/infra/verification/AppVerificationDeliveryConfiguration.java`
- Update: `ai-video-infra/src/test/java/org/dromara/aivideo/infra/identity/AppExternalIdentityGatewayTest.java`
- Update: `ai-video-infra/src/test/java/org/dromara/aivideo/infra/verification/AppVerificationDeliveryAdaptersTest.java`

**Step 1：User**

改为 `IAppAuthApplicationService` + `service.impl.AppAuthApplicationServiceImpl`，通过保留实现类简单名维持默认 Spring Bean 名；Controller 仍只接收现有 BO、返回现有 VO。外部身份实现列表改为 `List<IAppExternalIdentityService>`，按原 channel 构图并保持重复 channel、缺失 channel、重放保护和异常语义。

**Step 2：Platform**

改为 `IAppIdentityAdminService`，实现继续同时承担已确认的安全授权 Service；所有 `@SaCheckPermission`、actor/data scope、审计、乐观锁与事务后失效事件保持不变。

**Step 3：Infra**

把四个现有类中的直接 SDK 代码迁入各自 `provider`；新增四个薄 `service.impl` 门面实现 core 的 `I...Service` 并显式保留原 Bean 名。保持条件装配、properties、可见性、bean 数量、channel、HTTP 超时、响应校验、重复 channel 检查和验证码明文不落日志等行为；测试随 provider 包迁移，禁止为迁包放宽生产可见性。

**Step 4：模块级回归**

先用 `maven.test.skip=true install` 安装改后的 core 与依赖，再分别脱离 `-am` 执行 infra、user、platform 全部 unit tests。任何 Spring bean 歧义、构造注入变化或 `NoSuchBeanDefinition` 都是阻塞问题。

**Step 5：提交跨模块迁移**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-infra ai-video-api/ruoyi-modules/ai-video/ai-video-user ai-video-api/ruoyi-modules/ai-video/ai-video-platform
git commit -m "refactor: 对齐 P0-A 端侧与基础设施 Service"
```

## Task 4：同步 P0-A 可执行验收文档并做独立审查

**Files:**

- Update: `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0a-identity-security.md`
- Update if implementation exposes a real exception: `docs/superpowers/specs/2026-08-01-p0a-ruoyi-layering-remediation-design.md`

**Step 1：更新旧符号引用**

把当前 P0-A 计划中的旧 `application`、`port`、`command`、`model` 路径替换为本计划最终路径；历史完成记录可保留，但必须明确标记为追溯信息，不得继续作为可复制实现模板。注册延期声明保持原样。

**Step 2：禁止无授权例外**

若实现必须保留任何禁止目录、改变缓存兼容策略或改变公共契约，立即停止代码迁移；先更新 `docs/DOMAIN_MODEL.md` 与规格并取得项目负责人确认。不得自行写“临时例外”。

**Step 3：独立审查**

只读审查者基于任务卡、最终 diff 和验证报告检查：

1. HTTP/API/数据库/schema 是否零变化；
2. 密码、验证码、授权码、client secret 是否仍脱敏且不进入日志/审计；
3. 双 Token namespace、session revision、actor/data scope、权限与跨账号反向场景是否保持；
4. `@Transactional`、隔离级别、事务后事件和异常转换是否逐方法保持；
5. Spring 多实现注入、条件装配和注册延期是否保持；
6. 无 core DTO 进入 `ruoyi-api`，无供应商原始对象泄漏 core。

阻塞项修复后只复核相关 diff 与测试，不重复全量审查。

## Task 5：执行 P0-A 既定验收

### 5.1 静态边界与规范

```powershell
rg -n "package org\.dromara\.aivideo\.identity\.(application|port|model)(\.|;)" ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java
rg -n "org\.dromara\.aivideo\.identity\.(application|port|model)" ai-video-api/ruoyi-modules/ai-video -g "*.java"
rg -n "package .*\.(domain\.dto|service\.dto)(\.|;)" ai-video-api/ruoyi-modules/ai-video -g "*.java"
rg -n "package org\.dromara\.aivideo.*\.dto" ai-video-api/ruoyi-api -g "*.java"
powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
git diff --check
```

Expected: 四个禁止扫描均无命中；规范校验和 diff check 成功。

### 5.2 后端 unit 与装配

使用 JDK 21，先安装依赖快照，再分别运行：

- `ai-video-core` 全部 unit tests，跳过 IT；
- `ai-video-infra` 全部 unit tests；
- `ai-video-user` 全部 unit tests；
- `ai-video-platform` 全部 unit tests；
- `StrictCredentialIngressTest`；
- `UserStarterAssemblyIT`、`PlatformStarterAssemblyIT`；
- `ExternalStarterJarAssemblyIT`、`ExternalStarterProcessCleanupIT`。

### 5.3 MySQL/Redis P0-A IT

用户端 `application-dev.yml` 配置齐全后，按原 P0-A 计划运行以下既定 16 个 IT；环境变量仅用于可选覆盖：

`AppIdentitySchemaIT`、`AppIdentityIsolationIT`、`AppPermissionTypeIT`、`AppSessionRevisionIT`、`AppMutationIsolationIT`、`AppIdentityAdminControllerIT`、`UserStarterAssemblyIT`、`PlatformStarterAssemblyIT`、`AppSessionNamespaceIT`、`AppSaTokenDaoNamespaceIT`、`AppSessionLogoutIT`、`AppSessionWorkspaceInvalidationIT`、`AppActorAndScopeIT`、`ExternalStarterJarAssemblyIT`、`ExternalStarterProcessCleanupIT`、`DualTokenIsolationIT`。

必须覆盖无凭据、伪造/错误凭据、过期/旧 revision、错误角色、跨账号、直接接口访问、client policy 与双 Token namespace 反向场景。缺少环境时只报告“未运行”，不得关闭任务。

### 5.4 前端既定验收

本次只删除创作端安全页一个由外层命名 `section` 已覆盖的冗余 `div aria-label`，用于修复 P0-A 文件的 Biome 可访问性错误，不改变页面行为、接口或文案。运行该文件的定向 lint、页面测试、TypeScript 检查和生产构建；全量 lint 的无关存量问题继续单独记录，不得误报为本次已修复。

### 5.5 最终提交

确认 `git status --short` 只包含本任务文件与用户既有的未跟踪 `package-lock.json`；禁止暂存后者。所有可执行门禁通过、环境型未验证项明确记录后再提交验收文档：

```powershell
git add docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0a-identity-security.md docs/superpowers/plans/2026-08-01-p0a-ruoyi-layering-remediation.md
git commit -m "docs: 同步 P0-A 分层整改验收"
```

## 2026-08-01 执行记录

### 已完成的行为保持整改

- `bd23eb9`：增加分层边界测试并确认旧目录触发 RED。
- `17b6fc9`：将 core 的状态、事件、DTO 与业务服务迁入 `domain`、`event`、同级 `dto`、`service`、`service.impl`；HTTP BO/VO 未移动，注册仍延期。
- `0ee7601`：对齐 user、platform 与 infra 的 `I...Service`/`...ServiceImpl`；供应商 SDK 逻辑收口到 provider，四个既有 Spring Bean 名保持不变。
- `79cc3fb`：根据独立审查补齐 3 个敏感 DTO 的脱敏 `toString()` 与回归测试，恢复 3 处迁移前诊断文本，并锁定 `adapter`、`command` 禁止目录。
- HTTP 路径、BO/VO、数据库脚本、权限码和前端代码均无修改；core 的 `@Transactional` 数量在整改前后均为 19，Service 实现的 `@Service` 数量均为 6。

### 已通过的可执行门禁

- 后端：common-redis 3、common-satoken 3、core 101、infra 10、user 88、platform 2 个单元测试通过；P0-A 最终 `clean verify` 精确生成既定 16 个 Failsafe 类报告，共 70 tests、0 failures、0 errors、0 skipped。报告包含 `UserStarterAssemblyIT` 2、`PlatformStarterAssemblyIT` 2、`AppIdentityAdminControllerIT` 3、外部 starter JAR 2、进程回收／中断恢复／端口解析／脱敏 5、真实双 starter HTTP 隔离 4，以及 core MySQL/Redis 52 个场景；结束后外部进程和 Redis DB 15 Key 均为 0。
- 创作端：P0-A 相关 4 个测试文件共 90 个测试通过；安全页本轮定向测试 20 个、目标文件 Biome lint、TypeScript 检查与生产构建通过。
- 运营端：5 个测试文件共 9 个测试通过，lint 与生产构建通过。

### 剩余发布门禁与任务外存量项

- 创作端全量 lint 仍返回 36 个 error、50 个 warning、12 个 info，主要命中未改动的数字人工作台等存量文件；P0-A 的 `src/pages/user/security/index.tsx` 已定向 lint 通过。本轮不越权扩修无关页面，因此全量 lint 门禁仍未通过，但不再包含 P0-A 文件错误。
- `AppPrincipalSnapshotDTO`、`AppWorkspaceSessionSnapshotDTO` 与在线索引内部类型 `AppSessionServiceImpl$AppOnlineSession` 均已改包；负责人已批准受控一次性使迁移前 App 会话失效，不实现旧 FQCN 兼容读取。该操作只允许在发布维护窗口处理 `Authorization:app:*` 与 `aivideo:app:online:*` 对应物理键，必须验证 sys 会话不受影响；执行前仍属于发布门禁，而非代码阻塞。
- 用户注册保持延期，没有恢复、删减或改变其契约。
