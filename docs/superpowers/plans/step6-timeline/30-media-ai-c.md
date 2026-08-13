# 30 C：媒体与 AI 实现计划

> **负责人：C。** 本文件保留原总计划第 4 节和 6.4 任务卡。开始前必须确认当前分支起点等于 00 计划发布的 C0_SHA；不得自行修改业务状态机或公共 DTO。共享纪律见 [README](README.md)。

## 4. 媒体设备：安全进程、ASS、FFmpeg 与 AI 建议

> 本节只允许媒体设备执行。C0 接口不存在或 `HEAD != C0_SHA` 时立即停止；严格实现 C0 冻结签名，不在 infra 自行更改公共 DTO／接口，也不承担 Entity、Controller 或任务数据库状态机。

### 任务 27：建立 fail-closed 媒体配置与 Bean 装配

**风险：** 红色。禁用或配置无效必须明确不可用，生产环境不能注册假成功实现。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureProperties.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfiguration.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskScheduler.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/UnavailableTimelineMediaRenderService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/UnavailableTimelineAiSuggestionService.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructurePropertiesTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfigurationTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskSchedulerTest.java`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`，只增加 `aivideo.timeline.*` 环境变量占位。

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureProperties.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfiguration.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskScheduler.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/UnavailableTimelineMediaRenderService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/UnavailableTimelineAiSuggestionService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructurePropertiesTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfigurationTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskSchedulerTest.java
git add -- ai-video-api/ai-video-user-api/src/main/resources/application.yml
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写配置红灯测试：`enabled=false` 时装配明确抛 `46610` 的 unavailable 实现且不启动 Scheduler；`enabled=true` 时缺 ffmpeg、缺 ffprobe、相对路径、不可执行二进制、越界／不可写 work root、缺字体、非法上限、AI 开启但缺 key 均阻止真实 Bean 装配或启动。
- [ ] 属性固定包含：二进制绝对路径、批准工作根／字体根、进程超时、输出字节上限、单用户／系统并发上限，以及 AI base URL、key、model、超时和响应上限。
- [ ] 基础 `application.yml` 只写配置占位和安全默认值，`enabled` 默认 false；团队共享开发路径、密码、Token 与密钥写入并提交在两端 `application-dev.yml`，环境变量可选覆盖。
- [ ] 复用现有数字人基础设施的条件装配形式，但不要让 timeline 包依赖 digitalhuman 包的私有实现。Unavailable 实现只能稳定失败，不能返回假任务、假建议、假素材或假成功。
- [ ] Scheduler 只依赖 C1 的 `IAiTaskService`，按受控间隔调用 `recoverExpired`、`compensatePendingOutputs` 和 `dispatchNext(workerId, perUserConcurrencyLimit, systemConcurrencyLimit)`；使用稳定 worker ID、互斥重入并把已校验的单用户／系统上限原样传入 Service，不直接访问 Mapper、不读取 owner，也不在本地复制集群并发事实。Scheduler 测试覆盖禁用、空队列、参数透传、本进程系统执行槽、重入、单任务异常和关闭；同用户与集群系统上限的原子领取由后端任务 14 测试。
- [ ] 运行红灯／绿灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -Dtest=TimelineInfrastructurePropertiesTest,TimelineInfrastructureConfigurationTest,TimelineTaskSchedulerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 精确提交 `feat: 增加时间轴媒体配置`。

### 任务 28：实现真实路径防护与受控进程执行器

**风险：** 红色。参数数组不能替代路径、协议、stdin、超时与进程树控制。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/path/TimelinePathGuard.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessExecutor.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/JdkTimelineProcessExecutor.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessRequest.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessResult.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/path/TimelinePathGuardTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/process/JdkTimelineProcessExecutorTest.java`
- 新建测试辅助：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/process/FakeProcessMain.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/path/TimelinePathGuard.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessExecutor.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/JdkTimelineProcessExecutor.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessRequest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/process/TimelineProcessResult.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/path/TimelinePathGuardTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/process/JdkTimelineProcessExecutorTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/process/FakeProcessMain.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 路径红灯测试覆盖普通输入、输出已存在父目录、`..`、符号链接、Windows junction／reparse point、祖先替换、大小写／分隔符差异和 `toRealPath` 越界。
- [ ] 实现逐级 `NOFOLLOW_LINKS` 检查和真实根包含判断；禁止仅用字符串 `startsWith`。输出文件创建前验证真实父目录，创建后再次核验。
- [ ] 进程红灯测试覆盖：`;|&$()` 仍是单个参数、无 shell、stdin 立即 EOF、stdout／stderr 硬上限、非零退出、超时、取消、线程中断和只终止登记进程树。
- [ ] 执行器只接受不可变 `List<String>` 和受控环境白名单，使用 `ProcessBuilder`；不记录完整命令、路径或环境秘密。
- [ ] 用 Java `FakeProcessMain` 建测试子进程，不依赖 PowerShell、cmd、bash 或平台 sleep。
- [ ] 运行本任务两个测试类，绿灯后精确提交 `feat: 增加安全媒体进程执行器`。

### 任务 29：实现 ffprobe 严格探测与固定媒体事实

**风险：** 红色。损坏、协议越界或媒体事实不符时必须在合成前失败。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/probe/MediaProbe.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/probe/FfprobeClient.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/probe/FfprobeClientTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/probe/MediaProbe.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/probe/FfprobeClient.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/probe/FfprobeClientTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写红灯测试：合法视频／音频／图片，损坏文件，缺视频／音频流，超时长／尺寸／帧率，超大 JSON，未知字段和数值越界。
- [ ] 写协议红灯测试：用户构造的 `http`、`https`、`concat`、`crypto`、`data`、命名管道和设备 URI 均不能成为输入；只允许路径防护后的本地文件和固定 `file,pipe` 协议白名单。
- [ ] ffprobe 参数使用固定数组和机器可解析 JSON；解析只保留 C0 所需的时长、尺寸、帧率、流、格式和安全摘要。
- [ ] 运行测试，绿灯后精确提交 `feat: 增加时间轴媒体探测`。

### 任务 30：实现 ASS 安全文本编码、字幕和六种花字模板

**风险：** 红色。用户文字不能改变样式、执行 drawing 或进入滤镜表达式。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/AssTextEncoder.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/AssScriptWriter.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/TimelineFontMeasurer.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/AssTextEncoderTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/AssScriptWriterTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/TimelineFontMeasurerTest.java`
- 新建测试资源：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/ass-malicious-text.json`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/AssTextEncoder.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/AssScriptWriter.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ass/TimelineFontMeasurer.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/AssTextEncoderTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/AssScriptWriterTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ass/TimelineFontMeasurerTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/ass-malicious-text.json
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写攻击矩阵红灯测试：反斜杠、花括号、`\\N`／`\\n`／`\\h`、override tags、drawing tags、NUL、C0/C1 控制符、双向控制符、emoji 和组合字符。
- [ ] 字幕先按 C0 规范执行 NFC、码点与单行完整性校验；花字拒绝换行和危险控制符。所有允许字符经过可逆 ASS 转义。
- [ ] 脚本红灯测试证明用户文字只出现在编码后的 `Dialogue Event Text`，不进入 Style 名、字体路径、滤镜、选项或日志。
- [ ] 六种花字模板由固定代码映射到登记字体和受控 Style／Event 参数；未知模板或字体缺失直接失败。
- [ ] 字体测量使用服务器登记字体代码解析到批准字体根中的真实字体，返回确定像素宽高；测试覆盖 CJK、ASCII、emoji、组合字符、字号变化、缺字体和越界字体代码。
- [ ] 运行测试，绿灯后精确提交 `feat: 实现安全字幕与花字脚本`。

### 任务 31：构建白名单媒体计划与 FFmpeg 参数

**风险：** 红色。时间轴 JSON 不得直接拼成 FFmpeg 字符串。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlan.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlanBuilder.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/FfmpegCommandBuilder.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlanBuilderTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/FfmpegCommandBuilderTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineContractFixtureTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlan.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlanBuilder.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/FfmpegCommandBuilder.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/TimelineRenderPlanBuilderTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/FfmpegCommandBuilderTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineContractFixtureTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 契约测试从仓库根直接读取 C0 `timeline-1` Schema和固定样例；缺 C0 文件必须失败，不在媒体测试资源复制时间轴结构。
- [ ] 计划红灯测试覆盖主视频；图片 `fitMode`、裁剪、淡入淡出；画中画 `sourceStartMs`、固定静音和循环；字幕、花字、主声道；背景音乐裁剪／循环／ducking；音效、基础画面特效、层级和输出配置。
- [ ] 校验同一时间只能有一个主声道语义，背景音乐 ducking 和音效重叠按固定规则生成；计划构建器只接受后端已通过 `ICreationAssetService` 受控解析并随句柄携带的素材事实，不自行访问存储。
- [ ] 命令红灯测试断言固定包含 `-nostdin -hide_banner -nostats -progress pipe:1`、本地协议白名单、H.264/AAC/MP4 和确定帧率／像素格式。
- [ ] 受控素材先落入本次隔离目录的生成文件名（例如 `input-0001.mp4`、`overlay.ass`、`filter.txt`）；FFmpeg 使用独立参数引用固定 filter script，原始文件名和内部对象键都不进入命令或滤镜脚本。
- [ ] 文件名、元数据和恶意文字不能改变参数数量；原始文字、路径、模板名和任意选项字符串不进入 option 或 filter graph。只有经过类型校验与范围限制的时间、坐标、尺寸、音量数值由受信 builder 按固定格式序列化。
- [ ] 运行三个测试类，绿灯后精确提交 `feat: 构建时间轴媒体合成计划`。

### 任务 32：实现真实媒体渲染 Service

**风险：** 红色。infra 只完成受控技术渲染，不直接更新任务表、项目表或成品素材状态。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/FfmpegTimelineMediaRenderService.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/FfmpegTimelineMediaRenderServiceTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/render/FfmpegTimelineMediaRenderService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/render/FfmpegTimelineMediaRenderServiceTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 使用测试假进程写红灯测试：阶段／进度回调、按命令引用顺序逐个读取调用方已打开的受控句柄、工作目录隔离、先探测、生成 ASS、执行 FFmpeg、后探测、SHA-256、成功输出和关闭本次输出资源。
- [ ] 写失败红灯测试：素材缺失／损坏、字体缺失、磁盘不足、进程非零、超时、取消、后探测不合格和输出超限；只抛稳定安全错误，不假成功。
- [ ] 写清理红灯测试：成功、失败、取消都清本次媒体工作目录和未移交的输出资源，但绝不关闭调用方输入句柄；不能删除批准根、其他任务目录或已登记对象。后端编排层的测试再证明输入句柄在 render 返回或抛错后恰好关闭一次。
- [ ] 严格实现 C0 `ITimelineMediaRenderService`；它只消费 `render(...)` 参数中的 `List<CreationMediaHandle>`，不注入或调用 `ICreationAssetService`。`AiTaskServiceImpl` 在调用媒体接口前按版本引用通过 `ICreationAssetService` 打开全部句柄，并以外层 try-with-resources 统一关闭；任一素材打开失败时关闭此前已打开句柄且不调用媒体 Service。
- [ ] 运行测试，绿灯后精确提交 `feat: 实现服务端时间轴重合成`。

### 任务 33：实现 DeepSeek 建议与可信时间锚／Whisper 字幕对齐

**风险：** 红色。AI 结果是有界建议，不直接更新草稿。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/TimelineAiSuggestionServiceImpl.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/DeepSeekTimelineSuggestionClient.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/WhisperTimelineSubtitleAlignmentService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/TimelineSubtitleAlignmentMapper.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImpl.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/TimelineAiSuggestionServiceImplTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/DeepSeekTimelineSuggestionClientTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/WhisperTimelineSubtitleAlignmentServiceTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/TimelineSubtitleAlignmentMapperTest.java`
- 修改测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImplTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/TimelineAiSuggestionServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/DeepSeekTimelineSuggestionClient.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/WhisperTimelineSubtitleAlignmentService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/ai/TimelineSubtitleAlignmentMapper.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImpl.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/TimelineAiSuggestionServiceImplTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/DeepSeekTimelineSuggestionClientTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/WhisperTimelineSubtitleAlignmentServiceTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/ai/TimelineSubtitleAlignmentMapperTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImplTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 使用 JDK loopback `HttpServer` 写 DeepSeek 合法响应红灯测试，断言它只处理图片提示词和花字建议，请求只包含服务端重取的最小文案范围、允许风格、Schema 和模板白名单；DeepSeek 请求中绝不出现音频、字幕对齐或 Whisper 字段。
- [ ] 写 DeepSeek 反向红灯测试：未知字段／模板、项目外关键词、原文范围越界、响应过大、429、5xx、超时、无效 JSON 和供应商额外说明。
- [ ] 写字幕对齐双路径红灯测试：非空上游可信 cues 必须时间单调、范围合法，并在按 C0 规范化后逐 Unicode 码点等于冻结脚本文本；合法时直接生成字幕且 Whisper 零调用，非空但不匹配时返回 `CREATION_SOURCE_INVALID`，不能静默降级。只有 cues 为空时，`WhisperTimelineSubtitleAlignmentService` 才通过 C0 `CreationMediaHandle` 把当前用户主音频以 try-with-resources 交给 `IWhisperTranscriptionService.transcribe(WhisperTranscriptionInputDTO, InputStream)`，只消费 `VoiceTranscriptionResultDTO.transcriptTimeline()` 并映射到同一冻结文本。禁止构造或伪造旧 `VoiceTranscriptionLeaseDTO`，禁止传空 tenant／workspace，禁止把参考声音转写冒充生成口播时间锚。
- [ ] 写字幕对齐反向测试：上游 cue 越界／乱序／少字，Whisper 空时间线／越界／改写文本，受控句柄提前关闭、读取失败与取消；全部安全失败，不能减少、替换或扩展规范文本。
- [ ] `TimelineAiSuggestionServiceImpl` 实现 C0 接口并编排 `DeepSeekTimelineSuggestionClient`、`WhisperTimelineSubtitleAlignmentService` 与 `TimelineSubtitleAlignmentMapper`；DeepSeek 只生成图片提示词和花字建议，不负责字幕时间轴。严格拒绝未知字段，花字关键词必须来自冻结脚本，模板必须属于六种代码。
- [ ] 失败只返回稳定安全错误，不产生可应用建议、不修改时间轴、不保存供应商原始响应。
- [ ] 参考现有 DeepSeek 客户端的 JDK HttpClient、响应上限和 JSON 方式，但使用独立 `aivideo.timeline.ai.*` 配置，不能依赖问卷配置；在现有 `WhisperTranscriptionServiceImpl` 中机械实现 C0 通用重载并复用同一 HTTP 客户端，旧声音转写方法与测试行为保持不变。字幕对齐只复用该 Bean 和受控媒体句柄，不调用 DeepSeek。
- [ ] 运行测试，绿灯后精确提交 `feat: 增加时间轴 AI 建议`。

### 任务 34：固定真实媒体夹具并执行媒体专项门禁

**风险：** 红色。真实测试必须可重复，不依赖操作系统字体或网络。

**文件：**

- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/base-with-audio.mp4`
- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/pip-silent.mp4`
- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/overlay.png`
- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/primary.wav`
- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/bgm.wav`
- 新建二进制夹具：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/sfx.wav`
- 新建生产字体：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/NotoSansCJKsc-Regular.otf`
- 新建生产字体：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/NotoSerifCJKsc-Regular.otf`
- 新建许可证：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/OFL.txt`
- 新建字体登记副本：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/font-registry.json`
- 新建校验：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/SHA256SUMS`
- 新建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineRealMediaIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/base-with-audio.mp4
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/pip-silent.mp4
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/overlay.png
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/primary.wav
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/bgm.wav
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media/sfx.wav
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/NotoSansCJKsc-Regular.otf
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/NotoSerifCJKsc-Regular.otf
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/OFL.txt
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/font-registry.json
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts/SHA256SUMS
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineRealMediaIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 用仓库批准的 ffmpeg 生成 320×180、30fps、3 秒固定短夹具；生成一次后提交二进制和 SHA-256，测试运行时不动态重编码输入。
- [ ] 从仓库根执行以下固定生成命令；`AIVIDEO_TIMELINE_FFMPEG_PATH` 必须由本机环境提供，不把绝对路径写入提交：

```powershell
$ffmpegBin=$env:AIVIDEO_TIMELINE_FFMPEG_PATH
if (-not (Test-Path -LiteralPath $ffmpegBin -PathType Leaf)) { throw 'AIVIDEO_TIMELINE_FFMPEG_PATH 未指向可用 ffmpeg' }
$fixtureDir='ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media'
New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=30:duration=3' -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=3' -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest "$fixtureDir/base-with-audio.mp4"
if ($LASTEXITCODE -ne 0) { throw '生成 base-with-audio.mp4 失败' }
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=160x90:rate=30:duration=1' -an -c:v libx264 -pix_fmt yuv420p "$fixtureDir/pip-silent.mp4"
if ($LASTEXITCODE -ne 0) { throw '生成 pip-silent.mp4 失败' }
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'color=c=red:s=64x64:d=1' -frames:v 1 "$fixtureDir/overlay.png"
if ($LASTEXITCODE -ne 0) { throw '生成 overlay.png 失败' }
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=660:sample_rate=48000:duration=3' -c:a pcm_s16le "$fixtureDir/primary.wav"
if ($LASTEXITCODE -ne 0) { throw '生成 primary.wav 失败' }
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=220:sample_rate=48000:duration=1.2' -c:a pcm_s16le "$fixtureDir/bgm.wav"
if ($LASTEXITCODE -ne 0) { throw '生成 bgm.wav 失败' }
& $ffmpegBin -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=1200:sample_rate=48000:duration=0.2' -c:a pcm_s16le "$fixtureDir/sfx.wav"
if ($LASTEXITCODE -ne 0) { throw '生成 sfx.wav 失败' }
Get-ChildItem -LiteralPath $fixtureDir -File | Sort-Object Name | Get-FileHash -Algorithm SHA256
```

- [ ] 按任务 2 的唯一 registry 从 Noto 官方固定提交 vendoring 两个静态 OTF：Sans SHA `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`，Serif SHA `2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca`，OFL SHA `6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2`。下载后先核对摘要再提交；测试和生产运行时不联网，禁止复制或回退操作系统字体。
- [ ] 后端启动、文字测量和每次渲染前都校验 registry 与 OTF 摘要，只把已校验字体复制到本任务隔离 fontsdir；生成 ASS 前检查所选字体 cmap 覆盖全部保留码点，缺字返回 `46611`，防止 libass 静默使用系统 fallback。后端 registry 副本必须与 C0 字节一致，字体二进制必须与前端副本逐字节一致。
- [ ] 真实 IT 用 320×180 的短输入夹具合成包含七类可添加元素的首版竖屏短片，用 ffprobe 断言输出 MP4、H.264、AAC、`1080×1920`、30fps、时长容差、音视频流和可验证的字幕／花字帧差异；不得为测试绕过 C0 画布白名单。
- [ ] 在 `@TempDir` 运行时生成损坏文件、越界路径、符号链接和 reparse 场景；不提交平台脆弱夹具。
- [ ] 运行单元与真实媒体：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am -Pdev -Dmaven.test.skip=false -DskipTests=false '-Dtest=**/timeline/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=TimelineRealMediaIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

- [ ] 运行 `git diff --check`，发起独立媒体安全审查，重点覆盖 ASS、参数注入、协议、stdin、real path、symlink／reparse、超时、取消、进程树、输出上限和敏感日志。
- [ ] 修复必须修复项并定向复核，推送 `codex/step6-media`，创建面向集成分支的 PR。

## 6. 本角色最小任务卡

### 6.4 媒体设备任务卡

- **单一目标：** 完成任务 27 至任务 34，交付受控 ffprobe／FFmpeg／ASS 和三类 AI 建议的 C0 接口实现。
- **禁止事项：** 不改 Controller、Entity、Mapper、任务状态机、C0 或成品业务事务；不使用 shell，不保存完整命令或供应商原始响应。
- **权威输入：** `C0_SHA`、本计划第 4 节、媒体安全规则和唯一契约夹具。
- **独占路径：** infra `timeline/**`、对应测试与固定媒体／字体资源，`application.yml` 的 `aivideo.timeline.*` 命名空间。
- **交付证据：** 单元测试、真实 FFmpeg 短片、ffprobe 输出事实、攻击矩阵、路径／协议／进程安全审查、SHA／许可证和 PR。
- **停止条件：** 需要业务状态或公共 DTO 变化时提交契约变更卡；不在 infra 增加平行业务层。
