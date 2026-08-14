# T1 人工黄金链当前辅助 runbook

> 本文件只有在 `docs/EXECUTION.md` 明确点名时才是当前辅助 runbook。它冻结 T1 的目标、边界、验收和执行方法，但不保存实时状态、证据或下一步；这些事实只写入 `docs/EXECUTION.md`。

**初始编写基线：** `cab31a0d16fa3f2a1a2f65d9fbff9fd8d117905a`。每次执行必须以实时 HEAD、工作区和 `docs/EXECUTION.md` 为准，不能把本日期或基线当作当前证明。

**风险：** 红色。真实 Provider、文件/资产归属、异步任务、幂等和付费调用均跨越外部边界。

## 冻结目标与边界

目标是在当前源码上用一个固定样例跑通真实链路，并锁定实际 Provider、ComfyUI 工作流/模型、两套任务 ID 和最终 MP4：

```text
登录
-> 说需求并生成/确认文案
-> 选择 ready 人物和 origin 原声音
-> IndexTTS2 生成并确认声音
-> ComfyUI 生成数字人底片
-> 创建时间轴项目和初始字幕
-> FFmpeg 渲染
-> 预览并下载最终 MP4
```

- 固定样例：目标 30 秒的中文商品介绍、9:16、烧录字幕，一张已入库人物图和一个已入库原声音。
- 允许范围：本地环境配置、现有链路运行、只读诊断和脱敏证据。发现产品缺陷时只记录最小复现、影响和建议切片，未经重新定范围不修改运行时代码。
- 两套任务事实必须分别记录：声音/底片使用 `av_dh_generation_job`，时间轴渲染使用 `av_ai_task`。

### 非目标

- 不开发 `/agent`，不合并 Avatar Space 分支，不改数据库结构、任务模型或 Provider，不顺手统一两套任务。
- 不启动、停止或重启来源不明的进程；未知 `8080` 进程必须保留，用户 API 使用 `18080`。
- 不伪造任务、资产、成功状态、模型名称或运行轨迹；不在证据中写 Token、密码、签名 URL、私有配置或用户隐私。
- 同一真实付费意图只允许一个稳定幂等请求。Provider 提交状态未知时只对账或转人工，禁止再次 POST。

## 停止条件

- 路径、分支、HEAD 或工作区存在未解释差异时停止，先查明来源。
- 账号、素材授权、Provider 身份或额度上限未确认时，不得进入真实付费生成。
- Provider 提交结果未知、同一意图可能重复收费或因果任务链无法确认时停止并对账，不得换键重提。
- 出现越权、秘密暴露、不可定位失败、无变化重复失败或需要修改运行时代码时，保持 T1 未完成并重新定范围。

## T1.0 开工与授权门禁

```powershell
$videoOpsRoot = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $videoOpsRoot
git rev-parse --show-toplevel
git branch --show-current
git rev-parse HEAD
git status --short
Get-Content .\docs\EXECUTION.md -Raw
```

根目录必须是当前独立仓库的顶层，且包含 `.git`、`AGENTS.md` 和 `docs/PROJECT.md`，不能把父目录、公司仓库或来源不明的副本当作目标。把实时路径、分支、HEAD 和工作区写入 `EXECUTION.md`；只有项目负责人确认正式进入 T1 后才进入后续步骤。

真实生成前另须明确记录允许使用的测试账号、固定素材、Provider 地址/启动方式和额度上限。T1.0 不调用 Provider，也不终止未知 8080 进程。

## T1.1 本机环境与端口

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
Get-ChildItem -LiteralPath "$env:WINDIR\Fonts" -File | Where-Object Name -Match 'Noto|SourceHan|msyh|simhei'
```

通过条件：Java 21 可解析；Node 满足 Web `package.json`；3306/6379 可用；18080 空闲；FFmpeg/ffprobe 可执行；冻结的 CJK 字体存在。39000/8189 未监听时只记录 Provider 缺口，仍继续安全清点；8080 无论是否占用都不由本任务结束未知进程。

## T1.2 数据库、Redis 与迁移

先读取：

- `docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`
- `docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql`
- `ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`

解析 `spring.datasource.dynamic.datasource.master` 的最终目标，只显示主机、端口和库名。数据库客户端实际选中的库与目标一致后，才可执行权威幂等初始化 SQL；不得改用 Docker 或旁路连接。核对必要迁移、两套任务表、创作项目/资产表和账号/权限基础数据。初始化 SQL 不创建真实人物或声音媒体，不能据此通过 T1.3。

通过条件：目标库明确、迁移一致、Redis 认证可用、初始化可幂等执行且未影响其他库。命令、退出码和脱敏摘要写入 `EXECUTION.md` 证据索引或其安全引用。

## T1.3 账号、真实资产与 OSS

通过本次测试账号的正常 API/Service 查询至少一个归属当前用户的 `ready` 人物和一个 `origin` 原声音，并验证后端授权媒体读取能返回对应 OSS 对象。参考入口：

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/asset-selection/AssetSelectionStep.tsx`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/digitalhuman/controller/UserDigitalHumanController.java`

不得在 SQL 中手工伪造 `ready` 状态，不把签名 URL 写入证据。没有合格资产时，通过产品正常上传/创建路径准备，T1 保持未完成。

## T1.4 Provider 与工作流身份

在不生成媒体的前提下先核对 IndexTTS2/ComfyUI 连通性、TLS、认证和工作流读取。敏感配置只注入当前 PowerShell 进程或 Git 忽略的本地配置，证据只记录“存在/缺失”。

从实际 ComfyUI 读取 `数字人口播.json`，保存到授权存储，计算 SHA-256，并记录工作流 UUID、模型加载节点的 checkpoint/LoRA 名称和环境标签。测试夹具中的 `WanVideoSampler` 或旧分支 LTX 2.3 都不能证明当前实际模型。

`scripts/test-digital-human-live.ps1` 会真实调用声音和视频 Provider，不是免费 health check。默认不运行；仅当完整应用在 Provider 边界失败、现有日志无法定位，且项目负责人另行批准诊断素材和额度时，才使用一次：

```powershell
$videoOpsRoot = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $videoOpsRoot
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

`INDEXTTS2_LIVE_OK`、`COMFYUI_LIVE_OK`、`DIGITAL_HUMAN_LIVE_OK` 只证明 Provider 边界，不能证明登录、数据库、资产、时间轴或最终渲染。

## T1.5 后端测试、构建与启动

```powershell
$videoOpsRoot = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath (Join-Path $videoOpsRoot 'ai-video-api')
.\mvnw.cmd -pl :ai-video-core,:ai-video-infra,:ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  "-Dtest=DigitalHumanGenerationServiceImplTest,DigitalHumanProviderClientTest,TimelineTaskApplicationServiceTest,TimelineTaskSchedulerTest,FfmpegTimelineMediaRenderServiceTest" test
.\mvnw.cmd -pl :ai-video-user-api -am -Dmaven.test.skip=true package

Set-Location -LiteralPath $videoOpsRoot
.\scripts\start-local-user-api.ps1 -Port 18080 -LocalConfigPath .\.runtime\user-api.local.yml
```

两条 Maven 命令须退出码 0；失败时保存首个根因和相关 surefire 报告路径，输入/环境无变化最多重试两次。根目录 `.env` 不会被 Spring 自动读取；密钥只进当前进程或 Git 忽略配置。启动后验证健康入口、登录、当前用户权限和目标业务路由，不复用未知 8080 进程作为证据。

## T1.6 无 Mock 前端验证

```powershell
$videoOpsRoot = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath (Join-Path $videoOpsRoot 'ai-video-ui\ai-video-webapp')
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

验证 `/studio` 登录、权限、真实人物/声音列表以及加载/失败状态。步骤 0～4 的 `voiceJob`/`videoJob` 上下文主要在 React 内存；创建 `projectId` 前不要刷新，并把该恢复缺陷记入剩余风险。

## T1.7 单次真实黄金样例

仅在账号、素材授权、Provider、OSS 和额度上限全部确认后执行。固定目标、脚本事实、人物和音色；已支持幂等的声音、底片、创作项目、时间轴保存和渲染动作必须保存稳定幂等键，整个意图不得换键重提。问卷和文案接口当前不接收幂等键，因此各调用一次并记为 T3 缺口。

1. 调用 `POST /api/studio/questionnaires/generate` 与 `POST /api/studio/scripts/generate`，确认 `modelMode=deepseek`。
2. 从 `GET /api/portraits`、`GET /api/voices?voiceType=origin` 选择 T1.3 资产。
3. 创建并轮询 voice job，试听后确认。
4. 创建 video job，通过 `/api/studio/jobs/{id}` 观察真实终态并预览媒体。
5. 创建 creation project，确认主视频轨和初始字幕轨。
6. 保存 timeline draft，创建 render task，观察 `av_ai_task` 到终态。
7. 读取 `outputs/latest`，通过授权内容接口下载最终 MP4。

逐步记录开始/结束时间、HTTP/业务摘要、voice/video jobId、projectId、timeline taskId、outputAssetId 和失败点。任务 ID 可脱敏，但必须证明同一因果链；提交状态未知时停止并对账。

## T1.8 成品与一致性验收

```powershell
$finalVideoPath = (Read-Host '请输入刚下载的最终 MP4 绝对路径').Trim()
Get-Item -LiteralPath $finalVideoPath
Get-FileHash -LiteralPath $finalVideoPath -Algorithm SHA256
& "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffprobe.exe" `
  -v error -show_streams -show_format -of json $finalVideoPath
```

验证 MP4 可解码、9:16、有视频和音轨、字幕可见，音视频流时长差满足现有 0.25 秒不变量；记录目标 30 秒与实际时长偏差并确认内容未截断。T1 不新设未经样本校准的目标时长阈值。`outputs/latest.taskId`、`outputAssetId` 必须与最终任务详情一致；实际运行使用的工作流 UUID/SHA/checkpoint/LoRA 必须与 T1.4 记录一致。人工播放检查身份、声音、口型、稳定性和字幕，但不能替代媒体门禁。

## T1.9 证据、独立复核与收口

在 `docs/EXECUTION.md` 记录或安全引用：完整源码哈希与工作区、`REAL/DEMO/MOCK` 标签、命令/退出码、两套任务和产物因果链、MP4/工作流 SHA-256、ffprobe 摘要、人工检查、`PASS/FAIL/NOT_RUN`、剩余风险。大型视频、原始素材、完整日志和工作流安全副本留在授权存储；仓库只放脱敏摘要和哈希。

由未实施本次样例的人只读复核本 runbook 的成功与反向验收、两套任务到最终资产的因果链、真实性标签、模型声明、未验证项、无重复 Provider 提交和秘密脱敏。复核缺失或发现关键证据缺口时不得完成 T1。

```powershell
$videoOpsRoot = (git rev-parse --show-toplevel).Trim()
Set-Location -LiteralPath $videoOpsRoot
git status --short
git diff --check
.\scripts\validate-development-standards.ps1
```

## DONE 门禁

以下全部成立才可在 `EXECUTION.md` 标记 T1 完成：

- 文案明确为 DeepSeek 模式；形象和音色来自真实授权 API。
- voice generation、video generation 和 timeline render 均有成功任务，creation project 与最终资产关联一致。
- 最终文件满足 MP4、9:16、音轨、可见字幕、0.25 秒音视频不变量、哈希和 ffprobe 门禁。
- 实际 ComfyUI 工作流 UUID、JSON SHA-256、checkpoint/LoRA 加载节点和环境标签可追溯。
- 没有重复提交真实 Provider；问卷/文案的幂等缺口已如实记录。
- 缺 Java、Provider、OSS、真实资产或证据时保持未完成，不以 Mock 代替。
- 创建 `projectId` 前刷新导致上下文丢失时如实记录，不能手工篡改状态伪造恢复。
- Provider 提交未知时只对账或转人工；测试夹具或旧分支模型名不能冒充当前工作流。
- 非实施者复核通过，所有未运行项和剩余风险均明确。
