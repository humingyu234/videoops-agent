# AI Video 前后端开发规范文档建设实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `subagent-driven-development`（推荐）或 `executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 将当前混合的编码纪律重构为独立、可执行、可验证的后端与前端编码手册，并按 RuoYi-Vue-Plus 6.X 真实协议修正文档契约和阅读入口。

**架构：** 两份 Coding Standards 分别成为后端和前端代码规则的唯一权威来源，Guide 只保留工程组织与开发流程，API Contract 只保留线上传输协议。根入口负责按任务路由文档，纯 PowerShell 验证脚本负责检查结构、关键红线、旧引用和本地 Markdown 链接。

**技术栈：** Markdown、PowerShell 5.1+、Java 21、Spring Boot 4.1.0、RuoYi-Vue-Plus 6.0.0-BETA、TypeScript 6/7、React 19、Umi Max 4、Ant Design 6、ProComponents 3、React Query 5，以及各前端包实际配置的 Biome/Vitest 或 Oxc 工具链。

---

## 规格来源与范围

规格来源：

- `docs/superpowers/specs/2026-07-27-ai-video-development-standards-design.md`

本计划只建设规范文档和文档验证脚本：

- 创建后端编码规范。
- 创建前端编码规范。
- 修正 RuoYi API 契约。
- 收窄前后端 Guide 的职责。
- 更新根入口、文档地图和 AI 阅读路径。
- 删除旧的混合编码规范。
- 创建纯 PowerShell 文档验证脚本。

本计划明确不修改：

- Java、TypeScript、React 或 Electron 业务代码。
- Maven POM、Biome、Vitest、Husky、GitHub Actions 或其他 CI 配置。
- `docs/DOMAIN_MODEL.md`：本次不改变领域字段、状态和字典。
- `docs/ASYNC_TASKS.md`：本次不改变任务状态机、回调、额度和幂等契约。
- 历史 `docs/superpowers/specs/**` 与 `docs/superpowers/plans/**` 中的迁移记录。

真实 RuoYi API 适配代码、前端质量门禁和后端质量门禁分别使用独立规格或计划，不并入本轮文档迁移。

## 执行前提

1. 使用 `using-git-worktrees` 从包含本计划的提交创建专用工作树，建议分支名为 `codex/development-standards-docs`。
2. 在专用工作树中运行 `git status --short`，预期无输出。
3. 原工作树当前存在用户自己的 `AGENTS.md` Ant Design CLI 区块。该改动必须留在原工作树，不复制、不删除、不暂存到本计划分支。
4. 如果不使用专用工作树，执行到 `AGENTS.md` 时必须停止并先解决局部暂存问题；禁止使用 `git add -A`、`git add .` 或整文件暂存误收用户改动。
5. 每次提交只使用计划中列出的显式文件路径。

## 文件结构

### 创建

| 文件 | 职责 |
| --- | --- |
| `docs/BACKEND_CODING_STANDARDS.md` | Java 21、Spring Boot 4.1、RuoYi 6.X 后端编码规则、正反例和检查方式 |
| `docs/FRONTEND_CODING_STANDARDS.md` | TypeScript、React 19、Ant Design Pro 前端编码规则、正反例和检查方式 |
| `scripts/validate-development-standards.ps1` | 验证新手册结构、项目红线、API 契约、导航关系、旧引用和本地链接 |

### 修改

| 文件 | 修改职责 |
| --- | --- |
| `docs/API_CONTRACT.md` | 统一响应、`rows/total` 分页、请求头、错误、ID/精度和专用响应 |
| `docs/BACKEND_GUIDE.md` | 仅保留技术基座、模块组织、开发流程、generator 与相似代码查找 |
| `docs/FRONTEND_GUIDE.md` | 仅保留包结构、页面开发流程、组件选型、布局和 Electron bridge |
| `AGENTS.md` | 按任务路由到新手册，不复制规范正文 |
| `RULES.md` | 区分 Guide、Coding Standards 与 API Contract 的权威职责 |
| `README.md` | 将两份新手册加入入口文档 |
| `docs/DOCUMENT_MAP.md` | 重写任务阅读路径、文档边界和更新规则 |
| `docs/AI_CODING_RULES.md` | 指向新手册、真实 RuoYi skill 路径和验证脚本 |
| `docs/ARCHITECTURE.md` | 将编码规则与工程 Guide 分开链接 |

### 删除

| 文件 | 删除条件 |
| --- | --- |
| `docs/CODING_STANDARDS.md` | 两份新手册、API 契约、Guide 与全部活动入口迁移完成且验证通过后删除 |

## 旧文档迁移矩阵

| `docs/CODING_STANDARDS.md` 原内容 | 唯一归属 |
| --- | --- |
| Java、数据库和权限命名 | `docs/BACKEND_CODING_STANDARDS.md` |
| TypeScript、组件和页面命名 | `docs/FRONTEND_CODING_STANDARDS.md` |
| Controller、Service、Mapper、BO、VO、Entity | `docs/BACKEND_CODING_STANDARDS.md` |
| React 页面、Hook、API Service 和状态 | `docs/FRONTEND_CODING_STANDARDS.md` |
| `R<T>`、分页、错误、ID、金额、文件和流式协议 | `docs/API_CONTRACT.md` |
| 契约何时更新、AI 修改范围、验证与交付 | `docs/AI_CODING_RULES.md` 与 `docs/DOCUMENT_MAP.md` |
| 业务领域状态、任务与额度事实 | 继续引用 `docs/DOMAIN_MODEL.md` 与 `docs/ASYNC_TASKS.md`，不复制 |

---

### 任务 1：创建后端编码规范

**文件：**

- 创建：`docs/BACKEND_CODING_STANDARDS.md`
- 参考：`docs/superpowers/specs/2026-07-27-ai-video-development-standards-design.md`
- 参考：`docs/CODING_STANDARDS.md`
- 参考：`docs/BACKEND_GUIDE.md`
- 参考：`docs/DOMAIN_MODEL.md`
- 参考：`.agents/skills/ruoyi-plus-ai-coding/SKILL.md`
- 参考：`.agents/skills/ruoyi-plus-ai-coding/references/backend.md`
- 参考：`.agents/skills/ruoyi-plus-ai-coding/references/examples.md`

- [ ] **步骤 1：验证目标文件尚不存在并核对事实基线**

运行：

```powershell
if (Test-Path 'docs/BACKEND_CODING_STANDARDS.md') {
  throw 'docs/BACKEND_CODING_STANDARDS.md 已存在，先检查是否有其他任务正在修改'
}

rg -n '<java.version>|<spring-boot.version>|<revision>|<mybatis-plus.version>|<satoken.version>' `
  ai-video-api/pom.xml
```

预期：

- `Test-Path` 不抛异常。
- POM 输出包含 Java 21、Spring Boot 4.1.0、revision 6.0.0-BETA、MyBatis-Plus 3.5.16、Sa-Token 1.45.0。

- [ ] **步骤 2：写入使用说明、规则等级和冲突优先级**

文首必须写入：

```markdown
# 后端编码规范

## 使用说明
```

使用说明必须明确：

- 适用范围是 `ai-video-api`。
- 真实基线是 Java 21、Spring Boot 4.1.0、RuoYi-Vue-Plus 6.0.0-BETA、MyBatis-Plus/MPJ、Sa-Token、MapStruct Plus、Redis/Redisson/Lock4j、SnailJob 和 Spring AI/Snail AI。
- JPA、Spring Security 和 RocketMQ 不是主业务技术基线，不能把相应通用条款强加给项目。
- `【强制】`、`【推荐】`、`【参考】` 的定义与设计稿一致。
- 冲突优先级为：安全与正确性 → 项目契约 → 相邻代码与公共模块 → RuoYi skill/生成器 → 通用行业规范。
- HTTP 以 `API_CONTRACT.md` 为准，领域以 `DOMAIN_MODEL.md` 为准，任务以 `ASYNC_TASKS.md` 为准，模块组织与开发流程以 `BACKEND_GUIDE.md` 为准。
- “当前可执行检查”和“目标门禁”必须分开标注，不能把尚未配置的 ArchUnit、Checkstyle、SpotBugs 或 JaCoCo 描述成现有能力。

- [ ] **步骤 3：写入第 1 至第 3 章**

章节标题必须精确为：

```markdown
## 1. Java 基础、命名和代码格式
## 2. 面向对象、集合、日期和精度
## 3. 异常、日志和敏感数据
```

第 1 章必须覆盖：

- UTF-8、LF、4 空格、文件末尾换行和禁止行尾空格。
- Java/JSON `lowerCamelCase`、类型 `UpperCamelCase`、数据库 `snake_case`。
- `Bo`、`Vo`、`Mapper`、`I...Service` 和 `${module}:${business}:${action}` 命名。
- 稳定状态使用英文值，禁止使用中文消息进行程序分支。
- 公共类型、字段、Controller/Service/Mapper 方法的简洁 JavaDoc；`void` 不写 `@return`。
- 只格式化任务相关文件，import 和注解顺序跟随相邻代码。

第 2 章必须覆盖：

- 后端业务 ID 使用 `Long`，跨 HTTP 边界兼容字符串化大整数。
- 金额、额度和精度值使用 `BigDecimal`，明确单位、精度和舍入方式，禁止 `float/double`。
- HTTP 集合与分页行数据用空集合而不是 `null`。
- `List.of(...)` 只能用于不会继续修改的集合。
- 新业务时间使用 `java.time`；已有 BaseEntity 字段不做无关迁移。
- 稳定状态和业务常量集中定义，禁止魔法字符串。

第 3 章必须覆盖：

- 业务失败使用项目异常模型和统一异常处理，Controller 不散写 `try/catch`。
- 响应不得泄露堆栈、SQL、内部路径、内部 URL 或供应商凭据。
- 日志不得记录完整 Token、签名、临时下载 URL、隐私内容或外部服务密钥。
- `@Log` 默认记录请求与响应，系统全局排除项不覆盖 Token、`clientSecret`、`secretKey` 和 `accessKey`。
- 敏感接口使用 `excludeParamNames` 或 `isSaveRequestData=false` / `isSaveResponseData=false`。
- VO、异常、审计记录和测试快照都必须脱敏。

日志脱敏规则必须包含正例、反例、说明和检查方式。

- [ ] **步骤 4：写入第 4 至第 6 章**

章节标题必须精确为：

```markdown
## 4. Controller、Service、Mapper 分层
## 5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus
## 6. MyBatis-Plus、MPJ、查询、分页和排序
```

第 4 章必须覆盖：

- Controller 只负责 HTTP 入参、Jakarta Validation、登录主体、权限、日志、防重和统一返回。
- Service 负责业务编排、状态、归属、事务、幂等和领域异常。
- Mapper 只负责数据访问；`@DataPermission` 是合法的 SQL 行级数据域机制。
- 标准 CRUD 命名沿用 `queryById`、`queryPageList`、`queryList`、`insertByBo`、`updateByBo`、`deleteWithValidByIds`。
- 普通 JSON 使用 `R<T>`，分页使用 `R<PageResult<Vo>>`；禁止 Entity、裸 `Page/IPage` 和 `R<R<T>>`。
- SSE、文件流、上游强制格式回调和 `/api/snail/chat/**` 必须逐端点声明例外。

必须加入以下字符串返回正反例：

```java
// 正例：字符串作为 data
return R.data(downloadUrl);

// 反例：R.ok(String) 会把字符串放入 msg
return R.ok(downloadUrl);
```

第 5 章必须覆盖：

- Entity 默认继承 `BaseEntity` 以复用项目审计字段；只有模块现状或规格明确要求时才允许例外。
- Entity 使用 `@TableName`、`@TableId`，按需使用 `@TableLogic`、`@Version`，且不得跨 HTTP 边界。
- BO 实现 `Serializable`，使用 `@AutoMapper(target = Entity.class, reverseConvertGenerate = false)`。
- BO 使用 `AddGroup`、`EditGroup`、`QueryGroup` 和 Jakarta Validation；范围查询按项目习惯使用 `params`。
- VO 实现 `Serializable`，使用 `@AutoMapper(target = Entity.class)`，展示、翻译和脱敏放在 VO。
- 转换优先 `MapstructUtils.convert`。
- AI 视频业务专属的稳定跨模块契约放在 `ai-video-core` 对应业务聚合的平级 `dto` 包；两个启动模块不得复制核心 DTO 或业务模型，且不得将其迁入全局 `ruoyi-api`。

第 6 章必须覆盖：

- Mapper 默认继承 `BaseMapperPlus<Entity, Vo>`。
- generator 风格查询优先 `QueryBuilder.lambda(...)`；联表查询复用相邻模块 MPJ/`lambdaJoin` 写法。
- 使用项目已有的条件方法，如 `eqIfPresent`、`eqIfText`、`likeIfText`、`inIfNotEmpty`、`betweenParams`。
- wrapper/MPJ 无法清晰表达或相邻模块已采用 XML 时才新增 XML。
- `PageQuery` 的默认 pageSize 是 `Integer.MAX_VALUE`，业务接口必须设置最大页大小。
- `PageQuery(Integer pageSize, Integer pageNum)` 参数顺序必须醒目标注。
- `orderByColumn` 必须经过业务白名单映射，字符过滤不能替代字段授权。
- HTTP 空页禁止无参 `PageResult.build()`，目标响应必须稳定为 `rows=[]`。
- owner/data scope 必须进入 SQL 条件或 `@DataPermission`，禁止查询后在内存过滤。

分页正例必须使用：

```java
Page<VideoTaskVo> page =
    videoTaskMapper.selectVoPage(pageQuery.build(), wrapper);
return PageResult.build(page.getRecords(), page.getTotal());
```

- [ ] **步骤 5：写入第 7 至第 10 章**

章节标题必须精确为：

```markdown
## 7. 事务、并发、幂等和外部副作用
## 8. Sa-Token、权限码、数据归属和数据权限
## 9. Redis、Spring Cache、Redisson 和 Lock4j
## 10. SnailJob、事件和异步任务
```

第 7 章必须覆盖：

- 事务边界放 Service，多表写使用 `@Transactional(rollbackFor = Exception.class)`。
- 归属、状态和额度校验与写入保持一致边界。
- 并发更新使用条件更新、乐观锁或唯一约束，禁止先查后写的无保护竞态。
- AI、OSS、通知和第三方调用不得放在无法回滚的长事务中，使用事务后事件、补偿或可恢复状态。
- 禁止吞异常导致事务错误提交。
- `ownerId + idempotencyKey` 去重；Job、回调和重投事件按至少一次执行设计。
- 分布式并发复用 Redisson/Lock4j；锁键、等待时间、租期和释放策略必须明确。

第 8 章必须覆盖：

- 管理接口使用 `@SaCheckPermission` 和统一权限码。
- 权限码不能替代资源归属；ownerId 从登录上下文派生。
- 查询、详情、修改、删除、下载、预览和任务结果都校验归属。
- 管理端跨用户操作需要独立权限与审计。
- `@DataPermission`/`@DataColumn` 的别名必须匹配 SQL/MPJ。
- `DataPermissionHelper.ignore`、`@SaIgnore`、`security.excludes` 必须最小范围、有理由、有测试。
- `SecurityConfig` 同时校验 `clientid`、`clientAccessPath` 和 `clientIpWhitelist`，不得绕过。

第 9 章必须覆盖：

- 复用 Spring Cache、`CacheUtils`、`RedisUtils` 和现有 `CacheNames`。
- 缓存键包含 owner/业务隔离维度，禁止跨用户污染。
- 数据库写与缓存失效必须有一致性策略，事务回滚前不得发布错误缓存状态。
- 权限和归属不得被陈旧缓存绕过。
- 空值、TTL、序列化和批量失效行为必须显式。
- 分布式锁复用 Redisson/Lock4j，不自造 Redis 锁协议。

第 10 章必须覆盖：

- 长耗时 AI 生成持久化任务并立即返回 taskId。
- 状态、进度、失败原因和输出以后端任务记录为准并链接 `ASYNC_TASKS.md`。
- 终态防重复、防回退；重试不重复扣额度、创建输出或发送通知。
- 事务事件在提交后处理，失败必须可观察、可重试或可补偿。
- SnailJob、轮询、事件和回调都按可能重复执行设计。
- 当前项目没有 RocketMQ 业务基线，不能虚构消息拓扑。

- [ ] **步骤 6：写入第 11 至第 14 章**

章节标题必须精确为：

```markdown
## 11. 文件、OSS、导入和导出
## 12. Spring AI、Snail AI 和外部服务适配
## 13. 配置、密钥和双启动应用边界
## 14. 单元、Web、数据访问和集成测试
```

第 11 章必须覆盖：

- 复用 RuoYi OSS、文件与 Excel 能力。
- 上传检查大小、数量、扩展名、MIME、文件魔数、归属和恶意内容风险。
- 文件名和路径安全化，不返回真实 bucket 或内部路径。
- 下载、预览和任务结果使用授权且有期限的访问凭证。
- 大文件流式传输，禁止整文件读入内存。
- Excel 导入逐行校验并汇总错误，导出过滤敏感字段。

第 12 章必须覆盖：

- 外部 AI/OSS/通知适配放在集成边界，核心领域不依赖供应商 DTO。
- 显式映射供应商任务 ID、错误码、超时、取消和重试语义。
- 非幂等请求禁止盲目重试，回调验证来源并保证幂等。
- 凭据只从配置读取，不写日志、不进入 VO。
- 普通 Snail AI JSON API 使用 `R<T>`；仅 `/api/snail/chat/**` 使用 SDK `Result`。
- SSE 使用专用流式协议。

第 13 章必须覆盖：

- 配置使用 `@ConfigurationProperties`；共享开发环境的地址、账号、密码、Token 和密钥统一维护在两个启动模块的 `application-dev.yml`。
- `ruoyi-admin` 与 `ai-video-user-api` 只承担各自接口、配置和路由装配。
- 新 Controller 必须声明管理端、用户端或双端暴露目标。
- 用户端和管理端可以有不同 BO/VO 和权限入口，但不能复制核心业务。
- 两个启动应用的路由、安全排除和配置差异必须分别验证。

第 14 章必须覆盖：

- 单元测试覆盖业务规则、边界、状态、额度和幂等失败。
- Web 测试覆盖参数、401/403、权限、归属、`R<T>`、字符串返回和 `rows=[]`。
- Mapper 测试覆盖 owner/DataPermission、分页上限、排序白名单和联表别名。
- 集成测试直接连接开发机本机安装的 MySQL 8（关系型数据库）和 Redis 7（缓存数据库）专用测试库/命名空间；默认读取用户端 `application-dev.yml` 的本机配置与凭据，环境变量仅可选覆盖，禁止 Docker、Docker Compose、Testcontainers、WSL、虚拟机、Podman 及其他容器化或虚拟化环境。该历史计划已被 2026-07-30 的本机受控集成测试决定替代，具体约束以 `docs/BACKEND_CODING_STANDARDS.md` 为准。
- 两个启动应用分别进行路由暴露和安全边界 Smoke Test。
- 重复回调、重复 Job、事务回滚、缓存失效、文件越权和外部失败必须有回归测试。
- 不允许跳过测试或“零测试”制造成功。
- `unit/integration/slow`、ArchUnit、Checkstyle、SpotBugs、JaCoCo 等必须标注为目标门禁，不得宣称当前已经启用。

以下高风险主题必须使用完整的“规则、正例、反例、说明、检查方式”格式：

- `R.ok(String)`。
- 分页空集合、pageSize 上限与排序白名单。
- 权限码与数据归属。
- 客户端路径/IP 访问控制。
- `@Log` 敏感数据。
- 事务中的外部副作用。
- 任务幂等。
- 缓存一致性。
- 文件上传。
- Snail AI 专用协议。
- 双启动应用路由暴露。

- [ ] **步骤 7：验证后端手册结构和项目红线**

运行：

```powershell
$path = 'docs/BACKEND_CODING_STANDARDS.md'
$text = Get-Content -Raw -Encoding UTF8 $path

$required = @(
  '## 1. Java 基础、命名和代码格式',
  '## 2. 面向对象、集合、日期和精度',
  '## 3. 异常、日志和敏感数据',
  '## 4. Controller、Service、Mapper 分层',
  '## 5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus',
  '## 6. MyBatis-Plus、MPJ、查询、分页和排序',
  '## 7. 事务、并发、幂等和外部副作用',
  '## 8. Sa-Token、权限码、数据归属和数据权限',
  '## 9. Redis、Spring Cache、Redisson 和 Lock4j',
  '## 10. SnailJob、事件和异步任务',
  '## 11. 文件、OSS、导入和导出',
  '## 12. Spring AI、Snail AI 和外部服务适配',
  '## 13. 配置、密钥和双启动应用边界',
  '## 14. 单元、Web、数据访问和集成测试',
  '【强制】',
  '【推荐】',
  '【参考】',
  '正例：',
  '反例：',
  '检查方式：',
  'Java 21',
  'Spring Boot 4.1.0',
  'RuoYi-Vue-Plus 6.0.0-BETA',
  'BaseEntity',
  'R.ok(String)',
  'PageResult.build()',
  'PageQuery(Integer pageSize, Integer pageNum)',
  'orderByColumn',
  '@DataPermission',
  'clientAccessPath',
  'clientIpWhitelist',
  'excludeParamNames',
  'ruoyi-admin',
  'ai-video-user-api',
  '/api/snail/chat/**'
)

$missing = $required | Where-Object { -not $text.Contains($_) }
if ($missing) {
  throw "后端规范缺少：$($missing -join ', ')"
}

'BACKEND_STANDARDS_OK'
```

预期：输出 `BACKEND_STANDARDS_OK`，退出码 0。

- [ ] **步骤 8：提交后端编码规范**

运行：

```powershell
git add -- docs/BACKEND_CODING_STANDARDS.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: add backend coding standards"
```

预期：

- `git diff --cached --check` 无输出。
- 暂存列表只有 `docs/BACKEND_CODING_STANDARDS.md`。
- 提交成功。

---

### 任务 2：创建前端编码规范

**文件：**

- 创建：`docs/FRONTEND_CODING_STANDARDS.md`
- 参考：`docs/superpowers/specs/2026-07-27-ai-video-development-standards-design.md`
- 参考：`docs/CODING_STANDARDS.md`
- 参考：`docs/FRONTEND_GUIDE.md`
- 参考：`ai-video-ui/ai-video-webapp/package.json`
- 参考：`ai-video-ui/ai-video-webapp/biome.json`
- 参考：`ai-video-ui/ai-video-webapp/vitest.config.ts`
- 参考：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 参考：`ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts`
- 参考：`ai-video-ui/ai-video-platform-ui/package.json`

- [ ] **步骤 1：验证目标文件尚不存在并核对前端版本**

运行：

```powershell
if (Test-Path 'docs/FRONTEND_CODING_STANDARDS.md') {
  throw 'docs/FRONTEND_CODING_STANDARDS.md 已存在，先检查是否有其他任务正在修改'
}

$webapp = Get-Content -Raw -Encoding UTF8 `
  'ai-video-ui/ai-video-webapp/package.json' | ConvertFrom-Json
$platform = Get-Content -Raw -Encoding UTF8 `
  'ai-video-ui/ai-video-platform-ui/package.json' | ConvertFrom-Json

@(
  [pscustomobject]@{
    Package = 'ai-video-webapp'
    Node = $webapp.engines.node
    TypeScript = $webapp.devDependencies.typescript
    React = $webapp.dependencies.react
    UmiMax = $webapp.devDependencies.'@umijs/max'
    AntDesign = $webapp.dependencies.antd
    ProComponents = $webapp.dependencies.'@ant-design/pro-components'
    ReactQuery = $webapp.dependencies.'@tanstack/react-query'
    QualityTools = "Biome $($webapp.devDependencies.'@biomejs/biome'); Vitest $($webapp.devDependencies.vitest)"
  }
  [pscustomobject]@{
    Package = 'ai-video-platform-ui'
    Node = $platform.engines.node
    TypeScript = $platform.devDependencies.typescript
    React = $platform.dependencies.react
    UmiMax = $platform.dependencies.'@umijs/max'
    AntDesign = $platform.dependencies.antd
    ProComponents = $platform.dependencies.'@ant-design/pro-components'
    ReactQuery = $platform.dependencies.'@tanstack/react-query'
    QualityTools = "Oxfmt $($platform.devDependencies.oxfmt); Oxlint $($platform.devDependencies.oxlint)"
  }
)
```

预期：

- `ai-video-webapp` 输出 Node 22+、TypeScript 7、React 19、Umi Max 4、Ant Design 6、ProComponents 3、React Query 5、Biome 2 和 Vitest 4。
- `ai-video-platform-ui` 输出 Node 20+、TypeScript 6、React 19、Umi Max 4、Ant Design 6、ProComponents 3、React Query 5、Oxfmt 和 Oxlint 的对应版本。

- [ ] **步骤 2：写入使用说明、规则等级和冲突优先级**

文首必须写入：

```markdown
# 前端编码规范

## 使用说明
```

使用说明必须明确：

- 强制适用于 `ai-video-ui/ai-video-webapp` 和 `ai-video-ui/ai-video-platform-ui` 两个 React 包；包级目录、构建命令和检查工具差异以各自工程为准。
- 技术基线使用 major 版本，精确版本以 `package.json` 和锁文件为准。
- React、Ant Design、ProComponents、React Query 和 API 消费规则由两个包共同遵守；TypeScript 版本及 Biome/Vitest、Oxc 等工具规则必须标明适用包，不能把一个包的脚本假定为另一个包已经具备。
- `【强制】`、`【推荐】`、`【参考】` 的定义。
- 冲突优先级为：安全与契约 → 项目设计/相邻代码 → React/Ant Design/Umi 官方规则 → 通用 TypeScript 规范。
- 线上字段、Header、分页和错误以 `API_CONTRACT.md` 为唯一来源。
- 包结构、页面开发流程和 Electron bridge 以 `FRONTEND_GUIDE.md` 为准。
- 当前 Biome/Vitest/演示协议问题必须标记为“当前差距”，不宣称自动门禁已启用。

- [ ] **步骤 3：写入第 1 至第 4 章**

章节标题必须精确为：

```markdown
## 1. TypeScript 类型、命名和模块边界
## 2. React 组件、Props 和组合设计
## 3. Hooks、副作用和闭包安全
## 4. 本地状态、服务端状态和请求管理
```

第 1 章必须覆盖：

- 保持 `strict` 和 `noImplicitReturns`。
- 手写业务代码不得无理由使用 `any`、非空断言或双重断言；使用 `unknown`、类型守卫或解析函数。
- 跨页面 DTO/VO、枚举和状态映射集中定义，页面不临时声明协议类型。
- 页面不得散写 URL、状态字符串、错误码或 envelope 解包。
- 生成目录明确隔离；手写 Service 和 RuoYi adapter 不得因位于 `src/services` 而逃避检查。

类型安全正例必须包含：

```ts
function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败';
}
```

反例必须展示直接使用 `any` 和未经验证的双重断言。

第 2 章必须覆盖：

- 组件单一职责，复杂页面拆成容器、展示组件和领域 Hook。
- Props 使用业务语义命名，事件回调采用 `on<Action>`。
- render 保持纯函数，不修改 props/state，不执行请求。
- 可计算值在渲染阶段派生，不用 Effect 镜像。
- 列表 key 使用稳定业务 ID，禁止随机数和数组下标替代可用 ID。

第 3 章必须覆盖：

- Hook 只在组件或自定义 Hook 顶层调用。
- Effect 仅用于外部系统同步，依赖完整，禁止无理由关闭依赖检查。
- 请求、订阅、定时器和监听器必须清理。
- 旧请求结果不得覆盖新查询，使用 AbortController 或请求库提供的取消能力。
- 事件处理器读取最新状态，避免陈旧闭包。

Effect 正例必须包含完整依赖和清理：

```tsx
useEffect(() => {
  const controller = new AbortController();

  void loadResource(resourceId, controller.signal).catch((error: unknown) => {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      reportError(error);
    }
  });

  return () => controller.abort();
}, [resourceId, reportError]);
```

第 4 章必须覆盖：

- 页面局部 UI 状态使用组件状态。
- 服务端状态由 React Query 或 Umi 请求层管理，不复制为多份本地真相。
- mutation 成功后按领域键失效缓存或刷新表格。
- 前端不得自行决定任务终态、额度结果、文件授权或数据归属。
- 异步页面确定处理加载、空、搜索无结果、失败、403、取消、操作中、成功和失败。
- 网络取消不显示普通业务错误。

- [ ] **步骤 4：写入第 5 至第 8 章**

章节标题必须精确为：

```markdown
## 5. Ant Design 与 ProComponents 组件选型
## 6. 表单、表格、弹窗、抽屉和反馈
## 7. 路由、菜单、前端权限和国际化
## 8. 样式、主题、响应式和无障碍
```

第 5 章必须覆盖：

- 优先使用 Ant Design、ProComponents 和现有业务组件。
- 管理页优先 ProTable/ProForm/ProDescriptions/ProList，生产工作台使用基础组件和领域组件组合。
- API、Token 或 Semantic 不确定时查询官方资料或 `@ant-design/cli`。
- 禁止通过 DOM 查询、内部类名或宽泛全局 CSS 控制 Ant Design 内部状态。
- 主题使用 `ConfigProvider` Token。

第 6 章必须覆盖：

- 表单前端校验、防重复提交；提交中 loading/禁用，后端成功后才关闭和重置。
- ProTable `request` 只接收 adapter 结果 `{ data, total, success }`。
- 排序列显式映射，未知列不透传；前端限制 pageSize，后端独立兜底。
- Modal/Drawer 的打开、提交、关闭、销毁和重置状态可预测。
- 删除、覆盖、取消任务、重试和额度消耗需要确认。
- 上传展示类型、大小、数量、进度和失败原因。
- 成功提示只能在服务端确认后出现。

第 7 章必须覆盖：

- 路由和菜单由 Umi 配置维护，页面不自造第二套路由。
- `access.ts` 只控制界面显示，不能替代后端授权和归属。
- 角色、权限码和状态不得散写。
- 401 和 403 由 adapter 统一处理；403 不盲目跳登录。
- 新增用户可见文案进入国际化体系。
- `content-language` 由请求层根据当前语言统一设置。
- Electron 主进程不保存业务 Token 或调用业务 API。

第 8 章必须覆盖：

- 颜色、间距和主题使用 Token，避免硬编码。
- 页面适配项目支持的视口，关键操作不得因窄屏不可达。
- 交互元素支持键盘、有标签或替代文本、焦点可见。
- 不以颜色作为唯一状态表达。
- 当前 Biome 关闭的部分 a11y 规则必须列为自动化缺口，暂由 Ant Design CLI、React Doctor、测试或 Review 明确承担。

- [ ] **步骤 5：写入第 9 和第 10 章**

章节标题必须精确为：

```markdown
## 9. RuoYi API 前端使用边界
## 10. 性能、错误边界和测试
```

第 9 章只定义代码职责和消费方式，wire schema 链接 `API_CONTRACT.md`，不得复制出第二份权威协议。必须覆盖：

- Page → 模块 Service → RuoYi adapter → Umi Request 的依赖方向。
- 页面不处理 Token、`clientid`、envelope、`rows` 或业务错误码。
- 普通 JSON、Blob、SSE 和 `/api/snail/chat/**` 使用各自明确的 adapter。
- ProTable 页面只消费标准结果。
- ID 规范化为字符串，禁止 `Number`、`parseInt` 和算术。
- 金额/额度字符串需要计算时使用十进制库或交给后端。

页面使用正例：

```tsx
<ProTable<VideoTask>
  request={(params, sorter) => videoTaskService.page(params, sorter)}
/>
```

反例必须展示页面直接读取 `response.data.rows`、拼接 Authorization 或判断中文 `msg`。

第 10 章必须覆盖：

- 性能优化以测量为依据，禁止无证据滥用 memo。
- 根级和关键页面设置错误边界，用户可恢复。
- Vitest/Testing Library 测试用户可观察行为。
- 高风险流程覆盖成功、失败、权限、取消和重复提交。
- 测试排除必须最小范围且说明原因。
- “零测试”不得产生成功状态。
- 生成声明可以明确排除，手写业务目录不得整体排除。
- 当前差距必须列明：`src/services` 整体排除、`noExplicitAny`/Hook 依赖/a11y 规则关闭、登录测试排除、`passWithNoTests`、演示 API 协议和格式检查缺口。

以下主题必须使用完整的“规则、正例、反例、说明、检查方式”格式：

- `any`/断言。
- Effect 依赖与取消。
- 服务端状态。
- ProTable adapter。
- 前端权限。
- RuoYi envelope。
- ID/精度。
- 测试排除。

- [ ] **步骤 6：验证前端手册结构和项目红线**

运行：

```powershell
$path = 'docs/FRONTEND_CODING_STANDARDS.md'
$text = Get-Content -Raw -Encoding UTF8 $path

$required = @(
  '## 1. TypeScript 类型、命名和模块边界',
  '## 2. React 组件、Props 和组合设计',
  '## 3. Hooks、副作用和闭包安全',
  '## 4. 本地状态、服务端状态和请求管理',
  '## 5. Ant Design 与 ProComponents 组件选型',
  '## 6. 表单、表格、弹窗、抽屉和反馈',
  '## 7. 路由、菜单、前端权限和国际化',
  '## 8. 样式、主题、响应式和无障碍',
  '## 9. RuoYi API 前端使用边界',
  '## 10. 性能、错误边界和测试',
  '【强制】',
  '【推荐】',
  '【参考】',
  '正例：',
  '反例：',
  '检查方式：',
  'TypeScript 6',
  'TypeScript 7',
  'React 19',
  'Umi Max 4',
  'Ant Design 6',
  'ProComponents 3',
  'React Query 5',
  'Biome 2',
  'Vitest 4',
  'Oxfmt',
  'Oxlint',
  'ai-video-webapp',
  'ai-video-platform-ui',
  'src/services',
  'passWithNoTests',
  '/api/snail/chat/**'
)

$missing = $required | Where-Object { -not $text.Contains($_) }
if ($missing) {
  throw "前端规范缺少：$($missing -join ', ')"
}

'FRONTEND_STANDARDS_OK'
```

预期：输出 `FRONTEND_STANDARDS_OK`，退出码 0。

- [ ] **步骤 7：提交前端编码规范**

运行：

```powershell
git add -- docs/FRONTEND_CODING_STANDARDS.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: add frontend coding standards"
```

预期：

- `git diff --cached --check` 无输出。
- 暂存列表只有 `docs/FRONTEND_CODING_STANDARDS.md`。
- 提交成功。

---

### 任务 3：按 RuoYi 6.X 修正 API 契约

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 参考：`ai-video-api/ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/R.java`
- 参考：`ai-video-api/ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/PageResult.java`
- 参考：`ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/core/page/PageQuery.java`
- 参考：`ai-video-api/ruoyi-common/ruoyi-common-security/src/main/java/org/dromara/common/security/config/SecurityConfig.java`
- 参考：`ai-video.md`
- 参考：`ai-video-api/ruoyi-admin/src/main/resources/application.yml`
- 参考：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 参考：`ai-video-api/ruoyi-admin/src/main/java/org/dromara/web/controller/AuthController.java`
- 参考：`ai-video-api/ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysUserController.java`
- 参考：`ai-video-api/ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysOssController.java`
- 参考：`ai-video-ui/ai-video-webapp/config/proxy.ts`

- [ ] **步骤 1：运行旧契约检查并确认失败**

运行：

```powershell
$hits = rg -n '"records"|data\.records|禁止返回 `rows`|sortField|sortOrder' `
  docs/API_CONTRACT.md

$searchExitCode = $LASTEXITCODE
if ($searchExitCode -eq 1) {
  throw '预期发现旧分页契约，但没有找到；先重新核对当前文件'
}
if ($searchExitCode -gt 1) {
  exit $searchExitCode
}

$hits
```

预期：输出当前 `records`、`data.records`、禁止 `rows`、`sortField` 或 `sortOrder` 的位置。

- [ ] **步骤 2：修正业务 API 前缀与 RuoYi 内置端点边界**

保留新 AI 视频业务 API 的 `/api` 前缀，并明确：

- PRD 和模块规格定义的新业务资源使用 `/api/**`。
- RuoYi 内置端点保留实际映射，例如 `/auth/**`、`/system/**`、`/resource/**`。
- 前端通过集中 baseURL/proxy 和模块 Service 选择正确路径，禁止机械地给所有 RuoYi 内置路径添加 `/api`。
- 专用内部执行器是否使用 `/api` 由其独立规格决定。

- [ ] **步骤 3：写入统一响应和分页权威类型**

文档必须包含：

```ts
export interface RuoYiResponse<T> {
  code: number;
  msg: string;
  data: T | null;
}

export interface RuoYiPageResult<T> {
  total: number;
  // 兼容当前源码可能产生的 null；目标 HTTP 契约仍要求服务端返回数组。
  rows: T[] | null;
}
```

分页 JSON 必须改为：

```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "total": 0,
    "rows": []
  }
}
```

同时写明：

- 普通分页成功响应的 `data` 字段只有 `total` 和 `rows`。
- HTTP 空页必须稳定为 `rows=[]`。
- 字符串业务值必须位于 `data`，`msg` 只承载用户可读提示。
- 前端 adapter 可防御性地把遗留或异常的 `rows=null` 归一化为空数组，但这不是服务端目标契约。

- [ ] **步骤 4：修正 ProTable 请求和响应映射**

请求映射必须改为：

| ProTable | RuoYi |
| --- | --- |
| `current` | `pageNum` |
| `pageSize` | `pageSize` |
| 排序列键经显式白名单映射 | `orderByColumn` |
| `ascend` | `isAsc=asc` |
| `descend` | `isAsc=desc` |

必须写明：

- 未知排序列不发送。
- 前端限制可选和发送的 pageSize，后端独立设置最大值。
- 后端字符清理不能代替字段白名单。

响应转换固定为：

```ts
function toProTableResult<T>(page: RuoYiPageResult<T>) {
  return {
    data: page.rows ?? [],
    total: page.total,
    success: true,
  };
}
```

- [ ] **步骤 5：补充请求头、错误、ID 和专用响应**

请求头必须写为：

```http
Authorization: <登录响应中的原始 access_token>
clientid: <configured-client-id>
content-language: <当前国际化语言，例如 zh-CN>
```

约束必须包括：

- 未经后端配置不得擅自添加 `Bearer `。
- `clientid` 与登录和 Token 绑定客户端一致。
- `content-language` 必须跟随当前国际化语言，不得固定为 `zh-CN`。
- `clientAccessPath` 和 `clientIpWhitelist` 是服务端访问控制。
- 页面和模块 Service 不重复拼接 Header。
- HTTP 状态与 `R.code` 是两条错误通道，adapter 归一化并防止重复提示。
- HTTP/R 401 清理登录态并只跳转一次。
- HTTP/R 403 展示无权限，不跳登录。
- 除 401/403 外的业务码抛出携带 `code`/`msg` 的标准业务异常。
- 网络、超时、取消、5xx 与业务错误分开。
- 禁止依据中文 `msg` 分支。
- 业务 ID 在前端使用字符串；金额、额度和 BigDecimal 使用字符串。
- Blob 与 SSE 使用独立 adapter。
- 仅 `/api/snail/chat/**` 使用 SDK `Result`，其他 Snail AI JSON API 仍使用 `R<T>`。

移除“RuoYi 后端接口映射”中的 Controller/BO/VO/Mapper 实现规则，替换成到 `BACKEND_CODING_STANDARDS.md` 的链接。上传下载章节保留线上字段与响应，验证实现细节链接后端手册。

- [ ] **步骤 6：验证新契约**

运行：

```powershell
$text = Get-Content -Raw -Encoding UTF8 'docs/API_CONTRACT.md'
$required = @(
  '"rows"',
  '"total"',
  'orderByColumn',
  'isAsc',
  'page.rows ?? []',
  'Authorization',
  'clientid',
  'content-language',
  '当前国际化语言',
  '携带 `code`/`msg` 的标准业务异常',
  'clientAccessPath',
  'clientIpWhitelist',
  'BigDecimal',
  'Blob',
  'SSE',
  '/api/snail/chat/**'
)

$missing = $required | Where-Object { -not $text.Contains($_) }
if ($missing) {
  throw "API 契约缺少：$($missing -join ', ')"
}

$bad = rg -n '"records"|data\.records|禁止返回 `rows`|sortField|sortOrder' `
  docs/API_CONTRACT.md

if ($LASTEXITCODE -eq 0) {
  $bad
  throw 'API 契约仍包含旧分页字段'
}
if ($LASTEXITCODE -gt 1) {
  exit $LASTEXITCODE
}

$implementationLeak = rg -n 'PageResult\.build\(|R\.ok\(String\)' `
  docs/API_CONTRACT.md

if ($LASTEXITCODE -eq 0) {
  $implementationLeak
  throw 'API 契约仍包含应归入后端编码规范的 Java 实现细节'
}
if ($LASTEXITCODE -gt 1) {
  exit $LASTEXITCODE
}

'API_CONTRACT_OK'
```

预期：输出 `API_CONTRACT_OK`，不存在旧分页字段。

- [ ] **步骤 7：提交 API 契约修正**

运行：

```powershell
git add -- docs/API_CONTRACT.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: align API contract with RuoYi 6"
```

预期：暂存和提交只包含 `docs/API_CONTRACT.md`。

---

### 任务 4：收窄前后端 Guide 的职责

**文件：**

- 修改：`docs/BACKEND_GUIDE.md`
- 修改：`docs/FRONTEND_GUIDE.md`

- [ ] **步骤 1：重写 Backend Guide 的职责说明**

`BACKEND_GUIDE.md` 最终只保留：

1. 技术基座与两个启动模块的定位。
2. 共享业务模块、用户端接口层、管理端接口层的职责。
3. 推荐模块和业务聚合目录。
4. 开始后端任务前的阅读顺序。
5. generator 模板与相似模块的查找流程。
6. 新增模块、扩展复杂模块和复用基础能力的开发步骤。
7. 到后端编码规范、API、领域和异步任务契约的链接。

必须修正本地 skill 路径：

```text
.agents/skills/ruoyi-plus-ai-coding/SKILL.md
```

以下内容不再在 Guide 中维护完整规则正文：

- Controller/Service/Mapper 分层。
- BO/VO/Entity 命名。
- RuoYi 返回、分页、查询、权限、事务、缓存和文件细则。
- 任务与额度生命周期事实。

这些内容分别链接 `BACKEND_CODING_STANDARDS.md`、`API_CONTRACT.md`、`DOMAIN_MODEL.md` 和 `ASYNC_TASKS.md`。

- [ ] **步骤 2：重写 Frontend Guide 的职责说明**

`FRONTEND_GUIDE.md` 最终只保留：

1. 技术栈和用户端/管理端/Electron 包边界。
2. 管理页与生产工作台的页面类型和组件选型流程。
3. 用户端 Web、平台 UI 和 Electron 的目录职责。
4. 新建页面时的路由、页面、领域组件、Service 和验证流程。
5. 布局范式。
6. Electron bridge 的能力和接入流程。
7. 到前端编码规范、API 契约和 AI 编码规则的链接。

以下内容不再在 Guide 中维护完整规则正文：

- TypeScript、React、Hook、状态和测试强制规则。
- ProTable 线上字段转换。
- API envelope、ID、金额和错误处理。
- Ant Design CLI 命令全集。

这些内容分别链接 `FRONTEND_CODING_STANDARDS.md`、`API_CONTRACT.md` 和 `AI_CODING_RULES.md`。

- [ ] **步骤 3：验证 Guide 与 Coding Standards 的边界**

运行：

```powershell
$backend = Get-Content -Raw -Encoding UTF8 'docs/BACKEND_GUIDE.md'
$frontend = Get-Content -Raw -Encoding UTF8 'docs/FRONTEND_GUIDE.md'

$requiredBackend = @(
  'BACKEND_CODING_STANDARDS.md',
  'API_CONTRACT.md',
  'DOMAIN_MODEL.md',
  'ASYNC_TASKS.md',
  '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
$requiredFrontend = @(
  'FRONTEND_CODING_STANDARDS.md',
  'API_CONTRACT.md',
  'AI_CODING_RULES.md',
  'Electron'
)

$missingBackend = $requiredBackend | Where-Object { -not $backend.Contains($_) }
$missingFrontend = $requiredFrontend | Where-Object { -not $frontend.Contains($_) }

if ($missingBackend) {
  throw "Backend Guide 缺少：$($missingBackend -join ', ')"
}
if ($missingFrontend) {
  throw "Frontend Guide 缺少：$($missingFrontend -join ', ')"
}

$forbiddenBackend = @(
  'R<PageResult',
  'PageResult\.build\(',
  'PageQuery\(',
  '\borderByColumn\b',
  '@DataPermission',
  '@Transactional',
  '\bclientAccessPath\b',
  '\bclientIpWhitelist\b',
  '\bexcludeParamNames\b'
)
$forbiddenFrontend = @(
  'R<PageResult',
  'data\.records',
  'data\.rows',
  '\bpageNum\b',
  '\bpageSize\b',
  '\borderByColumn\b',
  '\bisAsc\b',
  '\bclientid\b',
  'content-language',
  '\bBigDecimal\b',
  'antd\s+(info|doc|demo|token|semantic|lint|doctor)\b',
  'ProTable\s*/\s*ProForm\s*约定',
  '通用组件与状态',
  '枚举、类型和 API',
  '状态管理',
  'Ant Design AI 辅助'
)

$duplicateBackend = $forbiddenBackend |
  Where-Object { $backend -match $_ }
$duplicateFrontend = $forbiddenFrontend |
  Where-Object { $frontend -match $_ }

if ($duplicateBackend) {
  throw "Backend Guide 仍复制编码细则：$($duplicateBackend -join ', ')"
}
if ($duplicateFrontend) {
  throw "Frontend Guide 仍复制编码或协议细则：$($duplicateFrontend -join ', ')"
}

'GUIDE_BOUNDARIES_OK'
```

预期：输出 `GUIDE_BOUNDARIES_OK`。

- [ ] **步骤 4：提交 Guide 重构**

运行：

```powershell
git add -- docs/BACKEND_GUIDE.md docs/FRONTEND_GUIDE.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: separate guides from coding standards"
```

预期：暂存和提交只有两个 Guide。

---

### 任务 5：迁移活动入口并安全删除旧规范

**文件：**

- 修改：`AGENTS.md`
- 修改：`RULES.md`
- 修改：`README.md`
- 修改：`docs/DOCUMENT_MAP.md`
- 修改：`docs/AI_CODING_RULES.md`
- 修改：`docs/ARCHITECTURE.md`
- 创建：`scripts/validate-development-standards.ps1`
- 删除：`docs/CODING_STANDARDS.md`

- [ ] **步骤 1：记录迁移前旧入口和 AGENTS 范围**

在专用工作树运行：

```powershell
git status --short
rg -n 'docs/CODING_STANDARDS\.md' `
  AGENTS.md RULES.md README.md docs `
  --glob '*.md' `
  --glob '!docs/superpowers/specs/**' `
  --glob '!docs/superpowers/plans/**'
```

预期：

- `git status --short` 无输出。
- 旧入口至少命中 `docs/DOCUMENT_MAP.md`。
- 专用工作树的 `AGENTS.md` 不包含原工作树未提交的 `antd-cli setup` 区块。

- [ ] **步骤 2：更新 AGENTS、RULES 和 README**

`AGENTS.md` 只更新阅读顺序和任务路由：

- 前端任务读取 `docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md` 和 `docs/API_CONTRACT.md`。
- 后端任务读取 `.agents/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md` 和 `docs/API_CONTRACT.md`。
- 规范文档变更运行 `scripts/validate-development-standards.ps1`。
- 不复制手册正文。
- 不添加、删除或修改原工作树的 Ant Design CLI 用户区块。

`RULES.md` 必须区分：

- Guide 负责“如何组织和完成开发”。
- Coding Standards 负责“代码必须如何编写”。
- API Contract 负责“前后端如何交换数据”。
- 前端硬规则引用 `FRONTEND_CODING_STANDARDS.md`。
- 后端硬规则引用 `BACKEND_CODING_STANDARDS.md`。

`README.md` 的入口文档必须同时列出：

- `docs/FRONTEND_CODING_STANDARDS.md`
- `docs/BACKEND_CODING_STANDARDS.md`

- [ ] **步骤 3：更新 Document Map、AI Coding Rules 和 Architecture**

`docs/DOCUMENT_MAP.md` 必须：

- 前端页面任务路由到 Frontend Guide、Frontend Coding Standards 和 API Contract。
- 后端 API 任务路由到 RuoYi skill、Backend Guide、Backend Coding Standards、API Contract 和领域文档。
- 将“改通用代码风格”拆成前端与后端两条。
- 文档边界分别定义两份 Coding Standards。
- 更新规则明确：改语言/框架编码规则更新对应 Coding Standards；改组织流程更新对应 Guide。
- 删除活动入口中对 `docs/CODING_STANDARDS.md` 的描述。

`docs/AI_CODING_RULES.md` 必须：

- 前端修改前读取 Frontend Coding Standards。
- 后端修改前读取真实路径的 RuoYi skill 和 Backend Coding Standards。
- 文档规范变更后运行验证脚本。
- 保留 Ant Design 官方资料和 CLI 的权威查询流程。
- 区分现有检查与目标门禁。

`docs/ARCHITECTURE.md` 的文档边界必须：

- 前端工程组织链接 Frontend Guide，代码规则链接 Frontend Coding Standards。
- 后端工程组织链接 Backend Guide，代码规则链接 Backend Coding Standards。
- API、领域和异步契约链接保持不变。

- [ ] **步骤 4：创建完整的文档验证脚本**

本脚本校验活动文档中的普通内联、非图片本地 Markdown 链接；引用式链接、图片链接和带 title 的复杂链接不在本轮自动校验范围，仍由 Review 检查。

创建 `scripts/validate-development-standards.ps1`，内容必须完整为：

```powershell
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$docsRoot = Join-Path $projectRoot 'docs'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError {
  param([Parameter(Mandatory)][string]$Message)
  [void]$script:errors.Add($Message)
}

function Read-Utf8File {
  param([Parameter(Mandatory)][string]$Path)
  return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
}

function Assert-FileExists {
  param([Parameter(Mandatory)][string]$Path)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Add-ValidationError "缺少文件：$Path"
  }
}

function Assert-ContainsAll {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string[]]$Terms
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return
  }

  $content = Read-Utf8File -Path $Path
  foreach ($term in $Terms) {
    if (-not $content.Contains($term)) {
      Add-ValidationError "$Path 缺少：$term"
    }
  }
}

function Assert-NotMatch {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string[]]$Patterns
  )

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    return
  }

  $content = Read-Utf8File -Path $Path
  foreach ($pattern in $Patterns) {
    if ($content -match $pattern) {
      Add-ValidationError "$Path 仍匹配禁用模式：$pattern"
    }
  }
}

$paths = @{
  Agents = Join-Path $projectRoot 'AGENTS.md'
  Rules = Join-Path $projectRoot 'RULES.md'
  Readme = Join-Path $projectRoot 'README.md'
  Architecture = Join-Path $docsRoot 'ARCHITECTURE.md'
  ApiContract = Join-Path $docsRoot 'API_CONTRACT.md'
  BackendGuide = Join-Path $docsRoot 'BACKEND_GUIDE.md'
  FrontendGuide = Join-Path $docsRoot 'FRONTEND_GUIDE.md'
  BackendStandards = Join-Path $docsRoot 'BACKEND_CODING_STANDARDS.md'
  FrontendStandards = Join-Path $docsRoot 'FRONTEND_CODING_STANDARDS.md'
  AiRules = Join-Path $docsRoot 'AI_CODING_RULES.md'
  DocumentMap = Join-Path $docsRoot 'DOCUMENT_MAP.md'
  LegacyStandards = Join-Path $docsRoot 'CODING_STANDARDS.md'
}

foreach ($path in $paths.Values) {
  if ($path -ne $paths.LegacyStandards) {
    Assert-FileExists -Path $path
  }
}

if (Test-Path -LiteralPath $paths.LegacyStandards) {
  Add-ValidationError "旧规范仍存在：$($paths.LegacyStandards)"
}

$ruleShape = @('【强制】', '【推荐】', '【参考】', '正例：', '反例：', '检查方式：')

Assert-ContainsAll -Path $paths.BackendStandards -Terms ($ruleShape + @(
  '## 1. Java 基础、命名和代码格式',
  '## 2. 面向对象、集合、日期和精度',
  '## 3. 异常、日志和敏感数据',
  '## 4. Controller、Service、Mapper 分层',
  '## 5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus',
  '## 6. MyBatis-Plus、MPJ、查询、分页和排序',
  '## 7. 事务、并发、幂等和外部副作用',
  '## 8. Sa-Token、权限码、数据归属和数据权限',
  '## 9. Redis、Spring Cache、Redisson 和 Lock4j',
  '## 10. SnailJob、事件和异步任务',
  '## 11. 文件、OSS、导入和导出',
  '## 12. Spring AI、Snail AI 和外部服务适配',
  '## 13. 配置、密钥和双启动应用边界',
  '## 14. 单元、Web、数据访问和集成测试',
  'Java 21',
  'Spring Boot 4.1.0',
  'RuoYi-Vue-Plus 6.0.0-BETA',
  'BaseEntity',
  'R.ok(String)',
  'PageResult.build()',
  'PageQuery(Integer pageSize, Integer pageNum)',
  'orderByColumn',
  '@DataPermission',
  'clientAccessPath',
  'clientIpWhitelist',
  'excludeParamNames',
  'ruoyi-admin',
  'ai-video-user-api',
  '/api/snail/chat/**'
))

Assert-ContainsAll -Path $paths.FrontendStandards -Terms ($ruleShape + @(
  '## 1. TypeScript 类型、命名和模块边界',
  '## 2. React 组件、Props 和组合设计',
  '## 3. Hooks、副作用和闭包安全',
  '## 4. 本地状态、服务端状态和请求管理',
  '## 5. Ant Design 与 ProComponents 组件选型',
  '## 6. 表单、表格、弹窗、抽屉和反馈',
  '## 7. 路由、菜单、前端权限和国际化',
  '## 8. 样式、主题、响应式和无障碍',
  '## 9. RuoYi API 前端使用边界',
  '## 10. 性能、错误边界和测试',
  'TypeScript 6',
  'TypeScript 7',
  'React 19',
  'Umi Max 4',
  'Ant Design 6',
  'ProComponents 3',
  'React Query 5',
  'Biome 2',
  'Vitest 4',
  'Oxfmt',
  'Oxlint',
  'ai-video-webapp',
  'ai-video-platform-ui',
  'src/services',
  'passWithNoTests',
  '/api/snail/chat/**'
))

Assert-ContainsAll -Path $paths.ApiContract -Terms @(
  '"rows"',
  '"total"',
  'orderByColumn',
  'isAsc',
  'page.rows ?? []',
  'Authorization',
  'clientid',
  'content-language',
  '当前国际化语言',
  '携带 `code`/`msg` 的标准业务异常',
  'clientAccessPath',
  'clientIpWhitelist',
  'BigDecimal',
  'Blob',
  'SSE',
  '/api/snail/chat/**'
)

Assert-NotMatch -Path $paths.ApiContract -Patterns @(
  '"records"\s*:',
  'data\.records',
  '禁止返回\s*`?rows`?',
  '\bsortField\b',
  '\bsortOrder\b'
)
Assert-NotMatch -Path $paths.ApiContract -Patterns @(
  'PageResult\.build\(',
  'R\.ok\(String\)'
)

$navigationFiles = @(
  $paths.Agents,
  $paths.Rules,
  $paths.Readme,
  $paths.DocumentMap
)

foreach ($path in $navigationFiles) {
  Assert-ContainsAll -Path $path -Terms @(
    'docs/BACKEND_CODING_STANDARDS.md',
    'docs/FRONTEND_CODING_STANDARDS.md'
  )
}

Assert-ContainsAll -Path $paths.BackendGuide -Terms @(
  'BACKEND_CODING_STANDARDS.md',
  '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.FrontendGuide -Terms @(
  'FRONTEND_CODING_STANDARDS.md',
  'API_CONTRACT.md'
)
Assert-NotMatch -Path $paths.BackendGuide -Patterns @(
  'R<PageResult',
  'PageResult\.build\(',
  'PageQuery\(',
  '\borderByColumn\b',
  '@DataPermission',
  '@Transactional',
  '\bclientAccessPath\b',
  '\bclientIpWhitelist\b',
  '\bexcludeParamNames\b'
)
Assert-NotMatch -Path $paths.FrontendGuide -Patterns @(
  'R<PageResult',
  'data\.records',
  'data\.rows',
  '\bpageNum\b',
  '\bpageSize\b',
  '\borderByColumn\b',
  '\bisAsc\b',
  '\bclientid\b',
  'content-language',
  '\bBigDecimal\b',
  'antd\s+(info|doc|demo|token|semantic|lint|doctor)\b',
  'ProTable\s*/\s*ProForm\s*约定',
  '通用组件与状态',
  '枚举、类型和 API',
  '状态管理',
  'Ant Design AI 辅助'
)
Assert-ContainsAll -Path $paths.AiRules -Terms @(
  'docs/BACKEND_CODING_STANDARDS.md',
  'docs/FRONTEND_CODING_STANDARDS.md',
  'scripts/validate-development-standards.ps1',
  '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.Agents -Terms @(
  'docs/BACKEND_CODING_STANDARDS.md',
  'docs/FRONTEND_CODING_STANDARDS.md',
  'scripts/validate-development-standards.ps1',
  '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.Architecture -Terms @(
  'BACKEND_CODING_STANDARDS.md',
  'FRONTEND_CODING_STANDARDS.md'
)

$activeRootPaths = @(
  $paths.Agents
  $paths.Rules
  $paths.Readme
)
$activeRootFiles = $activeRootPaths |
  ForEach-Object { Get-Item -LiteralPath $_ }
$activeDocs = Get-ChildItem -LiteralPath $docsRoot -Recurse -Filter '*.md' -File |
  Where-Object {
    $_.FullName -notmatch '[\\/]docs[\\/]superpowers[\\/](specs|plans)[\\/]'
  }
$activeFiles = @($activeRootFiles) + @($activeDocs)

foreach ($file in $activeFiles) {
  $content = Read-Utf8File -Path $file.FullName
  if ($content -match '(?<![A-Za-z0-9_])(?:docs/)?CODING_STANDARDS\.md') {
    Add-ValidationError "活动文档仍引用旧规范：$($file.FullName)"
  }
}

$linkPattern = '(?<!\!)\[[^\]]+\]\((?<target>[^)]+)\)'
foreach ($file in $activeFiles) {
  $content = Read-Utf8File -Path $file.FullName

  foreach ($match in [regex]::Matches($content, $linkPattern)) {
    $target = $match.Groups['target'].Value.Trim().Trim('<', '>')

    if ($target -match '^(https?://|mailto:|file:|#)') {
      continue
    }

    $pathPart = ($target -split '#', 2)[0]
    if ([string]::IsNullOrWhiteSpace($pathPart)) {
      continue
    }

    try {
      $resolved = [System.IO.Path]::GetFullPath(
        (Join-Path $file.DirectoryName $pathPart)
      )
      if (-not (Test-Path -LiteralPath $resolved)) {
        Add-ValidationError "本地链接不存在：$($file.FullName) -> $target"
      }
    }
    catch {
      Add-ValidationError "本地链接无效：$($file.FullName) -> $target"
    }
  }
}

if ($errors.Count -gt 0) {
  foreach ($validationError in $errors) {
    [Console]::Error.WriteLine("ERROR: $validationError")
  }
  exit 1
}

Write-Output 'DEVELOPMENT_STANDARDS_OK'
```

- [ ] **步骤 5：更新入口后运行验证并确认旧文件阻断**

运行：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\validate-development-standards.ps1
```

预期：退出码 1，错误至少包含：

```text
旧规范仍存在
```

如果还报告导航缺项或本地链接错误，先修正这些问题；不要通过放宽脚本掩盖错误。

- [ ] **步骤 6：删除旧混合规范**

先运行活动引用检查：

```powershell
$hits = rg -n 'docs/CODING_STANDARDS\.md' `
  AGENTS.md RULES.md README.md docs `
  --glob '*.md' `
  --glob '!docs/superpowers/specs/**' `
  --glob '!docs/superpowers/plans/**'

if ($LASTEXITCODE -eq 0) {
  $hits
  throw '活动文档仍引用旧规范，禁止删除'
}
if ($LASTEXITCODE -gt 1) {
  exit $LASTEXITCODE
}
```

预期：无命中，`rg` 退出码 1。

删除精确文件：

```powershell
git rm -- docs/CODING_STANDARDS.md
```

不得保留重定向空壳，也不得改写历史设计稿和计划中的迁移说明。

- [ ] **步骤 7：运行完整文档验证**

运行：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\validate-development-standards.ps1
```

预期：

```text
DEVELOPMENT_STANDARDS_OK
```

退出码 0。

- [ ] **步骤 8：验证变更范围、空白和用户改动隔离**

运行：

```powershell
$unexpected = git diff --name-only -- . |
  Where-Object {
    $_ -notmatch '^(AGENTS\.md|RULES\.md|README\.md|docs/.+\.md|scripts/validate-development-standards\.ps1)$'
  }

if ($unexpected) {
  $unexpected
  throw '发现超出文档建设范围的文件'
}

git diff --check
git status --short
```

预期：

- `$unexpected` 为空。
- `git diff --check` 无输出。
- 状态只包含任务 5 的入口文档、验证脚本和旧文件删除。
- 原工作树的用户 `AGENTS.md` 改动仍位于原工作树，未进入专用工作树提交。

- [ ] **步骤 9：提交入口迁移和旧规范删除**

运行：

```powershell
git add -- `
  AGENTS.md `
  RULES.md `
  README.md `
  docs/DOCUMENT_MAP.md `
  docs/AI_CODING_RULES.md `
  docs/ARCHITECTURE.md `
  scripts/validate-development-standards.ps1

git diff --cached --check
git diff --cached --name-status

$expectedStaged = @(
  'AGENTS.md',
  'README.md',
  'RULES.md',
  'docs/AI_CODING_RULES.md',
  'docs/ARCHITECTURE.md',
  'docs/CODING_STANDARDS.md',
  'docs/DOCUMENT_MAP.md',
  'scripts/validate-development-standards.ps1'
) | Sort-Object
$actualStaged = @(git diff --cached --name-only) | Sort-Object
$stagedDelta = Compare-Object $expectedStaged $actualStaged

if ($stagedDelta) {
  $stagedDelta
  throw '暂存文件列表与任务 5 预期不一致'
}

git commit -m "docs: migrate development standards navigation"
```

预期暂存列表精确包含：

- `AGENTS.md`
- `RULES.md`
- `README.md`
- `docs/DOCUMENT_MAP.md`
- `docs/AI_CODING_RULES.md`
- `docs/ARCHITECTURE.md`
- `scripts/validate-development-standards.ps1`
- `docs/CODING_STANDARDS.md` 删除

提交成功后运行：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\validate-development-standards.ps1

$unexpectedCommitted = git diff --name-only c712f0644..HEAD |
  Where-Object {
    $_ -notmatch '^(AGENTS\.md|RULES\.md|README\.md|docs/.+\.md|scripts/validate-development-standards\.ps1)$'
  }
if ($unexpectedCommitted) {
  $unexpectedCommitted
  throw '完整实施范围包含非文档或非验证脚本文件'
}

git status --short
git log --oneline -5
```

预期：

- 输出 `DEVELOPMENT_STANDARDS_OK`。
- 从已批准设计提交 `c712f0644` 到当前 HEAD 的变更全部位于计划允许的文档和验证脚本范围。
- 专用工作树状态干净。
- 最近五个提交依次覆盖后端手册、前端手册、API 契约、两个 Guide 和入口迁移。

---

## 最终验收映射

| 设计要求 | 对应任务 |
| --- | --- |
| 前后端编码规范拆分 | 任务 1、任务 2 |
| 阿里手册式规则等级、正反例和检查方式 | 任务 1、任务 2 |
| RuoYi 6.X `rows/total`、Header、错误与精度 | 任务 3 |
| Guide 与 Coding Standards 职责分离 | 任务 4 |
| AGENTS、RULES、README、文档地图和 AI 入口 | 任务 5 |
| 删除旧 `CODING_STANDARDS.md` | 任务 5 |
| 保留历史设计/计划迁移记录 | 任务 5 的验证范围 |
| 不修改业务代码、POM、前端配置和 CI | 每个任务的显式暂存与最终范围检查 |
| 用户原有 AGENTS CLI 改动不进入提交 | 执行前提与任务 5 |

## 本计划之外的独立交付

文档建设验收后，如需落地工程能力，分别生成以下独立计划：

1. React 前端 RuoYi API adapter 与契约测试。
2. 前端 Biome、Vitest、Ant Design CLI、React Doctor 和覆盖率门禁。
3. 后端 Maven 测试启用、静态检查、ArchUnit、JaCoCo 和集成测试门禁。

这些工程改造不得追加到本计划的提交中。
