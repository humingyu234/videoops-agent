# 10 A：后端实现计划

> **负责人：A。** 本文件保留原总计划第 2 节和 6.2 任务卡。开始前必须确认当前分支起点等于 00 计划发布的 C0_SHA；不得修改 00 冻结的公共契约。共享纪律见 [README](README.md)。

## 2. 后端设备：项目、草稿、版本、素材与统一任务

> 本节只允许后端设备执行。它不修改 C0 文件、前端文件、infra 媒体实现或媒体配置。

### 任务 8：实现 app 专用 MyBatis 审计上下文

**风险：** 红色。必须证明 app 用户编号不会被运营用户审计上下文污染。

**文件：**

- 新建：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AppAuditRequired.java`
- 新建：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AuditFillContext.java`
- 修改：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/InjectionMetaObjectHandler.java`
- 新建测试：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/test/java/org/dromara/common/mybatis/audit/AuditFillContextTest.java`
- 新建测试：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/test/java/org/dromara/common/mybatis/handler/InjectionMetaObjectHandlerAppAuditTest.java`
- 新建：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/aivideo/bootstrap/AppMybatisAuditContextFilter.java`
- 新建测试：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/AppMybatisAuditContextFilterTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AppAuditRequired.java
git add -- ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AuditFillContext.java
git add -- ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/InjectionMetaObjectHandler.java
git add -- ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/test/java/org/dromara/common/mybatis/audit/AuditFillContextTest.java
git add -- ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/test/java/org/dromara/common/mybatis/handler/InjectionMetaObjectHandlerAppAuditTest.java
git add -- ai-video-api/ai-video-user-api/src/main/java/org/dromara/aivideo/bootstrap/AppMybatisAuditContextFilter.java
git add -- ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/AppMybatisAuditContextFilterTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写失败测试：嵌套上下文按栈恢复，`close`／异常后清理，同线程没有泄漏，错误关闭顺序被拒绝。
- [ ] 写失败测试：带 `@AppAuditRequired` 的 Entity 缺上下文时填充直接失败；有上下文时 `createBy/updateBy` 写当前 app 用户 `Long`，`createDept` 为空；同号 `sys_user` 不会被读取。
- [ ] 写过滤器失败测试：只从认证后的 `AppLoginHelper` 取得用户编号，`finally` 必清理；未登录、异常、异步线程均不会遗留上下文。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-common/ruoyi-common-mybatis,ai-video-user-api -am -Dmaven.test.skip=false -DskipTests=false -Dtest=AuditFillContextTest,InjectionMetaObjectHandlerAppAuditTest,AppMybatisAuditContextFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 最小实现注解、`AutoCloseable` 上下文、MetaObjectHandler 分支和过滤器；未标记既有 Entity 行为保持不变。
- [ ] 绿灯后精确提交 `feat: 增加用户端审计填充上下文`。

### 任务 9：建立九张表的贫血 Entity 与 Mapper

**风险：** 红色。所有查询和条件更新必须显式携带当前用户归属。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/domain/CreationAsset.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/domain/CreationProject.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/mapper/CreationAssetMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/mapper/CreationProjectMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/creation/CreationAssetMapper.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/creation/CreationProjectMapper.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineDraft.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineVersion.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineAssetRef.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineWriteReceipt.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineDraftMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineVersionMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineAssetRefMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineWriteReceiptMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/timeline/TimelineVersionMapper.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTask.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskExecution.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskAttempt.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskExecutionMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskMapper.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskExecutionMapper.xml`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/CreationPersistenceContractTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskMapperContractTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/domain/CreationAsset.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/domain/CreationProject.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/mapper/CreationAssetMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/mapper/CreationProjectMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/creation/CreationAssetMapper.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/creation/CreationProjectMapper.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineDraft.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineVersion.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineAssetRef.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/domain/TimelineWriteReceipt.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineDraftMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineVersionMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineAssetRefMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/mapper/TimelineWriteReceiptMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/timeline/TimelineVersionMapper.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTask.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskExecution.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskAttempt.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskExecutionMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskMapper.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskExecutionMapper.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/CreationPersistenceContractTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskMapperContractTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 先写失败的反射测试：九个 Entity 均继承 `BaseEntity`、带 `@AppAuditRequired`，字段与迁移逐一对应；只有项目、素材、草稿含逻辑删除字段。
- [ ] 写 Mapper 失败测试：按 `(ownerUserId, id)` 查询，不提供不带 owner 的业务读取方法；CAS 更新必须同时匹配当前状态、`rowVersion`、当前执行和租约条件。
- [ ] 任务领取 SQL 只能领取到期的 `queued` 执行；续租、进度和终态更新必须匹配 `running + lease_token + row_version`。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=CreationPersistenceContractTest,AiTaskMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 按相邻 generator 模板实现贫血 Entity、`BaseMapperPlus<Entity,Entity>` Mapper 和必要 XML 条件更新；不在 Entity 放事务、查询或外部调用。
- [ ] 运行绿灯，并执行 `rg -n "tenantId|workspaceId|Tenant|Workspace"` 只针对本任务新增生产文件；预期无命中。
- [ ] 精确提交 `feat: 建立时间轴持久化对象`。

### 任务 10：实现通用创作素材 Service 与用户端资源

**风险：** 红色。上传、旧输出登记、成品登记和删除保护共享同一事实源。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationAssetServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/CreationAssetServiceImplTest.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/UploadCreationAssetBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/CreationAssetQueryBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/CreationAssetVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/controller/CreationAssetController.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/controller/CreationAssetControllerTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationAssetServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/CreationAssetServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/UploadCreationAssetBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/CreationAssetQueryBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/CreationAssetVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/controller/CreationAssetController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/controller/CreationAssetControllerTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写 Service 红灯测试：上传只接受 image／video／audio 白名单、用途白名单和服务端 SHA-256 摘要；客户端传入 owner、路径、对象键或 URL 无对应字段。
- [ ] 写红灯测试：同用户同幂等键同摘要回放原素材，同键异摘要冲突；第 5 步输出通过 `(owner, digital_human_output, sourceRefId)` 幂等登记。
- [ ] 写红灯测试：合成先登记确定性 `pending` 素材，成功条件更新为 `ready`，失败标记并保留补偿依据；重试复用同一素材和对象键。
- [ ] 成品对象键只由服务端按 `timeline-renders/{ownerUserId}/{taskId}/{inputVersionId}/{outputConfigDigest}.mp4` 构造，其中摘要来自规范输出配置；所有片段均由已校验十进制 ID 或小写十六进制摘要组成。该键永不进入 HTTP／JSON／日志。
- [ ] 写删除保护红灯测试，至少覆盖：草稿引用、不可变版本引用、项目基础视频、主音频、当前成品、根任务成品、执行成品；任一存在时删除返回稳定占用错误。
- [ ] 写 Controller 红灯测试：`@SaCheckPermission(type = "app")` 权限、当前 `AppLoginHelper` 主体、分页、multipart 大小／类型、Range 内容、权限与跨账号安全语义。素材列表默认 `pageSize=20`，只允许 `pageNum>=1`、`pageSize=1..100`；首版不绑定客户端 `orderByColumn/isAsc`，服务端固定 `create_time DESC, asset_id DESC`，越界参数拒绝，越过末页必须保留真实 `total` 且 `rows=[]`。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -Dtest=CreationAssetServiceImplTest,CreationAssetControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 最小实现 C0 `ICreationAssetService`；复用现有存储、媒体校验和受控读取能力，但不把旧 `IAssetService` 扩成第二套通用事实源。
- [ ] Controller 只做认证、权限、BO 转换；普通 JSON 使用 `R<T>`，分页使用 `R<PageResult<T>>`。`GET /api/studio/creation-assets/{assetId}/content` 是唯一包装例外，返回 `StreamingResponseBody`、`Resource` 或项目等价受控流，禁止整体读入 `byte[]`：无 Range 为 200；`start-end`、`start-`、`-suffix` 单 Range 为 206；格式错误、多 Range、零 suffix、`start>end`、`start>=total` 为真实 HTTP 416 并返回 `Content-Range: bytes */total`。成功响应设置 `Content-Type`、`Accept-Ranges: bytes`、精确 `Content-Length`／`Content-Range`、安全 inline 文件名和 `Cache-Control: no-store`；认证、权限、归属和不存在仍返回 JSON `R` 错误。Service 负责 Range 解析、归属和流生命周期，测试包含客户端中止关闭流与未发生全量缓冲。归属、删除保护和幂等全部在 Service。
- [ ] 绿灯后精确提交 `feat: 增加通用创作素材接口`。

### 任务 11：实现第 5 步来源桥接与项目初始化

**风险：** 红色。旧数据仍按旧规则校验，新项目只记录当前 app 用户归属。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationProjectService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImplTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationProjectService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImplTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 先写来源桥接红灯测试：只接受成功的视频任务；必须重新校验旧任务属于当前 app 用户和旧兼容范围，输出媒体真实存在且可读，脚本文本快照来自服务端任务。
- [ ] 写项目创建红灯测试：同用户同幂等键同摘要返回原项目；同键异请求冲突；并发唯一键冲突整笔回滚后回读赢家。
- [ ] 写事务红灯测试：一次创建项目、稳定基础视频素材、可选主音频素材和唯一初始草稿；任何一步失败不留下半项目。
- [ ] 写反向测试：请求无法提供用户编号、脚本编号、脚本版本、租户、工作区、路径或媒体 URL；另一个 app 用户即使猜中来源任务编号也不能创建项目。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=CreationProjectServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] `CreationProjectServiceImpl` 只调用 C0 `ICreationAssetService` 取得 `DigitalHumanCreationSourceDTO`；`CreationAssetServiceImpl` 内部使用现有 `IDigitalHumanGenerationService.getJob/getOutputMedia` 和旧会话范围完成兼容校验，不修改旧数字人公共接口，也不把旧 owner DTO 暴露给新 HTTP 请求。
- [ ] 在短事务内完成项目和草稿创建；初始化时间轴使用 C0 `timeline-1` Schema、基础视频时长／画布和冻结脚本文本。
- [ ] 绿灯后精确提交 `feat: 打通数字人视频创作项目`。

### 任务 12：实现时间轴校验、字幕规范化和草稿保存

**风险：** 红色。后端返回的规范时间轴是持久化事实，前端不能自行覆盖。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineDocumentService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineDocumentServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ISubtitleNormalizationService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/SubtitleNormalizationServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ISubtitleFontMeasurementService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/SubtitleFontMeasurementServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineDraftService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineDraftServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineDocumentServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/SubtitleNormalizationServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineDraftServiceImplTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineDocumentService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineDocumentServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ISubtitleNormalizationService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/SubtitleNormalizationServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ISubtitleFontMeasurementService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/SubtitleFontMeasurementServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineDraftService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineDraftServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineDocumentServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/SubtitleNormalizationServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineDraftServiceImplTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 从 `subtitle-normalization.example.json` 写表驱动红灯测试：Unicode NFC、Unicode 码点偏移、标点／换行／空白删除、小数点和 emoji、不能少字、确定性拆分 ID、连续时间段。
- [ ] 写字体测量红灯测试：仅允许配置白名单字体；`SubtitleFontMeasurementServiceImpl` 调用 C0 媒体接口的登记字体测量能力，按最终画布安全区计算，必要时逐级减字号或确定性拆分；没有合法结果时拒绝保存，不截断文字，也不读取任意字体路径。
- [ ] 写文档红灯测试：七类元素字段、轨道顺序、元素唯一 ID、时间边界；图片 `fitMode`、裁剪与淡入淡出；画中画四角／边距、`sourceStartMs`、`loopWhenOverflow=true`、`audioEnabled=false`；背景音乐裁剪／循环／自动 ducking；六种花字模板、素材类型和归属。
- [ ] 文档 Service 的验证顺序固定为：先按 UTF-8 字节数与最大深度拒绝超限输入，再用 C0 的 NetworkNT validator 对 `timeline-1.schema.json` 做结构和未知字段校验，再映射强类型 DTO，最后执行元素总数、全局唯一 ID、轨道顺序、素材归属、跨字段时间与字幕／字体语义校验。任何阶段失败都不能写草稿或任务。
- [ ] 写保存红灯测试：`expectedRevision` 不匹配返回修订冲突；同键同摘要读取写回执；同键异摘要返回 `TIMELINE_IDEMPOTENCY_CONFLICT`。
- [ ] 写被超越回放测试：旧回执返回 `replayed=true`、`superseded=true`、操作修订／摘要和当前修订，不携带旧时间轴；前端必须重新 GET。
- [ ] 写事务红灯测试：更新草稿、重建草稿引用投影、插入 `draft_save` 回执一次提交；素材校验或回执冲突时整笔回滚。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=TimelineDocumentServiceImplTest,SubtitleNormalizationServiceImplTest,TimelineDraftServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现纯校验／规范化与短事务保存；通过 `JsonUtils` 生成规范哈希，禁止依赖 JSON 字段顺序不稳定的默认 `toString()`。
- [ ] 绿灯后精确提交 `feat: 实现时间轴草稿保存`。

### 任务 13：实现不可变版本、恢复和素材引用索引

**风险：** 红色。历史版本只追加，恢复产生新事实，不能改写旧行。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineVersionService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineVersionServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineConsistencyService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineConsistencyServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineVersionServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineConsistencyServiceImplTest.java`
- 新建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelinePersistenceIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineVersionService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineVersionServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineConsistencyService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/impl/TimelineConsistencyServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineVersionServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/service/impl/TimelineConsistencyServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelinePersistenceIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写手动版本红灯测试：校验预期修订，生成递增 `versionNo`，以 `version_reason=manual_save` 复制规范时间轴和不可变版本引用，写 `operation_type=manual_version` 回执；并发冲突回读赢家。
- [ ] 写冲突副本红灯测试：传入冲突时冻结的完整本地时间轴与 `baseRevision`，重新执行所有保存校验，只追加 `conflict_copy` 不可变版本、版本引用和 `conflict_version` 回执；当前草稿 JSON、revision、引用和项目状态逐字节不变。同键同摘要回放原版本，同键异摘要稳定冲突。
- [ ] 写恢复红灯测试：来源版本必须属于同一当前用户和项目；更新当前草稿、修订、草稿引用，并新增 `restored` 历史版本与回执；旧版本行和引用保持字节不变。
- [ ] 写幂等响应丢失、同键异请求、被后续编辑超越、跨账号版本猜测的反向测试。
- [ ] 写一致性巡检红灯测试：孤立草稿／版本、多个有效草稿、JSON 与引用漂移、失效素材、任务缺版本、成功任务无成品、项目成品漂移、过期租约、超时 `pending` 成品；只报告编号和安全摘要，不自动修复。
- [ ] 使用本机专用测试库运行 `TimelinePersistenceIT`，证明无物理外键时事务、唯一键和反查索引仍保持约束。
- [ ] 运行单元红灯／绿灯和：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=TimelinePersistenceIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

- [ ] 精确提交 `feat: 增加时间轴不可变版本`。

### 任务 14：实现统一根任务、执行、尝试与租约状态机

**风险：** 红色。任何迟到 Worker 都不能改写终态或制造第二条根任务。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskTransactionService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskTransactionServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IFreeAiTaskQuotaPolicyService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/FreeAiTaskQuotaPolicyServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/IRenderOutputLifecycleService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/RenderOutputLifecycleServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/service/impl/AiTaskTransactionServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/RenderOutputLifecycleServiceImplTest.java`
- 新建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskRuntimeIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskTransactionService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskTransactionServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IFreeAiTaskQuotaPolicyService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/FreeAiTaskQuotaPolicyServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/IRenderOutputLifecycleService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/RenderOutputLifecycleServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/service/impl/AiTaskTransactionServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/RenderOutputLifecycleServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskRuntimeIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 分开写两组创建红灯测试：三类建议任务只冻结 `draftRevision`、输入文案范围和输入摘要，创建根任务与首个执行，`inputVersionId=null`；只有 `timeline_render` 才在同一事务内按“根任务幂等占位 → 校验当前修订 → 创建 `render_input` 不可变版本及引用 → 回填 `inputVersionId` → 创建首个执行”的顺序提交。两类提交事务都不创建 attempt。
- [ ] 写唯一键竞态红灯测试：失败事务不留下多余版本／执行／尝试，回读赢家；同键异摘要稳定冲突。
- [ ] 写免费策略红灯测试：四类任务均冻结稳定 `quotaPolicyVersion` 和 `estimatedUsage=0`，不创建余额、冻结、扣减或退款流水。
- [ ] 写领取红灯测试：短事务 CAS 将到期 `queued` 推进到 `running`，生成不可预测租约令牌；外部调用前才插入 `running` attempt。
- [ ] 按 C1 调度签名实现 `dispatchNext(workerId, perUserConcurrencyLimit, systemConcurrencyLimit)`：两个上限只允许 `1..100` 且单用户不得大于系统上限。领取短事务使用数据库时间统计未过期租约的 `running` 执行，并原子保证集群系统占用和候选 owner 占用均未达到上限；达到上限返回 `none`，不领取后回退。单元测试和 `AiTaskRuntimeIT` 覆盖同用户、不同用户及两个并发 Scheduler，证明不会突破任一上限。
- [ ] 写续租／进度／完成／失败／取消红灯测试：同时匹配执行号、`running`、租约令牌和版本；根任务只接受当前 `activeExecutionId` 推进。
- [ ] 写建议结果持久化红灯测试：三个 AI Service 返回值先映射到匹配的 `AiTaskResultPayloadDTO`，执行完成 CAS 在同一短事务中写入规范 `result_payload_json`、payload schema 和 success；超限、类型不匹配、未知字段或序列化失败整笔失败且不产生 success。详情读取强类型 payload，列表摘要不读取／返回大结果；重启后仍可取得同一建议。
- [ ] 进度持久化每个执行至多每秒一次，阶段变化和终态不节流；百分比只能单调增加且位于 0..100，重启恢复沿用数据库值，不从日志或 FFmpeg 文本反推已完成终态。
- [ ] 写恢复红灯测试：租约过期复用同一执行；再次真实调用前增加 attempt；只有白名单内部重试才增加 `executionNo`；用户主动重试创建新根任务和新幂等键。
- [ ] 首版四种任务的自动内部执行重试白名单固定为空，因此正常业务只存在 `executionNo=1`；租约恢复只能在同一执行增加 attempt，用户主动重试只能创建新根任务。以后允许 `executionNo>1` 必须先改公共任务契约。
- [ ] 写终态反向测试：迟到结果、重复回调、过期租约、取消后成功回调均影响 0 行并停止副作用。
- [ ] `AiTaskServiceImpl.dispatchNext` 负责在事务外调用 C0 媒体／AI 接口；`AiTaskTransactionServiceImpl` 通过 `IAiTaskTransactionService` 提供领取、attempt、续租、进度和终态的短事务，并在每次写事务内用根任务冻结 `actorId` 打开 `AuditFillContext`。过期恢复只重新排队原执行，不创建第二根任务。
- [ ] `RenderOutputLifecycleServiceImpl` 固定执行：短事务登记确定性 `pending` 素材 → 事务外消费媒体输出句柄并上传对象 → 短事务以有效租约/CAS 同时推进素材 `ready`、execution success、root success、项目最新成品。上传后最终事务失败时保留 pending 事实，补偿扫描只处理超时且无引用对象；同键同 SHA 复用，不同 SHA 拒绝覆盖。
- [ ] 运行单元测试，再用 `AiTaskRuntimeIT` 对真实 MySQL 执行并发提交、CAS、领取、输出终态事务、补偿与恢复：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=AiTaskRuntimeIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

- [ ] 精确提交 `feat: 实现统一任务租约状态机`。

### 任务 15：实现项目、时间轴、任务 HTTP 与第 7 步成品读取

**风险：** 红色。Controller 不得包含事务、状态机或直接 Mapper 调用。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/common/domain/bo/StrictAppRequestBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/CreateCreationProjectBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/UpdateCreationProjectBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/SaveTimelineDraftBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineVersionBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/RestoreTimelineVersionBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineConflictCopyBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateImagePromptTaskBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateFancyTextSuggestionTaskBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateSubtitleAlignmentTaskBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineRenderTaskBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/CreationProjectVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/LatestCreationOutputVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineDraftVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineWriteResultVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineVersionVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/controller/CreationProjectController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineDraftController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineVersionController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineTaskController.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/bo/AiTaskQueryBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/bo/RetryAiTaskBo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskListItemVo.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/controller/CreationProjectControllerTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineDraftControllerTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineVersionControllerTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineTaskControllerTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/controller/AiTaskControllerTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/common/domain/bo/StrictAppRequestBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/CreateCreationProjectBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/bo/UpdateCreationProjectBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/SaveTimelineDraftBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineVersionBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/RestoreTimelineVersionBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineConflictCopyBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateImagePromptTaskBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateFancyTextSuggestionTaskBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateSubtitleAlignmentTaskBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineRenderTaskBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/CreationProjectVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/LatestCreationOutputVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineDraftVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineWriteResultVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/vo/TimelineVersionVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/controller/CreationProjectController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineDraftController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineVersionController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/controller/TimelineTaskController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/bo/AiTaskQueryBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/bo/RetryAiTaskBo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskListItemVo.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/controller/CreationProjectControllerTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineDraftControllerTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineVersionControllerTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/timeline/controller/TimelineTaskControllerTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/controller/AiTaskControllerTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写 Controller 契约红灯测试，覆盖规格第 9.1 节全部路径以及 C0 新增的 `POST /api/studio/creation-projects/{projectId}/timeline-versions/conflict-copies`、`R<T>`／`PageResult<T>`、十进制字符串 ID 和 app 权限标识。
- [ ] 写参数红灯测试：所有新增 JSON BO 和 multipart `metadata` 采用字段 allowlist；BO 不含 owner、tenant、workspace、路径、URL、对象键、FFmpeg 参数、任务状态或输出编号，传入 `ownerUserId`、`ownerId`、`tenantId`、`workspaceId`、`path`、`storageKey`、`url` 或任意未知字段一律拒绝，绝不忽略。每个新增 BO 通过统一基类的 `@JsonAnySetter` fail-closed，multipart metadata 使用同一专用 strict `ObjectReader`；每个端点断言稳定 4xx 业务错误且 Service 零调用。不得修改全局 Jackson 策略影响旧接口，也不得把未知字段收进 Map 后继续执行业务。
- [ ] 写项目／草稿／版本／AI／合成／成品的跨账号、缺权限、归档项目、素材失效、修订冲突、幂等冲突和安全错误映射测试。
- [ ] 写项目状态矩阵测试：`editing`／`ready` 允许提交合成，`rendering` 拒绝第二个合成但允许继续编辑草稿，`archived` 拒绝新写操作；合成失败且无旧成品回 `editing`，已有旧成品保持 `ready`，成功才条件更新最新成品。
- [ ] 写统一任务查询、详情、取消、主动重试测试；未知合法任务类型仍返回通用展示字段。素材、版本、任务分页都默认 `20`、硬上限 `100`、`pageNum>=1`，首版不开放客户端排序；服务端分别固定 `create_time DESC,asset_id DESC`、`version_no DESC,timeline_version_id DESC`、`created_at DESC,task_id DESC`。不得直接把请求排序字符串交给 `PageQuery.build()`；空页用 `PageResult.build(rows,total)` 返回 `rows=[]` 和真实 `total`。
- [ ] 任务详情对三类已知建议 success 返回强类型 `resultPayload`，列表项永远不返回该大字段；渲染详情只返回 `resultAssetId`。payload 与 taskType 不匹配或持久化 JSON 无法通过 C0 reader 时返回一致性错误，不把原始 JSON 透传；未知未来任务仍返回通用元数据且 `resultPayload=null`。
- [ ] 固定异常映射：新增 `TimelineErrorCodes` 常量类保存 `46601..46612`；Service 只按项目真实构造顺序 `new ServiceException(safeMessage, TimelineErrorCodes.XXX)` 抛出，测试断言 `getCode()` 防止参数颠倒。当前全局处理器把业务码放入 `R.code`，不要假定自动转成 HTTP 404/409；权限不足沿用 Sa-Token `R.code=403`，未登录沿用用户端安全处理器的真实 HTTP 401。跨账号与不存在使用相同业务码，避免泄漏存在性；Controller 不捕获后改码，前端不按中文 `msg` 分支。
- [ ] 固定端点级日志／防重矩阵：所有 GET 无 `@Log`、无 `@RepeatSubmit`；所有 mutation 均有固定标题的安全 `@Log`，并设置 `isSaveRequestData=false,isSaveResponseData=false`；上传与 content 不记录请求、响应或文件。所有写端点都禁止 `@RepeatSubmit`：项目／素材／任务创建用 owner+idempotencyKey+摘要唯一键，草稿／版本／恢复用 write receipt，项目更新／取消／删除用 CAS 或条件幂等；5 秒 Redis 防重不能替代持久幂等，也不能阻断网络结果未知后的合法重放。反射测试断言 mutation 的安全 `@Log` 与 `@RepeatSubmit` 缺失，日志不得包含文件内容、Range、脚本全文、时间轴 JSON、内部对象键或媒体句柄。
- [ ] 实现 Controller 仅调用 Service，所有主体从 `AppLoginHelper` 取得；不可从请求反序列化归属。
- [ ] 合成提交 Service 在一个事务中创建根任务、不可变输入版本、版本引用和首个执行；事务提交后只唤醒 Worker。
- [ ] `outputs/latest` 只在成品素材真实 `ready` 且归属当前用户时返回预览／下载能力；成功任务缺素材时返回一致性错误。
- [ ] 运行：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -Dtest=CreationProjectControllerTest,TimelineDraftControllerTest,TimelineVersionControllerTest,TimelineTaskControllerTest,AiTaskControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 精确提交 `feat: 暴露时间轴与统一任务接口`。

### 任务 16：后端专项集成、反向验收与 PR

**风险：** 红色。后端 PR 只声明业务编排和假媒体边界通过，不声明真实媒体已装配。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineIsolationIT.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/CreationTimelineHttpContractTest.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/CreationControllerAssemblyTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineIsolationIT.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/CreationTimelineHttpContractTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/creation/CreationControllerAssemblyTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 使用测试作用域假 `ITimelineMediaRenderService`／`ITimelineAiSuggestionService`，不在生产代码注册假成功 Bean。
- [ ] 集成测试覆盖两个 app 用户的项目、素材、草稿、版本、任务、成品隔离，同号运营用户不能污染 `create_by/update_by`。
- [ ] 覆盖保存／版本／恢复响应丢失重放、并发修订冲突、任务唯一键整笔回滚、租约恢复、取消和终态不可逆。
- [ ] 覆盖素材全部直接引用删除保护以及巡检只报告不修改。
- [ ] `CreationControllerAssemblyTest` 验证全部 creation／timeline／task Controller 只存在于 `ai-video-user` 且使用 app 权限；完整双启动路由由唯一集成负责人在任务 37 更新 fixture 后验证，后端分支不修改 package-private fixture。
- [ ] 运行 core、user 相关测试和打包：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false test
.\mvnw.cmd -pl ai-video-user-api -am -DskipTests package
```

- [ ] 运行受控 IT、`scripts/validate-development-standards.ps1` 和 `git diff --check`。
- [ ] 发起只读审查，重点检查归属、事务、幂等、CAS、权限、素材删除保护和 RuoYi 分层；修复必须修复项后只做一次定向复核。
- [ ] 推送 `codex/step6-backend`，创建面向 `codex/step6-integration` 的 PR，附命令、结果和仍依赖媒体实现的边界。

## 6. 本角色最小任务卡

### 6.2 后端设备任务卡

- **单一目标：** 完成任务 8 至任务 16，交付 owner-only 项目／素材／草稿／版本／任务业务编排和用户端 HTTP。
- **禁止事项：** 不改 C0、前端、infra 媒体实现、媒体配置或旧数据归属结构；不注册生产假媒体 Bean。
- **权威输入：** `C0_SHA`、本计划第 2 节、RuoYi skill、相邻 UserScript／Voice／DigitalHuman 实现。
- **独占路径：** core 的 creation／timeline／task 实现，user 的 creation／timeline／task HTTP，四个 app MyBatis 审计文件及测试。
- **交付证据：** 模块测试、本机 MySQL／Redis IT、双启动路由、两个账号隔离、幂等／事务／CAS／租约／删除保护证据和 PR。
- **停止条件：** 需要修改 C0 或媒体签名时提交契约变更卡并暂停，不在自己的分支修公共接口。
