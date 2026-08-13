# 用户端文案库与手工录入设计

> 状态：已批准设计，待用户书面审查规格文件
> 日期：2026-08-05
> 模块：用户端“文案”菜单／我的文案库
> 适用端：`ai-video-ui/ai-video-webapp`、`ai-video-api/ai-video-user-api`
> 需求优先级：速度优先，但不豁免认证、账号归属、幂等、数据安全和验证门禁

## 1. 背景与现状

用户端工作台的“文案”菜单目前由 `digital-human-studio/model.ts` 中的静态 `SCRIPTS` 数据驱动，列表、详情、编辑、版本和复制操作没有接入真实用户端接口。现有冻结规格定义了生成文案、文案版本、确认和“我的文案库”，但文案主体及版本默认与创作草稿、问卷分支和生成上下文绑定，不能直接表达用户从文案库独立录入标题和正文的场景。

本次需求经过确认后取消文件上传，改为：用户点击“新建文案”，在弹窗中输入标题和正文，保存后进入当前用户的“我的文案”列表。手工录入文案暂不绑定创作草稿、不创建文案确认记录，也不能直接用于声音克隆、数字人或作品生成。

## 2. 决策记录

### 2.1 已确认决策

- 入口属于用户端“文案”菜单，不新增管理端入口。
- 新建界面使用弹窗，不使用抽屉或列表内联表单。
- 用户直接输入标题和正文，不上传文件。
- 保存后只进入“我的文案”，支持库内列表、详情、复制、编辑新版本和删除。
- 暂不支持组织共享、工作区选择或由客户端提交租户、所有者、工作区信息。
- 暂不创建文案确认记录，不允许作为声音、数字人或作品的下游输入。

### 2.2 方案对比

| 方案 | 说明 | 结论 |
| --- | --- | --- |
| 弹窗录入并通过 JSON 一次保存 | 前端填写标题和正文，后端原子创建文案主体和 v1 | 采用；实现最短且没有临时文件状态。 |
| 右侧抽屉录入 | 信息容量更大，但需要额外抽屉状态与响应式处理 | 不采用；当前字段只有两个。 |
| 列表顶部内联录入 | 少一次弹窗，但会挤压列表并增加展开、取消和分页切换状态 | 不采用。 |

### 2.3 明确不做

- 文件上传、解析、OSS 保存和导入批次。
- AI 生成、文案优化、问卷、证据搜索、任务、通知和额度处理。
- 组织文案、成员共享、对象授权和工作区切换。
- 文案确认及声音、数字人、作品下游引用。
- 用户端文案模板接口；当前静态模板卡片不能继续伪装成真实数据。
- 管理端文案管理和跨用户操作。

## 3. 风险等级与任务边界

### 3.1 风险等级

风险等级为红色，触发依据如下：

- 新增公共用户端接口并调整文案数据模型。
- 涉及创作端认证、权限和用户账号数据隔离。
- 涉及文案创建、版本不可变、幂等与逻辑删除。
- 需要防止通过直接调用接口读取、修改或删除其他用户文案。

因此实现与交付必须执行一次规格／契约审查和一次身份、权限、数据归属专项审查；修复后只定向复核原发现和直接受影响测试。

### 3.2 单一交付目标

让已登录创作端用户在“文案”页面通过标题和正文创建自己的文案，并通过真实用户端接口完成列表、详情、复制、编辑新版本和删除。

### 3.3 允许影响范围

- 用户端 Web 的文案页面、模块 Service、类型、adapter、测试和国际化文案。
- `ai-video-user` 的用户端文案 Controller、BO、VO 与装配。
- `ai-video-core` 的文案 Entity、DTO、Mapper、Service、迁移和测试。
- 文案相关公共 API、领域模型和架构文档中与本规格直接相关的最小契约。

### 3.4 不允许影响范围

- 登录认证流程本身、运营端身份和管理端路由。
- P1/P2/P3 生成链路、任务、额度、知识路由和提供商调用。
- 声音、人物形象、数字人和作品业务。
- 当前工作区中与人物形象、资产、声音或其他功能有关的未提交修改。

## 4. 用户体验设计

### 4.1 页面入口与布局

- 保持用户端路由 `/studio` 和左侧“文案”菜单不变。
- 页面标题仍为“文案”，副标题为“管理口播文案版本及其关联资产”。
- 工具栏保留搜索框，主操作改为“新建文案”。
- 列表只展示当前登录用户的真实文案，不合并静态 `SCRIPTS` 或静态模板卡片。
- “复制”只使用浏览器剪贴板，不创建后端接口或审计版本。

### 4.2 新建文案弹窗

建议使用 Ant Design `Modal`、`Form`、`Input` 和 `Input.TextArea`，具体 API 在实现前通过项目 Ant Design skill 和官方文档核验。

| 字段 | 类型 | 规则 | 默认值 |
| --- | --- | --- | --- |
| `displayTitle` | 单行文本 | 必填；去除首尾空白后 1～100 个 Unicode code point | 空 |
| `scriptText` | 多行文本 | 必填；统一换行后去除首尾空白，1～20,000 个 Unicode code point | 空 |

交互规则：

- “保存”在表单不合法或提交中时不可用。
- 提交期间显示 loading 并防止重复点击。
- 只有服务端确认成功后关闭弹窗并清空表单。
- 保存失败时弹窗保持打开，标题和正文不得丢失。
- 用户关闭存在未保存内容的弹窗时给出放弃确认。
- 成功后刷新第一页或按当前查询条件重新查询，并使新文案可见。

### 4.3 列表与详情

列表项至少展示：

- 标题。
- 当前版本号与版本数量。
- 正文字数和预计时长。
- 最近更新时间。
- 正文摘要。
- 查看详情、编辑、复制和删除操作。

详情展示文案主体信息、当前版本全文和按时间倒序排列的不可变版本历史。编辑从当前版本创建新版本，不覆盖旧版本；保存成功后新版本成为当前版本。

### 4.4 页面状态

| 状态 | 行为 |
| --- | --- |
| 加载 | 显示列表骨架或稳定 loading，不闪现静态假数据。 |
| 空数据 | 显示“还没有文案”和“新建文案”入口。 |
| 搜索无结果 | 显示“没有匹配的文案”，保留清除搜索操作。 |
| 接口失败 | 显示可重试错误态，保留当前搜索条件。 |
| 401 | 由集中请求层清理登录态并只跳转一次。 |
| 403 | 显示权限不足，不跳转登录页。 |
| 提交中 | 弹窗保持打开，按钮 loading 且防重复提交。 |
| 提交失败 | 保留字段，显示统一错误；字段错误映射到对应表单项。 |
| 删除中 | 仅禁用当前文案的危险操作。 |
| 删除失败 | 保留列表项；存在引用时展示稳定业务提示。 |

## 5. 用户端 API 契约

所有接口只装配到 `ai-video-user-api`，统一使用 `/api/studio/scripts`、创作端 `app` 令牌和 RuoYi `R<T>`。请求不得包含 `tenantId`、`ownerType`、`ownerId`、`workspaceId`、`appUserId`、确认状态或下游引用字段。业务 ID 以 JSON 十进制字符串返回。

### 5.1 创建文案

`POST /api/studio/scripts`

权限：`aivideo:script:edit`，权限注解必须显式使用 `app` 类型。

请求：

```json
{
  "displayTitle": "夏季新品口播",
  "scriptText": "这里是用户输入的文案正文。",
  "idempotencyKey": "7cf3f2a3-e04b-4b2e-a839-c9b43877e625"
}
```

| 字段 | 规则 |
| --- | --- |
| `displayTitle` | 必填；规范化后 1～100 个 Unicode code point。 |
| `scriptText` | 必填；规范化后 1～20,000 个 Unicode code point。 |
| `idempotencyKey` | 必填；一次用户保存意图使用一个键，网络重试和按钮防重期间复用。 |

成功响应 `data`：

```json
{
  "scriptId": "1765400000000000001",
  "currentVersionId": "1765400000000000002",
  "scriptRevision": "1",
  "versionNo": 1,
  "displayTitle": "夏季新品口播",
  "effectiveCharacterCount": 15,
  "estimatedDurationSeconds": 5,
  "createdAt": "2026-08-05 10:00:00",
  "reused": false
}
```

相同登录用户、相同幂等键和相同规范化请求返回原结果并令 `reused=true`；同键不同内容返回既有 `46116 IDEMPOTENCY_KEY_CONFLICT`。

### 5.2 分页查询当前用户文案

`GET /api/studio/scripts`

权限：`aivideo:script:query`，显式使用 `app` 类型。

查询参数：

- `pageNum`：从 1 开始。
- `pageSize`：默认 20，最大 100。
- `keyword`：可选，匹配标题；上限 100 个字符。
- `orderByColumn`：只允许前端列键 `updatedAt` 或 `displayTitle`。
- `isAsc`：只允许 `asc` 或 `desc`，默认按 `updatedAt desc, scriptId desc`。

返回 `R<PageResult<UserScriptListVo>>`。空页必须返回 `rows=[]`。列表项字段精确为：

- `scriptId`
- `displayTitle`
- `currentVersionId`
- `versionNo`
- `versionCount`
- `sourceType`，本期只返回 `manual_input` 或 `manual_edit`
- `effectiveCharacterCount`
- `estimatedDurationSeconds`
- `preview`
- `createdAt`
- `updatedAt`

### 5.3 查询详情和版本历史

`GET /api/studio/scripts/{scriptId}`

权限：`aivideo:script:query`，显式使用 `app` 类型；只允许读取当前登录用户自己的未删除文案。

响应包含：

- 文案主体：`scriptId`、`displayTitle`、`scriptRevision`、`currentVersionId`、`createdAt`、`updatedAt`。
- 当前版本：`versionId`、`versionNo`、`sourceType`、`scriptText`、`effectiveCharacterCount`、`estimatedDurationSeconds`、`createdAt`。
- `versions`：不可变版本摘要数组，按 `versionNo desc` 返回；每项只包含 `versionId`、`parentVersionId`、`versionNo`、`sourceType`、`effectiveCharacterCount`、`estimatedDurationSeconds`、`preview` 和 `createdAt`，不重复返回完整正文。

### 5.4 查询一个历史版本

`GET /api/studio/scripts/{scriptId}/versions/{versionId}`

权限：`aivideo:script:query`，显式使用 `app` 类型。`versionId` 必须属于路径中的文案，且文案属于当前登录用户。

响应精确包含 `scriptId`、`versionId`、`parentVersionId`、`versionNo`、`sourceType`、`scriptText`、`effectiveCharacterCount`、`estimatedDurationSeconds` 和 `createdAt`。

### 5.5 编辑并创建新版本

`POST /api/studio/scripts/{scriptId}/versions`

权限：`aivideo:script:edit`，显式使用 `app` 类型。

请求字段：

| 字段 | 规则 |
| --- | --- |
| `parentVersionId` | 必填且必须等于服务端当前版本。 |
| `expectedScriptRevision` | 必填且必须等于当前文案修订号。 |
| `displayTitle` | 必填；更新文案主体展示标题。 |
| `scriptText` | 必填；创建新的不可变版本。 |
| `idempotencyKey` | 必填；同一编辑保存意图复用。 |

成功时在同一事务插入 `manual_edit` 版本、更新 `currentVersionId`、标题和 `scriptRevision`。父版本与修订号冲突返回新增稳定错误 `46136 SCRIPT_REVISION_CONFLICT`，前端刷新详情并保留用户编辑内容，不自动覆盖。

### 5.6 删除文案

`DELETE /api/studio/scripts/{scriptId}`

权限：`aivideo:script:remove`，显式使用 `app` 类型。

- 只逻辑删除当前登录用户自己的文案主体，不删除不可变版本。
- 删除前检查声音、数字人、作品和其他后续引用；存在引用时复用 `46118 SCRIPT_HAS_REFERENCES`。
- 重复删除返回成功或相同的不可见结果，不泄露其他用户资源是否存在。

## 6. 数据模型设计

### 6.1 `av_user_script`

复用 P3 文案主体，不创建平行的“手工文案表”。为独立手工录入补充或确认以下语义：

- `draft_id` 对 `manual_input` 文案允许为空；非空时仍遵守原草稿唯一性规则。
- 新增 `current_version_id`，表示库内当前可编辑版本，与 `current_confirmed_version_id` 分离；手工文案的确认版本为空。
- 新增 `create_idempotency_key` 和 `create_request_hash`，唯一范围为当前用户事实归属加幂等键。
- `owner_type` 本期固定为 `personal`，`owner_id` 固定为当前 `app_user` 编号。
- `tenant_id` 如属于现有表的技术必填列，只能由后端从当前创作用户的个人隔离上下文填充；不进入用户请求、页面展示或本期工作区业务。
- `script_revision` 从 1 开始，每次当前版本或标题变化加 1。
- 逻辑删除继续使用既有字段和索引。

### 6.2 `av_script_version`

- `source_type` 增加 `manual_input`，后续人工编辑使用既有或统一为 `manual_edit`。
- 手工文案的 `draft_id`、分支修订、生成上下文修订、生成输入摘要、知识快照、来源任务和候选代码均为空。
- `parent_version_id`：v1 为空，后续版本指向保存时的当前版本。
- 每个版本保存单调递增的 `version_no`；唯一范围为 `tenant_id + script_id + version_no`。
- `manual_idempotency_key` 继续防止同一编辑意图生成两个版本。
- 手工文案没有三个 AI 发布标题，`publish_titles_json` 固定为空数组，`selected_title_index` 为空；原生成／确认文案仍保持恰好三个标题和 0～2 选中序号的约束。数据库检查约束必须按 `source_type` 区分，不能削弱生成文案规则。
- `target_duration_seconds` 为空；`effective_chars_per_minute`、`estimated_duration_seconds` 和规则版本使用当前已发布的统一文案时长配置计算并冻结，计算公式为向上取整 `effectiveCharacterCount × 60 ÷ effectiveCharsPerMinute`，不校验目标时长容差。
- Entity 延续已批准的 P3 `BaseEntity` 例外：显式 typed actor、所有者和时间字段，保持贫血 Entity。

### 6.3 `av_script_confirmation`

手工录入和手工编辑不会写入此表，`current_confirmed_version_id` 保持为空。任何下游要求“已确认版本”的 Service 必须继续拒绝该文案，不能把 `current_version_id` 当成确认版本。

## 7. 后端分层与职责

后端必须采用 RuoYi 贫血 Entity 加 Service 编排，不引入 DDD、Clean Architecture、Hexagonal Architecture，也不新增 `application`、`port`、`adapter`、`command` 或 `model` 平行业务层。

### 7.1 `ai-video-core`

- `domain`：`AvUserScript`、`AvScriptVersion`。
- `dto`：用户端适配层需要的稳定 `UserScript*DTO` 数据契约。
- `mapper`：继承 `BaseMapperPlus`，所有查询在 SQL 中带当前用户归属和逻辑删除条件。
- `service`：`IUserScriptService`。
- `service.impl`：`UserScriptServiceImpl`，负责规范化、权限后的归属复核、幂等、版本创建、修订条件更新、引用检查和事务。

### 7.2 `ai-video-user`

- `domain.bo`：创建、查询和创建版本 BO。
- `domain.vo`：列表、详情、版本和创建结果 VO。
- `controller`：用户端 `UserScriptController`，只负责参数、Validation、`app` 登录主体、权限、防重、日志和 `R<T>` 包装。
- 用户端模块不复制核心 Entity、Mapper 或业务 Service。

### 7.3 日志与审计

- 创建、编辑和删除写操作记录业务日志，但不得记录完整文案正文。
- 日志只记录文案 ID、请求 ID、动作、结果、正文摘要和长度。
- 审计主体固定为 `actor_type=app_user` 和当前用户 ID，禁止与同号 `sys_user` 混用。

## 8. 身份、权限与账号归属

- 所有接口只接受 `StpLogic("app")` 创作端令牌，Controller 权限注解必须显式 `type=app`。
- 创建、查询、详情、编辑和删除都从 `AppLoginHelper` 解析唯一当前用户。
- 前端和 BO 禁止包含归属字段；反序列化遇到未知的 `tenantId`、`ownerId`、`workspaceId` 等字段必须拒绝。
- 本期有效数据范围等价于 `owner_type=personal AND owner_id=currentAppUserId`；租户技术字段只用于现有表隔离，不能扩大查询范围。
- 查询和写入必须在 SQL 条件或条件更新中携带归属，不允许先查全部再在内存过滤。
- 他人文案 ID 的详情、编辑和删除使用相同的不可见响应，避免 IDOR 和资源存在性泄露。
- 前端菜单、按钮和 `access.ts` 只改善可见性，不替代后端授权。

## 9. 幂等、并发与事务

- 创建文案的唯一幂等范围为当前用户事实归属加 `idempotencyKey`。
- 请求摘要使用规范化后的标题与正文计算；相同键同摘要复用，相同键不同摘要返回 `46116`。
- 创建操作在一个事务中完成文案主体、v1、`current_version_id` 和审计字段写入。
- 编辑使用 `parentVersionId + expectedScriptRevision` 条件更新；只有成功更新主体后事务才提交新版本，或采用先插入后条件更新并在冲突时整体回滚，禁止产生孤立版本。
- 编辑成功后版本号和主体修订号单调递增；旧版本不可更新或删除。
- 不调用外部服务，不创建异步任务，不冻结或扣减额度。

## 10. 前端模块与数据流

依赖方向固定为：

```text
ScriptLibraryView / ScriptEditorModal
  → userScriptService
  → RuoYi adapter
  → 集中 Umi Request
  → /api/studio/scripts
```

- 从当前通用 `LibraryView` 中只抽取文案页面的业务组件，避免继续把真实请求状态塞入静态多资源组件；不顺手重构形象、声音和作品页面。
- 服务端数据由 React Query 或项目现有 Umi 请求层管理；页面不复制服务端真相。
- 创建、编辑、删除成功后失效文案列表和对应详情 query key。
- 页面只消费领域结果，不解包 `R<T>`、不拼 URL、不拼认证 Header、不按中文 `msg` 分支。
- `scriptId`、`versionId` 和修订号按字符串处理，不转为 JavaScript `number`。

## 11. 错误处理

| 错误 | 处理 |
| --- | --- |
| 400／字段校验失败 | 映射到标题或正文；未知字段统一拒绝。 |
| 401 | 集中清理登录态并只跳转一次。 |
| 403 | 显示权限不足，不跳转登录。 |
| `46116 IDEMPOTENCY_KEY_CONFLICT` | 生成新键并要求用户再次确认保存，不自动重复。 |
| `46118 SCRIPT_HAS_REFERENCES` | 保留文案并提示先解除引用。 |
| `46136 SCRIPT_REVISION_CONFLICT` | 刷新最新版本，保留本地编辑内容，要求用户重新确认。 |
| 其他业务错误 | 由集中错误层显示标准提示；页面不按中文消息分支。 |
| 网络失败／超时 | 保留表单，可由用户重试；重试复用同一幂等键。 |
| 请求取消 | 不显示为业务失败。 |

## 12. 测试与验收

### 12.1 后端测试

- 创建成功时原子产生文案主体和 v1，当前版本正确。
- 相同幂等键同请求只产生一套数据；同键不同请求返回 `46116`。
- 编辑产生不可变新版本并更新当前版本；旧版本内容不变。
- 父版本或修订冲突返回 `46136`，没有孤立版本。
- 删除无引用文案只逻辑删除主体；版本保留。
- 存在引用时返回 `46118`，数据不变。
- 空分页返回 `rows=[]`；`pageSize` 超限、非法排序字段被拒绝或归一化。
- 未登录、运营端令牌、缺少权限、错误角色均被拒绝。
- 用户 A 无法查询、编辑或删除用户 B 的文案；直接接口调用同样被拒绝。
- 请求伪造 `ownerId`、`tenantId`、`workspaceId` 或未知字段时在业务保存前拒绝。
- 详情、编辑、删除逻辑不会泄露他人资源是否存在。

### 12.2 前端测试

- 页面加载、空数据、搜索无结果、失败、403 和分页状态。
- 新建弹窗字段校验、字数显示、提交 loading 和重复点击防护。
- 保存成功关闭弹窗并刷新列表；保存失败保留输入。
- 有未保存内容时关闭弹窗需要确认。
- 编辑成功出现新版本；冲突时保留本地内容并展示刷新提示。
- 复制使用当前版本正文并提供成功／失败反馈。
- 删除确认、引用失败和成功刷新。
- 页面不渲染静态 `SCRIPTS` 或静态模板假数据。

### 12.3 必须运行的验证

- 用户端 Web：受影响测试、TypeScript 类型检查、Biome/lint 和构建。
- 后端：受影响 Maven 模块单元测试、Web 安全测试、Mapper／Service 测试和构建。
- 本机集成测试只能连接受控的 `ai_video_test` 与隔离 Redis 逻辑库；禁止 Docker、Testcontainers、WSL 和 `FLUSHALL`。
- `ai-video-user-api` 与 `ruoyi-admin` 分别执行路由暴露 Smoke Test，确认用户接口只出现在用户端。
- 公共契约文档变化后运行 `scripts/validate-development-standards.ps1`。

## 13. 协作与实施顺序

本任务涉及同一组公共契约、数据库表和前后端类型，不适合多个实施者同时修改同一文件。推荐按下列顺序执行：

1. 契约 owner 先更新本规格直接要求的公共 API、领域和架构文档，并冻结迁移与 DTO 字段。
2. 后端先完成迁移、core Service／Mapper 和用户端 Controller 契约测试。
3. 前端可在冻结的 BO／VO 字段基础上使用 mock 开发弹窗、列表和详情状态。
4. 后端接口可用后替换 mock，完成 adapter 契约测试和页面联调。
5. 执行一次独立代码审查和一次身份／归属专项审查，修复后只做一次定向复核。

可 mock 先行：列表、详情、创建成功／失败、403、修订冲突和引用删除失败的 VO。必须等待后端：真实认证、归属隔离、幂等、条件更新、逻辑删除和路由暴露验证。

## 14. 需要同步的公共契约

实现前必须由契约 owner 最小更新：

- `docs/API_CONTRACT.md`：新增创建手工文案端点、编辑字段、列表／详情响应和 `46136`。
- `docs/DOMAIN_MODEL.md`：`manual_input`、`current_version_id`、手工文案空草稿／空确认语义及条件约束。
- `docs/ARCHITECTURE.md`：用户端文案库数据流和双启动模块装配边界。
- `docs/ASYNC_TASKS.md`：无需增加任务类型；只确认本流程为同步免费操作，不进入任务中心。
- P3 文案实施计划：在真实实施前对齐手工文案与原生成文案表约束，不能按旧计划直接执行冲突部分。

## 15. 完成标准

- 已登录创作用户可通过弹窗输入标题和正文并保存。
- 页面只展示当前用户的真实文案，不展示静态假数据。
- 创建、列表、详情、编辑版本和删除接口均使用统一 `R<T>` 与分页协议。
- 文案版本不可变，当前版本和主体修订一致，重复请求不会产生重复数据。
- 未登录、错误端令牌、缺少权限、伪造归属和跨用户直接访问均被后端拒绝。
- 手工文案没有确认记录，不能绕过下游“准确确认版本”校验。
- 前后端状态、失败分支、测试、构建和双启动路由验证均有实际证据。

## 16. 规格自检结果

- 占位符扫描：通过。
- 一致性：页面、接口、数据模型、权限、错误和验收均使用“当前登录用户个人文案、无工作区业务、无下游确认”的同一边界。
- 范围：一个实现计划可以覆盖，未引入上传、AI 生成、组织共享或管理端派生任务。
- 模糊性：标题、正文、接口、来源类型、当前版本、确认边界、权限和反向场景均已明确。
- 分层：保持 RuoYi Entity／DTO／Mapper／`I...Service`／`service.impl`／端侧 BO／VO／Controller，不引入平行业务层。
- 例外：沿用已批准的 P3 创作域 Entity 不继承 `BaseEntity` 例外；本规格不新增其他目录或对象职责例外。
