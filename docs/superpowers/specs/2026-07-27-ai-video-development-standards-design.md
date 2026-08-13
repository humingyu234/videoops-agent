# AI Video 前后端开发规范设计

> 状态：已确认
> 日期：2026-07-27
> 适用项目：`D:\Workspace\ai\projects\ai-video`

## 1. 背景

项目现有文档已经覆盖部分架构、接口契约和开发流程，但尚未形成类似《阿里巴巴 Java 开发手册》的系统化编码规范。现有内容还存在以下问题：

- 前后端编码约束混合在同一份 `CODING_STANDARDS.md` 中，不利于按技术栈加载和执行。
- 部分规则是通用 Spring Boot/JPA 假设，与项目实际的 RuoYi-Vue-Plus 6.X、MyBatis-Plus 和 Sa-Token 不一致。
- 前端仍保留 Ant Design Pro 演示接口协议，没有真正落地 RuoYi `R<T>` 适配。
- `API_CONTRACT.md` 将分页写成 `records`，与 RuoYi 6.X 实际的 `rows` 不一致。
- 前后端已经具备若干检查工具，但仍存在测试跳过、零测试通过、格式漏检等“假绿灯”。

本设计用于确定规范体系、规则来源、文档边界、RuoYi API 适配和质量门禁。设计确认后再另行编写实施计划，不在本阶段修改生产代码或构建配置。

## 2. 项目事实基线

### 2.1 后端

- Java 21。
- Spring Boot 4.1.0。
- RuoYi-Vue-Plus 6.0.0-BETA。
- MyBatis-Plus 3.5.16、MyBatis-Plus-Join 1.5.7。
- Sa-Token 1.45.0。
- MapStruct Plus、Redisson、Lock4j、SnailJob、Spring AI/Snail AI。
- 启动应用包括 `ruoyi-admin` 和 `ai-video-user-api`。
- 主业务不是 JPA，也不使用 Spring Security 作为主应用认证框架。
- 当前仓库没有 RocketMQ 业务基线。

后端规范必须以实际代码和项目内的 `ruoyi-plus-ai-coding` skill 为 RuoYi 约定来源，不能把通用 Spring Boot 示例强行套入项目。

### 2.2 前端

- Node.js 22 及以上。
- TypeScript 7。
- React 19。
- Umi Max 4。
- Ant Design 6。
- Ant Design ProComponents 3。
- TanStack React Query 5。
- Biome 2。
- Vitest 4。
- 已安装 Ant Design CLI。

前端规范以 React、TypeScript、Ant Design、Umi Max 和 ProComponents 官方约定为框架基线，以项目实际配置为落地边界。

## 3. 目标与非目标

### 3.1 目标

1. 建立两份互相独立的前后端编码手册。
2. 采用 `【强制】`、`【推荐】`、`【参考】` 的规则等级。
3. 关键规则提供正例、反例、说明和可执行检查方式。
4. 让开发人员和 Codex 使用同一套项目事实。
5. 明确 RuoYi 6.X 与 Ant Design Pro 之间的 API 适配边界。
6. 把可机械验证的强制规则落实为自动化质量门禁。
7. 修正文档与真实源码之间已经发现的矛盾。

### 3.2 非目标

- 本阶段不修改业务功能。
- 本阶段不直接重构现有前后端代码。
- 不把项目迁移到 JPA、Spring Security 或其他框架。
- 不照搬某一份外部规范的全部条款。
- 不要求一次性修复全部存量质量问题。
- 不把 Ant Design CLI、Alibaba P3C 或任一单一工具当作完整规范。

## 4. 规范体系与文档边界

### 4.1 文档拆分

前后端编码规范不得继续放在同一个 `CODING_STANDARDS.md` 中。

| 文件 | 职责 |
| --- | --- |
| `docs/BACKEND_CODING_STANDARDS.md` | Java 21、Spring Boot、RuoYi-Vue-Plus 6.X 后端编码规范 |
| `docs/FRONTEND_CODING_STANDARDS.md` | TypeScript、React 19、Ant Design Pro 前端编码规范 |
| `docs/BACKEND_GUIDE.md` | 后端模块结构、开发流程和脚手架使用说明 |
| `docs/FRONTEND_GUIDE.md` | 前端目录结构、页面开发流程和组件选型说明 |
| `docs/API_CONTRACT.md` | HTTP、分页、认证、错误、ID、金额、文件和流式响应契约 |
| `docs/AI_CODING_RULES.md` | Codex 修改范围、测试、验证和交付要求 |
| `docs/DOCUMENT_MAP.md` | 文档导航和按任务阅读路径 |
| `AGENTS.md` | 最小化规范入口、规则优先级和必须执行的检查 |

原 `docs/CODING_STANDARDS.md` 不再承载正文。实施时先迁移有效内容、更新所有引用，再删除旧文件，避免形成第三个编码规范入口。

### 4.2 Guide 与 Coding Standards 的区别

- Guide 回答“项目如何组织、如何完成一次开发”。
- Coding Standards 回答“代码必须或不应如何编写”。
- API Contract 回答“前后端在线上如何交换数据”。
- AI Coding Rules 回答“Codex 修改和交付时必须执行什么流程”。

同一规则只保留一个权威定义，其他文档通过链接引用，避免复制后产生漂移。

## 5. 规则来源与冲突优先级

规则发生冲突时按以下顺序处理：

1. 安全、数据正确性和法规要求。
2. 项目明确的领域模型、API 契约和业务约束。
3. 当前模块相邻代码、项目公共模块和已验证的运行行为。
4. RuoYi-Vue-Plus 6.X 项目约定和 `ruoyi-plus-ai-coding` skill。
5. React、Ant Design、Umi、TypeScript 等官方规则。
6. 阿里、Google 等通用编码规范。
7. 工具默认值和个人偏好。

应用示例：

- 阿里 Java 规范提供通用语言和工程规则，但不能要求本项目改用 JPA。
- RuoYi 约定决定 `Entity/BO/VO`、`R<T>`、`PageResult<T>`、`BaseMapperPlus` 等框架形态。
- Ant Design 官方规则决定组件 API 和推荐组合。
- 项目 API 契约决定 React 前端如何处理 `code/msg/data`。
- 文档与 RuoYi 6.X 实际源码冲突时，先以源码核实并修正文档。

## 6. 规则表达格式

### 6.1 规则等级

- `【强制】`：违反后可能造成正确性、安全、架构或长期维护问题，必须遵守。
- `【推荐】`：原则上应遵守；偏离时需要说明原因和替代措施。
- `【参考】`：经验性建议，由具体场景决定。

### 6.2 标准条目模板

```md
### 4.3 Controller 返回值

【强制】普通 JSON Controller 必须返回项目统一返回体，不得直接返回 Entity。

正例：
public R<UserVo> get(Long id) {
    return R.ok(userService.queryById(id));
}

反例：
public UserEntity get(Long id) {
    return userMapper.selectById(id);
}

说明：
统一返回体用于稳定错误码、消息和数据结构；Entity 不得跨越 HTTP 边界。

检查方式：
代码审查；ArchUnit；Controller 测试。
```

关键强制规则原则上应包含正例、反例、说明和检查方式。纯命名表或无需反例的规则可以省略不适用字段。

## 7. 后端编码规范设计

`BACKEND_CODING_STANDARDS.md` 采用以下章节：

1. Java 基础、命名和代码格式。
2. 面向对象、集合、日期和精度。
3. 异常、日志和敏感数据。
4. Controller、Service、Mapper 分层。
5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus。
6. MyBatis-Plus、MPJ、查询、分页和排序。
7. 事务、并发、幂等和外部副作用。
8. Sa-Token、权限码、数据归属和数据权限。
9. Redis、Spring Cache、Redisson 和 Lock4j。
10. SnailJob、事件和异步任务。
11. 文件、OSS、导入和导出。
12. Spring AI、Snail AI 和外部服务适配。
13. 配置、密钥和双启动应用边界。
14. 单元、Web、数据访问和集成测试。

### 7.1 RuoYi 分层基线

- Entity 映射数据库模型，不作为 Controller 入参或出参。
- BO 表示业务请求和服务命令。
- VO 表示接口响应。
- AI 视频业务专属的跨模块稳定契约以 `*DTO` 放在 `ai-video-core` 对应业务聚合的平级 `dto` 包，不放入全局 `ruoyi-api`。
- Mapper 负责数据访问和 SQL 级数据域过滤，不承载业务状态决策。
- Service 负责业务编排、资源归属、状态流转、事务、幂等和领域异常。
- Controller 负责 HTTP 参数、Jakarta Validation、登录主体、权限声明和统一返回。

简单 CRUD 优先沿用项目现有命名和基础设施：

- `queryById`
- `queryPageList`
- `queryList`
- `insertByBo`
- `updateByBo`
- `deleteWithValidByIds`
- `BaseMapperPlus`
- `QueryBuilder`
- `MapstructUtils`

### 7.2 后端红线

- 普通分页接口必须使用 `R<PageResult<Vo>>`。
- 禁止返回裸 `Page/IPage`、Entity 或重复包装的 `R<R<T>>`。
- 字符串数据禁止使用 `R.ok(string)`，因为该重载会把字符串当作消息；应使用 `R.data(value)` 或 `R.ok(msg, value)`。
- `PageResult<T>` 的字段是 `total` 和 `rows`。
- HTTP 分页接口禁止使用会产生 `rows=null` 的无参 `PageResult.build()`；空页必须稳定返回 `rows=[]`。
- `PageQuery` 必须限制最大 `pageSize`。
- 动态排序必须使用业务字段白名单，不能只依赖字符过滤。
- `PageQuery(Integer pageSize, Integer pageNum)` 参数顺序容易误用，规范中必须明确提示。
- 权限码不能替代资源归属检查。
- `@DataPermission` 是 Mapper 层合法的 SQL 行级数据域机制，不能被“Mapper 不处理业务权限”规则误伤。
- `DataPermissionHelper.ignore`、`@SaIgnore` 和 `security.excludes` 属于高风险改动。
- Sa-Token 客户端访问控制还包括 Token 绑定的 `clientAccessPath` 和 `clientIpWhitelist`；新代码不得绕过 `SecurityConfig` 的客户端路径与 IP 白名单检查。
- 新接口必须明确在 `ruoyi-admin`、`ai-video-user-api` 中的暴露边界。
- 事务内禁止直接执行无法回滚的外部副作用；需要事务后事件或补偿设计。
- SnailJob 和外部回调必须按可能重复执行设计幂等。
- 共享开发地址、账号、密码、Token 和密钥统一写入并提交在两端 `application-dev.yml`；这些值不得进入日志、响应、异常或测试快照。
- 项目 `@Log` 默认可能同时记录请求和响应，而全局排除项不覆盖 Token、`clientSecret`、`secretKey` 和 `accessKey`；敏感接口必须配置 `excludeParamNames`、使用现有注解开关关闭请求或响应记录，并禁止返回或记录含密钥的 VO。
- 文件上传必须检查大小、扩展名、MIME、文件魔数、权限和恶意内容风险。
- 大文件下载必须流式处理，不能完整加载进内存。

### 7.3 明确例外

- 只有 `/api/snail/chat/**` 的 Snail AI Chat SDK 专用协议可以保持 SDK 自身的 `Result(status/message/data)`；其他 Snail AI 普通 JSON API 仍使用 `R<T>`。
- SSE、文件 Blob 使用专用适配器；第三方回调只有在上游协议明确强制响应格式时才允许例外。
- 例外必须按端点明确列出，不能用例外弱化普通 JSON API 的统一约束。

## 8. 前端编码规范设计

`FRONTEND_CODING_STANDARDS.md` 采用以下章节：

1. TypeScript 类型、命名和模块边界。
2. React 组件、Props 和组合设计。
3. Hooks、副作用和闭包安全。
4. 本地状态、服务端状态和请求管理。
5. Ant Design 与 ProComponents 组件选型。
6. 表单、表格、弹窗、抽屉和反馈。
7. 路由、菜单、前端权限和国际化。
8. 样式、主题、响应式和无障碍。
9. RuoYi API 前端使用边界。
10. 性能、错误边界和测试。

### 8.1 TypeScript 与 React 基线

- 保持 `strict` 和 `noImplicitReturns`。
- 业务代码禁止无理由使用 `any`、非空断言和双重类型断言。
- 组件保持单一职责，复杂页面拆分为容器、展示组件和领域 Hook。
- 派生数据优先在渲染阶段计算，不能用 Effect 同步可计算状态。
- Effect 只用于与外部系统同步，并完整声明依赖。
- 禁止在条件、循环和普通函数中调用 Hook。
- 服务端状态优先由 React Query 或 Umi 请求层管理，不复制为多份本地状态。
- 加载、空状态、失败、无权限和取消请求必须有确定行为。

### 8.2 Ant Design Pro 基线

- 优先使用 Ant Design 和 ProComponents，避免重复实现已有交互模式。
- ProTable 的 `request` 只接收前端适配后的标准结果。
- 表单提交必须防重复；服务端校验错误应映射到字段或统一提示。
- Modal/Drawer 的打开、提交、关闭和销毁状态必须可预测。
- 破坏性操作必须二次确认，并在成功后按策略刷新缓存或表格。
- 不通过 DOM 查询或全局 CSS 强行控制组件内部状态。
- 前端权限只用于界面展示，不能代替后端授权。
- Ant Design CLI 用于发现组件兼容、废弃 API、可访问性和使用问题，但不能替代类型检查、测试和构建。

### 8.3 生成代码边界

- OpenAPI 或 Ant Design Pro 演示生成代码放在明确目录。
- 生成目录允许单独配置检查例外。
- 手写的 RuoYi 适配器和业务 Service 必须纳入 Biome、TypeScript 和测试。
- 禁止把整个 `src/services` 排除在质量检查之外。

## 9. RuoYi API 适配设计

RuoYi 适配层负责把后端传输协议转换成前端领域类型和 ProComponents 所需结构。页面不解析 `code/msg/data`，也不直接处理 Token。

### 9.1 基础类型

```ts
export interface RuoYiResponse<T> {
  code: number;
  msg: string;
  data: T | null;
}

export interface RuoYiPageResult<T> {
  total: number;
  /**
   * 兼容存量 PageResult.build() 可能产生的 null；
   * 目标 HTTP 契约要求后端稳定返回 []。
   */
  rows: T[] | null;
}
```

真实分页 JSON：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "total": 123,
    "rows": []
  }
}
```

现有 `API_CONTRACT.md` 中的 `records`、响应内 `pageNum/pageSize` 必须修正。

### 9.2 请求头

登录后请求集中添加：

```http
Authorization: <access_token>
clientid: <configured-client-id>
content-language: zh-CN
```

- Token 默认按登录结果原值写入，除非后端配置明确要求前缀。
- `clientid` 必须与登录和 Token 中记录的客户端一致。
- 后端还会校验 Token 绑定的客户端允许路径和 IP 白名单；这两项是服务端访问控制，前端不得尝试绕过或替代。
- 语言头跟随当前国际化状态。
- 页面和模块 Service 不重复拼接请求头。

### 9.3 成功和错误

- HTTP 状态码和 `R<T>.code` 是两条错误通道，适配层必须归一化处理并避免重复提示。
- HTTP 为成功状态且 `code === 200`：业务成功。
- HTTP 401 或 `code === 401`：清理登录态并进行一次性登录跳转。
- HTTP 403 或 `code === 403`：显示无权限，不盲目跳转登录页。
- 其他业务码：抛出包含 `code/msg` 的标准业务异常。
- 网络错误、超时、取消请求和 HTTP 5xx 与业务异常分开建模。
- 禁止根据中文 `msg` 判断业务流程。

### 9.4 ProTable 分页转换

请求映射：

| Ant Design Pro | RuoYi |
| --- | --- |
| `current` | `pageNum` |
| `pageSize` | `pageSize` |
| 排序列键（经显式白名单映射） | `orderByColumn` |
| `ascend` | `isAsc=asc` |
| `descend` | `isAsc=desc` |

前端必须维护“ProTable 列键 → 后端允许排序字段”的显式映射，未知列键不得透传；后端仍须独立校验排序白名单，不能信任前端映射。

前端适配器必须限制允许选择和发送的 `pageSize`；后端继续独立设置最大页大小，不能依赖前端限制。

响应映射：

```ts
function toProTableResult<T>(page: RuoYiPageResult<T>) {
  return {
    data: page.rows ?? [],
    total: page.total,
    success: true,
  };
}
```

分页和排序转换必须集中复用，页面不得自行重复实现。

### 9.5 ID 与精度

- 业务 ID 默认使用 `string`。
- 边界接收 `string | number` 时立即规范化为字符串。
- 禁止对业务 ID 使用 `Number`、`parseInt` 或算术运算。
- `BigDecimal`、金额、额度和其他精度值按字符串建模。
- 需要客户端计算时使用十进制库，或由后端完成计算。

### 9.6 专用响应

- 文件下载使用 Blob 适配器。
- SSE 使用流式适配器。
- `/api/snail/chat/**` 的 Snail AI SDK 专用响应使用独立适配器；其他 Snail AI 普通 JSON API 不自动获得例外。
- 专用响应必须显式声明，不能让全局拦截器错误地按 `R<T>` 解包。

### 9.7 当前前端差距

- `app.tsx` 仍指向 Ant Design Pro 演示 API。
- `requestErrorConfig.ts` 仍使用 `success/errorCode/errorMessage/showType` 演示协议。
- Token 拦截逻辑尚未启用。
- 当前代码尚未统一处理 `code/msg/data`、`clientid` 和 `rows/total`。

实施适配时必须先补契约测试，再替换演示协议。

## 10. 自动化质量门禁

### 10.1 当前状态

前端：

- TypeScript 检查通过。
- Vitest 实际执行 5 个测试文件、36 个测试；另有 1 个登录测试文件被配置排除。
- Biome 当前存在 36 个错误、50 个警告和 12 个提示，不能宣称基线全绿。
- CI 的 `biome lint` 不校验格式。
- `src/services` 被整体排除。
- Biome 当前关闭了 `noExplicitAny`、`useExhaustiveDependencies` 和部分无障碍规则，因此书面规则尚未全部成为自动化门禁。
- Vitest 设置 `passWithNoTests: true`，没有覆盖率阈值。
- CI 混用了 `npm` 和 `ut`。
- React Doctor 本地依赖为 0.7.8，现有 GitHub Action 固定为 0.7.1，存在版本漂移。

后端：

- 根 POM 设置 `maven.test.skip=true`。
- 普通 `test/package/verify` 可能没有执行测试。
- 仅有两个启动模块中重复的 8 个 JUnit 教学测试。
- Surefire 测试标签与 `local/dev/prod` 部署环境耦合。
- 尚未配置 Enforcer、格式检查、Checkstyle、SpotBugs、ArchUnit、Failsafe 和 JaCoCo。

### 10.2 提交前

前端保留快速钩子：

```text
lint-staged → Biome 修复暂存文件 → commitlint
```

不在 pre-commit 中运行全量构建。

后端不强制设置全量 Maven Git Hook，开发者按受影响模块运行编译和定向测试。

### 10.3 PR 强制门禁

前端目标流水线：

```text
npm ci
→ Biome 无写入检查
→ tsc --noEmit
→ Vitest
→ Umi 生产构建
→ antd doctor
→ antd lint
→ React Doctor
```

- 新增无写入的 `biome:check`。
- `antd doctor` 和 `antd lint` 为阻断项。
- `antd usage` 只输出统计报告。
- React Doctor 使用锁文件中的本地 `npm run doctor`；现有 Action 必须与本地版本和配置同步，清理存量基线后再转为阻断项。
- 对手写代码重新启用或等价落实 `noExplicitAny`、Hook 依赖和无障碍检查；暂时无法由 Biome 覆盖的条款必须明确由 Ant Design CLI、React Doctor、测试或代码审查中的哪一项负责。
- 规则级 suppression 必须包含原因并限制到最小范围，不能继续通过全局关闭制造门禁假象。
- 移除 `passWithNoTests`。
- 统一使用 `package-lock.json` 和 `npm ci`。

后端目标入口：

```powershell
.\mvnw.cmd -B -ntp -Pci verify
```

实现前必须：

- 取消默认跳过测试。
- 取消用部署环境筛选测试。
- 测试标签改为 `unit/integration/slow`。
- CI 只使用 Maven Wrapper。

目标 `verify` 包含：

- Maven Enforcer。
- Spotless 格式和 import 顺序检查。
- Checkstyle 项目编码规则。
- SpotBugs 缺陷检查。
- Surefire 单元测试。
- ArchUnit 分层和暴露边界测试。
- JaCoCo 覆盖率采集。
- Failsafe 集成测试。

Alibaba P3C 中适用于本项目的规则转换成项目 Checkstyle/PMD 规则；不直接把兼容性未经验证的旧插件设置为 Java 21 硬门禁。

### 10.4 覆盖率

1. 第一阶段只采集真实基线，不宣称覆盖率已经成为阻断门禁。
2. 前端使用 Vitest V8 生成机器可读报告并由 CI 保存或上传；后端使用 JaCoCo 生成报告。
3. 第二阶段接入能够判断新增代码覆盖率的 Codecov、SonarQube 或等价工具，再将新增或修改业务代码的行覆盖率 80%、分支覆盖率 70% 设置为 required check。
4. 当前 Codecov 的 informational 状态必须在基线确认、排除项审查和阈值启用后才能改为合并阻断。
5. 存量全局覆盖率逐步提升。
6. 权限、数据归属、状态流转、事务和幂等逻辑必须覆盖成功、失败和越权路径。
7. 生成代码和纯声明可以明确排除，不允许通过排除手写业务目录制造合格率。

### 10.5 定时和发布

```text
本机 MySQL 8 与 Redis 7 受控集成测试
→ ruoyi-admin 启动 Smoke Test
→ ai-video-user-api 启动 Smoke Test
→ 依赖漏洞扫描
→ 密钥扫描
→ SBOM
→ 发布制品验证
```

发布必须复用已经通过同一提交全部门禁的制品。

### 10.6 存量基线策略

1. 单独完成一次基线清理，不混入业务功能。
2. 前端先修复当前 Biome 错误。
3. 后端先取消测试假绿灯并增加真实业务测试。
4. 静态检查首次接入时可对 RuoYi 存量建立有期限的基线。
5. 基线清理后开启全量阻断。
6. 禁止通过扩大忽略目录或增加宽泛 suppression 绕过检查。

## 11. 实施顺序

后续实施计划应按以下顺序拆分：

1. 新建前后端两份编码规范，迁移 `CODING_STANDARDS.md` 中仍有效的内容。
2. 按真实 RuoYi 6.X 源码修正 `API_CONTRACT.md`。
3. 更新 `BACKEND_GUIDE.md`、`FRONTEND_GUIDE.md` 的引用和职责边界。
4. 更新 `DOCUMENT_MAP.md`、`AGENTS.md` 和 `AI_CODING_RULES.md` 的阅读入口。
5. 删除已无正文和引用的 `CODING_STANDARDS.md`。
6. 单独制定前端 RuoYi API 适配实现计划和契约测试。
7. 单独制定前端存量 Biome 清理与 CI 改造计划。
8. 单独制定后端测试、静态检查和 CI 改造计划。

规范文档建设与构建门禁改造分开提交，避免文档迁移、存量格式修复和业务变更混在同一个变更中。

## 12. 验收标准

文档阶段完成时必须满足：

- 前后端编码规范是两个独立文件。
- 每份规范均使用 `【强制】/【推荐】/【参考】`。
- 高风险强制规则包含正例、反例和检查方式。
- 不再把项目描述为 Spring Boot 3、JPA 主项目或 Spring Security 主认证。
- 不再包含当前项目没有采用的 RocketMQ 强制规则。
- RuoYi 分页在所有文档中统一为 `data.total + data.rows`。
- `R.ok(String)` 重载陷阱、`clientid`、客户端路径/IP 白名单、Sa-Token、数据归属和双启动应用暴露边界有明确规则。
- 前端 ID、BigDecimal、ProTable 分页和异常适配有唯一权威定义。
- `CODING_STANDARDS.md` 的旧引用全部迁移。
- 所有内部 Markdown 链接有效。
- `AGENTS.md` 保持为精简入口，不复制整份规范。
- 文档明确区分当前已存在能力、目标门禁和后续实施项。

## 13. 风险与缓解

### 13.1 规范与上游 RuoYi 漂移

缓解：以当前 6.X 源码和项目 skill 为版本化基线；升级 RuoYi 时同步复查规范。

### 13.2 规则过多导致无人遵守

缓解：强制规则必须可解释、可检查；推荐和参考规则不伪装成硬门禁。

### 13.3 首次启用门禁导致大量失败

缓解：先建立真实基线和专门清理变更，再开启阻断；临时基线必须有范围、原因和清理期限。

### 13.4 生成代码与手写代码混淆

缓解：只按明确生成目录配置例外，手写 Service 和适配器必须检查。

### 13.5 两个启动应用接口边界失控

缓解：规范中要求声明暴露目标，后续使用路由清单或架构测试验证。

### 13.6 文档与工具规则重复

缓解：文档解释意图和边界，工具只负责可机械验证部分；工具配置链接回对应规范条目。

## 14. 自检清单

设计稿和后续规范实施完成后至少执行：

```text
检查所有规范文件链接
检查 CODING_STANDARDS 旧引用
检查 records/rows 分页术语
检查 Spring Boot 3/JPA/Spring Security/RocketMQ 陈旧描述
检查前后端规范是否仍有重复权威定义
检查 AGENTS.md 是否只保留入口与优先级
检查 git diff 只包含本任务文件
```

本设计不授权直接修改业务代码、构建配置或 CI；这些内容需要在设计稿审阅通过后另行编写实施计划。
