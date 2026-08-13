# 创作第三步形象与声音选择实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `subagent-driven-development` 逐任务实现此计划。步骤使用复选框（`- [ ]`）跟踪进度。

**目标：** 将创作流程第三步从静态演示数据改为真实人物形象与原声音资源选择，支持独立分页、刷新、上传后回选、形象大图预览、声音音轨播放，并让后续生成任务直接引用资源 ID。

**架构：** 前端选择面板只消费既有形象/声音资源接口，分别维护分页与刷新状态；创作入口上传原声音时显式关闭转写，声音功能模块保留主动发起转写能力。后端在现有 RuoYi Controller/Service/Mapper 分层内补充延迟转写、资源归属读取和资源 ID 生成入口，保留旧 multipart 生成接口兼容；数据库迁移统一五态约束、可空调度时间与工作区级幂等键。

**技术栈：** Java 21、Spring Boot、MyBatis-Plus、MySQL 8、Redis 7、React 19、TypeScript、Ant Design 6、Vitest、Testing Library、Maven Wrapper。

---

## 文件结构

- 修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`：登记延迟转写、主动转写、工作区幂等和资源 ID 生成契约。
- 创建 `docs/sql/ai-video/mysql/20260806_01_creation_asset_selection.sql`：迁移声音五态、可空调度时间和工作区幂等唯一键。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/CreateVoiceDTO.java`、`voice/service/IVoiceService.java`、`voice/service/impl/VoiceServiceImpl.java`：实现延迟转写与显式启动。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/StartVoiceTranscriptionDTO.java`：定义主动转写服务参数。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/bo/CreateVoiceBo.java`、`voice/controller/VoiceController.java`、`voice/domain/vo/VoiceVo.java`：暴露上传开关和启动接口。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/bo/StartVoiceTranscriptionBo.java`：承载修订号。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`、`asset/service/impl/AssetServiceImpl.java`：安全读取归属当前工作区的形象底层素材。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/PortraitAssetReader.java`：隔离形象对象流读取回调。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/service/IDigitalHumanGenerationService.java`、`digitalhuman/service/impl/DigitalHumanGenerationServiceImpl.java`：用资源 ID 解析媒体并复用原生成链路。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/dto/CreateVoiceGenerationByResourceDTO.java`、`CreateDigitalHumanVideoByResourceDTO.java`：定义跨模块稳定 DTO。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/controller/UserDigitalHumanController.java`：同路径增加 JSON 入口并保留 multipart。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/domain/bo/CreateVoiceJobByResourceBo.java`、`CreateVideoJobByResourceBo.java`：定义 HTTP JSON 请求。
- 修改 `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/types.ts`、`voice/api.ts`、`voice/adapter.ts`：增加 `unparsed`、上传开关和主动转写 API。
- 修改 `ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/types.ts`、`digitalHuman/api.ts`：增加资源 ID 生成请求。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts`：允许未知时长媒体加载和播放。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`、`voices/VoiceCard.tsx`：抽取并复用资源卡片展示。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/PortraitSelectionPanel.tsx`、`OriginVoiceSelectionPanel.tsx`、`PagedSwipe.tsx`：实现独立选择面板和底部分页。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.tsx`、`VoiceStep.tsx`、`BaseStep.tsx`、`model.ts`、`index.tsx`、`style.css`：接入真实列表、上传回选和资源 ID 生成。
- 修改对应 `*.test.ts`、`*.test.tsx` 与新增迁移集成测试：锁定每项行为。

## 治理任务卡

- 风险：红色高风险，涉及公开 API、文件访问、异步状态、数据库约束和工作区归属。
- 权威来源：`docs/superpowers/specs/2026-08-06-creation-step-3-portrait-voice-selection-design.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`。
- 不做范围：不改变生成任务表的既有 `tenant + owner` 过渡归属模型，不移除旧 multipart，不在创作页等待或轮询转写，不新增 Java 平行状态枚举，不改人物形象处理状态机。
- 并发：实施期同时最多一名实现者；完成后由一名独立审查者做一次完整审查，发现阻塞时只做一次定向复核。
- 验证：目标单测、模块测试、TypeScript 编译、Biome、前端构建、开发标准校验；本机 MySQL/Redis 集成测试仅通过 `LocalIntegrationEnvironment` 与 `local-integration-test` 运行。
- 输出：逐项报告红灯证据、绿灯证据、改动文件、剩余风险和环境阻塞。

### 任务 1：锁定公共契约和数据库迁移

**文件：**
- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 创建：`docs/sql/ai-video/mysql/20260806_01_creation_asset_selection.sql`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceDeferredTranscriptionMigrationIT.java`

- [ ] **步骤 1：先写失败的迁移契约测试**

在 `VoiceSchemaContractTest` 断言新迁移存在且包含 `next_attempt_at DATETIME NULL DEFAULT NULL`、五态唯一 CHECK、`tenant_id, workspace_id, owner_id, idempotency_key` 唯一键；在迁移集成测试先构造旧表约束，再执行迁移并验证匿名/命名旧 CHECK 均消失且新唯一键可区分工作区。

- [ ] **步骤 2：运行测试并确认红灯**

运行：

```powershell
cd ai-video-api
.\mvnw.cmd --% -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VoiceSchemaContractTest test
```

预期：因 `20260806_01_creation_asset_selection.sql` 不存在或契约片段缺失而失败。

- [ ] **步骤 3：编写迁移与公共契约**

迁移必须通过 `information_schema.TABLE_CONSTRAINTS` 找出 `av_voice.transcription_status` 的全部 CHECK 名称并逐一删除，再建立唯一命名五态 CHECK；删除旧幂等唯一键并创建：

```sql
UNIQUE KEY uk_av_voice_workspace_idempotency
  (tenant_id, workspace_id, owner_id, idempotency_key)
```

文档明确 `unparsed -> pending` 只由主动启动接口触发，创建时 `transcriptionRequested` 省略等价于 `true`。

- [ ] **步骤 4：运行契约测试确认绿灯**

重复步骤 2 命令，预期 `VoiceSchemaContractTest` 全部通过。

### 任务 2：实现声音延迟转写与显式启动

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/CreateVoiceDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/StartVoiceTranscriptionDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IVoiceService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/bo/CreateVoiceBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/bo/StartVoiceTranscriptionBo.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/controller/VoiceController.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/vo/VoiceVo.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImplTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/voice/controller/VoiceControllerContractTest.java`

- [ ] **步骤 1：写上传开关、幂等和主动启动失败测试**

覆盖省略/`true` 指纹相同、`false` 指纹不同；`false` 创建为 `unparsed` 且 `nextAttemptAt=null`；同工作区重复键回读、跨工作区互不冲突、并发唯一键冲突回读；主动启动仅能以匹配 `tenant/workspace/owner/type/status/revision/deleted` 的条件更新为 `pending`。

- [ ] **步骤 2：运行后端目标测试确认红灯**

```powershell
cd ai-video-api
.\mvnw.cmd --% -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=VoiceServiceImplTest,VoiceControllerContractTest test
```

预期：新增方法、字段和 `/transcription/start` 路由尚未定义而失败。

- [ ] **步骤 3：写最小实现**

Controller 先规范化有效布尔值并同时传入指纹与 DTO：

```java
boolean transcriptionRequested = !Boolean.FALSE.equals(bo.getTranscriptionRequested());
```

Service 创建时按有效值写 `pending + now` 或 `unparsed + null`；查询和数据库唯一键统一使用 `tenant + workspace + owner + idempotencyKey`，捕获 `DuplicateKeyException` 后按同一条件回读。主动启动使用带修订号的条件更新，受影响行数不是 1 时按现有错误码区分不存在、状态冲突和修订冲突。

- [ ] **步骤 4：重复步骤 2 命令确认绿灯**

预期：两组目标测试全部通过。

### 任务 3：实现资源 ID 生成入口与归属校验

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/PortraitAssetReader.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/dto/CreateVoiceGenerationByResourceDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/dto/CreateDigitalHumanVideoByResourceDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/service/IDigitalHumanGenerationService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/service/impl/DigitalHumanGenerationServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/domain/bo/CreateVoiceJobByResourceBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/domain/bo/CreateVideoJobByResourceBo.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/controller/UserDigitalHumanController.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/AssetServiceImplTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/digitalhuman/service/impl/DigitalHumanGenerationServiceImplTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/digitalhuman/controller/UserDigitalHumanControllerTest.java`

- [ ] **步骤 1：写 JSON 路由和安全边界失败测试**

覆盖请求体：

```json
{"scriptText":"测试文案","referenceVoiceId":"voice-id"}
```

```json
{"voiceJobId":"voice-job-id","portraitId":"portrait-id"}
```

测试当前工作区归属、资源类型、资源状态、底层素材归属、OSS 读取及旧 multipart 仍可用；拒绝跨工作区资源。

- [ ] **步骤 2：运行目标测试确认红灯**

```powershell
cd ai-video-api
.\mvnw.cmd --% -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AssetServiceImplTest,DigitalHumanGenerationServiceImplTest,UserDigitalHumanControllerTest test
```

预期：资源 ID DTO、读取方法和 JSON Controller 映射缺失而失败。

- [ ] **步骤 3：实现资源解析并复用旧生成逻辑**

`IAssetService` 提供归属读取回调；生成 Service 先用当前主体校验声音/形象资源及底层素材，再读取对象存储字节并调用既有 byte[] 生成方法。Controller 使用同一 URL、通过 `consumes = MediaType.APPLICATION_JSON_VALUE` 区分新入口，旧 multipart 映射保持不变。

- [ ] **步骤 4：重复步骤 2 命令确认绿灯**

预期：三组测试全部通过，越权和状态错误保持稳定业务错误码。

### 任务 4：扩展前端 API 与未知时长播放

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/voice/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/voice/adapter.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts`
- 修改：对应 `api.test.ts`、`adapter.test.ts`、`useVoicePlayback.test.ts`

- [ ] **步骤 1：写失败测试**

断言创作上传表单包含 `transcriptionRequested=false`；主动转写调用 `POST /api/voices/{id}/transcription/start`；`unparsed` 显示“未解析”；资源 ID 生成使用 JSON；`durationSeconds` 缺失或为 0 时点击播放仍会请求访问地址并调用 `audio.play()`。

- [ ] **步骤 2：运行目标测试确认红灯**

```powershell
cd ai-video-ui/ai-video-webapp
npm.cmd test -- src/services/ai-video/voice/api.test.ts src/services/ai-video/voice/adapter.test.ts src/services/ai-video/digitalHuman/api.test.ts src/pages/digital-human-studio/voices/useVoicePlayback.test.ts
```

预期：新参数、状态和 JSON 方法缺失，未知时长测试因提前返回失败。

- [ ] **步骤 3：写最小实现并重复步骤 2 命令**

上传 API 仅在调用方传值时追加布尔字段；数字人 API 对资源入口设置 JSON body；播放 Hook 不再以时长作为可播放前置条件，已知时长继续用于轨道比例。预期全部通过。

### 任务 5：实现真实选择面板和底部分页

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/PagedSwipe.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/PortraitSelectionPanel.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/OriginVoiceSelectionPanel.tsx`
- 创建：上述三个组件的 `*.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

- [ ] **步骤 1：写选择面板失败测试**

形象请求固定 `pageSize=6` 并渲染两行三列，所有状态可见但仅 `ready` 可选；声音请求固定 `pageSize=6` 且过滤 `type=origin`；两个区域各自刷新、页码和加载/空/失败状态互不影响；分页按钮在卡片下方并支持左右滑动；形象预览无旋转动作；声音轨道可点击跳转并播放。

- [ ] **步骤 2：运行组件测试确认红灯**

```powershell
cd ai-video-ui/ai-video-webapp
npm.cmd test -- src/pages/digital-human-studio/asset-selection src/pages/digital-human-studio/steps/AssetStep.test.tsx
```

预期：选择组件不存在，静态 `AVATARS`/`VOICES` 不能满足真实 API 断言。

- [ ] **步骤 3：实现组件**

使用 Ant Design `Image.PreviewGroup`，通过 `toolbarRender` 仅保留缩放、适应、关闭等动作，不提供旋转；用 `Row/Col` 保证桌面三列；`PagedSwipe` 把上一页、页码、下一页统一放在内容下方并以触摸位移阈值切页。声音条目复用声音库展示字段和播放 Hook，不显示克隆声音。

- [ ] **步骤 4：重复步骤 2 命令确认绿灯**

预期：真实列表、独立状态、分页位置、预览和播放测试全部通过。

### 任务 6：串联上传回选与后续资源 ID 生成

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/VoiceStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/VoiceStep.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.test.tsx`

- [ ] **步骤 1：写端到端组件失败测试**

覆盖新增形象上传成功后请求第一页并选中新 ID；新增原声音发送 `transcriptionRequested=false`，成功后刷新第一页并选中新 ID，不启动轮询；第三步未选完整不能继续；声音生成提交 `{scriptText, referenceVoiceId}`；视频生成提交 `{voiceJobId, portraitId}`。

- [ ] **步骤 2：运行目标测试确认红灯**

```powershell
cd ai-video-ui/ai-video-webapp
npm.cmd test -- src/pages/digital-human-studio/index.test.tsx src/pages/digital-human-studio/steps/AssetStep.test.tsx src/pages/digital-human-studio/steps/VoiceStep.test.tsx src/pages/digital-human-studio/steps/BaseStep.test.tsx
```

预期：当前状态仍依赖 `File` 或静态默认 ID，提交形状和上传回选不满足断言。

- [ ] **步骤 3：实现状态与事件闭环**

模型只保存 `selectedPortraitId`、`selectedVoiceId` 和展示摘要；创建弹窗根据入口区分声音库上传与创作上传，创作上传固定关闭转写；上传成功把资源 ID 返回给选择面板，面板加载第一页并自动回选。后续步骤只传 ID，不从浏览器下载 OSS 再重传。

- [ ] **步骤 4：重复步骤 2 命令确认绿灯**

预期：四组组件测试全部通过。

### 任务 7：集成验证与定向审查

**文件：**
- 验证：本计划列出的全部变更文件

- [ ] **步骤 1：运行前端完整质量门禁**

```powershell
cd ai-video-ui/ai-video-webapp
npm.cmd test -- src/pages/digital-human-studio src/services/ai-video/voice src/services/ai-video/portrait src/services/ai-video/digitalHuman
npm.cmd run tsc
npm.cmd run biome:lint
npm.cmd run build
```

预期：测试、类型检查、Lint、构建均退出 0。

- [ ] **步骤 2：运行后端模块质量门禁**

```powershell
cd ai-video-api
.\mvnw.cmd --% -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：Reactor `BUILD SUCCESS`。

- [ ] **步骤 3：在安全本机环境运行迁移集成测试**

仅当 `LocalIntegrationEnvironment` 确认 MySQL 为本机专用 `ai_video_test`、Redis 为独立逻辑库且本次前缀可清理时运行：

```powershell
cd ai-video-api
.\mvnw.cmd --% -Plocal-integration-test -pl ruoyi-modules/ai-video/ai-video-core -am -DskipTests=false -DskipITs=false -Dit.test=VoiceDeferredTranscriptionMigrationIT verify
```

预期：迁移可重复执行，约束与唯一键断言通过；默认读取用户端 `application-dev.yml`，配置缺失或不安全时明确记录未运行，不连接开发、预发或生产数据源。

- [ ] **步骤 4：运行项目标准和差异检查**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
git diff --check
git status --short
```

预期：标准校验和空白检查通过；状态只包含本需求文件及用户原有未提交文件。

- [ ] **步骤 5：执行一次独立完整审查和必要的单次定向复核**

审查聚焦工作区越权、对象存储访问、幂等并发、旧 multipart 兼容、前端真实接口状态与上传回选。只有阻塞问题进入修复；修复后只复核受影响文件并重复相关目标测试。
