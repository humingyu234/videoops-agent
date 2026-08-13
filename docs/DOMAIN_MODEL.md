# 领域模型规则

本文档定义领域建模规则，不维护完整业务对象、字段、表结构、字典全集或状态机清单。

具体模块的领域对象、字段、表结构、状态流转和字典值，应在 `brainstorming` / `writing-plans` 阶段随模块规格确认后再落地。

## 命名规则

- 数据库表名使用 `snake_case`，AI 视频业务表建议使用 `ai_` 前缀。
- 数据库列名使用 `snake_case`。
- Java、JSON、TypeScript 字段使用 `lowerCamelCase`。
- 主键列使用业务名 ID，例如 `<business>_id`；Java 实体字段使用 `Long`，前端类型按 RuoYi-Vue-Plus 大数序列化结果兼容 `string`。
- API、BO、VO、前端类型必须使用 `lowerCamelCase`。
- 状态、类型、错误语义等稳定值使用英文，不使用中文作为逻辑判断值。

## RuoYi-Vue-Plus 适配规则

- 后端领域模型优先贴合 RuoYi-Vue-Plus 6.x 生成器、MyBatis-Plus、Validation、MapStruct Plus、SpringDoc / Javadoc 的既有风格。
- 单表 CRUD、基础列表、导入导出等常规能力优先使用框架生成器和既有模板，再在业务层补充 AI 视频的业务规则。
- 框架能力能表达的内容不重复造轮子，例如分页对象、统一响应、字典、翻译、脱敏、逻辑删除、数据权限、OSS 文件引用。
- 模块规格只确认当前需求需要的领域对象和字段，不因为框架支持某能力就提前引入未确认的复杂模型。

## RuoYi 领域对象与分层硬约束

- 本项目的“领域模型”采用 RuoYi 的贫血 Entity（实体）加 Service（业务服务）编排，不采用 DDD（领域驱动设计）充血聚合、领域服务分层、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）。
- 业务对象只能归入 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。共享核心模块无 HTTP 入口时省略 BO、VO 和 Controller。Service 接口使用 `I...Service`，实现类使用 `...ServiceImpl`；`dto` 不构成业务编排层。
- Entity 负责持久化字段、表映射和简单内聚判断，不能承担事务、跨表编排、外部调用、额度、权限／归属或复杂状态流转；这些规则由 Service 实现。简单只读判断（例如状态是否可用）不视为充血模型。
- 禁止把 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 作为业务分层或标准层的替代。端侧请求入参使用 BO，响应使用 VO；AI 视频业务专属的稳定跨模块 Service 契约使用 `ai-video-core` 对应聚合平级 `dto` 包中的 `*DTO`，禁止迁入全局 `ruoyi-api`；外部供应商请求／响应仅可位于 `ai-video-infra` 的直接 `client`／`provider` 集成边界。
- `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 仅限直接技术职责，不得承载第二套业务 Service。任何例外必须在模块规格中说明原因、最小范围、替代控制和回归条件，并经项目负责人明确确认。

## Entity / BO / VO / DTO

- Entity 对应持久化结构，不直接暴露给前端。
- BO 承载请求入参、查询条件和校验规则。
- VO 承载前端展示响应。
- DTO 用于 AI 视频内部稳定跨模块 Service 数据交换，不替代 Entity、BO 或 VO；它是数据契约，不承担事务、状态流转、权限／归属校验或持久化职责。
- Entity 默认继承 RuoYi-Vue-Plus `BaseEntity`，使用 `@TableName` 标记表名，使用 `@TableId` 标记主键；不适合继承时必须在模块规格中说明。
- 已批准的创作域安全例外：P2 问卷的创建／分支／修订／证据 Entity 与 P3 文案的主体、不可变版本、确认历史和
  任务输入 Entity 不继承 `BaseEntity`。这些表同时属于 `app_user` 创作归属域且包含 append-only（只追加）
  修订或不可变记录，默认 `create_by/update_by` 会错误表达为运营端 `sys_user`。Entity 必须显式声明
  `tenantId`、`ownerType`、`ownerId`、`createdByUserId` 以及实际需要的创建时间、更新时间和业务修订字段；
  Service 从已交叉验证的创作 actor/workspace 填充，仍保持贫血 Entity，不得借例外引入领域服务或第二套分层。
- BO / VO 默认实现 `Serializable`，优先使用 `@AutoMapper` 与 Entity 建立转换关系。
- BO 放请求校验注解、XSS 校验和业务入参，不承载前端展示翻译字段。
- VO 放前端展示字段，可使用 RuoYi-Vue-Plus 的翻译、脱敏和 JSON 序列化注解；敏感字段不得直接从 Entity 原样暴露。
- AI 视频内部稳定 DTO 放在 `ai-video-core` 对应业务聚合的平级 `dto` 包，使用 `*DTO` 命名；不得放入 `domain`、`domain.bo`、`domain.vo`、`service` 或全局 `ruoyi-api`。
- 供应商原始请求／响应、SDK 模型和协议字段只属于 `ai-video-infra` 的直接 `client`／`provider` 边界，不得泄漏为核心 DTO。
- BO / Entity / VO 转换优先复用 RuoYi-Vue-Plus 项目已有转换能力，不手写重复装配逻辑。

## 查询对象与分页

- 列表查询条件放在查询 BO 或模块约定的 BO 中，不放入 Entity。
- 分页、排序使用 RuoYi-Vue-Plus 的 `PageQuery` 规则，前端排序字段必须经过后端白名单或框架安全处理。
- 列表响应使用框架分页返回结构，不为 Ant Design Pro 单独设计另一套分页模型。
- 导出、批量操作、下拉选择等查询场景如果字段范围不同，应在模块规格中单独确认 BO / VO，不复用过宽对象。

## ID 与大数

- 后端主键默认使用 RuoYi-Vue-Plus / MyBatis-Plus 的雪花 ID 思路，Java 领域模型中使用 `Long`。
- 前端不得假设 ID 一定能安全放入 JavaScript `number`；TypeScript 类型优先按 `string` 或 `string | number` 与接口契约保持一致。
- 前端比较、缓存、路由参数和表格 `rowKey` 使用字符串化 ID，避免精度丢失。
- 金额、额度、耗时、文件大小等可能超过安全整数或需要精度的字段，必须在模块规格中确认单位、精度和前端类型。

## 基础审计字段

除字典、公共只读配置或明确说明的特殊表外，业务 Entity 默认继承 RuoYi-Vue-Plus `BaseEntity` 的审计字段：

- `create_dept`
- `create_by`
- `create_time`
- `update_by`
- `update_time`

AI 视频业务表如涉及用户私有数据，默认另行包含：

- `owner_id`：业务数据归属用户。

用户业务数据默认逻辑删除时，业务实体需要声明：

- `del_flag`

并按 RuoYi-Vue-Plus / MyBatis-Plus 规则使用 `@TableLogic`。如果某个表不包含上述字段，必须在模块规格或表设计中说明原因。

## 账号归属

- 用户业务数据默认按 `owner_id` 隔离。
- 前端不得传入并覆盖 `ownerId`。
- 后端从登录态和权限上下文派生数据范围。
- 查询、修改、删除、下载、预览、任务结果读取必须校验归属。
- 管理员跨用户数据查询必须有独立权限和审计记录。

### 双账号体系与审计主体

- 创作端用户以 `app_user` 为唯一登录主体，运营端用户以 `sys_user` 为唯一登录主体。相同用户名、手机号或数字编号不表示同一用户，不建立主键、外键、映射或同步关系。
- 创作端用户的手机号、邮箱在运营端查询响应中只能以脱敏值展示；运营端更新采用补丁语义：未提供联系方式字段即保留原值，只有显式清空标志才能删除，禁止以脱敏值、`null` 或空字符串回写明文联系方式。
- 创作端认证客户端、第三方身份、权限和角色分别使用独立 `app_auth_client`、`app_social_identity`、`app_permission`、`app_role` 与 `app_role_permission`；不得读取或复用对应 `sys_*` 事实源。
- 创作端个人/组织工作区、组织成员和资源授权使用 `app_organization`、`app_org_member` 与业务对象授权表。运营端部门、角色和数据权限不能扩大创作端业务查询范围。
- 创作端用户停用、改密、第三方解绑、权限变化、客户端停用或换密通过修订号只撤销受影响的 `app` 会话；不得影响相同编号的运营端会话。
- 审计记录必须同时保存 `actor_type` 与 `actor_id`。创作请求使用 `actor_type = app_user`，运营人员管理创作端资源使用 `actor_type = sys_user`，禁止只保存一个可能串域的数字编号。
- 本模块完整身份表、工作区表和字段约束以 `docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md` 第 10.2、10.5、10.6 节为准。

### 创作端会话缓存兼容

- 会话载荷或在线索引类型改包导致 Redis 中旧 FQCN 不可读取时，必须在模块规格中明确选择兼容读取或受控失效，禁止静默改变缓存策略。
- P0-A 分层整改已由负责人批准采用受控一次性 App 会话失效：不保留旧类、不增加兼容反序列化或双读逻辑，不在应用启动时自动清理。
- 发布时必须冻结所有 App 会话读写，并在任何新版 App 载荷写入前清零 `ai-video-user-api` 和 `ruoyi-admin/ai-video-platform` 中能够反序列化 App 会话的旧实例，禁止新旧 FQCN 消费者混部。
- 必须记录旧版与新版的实际 Redis 逻辑库、`redisson.keyPrefix` 与 `sa-token.redis-key-prefix`，按 `KeyPrefixHandler` 和 `PlusSaTokenDao` 的精确公式冻结物理扫描模式；本次发布不得同时改变逻辑库或前缀，也不得把不符合预期的零命中当作清理成功。
- 只处理逻辑键空间 `Authorization:app:*` 和 `aivideo:app:online:*` 对应的物理键；删除前后都要复核 `Authorization:login:*` 的物理映射、数量和既有 sys Token。
- 严禁使用 `FLUSHALL`、`FLUSHDB` 或全库通配删除；不得影响 `Authorization:login:*`、运营端在线会话及其他缓存。删除后旧 App Token 失效并要求重新登录，sys Token 必须保持有效。
- 具体维护窗口、计数、删除、验收与记录步骤以 `docs/superpowers/specs/2026-08-01-p0a-ruoyi-layering-remediation-design.md` 的“会话缓存发布决策”为准。

## 表设计规则

- 单表 CRUD 优先使用 RuoYi-Vue-Plus 代码生成器和既有模板。
- 表结构先满足模块业务边界，不为尚未确认的未来需求预留复杂结构。
- 用户业务数据默认逻辑删除。
- 逻辑删除字段由 Entity 显式声明并标注 `@TableLogic`，不要误认为它来自 `BaseEntity`。
- 任务、输出、额度流水等审计价值高的数据不应轻易物理删除。
- 删除业务对象前必须检查引用关系和权限。
- 表、字段和索引应随模块规格确认，不在本文档提前列全量清单。

### 数字人简化知识上下文 K0

- 最小持久化范围只登记 `av_knowledge_item`、`av_knowledge_version`、`av_knowledge_binding`、
  `av_video_type_rule` 四张表；知识由系统迁移预置，为全局只读数据。
- 内部请求字段及顺序精确为 `industryCode`、`purposeCode`、`targetDurationSeconds`、`tagCodes`；
  内部结果字段及顺序精确为 `knowledgeVersionIds`、`excerpts`、`copyRules`、`contentHash`。
- `*` 只表达服务端内部通配语义，客户端不得提交或构造该值。
- 摘要输入是 UTF-8 规范 JSON，三个键及顺序精确固定为 `knowledgeVersionIds`、`excerpts`、`copyRules`，
  `contentHash` 为其 SHA-256 小写十六进制摘要。空结果摘要固定为
  `62dffd7d09a50ad03b651edf697d9ab42a09c9607973ab89036bc2b6abb67e34`。
- K0 只提供内部只读 Service 契约，不提供 HTTP，不引入 owner／tenant，也不支持用户私有知识。

### K0 后续运营扩展边界

- 本节描述 K0 candidate 之后新增的运营端管理层与创作端兼容消费层，不改变 K0 的两个 DTO 和一个只读 Service 契约，也不把后续扩展计入 K0 PASS。
- `av_knowledge_item` 保存稳定条目标识与当前发布版本指针；正文编辑始终追加新的 `av_knowledge_version` 和对应绑定。已经发布或退役的历史版本正文不得原地修改或物理删除。
- 已发布版本切换为草稿或审核中、已退役版本重新进入草稿／审核／发布时，必须复制历史内容并追加新版本；已发布版本退役可以更新其状态并清空当前发布指针。同状态请求保持幂等。
- 发布新版本时必须在同一事务中退役旧的已发布版本和绑定，并把条目指针切换到新版本。同一条目只允许一个当前发布版本；知识上下文只消费状态为 `published` 且由指针和绑定共同确认的版本。
- 只有从未出现 `published` 或 `retired` 历史的纯草稿／审核条目可以物理删除；存在发布或退役历史时只能继续退役，不得级联删除历史版本与绑定。
- 运营端文件导入只接受明确白名单内的 UTF-8 文本知识格式，单次最多 20 个文件、单文件最多 10 MiB、总计最多 20 MiB；文件、名称、知识类型和状态必须逐项一一对应。空 MIME 或 `application/octet-stream` 只有在扩展名、UTF-8 与二进制内容检查全部通过时才兼容接受。
- 导入与手工创建的版本使用全局 `*/*` 绑定供 K0 查询消费；来源路径只作为服务端内部审计信息，不进入运营端列表或详情契约。
- 当前创作端兼容入口只返回诚实的 `knowledge-fallback`（知识规则兜底）模式，不代表真实 DeepSeek 或完整 P2。正式模型生成仍须具备 app 资源归属、草稿／分支、统一任务、幂等、attempt、额度与 usage 记录后才能接入 provider。

### 数字人纵链临时任务事实源

2026-08-03 项目负责人批准数字人声音与视频纵链在统一任务执行器接入前使用
`av_dh_generation_job` 作为最小持久化事实源。该表不是第二套长期通用任务模型，适用范围和替代控制固定如下：

- 只承载 `voice_generate` 和 `video_generate`，归属事实为 `tenant_id + owner_user_id`；Entity 继续继承
  `BaseEntity`，状态流转、归属、幂等和 Provider 编排全部位于 `DigitalHumanGenerationServiceImpl`。
- `(tenant_id, owner_user_id, job_type, idempotency_key)` 是并发幂等的数据库最终仲裁；应用层预查询只用于快速
  回读，不能替代唯一键。相同键配不同输入必须拒绝，只有唯一获胜行可以触发外部调用。
- 任务终态为 `succeeded | failed`，任何查询轮询、过期执行或 Provider 响应都不得把终态改回非终态。状态写入
  必须携带旧状态条件；输出媒体只允许唯一获胜更新关联。
- 输入和输出只保存配置根目录下的私有相对键、规范媒体类型、字节数和摘要，不保存可绕过授权的公开 URL；读取仍
  必须经过当前 `app_user`、租户和 owner 校验。
- 统一任务执行器可用后，必须以一次性前向迁移或吸收方式把仍需保留的记录并入唯一任务事实源，然后停止新写
  `av_dh_generation_job`；禁止长期双写、按查询结果拼接两套任务中心或复制计费流水。

本例外不改变其他生成任务必须使用统一任务、额度和通知体系的公共规则，也不授权草稿、共享、运营端或 P3 能力。

### 方向目录聚合版本与追溯子版本

core 内部唯一方向目录快照为 `direction/dto/DirectionCatalogSnapshotDTO`，八个 record component 的名称与顺序
精确固定为 `catalogVersion`、`contentHash`、`industryCatalogVersion`、`purposeCatalogVersion`、
`durationRuleVersion`、`industries`、`purposesByIndustry`、`targetDurations`。`catalogVersion`、
`industryCatalogVersion`、`purposeCatalogVersion` 必须为正数，`durationRuleVersion` 必须非空，
`contentHash` 必须是已发布快照的 64 位小写十六进制摘要；选项集合在 DTO 内保持不可变。

这些版本有两层不同职责：

- 聚合 `catalogVersion` 是 HTTP 唯一可见的乐观并发令牌。方向选项响应只映射它和三组选项，保存请求只接收
  `expectedCatalogVersion`；客户端不得提交 `contentHash` 或三个子版本。
- `industryCatalogVersion`、`purposeCatalogVersion`、`durationRuleVersion` 是服务端历史追溯事实。P2 保存方向时
  必须在同一事务中只读取一次当前 published snapshot，在该快照上完成聚合版本、行业／用途绑定、`custom`
  文本和目标时长校验，再把三个子版本写入同一条不可变方向修订。

`av_script_direction_revision.industry_catalog_version`、`purpose_catalog_version`、`duration_rule_version` 的唯一来源
是上述同一个 `DirectionCatalogSnapshotDTO`，不能来自 BO、前端缓存、第二次目录查询或当前标签反查。旧方向修订只追加、
不随目录重新发布而回填；读取历史时也不得用当前目录版本重建。该表自己的 `content_hash` 是方向修订规范内容摘要，
不得与目录快照的 `DirectionCatalogSnapshotDTO.contentHash` 混为同一持久化字段。

### 任务组成员表

`20260728_04a_p0c_task_group_guard.sql` 前向新增 `av_ai_task_group_member`，用于表达根任务属于哪个生成上下文
任务组，不替代 `av_ai_task`、用量操作、账本或操作槽：

- 唯一键精确为 `(tenant_id,task_group_key,root_task_id)`；`task_group_key` 只使用
  `script:{draftId}:{branchRevision}`。
- `origin_type` 只允许 `origin|inherited`；`created_by_type` 只允许 `app_user|sys_user`。origin 行的
  `source_task_group_key` 必须为空，inherited 行必须记录 source group。
- `root_task_id` 使用 `(tenant_id,root_task_id)` 同租户外键指向根任务，禁止跨租户成员；索引
  `idx_av_ai_task_active_group` 支持按 tenant、生成 group、根任务类型和活跃状态检查
  `pending|queued|running`。
- 分支继承只新增 membership，不复制 task、usage、ledger 或 operation slot。相同目标集合重放幂等；partial、
  superset、conflict 或 target 已含 origin 必须 fail-closed。

## 翻译、脱敏与文件引用

- 后端 VO 中需要展示名称、URL、标签等派生字段时，优先使用 RuoYi-Vue-Plus 的翻译能力，不在 Entity 中冗余存储展示值。
- 邮箱、手机号、密钥、外部服务响应、支付或额度敏感信息等字段，VO 层必须评估是否需要脱敏或按权限展示。
- 文件、图片、视频、音频等资源字段优先保存 OSS / 文件 ID 或稳定资源引用，展示 URL 通过框架翻译或专门接口派生。
- 外部 AI 服务原始响应不直接暴露给前端；需要展示的内容转成稳定 VO 字段，调试信息按权限控制。

## 索引规则

- 高频查询字段才建立索引。
- 归属隔离类业务表通常需要考虑 `owner_id` 相关索引。
- 列表页常用筛选、排序字段应在模块规格中确认后设计索引。
- 不为未确认的查询场景提前创建索引。
- 排序字段必须有后端白名单映射，禁止直接拼接前端字段。

## JSON 字段规则

- JSON 字段只用于结构化但变化频繁、且不适合立即拆表的内容。
- JSON 字段必须在模块规格中定义结构。
- 不允许随意追加未登记字段。
- 不允许用 JSON 字段绕过核心关系建模、权限校验或查询需求。
- 高频查询字段不应长期藏在 JSON 中。

### 问卷答案 identity 与 context 分离

`av_script_answer_revision` 的 `answer_identity_json` 与 `answer_context_json` 是同一不可变答案修订的两个不同
事实，不能互相覆盖或重算：

- `answerIdentityJson` 使用 UTF-8、NFC、无多余空格，键顺序精确为 `questionNo`、
  `questionVersionHash`、`selectedOptionCodes`、`customSelected`、`customText`。普通选项代码按 Unicode code
  point 排序去重且不包含 `custom`；未选自定义时 `customText` 为 JSON `null`。
- `answerHash = SHA-256(answerIdentityJson UTF-8 bytes)`。问题正文、目标槽、选项 label、规范值和槽贡献不
  参与 identity 或 hash；这些服务端语义只进入独立 `answerContextJson`。
- `answerContextJson` 键顺序精确为 `questionText`、`targetSlotCode`、`selectedOptions`、`customText`；
  `selectedOptions` 由服务端从不可变问题版本解析并按 code 排序。改变 context-only 文本不得改变
  `answerHash`。
- 客户端不得提交 owner、hash、identity 或 context 字段。服务端从当前已授权、已锁定的问题版本和答案输入
  生成双 JSON；两者随答案修订只追加保存，禁止以后来的字典 label 重建历史。

### 文案推荐理由派生

`av_script_version.angle_summary`／`ScriptVersionDTO.angleSummary` 是服务端派生事实。唯一 formatter version 为
`script-recommendation-1`：按冻结的 `KnowledgeRouteResultDTO.plans` A/B/C 顺序取得 1-based rank，并只使用
对应 `KnowledgePlanDTO` 的 `candidateCode`、`planCode`、`angleCode`、`primaryTemplateVersionId`、
`differentiatorTechniqueCode` 生成固定文案。同一 route/plan 必须逐字相同，优化版本使用同一冻结 route 与
formatter version 重建。

生成与优化 provider schema 均禁止 `angleSummary` 和任何推荐理由字段；provider 返回时必须作为 unknown
property 拒绝，不能输出、默认补值或覆盖服务端派生值。prompt 文本、provider 原始响应、当前字典 label 和
前端组件也不得成为推荐理由事实源。

## 字典与枚举规则

- 后端字典用于可配置展示项。
- 前端枚举用于类型安全、状态映射和交互控制。
- 前端逻辑判断依赖稳定英文值，不依赖中文展示文案。
- 可配置字典值、状态值和展示文案应在模块规格中确认。
- 不在本文档提前维护完整业务字典全集。

## 状态机规则

- 状态值必须是稳定英文值。
- 每个状态必须明确含义、可见操作和可进入的下一状态。
- 终态、可取消、可重试、可删除等规则必须在模块规格中确认。
- 状态流转由后端校验，前端隐藏按钮不能替代后端状态校验。
- 不在本文档提前维护完整状态流转图。

## 变更规则

以下变化必须同步更新相关模块规格、接口契约和前后端类型：

- 新增或删除领域对象。
- 新增、删除或重命名字段。
- 改变字段含义、必填性、默认值或枚举范围。
- 改变状态流转规则。
- 改变账号归属、权限或数据范围。
- 改变索引、删除策略或 JSON 字段结构。

## 发现页 RunningHub 单执行领域冻结（2026-08-11）

发现模板的用户端模型只以 `template_id` 标识公开模板。运营端对每个 `(tenant_id, template_id)` 保存唯一的**当前执行配置**；配置模式仅限 RunningHub Workflow 或 RunningHub AI App。该配置、其凭据和 RunningHub 外部标识只属于后端 Service 与 `ai-video-infra` 直接集成边界，不能成为用户 BO、VO、DTO、缓存、订单 JSON 或 DOM 数据。

不创建模板版本、执行配置版本、密钥版本或订单执行配置快照。每个尚未发出外部提交的领取／提交尝试都读取并使用当前配置；仅当前配置不可用、输入不兼容或提交前 revision 校验变化时以 `WORKFLOW_CONFIG_CHANGED` 失败。已保存 externalTaskId 的任务只能使用保存的 accountId、mode 与 externalTaskId 查询外部结果，并按当前 output policy 读取结果；不得因配置变化笼统失败，也不得把新配置套用于旧订单。禁止自动路由、自动选择、故障切换、回退和同订单人工重放。

用户订单请求对象只允许 `templateId`、`schemaHash`、`inputs`，幂等键由 HTTP Header 承载。`workflow-form-1` 输入对象与素材项禁止额外属性；素材一律以 `{assetId:string}[]` 传递。稳定机器夹具位于 `docs/contracts/discovery-runninghub/`，是本领域公共输入值形状的事实源。

第一阶段发现目录固定为平台全局 `tenant_id=0`，每个 `(tenant_id, template_id)` 最多一条未删除执行配置。运营端唯一配置仍可选择 `runninghub_workflow|runninghub_ai_app`；两种模式及账号、远端 ID、节点和任务标识均不得进入用户端模型。

模板发布采用人工启用：enable 只校验模板存在、唯一配置已启用且未删除、引用 RunningHub 账号已启用且未删除。`last_test_status` 和全部 `last_test_*_revision` 不参与发布、列表、详情、`creation-config` 或 enable 判定。修改模板或配置保持模板当前状态，不自动写入 `pending_test`；`pending_test` 仅作历史兼容。`row_revision` 只承担乐观并发控制，不表示测试修订、发布修订、配置版本或执行快照。

## 数字人供应商 HTTP 开发例外（2026-08-10）

开发环境登记的 ComfyUI 当前仅提供固定公网 IP 的明文 HTTP 入口。项目负责人已明确批准仅在
`application-dev.yml` 通过 `digital-human.comfy-ui.insecure-http-allowed-hosts` 精确允许
`36.133.55.206`；默认配置、其他环境和其他主机仍拒绝远程 HTTP。

该例外只存在于 `ai-video-infra` 的 ComfyUI 客户端边界，不接受用户提交的供应商地址，不支持通配符，
并继续执行固定工作流、Basic Auth、响应大小上限和媒体归属校验。远端提供 HTTPS 或受控加密代理后，
应删除开发白名单并恢复仅允许 HTTPS／回环 HTTP。

## 声音资源模型补充（2026-08-03）

`av_voice` 是当前 tenant/workspace/owner 私有声音资源，唯一绑定一条 `voice_sample` 类型 `av_asset`。
它以 `pending -> transcribing -> ready|failed` 持久化本地 Whisper 转写状态；`attempt_count`、
`next_attempt_at`、`lease_owner`、`lease_expires_at` 支持有界重试和崩溃恢复，`record_revision` 防止旧结果覆盖。
状态领取和成功／失败写回全部使用带状态、租约和修订号的条件更新。该实体继承 `BaseEntity`，不新增业务分层例外。

## 人物形象与私有图片

`av_portrait` 是创作端用户私有业务对象，`av_asset` 保存其唯一图片素材事实。形象不复制文件状态、对象 Key 或短期 URL，也不保存第二套可用状态。

- `av_portrait.asset_id` 唯一，一个素材最多绑定一个形象。
- `av_portrait.idempotency_key` 和 `request_digest` 仅用于创建幂等；历史记录允许为空，新创建记录必须同时写入。
- 唯一索引为 `(workspace_id, owner_id, idempotency_key)`，相同键但摘要不同属于 `46304` 冲突。
- 图片格式事实限定为 `jpeg|png|webp|gif`，文件大小不超过 10MB；所有格式均真实解码首帧，单边不超过 12000 像素且总像素不超过 25000000。
- 删除状态保存在素材：`delete_pending` 表示对象删除或数据库收尾尚未完成，`delete_failed` 表示对象删除失败。对象存储调用不进入数据库事务，重试必须安全。
- 上传后超过 24 小时仍未被任何 `av_portrait.asset_id` 引用的 `portrait_image` 素材可由内部清理编排分批处理。

## 个人文案与不可变版本

`av_user_script` 是个人文案主体，当前阶段固定 `owner_type=personal`、`owner_id=currentAppUserId`；`tenant_id` 只由服务端个人会话提供。`current_version_id` 表示库内当前可编辑版本，和仅供生成链路使用的 `current_confirmed_version_id` 分离。手工录入文案允许 `draft_id` 与确认版本为空。

`av_script_version` 保存不可变正文。首次录入写入 `manual_input` v1，后续编辑写入 `manual_edit` 并指向保存时的父版本；版本号单调递增，旧版本禁止修改和删除。主体标题或当前版本变化时 `script_revision` 加一，编辑使用父版本和主体修订号共同防止覆盖。

个人归属条件必须在 Mapper SQL 和条件更新中同时包含 tenant、personal owner、ownerId 与逻辑删除条件，禁止查询全量后在内存过滤。手工版本不创建 `av_script_confirmation`，也不能作为要求“已确认版本”的下游输入。

## 创作第 6 步领域契约（`timeline-1`）

本节只覆盖创作第 6 步新增对象，并覆盖本文件中通用 `owner_id`、租户或工作区归属规则：下列九张表统一使用 `owner_user_id` 作为当前 `app_user` 的业务归属，不增加租户列、工作区列或组织列。所有关系均由 Service 在同一归属范围内校验并以普通索引支撑；数据库禁止 `FOREIGN KEY`、级联更新和级联删除。数据库业务 ID 使用 `BIGINT`，跨 HTTP 边界使用十进制字符串。

### 九张核心表与不可变边界

| 表 | 核心事实与约束 |
| --- | --- |
| `av_creation_asset` | 创作素材；记录 `asset_type`、`usage_origin`、来源引用、`pending|ready|failed`、内部 `storage_key`、摘要和媒体探测事实。`del_flag` 是唯一删除事实，内部键不进入 JSON／VO。 |
| `av_creation_project` | 第 5 步来源到时间轴项目的聚合入口；记录基础视频、主配音、脚本文本快照、画布和 `editing|rendering|ready|archived`。`del_flag` 是唯一删除事实。 |
| `av_timeline_draft` | 每个项目恰好一个当前草稿；`revision` 从 1 单调递增，保存 `schema_version=timeline-1`、`content_json`、规范摘要和时长。草稿允许更新并逻辑删除。 |
| `av_timeline_version` | 不可变时间轴快照；项目内 `version_no` 单调递增，固定 `source_draft_revision`，`version_reason` 只允许 `manual_save|restored|render_input|conflict_copy`。 |
| `av_timeline_asset_ref` | 从草稿或版本 JSON 投影出的可查询素材引用；`document_type=draft|version`。草稿引用随保存事务整体重建，版本引用创建后不可修改。 |
| `av_timeline_write_receipt` | 不可变幂等写回执；`operation_type` 只允许 `draft_save|manual_version|version_restore|conflict_version`，保存请求摘要、预期／结果修订和结果版本。 |
| `av_ai_task` | 统一根任务；保存归属、类型、项目资源、冻结输入版本、幂等摘要、状态／阶段／进度、当前执行、免费策略、结果或安全错误。终态不逻辑删除。 |
| `av_ai_task_execution` | 根任务的一次受控执行；保存 `execution_no`、CAS 版本、调度时间、租约、取消快照、冻结输入、结果摘要和安全错误。 |
| `av_ai_task_attempt` | 执行中每次真实外部 AI 调用或媒体进程的不可变尝试；保存 `attempt_no`、租约令牌摘要、Worker、起止时间、退出码和安全摘要，终态后禁止修改。 |

`av_timeline_version`、版本类型 `av_timeline_asset_ref` 和 `av_timeline_write_receipt` 是 append-only（只追加）事实，不设置 `del_flag`。任务、执行和尝试依靠状态机与 CAS 保留审计事实，不设置 `del_flag`。`av_creation_asset`、`av_creation_project` 和 `av_timeline_draft` 的 Entity 显式声明 `@TableLogic del_flag`。旧 `av_dh_generation_job` 仍是数字人纵链的独立过渡事实源，不迁移、不双写，也不得伪装成 `av_ai_task`。

### 逻辑关系、唯一约束和索引

| 表 | 必须存在的唯一约束／查询索引（列顺序固定） |
| --- | --- |
| `av_creation_asset` | UNIQUE `(owner_user_id,idempotency_key)`；UNIQUE `(owner_user_id,usage_origin,source_ref_id)`（空来源不参与去重）；INDEX `(owner_user_id,del_flag,status,update_time)` |
| `av_creation_project` | UNIQUE `(owner_user_id,idempotency_key)`；INDEX `(owner_user_id,del_flag,update_time)`；INDEX `(owner_user_id,source_type,source_ref_id)`；分别为 `(owner_user_id,base_video_asset_id,del_flag)`、`(owner_user_id,primary_audio_asset_id,del_flag)`、`(owner_user_id,current_output_asset_id,del_flag)` 建反查索引 |
| `av_timeline_draft` | UNIQUE `(owner_user_id,project_id)` |
| `av_timeline_version` | UNIQUE `(owner_user_id,project_id,version_no)`；UNIQUE `(owner_user_id,project_id,idempotency_key)`；INDEX `(owner_user_id,project_id,create_time)` |
| `av_timeline_asset_ref` | UNIQUE `(owner_user_id,document_type,document_id,element_id,asset_id,usage_type)`；INDEX `(owner_user_id,document_type,document_id)`；INDEX `(owner_user_id,asset_id,project_id)` |
| `av_timeline_write_receipt` | UNIQUE `(owner_user_id,project_id,idempotency_key)` |
| `av_ai_task` | UNIQUE `(owner_user_id,idempotency_key)`；INDEX `(owner_user_id,status,create_time)`；INDEX `(owner_user_id,resource_type,resource_id,create_time)`；INDEX `(owner_user_id,result_asset_id)` |
| `av_ai_task_execution` | UNIQUE `(owner_user_id,task_id,execution_no)`；INDEX `(execution_status,next_run_at)`；INDEX `(execution_status,lease_expires_at)`；INDEX `(owner_user_id,result_asset_id)` |
| `av_ai_task_attempt` | UNIQUE `(owner_user_id,task_execution_id,attempt_no)` |

项目以逻辑关系引用基础视频、主配音和当前成品；草稿、版本、引用、回执和任务均以 `project_id` 逻辑关联项目；执行以 `task_id` 逻辑关联根任务；尝试以 `task_execution_id` 逻辑关联执行。每次读、写、条件更新和反查都必须同时包含 `owner_user_id`，禁止只按业务 ID 查询后在内存判断归属。

素材删除前必须反查：项目的三个素材列、当前草稿引用、所有不可变版本引用、活跃或历史任务输入／结果以及已登记成品。任一有效引用存在即拒绝删除；不得依靠物理外键或级联删除替代业务校验。

### 创作端审计主体

- 九张表对应 Entity 均实现标记接口 `AppAuditRequired` 并继承 `BaseEntity`；`owner_user_id` 表示数据属于谁，`actor_type + actor_id` 表示谁发起本次业务动作，`create_by/update_by` 只承担 RuoYi 审计，三者不得互相替代。
- `AuditFillContext<Long>` 必须可嵌套，进入时压入当前创作端主体，退出时在 `finally` 恢复或清空。创作端请求过滤器只从已认证的 `AppLoginHelper` 打开上下文，禁止从请求 BO 读取主体。
- `InjectionMetaObjectHandler` 遇到 `AppAuditRequired` 时只读取 `AuditFillContext<Long>`：创建写 `create_by/update_by`，更新写 `update_by`，`create_dept` 固定为空。上下文缺失、类型错误或主体无效必须 fail-closed，禁止回退 `LoginHelper`、系统用户或 `-1`。
- Worker 的每个短数据库事务使用根任务冻结的 `actor_type=app_user` 与 `actor_id` 打开审计上下文；技术执行者只写入 `lease_owner`、attempt Worker 等技术字段，不得冒充运营端用户。无法取得或核对冻结主体时停止写入并 fail-closed。
