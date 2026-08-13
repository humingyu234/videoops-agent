# 00 公共契约基线计划

> **负责人：A。** 本文件保留原总计划第 1 节和 6.1 任务卡。共享纪律、分支规则和受控测试配置见 [README](README.md)。完成后必须将公共契约合入主分支并发布完整 C0_SHA，10、20、30 才能开始。

## 1. C0：冻结契约、迁移与跨模块骨架

### 任务 1：建立集成分支、基线记录与 C0 所有权清单

**风险：** 红色。此任务只建立协作事实，不写业务实现。

**文件：**

- 新建：`docs/contracts/creation-timeline/ownership-manifest.md`
- 验证：`docs/superpowers/specs/2026-08-08-creation-step-6-full-stack-timeline-design.md`

- [ ] 在集成设备从当前远程主分支创建并推送 `codex/step6-integration`，记录完整 `BASE_SHA`。
- [ ] 从 `codex/step6-integration` 创建 `codex/step6-contract`，确认两者起点相同。
- [ ] 先写契约测试思路到所有权清单：每个 C0 新增文件必须只有一个负责人，三个功能分支不得修改 C0 文件。
- [ ] 在清单中逐项登记任务 2 至任务 6 的文件，以及后端、前端、媒体、集成四类独占路径；同时写明 `C0_SHA` 只发布在集成 PR／交付消息中，不能写入产生该 SHA 的提交。前端共享文件按第 0.2 节逐项唯一登记；本轮明确禁止修改 `package-lock.json`，集成负责人不得在冲突解决中二次编辑这些前端共享文件。
- [ ] 执行失败检查：`rg -n "C0_SHA|BASE_SHA|codex/step6-backend|codex/step6-ui|codex/step6-media" docs/contracts/creation-timeline/ownership-manifest.md`。首次应因文件不存在或记录不全而失败。
- [ ] 完成清单后重跑同一命令，应命中五类记录；再运行 `scripts/validate-development-standards.ps1`，预期输出 `DEVELOPMENT_STANDARDS_OK`。
- [ ] 精确提交：

```powershell
git add -- docs/contracts/creation-timeline/ownership-manifest.md
git commit -m "docs: 建立时间轴契约所有权清单"
```

### 任务 2：创建唯一 `timeline-1` Schema 与固定样例

**风险：** 红色。所有实现只能直接读取这些夹具，禁止复制维护第二套样例。

**文件：**

- 新建：`docs/contracts/creation-timeline/timeline-1.schema.json`
- 新建：`docs/contracts/creation-timeline/project.example.json`
- 新建：`docs/contracts/creation-timeline/timeline-draft.example.json`
- 新建：`docs/contracts/creation-timeline/timeline-task.example.json`
- 新建：`docs/contracts/creation-timeline/timeline-errors.example.json`
- 新建：`docs/contracts/creation-timeline/subtitle-normalization.example.json`
- 新建：`docs/contracts/creation-timeline/font-registry.json`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineContractLimits.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineContractFixtureTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- docs/contracts/creation-timeline/timeline-1.schema.json
git add -- docs/contracts/creation-timeline/project.example.json
git add -- docs/contracts/creation-timeline/timeline-draft.example.json
git add -- docs/contracts/creation-timeline/timeline-task.example.json
git add -- docs/contracts/creation-timeline/timeline-errors.example.json
git add -- docs/contracts/creation-timeline/subtitle-normalization.example.json
git add -- docs/contracts/creation-timeline/font-registry.json
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineContractLimits.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineContractFixtureTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 在 core 模块增加唯一生产依赖 `com.networknt:json-schema-validator:3.0.1`（不标记 test scope），后端保存和任务创建统一使用它执行 JSON Schema Draft 2020-12 校验；测试也复用同一个已配置 validator，不另写简化校验器。该 POM 由 C0 契约负责人独占，三个功能分支不得修改。写失败测试，读取七个固定文件并断言它们存在、JSON 可解析、Schema 版本等于 `timeline-1`、所有 HTTP 大整数都是十进制字符串。
- [ ] 测试必须覆盖轨道固定顺序：上方视觉轨道、中心主视频、下方音频轨道；元素 ID 在单文档内唯一；时间全部为非负整数毫秒。
- [ ] 测试必须覆盖图片、画中画视频、字幕、花字、背景音乐、音效、画面特效七类可添加元素和基础 `main_video`／`primary_audio`；同时冻结六种花字模板、图片 `fitMode=contain|cover`、规范裁剪框、淡入淡出，画中画 `sourceStartMs`、`loopWhenOverflow=true`、`audioEnabled=false`，以及背景音乐的固定 ducking 规则。
- [ ] 测试必须覆盖 `storageKey`、内部路径、任意 URL、凭据、租户字段、工作区字段不允许进入时间轴 JSON。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=TimelineContractFixtureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期失败原因：固定文件尚不存在或 Schema 校验失败。

- [ ] 先复制以下根结构作为 Schema 红灯对应的最小生产代码；根对象精确且只允许 `schemaVersion`、`canvas`、`tracks`。`durationMs` 只能在 `canvas`，元素只能在 `tracks[].elements`，输出配置属于 render 请求而不进入时间轴根对象；根、画布、轨道和每种元素全部使用 `additionalProperties:false`。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:aivideo:creation-timeline:timeline-1",
  "type": "object",
  "required": ["schemaVersion", "canvas", "tracks"],
  "properties": {
    "schemaVersion": { "const": "timeline-1" },
    "canvas": { "$ref": "#/$defs/canvas" },
    "tracks": { "type": "array", "minItems": 1, "maxItems": 32, "items": { "$ref": "#/$defs/track" } }
  },
  "additionalProperties": false,
  "$defs": {
    "canvas": {
      "type": "object",
      "required": ["width", "height", "frameRate", "durationMs", "safeMarginRatio"],
      "properties": {
        "width": { "const": 1080 },
        "height": { "const": 1920 },
        "frameRate": { "const": 30 },
        "durationMs": { "type": "integer", "minimum": 1, "maximum": 120000 },
        "safeMarginRatio": { "const": 0.05 }
      },
      "additionalProperties": false
    },
    "track": {
      "type": "object",
      "required": ["trackId", "trackType", "area", "order", "locked", "muted", "elements"],
      "properties": {
        "trackId": { "type": "string", "minLength": 1, "maxLength": 64, "pattern": "^[A-Za-z0-9_-]+$" },
        "trackType": { "enum": ["fancy_text", "subtitle", "visual_effect", "image_overlay", "pip_video", "main_video", "primary_audio", "background_music", "sound_effect"] },
        "area": { "enum": ["top", "center", "bottom"] },
        "order": { "type": "integer", "minimum": 0, "maximum": 31 },
        "locked": { "type": "boolean" },
        "muted": { "type": "boolean" },
        "elements": { "type": "array", "maxItems": 512, "items": { "$ref": "#/$defs/element" } }
      },
      "additionalProperties": false
    }
  }
}
```

- [ ] 在同一 Schema 中补齐七类可判别元素 `$defs`，并由 Service 增加 JSON Schema 无法表达的“全部轨道合计最多 2000 个元素”校验。全部数值上限同时登记在 Schema 的 `x-ai-video-limits` 与 `TimelineContractLimits`，契约测试逐项反射比对，防止 Java 与 Schema 漂移；C0 数值与白名单不得留给功能分支选择：

| 契约项 | `timeline-1` 固定值 |
| --- | --- |
| 规范 JSON 大小 | UTF-8 最多 `1,048,576` bytes、嵌套深度最多 `16`；保存前后都检查 |
| 轨道／元素／素材 | `1..32` 条轨道；单轨最多 `512`；全项目最多 `2000`；不同素材最多 `256`；素材引用最多 `2000` |
| 时长／画布 | `durationMs=1..120,000`；首版只允许 `1080×1920`、`30fps`、`safeMarginRatio=0.05` |
| 字符串／数组 | element/track/client key 最多 `64` ASCII；label `128` 码点；单字幕 source/display 各 `512` 码点；花字 `128`；项目脚本快照 `50,000`；AI 建议最多 `20` 条、单提示词 `2,048` 码点、理由 `256`、标签最多 `16×32` 码点；normalizationChanges 最多 `256`；任务 request/result JSON 各 `65,536` bytes；安全摘要 `512` 码点 |
| 图片上传 | JPEG/PNG/WebP；最多 `20 MiB`；宽高各 `1..8192` |
| 视频上传 | MP4/QuickTime/WebM；最多 `1 GiB`；最长 `120,000ms`；最大 `3840×2160`、`60fps` |
| 音频上传 | MP3/WAV/M4A/AAC；最多 `256 MiB`；最长 `120,000ms`；最大 `192kHz`、`8` 声道 |
| 输出配置 | `resolutionPreset=match_canvas`、`frameRate=30`；质量只允许 `standard(CRF 23,preset medium)`、`high(CRF 18,preset slow)` |
| 输出编码 | 服务端固定 MP4/H.264/yuv420p/AAC；请求不得携带 codec、pixel format、FFmpeg 参数或滤镜字符串 |
| 图片默认 | `fitMode=contain`；crop `xRatio=0,yRatio=0,widthRatio=1,heightRatio=1`；先裁剪再适配；fadeIn/fadeOut 默认为 `0`、各不超过元素时长且总和不超过元素时长 |
| 画中画默认 | `sourceStartMs=0`；`loopWhenOverflow=true`；`audioEnabled=false` 且首版不允许改为 true |
| 背景音乐默认 | `volumeRatio=0.30`、`loopWhenOverflow=true`；`duckingEnabled=true`、`targetGainRatio=0.35`、`attackMs=120`、`releaseMs=400`；只由唯一 `primary_audio` 触发 |
| 基础画面特效 | `fade_in`、`fade_out`（`durationMs=100..3000`）；`gentle_zoom_in`、`gentle_zoom_out`（`scale=1.00..1.20`）；`light_blur`（`radius=0.5..12.0`）；拒绝其他参数 |
| 登记字体 | 仅 `noto_sans_cjk_sc_regular` 与 `noto_serif_cjk_sc_regular`；保存 `fontCode + fontVersion + fontSha256`，不可变合成输入同时冻结 `fontRegistryVersion=timeline-fonts-1` |

- [ ] Schema 不依赖 `default` 关键字补齐持久化事实：创建元素时前端写入完整默认字段，服务端收到缺字段文档直接拒绝。图片 crop 必须满足 `x/y∈[0,1)`、`width/height∈(0,1]` 且右／下边界不超过 1；画中画和背景音乐的 `sourceDurationMs` 必须等于服务端重新探测且大于 0，`0<=sourceStartMs<sourceDurationMs`。画中画超时按 `sourceStartMs + (局部毫秒 mod (sourceDurationMs-sourceStartMs))` 循环；背景音乐 ducking 把 `volumeRatio` 过渡到 `volumeRatio*targetGainRatio`，音效不得携带 ducking 且不得自动循环。
- [ ] 冻结剩余白名单：花字模板 `keyword_pop|gold_impact|neon_breathe|handwriting_reveal|bubble_bounce|title_wipe`；字幕对齐 `left|center|right`；安全区锚点 `upper|center|lower`；动画强度 `subtle|normal|strong`；AI 图片风格 `photorealistic|cinematic|illustration|minimal`；所有颜色为大写 `#RRGGBBAA`。规范小数最多四位，摘要前去掉无意义尾零，禁止 NaN、无穷值和科学计数法。
- [ ] 语义校验固定：恰好一个 `main_video` 轨道，位于 `center`、`order=0`、`locked=true`；上方依次是花字、字幕、画面特效、图片／画中画及派生子轨，下方依次是主配音、背景音乐、音效及派生子轨。视觉轨道与同名 elementType 一一对应；`primary_audio|background_music|sound_effect` 三类轨道都只容纳 `elementType=audio`，并分别要求 `usageType=primary_audio|background_music|sound_effect`。元素编号全文档唯一，`0<=startMs<endMs<=canvas.durationMs`，坐标／尺寸／透明度位于 0..1、旋转角 -180..180、zIndex 0..999。

- [ ] `font-registry.json` 是前后端唯一字体登记源，精确冻结以下两个静态 OTF；实施时从固定提交下载并核对 SHA 后提交，运行与测试阶段均不得联网或读取操作系统同名字体：

| fontCode | 字体与版本 | 固定官方提交文件 | SHA-256 |
| --- | --- | --- | --- |
| `noto_sans_cjk_sc_regular` | Noto Sans CJK SC Regular 2.004 | `https://raw.githubusercontent.com/notofonts/noto-cjk/523d033d6cb47f4a80c58a35753646f5c3608a78/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf` | `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b` |
| `noto_serif_cjk_sc_regular` | Noto Serif CJK SC Regular 2.003 | `https://raw.githubusercontent.com/notofonts/noto-cjk/9b0f1436e455d902de067a2501422e5dc71ad16b/Serif/OTF/SimplifiedChinese/NotoSerifCJKsc-Regular.otf` | `2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca` |

  OFL 许可证登记摘要固定为 `6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2`。登记项还必须包含 familyName、PostScriptName、weight `400`、文件名、固定提交 URL 和许可证摘要；C0 测试校验代码、文件名和摘要一一对应。

- [ ] 在 `TimelineContractFixtureTest` 增加真实代码级断言，至少直接验证根字段和边界失败，预期首次因 Schema／夹具不存在而失败：

```java
@Test
void timelineRootHasExactlyThreeFieldsAndRejectsLimits() throws Exception {
    JsonNode sample = mapper.readTree(contract("timeline-draft.example.json"));
    JsonNode timeline = sample.required("timeline");
    assertThat(iterable(timeline.fieldNames()))
        .containsExactlyInAnyOrder("schemaVersion", "canvas", "tracks");
    assertThat(validate(schema(), timeline)).isEmpty();
    assertThat(utf8CanonicalBytes(timeline)).isLessThanOrEqualTo(1_048_576);
    assertThat(totalElements(timeline)).isLessThanOrEqualTo(2_000);
    assertThat(distinctAssetIds(timeline)).hasSizeLessThanOrEqualTo(256);
    assertThat(validate(schema(), timeline.deepCopy().put("durationMs", 1))).isNotEmpty();
    assertThat(validate(schema(), timeline.deepCopy().put("output", "high"))).isNotEmpty();
}
```
- [ ] 编写成功项目、草稿、任务、错误样例；所有编号使用不会被 JavaScript 安全整数截断的十进制字符串。
- [ ] 编写字幕规范化样例，明确 Unicode NFC、按 Unicode 码点计数、标点与空白删除、小数点和 emoji 处理、稳定拆分 ID、字号调整与连续时间段。
- [ ] 运行绿灯：同一 Maven 命令应通过。
- [ ] 按本任务“文件”清单逐项精确暂存七个契约文件、core POM 和测试，提交 `feat: 冻结时间轴 JSON 契约`。

### 任务 3：前向同步四份公共契约

**风险：** 红色。公共文档必须消除与冻结规格冲突的旧表述。

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`

**绿灯后的精确暂存命令：**

```powershell
git add -- docs/API_CONTRACT.md
git add -- docs/DOMAIN_MODEL.md
git add -- docs/ASYNC_TASKS.md
git add -- docs/ARCHITECTURE.md
git diff --cached --name-only
git diff --cached --check
```

- [ ] 在 `docs/API_CONTRACT.md` 登记 `/api/studio/creation-projects`、`/api/studio/creation-assets` 与统一 `/api/tasks` 的完整资源、请求／响应、字符串 ID、权限和稳定错误码。
- [ ] 统一任务动作路径固定为 `GET /api/tasks`、`GET /api/tasks/{taskId}`、`POST /api/tasks/{taskId}/cancellations`、`POST /api/tasks/{taskId}/retry`；取消是一次资源化请求，主动重试创建新根任务并返回新任务，不复制时间轴专用任务查询接口。
- [ ] `GET /api/studio/creation-assets/{assetId}/content` 固定为认证后的受控二进制读取：完整读取返回 200，单 Range 返回 206 与 `Content-Range`，非法／多 Range 返回 416；JSON 和时间轴 DTO 不返回内部对象键或临时 URL。
- [ ] 明确初始化请求只接收 `sourceType`、`sourceId`、可选标题和幂等键；草稿保存只接收幂等键、预期修订、Schema 版本和时间轴。
- [ ] 冻结 `POST /api/studio/creation-projects/{projectId}/timeline-versions/conflict-copies`：请求精确且只包含 `idempotencyKey`、`baseRevision`、`schemaVersion`、`timeline`。服务端重新执行归属、素材、Schema、字幕、字体和安全区校验，在一个短事务中创建不可变版本、版本素材引用与写回执，但绝不修改当前草稿或当前修订；`version_reason=conflict_copy`、`operation_type=conflict_version`、`source_draft_revision=baseRevision`。同键同摘要回放原版本，同键异摘要返回 `TIMELINE_IDEMPOTENCY_CONFLICT`。
- [ ] 在 `docs/DOMAIN_MODEL.md` 登记九张表、无物理外键、逻辑关联、全部索引、不可变事实、素材删除保护、`owner_user_id` 与 typed actor。
- [ ] 登记 `AppAuditRequired`、`AuditFillContext<Long>`、请求过滤器和 Worker 冻结主体的 fail-closed 审计规则。
- [ ] 在 `docs/ASYNC_TASKS.md` 登记根任务、执行、尝试、CAS（比较并交换）、租约、尝试创建时机、终态不可逆、四种任务类型、免费策略版本和轮询停止条件。
- [ ] 在 `docs/ARCHITECTURE.md` 登记第 5 步来源 → 项目／草稿 → 第 6 步 → 固定版本／任务 → 媒体 Worker → 第 7 步的完整数据流。
- [ ] 运行失败检查，确认不存在把新表描述为含租户或工作区归属、把旧数字人任务伪装为统一任务、或把浏览器作为最终合成器的文字：

```powershell
rg -n "av_creation_|av_timeline_|av_ai_task|timeline-1|AppAuditRequired|timeline_render" docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ASYNC_TASKS.md docs/ARCHITECTURE.md
```

首次应存在缺项；完成后每个主题都必须命中权威章节。

- [ ] 运行 `scripts/validate-development-standards.ps1`，预期 `DEVELOPMENT_STANDARDS_OK`。
- [ ] 精确暂存四份公共文档并提交 `docs: 冻结时间轴公共契约`。

### 任务 4：编写唯一数据库迁移和迁移测试

**风险：** 红色。迁移只由契约负责人修改，C0 共享后不可重写。

**文件：**

- 新建：`docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationContractTest.java`
- 新建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationIT.java`
- 新建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelinePermissionIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationContractTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationIT.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelinePermissionIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写失败的文本契约测试，断言迁移恰好创建 `av_creation_asset`、`av_creation_project`、`av_timeline_draft`、`av_timeline_version`、`av_timeline_asset_ref`、`av_timeline_write_receipt`、`av_ai_task`、`av_ai_task_execution`、`av_ai_task_attempt`。
- [ ] 断言所有表使用明确主键和索引、没有 `FOREIGN KEY`、没有租户／工作区归属列、全部业务 ID 使用 `BIGINT`，时间轴内容使用 MySQL `JSON`。
- [ ] 先确认远程 C0 基线的权限编号仍只使用到 `1000024`、`app_role_permission.id` 仍只使用到 `1000224`；迁移固定新增权限 `1000025..1000031` 与绑定 `1000225..1000231`，逐对映射到 `aivideo:creation:query`、`aivideo:creation:edit`、`aivideo:creation:generate`、`aivideo:creation-asset:query`、`aivideo:creation-asset:upload`、`aivideo:creation-asset:delete`、`aivideo:task:retry`。文本测试必须同时检查 permission ID、permission code、binding ID、`role_id=1000101`、两组主键和 `(role_id,permission_id)` 均无冲突；既有 `aivideo:task:query` 与 `aivideo:task:cancel` 不重复创建。
- [ ] 断言七个新增权限全部授权给个人创作者角色 `1000101`；沿用现有防漂移 guard、`INSERT ... WHERE NOT EXISTS`、角色 `role_revision` 和受影响 app 用户 `permission_revision` 更新惯例，不用会静默覆盖漂移记录的 `ON DUPLICATE KEY UPDATE`。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=CreationTimelineMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 编写迁移，完整落实规格第 5.2 至 5.4 节字段、唯一键、反查索引、状态检查和审计列；不可变表不增加逻辑删除列。
- [ ] `av_ai_task` 冻结可空 `result_payload_json JSON` 与 `result_payload_schema_version VARCHAR(32)`，规范 UTF-8 序列化不得超过 `65,536` bytes。三类建议任务 success 必须写匹配的强类型结果 payload 且 `result_asset_id` 为空；`timeline_render` success 必须写 ready `result_asset_id` 且结果 payload 为空；queued/running/failed/cancelled 均不得残留成功 payload。迁移契约和 IT 逐状态断言。
- [ ] 迁移中 `av_timeline_version.version_reason` 的 CHECK 明确允许 `manual_save|restored|render_input|conflict_copy`，`av_timeline_write_receipt.operation_type` 明确允许 `draft_save|manual_version|version_restore|conflict_version`；`conflict_copy` 只创建不可变版本和版本引用，不能推进草稿 revision。文本测试至少直接断言以下稳定编号片段，禁止用运行时 `MAX(id)+1`：

```sql
-- 固定权限编号；完整迁移仍须使用项目既有防漂移 guard 与 INSERT ... WHERE NOT EXISTS。
-- permission: 1000025..1000031
-- app_role_permission: 1000225..1000231
-- personal creator role: 1000101
```
- [ ] 每个 IT 独立建立 identity 基线：断言 `1000101` 是 active `personal_creator/personal` 角色，断言两组新增 ID 与七个 permission code 全部不存在，并快照角色行以及所有会被授权刷新影响的 app 用户 `permission_revision`、审计字段和更新时间。任何基线不符立即失败，不尝试“修好”共享测试身份数据。
- [ ] 写 `CreationTimelineMigrationIT`：只允许连接 `LocalIntegrationEnvironment` 验证过的专用 `ai_video_test`，启动前断言九个目标表不存在，执行原始迁移一次，查询 `information_schema` 校验表、列、索引、CHECK 和无外键；`finally` 按固定顺序精确删除本次 `1000225..1000231` 绑定与 `1000025..1000031` 权限、恢复快照中的角色／app 用户 revision 与审计字段，再按显式九表名单逆序删除本次创建对象，最后重新断言 identity 与启动前字节级事实相同。禁止拼接未校验名称、清空整个库或遗留权限副作用。
- [ ] 写 `CreationTimelinePermissionIT`：独立建立同一基线、执行迁移、验证七个权限／绑定唯一且角色 `1000101` 已获授权，并使用相同 `finally` 恢复；不影响既有 task query／cancel 权限。两个 IT 分开运行，不能依赖测试类顺序或共享数据库残留。
- [ ] MySQL DDL 不承诺事务回滚。每张表创建前先验证“不存在，或完整结构指纹与 C0 一致”，使用 `CREATE TABLE IF NOT EXISTS` 后立即做字段／索引／CHECK／无外键后置断言，同名异构表 fail closed；权限写入必须排在全部 DDL 验证之后。恢复 IT 先注入一个错误结构的后序表（例如 `av_timeline_write_receipt`），断言迁移停止、前序正确表可以保留，但权限／绑定／角色和用户 revision 均未改变；删除该测试注入表后重跑 `_01` 必须成功，第三次重放的表结构、权限、绑定和 revision 必须完全稳定。
- [ ] 数据库采用 expand-only：应用回滚不得运行 down migration、删除新表或删除新权限。交付演练先应用 C0 迁移，再启动 `BASE_SHA` 对应的上一版 user-api，验证旧认证、个人信息、Studio 和既有任务查询／取消链路不受新增对象影响；随后切回新版并确认迁移重放为 no-op。部分失败时保留现场、修复环境并前向重跑；若 C0 已共享且结构确需修正，只能按一次 C1 新增 `_02_creation_timeline_c1.sql`。紧急人工清理脚本不进入自动流程，必须逐行校验映射并同步恢复角色／用户权限 revision。
- [ ] 运行文本契约绿灯，再用受控环境运行：

```powershell
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=CreationTimelineMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=CreationTimelinePermissionIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

- [ ] 按“文件”清单逐项精确暂存迁移、一个契约测试和两个 IT，执行 `git diff --cached --check` 后提交 `feat: 建立时间轴数据契约`。

### 任务 5：冻结时间轴、创作素材和任务 DTO／枚举

**风险：** 红色。这里只创建跨模块稳定类型，不写 Mapper、Service 实现或 Controller。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineDocumentDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCanvasDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTrackDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMainVideoElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImageElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelinePipVideoElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAudioElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualEffectElementDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualTransformDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCropDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFadeDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAssetReferenceDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineNormalizationChangeDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureCommandDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderCommandDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineOutputConfigDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMediaProbeDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineProgressDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptCommandDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionCommandDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentCommandDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineElementType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackArea.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineFitMode.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVisualEffectCode.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineOutputQuality.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineAssetUsageType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineDocumentType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVersionReason.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/FancyTextTemplateCode.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetUploadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetQueryDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetResolveDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/DigitalHumanCreationSourceDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RegisterPendingRenderOutputDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/PendingRenderOutputDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputReadyDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputFailureDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetStatus.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetUsageOrigin.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/CreateFreeAiTaskDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRequestPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRenderPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskResultPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptResultPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextResultPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentResultPayloadDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSummaryDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskQueryDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskLeaseDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskProgressDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskCompletionDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/RetryAiTaskDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDispatchResultDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/WhisperTranscriptionInputDTO.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStatus.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskExecutionStatus.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskAttemptStatus.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStage.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskResourceType.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineErrorCodes.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineDtoContractTest.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskDtoContractTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineDocumentDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCanvasDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTrackDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMainVideoElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImageElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelinePipVideoElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAudioElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualEffectElementDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualTransformDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCropDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFadeDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAssetReferenceDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineNormalizationChangeDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureCommandDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderCommandDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineOutputConfigDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMediaProbeDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineProgressDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptCommandDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionCommandDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentCommandDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineElementType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackArea.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineFitMode.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVisualEffectCode.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineOutputQuality.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineAssetUsageType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineDocumentType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVersionReason.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/FancyTextTemplateCode.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetUploadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetQueryDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetResolveDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/DigitalHumanCreationSourceDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RegisterPendingRenderOutputDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/PendingRenderOutputDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputReadyDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputFailureDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetStatus.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetUsageOrigin.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/CreateFreeAiTaskDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRequestPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRenderPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskResultPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptResultPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextResultPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentResultPayloadDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSummaryDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskQueryDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskLeaseDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskProgressDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskCompletionDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/RetryAiTaskDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDispatchResultDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/WhisperTranscriptionInputDTO.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStatus.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskExecutionStatus.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskAttemptStatus.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStage.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskResourceType.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineErrorCodes.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineDtoContractTest.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskDtoContractTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] C0 负责人先把以下字段矩阵逐项复制进反射测试；名称、顺序、Java 类型、可空性与上限均是冻结契约，不允许后端、前端或媒体分支自行增删：

| 类型 | 精确字段（按构造顺序） |
| --- | --- |
| `TimelineDocumentDTO` | `String schemaVersion, TimelineCanvasDTO canvas, List<TimelineTrackDTO> tracks` |
| `TimelineCanvasDTO` | `int width, int height, int frameRate, long durationMs, BigDecimal safeMarginRatio` |
| `TimelineTrackDTO` | `String trackId, TimelineTrackType trackType, TimelineTrackArea area, int order, boolean locked, boolean muted, List<TimelineElementDTO> elements` |
| `TimelineElementDTO` | sealed interface；公共访问器固定为 `elementId, elementType, startMs, endMs, zIndex, enabled, locked, label`，Jackson 判别值精确为 `main_video|image_overlay|pip_video|subtitle|fancy_text|audio|visual_effect`，分别映射七个强类型 record；背景音乐／音效由 audio 的 usage 区分 |
| `TimelineMainVideoElementDTO` | 公共字段 + `String assetId, long sourceDurationMs,sourceStartMs, TimelineFitMode fitMode`；只能位于唯一锁定主视频轨 |
| `TimelineVisualTransformDTO` | `BigDecimal xRatio,yRatio,widthRatio,heightRatio,rotationDeg,opacity` |
| `TimelineCropDTO` | `BigDecimal xRatio,yRatio,widthRatio,heightRatio` |
| `TimelineFadeDTO` | `long fadeInMs,fadeOutMs` |
| `TimelineImageElementDTO` | 公共字段 + `String assetId, TimelineVisualTransformDTO transform, TimelineFitMode fitMode, TimelineCropDTO crop, TimelineFadeDTO fade, int sourceStartOffset,sourceEndOffset, String adoptedPrompt,sourceTaskId` |
| `TimelinePipVideoElementDTO` | 公共字段 + `String assetId, TimelineVisualTransformDTO transform, TimelineFitMode fitMode, TimelineCropDTO crop, TimelineFadeDTO fade, long sourceDurationMs,sourceStartMs, boolean loopWhenOverflow,audioEnabled`；首版 `loop=true/audio=false` |
| `TimelineSubtitleElementDTO` | 公共字段 + `String sourceTextSnapshot,displayText; int sourceStartOffset,sourceEndOffset; String fontCode,fontVersion,fontSha256; int fontSizePx; String color; boolean backgroundEnabled; String backgroundColor; boolean outlineEnabled; String outlineColor; int outlineWidthPx; String safeAreaAnchor,alignment` |
| `TimelineFancyTextElementDTO` | 公共字段 + `String text; FancyTextTemplateCode templateCode; String fontCode,fontVersion,fontSha256,color,accentColor; TimelineVisualTransformDTO transform; String animationIntensity; long enterDurationMs,exitDurationMs; String suggestionTaskId,suggestionReason` |
| `TimelineAudioElementDTO` | 公共字段 + `String assetId; TimelineAssetUsageType usageType; long sourceDurationMs,sourceStartMs,sourceEndMs; BigDecimal volumeRatio; TimelineFadeDTO fade; boolean loopWhenOverflow; boolean duckingEnabled; BigDecimal targetGainRatio; int attackMs,releaseMs` |
| `TimelineVisualEffectElementDTO` | 公共字段 + `TimelineVisualEffectCode effectCode, long durationMs, BigDecimal scale,radius`；不接收任意参数 map |
| `TimelineAssetReferenceDTO` | `String assetId, TimelineAssetUsageType usageType, List<String> elementIds, String sha256, long fileSize` |
| `TimelineNormalizationChangeDTO` | `String elementId, String changeType, String beforeDigest, String afterDigest, String safeMessage`；数组最多 256，不返回完整敏感内容 |
| `TimelineTextMeasureCommandDTO` | `String requestId,fontCode,text, int fontSizePx,canvasWidthPx,outlineWidthPx, BigDecimal safeMarginRatio` |
| `TimelineTextMeasureResultDTO` | `String requestId,fontCode,fontVersion,fontSha256,fontRegistrySha256; int widthPx,heightPx; boolean allCodePointsSupported` |
| `TimelineOutputConfigDTO` | `String resolutionPreset, int frameRate, TimelineOutputQuality qualityPreset`；`resolutionPreset` 只允许 `match_canvas` |
| `TimelineMediaProbeDTO` | `String assetId, String mediaType,formatName, long durationMs,fileSize, Integer width,height,frameRate,sampleRate,channels, boolean videoStream,audioStream` |
| `TimelineRenderCommandDTO` | `String taskId,executionId,attemptId,inputVersionId,fontRegistryVersion,fontRegistrySha256, TimelineDocumentDTO timeline, TimelineOutputConfigDTO outputConfig, List<TimelineAssetReferenceDTO> assets` |
| `TimelineRenderResultDTO` | `String fileName,contentType,sha256, long fileSize,durationMs, int width,height,frameRate`；不含路径、对象键或流 |
| `TimelineProgressDTO` | `AiTaskStage stage, int percent, String safeMessage`；message 最多 200 字且不得含路径／命令／供应商正文 |
| `TimelineImagePromptCommandDTO` | `String taskId,projectId,draftRevision, int sourceStartOffset,sourceEndOffset, String sourceText,contextBefore,contextAfter,canvasAspect,styleCode` |
| `TimelineImagePromptResultDTO` | `String taskId, List<Suggestion> suggestions`；嵌套 `Suggestion(String prompt,negativePrompt,List<String> styleTags,String reason)`，最多 20 |
| `TimelineFancyTextSuggestionCommandDTO` | `String taskId,projectId,draftRevision, int sourceStartOffset,sourceEndOffset, String sourceText,contextBefore,contextAfter, List<FancyTextTemplateCode> allowedTemplates` |
| `TimelineFancyTextSuggestionResultDTO` | `String taskId, List<Suggestion> suggestions`；嵌套 `Suggestion(String sourceText,int sourceStartOffset,int sourceEndOffset,long startMs,durationMs,FancyTextTemplateCode templateCode,BigDecimal xRatio,yRatio,String primaryColor,accentColor,reason)`，最多 20 |
| `TimelineSubtitleAlignmentCommandDTO` | `String taskId,projectId,draftRevision,primaryAudioAssetId,scriptTextSnapshot,language, List<TrustedCue> trustedCues`；嵌套 `TrustedCue(String text,long startMs,endMs)`，无可信 cue 时数组为空 |
| `TimelineSubtitleAlignmentResultDTO` | `String taskId,String sourceType,List<AlignedSubtitle> subtitles`；sourceType 仅 `trusted_cue|whisper`；嵌套 `AlignedSubtitle(int sourceStartOffset,sourceEndOffset,String displayText,long startMs,endMs)` |
| `CreationAssetUploadDTO` | `String originalName,contentType,usageIntent,idempotencyKey,requestDigest, long contentLength`；上传流作为 Service 独立参数，DTO 不持有流 |
| `CreationAssetQueryDTO` | `String assetType,usageIntent,status,keyword`；首版不携带任何客户端排序字段 |
| `CreationAssetDTO` | `String assetId,originalName,mimeType,sha256; CreationAssetType assetType; CreationAssetUsageOrigin usageOrigin; CreationAssetStatus status; long sizeBytes; Long durationMs; Integer width,height; boolean hasVideoStream,hasAudioStream; Instant createdAt` |
| `CreationAssetResolveDTO` | `String assetId,mimeType,sha256; CreationAssetType assetType; TimelineAssetUsageType usageType; long sizeBytes; Long durationMs; Integer width,height; boolean hasVideoStream,hasAudioStream`；无 owner/path/key/url |
| `DigitalHumanCreationSourceDTO` | `String sourceId,baseVideoAssetId,primaryAudioAssetId,scriptTextSnapshot, long durationMs, int width,height,frameRate, List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> trustedCues`；当前来源无 cue 时为空 |
| `RegisterPendingRenderOutputDTO` | `String taskId,inputVersionId,outputConfigDigest,idempotencyKey` |
| `PendingRenderOutputDTO` | `String assetId,taskId,inputVersionId,outputConfigDigest; CreationAssetStatus status; Instant createdAt` |
| `RenderOutputReadyDTO` | `String assetId,taskId,mimeType,sha256; long sizeBytes,durationMs; int width,height,frameRate; boolean hasVideoStream,hasAudioStream` |
| `RenderOutputFailureDTO` | `String assetId,taskId,failureCode,safeSummary` |
| `AiTaskRequestPayloadDTO` | sealed interface，只 permits 同包四个 wrapper：`AiTaskImagePromptPayloadDTO`、`AiTaskFancyTextPayloadDTO`、`AiTaskSubtitleAlignmentPayloadDTO`、`AiTaskRenderPayloadDTO`；每个 wrapper 只含一个对应的 timeline command，避免未命名模块下 sealed 跨包编译失败 |
| `CreateFreeAiTaskDTO` | `AiTaskType taskType,AiTaskResourceType resourceType,String resourceId,projectId,draftRevision,inputVersionId,idempotencyKey,requestDigest,quotaPolicyVersion; long estimatedUsage; AiTaskRequestPayloadDTO payload`；payload 运行时类型必须与 taskType 一一匹配 |
| `AiTaskResultPayloadDTO` | sealed interface，只 permits 同包三个 wrapper：`AiTaskImagePromptResultPayloadDTO`、`AiTaskFancyTextResultPayloadDTO`、`AiTaskSubtitleAlignmentResultPayloadDTO`；各自只含一个对应的 timeline result；渲染结果继续只用 ready `resultAssetId` |
| `AiTaskDTO` | `String taskId,taskType,status,stage,resourceType,resourceId,projectId,draftRevision,inputVersionId,resultAssetId,errorCode,safeMessage,createdAt,updatedAt; AiTaskResultPayloadDTO resultPayload; int progress; boolean cancellable,retryable`；仅详情返回 resultPayload；taskType/stage 用字符串保持未知合法值前向兼容，未知未来任务不返回未校验 raw payload |
| `AiTaskSummaryDTO` | `String taskId,taskType,status,stage,resourceType,resourceId,projectId,createdAt,updatedAt,errorCode,safeMessage, int progress, boolean cancellable,retryable` |
| `AiTaskQueryDTO` | `String taskType,status,resourceType,resourceId,projectId`；首版不携带任何客户端排序字段 |
| `AiTaskExecutionDTO` | `String executionId,taskId,executionStatus,workerId,leaseExpiresAt,startedAt,finishedAt,inputVersionId,resultAssetId,errorCode, int executionNo,rowVersion,progress`；不含 leaseToken |
| `AiTaskLeaseDTO` | `String taskId,executionId,attemptId,leaseToken,workerId,actorId,inputVersionId, int executionNo,attemptNo,rowVersion`；仅 Worker 内部可见 |
| `AiTaskAttemptDTO` | `String attemptId,executionId,status,workerId,startedAt,finishedAt,errorCode, int attemptNo` |
| `AiTaskProgressDTO` | `String executionId,leaseToken; int expectedRowVersion,percent; AiTaskStage stage; String safeMessage` |
| `AiTaskCompletionDTO` | `String executionId,leaseToken,resultAssetId,errorCode,safeMessage; AiTaskResultPayloadDTO resultPayload; int expectedRowVersion; boolean success,retryable`；三类建议成功必须有匹配 payload 且 resultAssetId 为空，渲染成功反之 |
| `RetryAiTaskDTO` | `String sourceTaskId,idempotencyKey,requestDigest` |
| `AiTaskDispatchResultDTO` | `String outcome,taskId,executionId`；outcome 仅 `none|completed|failed|cancelled|lease_lost` |
| `WhisperTranscriptionInputDTO` | `String requestId,originalName,contentType; long fileSize`；不含 tenant/workspace/owner/path/key/url，语言与时间戳策略由服务端配置固定 |

- [ ] 先写下列真实反射红灯，预期首次报 `ClassNotFoundException` 或字段集合不匹配；随后逐个 record／enum 实现，每新增一个类型立即重跑，不用“一次实现全部 DTO”作为单步：

```java
@Test
void crossModuleRecordsMatchFrozenComponents() {
    assertThat(recordComponents(TimelineDocumentDTO.class))
        .containsExactly("schemaVersion:String", "canvas:TimelineCanvasDTO", "tracks:List");
    assertThat(recordComponents(TimelineOutputConfigDTO.class))
        .containsExactly("resolutionPreset:String", "frameRate:int",
            "qualityPreset:TimelineOutputQuality");
    assertThat(recordComponents(WhisperTranscriptionInputDTO.class))
        .containsExactly("requestId:String", "originalName:String",
            "contentType:String", "fileSize:long");
}
```

- [ ] `TimelineElementDTO` 的最小骨架必须按以下 permits 列表编译；每个实现是独立同名 record 文件，公共字段顺序与上表一致：

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "elementType", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TimelineMainVideoElementDTO.class, name = "main_video"),
    @JsonSubTypes.Type(value = TimelineImageElementDTO.class, name = "image_overlay"),
    @JsonSubTypes.Type(value = TimelinePipVideoElementDTO.class, name = "pip_video"),
    @JsonSubTypes.Type(value = TimelineSubtitleElementDTO.class, name = "subtitle"),
    @JsonSubTypes.Type(value = TimelineFancyTextElementDTO.class, name = "fancy_text"),
    @JsonSubTypes.Type(value = TimelineAudioElementDTO.class, name = "audio"),
    @JsonSubTypes.Type(value = TimelineVisualEffectElementDTO.class, name = "visual_effect")
})
public sealed interface TimelineElementDTO permits
    TimelineMainVideoElementDTO, TimelineImageElementDTO,
    TimelinePipVideoElementDTO, TimelineSubtitleElementDTO,
    TimelineFancyTextElementDTO, TimelineAudioElementDTO,
    TimelineVisualEffectElementDTO {
    String elementId();
    TimelineElementType elementType();
    long startMs();
    long endMs();
    int zIndex();
    boolean enabled();
    boolean locked();
    String label();
}
```

- [ ] 先写反射与 JSON 往返失败测试，读取 C0 固定样例，断言 DTO 字段名、枚举值、未知字段拒绝、时间单位、字符串 HTTP ID 映射和有界结果 payload。
- [ ] 时间轴只使用专用 `ObjectReader` 显式开启 `FAIL_ON_UNKNOWN_PROPERTIES` 和受控多态白名单；不得修改全局 `ObjectMapper` 影响旧接口，也不得使用 default typing。Schema 与强类型 reader 两层都必须拒绝未知字段。
- [ ] DTO 仅表达规格已冻结的字段；供应商响应、内部对象键、绝对路径、租约令牌和用户自报归属不得进入公共 DTO。
- [ ] `AiTaskLeaseDTO`、`AiTaskProgressDTO`、`AiTaskCompletionDTO` 是 `@JsonIgnoreType` 的普通 final class，不是 record、不实现 `Serializable`、不覆盖会暴露租约令牌的 `toString()`；反射测试证明它们不会进入 Controller VO 或 JSON mapper。`AiTaskDTO/AiTaskSummaryDTO` 永远没有 leaseToken。
- [ ] `TimelineElementDTO` 使用受控的可判别结构表达七类元素；不能退化成任意 `Map<String,Object>`。
- [ ] `AiTaskType` 固定新增 `timeline_image_prompt_generate`、`timeline_fancy_text_suggest`、`timeline_subtitle_align`、`timeline_render`；`AiTaskDTO` 保留通用任务类型字符串的向前兼容读取，任务中心不得因未知合法类型反序列化失败。
- [ ] `TimelineVersionReason` 精确冻结 `manual_save|restored|render_input|conflict_copy`，`TimelineDocumentType` 精确冻结 `draft|version`；初始化项目只创建草稿，不伪造“初始版本”。写回执操作类型不是该枚举，按迁移中的四个 `operation_type` 独立冻结。
- [ ] `AiTaskStage` 固定至少包含 `queued`、`preparing_assets`、`reading_assets`、`building_ass`、`building_render_plan`、`encoding`、`verifying_output`、`registering_output`、`completed`、`failed`、`cancelled`；中文只在展示层映射。
- [ ] 错误夹具固定 `46601` 至 `46612` 及标识：`CREATION_PROJECT_NOT_FOUND`、`CREATION_SOURCE_INVALID`、`TIMELINE_REVISION_CONFLICT`、`TIMELINE_SCHEMA_UNSUPPORTED`、`TIMELINE_DOCUMENT_INVALID`、`TIMELINE_ASSET_INVALID`、`TIMELINE_TEXT_INTEGRITY_FAILED`、`TIMELINE_VERSION_NOT_FOUND`、`TIMELINE_IDEMPOTENCY_CONFLICT`、`TIMELINE_RENDER_UNAVAILABLE`、`TIMELINE_FONT_UNAVAILABLE`、`CREATION_PROJECT_STATE_CONFLICT`。
- [ ] 运行红灯：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=TimelineDtoContractTest,AiTaskDtoContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 最小实现全部 DTO／枚举，绿灯后运行模块编译。
- [ ] 将每个实际新增文件的完整路径补入 `ownership-manifest.md`。
- [ ] 精确暂存本任务文件，提交 `feat: 冻结时间轴跨模块类型`。

### 任务 6：冻结四个跨模块 Service 接口

**风险：** 红色。接口签名是后端与媒体并行开发的边界。

**文件：**

- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationAssetService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/CreationMediaHandle.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineMediaRenderService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineRenderOutputHandle.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineTaskProgressListener.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineAiSuggestionService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/exception/TimelineExecutionException.java`
- 新建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineExecutionFailureCode.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IWhisperTranscriptionService.java`
- 新建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineServiceBoundaryTest.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationAssetService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/CreationMediaHandle.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineMediaRenderService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineRenderOutputHandle.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineTaskProgressListener.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineAiSuggestionService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/exception/TimelineExecutionException.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineExecutionFailureCode.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IWhisperTranscriptionService.java
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineServiceBoundaryTest.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 先写 JUnit 反射边界失败测试：接口只能依赖 core DTO／枚举、JDK 类型、`PageQuery/PageResult` 和以下受控流；不能依赖 Controller BO／VO、infra client、Spring HTTP 类型、路径类型或供应商类型。把以下签名视为逐字冻结的 C0 代码，任何功能分支需要改动都必须走 C1：

```java
package org.dromara.aivideo.creation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import java.io.IOException;
import java.io.InputStream;

@JsonIgnoreType
public interface CreationMediaHandle extends AutoCloseable {
    CreationAssetResolveDTO metadata();
    InputStream stream();
    long offset();
    long length();
    long totalSize();
    @Override void close() throws IOException;
}
```

```java
package org.dromara.aivideo.timeline.service;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import java.io.IOException;
import java.io.InputStream;

@JsonIgnoreType
public interface TimelineRenderOutputHandle extends AutoCloseable {
    TimelineRenderResultDTO metadata();
    InputStream stream();
    @Override void close() throws IOException;
}
```

```java
package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;

@FunctionalInterface
public interface TimelineTaskProgressListener {
    void onProgress(TimelineProgressDTO progress);
}
```

```java
package org.dromara.aivideo.creation.service;

import org.dromara.aivideo.creation.dto.*;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface ICreationAssetService {
    CreationAssetDTO uploadOwned(long actorId, CreationAssetUploadDTO command, InputStream input);
    PageResult<CreationAssetDTO> pageOwned(long actorId, CreationAssetQueryDTO query, PageQuery pageQuery);
    CreationAssetDTO getOwned(long actorId, String assetId);
    CreationAssetResolveDTO resolveOwned(long actorId, String assetId, TimelineAssetUsageType usageType);
    CreationMediaHandle openOwnedMedia(long actorId, String assetId, TimelineAssetUsageType usageType);
    CreationMediaHandle openOwnedMediaRange(long actorId, String assetId,
        String singleRangeHeader);
    DigitalHumanCreationSourceDTO resolveDigitalHumanSource(long actorId, String sourceId);
    PendingRenderOutputDTO registerPendingRenderOutput(
        long actorId, RegisterPendingRenderOutputDTO command);
    RenderOutputReadyDTO storePendingRenderContent(long actorId, String assetId, TimelineRenderOutputHandle output);
    void markPendingRenderFailed(long actorId, RenderOutputFailureDTO command);
    void assertAssetDeletable(long actorId, String assetId);
    void deleteOwned(long actorId, String assetId);
    List<PendingRenderOutputDTO> findCompensatablePending(Instant olderThan, int limit);
}
```

```java
package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.*;
import java.util.List;
import java.util.function.BooleanSupplier;

public interface ITimelineMediaRenderService {
    TimelineMediaProbeDTO probe(CreationMediaHandle input);
    TimelineTextMeasureResultDTO measureText(TimelineTextMeasureCommandDTO command);
    TimelineRenderOutputHandle render(TimelineRenderCommandDTO command,
        List<CreationMediaHandle> inputs, TimelineTaskProgressListener progress,
        BooleanSupplier cancellationRequested);
    void cancel(String executionId, String attemptId);
}
```

```java
package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.*;
import java.util.function.BooleanSupplier;

public interface ITimelineAiSuggestionService {
    TimelineImagePromptResultDTO generateImagePrompt(TimelineImagePromptCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineFancyTextSuggestionResultDTO suggestFancyText(TimelineFancyTextSuggestionCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
        CreationMediaHandle primaryAudio, TimelineTaskProgressListener progress,
        BooleanSupplier cancellationRequested);
}
```

```java
package org.dromara.aivideo.task.service;

import org.dromara.aivideo.task.dto.*;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import java.time.Instant;

public interface IAiTaskService {
    AiTaskDTO createFreeTask(long actorId, CreateFreeAiTaskDTO command);
    AiTaskDTO getOwned(long actorId, String taskId);
    PageResult<AiTaskSummaryDTO> pageOwned(long actorId, AiTaskQueryDTO query, PageQuery pageQuery);
    AiTaskDTO requestCancellation(long actorId, String taskId, String cancellationKey);
    AiTaskDTO retryOwned(long actorId, RetryAiTaskDTO command);
    AiTaskDispatchResultDTO dispatchNext(String workerId);
    int recoverExpired(Instant now, int limit);
    int compensatePendingOutputs(Instant now, int limit);
}
```

- [ ] `IWhisperTranscriptionService` 保留现有 voice 专用方法以兼容旧代码，并新增以下 fail-closed 默认重载，使 C0 编译不要求提前修改 infra；媒体分支必须在现有实现中显式 override，禁止构造含空租户／工作区的旧 lease 冒充新任务：

```java
default VoiceTranscriptionResultDTO transcribe(
        WhisperTranscriptionInputDTO inputMetadata, InputStream input) {
    throw new TimelineExecutionException("字幕对齐能力暂不可用",
        TimelineExecutionFailureCode.CAPABILITY_UNAVAILABLE, true, null);
}
```

- [ ] 新重载只接收通用元数据与调用期内有效的流；现有声音 Scheduler 继续调用旧签名。媒体分支在现有 `WhisperTranscriptionServiceImpl` 中显式 override 新签名并复用同一 HTTP 客户端，禁止创建第二套 Whisper 客户端，更禁止伪造 `VoiceTranscriptionLeaseDTO` 或把旧 tenant／workspace 置空后冒充合法调用。
- [ ] 资源所有权冻结：Controller 创建的上传 `InputStream` 由 Controller 在 Service 返回／抛错后关闭；`openOwnedMedia`／`openOwnedMediaRange` 的调用者拥有 `CreationMediaHandle` 并必须 try-with-resources，媒体 Service 绝不关闭调用者输入；`render` 成功返回的 `TimelineRenderOutputHandle` 由 `AiTaskServiceImpl` 拥有，后端完整消费并完成对象上传后关闭，`close()` 才允许媒体实现删除该任务工作目录；任何异常、取消或租约丢失都按“输出句柄 → 输入句柄”的逆序关闭。两个句柄实现都以 `AtomicBoolean` 保证幂等 close；输出句柄先关闭内容流再执行清理器，前者失败仍执行后者并用 suppressed exception 保留第二个失败。句柄的 `stream()` 是单次、同一实例读取，关闭后再次读取必须失败，metadata 与任何日志不得出现路径／对象键。
- [ ] 异常语义冻结：参数、归属、状态、Schema 和字体业务校验抛 `new ServiceException(safeMessage, 466xx)`；媒体／AI 外部执行抛 `TimelineExecutionException(safeMessage, code, retryable, cause)`，只暴露最多 512 码点的安全 message、稳定 code 和 retryable，不暴露命令、路径、供应商正文或 cause message。稳定 code 至少覆盖 `CAPABILITY_UNAVAILABLE`、`INPUT_INVALID`、`INPUT_UNAVAILABLE`、`FONT_UNAVAILABLE`、`TIMEOUT`、`PROCESS_FAILED`、`OUTPUT_INVALID`、`REMOTE_FAILURE`、`RESPONSE_TOO_LARGE`、`RESPONSE_INVALID`、`CALLBACK_FAILED`；主动取消或进度回调判定租约丢失抛 JDK `CancellationException`，任务编排映射为 cancelled／lease_lost，不能误记 failed。进度监听器异常必须立即停止外部工作并进入同一资源关闭流程。
- [ ] 异常最小实现固定为普通 final class，不把 cause 暴露给序列化或 `toString()`；先写构造和 513 码点拒绝测试，再实现：

```java
import java.util.Objects;

public final class TimelineExecutionException extends RuntimeException {
    private final TimelineExecutionFailureCode code;
    private final boolean retryable;

    public TimelineExecutionException(String safeMessage,
            TimelineExecutionFailureCode code, boolean retryable, Throwable cause) {
        super(safeMessage, cause);
        if (safeMessage == null || safeMessage.isBlank()
                || safeMessage.codePointCount(0, safeMessage.length()) > 512) {
            throw new IllegalArgumentException("safeMessage must contain 1..512 code points");
        }
        this.code = Objects.requireNonNull(code, "code");
        this.retryable = retryable;
    }

    public TimelineExecutionFailureCode code() { return code; }
    public boolean retryable() { return retryable; }
}
```
- [ ] `dispatchNext(workerId)` 在一次调用内至多领取并执行一个任务，`workerId` 长度 `1..128`；`recoverExpired`／`compensatePendingOutputs` 的 limit 只允许 `1..100`，越界用 `ServiceException` 拒绝。领取／续租／attempt／完成／失败细节封装在实现内，租约令牌只允许在 Worker 内部 `AiTaskLeaseDTO/AiTaskProgressDTO/AiTaskCompletionDTO` 出现，不进入用户响应 DTO。
- [ ] 反射红灯必须逐一断言全部方法名、参数、返回类型、泛型和 `IWhisperTranscriptionService` 两个重载，并扫描公共签名不存在 `Path/File/URI/URL/Resource/MultipartFile/RestClient` 或供应商包；生命周期测试用计数 InputStream 证明正常、异常、取消与 listener 抛错都恰好关闭一次。
- [ ] 运行红灯后实现接口，再运行：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -Dtest=TimelineServiceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 更新 `ownership-manifest.md` 的完整接口路径，精确提交 `feat: 冻结时间轴跨模块服务边界`。

### 任务 7：C0 自检、独立审查、合入并发布 `C0_SHA`

**风险：** 红色。C0 未通过时三台设备不得开始功能编码。

- [ ] 在 C0 分支运行全部契约测试、core 模块测试和受控迁移测试。
- [ ] 运行 `scripts/validate-development-standards.ps1` 与 `git diff --check`。
- [ ] 检查迁移不存在物理外键，公共请求不存在归属自报字段，固定样例中不存在内部路径、URL 或凭据。
- [ ] 启动一个没有修改 C0 的只读 Codex 审查任务，重点审查规格覆盖、DDL 回滚风险、索引、权限、类型边界与三设备文件重叠。
- [ ] 只修复审查报告中的必须修复项，并由同一审查任务做一次定向复核。
- [ ] 通过 PR 把 `codex/step6-contract` 合入 `codex/step6-integration` 并推送。
- [ ] 集成负责人执行 `git fetch origin --prune` 与 `git rev-parse origin/codex/step6-integration`，把完整 40 位值发布到集成 PR／固定交付消息；不得尝试把提交号写回产生该提交号的文件，避免自引用 SHA。
- [ ] 三台设备分别 fetch，检出该提交，执行 `git rev-parse HEAD` 一致后创建各自功能分支。

## 6. 本角色最小任务卡

### 6.1 C0 契约任务卡

- **单一目标：** 完成任务 1 至任务 7，发布可供三台设备共同检出的 `C0_SHA`。
- **禁止事项：** 不写 Service 业务实现、Controller、TypeScript 页面、媒体实现或生产配置；不修改历史计划。
- **权威输入：** 本计划第 0、1 节与冻结规格第 5、6、9、10、15、19、21 节。
- **允许路径：** 四份公共契约、`docs/contracts/creation-timeline/**`、唯一 `_01` 迁移、C0 DTO／枚举／接口与对应契约测试。
- **交付证据：** 文档校验、契约解析、空库迁移、权限授权、无外键、所有权清单、独立审查、PR 和完整 `C0_SHA`。
- **停止条件：** C0 共享后暂停该任务；公共契约再次有误时才按第 0.5 节处理一次 C1。
