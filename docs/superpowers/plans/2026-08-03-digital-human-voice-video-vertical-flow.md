# 数字人声音与视频纵向链实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在现有数字人创作页跑通“IndexTTS2 克隆声音 → 用户确认 → ComfyUI 人物图加音频生成 MP4”的最短真实链路。

**架构：** RuoYi 用户端只暴露稳定平台 API；核心模块保存 owner 隔离的任务，基础设施模块封装两个 Provider 和私有文件存储。声音任务在受控执行器中运行，视频任务提交后由平台查询推进状态；前端只轮询平台任务。

**技术栈：** Java 21、Spring Boot、MyBatis-Plus、JDK HttpClient、MySQL 8、React 19、Umi Max、Ant Design、Vitest。

---

## 文件结构

- `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`：登记唯一平台契约、最小任务表和受限执行语义。
- `ai-video-core/.../digitalhuman/**`：贫血 Entity、DTO、Mapper 和 Service 编排。
- `ai-video-infra/.../digitalhuman/**`：IndexTTS2、ComfyUI 与私有媒体存储实现。
- `ai-video-user/.../digitalhuman/**`：App 端 Bo、Vo、Controller。
- `ai-video-webapp/src/services/ai-video/digitalHuman/**`：前端唯一 API/类型入口。
- `digital-human-studio` 的 Asset/Voice/Base 步骤：真实文件、任务、确认、轮询和媒体播放。

### 任务 1：冻结契约和 Provider 客户端

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/digitalhuman/**`
- 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/digitalhuman/**`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`

- [ ] 编写 fake HTTP server 测试，断言 IndexTTS2 的 `text/reference_audio/X-API-Key/Basic`；ComfyUI 使用工作流 `数字人口播.json`（UUID `8b7a9a57-2303-4ef5-9fc2-bf41713bd1fc`），依次覆盖原生 `/api/userdata`、`/upload/image`、`/prompt`、`prompt_id`、`history` 和 `view`。
- [ ] 运行定向 Maven 测试并确认因客户端不存在而 RED。
- [ ] 实现 `DigitalHumanProviderProperties`、`IndexTts2Client`、`ComfyUiClient` 和条件装配；证书只使用系统信任或指定 PEM。
- [ ] 再次运行定向测试，确认 GREEN，并精确提交任务 1 文件。

### 任务 2：实现持久化任务和用户端 API

**文件：**

- 创建：`docs/sql/ai-video/mysql/20260803_02_digital_human_vertical_flow.sql`
- 创建：`docs/sql/ai-video/mysql/20260803_03_digital_human_poll_lease.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/digitalhuman/**`
- 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/digitalhuman/**`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/**`
- 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/digitalhuman/**`

- [ ] 编写 Service 测试：owner 从参数上下文派生、幂等回读/冲突、失败终态、确认门禁、视频父任务校验和跨用户不可见。
- [ ] 运行定向测试并确认 RED。
- [ ] 创建单表 `av_dh_generation_job`、Entity/Mapper/DTO/Service；外部调用不放入数据库事务，文件键必须限制在配置根目录。
- [ ] 编写 Controller 测试，冻结 multipart、返回 Vo 和媒体 owner 校验；确认 RED 后实现最小 Controller。
- [ ] 运行 core/user 定向测试并确认 GREEN，精确提交任务 2 文件。

### 任务 3：替换前端假流程

**文件：**

- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/polling.ts`
- 测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/digitalHuman/api.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/AssetStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/VoiceStep.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx`
- 测试：对应 `*.test.tsx`

- [ ] 先写 API 与步骤行为测试，覆盖真实文件选择、声音创建/失败/确认、视频创建/轮询/失败和媒体 Blob URL，并确认 RED。
- [ ] 实现 API service；API 路径和任务状态字符串不得散落在页面。
- [ ] 让 AssetStep 保存本次会话的肖像和参考音频，让 VoiceStep/BaseStep 使用真实任务并删除假定时器。
- [ ] 运行定向 Vitest、TypeScript 和 Biome，确认 GREEN，精确提交任务 3 文件。

### 任务 4：精确验证和受限真实联调

**文件：**

- 创建：`scripts/test-digital-human-live.ps1`
- 仅在失败定位需要时修改任务 1～3 已登记文件。

- [ ] 运行后端各模块定向单测、前端定向测试和 `npm run tsc`；在 `ai-video-ui/ai-video-webapp` 下对已登记页面改动执行精确选择器：`npx biome check src/pages/digital-human-studio/model.ts src/pages/digital-human-studio/index.tsx src/pages/digital-human-studio/steps/AssetStep.tsx src/pages/digital-human-studio/steps/AssetStep.test.tsx src/pages/digital-human-studio/steps/VoiceStep.tsx src/pages/digital-human-studio/steps/VoiceStep.test.tsx src/pages/digital-human-studio/steps/BaseStep.tsx src/pages/digital-human-studio/steps/BaseStep.test.tsx`；`src/services/ai-video/digitalHuman` 因仓库 Biome 配置排除时，对 `src/services/ai-video/digitalHuman/types.ts`、`src/services/ai-video/digitalHuman/api.ts`、`src/services/ai-video/digitalHuman/api.test.ts`、`src/services/ai-video/digitalHuman/polling.ts` 分别执行 `Get-Content -Raw -LiteralPath <file> | npx biome check --stdin-file-path=<file>`；最后运行 `scripts/validate-development-standards.ps1`。
- [ ] 使用 Git metadata 临时目录生成非敏感短音频/测试图，显式读取环境变量，真实调用一次 IndexTTS2；校验 WAV。
- [ ] 将该 WAV 和测试图注入上述工作流并通过 ComfyUI 原生 API 真实提交一次，轮询到终态并校验 MP4；不调用搜索或其他模型，不输出认证信息。
- [ ] `git diff --check`、工作区范围检查、精确暂存、提交并 push 功能分支；只报告 candidate，不合并 main。
