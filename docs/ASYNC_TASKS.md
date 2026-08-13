# 异步任务契约

本文档只定义异步任务的公共治理规则，不定义具体任务接口、轮询间隔、错误码全集或计费策略。

## 适用原则

所有长耗时生成能力都必须使用异步任务承载。

适用场景包括：

- 视频生成。
- 数字人生成。
- 声音克隆。
- 图片生成。
- 后续新增的其他长耗时 AI 生成能力。

### 数字人纵链受限过渡例外

2026-08-03 项目负责人批准声音克隆加数字人视频纵链在 P0-C 统一任务中心接入前采用以下最小例外：

- 仅 `POST /api/studio/voice-jobs`、声音确认、`POST /api/studio/video-jobs` 及其任务／媒体查询使用临时
  `av_dh_generation_job`；本批不接额度、计费流水、草稿、共享、运营端、通知、搜索、其他模型或 P3。
- 仍必须完成参数、`app` 权限、当前租户和 owner、幂等、父声音任务、文件格式及大小校验；省略额度只适用于本条
  已批准范围，不能传播到其他生成能力。
- 声音任务可由 Java 进程内执行器运行，视频状态可由查询接口推进。执行器拒绝必须立即落入失败终态；Provider
  暂时故障只允许有界重试。从 `create_time` 起计，声音任务的总截止时间为 10 分钟，视频任务为 60 分钟；查询接口发现
  `queued/running` 已超时时，必须使用旧状态 CAS 写入失败终态，不得因 Provider 持续返回 `running` 而无限延长。
- 相同幂等键并发由数据库唯一键仲裁；只有获胜任务可以调用已登记 Provider。状态写入必须使用旧状态条件，终态
  不可回退，输出媒体不得重复落盘或被过期轮询覆盖。
- F1 前受限真实联调只允许已登记的 IndexTTS2 和 ComfyUI，使用非敏感短样例；不得调用搜索或其他模型，不得在
  日志、测试证据或响应中输出认证信息。
- 退出条件是 P0-C 统一任务执行器、任务中心和相应额度策略可用。届时按
  [DOMAIN_MODEL.md](DOMAIN_MODEL.md) 的前向迁移规则吸收临时记录并停止新写，禁止长期双写两套任务事实。

## 创建任务前置校验

创建任务前，后端必须完成：

- 参数校验。
- 权限校验。
- 用户账号归属校验。
- 额度校验。
- 幂等校验。
- 文件、素材或外部资源引用校验。

前端可以做提交前校验和体验提示，但不能替代后端校验。

## 创建任务结果

创建任务成功后，后端必须返回可用于后续查询的任务标识，并持久化任务记录。

创建任务响应仍采用 RuoYi-Vue-Plus 的 `R<T>` 格式。任务响应 VO 至少应包含：

- `taskId`
- `status`
- `progress`

模块规格可根据业务需要补充预计消耗、来源对象、展示名称等字段。

## 幂等规则

创建生成任务必须支持幂等键：

- 前端提交时生成 `idempotencyKey`。
- 后端在 `ownerId + idempotencyKey` 范围内去重。
- 重复提交返回已有任务，不创建第二条任务。
- 用户或系统主动重试、重新生成（regenerate）必须生成新的 `idempotencyKey`，并创建新的根任务；不得把旧根任务改回非终态。

### 主动重试与租约恢复不是同一流程

- **主动重试／重新生成：** 它是新的业务意图，必须使用新 `idempotencyKey`，创建新的 `rootTaskId`、新的
  `executionTaskId`（该根下仍为 `executionNo=1`），收费任务还要创建新的 `usageOperationId`。重新生成的稳定
  `taskType` 仍为 `script_generate`，只有计价操作类型为 `script_regenerate`。旧任务、旧执行、旧用量操作与旧
  attempt 保持不可变，不得复活或搬到新根任务。
- **基础设施租约恢复：** 它只是同一次执行在 worker 崩溃或 lease 过期后的耐久恢复。必须复用原
  `rootTaskId`、原 `executionTaskId`、`executionNo=1` 和原 `usageOperationId`，根任务保持 `running`；只以条件
  更新领取新 lease，绝不创建新根任务、新执行任务、新用量操作、第二次额度锁或复制账单流水。
- 恢复扫描本身不创建 provider attempt。只有执行 handler 紧邻一次真实 `ModelProvider` 或
  `WebSearchClient` 调用前，才在同一 `executionTaskId` 下追加新的 attempt；调用序号单调递增并受模块上限
  约束。真实调用未发生时 attempt 数保持不变。
- `usageOperationId` 是一次收费业务操作的唯一计费身份。恢复、回调与重复投递必须继续使用同一个值；任何
  settle/release/usage 聚合都按该 ID 幂等，不得按金额猜测重复操作。

## 状态归属

- 任务状态、进度、失败原因和输出关联以后端任务记录为准。
- 前端只展示后端返回的任务状态，不自行推导终态。
- 外部 AI 服务或内部执行器不能直接作为前端状态源。
- 任务状态机以 `docs/DOMAIN_MODEL.md` 和模块规格确认为准。

## 进度更新方式

进度更新可以通过轮询、回调、队列消费、WebSocket 或 SSE 等方式实现。

公共约束：

- 无论使用哪种刷新方式，最终状态必须落到后端任务记录。
- 前端刷新频率、是否使用实时通道、何时停止刷新，由模块规格根据页面体验确定。
- WebSocket 或 SSE 只能作为刷新通道优化，不能替代后端任务持久化。

## 外部回调原则

如使用外部 AI 服务回调，必须满足：

- 回调带签名、共享密钥或其他可信校验机制。
- 回调能关联到平台内部任务。
- 回调处理必须幂等。
- 已终态任务不允许被外部回调覆盖为非终态。
- 回调原文、摘要或关键字段需要记录，方便排查。

回调可更新的字段由模块规格和后端任务模型确认。

## 终态保护

任务进入终态后，除人工干预或明确的重试流程外，不允许被自动流程改回运行态。

终态至少包括：

- `success`
- `failed`
- `cancelled`

主动重试／重新生成必须创建新根任务；只有前述 lease 恢复可在不改变根任务和执行任务身份的前提下续租并
追加真实 provider attempt。两条路径都不得把已终态任务直接改回运行中。

## 创建、入队、领取与恢复

- `IAiTaskService.createChargeableTask/createFreeTask` 只创建一个 `pending` 根任务及其唯一
  `executionNo=1` 执行任务，不自行投递。
- 阶段业务 Service 在同一外层事务严格执行
  `create → freeze immutable input → IAiTaskExecutionDispatcher.enqueue`；复用命中时立即返回，不再次冻结或
  入队。`enqueue` 必须要求已有事务，并原子地把同一父子对从 `pending` 改为 `queued`。
- 首次领取在同一事务把唯一执行任务和根任务从 `queued` 改为 `running`。过期恢复只更新原执行任务的 lease
  owner、到期时间和 lease 计数，根任务仍为 `running`。
- 所有状态变化都必须使用旧状态、父子关系及 lease owner 的条件更新；过期 worker 不得写结果、provider
  用量、计费终态或任务终态。扫描器是最终恢复来源，after-commit 唤醒只能作为加速。

## 生成任务组、锁序与分支继承

脚本生成上下文的任务组键精确为 `script:{draftId}:{branchRevision}`。公共写事务固定遵守全局锁序：

```text
draft → current_branch → operation_slot → quota_account → task_or_group_member
```

- `20260728_04a_p0c_task_group_guard.sql` 前向创建 `av_ai_task_group_member` 与
  `idx_av_ai_task_active_group`；成员唯一键为 `(tenant_id,task_group_key,root_task_id)`，来源只允许
  `origin|inherited`，创建者只允许 `app_user|sys_user`，root task 必须同租户。
- `IAiTaskService.requireGenerationContextWritable(Long draftId,Long branchRevision)` 在 P2 任何方向、答案、补充或
  事实决定写入前检查同组活跃生成/优化根任务；命中 `pending|queued|running` 时按
  [API_CONTRACT.md](API_CONTRACT.md) 返回 `46123`。
- `IAiTaskService.inheritQuestionnaireTaskGroupMembers(Long draftId,Long sourceBranchRevision,Long targetBranchRevision,List<Long> retainedRootTaskIds,TaskInitiatorDTO initiator)`
  只继承 membership。不得复制 `task`、`usage`、`ledger` 或 `operation_slot`；tenant、app user、resource、family
  与 source membership 必须全部匹配。完全相同重放幂等，partial、superset、conflict 或 target 已含 origin
  均 fail-closed。
- inherited 与 origin 汇总计费时按 `usageOperationId` 去重；禁止 `SUM(DISTINCT amount)`，因为两个合法操作
  可以具有相同金额。

F1 的原 `p0c-f1-handoff.json` 保持不可变；上述前向能力由 exact 12-field
`p0c-f1-contract-addendum.json` 绑定 `20260728_04a_p0c_task_group_guard.sql`、成员表、索引、枚举、锁序、任务
组键、`membership_only` 与禁止复制集合，并携带 source-signatures、migration-04a、independent-review 三项
live-SHA evidence。P1/P2/P3 在真实集成前必须同时验证原 handoff、addendum 和独立 `PASS` review，任何字段、
顺序、head、时间或 SHA 漂移都 fail-closed。

## 额度处理原则

- 创建任务前必须校验额度。
- 创建任务后必须记录预计消耗或额度占用依据。
- 任务成功、失败、取消后的实际扣减、退回或结算策略由模块规格和计费规则确认。
- 所有额度变化必须可追踪、可审计。
- 同一收费根任务只有一个 `usageOperationId`；lease 恢复不新增用量操作。结算或释放必须对该 ID 幂等，且与
  冻结的计费主体一致。

## 通知原则

任务终态变化、额度变化等事件可以触发通知。

是否通知、通知内容、跳转目标和聚合方式由模块规格确认。

## 模块规格必须补充

每个具体生成模块在 `brainstorming` / `writing-plans` 阶段必须补充：

- 创建任务的业务入口和触发条件。
- 任务来源对象，例如草稿、素材、声音、模板或其他业务对象。
- 创建任务请求字段和响应 VO。
- 任务状态展示规则。
- 进度刷新方式和停止条件。
- 失败原因展示规则。
- 额度占用、扣减、退回或结算策略。
- 是否产生通知。
- 是否需要外部回调及回调字段。

## 发现页 RunningHub 单执行任务契约（2026-08-11）

发现页工作流创建使用既有根任务／执行／attempt 统一模型，不另建供应商任务模型。系统稳定任务类型为 `workflow_template_generate` 与 `workflow_template_test`，资源类型为 `workflow_order` 与 `workflow_template`，用户免费策略为 `workflow-free-1`。用户订单只创建、查询并在任务中心暴露 `workflow_template_generate`；`workflow_template_test` 仅用于 Sys 运营任务／测试记录，二者复用同一任务模型。

用户公共状态/阶段矩阵固定为：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。异步失败码至少包括 `WORKFLOW_SUBMISSION_UNKNOWN`、`WORKFLOW_EXECUTION_FAILED`、`WORKFLOW_OUTPUT_INVALID`、`WORKFLOW_CONFIG_CHANGED`。

执行 Service 在真实调用前解析 `(tenant_id, template_id)` 的唯一当前 RunningHub 配置。提交结果未知时记录 `WORKFLOW_SUBMISSION_UNKNOWN` 并停止，**绝不自动第二次 POST**。配置变更、执行失败或输出校验失败也不允许自动选择其他模式、路由、故障切换、回退或同订单人工重放；用户主动再次制作只能创建新业务意图、新幂等键和新根任务。

第一阶段只发布模板管理与用户端展示能力：模板人工 enable、用户可见性判断、列表、详情和 `creation-config` 均不创建、不查询、也不依赖 `workflow_template_test` 或任何其他异步任务。自动连接测试、RunningHub 请求/轮询、上传、订单和 `workflow_template_generate` 执行属于后续阶段；第一阶段不得为满足发布条件而补建测试任务或测试记录。

## 不进入任务中心的个人文案操作

个人文案的创建、列表、详情、历史版本查询、手工编辑新版本和删除都是同步免费操作，不创建任务记录，不轮询，不冻结或扣减额度，不产生任务通知。未来 AI 生成或优化文案仍必须使用本文件定义的任务、幂等和额度规则。

## 声音转写例外说明（2026-08-03）

声音上传后的 Whisper 转写属于 `av_voice` 资源内部后台处理，不是生成任务：不创建 `av_ai_task`，
不进入任务中心，不检查额度。数据库 `pending/transcribing` 状态是恢复事实；进程内事件只负责加速唤醒。
扫描器通过短租约领取，Worker 调用不持有数据库长事务，网络失败最多自动尝试 3 次并指数退避。

## 创作第 6 步统一任务契约（`timeline-1`）

创作第 6 步使用新的 `av_ai_task`、`av_ai_task_execution`、`av_ai_task_attempt` 三层事实模型，并进入统一 `/api/tasks` 任务中心。它不吸收、不改写、也不双写旧数字人纵链临时任务。

### 根任务、执行和尝试

- 根任务代表一次用户业务意图，唯一键为 `(owner_user_id,idempotency_key)`；任务类型、项目、草稿修订、冻结时间轴版本、输出配置或 AI 输入共同进入 `request_digest`。同键同摘要返回原根任务，同键异摘要返回幂等冲突。
- 创建任务在同一短事务中创建一个 `pending` 根任务及其 `execution_no=1` 执行；入队以条件更新把二者推进为 `queued`。根任务只允许 `active_execution_id` 指向的执行推进其状态。
- 执行记录表示同一根任务下的一次受控执行。基础设施租约恢复复用原根任务和原执行，只更新租约并增加 attempt；只有契约明确允许且次数受限的内部执行级重试才增加 `execution_no`。
- attempt 只在本地准备完成、已重新校验有效租约，并且即将发生一次真实外部 AI 调用或首个 `ffprobe`／FFmpeg 进程前，以独立短事务创建为 `running`。只下载、解析、校验、扫描或领取租约不得创建 attempt。

状态全集固定为：根任务 `pending|queued|running|success|failed|cancelled`；执行 `queued|running|success|failed|cancelled`；attempt `running|success|failed|cancelled|abandoned`。根任务和执行的 `success|failed|cancelled` 以及 attempt 除 `running` 外的状态都是不可逆终态。

### 第 6 步任务类型与结果

| `taskType` | 成功事实 |
| --- | --- |
| `timeline_image_prompt_generate` | 强类型图片提示词结果，`result_asset_id` 为空 |
| `timeline_fancy_text_suggest` | 强类型花字建议结果，`result_asset_id` 为空 |
| `timeline_subtitle_align` | 强类型字幕对齐结果，`result_asset_id` 为空 |
| `timeline_render` | 已就绪的合成视频 `result_asset_id`，建议结果 payload 为空 |

四类任务的 `resource_type` 固定为 `creation_project`。根任务的请求和结果 JSON 各不超过 65,536 UTF-8 bytes；只保存版本化、可解析的安全结构，不保存媒体地址、路径、命令、租约、凭据或供应商原始响应。

本轮免费策略版本固定为 `timeline-free-1`，`estimated_usage=0`。创建前仍通过统一策略入口验证任务类型免费且功能可用；不要求余额账户，不冻结、不扣减、不退款，也不伪造额度流水。该策略不跳过权限、`owner_user_id` 归属、素材、Schema、字体、文本完整性或幂等校验。

### CAS、租约、取消和重试

- 根任务、执行和 attempt 的所有状态变化都必须使用旧状态、`row_version` 和必要的父子／租约条件执行 CAS；更新影响 0 行即表示失去所有权，调用方停止后续副作用。
- Worker 只在短事务中从 `queued` 领取，写入随机 `lease_token`、`lease_owner` 和 `lease_expires_at` 后再执行外部工作；续租、进度、结果和终态写入必须同时匹配执行编号、`running`、当前令牌和版本号。
- 第 6 步调度入口固定为 `dispatchNext(workerId, perUserConcurrencyLimit, systemConcurrencyLimit)`。两个上限均为 `1..100`，且单用户上限不得大于系统上限；越界必须在领取前拒绝。Scheduler 只传入已校验配置并限制本进程重入，不能根据本地计数猜测集群容量。Service 必须在领取短事务中使用数据库时间，把未过期租约的 `running` 执行作为占用事实，原子校验系统占用和候选任务所属用户占用后再领取；达到任一上限或没有符合条件的任务时返回 `none`，不得先领取再回退，也不得把用户编号暴露给 Scheduler。并发 Scheduler 不得共同突破任一上限。
- 过期租约由扫描器恢复。迟到 Worker、重复回调和旧令牌不得修改结果、素材、项目或终态；条件更新必须影响 0 行。
- 取消是 `POST /api/tasks/{taskId}/cancellations` 创建的业务请求。只有后端状态和执行能力允许时才进入 `cancelled`，前端不得先改状态。
- 用户主动重试是新业务意图，必须使用新幂等键创建新根任务和新执行；旧任务、执行和 attempt 保持不可变。租约恢复不是主动重试，不得创建第二个根任务。

`timeline_render` 仅在固定 `render_input` 时间轴版本上运行。成功必须先安全登记 ready 成品素材，再以 CAS 写入任务结果和项目最新成品；失败或取消不得留下成功 payload。所有外部调用和媒体处理均不持有数据库长事务。

### 轮询与停止条件

首版只复用统一任务 HTTP 轮询，不新增 WebSocket 或 SSE：

- 当前时间轴任务详情在页面可见且获得焦点时每 2 秒刷新；任务中心存在活跃任务时每 5 秒刷新。
- 页面隐藏后退避为 15 秒；连续隐藏 5 分钟后停止网络刷新。重新可见或获得焦点时立即刷新一次后恢复正常间隔。
- 网络错误按 2、4、8、16、30 秒上限退避并加入小幅抖动；离线时暂停，恢复在线后立即刷新。
- 任务进入 `success|failed|cancelled` 时停止该任务轮询并做一次最终详情读取。
- 页面卸载、项目切换、退出登录时取消定时器和在途请求；401 停止全部任务轮询，403 或资源不存在停止对应资源轮询。
- 同一任务只能由一个共享查询实例轮询，禁止多个组件重复启动定时器。
