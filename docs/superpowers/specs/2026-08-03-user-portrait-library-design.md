# 用户端人物形象库设计

## 1. 文档状态

- 日期：2026-08-03。
- 模块：用户端“形象”菜单 / 人物形象库。
- 适用端：`ai-video-webapp`、`ai-video-user-api`、`ai-video-core` 与用户端文件能力。
- 设计状态：对话设计已确认，等待书面规格审查。
- 风险等级：红色高风险。
- 实施前置：本文通过用户审查后，使用 `writing-plans` 生成实现计划。

## 2. 背景与目标

当前 `/studio` 已有“形象”菜单、卡片网格、搜索与筛选、创建弹窗和详情抽屉，主要实现位于：

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- `ai-video-ui/ai-video-webapp/digital-human-studio.html`

现有页面使用 `AVATARS` 静态数据，上传、批量上传、预览、编辑和删除仍是弹窗或消息模拟；用户端后端也没有人物形象聚合或 `/api/portraits` 实现。

本设计把现有页面生产化，目标是：

1. 保持当前“顶部筛选 + 卡片网格 + 详情抽屉”的视觉基线。
2. 只实现创作端用户自己的人物形象，不实现公共形象和运营端管理。
3. 支持单张人物照片上传、类型一致性核验、格式对应的类型安全校验、列表、详情、预览、元数据编辑和不可恢复删除。
4. 所有数据按当前创作用户和当前工作区隔离，文件保持私有并通过短期授权地址访问。
5. “创作 → 选择形象与声音”读取同一人物形象列表，只在当前页面会话中选择，不实现草稿持久化。

## 3. 已确认决策

### 3.1 产品范围

- 仅支持用户上传人物照片创建形象。
- 暂不提供公共形象、公共形象初始化数据或“我的 / 公共”筛选。
- 暂不提供运营端 Controller、权限、页面或管理入口。
- 暂不提供批量上传。
- 暂不提供换图、形象版本、回收站或恢复。
- 用户需要更换图片时，必须删除原形象后重新创建。
- 暂不实现草稿表、草稿保存、形象文件快照或草稿引用检查。
- 创作步骤可以选择“可使用”形象，但选择仅存在于当前页面会话。
- 暂不提供 AI 生成人像、数字分身训练或视频形象克隆。

### 3.2 文件校验范围

允许的图片格式为 JPG、JPEG、PNG、WebP 和 GIF，单张最大 10MB。扩展名和声明 MIME
使用 `Locale.ROOT` 或等价方式做大小写不敏感匹配；文件头按原始字节精确匹配，不做大小写转换。服务端只执行：

- 扩展名白名单校验。
- 声明 MIME 白名单校验。
- 文件头真实类型识别。
- 扩展名、MIME、文件头和解码或结构解析结果一致性校验。
- JPEG、PNG、GIF 使用受限资源的图片解码器确认文件可以安全解码并提取尺寸。
- WebP 校验完整 RIFF 长度、`WEBP` 标识、合法 VP8/VP8L/VP8X 结构和正宽高，不新增 WebP 解码依赖。

明确不执行：

- 恶意文件扫描。
- 人脸检测。
- 清晰度检测。
- 构图检测。
- 图片内容审核。

这是用户明确确认的形象模块范围，和
`docs/superpowers/specs/2026-08-03-secure-asset-oss-upload-download-design.md`
当前描述的全量恶意文件扫描规则不同。实施前必须同步修订公共文件契约，明确人物形象图片采用
`portrait_image_type_only` 校验配置；不得把该例外静默扩展到视频、音频或其他素材类别。

### 3.3 公共和历史资源

- 不创建公共形象记录。
- 不保留用户可见或可恢复的形象版本。
- 不存在换图接口或后台替换状态机。
- 已生成作品属于其他模块的稳定结果，不因本模块删除形象而修改；本期不实现作品与形象的新增引用关系。

## 4. 范围与不做范围

### 4.1 本次范围

- 用户端人物形象数据模型和数据库迁移。
- 用户端列表、详情、创建、编辑、删除和短期预览授权接口。
- 单文件图片上传与人物形象创建编排。
- 当前用户、租户和工作区归属校验。
- 类型一致性与按格式执行的类型安全处理。
- 现有“形象”页面真实数据接入与完整页面状态。
- 创作步骤读取同一列表并进行会话内选择。
- API adapter、React Query Hook、mock、前后端测试和验证。

### 4.2 不做范围

- 公共形象和运营端功能。
- 批量上传。
- 草稿持久化和引用快照。
- 换图、版本、回收站和恢复。
- 人脸、清晰度、构图、内容审核和恶意文件扫描。
- AI 生成人像、模型训练、视频形象克隆和任何生成任务。
- 额度、计费和任务中心状态变化。
- 下载入口；形象页只提供受控预览。
- DDD、Clean Architecture、Hexagonal Architecture 或 `application`、`port`、`adapter`、`command`、`model` 等平行业务层。

## 5. 风险与最小任务卡

### 5.1 风险等级

本任务为红色高风险，触发依据是：

- 私有用户图片的上传、预览和删除。
- 用户、租户和工作区归属及跨账号访问。
- 阿里云 OSS 外部信任边界和短期访问授权。
- 数据库表、公共 API、错误码和权限变化。
- 删除失败补偿和并发更新。
- 对公共文件安全规则增加受限校验配置。

### 5.2 单一目标

交付一个仅面向创作端用户、只管理本人当前工作区人物照片的生产级形象库，保证所有写入和文件访问都经过认证、权限、归属和类型安全校验。

### 5.3 允许影响的模块

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ARCHITECTURE.md`
- `docs/ASYNC_TASKS.md` 的非任务声明
- `docs/superpowers/specs/2026-08-03-secure-asset-oss-upload-download-design.md`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user`
- `ai-video-api/ai-video-user-api`
- `docs/sql/ai-video/mysql`
- `ai-video-ui/ai-video-webapp`

不允许影响 `ruoyi-admin`、`ai-video-platform-ui`、公共形象或草稿业务。

### 5.4 审查与验证

- 一次规格和公共契约审查。
- 一次独立文件访问安全专项审查。
- 修复后只复核原发现和直接受影响测试。
- 后端单元测试、本机专用 MySQL/Redis 集成测试、用户端/运营端路由隔离 Smoke Test。
- 前端 adapter、Hook 和页面测试、TypeScript、Biome 与构建。
- 私有文件、跨账号访问、短期地址过期、类型伪造和删除失败反向验证。

## 6. 总体架构

```text
ai-video-webapp /studio 形象菜单
  -> portraitService
    -> RuoYi adapter
      -> /api/portraits/**
        -> ai-video-user portrait Controller / BO / VO
          -> ai-video-core IPortraitService
            -> PortraitMapper + MySQL
            -> IAssetService / IFileUploadService
              -> private OSS object
              -> portrait_image_type_only validator
```

职责边界：

- `ai-video-core/portrait`：人物形象 Entity、DTO、Mapper、Service 接口和 Service 实现。
- `ai-video-core/asset`：上传会话、文件对象、素材状态、短期访问地址和删除补偿的唯一事实源。
- `ai-video-user/portrait`：创作端 BO、VO、Controller、`app` 权限和当前主体解析。
- `ai-video-user-api`：装配用户端模块和配置，不承载业务逻辑。
- `ai-video-webapp`：页面、领域 Service、adapter、查询 Hook 和单文件上传组件。

不新增运营端 HTTP 边界，也不把用户端 Controller 装配到 `ruoyi-admin`。

## 7. 数据模型

### 7.1 `av_portrait`

人物形象是独立业务资源，图片文件仍由素材与文件表管理。建议字段：

| 字段 | 含义 |
| --- | --- |
| `portrait_id` | 人物形象 ID，HTTP 中使用十进制字符串。 |
| `tenant_id` | 当前工作区租户，由服务端派生。 |
| `workspace_id` | 当前工作区 ID，由服务端派生。 |
| `owner_id` | 当前 `app_user` ID，由服务端派生。 |
| `asset_id` | 唯一关联的图片素材 ID。 |
| `name` | 展示名称，去除首尾空白后 1–80 个字符。 |
| `gender` | `female`、`male` 或 `unspecified`。 |
| `scene_tags_json` | 最多 8 个去重标签，每项去除首尾空白后 1–20 个字符。 |
| `note` | 可选备注，最多 500 个字符。 |
| `idempotency_key` | 创建幂等键；历史记录可为空，新建记录必填。 |
| `request_digest` | 规范化创建请求的 SHA-256 摘要；历史记录可为空。 |
| `record_revision` | 乐观并发修订，HTTP 中使用十进制字符串。 |
| 审计字段 | 采用项目统一创建、更新和逻辑删除字段。 |

约束：

- `asset_id` 全局唯一，一个素材不能绑定多个人物形象。
- `(workspace_id, owner_id, idempotency_key)` 唯一；相同键不同摘要返回 `46304`。
- 列表常用索引覆盖 `(workspace_id, owner_id, del_flag, create_time)` 和名称搜索。
- 请求不得包含或覆盖 `tenant_id`、`workspace_id`、`owner_id`、文件状态或审计字段。
- `av_portrait` 不复制文件格式、大小、宽高、对象 Key、Bucket、短期 URL 或文件处理状态。

### 7.2 展示状态

人物形象不持久化第二套可用状态。VO 的 `availabilityStatus` 从关联素材/文件状态派生：

| 文件事实 | 形象展示状态 | 行为 |
| --- | --- | --- |
| `verifying` | `processing` | 可查看处理提示，不可预览或选择。 |
| `ready` | `ready` | 可预览、编辑和在创作页选择。 |
| `rejected` | `failed` | 展示稳定失败原因，不可预览或选择，可删除。 |
| `delete_pending` | `processing` | 禁止其他操作，等待删除结果。 |
| `delete_failed` | `failed` | 展示删除失败，可重试删除。 |
| `deleted` | 不返回 | 从普通列表和详情中消失。 |

前端不得自行把上传完成推断为 `ready`。

## 8. 文件处理与访问

### 8.1 创建流程

```text
选择单张 JPG/JPEG/PNG/WebP/GIF
-> 前端即时校验扩展名和 10MB 上限
-> POST /api/assets/uploads/portrait-images 直接上传单个 multipart 文件
-> 服务端校验类型一致性和格式结构后写入私有对象
-> 素材进入 ready，校验失败则不创建素材记录
-> POST /api/portraits 绑定 assetId 和人物形象资料
-> 前端失效列表查询并展示服务端状态
```

浏览器即时校验只改善体验，不能替代服务端结论。服务端不得信任客户端上传成功、扩展名、MIME、宽高或素材归属。

### 8.2 校验配置

`portrait_image_type_only` 只允许：

- `jpg` / `jpeg` + `image/jpeg`
- `png` + `image/png`
- `webp` + `image/webp`
- `gif` + `image/gif`
- 大小不超过 10MB

扩展名和 MIME 使用 `Locale.ROOT` 或等价方式忽略大小写，文件头必须按二进制精确匹配，
不得对文件头做大小写处理。验证器必须使用有界读取；JPEG、PNG、GIF 使用受限资源的
图片解码器确认可以安全解码并提取正宽高，WebP 则校验完整 RIFF 长度、`WEBP` 标识、
合法 VP8/VP8L/VP8X 结构和正宽高，不新增 WebP 解码依赖。类型不一致、损坏或结构非法的
文件均拒绝，并继续拒绝 BMP、SVG、HEIC/HEIF 和 AVIF；不调用 ClamAV，不执行人脸、
清晰度、构图或内容审核，仍只做类型安全校验。

### 8.3 预览

- Bucket 保持私有。
- 列表可为当前页 `ready` 形象返回 120 秒短期 `previewUrl` 和 `previewExpiresAt`。
- `GET /api/portraits/{portraitId}/access-url` 用于过期后刷新当前形象的短期预览地址。
- 地址签发前重新校验 `app` 会话、权限、当前工作区归属、人物形象和文件 `ready` 状态。
- 短期 URL 不写入数据库、本地长期存储、日志或错误上报。

## 9. API 契约

所有接口使用 `R<T>` 或 `R<PageResult<T>>`，只接受 `app` 创作端令牌。`Long` ID 和修订使用 JSON 十进制字符串。

### 9.1 权限

- `aivideo:portrait:query`
- `aivideo:portrait:add`
- `aivideo:portrait:edit`
- `aivideo:portrait:remove`

创建还需要统一素材上传权限。权限不替代用户与工作区归属校验。

### 9.2 分页列表

`GET /api/portraits`

查询字段：

```text
pageNum
pageSize
keyword?
availabilityStatus?  # processing|ready|failed
gender?              # female|male|unspecified
```

- 默认 `pageNum=1`、`pageSize=12`，最大 `pageSize=48`。
- `keyword` 匹配名称和场景标签。
- 固定按创建时间倒序排列。
- 空页返回 `rows=[]`。

列表项字段：

```text
portraitId
name
gender
sceneTags
availabilityStatus
failureCode?
previewUrl?
previewExpiresAt?
recordRevision
createTime
updateTime
```

只有 `ready` 项可以包含短期预览地址。

### 9.3 详情

`GET /api/portraits/{portraitId}`

在列表项字段基础上增加：

```text
note
originalFileName
contentType
sizeBytes
width
height
```

`sizeBytes` 使用十进制字符串。详情不返回 `assetId`、Bucket、Object Key、签名参数或内部文件状态。

### 9.4 创建

`POST /api/portraits`

请求字段精确为：

```text
assetId
name
gender
sceneTags
note?
idempotencyKey
```

服务端必须验证：

- 素材属于当前用户和当前工作区。
- 素材分类为 `portrait_image`，来源为当前用户单文件上传。
- 素材未删除且没有绑定其他形象。
- 名称、性别、标签、备注满足约束。
- 未知字段在进入业务 Service 前拒绝。

同一用户、工作区和 `idempotencyKey` 的相同请求返回原形象；请求摘要不一致返回 `46304`。

### 9.5 编辑

`PUT /api/portraits/{portraitId}`

请求字段精确为：

```text
expectedRevision
name
gender
sceneTags
note?
```

编辑只修改业务资料，不允许修改 `assetId`、文件或归属。修订不一致返回 `46303`，前端刷新详情并要求用户重新确认。

### 9.6 删除

`DELETE /api/portraits/{portraitId}?expectedRevision={revision}`

- 删除前校验当前用户、工作区、权限和修订。
- Service 先把素材进入删除流程，提交后删除私有 OSS 对象，再逻辑删除人物形象、素材和文件记录。
- 删除成功后前端移出列表，不提供恢复。
- OSS 删除失败使用既有有界补偿规则，不伪造成功；返回 `46211 ASSET_DELETE_FAILED`。
- 当前用户对已删除形象的相同删除重试返回已删除结果，保持幂等；其他用户不能据此判断资源是否存在。

### 9.7 刷新预览地址

`GET /api/portraits/{portraitId}/access-url`

响应字段精确为：

```text
url
expiresAt
contentType
```

只允许 `ready` 图片的 inline 预览，不提供 attachment 下载。

## 10. 稳定错误

沿用公共文件错误：

| 错误码 | 稳定标识 | 页面行为 |
| --- | --- | --- |
| `46201` | `FILE_TYPE_NOT_ALLOWED` | 显示支持 JPG、JPEG、PNG、WebP、GIF，并保留表单。 |
| `46202` | `FILE_SIZE_EXCEEDED` | 提示单张最大 10MB。 |
| `46203` | `PORTRAIT_IMAGE_DIMENSIONS_EXCEEDED` | 图片单边或总像素超过人物图片资源上限。 |
| `46204` | `UPLOAD_SESSION_CONFLICT` | 停止当前上传，重新创建会话。 |
| `46206` | `FILE_OBJECT_MISMATCH` | 显示上传核验失败，不猜测成功。 |
| `46209` | `FILE_NOT_READY` | 刷新状态，不签发预览地址。 |
| `46211` | `ASSET_DELETE_FAILED` | 保留列表项并允许重试删除。 |

人物形象新增错误：

| 错误码 | 稳定标识 | 语义与页面行为 |
| --- | --- | --- |
| `46301` | `PORTRAIT_NOT_FOUND` | 形象不存在、已删除或不属于当前用户/工作区；显示不存在，不泄露归属。 |
| `46302` | `PORTRAIT_ASSET_INVALID` | 素材不存在、归属不符、类型/分类错误或已绑定；显示“图片素材不可用于创建形象”。 |
| `46303` | `PORTRAIT_REVISION_CONFLICT` | 编辑或删除修订冲突；刷新详情并要求重新确认。 |
| `46304` | `PORTRAIT_IDEMPOTENCY_CONFLICT` | 相同幂等键对应不同创建参数；生成新键并要求重新提交。 |

前端只按数字码和稳定标识分支，不解析中文 `msg`。

## 11. 后端实现边界

### 11.1 `ai-video-core`

标准目录：

```text
portrait/domain
portrait/dto
portrait/mapper
portrait/service
portrait/service/impl
```

- Entity 只承载持久化字段、表映射和简单内聚判断。
- `IPortraitService` 负责列表、详情、创建、编辑、删除、归属和并发编排。
- `PortraitServiceImpl` 使用素材 Service DTO，不导入用户端 BO/VO。
- 事务、归属、幂等、修订和删除状态编排只在 Service 中完成。
- OSS 网络删除、预签名和图片解码不得处于长数据库事务中。
- 不创建 `application`、`port`、`adapter`、`command` 或 `model` 业务层。

### 11.2 `ai-video-user`

标准目录：

```text
portrait/controller
portrait/domain/bo
portrait/domain/vo
```

- Controller 只接参、校验、声明 `app` 权限、解析当前主体和包装 `R<T>`。
- 使用 `AppLoginHelper` 派生唯一当前创作用户、租户和工作区。
- BO 拒绝未知字段；VO 不暴露素材 ID、内部对象信息或供应商字段。
- 只装配到 `ai-video-user-api`。

### 11.3 归属和权限

- 列表查询固定附加当前 `tenantId/workspaceId/ownerId`。
- 详情、编辑、删除和访问地址必须同时校验三类归属。
- 请求中的 ID 只用于定位候选资源，不能代替归属条件。
- 无令牌、运营端令牌、错误 `clientid`、过期令牌、缺少权限、跨用户和跨工作区访问均在数据库写入或 OSS 签名前拒绝。

## 12. 前端设计

### 12.1 页面结构

保持现有 `/studio` 形象菜单布局：

- 顶部搜索。
- 状态筛选：全部、处理中、可使用、处理失败。
- 创建形象按钮。
- 形象卡片网格。
- 分页。
- 详情抽屉。

删除“我的 / 公共”筛选、公共形象卡片和“批量上传”按钮。

### 12.2 模块边界

建议结构：

```text
src/services/ai-video/portraits/
  api.ts
  types.ts
  adapter.test.ts
src/pages/digital-human-studio/portraits/
  PortraitLibraryView.tsx
  PortraitCard.tsx
  PortraitCreateModal.tsx
  PortraitDetailDrawer.tsx
  hooks.ts
```

依赖方向固定为：

```text
Page / Component -> portraitService -> RuoYi adapter
                            -> AssetUploader -> OSS transfer client
```

页面不得拼接 URL、Header、状态字符串、错误码或 RuoYi envelope。

### 12.3 创建弹窗

- 单文件 `Upload.Dragger`，只允许 JPG、JPEG、PNG、WebP、GIF，最大 10MB；扩展名与浏览器 MIME 大小写不敏感。
- 名称必填。
- 性别为女、男、不指定，默认不指定。
- 场景标签和备注可选。
- 提交中禁用重复提交并展示进度。
- 只有后端确认创建成功后关闭弹窗和刷新列表。
- 上传或创建失败保留用户已填写资料，允许明确重试。

### 12.4 卡片和详情

- 卡片展示短期预览图、名称、性别、标签和状态。
- `processing` 显示处理中占位和文本，不显示假缩略图。
- `failed` 显示失败原因和删除入口，不允许预览或选择。
- 详情抽屉展示名称、性别、标签、备注、格式、尺寸、大小、创建时间和状态。
- 编辑只修改名称、性别、标签和备注。
- 删除使用二次确认；失败不从列表乐观移除。

### 12.5 创作步骤接入

- `AssetStep` 从同一人物形象查询 Hook 读取 `ready` 列表。
- 当前选择只保存在 `StudioState.selectedPortraitId`，不写后端草稿。
- 处理中和失败形象不可选择。
- 已选形象在当前会话被删除或查询不到时，清空选择并提示重新选择。
- 本期不得描述为已具备草稿恢复或跨页面持久化能力。

### 12.6 页面状态

必须覆盖：

- 首次加载骨架。
- 空形象，引导创建第一项。
- 搜索无结果，提供清除筛选。
- 列表失败和重试。
- 401 由集中 adapter 清理登录态并只跳转一次。
- 403 显示无权限，不跳转登录。
- 上传、类型核验、创建、编辑和删除进行中。
- 操作成功和失败。
- 分页切换和筛选后回到第一页。

## 13. 前后端协作顺序

1. 先同步 `API_CONTRACT`、`DOMAIN_MODEL`、`ARCHITECTURE` 和文件校验配置，冻结表、字段、端点、状态和错误码。
2. 后端建立 Entity、Mapper、Service、迁移和 Fake 文件校验实现。
3. 前端基于冻结 VO/BO 建立类型、adapter、mock、Hook 和现有页面接入。
4. 用户端 Controller 与前端联调上传、创建、列表、详情、编辑、访问地址和删除。
5. 接入真实私有 OSS，并完成类型伪造、跨账号和删除失败反向验证。
6. 完成一次规格/契约审查、一次文件访问安全专项审查和交付前验证。

可以 mock 先行：

- 分页列表和详情。
- `processing/ready/failed` 状态。
- 创建成功、类型失败、编辑冲突和删除失败。
- 401、403、空数据和搜索无结果。

必须等待真实后端：

- 当前用户与工作区归属拒绝。
- 上传签名和对象完成核验。
- 文件头/解码或结构解析类型结论。
- 短期访问地址。
- 删除与 OSS 失败补偿。

## 14. 验收矩阵

### 14.1 成功路径

- JPG、JPEG、PNG、WebP、GIF 小于等于 10MB 时可以完成上传并创建形象；扩展名和 MIME 的大小写变化不影响合法文件通过。
- 文件处于 `verifying` 时列表显示处理中，服务端进入 `ready` 后可预览和选择。
- 列表支持关键词、状态、性别、分页和白名单排序。
- 创建、详情、编辑和删除后 React Query 缓存按领域键失效并刷新。
- 名称、性别、标签和备注修改成功，图片保持不变。
- 删除成功后形象消失且无法再次预览。
- 创作步骤只显示或允许选择 `ready` 形象，选择仅在当前会话生效。

### 14.2 反向与安全场景

- 无令牌、运营端令牌、错误 `clientid`、过期令牌和缺少权限均被拒绝。
- 跨用户或跨工作区列表、详情、编辑、删除和访问地址均被拒绝，且不泄露资源存在性。
- 请求伪造 `tenantId/workspaceId/ownerId` 或文件事实字段时在进入 Service 前拒绝。
- 错误素材类型、错误分类、已删除素材和重复绑定素材返回稳定错误。
- 扩展名、MIME、文件头或解码/结构解析结果不一致时返回 `46201`；空 MIME、截断 GIF/WebP、BMP、SVG、HEIC/HEIF 和 AVIF 同样返回 `46201`。
- 扩展名和 MIME 使用 `Locale.ROOT` 或等价方式忽略大小写，文件头仍按原始二进制精确匹配，不做大小写转换。
- 损坏或无法安全解码的 JPEG/PNG/GIF，以及 RIFF 长度不完整、`WEBP` 标识错误、VP8/VP8L/VP8X 结构非法或宽高非正数的 WebP，均不能进入 `ready`。
- 超过 10MB 返回 `46202`。
- 处理中或失败文件不能签发预览地址，不能在创作步骤选择。
- 相同幂等键相同请求只创建一条形象；不同请求返回 `46304`。
- 并发编辑只有一个修订成功，其他请求返回 `46303`。
- 访问地址过期后必须重新向后端授权，不能作为永久 URL 使用。
- OSS 删除失败不能伪造成功，形象保留失败状态并允许重试删除。

### 14.3 前端状态

- 加载、空、搜索无结果、列表失败、403、分页均有独立可见状态。
- 创建表单防止重复提交，并保留失败前输入。
- 删除必须二次确认，失败时卡片不会错误消失。
- 请求取消不显示为普通业务失败。
- 状态除颜色外同时使用文本或图标语义，操作支持键盘访问和可访问名称。

## 15. 验证门禁

实现计划必须给出精确命令，最低门禁为：

- 文档：`scripts/validate-development-standards.ps1`。
- 后端：受影响 Maven 模块单元测试、`ai-video-core` 本机专用 MySQL/Redis 集成测试、`ai-video-user-api` 构建。
- 路由：用户端人物形象接口可用，运营端启动产物不暴露 `/api/portraits/**`。
- 前端：形象 adapter/Hook/页面测试、TypeScript、Biome 和构建。
- 安全：凭据扫描、跨账号/跨工作区、伪造归属、类型不一致、短期地址过期和删除失败反向测试。
- 外部：私有 OSS 单张 JPG/JPEG/PNG/WebP/GIF 上传、预览地址刷新和删除冒烟测试。

每项验证记录命令、退出码、执行测试数量和失败数量；无法运行的门禁必须明确标记未完成。

## 16. 公共文档同步

实施计划的第一批任务必须先更新：

- `docs/API_CONTRACT.md`：人物形象端点、字段、状态、短期预览和 `463xx` 错误码。
- `docs/DOMAIN_MODEL.md`：`av_portrait`、素材唯一绑定、归属、修订和派生状态。
- `docs/ARCHITECTURE.md`：人物形象聚合依赖素材/文件能力的方向。
- `docs/ASYNC_TASKS.md`：明确人物形象文件类型核验不是 AI 生成任务，不进入任务中心。
- `docs/superpowers/specs/2026-08-03-secure-asset-oss-upload-download-design.md`：登记受限的 `portrait_image_type_only` 校验配置，并移除人物形象入口对 ClamAV 的依赖；其他素材规则不变。

文档规范变更后运行 `scripts/validate-development-standards.ps1`。

## 17. 规格自检

- 完整性：页面、字段、端点、状态、权限、归属、文件规则、错误、删除和验收均有明确结论。
- 一致性：人物形象不持久化第二套文件状态；前端展示状态从文件事实派生。
- 范围：没有公共形象、运营端、批量上传、草稿、换图、版本、恢复或生成能力。
- 安全：私有对象、短期访问、跨账号拒绝、类型一致性、JPEG/PNG/GIF 受限安全解码和 WebP 结构校验均有反向验收。
- 分层：后端遵循 RuoYi 贫血 Entity + Service 编排，没有平行业务层。
- 可实施性：前后端切分、mock 边界、真实后端依赖和验证门禁均已说明。
