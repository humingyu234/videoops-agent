# 架构说明

本文档只说明系统分层、职责边界和状态归属，不展开接口字段、数据库表、权限清单或具体实现步骤。

## 参赛版架构覆盖层

现有系统继续作为“视频生产工具层”，参赛版在其上增加一个可观察、可中断的 Agent 控制面。目标状态的默认用户界面只有 `/agent`；当前基线仍从 `/` 跳转 `/studio`。原七步 Studio 不作为比赛主流程展示，但保留为内部人工接管和排障入口。

```text
用户目标 + 素材
  -> Agent UI（澄清、进度、批准、结果）
    -> AgentRun / Action / Approval / Evaluation
      -> 工具适配层
        -> 既有应用 Service（认证、资产、音色、数字人、时间轴、渲染）
          -> AI Task / Worker / 外部 Provider
```

核心约束：

- Agent 在后端调用既有应用 Service，不通过“后端自己请求自己的 HTTP API”来伪装工具调用。
- 既有任务与素材表仍是执行事实来源；AgentRun 只记录目标、计划、动作、评价、批准和产物关联。
- 每个工具必须返回稳定状态和错误分类，长任务通过事件或受限轮询观察。
- 自动返工只能修复可定位问题，默认最多两次；连续不改善、超时、超成本或主观冲突立即转人工。
- 规则评价、模型评价和确定性媒体检查必须分开记录，不能只给一个不可解释的总分。

首期不把运营端、RunningHub 发现市场、独立形象空间和 Electron 壳纳入主演示依赖；这些模块可以保留，但不得阻塞 `/agent` 的浏览器纵切面。

## Agent 核心契约与状态

`DeliveryBrief` 是版本化交付输入，保存原始目标、确认事实、带归属的素材 ID、输出限制、可见默认值、阻塞问题和服务端规范化摘要。确认后发生变化必须创建新版本；旧版本的迟到结果只能成为历史候选，不能写回当前版本。

首期字段组固定为：原始目标与 `image_to_digital_human_video` 交付类型；主体、受众、核心信息、行动号召、必留事实和禁用内容；带归属的形象/音色/音频资产 ID 与已确认脚本版本；语言、时长、比例、字幕、画中画和 MP4 输出要求；截止时间、额度、0～2 次返工上限；可见假设、阻塞问题和服务端规范化 `briefHash`。字段级数据库与 API 设计仍须在 T2 进入施工时写入公共契约，本文不预设表结构。

`AcceptanceProfile` 是该 Brief 版本的验收快照，保存硬标准、主观标准、评价器/策略版本、证据要求和时间/额度/返工上限。单个模型分数不能独立决定通过、拒绝或返工。

Agent 控制面最小事实包括：

```text
AgentRun
  -> PlanStep
  -> ToolInvocation -> existing task / asset
  -> Evaluation
  -> Approval
  -> RunEvent
```

建议运行状态：

```text
PLANNING
-> WAITING_INPUT
-> WAITING_INITIAL_APPROVAL
-> EXECUTING
-> WAITING_EXTERNAL_TASK
-> EVALUATING
-> REPAIRING
-> WAITING_CONDITIONAL_APPROVAL
-> WAITING_FINAL_APPROVAL
-> COMPLETED | FAILED | CANCELLED
```

LLM 只生成符合 schema 的候选 Brief 或白名单动作；状态机和现有 Service 决定权限、归属、幂等、额度、任务终态和允许转换。长任务提交后先持久化外部任务 ID，再暂停 AgentRun；服务恢复时观察同一任务。外部提交状态未知时先对账或转人工，禁止再次 POST。

首期白名单工具覆盖：Brief 规范化/校验、形象与声音选择或校验、参考音频任务、脚本生成或确认、数字人底片、时间轴/字幕/画中画、最终渲染、任务与授权产物查询、质量检查，以及受限重试、局部返工和批准请求。每次调用必须有结构化输入/输出、归属、幂等键、超时、费用/额度预估、错误分类、外部任务 ID 和产物 ID；未注册工具、多余参数、任意 URL、SQL、文件路径或命令必须在产生副作用前被拒绝。

## 质量、返工与批准

评价分三层独立记录：确定性媒体检查、内容/版式规则、感知/主观评价。每项结果必须关联 criterion code、产物版本、评价器版本、具体证据和置信度；不能只保存总分。

首期稳定 criterion code 为：

```text
media.playable                 media.container_codec
media.video_dimensions         media.audio_present
media.duration                 content.script_integrity
content.must_include           content.prohibited
subtitle.text_integrity        subtitle.safe_area
subtitle.timing                perceptual.identity_similarity
perceptual.lip_sync            perceptual.voice_consistency
perceptual.visual_stability    style.tone_match
```

依赖失效的最小规则：

| 失败维度 | 保留 | 最小重做范围 |
| --- | --- | --- |
| 容器、编码、混流 | 脚本、声音、底片、时间轴 | 最终渲染/转码 |
| 字幕或画中画 | 脚本、声音、底片 | 时间轴→渲染 |
| 口型、闪烁、稳定性 | 脚本、已确认声音 | 底片→时间轴→渲染 |
| 发音、噪声、音色 | 形象、脚本 | 声音及全部下游 |
| 事实、禁用词、核心信息 | 形象 | 重新确认脚本及全部下游 |
| 形象/音色越权或主观冲突 | 当前候选 | 禁止自动，转人工 |

一次初始候选后最多两个质量返工候选。租约恢复、继续轮询和幂等回放不算质量返工，但不能新建收费根任务。第一次修复无可测改善、无法定位、超时/超额度、硬约束冲突、低置信主观判断、用户取消或 Provider 提交未知时停止。

人工批准只保留三个语义：首次执行批准、昂贵/变更/超限的条件批准、最终交付批准。硬技术、安全、权限和资产归属失败不能被批准按钮改成合格。

## 总体拓扑

```text
Electron 桌面壳
  -> 内置 Web 容器
    -> React + Ant Design / ProComponents 前端应用
      -> ai-video-user-api
        -> 数据库 / 文件存储 / 队列或任务执行器 / 外部 AI 服务

管理端/运营端前端
  -> ruoyi-admin
    -> 数据库 / 文件存储 / 队列或任务执行器 / 外部 AI 服务
```

## 核心决策

- 用户端是 Electron 承载的 Web 应用，不是 Electron 原生业务应用。
- 后端 Maven 根工程为 `ai-video-api`，其中 `ai-video-user-api` 和 `ruoyi-admin` 是可单独部署的启动模块。
- 管理端/运营端由 `ai-video-ui/ai-video-platform-ui` 承载页面，由 `ruoyi-admin` 提供 API。
- 用户端由 `ai-video-desktop` + `ai-video-ui/ai-video-webapp` 承载页面，由 `ai-video-user-api` 提供 API。
- 业务状态以后端为准，前端只保存 UI 状态、表单状态和必要的临时草稿状态。
- 用户端 API 和管理端/运营端 API 接口层分离，共享领域能力沉淀在后端公共模块。
- 长耗时 AI 生成不在 HTTP 请求线程内同步完成，统一通过任务模型承载。
- 外部 AI 服务不是业务系统的数据源，最终状态必须回写到后端任务、素材、输出等模型。
- RuoYi-Vue-Plus 作为后端基础能力底座，用户端前端不采用管理端页面结构作为主技术栈。

## 分层职责

### Electron

负责：

- 应用窗口和桌面壳层。
- 按构建配置打开用户端 Web；浏览器访问与桌面端访问共用同一套页面和后端接口。
- 限制页面导航、外链、权限、证书异常和下载来源等远程内容安全边界。
- 通过 Electron 下载会话与系统保存对话框提供“另存为”，不向网页暴露任意文件路径能力。
- 为后续确有必要的本地能力提供最小化 preload bridge 边界；首期 preload 不暴露任何 API。

不负责：

- 业务数据模型。
- 业务权限判断。
- 任务状态流转。
- 接口响应结构。
- 直接调用数据库或外部 AI 服务。

生产桌面包在构建时通过 `AI_VIDEO_WEB_URL` 固化唯一 HTTPS 用户端地址，运行时不提供地址编辑入口。开发模式仅允许回环地址 HTTP。Electron 主进程不读取登录令牌、不代理业务 API，也不保存网页业务状态；会话仍由用户端 Web 自身管理。外部 HTTPS 链接交给系统浏览器，非受信导航与权限请求默认拒绝。

当前首期提供 Windows x64 NSIS 安装包，以及 macOS x64/arm64 DMG 配置。未配置证书时只生成内部测试用未签名产物；CI、自动更新和签名公证留待后续单独设计。

### Web 前端

负责：

- 路由、导航、布局和页面交互。
- 表单、列表、筛选、分页、上传、预览等 UI 行为。
- 调用后端 API 并展示加载、空、失败、权限不足等状态。
- 将后端 `R<T>` / `R<PageResult<T>>` 适配为 Ant Design Pro / ProComponents 需要的数据结构。

不负责：

- 绕过后端修改业务状态。
- 直接决定额度扣减、任务终态或文件访问权限。
- 直接拼接真实文件地址绕过授权下载。

### 后端

负责：

- 认证、授权、账号归属和数据权限。
- 业务数据持久化。
- 文件上传、下载授权和访问控制。
- 任务创建、状态持久化、进度查询、结果关联。
- 额度校验、扣减、退回和流水记录。
- 对外部 AI 服务或内部队列进行编排。

不负责：

- 前端组件视觉细节。
- Electron 窗口、托盘、更新等本地壳层行为。

### 后端业务模块依赖与端侧隔离

后端业务模块固定为 `ai-video-core`（共享核心业务）、`ai-video-infra`（基础设施集成）、`ai-video-user`（用户端适配）和 `ai-video-platform`（运营端适配）。依赖只能向共享核心收敛：

```text
ai-video-user      ─┐
                    ├──> ai-video-core <── ai-video-infra
ai-video-platform  ─┘

ai-video-user-api  ───> ai-video-user + ai-video-core + ai-video-infra
ruoyi-admin        ───> ai-video-platform + ai-video-core + ai-video-infra
```

- `ai-video-core` 只承载共享 Entity、RuoYi 标准 Service、Mapper、状态规则，以及对应业务聚合平级 `dto` 包中的稳定跨模块 DTO；不得依赖 `ai-video-user`、`ai-video-platform` 或具体 `ai-video-infra` 实现。核心业务不得创建 `application`、`port` 等平行层，AI 视频业务 DTO 不得迁入全局 `ruoyi-api`。
- `ai-video-infra` 提供外部 AI、OSS、队列、回调和通知的直接技术集成，可实现核心声明的 `I...Service` 契约或消费 `ai-video-core` DTO；不得建立“端口／适配器”业务分层，也不得依赖用户端或运营端 Controller、BO、VO、令牌或权限入口。供应商原始请求／响应只留在直接 `client`／`provider` 边界。
- `ai-video-user` 与 `ai-video-platform` 都只能调用 `ai-video-core` 的共享能力，彼此不得直接依赖或相互调用；启动模块负责装配各自适配层与基础设施实现，不承载跨端业务逻辑。
- 用户端 Controller、BO、VO 和权限入口只属于 `ai-video-user` 与 `ai-video-user-api`，使用 `app`（创作端）令牌和 `type = app` 权限校验；运营端 Controller、BO、VO 和权限入口只属于 `ai-video-platform` 与 `ruoyi-admin`，使用 `sys_user`（运营端用户）和独立运营权限。两端不得共享 Controller、BO、VO、登录助手或权限入口。
- 所有业务聚合必须遵守 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl` 的 RuoYi 业务包硬约束；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。`dto` 与技术辅助包都不得成为第二套业务编排层。禁止以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替换此结构。

### 外部 AI 服务或任务执行器

负责：

- 执行视频生成、数字人生成、声音克隆等耗时任务。
- 返回进度、结果、失败原因或回调通知。

不负责：

- 用户权限。
- 账号归属。
- 平台内任务展示规则。
- 额度策略。
- 最终业务状态保存。

## 状态归属

| 状态类型 | 归属方 | 说明 |
| --- | --- | --- |
| 页面展开、筛选输入、弹窗开关 | Web 前端 | 纯 UI 状态。 |
| 表单编辑中的临时值 | Web 前端 / 后端草稿 | 未保存前在前端，保存后以后端草稿为准。 |
| 项目、草稿、素材、数字人、声音、输出 | 后端 | 以后端持久化数据为准。 |
| 任务状态、进度、失败原因 | 后端 | 外部服务只能通过后端回写。 |
| 额度余额和消耗记录 | 后端 | 前端只展示后端结果。 |
| 本地文件选择和下载保存位置 | Electron | 只代表本地能力，不代表业务授权。 |

## 关键数据流

### 普通页面查询

```text
Web 页面
  -> 后端 API
    -> RuoYi 权限 / 数据权限
      -> 数据库或文件服务
        -> R<T> / R<PageResult<T>>
          -> 前端 adapter
            -> Ant Design / ProComponents 页面
```

### AI 生成任务

```text
Web 提交生成
  -> 后端校验参数 / 权限 / 账号归属 / 额度 / 幂等
    -> 创建任务记录
      -> 提交队列或外部 AI 服务
        -> 回调或轮询更新任务
          -> Web 轮询任务详情并展示结果
```

### 文件下载

```text
Web 请求下载
  -> 后端校验权限和账号归属
    -> 返回下载凭证或短期 URL
      -> Web / Electron 执行下载保存
```

## 发现页 RunningHub 单执行架构（2026-08-11）

用户端发现层只读取首页、列表、详情与创建配置；它不选择提供方、版本或执行方案，也不知道 RunningHub 内部 ID。用户订单提交的唯一业务体为 `{templateId, schemaHash, inputs}`，幂等键在请求 Header。运营层为 `(tenant_id, template_id)` 维护一个当前 RunningHub Workflow 或 RunningHub AI App 配置；核心 Service 负责解析该配置、归属/额度/幂等校验与统一任务创建，`ai-video-infra` 只执行直接 RunningHub 调用。

```text
用户发现/创建配置 -> 用户提交三字段订单 -> core Service 解析唯一当前配置
  -> 统一 workflow_template_generate 任务 -> infra RunningHub 调用 -> 受控输出资产/任务中心
```

配置、凭据、Workflow/Web App/节点、RunningHub 外部 task ID 均不越过 Service/infra 边界。提交未知不重发；不实施用户端多供应商、多版本、多方案、自动路由、故障切换、回退或同订单人工重放。

## 文档边界

### 用户端个人文案库

```text
ScriptLibraryView / ScriptEditorModal
  -> userScriptApi / RuoYiAdapter
    -> ai-video-user UserScriptController (app token)
      -> ai-video-core IUserScriptService
        -> AvUserScriptMapper + AvScriptVersionMapper
          -> av_user_script + av_script_version
```

页面和 HTTP 层不接收租户、工作区或所有者字段；核心 Service 从当前 app principal 派生个人归属。该同步链路不调用 AI provider、任务中心、额度、文件或下游作品模块。

- 接口返回、分页、上传下载、任务响应等规则见 `docs/API_CONTRACT.md`。
- 后端工程组织、RuoYi 能力复用、权限和事务协作规则见 `docs/BACKEND_GUIDE.md`；后端代码硬规则见 `docs/BACKEND_CODING_STANDARDS.md`。
- 前端工程组织、目录、ProComponents 和 Electron bridge 规则见 `docs/FRONTEND_GUIDE.md`；前端代码硬规则见 `docs/FRONTEND_CODING_STANDARDS.md`。
- 领域对象、字典、状态机和数据库设计见 `docs/DOMAIN_MODEL.md`。
- 任务幂等、回调、轮询、失败和额度处理见 `docs/ASYNC_TASKS.md`。
# 本地 Whisper Worker 边界（2026-08-03）

`ai-video-worker/whisper` 是仅监听回环地址的常驻推理进程。它启动时用 `local_files_only=true` 加载本地
`faster-whisper` 模型，只接受 Java 后端携带内部 Token 的单文件转写请求，不访问数据库、OSS、用户接口、
权限、额度或任务中心。Java 后端负责受控文件读取、租约、重试、结果校验和持久化。

## 创作第 6 步时间轴完整数据流（`timeline-1`）

第 6 步是第 5 步数字人视频成品与第 7 步发布之间的服务端持久化编辑阶段。浏览器负责交互预览，但不是最终合成器；只有后端媒体 Worker 按冻结版本完成的 MP4 才能成为第 7 步输入。

```text
第 5 步成功数字人视频任务（现有 av_dh_generation_job，仅作为来源）
  -> 用户端后端校验当前 app_user 对来源的访问权和成功状态
    -> 登记 ready 基础视频／主配音 av_creation_asset
      -> 创建 av_creation_project + 唯一 av_timeline_draft（timeline-1）
        -> 第 6 步浏览器加载草稿和受控素材流，执行非最终预览
          -> 保存时后端校验 Schema／素材／字幕／字体／安全区并更新草稿和引用投影
            -> 合成请求创建不可变 render_input 版本
              -> 创建 av_ai_task 根任务 + execution，并进入统一任务中心
                -> 媒体 Worker 按租约读取固定版本和受控素材流
                  -> ffprobe／FFmpeg 生成并校验固定 MP4/H.264/yuv420p/AAC 成品
                    -> 后端登记 ready timeline_render_output 素材并以 CAS 完成任务
                      -> 项目 current_output_asset_id 指向最新成品
                        -> 第 7 步只读取已授权、ready 的成品素材继续发布
```

图片提示词、花字建议和字幕对齐也创建统一 `av_ai_task`，但成功结果是版本化结构 payload，不直接修改草稿；用户确认后再通过正常草稿保存进入 `timeline-1`。时间轴编辑期间素材、草稿、版本、任务和成品均按 `owner_user_id` 隔离，不引入租户或工作区归属。

冲突副本只产生 `conflict_copy` 不可变版本、版本素材引用和写回执，不推进当前草稿；主动重试创建新根任务，Worker 租约恢复复用原执行。旧数字人任务保持独立来源事实，不被包装或双写为统一任务。

职责边界固定如下：

- Web：编辑状态、时间轴交互、Canvas／媒体元素近似预览和统一任务展示。
- 用户端后端：认证、权限、归属、Schema／语义校验、草稿／版本事务、素材受控读取和任务编排。
- 媒体 Worker：只消费冻结的 `timeline-1`、登记字体和受控媒体流，输出确定性成品；不接收浏览器命令、文件路径或任意 FFmpeg 参数。
- 数据库：保存项目、草稿、不可变版本、素材引用、幂等回执以及根任务／执行／attempt 的唯一事实。
