---
name: ruoyi-plus-ai-coding
description: 在仓库内按代码生成器模板、项目 reference 文档和既有约定生成或修改代码。用于新增或修改 CRUD 模块、controller/service/mapper/BO/VO/entity、MyBatis-Plus/MPJ 查询、数据权限、缓存、翻译/JSON 增强、公共 common 模块能力、JavaDoc 注释，以及与后端接口配套的 Vue 3 + TypeScript 页面、types 和 api 文件；触发后应先按任务类型读取对应 references，再阅读目标模块真实代码和 generator 模板。
---

# RuoYi Plus AI 编码规范

先对齐代码生成器产物，再叠加仓库里真实业务代码已经形成的更强约定。

本 skill 可以从仓库内任意工作目录触发。执行 Maven、生成器或源码相关命令前，先定位 Git 仓库根目录，再把工作目录切换到 `<repo>/ai-video-api`。下文未带前缀的 `ruoyi-modules/`、`ruoyi-common/`、`src/` 和 Maven 路径都相对于该后端工程；`docs/`、`AGENTS.md` 与 `RULES.md` 从仓库根目录解析。

## 适用场景

在下面这些任务里优先使用此 skill：

- 新增标准 CRUD 模块。
- 根据新表结构补齐 entity、bo、vo、mapper、service、controller。
- 修改已有模块的查询、校验、导入导出、数据权限、事务逻辑。
- 修改 `ruoyi-common` 公共能力，例如 mybatis 查询构造器、translation、json enhance、excel、oss、redis、web 配置。
- 补充或修正 JavaDoc 注释，尤其是公共 API、接口、BO/VO/Entity 字段、Mapper 默认方法、Service/Controller 方法。
- 在系统、监控、工作流、demo 等模块内按现有约定扩展业务代码。
- 为后端新增接口同步补前端 `api/types/index.vue` 骨架。

## 不适用场景

下面这些任务不要机械套用本 skill 的 CRUD 规则：

- 基础框架升级、Spring Boot 主版本迁移。
- 与当前分层明显不同的实验性模块。
- 第三方中间件深度接入、基础设施改造。
- 完全脱离 generator 体系的独立子系统。

## 执行流程

1. 先判断任务类型，并按“文档读取规则”读取当前任务需要的 reference。
2. 确认目标模块，优先复用同模块中最近似功能的写法。
3. 新增标准 CRUD 代码前，先读取 `<repo>/ai-video-api/ruoyi-modules/ruoyi-gen/src/main/resources/fm/` 下的 FreeMarker 模板。
4. 【项目硬约束】命名和分层必须保持与仓库一致：
   业务聚合使用 `domain` entity（实体）、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。共享核心模块没有 HTTP（超文本传输协议）入口时省略 BO、VO 和 Controller；Service 接口命名为 `I...Service`，实现类位于 `service.impl` 并命名为 `...ServiceImpl`。`dto` 只是数据契约包，不是新的业务层。
5. 【项目硬约束】优先在生成器结构上扩展，禁止在业务聚合中以 `application`、`application.impl`、`port`、`adapter`、`command`、`model`、`aggregate`、`repository` 或 CQRS（命令查询职责分离）目录作为平行业务层；不得以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 的贫血 Entity 加 Service 编排。端侧请求归 BO，响应归 VO；AI 视频业务专属的稳定跨模块 Service 数据契约必须放在 `ai-video-core` 对应业务聚合的平级 `dto` 包并使用 `*DTO` 命名，禁止放入全局 `ruoyi-api`。
6. `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 只可承担直接框架或外部集成职责，不能成为另一套业务 Service 层。安全／框架例外必须先在 `docs/DOMAIN_MODEL.md` 与模块规格记录原因、范围、替代控制和回归条件，并取得项目负责人明确确认。
7. 修改 `ruoyi-system` 这类复杂模块前，先阅读同类现有实现，因为这些模块通常比生成器默认产物多出数据权限、联表、缓存、安全校验等逻辑。
8. 修改 `ruoyi-common` 公共模块前，先阅读同包接口、实现类和调用点，优先保持已有 API 语义与兼容性。
9. 只补注释或文档时，不运行无关格式化，不重排 import，不改代码逻辑。

## 文档读取规则

使用本 skill 时，先按任务类型读取适用 reference，不一次性展开所有文档：

- 后端 Java、Mapper、Service、Controller、BO、VO、Entity、权限、查询、公共模块或 JavaDoc 任务，先读 [references/backend.md](references/backend.md)。
- 前端 Vue、TypeScript、api、types 或页面任务，先读 [references/frontend.md](references/frontend.md)。
- 不确定任务边界、需要标准调用方式或需要对照典型场景时，再读 [references/examples.md](references/examples.md)。

reference 用来约束实现方式和自检范围。项目 `AGENTS.md`、`RULES.md` 及其路由的活动规范属于硬约束，不得被遗留代码覆盖；仅在硬约束允许的范围内，才以当前模块真实代码和实际调用点确定实现细节。

## 优先级规则

发生冲突时按下面顺序决策：

1. 项目 `AGENTS.md`、`RULES.md` 及其路由的活动规范和已确认模块规格。
2. 当前模块内符合上述硬约束的最近似业务代码。
3. 当前仓库公共基础模块约定，例如 `common-mybatis`、`common-core`、`common-web`。
4. 代码生成器模板。
5. 通用 Spring Boot / MyBatis-Plus 习惯。

也就是说：

- 同模块已有成熟实现时，优先复用该实现。
- 同模块没有现成代码时，再参考 generator 模板。
- 不要因为“更通用”就覆盖掉项目已形成的强约定。

## 后端规则

Java、MyBatis-Plus、BO/VO/entity、controller、mapper、service 的具体规则见 [references/backend.md](references/backend.md)。

## 前端规则

Vue 3、TypeScript API 文件、生成式列表页、表单状态、字典和日期范围约定见 [references/frontend.md](references/frontend.md)。

## 使用案例

具体调用方式见 [references/examples.md](references/examples.md)。

## 仓库通用规则

- 遵循后端工程 [`.editorconfig`](../../../ai-video-api/.editorconfig)：UTF-8、LF，默认 4 空格，JSON/YAML 为 2 空格。
- 不要把 `BaseMapperPlus`、`PageQuery`、`PageResult`、`R`、`MapstructUtils` 或项目工具类替换成临时自造方案。
- 仓库已使用 `List.of(...)` 的地方，数组转列表优先继续沿用。
- import、注解顺序、文件结构以附近代码为准，不要顺手重排整个文件。
- 只有在业务逻辑不直观时才加简短注释。

## 决策规则

- 如果任务是围绕单表的标准 CRUD，尽量贴近生成器默认产物。
- 如果目标模块已经存在自定义校验、数据权限、事务、缓存、Excel 导入导出、联表查询等逻辑，应在此基础上扩展，不要为了“简洁”把它们削平。
- 如果附近 controller 接口已经带有权限、日志、防重、加密、分组校验等注解，新接口默认同步保持一致，除非有明确理由不这样做。
- 如果 BO 或 VO 需要字段校验、翻译、Excel 注解，应优先参考同模块同用途对象，不要机械套通用注解。
- 如果修改公共基础模块，优先保持公开 API 兼容，新增能力要查调用点和同包风格。
- 如果任务只涉及注释，默认补 JavaDoc 并保持实现不变；框架覆写方法不强行重复注释，除非业务语义不直观。

## 目录映射规则

标准后端模块通常按下面结构组织：

- `src/main/java/.../domain/Entity.java`
- `src/main/java/.../dto/EntityDTO.java`（仅限 `ai-video-core` 稳定跨模块 Service 数据契约）
- `src/main/java/.../domain/bo/EntityBo.java`
- `src/main/java/.../domain/vo/EntityVo.java`
- `src/main/java/.../mapper/EntityMapper.java`
- `src/main/java/.../service/IEntityService.java`
- `src/main/java/.../service/impl/EntityServiceImpl.java`
- `src/main/java/.../controller/EntityController.java`

其中 `dto` 与 `domain`、`mapper`、`service` 平级；共享核心模块无 HTTP 入口时省略 `domain.bo`、`domain.vo`、`controller`。不得从遗留 `application`、`port`、`command`、`model` 路径反推新的目录规则。

标准生成器模板通常对应：

- `fm/java/domain.java.ftl` -> entity
- `fm/java/bo.java.ftl` -> bo
- `fm/java/vo.java.ftl` -> vo
- `fm/java/mapper.java.ftl` -> mapper
- `fm/java/service.java.ftl` -> service interface
- `fm/java/serviceImpl.java.ftl` -> service impl
- `fm/java/controller.java.ftl` -> controller
- `fm/xml/mapper.xml.ftl` -> 自定义 XML mapper 起点

## 任务分型

### 1. 标准单表 CRUD

优先按 generator 模板落骨架，再补校验、权限、导出、翻译等项目约定。

### 2. 强业务模块扩展

如果目标模块像 `system`、`workflow` 一样已经有复杂逻辑，优先增量修改，不要回退成模板式简化代码。

### 3. 基础能力复用

如果涉及数据权限、缓存、事务、导入导出、字典、翻译、加密、分组校验，优先查项目已有做法并复用公共能力。

### 4. 公共基础模块修改

修改 `ruoyi-common` 下的基础能力时，优先保证二进制/API 兼容：不要轻易改公开方法签名、泛型、返回值或异常语义。新增注释和小范围能力时，先查同包现有风格，例如 `common-mybatis` 的链式 wrapper、`common-translation` 的 `TranslationInterface` 实现、`common-json` 的字段处理器。

### 5. 注释修正任务

只要求“加注释/完善注释”时，默认补 JavaDoc，不改实现。优先补公共 API、接口方法、字段含义、复杂私有辅助方法；覆写框架回调方法只有在当前文件已有注释风格或业务语义不直观时才补。

## 输出要求

使用本 skill 时，默认期望产出应满足：

- 后端分层完整，不直接在 controller 里堆业务逻辑。
- `BO/VO/Entity` 职责分明。
- 查询、分页、删除校验、写入校验逻辑闭环完整。
- 权限、日志、防重、事务、数据权限尽量贴近同模块现有实现。
- 如果同步改前端，前端 API 路径和后端接口保持一致。

## 快速检查清单

- 包路径和 `@RequestMapping` 与模块保持一致。
- 权限标识遵循 `${module}:${business}:${action}`。
- Mapper 继承 `BaseMapperPlus<Entity, Vo>`。
- 手写 Service 注入 Mapper 时使用具体业务短名；代码生成器模板按类名首字母小写命名，例如 `SysRoleMapper` 生成 `sysRoleMapper`。
- Service 按场景返回 `PageResult` 或 `List<Vo>`。
- 查询代码优先使用 `LambdaQueryWrapper`，复杂模块沿用既有 MPJ 联表风格。
- 公共 Mapper 链式能力优先沿用 `LambdaCrudChainWrapper`、`LambdaQueryBuilder`、`LambdaQueryCondition` 的 `IfPresent` / `IfText` / `IfNotEmpty` 风格。
- 翻译能力优先沿用 `TranslationInterface` + `@TranslationType` + `@Translation`，批量翻译实现 `translationBatch`，避免退化成逐条查询。
- JSON 响应增强优先沿用 `JsonFieldProcessor` 的 `collect` / `prepare` / `process` 三阶段模型。
- BO 使用 `@AutoMapper(target = Entity.class, reverseConvertGenerate = false)`。
- VO 使用 `@AutoMapper(target = Entity.class)`。
- 前端 API 路径与后端路由完全对应。
- 前端列表页继续使用仓库里的 `proxy?.addDateRange`、`proxy?.$modal`、`proxy?.download`、`useDict`、`pagination` 等工具。

## 推荐提问方式

推荐把任务描述到下面这个粒度：

- 目标模块和业务名
- 是新建模块还是修改已有模块
- 表名或接口前缀
- 是否需要分页、导出、导入、数据权限、字典、翻译、联表
- 希望参考哪个现有模块

例如：

- 使用 `$ruoyi-plus-ai-coding` 在 `system` 模块新增一个标准单表 CRUD，参考 `SysConfig` 与 generator 模板。
- 使用 `$ruoyi-plus-ai-coding` 修改 `workflow/category` 的查询和导出逻辑，保持现有模块风格。
