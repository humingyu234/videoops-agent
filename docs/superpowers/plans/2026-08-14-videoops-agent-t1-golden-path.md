# T1 人工黄金链施工计划

> 本计划只执行现有能力的环境准备、诊断、真实运行和证据固化，不实现 Agent 或顺手修产品缺陷。执行前读取 `docs/EXECUTION.md` 和 `docs/tasks/T1-golden-path.md`。

**目标：** 在当前源码上跑通一条可重复的真实七步图生数字人口播链，并锁定实际 Provider、ComfyUI 工作流/模型、两套任务 ID 与最终 MP4 证据。

**基线：** 计划编写时为 `cab31a0d16fa3f2a1a2f65d9fbff9fd8d117905a`；正式开工时必须重新记录 HEAD 和工作区。

**风险：** 红色。任何真实生成前必须由项目负责人确认测试账号、素材授权、Provider 地址和额度上限。

## 0. 开工门禁

**只读检查：** Git、项目路径、进度状态。

```powershell
Set-Location D:\project\videoops-agent
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git status --short
Get-Content .\docs\EXECUTION.md -Raw
Get-Content .\docs\tasks\T1-golden-path.md -Raw
```

预期：根目录为 `D:\project\videoops-agent`；无来源不明改动。若 HEAD 或黄金链相关路径已经变化，先将旧证据标记为 `NEEDS_REVALIDATION`，核对计划后再继续。

在 `docs/EXECUTION.md` 中把 T1 状态改为 `IN_PROGRESS`、记录开工 HEAD、工作区和当前动作。未实际开工时不得提前修改。

## 1. T1.1 安全环境清点

**核对入口：** `scripts/start-local-user-api.ps1`、`ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`。

```powershell
Get-Command java,node,npm -ErrorAction SilentlyContinue
if (Get-Command java -ErrorAction SilentlyContinue) { java -version }
node --version
npm --version
Test-NetConnection 127.0.0.1 -Port 3306
Test-NetConnection 127.0.0.1 -Port 6379
Test-NetConnection 127.0.0.1 -Port 39000
Test-NetConnection 127.0.0.1 -Port 8189
Test-NetConnection 127.0.0.1 -Port 8080
Test-NetConnection 127.0.0.1 -Port 18080
& "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffmpeg.exe" -version
& "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffprobe.exe" -version
```

通过条件：Java 21 可解析；Node 满足 `ai-video-ui/ai-video-webapp/package.json`；3306/6379 可用；18080 空闲；ffmpeg/ffprobe 可执行；冻结 CJK 字体存在。8080 已知被未知进程占用，禁止为本任务结束该进程。

若 39000/8189 未监听，只记录 Provider 前置缺口并继续可做的本地清点，不把 T1 标为 `BLOCKED`。把版本、端口结论和 `NOT_RUN` 项写入 `docs/evidence/T1/T1-run.md`，不记录凭据值。

## 2. T1.2 数据库、Redis 与迁移核对

**权威文件：**

- `docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`
- `docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql`
- `ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`

先读取权威说明，解析 `spring.datasource.dynamic.datasource.master` 最终目标；只显示主机、端口和库名，不输出用户名/密码。数据库客户端实际选中的库必须与目标一致后，才允许执行权威幂等初始化 SQL。不得改用 Docker 或旁路配置。

核对必要迁移、任务表、创作项目/资产表和账号/权限基础数据。初始化 SQL 不会创建真实人物、声音媒体，不能把种子数据检查当成 T1.3 通过。

通过条件：目标库明确、迁移版本一致、Redis 认证可用、初始化可幂等执行且未影响其他库。把执行命令、退出码和脱敏摘要记录为 `T1-E02`。

## 3. T1.3 真实素材与 OSS 核对

**代码入口：**

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/AssetSelectionStep.tsx`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/controller/UserDigitalHumanController.java`

使用本次测试账号通过正常 API/Service 查询：至少一个归属当前用户的 `ready` 人物和一个 `origin` 原声音；分别验证后端授权媒体读取能返回对应 OSS 对象。不要在 SQL 中手工伪造 ready 状态，不把签名 URL 写进证据。

记录脱敏的用户/工作区标识、portraitId、voiceId、媒体哈希或安全摘要与访问结果。若没有合格资产，T1 保持 `IN_PROGRESS`，另行通过产品正常上传/创建路径准备，不在数据库造数据。

## 4. Provider 与工作流身份预检

**脚本：** `scripts/test-digital-human-live.ps1`。

先做不生成媒体的连通性、TLS/认证和工作流读取检查，并在当前 PowerShell 进程私密注入这些环境变量：

- 必需：`DEMO_INDEXTTS_BASE_URL`、`DEMO_COMFY_BASE_URL`、`DEMO_INDEXTTS_API_KEY`；
- 按环境可选：Basic Auth、CA 证书、`DEMO_COMFY_WORKFLOW_FILE`、`DEMO_COMFY_WORKFLOW_ID`。

从实际 ComfyUI 读取 `数字人口播.json`，保存到未公开的证据工作区或经授权的 `docs/evidence/T1/`；计算 SHA-256，并人工记录工作流 UUID、模型加载节点的 checkpoint/LoRA 名称。没有此证据不能宣称具体 Wan/LTX 版本。

`test-digital-human-live.ps1` 会真实调用声音和视频 Provider，不是免费 health check。为避免在完整应用 E2E 之外重复生成，默认不先运行它；只有完整应用在 Provider 边界失败、现有日志无法定位，且项目负责人另行批准诊断素材和额度时才执行一次：

```powershell
Set-Location D:\project\videoops-agent
$referenceAudioPath = (Read-Host '请输入已授权参考音频的绝对路径').Trim()
$portraitImagePath = (Read-Host '请输入已授权人物图的绝对路径').Trim()
Get-Item -LiteralPath $referenceAudioPath
Get-Item -LiteralPath $portraitImagePath
.\scripts\test-digital-human-live.ps1 `
  -ReferenceAudioPath $referenceAudioPath `
  -PortraitImagePath $portraitImagePath `
  -TimeoutSeconds 600 `
  -PollIntervalSeconds 2 `
  -KeepArtifacts
```

通过信号：`INDEXTTS2_LIVE_OK`、`COMFYUI_LIVE_OK`、`DIGITAL_HUMAN_LIVE_OK`。这只证明 Provider 边界，不能证明登录、数据库、应用 Service、时间轴或最终渲染。

## 5. 后端专项测试与构建

```powershell
Set-Location D:\project\videoops-agent\ai-video-api
.\mvnw.cmd -pl :ai-video-core,:ai-video-infra,:ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  "-Dtest=DigitalHumanGenerationServiceImplTest,DigitalHumanProviderClientTest,TimelineTaskApplicationServiceTest,TimelineTaskSchedulerTest,FfmpegTimelineMediaRenderServiceTest" test
.\mvnw.cmd -pl :ai-video-user-api -am -Dmaven.test.skip=true package
```

两条命令均以退出码 0 为通过。失败时保存首个根因和相关 surefire 报告路径；输入/环境无变化最多重试两次。

## 6. 在 18080 启动用户端后端

根目录 `.env` 不会被 Spring 自动读取。敏感配置只注入当前进程环境，或保存在 Git 忽略的 `.runtime/user-api.local.yml`；证据只写配置项“存在/缺失”。

```powershell
Set-Location D:\project\videoops-agent
.\scripts\start-local-user-api.ps1 -Port 18080 -LocalConfigPath .\.runtime\user-api.local.yml
```

脚本会复用本地运行密钥并在缺省时构建。验证健康入口、登录、当前用户权限和相关业务路由；不要复用 8080 的未知进程作为 T1 证据。

## 7. 启动无 Mock 前端并运行专项测试

```powershell
Set-Location D:\project\videoops-agent\ai-video-ui\ai-video-webapp
npm ci
$env:AI_VIDEO_API_ORIGIN='http://127.0.0.1:18080'
npm test -- `
  src/pages/digital-human-studio/steps/AssetStep.test.tsx `
  src/pages/digital-human-studio/steps/VoiceStep.test.tsx `
  src/pages/digital-human-studio/steps/BaseStep.test.tsx `
  src/pages/digital-human-studio/steps/TimelineStep.test.tsx `
  src/pages/digital-human-studio/steps/ExportStep.test.tsx
npm run dev
```

验证 `/studio` 登录、权限、真实人物/声音列表、加载/失败状态。步骤 0～4 的 `voiceJob`/`videoJob` 上下文主要在 React 内存；创建 `projectId` 前不要刷新浏览器，并把该缺陷记录到证据的剩余风险。

## 8. T1.6 单次真实黄金样例

只在账号、素材授权、Provider、OSS 和额度上限均确认后执行。固定目标、脚本事实、人物和音色。对当前已经支持幂等的声音、底片、创作项目、时间轴保存和渲染动作生成并保存稳定幂等键，整个意图不得更换键重提。问卷和文案生成接口当前不接收幂等键：本样例各调用一次，并把这个边界记录为 T3 工具适配缺口，不能伪称已防重复提交。

按页面执行：

1. `POST /api/studio/questionnaires/generate` 与 `POST /api/studio/scripts/generate`，确认 `modelMode=deepseek`。
2. 从 `GET /api/portraits`、`GET /api/voices?voiceType=origin` 选择 T1.3 的资产。
3. `POST /api/studio/voice-jobs`，轮询任务，试听后 `POST .../confirmation`。
4. `POST /api/studio/video-jobs`，通过 `GET /api/studio/jobs/{id}` 观察并刷新 Provider 状态，预览真实媒体。
5. `POST /api/studio/creation-projects`，确认主视频轨和初始字幕轨。
6. `PUT /api/studio/creation-projects/{projectId}/timeline-draft`，再 `POST /api/studio/creation-projects/{projectId}/render-tasks`，观察 `av_ai_task` 到终态。
7. `GET /api/studio/creation-projects/{projectId}/outputs/latest`，再从 `GET /api/studio/creation-assets/{assetId}/content` 下载最终 MP4。

逐步记录开始/结束时间、HTTP/业务结果摘要、voice/video jobId、projectId、timeline taskId、outputAssetId 和失败点。任务 ID 可脱敏，但必须能证明同一条因果链。Provider 提交状态未知时停止并对账，不能再次创建。

## 9. 最终 MP4 与一致性验收

```powershell
$finalVideoPath = (Read-Host '请输入刚下载的最终 MP4 绝对路径').Trim()
Get-Item -LiteralPath $finalVideoPath
$finalVideoHash = Get-FileHash -LiteralPath $finalVideoPath -Algorithm SHA256
$probeJson = & "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffprobe.exe" `
  -v error -show_streams -show_format -of json $finalVideoPath
$finalVideoHash
$probeJson
```

验证：MP4 可解码、9:16、有视频和音轨、字幕实际可见，音视频流时长差满足现有 0.25 秒不变量；记录目标 30 秒与实际时长偏差，并确认文案/声音/画面未被截断。T1 不新设未经真实样本校准的目标时长阈值，相关阈值在 T5 用固定样本确定。`outputs/latest.taskId`、`outputAssetId` 必须与最终任务详情相同。人工播放检查身份、声音、口型、稳定性和字幕，但主观观察不替代媒体门禁。

## 10. 固化证据、独立复核和收工

更新 `docs/evidence/T1/T1-run.md`：

- 完整源码哈希与工作区状态；
- `REAL` 环境和 Provider/模型/工作流版本摘要；
- 每个验收项的命令、退出码、任务/产物关联和结果；
- MP4/工作流 SHA-256、ffprobe 摘要和人工检查；
- `PASS`、`FAIL`、`NOT_RUN`；
- 未验证项、剩余风险和下一准确动作。

由没有实施本次黄金样例的人，在标记 `DONE` 前只读复核：任务卡每项 Given/When/Then、两套任务 ID 到最终资产的因果关系、`REAL/DEMO/MOCK` 标签、Provider/工作流/模型声明、未验证项和秘密脱敏。复核结论保存到同一 `T1-run.md`；存在真实性、任务重复提交或关键证据缺口时，T1 保持未完成。

运行：

```powershell
Set-Location D:\project\videoops-agent
git status --short
git diff --check
.\scripts\validate-development-standards.ps1
```

只有任务卡全部成功与反向场景成立，才把 T1 设为 `DONE` 并同步 `docs/PLAN.md`、`docs/EXECUTION.md` 和任务卡验收记录。否则保持 `IN_PROGRESS`、`VERIFYING` 或真正无安全动作时的 `BLOCKED`。
