# API 契约

本文档定义 AI 视频项目线上 HTTP 协议及前后端适配边界，不维护全量业务接口清单，也不规定后端分层和实现细节。后端实现规范见 [BACKEND_CODING_STANDARDS.md](BACKEND_CODING_STANDARDS.md)。

## 路径与命名

- PRD 和业务模块中新建的 AI 视频资源接口使用 `/api/**`，资源路径使用复数名词；动作型接口置于资源下，例如 `POST /api/tasks/{id}/retry`。
- RuoYi 内置端点保留其实际映射，不机械追加 `/api`。例如认证端点为 `/auth/**`，系统管理端点为 `/system/**`，资源端点为 `/resource/**`。
- 服务的 context path 当前为 `/`；前端必须通过集中配置的 baseURL、开发代理和模块 Service 选择正确路径。现有 Web 开发代理只配置 `/api/`，新增或调用内置端点时必须在集中配置处显式处理，页面不得拼接 URL。
- 专用内部执行器是否使用 `/api` 由它自己的接口规格决定；不得从业务 API 规则推断。
- JSON 字段使用 `lowerCamelCase`，后端字段使用稳定英文名，日期时间使用 `yyyy-MM-dd HH:mm:ss`。前端负责中文展示。

## 统一响应

除本文件明确列出的二进制、流式和 SDK 协议例外外，业务 JSON 接口使用下列协议：

```ts
export interface RuoYiResponse<T> {
  code: number;
  msg: string;
  data: T | null;
}
```

成功示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

失败示例：

```json
{
  "code": 500,
  "msg": "任务状态不允许当前操作",
  "data": null
}
```

约束：

- `code` 是业务响应码，`msg` 仅承载可展示或可记录的提示，`data` 承载业务数据。
- 字符串业务值必须放入 `data`，不得把业务结果放入 `msg`。
- 不得改变顶层 `code`、`msg`、`data` 的响应 envelope 以迁就页面组件。
- 前端禁止按中文 `msg` 分支；需要稳定分支的业务错误以 `code` 和模块接口规格为准。

## 分页协议

分页成功响应使用 `RuoYiResponse<RuoYiPageResult<T>>`：

```ts
export interface RuoYiPageResult<T> {
  total: number;
  // 兼容当前服务端可能产生的 null；目标 HTTP 契约仍要求服务端返回数组。
  rows: T[] | null;
}
```

```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "total": 0,
    "rows": []
  }
}
```

- 正常分页成功响应的 `data` 只包含 `total` 和 `rows`。
- 空页在 HTTP 协议中必须稳定返回 `rows=[]`；服务端不得以 `null` 替代空数组。
- 前端 adapter 可以防御性地将遗留或异常响应的 `rows=null` 归一为 `[]`，但这不是服务端目标契约。
- `total` 是匹配查询条件的总数，`rows` 是当前页的数据数组。

## ProComponents 请求与响应映射

管理类页面的 `ProTable`、`ProList` 和 `ProForm` 请求必须先经过模块 API adapter，再转换为线上请求。页面组件只能调用模块 API 方法，不直接消费 RuoYi envelope。

| ProTable 参数 | RuoYi 请求字段 | 规则 |
| --- | --- | --- |
| `current` | `pageNum` | 当前页从 1 开始。 |
| `pageSize` | `pageSize` | 前端仅提供并发送允许的值；后端另行配置最大值。 |
| 排序列键 | `orderByColumn` | 必须经过显式字段白名单映射。 |
| `ascend` | `isAsc=asc` | 升序。 |
| `descend` | `isAsc=desc` | 降序。 |
| `filters` | 业务筛选字段 | 字段名须与接口规格一致。 |
| `dateRange` | `beginTime` / `endTime` | 统一格式化为 `yyyy-MM-dd HH:mm:ss`。 |
| `keyword` | `keyword` | 通用搜索字段，具体含义由接口声明。 |

- 未知排序列不得发送；多列排序仅在接口规格明确支持时发送。
- 后端对排序字符串的清理不能替代字段白名单。白名单由接口/服务端将允许的列键映射为实际排序列。
- 前端将分页响应转换为 ProComponents 结构时，固定使用：

```ts
function toProTableResult<T>(page: RuoYiPageResult<T>) {
  return {
    data: page.rows ?? [],
    total: page.total,
    success: true,
  };
}
```

- `code`、`msg` 和 HTTP 状态由请求层统一处理；页面不重复提示或自行判断登录状态。

## 认证、客户端与国际化请求头

已认证请求由集中请求层附加下列 Header：

```http
Authorization: Bearer <登录响应中的 access_token>
clientid: <configured-client-id>
content-language: <当前国际化语言，例如 zh-CN>
```

- 当前基础 Sa-Token 配置 `ai-video-api/ruoyi-common/ruoyi-common-satoken/src/main/resources/common-satoken.yml` 声明 `token-prefix: "Bearer"`；因此普通认证请求的 `Authorization` 值必须使用 `Bearer <access_token>`。
- 如 Nacos、环境配置或启动模块配置覆盖 `sa-token.token-name` 或 `sa-token.token-prefix`，变更集中请求 adapter 前必须同步核验该环境的最终生效值，并以最终生效配置为准；不得继续假定原始无前缀 token。
- `clientid` 必须与登录使用的客户端及 Token 绑定的客户端一致。
- `content-language` 必须跟随当前国际化语言，不得固定为 `zh-CN`。
- 未携带 `content-language` 时，服务端回退到系统默认区域；需要语言一致性的调用不得依赖该缺省值。
- 服务端还会按 Token 中的 `clientAccessPath` 与 `clientIpWhitelist` 执行访问控制；前端不能绕过或替代这些校验。
- 页面和模块 Service 不得重复拼接 Header；Header 策略只在集中请求层维护。
- 当前 `platform-ui` 的普通请求与推送 URL 使用 `Bearer ` 前缀，和基础 Sa-Token 配置一致；后续 adapter 改造仍必须在目标环境核验覆盖配置后统一 Header 与推送认证格式。

### 用户端与运营端认证隔离

- 用户端 `ai-video-user-api` 只接受独立 `app`（创作端）Sa-Token 账号类型，身份、认证客户端、角色、权限和会话分别以 `app_user`、`app_auth_client`、`app_role`、`app_permission`、`app_role_permission` 及 `app` 会话为事实源。
- 运营端 `ruoyi-admin` 继续使用 `sys_user`、`sys_client`、`sys_role`、`sys_menu` 和默认运营端会话。两端不得建立用户映射、同号关系、权限映射或自动同步。
- 用户端认证链路必须使用 `StpLogic("app")`、`AppLoginUser` 和 `AppLoginHelper`；不得调用默认 `StpUtil`、`LoginHelper` 或只解析运营端身份的权限实现。旧 `ai_user + userType + StpUtil` 方案已废弃。
- 用户业务 Controller 的权限注解必须明确 `type = app`；运营端同名权限不能授予创作端访问能力。
- 两端只接受一个 `Authorization: Bearer <token>` 和一个当前端合法 `clientid`。重复认证头、逗号拼接令牌、Cookie/Query 与 Header 混合令牌、多个客户端键或交换两端客户端键均在身份解析前拒绝。
- 有效运营端令牌访问用户端业务接口、有效创作端令牌访问运营端接口均按未登录拒绝。失败响应不得泄露另一端用户或客户端是否存在。
- 运营端管理创作用户时仍使用运营端令牌和独立 `aivideo:app-*:*` 权限，只能调用 `/api/admin/app-*` 管理资源；不得签发创作端令牌或冒充创作用户。
- 具体认证、工作区、管理接口及失效规则以 `docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md` 第 10.6、11.0 和 12 节为准。

#### 用户端认证资源

`ai-video-user-api` 只装配下列 `/api/auth/**` 创作端认证与安全会话契约，实际暴露范围以下表“访问边界”为准；登录、注册、验证码、第三方身份和会话只读写 `app_*`（创作端身份）事实源，不得读取 `sys_user`（运营端用户）或 `sys_client`（运营端客户端）。`ruoyi-admin` 只通过独立 `/api/admin/app-*` 管理资源管理创作端账号、安全会话和审计，不能装配创作端认证 Controller、签发 `app`（创作端）令牌或以创作用户身份访问工作台。

> **2026-07-30 P0-A 范围调整：** `POST /api/auth/register`、注册页和注册验证码场景延期，不作为本轮已暴露能力；下表保留其后续实现契约。该调整不删除其他已冻结的认证与安全会话契约。

公开认证请求只携带当前创作端认证客户端标识；需要登录态的资源必须使用下列创作端令牌 Header，且不得混入运营端令牌或客户端：

```http
Authorization: Bearer <app_access_token>
clientid: <app_client_id>
```

- `app_access_token`（创作端访问令牌）只能由 `StpLogic("app")`（创作端登录逻辑）签发并由 `AppLoginHelper`（创作端登录助手）解析。
- `app_client_id`（创作端客户端标识）必须属于当前 `app_auth_client`（创作端认证客户端）；一个请求只能携带一个合法 `Authorization` 和一个 `clientid`。

| 请求 | 访问边界 | 中文用途与关键规则 |
| --- | --- | --- |
| `POST /api/auth/verification-codes` | 公开，需创作端 `clientid` | 申请登录或找回密码验证码；注册验证码场景随注册延期。场景与短信/邮件渠道必须明确，限流、图形验证码和目标脱敏提示不得泄露账号是否存在。 |
| `POST /api/auth/register` | 延期，不在本轮暴露 | 后续注册独立 `app_user`（创作端用户）；只写 `app_*` 身份表，并在同一事务创建个人租户、个人工作区和 `personal_creator`（个人创作者）角色。 |
| `POST /api/auth/login` | 公开，需创作端 `clientid` | 使用用户名、手机或邮箱加密码登录；只查询 `app_user` 和 `app_auth_client`，成功后返回 `app` 类型令牌、当前用户和默认个人工作区。 |
| `POST /api/auth/sms-logins` | 公开，需创作端 `clientid` | 使用短信验证码登录；验证码只用于登录场景，不得查询 `sys_user`。 |
| `POST /api/auth/email-logins` | 公开，需创作端 `clientid` | 使用邮件验证码登录；与短信登录采用相同的身份隔离、限流和一次性消费规则。 |
| `POST /api/auth/social-logins` | 公开，需创作端 `clientid` | 第三方授权登录或绑定后登录；只读写 `app_social_identity`（创作端第三方身份），必须校验回调状态、防重放和来源白名单。 |
| `POST /api/auth/mini-program-logins` | 公开，需创作端 `clientid` | 使用小程序授权码登录；授权码只能消费一次，只生成 `app` 类型会话。 |
| `POST /api/auth/password-resets` | 公开，需创作端 `clientid` | 使用已验证的短信或邮件凭证重置密码；成功后递增凭据修订号并撤销该创作用户的全部旧会话。 |
| `GET /api/auth/me` | 创作端令牌 | 查询当前创作端用户和安全状态；只使用 `AppLoginHelper`，返回脱敏联系方式、角色、权限修订和当前工作区，不返回密码摘要。 |
| `GET /api/auth/sessions` | 创作端令牌 | 查询当前用户的创作端会话；只返回脱敏设备、客户端、最近活动和当前会话标志，不返回令牌原文。 |
| `PUT /api/auth/password` | 创作端令牌 | 登录后修改密码；校验旧密码和密码策略，递增凭据修订号，撤销除当前确认响应外的全部旧会话并要求重新登录。 |
| `POST /api/auth/logout` | 创作端令牌 | 只退出当前 `app` 会话，不影响同号运营用户或其他创作端会话。 |
| `POST /api/auth/social-bindings` | 创作端令牌 | 绑定当前创作用户的第三方身份；只读写 `app_social_identity`，不得建立到 `sys_*` 身份的映射。 |
| `DELETE /api/auth/social-bindings/{socialIdentityId}` | 创作端令牌 | 解绑本人第三方身份；必须校验归属，在同一事务递增 `identityRevision`（身份修订号），并在提交后撤销受影响的 `app` 会话，不得影响同号运营端会话。 |

#### 用户工作台与问卷资源边界

方向、问卷和证据用户接口统一位于 `/api/studio/**`，只由 `ai-video-user` 的
`org.dromara.aivideo.user.studio` HTTP 适配层提供，并只装配到 `ai-video-user-api`。`ruoyi-admin` 不得装配
这些 Controller，也不得以 `/api/user/**`、`/api/admin/**` 或另一套同义路径重复暴露。当前冻结端点为：

```text
GET  /api/studio/direction-options
PUT  /api/studio/script-drafts/{draftId}/direction
POST /api/studio/script-drafts/{draftId}/questionnaire/start
POST /api/studio/script-drafts/{draftId}/questionnaire/turns
GET  /api/studio/script-drafts/{draftId}/questionnaire
PUT  /api/studio/script-drafts/{draftId}/questionnaire/supplements
POST /api/studio/script-drafts/{draftId}/evidence-searches
GET  /api/studio/script-drafts/{draftId}/evidence
PUT  /api/studio/script-drafts/{draftId}/evidence-decisions
```

##### 方向选项与保存的版本边界

`GET /api/studio/direction-options` 的 `data` 字段集合精确且只包含 `catalogVersion`、`industries`、
`purposesByIndustry`、`targetDurations`。`catalogVersion` 是客户端唯一可见的目录并发版本；三个选项字段分别承载
已发布行业、按行业分组的已发布用途和 30／45／60／90／120 秒时长白名单。core 内部
`DirectionCatalogSnapshotDTO` 还携带 `contentHash`、`industryCatalogVersion`、`purposeCatalogVersion`、
`durationRuleVersion`，但这四个字段是服务端追溯事实，不得序列化到该响应。

`PUT /api/studio/script-drafts/{draftId}/direction` 的请求字段集合精确且只包含：

```text
draftRevision
branchRevision
expectedCatalogVersion
industryCode
industryCustomText
purposeCode
purposeCustomText
targetDurationSeconds
```

请求不得包含 `catalogVersion`、`contentHash`、`industryCatalogVersion`、`purposeCatalogVersion` 或
`durationRuleVersion`；出现任一字段或其他未知字段都必须在进入业务保存前拒绝。保存 Service 在同一事务中只读取一次当前
published `DirectionCatalogSnapshotDTO`，用这一个不可变快照校验 `expectedCatalogVersion`、行业／用途绑定、
`custom` 文本规则和目标时长，然后从该快照把 `industryCatalogVersion`、`purposeCatalogVersion`、
`durationRuleVersion` 持久化到本次方向修订。客户端值、当前目录二次读取或旧修订标签都不得成为这三个字段的来源。

`expectedCatalogVersion` 与快照的聚合 `catalogVersion` 不一致，或提交的 code／时长不属于该快照时，返回
`46122 DIRECTION_CATALOG_CHANGED`，前端刷新方向选项并要求用户重新确认，不静默映射。保存响应及后续工作台响应如需返回
目录并发信息，也只返回聚合 `catalogVersion`；不得回传 `contentHash` 或三个追溯子版本。

- 所有端点只接受 `app` 创作端令牌；权限注解必须显式 `type = app`。Controller 首先解析唯一当前
  `app_user` actor，再校验当前 workspace 与 draft/branch 资源动作；不得接受运营端身份、同号用户回退或客户端自报归属。
- 请求只允许端点 BO 明确列出的业务输入，未知字段必须拒绝。尤其答案、补充与事实决定请求禁止接收或信任
  `tenantId`、`ownerType`、`ownerId`、`workspaceId`、`appUserId`、`actor` 等归属字段，以及
  `questionVersionHash`、`answerHash`、`answerIdentityJson`、`answerContextJson`、`questionnaireHash`、
  `knowledgeContextHash`、`generationContextRevision`、`generationInputHash` 等 hash/identity/context 派生字段。
  这些值必须由服务端基于已授权资源和当前锁内快照生成。
- Java `Long` 形式的 draft、branch、question、fact、revision、task 和版本 ID 继续按“标识、精度与专用响应”
  规则序列化为 JSON 十进制字符串；固定题号、进度和时长等非标识数值仍为 JSON number。

#### 数字人声音与视频受限纵链

2026-08-03 项目负责人批准数字人纵链在统一任务中心与额度能力接入前使用受限过渡契约。该例外只覆盖下列
`ai-video-user` 端点，不得扩展为第二套通用任务接口：

```text
POST /api/studio/voice-jobs
POST /api/studio/voice-jobs/{jobId}/confirmation
POST /api/studio/video-jobs
GET  /api/studio/jobs/{jobId}
GET  /api/studio/jobs/{jobId}/media
```

- 创建声音和视频任务必须显式携带 `Idempotency-Key` Header，并使用 `aivideo:studio:generate`
  权限及 `@SaCheckPermission(type = app)`；确认声音沿用同一生成权限。任务查询和媒体读取使用
  `aivideo:studio:query` 权限及 `type = app`。所有端点还必须按当前 `app_user`、租户和资源 owner 重新校验归属。
- `POST /api/studio/voice-jobs` 使用 `multipart/form-data`，字段精确为 `scriptText` 和 `referenceAudio`；正文为
  1～1000 个字符，参考音频只允许 WAV、MP3、M4A 或 FLAC，最大 10 MiB。
- `POST /api/studio/video-jobs` 使用 `multipart/form-data`，字段精确为 `voiceJobId` 和 `portraitImage`；声音任务
  必须属于当前 owner、状态为 `voice_generate/succeeded` 且已经确认，人物图片只允许 JPG、PNG 或 WebP，最大
  10 MiB。
- JSON 端点返回 `R<DigitalHumanJobVo>`；VO 字段精确为 `jobId`、`parentJobId`、`jobType`、`status`、
  `stage`、`progress`、`voiceConfirmed`、`outputAvailable`、`errorMessage`。ID 按本文件规则序列化为十进制字符串。
  过渡状态固定为 `queued | running | succeeded | failed`；阶段固定为 `queued | voice_synthesizing |
  awaiting_voice_confirmation | video_submitted | video_rendering | completed | failed`。
- `GET /api/studio/jobs/{jobId}/media` 是本文件明确登记的二进制例外：成功只返回经过服务端验证且不超过 32 MiB
  的 WAV 或不超过 128 MiB 的 MP4，使用对应 `Content-Type`、安全文件名及 `Cache-Control: no-store`；JSON
  错误、401、403 和取消不得被客户端当作可播放 Blob。
- 同一 owner、任务类型和幂等键的相同输入必须返回已有任务；相同键配不同输入必须拒绝。并发提交由数据库唯一键
  原子仲裁，只有获胜请求能够投递 Provider，失败请求不得留下孤立媒体。
- 该纵链的临时任务事实源、执行限制、终态保护与退出条件见 [ASYNC_TASKS.md](ASYNC_TASKS.md)，领域表登记见
  [DOMAIN_MODEL.md](DOMAIN_MODEL.md)。

#### 运营端创作用户资料更新

`PUT /api/admin/app-users/{id}` 仅由运营端 `sys`（运营端）令牌及
`aivideo:app-user:edit`（编辑创作端用户）权限调用。详情与列表只返回
`maskedPhone`（脱敏手机号）和 `maskedEmail`（脱敏邮箱），绝不返回可回填的明文联系方式。

更新请求包含必填的 `displayName`（显示名称）和 `expectedIdentityRevision`（预期身份修订号），
以及可选的 `phone`（新手机号）、`email`（新邮箱）、`clearPhone`（明确清空手机号）和
`clearEmail`（明确清空邮箱）。联系方式字段遵循下列补丁语义：

- 未提供 `phone` 或 `email`，或字段值为 JSON `null`（空值）时，后端保留对应已存值；页面不得将脱敏值回传。
- 提供非空 `phone` 或 `email` 时，以规范化后的新值替换已存值。
- 只有对应 `clearPhone=true` 或 `clearEmail=true` 才清空联系方式；清空标志不能与该字段的新值同时提交。
- 空字符串、只含空白或包含 `*` 的脱敏联系方式不是清空命令，必须被拒绝；运营人员如需清空必须使用明确的清空标志。

该更新仍以 `expectedIdentityRevision` 防止并发覆盖；成功后递增身份修订并只撤销受影响创作端的 `app` 会话。

#### 创作端个人积分账户

`GET /api/quota/account` 仅接受已通过登录状态校验并持有 `aivideo:quota:query` 权限的创作端
`app` 令牌。请求不接收 `userId`、`tenantId` 或主体类型；Controller 从当前会话取得
`appUserId`，Service 再从 `AppUser.personalTenantId` 取得个人租户编号，并固定按
`subject_type=app_user`、当前用户和 `unit_code=ai_text_credit` 查询。

成功响应 `data` 精确包含：

```json
{
  "quotaUnit": "ai_text_credit",
  "availableBalance": "8640",
  "lockedBalance": "160",
  "usedBalance": "0",
  "totalBalance": "8800"
}
```

四个余额字段均为 JSON 十进制字符串。`usedBalance` 当前仅持久化并返回，默认值为 `0`，本阶段不实施累计、扣减或统计逻辑。该接口严格只读；账户不存在时返回
`46135 QUOTA_ACCOUNT_NOT_FOUND`，不得创建账户、补默认余额或返回虚构的零账户。
`av_quota_account` 只支持当前 `app_user` 的个人账户；接口和底层模型均不接受组织、团队或其他主体。

## 发现与工作流模板用户端接口

本节冻结发现首页、模板详情、动态制作、工作流订单和统一任务中心的用户端 HTTP 契约。所有端点只装配到 `ai-video-user-api`，只接受当前 `app` 创作端令牌，并从会话派生 tenant、workspace 和 owner；请求不得携带这些归属字段。前端页面不得直接消费 `R<T>`，必须通过集中 adapter 严格解析。

权限标识固定为：

- 发现首页和模板只读：`aivideo:studio:query`。
- 创建工作流订单：`aivideo:studio:generate`。
- 任务与订单查询：`aivideo:task:query`。
- 取消任务：`aivideo:task:cancel`。
- 上传输入素材：`aivideo:asset:upload`；查询素材摘要：`aivideo:asset:query`；签发预览或下载地址：`aivideo:asset:download`。

### 发现与模板查询

```text
GET /api/discovery/home
GET /api/discovery/templates?pageNum&pageSize&channel?&categoryCode?&tagCodes?&keyword?&sort?
GET /api/discovery/templates/{templateId}
GET /api/discovery/templates/{templateId}/execution-plans
GET /api/discovery/templates/{templateId}/execution-plans/{executionPlanId}/form-schema
```

`tagCodes` 是去重、规范排序后的逗号分隔稳定代码；`sort` 只允许 `latest|recommended`。空分页返回 `rows=[]`。查询只返回已发布、未下架且至少有一个可用执行方案的模板。

精确用户端 wire 类型为：

```ts
type WorkflowChannel = 'video_template' | 'workflow_inspiration';
type WorkflowMediaType = 'image' | 'video';
type WorkflowProviderKind =
  | 'self_hosted_comfyui'
  | 'runninghub_workflow'
  | 'runninghub_ai_app';
type ExecutionPlanRuntimeStatus = 'available' | 'paused' | 'invalid';
type WorkflowInputValueType =
  | 'string'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'string_array'
  | 'asset_array';

interface WorkflowMediaVO {
  mediaId: string;
  mediaType: WorkflowMediaType;
  url: string;
  posterUrl?: string;
  width: number;
  height: number;
  alt: string;
}

interface DiscoveryBannerVO {
  bannerId: string;
  title: string;
  subtitle?: string;
  target:
    | { type: 'template'; templateId: string }
    | { type: 'channel'; channel: WorkflowChannel };
  media: WorkflowMediaVO;
}

interface DiscoveryChannelVO {
  channel: WorkflowChannel;
  label: string;
  description: string;
  templateCount: string;
}

interface DiscoveryCategoryVO {
  categoryCode: string;
  label: string;
  templateCount: string;
}

interface DiscoveryTagVO {
  tagCode: string;
  label: string;
}

interface WorkflowTemplateCardVO {
  templateId: string;
  templateVersionId: string;
  title: string;
  summary: string;
  channel: WorkflowChannel;
  category: { categoryCode: string; label: string };
  tags: DiscoveryTagVO[];
  cover: WorkflowMediaVO;
  preview?: WorkflowMediaVO;
  usageCount?: string;
  availableExecutionPlanCount: number;
  publishedAt: string;
}

interface DiscoveryHomeVO {
  banners: DiscoveryBannerVO[];
  recommendations: WorkflowTemplateCardVO[];
  channels: DiscoveryChannelVO[];
  categories: DiscoveryCategoryVO[];
  tags: DiscoveryTagVO[];
}

interface WorkflowTemplateDetailVO extends WorkflowTemplateCardVO {
  description: string;
  cases: WorkflowMediaVO[];
  requiredInputs: Array<{
    semanticKey?: string;
    label: string;
    valueType: WorkflowInputValueType;
    assetType?: 'image' | 'audio' | 'video' | 'file';
    required: boolean;
  }>;
}

interface WorkflowExecutionPlanVO {
  executionPlanId: string;
  displayName: string;
  description: string;
  providerDisplayName: string;
  providerKind: WorkflowProviderKind;
  featureTags: string[];
  estimatedDurationSeconds?: { min: number; max: number };
  runtimeStatus: ExecutionPlanRuntimeStatus;
  unavailableReasonCode?: string;
  userMessage?: string;
  supportsCancellation: boolean;
}
```

媒体 `url/posterUrl` 只允许 `https:` 绝对地址或以单个 `/` 开头的应用同源相对路径；拒绝 `http:`、协议相对地址、非 HTTP scheme、反斜杠和控制字符。响应出现 `credential`、`apiKey`、`baseUrl`、`workflowId`、`webappId`、`nodeId`、`providerConfigId` 等未公开字段时，前端 adapter 必须拒绝对象，不渲染或记录。Banner 按钮文案由前端依据 `target.type` 本地化。供应商方案没有“推荐”字段，展示顺序不产生默认选择。

### 动态表单值

用户动态表单与订单请求只包含 `templateId`、`schemaVersion='workflow-form-1'`、`schemaHash` 和 `inputs`；不包含版本、执行方案、服务商或执行模式字段。控件白名单为 `text|textarea|integer|decimal|boolean|select|multi_select|image|audio|video|file`。规范值形状为：文本和单选是 string；整数和小数是无指数的规范十进制 string；布尔值是 JSON boolean；多选是去重保序 string[]；任意文件控件都是 `{assetId:string}[]`。可选未填字段省略，不发送 null。未知 schema 版本、控件、值类型、额外输入 key 或额外对象属性必须拒绝。

### 通用私有素材上传会话

工作流输入复用以下端点，不使用人物形象专用 multipart 接口：

```text
POST /api/assets/uploads
POST /api/assets/uploads/{uploadId}/parts
POST /api/assets/uploads/{uploadId}/complete
POST /api/assets/uploads/{uploadId}/cancel
GET  /api/assets/uploads/{uploadId}
GET  /api/assets/{assetId}/access-url?disposition=inline|attachment
```

创建请求含 `fileName/declaredContentType/sizeBytes/assetType/category/idempotencyKey`；工作流输入还含 `purpose='workflow_input'` 与 `inputKey`。服务端从当前模板的唯一创建配置派生允许类型和业务上下文。上传幂等 scope 为 `(tenantId,workspaceId,ownerId,idempotencyKey)`。

```ts
type UploadMode = 'single' | 'multipart';
type UploadSessionStatus =
  | 'initialized'
  | 'uploading'
  | 'completing'
  | 'completed'
  | 'failed'
  | 'cancelled'
  | 'expired';
type UploadAssetStatus = 'processing' | 'ready' | 'rejected';

interface CreateUploadSessionVO {
  uploadId: string;
  fileId: string;
  mode: UploadMode;
  status: 'initialized' | 'uploading';
  expiresAt: string;
  singlePutUrl?: string;
  requiredHeaders?: Record<string, string>;
  partSizeBytes?: string;
  partCount?: number;
}

interface UploadPartSignaturesVO {
  uploadId: string;
  parts: Array<{
    partNumber: number;
    putUrl: string;
    expiresAt: string;
    requiredHeaders: Record<string, string>;
  }>;
}

type CompleteUploadRequest =
  | { mode: 'single' }
  | { mode: 'multipart'; parts: Array<{ partNumber: number; etag: string }> };

interface CompleteUploadVO {
  uploadId: string;
  fileId: string;
  assetId: string;
  uploadStatus: 'completed';
  assetStatus: UploadAssetStatus;
}

interface CancelUploadVO {
  uploadId: string;
  status: 'cancelled';
}

interface UploadSessionVO {
  uploadId: string;
  fileId: string;
  mode: UploadMode;
  status: UploadSessionStatus;
  expiresAt: string;
  assetId?: string;
  assetStatus?: UploadAssetStatus;
  failureCode?: string;
}
```

`parts` 请求精确为 `{partNumbers:number[]}`，编号去重升序且每批最多 20 个；响应为 `UploadPartSignaturesVO`。`complete` 响应为 `CompleteUploadVO`；`cancel` 响应为 `CancelUploadVO`；查询响应为 `UploadSessionVO`。签名对象 PUT 不使用 `R<T>`，不得携带 `Authorization`、`clientid` 或未由 `requiredHeaders` 给出的 Header。

未知网络结果重试沿用原上传幂等键；只有明确收到 `46212 UPLOAD_SESSION_EXPIRED` 才生成新键和新会话。客户端按当前 `uploadId + requestGeneration` 丢弃旧会话迟到响应。只有 `assetStatus=ready` 能进入订单输入。

### 工作流订单

`POST /api/workflow-orders` 使用 `Idempotency-Key` Header。请求精确包含 `templateId/templateVersionId/executionPlanId/schemaHash/inputs`，不接收 owner、tenant、workspace、供应商配置、工作流、节点或计费字段。创建响应为：

```ts
interface CreateWorkflowOrderVO {
  orderId: string;
  orderNo: string;
  taskId: string;
  taskStatus: 'pending' | 'queued';
  createdAt: string;
}
```

同一 tenant、workspace、owner、幂等键且规范请求摘要相同返回原订单；同 scope 同键不同摘要返回 `46507`。未知网络结果重试复用原键。模板版本陈旧 `46502` 时前端刷新详情与 schema，只保留仍兼容输入和 ready 资产，并要求用户重新确认；不能自动提交或切换供应商。

订单查询与取消：

```text
GET  /api/workflow-orders/{orderId}
POST /api/workflow-orders/{orderId}/cancellations
```

```ts
type AiTaskStatus = 'pending' | 'queued' | 'running' | 'success' | 'failed' | 'cancelled';
type AiTaskStage =
  | 'waiting_for_dispatch'
  | 'preparing_inputs'
  | 'submitting_to_provider'
  | 'confirming_provider_acceptance'
  | 'provider_processing'
  | 'processing_results'
  | 'completed'
  | 'failed'
  | 'cancelled';

interface AiTaskSummaryVO {
  taskId: string;
  taskType: 'workflow_template_generate';
  status: AiTaskStatus;
  stage: AiTaskStage;
  progressPercent?: number;
  failureCode?: string;
  failureMessage?: string;
  retryable: boolean;
  createdAt: string;
  updatedAt: string;
}

interface WorkflowOrderAssetVO {
  assetId: string;
  label: string;
  mediaType: 'image' | 'audio' | 'video' | 'file';
  fileName: string;
  sizeBytes: string;
  status: 'ready' | 'processing' | 'failed';
  primary: boolean;
}

interface WorkflowOrderDetailVO {
  orderId: string;
  orderNo: string;
  createdAt: string;
  template: {
    templateId: string;
    templateVersionId: string;
    title: string;
    cover: WorkflowMediaVO;
  };
  executionPlan: {
    executionPlanId: string;
    displayName: string;
    providerDisplayName: string;
    providerKind: WorkflowProviderKind;
    featureTags: string[];
  };
  inputs: Array<{
    inputKey: string;
    label: string;
    displayValue?: string;
    assets: WorkflowOrderAssetVO[];
  }>;
  task: AiTaskSummaryVO;
  outputs: WorkflowOrderAssetVO[];
  canCancel: boolean;
  canRemake: boolean;
}
```

合法状态矩阵为：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。`progressPercent` 只在可信且为 0..100 时返回。成功订单必须恰好有一个 ready 主结果。`confirming_provider_acceptance` 时 `canCancel=false`。再次制作只在最新详情 `canRemake=true` 时出现，并只导航到模板制作页创建新订单。

### 统一用户任务中心

`GET /api/tasks?pageNum&pageSize&taskType?&status?&keyword?` 返回所有用户生成根任务，而非工作流订单子集；空页返回 `rows=[]`，按 `createdAt desc,taskId desc` 稳定排序。

```ts
interface AiTaskListItemVO {
  taskId: string;
  taskType: string;
  taskTypeLabel: string;
  title: string;
  status: AiTaskStatus;
  stage: AiTaskStage;
  progressPercent?: number;
  failureCode?: string;
  failureMessage?: string;
  retryable: boolean;
  resourceType: string;
  resourceId: string;
  detailTarget?: { type: 'workflow_order'; orderId: string };
  canCancel: boolean;
  createdAt: string;
  updatedAt: string;
}
```

`taskType/resourceType` 必须匹配 `[a-z][a-z0-9_]{1,63}`。前端按 `taskTypeLabel/title` 展示未知但合法的其他生成类型，不能拒绝整页或过滤为订单列表；只对白名单 `detailTarget` 生成站内链接，不能把 resource 字段拼成 URL。所有私有查询键必须包含当前 `userId + workspaceId`，会话清除时清空应用私有缓存。

### 工作流模板稳定错误码

| 错误码 | 稳定标识 | 前端规则 |
| --- | --- | --- |
| `46212` | `UPLOAD_SESSION_EXPIRED` | 使用新上传幂等键重建会话，丢弃旧会话迟到响应。 |
| `46501` | `WORKFLOW_TEMPLATE_UNAVAILABLE` | 显示模板已下架或不可用。 |
| `46502` | `WORKFLOW_TEMPLATE_VERSION_STALE` | 刷新详情和 schema，保留兼容值并要求重新确认。 |
| `46503` | `WORKFLOW_EXECUTION_PLAN_UNAVAILABLE` | 保留用户输入，要求用户主动选择其他方案，不自动切换。 |
| `46504` | `WORKFLOW_FORM_SCHEMA_CONFLICT` | 展示 changed/removed keys，确认后才清理不兼容值。 |
| `46505` | `WORKFLOW_INPUT_INVALID` | 按 schema 顺序映射结构化 fieldErrors。 |
| `46506` | `WORKFLOW_INPUT_ASSET_INVALID` | 按 inputKey 映射素材归属、类型或 ready 状态错误。 |
| `46507` | `WORKFLOW_ORDER_IDEMPOTENCY_CONFLICT` | 用户重新确认后才生成新订单幂等键。 |
| `46509` | `WORKFLOW_ORDER_CANCEL_CONFLICT` | 刷新订单真实状态，不在本地强改为 cancelled。 |
| `46518` | `WORKFLOW_ORDER_NO_LONGER_REMAKABLE` | 仅提供稳定文案，不能单独开启再次制作。 |

### 2026-08-11 发现页 RunningHub 单执行冻结（优先级覆盖）

本小节覆盖本章此前所有发现页多供应商、模板版本、执行方案和方案级 schema 表述；被覆盖的端点、字段、错误码与交互语义均为**废止契约**，不得在新用户端 wire、缓存、DOM、文案、请求、响应或前端类型中保留。

#### 第一阶段模板管理与展示覆盖

第一阶段只交付运营模板 CRUD、每模板唯一 RunningHub 执行配置、RunningHub 账号最小管理，以及用户端 `home`、模板列表、模板详情和 `creation-config`。除运营端只读参数候选检查外，上传、订单、任务执行、RunningHub 生成请求/轮询、连接测试和 `workflow_template_test` 均不在本阶段实现；本节后文的上传、订单与任务契约仅供后续阶段使用，不能成为第一阶段验收或依赖。

平台发现目录固定使用 `tenant_id=0`。用户端 Controller 不接受客户端传入 tenant、owner 或 workspace；Core 查询也不得从客户端参数派生这些值。模板在用户端列表、详情和 `creation-config` 中可见的精确条件为：

```text
visible = template.status == enabled
       && execution_config.del_flag == 0
       && execution_config.enabled == true
       && runninghub_account.del_flag == 0
       && runninghub_account.enabled == true
```

`last_test_status` 和全部 `last_test_*_revision` 不参与列表、详情、`creation-config` 或 enable 判定。管理员通过独立 enable 动作明示已完成人工验证；修改模板或唯一配置保持模板当前状态，不自动转为 `pending_test`。`pending_test` 仅作历史数据兼容。详情中模板本身不可用返回 `46501 WORKFLOW_TEMPLATE_UNAVAILABLE`；唯一配置或其账号不存在、已删除或未启用返回 `46503 WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE`。列表和首页直接过滤所有不满足 `visible` 的模板，不返回半完整对象，也不恢复“选择方案”语义。

第一阶段用户端只开放：

```text
GET /api/discovery/home
GET /api/discovery/templates?pageNum&pageSize&channel?&categoryCode?&tagCodes?&keyword?&sort?
GET /api/discovery/templates/{templateId}
GET /api/discovery/templates/{templateId}/creation-config
```

模板列表使用框架分页 `PageResult<WorkflowTemplateCardVO>`，统一 `R<T>` 的 `data` 精确为 `{ rows: WorkflowTemplateCardVO[]; total: number }`，空页为 `rows=[]`，`total` 必须是 JSON number。卡片、详情和创建配置的第一阶段公共 wire 精确为：

```ts
interface WorkflowTemplateCardVO {
  templateId: string;
  title: string;
  summary: string;
  channel: 'video_template' | 'workflow_inspiration';
  category: { categoryCode: string; label: string };
  tags: Array<{ tagCode: string; label: string }>;
  cover: WorkflowMediaVO | null;
  preview?: WorkflowMediaVO;
  usageCount?: string;
  estimatedDurationSeconds?: number;
  enabledAt: string;
}

interface WorkflowTemplateDetailVO extends WorkflowTemplateCardVO {
  description: string;
  cases: WorkflowMediaVO[];
  requiredInputs: Array<{
    semanticKey?: string;
    label: string;
    valueType: WorkflowInputValueType;
    assetType?: 'image' | 'audio' | 'video' | 'file';
    required: boolean;
  }>;
}

interface WorkflowInputFieldVO {
  inputKey: string;
  semanticKey?: string;
  label: string;
  description?: string;
  control:
    | 'text' | 'textarea' | 'integer' | 'decimal' | 'boolean' | 'select'
    | 'multi_select' | 'image' | 'audio' | 'video' | 'file';
  valueType: WorkflowInputValueType;
  required: boolean;
  defaultValue?: string | boolean | string[] | Array<{ assetId: string }>;
  placeholder?: string;
  options?: Array<{ value: string; label: string }>;
  constraints?: {
    min?: string;
    max?: string;
    minLength?: number;
    maxLength?: number;
    minItems?: number;
    maxItems?: number;
    assetType?: 'image' | 'audio' | 'video' | 'file';
    allowedExtensions?: string[];
    allowedContentTypes?: string[];
    maxBytesPerAsset?: string;
  };
}

interface WorkflowCreationConfigVO {
  templateId: string;
  schemaVersion: 'workflow-form-1';
  schemaHash: `sha256:${string}`;
  fields: WorkflowInputFieldVO[];
  estimatedDurationSeconds?: number;
  billingPolicy: { mode: 'free' };
}
```

`WorkflowInputFieldVO` 的对象形状和控件白名单以 `docs/contracts/discovery-runninghub/workflow-form-1.schema.json` 为唯一事实源；`WorkflowCreationConfigVO` 顶层不得增加其他属性。`categoryCode`、`tagCode` 均为对应数据库十进制 ID 的字符串表示，不是运营自定义代码。封面素材未接入时 `cover` 合法值为 `null`。

第一阶段运营端点精确冻结为：

```text
GET    /api/admin/workflow-templates
POST   /api/admin/workflow-templates
GET    /api/admin/workflow-templates/{templateId}
PUT    /api/admin/workflow-templates/{templateId}
DELETE /api/admin/workflow-templates/{templateId}
GET    /api/admin/workflow-templates/{templateId}/execution-config
PUT    /api/admin/workflow-templates/{templateId}/execution-config
POST   /api/admin/workflow-templates/{templateId}/enable
POST   /api/admin/workflow-templates/{templateId}/disable
GET    /api/admin/workflow-templates/options

GET    /api/admin/runninghub-accounts
POST   /api/admin/runninghub-accounts
GET    /api/admin/runninghub-accounts/{accountId}
PUT    /api/admin/runninghub-accounts/{accountId}
DELETE /api/admin/runninghub-accounts/{accountId}
POST   /api/admin/runninghub-accounts/{accountId}/enable
POST   /api/admin/runninghub-accounts/{accountId}/disable
POST   /api/admin/runninghub-accounts/parameter-candidates
```

第一阶段不提供任何 `connection-tests` 端点。运营端唯一配置仍允许 `runninghub_workflow|runninghub_ai_app`，每个 `(tenant_id, template_id)` 只存在一条未删除配置；这两个值与 `self_hosted_comfyui` 等执行细节只在用户端禁止/隐藏。账号 API Key 与配置 `accessPassword` 只允许写入或返回脱敏/是否已配置状态，任何列表、详情、日志或序列化对象都不得返回明文。`row_revision` 只用于乐观并发控制，不代表测试、发布或配置版本。

任何旧多供应商、执行方案、自动测试成功才启用/才可见、修改后自动 `pending_test` 的表述，均由本第一阶段覆盖块明确废止。

`POST /api/admin/runninghub-accounts/parameter-candidates` 是运营端只读检查接口，权限为
`aivideo:runninghub-account:query`。请求精确为
`{accountId,executionMode,workflowId?,webAppId?}`，并且按 `runninghub_workflow|runninghub_ai_app`
二选一提交对应远端 ID。AI App 通过固定 RunningHub HTTPS 主机读取官方调用示例，Workflow 通过固定
RunningHub HTTPS 主机读取 API-format JSON；响应只返回
`{webAppName?,candidates:[{nodeId,nodeName,fieldName,fieldType,description?,defaultValue?,options:[{value,label}]}]}`。
接口不得返回 API Key、原始 `curl`、统计信息、原始 `fieldData`、完整 Workflow JSON 或供应商错误正文；
LIST 的 `fieldData` 只允许在后端受限解析为 options，并忽略 `default` 元数据项。参数读取失败稳定返回
`46522 WORKFLOW_PARAMETER_INSPECTION_FAILED`。
AI App 候选的 `fieldType` 保留 RunningHub 返回的安全原始类型字符串（最长 64 字符）；Workflow 候选按
JSON 标量类型返回 `text|integer|decimal|boolean`，并对 `LoadImage.image`、`LoadAudio.audio`、
`LoadVideo.video` 及同类明确文件输入按 `class_type + fieldName` 返回 `image|audio|video|file`。

用户端发现模板只开放以下端点：

```text
GET /api/discovery/home
GET /api/discovery/templates?pageNum&pageSize&channel?&categoryCode?&tagCodes?&keyword?&sort?
GET /api/discovery/templates/{templateId}
GET /api/discovery/templates/{templateId}/creation-config
```

`creation-config` 返回当前可用创建配置的 `schemaVersion='workflow-form-1'`、`schemaHash`、标题/说明和控件定义；它不暴露执行模式、供应商、版本、Workflow/Web App/节点或外部任务标识。用户端模板卡、详情与订单详情仅可包含 `templateId` 和展示数据，不能包含 `templateVersionId` 或执行配置标识。

创建订单仍为 `POST /api/workflow-orders`，`Idempotency-Key` 固定在 Header。请求 JSON **严格只含**：

```ts
interface CreateWorkflowOrderRequest {
  templateId: string;
  schemaHash: `sha256:${string}`;
  inputs: Record<string, string | number | boolean | string[] | Array<{ assetId: string }>>;
}
```

`schemaHash` 使用 RFC 8785 canonical JSON 后的 SHA-256，格式固定为 `sha256:` 加 64 位小写十六进制。`workflow-form-1` 的控件—值类型固定为 `text|textarea|select -> string`、`integer -> integer`、`decimal -> decimal`、`boolean -> boolean`、`multi_select -> string_array`、`image|audio|video|file -> asset_array`。数字 JSON token 禁止指数写法；所有文件控件都只可为 `{assetId:string}[]`；`inputs`、每个素材对象和创建请求都拒绝额外属性与未知控件。

通用上传继续复用 `/api/assets/uploads*` 与 `/api/assets/{assetId}/access-url`，但工作流输入上下文只可由 `templateId`、当前 `schemaHash` 与 `inputKey` 派生；不得接受或返回模板版本、执行方案、供应商、模式或外部运行标识。订单资产下载/预览的运营端受控短链固定为：

```text
GET /api/admin/workflow-orders/{orderId}/assets/{assetId}/access-url?disposition=inline|attachment
```

每个 `(tenant_id, template_id)` 只有一个当前执行配置，由运营端维护；其模式仅可为 RunningHub Workflow 或 RunningHub AI App，且模式本身不向用户端暴露。禁止模板版本、配置版本、密钥版本、订单执行配置快照、自动路由、自动选择、故障切换、回退或同订单人工重放。

以下字段和值在任何用户可见数据面均禁止：`self_hosted_comfyui`、`runninghub_workflow`、`runninghub_ai_app`、`providerKind`、`executionMode`、`executionPlanId`、`templateVersionId`、Workflow ID、Web App ID、节点 ID、RunningHub 外部 task ID。`GET /api/discovery/templates/{templateId}/execution-plans` 及其方案级 `form-schema` 端点已废止。

工作流订单任务复用统一任务模型。系统全部任务类型冻结为 `workflow_template_generate|workflow_template_test`，资源类型为 `workflow_order|workflow_template`，免费策略版本为 `workflow-free-1`；其中 `workflow_template_test` 只存在于运营任务／测试记录，普通用户任务、订单和任务中心只暴露 `workflow_template_generate`。用户公共状态/阶段矩阵精确为：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。提交结果未知时绝不自动发起第二次 RunningHub POST。

稳定用户错误码如下；旧 `46504`、`WORKFLOW_TEMPLATE_VERSION_STALE`、`WORKFLOW_EXECUTION_PLAN_UNAVAILABLE` 和“选择其他方案”语义已废止：

| 错误码 | 稳定标识 | 用户端规则 |
| --- | --- | --- |
| `46501` | `WORKFLOW_TEMPLATE_UNAVAILABLE` | 显示模板不可用。 |
| `46502` | `WORKFLOW_CREATION_CONFIG_STALE` | 仅返回 `currentSchemaHash`；刷新创建配置并要求重新确认。 |
| `46503` | `WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE` | 保留输入，显示配置暂不可用；不选择、切换或回退方案。 |
| `46505` | `WORKFLOW_INPUT_INVALID` | 按创建配置控件顺序映射字段错误。 |
| `46506` | `WORKFLOW_INPUT_ASSET_INVALID` | 映射素材归属、类型或 ready 状态错误。 |
| `46507` | `WORKFLOW_ORDER_IDEMPOTENCY_CONFLICT` | 用户确认后才生成新幂等键。 |
| `46509` | `WORKFLOW_ORDER_CANCEL_CONFLICT` | 刷新订单真实状态。 |
| `46518` | `WORKFLOW_ORDER_NO_LONGER_REMAKABLE` | 仅展示稳定文案。 |

运营发现配置端点冻结为：`GET/PUT /api/admin/discovery/home`、Banner/category/tag CRUD、`PUT /api/admin/discovery/recommendations`。所有端点受独立运营权限和审计保护；用户端永远不接收其配置细节。

## 错误与异常适配

HTTP 状态与 `RuoYiResponse.code` 是两条错误通道。请求 adapter 必须统一归一化，避免同一失败触发多次提示。

- HTTP 或业务响应为 401 时，清理登录态并且只跳转登录页一次。
- HTTP 或业务响应为 403 时，展示无权限状态，不跳转登录页。
- 除 401/403 外的业务码，抛出携带 `code`/`msg` 的标准业务异常，由集中错误层处理。
- 网络错误、超时、取消、5xx 响应与业务错误分别处理；取消请求不应显示为业务失败。
- 禁止根据中文 `msg` 判断错误类别或业务状态。

### 创作端身份稳定错误码

下列错误码、英文稳定标识与中文语义来自 `P0-A-identity-security`（账号与安全底座）冻结规格；客户端按 `code`（错误码）处理，不得以中文消息分支或重定义英文稳定标识。

| 错误码 | 稳定标识 | 中文语义 | 处理建议 |
| --- | --- | --- | --- |
| `46128` | `APP_AUTH_CREDENTIALS_INVALID`（创作端账号或登录凭据不正确） | 创作端账号或登录凭据不正确 | 统一显示“账号或凭据不正确”；不得区分账号不存在、密码错误或第三方身份未绑定。 |
| `46129` | `APP_ACCOUNT_UNAVAILABLE`（创作端账号不可用） | 创作端账号已停用、注销中或身份修订失效 | 清理创作端会话并返回登录页；不得影响运营端会话。 |
| `46130` | `APP_AUTH_CLIENT_UNAVAILABLE`（创作端认证客户端不可用） | 创作端客户端不存在、已停用、换密或不允许当前路径/网络 | 拒绝请求并提示客户端不可用；不得回退查询 `sys_client`。 |
| `46131` | `APP_SESSION_REVISION_STALE`（创作端会话修订过期） | 创作端会话中的凭据、身份、权限、客户端或工作区修订已过期 | 撤销当前 `app` 会话并要求重新登录或重新选择工作区。 |
| `46132` | `MULTIPLE_AUTH_CREDENTIALS_REJECTED`（多个认证凭据被拒绝） | 请求包含重复、拼接或跨通道的多个认证凭据 | 在解析用户前拒绝，清理当前请求状态，不选择任一凭据继续。 |
| `46133` | `APP_PASSWORD_RESET_REQUIRED`（创作端账号必须重置密码） | 当前创作端账号必须先修改初始或重置密码 | 只放行当前用户、安全会话、退出和修改密码接口。 |
| `46134` | `APP_ROLE_REVISION_CONFLICT`（创作端角色修订冲突） | 运营端编辑创作端角色或权限时预期修订号过期 | 刷新角色详情并由运营人员重新确认，不覆盖他人修改。 |

### 生成上下文写锁错误

`46123 GENERATION_CONTEXT_LOCKED` 表示当前 draft/branch revision 已有
`script_generate` 或 `script_optimize` 根任务处于 `pending`、`queued` 或 `running`，答案、补充、方向或事实
决定写入被拒绝。响应 `data` 的字段集合必须精确且只包含 `rootTaskId`、`taskType`、`status`：

```json
{
  "code": 46123,
  "msg": "生成上下文已锁定",
  "data": {
    "rootTaskId": "9824516531",
    "taskType": "script_generate",
    "status": "running"
  }
}
```

- `rootTaskId` 是 JSON 十进制字符串；`taskType` 只允许 `script_generate|script_optimize`，`status` 只允许
  `pending|queued|running`。不得返回 tenant、owner、workspace、任务组键、模型 payload、费用或其他内部字段。
- 前端 adapter 必须按 `code === 46123` 解析上述精确形状，暂停当前上下文的所有写操作，用
  `rootTaskId` 接入统一任务轮询并展示稳定的“生成中不可修改”状态。任务终态后先重新拉取问卷上下文和修订，
  再允许用户显式重试；不得自动重放旧答案、把它映射成额度不足/幂等冲突，或根据中文 `msg` 分支。
- 缺少任一字段、出现未知字段或枚举越界都按契约异常处理，不得猜测任务或解锁状态。

## 标识、精度与专用响应

- 业务 ID 在前端以字符串处理，避免 JavaScript 数值精度丢失。
- 金额、额度和 `BigDecimal` 对应的值在 JSON 中以字符串传输和处理。
- 文件上传使用 `multipart/form-data`，文件字段、业务字段及成功响应以具体接口说明为准；后端校验实现规则见后端编码规范。
- 下载凭证等 JSON 响应使用本文件的统一 envelope；直接二进制下载由独立 Blob adapter 处理，接口说明必须标明 `Content-Type` 与文件名规则。
- SSE 使用独立 adapter；不得将其作为普通 JSON 响应解析。原生 `EventSource` 不能设置 `Authorization`、`clientid`、`content-language` 等自定义 Header。
- SSE 专用 adapter 必须按端点规格选择服务端支持的查询参数、认证 Cookie 或 fetch 流式实现；不得把普通请求的 Header 策略假定为原生 `EventSource` 可用。
- 仅当端点规格明确允许时，才可使用 token 查询参数，并且仅限该端点；优先使用短期凭据，同时避免令牌进入访问日志、Referer 或第三方域名请求。
- `/api/snail/chat/**` 使用 SDK `Result` 协议；其他 Snail AI JSON API 仍使用 `R<T>` 线上协议。

## 任务与状态接口

所有生成类接口必须创建统一任务记录，并能通过任务中心查询。任务创建响应示例：

```json
{
  "code": 200,
  "msg": "任务已创建",
  "data": {
    "taskId": "task_001",
    "status": "queued",
    "progress": 0,
    "quotaFrozen": "100"
  }
}
```

- 创建任务请求携带 `idempotencyKey`；重复提交返回已有任务，不创建第二条任务。
- 任务状态使用稳定英文值，展示文案由统一字典或枚举映射；状态全集见 [DOMAIN_MODEL.md](DOMAIN_MODEL.md)。
- 任务状态、轮询与通知遵循 [ASYNC_TASKS.md](ASYNC_TASKS.md)。

## 运营端知识库管理

运营端知识资源统一使用 `/api/admin/knowledge-items`，仅接受运营端 `sys` 令牌及对应知识库权限。业务 ID 按本文“标识、精度与专用响应”约定返回 JSON 十进制字符串。

| 请求 | 请求字段 | 响应与规则 |
| --- | --- | --- |
| `GET /api/admin/knowledge-items` | `pageNum`、`pageSize`；可选 `name`、`knowledgeType`、`status` | 返回 `RuoYiResponse<RuoYiPageResult<KnowledgeItemAdmin>>`；名称支持模糊筛选。缺省每页 20 条，服务端最多返回 100 条并忽略未进入白名单的客户端排序。 |
| `GET /api/admin/knowledge-items/{id}` | 路径参数 `id` | 返回包含 `id`、`name`、`knowledgeType`、`status`、`versionNo`、`summary`、`content`、`updateTime` 的知识详情。 |
| `POST /api/admin/knowledge-items` | JSON：`name`、`knowledgeType`、`status`、`content`，可选 `summary` | 新增知识并返回新知识 ID。 |
| `PUT /api/admin/knowledge-items/{id}` | 与新增请求相同 | 编辑名称、知识类型、状态、正文和摘要；正文始终追加新版本，不覆盖已发布或已退役历史。 |
| `DELETE /api/admin/knowledge-items/{id}` | 路径参数 `id` | 仅允许物理删除从未发布或退役的纯草稿／审核条目；前端必须二次确认。存在发布或退役历史时返回业务失败并提示改为退役。 |
| `PUT /api/admin/knowledge-items/{id}/status` | JSON：`status` | 修改单条知识状态，供列表行内状态控件调用。已发布版本切回草稿／审核以及已退役版本重新启用时追加新版本；发布转退役可以原地更新状态；同状态请求幂等。 |
| `POST /api/admin/knowledge-items/imports` | `multipart/form-data`：重复字段 `files`、`names`、`knowledgeTypes`、`statuses` | 批量导入知识。四组字段按上传顺序一一对应且全部必填；每个文件均可在提交前修改名称、知识类型和状态。单次最多 20 个文件、单文件最多 10 MiB、总计最多 20 MiB，只接受通过扩展名、MIME、UTF-8 和二进制内容检查的文本文件。返回总数、成功数、跳过数、失败数及逐文件结果。 |

列表行至少返回 `id`、`name`、`knowledgeType`、`status`、`versionNo`、`updateTime`。`content` 只在详情接口返回，列表查询不得加载知识正文；列表和详情均不向前端展示或要求消费 `sourceRef`（来源路径）。导入文件格式由服务端允许的文本知识格式白名单校验，不以 Markdown 作为唯一格式或页面名称。

知识类型代码仅用于前后端传输和持久化，页面必须显示对应中文：

| 代码 | 中文名称 |
| --- | --- |
| `primary_template` | 基础模板 |
| `writing_technique` | 写作技巧 |
| `psychology` | 心理策略 |
| `case` | 案例参考 |
| `mandatory_rule` | 强制规则 |

知识状态代码仅用于前后端传输和持久化，页面必须显示对应中文：

| 代码 | 中文名称 |
| --- | --- |
| `draft` | 草稿 |
| `reviewing` | 审核中 |
| `published` | 已发布 |
| `retired` | 已停用 |

运营端页面的表格、筛选项、详情、表单和导入弹窗只展示上述中文名称，不直接展示英文类型代码、英文状态代码或来源路径。
## 声音上传与本地转写接口补充（2026-08-03）

创作端声音接口统一使用 app 登录态、tenant/workspace/owner SQL 归属条件和 `R<T>`/`TableDataInfo<T>` 包装：

- `POST /api/voices`：`multipart/form-data`，仅接受 `file` 与 JSON `metadata`，权限 `aivideo:voice:upload`。
- `GET /api/voices`、`GET /api/voices/{voiceId}`、`GET /api/voices/{voiceId}/access-url`：权限 `aivideo:voice:query`；试听 URL 有效期 120 秒。
- `PUT /api/voices/{voiceId}/transcript`：仅 `ready` 可修改，使用 `expectedRevision` 条件更新，权限 `aivideo:voice:edit`。
- `POST /api/voices/{voiceId}/transcription/retry`：仅 `failed` 可重试，权限 `aivideo:voice:transcribe`。
- `DELETE /api/voices/{voiceId}`：无请求体，权限 `aivideo:voice:delete`；仅允许删除当前 tenant/workspace/owner 下未删除的 `origin|clone` 声音，并在同一事务逻辑删除其 `voice_sample` 资产，提交后尝试清理存储对象。不存在、已删除、公共或跨归属 ID 统一返回 `46401 VOICE_NOT_FOUND`。
- Worker：`GET /health`；`POST /internal/v1/transcriptions` 仅监听 `127.0.0.1:18181`，multipart 请求必须携带 `X-Internal-Token`。

稳定错误码：`46401 VOICE_NOT_FOUND`、`46402 VOICE_INPUT_INVALID`、`46403 VOICE_REVISION_CONFLICT`、
`46404 VOICE_TRANSCRIPTION_STATE_INVALID`、`46405 VOICE_TRANSCRIPTION_UNAVAILABLE`。声音文件继续复用
`46201 FILE_TYPE_NOT_ALLOWED`、`46202 FILE_SIZE_EXCEEDED`、`46204 UPLOAD_SESSION_CONFLICT`、`46209 FILE_NOT_READY`。

## 用户端人物形象接口

人物形象接口仅装配到创作端，使用当前 `app` 会话派生租户、工作区和所有者，不提供公共形象或运营端入口。

- `GET /api/portraits`：数据库联表完成归属、关键词、性别和 `processing|ready|failed` 状态过滤后再分页；列表不返回 `assetId` 和文件详情。
- `GET /api/portraits/{portraitId}`：返回归属当前用户的详情；文件大小字段为十进制字符串 `sizeBytes`。
- `POST /api/assets/uploads/portrait-images`：单文件上传，仅允许忽略大小写的 JPG/JPEG、PNG、WebP、GIF，最大 10MB；校验后缀、MIME、文件头和格式结构一致性后，必须用对应 ImageIO Reader 真实解码首帧。
- `POST /api/portraits`：请求字段为 `assetId`、`name`、`gender`、`sceneTags`、可选 `note` 和必填 `idempotencyKey`；同一工作区、用户和幂等键的相同请求返回原结果。
- `GET /api/portraits/{portraitId}/access-url`：只为 `ready` 素材返回 `url`、`expiresAt`、`contentType`。

上传错误码：`46201` 表示类型不一致或无法安全解码，`46202` 表示文件超过 10MB，`46203` 表示像素尺寸超过资源上限。上传对象在数据库写入异常或事务回滚时必须补偿删除。

## 用户端个人文案库接口

个人文案接口仅装配到 `ai-video-user`，统一使用 `R<T>`、`app` 令牌和服务端当前用户归属。请求不得包含 `tenantId`、`ownerType`、`ownerId`、`workspaceId` 或 `appUserId`，业务 ID 与修订号均返回十进制字符串。

- `POST /api/studio/scripts`：`aivideo:script:edit`；请求为 `displayTitle`、`scriptText`、`idempotencyKey`。
- `GET /api/studio/scripts`：`aivideo:script:query`；支持 `keyword`、`pageNum`、`pageSize`、`orderByColumn=updatedAt|displayTitle`、`isAsc=asc|desc`。
- `GET /api/studio/scripts/{scriptId}`：查询当前版本和倒序版本摘要。
- `GET /api/studio/scripts/{scriptId}/versions/{versionId}`：查询归属当前文案的单个不可变版本。
- `POST /api/studio/scripts/{scriptId}/versions`：`aivideo:script:edit`；以 `parentVersionId` 和 `expectedScriptRevision` 创建 `manual_edit` 新版本。
- `DELETE /api/studio/scripts/{scriptId}`：`aivideo:script:remove`；逻辑删除主体，保留不可变版本。

标题规范化后为 1～100 个 Unicode code point，正文为 1～20,000 个。幂等键同请求复用，异请求返回 `46116`；版本冲突返回 `46136`；已有确认版本的删除返回 `46118`。手工录入不产生确认记录，不进入生成任务或额度流程。

## 创作第 6 步时间轴 HTTP 契约（`timeline-1`）

本节是创作第 6 步的权威 HTTP 契约，只装配到 `ai-video-user-api`。服务端从当前 `app_user` 会话派生 `owner_user_id`；请求和响应均不得出现租户、工作区、所有者、内部对象键、内部路径或临时 URL。所有业务 ID、修订号和版本号跨 HTTP 边界时均使用十进制字符串，普通 JSON 使用 `R<T>`，分页使用 `R<PageResult<T>>`。

### 权限

| 权限标识 | 允许操作 |
| --- | --- |
| `aivideo:creation:query` | 查询项目、草稿、版本和成品 |
| `aivideo:creation:edit` | 初始化／修改项目，保存草稿、版本、冲突副本和恢复版本 |
| `aivideo:creation:generate` | 图片提示词、花字建议、字幕对齐和最终合成 |
| `aivideo:creation-asset:query` | 查询和受控读取素材 |
| `aivideo:creation-asset:upload` | 上传素材 |
| `aivideo:creation-asset:delete` | 删除未被草稿、版本、任务或成品引用的素材 |
| `aivideo:task:query` | 查询统一任务列表和详情 |
| `aivideo:task:cancel` | 创建取消请求 |
| `aivideo:task:retry` | 主动重试并创建新根任务 |

前端可见性不能替代后端权限和 `owner_user_id` 归属校验；越权编号与不存在编号使用同一资源不存在语义。

### 项目、草稿和版本资源

| 方法与路径 | 请求 | 成功响应 `data` |
| --- | --- | --- |
| `POST /api/studio/creation-projects` | `CreateCreationProjectRequest` | `CreationProjectVO` |
| `GET /api/studio/creation-projects/{projectId}` | 无 | `CreationProjectVO` |
| `PUT /api/studio/creation-projects/{projectId}` | 精确且只含 `projectTitle` | `CreationProjectVO` |
| `GET /api/studio/creation-projects/{projectId}/timeline-draft` | 无 | `TimelineDraftVO` |
| `PUT /api/studio/creation-projects/{projectId}/timeline-draft` | `SaveTimelineDraftRequest` | `SaveTimelineDraftResultVO` |
| `GET /api/studio/creation-projects/{projectId}/timeline-versions` | `pageNum,pageSize` | `PageResult<TimelineVersionVO>` |
| `POST /api/studio/creation-projects/{projectId}/timeline-versions` | 精确且只含 `idempotencyKey,expectedRevision` | `TimelineVersionVO` |
| `POST /api/studio/creation-projects/{projectId}/timeline-versions/{versionId}/restorations` | 精确且只含 `idempotencyKey,expectedRevision` | `SaveTimelineDraftResultVO` |
| `POST /api/studio/creation-projects/{projectId}/timeline-versions/conflict-copies` | `CreateConflictCopyRequest` | `TimelineVersionVO` |
| `GET /api/studio/creation-projects/{projectId}/outputs/latest` | 无 | `CreationOutputVO`；没有成品时返回资源不存在 |

建项时，已授权源视频的探测宽度、高度和帧率必须为正值，但不要求等于时间轴画布。`CreationProject` 与初始草稿始终使用 `1080×1920 / 30fps` 画布，主视频元素通过 `fitMode=cover` 适配该画布；源媒体探测事实仍保存在素材记录中。

```ts
interface CreateCreationProjectRequest {
  sourceType: 'digital_human_job';
  sourceId: string;
  projectTitle?: string;
  idempotencyKey: string;
}

interface SaveTimelineDraftRequest {
  idempotencyKey: string;
  expectedRevision: string;
  schemaVersion: 'timeline-1';
  timeline: TimelineDocument;
}

interface CreateConflictCopyRequest {
  idempotencyKey: string;
  baseRevision: string;
  schemaVersion: 'timeline-1';
  timeline: TimelineDocument;
}

interface CreationProjectVO {
  projectId: string;
  projectTitle: string;
  sourceType: 'digital_human_job';
  sourceId: string;
  baseVideoAssetId: string;
  primaryAudioAssetId?: string;
  status: 'editing' | 'rendering' | 'ready' | 'archived';
  canvas: { width: 1080; height: 1920; frameRate: 30; durationMs: number };
  currentDraftRevision: string;
  schemaVersion: 'timeline-1';
  latestOutputAssetId?: string;
  createdAt: string;
  updatedAt: string;
}

interface CreationOutputVO {
  projectId: string;
  outputAssetId: string;
  taskId: string;
  createdAt: string;
}

interface TimelineDraftVO {
  projectId: string;
  timelineDraftId: string;
  revision: string;
  schemaVersion: 'timeline-1';
  contentHash: string;
  timeline: TimelineDocument;
  savedAt: string;
}

interface SaveTimelineDraftResultVO extends TimelineDraftVO {
  replayed: boolean;
  superseded: boolean;
  operationResultRevision?: string;
  operationContentHash?: string;
  currentRevision?: string;
  normalizationChanges: TimelineNormalizationChange[];
}

interface TimelineVersionVO {
  versionId: string;
  projectId: string;
  versionNo: string;
  sourceDraftRevision: string;
  schemaVersion: 'timeline-1';
  contentHash: string;
  versionReason: 'manual_save' | 'restored' | 'render_input' | 'conflict_copy';
  sourceVersionId?: string;
  createdAt: string;
  replayed?: boolean;
}
```

初始化请求只允许 `sourceType`、`sourceId`、可选标题和幂等键，不接收脚本编号、脚本版本编号或媒体地址。草稿保存只允许幂等键、预期修订、Schema 版本和时间轴；服务端必须返回实际规范化并持久化的完整文档。`timeline-1` 的唯一结构定义是 `docs/contracts/creation-timeline/timeline-1.schema.json`。

冲突副本接口必须重新校验项目归属、素材归属和状态、Schema、字幕完整性、字体登记与安全区。在一个短事务中只创建不可变版本、对应版本素材引用和写回执，固定写入 `version_reason=conflict_copy`、`operation_type=conflict_version`、`source_draft_revision=baseRevision`，绝不修改当前草稿内容或修订号。同键同规范摘要回放原版本；同键异摘要返回 `46609 TIMELINE_IDEMPOTENCY_CONFLICT`。

### 创作素材资源

| 方法与路径 | 契约 |
| --- | --- |
| `GET /api/studio/creation-assets` | 按 `assetType?`、`status=ready`、`pageNum`、`pageSize` 查询当前用户素材 |
| `POST /api/studio/creation-assets` | `multipart/form-data`，只接收 `file`、`usageIntent`、`idempotencyKey`，返回 `CreationAssetVO` |
| `GET /api/studio/creation-assets/{assetId}` | 返回 `CreationAssetVO` |
| `GET /api/studio/creation-assets/{assetId}/content` | 认证后的受控二进制读取 |
| `DELETE /api/studio/creation-assets/{assetId}` | 引用检查通过后逻辑删除，返回 `R<Void>` |

`CreationAssetVO` 只包含字符串 `assetId`、`assetType`、`usageOrigin`、`status`、安全文件名、MIME、字节数、SHA-256、时长／宽高等已探测元数据和创建时间；不得包含 `storageKey`、文件系统路径、对象存储地址或签名 URL。

`content` 完整读取返回 `200`、正确 `Content-Type` 和 `Content-Length`；合法单段 `Range` 返回 `206`、`Accept-Ranges: bytes`、`Content-Range` 和对应 `Content-Length`；非法范围或多个 Range 返回 `416` 和 `Content-Range: bytes */{fullLength}`。该端点每次都校验认证、权限、归属、逻辑删除和 `ready` 状态。

### 第 6 步任务创建与统一任务中心

第 6 步只创建以下四种根任务，不新增时间轴专用任务查询接口：

| 创建路径 | `taskType` | 请求中的业务输入 |
| --- | --- | --- |
| `POST /api/studio/creation-projects/{projectId}/image-prompt-tasks` | `timeline_image_prompt_generate` | `idempotencyKey,expectedRevision,sourceSelection,style` |
| `POST /api/studio/creation-projects/{projectId}/fancy-text-suggestion-tasks` | `timeline_fancy_text_suggest` | `idempotencyKey,expectedRevision,sourceSelection,animationIntensity` |
| `POST /api/studio/creation-projects/{projectId}/subtitle-alignment-tasks` | `timeline_subtitle_align` | `idempotencyKey,expectedRevision,subtitleElementIds` |
| `POST /api/studio/creation-projects/{projectId}/render-tasks` | `timeline_render` | `idempotencyKey,expectedRevision,outputConfig` |

`sourceSelection` 只能是项目脚本快照中的 Unicode 码点起止范围或当前草稿中的字幕元素 ID 集合；服务端重新读取真实文本。`outputConfig` 只允许 `resolutionPreset=match_canvas`、`frameRate=30` 和 `qualityPreset=standard|high`，不得接收 `quality`、codec、像素格式、FFmpeg 参数、滤镜表达式、路径或 URL。任务创建成功统一返回 `AiTaskVO`。

`GET /api/studio/creation-projects/{projectId}/outputs/latest` 的 `CreationOutputVO` 业务字段只能为 `projectId`、`outputAssetId`、`taskId`、`createdAt`。不得返回 `assetId`、`mimeType`、`sizeBytes`、`previewUrl`、`downloadUrl`、`storageKey`、文件路径、对象存储地址或签名 URL；成品预览和下载均使用 `GET /api/studio/creation-assets/{outputAssetId}/content` 的受控二进制读取。

统一任务动作固定为：

```text
GET  /api/tasks
GET  /api/tasks/{taskId}
POST /api/tasks/{taskId}/cancellations
POST /api/tasks/{taskId}/retry
```

列表接受 `pageNum,pageSize,taskType?,status?,keyword?`，返回所有合法类型的当前用户根任务。取消请求精确且只含新的 `idempotencyKey`，返回取消后的真实任务；它是一次资源化请求，不允许前端预先改终态。主动重试请求精确且只含新的 `idempotencyKey`，创建并返回新的根任务，旧根任务、执行和尝试保持不可变。

```ts
interface AiTaskVO {
  taskId: string;
  taskType: string;
  resourceType: 'creation_project' | string;
  resourceId: string;
  inputVersionId?: string;
  status: 'pending' | 'queued' | 'running' | 'success' | 'failed' | 'cancelled';
  stage: string;
  progress: number;
  canCancel: boolean;
  canRetry: boolean;
  resultAssetId?: string;
  resultSchemaVersion?: string;
  result?: object;
  errorCode?: string;
  errorSummary?: string;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
}
```

任务响应不得暴露执行租约、Worker 标识、内部诊断、对象键、命令、路径或供应商原始响应。三类建议任务成功时返回对应强类型 `result` 且不返回 `resultAssetId`；`timeline_render` 成功时返回已就绪的 `resultAssetId` 且不返回建议 payload。

### 稳定错误码

| 错误码 | 稳定标识 | 语义 |
| --- | --- | --- |
| `46601` | `CREATION_PROJECT_NOT_FOUND` | 项目不存在、已删除或不属于当前用户 |
| `46602` | `CREATION_SOURCE_INVALID` | 第 5 步来源无效、未成功或不可访问 |
| `46603` | `TIMELINE_REVISION_CONFLICT` | 草稿修订号冲突 |
| `46604` | `TIMELINE_SCHEMA_UNSUPPORTED` | 时间轴 Schema 版本不受支持 |
| `46605` | `TIMELINE_DOCUMENT_INVALID` | 时间轴结构、时间、坐标或元素规则无效 |
| `46606` | `TIMELINE_ASSET_INVALID` | 素材不存在、失效、越权、被引用或类型不匹配 |
| `46607` | `TIMELINE_TEXT_INTEGRITY_FAILED` | 字幕少字、顺序、换行、标点或溢出校验失败 |
| `46608` | `TIMELINE_VERSION_NOT_FOUND` | 历史版本不存在或不属于项目 |
| `46609` | `TIMELINE_IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同规范请求 |
| `46610` | `TIMELINE_RENDER_UNAVAILABLE` | 合成能力不可用或输入无法合成 |
| `46611` | `TIMELINE_FONT_UNAVAILABLE` | 冻结版本要求的字体未登记或摘要不符 |
| `46612` | `CREATION_PROJECT_STATE_CONFLICT` | 当前项目状态不允许该操作 |

功能权限不足仍使用统一 403。错误响应不得包含堆栈、SQL、内部路径、命令、内部 URL、凭据或签名地址。
