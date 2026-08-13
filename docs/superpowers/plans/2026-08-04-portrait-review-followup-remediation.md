# 人物形象审查跟进整改实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `subagent-driven-development` 逐任务实现，遵循 TDD 红灯—绿灯；步骤使用复选框跟踪。当前工作区包含用户既有未提交修改，禁止覆盖无关差异；用户已明确不保留版本，因此不创建 commit。

**目标：** 关闭人物形象上传、删除、孤儿清理、列表请求和短期预览地址中已经复核确认的缺陷，并把视频创建形象选择切换到当前用户的可用形象。

**架构：** 后端继续使用 RuoYi Entity/DTO/Mapper/Service 编排：Creator 启动器只装载私有 OSS，素材上传写入真实 MIME，删除在素材行锁后重新读取形象版本，孤儿清理使用数据库认领令牌。前端以请求序号隔离乱序响应，以访问地址接口按需续签，并由真实人物形象列表驱动创建步骤。

**技术栈：** Java 21、Spring Boot、RuoYi-Vue-Plus 6.x、MyBatis、MySQL 8、React 19、TypeScript、Ant Design、Vitest。

**规格输入：** `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/superpowers/specs/2026-08-03-user-portrait-library-design.md`、`docs/superpowers/specs/2026-08-04-portrait-library-complete-remediation-design.md`。

---

## 文件结构

- `ai-video-api/ai-video-user-api/.../CreatorOssConfigurationInitializer.java`：拒绝非私有 Creator OSS 配置。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/.../AssetServiceImpl.java`：上传 MIME、行锁读取和带令牌孤儿清理。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/.../PortraitServiceImpl.java`：删除版本串行化及重复删除收口。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/.../AssetFileMapper.java`、`AssetFileMapper.xml`：清理认领令牌的条件更新。
- `docs/sql/ai-video/mysql/20260804_02_portrait_asset_cleanup_claim.sql`：新增清理令牌与认领时间。
- `ai-video-ui/ai-video-webapp/.../PortraitLibraryView.tsx`：列表请求隔离和预览地址续签。
- `ai-video-ui/ai-video-webapp/.../steps/AssetStep.tsx`、`BaseStep.tsx`、`index.tsx`：真实可用形象选择与后续步骤展示。
- 对应 Java/Vitest 测试：先复现问题，再实施最小修复。

### 任务 1：强制私有 OSS 并写入对象 MIME

**风险：** 红色（私有文件外部信任边界）

**文件：**
- 修改：`ai-video-api/ai-video-user-api/src/main/java/org/dromara/aivideo/bootstrap/CreatorOssConfigurationInitializer.java`
- 修改：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/CreatorOssConfigurationInitializerTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/AssetServiceImplTest.java`

- [ ] **步骤 1：编写失败测试**

Initializer 测试分别断言 `access_policy=0` 可以写入缓存，`1/2/空值` 会抛出启动异常且从不写缓存。上传测试捕获 `Options` 并断言：

```java
verify(client).upload(eq("portraits/1001/new.webp"), eq(command.content()),
    argThat(options -> "image/webp".equals(options.getContentType())));
```

- [ ] **步骤 2：运行红灯**

运行 `CreatorOssConfigurationInitializerTest,AssetServiceImplTest`，预期公共策略仍被接受，且上传调用没有 `Options`。

- [ ] **步骤 3：最小实现**

`toProperties` 在构造缓存对象前校验 `"0".equals(accessPolicy)`，否则抛出 `IllegalStateException`；上传改为：

```java
client.upload(key, command.content(), Options.builder().setContentType(metadata.contentType()));
```

- [ ] **步骤 4：运行绿灯**

重新运行同组测试，确认私有策略和 MIME 断言通过。

### 任务 2：删除版本串行化和并发幂等

**风险：** 红色（数据删除、并发一致性）

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/portrait/service/impl/PortraitServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/portrait/service/impl/PortraitServiceImplTest.java`

- [ ] **步骤 1：编写失败测试**

增加两个行为测试：取得素材锁后必须重新读取人物形象并拒绝已经变化的 `recordRevision`；收口删除返回 0 时，如果同租户、工作空间和用户的历史记录已经 `del_flag=1`，重复请求成功且不再删除资产记录。

- [ ] **步骤 2：运行红灯**

运行 `PortraitServiceImplTest`，预期旧实现按锁前快照校验版本，并在并发重复收口时返回 `46211`。

- [ ] **步骤 3：最小实现**

新增 `requireOwnedPortraitAssetForUpdate` 复用 `selectOwnedPortraitAssetForUpdate`。`prepareDelete` 先用首次读取确定资产 ID，再取得资产行锁，再次读取形象后校验版本和归属。收口删除未命中时执行：

```java
Portrait historical = portraitMapper.selectOwnedIncludingDeleted(
    context.portraitId(), tenantId, workspaceId, ownerId);
if (historical != null && "1".equals(historical.getDelFlag())) return;
throw new ServiceException("人物形象删除收尾失败，请重试", DELETE_FAILED);
```

- [ ] **步骤 4：运行绿灯**

重新运行 `PortraitServiceImplTest`，确认版本交错和重复删除通过。

### 任务 3：孤儿素材使用认领令牌

**风险：** 红色（集群清理、私有文件删除）

**文件：**
- 创建：`docs/sql/ai-video/mysql/20260804_02_portrait_asset_cleanup_claim.sql`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/mapper/AssetFileMapper.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/asset/AssetFileMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/AssetCleanupMapperContractTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/AssetServiceImplTest.java`

- [ ] **步骤 1：编写失败测试**

Mapper 契约测试精确提取 `reserveUnboundPortraitAsset`，断言写入 `cleanup_claim_token/cleanup_claimed_at`；成功收口和失败标记都必须 `WHERE cleanup_claim_token = #{claimToken}`。Service 测试断言每次候选生成非空 UUID，并把同一令牌传给 reserve/finalize/fail。

- [ ] **步骤 2：运行红灯**

运行 `AssetCleanupMapperContractTest,AssetServiceImplTest`，预期旧 SQL 和方法签名缺少令牌。

- [ ] **步骤 3：最小实现**

迁移向 `av_asset` 增加可空 `cleanup_claim_token VARCHAR(36)`、`cleanup_claimed_at DATETIME`。候选允许 `ready/delete_failed`，或认领时间超过 30 分钟的 `delete_pending`；reserve 写入新 UUID 和当前时间，收口/失败仅允许当前令牌更新并清空令牌字段。

- [ ] **步骤 4：运行绿灯**

重新运行同组测试；若本机安全 MySQL 集成变量齐全，再使用 `local-integration-test` 验证两个连接只有一个令牌可以完成收口。

### 任务 4：列表请求隔离和短期地址续签

**风险：** 黄色（前端数据正确性）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`

- [ ] **步骤 1：编写失败测试**

使用两个可控 Promise 发起旧、新列表请求，后完成旧请求后仍只显示新结果。触发列表或详情图片 `error` 后，断言调用 `portraitApi.accessUrl(portraitId)` 并把 `src/previewExpiresAt` 更新为新值；同一形象续签中不得重复请求。

- [ ] **步骤 2：运行红灯**

运行 `PortraitLibraryView.test.tsx`，预期旧响应覆盖新响应，图片错误不会续签。

- [ ] **步骤 3：最小实现**

增加 `requestRevisionRef`；每次加载递增，只有当前 revision 可以更新 records、total、error 和 loading。`load(targetPage = page)` 接受明确页码，创建后用 `load(1)`。增加按形象 ID 去重的 `refreshPreviewUrl`，成功后同时更新列表和详情，失败后清空失效地址并显示占位图。

- [ ] **步骤 4：运行绿灯**

重新运行该测试文件，确认乱序和续签用例通过。

### 任务 5：视频创建步骤使用真实可用形象

**风险：** 黄色（既有创建流程数据源替换）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`

- [ ] **步骤 1：编写失败测试**

测试进入“选择形象与声音”时请求 `portraitApi.list({ availabilityStatus: 'ready' })`，只渲染接口返回形象；加载、空、失败、重试均可见；选择后把 `portraitId` 写入 `selectedAvatar`，后续 BaseStep 使用同一真实名称和预览图。

- [ ] **步骤 2：运行红灯**

运行 `AssetStep.test.tsx,index.test.tsx`，预期页面仍显示 `AVATARS` 模拟数据。

- [ ] **步骤 3：最小实现**

在 Studio 父层维护 `Portrait[]` 与加载状态并传给 AssetStep/BaseStep；首次进入相关步骤时加载最多 48 个 ready 形象。默认 `selectedAvatar` 改为空字符串；当前选择不在新列表中时清空，不回退公共或静态形象。

- [ ] **步骤 4：运行绿灯**

重新运行同组测试，确认真实数据、状态和跨步骤选择一致。

### 任务 6：契约同步与完整验证

**文件：**
- 修改：`docs/superpowers/specs/2026-08-03-user-portrait-library-design.md`
- 修改：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`（仅新增令牌字段或现有说明需要同步时）

- [ ] 删除旧规格中 `46203 UPLOAD_SESSION_EXPIRED`，保留公共契约中的 `46203 PORTRAIT_IMAGE_DIMENSIONS_EXCEEDED`；不新增上传会话错误码。
- [ ] 运行所有人物形象、素材、Creator 启动测试与相关前端 Vitest。
- [ ] 运行 Maven 模块构建、TypeScript `--noEmit`、Biome、`scripts/validate-development-standards.ps1` 和 `git diff --check`。
- [ ] 重启本地用户端 API，使用现有 creator 测试账号验证登录、列表、上传、创建、预览续签和删除；通过签名 GET 响应头确认真实图片 MIME。
- [ ] 仅对本计划差异执行一次规格合规审查和一次代码质量审查；不递归扩大全量审查。

## 自检

- 计划没有引入草稿、公共形象、运营端、批量上传、换图、版本、人脸识别、清晰度、构图或内容审核。
- “操作成功但刷新失败会误报失败”经复核为误判：`load()` 已吞并刷新异常，本计划不做无依据重构；仅修复旧页码和乱序覆盖。
- 私有 OSS、删除和清理属于红色风险，均有先失败测试、最小实现和定向绿灯。
- 当前分支保留用户未提交修改，不创建 commit、不清理工作区。
