# 用户端安全素材文件与阿里云 OSS 上传下载设计

## 1. 文档状态

- 日期：2026-08-03。
- 模块：素材管理 / 全局文件能力。
- 适用端：创作端 Web、`ai-video-user-api`、共享核心业务模块和基础设施模块。
- 设计状态：对话设计已确认，书面规格待用户审查。
- 风险等级：红色高风险。
- 实施前置：本文通过用户审查后，使用 `writing-plans` 生成实现计划。

## 2. 背景与目标

当前仓库保留了 RuoYi-Vue-Plus 的 `ruoyi-common-oss` 与管理端 `ruoyi-system` OSS 文件能力，
底层使用 AWS SDK for Java 2.x 的 S3 兼容客户端。管理端可以通过 `sys_oss_config` 切换存储配置，
并通过 `sys_oss` 保存通用文件元数据。

创作端当前没有真实的素材文件聚合、上传会话、用户及工作区归属、私有预览或下载接口；
`ai-video-user-api` 最终产物也没有装配 `ruoyi-common-oss` 或 `ruoyi-system`。现有 `sys_oss` 仅包含通用
OSS 元数据，不能表达创作用户、工作区、素材类别、引用关系、安全扫描状态和删除补偿，因此不能直接作为
创作端素材的业务事实表。

本设计建立统一的用户端安全文件链路，目标是：

1. 使用私有阿里云 OSS Bucket 保存图片、视频和音频文件。
2. 采用后端鉴权和预签名、前端直传的方式，避免 500MB 视频经过 API 服务中转。
3. 同时支持小文件单次 PUT 和大文件分片上传、进度、取消、恢复、过期及失败重试。
4. 文件完成后经过对象核验、媒体解析和恶意文件扫描，只有 `ready` 文件可以被预览、下载或用于生成。
5. 素材、文件、上传会话和所有访问凭证都按创作用户与工作区隔离。
6. 素材管理、图生数字人、视频数字人和声音克隆复用同一上传组件与后端契约。
7. 阿里云共享开发凭据直接写入并提交在两端 `application-dev.yml` 的服务端配置中，环境变量可选覆盖；不得进入前端、日志或 API 响应。

## 3. 已确认决策

### 3.1 存储与网络

- 存储服务：阿里云 OSS 的 S3 兼容接口。
- Bucket：`qc-test-01`。
- Region：`cn-shanghai`。
- S3 兼容 Endpoint：`s3.oss-cn-shanghai.aliyuncs.com`。
- 协议：HTTPS。
- 网络：公网。
- 环境：开发环境。
- 对象前缀：`ai-video`。
- 自定义域名：无。
- Bucket ACL：必须从公共读调整为私有后才能验收。
- 上传签名有效期：10 分钟。
- 预览和下载签名有效期：120 秒。

Bucket、Region、AccessKey ID 与 AccessKey Secret 统一保存并提交在两端 `application-dev.yml`；
`AI_VIDEO_OSS_ACCESS_KEY_ID` 与 `AI_VIDEO_OSS_ACCESS_KEY_SECRET` 仅作为可选覆盖。凭据不得进入前端、日志、API 响应或预签名 URL 的持久化记录。

### 3.2 上传方式

- 文件大小不超过 64MB 时使用单次预签名 PUT。
- 文件大小超过 64MB 时使用 Multipart Upload。
- 分片大小固定为 16MB，最后一片可以小于 16MB。
- 浏览器最多并发上传 4 个分片。
- 单个分片遇到可重试网络错误时自动重试 2 次，之后转为可手动恢复的失败状态。
- Multipart 的阿里云 UploadId 只保存在服务端，不返回前端。
- 前端只能用平台 `uploadId` 请求指定分片号的预签名 URL。

### 3.3 文件范围

| 类型 | 扩展名 | 最大大小 | 额外规则 |
| --- | --- | ---: | --- |
| 图片 | JPG、JPEG、PNG | 10MB | 必须能被图片解码器读取；数字人照片后续继续做人脸和清晰度检测。 |
| 视频 | MP4、MOV、AVI | 500MB | 必须由媒体解析器识别视频轨道，并读取时长、分辨率和编码信息。 |
| 音频 | MP3、WAV、M4A | 100MB | 建议时长 30 秒至 5 分钟；声音克隆入口必须执行音量、噪声、静音和清晰度检测。 |

浏览器 `accept` 只改善选择体验，不能成为安全判断。后端必须同时校验声明 Content-Type、扩展名、
对象头部特征和解码结果。

## 4. 范围与不做范围

### 4.1 本次范围

- 阿里云 OSS S3 兼容配置和 Java SDK 兼容修正。
- 创作端素材、文件对象和上传会话数据模型。
- 单次 PUT 与 Multipart 预签名、完成、取消、恢复和过期清理。
- 文件类型、大小、对象一致性、媒体元数据和恶意文件扫描。
- 素材列表、筛选、详情、预览、下载、重命名、标签和删除。
- 素材上传组件，并接入素材管理、图生数字人、视频数字人和声音克隆入口。
- 私有对象预览、Range 播放和下载授权。
- 权限、归属、审计、错误码、测试和阿里云真实冒烟验证。

### 4.2 不做范围

- 不让浏览器持有永久 AccessKey 或 STS Session Token。
- 不复用管理端 `/resource/oss/**` 作为创作端接口。
- 不把 `sys_oss` 改造成创作端素材事实表。
- 不在本任务实现 AI 生成、视频转码、图片缩略图生成、内容审核或声音克隆模型。
- 不在本任务修改额度、计费和生成任务状态机。
- 不创建 DDD、Clean Architecture、Hexagonal Architecture、`application`、`port`、`adapter`、
  `command` 或 `model` 等平行业务层。

视频预览优先使用原文件 Range 播放；后续生成转码预览文件时，仍通过本文的文件引用与授权机制接入。

## 5. 风险与最小任务卡

### 5.1 风险分级

本任务命中以下红色高风险条件：

- 上传与文件安全。
- 私有文件下载、预览和跨账号数据访问。
- 阿里云 OSS 外部信任边界与长期凭据。
- 公共 API、数据库结构、权限和缓存配置变化。
- 分片并发、幂等、超时、重试、删除补偿和恢复。

### 5.2 单一目标

交付可在开发环境真实连接私有阿里云 OSS 的创作端安全素材文件链路，保证所有上传、预览、下载、
修改和删除操作都经过后端认证、权限和资源归属校验。

### 5.3 允许影响的模块

- `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ARCHITECTURE.md`。
- `ai-video-api/ruoyi-common/ruoyi-common-oss` 的最小兼容配置。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core`。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra`。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user`。
- `ai-video-api/ai-video-user-api`。
- `docs/sql/ai-video/mysql`。
- `ai-video-ui/ai-video-webapp` 的素材服务、组件、页面和相关数字人/声音入口。

### 5.4 强制审查与验证

- 一次规格和契约审查。
- 一次独立文件安全专项审查。
- 修复后只复核差异及直接受影响测试。
- 后端单元测试、专用 MySQL/Redis 集成测试、双启动路由暴露 Smoke Test。
- 前端类型检查、格式检查、组件/Hook/adapter 测试和构建。
- 阿里云开发环境真实上传、分片、Range 预览、下载、取消和删除冒烟测试。

## 6. 总体架构

```text
ai-video-webapp
  -> /api/assets/**
    -> ai-video-user Controller / BO / VO
      -> ai-video-core asset/file/upload Service
        -> ai-video-core Mapper + MySQL
        -> ai-video-infra OSS provider
          -> ruoyi-common-oss S3 client
            -> private Alibaba Cloud OSS bucket
        -> ai-video-infra media inspector / malware scanner
```

职责边界：

- `ai-video-core/asset`：贫血 Entity、Mapper、`I...Service`、`service.impl` 和稳定 `*DTO`。
- `ai-video-infra`：OSS 预签名、Multipart、对象核验、媒体解析、ClamAV 调用等直接技术集成。
- `ai-video-user/asset`：创作端 Controller、BO、VO、`app` 权限和登录主体解析。
- `ai-video-user-api`：装配模块、环境配置和启动入口，不承载业务逻辑。
- `ai-video-webapp`：领域 Service、RuoYi adapter、上传 Hook、共享上传组件和页面组装。

核心业务不依赖具体阿里云类型；阿里云原始请求和响应只留在 `ai-video-infra` 的 provider/client 边界。

## 7. 数据模型

所有业务 ID 使用 `Long`，HTTP 中序列化为十进制字符串。三个表都按项目规则包含审计字段和 `del_flag`；
`owner_id`、`tenant_id`、`workspace_id` 均由服务端登录态和资源上下文派生。

### 7.1 `av_file_object`

稳定文件事实表，建议字段：

| 字段 | 含义 |
| --- | --- |
| `file_id` | 文件 ID。 |
| `tenant_id` / `workspace_id` / `owner_id` | 租户、工作区和创作用户归属。 |
| `storage_config_key` | 非敏感存储配置键，例如 `aliyun-dev`。 |
| `bucket_name` | 上传时实际 Bucket；用于配置变更后仍能定位旧对象。 |
| `object_key` | 服务端生成的稳定 Object Key。 |
| `original_name` | 用户原始文件名，仅用于展示与下载文件名。 |
| `extension` / `content_type` | 服务端核验后的扩展名和 MIME。 |
| `size_bytes` | OSS 核验后的文件大小。 |
| `etag` / `sha256` | 对象版本和内容摘要；Multipart ETag 不作为 MD5。 |
| `status` | `verifying/scanning/ready/rejected/delete_pending/deleted/delete_failed`。 |
| `width` / `height` / `duration_ms` | 媒体解析后的元数据。 |
| `scan_result_code` | 稳定扫描结果，不保存扫描器敏感原文。 |

`object_key` 唯一。任何 Entity/VO 都不得保存或返回永久公开 URL。

### 7.2 `av_upload_session`

| 字段 | 含义 |
| --- | --- |
| `upload_id` | 平台上传会话 ID。 |
| `file_id` | 预创建的文件记录。 |
| `mode` | `single` 或 `multipart`。 |
| `provider_upload_id` | 阿里云 Multipart UploadId，只在服务端保存。 |
| `part_size_bytes` / `part_count` | 分片大小和总分片数。 |
| `idempotency_key` | 用户范围内创建上传的幂等键。 |
| `status` | 上传会话状态。 |
| `expires_at` | 会话过期时间。 |
| `completed_at` / `cancelled_at` | 完成或取消时间。 |
| `failure_code` | 稳定失败码。 |

唯一键为 `(tenant_id, workspace_id, owner_id, idempotency_key)`。同一 scope 幂等键的文件名、大小、类型或业务上传上下文不一致时拒绝，
不得返回旧会话冒充成功。

### 7.3 `av_asset`

| 字段 | 含义 |
| --- | --- |
| `asset_id` | 素材 ID。 |
| `tenant_id` / `workspace_id` / `owner_id` | 素材归属。 |
| `file_id` / `thumbnail_file_id` | 原文件和可选缩略图引用。 |
| `asset_type` | `image/video/audio`。 |
| `category` | `general/brand/product/model/digital_human/voice_sample`。 |
| `name` | 素材展示名称。 |
| `source_type` | `user_upload/task_output/template`。 |
| `status` | `processing/ready/rejected/delete_pending/deleted/delete_failed`。 |
| `tags_json` | 有界标签集合；本次不单独建标签表。 |
| `reference_count` | 展示缓存，不替代删除前实时引用检查。 |

列表常用索引覆盖 `(workspace_id, owner_id, status, create_time)`、素材类型、分类和名称搜索。

## 8. 状态机

### 8.1 上传会话

```text
initialized -> uploading -> completing -> completed
      |            |             |
      +------------+-----------> failed
      |            |
      +------------+-----------> cancelled
      |            |
      +------------+-----------> expired
```

- 创建会话后为 `initialized`。
- 首个对象或分片上传后按前端查询与服务端事实表现为 `uploading`。
- 完成请求以条件更新进入 `completing`，同一会话只能有一个完成者。
- 完成成功后上传会话进入 `completed`，文件进入后处理状态。
- `completed/cancelled/expired` 不得回到非终态。

### 8.2 文件与素材后处理

```text
verifying -> scanning -> ready
    |           |
    +-----------+-> rejected

ready -> delete_pending -> deleted
                    +----> delete_failed -> delete_pending
```

- `verifying`：通过 HEAD/Object API 核验 Bucket、Object Key、大小、ETag 和 Multipart 完成事实。
- `scanning`：校验文件头、解码媒体、提取元数据并调用恶意文件扫描。
- 任一步骤失败都进入 `rejected`，对象不可访问，并由清理流程删除。
- 扫描器不可用时保持不可访问并记录稳定失败/重试状态，绝不自动进入 `ready`。
- 删除失败保留 `delete_failed` 和审计事实，由有界补偿任务重试，不静默丢失。

文件后处理不是 AI 生成任务，不进入用户任务中心；前端通过上传会话或素材详情查询状态。

## 9. Object Key 与凭据规则

Object Key 格式固定为：

```text
ai-video/dev/{workspaceId}/{yyyy}/{MM}/{dd}/{uuid}.{normalizedExtension}
```

- 不包含原始文件名、用户名、手机号或邮箱。
- 前端不得指定或覆盖 Object Key、Bucket、Region、ACL 或存储配置键。
- 预签名请求限制精确 HTTP Method、Object Key、Content-Type 和有效期。
- 日志不记录签名 URL、Query、AccessKey、Secret、UploadId 或完整 ETag 列表。
- 预签名 URL 不持久化到数据库、浏览器长期存储或错误上报系统。

## 10. API 契约

所有接口使用 `R<T>` / `R<PageResult<T>>`，`Long` ID 以字符串传输，且只接受 `app` 创作端令牌。
权限注解必须显式 `type = app`。

### 10.1 权限

- `aivideo:asset:query`
- `aivideo:asset:upload`
- `aivideo:asset:edit`
- `aivideo:asset:remove`
- `aivideo:asset:download`

默认个人创作者角色获得本人工作区的上述权限；权限不改变归属校验。

### 10.2 创建上传会话

`POST /api/assets/uploads`

请求字段精确为：

```text
fileName
declaredContentType
sizeBytes
assetType
category
idempotencyKey
```

`sizeBytes` 是十进制字符串。请求不得包含 owner、tenant、workspace、Bucket、Object Key、ACL 或服务商字段。

响应精确为 `CreateUploadSessionVO`：

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
```

`single` 必须返回 `singlePutUrl`，且不能返回 `partSizeBytes/partCount`；`multipart` 必须返回正整数 `partCount` 与十进制字符串 `partSizeBytes`，且不能返回 `singlePutUrl`。`requiredHeaders` 仅包含签名对象请求必须原样发送的非应用认证头。

### 10.3 获取分片签名

`POST /api/assets/uploads/{uploadId}/parts`

请求精确为 `{partNumbers:number[]}`，只包含去重、升序且位于 `1..partCount` 的 `partNumbers`，单次最多申请 20 片。响应精确为：

```ts
interface UploadPartSignaturesVO {
  uploadId: string;
  parts: Array<{
    partNumber: number;
    putUrl: string;
    expiresAt: string;
    requiredHeaders: Record<string, string>;
  }>;
}
```

后端必须校验上传会话归属、状态和有效期。

### 10.4 完成上传

`POST /api/assets/uploads/{uploadId}/complete`

- `single` 请求精确为 `{mode:'single'}`。
- `multipart` 请求精确为 `{mode:'multipart',parts:[{partNumber,etag}]}`，分片列表完整且升序。
- 后端调用 OSS Complete Multipart Upload 后再核验对象，不相信前端大小、类型或成功状态。
- 重复的相同完成请求返回同一文件/素材处理状态；不同分片集合的重复请求拒绝。

响应精确为 `{uploadId:string,fileId:string,assetId:string,uploadStatus:'completed',assetStatus:'processing'|'ready'|'rejected'}`。

### 10.5 取消和查询会话

```text
POST /api/assets/uploads/{uploadId}/cancel
GET  /api/assets/uploads/{uploadId}
```

取消操作幂等；Multipart 会话必须调用 Abort Multipart Upload。已经完成的会话不能取消。取消成功响应精确为 `{uploadId:string,status:'cancelled'}`。查询响应精确为 `{uploadId:string,fileId:string,mode:UploadMode,status:UploadSessionStatus,expiresAt:string,assetId?:string,assetStatus?:UploadAssetStatus,failureCode?:string}`。

未知网络结果重试必须沿用原 `idempotencyKey`。明确收到 `46212 UPLOAD_SESSION_EXPIRED` 后必须生成新的上传幂等键并创建新会话；旧会话保持终态，客户端按当前 `uploadId + requestGeneration` 丢弃迟到响应，不得让旧响应覆盖新文件或新业务上下文。

### 10.6 素材查询和编辑

```text
GET    /api/assets
GET    /api/assets/{assetId}
PUT    /api/assets/{assetId}
DELETE /api/assets/{assetId}
```

列表支持分页、关键词、素材类型、分类、标签、创建时间、尺寸和大小筛选，以及显式白名单排序。
修改请求只允许 `name` 和 `tags`。删除前必须实时检查草稿、任务、数字人和声音引用，并返回影响摘要；
确认删除使用请求中的独立 `confirmReferenceImpact`，不能仅依赖前端弹窗。

### 10.7 预览与下载授权

`GET /api/assets/{assetId}/access-url?disposition=inline|attachment`

- 要求 `ready` 状态、权限和当前工作区归属。
- `inline` 用于图片、音频和视频 Range 预览。
- `attachment` 增加安全编码后的原始文件名。
- 返回 `url`、`expiresAt`、`contentType` 和 `fileName`，有效期固定 120 秒。
- URL 过期后前端重新请求，不自动缓存为永久素材 URL。

## 11. 稳定错误

| 错误码 | 稳定标识 | 语义 |
| --- | --- | --- |
| `46201` | `FILE_TYPE_NOT_ALLOWED` | 扩展名、声明 MIME、文件头或解码类型不允许。 |
| `46202` | `FILE_SIZE_EXCEEDED` | 文件超过对应类型上限。 |
| `46212` | `UPLOAD_SESSION_EXPIRED` | 上传会话或签名已经过期；客户端废弃旧会话并使用新幂等键重建。 |
| `46204` | `UPLOAD_SESSION_CONFLICT` | 幂等键已被不同文件参数使用，或会话状态不允许操作。 |
| `46205` | `UPLOAD_PART_MISMATCH` | 分片编号、数量或 ETag 集合不匹配。 |
| `46206` | `FILE_OBJECT_MISMATCH` | OSS 对象大小、Key、Bucket 或完成事实不一致。 |
| `46207` | `FILE_SECURITY_SCAN_FAILED` | 恶意文件扫描或媒体安全校验未通过。 |
| `46208` | `FILE_SECURITY_SCAN_UNAVAILABLE` | 扫描服务不可用，文件保持不可访问。 |
| `46209` | `FILE_NOT_READY` | 文件仍在核验、扫描、删除或失败状态。 |
| `46210` | `ASSET_IN_USE` | 素材被草稿、任务、数字人或声音引用。 |
| `46211` | `ASSET_DELETE_FAILED` | OSS 删除失败，已进入补偿状态。 |

上述数值错误码在实施时同步登记到 `docs/API_CONTRACT.md`。前端只按数值码和稳定标识处理，不得解析中文消息。

## 12. 后端实现边界

### 12.1 `ai-video-core`

聚合目录使用：

```text
asset/domain
asset/dto
asset/mapper
asset/service
asset/service/impl
```

建议 Service：

- `IAssetService`：素材列表、详情、编辑、引用检查和删除编排。
- `IFileObjectService`：文件状态、核验、扫描结果和授权前检查。
- `IFileUploadService`：上传会话、幂等、完成、取消、过期和条件状态流转。
- `IObjectStorageService`：由 core 声明的稳定 OSS 技术契约，使用 `*DTO` 交换数据。
- `IFileSecurityScanService`：由 core 声明的扫描契约。

事务只包数据库状态和事件事实；OSS 网络调用、媒体解析和扫描不得处于长数据库事务中。

### 12.2 `ai-video-infra`

- 依赖并复用 `ruoyi-common-oss`，不复制 S3 客户端。
- 实现单 PUT、Multipart 初始化、分片签名、完成、终止、HEAD、GET 签名和删除。
- Java SDK 2.x 的阿里云配置必须使用虚拟主机风格，并显式禁用不兼容的 chunked encoding。
- 媒体解析使用受控 `ffprobe` 进程，必须设置超时、输出上限和参数白名单。
- 恶意文件扫描使用本机安装的 ClamAV daemon；开发默认地址 `127.0.0.1:3310`，连接失败时 fail closed。
- 扫描器和媒体工具输出只映射为稳定 DTO，不返回原始命令、路径或供应商错误。

本机开发和集成测试禁止使用 Docker、WSL、Testcontainers 或其他容器运行 OSS、ClamAV、MySQL 或 Redis。

### 12.3 `ai-video-user`

- Controller 只接参、校验、声明 `app` 权限并返回 `R<T>`。
- 使用 `AppLoginHelper` 派生唯一创作用户、租户和工作区。
- BO 拒绝未知字段；VO 不暴露内部 Bucket、Object Key、UploadId、签名参数或扫描器细节。
- 对完成、取消、删除等写操作记录安全审计，日志排除签名、ETag 列表和敏感配置。

## 13. 前端设计

### 13.1 模块边界

```text
src/services/ai-video/assets/
src/components/asset-upload/
src/pages/assets/
src/hooks/ 或页面内上传 Hook
```

依赖方向保持 `Page -> assetService -> RuoYi adapter / OSS transfer client`。页面不散写接口路径、Header、
状态字符串或错误码。

### 13.2 上传组件

统一 `AssetUploader` 使用 Ant Design `Upload.Dragger`、受控 `fileList` 和 `customRequest`：

- 素材管理支持批量选择和逐文件独立进度。
- 图生数字人、视频数字人和声音克隆入口默认单文件；成功后加入素材库并自动选中。
- `beforeUpload` 仅做即时类型和大小提示，真实结论以后端为准。
- 上传 Hook 管理 AbortController、分片队列、并发、重试和页面恢复。
- 页面卸载时取消仍在执行的浏览器请求，但不自动取消可恢复的服务端会话。
- 用户主动取消才调用后端取消接口。

前端展示状态：

```text
等待上传 / 上传中 / 暂停 / 校验中 / 安全扫描中
上传成功 / 上传失败 / 已取消 / 已过期
```

所有状态除颜色外必须有文本或图标语义；按钮可键盘访问并具有可访问名称。

### 13.3 素材管理页面

- 路由：`/assets`，进入用户端统一导航的“素材管理”。
- 管理页使用筛选区、类型标签、网格/列表、分页和上传操作区。
- 图片显示缩略图，视频显示时长和首帧占位，音频显示类型和时长。
- 点击素材打开详情 Drawer 或详情路由，支持预览、下载、重命名、标签、用于创作和删除。
- 页面必须覆盖加载、空、搜索无结果、失败、无权限、上传中、扫描中和删除补偿状态。
- 删除前展示后端返回的引用影响摘要，用户再次确认后才提交。

### 13.4 OSS 直传与 CORS

- OSS PUT 使用独立传输函数，不走普通 `R<T>` JSON adapter。
- 开发 Bucket CORS 只允许 `http://localhost:8000`。
- 允许 `PUT`、`GET`、`HEAD` 和预检所需方法；请求头只开放签名所需集合。
- 暴露 `ETag`、`Content-Length`、`Content-Range`、`Accept-Ranges` 等预览与完成所需 Header。
- 禁止使用 `AllowedOrigin=*`，生产域名必须通过独立环境配置显式加入。

## 14. 安全扫描与后处理

完成上传后按顺序执行：

1. OSS HEAD 核验对象与上传会话一致。
2. 读取有界头部识别真实文件类型，拒绝双扩展名和伪造 MIME。
3. 图片执行安全解码；视频和音频使用 `ffprobe` 有界解析。
4. ClamAV 扫描完整对象；扫描临时文件使用受控目录、随机文件名和清理保障。
5. 将安全结果和媒体元数据写回文件与素材。
6. 成功后进入 `ready`；失败进入 `rejected` 并安排对象清理。

扫描处理必须有租约或条件状态更新，避免两个实例重复发布结果。相同文件的恢复只续接同一处理事实，
不得创建第二个素材或把终态改回处理中。

## 15. 删除与补偿

- 第一次删除在事务中锁定素材，重新检查引用并将素材和文件标记为 `delete_pending`。
- 事务提交后删除 OSS 对象。
- 删除成功后逻辑删除素材和文件，保留安全审计。
- 删除失败进入 `delete_failed`，素材不再签发访问 URL，补偿任务按有界退避重试。
- 用户重复删除返回当前状态，不重复创建删除操作。
- 补偿超过上限后保留审计和运营告警，不伪造成功。

## 16. 阿里云配置与最小权限

RAM 用户只能访问开发 Bucket `qc-test-01` 及 `ai-video/*` 前缀，至少具备：

- 对象写入和 Multipart 操作。
- 对象读取与 HEAD。
- 对象删除和终止 Multipart。
- 完成验证所需的最小 Bucket/对象读取权限。

不授予 RAM 管理、其他 Bucket 或阿里云主账号权限。Bucket ACL、CORS 和 RAM 策略变更属于外部状态，
实施时必须明确展示精确目标和变更内容后再执行。

## 17. 前后端协作顺序

1. 先更新 `API_CONTRACT`、`DOMAIN_MODEL` 和 `ARCHITECTURE`，冻结表、状态、错误码和端点。
2. 后端完成 Entity/Mapper/Service、迁移和 Fake ObjectStorage 实现，前端可使用冻结 mock 并行开发。
3. `ai-video-infra` 接入阿里云与 ClamAV，完成 provider 单元测试。
4. 用户端 Controller 与 adapter 联调单次上传，再联调 Multipart、扫描、访问 URL 和删除。
5. 素材管理页完成后，把统一上传组件接入图生数字人、视频数字人和声音克隆入口。
6. 完成真实阿里云冒烟、安全专项审查和交付验证。

可以 mock 先行：素材列表、上传状态、进度、错误、扫描和引用影响摘要。

必须等待真实后端：预签名格式、Multipart ETag 完成、归属拒绝、扫描结果、访问 URL 和删除补偿。

## 18. 验收矩阵

### 18.1 成功路径

- JPG/PNG 小文件单 PUT 成功，经过核验和扫描后进入素材列表。
- MP3/WAV/M4A 上传并提取时长。
- 65MB 以上文件自动进入 Multipart；500MB 上限文件可完成上传。
- 页面刷新后恢复未过期会话和已完成分片。
- 图片、音频和视频可通过短期 URL 预览；视频支持 Range。
- 下载使用附件文件名，URL 过期后重新授权。
- 重命名、标签、分页、筛选、排序和列表/网格切换符合 PRD。

### 18.2 反向与安全场景

- 无令牌、运营端令牌、错误 `clientid`、过期令牌全部拒绝。
- 有权限但跨用户或跨工作区读取、签名、完成、取消、编辑和删除全部拒绝。
- 前端伪造 owner、Bucket、Object Key、ACL、大小、Content-Type 或 UploadId 被拒绝。
- 幂等键参数冲突、重复完成不同 parts、遗漏分片、重复 ETag 和越界分片被拒绝。
- 签名过期、会话过期和完成后取消有稳定错误。
- 文件扩展名、MIME、魔数或解码结果不一致被拒绝。
- ClamAV 不可用或命中恶意内容时不签发访问 URL。
- `verifying/scanning/rejected/delete_pending/delete_failed` 都不能预览、下载或用于生成。
- 私有对象不经平台授权不能匿名访问。
- 日志、响应、前端产物和数据库业务表不包含 Secret 或完整预签名 URL。

### 18.3 故障与恢复

- 单分片网络失败自动重试不超过 2 次，之后允许手动继续。
- 取消 Multipart 后 OSS 不保留未完成上传。
- OSS Complete 成功但数据库提交失败时，恢复任务能识别孤儿对象并补偿。
- 数据库完成但 OSS 删除失败时进入 `delete_failed`，不会继续暴露文件。
- 多实例同时完成、扫描或删除时只有一个条件更新成功。
- 过期会话清理不影响已完成文件，也不删除其他用户对象。

## 19. 验证命令与证据

实现计划必须给出精确命令；最低门禁为：

- 文档规范：`scripts/validate-development-standards.ps1`。
- 后端：受影响 Maven 模块单元测试、`ai-video-core` 本机专用 MySQL/Redis 集成测试、
  `ai-video-user-api` 构建及用户端/运营端路由隔离 Smoke Test。
- 前端：`ai-video-webapp` 受影响测试、TypeScript、Biome 和构建。
- 安全：凭据扫描、跨账号反向集成测试、签名过期/重放、Multipart 冲突、扫描 fail-closed。
- 外部：私有 Bucket 小图片、65MB 以上 Multipart、Range 预览、下载、取消和删除真实冒烟。

每项验证记录命令、退出码、执行测试数量和失败数量；无法运行的门禁必须明确标记未完成。

## 20. 上线、回滚与清理

- 先部署数据库和后端但保持创作端上传入口关闭。
- 配置轮换后的开发 RAM 凭据、私有 Bucket ACL、CORS、ClamAV 和 `ffprobe`。
- 执行后端真实冒烟后再开放素材上传入口。
- 回滚时关闭入口和签名签发，不删除已完成对象或业务记录。
- 未完成 Multipart 由过期清理终止；孤儿对象按仅限本次前缀的清单补偿，不执行 Bucket 全量删除。
- 禁止使用 `FLUSHALL`、`FLUSHDB`、Bucket 全量删除或宽泛通配清理。

## 21. 规格自检

- 完整性：不存在占位标记、待定字段或未分配的行为语义；数值错误码已经冻结。
- 一致性：私有 Bucket、短期授权、上传状态、后处理状态、删除补偿和前后端展示一致。
- 范围：聚焦安全文件底座、素材管理和三个既有上传入口，不扩展 AI 生成、转码、计费或内容审核。
- 安全：凭据、签名、Object Key、用户与工作区归属、扫描 fail-closed 和反向测试均有明确约束。
- 分层：遵循 RuoYi Entity + Service 编排和既有模块依赖方向，没有新增平行业务层。
