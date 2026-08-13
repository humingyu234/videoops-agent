# 发现页模板管理与展示（第一阶段）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 先交付运营端工作流模板、唯一 RunningHub 执行配置和 RunningHub 账号管理，并让用户端发现页通过真实接口看到全部可发布模板及详情。

**架构：** 复用既有 MySQL 表，在 `ai-video-core/workflow` 以贫血 Entity、Mapper 和 Service 编排模板/配置/账号；运营端与用户端分别在 `ai-video-platform`、`ai-video-user` 提供 BO/VO 和 HTTP 适配。用户可见性只依赖模板、唯一配置和账号均启用，运营人员人工验证后显式启用，不依赖 RunningHub 自动测试记录。

**技术栈：** Java 17、Spring Boot、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、JUnit 5、React 19、Umi Max、Ant Design 6、ProComponents、TanStack Query、Vitest。

**规格：** `docs/superpowers/specs/2026-08-11-discovery-template-management-phase1.md`

**风险与协作：** 红色；命中公共接口、数据库事实、运营权限、敏感凭据和用户端/运营端隔离。实现任务串行使用一名实现子代理；完成后进行一轮规格/契约审查和一轮安全专项审查，修复后只定向复核差异。不得扩展到上传、订单、真实 RunningHub 请求、测试任务或结果轮询。

---

## 文件结构

### 公共契约

- 修改 `docs/API_CONTRACT.md`：冻结人工启用后的用户可见条件和第一阶段运营端端点。
- 修改 `docs/DOMAIN_MODEL.md`：说明测试修订不再作为发布门槛，`row_revision` 只是并发控制。
- 修改 `docs/ASYNC_TASKS.md`：明确模板可见性不创建测试任务。
- 修改 `docs/superpowers/specs/2026-08-11-discovery-template-management-phase1.md`：明确 AI App 只在用户端隐藏，运营端仍可作为唯一 RunningHub 执行模式。
- 修改 `docs/superpowers/plans/2026-08-11-discovery-runninghub-single-execution.md`：把本计划列为优先阶段，并删除“测试成功才可启用”的旧步骤。

### 后端核心

- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowTemplate.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowExecutionConfig.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/RunningHubAccount.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/DiscoveryCategory.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/DiscoveryTag.java`
- 创建同名 Mapper：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/*.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/workflow/WorkflowTemplateMapper.xml`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowChannel.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowExecutionMode.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowTemplateStatus.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/constant/WorkflowErrorCodes.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowTemplateDTOs.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/RunningHubAccountDTOs.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/validation/WorkflowSchemaCanonicalizer.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowTemplateService.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IRunningHubAccountService.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowCredentialWriteService.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/WorkflowTemplateServiceImpl.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/RunningHubAccountServiceImpl.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/security/RunningHubCredentialCipher.java`
- 创建对应单元测试及 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowTemplateVisibilityIT.java`

### 运营端后端

- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/WorkflowTemplateAdminController.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/RunningHubAccountAdminController.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/WorkflowTemplateAdminBos.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/RunningHubAccountAdminBos.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/WorkflowTemplateAdminVos.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/RunningHubAccountAdminVos.java`
- 创建两个端侧 Service 接口与实现：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/**`
- 创建对应 Controller/Service 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/**`

### 用户端后端

- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/controller/DiscoveryController.java`
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/service/IDiscoveryService.java`
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/service/impl/DiscoveryServiceImpl.java`
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/DiscoveryHomeVo.java`
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplateCardVo.java`
- 删除 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplatePageVo.java`，分页统一使用框架 `PageResult<WorkflowTemplateCardVo>`。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplateDetailVo.java`
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowCreationConfigVo.java`
- 修改/创建用户发现页 Controller/Service 测试。

### 运营端前端

- 修改 `ai-video-ui/ai-video-platform-ui/src/pages/dynamicPage.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-template/{types.ts,index.ts,index.test.ts}`
- 创建 `ai-video-ui/ai-video-platform-ui/src/api/aivideo/runninghub-account/{types.ts,index.ts,index.test.ts}`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/{index.tsx,index.test.tsx}`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateForm.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowFormSchemaEditor.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/ExecutionConfigForm.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateDetailDrawer.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/runninghub-account/{index.tsx,index.test.tsx}`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/runninghub-account/components/RunningHubAccountFormModal.tsx`
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/runninghub-account/components/RunningHubAccountDetailDrawer.tsx`

### 用户端前端

- 修改 `ai-video-ui/ai-video-webapp/src/services/ai-video/core/{wire.ts,wire.test.ts}`
- 修改 `ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/{types.ts,api.ts,queryKeys.ts,api.test.ts}`
- 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/{adapter.ts,adapter.test.ts}`
- 修改 `ai-video-ui/ai-video-webapp/src/pages/discovery/{index.tsx,discovery.module.css,discovery.test.tsx}`
- 修改 `ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/{index.tsx,index.test.tsx,template-detail.module.css}`
- 修改 `ai-video-ui/ai-video-webapp/config/routes.ts`、`ai-video-ui/ai-video-webapp/src/routes.test.ts`、`ai-video-ui/ai-video-webapp/package.json` 和 `ai-video-ui/ai-video-webapp/src/config.test.ts`
- 删除 `ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/{index.tsx,template-create.module.css}`
- 修改 `ai-video-ui/ai-video-webapp/mock/discovery.ts`

---

### 任务 1：同步人工审核发布契约

**风险：** 红色。公共接口和用户可见条件发生变化。

**文件：** 公共契约四个文件。

- [ ] **步骤 1：把用户可见条件写成精确布尔规则。**

```text
visible = template.status == enabled
       && execution_config.del_flag == 0
       && execution_config.enabled == true
       && runninghub_account.del_flag == 0
       && runninghub_account.enabled == true
```

明确 `last_test_status` 和三个 `last_test_*_revision` 不参与列表、详情、creation-config 或 enable 判定；管理员通过独立 enable 动作表示已完成人工验证。发现目录为平台全局目录，Core 查询固定 `tenant_id=0`，Controller 不接受客户端传入 tenant、owner 或 workspace。分类与标签的用户 wire code 固定为其十进制 ID 字符串；封面素材未接入前允许返回 `null`。

- [ ] **步骤 2：冻结第一阶段运营端端点。**

```text
GET    /api/admin/workflow-templates
GET    /api/admin/workflow-templates/{templateId}
POST   /api/admin/workflow-templates
PUT    /api/admin/workflow-templates/{templateId}
DELETE /api/admin/workflow-templates/{templateId}
GET    /api/admin/workflow-templates/{templateId}/execution-config
PUT    /api/admin/workflow-templates/{templateId}/execution-config
POST   /api/admin/workflow-templates/{templateId}/enable
POST   /api/admin/workflow-templates/{templateId}/disable
GET    /api/admin/workflow-templates/options

GET    /api/admin/runninghub-accounts
GET    /api/admin/runninghub-accounts/{accountId}
POST   /api/admin/runninghub-accounts
PUT    /api/admin/runninghub-accounts/{accountId}
DELETE /api/admin/runninghub-accounts/{accountId}
POST   /api/admin/runninghub-accounts/{accountId}/enable
POST   /api/admin/runninghub-accounts/{accountId}/disable
```

模板 create/update 只保存基础资料和表单 schema；唯一执行配置通过独立 `GET/PUT execution-config` 维护，以兼容已确认的单执行接口。运营端新增向导先创建草稿，再保存唯一配置；第二步失败时保留不可见草稿并提供原地重试，不自动启用。

- [ ] **步骤 3：验证文档。**

运行：`powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1`

预期：退出码 `0`，不再出现“测试成功才可见/才可启用”的活动契约。

- [ ] **步骤 4：提交。**

```powershell
git add docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ASYNC_TASKS.md docs/superpowers/specs/2026-08-11-discovery-template-management-phase1.md docs/superpowers/plans/2026-08-11-discovery-runninghub-single-execution.md
git commit -m "docs(发现): 改为人工验证后发布模板"
```

### 任务 2：实现模板、单配置、账号核心服务

**风险：** 红色。数据库一致性和敏感凭据边界。

**文件：** 后端核心与对应测试文件。

- [ ] **步骤 1：先写失败测试。** 覆盖：创建模板固定为全局目录草稿；首次保存唯一配置成功、同租户第二条配置被唯一约束拒绝；Workflow/AI App 字段互斥；启用只检查配置/账号启用，不读取测试成功修订；停用账号使用户查询不可见；账号 API Key 只经写入加密接口持久化且任何 DTO/toString 不含明文；删除被引用账号失败；旧 `rowRevision` 更新失败。

```java
@Test
void enableAcceptsManuallyVerifiedTemplateWithoutSuccessfulTestRevision() {
    when(configMapper.selectCurrent(0L, 101L)).thenReturn(config(101L, 201L, true, "never"));
    when(accountMapper.selectByScope(0L, 201L)).thenReturn(account(201L, true));

    service.enable("101", 3L, 9001L);

    verify(templateMapper).enableByRevision(0L, 101L, 3L, 9001L);
    verifyNoInteractions(taskExecutionService);
}
```

- [ ] **步骤 2：建立贫血 Entity 和 Mapper。** 主键使用 `@TableId(type = IdType.ASSIGN_ID)`；`delFlag` 使用 `@TableLogic`；`rowRevision` 使用 `@Version`。复杂用户分页只在 XML 中联结当前配置和账号：

```sql
FROM av_workflow_template t
JOIN av_workflow_execution_config c
  ON c.tenant_id=t.tenant_id AND c.template_id=t.template_id
 AND c.del_flag='0' AND c.enabled=1
JOIN av_runninghub_account a
  ON a.tenant_id=t.tenant_id AND a.account_id=c.runninghub_account_id
 AND a.del_flag='0' AND a.enabled=1
WHERE t.tenant_id=0 AND t.del_flag='0' AND t.status='enabled'
```

- [ ] **步骤 3：定义跨模块 DTO 和 Service。**

```java
public interface IWorkflowTemplateService {
    PageResult<WorkflowTemplateDTOs.AdminSummary> queryAdminPage(
        WorkflowTemplateDTOs.AdminQuery query, PageQuery pageQuery);
    WorkflowTemplateDTOs.AdminDetail queryAdminDetail(String templateId);
    String create(WorkflowTemplateDTOs.Save command, Long actorId);
    void update(String templateId, WorkflowTemplateDTOs.Save command, Long actorId);
    WorkflowTemplateDTOs.ExecutionConfig queryExecutionConfig(String templateId);
    void saveExecutionConfig(
        String templateId,
        WorkflowTemplateDTOs.ExecutionConfigSave command,
        Long actorId);
    void enable(String templateId, Long expectedRevision, Long actorId);
    void disable(String templateId, Long expectedRevision, Long actorId);
    void delete(String templateId, Long expectedRevision, Long actorId);
    WorkflowTemplateDTOs.Options queryOptions();
    PageResult<WorkflowTemplateDTOs.PublicCard> queryVisiblePage(
        WorkflowTemplateDTOs.PublicQuery query, PageQuery pageQuery);
    WorkflowTemplateDTOs.PublicDetail queryVisibleDetail(String templateId);
    WorkflowTemplateDTOs.CreationConfig queryVisibleCreationConfig(String templateId);
    WorkflowTemplateDTOs.DiscoveryHome queryDiscoveryHome();
}

public interface IRunningHubAccountService {
    PageResult<RunningHubAccountDTOs.Summary> queryPage(
        RunningHubAccountDTOs.Query query, PageQuery pageQuery);
    RunningHubAccountDTOs.Detail queryDetail(String accountId);
    String create(RunningHubAccountDTOs.Save command, Long actorId);
    void update(String accountId, RunningHubAccountDTOs.Save command, Long actorId);
    void enable(String accountId, Long expectedRevision, Long actorId);
    void disable(String accountId, Long expectedRevision, Long actorId);
    void delete(String accountId, Long expectedRevision, Long actorId);
}

public interface IWorkflowCredentialWriteService {
    String encryptForStorage(char[] plaintext);
}
```

- [ ] **步骤 4：实现 schema 规范化。** 只接受 `schemaVersion=workflow-form-1` 和字段白名单；按 Unicode key 排序、保留数组顺序，计算 `sha256:` 加 64 位小写十六进制。字段控件和值类型映射固定为公共契约，未知属性或重复 `inputKey` 失败。

- [ ] **步骤 5：实现账号与配置保存。** API Key 创建必填、修改空值表示不变；accessPassword 空值表示不变，`clearAccessPassword=true` 才清除。AES-GCM 使用随机 96-bit nonce，主密钥仅从 `AI_VIDEO_RUNNINGHUB_MASTER_KEY` 读取；缺失、长度非法时仅秘密写操作 fail closed，不让缺失密钥阻断应用启动或只读查询；加密实现不提供 Core 解密方法。修改模板或配置保持当前模板状态，不自动进入 `pending_test`；`pending_test` 仅作为历史兼容状态保留。

- [ ] **步骤 6：运行单元测试和本机 MySQL IT。**

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowTemplateServiceImplTest,RunningHubAccountServiceImplTest,WorkflowSchemaCanonicalizerTest,RunningHubCredentialCipherTest' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowTemplateVisibilityIT' verify
```

预期：单测全绿；IT 只使用 `ai_video_test` 与 Redis DB 15，并证明 failed/never 测试状态不影响已人工启用模板可见性。

- [ ] **步骤 7：提交。**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core ai-video-api/ruoyi-modules/ai-video/ai-video-infra
git commit -m "feat(workflow): 实现模板单配置与账号核心管理"
```

### 任务 3：实现运营端模板与账号 HTTP

**风险：** 红色。高权限操作与秘密写入。

**文件：** 运营端后端全部文件。

- [ ] **步骤 1：先写 Controller/Service 失败测试。** 覆盖任务 1 冻结的全部端点、分页映射、请求校验、旧 revision 冲突、无权限时 Service 零调用、API Key/accessPassword 不出现在 JSON 和操作日志中。

- [ ] **步骤 2：实现 BO/VO。** 模板保存 BO 只包含基础资料与 `formSchema`；创建不接受 `templateId/status/enabledAt/rowRevision`，修改只从路径取 ID。唯一配置使用独立 `ExecutionConfigBo` 和 `GET/PUT execution-config`；账号 VO 只含 `apiKeyMasked/hasApiKey`，模板详情仅含配置摘要，不含 API Key 或 accessPassword。

```java
public record StatusChangeBo(@NotNull Long expectedRevision) {}

public record ExecutionConfigBo(
    @NotNull String runningHubAccountId,
    @Pattern(regexp = "runninghub_workflow|runninghub_ai_app") String executionMode,
    String workflowId,
    String webAppId,
    String accessPassword,
    boolean clearAccessPassword,
    @NotNull JsonNode inputMapping,
    @NotNull JsonNode outputPolicy,
    @Min(1) @Max(86400) int timeoutSeconds,
    @NotNull Boolean enabled,
    Long expectedRevision
) {}
```

- [ ] **步骤 3：实现端侧 Service 映射。** Controller 只做权限、校验、日志和 `R` 包装；事务和业务校验留在 Core Service。所有 ID 跨 HTTP 转十进制字符串。

- [ ] **步骤 4：实现权限矩阵。**

```text
aivideo:workflow-template:query|add|edit|remove|enable|disable
aivideo:runninghub-account:query|add|edit|remove|enable|disable|update-key
```

POST/PUT/DELETE 使用 `@Log` 和 `@RepeatSubmit`；执行配置查询沿用 `aivideo:workflow-template:query`，保存沿用 `aivideo:workflow-template:edit`。涉及密钥的 Controller 日志不得记录请求/响应正文。

- [ ] **步骤 5：运行测试。**

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowTemplateAdminControllerTest,WorkflowTemplateAdminServiceImplTest,RunningHubAccountAdminControllerTest,RunningHubAccountAdminServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **步骤 6：提交。**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-platform
git commit -m "feat(运营): 增加工作流模板与 RunningHub 账号接口"
```

### 任务 4：实现用户端模板列表、详情与创建配置 HTTP

**风险：** 红色。用户数据面必须拒绝运营字段泄漏。

**文件：** 用户端后端全部文件。

- [ ] **步骤 1：先写失败测试。** 列表只返回可见模板并使用 `PageResult` 的数值 `total`；停用模板详情返回 `46501`；配置/账号停用时详情和 creation-config 不可用；测试状态 `never/failed` 不影响可见；11 个 canonical forbidden fields 在序列化 JSON 任意深度均不存在；所有发现端点都要求 App 权限且不接受客户端 tenant/owner/workspace。

- [ ] **步骤 2：扩展接口。**

```java
@SaCheckPermission(value = "aivideo:studio:query", type = "app")
@GetMapping("/templates/{templateId}")
public R<WorkflowTemplateDetailVo> template(@PathVariable String templateId) {
    return R.ok(discoveryService.queryTemplate(templateId));
}

@SaCheckPermission(value = "aivideo:studio:query", type = "app")
@GetMapping("/templates/{templateId}/creation-config")
public R<WorkflowCreationConfigVo> creationConfig(@PathVariable String templateId) {
    return R.ok(discoveryService.queryCreationConfig(templateId));
}
```

`home`、`templates` 同样使用上述 App 权限。`templates` 返回 `R<PageResult<WorkflowTemplateCardVo>>`，删除自定义 `WorkflowTemplatePageVo`，确保 `total` 是 JSON number。

- [ ] **步骤 3：删除旧用户字段。** `WorkflowTemplateCardVo` 不含 `templateVersionId`、方案数量或任何配置标识；详情只含展示字段和 `requiredInputs`；creation-config 精确只含 `templateId/schemaVersion/schemaHash/fields/estimatedDurationSeconds?/billingPolicy:{mode:'free'}`。用户 wire 中禁止任何执行模式、账号、远端 ID、节点或任务字段。

- [ ] **步骤 4：实现首页。** Banner 在没有可用素材管理前返回空数组；recommendations 取已启用模板前 6 条；频道、分类、标签和计数来自真实模板/category/tag 表。封面为空时返回 `null`，前端展示占位，不拼接内部对象 Key。

- [ ] **步骤 5：运行测试。**

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=DiscoveryControllerTest,DiscoveryServiceImplTest,DiscoveryUserWireContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **步骤 6：提交。**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-user
git commit -m "feat(发现): 提供真实模板列表与详情接口"
```

### 任务 5：实现运营端管理页面

**风险：** 红色。页面按钮权限和密钥处理必须与后端一致。

**文件：** 运营端前端全部文件。

- [ ] **步骤 1：先写 API 与页面失败测试。** 覆盖路径/方法、`{data,total,success}` 分页映射、403、加载失败、空列表、提交中、删除确认、启停、API Key 不回填、关闭表单后秘密清空。

- [ ] **步骤 2：定义类型和 API。** Template save 只含基础资料和 `formSchema`，执行配置通过独立 `GET/PUT /{templateId}/execution-config`；账号 detail 只接受 masked 状态。模板 API 使用固定常量 `/api/admin/workflow-templates`，账号 API 使用 `/api/admin/runninghub-accounts`，启停动作均使用 `POST`。

- [ ] **步骤 3：注册动态页面。** 在 `migratedPages` 增加：

```ts
'aivideo/workflow-template/index': WorkflowTemplatePage,
'aivideo/runninghub-account/index': RunningHubAccountPage,
```

- [ ] **步骤 4：实现账号页。** 使用 `PageContainer + ProTable + ModalForm`；查询/add/edit/remove/enable/disable 按 `hasPermi` 控制。创建 API Key 必填，编辑留空表示不变，掩码永不回交。

- [ ] **步骤 5：实现模板页。** 使用 `PageContainer + ProTable + DrawerForm`；基础字段和唯一配置同屏编辑，提交时先保存模板草稿/基础资料，再保存独立唯一配置。配置保存失败则提示“草稿已保存，配置保存失败”，保持抽屉和输入供原地重试，不自动启用。执行模式只有 RunningHub Workflow/RunningHub AI App 两项，互斥显示 `workflowId`/`webAppId`；不出现 self-hosted、版本、优先级、权重、路由或故障切换。启用按钮弹出“已人工验证模板与配置”的二次确认。

- [ ] **步骤 6：校验 JSON 编辑字段。** `formSchema/inputMapping/outputPolicy` 在提交前 `JSON.parse`，错误定位到对应表单项；未知后端错误由统一 request 层展示，不能按中文消息分支。

- [ ] **步骤 7：运行前端验证。** 本地 Ant Design skill 缺失，使用仓库 CLI 和现有页面模式校验。

```powershell
Set-Location ai-video-ui\ai-video-platform-ui
pnpm.cmd test -- src/api/aivideo/workflow-template/index.test.ts src/api/aivideo/runninghub-account/index.test.ts src/pages/aivideo/workflow-template/index.test.tsx src/pages/aivideo/runninghub-account/index.test.tsx
pnpm.cmd lint
pnpm.cmd build
```

- [ ] **步骤 8：提交。**

```powershell
git add ai-video-ui/ai-video-platform-ui
git commit -m "feat(运营): 增加工作流模板与账号管理页"
```

### 任务 6：切换用户端发现页到真实安全 wire

**风险：** 红色。旧服务商字段必须从缓存、类型、DOM 和 Mock 中消失。

**文件：** 用户端前端全部文件。

- [ ] **步骤 1：先写 adapter 和页面失败测试。** adapter 必须拒绝 canonical 11 个 forbidden 字段和值（顶层和嵌套）、额外属性、非法 ID/枚举/媒体 URL；页面覆盖 loading/error/retry/empty/pagination/detail unavailable、creation-config unavailable 和无封面占位；wire 测试只拒绝 canonical key/value，不误杀普通中文说明文本。

- [ ] **步骤 2：收敛类型/API/query key。** 删除 `WorkflowProviderKind`、`WorkflowExecutionPlan`、方案级 schema；保留：

```ts
interface DiscoveryApi {
  getHome(): Promise<DiscoveryHome>;
  getTemplates(params: TemplateListParams): Promise<WorkflowTemplatePage>;
  getTemplate(templateId: string): Promise<WorkflowTemplateDetail>;
  getCreationConfig(templateId: string): Promise<WorkflowCreationConfig>;
}
```

- [ ] **步骤 3：实现严格 adapter。** 响应先做 exact-key 和 forbidden-key 检查，再进入 React Query 缓存；`cover` 可为 `null`，URL 只允许同源绝对路径或 HTTPS。

- [ ] **步骤 4：更新发现列表和详情。** 删除“可用方案数量”“选择服务”“服务商”文案；卡片和详情无封面时显示一致占位。首页与模板列表使用独立 loading/error 边界，首页失败不能遮蔽已成功的列表。详情并行读取 detail 与 creation-config，展示 description、标签和由 creation-config 派生的必需输入；分别处理 403、`46501` 模板不可用、`46503` 配置不可用和网络失败。本阶段“使用此模板”按钮禁用并显示“制作功能正在接入”。私有查询只有在真实 `userId/workspaceId` 就绪后启用，不再使用伪造的 `current-user/current-workspace`。

- [ ] **步骤 5：删除旧创建入口。** 从 `config/routes.ts` 删除旧 create 路由并删除创建页及样式；清理订单详情等页面指向该路由的导航，任何页面都不再请求 execution-plans/form-schema 或提交旧订单参数。`package.json` 的默认 `start` 与 `dev` 都显式使用 `MOCK=none`，只有 `dev:discovery` 可加载发现 Mock。

- [ ] **步骤 6：更新 Mock。** 只保留 home/list/detail/creation-config，且所有对象与真实 wire 完全一致；删除旧 execution-plans 和方案级 form-schema 路由。

- [ ] **步骤 7：运行用户前端验证。**

```powershell
Set-Location ai-video-ui\ai-video-webapp
npm test -- src/services/ai-video/core/wire.test.ts src/services/ai-video/discovery/api.test.ts src/services/ai-video/discovery/adapter.test.ts src/pages/discovery/discovery.test.tsx src/pages/discovery/template-detail/index.test.tsx src/routes.test.ts src/config.test.ts
npm run tsc
npm run biome:lint
npm test
npx antd lint ./src/pages/discovery --format json
npm run build
```

预期：全部通过；`src/pages/discovery` 和 `src/services/ai-video/discovery` 不再定义服务商/方案/版本字段，只有安全断言测试可以引用 forbidden fixture。

- [ ] **步骤 8：提交。**

```powershell
git add ai-video-ui/ai-video-webapp
git commit -m "feat(发现): 展示真实工作流模板与详情"
```

### 任务 7：联调、回归与独立审查

**风险：** 红色。双启动器隔离、权限与公开数据面必须一起验证。

**文件：** 仅允许修复本计划直接缺陷；不新增业务范围。

- [ ] **步骤 1：运行后端组合回归。**

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-platform,ai-video-user-api,ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowRunningHubMigrationIT,org.dromara.aivideo.workflow.WorkflowTemplateVisibilityIT' verify
```

- [ ] **步骤 2：运行两套前端完整验证。**

```powershell
Set-Location ai-video-ui\ai-video-platform-ui
pnpm.cmd lint
pnpm.cmd test
pnpm.cmd build

Set-Location ..\ai-video-webapp
npm run lint
npm test
npm run build
```

- [ ] **步骤 3：执行静态边界扫描。**

```powershell
rg -n "self_hosted_comfyui|providerKind|executionPlanId|templateVersionId" ai-video-ui/ai-video-webapp/src/pages/discovery ai-video-ui/ai-video-webapp/src/services/ai-video/discovery -g "!*.test.ts" -g "!*.test.tsx" -g "!adapter.ts"
rg -n "application|application\.impl|port|adapter|command|model" ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow
git diff --check
```

预期：第一条仅允许安全测试/denylist 引用，第二条不存在平行业务分层，`git diff --check` 无输出。

- [ ] **步骤 4：人工联调最短闭环。** 运营端创建停用账号→启用账号→创建草稿模板并保存其唯一配置→人工确认并启用→用户端列表出现→详情可打开→停用模板后列表消失且详情返回 46501。确认 API Key、accessPassword、执行模式和远端 ID 从未出现在用户响应、用户 DOM 或普通日志中。

- [ ] **步骤 5：进行一次规格/契约审查和一次安全专项审查。** 最终问题只标为 `[必须修复]`、`[建议修改]`、`[仅供参考]`；只关闭本计划的 `[必须修复]`，修复后定向复核差异。

- [ ] **步骤 6：提交收口修复。**

```powershell
git add docs ai-video-api ai-video-ui
git commit -m "fix(发现): 收口模板管理与展示第一阶段"
```

---

## 计划自检

- **规格覆盖：** 运营模板 CRUD、唯一配置、账号最小管理、人工启用、用户列表/详情、禁用字段、页面状态和明确不做范围均有任务。
- **占位符扫描：** 计划不包含未定义类型或省略实现的占位任务；第二阶段能力只作为本阶段禁止范围，不作为当前交付步骤。
- **类型一致性：** HTTP 使用十进制字符串 ID；Core Entity 使用 `Long`；`PageResult.total` 在用户 wire 中为 number；模板状态兼容 `draft|pending_test|enabled|disabled`，本阶段只主动写入 `draft|enabled|disabled`，不因修改自动进入 `pending_test`；执行模式仅在运营端为 `runninghub_workflow|runninghub_ai_app`。
- **恢复边界：** IT 仅使用 `LocalIntegrationEnvironment`、MySQL `ai_video_test`、Redis DB 15 和本次前缀；不使用容器、不清理其他数据库或 Redis key。
