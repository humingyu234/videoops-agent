# 人物形象库完整整改设计

## 1. 目标与范围

修复人物形象库代码审查确认的六项问题：WebP/GIF 数据库约束、状态分页、删除一致性、创建幂等、HTTP 契约漂移和前端中文错误文案分支。

本次仍只覆盖创作端个人形象；不新增公共形象、运营端、批量上传、换图、版本、草稿或形象生成能力。

## 2. 采用方案

采用已确认的方案 B：使用向前数据库迁移、RuoYi Entity/DTO/Mapper/Service 编排、数据库联表分页、同步两阶段删除、业务幂等和前后端契约同步完成定向整改。不引入上传会话、Outbox、后台补偿任务或新的业务分层。

## 3. 数据模型

- `av_asset.ck_av_asset_portrait_type` 允许 `jpeg`、`png`、`webp`、`gif`，继续限制单文件不超过 10MB。
- `av_portrait` 增加可空 `idempotency_key VARCHAR(64)` 和 `request_digest CHAR(64)`，历史记录保持 `NULL`。
- 唯一键为 `(workspace_id, owner_id, idempotency_key)`；MySQL 对 `NULL` 的唯一键语义允许历史记录共存。
- 新建向前迁移，不重写已执行的 `20260803_01_user_portrait.sql`。

## 4. 创建幂等

创建请求增加必填 `idempotencyKey`。Service 先规范化名称、性别、标签和备注，再对 `assetId` 与规范化字段计算 SHA-256 请求摘要。

- 同一用户、工作区和幂等键且摘要相同：返回原形象。
- 相同幂等键但摘要不同：返回 `46304 PORTRAIT_IDEMPOTENCY_CONFLICT`。
- 并发创建由唯一键兜底，捕获重复键后重新查询并执行同一判断。
- 前端同一文件和同一规范化表单重试时复用已上传的 `assetId` 和 `idempotencyKey`；文件或表单发生变化时生成新键。
- 创建事务回滚后，仅在再次确认素材未被任何形象引用时尝试清理该素材，不能删除并发请求已经绑定的素材。

## 5. 列表与详情

人物形象 Mapper 联表读取 `av_portrait` 和 `av_asset`，在数据库分页前应用用户、工作区、性别、状态和关键词条件。素材事实映射为：

- `verifying`、`delete_pending` -> `processing`
- `ready` -> `ready`
- `rejected`、`failed`、`delete_failed` -> `failed`
- 已逻辑删除记录不返回

非 `ready` 记录可以返回状态和失败原因，但不签发预览地址。列表和详情不得调用只接受 ready 素材的方法。

## 6. 删除一致性

删除使用同步两阶段编排，OSS 网络操作不处于数据库事务中：

1. 第一事务校验权限、归属和 `expectedRevision`，将素材状态置为 `delete_pending`。
2. 事务提交后幂等删除私有 OSS 对象。
3. 第二事务逻辑删除形象和素材记录。
4. OSS 失败时写入 `delete_failed` 并返回 `46211`；用户可用同一接口重试。
5. OSS 已删除而第二事务失败时，记录保留 `delete_pending`；再次请求利用对象删除幂等性完成收口。
6. 同一用户对已逻辑删除形象的重复删除返回成功；跨用户仍返回不存在。

## 7. HTTP 契约

- 列表 VO 不返回 `assetId` 或详情文件字段；返回 `portraitId/name/gender/sceneTags/availabilityStatus/failureCode/previewUrl/previewExpiresAt/recordRevision/createTime/updateTime`。
- 详情 VO 增加 `note/originalFileName/contentType/sizeBytes/width/height/fileFormat`，`sizeBytes` 为十进制字符串。
- 访问地址响应为 `url/expiresAt/contentType`。
- 创建请求增加 `idempotencyKey`。
- 同步 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 和本模块规格。

## 8. 前端状态

- 列表按 `ApiError.code` 或 `status` 判断 403，不解析中文消息。
- 点击卡片时调用详情接口，并显示加载、失败和重试状态。
- 创建失败保留文件、表单、已上传素材和幂等上下文；成功、关闭弹窗或更换文件时清理上下文。

## 9. 测试与验收

- 迁移契约测试覆盖 WebP/GIF 检查约束和幂等列、索引。
- Service 测试覆盖状态分页、非 ready 映射、相同/冲突幂等、并发唯一键、两阶段删除、OSS 失败和重复删除。
- Controller 测试覆盖列表/详情字段边界、创建幂等字段和访问地址结构。
- 前端测试覆盖 403 稳定错误码、详情加载和创建重试不重复上传。
- 运行受影响 Maven 测试、Vitest、TypeScript、Biome lint、文档规范验证和 `git diff --check`；真实 MySQL/Redis/OSS 能运行时执行本机受控集成验证，不能运行则明确报告。

## 10. 规格自检

- 无占位符或未决字段。
- 不改变已确认的产品范围。
- 删除、幂等、状态和 HTTP 字段在前后端保持单一语义。
- 后端保持 RuoYi 贫血 Entity 加 Service 编排，没有新增平行业务层。

## 11. 审查阻塞项收口（2026-08-04）

本轮仅关闭下列三个已经确认的阻塞项；列表预览地址 N+1 查询作为非阻塞优化留在 backlog，不扩大当前范围。

### 11.1 风险与任务卡

- 风险等级：红色。触发文件上传安全、私有文件删除、对象存储外部信任边界和重复请求一致性。
- 权威来源：本规格、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/AI_AGENT_GOVERNANCE.md` 与 RuoYi Plus AI Coding 规则。
- 允许修改：人物形象用户端 Controller、素材 Service、图片校验器、对应测试、依赖声明与公共契约文档。
- 不做范围：公共形象、运营端、批量上传、换图、版本、草稿、生成能力和 N+1 优化。
- 正向验收：创建重试进入业务幂等；不同文件上传互不误拦截；所有允许格式真实解码后才通过；数据库失败或事务回滚后删除刚上传对象；超过保留期且无引用的素材可被安全清理。
- 反向验收：同幂等键不同摘要仍返回 `46304`；伪造文件头、损坏压缩数据和超像素资源文件被拒绝；清理前后出现形象引用时不得删除对象；对象删除失败时保留可重试记录。
- 协作安排：单一实现者；完成后仅对本次差异做一次规格/契约自查和一次文件安全/数据补偿专项自查，不发起新的全量审查。

### 11.2 通用防重与业务幂等

- 上传、创建、删除接口移除 `@RepeatSubmit`。上传文件参数不参与通用防重摘要，会误判不同文件；创建和删除已经分别由业务幂等键、修订号和幂等删除语义负责。
- 更新接口保留 `@RepeatSubmit`，同时继续使用 `expectedRevision` 处理并发写入。
- 测试直接锁定 Controller 注解边界，防止后续再次把通用防重覆盖到上述三个接口。

### 11.3 图片真实解码与资源上限

- JPG/JPEG、PNG、WebP、GIF 均必须由对应 ImageIO Reader 读取宽高并真实解码首帧；仅解析文件头、容器块或尺寸字段不能判定为安全可用。
- WebP 引入可由 ImageIO 自动发现的纯 Java Reader，禁止依赖本机命令、临时服务或原生可执行文件。
- 解码前先读取尺寸并限制单边不超过 12000 像素、总像素不超过 25000000；超限返回 `46203 PORTRAIT_IMAGE_DIMENSIONS_EXCEEDED`，避免压缩炸弹在完整分配像素缓冲区后才被发现。
- 文件大小仍限制为 10MB；扩展名和 MIME 大小写归一化后校验，魔数与二进制内容保持精确匹配。

### 11.4 对象存储补偿与未绑定清理

- 素材上传到私有 OSS 后，数据库插入异常或外层事务最终回滚时，在事务完成回调中幂等删除本次新对象；补偿失败必须记录错误，不能覆盖原始业务异常。
- 创建失败沿用既有“再次确认无形象引用后清理”规则；已被任一形象引用的素材绝不进入清理。
- 对已上传但从未进入创建、客户端中断等遗留素材，提供内部定时清理：只选择创建超过 24 小时、类别为 `portrait_image`、未逻辑删除且不存在 `av_portrait.asset_id` 引用的记录，每批最多 100 条。
- 清理先在事务内再次确认无引用并把素材原子置为 `delete_pending`，再在事务外删除 OSS，最后逻辑删除数据库记录。对象删除失败写 `delete_failed`，后续批次可重试；并发创建看到非 `ready` 状态必须失败，从而避免绑定与清理竞态。
