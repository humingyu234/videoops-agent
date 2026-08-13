# Liblib 风格发现首页与多供应商工作流模板设计

> **后续变更：** 2026-08-10 已确认发现页改为“RunningHub 单执行配置”模型。本文中关于用户选择供应商、多执行方案、自建 ComfyUI、模板版本、执行配置快照及相关 API／数据模型的设计，均由
> [发现页 RunningHub 单执行配置设计](./2026-08-10-discovery-runninghub-single-execution-design.md) 取代；发现页入口、内容布局与未被新规格修改的视觉规则继续有效。两份文档冲突时，以 2026-08-10 规格为准。

## 1. 文档状态

- 日期：2026-08-05。
- 模块：用户端“发现”首页、工作流模板详情、模板订单制作，以及运营端发现页、工作流模板和供应商管理。
- 适用端：`ai-video-webapp`、`ai-video-platform-ui`、`ai-video-user`、`ai-video-platform`、`ai-video-core`、`ai-video-infra`、`ai-video-user-api` 与 `ruoyi-admin`。
- 设计状态：对话设计已确认，等待书面规格审查。
- 风险等级：红色高风险。
- 实施前置：本文通过用户审查后，使用 `writing-plans` 生成实现计划；当前规格阶段不修改运行时代码。

## 2. 背景与目标

用户需要新增一个参考 [Liblib 首页](https://www.liblib.art/) 内容结构和交互密度的发现模块，但不替换现有工作台默认入口。

当前用户端根路径 `/` 重定向到 `/studio`，`/studio` 使用项目自有的侧栏、顶栏和内容壳层。新模块必须：

1. 保持 `/`、`/studio` 及现有“我的”分组五项菜单不变，新增独立内容路由 `/discover`，并在侧栏独立“探索”分组中增加“发现”入口。
2. 在现有项目壳层内复刻 Liblib 首页内容区的布局关系、卡片密度、筛选方式、瀑布流和悬停预览，不复制 Liblib 的 Logo、品牌、图片或其他受保护素材。
3. 只提供“视频模板”和“创作灵感”两个发现频道；二者只是内容分类，底层均为可执行的 ComfyUI 工作流模板。
4. 用户点击卡片先进入模板介绍页，再点击“使用此模板”进入订单制作页。
5. 一个逻辑模板可以同时绑定自建 ComfyUI、RunningHub Workflow、RunningHub AI App，以及未来通过受控适配器增加的其他供应商。
6. 用户主动选择供应商；订单制作页只展示该供应商工作流需要用户填写或上传的内容。
7. 提交后创建内部制作订单与平台异步任务，结果统一进入任务中心并登记为用户授权资产。
8. 运营端可以配置发现首页、模板、模板版本、多个供应商执行方案、动态输入、输出节点和发布流程。

本模块不是静态首页或演示页面。除开发和自动化测试使用的 mock 外，用户端与运营端必须接入真实后端契约和真实执行适配器。

## 3. 已确认决策

### 3.1 入口与视觉

- 用户端新增 `/discover`，不改变 `/` 到 `/studio` 的重定向。
- 复用或抽取当前 `StudioSider`、`StudioTopbar` 和认证壳层，不复制第二套侧栏、顶栏、登录态或积分面板。
- `/discover`、模板、订单和任务页面只替换右侧内容区；侧栏新增独立“探索 / 发现”入口，但不得删除、改名或重排现有“创作、形象、声音、文案、作品”菜单。
- 视觉参考只约束内容区；项目现有外壳保持项目品牌和交互。
- 桌面视觉验收基准为 `1440 × 900`；窄屏保持可访问、不遮挡、不横向溢出，不要求移动端像素级复刻。
- 不要求额外原型、线框图或视觉提案；实现时直接对照参考页面和本文规格。

### 3.2 内容范围

- 频道只有 `video_template`（视频模板）和 `workflow_inspiration`（创作灵感）。内部值使用
  `workflow_inspiration`，避免与旧首页的文案灵感 `copywriting_inspiration` 混为同一对象。
- 两个频道共用同一工作流模板、详情、制作、订单和任务链路，不建立两套业务表或两套执行逻辑。
- 不提供图片生成模板频道。
- 不提供独立数字人或声音频道；数字人类工作流仍可以作为普通视频模板或创作灵感发布。
- `av_workflow_template` 是项目内“可执行 ComfyUI 工作流模板”的唯一事实源。发现页、后续模板中心入口和其他页面若展示同一工作流模板，必须查询这一事实源，不得复制为第二套发现模板表。
- 既有视频创作五步流中的脚本／行业模板属于不同业务对象；只有在公共契约明确合并后才可引用本事实源，不能因页面都叫“模板”而混用表、类型或接口。

### 3.3 模板、供应商与表单

- 一个逻辑模板可以绑定多个供应商执行方案。
- 首期真实支持自建 ComfyUI、RunningHub Workflow 和 RunningHub AI App。
- 用户在订单制作页选择供应商，平台不自动选择或自动切换供应商。
- 每个供应商执行方案可以拥有不同的动态表单、输入限制、节点映射和输出映射。
- 运营端导入或读取工作流后，系统解析候选节点；运营人员人工选择对用户开放的字段，不自动暴露所有可变参数。
- 运营人员明确指定输出节点、结果类型、主结果和排序，不自动把全部中间产物展示给用户。

### 3.4 订单与额度

- “订单”是内部生成订单，不是现金商品订单。
- 首期不扣积分、不冻结积分、不退款，也不包含支付和对账。
- 数据与 Service 契约只冻结 `billingMode=free`，并预留可空的未来资费策略、额度单位和预计消耗字段；当前 `usageOperationId=null`，不创建虚假额度操作、结算记录或账本流水。
- 用户切换供应商后提交会创建新订单；用户主动再次制作也创建新订单。
- 供应商内部技术性重试只能继续使用订单已选择的供应商和工作流配置，不得无提示切换供应商。

### 3.5 模板版本

- 保留模板版本功能。
- 草稿可以反复保存，不因每次字段修改创建新版本。
- 只有运营人员执行“发布更新”时，才生成一条新的不可变已发布版本。
- 新订单使用当前已发布版本；历史订单继续引用其提交时版本。
- 回滚通过“从旧版本复制为新草稿，再发布为新版本”完成，不直接修改或重新激活历史记录。

## 4. 范围与不做范围

### 4.1 本次范围

- 用户端保留现有“我的”分组原样，发现模块可通过侧栏“探索 / 发现”、业务跳转或直接 URL 进入。
- 发现首页、模板详情、订单制作、订单详情以及任务中心跳转。
- 运营 Banner、推荐位、频道、分类、标签和人工排序配置。
- 逻辑模板、模板草稿、不可变已发布版本和上下架管理。
- 单模板多供应商执行方案。
- 自建 ComfyUI、RunningHub Workflow、RunningHub AI App 的真实后端适配。
- 工作流 API JSON 导入或读取、候选节点解析、人工输入输出映射和发布前测试。
- 文本、数字、布尔、单选、多选、图片、音频、视频和受控通用文件输入。
- 内部制作订单、平台任务、幂等、回调、轮询、取消、结果校验和资产登记。
- 用户、租户、工作区和文件归属校验。
- 运营端供应商连接、凭据、健康检查、暂停和审计。
- 用户端、运营端、后端、供应商适配器、数据库迁移和公共契约测试。

### 4.2 不做范围

- 图片生成模板频道。
- 独立数字人、声音频道或首页模块。
- 现金价格、支付、退款、发票和对账。
- 首期积分冻结、扣减、退回或账本写入。
- 用户自行提交供应商 API Key、服务器地址或任意工作流 JSON。
- 用户自行发布模板或社区投稿。
- 自动供应商路由、自动降级或静默切换供应商。
- RunningHub、自建 ComfyUI 之外其他供应商的首期真实实现；仅保留受控扩展能力。
- 评论、关注、点赞、收藏、社区发帖和创作者主页。
- Liblib 品牌、Logo、原始图片、原始视频、文案或其他受保护资产。
- Electron 壳层改造。
- DDD、Clean Architecture、Hexagonal Architecture，或 `application`、`port`、`adapter`、`command`、`model` 等平行业务层。

## 5. 风险与任务卡

### 5.1 风险等级

本任务为红色高风险，触发依据包括：

- 新增跨用户端与运营端的数据库对象、公共 API、权限和状态契约。
- 接入自建地址、第三方凭据、外部任务、回调与临时结果 URL。
- 上传图片、音频、视频并跨平台传输，涉及归属、类型校验和私有访问。
- 创建异步任务、幂等订单、供应商重试、终态保护和结果资产登记。
- 模板版本、动态表单和节点映射错误可能导致错误输入、额外供应商费用或不可追溯结果。
- RunningHub `retainSeconds` 等配置会产生额外费用，必须隔离普通用户。

### 5.2 单一目标

交付一套可由运营人员发布、可由创作用户选择供应商并提交动态工作流订单的发现模块，保证内容、版本、输入、任务、文件、凭据和结果全链路可追溯且不越权。

### 5.3 允许影响的模块

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`
- `docs/ARCHITECTURE.md`
- `ai-video-pages.md`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform`
- `ai-video-api/ai-video-user-api`
- `ai-video-api/ruoyi-admin`
- `docs/sql/ai-video/mysql`
- `ai-video-ui/ai-video-webapp`
- `ai-video-ui/ai-video-platform-ui`

不允许借本任务重构现有身份、积分、人物、声音、文案或作品模块。

当前仓库仍缺少可直接复用的通用图片／音频／视频安全上传组件和完整用户端任务中心页面；二者是本模块闭环的强依赖。实现计划必须将统一工作流输入上传能力和任务中心接入纳入同一交付或列为已验证的前置任务，不能以人物图片专用上传器、静态任务占位页或 404 路由替代。

### 5.4 候选工作任务与审查

| 任务 | 风险 | 输出 | 必须审查 |
| --- | --- | --- | --- |
| 公共契约与迁移 | 红色 | 领域对象、表、索引、状态、错误码、API 和迁移 | 契约 owner + 后端独立审查 |
| 核心模板与订单 Service | 红色 | 版本发布、归属、幂等、订单快照、任务编排 | 后端分层、事务、幂等审查 |
| 供应商与文件集成 | 红色 | ComfyUI/RunningHub 适配、上传、回调、轮询、结果登记 | 安全、SSRF、凭据、文件专项审查 |
| 运营端页面 | 红色 | 首页配置、模板、版本、供应商、映射、测试运行 | 权限与敏感字段审查 |
| 用户端页面 | 黄色 | 发现、详情、供应商选择、动态表单、订单详情 | 页面状态、字段与归属审查 |
| 联调与验收 | 红色 | 双供应商 E2E、异常、视觉与回归证据 | 前端、后端、联调三视角复核 |

修复后只复核原发现及直接受影响测试，禁止递归扩大审查范围。

## 6. 总体架构与职责

用户链路：

```text
ai-video-webapp
  -> 用户端 discovery / workflow-order API
    -> ai-video-user Controller / BO / VO
      -> ai-video-core 模板、版本、订单、任务 Service
        -> asset / task / quota 稳定 DTO
        -> ai-video-infra 供应商执行 Service
          -> 自建 ComfyUI
          -> RunningHub Workflow / AI App
```

运营链路：

```text
ai-video-platform-ui
  -> /api/admin/discovery/**
    -> ai-video-platform Controller / BO / VO
      -> 同一 ai-video-core Service
        -> ai-video-infra 连接检查、工作流读取、测试执行
```

职责边界：

- `ai-video-core/workflowtemplate`：逻辑模板、版本、供应商绑定、输入输出定义、发布和查询。
- `ai-video-core/workfloworder`：订单、订单快照、归属、幂等、任务关联和结果引用。
- `ai-video-core/discovery`：首页运营配置和面向用户的已发布内容聚合。
- `ai-video-infra/comfyexecution`：供应商原始 DTO、HTTP/WebSocket 客户端、上传和状态映射；不得承担订单、权限、版本或额度编排。不得使用已有 Flowable 审批模块的 `workflow` 包、菜单或路由。
- `ai-video-user`：创作端 BO、VO、Controller、当前 `app_user` 与工作区解析；另承载唯一一个精确登记的 RunningHub 技术回调 Controller 例外，只由 `ai-video-user-api` 装配。
- `ai-video-platform`：运营端 BO、VO、Controller、`sys_user` 权限和审计入口。
- 两个启动模块只负责装配、配置和部署，不复制核心业务。

业务聚合严格采用 RuoYi 贫血 Entity 加 Service 编排。核心模块使用 `domain`、平级 `dto`、`mapper`、`service`、`service.impl`；端侧另使用 `domain.bo`、`domain.vo`、`controller`。供应商 `client`／`provider` 只属于 `ai-video-infra` 的直接集成边界。

## 7. 用户端信息架构与视觉

### 7.1 路由

| 路由 | 页面 | 访问边界 |
| --- | --- | --- |
| `/discover` | 发现首页 | 已登录创作用户 |
| `/discover/templates/:templateId` | 模板介绍 | 已登录创作用户；只返回当前已发布版本 |
| `/discover/templates/:templateId/create` | 订单制作 | 已登录创作用户；模板和供应商均可用 |
| `/orders/:orderId` | 制作订单详情 | 仅当前 app 用户，且同时满足 tenant、workspace 和 owner 归属 |
| `/tasks` | 统一任务中心 | 已登录创作用户；复用公共任务契约 |

- `/` 继续重定向 `/studio`。
- `/discover` 系列路由与当前 `/studio` 一样使用 `layout: false`，复用抽取后的 Studio 认证和视觉壳层，不重新启用 Ant Design Pro 模板菜单。
- `StudioSider` 在原“我的”分组上方新增独立“探索”分组和“发现”入口，同时保留“创作、形象、声音、文案、作品”五项原有菜单且不增加“任务中心”；发现相关路由只替换右侧内容，并仅把“发现”标为当前项。
- `/discover` 系列页面复用同一认证壳层、侧栏折叠状态、用户信息和积分展示；共享壳层提供唯一页面一级标题和 `main` 地标，并通过原菜单返回 `/studio?view=<StudioRoute>`。
- 旧规格中的 `/ -> /dashboard` 与当前实现和本次确认冲突；本规格明确覆盖该旧规则，默认入口保持 `/studio`。
- 既有 `/templates` 属于旧视频创作模板语义，不复用为本模块路由、类型或 API；如未来合并，必须另行更新公共契约。

### 7.2 发现首页结构

内容区顺序固定为：

1. 运营 Banner。
2. 推荐模板横向区域。
3. `视频模板 / 创作灵感` 频道切换。
4. 关键词搜索、分类、标签和排序筛选。
5. 瀑布流模板卡片与分页式无限加载。

模板卡片展示：

- 封面或静态首帧。
- 支持时显示悬停预览视频；用户系统设置减少动态效果时不自动播放。
- 模板标题、简短介绍、频道、标签。
- 当前可用供应商数量。
- 运营推荐标识；不得伪造点赞、作者或社区数据。

交互规则：

- 点击卡片统一进入介绍页，不直接创建任务。
- 卡片整体是可聚焦链接，键盘焦点与鼠标悬停具有等价的信息和预览入口；封面提供可理解替代文本，视频不自动播放声音。
- 搜索框位于频道下方的筛选条，提交、清空和 Enter 行为一致；URL query 固定为 `keyword`，并与 `channel`、`categoryCode`、逗号分隔且排序规范化的 `tagCodes` 和 `sort` 一同在刷新后恢复。
- 从模板详情返回发现页时恢复筛选、已加载页数和滚动位置；切换筛选条件时清空旧瀑布流并从第一页重新加载。
- 无限加载底层仍使用服务端页码与稳定排序；同一项不得重复、跳页或因卡片高度变化丢失。
- 已下架模板在下一次请求中消失；已打开详情按稳定错误进入“模板已下架”状态。

### 7.3 视觉约束

- 参考 Liblib 的 Banner 比例、内容宽度、模块间距、卡片圆角、卡片密度、筛选条和瀑布流关系。
- 保持项目既有字体、主题 Token、侧栏和顶栏；不复制参考站全局外壳。
- 所有封面、预览和 Banner 使用项目自有、运营上传或已授权资产。
- 实施开始时在固定 `1440 × 900` 视口保存参考站和本地实现截图；验收比较内容区的模块顺序、相对尺寸、间距、密度、卡片比例和滚动行为。
- 参考站后续改版不自动扩大本文范围；验收以实施开始时归档的参考截图和本文为准。

## 8. 模板介绍与订单制作

### 8.1 模板介绍页

展示字段：

- 标题、摘要、频道、分类、标签。
- 封面、预览视频和效果案例。
- 富文本或结构化用途介绍。
- 所需素材概览，只列语义类型，不展示节点编号。
- 当前可用供应商方案数量和简要特点。
- 当前已发布版本的发布时间；不向普通用户展示内部版本编辑信息。

页面提供固定“使用此模板”按钮。以下情况按钮禁用：

- 模板已下架。
- 当前版本没有可用供应商方案。
- 用户缺少订单创建权限。
- 关键预览或详情正在加载。

### 8.2 供应商选择

订单制作页顶部先展示供应商方案卡片：

- `executionPlanId`。
- 运营配置的用户可见名称、说明和特点标签。
- 预计生成时长范围。
- 可用、暂停或暂不可用状态及用户可理解的原因。
- 不展示供应商账号、API Key、服务器地址、工作流 ID、App ID、节点或成本配置。

用户必须主动选择一个可用方案。平台不预先发起任务，也不在提交后自动切换供应商。

### 8.3 动态表单

选定供应商后加载该绑定的表单定义。支持的控件类型：

- `text`
- `textarea`
- `integer`
- `decimal`
- `boolean`
- `select`
- `multi_select`
- `image`
- `audio`
- `video`
- `file`

每个字段定义至少包含：

```text
inputKey              # 供应商执行方案内稳定且唯一
semanticKey?          # 跨供应商复用值的稳定语义键
label
description?
controlType
valueType
required
defaultValue?
options?
min/max/step?
minLength/maxLength?
acceptedExtensions?
acceptedMimeTypes?
maxFileSizeBytes?
minCount/maxCount?
minDurationMs/maxDurationMs?
sortOrder
```

表单响应顶层包含 `schemaVersion`、`schemaHash`、`templateVersionId`、`executionPlanId` 和 `fields`；`schemaHash` 不在每个字段重复。

线上 `inputs[inputKey]` 与 `defaultValue` 使用同一稳定值形状：

| 控件 | JSON 值形状 | 约束 |
| --- | --- | --- |
| `text` / `textarea` | `string` | 空字符串与未提供不同；是否允许空串由长度规则决定。 |
| `integer` | 规范十进制 `string` | 例如 `"42"`；不允许前导 `+`、多余前导零、小数点或指数，后端用 `BigInteger` 校验。 |
| `decimal` | 规范十进制 `string` | 例如 `"1.25"`；不允许指数，后端用 `BigDecimal` 校验精度／小数位，再按映射要求转成供应商数值。 |
| `boolean` | `boolean` | 不接受 `0/1` 或字符串真假值。 |
| `select` | `string` | option value 一律是方案内稳定字符串；供应商原类型由白名单映射转换。 |
| `multi_select` | `string[]` | 去重并保序；元素必须来自当前 options。 |
| `image` / `audio` / `video` / `file` | `{ "assetId": "..." }[]` | 单文件也使用长度为 1 的数组；数量由 `minCount/maxCount` 决定。 |

- 可选字段未填写时省略对应 key；不使用 `null` 表示缺失。`multi_select=[]` 只在 `minCount=0` 时是显式合法值。
- 服务端先应用 schema 中的用户可见默认值，再生成规范摘要；文件控件不得配置指向其他用户或永久 URL 的默认值。
- 请求拒绝未知 key、重复资产 ID、额外对象属性、混合数组类型、`NaN/Infinity` 等非 JSON 值以及与当前 schema 不同的值形状。
- 节点原始类型与用户值形状不同，只能由已登记的 `valueTransform` 在服务端转换；前端不能直接构造供应商 `fieldValue`。

节点编号、字段名和值转换规则只存在于后端和运营端配置，不返回普通用户表单接口。

`fields=[]` 是合法的“该方案无需用户输入”，页面可以直接提交；未知控件类型或不受支持的 schema 版本属于配置错误，页面不得忽略字段后继续提交。

切换供应商时：

- `semanticKey`、值类型和校验规则兼容的已填值可以保留。
- 存在不兼容字段或不再引用的上传项时，先明确列出将被清除的内容并要求用户确认；确认后再清除。
- 已上传文件仍是用户私有资产；如果新方案不引用，提交请求不得携带该资产。
- 表单必须重新使用新供应商方案的 `schemaHash` 校验。

### 8.4 提交行为

- 提交中禁用供应商切换、重复提交和字段编辑。
- 前端提交 `templateId`、`templateVersionId`、`executionPlanId`、`schemaHash`、规范化输入值和 `Idempotency-Key`，不提交 owner、供应商地址、工作流标识或节点映射。
- 后端重新校验模板版本、绑定可用性、字段全集、未知字段、默认值、类型、文件归属和权限。
- 创建成功后进入 `/orders/:orderId`，并提供前往任务中心入口。
- 创建失败保留用户仍有权访问的表单值和文件引用；版本过期时要求刷新配置后重新确认，不能静默按新配置提交。
- 请求结果因网络中断而不确定时，前端使用原 `Idempotency-Key` 查询或重试，不生成新键创建第二单。

## 9. 运营端页面

运营端只接受 sys 会话，固定使用独立后台路由；不得复用用户端 `/orders/:orderId`。后端菜单、隐藏路由、`dynamicPage.tsx` 和 `migratedPages` 使用同一组路径／component key：

| 后台路径 | component key | 用途 |
| --- | --- | --- |
| `/aivideo-discovery/home` | `aivideo/discovery-home/index` | 发现首页配置。 |
| `/aivideo-discovery/templates` | `aivideo/workflow-template/index` | 模板列表。 |
| `/aivideo-discovery/templates/create` | `aivideo/workflow-template/edit` | 隐藏新建页。 |
| `/aivideo-discovery/templates/edit/:templateId` | `aivideo/workflow-template/edit` | 隐藏编辑页，路径前缀映射。 |
| `/aivideo-discovery/providers` | `aivideo/workflow-provider/index` | 供应商配置。 |
| `/aivideo-discovery/orders` | `aivideo/workflow-order/index` | 订单列表。 |
| `/aivideo-discovery/orders/:orderId` | `aivideo/workflow-order/detail` | 隐藏订单详情，路径前缀映射。 |

菜单迁移为 `/aivideo-discovery` 目录下四个可见页面，模板编辑和订单详情为隐藏页面。数据库迁移、页面 import、精确 `migratedPages` 键和带参数的 `migratedPathPrefixes` 必须在同一变更中完成；component key 不使用既有 Flowable `workflow/**`。

### 9.1 发现页配置

使用 ProComponents 管理：

- Banner：标题、副标题、图片或视频资产、目标模板、展示时间、启停和排序。
- 推荐位：槽位、模板、标题覆盖、启停和排序。
- 频道、分类和标签：稳定代码、展示名称、启停和排序。
- 首页预览：使用真实已发布模板数据预览，不调用生成供应商。

被下架或无已发布版本的模板不能进入可见推荐位；已存在引用时在保存或查询时过滤并提示运营人员。

### 9.2 工作流模板管理

列表使用 `ProTable`，至少支持：

- 频道、状态、关键词、分类、标签和更新时间筛选。
- 标题、当前已发布版本、草稿状态、可用供应商数、发布人和发布时间展示。
- 新建、编辑草稿、复制旧版本为草稿、发布、下架和查看版本记录。

编辑页按标签页组织：

1. 基础介绍。
2. 展示素材与效果案例。
3. 供应商执行方案。
4. 用户输入映射。
5. 输出映射。
6. 测试运行与发布检查。

草稿反复保存只更新当前草稿。发布时验证当前草稿内容摘要与最近一次测试通过摘要一致，生成新的不可变已发布版本并切换模板的 `publishedVersionId`。

“复制旧版本为草稿”会替换当前活动草稿内容；如果草稿与当前发布版本不同，必须先展示将被覆盖的草稿修订并二次确认，不能静默丢失运营修改。

### 9.3 供应商管理

供应商配置列表和表单至少包含：

- 内部名称和供应商类型。
- `self_hosted_comfyui` 或 `runninghub`。
- 自建 ComfyUI 的受控 `baseUrl`、连接超时和启停。
- RunningHub 的固定官方 API Host、API Key 密钥引用、Key 类型说明和启停。
- 健康检查结果、最后检查时间和稳定失败类型。
- 创建人、更新人和审计信息。

API Key、访问密码和其他凭据为 write-only：

- 列表和详情只返回是否已配置、尾部脱敏信息或密钥修订号。
- 编辑时空值表示保留原密钥，显式轮换操作才替换。
- 带密钥的写接口关闭请求和响应日志保存。
- 连接地址、账号参数或凭据修订变化后，所有依赖方案先进入待复检／暂停接单；只有新修订连接检查与最小真实测试通过后才恢复，不能继续沿用旧健康结果。

### 9.4 执行方案编辑

每个模板草稿可添加多个供应商方案。运营字段包括：

- 用户可见名称、说明、特点标签、预计时长、排序和启停。
- 供应商配置引用。
- 执行模式：`comfy_api_json`、`runninghub_workflow`、`runninghub_ai_app`。
- 自建 ComfyUI API 格式工作流 JSON 或受控工作流文件引用及 SHA-256。
- RunningHub Workflow 的 `workflowId` 或 AI App 的内部字段 `web_app_id`；两种模式不能混用。基础设施调用 AI App 时必须准确映射为供应商字段 `webappId`。
- 可选加密访问密码密钥引用。
- 管理员专属执行参数，例如 `instanceType`、`usePersonalQueue`、`retainSeconds`。
- 输入、输出和内部默认参数映射。

`retainSeconds` 只允许有专门费用配置权限的运营人员修改，并明确提示会产生额外 RunningHub 费用；普通用户永远不能提交或覆盖。

### 9.5 节点解析与人工映射

- RunningHub Workflow 优先通过官方“获取工作流 JSON”能力读取；失败时允许运营上传该工作流导出的 API 格式 JSON 作为解析依据。
- 自建 ComfyUI 要求 API 格式 JSON，不接受仅供前端画布使用的 UI JSON。
- 解析器列出候选节点、输入名、原值类型、连接关系和风险提示。
- 只有 API 格式 JSON 中存在的字面量输入可以进入候选映射。
- 数组形式的节点连线、纯浏览器字段、`control_after_generate` 和 group 前端逻辑不得映射为用户字段。
- 运营人员逐项选择、命名、配置控件和校验规则；系统不自动发布解析结果。
- RunningHub 的 `seed` 在 API 调用时可能被重置；需要固定或允许用户输入时必须显式进入映射或内部默认参数。
- 输入字段与 `nodeId + fieldName` 或对应 AI App 输入键绑定；`fieldValue` 类型必须与原字段一致。

### 9.6 输出映射与测试发布

输出项至少配置：

```text
outputKey
nodeId
providerOutputNameOrIndex?
mediaType            # image|audio|video|file
displayName
isPrimary
sortOrder
maxCount
```

- 每个供应商方案必须且只能有一个主结果。
- 未映射的中间产物不登记为用户可见输出。
- 运营端可以为自建 ComfyUI 选择输出节点；RunningHub 只有在官方结果契约和真实测试能恢复稳定输出身份时才开放多节点映射，否则 UI 锁定为“唯一主输出”并解释限制。
- 测试运行同样创建平台任务，任务类型标识为模板测试，只有运营端可见。
- 当前配置摘要发生变化后，旧测试结果自动失效。
- 发布要求至少一个用户可用供应商方案；每个要对用户开放的方案都必须通过连接、映射、文件和完整测试运行检查。

## 10. 模板版本与发布模型

### 10.1 生命周期

逻辑模板状态：

- `draft`：没有已发布版本，仅运营端可见。
- `published`：存在当前已发布版本，用户端可见。
- `offline`：保留版本和历史订单，用户端不可新建订单。

模板版本状态：

- `draft`：当前可编辑草稿。
- `published`：已发布且不可变。

已发布版本是否为当前版本由逻辑模板的 `publishedVersionId` 派生；历史发布版本仍保持 `published`，不为了显示“已被替代”而回写其状态或内容。

状态含义不同，不以模板状态代替版本状态，也不把任务状态写入模板对象。

### 10.2 保存与发布

- 一个逻辑模板最多有一个活动草稿。
- 首次新建创建草稿，不分配发布版本号。
- 草稿反复保存使用乐观修订号，不新增版本记录。
- 发布事务不把草稿行原地改成发布行，而是分配递增 `versionNo`，把当前草稿聚合完整复制为新的不可变发布版本，冻结模板内容、供应商方案、输入、输出、内部默认参数和配置摘要。
- 发布成功后更新逻辑模板的 `publishedVersionId`；发布事务提交后刷新发现页缓存。
- 活动草稿作为下一次修改的工作副本继续存在；草稿与当前发布摘要完全相同时禁用“发布更新”，避免生成无变化版本。
- 历史版本不允许更新、逻辑删除或重新激活。
- 回滚只允许在明确确认后用历史版本内容替换活动草稿，通过完整检查后发布为新的更高版本；不改写旧发布行。

### 10.3 运行时暂停

供应商方案的“暂停接单”属于运行时可用性，不要求发布新模板版本：

- 暂停只阻止新订单。
- 已提交订单继续使用原方案和任务。
- 暂停原因、操作人和时间必须审计。
- 供应商配置或凭据修订变化、第三方 workflow/app 摘要漂移也会自动暂停依赖方案；复检通过只恢复运行时可用性，不改写发布版本。
- 修改工作流标识、节点、表单、输出、默认参数或用户展示说明仍必须创建新草稿并发布新版本。

## 11. 数据模型草案

所有业务 ID 后端使用 `Long`，HTTP 和 TypeScript 使用十进制字符串。本规格不预先批准任何 Entity 脱离 `BaseEntity`：不可变版本和执行事实也优先继承 `BaseEntity`，通过 Service 禁止更新／删除、数据库约束和反向测试保证不可变。若实现阶段确需偏离，必须先在 `docs/DOMAIN_MODEL.md` 登记原因、最小表范围、替代审计控制、回归测试和项目负责人确认，未确认不得进入实现计划。

数据归属先冻结以下硬边界：

- 模板封面、预览、案例和 Banner 是平台运营内容资产，不属于某个 `app_user` 的私有创作资产；复用 RuoYi `sys_oss` 及其 sys 权限，不写入创作端 `av_asset`，不能伪造一个用户 owner。
- 用户表单上传和工作流输出是当前 `app_user + tenant + workspace` 的私有资产；发现页公开查询永远不能返回其原始存储地址。
- 已发布版本、订单快照、任务执行尝试、回调事件和资产引用是审计事实，不允许物理删除；草稿和配置即使采用逻辑删除，也必须先通过被引用检查。
- 至少建立 `(template_id, version_no)`、`(execution_plan_id, input_key)`、`(execution_plan_id, output_key)`、`(tenant_id, workspace_id, owner_id, idempotency_key)`、`(execution_task_id, provider_call_sequence)` 和非空 `(provider_config_revision_id, provider_request_id)` 唯一约束；主输出唯一性使用数据库可表达的受控约束或事务内锁定校验加并发反向测试实现。

### 11.1 `av_workflow_template`

逻辑模板稳定身份：

| 字段 | 含义 |
| --- | --- |
| `template_id` | 模板 ID。 |
| `template_code` | 稳定唯一代码。 |
| `lifecycle_status` | `draft|published|offline`。 |
| `draft_version_id` | 当前活动草稿，可空。 |
| `published_version_id` | 当前发布版本，可空。 |
| `published_at` | 最近发布时间。 |
| `record_revision` | 乐观修订号。 |
| 审计字段 | RuoYi 基础审计与逻辑删除。 |

### 11.2 `av_workflow_template_version`

草稿与不可变发布快照：

| 字段 | 含义 |
| --- | --- |
| `template_version_id` | 版本 ID。 |
| `template_id` | 所属逻辑模板。 |
| `version_no` | 发布版本号；草稿为空。 |
| `version_status` | `draft|published`；当前发布状态由模板根指针派生。 |
| `channel_code` | `video_template|workflow_inspiration`。 |
| `title` / `summary` | 标题与摘要。 |
| `description_content` | 受控富文本或结构化介绍。 |
| `cover_oss_id` | 平台 `sys_oss` 封面。 |
| `preview_oss_id` | 平台 `sys_oss` 预览视频，可空。 |
| `category_code` | 运营分类代码。 |
| `tags_json` | 有上限的稳定标签代码数组。 |
| `case_oss_ids_json` | 有上限、保序的授权平台 `sys_oss` ID。 |
| `content_hash` | 规范化版本内容 SHA-256。 |
| `record_revision` | 草稿乐观修订；发布后不再变化。 |
| 发布审计 | 发布人、发布时间和从哪个版本复制。 |

`tags_json` 和 `case_oss_ids_json` 结构必须在公共领域文档登记；高频筛选的频道、分类和状态不得藏入 JSON。

### 11.3 `av_workflow_provider_config` 与不可变修订

选择“稳定配置根 + 不可变配置修订”方案，不采用覆盖式可变行或仅记录无法重放的 revision 数字。

`av_workflow_provider_config` 只保存稳定身份和当前指针：

| 字段 | 含义 |
| --- | --- |
| `provider_config_id` | 稳定配置 ID。 |
| `provider_type` | `self_hosted_comfyui|runninghub`；创建后不可改。 |
| `internal_name` | 仅运营端可见。 |
| `current_revision_id` | 当前已激活的不可变修订。 |
| `enabled` | 平台级启停。 |
| `health_status` / `last_checked_at` | 当前修订的健康摘要。 |
| `record_revision` | 根记录乐观修订号。 |

`av_workflow_provider_config_revision` 保存可重放的不可变连接事实：

| 字段 | 含义 |
| --- | --- |
| `provider_config_revision_id` | 修订 ID。 |
| `provider_config_id` / `revision_no` | 所属根和单调递增修订号，组合唯一。 |
| `base_url` | 受控地址；RunningHub 固定官方 Host。 |
| `credential_version_ref` | 版本化密钥服务引用；只写引用，不保存明文。 |
| `config_json` | 白名单超时、Key 类型和连接参数。 |
| `config_hash` | 不含密钥明文的规范配置摘要。 |
| 创建审计 | sys actor、创建时间和轮换原因。 |

- 新建、改地址、改账号参数或轮换凭据都追加修订，真实连接／最小任务测试通过后才原子切换根的 `current_revision_id`。
- 订单与非终态 attempt 固定引用精确 `provider_config_revision_id`；查询、取消和补偿继续使用该修订的 `credential_version_ref`，不能静默换成当前修订。
- 版本化 secret 在所有引用 attempt 终态且审计保留期结束前不可删除、覆盖或禁用；密钥系统不支持版本化引用时，本模块禁止启用该供应商，不能退化为有非终态任务时覆盖密钥。
- 若第三方已在平台外撤销旧 Key，旧 attempt 进入稳定凭据不可用／人工补偿流程，但平台仍保留原修订与审计事实，不用新 Key 猜测查询权限。

### 11.4 `av_workflow_execution_plan`

某个模板版本下的不可变供应商执行方案。普通用户只看到 `executionPlanId` 与展示字段，不看到供应商配置 ID：

| 字段 | 含义 |
| --- | --- |
| `execution_plan_id` | 执行方案 ID。 |
| `template_version_id` | 所属模板版本。 |
| `provider_config_id` | 供应商配置，仅服务端和有权限运营端可见。 |
| `execution_mode` | `comfy_api_json|runninghub_workflow|runninghub_ai_app`。 |
| `display_name` / `description` | 用户可见名称和说明。 |
| `feature_tags_json` | 用户可见特点标签。 |
| `estimated_duration_min_seconds` / `estimated_duration_max_seconds` | 预计时长秒数，可空。 |
| `workflow_id` | RunningHub Workflow 模式使用。 |
| `web_app_id` | RunningHub AI App 模式使用。 |
| `workflow_oss_id` | 自建或高级模式的私有 `sys_oss` API JSON 文件引用。 |
| `workflow_hash` / `schema_hash` | 发布时冻结的导出工作流和解析 schema 的 SHA-256。 |
| `remote_source_revision` | 第三方远端工作流可验证修订或摘要，可空。 |
| `access_password_secret_version_ref` | 可选版本化访问密码引用；发布版本引用期间不可覆盖。 |
| `provider_options_json` | 受控 `instanceType/usePersonalQueue/retainSeconds` 等。 |
| `sort_order` | 用户选择列表排序。 |
| `config_hash` | 执行、输入、输出和内部参数规范摘要。 |
| `last_tested_config_hash` | 最近测试通过摘要。 |

发布版本上的执行方案字段不可变。Workflow 与 AI App 是不同协议；同一方案只能选择一种 `execution_mode`。仅引用第三方 `workflowId` 或内部 `web_app_id` 时，发布记录必须同时保存可审计的导出 JSON／输入 schema 摘要；执行前重新读取并比较远端摘要，发生漂移时失败关闭。账号能力允许提交完整冻结 workflow JSON 时优先使用该模式；只能引用可被第三方原地修改的 ID 时，必须在运营端明确展示残余漂移风险。

### 11.5 `av_workflow_execution_runtime`

运行时可用性与不可变执行方案分离，保存 `execution_plan_id`、`runtime_status=available|paused|invalid`、稳定原因代码、用户提示、`verified_provider_config_revision_id`、`verified_remote_source_revision`、最后健康／真实检查时间、暂停操作 actor 与时间。暂停接单和复检只更新本表，不篡改历史模板版本或方案配置。

### 11.6 `av_workflow_input_definition`

只描述用户可见字段，不保存供应商节点：

| 字段 | 含义 |
| --- | --- |
| `input_definition_id` | 输入定义 ID。 |
| `execution_plan_id` | 所属执行方案。 |
| `input_key` | 方案内稳定唯一键。 |
| `semantic_key` | 跨供应商复用语义，可空。 |
| `label` / `description` | 用户文案。 |
| `control_type` / `value_type` | 控件和稳定值类型。 |
| `required` / `default_value_json` | 必填和默认值。 |
| `validation_json` | 已登记的长度、范围、选项、文件规则。 |
| `sort_order` | 表单顺序。 |

`validation_json` 必须冻结精确 schema 与允许键，不能作为任意扩展袋。

### 11.7 `av_workflow_input_mapping`

将用户字段或内部常量映射到供应商参数，支持一个用户字段写入多个节点：

| 字段 | 含义 |
| --- | --- |
| `input_mapping_id` | 映射 ID。 |
| `execution_plan_id` | 所属执行方案。 |
| `value_source` | `user_input|internal_constant`。 |
| `input_definition_id` | 用户输入来源使用，内部常量为空。 |
| `constant_value_json` | 内部常量使用，用户输入为空。 |
| `node_id` / `field_name` | 供应商目标节点和字段。 |
| `value_transform` | 白名单转换，例如原类型或供应商 `fileName`。 |
| `sort_order` | 同一字段多目标映射顺序。 |

凭据、服务器路径、任意 URL、节点连线数组和纯前端字段不能成为用户输入或内部常量。

### 11.8 `av_workflow_output_definition`

描述稳定用户输出，保存 `execution_plan_id`、`output_key`、`media_type`、`display_name`、`required`、`is_primary`、`sort_order` 和 `max_count`。数据库唯一约束保证每个方案最多一个主结果；发布检查保证恰好一个主结果。

### 11.9 `av_workflow_output_mapping`

将稳定 `output_key` 映射到供应商 `node_id`、输出名或 slot/index，并记录结果识别规则。一个输出定义可以有受控的多文件映射，但用户只看到已登记资产。

### 11.10 发现运营配置

- Banner 保存授权资产、标题、目标模板、展示窗口、启停和排序。
- 推荐槽保存槽位代码、模板 ID、可选标题覆盖、启停和排序。
- `av_discovery_category` 与 `av_discovery_tag` 保存稳定 code、展示名称、启停、排序和 sys 审计；code 创建后不可改，模板版本只引用 code。
- 频道固定为代码枚举，不提供运营增删频道表。
- 引用逻辑模板，不复制模板内容；用户查询时解析当前已发布版本并过滤下架或无可用方案模板。

### 11.11 `av_workflow_order`

订单是当前创作用户的不可恢复业务事实：

| 字段 | 含义 |
| --- | --- |
| `order_id` / `order_no` | 订单主键和用户可见编号。 |
| `tenant_id` / `workspace_id` / `owner_id` | 从当前 app 会话派生。 |
| `template_id` / `template_version_id` | 冻结逻辑模板和发布版本。 |
| `execution_plan_id` / `provider_config_id` / `provider_config_revision_id` | 冻结用户选择和精确不可变连接修订；供应商配置不返回用户。 |
| `root_task_id` | 唯一平台根任务。 |
| `idempotency_key` / `request_digest` | 与冻结的 tenant/workspace/owner 共同构成创建幂等。 |
| `template_snapshot_json` | 标题、版本号和展示摘要。 |
| `form_schema_snapshot_json` | 用户提交时完整动态表单。 |
| `input_snapshot_json` | 规范化标量值和资产 ID，不保存文件字节或临时 URL。 |
| `execution_snapshot_json` | 工作流标识、哈希、节点和输出映射，不含凭据。 |
| `billing_mode` | 当前固定 `free`。 |
| `future_tariff_policy_code` | 未来资费策略预留，当前为空。 |
| `future_quota_unit` / `estimated_amount` | 未来额度单位和预计消耗预留，当前为空。 |
| `usage_operation_id` | 当前为空；免费任务不得生成。 |
| 创建审计 | app 用户主体与创建时间。 |

订单不复制任务执行状态；所有排队、运行、成功、失败和取消展示来自统一任务状态。

### 11.12 订单资产与供应商执行事实

- `av_workflow_order_asset` 记录订单、`inputKey/outputKey`、方向、资产 ID 和顺序，支持归属、引用和清理检查。
- 供应商原始任务 ID、执行尝试、请求摘要、提交不确定状态、回调事件和供应商终态进入任务聚合下的执行事实表；不得塞入模板或用户 VO。
- `av_ai_task` 继续使用公共 root/execution 行：本模块根任务 `task_type=workflow_template_generate`、执行行 `execution_no=1`、`resource_type=workflow_order`、`resource_id=order_id`，免费根任务 `usage_operation_id=null`。订单只引用 root task，不复制状态。
- `av_ai_task_attempt` 每行代表一次真实供应商提交尝试，沿用公共 `provider_call_sequence` 与 `provider_request_id`，后者在核心 DTO 中映射为 `externalTaskId`，不得另建同义列。新增字段为 `execution_plan_id`、`provider_config_revision_id`、`submission_state`、`submission_correlation_key`、`provider_state`、`execution_config_hash`、全局唯一 `callback_nonce_digest`、`reconcile_deadline_at/next_reconcile_at/last_reconciled_at/reconcile_count`、`provider_terminal_at`、`attempt_revision`、人工收口来源／actor／证据摘要／时间；现有 `input_hash` 作为请求摘要。`(execution_task_id, provider_call_sequence)` 与非空 `(provider_config_revision_id, provider_request_id)` 唯一。
- `av_ai_task_callback_event` 冻结 `callback_event_id`、tenant、attempt、可空 provider event ID、nonce 摘要、`provider_request_id`、事件内容摘要、接收时间、净化载荷、`verification_status=pending|verified|rejected`、`processing_status=pending|applied|ignored_duplicate|late_after_terminal|failed`、稳定错误和重试时间；`(attempt_id, event_digest)` 唯一，存在 provider event ID 时再做供应商作用域唯一，原始敏感请求不落库。
- `av_ai_task` 的 `queued` execution 行和条件领取扫描器是耐久调度事实；本模块不新增任务 outbox。既有 `av_outbox_event` 仅在未来确需通知时承载通知事件，本期不写通知 outbox。
- 执行事实方案已冻结：使用统一 `av_ai_task` 的 root/execution 两行和 `av_ai_task_attempt`，不新增平行 `av_ai_task_provider_execution`。工作流字段进入 attempt；回调事件使用 `av_ai_task_callback_event`。
- 当前仓库尚无可直接调用的完整 `av_ai_task` 实现、`IAiTaskService` 和通用私有媒体资产闭环；它们不是可假设已存在的依赖。实现计划必须先交付并验收公共任务／资产契约，或把同等能力纳入本模块首批任务，然后才能接真实供应商。

### 11.13 私有资产、上传会话与平台媒体

- 公共安全素材契约中的 `av_file_object + av_upload_session + av_asset` 是 app 用户私有文件唯一事实源，不新建第二套工作流文件表。`av_asset.category` 新增 `workflow_input|workflow_output`，对象 Key、格式／MIME、大小、哈希和媒体元数据继续归 `av_file_object`。
- `av_upload_session` 在公共字段外增加 `purpose=workflow_input`、template version、execution plan 和 input key；tenant/workspace/owner 从关联 file/asset 与登录 scope 派生并纳入幂等查询。会话短期有效且一次完成，完成后不可改绑其他字段。
- 平台模板封面、预览、案例、Banner 和 workflow JSON 复用 RuoYi `sys_oss`；模板／发现表只保存 `oss_id`。公开媒体必须是已授权展示类型，workflow JSON 必须使用私有 OSS 配置且只允许有权限 sys 用户和服务端执行器读取，不能把上传响应 URL 带到 app VO。
- `av_workflow_order_asset` 的输入／输出外键只指向 `av_asset`；模板和 Banner 只指向 `sys_oss`。两类资产访问 Service、权限和 URL 签发不能混用。

## 12. 供应商执行契约

### 12.1 统一核心契约

`ai-video-core` 定义稳定的 `IWorkflowExecutionService + *DTO` 契约，只表达平台需要的执行语义，不依赖 RunningHub、ComfyUI SDK 或原始 JSON 响应；`ai-video-infra` 提供按执行模式分离的实现。统一能力至少包含：

- 连接检查。
- 工作流元数据读取或 API JSON解析。
- 上传输入资产。
- 提交任务。
- 查询状态与进度。
- 查询结果。
- 取消任务。
- 声明能力矩阵，例如是否支持单任务取消、可信进度、回调复核和可恢复的多输出标识。
- 将供应商错误映射为平台稳定错误。

供应商原始对象只存在于 `ai-video-infra/comfyexecution/client` 或 `provider`。这不是 Hexagonal `port/adapter` 分层；业务编排仍由核心 RuoYi Service 完成。

### 12.2 自建 ComfyUI

- 供应商配置保存受控服务器 `baseUrl` 和服务端凭据引用。
- 模板方案保存 ComfyUI API 格式工作流 JSON 文件和 SHA-256，不只保存任意网页地址。
- 节点映射使用 API JSON 中的 `nodeId + inputName`。
- 提交前由后端替换白名单输入并上传素材。生产接入必须经过平台受控包装层，该包装层接受平台 `idempotencyKey`、持久化请求摘要并返回可按键查询的提交结果；不能把原生裸 `/prompt` 当成具备幂等能力的接口。
- 保存本次执行返回的 `prompt_id` 作为外部任务 ID。
- 通过 history/WebSocket 或受控轮询读取进度和结果；不向浏览器开放 ComfyUI 管理面、任意 `/view` 或服务器路径。
- 输出由后端读取、校验并登记资产。
- 原生 ComfyUI 的全局 interrupt 可能影响其他任务，不能映射成订单级取消。只有受控包装层能够证明按外部任务 ID 单独取消时，执行方案才声明 `supportsPerTaskCancel=true` 并向用户展示取消按钮。

### 12.3 RunningHub Workflow

基于用户指定的官方高级接口：

```text
POST https://www.runninghub.cn/task/openapi/create
```

硬约束：

- 常规执行保存并提交 `workflowId`。
- `nodeInfoList` 每项精确包含 `nodeId`、`fieldName` 和保持原类型的 `fieldValue`。
- 绑定可以选择高级完整 `workflow` JSON 模式；一旦提交完整 `workflow`，RunningHub 会忽略 `workflowId`，两种模式在同一执行方案中必须互斥。
- 可选 `accessPassword` 来自受保护配置，不来自普通用户。
- `instanceType`、`usePersonalQueue`、`retainSeconds` 只来自运营配置。
- `retainSeconds` 按当前官方契约只接受 `10–180` 秒、仅对特定 Key 类型生效且会产生额外费用，默认不设置；越界或账号不支持时发布检查失败。
- 保存响应中的 `taskId`、初始 `taskStatus` 和 `promptTips`；`promptTips` 中的节点错误映射为稳定平台错误和运营诊断信息。
- 远端 `workflowId` 可以在第三方平台被原地修改。提交前必须把当前可验证摘要与发布版本冻结摘要比较；读取失败或摘要不一致时将方案标记为 `invalid` 并拒绝新订单，不能继续猜测节点兼容性。

### 12.4 RunningHub 文件上传

使用当前官方 V2 接口：

```text
POST https://www.runninghub.cn/openapi/v2/media/upload/binary
```

- 浏览器先把文件上传到平台私有资产能力，不能直接携带平台 API Key 调用 RunningHub。
- 执行器从平台授权资产流式读取并上传 RunningHub。
- 图片、音频、视频或 ZIP 等格式必须同时满足模板字段约束和 RunningHub 当前白名单。
- ComfyUI 节点使用上传响应的 `fileName`，不使用临时 `download_url` 作为节点永久引用。
- `fileName` 是供应商相对路径，不能拼接成外链或写回平台资产 URL。
- RunningHub 临时下载 URL 不能持久化为最终用户资产；输出必须在有效期内由后端下载并登记。

### 12.5 RunningHub AI App

当前官方创建接口为：

```text
POST https://www.runninghub.cn/task/openapi/ai-app/run
```

- 执行方案内部保存 `web_app_id`，基础设施请求准确使用官方字段 `webappId`，不能同时填写 `workflowId`。
- 当前官方请求使用 Bearer 认证，示例／模型还要求请求体中的 `apiKey`；两处都只能由服务端凭据映射生成，不能进入用户请求、模板快照或日志。
- 请求体使用 AI App 自己的 `nodeInfoList`、`webhookUrl`、`instanceType` 等白名单字段，不复用 Workflow 创建 DTO。
- 导入 AI App 的公开输入定义后，运营仍需人工决定哪些字段对用户开放。
- 图片、音频和视频字段先走平台文件与 RunningHub 上传，再把供应商返回值写入对应 AI App 输入。
- AI App 的原始字段类型、状态和错误只在基础设施边界映射，不直接返回前端。
- AI App 与 Workflow 必须使用各自的官方端点、请求 DTO、状态映射和契约测试，不能仅把 `workflowId` 字段改名为 `webappId` 后复用同一请求体。
- 官方更新日志提到发布内容可带 `accessPassword`，但当前 AI App 请求模型与示例尚不一致；该字段默认关闭，只有真实账号联调验证通过并留下请求契约证据后才允许配置。
- AI App 官方说明结果不带工作流信息；默认按唯一主输出能力发布。AI App 单任务取消和带节点回调均不能从 Workflow 文档外推，必须分别实测后再开启能力标志。

### 12.6 RunningHub 输出恢复限制

当前官方 V2 查询结果只稳定返回结果 URL 与 `outputType`，不保证返回原 ComfyUI `nodeId`。因此：

- 不能依赖 URL 顺序、文件名或媒体类型猜测任意多输出属于哪个节点。
- RunningHub 方案发布前的真实测试必须证明：即使回调丢失，平台仍能仅靠持久化事件与官方查询恢复全部必需输出及其 `outputKey`。
- 在没有官方稳定输出标识时，RunningHub 方案只允许一个必需主输出，禁用辅助输出；该结果按“唯一主输出”登记，而不是伪称恢复了节点映射。
- 只有当官方 webhook／查询载荷提供了经真实测试确认的稳定输出身份，且原始回调事件可去重、持久化、重放并由 V2 查询复核任务归属和终态时，才可启用 RunningHub 多输出映射。
- Workflow webhook 的当前示例把 node-aware 结果放在字符串形式的 `eventData` 中，需要受限的二次 JSON 解析；平台必须先校验外层字段、长度和任务关联，再解析内层允许字段。AI App 不得假定具有同样载荷。
- 官方 webhook detail 可以作为已配置 Workflow 回调的补偿／诊断来源，但官方未承诺其保留期和 SLA，且旧的 node-aware outputs 接口已标记将废弃；二者都不能在未完成丢回调演练前被当作长期唯一恢复保证。
- 能力结论、接口样例摘要和测试证据必须随发布检查保存；第三方载荷变化后旧证据失效，方案暂停接单。

### 12.7 回调、轮询和取消

- RunningHub 优先配置后端 `webhookUrl`，同时保留有界轮询补偿。
- 回调事件按供应商事件 ID 或稳定内容摘要幂等；同一尝试使用专属随机 nonce 路径，数据库只保存 nonce 摘要。
- 如果供应商未提供可验证签名，回调只作为“不可信唤醒信号”：限制正文大小、来源速率和字段全集，校验尝试与外部任务 ID 关联后，仍通过官方查询接口复核任务和结果，不以回调正文直接推进终态。
- 轮询使用退避、超时和最大重试，避免无界请求。
- 回调、轮询和超时 Job 竞争更新同一尝试时使用租约或条件更新；任务终态和输出登记都必须比较当前修订，防止竞态回退与重复资产。
- 取消是执行方案能力，不是所有供应商的默认能力。只在能力矩阵和当前任务状态同时允许时展示；取消请求幂等，不支持的方案明确显示“此供应商不支持中途取消”。
- RunningHub 当前 `/task/openapi/cancel` 文档只明确说明 ComfyUI 任务；Workflow 可按官方契约接入，AI App 只有真实取消联调通过后才可声明支持。
- 用户选择的供应商失败后不自动切换；可重试的技术错误只在同一供应商、同一配置和同一逻辑任务下有界重试。
- RunningHub 若没有可用于提交去重的供应商相关键，网络中断导致“是否已受理”不确定时进入 `submission_unknown`，停止自动重新 POST，由按键查询、轮询对账或运营人工补偿确认，不能为避免卡住而重复计费提交。

### 12.8 `submission_unknown` 有界收口

attempt 只在真实 POST 紧邻前创建，因此 `submission_state` 精确使用 `submitting|submitted|submission_unknown|not_accepted|submission_unresolved`；`provider_state` 另用 `not_started|queued|running|success|failed|cancelled|unknown`。两套状态不得合并或写入订单。

- 请求可能已发出但未取得 `externalTaskId` 时，以首次不确定时间起固定 `24h` 为 `reconcileDeadlineAt`；平台任务保持 `running`，用户看到“正在确认供应商是否已受理”，不显示伪进度。
- 截止前，可信 callback 带回 task ID 时先用 pinned `providerConfigRevisionId` 主动查询并校验唯一关联，成功后把 `provider_request_id` 条件写入并更新为 `submitted`。
- 具有 `aivideo:workflow-order:retry-or-compensate` 的 sys 运营人员可以执行三种带独立操作幂等键的审计动作：`bind_external_task`（主动查询确认后绑定唯一 task ID）、`confirm_not_accepted_and_retry`（上传／填写未受理证据后把旧 attempt 置为 `not_accepted`，在同一 execution task 下追加新 attempt）、`close_failed`（明确结束为失败）。普通用户不能执行。
- `confirm_not_accepted_and_retry` 仍固定原 execution plan 与 `providerConfigRevisionId`，且只允许一次条件更新成功；没有“未受理”证据时禁止重 POST。
- 到达 24h 仍未收口时，扫描器把 attempt 条件更新为 `submission_unresolved`，root/execution task 进入 `failed`，稳定错误为 `WORKFLOW_SUBMISSION_UNRESOLVED`。用户之后再次制作必须新建订单和幂等键。
- 终态后到达的 callback 只登记为 `late_after_terminal` 并触发运营审计，不回退任务、不自动登记用户输出；人工处理也不得把旧根任务改回运行态。
- 公共租约恢复规则对 `call_purpose=workflow_submit` 采用窄化规则：若旧 attempt 已是 `submitted|submission_unknown`，新 worker 只能条件接管并恢复同一 attempt 的查询／收口，不能先标记 lease lost 后追加 attempt，更不能重新 POST。只有已证实 `not_accepted` 且满足自动有界重试规则，或 sys 执行 `confirm_not_accepted_and_retry`，才在下一次真实 POST 紧邻前追加新的 `provider_call_sequence`。

## 13. 文件、任务与订单流程

### 13.1 输入文件

用户上传流程：

```text
选择文件
-> 前端即时校验
-> 获取与 templateVersionId/executionPlanId/inputKey 绑定的上传会话
-> 上传到平台私有资产
-> 服务端校验类型、魔数、大小、数量、时长、归属和字段用途
-> 返回 assetId
-> 创建订单时重新校验并引用
```

- 前端校验只改善体验，不能替代后端校验。
- 上传会话必须绑定当前用户、工作区、模板版本、供应商方案和输入字段。
- 未被订单引用的工作流输入资产按公共文件契约进行有界孤儿清理。
- 文件下载、预览和供应商读取均需重新校验 owner、workspace、状态与用途。

### 13.2 创建订单与任务

服务端顺序：

1. 校验 app 会话、权限、租户、工作区和 owner。
2. 校验当前模板、已发布版本、供应商运行时状态和 `schemaHash`。
3. 规范化并校验全部输入，拒绝未知字段和多余资产。
4. 计算包含规范输入与派生 scope 的 `requestDigest`，只在 `(tenantId, workspaceId, ownerId, idempotencyKey)` 范围内处理幂等；查询和回读必须复核四项 scope。
5. 幂等已命中且摘要一致时直接返回原订单；不得再次冻结快照、增加资产引用、创建任务或发送入队事件。键相同而摘要不同则返回稳定冲突。
6. 首次创建在同一数据库事务中冻结模板、表单、输入、执行和输出快照，创建订单与订单资产引用，并通过统一任务契约调用 `createFreeTask` 创建 `pending` 根任务和一个初始执行记录。
7. 根任务冻结 `taskType=workflow_template_generate`、`resourceType=workflow_order`、`resourceId=orderId`、`billingMode=free`、`usageOperationId=null`；不访问当前只读额度账户或账本。运营测试使用独立的 `taskType=workflow_template_test`，不得进入用户任务中心。
8. 同一事务建立 `order.rootTaskId`，并调用公共 `IAiTaskExecutionDispatcher.enqueue` 原子地把同一 root/execution 从 `pending` 推进到 `queued`；不写任务/provider outbox。
9. 提交后的 worker 唤醒只是 best-effort 加速；唤醒失败由 `av_ai_task` 的 `queued` 扫描索引领取原 execution 恢复，不创建第二订单、第二根任务或第二执行行。

外部供应商调用不得放在创建订单的数据库长事务中。

### 13.3 执行与结果

```text
平台任务 queued
-> 上传订单输入到已选供应商
-> 提交工作流并把 DTO externalTaskId 保存到 providerRequestId
-> callback / poll 更新平台进度
-> 供应商终态 success
-> 读取已映射输出
-> 下载、类型和媒体完整性校验
-> 登记为当前 owner 的授权资产
-> 写入订单输出引用
-> 平台任务 success
```

- 供应商成功不等于平台成功。
- 供应商已成功但输出仍在下载、校验或登记时，平台任务保持 `running`，详情显示稳定的“正在处理结果”阶段，不提前标记成功。
- 只有全部必需输出下载、校验、登记和关联完成后，平台任务才能进入成功终态。
- 缺少主输出、文件过期、类型不符、下载失败或资产登记失败时，平台任务不能伪造成功。
- 任务终态不可回退；重复回调、轮询和 Job 不得重复登记输出或发送通知。
- 供应商不提供可信百分比时只展示离散阶段与不确定进度，不根据轮询次数或预计时长伪造百分比。

### 13.4 订单与重试

- 一个订单关联一个逻辑平台任务。
- 供应商网络重试记录在任务执行尝试中，不创建新订单。
- 非幂等提交只有在能确认供应商未受理时才允许重提；已经获得外部任务 ID 后只能查询或按能力取消。无法确认是否受理时记录 `submission_unknown`，不自动再次提交。
- 用户主动“再次制作”或改选供应商会创建新订单和新幂等键。
- 订单和任务不物理删除；用户列表可以隐藏已取消记录，但审计事实保留。
- 本期不新增站内信、短信或邮件完成通知；用户从订单详情和统一任务中心查看状态。代码不预埋会重复发送的空通知钩子，未来通知必须另行登记唯一事件契约。

## 14. API 契约草案

所有普通 JSON 返回 `R<T>`，分页返回 `R<PageResult<T>>`。ID、文件大小、额度和修订等大数使用十进制字符串。未知请求字段在进入业务 Service 前拒绝。

### 14.1 用户端查询

```text
GET /api/discovery/home
GET /api/discovery/templates
GET /api/discovery/templates/{templateId}
GET /api/discovery/templates/{templateId}/execution-plans
GET /api/discovery/templates/{templateId}/execution-plans/{executionPlanId}/form-schema
```

模板分页参数至少包含：

```text
pageNum
pageSize
channel?
categoryCode?
tagCodes?      # 逗号分隔的稳定代码，服务端去重并规范排序
keyword?
sort?          # latest|recommended
```

- `pageSize` 有后端上限。
- 排序值映射白名单，不接收任意列名。
- 空页稳定返回 `rows=[]`。
- 用户查询只返回已发布、未下架且至少有一个可用方案的模板。

供应商列表只返回用户展示字段、`executionPlanId`、可用状态和预计时长。动态表单接口返回 `schemaHash` 和用户字段，不返回节点、工作流、供应商配置或凭据。

#### 14.1.1 用户端只读 VO 精确字段

用户端查询不接受页面自行猜测字段。下列字段名、可空性和枚举是前后端 wire contract；响应出现未登记的敏感字段（名称匹配 `credential`、`apiKey`、`baseUrl`、`workflowId`、`webappId`、`nodeId`、`providerConfigId`）时，前端 parser 必须按契约异常拒绝该对象，不把它渲染到 DOM 或日志。

```ts
type WorkflowChannel = 'video_template' | 'workflow_inspiration';
type WorkflowMediaType = 'image' | 'video';
type WorkflowInputValueType =
  | 'string'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'string_array'
  | 'asset_array';
type ExecutionPlanRuntimeStatus = 'available' | 'paused' | 'invalid';
type WorkflowProviderKind =
  | 'self_hosted_comfyui'
  | 'runninghub_workflow'
  | 'runninghub_ai_app';

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
  target: { type: 'template'; templateId: string } | { type: 'channel'; channel: WorkflowChannel };
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

约束：

- `url/posterUrl` 只允许后端生成的 `https:` 绝对地址或以单个 `/` 开头的应用同源相对路径；拒绝 `http:`、协议相对地址、`data:`、`blob:`、`javascript:`、反斜杠和控制字符。Banner `target` 只允许模板或频道，不接受任意外链。
- `width/height` 是正整数；未知 channel、media、provider 或 runtime 枚举导致该响应失败，不静默降级。
- `recommendations` 与模板分页共用同一 `WorkflowTemplateCardVO`，页面不得另外维护一套卡片 DTO。
- `usageCount` 缺失时不展示使用量；前端不得生成虚假作者、点赞、收藏或使用数。Banner 行为按钮文案由前端依据已冻结的 `target.type` 本地化，不从运营内容派生。
- `availableExecutionPlanCount` 只表示当前可接单方案数量；方案详情仍通过独立接口读取，用户必须主动选择。
- 方案列表的展示顺序只使用后端运营排序；首项、排序或供应商类型都不表示“推荐”，页面不生成推荐徽标，也不自动选择。

### 14.2 工作流输入上传

复用安全素材规格已经冻结的通用私有资产上传会话，不能扩展人物图片专用 multipart 接口或再建 `/upload-sessions` 同义路径：

```text
POST /api/assets/uploads
POST /api/assets/uploads/{uploadId}/complete
GET  /api/assets/{assetId}/access-url?disposition=inline|attachment
```

创建会话请求至少包含：

```json
{
  "fileName": "input.png",
  "declaredContentType": "image/png",
  "sizeBytes": "123456",
  "assetType": "image",
  "category": "workflow_input",
  "idempotencyKey": "...",
  "purpose": "workflow_input",
  "templateVersionId": "...",
  "executionPlanId": "...",
  "inputKey": "sourceImage"
}
```

- 服务端用当前 app actor 派生 owner／tenant／workspace，并从动态字段定义派生允许的资产类型与用途；客户端 `assetType/category` 必须与派生值严格相同，不能扩大用途。
- 上传创建的 `idempotencyKey` 只在 `(tenantId, workspaceId, ownerId, idempotencyKey)` scope 命中；同键不同文件或字段上下文返回上传会话冲突。
- 创建／完成上传要求 `aivideo:asset:upload`；查询资产摘要要求 `aivideo:asset:query`；获取预览／下载授权要求 `aivideo:asset:download`，三者都显式使用 `type = app`。
- 创建响应沿用公共 `uploadId/fileId/mode/status/expiresAt/singlePutUrl/partSizeBytes/partCount`；浏览器向 OSS 的签名 PUT 不使用 `R<T>`，分片签名、取消和会话查询复用公共 `/api/assets/uploads/{uploadId}/**` 契约。
- 完成接口沿用 `uploadId/fileId/assetId/uploadStatus/assetStatus`，进行真实对象大小、魔数、解码、媒体元数据、哈希和病毒／内容安全策略校验；只有 `assetStatus=ready` 才能创建订单。
- `GET /access-url` 返回短期预览／下载授权，不返回永久存储地址；创建订单时仍重新校验用途、归属和上传上下文。

工作流输入上传沿用下列精确 wire 类型；`requiredHeaders` 的键和值都由服务端签名策略给出，前端只原样应用到对应 OSS 请求，不追加应用认证头：

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

interface UploadPartSignatureVO {
  partNumber: number;
  putUrl: string;
  expiresAt: string;
  requiredHeaders: Record<string, string>;
}

interface UploadPartSignaturesVO {
  uploadId: string;
  parts: UploadPartSignatureVO[];
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

- `POST /api/assets/uploads/{uploadId}/parts` 请求精确为 `{partNumbers:number[]}`，响应为 `UploadPartSignaturesVO`；分片编号去重升序且每批最多 20 个。
- `POST /api/assets/uploads/{uploadId}/complete` 请求使用 `CompleteUploadRequest`，响应为 `CompleteUploadVO`；`POST /api/assets/uploads/{uploadId}/cancel` 响应为 `CancelUploadVO`；`GET /api/assets/uploads/{uploadId}` 响应为 `UploadSessionVO`。
- 未知网络结果重试必须复用原上传幂等键。只有明确收到 `46212 UPLOAD_SESSION_EXPIRED` 后，前端才废弃旧会话并生成新的上传幂等键；旧会话的迟到响应必须按当前 `uploadId + requestGeneration` 比对后丢弃，不得覆盖新文件、新方案或新输入字段。

### 14.3 创建订单

```text
POST /api/workflow-orders
Idempotency-Key: <1-128 chars>
```

请求：

```json
{
  "templateId": "...",
  "templateVersionId": "...",
  "executionPlanId": "...",
  "schemaHash": "64-lowercase-hex",
  "inputs": {
    "portraitImage": [{ "assetId": "..." }],
    "prompt": "..."
  }
}
```

响应：

```json
{
  "orderId": "...",
  "orderNo": "...",
  "taskId": "...",
  "taskStatus": "pending",
  "createdAt": "..."
}
```

`taskStatus` 是响应时任务真实状态，通常为 `pending`，也可能已推进到 `queued`；前端不能假定创建响应必为某一非终态。

同一 `tenant + workspace + owner + idempotencyKey` 且摘要相同才返回原订单；同 scope 同键不同摘要返回稳定幂等冲突。任何一项 scope 不同都不得命中、回读或泄露另一工作区订单。

字段或字段资产校验失败（`46505/46506`）使用不改变 `R<T>` 顶层的结构化数据：

```json
{
  "code": 46505,
  "msg": "输入内容不符合模板要求",
  "data": {
    "fieldErrors": [
      {
        "inputKey": "portraitImage",
        "reasonCode": "ASSET_TYPE_MISMATCH",
        "message": "请上传符合要求的图片"
      }
    ]
  }
}
```

- `fieldErrors` 按 schema 顺序返回，`inputKey + reasonCode` 稳定；不回显用户文本、文件路径、节点、供应商原始值或内部规则表达式。
- schema 冲突 `46504` 的 `data` 返回当前 `templateVersionId`、`executionPlanId`、`schemaHash`、`changedInputKeys` 和 `removedInputKeys`。前端重新读取 schema、计算可保留值并列出将被清除内容，只有用户确认后才清除和重新提交。

### 14.4 订单查询与取消

```text
GET  /api/workflow-orders
GET  /api/workflow-orders/{orderId}
POST /api/workflow-orders/{orderId}/cancellations
```

订单详情返回：

- 订单编号、创建时间。
- 模板与版本快照。
- 用户选择的供应商展示快照。
- 用户输入展示值和当前用户有权访问的输入资产摘要。
- 平台任务状态、进度和稳定失败信息。
- 稳定 `stage`；`submission_unknown` 只映射为 `confirming_provider_acceptance`，此阶段 `canCancel=false`，不返回内部 attempt／provider 状态。
- 已登记输出资产和主结果。
- 是否可取消、是否可再次制作。

跨用户、跨租户或跨工作区统一返回订单不存在，不泄露资源存在性。

#### 14.4.1 订单与任务 VO 精确字段

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

- `progressPercent` 只在供应商有可信进度且值为 `0..100` 时返回；缺失时页面只显示离散阶段。
- `status × stage` 合法矩阵固定为：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。任何冲突组合都是契约错误，前端不得猜测哪个字段优先。
- `confirming_provider_acceptance` 的订单 `canCancel=false`。
- `status=success` 时 `outputs` 必须恰好一个 `primary=true` 且该主结果 `status=ready`；其他状态最多一个主结果。平台任务只有在全部必需输出成为 `ready` 后才能返回 `success`。
- “再次制作”不修改旧订单，也不调用复制订单接口；它只导航到 `/discover/templates/{templateId}/create`，生成新的订单幂等键，并要求用户重新选择供应商和确认输入。
- `46518 WORKFLOW_ORDER_NO_LONGER_REMAKABLE` 只决定稳定错误文案；页面只有在最新订单详情 `canRemake=true` 时才展示“再次制作”，错误码不能单独开启该操作。
- 输出预览／下载地址不内嵌在订单详情，页面按需调用 `GET /api/assets/{assetId}/access-url`，地址过期后重新签发。

#### 14.4.2 用户任务中心

本次用户端页面同时交付真实 `/tasks` 路由和列表契约，禁止把订单列表伪装为统一任务中心：

```text
GET /api/tasks?pageNum&pageSize&taskType?&status?&keyword?
```

分页行使用以下最小字段：

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

- 空页返回 `rows=[]`，按 `createdAt desc, taskId desc` 稳定排序。
- `taskType/resourceType` 是后端任务注册表中的稳定小写代码，必须匹配 `[a-z][a-z0-9_]{1,63}`；页面用 `taskTypeLabel/title` 展示，不能因遇到非工作流任务而拒绝整页。`status` 使用 `AiTaskStatus`，筛选值由后端注册表与状态白名单校验。
- 所有用户生成根任务都进入本列表；只有已冻结安全跳转的任务返回 `detailTarget`。首期工作流模板任务返回 `{type:'workflow_order',orderId:resourceId}` 并进入 `/orders/{orderId}`，其他任务即使暂未提供详情路由也必须正常展示，不得被过滤成“订单列表”。页面不得把 `resourceType/resourceId` 拼成任意 URL。
- 普通用户响应不返回 owner、tenant、workspace、attempt、外部任务 ID 或供应商内部状态。
- 任务中心要求 `aivideo:task:query`；取消仍走订单取消接口并要求 `aivideo:task:cancel`。

### 14.5 运营端

运营接口使用 `/api/admin` 前缀，至少包括：

```text
/api/admin/discovery/banners/**
/api/admin/discovery/slots/**
/api/admin/workflow-templates/**
/api/admin/workflow-templates/{id}/draft/**
/api/admin/workflow-templates/{id}/versions/**
/api/admin/workflow-templates/{id}/publications
/api/admin/workflow-providers/**
/api/admin/workflow-providers/{id}/connection-tests
/api/admin/workflow-execution-plans/{id}/workflow-imports
/api/admin/workflow-execution-plans/{id}/test-runs
/api/admin/workflow-execution-plans/{id}/runtime-status-changes
/api/admin/workflow-orders/**
POST /api/admin/workflow-orders/{orderId}/provider-attempts/{attemptId}/submission-resolutions
```

精确 REST 路由在 `docs/API_CONTRACT.md` 评审时冻结；前端不得在页面组件中散落路径。

`submission-resolutions` 要求 Header `Idempotency-Key` 和请求字段 `expectedAttemptRevision`、`action`、受控证据备注／摘要；`action` 只允许 `bind_external_task|confirm_not_accepted_and_retry|close_failed`。`bind` 还需提供待核验 task ID 并主动查询；Controller 本身不直接重 POST 供应商。该接口只允许 `aivideo:workflow-order:retry-or-compensate`。

### 14.6 权限

用户端 app 权限复用已经登记的唯一权限事实：

- 发现、模板和订单上下文查询：`aivideo:studio:query`。
- 创建免费工作流订单：`aivideo:studio:generate`；首期不要求 `aivideo:quota:use`。
- 查询订单关联任务：`aivideo:task:query`。
- 取消订单关联任务：`aivideo:task:cancel`。
- 工作流输入上传固定使用 `aivideo:asset:upload`，资产摘要查询使用 `aivideo:asset:query`，输入／输出预览下载使用 `aivideo:asset:download`；三项必须注册到 `app_permission`，不使用通配权限或人物专用权限替代。

用户端 Controller 必须使用 `@SaCheckPermission(..., type = "app")` 和 `AppLoginHelper`，不得用默认 sys 登录逻辑。

运营端 sys 权限：

- `aivideo:discover-home:query`
- `aivideo:discover-home:edit`
- `aivideo:workflow-template:query`
- `aivideo:workflow-template:add`
- `aivideo:workflow-template:edit`
- `aivideo:workflow-template:publish`
- `aivideo:workflow-template:offline`
- `aivideo:workflow-provider:query`
- `aivideo:workflow-provider:add`
- `aivideo:workflow-provider:edit`
- `aivideo:workflow-provider:test`
- `aivideo:workflow-provider:enable`
- `aivideo:workflow-provider:rotate-secret`
- `aivideo:workflow-provider:cost-option`
- `aivideo:workflow-order:query`
- `aivideo:workflow-order:asset-access`
- `aivideo:workflow-order:retry-or-compensate`

运营封面、预览、案例、Banner 和私有 workflow JSON 复用既有 `system:oss:list|query|upload|download`；保存到发现或模板对象时还必须具备对应 `aivideo:discover-home:edit` 或 `aivideo:workflow-template:edit` 并重新校验文件类型。`system:oss:*` 不能授予 app 用户。

页面权限不替代模板状态、订单 owner、资产 owner、工作区和供应商凭据访问校验。

### 14.7 供应商技术回调

首期只暴露精确的 RunningHub 技术入口：

```text
POST /api/integrations/runninghub/callbacks/{attemptNonce}
```

- `attemptNonce` 是每次供应商执行尝试独立生成的高熵随机值；普通 API 永不返回，日志只记录摘要。
- 该入口不使用 app 或 sys 登录态，也不接受模板、订单、owner、结果 URL 或目标状态作为路径／查询控制参数。
- Controller 只进行请求硬限制、最小格式校验和唤醒事件登记，返回不泄露任务是否存在的通用响应；核心任务更新必须等待官方查询复核。
- Security 配置只允许上述精确路径模式匿名访问。新增供应商时必须逐一登记独立路径和验证规则，不能放宽为 `/api/**/callbacks/**` 或整个集成目录匿名。

## 15. 状态、错误与页面行为

### 15.1 状态事实源

- 模板生命周期、模板版本状态和供应商方案运行时状态分别使用集中英文枚举。
- 订单不持久化第二套执行状态；VO 直接包含统一任务摘要。
- RunningHub、ComfyUI 原始状态只在 `ai-video-infra` 映射。
- `submission_unknown` 是供应商执行尝试状态，不是新的订单或平台任务终态；平台任务保持非终态并向用户展示稳定阶段“正在确认供应商是否已受理”。
- 前端只按公共稳定值和错误标识分支，不解析中文 `msg` 或 `promptTips` 原文。

### 15.2 新增稳定错误范围

拟为本模块预留 `46501–46518`，实施前必须在 `docs/API_CONTRACT.md` 查重并冻结：

| 错误码 | 稳定标识 | 页面行为 |
| --- | --- | --- |
| `46501` | `WORKFLOW_TEMPLATE_NOT_FOUND` | 显示模板不存在或已下架。 |
| `46502` | `WORKFLOW_TEMPLATE_VERSION_STALE` | 刷新详情和表单，要求用户重新确认。 |
| `46503` | `WORKFLOW_EXECUTION_PLAN_UNAVAILABLE` | 保留页面并要求选择其他供应商。 |
| `46504` | `WORKFLOW_SCHEMA_MISMATCH` | 重新加载并对比表单，列出将丢失字段，用户确认后再清除不兼容值。 |
| `46505` | `WORKFLOW_INPUT_INVALID` | 精确定位用户字段，不展示节点。 |
| `46506` | `WORKFLOW_INPUT_ASSET_INVALID` | 提示重新上传或选择有权访问的素材。 |
| `46507` | `WORKFLOW_ORDER_IDEMPOTENCY_CONFLICT` | 生成新幂等键并要求重新提交。 |
| `46508` | `WORKFLOW_ORDER_NOT_FOUND` | 订单不存在或无权访问。 |
| `46509` | `WORKFLOW_ORDER_STATE_CONFLICT` | 刷新任务状态和可操作按钮。 |
| `46510` | `WORKFLOW_PROVIDER_UNAVAILABLE` | 显示供应商暂不可用，不自动切换。 |
| `46511` | `WORKFLOW_PROVIDER_REJECTED` | 显示稳定失败说明和可重试性。 |
| `46512` | `WORKFLOW_MAPPING_INVALID` | 运营端定位映射；用户端只显示模板暂不可用。 |
| `46513` | `WORKFLOW_CALLBACK_INVALID` | 拒绝回调并记录安全审计。 |
| `46514` | `WORKFLOW_OUTPUT_INVALID` | 任务失败，运营端查看缺失或非法输出。 |
| `46515` | `WORKFLOW_PROVIDER_CREDENTIAL_INVALID` | 运营端更新凭据；用户端只显示不可用。 |
| `46516` | `WORKFLOW_PUBLISH_PREFLIGHT_FAILED` | 运营端展示未通过检查清单。 |
| `46517` | `WORKFLOW_IMPORT_FAILED` | 保留草稿并允许修正 ID 或重新导入。 |
| `46518` | `WORKFLOW_SUBMISSION_UNRESOLVED` | 订单任务失败；提示供应商受理状态未能在 24h 内确认，可重新制作并由运营审计旧尝试。 |

供应商原始错误、堆栈、服务器路径、完整 `promptTips`、内部 URL 和密钥不得返回普通用户。

### 15.3 页面状态

发现首页必须覆盖：

- 首屏骨架。
- Banner 或推荐位局部失败。
- 空频道。
- 搜索或筛选无结果。
- 分页加载、加载失败和重试。
- 整页权限不足或认证失效。

模板详情必须覆盖：

- 加载、已下架、无可用供应商、预览失败、权限不足和接口失败。

制作页必须覆盖：

- 供应商加载、没有可用供应商、供应商切换。
- 表单加载、字段校验、上传中、上传失败、素材失效。
- 提交中、幂等命中、版本过期、创建失败和创建成功。

订单详情必须覆盖：

- `pending/queued/running/success/failed/cancelled` 等项目统一任务状态。
- 轮询失败但任务仍可能运行的恢复提示。
- 输出登记处理中、结果不可用和访问地址过期刷新。
- 取消确认、取消中、取消成功和状态冲突。

运营页面全部具备加载、空、失败、权限不足、分页、删除或下架确认、提交中和并发修订冲突状态。

## 16. 安全与审计

### 16.1 自建地址与 SSRF

- 只有有权限的 sys 用户可以配置自建 ComfyUI 地址。
- 只允许 `http/https`，拒绝用户信息、片段、非预期端口和不规范 Host。
- 自建服务只允许命中运营预先登记的 Host／CIDR／端口白名单；不能因为“自建”就放开任意内网。连接前和每次重定向后重新解析 DNS、校验 IP 与目标，默认禁用跨 Host 重定向。
- 请求设置连接、读取、总时限、响应大小和下载大小上限。
- RunningHub Host 固定为官方域名，不允许模板级覆盖。
- 普通用户不能提交 URL、Host、回调地址或供应商文件路径。

### 16.2 凭据

- API Key、访问密码和服务令牌只从配置或密钥引用读取。
- 凭据不进入模板版本、订单快照、任务 manifest、日志、异常、VO、mock 或测试快照。
- 凭据轮换有独立权限与审计；旧密钥值不可再次读取。
- 敏感管理接口使用 `@Log` 排除字段或关闭请求、响应保存。

### 16.3 归属与文件

- 用户请求中的 `ownerId`、`tenantId`、`workspaceId` 一律忽略或拒绝；Service 从经过校验的 app actor/workspace 派生。
- 订单列表、详情、取消、输入预览、输出预览和下载都同时校验租户、工作区和 owner。
- 运营端跨用户查询需要独立权限，审计保存 `actor_type=sys_user` 和 `actor_id`。
- 上传同时校验扩展名、声明 MIME、文件魔数、真实解码、大小、数量、图片分辨率、音视频时长和用途；音视频元数据使用受限进程／库读取，具体白名单由字段定义与公共文件策略交集决定。
- 输入和输出均采用流式读写、分段哈希和硬上限，禁止 `getBytes()` 或等价的整文件内存读取。
- 输出下载只允许已配置供应商 Host 或官方结果 Host；每次 DNS 解析和重定向都重新校验，限制重定向次数、响应长度、MIME、魔数和解码结果。只有下载到平台、哈希完成并登记成授权资产后才算可用结果。

### 16.4 回调与幂等

- 回调令牌、事件 ID 和请求摘要不进入普通日志。
- 重复、乱序或终态后的回调不得回退状态。
- 未签名回调必须通过不可猜测 URL 令牌、任务关联和主动查询三重校验。
- 输出登记和未来通知／额度结算以 task/order 维度唯一化；本期不实际产生通知或额度事件。

回调入口既不是 app 用户接口，也不是 sys 运营接口，需要登记一个最窄技术入口例外：

- Controller 仅负责固定供应商回调路径、正文大小限制、速率限制、nonce 摘要定位和最小 BO 校验，放在 `ai-video-user/integration/controller/RunningHubCallbackController`，只由 `ai-video-user-api` 启动模块装配；`ai-video-infra` 不包含 Controller，避免被两个启动模块误扫描暴露。
- 安全配置只放行精确路径模式，不使用覆盖业务 API 的广域 `@SaIgnore`；普通模板、订单、资产和运营接口仍分别执行 app／sys 权限。
- 回调不能读取模板、订单或资产详情，也不能直接写终态；它只登记唤醒事件，由核心 Service 根据任务关联和官方主动查询结果条件更新。
- 例外原因仅为第三方无法携带 app/sys 会话而又需要公网回调；最小范围仅为上述一个类、一个精确路径模式和 `ai-video-user-api` 一个启动模块。替代控制为 nonce、限流、正文上限、任务关联、官方回查、事件幂等和禁止直接推进终态。
- 回归条件至少验证：用户／运营业务 API 仍需各自登录、广域 callback 路径未匿名、`ruoyi-admin` 未暴露该 Controller、伪造／重复／超限回调不改变任务。本例外须同步写入 `docs/DOMAIN_MODEL.md`、`docs/API_CONTRACT.md` 与 `docs/ARCHITECTURE.md`，并以用户对本书面规格的最终明确批准作为项目负责人确认；批准前不得进入实现计划。

## 17. 后端实现边界

建议业务聚合：

```text
ai-video-core/
  discovery/{domain,dto,mapper,service,service.impl}
  workflowtemplate/{domain,dto,mapper,service,service.impl}
  workfloworder/{domain,dto,mapper,service,service.impl}

ai-video-user/
  discovery/{controller,domain.bo,domain.vo}
  workfloworder/{controller,domain.bo,domain.vo}
  integration/controller/RunningHubCallbackController

ai-video-platform/
  discovery/{controller,domain.bo,domain.vo}
  workflowtemplate/{controller,domain.bo,domain.vo}
  workflowprovider/{controller,domain.bo,domain.vo}

ai-video-infra/
  comfyexecution/client
  comfyexecution/provider
  comfyexecution/config
```

- `IWorkflowTemplateService`、`IDiscoveryService`、`IWorkflowOrderService` 等核心 Service 负责业务编排；核心定义 `IWorkflowExecutionService + DTO` 稳定技术契约，基础设施只实现该契约，不反向承载订单状态机。
- 跨模块稳定契约使用对应聚合平级 `dto/*DTO`。
- 供应商执行可通过核心定义的稳定 Service 数据契约被基础设施实现，但不得新建 `port` 或业务 `adapter` 层。
- Entity 保持贫血，版本发布、订单快照、归属、幂等、任务、状态和跨 Mapper 写入都在 Service。
- 多表写使用事务；供应商 HTTP、文件上传下载和健康检查位于事务外，通过 `queued` execution、条件领取扫描器和 best-effort 唤醒衔接。`submission_unknown`、尝试租约和条件状态更新由 Service 统一编排。
- Mapper 使用 `BaseMapperPlus`、`PageQuery`、`PageResult`、查询条件和排序白名单。
- Controller 只接参、校验、权限、当前主体、日志和 `R<T>` 包装。

实现前必须先读 generator 模板和当前 `portrait`、`asset`、`identity`、`quota`、任务相关模块的最近似实现。

## 18. 前端实现边界

### 18.1 用户端

建议结构：

```text
src/pages/discovery/
  index.tsx
  template-detail/
  template-create/
  components/
src/pages/workflow-orders/
  detail/
src/pages/tasks/
src/services/ai-video/discovery/
src/services/ai-video/workflow-orders/
src/services/ai-video/tasks/
```

前后端至少共享或一一映射以下稳定 TypeScript 契约：`DiscoveryHomeVO`、`WorkflowTemplateCardVO`、`WorkflowTemplatePageVO`、`WorkflowTemplateDetailVO`、`WorkflowExecutionPlanVO`、`WorkflowFormSchemaVO`、`CreateWorkflowOrderRequest`、`WorkflowOrderDetailVO`、`AiTaskSummaryVO` 和 `AiTaskListItemVO`。API adapter 统一处理 `R<T>`／`PageResult<T>`、大数字符串、稳定错误标识和取消信号；React 组件不接触原始响应 envelope。

- 页面和组件只依赖领域 Service/Hook，不直接拼接 URL、Header、RuoYi envelope、状态字符串或错误码。
- React Query 由应用级唯一 `QueryClientProvider` 提供；私有查询 key 必须包含当前 app 用户 ID 与 workspace ID，并继续包含频道、筛选、模板版本、供应商方案和 schema hash 等隔离维度。app 会话清除时必须同步清空私有查询缓存。
- 长 ID 一律用字符串。
- 动态表单由经过白名单的控件注册表渲染，禁止后端下发任意组件名、HTML、脚本或表达式。
- 富文本介绍在服务端或前端统一净化，不执行运营输入脚本。
- 上传复用统一资产上传能力；供应商 API Key 永不进入浏览器。

### 18.2 运营端

建议结构：

```text
src/pages/aivideo/discovery-home/
src/pages/aivideo/workflow-template/
src/pages/aivideo/workflow-provider/
src/pages/aivideo/workflow-order/
src/api/aivideo/discovery/
src/api/aivideo/workflow-template/
src/api/aivideo/workflow-provider/
src/api/aivideo/workflow-order/
```

- 列表优先使用 `ProTable`，编辑使用 `ProForm`、`StepsForm`、`DrawerForm` 或分区表单。
- 工作流原始 JSON 只在有权限的运营编辑页按需查看，默认折叠且净化；不在列表或普通日志返回。
- 密钥字段使用一次性设置或轮换交互，不能在表单回填明文。
- 菜单、路由和按钮权限由运营端动态菜单与 `hasPermi` 统一控制。
- 当前运营端通过后端菜单和 `dynamicPage.tsx` 映射页面；新增菜单、隐藏编辑路由和 `migratedPages` 映射必须同步完成，不能落入“页面迁移中”。
- 用户端静态文案进入现有国际化资源；模板标题、介绍、标签等运营内容由接口返回。

### 18.3 Ant Design 使用

实现前使用项目 `.agents/skills/antd/SKILL.md` 和 `@ant-design/cli` 查询实际版本的组件 API、Token、Demo 与语义结构，不凭记忆编写 Ant Design 6 或 ProComponents API。

## 19. Mock、联调与协作顺序

### 19.1 可先行 mock

- 发现首页、模板列表和详情的已发布只读响应。
- 供应商展示列表和不同动态表单 schema。
- 订单详情中的统一任务状态与错误展示。
- 运营端表格、草稿编辑、节点候选和发布检查 UI。

Mock 只存在于开发和测试环境，类型必须由同一 API 契约生成或共享；生产构建不得回退 mock。

### 19.2 必须等待真实后端

- 认证、app 权限和 owner/workspace 归属。
- 模板草稿、发布版本、乐观并发和下架。
- 文件上传会话、资产校验和访问授权。
- 订单幂等、快照、任务创建和取消。
- 供应商凭据、连接测试、工作流读取、真实任务、回调和结果登记。
- 管理端跨用户订单查询与审计。

### 19.3 联调顺序

1. 冻结领域、API、状态、错误码、文件和任务公共契约。
2. 建立迁移和核心 Service 契约。
3. 用户端和运营端使用契约 mock 并行开发。
4. 接入真实模板、版本、供应商和文件 API。
5. 接入自建 ComfyUI E2E。
6. 接入 RunningHub Workflow E2E。
7. 接入 RunningHub AI App E2E。
8. 完成回调、轮询、取消、异常、视觉和双端安全验收。

任何跨模块字段、状态、错误码、任务或文件语义变化必须先更新公共契约并 review，不能由前后端各自临时修改。

## 20. 测试与验证

### 20.1 后端单元与数据测试

- 草稿反复保存不增加发布版本；发布生成不可变递增版本。
- 复制历史版本为新草稿，不修改历史记录。
- 发布检查覆盖无供应商、测试摘要过期、输入输出不完整和主结果数量错误。
- 一个模板版本可绑定多个供应商且表单互不污染。
- owner、tenant、workspace 和运营 actor 隔离。
- 订单只在相同 tenant/workspace/owner/key scope 内同摘要返回原结果、不同摘要冲突；切换工作区后相同 key 不命中旧订单。
- 幂等命中不重复冻结快照、增加资产引用、创建根任务或入队；唤醒失败时 queued 扫描器只恢复同一 execution。
- 免费任务固定 `usageOperationId=null`，测试证明不会写额度操作、账本或结算事实。
- 动态字段拒绝未知 key、错误类型、越界值和多余资产。
- 暂停供应商只影响新订单，不影响已提交订单。
- 唯一约束、恰好一个主输出、历史事实不可物理删除和并发发布冲突均有反向测试。
- 供应商修订只追加；订单／attempt 固定精确 revision，轮换后旧非终态 attempt 仍解析旧 secret version，引用未清零前禁止禁用或删除。
- 任务终态防回退，回调／轮询竞态使用条件更新，重复回调不重复登记输出。
- 供应商成功但资产尚未登记时平台任务仍为 `running`；必需结果缺失不能成功。
- 分页上限、排序白名单、空页 `rows=[]` 和大数字符串化。

### 20.2 供应商契约测试

- 自建 ComfyUI API JSON 解析、输入替换、上传、幂等包装层、prompt ID、history 和输出读取；裸全局 interrupt 不得伪装成单任务取消。
- RunningHub `workflowId + nodeInfoList` 请求类型保持。
- RunningHub 完整 `workflow` 与 `workflowId` 互斥。
- RunningHub Workflow 与 AI App 使用不同端点和请求 DTO；AI App 准确发送 `webappId`，互相传错时失败关闭。
- RunningHub `seed` 显式映射、连线数组拒绝、`promptTips` 节点错误映射。
- RunningHub V2 上传使用 `fileName` 注入图片、音频和视频节点。
- RunningHub V2 查询缺少稳定输出节点身份时只允许唯一主输出；任意多输出、按 URL 顺序猜测和回调丢失后不可恢复的方案必须发布失败。
- Workflow 字符串 `eventData` 二次解析有正文／深度／字段限制；AI App 不外推 node-aware 回调和取消能力。
- RunningHub 远端 workflow/app 摘要漂移、无法读取或与发布快照不一致时停止新执行。
- `retainSeconds` 不由用户请求覆盖。
- 回调重复、回调伪造、正文超限、nonce 错误、查询补偿、临时结果过期和下载重定向校验。
- 供应商已受理但本地超时或受理状态不确定时进入 `submission_unknown`，不得盲目重提。
- `submission_unknown` 的 callback 绑定、sys 三种人工收口、操作幂等、attempt 修订冲突、24h deadline 和 late callback 均有正反测试。
- submitted/unknown 长生命周期 attempt 的 lease 接管复用同一 `provider_call_sequence`；只有确认 `not_accepted` 才能紧邻真实 POST 追加 attempt。

### 20.3 Web 与安全测试

- app 未登录、错误客户端、缺权限、跨用户、跨工作区访问。
- `aivideo:asset:query|upload|download` 分别校验，人物专用权限或通配字符串不能替代；上传幂等不会跨 workspace 命中。
- sys 用户缺少各管理权限和敏感费用配置权限。
- Provider API Key 在 VO、日志、审计、异常和快照中均不可见。
- 回调只放行精确技术路径；业务路径未被广域匿名规则覆盖，回调正文不能直接推进任务终态。
- `RunningHubCallbackController` 只存在于 user-api 路由表，ruoyi-admin 路由 Smoke Test 必须为不存在。
- 自建 URL 的协议、DNS、重定向、内网范围和超时防护。
- 伪造扩展名、MIME、魔数、解码失败、超限大小／分辨率／时长／数量和跨用户资产。
- 输出 Host、DNS 重绑定、临时 URL、Range、重定向上限和大文件流式下载。

### 20.4 前端测试

- 发现频道、筛选、URL query、无限加载和搜索空态。
- 卡片悬停预览同时支持键盘焦点、减少动态效果和失败降级，不能只依赖鼠标 hover。
- 模板下架、无供应商、预览失败和权限不足。
- 用户切换供应商后加载不同 schema；只保留兼容 `semanticKey` 值。
- 动态控件的稳定 JSON 值形状、默认值、缺失／空值、必填、范围、选项、单／多文件数组和文件规则。
- `fieldErrors` 精确映射 `inputKey`；schema 冲突先列出不兼容值，未确认时不得清除。
- 提交中防重复、幂等命中、版本过期、上传失败和重试。
- 订单任务状态、轮询恢复、取消确认和结果访问地址刷新。
- 用户 `/orders/:orderId` 拒绝 sys 会话与非 owner；运营订单详情只走独立后台路由。
- 运营草稿、发布、复制历史版本、节点映射、测试运行、密钥轮换，以及菜单／隐藏路由／component key 映射。

### 20.5 视觉与端到端

- 固定 `1440 × 900` 对照参考站和本地内容区截图。
- 验证 Banner、推荐区、频道、筛选、瀑布流、卡片比例、悬停预览和滚动加载。
- 至少完成一条自建 ComfyUI、一条 RunningHub Workflow 和一条 RunningHub AI App 的真实链路：运营配置、测试、发布、用户选择、上传、下单、任务完成和资产访问。
- 验证模板发布新版本后，旧订单仍展示旧版本快照并可读取原结果。

当前可执行前端验证至少包括：

```text
ai-video-webapp: npm.cmd run tsc / npm.cmd run test / npm.cmd run build
ai-video-platform-ui: pnpm lint / pnpm test / pnpm build
```

后端使用受影响 Maven 模块测试和项目本机专用 MySQL 8、Redis 7 集成环境；禁止 Docker、Testcontainers、WSL 或其他容器化测试替代。

## 21. 验收标准

1. `/`、`/studio` 和原“我的”分组五项行为不变；`/discover` 可通过侧栏“探索 / 发现”、业务跳转或直接 URL 进入，且只替换右侧内容区。
2. 发现内容区在固定桌面视口达到参考站同构的模块关系、密度和交互，且不使用 Liblib 品牌资产。
3. 视频模板和创作灵感共用同一工作流模板链路。
4. 运营人员可以创建草稿、配置多个供应商、人工选择输入输出节点、测试并发布版本。
5. 草稿反复保存不增加版本；每次发布生成新的不可变版本。
6. 用户先看模板介绍，再选择供应商；选择不同供应商时只展示该方案所需表单。
7. 用户端永远看不到 API Key、服务器地址、工作流 ID、节点编号或内部默认参数。
8. 文件先成为当前用户私有平台资产，再由后端传输到已选供应商。
9. 创建订单具备 owner 归属、版本冻结、schema 校验和幂等保护，并进入统一任务中心。
10. 平台不自动切换供应商；技术重试不改变订单已选方案。
11. 供应商成功后，只有已指定输出完成下载、校验和资产登记，平台任务才成功。
12. 历史订单继续引用提交时版本；新版本和下架不改写历史订单。
13. 首期不扣积分，但额度扩展字段和 Service 接缝不阻塞未来接入。
14. 用户端与运营端完整覆盖加载、空、失败、权限、分页、提交和异常状态。
15. 自建 ComfyUI、RunningHub Workflow 和 RunningHub AI App 各有一条真实通过的端到端验收证据。
16. RunningHub 无法稳定恢复输出节点身份时，方案只能发布唯一主输出；不以结果顺序或文件名猜测多输出映射。
17. 网络不确定提交不会自动重复调用供应商；未签名回调不直接决定终态，且用户选择的供应商永不自动切换。
18. 本期不发送完成通知，订单详情与统一任务中心是状态查看入口。
19. 订单和上传幂等均包含 tenant/workspace/owner scope；供应商连接与密钥通过不可变修订固定，旧非终态 attempt 可审计重放。

## 22. 公共文档影响

本规格阶段已同步以下公共文档；用户最终批准前仍不得进入实现计划。后续若改变字段、状态、权限或恢复语义，必须先更新这些事实源并重新评审：

- `ai-video-pages.md`：新增发现、模板详情、订单制作和运营端页面。
- `docs/API_CONTRACT.md`：用户端/运营端接口、权限、错误码、幂等、动态表单和回调边界。
- `docs/DOMAIN_MODEL.md`：模板、版本、供应商、执行方案、输入输出、订单、执行事实和精确回调技术入口例外。
- `docs/ASYNC_TASKS.md`：工作流订单任务类型、供应商执行、回调复核、结果登记和取消。
- `docs/ARCHITECTURE.md`：双端模块依赖和 `ai-video-infra` 工作流集成边界。
- `docs/superpowers/specs/2026-08-03-secure-asset-oss-upload-download-design.md`：复用统一上传路径、完整 scope 幂等和无冲突错误码。

公共文档更新后运行：

```powershell
scripts/validate-development-standards.ps1
```

## 23. 外部依据

- Liblib 首页：<https://www.liblib.art/>
- ComfyUI Workflow JSON：<https://docs.comfy.org/specs/workflow_json>
- ComfyUI API 格式工作流与执行概览：<https://docs.comfy.org/development/cloud/overview>
- RunningHub 高级 ComfyUI 任务接口：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749013>
- RunningHub AI App 创建任务：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749010>
- RunningHub AI App 请求模型：<https://www.runninghub.cn/runninghub-api-doc-cn/schema-252329711>
- RunningHub `nodeInfoList`：<https://www.runninghub.cn/runninghub-api-doc-cn/doc-8287336>
- RunningHub V2 文件上传：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749007>
- RunningHub V2 任务结果查询：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425767306>
- RunningHub 旧版任务结果接口（官方已标记将废弃）：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749004>
- RunningHub 取消 ComfyUI 任务：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749015>
- RunningHub webhook 详情查询：<https://www.runninghub.cn/runninghub-api-doc-cn/api-425749005>
- RunningHub API 更新日志：<https://www.runninghub.cn/runninghub-api-doc-cn/doc-8287335>
- RunningHub 工作流完整接入示例：<https://www.runninghub.cn/runninghub-api-doc-cn/doc-8287342>
