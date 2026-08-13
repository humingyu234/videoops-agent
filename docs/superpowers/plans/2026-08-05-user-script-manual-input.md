# 用户端文案库手动录入实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 已登录创作端用户可在“文案”菜单通过标题和正文新建自己的文案，并通过真实用户端接口完成分页、详情、复制、编辑新版本和删除。

**架构：** 后端沿用 RuoYi-Vue-Plus 的贫血 Entity + Service 编排，在 `ai-video-core` 放文案 Entity、DTO、Mapper 和 Service，在 `ai-video-user` 放 BO、VO 和 Controller；数据只按服务端会话中的个人 `tenantId + appUserId` 隔离。前端从通用静态 `LibraryView` 抽出 `ScriptLibraryView`，通过集中 `userScriptApi` 调用 `/api/studio/scripts`，不上传文件、不创建异步任务、不扣额度，也不把手工文案暴露给下游生成链路。

**技术栈：** Java 21、Spring Boot 3、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、Sa-Token app logic、JUnit 5、Mockito、React 19、TypeScript 7、Umi Max Request、Ant Design 6、Vitest、Testing Library、Biome。

---

## 0. 权威输入、已冻结边界与实施前检查

- 规格：`docs/superpowers/specs/2026-08-05-user-script-manual-input-design.md`
- 后端模式：`ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`、generator 模板、现有 `portrait` 聚合。
- 前端模式：`src/services/ai-video/portrait/*`、`PortraitLibraryView.tsx`、统一 `RuoYiAdapter`。
- 本计划生成时 `.agents/skills/antd/SKILL.md` 不存在；实施 Task 7 前先再次检查。仍不存在时，运行项目已安装的 `@ant-design/cli` 查询 `Modal`、`Form`、`Input`、`Pagination`、`Empty` 和 `Alert`，并以当前仓库 Ant Design 6 代码为兼容基线。
- 当前工作树已经存在用户自己的肖像、资产、声音、公共契约和 `digital-human-studio` 未提交修改。执行者不得还原、覆盖或顺手格式化这些改动；应使用 `using-git-worktrees` 从包含规格与本计划的 HEAD 创建隔离工作树。若隔离工作树仍需消费这些未提交契约，先让这些变更形成可引用提交，再开始 Task 1。
- 本期只有个人文案：`owner_type='personal'`、`owner_id=principal.appUserId()`；`tenant_id` 仅来自 `principal.workspace().tenantId()`。请求、VO 和页面都不出现租户、所有者或工作区字段。
- 本期手工文案始终 `current_confirmed_version_id IS NULL`，不写 `av_script_confirmation`，不创建 AI 任务，不锁定或扣减额度。删除时若主体已经出现非空确认版本，按存在下游保护处理并返回 `46118`；这既阻止未来 P3 数据被误删，也不新增虚假的引用表。
- 正文时长冻结规则：有效字符采用 P3 规则（CJK code point 每个计 1，连续 Latin/数字 token 计 1，空白和标点计 0），`effective_chars_per_minute=240`，`estimated_duration_seconds=ceil(effectiveCharacterCount*60/240)`，规则版本写入 `{"duration":"manual-v1","character":"p3-v1"}`。

## 1. 文件结构

### 公共契约

- 修改 `docs/API_CONTRACT.md`：六个用户端路由、精确 BO/VO、分页、错误码 `46136`、app 权限。
- 修改 `docs/DOMAIN_MODEL.md`：`manual_input/manual_edit`、当前版本与确认版本分离、个人归属、不可变版本。
- 修改 `docs/ARCHITECTURE.md`：双启动模块边界和前端数据流。
- 修改 `docs/ASYNC_TASKS.md`：明确本流程同步、免费、不进入任务中心。
- 修改 `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md`：对齐首批两张表、`current_version_id`、可空 `draft_id` 和手工来源约束，禁止未来重复建表。

### 数据库与核心后端

- 创建 `docs/sql/ai-video/mysql/20260805_01_user_script_manual_input.sql`：`av_user_script`、`av_script_version`、权限种子和重复执行保护。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/domain/AvUserScript.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/domain/AvScriptVersion.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptCreateDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptEditDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptQueryDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptListDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptDetailDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/UserScriptSaveResultDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/ScriptVersionDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/dto/ScriptVersionSummaryDTO.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/mapper/AvUserScriptMapper.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/mapper/AvScriptVersionMapper.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/script/AvUserScriptMapper.xml`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/script/AvScriptVersionMapper.xml`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/service/IUserScriptService.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/service/impl/UserScriptServiceImpl.java`。

### 用户端 HTTP

- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/bo/CreateUserScriptBo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/bo/EditUserScriptBo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/vo/UserScriptListVo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/vo/UserScriptDetailVo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/vo/UserScriptSaveResultVo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/domain/vo/ScriptVersionVo.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/controller/UserScriptController.java`。

### 前端

- 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/script/types.ts`：稳定请求、列表、详情和版本类型。
- 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/script/api.ts`：所有 URL、查询串和运行时鉴权适配。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptEditorModal.tsx`：新建/编辑表单与离开确认。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptLibraryView.tsx`：真实列表、详情、复制、编辑、删除和全部页面状态。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx`：`route==='scripts'` 时直接渲染 `ScriptLibraryView`，其余资源逻辑不动。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`：删除仅服务于文案页的静态 `SCRIPTS` 常量。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`：只增加文案真实页面所需样式。

### 测试

- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/UserScriptSchemaIT.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/service/impl/UserScriptServiceImplTest.java`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/script/controller/UserScriptControllerTest.java`。
- 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/script/api.test.ts`。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx`。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptLibraryView.test.tsx`。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.test.tsx`。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`。

## 2. 冻结的 Java 与 TypeScript 签名

```java
public record UserScriptCreateDTO(String displayTitle, String scriptText, String idempotencyKey) {}

public record UserScriptEditDTO(
    String scriptId,
    String parentVersionId,
    String expectedScriptRevision,
    String displayTitle,
    String scriptText,
    String idempotencyKey
) {}

public record UserScriptQueryDTO(String keyword, String orderByColumn, String isAsc) {}

public record UserScriptListDTO(
    String scriptId, String displayTitle, String currentVersionId,
    Integer versionNo, Long versionCount, String sourceType,
    Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
    String preview, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt
) {}

public record ScriptVersionSummaryDTO(
    String versionId, String parentVersionId, Integer versionNo, String sourceType,
    Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
    String preview, java.time.LocalDateTime createdAt
) {}

public record ScriptVersionDTO(
    String scriptId, String versionId, String parentVersionId, Integer versionNo,
    String sourceType, String scriptText, Integer effectiveCharacterCount,
    Integer estimatedDurationSeconds, java.time.LocalDateTime createdAt
) {}

public record UserScriptDetailDTO(
    String scriptId, String displayTitle, String scriptRevision, String currentVersionId,
    java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt,
    ScriptVersionDTO currentVersion, java.util.List<ScriptVersionSummaryDTO> versions
) {}

public record UserScriptSaveResultDTO(
    String scriptId, String currentVersionId, String scriptRevision, Integer versionNo,
    String displayTitle, Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
    java.time.LocalDateTime createdAt, boolean reused
) {}
```

```java
public interface IUserScriptService {
    org.dromara.common.core.domain.PageResult<UserScriptListDTO> queryPage(
        UserScriptQueryDTO query,
        org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO principal,
        org.dromara.common.mybatis.core.page.PageQuery pageQuery);
    UserScriptDetailDTO queryById(String scriptId, AppPrincipalSnapshotDTO principal);
    ScriptVersionDTO queryVersion(String scriptId, String versionId, AppPrincipalSnapshotDTO principal);
    UserScriptSaveResultDTO create(UserScriptCreateDTO command, AppPrincipalSnapshotDTO principal);
    UserScriptSaveResultDTO createVersion(UserScriptEditDTO command, AppPrincipalSnapshotDTO principal);
    void delete(String scriptId, AppPrincipalSnapshotDTO principal);
}
```

```ts
export type ScriptSourceType = 'manual_input' | 'manual_edit';

export interface UserScriptListItem {
  scriptId: string;
  displayTitle: string;
  currentVersionId: string;
  versionNo: number;
  versionCount: number;
  sourceType: ScriptSourceType;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  preview: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserScriptInput {
  displayTitle: string;
  scriptText: string;
  idempotencyKey: string;
}

export interface UserScriptEditInput extends UserScriptInput {
  parentVersionId: string;
  expectedScriptRevision: string;
}

export interface UserScriptListQuery {
  keyword?: string;
  orderByColumn?: 'updatedAt' | 'displayTitle';
  isAsc?: 'asc' | 'desc';
  pageNum?: number;
  pageSize?: number;
}

export interface UserScriptPage {
  rows: UserScriptListItem[];
  total: number;
}

export interface ScriptVersionSummary {
  versionId: string;
  parentVersionId?: string;
  versionNo: number;
  sourceType: ScriptSourceType;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  preview: string;
  createdAt: string;
}

export interface ScriptVersion extends Omit<ScriptVersionSummary, 'preview'> {
  scriptId: string;
  scriptText: string;
}

export interface UserScriptDetail {
  scriptId: string;
  displayTitle: string;
  scriptRevision: string;
  currentVersionId: string;
  createdAt: string;
  updatedAt: string;
  currentVersion: ScriptVersion;
  versions: ScriptVersionSummary[];
}

export interface UserScriptSaveResult {
  scriptId: string;
  currentVersionId: string;
  scriptRevision: string;
  versionNo: number;
  displayTitle: string;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  createdAt: string;
  reused: boolean;
}
```

## 3. 任务卡与 TDD 步骤

### 任务 1：先冻结公共契约并消除 P3 表冲突

**风险与任务卡：** 红色；writer 为当前实现者，reviewer 必须是未写本任务的人；只修改五份文档，不修改运行时代码。该任务必须串行，Task 2 和 Task 7 只能在本任务字段表确认后开始。

**文件：** 修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ARCHITECTURE.md`、`docs/ASYNC_TASKS.md`、`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md`。

- [ ] **步骤 1：确认隔离工作树没有继承用户未提交的文档改动**

运行：

```powershell
git status --short -- docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ARCHITECTURE.md docs/ASYNC_TASKS.md
```

预期：无输出；若有输出，停止本任务并先确定这些文件的唯一 writer，不允许选择 ours/theirs 覆盖。

- [ ] **步骤 2：写入精确契约**

在 API 契约中固定以下路由矩阵：

```text
POST   /api/studio/scripts                                      aivideo:script:edit
GET    /api/studio/scripts                                      aivideo:script:query
GET    /api/studio/scripts/{scriptId}                           aivideo:script:query
GET    /api/studio/scripts/{scriptId}/versions/{versionId}      aivideo:script:query
POST   /api/studio/scripts/{scriptId}/versions                  aivideo:script:edit
DELETE /api/studio/scripts/{scriptId}                           aivideo:script:remove
```

同时写明请求无 `tenantId/ownerType/ownerId/workspaceId/appUserId`，ID/修订号均为十进制字符串，错误码为 `46116/46118/46136`，手工流程同步且免费。

- [ ] **步骤 3：修订 P3 计划的冲突章节**

把 P3 的 `av_user_script`/`av_script_version` 定义改为“复用 `20260805_01` 已建表并通过后续迁移扩列”，增加 `manual_input`、`current_version_id`、可空 `draft_id`，并保留生成文案三标题约束的条件分支。不得再由 `20260728_07_p3_script.sql` 重建这两张表。

- [ ] **步骤 4：运行文档门禁并提交**

运行：

```powershell
pwsh -NoProfile -File scripts/validate-development-standards.ps1
git diff --check -- docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ARCHITECTURE.md docs/ASYNC_TASKS.md docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md
```

预期：标准校验退出码 0；`git diff --check` 无输出。由契约 reviewer 对字段、权限、错误码、P3 兼容性给出一次 PASS 后提交：

```powershell
git add -- docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ARCHITECTURE.md docs/ASYNC_TASKS.md docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md
git commit -m "docs: 冻结用户端手工文案契约"
```

### 任务 2：以失败的 MySQL 集成测试驱动首批文案表和权限

**风险与任务卡：** 红色；触发数据迁移、权限种子和个人隔离。单 writer 修改迁移，独立 reviewer 核对重放安全、索引、约束和权限修订；不能与其他迁移 writer 并发抢占 `20260805_01`。

**文件：** 创建 `20260805_01_user_script_manual_input.sql`、`UserScriptSchemaIT.java`。

- [ ] **步骤 1：先写失败的 schema 集成测试**

测试通过 `LocalIntegrationEnvironment.requireFromEnvironment()` 连接本机专用 `ai_video_test`，每次只调用 `resetDedicatedMySqlSchema()`，依次执行 `ry_vue.sql`、`20260728_01_p0a_identity_security.sql` 和本迁移；断言：

```java
assertThat(columns(connection, "av_user_script")).contains(
    "id", "tenant_id", "owner_type", "owner_id", "draft_id", "display_title",
    "current_version_id", "current_confirmed_version_id", "create_idempotency_key",
    "create_request_hash", "script_revision", "deleted");
assertThat(indexColumns(connection, "av_user_script", "uk_av_user_script_create_intent"))
    .containsExactly("tenant_id", "owner_type", "owner_id", "create_idempotency_key", "deleted");
assertThat(indexColumns(connection, "av_script_version", "uk_av_script_version_no"))
    .containsExactly("tenant_id", "script_id", "version_no");
assertThat(permissionCodes(connection)).contains(
    "aivideo:script:query", "aivideo:script:edit", "aivideo:script:remove");
```

- [ ] **步骤 2：运行并确认 RED**

运行（默认读取用户端 `application-dev.yml`，仅允许本机 MySQL 8 的 `ai_video_test` 和 Redis 7 的隔离 DB；环境变量仅可选覆盖，禁止容器和 `FLUSHALL`）：

```powershell
mvn '-Pdev,local-integration-test' -pl ruoyi-modules/ai-video/ai-video-core -am -Dit.test=org.dromara.aivideo.script.UserScriptSchemaIT -DskipITs=false verify
```

预期：FAIL，原因是迁移文件或 `av_user_script` 尚不存在；不能因为无测试而通过。

- [ ] **步骤 3：创建幂等迁移**

迁移使用 `CREATE TABLE IF NOT EXISTS` 加 `information_schema` 定义核对，核心 DDL 固定为：

```sql
CREATE TABLE IF NOT EXISTS av_user_script (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NULL,
  display_title VARCHAR(100) NOT NULL,
  current_version_id BIGINT NULL,
  current_confirmed_version_id BIGINT NULL,
  create_idempotency_key VARCHAR(64) NOT NULL,
  create_request_hash CHAR(64) NOT NULL,
  script_revision BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_av_user_script_create_intent
    (tenant_id, owner_type, owner_id, create_idempotency_key, deleted),
  KEY idx_av_user_script_owner_updated
    (tenant_id, owner_type, owner_id, deleted, updated_at, id),
  CONSTRAINT ck_av_user_script_owner_type CHECK (owner_type = 'personal'),
  CONSTRAINT ck_av_user_script_revision CHECK (script_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_version (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  script_id BIGINT NOT NULL,
  parent_version_id BIGINT NULL,
  version_no INT NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  script_text LONGTEXT NOT NULL,
  effective_character_count INT NOT NULL,
  estimated_duration_seconds INT NOT NULL,
  effective_chars_per_minute INT NOT NULL,
  rule_config_versions_json VARCHAR(500) NOT NULL,
  manual_idempotency_key VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_av_script_version_no (tenant_id, script_id, version_no),
  UNIQUE KEY uk_av_script_version_manual_intent
    (tenant_id, script_id, manual_idempotency_key),
  KEY idx_av_script_version_history (tenant_id, script_id, version_no, id),
  CONSTRAINT fk_av_script_version_script FOREIGN KEY (script_id) REFERENCES av_user_script(id),
  CONSTRAINT fk_av_script_version_parent FOREIGN KEY (parent_version_id) REFERENCES av_script_version(id),
  CONSTRAINT ck_av_script_version_source CHECK (source_type IN ('manual_input','manual_edit')),
  CONSTRAINT ck_av_script_version_no CHECK (version_no > 0),
  CONSTRAINT ck_av_script_version_count CHECK (effective_character_count >= 0),
  CONSTRAINT ck_av_script_version_duration CHECK (estimated_duration_seconds >= 0),
  CONSTRAINT ck_av_script_version_rate CHECK (effective_chars_per_minute = 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

权限种子按现有 app permission/role revision 模式，把三个权限分配给 `personal_creator`，重复执行不得重复增加角色或用户权限修订号。

- [ ] **步骤 4：运行 GREEN、重放和提交**

同一测试中连续执行迁移两次并断言定义不漂移；重新运行上一步 Maven 命令，预期 `UserScriptSchemaIT` tests>0 且 PASS。提交：

```powershell
git add -- docs/sql/ai-video/mysql/20260805_01_user_script_manual_input.sql ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/UserScriptSchemaIT.java
git commit -m "feat: 建立个人文案与不可变版本表"
```

### 任务 3：以 Service 单测驱动 Entity、DTO、Mapper 和事务编排

**风险与任务卡：** 红色；触发 IDOR、幂等、不可变版本和并发控制。一个 writer 负责整个 `script` 聚合；独立 reviewer 专查 SQL 是否在查询/更新条件中同时包含 tenant、personal owner、ownerId、deleted。可与 Task 7 的前端 service 并发，但不能与 Task 4 并发修改 Java 签名。

**文件：** 创建本计划“数据库与核心后端”下除迁移外的全部文件，以及 `UserScriptServiceImplTest.java`。

- [ ] **步骤 1：写失败的 Service 测试矩阵**

使用 Mockito mock 两个 Mapper，固定个人 principal：

```java
private static AppPrincipalSnapshotDTO principal(long userId, long tenantId, Set<String> permissions) {
    var workspace = new AppWorkspaceSessionSnapshotDTO(
        "personal-" + userId, "personal", tenantId, "app_user", userId,
        "app_user", userId, "personal_creator", permissions, 1L, null);
    return new AppPrincipalSnapshotDTO(userId, "creator", "web", 1L, 1L, 1L, 1L, workspace);
}
```

必须覆盖：创建原子写主体/v1/当前版本；同键同摘要复用；同键不同摘要 `46116`；标题/正文 Unicode code point 上限；有效字符与时长；列表排序白名单；他人 ID 不可见；编辑新建 `manual_edit` 且旧版不变；父版本或 revision 冲突 `46136` 且事务无孤儿版本；删除逻辑删除主体且版本保留；非空确认版本删除返回 `46118`；缺权限 403。

- [ ] **步骤 2：运行并确认 RED**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-core -am -Dtest=org.dromara.aivideo.script.service.impl.UserScriptServiceImplTest test
```

预期：FAIL，缺少 `IUserScriptService`、DTO 和实现类。

- [ ] **步骤 3：创建贫血 Entity、DTO 和 Mapper**

Entity 只声明字段，不放业务方法；沿用已批准 P3 创作表“不继承 BaseEntity”的显式 actor/time 例外。Mapper 必须继承 `BaseMapperPlus<AvUserScript, AvUserScript>` / `BaseMapperPlus<AvScriptVersion, AvScriptVersion>`。分页 SQL 固定以：

```sql
WHERE s.tenant_id = #{tenantId}
  AND s.owner_type = 'personal'
  AND s.owner_id = #{ownerId}
  AND s.deleted = '0'
  AND (#{keyword} IS NULL OR s.display_title LIKE CONCAT('%', #{keyword}, '%'))
ORDER BY
  CASE WHEN #{orderByColumn} = 'displayTitle' AND #{isAsc} = 'asc' THEN s.display_title END ASC,
  CASE WHEN #{orderByColumn} = 'displayTitle' AND #{isAsc} = 'desc' THEN s.display_title END DESC,
  CASE WHEN #{orderByColumn} = 'updatedAt' AND #{isAsc} = 'asc' THEN s.updated_at END ASC,
  CASE WHEN #{orderByColumn} = 'updatedAt' AND #{isAsc} = 'desc' THEN s.updated_at END DESC,
  s.id DESC
```

实现排序前先将 `orderByColumn` 归一为 `updatedAt|displayTitle`、`isAsc` 归一为 `asc|desc`，非法值抛参数错误，禁止把原始字符串拼进 `${}`。

- [ ] **步骤 4：实现最小 Service 事务**

`create` 在同一 `@Transactional` 中规范化输入、计算 SHA-256、处理唯一键竞争、插入主体、插入 v1、按主体 id 条件设置 `current_version_id`。`createVersion` 先锁定归属主体，再插入新版本并按下列条件更新；任一步失败整体回滚：

```java
int affected = userScriptMapper.update(null, new LambdaUpdateWrapper<AvUserScript>()
    .eq(AvUserScript::getId, scriptId)
    .eq(AvUserScript::getTenantId, tenantId)
    .eq(AvUserScript::getOwnerType, "personal")
    .eq(AvUserScript::getOwnerId, principal.appUserId())
    .eq(AvUserScript::getCurrentVersionId, parentVersionId)
    .eq(AvUserScript::getScriptRevision, expectedRevision)
    .eq(AvUserScript::getDeleted, "0")
    .set(AvUserScript::getDisplayTitle, normalizedTitle)
    .set(AvUserScript::getCurrentVersionId, versionId)
    .setSql("script_revision = script_revision + 1"));
if (affected != 1) {
    throw new ServiceException("文案已被修改，请刷新后重试", 46136);
}
```

Java 规范化固定为 `\r\n/\r -> \n`、首尾 `strip()`；长度用 `codePointCount(0, length)`。创建请求摘要固定为 `SHA-256(normalizedTitle + "\0" + normalizedText)`。预览取正文前 120 个 Unicode code point，完整正文不加截断标记，发生截断时加 `…`。

- [ ] **步骤 5：运行 GREEN、包边界扫描和提交**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-core -am -Dtest=org.dromara.aivideo.script.service.impl.UserScriptServiceImplTest test
rg -n "package .*\.(application|port|adapter|command|model)(\.|;)" ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script
```

预期：测试 PASS；包边界扫描无输出。提交全部 `script` core 文件：

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/script ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/service/impl/UserScriptServiceImplTest.java
git commit -m "feat: 实现个人文案版本服务"
```

### 任务 4：以 Controller 测试驱动 app 鉴权、BO/VO 和六个端点

**风险与任务卡：** 红色；触发公网路由、令牌类型、权限和反序列化。独立安全 reviewer 必须复跑错误 token、缺权限、越权 ID 和伪造归属字段用例。与 Task 3 串行；可与 Task 7 的组件工作并发。

**文件：** 创建本计划“用户端 HTTP”全部文件和 `UserScriptControllerTest.java`。

- [ ] **步骤 1：写失败的 Controller 测试**

用 MockMvc/独立 Controller 单测覆盖六个路由、`R<T>` 和 `PageResult` 形状，并反射断言每个方法的权限注解均指定 app 类型：

```java
SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
assertThat(permission.type()).isEqualTo("app");
assertThat(permission.value()).containsExactly("aivideo:script:edit");
```

创建请求额外传入 `ownerId`、`tenantId` 或 `workspaceId` 时预期 400，且 `userScriptService.create` 从未调用。

- [ ] **步骤 2：运行并确认 RED**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-user -am -Dtest=org.dromara.aivideo.user.script.controller.UserScriptControllerTest test
```

预期：FAIL，缺少 Controller、BO 和 VO。

- [ ] **步骤 3：创建 BO 并显式拒绝未知字段**

BO 使用 class + Jakarta Validation；不包含任何归属字段，并用 `@JsonAnySetter` fail closed：

```java
@JsonAnySetter
public void rejectUnknown(String field, Object value) {
    throw new IllegalArgumentException("不允许的请求字段: " + field);
}
```

`CreateUserScriptBo` 字段为 `displayTitle/scriptText/idempotencyKey`；`EditUserScriptBo` 再增加 `parentVersionId/expectedScriptRevision`。字符串长度的权威校验仍在 Service 以 Unicode code point 执行，BO 负责非空与幂等键最大 64。

- [ ] **步骤 4：创建 Controller 和 VO**

每个方法显式使用 app 权限并从 `AppLoginHelper` 取唯一 principal：

```java
@PostMapping("/api/studio/scripts")
@SaCheckPermission(value = "aivideo:script:edit", type = "app")
public R<UserScriptSaveResultVo> create(@Valid @RequestBody CreateUserScriptBo body) {
    var result = userScriptService.create(
        new UserScriptCreateDTO(body.getDisplayTitle(), body.getScriptText(), body.getIdempotencyKey()),
        loginHelper.getPrincipal());
    return R.ok(UserScriptSaveResultVo.from(result));
}
```

GET 列表只接收 `keyword/orderByColumn/isAsc/PageQuery`；路径方法把 `scriptId/versionId` 原样作为字符串传给 Service。创建和编辑不使用会抢先拦截网络重试的 `@RepeatSubmit`，而由 `idempotencyKey + request hash + 数据库唯一键` 完成可复用防重。写操作日志不得记录正文，只记录 scriptId、动作、字符数和结果。

- [ ] **步骤 5：运行 GREEN、双模块路由扫描和提交**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-user -am -Dtest=org.dromara.aivideo.user.script.controller.UserScriptControllerTest test
rg -n "/api/studio/scripts" ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main
```

预期：测试 PASS；路由只出现在 `ai-video-user`，`ai-video-platform` 无匹配。提交：

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/script
git commit -m "feat: 提供用户端文案接口"
```

### 任务 5：补齐真实持久化、所有权和并发集成验证

**风险与任务卡：** 红色；直接连接本机受控 MySQL/Redis。writer 只能清理 `ai_video_test` 和本次 Redis 前缀；独立 reviewer 核对没有生产地址、没有 `FLUSHALL`、没有密码输出。必须在 Task 2–4 后串行。

**文件：** 扩充 `UserScriptSchemaIT.java`，创建 `UserScriptPersistenceIT.java`（同一测试目录）。

- [ ] **步骤 1：写失败的真实 Mapper/事务用例**

用两个 app user 写入数据，断言 A 的 owner 查询无法返回 B；并发两个相同 idempotency key 最终只有一个主体/v1；两个相同父版本和 revision 的编辑最多一个成功，失败方为 `46136`，版本表无孤儿记录。

```java
assertThat(count(connection,
    "SELECT COUNT(*) FROM av_user_script WHERE tenant_id=? AND owner_id=? AND deleted='0'",
    tenantA, userA)).isEqualTo(1L);
assertThat(count(connection,
    "SELECT COUNT(*) FROM av_script_version WHERE tenant_id=? AND script_id=?",
    tenantA, scriptId)).isEqualTo(2L);
```

- [ ] **步骤 2：运行并确认 RED，再最小修正 Mapper/事务**

```powershell
mvn '-Pdev,local-integration-test' -pl ruoyi-modules/ai-video/ai-video-core -am -Dit.test=org.dromara.aivideo.script.UserScriptPersistenceIT -DskipITs=false verify
```

预期首次 FAIL 于真实 SQL、唯一键竞争或事务断言；只修正 `AvUserScriptMapper.xml`、`AvScriptVersionMapper.xml`、`UserScriptServiceImpl.java` 及对应测试，不扩大到下游模块。

- [ ] **步骤 3：运行 GREEN 和全量 script 后端测试**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -Dtest='org.dromara.aivideo.script.**,org.dromara.aivideo.user.script.**' test
mvn '-Pdev,local-integration-test' -pl ruoyi-modules/ai-video/ai-video-core -am -Dit.test='org.dromara.aivideo.script.UserScriptSchemaIT,org.dromara.aivideo.script.UserScriptPersistenceIT' -DskipITs=false verify
```

预期：Surefire 与 Failsafe 都 tests>0 且 PASS。提交定向修正：

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/script ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script
git commit -m "test: 验证个人文案持久化隔离"
```

### 任务 6：以 Vitest 驱动前端稳定类型和 API 适配

**风险与任务卡：** 绿色；不改 UI，只冻结浏览器和后端之间的类型、路径与 ID 字符串。可在 Task 3 后与 Task 4 并发；API reviewer 核对页面外没有散落 URL。

**文件：** 创建 `src/services/ai-video/script/types.ts`、`api.ts`、`api.test.ts`。

- [ ] **步骤 1：写失败的 API 测试**

```ts
it('creates a manual script through the user endpoint', async () => {
  const request = vi.fn().mockResolvedValue({ scriptId: '1765400000000000001' });
  const api = createUserScriptApi({ request });
  const input = { displayTitle: '夏季新品', scriptText: '正文', idempotencyKey: 'intent-1' };
  await api.create(input);
  expect(request).toHaveBeenCalledWith('/api/studio/scripts', { method: 'POST', data: input });
});

it('encodes script and version ids without converting them to numbers', async () => {
  const request = vi.fn().mockResolvedValue({ versionId: '1765400000000000002' });
  const api = createUserScriptApi({ request });
  await api.version('1765400000000000001', '1765400000000000002');
  expect(request).toHaveBeenCalledWith(
    '/api/studio/scripts/1765400000000000001/versions/1765400000000000002',
    { method: 'GET' },
  );
});
```

- [ ] **步骤 2：运行并确认 RED**

```powershell
npm test -- src/services/ai-video/script/api.test.ts
```

工作目录：`ai-video-ui/ai-video-webapp`。预期：FAIL，模块不存在。

- [ ] **步骤 3：实现 API**

`UserScriptApi` 固定方法：

```ts
export interface UserScriptApi {
  list(input?: UserScriptListQuery): Promise<UserScriptPage>;
  detail(scriptId: string): Promise<UserScriptDetail>;
  version(scriptId: string, versionId: string): Promise<ScriptVersion>;
  create(input: UserScriptInput): Promise<UserScriptSaveResult>;
  createVersion(scriptId: string, input: UserScriptEditInput): Promise<UserScriptSaveResult>;
  remove(scriptId: string): Promise<void>;
}
```

运行时创建方式逐项复用 `portrait/api.ts` 的 `createRuoYiAdapter`、`APP_AUTH_CLIENT_ID`、`authSession` 和单次登录跳转；不得在组件中解包 `R<T>` 或拼 URL。

- [ ] **步骤 4：运行 GREEN、类型检查和提交**

```powershell
npm test -- src/services/ai-video/script/api.test.ts
npm run tsc
```

预期：PASS。提交：

```powershell
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/script
git commit -m "feat: 添加用户文案前端接口层"
```

### 任务 7：以组件测试驱动新建/编辑弹窗

**风险与任务卡：** 黄色；涉及表单丢失和重复提交。一个 writer 只改 modal；UI reviewer 核对 Ant Design API、键盘可用性和未保存确认。可与 Task 4 并发。

**文件：** 创建 `ScriptEditorModal.tsx`、`ScriptEditorModal.test.tsx`。

- [ ] **步骤 1：检查 Ant Design 本地资料**

若项目 skill 仍不存在，运行：

```powershell
npx antd info Modal --lang zh
npx antd doc Form --lang zh
npx antd doc Input --lang zh
```

预期：命令退出码 0；把确认到的 `open/confirmLoading/destroyOnHidden` 和 Form preserve 行为应用到组件，不新增自制 modal。

- [ ] **步骤 2：写失败的交互测试**

测试新建两字段、空白校验、100/20,000 code point 边界、提交 loading 防重、失败保留输入、成功 reset、关闭脏表单确认、编辑初始值和 `46136` 保留本地内容。

```tsx
await user.type(screen.getByLabelText('标题'), '夏季新品');
await user.type(screen.getByLabelText('文案正文'), '这是一段正文');
await user.click(screen.getByRole('button', { name: '保存' }));
expect(onSubmit).toHaveBeenCalledTimes(1);
expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
  displayTitle: '夏季新品',
  scriptText: '这是一段正文',
}));
```

- [ ] **步骤 3：运行 RED 并实现组件**

```powershell
npm test -- src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx
```

预期首次 FAIL。实现 `Modal + Form + Input + Input.TextArea`；idempotency key 由父组件传入，同一次打开/网络重试不变，关闭并重新开始保存意图时才生成新 key。正文换行归一和 code point 计数与后端一致。

- [ ] **步骤 4：运行 GREEN 和提交**

```powershell
npm test -- src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx
npm run tsc
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptEditorModal.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx
git commit -m "feat: 添加文案录入编辑弹窗"
```

### 任务 8：以页面测试驱动真实文案库并移除静态数据

**风险与任务卡：** 黄色；修改共享 `LibraryView/model/style`，当前工作树另有肖像相关改动，因此只能由一个 writer 在隔离工作树串行合入。reviewer 专查加载、空、失败、403、分页、删除和无假数据状态。

**文件：** 创建 `ScriptLibraryView.tsx/test.tsx`；修改 `LibraryView.tsx/test.tsx`、`model.ts`、`style.css`、`index.test.tsx`。

- [ ] **步骤 1：写失败的页面状态测试**

mock `userScriptApi`，逐一覆盖：loading；空数据“还没有文案”；搜索无结果；网络失败和重试；403 权限不足；分页；新建成功刷新第一页；新建失败保留表单；详情版本倒序；复制成功/失败；编辑新版本；`46136` 冲突；删除确认；`46118` 引用保护。

```tsx
expect(screen.queryByText('示范文案')).not.toBeInTheDocument();
expect(screen.getByRole('button', { name: '新建文案' })).toBeInTheDocument();
await waitFor(() => expect(userScriptApi.list).toHaveBeenCalledWith(expect.objectContaining({
  pageNum: 1,
  pageSize: 20,
})));
```

- [ ] **步骤 2：运行 RED**

```powershell
npm test -- src/pages/digital-human-studio/components/ScriptLibraryView.test.tsx src/pages/digital-human-studio/components/LibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx
```

预期：FAIL，`ScriptLibraryView` 不存在且静态示范文案仍出现。

- [ ] **步骤 3：实现页面最短真实数据流**

`ScriptLibraryView` 复用现有 useState/useEffect 请求风格并用 request revision 防止旧响应覆盖新查询；列表页只持有 `rows/total/page/keyword/loading/error/errorCode`。详情按需 GET；复制必须先取得当前版本正文再调用 `navigator.clipboard.writeText`。创建、编辑、删除成功后重新请求列表；403 使用 `Alert`，401 由 adapter 处理。

`LibraryView` 在任何读取 `SCRIPTS`、计算 count 或过滤之前执行：

```tsx
if (route === 'scripts') {
  return <ScriptLibraryView onToast={onToast} />;
}
```

随后删除 `LibraryView` 中静态文案分支和 `model.ts` 的 `SCRIPTS`；保留形象、声音、作品代码原状。按钮文案从“AI 生成文案”改为“新建文案”，不再导航创作步骤。

- [ ] **步骤 4：运行 GREEN、lint、构建和提交**

```powershell
npm test -- src/services/ai-video/script/api.test.ts src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx src/pages/digital-human-studio/components/ScriptLibraryView.test.tsx src/pages/digital-human-studio/components/LibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx
npm run lint
npm run build
```

预期：所有指定 Vitest PASS，lint/typecheck 退出码 0，Umi build 成功。提交：

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptLibraryView.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptLibraryView.test.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.test.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx
git commit -m "feat: 接入真实个人文案库页面"
```

### 任务 9：联调、双启动路由、专项审查与收口

**风险与任务卡：** 红色；最终集成门禁。契约/实现 writer 不得自签；至少一次规格契约 review 和一次身份/权限/归属 review，修复后只复核原问题及直接受影响测试。禁止扩展到上传、AI 生成、组织共享或下游使用。

**文件：** 只修改 review 发现的直接问题；不得新建业务范围。

- [ ] **步骤 1：运行全套定向后端验证**

```powershell
mvn '-Pdev' -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -Dtest='org.dromara.aivideo.script.**,org.dromara.aivideo.user.script.**' test
mvn '-Pdev,local-integration-test' -pl ruoyi-modules/ai-video/ai-video-core -am -Dit.test='org.dromara.aivideo.script.UserScriptSchemaIT,org.dromara.aivideo.script.UserScriptPersistenceIT' -DskipITs=false verify
```

预期：两条命令均退出码 0，报告 tests>0、failures/errors/skipped=0。

- [ ] **步骤 2：运行全套前端与规范验证**

```powershell
npm test -- src/services/ai-video/script/api.test.ts src/pages/digital-human-studio/components/ScriptEditorModal.test.tsx src/pages/digital-human-studio/components/ScriptLibraryView.test.tsx src/pages/digital-human-studio/components/LibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx
npm run lint
npm run build
pwsh -NoProfile -File scripts/validate-development-standards.ps1
git diff --check
```

前 3 条在 `ai-video-ui/ai-video-webapp` 执行，后 2 条在仓库根执行。预期全部退出码 0。

- [ ] **步骤 3：双启动路由 smoke**

分别启动用户端 API 和运营端 `ruoyi-admin`。用 app token 请求用户端六个路由，预期按权限返回业务响应；对运营端同路径请求预期 404。用运营 token 请求用户端路径预期 401/403；用用户 A 的 app token读取用户 B 的 scriptId 预期与不存在资源相同。

- [ ] **步骤 4：浏览器验收**

在 `http://127.0.0.1:8000/user/login` 登录 creator，进入 `/studio` 的“文案”：

```text
新建文案 -> 输入标题和正文 -> 保存 -> 列表可见
查看详情 -> 当前 v1 与完整正文可见
编辑 -> 保存 -> v2 成为当前版本，v1 仍可查看
复制 -> 剪贴板得到当前正文
删除 -> 二次确认 -> 列表消失
刷新页面 -> 不出现任何静态示范文案
```

- [ ] **步骤 5：完成两次独立 review**

规格契约 review 核对六路由、字段、错误码、P3 表兼容、无任务/额度/上传；身份归属 review 核对 `type="app"`、AppLoginHelper、tenant/personal owner SQL、未知归属字段拒绝、同形响应防 IDOR、日志不含全文。结论必须为 PASS 才能进入提交；若有问题，仅修复指出项并复跑直接受影响测试一次。

- [ ] **步骤 6：最终提交与工作树证明**

```powershell
git status --short
git diff --check HEAD^..HEAD
```

预期：只有本功能明确文件；没有用户原工作树的肖像、资产、声音改动被带入。若 review 产生修复，按对应任务卡列出的精确文件集合完成定向暂存，并以 `fix: 收紧个人文案接口边界` 提交；禁止使用 `git add .`。

## 4. 自检结果

- 规格覆盖：标题/正文弹窗、真实用户端六接口、分页、详情、复制、编辑不可变版本、删除、所有页面状态、个人归属、app 权限、幂等、并发、日志、无任务/额度/下游均有对应任务。
- 占位符扫描：计划中的每个代码步骤都有精确文件、签名、命令和预期结果；没有把实现决策留给执行阶段。
- 类型一致性：Java 与 TypeScript 均使用 `displayTitle/scriptText/scriptId/versionId/currentVersionId/scriptRevision/idempotencyKey`；业务 ID 和 revision 全程为字符串边界。
- 分层一致性：core 只有 `domain/dto/mapper/service/service.impl`，user 只有 `domain.bo/domain.vo/controller`；没有新增 DDD/Clean/Hexagonal 平行业务层。
- 安全一致性：请求不接受归属字段；SQL 不在内存过滤；他人资源与不存在资源同形；Controller 和 Service 双重权限/归属防护。
- 测试一致性：每个实现任务先 RED、再最小 GREEN；本机 IT 只允许专用 MySQL/Redis，无容器、无虚拟化、无 `FLUSHALL`。
