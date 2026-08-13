# P0-A RuoYi 分层整改与 DTO 归属设计

## 状态

- 日期：2026-08-01
- 范围：P0-A 身份与安全代码的分层整改，以及全项目 AI 视频业务 DTO 归属规则
- 用户决策：DTO 位于 `ai-video-core` 内，与 `domain`、`mapper`、`service` 平级
- 会话缓存决策：负责人已批准受控一次性使迁移前 App 会话失效，不实现旧 FQCN 兼容读取
- 当前阶段：设计、实施计划、分层整改与 P0-A 自动化最终门禁均已通过；待实际发布维护窗口执行一次性旧 App 会话失效并验证 sys 会话不受影响
- 风险等级：RED。原因是后续整改会触及身份、安全和跨模块公共契约

## 目标

消除现有规范中的两个冲突：

1. “AI 视频稳定跨模块 DTO 必须进入全局 `ruoyi-api`”与四模块边界冲突；
2. “业务只能存在固定目录”没有包含 `dto`，导致 DTO 无法合法落入 `ai-video-core`。

整改后的规则必须同时满足：

- 延续 RuoYi-Vue-Plus 的贫血 Entity 加 Service 编排；
- 不引入 DDD、Clean Architecture、Hexagonal Architecture 或新的契约模块；
- `domain` 继续承载 Entity，不把 DTO 塞入 `domain`；
- `dto` 作为数据契约包存在，不成为新的业务编排层；
- 用户端和运营端继续拥有各自的 BO、VO、Controller 与权限入口。

## 非目标

- 本次不移动 Java 文件、不调整 Spring Bean、不修改数据库和接口；
- 不改变认证、授权、归属、审计、会话修订、事务或异常语义；
- 不恢复或开放当前延期的注册能力；
- 不进入 P0-B 及后续业务实现；
- 不把供应商协议对象统一改造成核心 DTO。

## 最终目录与职责

`ai-video-core` 中每个业务聚合采用以下结构：

```text
org.dromara.aivideo.<aggregate>/
├── domain/          # Entity 与紧密关联的闭合状态定义
├── dto/             # AI 视频内部稳定跨模块 Service 数据契约，类型名为 *DTO
├── mapper/          # 数据访问
├── service/         # I...Service
│   └── impl/        # ...ServiceImpl
├── event/           # 直接事件职责，按需存在
└── security/        # 直接 Sa-Token／安全技术职责，按需存在
```

端侧 HTTP 对象保留在各自模块：

```text
ai-video-user/.../<aggregate>/
├── controller/
└── domain/
    ├── bo/
    └── vo/

ai-video-platform/.../<aggregate>/
├── controller/
└── domain/
    ├── bo/
    └── vo/
```

`ai-video-infra` 的供应商原始请求／响应、SDK 模型和协议字段只位于直接 `client`／`provider` 集成边界。

## 对象边界

| 对象 | 归属 | 允许职责 | 禁止职责 |
| --- | --- | --- | --- |
| Entity | `ai-video-core/.../domain` | 表映射、持久化字段、简单内聚判断 | HTTP 展示、跨表事务、权限／额度编排 |
| DTO | `ai-video-core/.../dto` | 稳定的跨模块 Service 入参、出参或快照数据 | HTTP 校验／展示语义、持久化映射、业务编排 |
| BO | `ai-video-user` 或 `ai-video-platform` 的 `domain.bo` | HTTP 请求与 Jakarta Validation | 作为核心共享契约 |
| VO | `ai-video-user` 或 `ai-video-platform` 的 `domain.vo` | HTTP 响应、展示、翻译、脱敏 | 作为核心共享契约 |
| 供应商对象 | `ai-video-infra` 的直接集成包 | 外部协议、SDK、回调载荷 | 泄漏到核心 Service 契约 |

补充约束：

- `dto` 与 `domain`、`mapper`、`service` 平级，不使用 `domain.dto` 或 `service.dto`；
- AI 视频业务专属 DTO 不进入全局 `ruoyi-api`；
- DTO 使用 `*DTO` 命名；仅模块内部临时参数不必为了目录统一而强制建 DTO；
- `event`、`security` 等辅助包只能保留直接技术职责，不得承接原 `application` 的业务编排。

## 模块依赖

```text
ai-video-user      ─┐
                    ├──> ai-video-core
ai-video-platform  ─┘

ai-video-infra ───────> ai-video-core
```

- `ai-video-user` 与 `ai-video-platform` 通过核心 Service 和核心 DTO 复用业务能力，彼此不依赖；
- `ai-video-infra` 可实现核心声明的 Service 契约或消费核心 DTO；
- `ai-video-core` 不依赖端侧模块、具体基础设施实现或全局 `ruoyi-api` 中的 AI 视频业务 DTO；
- 不新增第五个 AI 视频契约模块。

## P0-A 后续迁移原则

现有 `application`、`application.impl`、`port`、`command`、`model` 不能机械改包名，实施计划必须逐类判定：

- 业务接口迁为 `I...Service`，实现迁为 `service.impl/...ServiceImpl`；
- 稳定跨模块纯数据对象迁入身份聚合平级 `dto`；
- Entity 与闭合状态保留在 `domain`；
- 业务编排并入 Service，事务边界与调用顺序保持不变；
- 直接 Sa-Token／安全对象才可留在 `security`；
- 外部渠道协议与实现迁入 `ai-video-infra` 的直接集成边界；
- 事件进入 `event`，不得借事件包形成第二套应用层。

后续迁移必须保持 API 路径、请求／响应字段、错误码、数据库结构、权限标识、Token 类型、账号归属、会话修订、审计和注册延期状态不变。

## 会话缓存发布决策（2026-08-01 已批准）

`AppPrincipalSnapshotDTO`、`AppWorkspaceSessionSnapshotDTO` 与 `AppSessionServiceImpl$AppOnlineSession` 的包名变化会使迁移前 Redis 载荷包含旧 FQCN。负责人选择受控一次性失效全部旧 App 会话；本次不增加旧类别名、兼容反序列化器或双读逻辑，也不在应用启动时自动、重复清理。

发布操作必须遵守以下顺序：

1. 进入维护窗口，摘除创作端流量，并冻结运营端的 App 会话查询、踢出等读写操作。停止全部旧版 `ai-video-user-api`；`ruoyi-admin/ai-video-platform` 可以为保持 sys 能力而滚动升级，但在任何新版 App 节点写入新载荷前，必须确认这两类服务中能够读写 App 会话 Redis 载荷的旧实例均已清零。不得让新旧 FQCN 消费者混部读写。
2. 从旧版与新版目标环境分别确认并记录 Redis 地址、逻辑库、`redisson.keyPrefix`（记为 `R`）与 `sa-token.redis-key-prefix`（记为 `S`）的实际值。本次发布不得同时改变 Redis 逻辑库或这两个前缀；若不一致，停止发布并拆分为独立迁移。
3. 按 `KeyPrefixHandler` 与 `PlusSaTokenDao` 的实际规则计算物理键，禁止凭默认值或简单拼接猜测：`RP = R` 为空时取空字符串，否则取 `R + ":"`；Sa-Token 逻辑键 `L` 先得到 `T = S + L`，再得到 `K = RP` 为空或 `T` 已以 `RP` 开头时取 `T`，否则取 `RP + T`；普通 Redisson 逻辑键 `O` 则在 `RP` 为空或 `O` 已以 `RP` 开头时取 `O`，否则取 `RP + O`。不得自行去除配置中已有的冒号。
4. 在发布记录中冻结并由两人复核三个物理扫描模式：将 `L` 分别取 `Authorization:app:*`、`Authorization:login:*`，将 `O` 取 `aivideo:app:online:*`，按上一步公式计算。记录旧版、新版映射结果和不含 Token/载荷的物理 Key 示例；新版 `Authorization:login:*` 物理映射必须与旧版完全一致。
5. 先执行只读扫描，分别记录两类 App 目标和受保护的 `Authorization:login:*` 数量。若业务侧确认存在在线 App 会话但两个 App 模式均零命中，或实际 Key 示例与公式不符，必须停止，不得把零命中当作清理成功。记录环境、执行人、时间和数量，不记录 Token、会话载荷或其他敏感值。
6. 经当次发布复核后，只删除 `Authorization:app:*` 与 `aivideo:app:online:*` 两个已冻结物理模式命中的键，并再次只读扫描确认二者精确零残留。严禁 `FLUSHALL`、`FLUSHDB`、全库通配删除，也不得删除 `Authorization:login:*`、运营端在线会话或其他缓存；受保护模式的数量和一个既有 sys Token 必须在删除后复验。
7. 确认新版 `ruoyi-admin/ai-video-platform` 已无旧实例后，部署并启动全部新版 `ai-video-user-api`，再恢复 App 会话管理操作与创作端流量；验证旧 App Token 返回 401、新登录与会话列表正常、既有 sys Token 仍有效。
8. 在发布记录中保存删除前后数量、旧实例清零证据与验收结果。回滚代码不会恢复已删除会话，用户重新登录是本决策已接受的残余影响。

## 前端与接口影响

本次规范同步和后续纯分层整改均不改变前端页面、路由、字段、加载／空／失败／权限状态、API adapter 或 mock。若实施中发现必须改变任何公共 API、状态或错误码，必须停止整改，先更新公共契约并重新评审。

## 协作与 Token 治理

- RED 任务最多同时使用 2 个 Agent：1 个实施、1 个独立审查；
- 只向 Agent 提供当前整改所需文件、符号和验证命令，不重复加载整份 PRD 或全部历史计划；
- 先完成规格复核，再使用 `writing-plans` 生成逐文件实施计划；
- 实施阶段必须有独立审查，重点检查安全语义、事务边界、模块依赖和 DTO 泄漏；
- Token 节省不得降低测试、审查或安全门禁。

## 验收

本次设计同步完成条件：

- 活动规范、架构、领域模型、模板、本地 RuoYi skill 和当前实施计划口径一致；
- 不再存在“AI 视频稳定跨模块 DTO 必须进入全局 `ruoyi-api`”的活动规则；
- 所有固定业务目录清单明确允许与 `domain` 平级的 `dto`；
- `scripts/validate-development-standards.ps1` 通过；
- 修改前后的 skill 压力测试分别证明旧规则导向错误位置、新规则导向本规格位置；
- 设计评审阶段的变更仅包含文档与本地 skill，不包含业务代码。

后续代码整改完成条件将在实施计划中细化，至少包括包路径扫描、模块依赖检查、P0-A 相关测试、双启动装配验证和安全回归。

## 确认记录

- 项目负责人已确认：`ai-video-core` 对应业务聚合内，`dto` 与 `domain`、`mapper`、`service` 平级。
- 分层整改实施计划已生成并执行，最终门禁以 `docs/superpowers/plans/2026-08-01-p0a-ruoyi-layering-remediation.md` 为准。
- 项目负责人已确认旧 App 会话采用本规格所述的受控一次性失效方案。
