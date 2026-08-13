# 后端编码规范

## 使用说明

本文适用于 `ai-video-api`。项目事实基线是 Java 21、Spring Boot 4.1.0、RuoYi-Vue-Plus 6.0.0-BETA、MyBatis-Plus/MPJ、Sa-Token、MapStruct Plus、Redis/Redisson/Lock4j、SnailJob 以及 Spring AI/Snail AI。JPA、Spring Security、RocketMQ 不是当前主营业务技术基线，不能把它们的通用条款强加给项目。

规则等级：`【强制】` 违反可能造成安全、正确性或架构问题，必须遵守；`【推荐】` 原则上应遵守，偏离时说明原因和替代措施；`【参考】` 按场景选择。

冲突优先级为：安全与正确性 > 项目契约 > 相邻代码与公共模块 > RuoYi skill/生成器 > 通用行业规范。HTTP 以 [API_CONTRACT.md](API_CONTRACT.md) 为准，领域以 [DOMAIN_MODEL.md](DOMAIN_MODEL.md) 为准，异步任务以 [ASYNC_TASKS.md](ASYNC_TASKS.md) 为准，模块组织与流程以 [BACKEND_GUIDE.md](BACKEND_GUIDE.md) 为准。

“当前可执行检查”只描述仓库已具备的能力；ArchUnit、Checkstyle、SpotBugs、JaCoCo 等属于目标门禁，未配置前不得表述为当前已启用。

## 1. Java 基础、命名和代码格式

【强制】源文件使用 UTF-8、LF、4 空格缩进、文件末尾换行，禁止行尾空格；只格式化本任务相关文件。Java/JSON 字段使用 `lowerCamelCase`，类和接口使用 `UpperCamelCase`，数据库列使用 `snake_case`。

【强制】BO、VO、Mapper、`I...Service` 按项目既有命名；权限码使用 `${module}:${business}:${action}`。稳定状态值使用英文值，禁止用中文消息作为程序分支。

【推荐】公共类型、字段及 Controller/Service/Mapper 方法写简洁 JavaDoc，说明职责和关键参数；`void` 方法不写 `@return`。import 与注解顺序跟随相邻代码。

检查方式：查看 `.editorconfig`、相邻模块和 `git diff --check`；搜索权限码与状态分支，确认没有以中文展示文案作逻辑判断。

## 2. 面向对象、集合、日期和精度

【强制】后端业务 ID 使用 `Long`；跨 HTTP 边界需要兼容 JavaScript 大整数时按接口契约字符串化。金额、额度和精度值使用 `BigDecimal`，明确单位、精度与舍入方式，禁止 `float`/`double`。

【强制】HTTP 集合和分页行数据返回空集合，不返回 `null`。`List.of(...)` 仅用于后续不会修改的集合。新业务时间使用 `java.time`；既有 `BaseEntity` 审计字段不做无关迁移。

【推荐】稳定状态和业务常量集中定义，禁止魔法字符串散落在多处。

检查方式：检查金额字段类型、空页序列化结果和集合后续是否被修改；审查新增时间类型与现有审计字段的兼容性。

## 3. 异常、日志和敏感数据

【强制】业务失败使用项目异常模型和统一异常处理；Controller 不散落 `try/catch`。响应、异常和日志不得泄露堆栈、SQL、内部路径、内部 URL、供应商凭据、完整 Token、签名、临时下载 URL 或隐私内容。

【强制】`@Log` 默认可能记录请求与响应，全局排除项不会自动覆盖 Token、`clientSecret`、`secretKey`、`accessKey`。敏感接口必须使用 `excludeParamNames` 或 `isSaveRequestData=false` / `isSaveResponseData=false`，VO、审计记录和测试快照同样脱敏。

正例：

```java
@Log(title = "更新凭据", businessType = BusinessType.UPDATE,
    excludeParamNames = {"clientSecret"}, isSaveResponseData = false)
public R<Void> update(@Validated(EditGroup.class) @RequestBody ClientBo bo) {
    clientService.updateByBo(bo);
    return R.ok();
}
```

反例：

```java
log.info("callback token={}, secret={}", token, clientSecret);
return R.data(providerResponse);
```

说明：日志和返回值一旦进入审计、监控或客户端即难以收回；脱敏必须在数据离开业务边界前完成。

检查方式：搜索 `@Log`、`log.`、Token/secret 字段和异常映射；人工确认敏感接口的请求、响应和快照都不含原值。

## 4. Controller、Service、Mapper 分层

【强制】业务聚合只能按 RuoYi 标准业务包组织：`domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。无 HTTP 入口的共享核心模块省略 BO、VO 和 Controller；`dto` 只是数据契约包，不是新的业务层。

【强制】Service 接口命名为 `I...Service`，实现类命名为 `...ServiceImpl` 并置于 `service.impl`。Entity 保持贫血持久化对象风格，只承担字段映射、表映射和简单内聚判断；事务、状态流转、账号／工作区归属、幂等、额度和跨 Mapper 编排必须位于 Service。不得以 DDD（领域驱动设计）充血聚合、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替换此模式。

【强制】禁止在业务聚合新增或保留 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 等作为平行的业务分层。端侧 HTTP 请求语义使用 BO，HTTP 响应语义使用 VO；AI 视频业务专属的稳定跨模块 Service 契约使用 `ai-video-core` 对应聚合平级 `dto` 包中的 `*DTO`，禁止放入全局 `ruoyi-api`。`config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 仅限直接框架或外部集成职责，不能成为另一套业务编排层。

【强制】对上述目录、`BaseEntity` 或对象职责的安全／框架例外，必须先在 `docs/DOMAIN_MODEL.md` 和模块规格记录原因、影响范围、替代控制与回归条件，并取得项目负责人明确确认；未经确认不得自行创建例外。

【强制】Controller 只处理 HTTP 入参、Jakarta Validation、登录主体、权限、日志、防重和统一返回；Service 负责业务编排、状态、归属、事务、幂等和领域异常；Mapper 只负责数据访问。`@DataPermission` 是合法的 SQL 行级数据域机制。

【强制】普通 JSON 使用 `R<T>`，分页使用 `R<PageResult<Vo>>`；禁止暴露 Entity、裸 `Page/IPage` 和 `R<R<T>>`。SSE、文件流、上游强制格式回调与 `/api/snail/chat/**` 必须按端点声明例外。

【强制】字符串作为 data 时使用 `R.data(value)`，禁止 `R.ok(String)`。

正例：

```java
// 正例：字符串作为 data
return R.data(downloadUrl);
```

反例：

```java
// 反例：R.ok(String) 会把字符串放入 msg
return R.ok(downloadUrl);
```

说明：`R.ok(String)` 选择消息重载，客户端无法从 `data` 读取下载地址。

检查方式：审查 Controller 返回类型、Service 事务与 Mapper 查询条件；搜索业务包声明，确认未以 `application`、`port` 或同类目录替代标准层；搜索 `R.ok(` 的字符串实参，确认字符串数据未误传为消息。

## 5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus

【强制】Entity 默认继承 `BaseEntity` 复用审计字段，使用 `@TableName`、`@TableId`，按需使用 `@TableLogic`、`@Version`；Entity 不跨越 HTTP 边界。

【强制】BO 实现 `Serializable`，使用 `@AutoMapper(target = Entity.class, reverseConvertGenerate = false)`；请求校验使用 `AddGroup`、`EditGroup`、`QueryGroup` 与 Jakarta Validation，范围查询按既有约定使用 `params`。VO 实现 `Serializable`，使用 `@AutoMapper(target = Entity.class)`，展示、翻译、脱敏放在 VO。

【推荐】转换优先使用 `MapstructUtils.convert`。稳定跨模块契约放入 `ai-video-core` 对应业务聚合的平级 `dto` 包；两个启动应用不得复制核心 DTO 或业务模型。

检查方式：检查 Controller 签名未暴露 Entity，BO/VO 注解与校验分组齐全，跨模块调用没有以复制 DTO 代替公共契约。

## 6. MyBatis-Plus、MPJ、查询、分页和排序

【强制】Mapper 默认继承 `BaseMapperPlus<Entity, Vo>`。生成器风格查询优先 `QueryBuilder.lambda(...)`；联表查询沿用相邻模块 MPJ/`lambdaJoin` 写法。优先使用 `eqIfPresent`、`eqIfText`、`likeIfText`、`inIfNotEmpty`、`betweenParams` 等现有条件方法；wrapper 不清晰或模块已有 XML 时才新增 XML。

【强制】业务接口必须限制最大 `pageSize`。`PageQuery(Integer pageSize, Integer pageNum)` 参数顺序是“页大小、页码”，调用处必须醒目标注。`orderByColumn` 必须映射到业务字段白名单，字符过滤不能替代字段授权。owner/data scope 必须进入 SQL 条件或 `@DataPermission`，禁止查出后内存过滤。

【强制】HTTP 空页禁止无参 `PageResult.build()`，响应必须稳定为 `rows=[]`。

【强制】分页入口显式限制 `pageSize`，并把客户端排序字段映射为允许列；不能把原始 `orderByColumn` 直接拼入 SQL。

正例：

```java
Page<VideoTaskVo> page =
    videoTaskMapper.selectVoPage(pageQuery.build(), wrapper);
return PageResult.build(page.getRecords(), page.getTotal());
```

反例：

```java
PageResult.build(); // 可能使 rows 为 null
wrapper.orderByAsc(pageQuery.getOrderByColumn()); // 未经白名单授权
```

说明：前端分页协议固定为 `total` 与 `rows`；空集合稳定化避免调用方产生额外空值分支。页大小上限保护数据库，排序白名单防止未授权字段暴露和 SQL 风险。

检查方式：检查分页服务的 pageSize 上限、排序白名单、SQL 数据域和 `PageResult.build(records, total)` 调用；覆盖空页与非法排序字段测试。

## 7. 事务、并发、幂等和外部副作用

【强制】事务边界放在 Service；多表写使用 `@Transactional(rollbackFor = Exception.class)`。归属、状态和额度校验与写入保持同一一致性边界，禁止吞异常导致错误提交。

【强制】并发写使用条件更新、乐观锁或唯一约束，禁止无保护的先查后写。AI、OSS、通知和第三方调用不得放入无法回滚的长事务；使用事务后事件、补偿或可恢复状态。

【强制】以 `ownerId + idempotencyKey` 等稳定键去重；Job、回调和重投事件按至少一次执行设计。分布式并发复用 Redisson/Lock4j，明确锁键、等待时间、租期和释放策略。

正例：

```java
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Transactional(rollbackFor = Exception.class)
public Long createTask(CreateTaskBo bo, Long ownerId) {
    Long taskId = taskMapper.insertByBo(bo, ownerId);
    applicationEventPublisher.publishEvent(new TaskCreatedEvent(taskId));
    return taskId;
}

@Component
@RequiredArgsConstructor
class TaskCreatedListener {
    private final ProviderClient providerClient;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handle(TaskCreatedEvent event) {
        providerClient.generate(event.taskId());
    }
}
```

反例：

```java
@Transactional
public void createTask(CreateTaskBo bo) {
    providerClient.generate(bo); // 外部副作用随事务锁长期运行
    taskMapper.insertByBo(bo);
}
```

说明：数据库回滚无法撤销外部生成或通知。仅发布普通 Spring event 不会自动延后执行；消费者必须使用 `@TransactionalEventListener(phase = AFTER_COMMIT)`，使外部调用只在数据库事务成功提交后发生；失败再按任务状态进行重试或补偿。

检查方式：审查事务内的外部调用、状态更新条件、唯一索引/锁与重复回调回归测试。

正例：

```java
if (!taskService.markCallbackHandled(callbackId)) {
    return;
}
quotaService.consumeOnce(taskId, ownerId);
```

反例：

```java
quotaService.consume(ownerId); // 每次回调都扣减
notificationService.sendCompleted(taskId);
```

说明：回调、Job 与事件可能重投；先落幂等状态并让副作用以任务维度唯一化。

检查方式：连续提交相同回调或重跑 Job，验证额度、输出记录和通知只生效一次。

## 8. Sa-Token、权限码、数据归属和数据权限

【强制】管理接口使用 `@SaCheckPermission` 和统一权限码；权限码不能代替资源归属检查。`ownerId` 必须从登录上下文派生，查询、详情、修改、删除、下载、预览和任务结果都校验归属；管理端跨用户操作需独立权限与审计。

【强制】`@DataPermission`/`@DataColumn` 别名必须匹配 SQL/MPJ。`DataPermissionHelper.ignore`、`@SaIgnore`、`security.excludes` 必须最小范围、有理由、有测试。不得绕过 `SecurityConfig` 对 `clientid`、`clientAccessPath`、`clientIpWhitelist` 的 Token 绑定访问控制。

正例：

```java
@SaCheckPermission("video:task:query")
public R<VideoTaskVo> getInfo(@PathVariable Long taskId) {
    return R.data(taskService.queryOwnedById(taskId, LoginHelper.getUserId()));
}
```

反例：

```java
public R<VideoTaskVo> getInfo(@PathVariable Long taskId) {
    return R.data(taskService.queryById(taskId));
}
```

说明：有页面权限不代表可读取任意 ID；归属检查防止 IDOR。客户端路径或 IP 不在 Token 允许范围内时，必须由既有 `SecurityConfig` 拒绝，而不是新增排除规则放行。

检查方式：以普通用户和管理员分别测试他人资源；审查忽略注解、路径排除与客户端 Token 访问限制。

## 9. Redis、Spring Cache、Redisson 和 Lock4j

【强制】复用 Spring Cache、`CacheUtils`、`RedisUtils` 和现有 `CacheNames`。缓存键包含 owner 或业务隔离维度，禁止跨用户污染；数据库写入与缓存失效必须有一致性策略，事务回滚前不得发布错误缓存状态。

【强制】权限与归属不得被陈旧缓存绕过。空值、TTL、序列化和批量失效行为必须显式。分布式锁复用 Redisson/Lock4j，不自造 Redis 锁协议。

正例：

```java
@Transactional(rollbackFor = Exception.class)
public void updateProfile(ProfileBo bo, Long ownerId) {
    profileMapper.updateByBo(bo, ownerId);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override public void afterCommit() {
                CacheUtils.evict(CacheNames.USER_INFO, ownerId);
            }
        });
}
```

反例：

```java
profileMapper.updateByBo(bo, ownerId);
redisTemplate.delete("profile"); // 无隔离维度且未处理事务回滚
```

说明：失效必须与提交结果一致并精确到隔离维度。

检查方式：审查缓存 key、TTL、事务后失效和权限相关缓存；在回滚与多用户场景下验证读取结果。

## 10. SnailJob、事件和异步任务

【强制】长耗时 AI 生成持久化任务并立即返回 `taskId`。状态、进度、失败原因和输出以后端任务记录为准，并链接 [ASYNC_TASKS.md](ASYNC_TASKS.md)。

【强制】终态防重复、防回退；重试不得重复扣额度、创建输出或发送通知。事务事件在提交后处理，失败必须可观察、可重试或可补偿。SnailJob、轮询、事件和回调均按可能重复执行设计。

【参考】当前项目没有 RocketMQ 业务基线，不虚构消息拓扑；按已有 SnailJob 与事件能力选择实现。

检查方式：对重复回调、重投 Job、失败恢复、额度和终态回退写回归测试；检查任务记录与异步协议一致。

## 11. 文件、OSS、导入和导出

【强制】复用 RuoYi OSS、文件与 Excel 能力。上传检查大小、数量、扩展名、MIME、文件魔数、归属和恶意内容风险；文件名和路径安全化，不返回真实 bucket 或内部路径。

【强制】下载、预览和任务结果使用授权且有期限的访问凭证；大文件流式传输，禁止整文件读入内存。Excel 导入逐行校验并聚合错误，导出过滤敏感字段。

正例：

```java
public R<OssVo> upload(MultipartFile file) {
    filePolicy.validate(file); // 大小、类型、魔数、归属与风险检查
    return R.data(ossService.upload(file));
}
```

反例：

```java
return R.data(ossService.upload(file)); // 未验证类型、大小或归属
```

说明：上传入口必须先建立可信文件边界；下载仍需按归属授权并流式传输，不能把任意路径读入内存。

检查方式：测试超限、伪造 MIME、魔数不符、跨用户下载和大文件；审查导出字段与错误汇总。

## 12. Spring AI、Snail AI 和外部服务适配

【强制】外部 AI/OSS/通知适配放在集成边界，核心领域不依赖供应商 DTO。显式映射供应商任务 ID、错误码、超时、取消和重试语义；非幂等请求禁止盲目重试，回调验证来源并保证幂等。

【强制】凭据仅从配置读取，不进入日志或 VO。普通 Snail AI JSON API 使用 `R<T>`；仅 `/api/snail/chat/**` 使用 SDK `Result` 协议；SSE 使用专用流式协议。

正例：

```java
@PostMapping("/api/snail/tasks")
public R<AiTaskVo> create(@RequestBody AiTaskBo bo) {
    return R.data(taskService.create(bo));
}
```

反例：

```java
@PostMapping("/api/snail/tasks")
public Result create(@RequestBody AiTaskBo bo) { // 非 chat 端点误用 SDK 协议
    return providerClient.create(bo);
}
```

说明：SDK `Result` 的例外只属于 `/api/snail/chat/**`；普通项目接口仍需保持统一 `R<T>` 契约。

检查方式：审查供应商 DTO 是否穿透领域、重试条件、回调验签/幂等和协议例外是否只限指定端点。

## 13. 配置、密钥和双启动应用边界

【强制】配置使用 `@ConfigurationProperties`；共享开发环境的地址、账号、密码、Token 和密钥统一维护在 `ruoyi-admin` 与 `ai-video-user-api` 的 `application-dev.yml`。两个启动模块只承载各自接口、配置和路由装配。

【强制】新增 Controller 必须声明面向管理端、用户端或双端；两端可有不同 BO/VO、权限入口，但不能复制核心业务。两个启动应用的路由、安全排除和配置差异必须分别验证。

正例：

```text
ruoyi-admin：/system/video-task/**（管理端权限与审计）
ai-video-user-api：/api/video-task/**（当前用户归属校验）
共用：ai-video-core 聚合 dto 中的稳定 DTO 与同一 Service 业务编排
```

反例：

```text
两个启动应用各复制一份 VideoTaskService，并都暴露 /api/video-task/**
```

说明：路由与安全边界属于启动应用装配；共用业务应复用模块能力而非复制实现。

检查方式：审查模块依赖与 Controller 包归属；分别启动或 Smoke Test 两个应用，确认端点暴露和安全边界。

## 14. 单元、Web、数据访问和集成测试

【强制】单元测试覆盖业务规则、边界、状态、额度和幂等失败；Web 测试覆盖参数、401/403、权限、归属、`R<T>`、字符串返回与 `rows=[]`。Mapper 测试覆盖 owner/DataPermission、分页上限、排序白名单与联表别名。

【强制】开发、调试、自动化与集成测试一律直接连接开发机本机安装的 MySQL 8（关系型数据库）和 Redis 7（缓存数据库）的专用测试库/命名空间；禁止 Docker、Docker Compose、Testcontainers、WSL、虚拟机、Podman 及其他容器化或虚拟化运行环境。共享连接信息和凭据从 `application-dev.yml` 读取，环境变量可以覆盖，日志不得输出凭据；测试夹具必须仅接受 `localhost`、`127.0.0.1` 或 `::1`，MySQL 必须为专用库 `ai_video_test`，Redis 必须使用独立逻辑库和本次运行前缀，校验失败立即终止且不得回退到开发、预发或生产库；禁止 `FLUSHALL`。两个启动应用分别做路由暴露与安全边界 Smoke Test（冒烟测试）。重复回调、重投 Job、事务回滚、缓存失效、文件越权和外部失败必须有回归测试。本规则仅约束开发和测试环境，不改变生产部署选型。

【推荐】以 `unit`、`integration`、`slow` 等标签组织测试。ArchUnit、Checkstyle、SpotBugs、JaCoCo 是目标门禁，配置并在 CI 执行后才可宣称启用。

检查方式：运行受影响模块测试并记录命令；无法自动化的外部场景给出可复现手工验证步骤和剩余风险。
