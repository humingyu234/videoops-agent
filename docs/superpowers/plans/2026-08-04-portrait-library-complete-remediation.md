# 人物形象库完整整改实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `executing-plans` 在当前会话逐任务实现；步骤使用复选框跟踪。用户要求内联快速执行，不创建 commit。

**目标：** 完整修复人物形象库的数据库格式约束、状态分页、创建幂等、删除一致性、HTTP 契约和前端错误状态。

**架构：** 使用向前迁移扩展数据约束和幂等字段；Mapper 联表分页，Service 编排幂等与两阶段删除；用户端 BO/VO 和 React API 类型严格对齐公共契约。

**技术栈：** Java 21、Spring Boot、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、React 19、TypeScript、Ant Design、Vitest。

**规格输入：** `docs/superpowers/specs/2026-08-04-portrait-library-complete-remediation-design.md`

---

### 任务 1：数据库约束与幂等字段

**文件：**
- 创建：`docs/sql/ai-video/mysql/20260804_01_portrait_library_remediation.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/portrait/PortraitMigrationContractTest.java`

- [ ] 编写失败测试：读取新迁移并断言格式约束包含 `jpeg/png/webp/gif`，且存在 `idempotency_key`、`request_digest` 和工作区用户幂等唯一键。
- [ ] 运行 `PortraitMigrationContractTest`，预期因迁移文件不存在而失败。
- [ ] 编写可重复执行、失败关闭的 MySQL 8 向前迁移，验证表、列、检查约束和唯一索引后置条件。
- [ ] 重新运行测试，预期通过。

### 任务 2：状态分页和非 ready 映射

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/dto/PortraitPageRowDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/mapper/PortraitMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/portrait/PortraitMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/service/impl/PortraitServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/portrait/service/impl/PortraitServiceImplTest.java`

- [ ] 先增加失败测试：状态条件必须传入 Mapper 分页查询；`processing/failed` 行可以映射且不签发预览地址；总数来自过滤后的数据库分页。
- [ ] 运行测试，预期旧 `selectPage` 和 ready-only 映射导致失败。
- [ ] 实现联表分页和状态映射；只有 ready 行调用短期地址服务。
- [ ] 重新运行测试，预期通过。

### 任务 3：创建业务幂等

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/domain/Portrait.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/dto/CreatePortraitDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/portrait/domain/bo/CreatePortraitBo.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/portrait/controller/PortraitController.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/service/impl/PortraitServiceImpl.java`
- 修改：对应 Core 和 Controller 测试。

- [ ] 增加失败测试：同键同摘要返回原记录、同键不同摘要返回 `46304`、并发重复键重新查询、事务回滚只清理未引用素材。
- [ ] 运行测试，预期因命令和实体缺少幂等字段而失败。
- [ ] 实现规范化摘要、唯一键冲突处理和安全回滚清理。
- [ ] 重新运行测试，预期通过。

### 任务 4：两阶段幂等删除

**文件：**
- 修改：`IAssetService.java`、`AssetServiceImpl.java`、`PortraitServiceImpl.java`
- 修改：`PortraitServiceImplTest.java`

- [ ] 增加失败测试：OSS 调用时事务未激活；失败写 `delete_failed`；成功后第二事务删除记录；第二事务失败可重试；已删除记录重复请求成功。
- [ ] 运行测试，预期旧实现在单事务内删除 OSS 且重复删除返回不存在。
- [ ] 使用 Spring `TransactionTemplate` 实现准备、外部删除和收口三个阶段，不新增业务分层。
- [ ] 重新运行测试，预期通过。

### 任务 5：HTTP 契约和前端状态

**文件：**
- 创建：用户端 `PortraitListVo.java`、`PortraitDetailVo.java`
- 修改：`PortraitAccessUrlVo.java`、`PortraitController.java` 和 Controller 测试
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/portrait/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/portrait/api.ts`
- 修改：`PortraitLibraryView.tsx` 和测试
- 修改：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、人物形象规格

- [ ] 增加失败测试：响应不含 `assetId`，访问地址包含 `expiresAt/contentType`，403 使用数值错误，详情单独加载，创建重试不重复上传。
- [ ] 运行后端和前端测试，确认因旧契约失败。
- [ ] 实现 BO/VO、DTO、前端类型和页面状态的最小修改，并同步公共文档。
- [ ] 重新运行后端和前端测试，预期通过。

### 任务 6：完整验证

- [ ] 运行人物形象、素材、安全和 Controller Maven 测试，确认 0 失败。
- [ ] 运行相关 Vitest、TypeScript 和 Biome lint，确认 0 失败。
- [ ] 运行 `scripts/validate-development-standards.ps1` 与 `git diff --check`。
- [ ] 若用户端 `application-dev.yml` 的本机集成配置完整，使用 `local-integration-test` 配置在本机 MySQL 8 专用库 `ai_video_test`、Redis DB 15 和本次前缀执行集成测试；环境变量仅可选覆盖，禁止容器和宽范围清理。
- [ ] 对照规格逐项检查，报告已验证项和无法执行的外部 OSS 验证。

### 任务 7：通用防重与业务幂等边界

**风险：** 红色（重复请求一致性）

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/portrait/controller/PortraitController.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/portrait/controller/PortraitControllerTest.java`

- [ ] 先增加失败测试：上传、创建和删除不得存在 `@RepeatSubmit`，更新必须保留。
- [ ] 运行 Controller 测试，确认旧注解边界导致失败。
- [ ] 移除三个冲突注解，保留更新接口的通用防重和 Service 层并发规则。
- [ ] 重新运行测试，确认业务幂等重试不再被 AOP 提前拦截。

### 任务 8：图片真实解码与像素资源限制

**风险：** 红色（文件上传安全）

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/PortraitImageValidator.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/PortraitImageValidatorTest.java`
- 修改：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`

- [ ] 先增加失败测试：WebP 只有合法容器/尺寸但压缩数据损坏时必须失败；超过 12000 单边或 25000000 总像素时返回 `46203`。
- [ ] 运行校验器测试，确认旧实现仍接受未真实解码的 WebP，且没有像素错误码。
- [ ] 引入 ImageIO WebP Reader，并统一使用 Reader 在分配完整图像前读取尺寸、执行资源上限、真实解码首帧。
- [ ] 重新运行校验器测试，确认 JPG/JPEG、PNG、WebP、GIF 正向样本和全部反向样本通过。

### 任务 9：OSS 回滚补偿与未绑定素材清理

**风险：** 红色（私有文件、外部对象存储、删除一致性）

**文件：**
- 修改：`IAssetService.java`、`AssetServiceImpl.java`、素材 Mapper 与 XML
- 创建或修改：用户端启动模块中的内部清理调度器
- 修改：对应 Core 和启动模块测试

- [ ] 先增加失败测试：数据库插入异常和事务最终回滚会删除刚上传对象；提交成功不删除；清理只保留 24 小时以上、无引用素材；绑定竞态和 OSS 删除失败均不得误删记录。
- [ ] 运行测试，确认旧实现会在异常/回滚后遗留对象，且没有未绑定清理路径。
- [ ] 使用事务完成回调实现上传补偿；使用“事务内预占、事务外删对象、事务内收口”的 Service 编排实现每小时最多 100 条的 24 小时 TTL 清理。
- [ ] 重新运行受影响测试；在本机受控环境验证活跃素材不被清理，既有 24 小时内记录不被修改。

### 任务 10：定向复核与最终验证

- [ ] 仅复核任务 7-9 差异：一次规格/契约自查、一次文件安全/数据补偿专项自查。
- [ ] 运行人物形象与素材 Maven 测试、模块构建、前端既有回归、文档规范验证和 `git diff --check`。
- [ ] 构建并重启本地用户端 API，使用当前测试账号完成登录、列表和一张允许格式图片的受控上传/创建验证。
- [ ] 不提交、不创建 PR；输出实际测试证据、未验证项和 backlog 中的 N+1 优化。

## 计划自检

- 已确认审查问题均有实现任务和测试；任务 7-10 负责关闭本轮三个阻塞项。
- 类型名称在后端 DTO、用户端 VO 和前端接口中保持一致。
- 没有公共形象、运营端、批量上传、换图、版本、草稿或生成能力扩张。
- 没有占位任务；每项均包含红灯、实现和绿灯步骤。
