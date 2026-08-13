# 发现页 RunningHub 单执行配置设计

## 1. 文档状态

- 日期：2026-08-10。
- 模块：用户端发现首页、模板详情、模板制作与订单；运营端发现配置、模板、RunningHub 账号和订单运维。
- 适用端：`ai-video-webapp`、`ai-video-platform-ui`、`ai-video-user`、`ai-video-platform`、`ai-video-core`、`ai-video-infra`、`ai-video-user-api` 与 `ruoyi-admin`。
- 设计状态：对话设计已确认，等待书面规格审查。
- 风险等级：红色高风险。
- 实施前置：本文通过用户书面审查后，使用 `writing-plans` 生成实现计划；当前规格阶段不修改运行时代码。

## 2. 变更背景与现状结论

2026-08-05 的发现页设计将一个模板建模为多个供应商执行方案，并要求用户在自建 ComfyUI、RunningHub Workflow 与 RunningHub AI App 之间主动选择。现已确认改变该规则：发现页中的 ComfyUI 工作流全部通过 RunningHub 执行，用户不再知道或选择服务商。

仓库检查得到以下事实：

1. 用户前端已经存在发现首页、详情、制作和订单详情页面，但制作页仍展示三个供应商选项，并依赖 Mock 返回执行方案。
2. 用户后端当前仅实现发现首页和模板列表入口，Service 仍返回空数据；模板详情、制作配置、订单、上传和 RunningHub 执行尚未形成真实闭环。
3. 运营前端和运营后端尚未实现发现配置、工作流模板、RunningHub 账号、执行配置和订单运维页面／接口。
4. 运行时代码中没有 RunningHub Client、配置、提交、查询、取消或结果登记实现。
5. 现有 `ruoyi-workflow` 是 Warm-Flow 审批流，不是 AI 工作流模板，禁止复用为本模块事实源。
6. 现有数字人模块的自建 `ComfyUiClient` 属于另一条业务链，本次不修改，也不得复用为发现页的新执行入口。
7. `docs/API_CONTRACT.md` 仍冻结多供应商选择、`executionPlanId` 和 `templateVersionId`，实施前必须先改为本文契约。

因此，本次不是隐藏一个单选控件，而是将用户契约、运营模型、数据库结构和任务执行统一改为“模板唯一当前配置、服务端解析、RunningHub 执行”。

## 3. 与既有规格的关系

本文取代 `2026-08-05-discovery-multi-provider-workflow-template-design.md` 中以下内容：

- 用户主动选择供应商。
- 一个模板绑定多个执行方案。
- 用户端供应商类型、展示名和方案数量。
- 自建 ComfyUI 作为发现页执行方式。
- 模板草稿／不可变发布版本／回滚版本。
- 用户端 `execution-plans` 与方案级表单接口。
- `templateVersionId`、`executionPlanId` 订单入参。
- 订单执行配置快照和凭据修订快照。
- 自动切换、故障切换或多配置选择的任何扩展预留。

既有规格下列内容继续有效：

- `/discover` 入口、发现首页信息架构和项目自有壳层。
- `video_template` 与 `workflow_inspiration` 两个内容频道。
- 模板详情、动态表单、订单详情和统一任务中心入口。
- 发现 Banner、推荐位、分类、标签、排序等运营能力。
- 文件归属、工作空间、幂等、权限、结果资产和免费策略。
- Liblib 仅作为内容结构参考，不复制品牌或受保护素材。

冲突时以本文为准。

## 4. 已确认决策

### 4.1 用户端隐藏执行实现

- 用户端不显示 `self_hosted_comfyui`、`runninghub_workflow` 或 `runninghub_ai_app`。
- 用户端不显示服务商名称、执行模式、方案卡片、方案数量、Workflow ID、Web App ID、节点 ID 或 RunningHub 外部任务 ID。
- 模板详情点击“使用此模板”后直接进入动态表单，不存在“选择运行方案”步骤。
- 用户请求不能指定执行配置，后端只读取模板唯一当前配置。

### 4.2 运营端支持两种 RunningHub 模式

- `runninghub_workflow`：面向 ComfyUI 工作流，使用 RunningHub Workflow API。
- `runninghub_ai_app`：面向 RunningHub AI App，仍可由运营端绑定，用户无感。
- `self_hosted_comfyui` 不允许用于发现页新配置，也不出现在运营端新建／编辑选项中。
- 本次不改变现有数字人等其他模块的自建 ComfyUI 链路。

### 4.3 一个模板一个当前配置

- 每个模板最多绑定一个当前执行配置，数据库以 `(tenant_id, template_id)` 唯一索引保证；草稿可暂时没有配置，但进入测试或启用前必须恰好有一个有效配置。
- 不提供多个 RunningHub 配置供用户或系统选择。
- 不做账号、Workflow、AI App 或供应商故障切换。
- 不做自动路由、权重、优先级或降级。
- 模板、执行配置和 RunningHub 账号保留单调递增的 `row_revision` 作为乐观锁和 CAS 并发令牌；它不形成历史记录，不提供查询、回滚或版本管理。

### 4.4 无模板版本管理

- 不建立模板版本表、执行配置版本表或发布版本号。
- 运营保存时直接更新模板和唯一当前配置。
- 不提供版本列表、版本比较、版本回滚或“复制旧版本为草稿”。
- 模板仍有草稿／待测试／启用／停用等当前状态，但这些是可用性状态，不是版本。

### 4.5 不保存订单执行配置快照

- 订单不复制 Workflow ID、Web App ID、节点映射、输出规则、完整执行配置或密钥修订。
- 排队任务在真正提交 RunningHub 前重新读取模板当前配置。
- 当前表单结构已变化时，旧请求失败并要求用户重新提交。
- 当前表单结构未变但映射、Workflow、AI App 或账号已变化时，使用最新配置。
- 发起 RunningHub 请求前必须保存 `runninghubAccountId`、`executionMode` 和 `submissionStartedAt`；已受理后继续保存 `externalTaskId`、`submittedAt` 和查询截止时间。这些是防止重复提交并继续查询外部任务所必需的运行事实，不是配置快照。
- 账号密钥直接覆盖更新，不保留密钥修订；已提交任务后续使用该账号记录的当前密钥查询。
- 已提交任务处理结果时不读取 `output_policy_json` 做类型、数量或唯一主结果校验；RunningHub 返回的所有有效结果都按返回顺序接收。该字段仅为历史兼容列，新保存配置固定写入空对象。

### 4.6 任务、结果与额度

- 所有用户生成与运营测试生成均进入统一任务中心。
- 用户生成首期继续免费，`usageOperationId=null`，不冻结、不扣减、不退款，也不写额度账本。
- RunningHub 不可用时任务失败，不回退自建 ComfyUI。
- 提交是否受理未知时不自动重新提交，避免重复生成和重复扣费。
- 平台完成结果下载、校验和资产登记后，任务才能成功。

## 5. 范围与不做范围

### 5.1 本次范围

- 用户端发现首页、模板详情、单表单制作、订单详情和任务中心跳转。
- 用户端真实模板查询、制作配置、上传、订单创建、查询和取消接口，以及再次制作导航。
- 运营端发现内容、模板、唯一执行配置、RunningHub 账号、测试运行、启停和订单运维。
- RunningHub Workflow 与 RunningHub AI App 的真实服务器端集成。
- RunningHub V2 素材上传、任务提交、任务查询、Workflow 取消、结果下载和资产登记。
- 动态输入表单、字段校验、素材归属、节点映射和 RunningHub 多结果资产登记。
- 数据库迁移、权限字典、错误码、Mock、单元测试、集成测试和端到端验收。
- 公共 API、领域、异步任务、架构和页面说明同步。

### 5.2 不做范围

- 发现页自建 ComfyUI。
- 用户端任何供应商或模式选择。
- 多执行配置、多账号自动选择、负载均衡、故障切换或自动降级。
- 模板／配置版本、回滚和订单配置快照。
- 供应商 Webhook；本次统一采用有界轮询。
- 用户自行填写 API Key、服务器地址、Workflow ID、Web App ID 或工作流 JSON。
- 现金价格、支付、退款、发票和对账。
- 修改数字人、时间线或其他模块现有供应商链路。
- DDD、Clean Architecture、Hexagonal Architecture，或 `application`、`port`、`adapter`、`command`、`model` 等平行业务层。

## 6. 风险与最小任务卡

### 6.1 风险等级

本任务为红色高风险，命中以下治理触发条件：

- 修改用户端与运营端共享 API、数据库对象、状态和权限。
- 保存第三方 API Key 与访问密码并向外部 AI 服务发送用户素材。
- 创建异步任务，涉及幂等、轮询、取消、超时和终态一致性。
- 下载外部结果并登记为用户资产，涉及 SSRF、文件类型、归属和访问控制。
- RunningHub 请求可能产生外部费用，提交结果未知时存在重复费用风险。
- 运营端具有模板启停、账号配置和跨用户订单查询能力。

### 6.2 单一目标

交付一套由运营端为每个发现模板维护唯一 RunningHub 执行配置、用户直接填写表单生成、平台统一追踪任务和登记结果资产的完整链路，确保用户无法看到或选择服务商，也无法绕过当前唯一配置。

### 6.3 允许影响的模块

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`
- `docs/ARCHITECTURE.md`
- `docs/DOCUMENT_MAP.md`
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

禁止借本任务重构身份、数字人、时间线、积分、声音、文案、作品或 Warm-Flow 审批模块。

### 6.4 候选工作任务与审查

| 工作任务 | 风险 | 主要输出 | 必须审查与验证 |
| --- | --- | --- | --- |
| 公共契约与数据库 | 红色 | API、表、索引、状态、错误码、权限和迁移 | 契约 owner；后端数据／迁移独立审查；文档规范校验 |
| RunningHub 集成与统一任务 | 红色 | Client、上传、提交、轮询、取消、结果登记 | 外部信任边界与文件安全专项审查；Mock Server 契约测试 |
| 用户端前后端 | 红色 | 单表单制作、订单、上传、权限和任务详情 | 账号／工作空间／素材归属反向测试；前端状态测试 |
| 运营端前后端 | 红色 | 模板、唯一配置、账号、测试、启停和订单运维 | 高权限、敏感字段、审计和误操作专项审查 |
| 联调与验收 | 红色 | Workflow／AI App E2E、失败收口和回归证据 | 一次完整验收；修复后只复核差异和直接受影响测试 |

同一红色工作任务的实施或混合阶段最多同时使用两名智能体，其中一名实施、一名独立审查或验证；不得递归扩大审查范围。

## 7. 总体架构与职责

用户链路：

```text
ai-video-webapp
  -> 用户 discovery / workflow-order / asset API
    -> ai-video-user Controller / BO / VO
      -> ai-video-core 模板、订单和任务 Service
        -> ai-video-infra RunningHub Client
          -> RunningHub Workflow 或 AI App
        -> 平台资产 Service
```

运营链路：

```text
ai-video-platform-ui
  -> /api/admin/discovery/**
  -> /api/admin/workflow-templates/**
  -> /api/admin/runninghub-accounts/**
  -> /api/admin/workflow-orders/**
    -> ai-video-platform Controller / BO / VO
      -> 同一 ai-video-core Service
        -> ai-video-infra RunningHub Client
```

职责边界：

- 用户前端只负责内容展示、表单输入、上传、提交和任务结果，不推断执行模式。
- 用户后端根据 `templateId` 解析唯一当前配置，用户入参不能覆盖执行选择。
- 运营前端维护模板、表单、映射和 RunningHub 账号，但永不回显完整密钥。
- 运营后端校验配置互斥、唯一性、测试状态和权限，Controller 不堆执行逻辑。
- `ai-video-core` 使用贫血 Entity 与 Service 编排业务，不直接依赖 RunningHub 原始对象。
- `ai-video-infra` 封装 RunningHub 请求／响应和外部错误转换，原始供应商 DTO 不进入用户或运营 VO。
- 统一任务中心是运行状态唯一事实源，不另造一套平行订单状态机。

## 8. 用户端页面设计

### 8.1 路由

继续使用：

```text
/discover
/discover/templates/:templateId
/discover/templates/:templateId/create
/orders/:orderId
/tasks
```

现有 `/` 到 `/studio` 的重定向和项目壳层保持不变。

### 8.2 发现首页

- 模板卡片删除 `availableExecutionPlanCount` 和“多个可用方案”文案。
- 用户发现首页和列表只返回当前 `enabled` 且执行配置、账号、成功测试均有效的模板；返回的卡片均可进入详情，不展示可用性徽标。
- 列表具备加载、空、搜索无结果、接口失败、权限不足和分页状态。
- 模板在列表返回后变为不可用时，详情返回 `46501`；不得继续展示旧缓存内容或提供制作入口。

### 8.3 模板详情

- 保留模板说明、示例、输入要求、预计耗时和“使用此模板”。
- 删除“下一步可自主选择制作服务”等文案。
- 不显示服务商、执行模式或外部资源标识。
- 模板不可用时接口返回 `46501`，页面展示稳定不可用状态并提供返回发现页操作。

### 8.4 制作页

- 删除“选择运行方案”步骤、Radio、方案卡片和 `providerLabel`。
- 页面直接加载 `creation-config` 并渲染动态表单。
- 支持文本、数字、布尔、单选、多选、图片、音频、视频和受控文件。
- 未识别控件必须阻断整表提交并显示“当前模板暂不受支持”，不能过滤字段后继续生成。
- 上传必须使用真实资产上传 API，禁止 `setTimeout` 或本地伪造 `assetId`。
- 提交中禁用重复提交；失败后保留仍兼容的用户输入。
- Schema 变化时刷新配置、标记变化字段，并要求用户重新确认。
- 免费信息来自 `billingPolicy.mode=free`，前端不能硬编码为永远免费。

### 8.5 订单与任务详情

- 订单详情展示统一任务状态、阶段、进度、结果资产、用户可理解的失败原因、取消和再次制作。
- 删除 `providerKind`、`providerDisplayName`、`executionMode` 和外部任务号。
- “再次制作”只在 `canRemake=true` 时导航到模板当前制作页；不调用复制／重放接口，也不复制历史执行配置。
- 订单终态后停止轮询；页面卸载、退出登录或切换工作空间时取消请求并清理私有缓存。
- Workflow 已提交后可按状态显示取消；AI App 提交后不承诺取消能力。

## 9. 用户端 API 契约

### 9.1 查询接口

```text
GET /api/discovery/home
GET /api/discovery/templates
GET /api/discovery/templates/{templateId}
GET /api/discovery/templates/{templateId}/creation-config
```

删除：

```text
GET /api/discovery/templates/{templateId}/execution-plans
GET /api/discovery/templates/{templateId}/execution-plans/{executionPlanId}/form-schema
```

本仓库尚无对应真实后端实现和生产数据，不保留旧接口兼容窗口；前后端在同一交付中切换。

### 9.2 精确只读 VO

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

interface WorkflowMediaVO {
  mediaId: string;
  mediaType: WorkflowMediaType;
  url: string;
  posterUrl?: string;
  width: number;
  height: number;
  alt: string;
}

interface DiscoveryTagVO {
  tagCode: string;
  label: string;
}

interface WorkflowTemplateCardVO {
  templateId: string;
  title: string;
  summary: string;
  channel: WorkflowChannel;
  category: { categoryCode: string; label: string };
  tags: DiscoveryTagVO[];
  cover: WorkflowMediaVO;
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
```

`DiscoveryHomeVO`、Banner、频道、分类和标签结构继续沿用 `docs/API_CONTRACT.md` 的既有 wire；其中所有模板卡片替换为上面的新 `WorkflowTemplateCardVO`。

`estimatedDurationSeconds` 若返回，必须是秒为单位的非负安全整数。媒体 URL 继续遵守现有 HTTPS／同源相对路径校验规则。

### 9.3 动态表单与制作配置

```ts
type WorkflowFormControl =
  | 'text'
  | 'textarea'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'select'
  | 'multi_select'
  | 'image'
  | 'audio'
  | 'video'
  | 'file';

type WorkflowFormValue = string | boolean | string[] | Array<{ assetId: string }>;

interface WorkflowInputFieldVO {
  inputKey: string;
  semanticKey?: string;
  label: string;
  description?: string;
  control: WorkflowFormControl;
  valueType: WorkflowInputValueType;
  required: boolean;
  defaultValue?: WorkflowFormValue;
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
  schemaHash: string;
  fields: WorkflowInputFieldVO[];
  estimatedDurationSeconds?: number;
  billingPolicy: { mode: 'free' };
}
```

制作配置只在模板当前可用时返回成功对象。模板不可用返回 `46501`；唯一执行配置、账号或测试状态不可用返回 `46503`，不使用 `available=false` 的半完整响应。

规范值形状：文本和单选是 string；整数和小数是无指数的规范十进制 string；布尔值是 JSON boolean；多选是去重保序 string[]；任意文件控件都是 `{assetId:string}[]`。当前范围每个文件控件固定 `maxItems=1`，但仍使用数组 wire。可选未填字段省略，不发送 null。未知 Schema 版本、控件、值类型、额外输入 key 或额外对象属性必须拒绝。

控件和值类型组合固定为：`text|textarea|select -> string`、`integer -> integer`、`decimal -> decimal`、`boolean -> boolean`、`multi_select -> string_array`、`image|audio|video|file -> asset_array`。`select|multi_select` 必须有非空且 value 唯一的 options；文件控件必须有与控件一致的 `constraints.assetType`，且不得提供默认资产。

`schemaHash` 格式固定为 `sha256:` 加 64 位小写十六进制。哈希输入是对象 `{ "schemaVersion": "workflow-form-1", "fields": [...] }` 经 RFC 8785 JSON Canonicalization Scheme 生成的 UTF-8 字节，再计算 SHA-256；数组顺序保留，展示以外且不参与输入校验的模板字段不进入哈希。节点映射、Workflow、AI App 或账号变化不进入表单哈希；表单不变时排队任务使用最新执行配置。

禁止在任何用户 wire 对象中添加或容忍：

```text
providerKind
providerDisplayName
executionMode
executionConfigId
executionPlanId
templateVersionId
workflowId
webappId
nodeId
fieldName
externalTaskId
```

集中 adapter 遇到上述额外字段必须拒绝响应且不得写入缓存或日志；不能只靠 React 不渲染来隐藏。

### 9.4 通用私有素材上传上下文

继续复用：

```text
POST /api/assets/uploads
POST /api/assets/uploads/{uploadId}/parts
POST /api/assets/uploads/{uploadId}/complete
POST /api/assets/uploads/{uploadId}/cancel
GET  /api/assets/uploads/{uploadId}
GET  /api/assets/{assetId}/access-url?disposition=inline|attachment
```

`purpose='workflow_input'` 的创建上传请求将旧 `templateVersionId/executionPlanId` 替换为：

```text
templateId
schemaHash
inputKey
```

服务端从当前 tenant、模板、Schema 和 `inputKey` 派生允许的文件类型、大小和业务上下文。上传幂等 scope 固定为 `(tenantId,workspaceId,ownerId,idempotencyKey)`。配置变化不会删除已经完成的私有资产，但订单提交时必须重新校验当前 Schema、归属、工作空间和 Ready 状态。

### 9.5 创建订单

```http
POST /api/workflow-orders
Idempotency-Key: <客户端生成且仅用于本次意图的键>
```

```json
{
  "templateId": "1001",
  "schemaHash": "sha256:...",
  "inputs": {
    "prompt": "用户输入",
    "image": [{ "assetId": "2001" }]
  }
}
```

删除 `templateVersionId` 和 `executionPlanId`。

后端在一个事务内完成：

1. 校验 App 会话、权限、tenant、用户和工作空间。
2. 查询模板当前状态和唯一执行配置。
3. 校验 `schemaHash`、字段集合、类型、范围和未知字段。
4. 校验所有素材 `tenantId + ownerId + workspaceId + ready + mediaType`。
5. 用规范化请求计算摘要并校验幂等键。
6. 创建工作流订单和统一任务；任一步失败整体回滚。

精确创建响应为：

```ts
interface CreateWorkflowOrderVO {
  orderId: string;
  orderNo: string;
  taskId: string;
  taskStatus: 'pending' | 'queued';
  createdAt: string;
}
```

同一 `(tenantId,workspaceId,ownerId,Idempotency-Key)` 与相同摘要返回原订单；同 scope 同键不同摘要返回稳定冲突错误。

### 9.6 订单查询、取消与再次制作

```text
GET  /api/workflow-orders/{orderId}
POST /api/workflow-orders/{orderId}/cancellations
```

- 所有查询键必须包含当前 `tenantId + ownerId + workspaceId`。
- 不提供 `/remake` 后端接口。“再次制作”只在最新详情 `canRemake=true` 时导航到模板制作页；前端可用订单详情中的输入预填，但必须获取当前制作配置并重新校验 Schema 与素材。
- 已删除、停用、待测试或当前配置不可用的模板返回 `WORKFLOW_ORDER_NO_LONGER_REMAKABLE`。

精确任务与订单响应为：

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
    title: string;
    cover: WorkflowMediaVO;
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

订单保存模板标题、封面和输入展示所需的最小展示快照，以保证改名或逻辑删除后仍可读；该快照不包含任何执行模式、远端 ID、节点映射、输出策略或密钥，不属于订单执行配置快照。

合法状态矩阵：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。`progressPercent` 只在可信且为 0..100 时返回；成功订单可以包含任意数量和任意混合类型的 Ready 结果。平台可把返回顺序第一项标记为默认预览，但该标记不参与任务成功校验。

### 9.7 权限矩阵

| 端点 | App 权限 |
| --- | --- |
| 发现首页、模板列表、模板详情、制作配置 | `aivideo:studio:query` |
| 创建工作流订单 | `aivideo:studio:generate` |
| 查询订单与统一任务 | `aivideo:task:query` |
| 取消订单任务 | `aivideo:task:cancel` |
| 创建／查询上传 | `aivideo:asset:upload`、`aivideo:asset:query` |
| 预览／下载输入输出资产 | `aivideo:asset:download`，并再次校验 tenant、owner 和 workspace |

所有用户端 Controller 使用 `@SaCheckPermission(..., type = "app")` 和 `AppLoginHelper`，tenant、owner 和 workspace 只从会话派生，请求不得提交这些字段。

### 9.8 同步错误与异步失败

同步 HTTP 业务错误通过 `R.code` 返回：

| 错误码 | 稳定标识 | 用户端规则 |
| --- | --- | --- |
| `46501` | `WORKFLOW_TEMPLATE_UNAVAILABLE` | 显示模板已下架或暂不可用。 |
| `46502` | `WORKFLOW_CREATION_CONFIG_STALE` | 响应 `data.currentSchemaHash`；刷新制作配置并按下述规则重新确认。 |
| `46503` | `WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE` | 显示当前执行配置不可用；不得提示选择其他方案。 |
| `46505` | `WORKFLOW_INPUT_INVALID` | 按 Schema 顺序展示结构化字段错误。 |
| `46506` | `WORKFLOW_INPUT_ASSET_INVALID` | 映射素材归属、类型、状态或工作空间错误。 |
| `46507` | `WORKFLOW_ORDER_IDEMPOTENCY_CONFLICT` | 用户重新确认后才能生成新幂等键。 |
| `46509` | `WORKFLOW_ORDER_CANCEL_CONFLICT` | 刷新真实状态，不在本地强改终态。 |
| `46518` | `WORKFLOW_ORDER_NO_LONGER_REMAKABLE` | 禁用再次制作并显示稳定原因。 |

`46502` 只返回当前哈希，不依赖服务端保存旧 Schema。前端将当前页面缓存的旧 `fields` 与刷新后的新 `fields` 按 `inputKey` 比较：相同 `control + valueType` 且当前值满足新约束视为兼容；删除、类型变化或不满足新约束视为不兼容；新增必填字段视为待补充。前端展示新增、变化和删除键，用户确认后才清理不兼容值，且不得自动提交。

订单已经创建后的异步失败不改变订单查询 HTTP 成功响应，而进入 `AiTaskSummaryVO.failureCode/failureMessage/retryable`：

| `failureCode` | `retryable` | 用户端规则 |
| --- | --- | --- |
| `WORKFLOW_SUBMISSION_UNKNOWN` | `false` | 提示提交结果未知，禁止自动重试；用户确认后只能创建新订单。 |
| `WORKFLOW_EXECUTION_FAILED` | 由受控错误分类决定 | 显示稳定执行失败文案，不暴露原始 RunningHub 错误。 |
| `WORKFLOW_OUTPUT_INVALID` | `false` | 显示结果处理失败，任务不得成功。 |
| `WORKFLOW_CONFIG_CHANGED` | `true` | 提示模板配置已更新，返回制作页重新确认。 |

旧 `46504 WORKFLOW_FORM_SCHEMA_CONFLICT`、`WORKFLOW_TEMPLATE_VERSION_STALE`、`WORKFLOW_EXECUTION_PLAN_UNAVAILABLE` 和用户选择其他方案的语义明确废止。

## 10. 运营端页面与 API

### 10.1 页面

运营端新增：

```text
/aivideo/discovery-home
/aivideo/workflow-template
/aivideo/workflow-template/:id
/aivideo/runninghub-account
/aivideo/workflow-order
/aivideo/workflow-order/:id
```

页面职责：

- 发现首页：Banner、推荐位、频道、分类、标签和排序。
- 模板列表：名称、频道、分类、当前状态、执行模式、测试状态、更新时间和操作。
- 模板编辑：基本信息、用户表单、唯一执行配置、测试运行和启停；不提供输出类型、结果数量或主结果配置。
- RunningHub 账号：账号名称、密钥掩码、启停和连接测试。
- 订单运维：按用户、工作空间、模板、状态和时间查询，查看脱敏失败信息与结果资产。

所有管理页必须有加载、空、筛选无结果、失败、权限不足、分页、保存中、保存成功和保存失败状态。

### 10.2 模板编辑规则

- 基本信息与用户表单保存在模板当前记录。
- 执行模式只能选 `runninghub_workflow` 或 `runninghub_ai_app`。
- Workflow 模式要求 `workflowId`，禁止 `webappId`。
- AI App 模式要求 `webappId`，禁止 `workflowId`。
- 两种模式都要求一个启用的 RunningHub 账号、字段映射和总超时；不配置输出类型、结果数量或唯一主结果。
- `accessPassword` 可空，只写不读；留空表示不修改已保存值，显式清除需要独立确认。
- 运营人员先点击“读取远端结构”，后端使用当前账号和远端 ID 获取候选节点；页面只允许从候选节点中选择用户字段映射，不允许自由输入任意节点或上传任意工作流 JSON。
- 修改用户表单、执行模式、账号、远端 ID、访问密码或输入映射后，模板自动进入 `pending_test`，清除当前成功测试资格。
- 更新 RunningHub API Key 后，账号健康状态重置为 `unknown`，所有引用该账号的模板进入 `pending_test`。
- 测试成功后运营人员才能启用；测试失败保持不可用。
- 测试运行请求必须携带 `Idempotency-Key`、测试输入和 `confirmExternalCost=true`；页面明确提示将产生平台侧 RunningHub 消耗，但不写用户额度账本。
- 保存、测试完成和启用都使用 `expectedRevision` 做 CAS。测试任务只在其记录的模板、执行配置和账号行修订仍等于当前修订时写入成功资格；旧测试完成不能使新配置可用。
- 删除使用中的模板或账号只允许逻辑删除；存在订单或任务时禁止物理删除。

### 10.3 运营 API

```text
GET    /api/admin/workflow-templates
POST   /api/admin/workflow-templates
GET    /api/admin/workflow-templates/{templateId}
PUT    /api/admin/workflow-templates/{templateId}
DELETE /api/admin/workflow-templates/{templateId}
GET    /api/admin/workflow-templates/{templateId}/execution-config
PUT    /api/admin/workflow-templates/{templateId}/execution-config
POST   /api/admin/workflow-templates/{templateId}/execution-config/inspections
POST   /api/admin/workflow-templates/{templateId}/test-runs
POST   /api/admin/workflow-templates/{templateId}/enable
POST   /api/admin/workflow-templates/{templateId}/disable

GET    /api/admin/runninghub-accounts
POST   /api/admin/runninghub-accounts
GET    /api/admin/runninghub-accounts/{accountId}
PUT    /api/admin/runninghub-accounts/{accountId}
DELETE /api/admin/runninghub-accounts/{accountId}
POST   /api/admin/runninghub-accounts/{accountId}/connection-tests

GET    /api/admin/workflow-orders
GET    /api/admin/workflow-orders/{orderId}
```

`PUT`、`enable`、`disable` 请求必须携带 `expectedRevision`；修订不匹配返回稳定并发冲突，不允许后写静默覆盖。`inspections` 只读取受控远端结构并返回候选节点，不保存映射；Workflow 使用官方工作流 JSON 接口，AI App 使用官方 API 调用示例接口。候选响应只对有权限的 Sys 运营人员可见，且不包含 API Key、访问密码或默认节点敏感值。

RunningHub 账号连接测试固定调用无生成副作用的官方 `POST /uc/openapi/accountStatus`。`code=0` 且响应结构合法才视为凭据有效；余额和当前任务数只用于当次受控诊断，不写普通日志、不返回给无账号测试权限的人员，也不能用创建生成任务冒充连接测试。

发现 Banner、推荐位和分类接口继续沿用既有发现页规格，在 `docs/API_CONTRACT.md` 中与上述接口一并冻结。

### 10.4 运营权限

用户端 App 权限复用既有事实：

- 发现与模板查询：`aivideo:studio:query`。
- 创建免费工作流订单：`aivideo:studio:generate`。
- 订单与任务查询／取消：`aivideo:task:query`、`aivideo:task:cancel`。
- 素材上传／查询／下载：`aivideo:asset:upload`、`aivideo:asset:query`、`aivideo:asset:download`。

精确端点矩阵以 9.7 节为准。用户 Controller 必须使用 `@SaCheckPermission(..., type = "app")` 和 `AppLoginHelper`。

运营端 Sys 权限：

```text
aivideo:discover-home:query
aivideo:discover-home:edit
aivideo:workflow-template:query
aivideo:workflow-template:add
aivideo:workflow-template:edit
aivideo:workflow-template:remove
aivideo:workflow-template:inspect
aivideo:workflow-template:test
aivideo:workflow-template:enable
aivideo:workflow-template:disable
aivideo:runninghub-account:query
aivideo:runninghub-account:add
aivideo:runninghub-account:edit
aivideo:runninghub-account:remove
aivideo:runninghub-account:test
aivideo:runninghub-account:enable
aivideo:runninghub-account:disable
aivideo:runninghub-account:update-key
aivideo:workflow-order:query
aivideo:workflow-order:asset-access
```

页面权限不替代 tenant 数据范围、模板状态、订单 owner、工作空间、资产 owner 和敏感字段访问校验。

## 11. 数据模型

### 11.1 `av_workflow_template`

主要字段：

```text
id
tenant_id
channel
name
slug
summary
description
cover_asset_id
category_id
tags_json
form_schema_json
schema_hash
status
recommended
sort_no
estimated_duration_seconds
billing_mode
enabled_at
execution_relevant_updated_at
row_revision
create_by / create_time / update_by / update_time / del_flag
```

约束：

- `channel` 只允许 `video_template|workflow_inspiration`。
- `status` 只允许 `draft|pending_test|enabled|disabled`。
- `billing_mode` 首期固定 `free`。
- `schema_hash` 由规范化 `form_schema_json` 计算。
- 用户查询只返回 `enabled` 且执行配置、账号和测试状态均有效的模板。
- 所有查询默认受 `tenant_id` 隔离；用户端 tenant 从 App 会话派生。
- `row_revision` 每次更新单调递增，只用于 CAS，不创建历史行。
- 用户表单或执行相关字段变化时更新 `execution_relevant_updated_at` 并进入 `pending_test`。

### 11.2 `av_workflow_execution_config`

主要字段：

```text
id
tenant_id
template_id
execution_mode
runninghub_account_id
workflow_id
webapp_id
access_password_ciphertext
input_mapping_json
output_policy_json
timeout_seconds
enabled
last_test_status
last_test_task_id
last_test_template_revision
last_test_execution_revision
last_test_account_revision
last_test_time
last_test_summary
row_revision
create_by / create_time / update_by / update_time / del_flag
```

约束：

- `(tenant_id, template_id)` 唯一，禁止第二条当前配置；执行配置不提供独立删除入口，只随模板逻辑停用。
- `execution_mode` 只允许 `runninghub_workflow|runninghub_ai_app`。
- Workflow 模式要求 `workflow_id IS NOT NULL AND webapp_id IS NULL`。
- AI App 模式要求 `webapp_id IS NOT NULL AND workflow_id IS NULL`。
- 不存在 Base URL、Basic Auth、本地 Workflow JSON、自建 ComfyUI 地址或 TLS 跳过字段。
- `last_test_status` 只允许 `never|running|success|failed`。
- `last_test_summary` 只存脱敏摘要，不存 API Key、访问密码或完整供应商响应。
- 成功测试记录三张当前行的 `row_revision`；启用时必须与当前模板、执行配置和账号修订完全一致。

### 11.3 `av_runninghub_account`

主要字段：

```text
id
tenant_id
name
api_key_ciphertext
api_key_masked
enabled
last_health_status
last_health_time
last_health_summary
credential_updated_at
row_revision
create_by / create_time / update_by / update_time / del_flag
```

约束：

- RunningHub Host 固定在服务器端受控配置，不作为表字段由运营修改。
- API Key 只写不读，VO 仅返回掩码和是否已配置。
- 密钥更新直接覆盖当前密文，不创建修订记录。
- 被启用模板引用的账号不能删除或停用，除非先停用相关模板。
- 项目若不能提供符合安全要求的加密存储，RunningHub 账号不得启用。
- `(tenant_id, name)` 在未删除记录中唯一；模板只能绑定同 tenant 的账号。
- 密钥更新递增 `row_revision`、更新 `credential_updated_at`、清空健康成功状态并使引用模板待测试。

### 11.4 `av_workflow_order`

主要字段：

```text
id
tenant_id
order_no
owner_id
workspace_id
template_id
task_id
schema_hash
input_payload_json
idempotency_key
request_hash
billing_mode
usage_operation_id
template_title_snapshot
template_cover_snapshot_json
input_display_snapshot_json
create_time / update_time
```

约束：

- 不保存 `template_version_id`、`execution_plan_id` 或执行配置 JSON。
- `input_payload_json` 是用户提交事实，不是执行配置快照。
- 展示快照只保存模板标题、封面和输入标签／展示值，不包含执行配置。
- `usage_operation_id` 首期必须为空。
- `(tenant_id, owner_id, workspace_id, idempotency_key)` 唯一。
- 任务运行状态以关联 `ai_task` 为唯一事实源。
- 所有用户查询固定带 `tenant_id + owner_id + workspace_id`，不得依赖前端传入归属字段。

### 11.5 `av_workflow_task_execution`

主要字段：

```text
id
tenant_id
order_id
task_id
submission_state
execution_mode
runninghub_account_id
external_task_id
submission_started_at
submitted_at
provider_deadline_at
last_polled_at
poll_count
provider_error_code
provider_error_summary
provider_duration_ms
provider_usage_json
cost_reconciliation_status
result_manifest_json
create_time / update_time
```

约束：

- `task_id` 唯一；`order_id` 对运营测试可空，对用户生成必填并唯一。
- 以资源类型和检查约束保证用户生成必须关联同 tenant 的 `order_id`，运营测试必须关联 `workflow_template` 资源且 `order_id IS NULL`，二者恰有一种资源归属。
- `submission_state` 只允许 `not_started|submitting|accepted|unknown|finished`。
- `execution_mode`、`runninghub_account_id` 和 `submission_started_at` 在外部请求前记录；`external_task_id` 在供应商接受后记录，用于查询和取消；不复制 Workflow／AI App／映射／密钥。
- `provider_error_summary` 必须脱敏和限长。
- `provider_usage_json` 只保存官方响应实际提供的受控消费字段，例如币种、消耗金额或消耗币数量；与用户免费额度事实完全隔离。官方未返回时置空，不调用废弃接口推测成本。
- `cost_reconciliation_status` 只允许 `not_reported|reported|unknown`；提交未知时标记 `unknown` 供运营对账。
- `result_manifest_json` 只保存结果类型、大小、哈希和平台资产 ID；完成资产登记后不长期保存外部临时 URL。统一任务结果 payload 仅保存有界的结果数量摘要，完整结果集合以 `av_workflow_order_asset` 为事实源，避免结果数量受到任务摘要 JSON 大小限制。

### 11.6 订单素材

输入和输出素材继续使用平台统一资产事实源，并以订单关联表区分 `input|output`、输入键、排序和默认预览标记。所有关联都必须保留 `tenantId + ownerId + workspaceId`，禁止只凭 `assetId` 访问。默认预览标记仅用于展示，不是唯一主结果业务约束。

## 12. RunningHub 集成契约

### 12.1 固定端点

服务器端只允许调用 RunningHub 官方 HTTPS Host，首期使用：

```text
POST /uc/openapi/accountStatus
POST /api/openapi/getJsonApiFormat
GET  /api/webapp/apiCallDemo
POST /openapi/v2/media/upload/binary
POST /task/openapi/create
POST /task/openapi/ai-app/run
POST /openapi/v2/query
POST /task/openapi/cancel
```

- V2 上传返回的 `fileName` 用于 `nodeInfoList.fieldValue`。
- Workflow 使用 `workflowId + nodeInfoList`，可选 `accessPassword`。
- AI App 使用 `webappId + nodeInfoList`，可选访问密码按官方当前契约适配。
- 查询统一使用 V2 Query。
- 取消接口只对官方明确支持的 ComfyUI Workflow 任务开放。
- 本次不使用 Webhook，不开放技术回调路由。
- 每个端点使用独立请求 DTO 和字段白名单；鉴权按当前官方文档使用 Bearer Header，并且只在对应官方模型仍要求时附带 Body API Key。
- 禁止发送本范围未授权的完整 `workflow`、`webhookUrl`、`retainSeconds`、`instanceType`、`usePersonalQueue` 或其他费用／调度参数。
- `accountStatus` 只用于无生成副作用的账号连接测试；工作流 JSON 与 AI App 调用示例只用于候选节点检查，不直接持久化或提交其中默认值。

### 12.2 输入映射

`input_mapping_json` 为每个用户输入键保存受控映射：

```text
inputKey
nodeId
fieldName
valueType
valueTransform
required
remoteValueType
```

规则：

- 运营人员只映射明确允许的字段，不能自动暴露全部可变节点。
- Workflow 候选必须来自当前 `workflowId` 的受控 JSON 检查；AI App 候选必须来自当前 `webappId` 的受控调用示例检查。保存时再次确认 `nodeId + fieldName + remoteValueType` 仍属于最近一次检查结果。
- 前端只看到 `inputKey` 和用户表单定义，不看到节点信息。
- 文件先完成平台归属校验，再由服务器二进制上传 RunningHub；禁止把平台私有 OSS 原始地址交给 RunningHub 拉取。
- 映射只允许白名单转换，例如字符串裁剪、数字范围、布尔转换、枚举索引和 RunningHub 文件名替换；禁止任意脚本表达式。
- 当前映射无法接受订单输入时，任务在外部提交前失败。

文件输入首期白名单固定如下，模板字段可以设置更小上限但不能扩大：

| 资产类型 | 扩展名 | 声明 MIME | 单文件上限 | 允许的远端加载字段 |
| --- | --- | --- | --- | --- |
| image | `.jpg,.jpeg,.png,.webp` | `image/jpeg,image/png,image/webp` | 30 MiB | 受检候选中的图像输入；官方 `LoadImage.image` 作为内置白名单 |
| audio | `.mp3,.wav,.flac` | `audio/mpeg,audio/wav,audio/x-wav,audio/flac` | 30 MiB | 受检候选中的音频输入；官方 `LoadAudio.audio` 作为内置白名单 |
| video | `.mp4,.avi,.mov,.mkv` | `video/mp4,video/x-msvideo,video/quicktime,video/x-matroska` | 30 MiB | 受检候选中的视频输入；官方 `LoadVideo.video` 作为内置白名单 |
| file | `.zip` | `application/zip` | 30 MiB | 受检候选中的图片 ZIP 输入；官方 `LoadImages.upload` 作为内置白名单 |

扩展名、声明 MIME 和文件魔数必须同时匹配。ZIP 只允许图片压缩包，最多 100 个条目、解压总量最多 300 MiB；拒绝加密、符号链接、绝对路径、`..` 路径、嵌套压缩包和非图片条目。当前每个文件控件最多一个资产，节点字段不能由用户提交。

### 12.3 结果接收与安全校验

RunningHub 可能为一次执行返回多个结果，结果类型可以不同，也可能缺少可识别的 `outputType`。平台必须接收供应商返回的全部有效结果，不设置允许类型列表、结果数量上限或唯一主结果业务规则。

规则：

- 不依赖供应商一定返回节点 ID、稳定输出类型或唯一主结果。
- 查询结果只要包含非空结果 URL，就进入逐项下载与登记流程；不得因结果数量、混合类型、未知类型或多个同类型结果而失败。
- 每个结果下载前仍执行 URL 协议、主机和地址安全校验；下载内容必须非空，单文件固定安全上限为 100 MiB。该上限是平台文件安全边界，不是运营可配置的输出策略。
- 能从文件内容识别类型时使用识别结果；无法识别时按通用二进制文件登记，不因类型未知而丢弃结果。
- 所有下载成功并通过安全校验的结果都登记为平台资产，保持 RunningHub 返回顺序。平台可将第一项标记为默认预览，但不要求供应商返回唯一主结果。
- 结果列表为空、结果 URL 不安全、下载失败、内容为空、超过单文件安全上限或平台资产保存失败时，任务不得成功。
- `output_policy_json` 仅保留为历史数据库兼容列，新保存配置固定写入 `{}`；迁移删除 `allowedOutputTypes`、`primaryOutputType`、`requireUniquePrimary`、`maxResultCount` 和 `maxBytesPerResult` 等历史限制键。
- 用户接口永不返回 RunningHub 临时 URL。

### 12.4 外部响应与日志

- RunningHub Client 将供应商状态映射为内部受控结果，不把原始响应对象穿透到 Service 或 VO。
- 日志只记录内部任务 ID、外部任务 ID 的受控摘要、耗时、HTTP 状态和稳定错误分类。
- 请求体日志必须关闭或脱敏，禁止记录 API Key、访问密码、用户完整提示词、节点值和素材内容。
- 原始供应商错误只可进入受限诊断摘要，普通用户看到稳定业务文案。
- API Key 与访问密码使用项目受控的认证加密保存，例如 AES-GCM；主密钥来自部署密钥／环境而不入业务库，随机 nonce 与认证标签随密文保存，只有 `ai-video-infra` Client 在调用瞬间解密。解密失败、认证失败或主密钥缺失时失败关闭，密文、明文和主密钥均不得进入日志、缓存、VO 或审计载荷。

## 13. 订单与统一任务流程

### 13.1 任务类型与资源类型

新增：

```text
AiTaskType.WORKFLOW_TEMPLATE_GENERATE = workflow_template_generate
AiTaskType.WORKFLOW_TEMPLATE_TEST = workflow_template_test
AiTaskResourceType.WORKFLOW_ORDER = workflow_order
AiTaskResourceType.WORKFLOW_TEMPLATE = workflow_template
```

运营测试任务只出现在运营任务／测试记录中，不进入普通用户任务列表。

### 13.2 状态与阶段

复用现有任务状态：

```text
pending | queued | running | success | failed | cancelled
```

工作流用户 wire 与公共任务契约统一使用：

```text
waiting_for_dispatch
preparing_inputs
submitting_to_provider
confirming_provider_acceptance
provider_processing
processing_results
completed
failed
cancelled
```

合法矩阵固定为：`pending|queued -> waiting_for_dispatch`；`running -> preparing_inputs|submitting_to_provider|confirming_provider_acceptance|provider_processing|processing_results`；`success -> completed`；`failed -> failed`；`cancelled -> cancelled`。内部执行器若复用更细阶段，必须在统一 adapter 映射到上述公共枚举；禁止把两套值同时暴露或在订单表另建语义重复的状态枚举。

### 13.3 执行流程

1. Worker 领取任务并取得租约。
2. 重新读取模板、当前执行配置、RunningHub 账号及三者 `row_revision`。
3. 模板为 `pending_test|disabled`、配置停用或账号停用时，外部提交前失败。
4. 重新校验 `schemaHash`、用户输入、tenant、素材归属和素材状态。
5. 将文件输入逐个上传 RunningHub，并基于本次读取的当前映射构建 `nodeInfoList`。
6. 外部 POST 前再次读取三张行的 `row_revision`；任一变化都以 `WORKFLOW_CONFIG_CHANGED` 失败，不混用旧内存配置与新数据库配置。
7. 以 CAS 将执行事实从 `not_started` 改为 `submitting`，同时持久化账号 ID、模式、`submission_started_at` 和查询截止时间；只有 CAS 成功者可以执行一次创建请求。
8. 成功取得 `taskId` 后保存外部任务号和提交时间，进入有界轮询。
9. RunningHub 成功后按返回顺序下载全部结果；逐项完成 URL、非空和单文件固定安全上限校验后登记平台资产，不按类型、数量或唯一主结果筛选。
10. 资产登记事务成功后，统一任务置为 `success`。
11. 任何终态更新使用条件更新或 CAS，终态不可回退。

### 13.4 提交结果未知

- 在创建请求发出前发生的本地失败可以按统一任务租约规则有限重试。
- 创建请求已发出但超时、断连或响应无法解析时，供应商可能已经受理。
- 此时写入 `submission_state=unknown`，任务以 `WORKFLOW_SUBMISSION_UNKNOWN` 失败收口。
- 不自动再次 POST，不自动切换账号或模式，也不进入无限等待。
- Worker 在 `submitting` CAS 后、实际发请求前崩溃也按未知收口；这可能保守地放弃一次未发出的请求，但能保证恢复任务绝不重复扣费。
- 租约恢复发现过期 `submitting` 且没有外部任务号时，直接改为 `unknown` 并失败；统一任务自动重试器不得重新进入创建 POST。
- 运营端只提供查询和核查信息；本文不授权人工重放同一订单。
- 用户若确认重新生成，必须创建新订单和新幂等键。

### 13.5 轮询

- 已取得外部 `taskId` 后按指数退避加抖动轮询，间隔和总时限由服务器受控配置限定。
- 查询临时失败可有限重试；超过总时限以稳定超时错误失败。
- 任务租约、轮询次数和最后轮询时间必须持久化，进程重启后可恢复。
- 外部终态转换必须幂等，重复查询不能重复登记资产或重复发送通知。
- 轮询使用提交时保存的 `runninghub_account_id + execution_mode + external_task_id`，密钥从账号当前记录读取；不重新解析模板当前执行模式。
- 官方响应实际提供消费或耗时字段时，按字段白名单写入供应商消费事实；未提供时保持 `not_reported`，不得调用废弃接口补猜数据。用户免费策略与平台侧 RunningHub 成本分别核算。

### 13.6 取消

- `queued` 且尚未提交 RunningHub：本地取消并终止后续领取。
- Workflow 已取得外部任务号：调用 RunningHub Cancel；成功确认后才更新内部取消终态。
- AI App 已提交：`canCancel=false`，不调用未被官方文档保证的取消能力。
- 取消与成功结果竞态时，以条件更新保护先确认的真实终态；取消冲突返回 `46509` 并刷新详情。

### 13.7 无快照下的配置变更语义

- 执行配置保存后模板进入 `pending_test`，新的用户订单被拒绝。
- 尚未外部提交的排队任务领取时若模板仍为 `pending_test`，直接失败。
- 测试通过并重新启用后，仍未领取的旧排队任务读取新配置；Schema 不兼容则失败，兼容则按新配置执行。
- 已取得外部任务号的任务继续根据保存的账号 ID、模式和外部任务号查询，不重新读取 Workflow 或 AI App ID。
- 结果处理不读取 `output_policy_json` 做类型、数量或唯一主结果校验；所有通过 URL、非空、单文件固定安全上限和资产保存校验的结果均被接收。
- 账号密钥更新后查询使用新密钥；新密钥无法访问旧任务时按供应商查询失败收口，不回退旧密钥。

运营测试任务开始时记录当前模板、执行配置和账号的三个 `row_revision`。测试成功只在三个修订仍完全一致时，以 CAS 写入 `last_test_status=success` 和对应修订；任一修订变化都丢弃旧测试成功资格并保持 `pending_test`。启用接口再次比较成功测试修订与当前修订，禁止测试 A 误启用随后保存的配置 B。

以上行为是“不做版本与配置快照”的已确认代价，实施不得私自引入隐藏版本或历史密钥修订改变语义。

## 14. 文件与结果安全

- 用户上传先进入平台统一上传会话，服务端校验扩展名、声明 MIME、文件魔数、大小和媒体元数据。
- 只有 `ready` 且属于当前 `tenantId + ownerId + workspaceId` 的资产可进入订单。
- RunningHub 上传由服务器读取已授权平台资产并发送二进制；不接受用户提供远程 URL。
- RunningHub 结果 URL 视为不可信输入：只允许 HTTPS，禁止访问私网、回环、链路本地、元数据地址和解析后落入禁止网段的目标。
- 结果 Host 必须命中服务器端只读允许列表；普通运营人员不能修改该列表。首次连接和每次重定向都重新校验 Host、DNS 解析结果和实际连接地址，并将连接绑定到已校验公网地址，防止 DNS 重绑定。
- 每次重定向都重新校验目标；限制重定向次数、连接时间、总下载时间、`Content-Length`、流式实际字节数和并发数。
- 下载后重新验证 MIME、魔数和输出类型，再转存平台 OSS。
- 用户下载和预览始终通过平台授权接口，不暴露 OSS 永久地址或 RunningHub 临时地址。

### 14.1 高权限审计

以下运营动作必须写入项目现有操作日志／安全审计事实：

```text
workflow_template_create
workflow_template_update
workflow_execution_config_inspect
workflow_execution_config_update
workflow_template_test
workflow_template_enable
workflow_template_disable
workflow_template_delete
runninghub_account_create
runninghub_account_key_update
runninghub_account_test
runninghub_account_enable
runninghub_account_disable
runninghub_account_delete
workflow_order_asset_access
```

每条审计至少记录 `tenantId`、操作者、权限标识、目标类型与 ID、请求 ID、时间、结果、失败分类、变更字段名以及受控的前后配置摘要哈希。禁止记录 API Key、访问密码、密文、用户节点值、完整提示词、完整输入映射或外部临时 URL。账号密钥覆盖只记录“密钥已变化”和新掩码尾号，不记录前后明文或可逆值。

## 15. 页面状态与交互

### 15.1 用户端

| 页面 | 必须覆盖的状态 |
| --- | --- |
| 发现首页 | 加载、空、搜索无结果、分页、失败、权限不足 |
| 模板详情 | 加载、不可用、失败、权限不足、正常 |
| 制作页 | 配置加载、上传中、上传失败、未知控件、Schema 过期、提交中、成功、失败 |
| 订单详情 | 排队、运行、成功、失败、取消中、已取消、取消冲突、权限不足 |
| 任务中心 | 加载、空、筛选、分页、未知合法任务类型、失败、权限不足 |

### 15.2 运营端

| 页面 | 必须覆盖的状态 |
| --- | --- |
| 模板列表 | 加载、空、筛选无结果、分页、失败、权限不足 |
| 模板编辑 | 新建、编辑、保存中、校验失败、保存失败、待测试、启用、停用 |
| 测试运行 | 未测试、排队、运行、成功、失败、超时、结果校验失败 |
| RunningHub 账号 | 未配置、已配置掩码、连接测试中、成功、失败、停用冲突 |
| 订单运维 | 加载、空、筛选、分页、详情、资产无权访问、脱敏错误 |

用户端使用 Ant Design 基础组件组合；运营端优先使用 `ProTable`、`ProForm`、`ProDescriptions`。实现前必须按项目规则读取 Ant Design Skill，并以官方文档核对组件 API。

## 16. 后端实现边界

必须遵循 RuoYi-Vue-Plus 6.x：

- `domain` 放贫血 Entity。
- 共享稳定输入输出放 `ai-video-core` 对应业务包平级 `dto`，使用 `*DTO` 命名。
- `mapper` 使用 `BaseMapperPlus` 等框架公共能力。
- `service` 放 `I...Service`，`service.impl` 放编排实现。
- 用户／运营 HTTP 模块使用 `domain.bo`、`domain.vo` 和 `controller`。
- 供应商原始请求／响应只位于 `ai-video-infra` 直接集成边界。
- 使用 `R`、`PageQuery`、`PageResult`、`MapstructUtils`、权限、日志、OSS、缓存和事务等已有能力。
- Controller 只做协议适配、权限、校验和 Service 调用，不编排 RunningHub 流程。
- 不新增 `application`、`port`、`adapter`、`command`、`model` 平行业务层。

实现后端前必须先读取本地 RuoYi Plus AI Coding Skill、generator 模板和仓库相似模块。

## 17. 前端实现边界

### 17.1 用户端

- 删除 `WorkflowProviderKind` 与所有用户可见供应商字段。
- 删除 `getExecutionPlans` 和方案级 `getFormSchema`。
- 新增 `getCreationConfig(templateId)`。
- `CreateWorkflowOrderInput` 只包含 `templateId + schemaHash + inputs`。
- API 路径集中在 service adapter，页面不得散落字符串。
- Mock 必须与新契约一致，不能继续返回自建 ComfyUI 或 AI App 方案卡片。
- 动态字段组件以白名单注册；未知字段失败关闭。

### 17.2 运营端

- 新增发现配置、模板、RunningHub 账号和订单 API adapter／类型。
- 模板编辑以一个表单维护一个执行配置，不提供执行方案子表列表。
- Workflow 与 AI App 字段按模式互斥显示并在前后端双重校验。
- API Key／访问密码输入框不回填密文，保存语义区分“不修改”和“显式清除”。
- 路由权限、按钮权限和后端权限同时生效，隐藏按钮不能替代接口授权。

## 18. Mock、联调与交付顺序

### 18.1 可先行工作

公共契约和 TypeScript 类型冻结后，以下内容可以使用严格契约 Mock 并行：

- 用户发现卡片和单表单制作交互。
- 运营模板基本信息、表单和单执行配置表单。
- 页面加载、空、失败、权限不足和稳定错误码交互。

Mock 不得模拟为已经完成的真实上传、任务恢复或供应商调用。

### 18.2 必须等待真实后端

- 素材上传、归属和 Ready 校验。
- 订单幂等、任务创建和取消。
- RunningHub 账号测试、任务提交、轮询和结果登记。
- 运营启停、权限、审计和订单资产访问。

### 18.3 实施顺序

1. 同步公共契约和错误码。
2. 创建数据库迁移、权限字典和核心 Entity／Mapper／Service。
3. 实现统一任务扩展、RunningHub Client 和 Mock Server 契约测试。
4. 实现用户后端与运营后端。
5. 实现用户前端与运营前端。
6. 完成 Workflow 与 AI App 联调、文件安全验证和端到端验收。
7. 运行完整构建、测试和开发规范校验，再进行独立审查。

## 19. 测试与验证

### 19.1 数据与后端测试

- `(tenant_id, template_id)` 唯一索引拒绝第二条执行配置。
- 模式与 `workflowId/webappId` 互斥约束。
- 模板修改后进入 `pending_test`，未测试不能启用。
- 测试 A 运行期间保存配置 B 后，A 的迟到成功不能启用 B；保存和启停修订冲突不得静默覆盖。
- 同 tenant、owner、workspace、幂等键同摘要返回原订单；异摘要冲突，跨 tenant 不碰撞。
- 无凭据、错误 App 客户端、错误角色和缺权限均不进入业务 Service。
- 跨 tenant、跨用户、跨工作空间、非 Ready、类型伪造素材全部拒绝。
- 订单和任务创建事务任一步失败不留下孤儿数据。
- 终态条件更新防止取消、查询和完成竞态回退。
- 用户生成要求 `order_id`，运营测试要求 `order_id IS NULL`，`task_id` 唯一和资源归属约束均有数据测试。
- 密钥、配置、测试、启停、删除和订单资产访问产生脱敏审计记录，任何审计载荷不含秘密或节点值。

### 19.2 RunningHub 契约测试

- V2 文件上传成功、超时、5xx、非法 JSON、超大响应和错误文件名。
- 图片、音频、视频和图片 ZIP 的扩展名／MIME／魔数／大小白名单，以及 ZIP 炸弹、路径穿越、加密和嵌套压缩包拒绝。
- `accountStatus` 连接测试不创建生成任务；Workflow／AI App 远端结构检查只返回受控候选。
- Workflow 创建请求字段、AI App 创建请求字段和模式隔离。
- 每个端点 Bearer／Body API Key 形状与字段白名单；`workflow/webhookUrl/retainSeconds/instanceType/usePersonalQueue` 不得发送。
- 提交前失败可有限重试；CAS 后崩溃、提交响应未知和过期 `submitting` 均不得再次 POST。
- Query 的排队、运行、成功、失败、超时和重复终态。
- Workflow Cancel 成功、任务不存在、取消冲突和迟到成功。
- AI App 提交后 `canCancel=false`。
- 结果缺失、主类型不唯一、数量越界、类型错误、非允许域名、私网 URL、DNS 重绑定、恶意重定向、MIME／魔数不符和下载超限。
- RunningHub 成功但平台资产登记失败时任务不得成功。
- 官方返回供应商消费事实时按白名单保存，未返回时保持 `not_reported`；提交未知标记成本待核查。

### 19.3 用户前端测试

- 平台静态控件、系统文案和 DOM 属性中不存在 `ComfyUI`、`RunningHub`、`provider`、服务商选择和方案数量；运营可编辑的模板正文按普通内容处理，不据此推断执行模式。
- 首页卡片、模板详情、制作配置、订单创建和订单详情 adapter 运行严格 wire 测试；响应出现 `providerKind/providerDisplayName/executionMode/executionPlanId/templateVersionId` 等旧字段时必须拒绝，不能进入缓存或日志。
- 制作页直接加载唯一表单。
- 未知控件阻断提交。
- `workflow-form-1` 值形状、文件数组、Schema Hash 格式和 Schema 过期兼容比较均有测试。
- 上传失败、输入错误、幂等冲突、供应商失败和结果失败均有稳定交互。
- 订单详情不显示执行模式、外部任务号或原始错误。
- 登出和工作空间切换清理私有缓存。

### 19.4 运营前端测试

- 不存在自建 ComfyUI 选项。
- Workflow／AI App 字段互斥。
- 密钥永不回显，掩码不能被当作新密钥提交。
- 表单、配置或账号密钥修改后待测试；只有与当前三张行修订一致的成功测试才能启用。
- 远端结构检查只能选择候选节点，不能自由注入节点或完整 Workflow JSON。
- 测试运行要求显式费用确认和幂等键。
- 无权限时页面和按钮不可用，直接接口调用仍被后端拒绝。
- 订单详情对非授权资产和敏感错误保持脱敏。

### 19.5 验证命令

实现计划必须根据实际改动冻结精确命令，至少覆盖：

- 受影响后端模块单元测试与构建。
- 用户端、运营端 TypeScript 检查和测试。
- RunningHub Mock Server 契约测试。
- 数据库迁移校验。
- `scripts/validate-development-standards.ps1`。
- 有受控测试账号和明确费用授权时，分别执行一次 Workflow 与 AI App 真实烟雾测试；没有授权时明确记录为未验证，不得偷偷产生费用。

## 20. 验收标准

1. 用户端页面、接口响应和 DOM 不出现服务商、执行模式或执行方案。
2. 用户无法通过旧 `executionPlanId` 或构造请求选择隐藏配置。
3. 运营端每模板只能保存一个执行配置。
4. 运营端不能新建 `self_hosted_comfyui` 配置。
5. ComfyUI 工作流只调用 RunningHub Workflow API。
6. 运营端仍可绑定 RunningHub AI App，用户无感。
7. 不存在模板版本、配置版本、回滚或订单执行配置快照。
8. `row_revision` 只用于 CAS，不形成历史版本；旧测试不能启用新配置，外部提交前配置变化不会混用。
9. RunningHub 失败时不自动选择、切换或回退。
10. 提交 CAS 后崩溃或是否受理未知时不自动重复提交。
11. 所有生成进入统一任务中心，状态、阶段和终态一致。
12. 未校验并登记为平台资产的结果不能使任务成功。
13. API Key、访问密码、节点映射、外部任务号和原始错误不泄露给用户。
14. tenant、文件归属、工作空间、权限、幂等、取消竞态和终态保护通过反向测试。
15. 免费任务不产生额度冻结、扣减、结算或账本流水。
16. 用户端、运营端、后端、数据库和公共文档在同一交付中完成，不以 Mock 冒充闭环。

## 21. 公共文档影响

进入运行时代码实现前必须同步；`writing-plans` 生成的实现计划必须把这些公共契约更新列为第一项阻塞任务：

- `docs/API_CONTRACT.md`：替换执行方案接口、用户 VO、订单入参、错误码和运营接口。
- `docs/DOMAIN_MODEL.md`：登记模板、唯一执行配置、RunningHub 账号、订单和外部执行事实。
- `docs/ASYNC_TASKS.md`：登记任务类型、资源类型、阶段、轮询、提交未知和取消规则。
- `docs/ARCHITECTURE.md`：登记用户／运营／核心／基础设施职责与 RunningHub 信任边界。
- `ai-video-pages.md`：删除用户主动选择服务商，改为直接制作。
- `docs/DOCUMENT_MAP.md`：确保本文和更新后的公共契约可被任务路由发现。

文档更新后运行 `scripts/validate-development-standards.ps1`。

## 22. 已接受的显式代价

项目负责人已明确选择简单的当前配置模型，并接受以下结果：

- 运营修改配置会影响尚未提交 RunningHub 的排队任务。
- 不支持复现历史订单当时使用的 Workflow、AI App、映射或密钥。
- 账号密钥覆盖后，旧外部任务可能无法继续查询。
- 已提交任务接收 RunningHub 返回的全部有效结果；历史 `output_policy_json` 变化不会使在途任务因类型、数量或唯一主结果规则失败。
- 再次制作只使用当前模板配置，不能重放历史配置。
- 无法通过版本回滚恢复旧配置，只能由运营再次手工修改当前记录并重新测试。

实施不得用隐藏版本、不可见快照或自动故障切换绕过这些已确认决策；若未来需要改变，必须先重新更新公共契约和规格。

## 23. 外部依据

- [RunningHub API 平台说明](https://www.runninghub.cn/runninghub-api-doc-cn/)
- [RunningHub 发起 ComfyUI 高级任务](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749013)
- [RunningHub 发起 AI App 任务](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749010)
- [RunningHub 获取账户信息](https://www.runninghub.cn/runninghub-api-doc-cn/api-425748943)
- [RunningHub 获取工作流 JSON](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749014)
- [RunningHub 获取 AI App API 调用示例](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749011)
- [RunningHub V2 二进制上传](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749007)
- [RunningHub V2 查询任务结果](https://www.runninghub.cn/runninghub-api-doc-cn/api-425767306)
- [RunningHub 取消 ComfyUI 任务](https://www.runninghub.cn/runninghub-api-doc-cn/api-425749015)
- [RunningHub 更新日志](https://www.runninghub.cn/runninghub-api-doc-cn/doc-8287335)

实现时必须重新核对 RunningHub 官方文档；第三方接口字段、鉴权方式和支持能力不得仅凭本规格或训练记忆实现。
