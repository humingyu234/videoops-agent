# 创作第 3 步人物形象与参考声音选择设计规格

## 1. 文档状态与结论

- 日期：2026-08-06
- 模块：数字人工作台 / 创作 / 第 3 步“选择形象与声音”
- 风险等级：红色。原因是本需求同时涉及用户文件上传与访问、资源归属、权限、公共 API、数据库状态约束、后台转写领取规则和创作链路状态清理。
- 设计结论：采用已确认的方案 A，抽取形象库和声音库中的共享业务组件与 Hook，由创作步骤组合这些能力；不嵌入完整资源库页面，也不在 `AssetStep` 复制第二套卡片、上传和播放逻辑。
- 实施门禁：本规格经用户书面确认后才能进入 `writing-plans` 和业务代码实现。

本规格是“创作第 3 步接入真实形象/声音资源”及“创作上传原声时延迟转写”的权威设计。它只覆盖本模块新增与复用边界；与旧规格冲突时，本规格优先于旧规格中关于创作第 3 步静态演示数据、声音上传后必定立即进入转写队列的描述，其他模块规则不变。

## 2. 背景与现状

当前 `AssetStep.tsx` 从 `model.ts` 的静态 `AVATARS`、`VOICES` 读取数据，人物区域是单行滚动卡片，声音区域是静态条目，试听只显示提示。真实资源能力已经分别存在于：

- `PortraitLibraryView.tsx`：真实形象分页、上传素材、创建形象、详情与状态展示。
- `VoiceLibraryView.tsx`：真实声音列表、状态轮询、试听、时间轴、解析重试和文本编辑。
- `portraitApi`：形象列表、详情、上传、创建和短期访问地址。
- `voiceApi`：声音列表、详情、上传、短期访问地址和解析相关动作。

现有声音创建服务固定写入 `transcriptionStatus=pending`、`nextAttemptAt=now`，后台 Worker 会自动领取。因此，仅修改创作页面无法满足“这里只上传即可用于克隆、不等待也不自动解析”的用户决策，必须同步扩展声音上传契约和资源状态。

## 3. 目标与范围

### 3.1 必须实现

1. 进入第 3 步时，并行调用真实人物形象列表和真实声音列表接口。
2. 人物形象每页固定 6 个，严格按两行三列展示；声音每页固定 6 个。
3. 两个区域独立分页、独立刷新、独立加载与失败，分页控件统一放在各自区域下方。
4. 人物卡片沿用形象库的信息结构，显示名称、性别、场景标签、可用状态和预览图。
5. 人物图片支持大图连续预览，支持上一张、下一张、键盘切换、缩放和恢复默认适应窗口，不显示旋转按钮。
6. 参考声音只查询并显示 `voiceType=origin`，条目沿用声音库的信息结构。
7. 声音播放展开真实音轨；点击音轨任意位置跳转并从该位置播放，全页同时只播放一个声音。
8. 两个区域都提供“刷新”和“新增”按钮。新增成功后持久化到对应资源库，切回第 1 页、刷新并自动选中新资源。
9. 创作步骤上传的原声音不进入文本解析队列，不等待解析即可作为克隆参考声音。
10. 声音功能模块对未解析原声显示“未解析”，由用户点击“解析”后才进入现有异步解析流程。
11. 点击下一步前重新校验所选资源的归属、类型和状态，避免使用已经删除、越权或失效的数据。
12. 后续声音生成和视频生成直接提交已选 `referenceVoiceId` / `portraitId`，由服务端再次校验并读取已归属素材；不能要求浏览器把资源库文件下载成 `File` 后再上传。

### 3.2 明确不做

- 不在第 3 步提供关键词搜索、性别筛选、状态筛选、编辑、删除、文本编辑、解析、重试解析或重新同步。
- 不在参考声音中显示克隆声音或公共声音。
- 不因为转写状态为 `unparsed`、`pending`、`transcribing` 或 `failed` 阻止原声被选中、试听或用于克隆；转写状态在此处只做信息展示。
- 不为第 3 步新增永久对象地址、浏览器直传凭据或独立文件存储方案。
- 不新增平行业务分层，不采用 DDD、Clean Architecture 或 Hexagonal Architecture。
- 不改变统一任务中心、额度、草稿持久化、Provider 算法或既有生成任务状态机；为闭合真实资源选择链路，必须同步调整 `VoiceStep`、`BaseStep` 和生成接口的输入方式。
- 不通过短期预览/试听 URL 在浏览器下载 Blob 后重新上传到生成接口。
- 不升级 Ant Design 或其他依赖版本。

## 4. 页面布局与交互

### 4.1 总体布局

第 3 步继续保持人物形象和参考声音两个选择区域。常用桌面/Electron 视口使用左右布局；空间不足时两个区域可以上下排列且 Panel 占满可用宽度，但每页数量和人物两行三列结构不变，页面不得产生浏览器级横向滚动条。人物网格列使用 `repeat(3, minmax(0, 1fr))`，卡片内部文本允许省略，禁止以固定像素卡宽撑破容器。

每个区域结构统一为：

```text
标题与说明                 刷新  新增
资源内容区（固定一页）
上一页  有界页码圆点/页码文本  下一页
```

- “刷新”与“新增”紧邻放在区域标题右侧。
- 分页按钮、圆点和页码放在内容区下方，不悬浮在卡片左右侧。
- 页码很多时只显示当前位置附近的有界圆点或省略提示，不渲染无上限圆点。
- 第一页禁用“上一页”，末页禁用“下一页”。
- 上一页和下一页在边界也保持渲染并使用原生 `disabled`；页码圆点是可聚焦按钮，具有“第 N 页”可访问名称，当前页设置 `aria-current="page"`。

### 4.2 整页左右翻页

人物和声音各自维护页码，翻页只替换该区域的完整 6 条内容：

- 支持底部上一页/下一页按钮和页码圆点。
- 支持鼠标横向拖动和触摸左右滑动；设置 `touch-action: pan-y`，垂直滚动优先。
- 只有横向位移达到 48px 且明显大于纵向位移时触发一次翻页；一次手势最多翻一页。
- 拖动期间不触发卡片选择、图片预览或音轨跳播。
- 已触发翻页的拖动结束后抑制浏览器紧随其后产生的一次 `click`，防止误选资源。
- 不显示内容区原生横向滚动条。
- 快速连续翻页时用请求序号或 `AbortController` 丢弃过期响应，旧页响应不得覆盖新页。

### 4.3 人物形象卡片

人物区域请求全部状态，不传 `availabilityStatus` 筛选。当前页固定呈现 6 个位置，实际记录不足时不补造数据。

卡片显示内容与形象库保持同一映射：

- 预览图或占位图。
- 名称。
- 性别标签。
- 场景标签；无标签时显示“暂无场景标签”。
- `processing | ready | failed` 的中文状态与一致的状态色。

交互边界：

- 只有 `availabilityStatus=ready` 的人物可以选中。
- 点击卡片信息区选择人物；再次点击同一人物不重复清理下游状态。
- 非 `ready` 卡片保留完整信息。选择入口保持可聚焦，使用 `aria-disabled="true"` 而不是原生 `disabled`；鼠标或键盘激活时只播报/提示“处理中”或“处理失败”，不改变选择。
- 点击图片或独立预览入口只打开大图，不改变选择。
- 若接口没有返回可用预览地址，则显示占位图并隐藏/禁用预览入口。
- 非 `ready` 记录只有在接口确实返回可用预览地址时才可预览；按现有契约它们通常没有预览地址，设计不要求后端为其额外签发地址。
- 选中态使用边框、勾选标识和实际选择按钮上的 `aria-pressed` 表达，不能只依靠颜色；本页面不使用缺少对应复合控件角色的 `aria-selected`。
- 选择按钮、预览按钮、声音播放按钮和音轨必须是互为兄弟的交互元素，禁止按钮嵌套按钮或把 slider 放进选择按钮。

### 4.4 人物大图预览

当前人物页使用 Ant Design `Image.PreviewGroup` 组成一个连续预览组：

- 预览组只包含当前页具有预览地址的记录。
- 支持预览上一张/下一张、左右方向键、关闭、缩小、放大和恢复默认适应窗口；Ant Design 的 `onReset` 语义同时承担“恢复默认适应窗口”，不额外虚构独立的 Fit 动作。
- 通过 Ant Design 6.5.1 的 `PreviewGroup.preview.toolbarRender` 或官方 `actionsRender` 白名单只渲染上一张、下一张、缩小、放大和重置；不得通过 CSS 选择器或内部 DOM 类名隐藏旋转图标。
- 关闭大图后焦点返回触发预览的按钮。
- 预览地址过期或首次加载因授权失效失败时，调用 `portraitApi.accessUrl(portraitId)` 获取新地址并只自动重试一次；第二次失败显示明确错误，不循环重试。
- 页面切换后销毁不再需要的临时访问地址状态；不把短期 URL 写入长期工作台状态。

### 4.5 参考声音条目

声音区域固定请求 `voiceType=origin&pageSize=6`，不提供类型切换。条目复用声音库的字段映射并按当前数据可用性显示：

- 名称。
- “原声”类型标签。
- 性别、风格和标签组成的元信息。
- 转写状态：未解析、等待解析、解析中、已解析或解析失败。
- 时长；后端没有时长时先显示 `--:--`，音频 `loadedmetadata` 后在当前页面更新为真实时长。
- 有解析文本时可显示短摘要；未解析时显示“未解析文本，不影响克隆”，不得显示“正在解析”。

所有 `origin` 记录均可选择，转写状态不作为选择门槛。播放按钮和音轨属于独立操作，不改变所选声音；点击条目的非交互信息区才选择声音。

### 4.6 声音播放与音轨

- 点击播放按钮后展开该条目的音轨并立即播放；再次点击暂停。
- 同一时间只允许一个 `HTMLAudioElement` 播放。播放其他条目时先暂停并清理上一个播放状态。
- 即使 DTO 的 `durationMillis` 缺失或为 0，播放动作也必须先获取访问地址并创建 `HTMLAudioElement`，不能因未知时长提前返回。
- 使用 `loadedmetadata` 的 `duration` 建立不依赖转写文本的百分比音轨。播放器按声音 ID 维护 `loading/error/duration`，元数据加载失败显示条目级错误，不能静默吞掉。
- 元数据未就绪时允许从 0 开始播放，音轨显示加载态并暂不接受百分比跳播；取得有效时长后再开放点击、拖动和键盘跳播。
- 点击或拖动音轨按 `目标百分比 × duration` 设置 `currentTime`，随后播放。
- 音轨提供 `role=slider`、当前百分比、键盘左右调整和可读的当前时间/总时长。
- 已解析且存在精确时间轴时，声音功能模块可以继续显示词元跳播；第 3 步只保证基础音轨跳播，不发起文本解析，也不依赖词元数据。
- 试听地址通过 `voiceApi.accessUrl(voiceId)` 获取；授权失效只刷新并重试一次。
- 翻到其他声音页、离开第 3 步、切换工作台路由或组件卸载时暂停音频并释放监听器。

## 5. 选择状态与下游清理

`StudioState.selectedAvatar` 和 `StudioState.selectedVoice` 继续保存稳定字符串 ID；页面局部缓存保存当前可见 DTO 和已选资源快照，用于跨页展示选中摘要。`initialStudioState.selectedVoice` 从静态 `vs-003` 改为 `null`，静态 `AVATARS`、`VOICES` 不再参与第 3 步运行时渲染和校验。

后续生成意图同步改用稳定资源 ID：

- `VoiceGenerationIntent` 保存 `referenceVoiceId`、`scriptText` 和幂等键，不再以 `File` 对象身份判断声音输入是否变化。
- `VideoGenerationIntent` 保存 `portraitId`、`voiceJobId` 和幂等键，不再以 `File` 对象身份判断人物输入是否变化。
- `VoiceStep` 以 `selectedVoice` 创建声音任务；`BaseStep` 以 `selectedAvatar` 创建视频任务。`referenceAudio`、`portraitImage` 这两个只服务旧直传路径的工作台状态在新链路迁移完成后移除，不允许继续作为第 3 步能否推进的门槛。

当人物 ID 真正变化时，沿用现有依赖清理：

- 设置新的 `selectedAvatar`。
- 清空 `videoGenerationIntent` 和 `videoJob`。

当声音 ID 真正变化时，沿用现有依赖清理：

- 设置新的 `selectedVoice`。
- 清空 `voiceGenerationIntent`、`voiceJob`、`videoGenerationIntent` 和 `videoJob`。

预览、播放、展开音轨、刷新、翻页或重复点击当前选择不得清理任何下游状态。选择跨页保留；翻到其他页时底部摘要仍显示已选名称和状态。

## 6. 前端组件与复用边界

采用共享业务组件/Hook，而不是复用整页：

```text
PortraitLibraryView ─┐
                     ├─ PortraitCard / PortraitPreviewGroup / portrait upload flow
PortraitSelectionPanel ┘

VoiceLibraryView ────┐
                     ├─ VoiceSummary / VoiceTrackPlayer / useVoicePlayback / voice upload flow
OriginVoiceSelectionPanel ┘

AssetStep
  ├─ PortraitSelectionPanel
  ├─ OriginVoiceSelectionPanel
  └─ 已选摘要 + StepFooter
```

### 6.1 人物侧

- 从 `PortraitLibraryView` 的内联卡片抽取 `PortraitCard`，统一名称、性别、标签、状态、占位图和预览图映射。
- 卡片通过明确的 `mode="library" | "selection"` 或等价能力属性控制操作区；选择模式不渲染编辑、删除和“进入形象空间”。
- 抽取上传/创建弹窗和请求状态，使资源库与第 3 步共用文件校验、幂等键复用、上传素材、创建形象和失败保留表单的逻辑。
- `PortraitSelectionPanel` 负责页码、刷新、滑动翻页、选中和大图预览，不承载编辑/删除。

### 6.2 声音侧

- 保留并复用 `toVoiceItem` 的元信息映射，但扩展 `unparsed`，避免被错误映射成“正在解析”。
- 将现有 `VoiceCard` 中可共享的摘要和音轨播放器拆为小组件，或为其增加受约束的选择模式；选择模式不渲染文本编辑、删除、解析、重试和重新同步动作。
- 继续复用 `useVoicePlayback` 的单实例播放、访问地址刷新、进度和清理能力，并修复其当前 `secs <= 0` 直接停止的分支，使 `durationMillis` 缺失的 `unparsed` 原声也会加载真实媒体元数据。
- 抽取声音上传表单/Hook；声音库默认上传行为保持自动解析，第 3 步显式传 `transcriptionRequested:false`。
- `OriginVoiceSelectionPanel` 负责固定类型查询、页码、刷新、滑动翻页和选择。

### 6.3 请求状态

两个 Panel 各自维护：

- `pageNum=1`、固定 `pageSize=6`、`total`、当前页记录。
- 初次加载、静默刷新、错误和权限状态。
- 以页码为键、只缓存已经访问页面的有界页面缓存；不做没有性能证据的推测性预取。
- 当前请求序号/取消控制和 Panel 级 `epoch`。手动刷新、上传成功和卸载时递增 `epoch`，任何旧 epoch 的列表或缓存响应均被丢弃。

页面缓存只存在于第 3 步组件生命周期内。手动刷新只失效并重取当前区域的当前页；上传成功失效对应区域全部页缓存并加载第 1 页。

## 7. 数据流

### 7.1 进入第 3 步

并行发起：

```text
GET /api/portraits?pageNum=1&pageSize=6
GET /api/voices?voiceType=origin&pageNum=1&pageSize=6
```

任一请求失败不取消另一请求。已有 `selectedAvatar`/`selectedVoice` 先保留；对应记录出现在任意页时恢复选中样式，未出现在当前页不代表资源失效。

离开并再次进入第 3 步时，Panel 局部快照可能已经释放。首屏列表返回后，如果已有选中 ID 不在当前页且没有快照，则分别调用对应 detail 接口恢复不含长期访问 URL 的选中摘要：

- detail 成功时更新快照。
- 明确不存在/越权时按第 7.5 节只清理对应选择。
- 临时网络或 5xx 失败时保留 ID，摘要显示“已选择，详情暂不可用”并允许重试，不能把临时失败当成失效。

### 7.2 翻页与刷新

- 翻页只更新对应 Panel 的页码和数据。
- 手动刷新当前页，保留当前页码、另一侧数据、播放以外的有效选择和已生成的下游状态。
- 刷新声音区域前暂停正在播放的音频，避免旧 URL 与新记录状态混用。
- 刷新后总数导致当前页超过末页时，将页码收敛到新的最后一页并只再请求一次；总数为 0 时回到第 1 页。
- 当前页没有已选记录时不清空选择；列表缺失不是资源删除的充分证据。

### 7.3 新增人物形象

```text
选择照片并填写元数据
  -> portraitApi.upload(file)
  -> portraitApi.create({ assetId, metadata, idempotencyKey })
  -> 使用创建响应立即写入 selectedAvatar/快照
  -> 清理人物变化对应的下游状态
  -> 关闭弹窗、失效缓存并显式调用 loadPage(1)
  -> 在首屏定位并显示选中态
```

- 创建失败保留文件、表单、已上传素材 ID 和幂等上下文；同一文件与同一规范化表单重试不重复上传。
- 创建成功但第 1 页刷新失败时，创建结果与选择仍然有效，显示“已新增，列表刷新失败”的局部警告和重试按钮。
- 不能使用 `setPage(1); await load()` 依赖异步状态立即生效；`loadPage(1)` 必须以显式参数发出 `pageNum=1` 请求。

### 7.4 新增原声音

```text
选择音频并填写元数据
  -> voiceApi.upload(file, { ...metadata, transcriptionRequested:false })
  -> 后端保存 origin + unparsed，不进入 Worker 队列
  -> 使用上传响应立即写入 selectedVoice/快照
  -> 清理声音变化对应的下游状态
  -> 关闭弹窗、失效缓存并显式调用 loadPage(1)
  -> 在首屏定位并显示选中态，可立即试听和用于克隆
```

- 上传失败保留文件、元数据与幂等键。
- 不轮询转写状态，不显示解析进度，不等待 transcript。
- 上传成功但列表刷新失败时沿用人物侧的“结果有效、刷新可重试”规则。

### 7.5 点击下一步前校验

点击“去生成声音”时并行调用：

```text
GET /api/portraits/{selectedAvatar}
GET /api/voices/{selectedVoice}
```

通过条件：

- 人物仍属于当前 tenant/workspace/owner，且 `availabilityStatus=ready`。
- 声音仍属于当前 tenant/workspace/owner，且 `voiceType=origin`；`transcriptionStatus` 不参与通过判断。

处理规则：

- 某一侧得到明确的不存在/越权业务结果，或详情证明状态/类型不符合时，只清除该侧选择和其对应下游依赖，保留另一侧，并将焦点移到该区域错误提示。
- 网络错误、超时或 5xx 时保留两侧选择，阻止进入下一步并提供重试，不能把临时故障误判为资源删除。
- 两侧都通过后，用详情响应刷新选择快照，再进入下一步。
- 校验期间禁用重复提交；过期校验响应不得在用户改选后推进步骤。

### 7.6 后续生成消费所选资源

第 3 步通过后不下载 OSS 文件到浏览器。后续步骤直接使用稳定资源 ID：

```text
VoiceStep
  -> POST /api/studio/voice-jobs (application/json)
     { scriptText, referenceVoiceId: selectedVoice }
  -> 服务端校验并读取自有 origin 声音，复制为任务输入快照

BaseStep
  -> POST /api/studio/video-jobs (application/json)
     { voiceJobId, portraitId: selectedAvatar }
  -> 服务端校验并读取自有 ready 人物，复制为任务输入快照
```

- 生成接口必须再次校验，不能依赖第 3 步的前端详情结果。
- 输入媒体在任务创建成功前复制到既有数字人任务私有输入存储；此后用户删除资源库条目不破坏已经创建的任务。
- 声音不需要 transcript 即可走 `referenceVoiceId` 克隆路径；文件格式与大小仍受既有数字人生成 Provider 输入契约约束，不因省略文本解析而绕过媒体校验。

## 8. HTTP 与领域契约

### 8.1 复用接口

| 用途 | 方法与路径 | 关键请求 |
| --- | --- | --- |
| 人物列表 | `GET /api/portraits` | `pageNum`、固定 `pageSize=6`，不传状态筛选 |
| 人物详情 | `GET /api/portraits/{portraitId}` | 下一步校验 |
| 人物上传 | 既有 `portraitApi.upload` 对应接口 | 复用形象库校验与幂等流程 |
| 人物创建 | 既有 `portraitApi.create` 对应接口 | 复用形象库创建契约 |
| 人物预览 | `GET /api/portraits/{portraitId}/access-url` | 短期地址失效后最多重取一次 |
| 声音列表 | `GET /api/voices` | `voiceType=origin`、固定 `pageSize=6` |
| 声音详情 | `GET /api/voices/{voiceId}` | 下一步校验 |
| 声音上传 | `POST /api/voices` | multipart 的 `metadata` 增加可选布尔字段 |
| 声音试听 | `GET /api/voices/{voiceId}/access-url` | 短期地址失效后最多重取一次 |
| 按库声音生成 | `POST /api/studio/voice-jobs` | 新增 JSON 变体：`scriptText`、`referenceVoiceId` |
| 按库人物生成 | `POST /api/studio/video-jobs` | 新增 JSON 变体：`voiceJobId`、`portraitId` |

所有 `Long` ID、修订号和长整数字段继续按现有 JSON 十进制字符串契约传输。

### 8.2 声音上传扩展

`POST /api/voices` 的 `metadata` 增加：

```json
{
  "transcriptionRequested": false
}
```

- 类型为可选布尔值。
- 服务端有效默认值为 `true`，保证既有声音库和旧客户端省略字段时仍自动解析。
- Java BO 使用可空 `Boolean` 或等价方式区分“省略”和显式 `false`，不能用默认值为 `false` 的原始布尔破坏兼容性。
- Controller 对 `null` 和显式 `true` 只规范化一次并得到同一个有效值 `true`；该有效值同时传入 `CreateVoiceDTO` 和指纹函数。
- 规范化后的有效值必须参与上传指纹/幂等摘要。同一幂等键、文件和其他元数据相同但该值不同，按现有幂等冲突处理，不能静默复用具有不同解析副作用的声音；省略字段与显式 `true` 的指纹必须相同。

创建状态：

| 有效值 | `voiceType` | `transcriptionStatus` | `nextAttemptAt` | Worker 行为 |
| --- | --- | --- | --- | --- |
| `true` | `origin` | `pending` | 当前时间 | 沿用现有自动解析 |
| `false` | `origin` | `unparsed` | `NULL` | 不可领取 |

前端 TypeScript 的 `VoiceTranscriptionStatus` 联合类型、后端字符串领域契约、`VoiceDTO`/`VoiceVo` 的状态说明、列表筛选允许值、工作台模型和适配器统一增加 `unparsed`。当前后端没有该名称的 Java 枚举，本需求不为同一状态再创建一套平行枚举；`VoiceVo` 也不新增重复布尔字段。

### 8.3 主动开始解析

新增用户端接口：

```http
POST /api/voices/{voiceId}/transcription/start
Content-Type: application/json

{
  "expectedRevision": "3"
}
```

成功返回更新后的 `VoiceVo`，状态为 `pending`。约束如下：

- Controller 使用 `@SaCheckPermission(value="aivideo:voice:transcribe", type="app")` 和 `@RepeatSubmit`，只做参数转换和结果包装。
- Service 再次校验 `aivideo:voice:transcribe`、当前 tenant/workspace/owner、非删除记录、`voiceType=origin`、当前状态严格为 `unparsed` 和 `expectedRevision`。
- 条件更新必须同时包含 `voice_id + tenant_id + workspace_id + owner_id + del_flag=0 + voice_type=origin + transcription_status=unparsed + record_revision`；成功后置为 `pending`，设置 `attemptCount=0`、`nextAttemptAt=now`，清理失败信息和租约字段，并将 `recordRevision` 加一。
- 不存在或越权沿用 `46401`；修订冲突沿用 `46403`；非 `unparsed` 或类型不允许沿用 `46404`；权限不足为 403。
- 相同修订号的并发请求最多一个成功；其余请求可能先被 `@RepeatSubmit` 拒绝，或在 Service 得到修订冲突/状态冲突，不能承诺唯一一种失败码。
- 声音功能模块只对 `unparsed` 且当前用户有解析权限的原声显示“解析”动作。点击成功后进入现有 `pending -> transcribing -> ready|failed` 轮询、重试和重新同步流程。

### 8.4 Worker 领取边界

- `claimNext` 继续只领取到期 `pending` 或租约过期的 `transcribing`。
- `unparsed` 永远不满足领取条件，且 `nextAttemptAt=NULL`。
- 失败重试只接受 `failed`；重新同步只接受 `ready`；新增 `start` 只接受 `unparsed`，三个动作的状态边界不能合并成模糊的“重新解析”。

### 8.5 生成任务的资源 ID 输入变体

保留 2026-08-03 已批准的 multipart 直传契约，不破坏旧调用者；在相同路径按 `Content-Type` 增加 JSON 变体。

创建声音任务：

```http
POST /api/studio/voice-jobs
Content-Type: application/json
Idempotency-Key: <key>

{
  "scriptText": "已确认的口播正文",
  "referenceVoiceId": "10001"
}
```

创建视频任务：

```http
POST /api/studio/video-jobs
Content-Type: application/json
Idempotency-Key: <key>

{
  "voiceJobId": "20001",
  "portraitId": "30001"
}
```

契约边界：

- JSON 字段集合精确如上，ID 使用十进制字符串；响应继续为既有 `DigitalHumanJobVo`。
- JSON Controller 从 `AppLoginHelper` 取得完整 `AppPrincipalSnapshotDTO` 并传给 Service；客户端不能提交 owner、tenant 或 workspace。
- 两个变体仍要求 `aivideo:studio:generate`。资源解析还必须经过相应 `aivideo:voice:query` / `aivideo:portrait:query` Service 校验。
- 声音严格要求当前 tenant/workspace/owner 的非删除 `origin` 记录及 ready 底层声音素材；`transcriptionStatus` 不参与生成资格。
- 人物严格要求当前 tenant/workspace/owner 的非删除 `ready` 记录及 ready 底层人物素材。
- 不存在、越权或跨工作区统一按资源不存在处理，不暴露资源是否存在。
- Service 从对象存储读取真实媒体并复用现有格式、大小、摘要、私有任务输入存储和 Provider 提交流程；幂等输入摘要继续基于实际媒体字节及脚本/父声音任务，不散列临时 URL。
- `IAssetService` 复用既有 `readOwnedVoiceAsset`，并增加对称的 `readOwnedPortraitAsset`；Controller 只做 BO/DTO 转换，不下载或编排素材。
- 临时 `av_dh_generation_job` 本次继续沿用已批准的 `tenant + owner` 任务归属和幂等边界；本需求只保证引用素材在创建时按当前 workspace 校验，不顺带扩张临时任务表的 workspace 模型。

## 9. 后端落点与 RuoYi 分层

后端保持现有贫血 Entity + Service 编排：

- `ai-video-user`：扩展 `CreateVoiceBo`，增加开始解析请求 BO 和 `VoiceController` 路由；Controller 不写状态迁移业务逻辑。
- `ai-video-core/domain`：`Voice` 继续使用现有字段，不新增重复的 `parseRequested` 数据库事实。
- `ai-video-core/dto`：扩展 `CreateVoiceDTO`，增加语义明确的开始解析 DTO。
- `IVoiceService` / `VoiceServiceImpl`：实现默认值传递、创建状态分支、主动开始解析和既有归属/权限/乐观锁校验。声音创建的幂等查询统一包含 tenant、workspace、owner 和幂等键。
- `VoiceMapper`：复用现有 MyBatis-Plus 条件更新；不得新增 Controller 直连 Mapper。
- `IDigitalHumanGenerationService` / 既有实现：增加从 `referenceVoiceId` / `portraitId` 解析、校验、读取并快照任务输入的方法，再复用现有任务创建逻辑；不得在 Controller 堆资源读取或状态判断。
- `IAssetService`：保留 `readOwnedVoiceAsset` 并增加对称的 `readOwnedPortraitAsset`，两者都必须先执行素材归属和 ready 校验，再提供有界输入流读取。
- `ai-video-infra`：调度器无需新分支，只需通过核心 Service 的领取查询自然排除 `unparsed`。
- SQL：新增可重复安全执行的向前迁移，不修改已经执行的历史迁移。迁移必须完成：
  1. 将 `next_attempt_at` 改为 `DATETIME NULL DEFAULT NULL`；自动解析创建和主动开始解析仍由 Service 显式写当前时间。
  2. 从 `information_schema` 识别并删除 `av_voice` 上所有约束表达式涉及 `transcription_status` 的旧 CHECK，包括历史 SQL 中命名约束和匿名重复约束；重新建立唯一命名的 `ck_av_voice_transcription_status`，允许 `('unparsed','pending','transcribing','ready','failed')`。
  3. 删除旧 `uk_av_voice_owner_idempotency (tenant_id, owner_id, idempotency_key)`，新建 `uk_av_voice_workspace_idempotency (tenant_id, workspace_id, owner_id, idempotency_key)`；Service 查询使用相同字段顺序和语义。
- 声音创建并发插入捕获 `DuplicateKeyException` 后，按同一 tenant/workspace/owner/幂等键回读获胜记录并比较上传指纹；不得向客户端泄露数据库原始唯一键异常。

公共契约必须在实现代码之前或同一原子变更中同步更新：

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`
- 2026-08-03 数字人纵链规格中的生成输入变体说明
- 声音上传/转写相关模块规格与数据库契约测试

## 10. 页面状态、错误与权限

人物和声音区域互不阻塞，分别覆盖：

| 状态 | 展示与行为 |
| --- | --- |
| 首次加载 | 人物显示 6 个卡片骨架；声音显示 6 个条目骨架；保留区域固定高度，减少布局跳动 |
| 空数据 | 区域内显示空态和新增入口；人物说明“还没有人物形象”，声音说明“还没有原声音” |
| 首次加载失败 | 只在失败区域显示错误说明和“重新加载”，另一侧照常可用 |
| 手动刷新中 | 保留旧内容和选择，刷新按钮显示忙碌状态，不用整区骨架替换 |
| 手动刷新失败 | 保留旧内容，区域内显示非阻断警告和重试；不把列表替换为空 |
| 查询权限不足 | 显示权限状态，不伪装为空数据；下一步不能绕过后端校验 |
| 新增权限不足 | 已知权限集合时隐藏或禁用新增按钮；接口仍是最终授权边界 |
| 访问地址失效 | 自动重取一次；再次失败显示预览/试听错误，不影响选择 |
| 上传失败 | 弹窗保持打开并保留文件、表单、素材/幂等上下文，允许安全重试 |

错误分支按稳定 `ApiError.code`/HTTP 状态判断，不解析中文错误消息。两个区域可同时显示各自错误，不使用覆盖全步骤的单一 Spin 或单一 Error 状态。

## 11. 安全、归属与一致性

- 所有列表、详情、访问地址、上传、创建和解析动作继续由后端校验 app 端权限、tenant、workspace、owner 和逻辑删除状态。
- 前端传入的资源 ID 只用于定位，不代表归属可信。
- 下一步详情校验是防止长时间停留后的资源失效，不替代后续声音克隆/数字人生成 Service 自身的资源归属和状态校验。
- 短期预览/试听地址只保存在内存，不写入 `StudioState`、localStorage 或日志。
- 上传表单不输出文件内容、对象存储密钥或永久地址。
- 创建与开始解析都保留业务幂等/乐观锁；不得用前端禁用按钮代替并发保护。
- 实施时保护工作区已有 OSS、配置和创作页未提交改动，禁止整文件覆盖。

## 12. 测试设计

### 12.1 前端 API 与适配器

- 人物列表参数固定 `pageSize=6`，不传状态筛选。
- 声音列表固定 `voiceType=origin&pageSize=6`。
- 第 3 步声音上传发送 `transcriptionRequested:false`；声音库省略该字段或发送 `true`。
- `unparsed` 映射为“未解析”，不映射为“正在解析”或失败。
- `voiceApi.startTranscription` 的路径、方法和 `expectedRevision` 请求体正确。
- 数字人声音/视频 API 的新调用分别发送 JSON `referenceVoiceId` / `portraitId` 和既有 `Idempotency-Key`，不生成短期 URL、Blob 或 multipart 文件。
- 既有 multipart 生成 API 测试继续通过，证明旧调用兼容。

### 12.2 前端组件与 Hook

- 人物 0、1、6、7、12、13 条时页数、两行三列和底部分页正确。
- 声音 0、1、6、7、12、13 条时每页最多 6 条且底部分页正确。
- 按钮、圆点、鼠标拖动和触摸滑动整页翻页；纵向手势和小位移不误翻页。
- 乱序响应、快速翻页、刷新/上传导致的 epoch 变化和卸载后的响应不覆盖当前状态或旧缓存。
- 两个区域的加载、失败、刷新和权限状态互不影响。
- 刷新当前页保留有效选择；总数缩减时页码正确收敛。
- 人物只有 `ready` 可选；图片点击只预览，卡片信息点击只选择。
- 当前页大图可连续切换，键盘和缩放可用，工具栏没有旋转动作，访问地址只重试一次。
- 声音播放不改变选择；切换声音只保留一个播放器；点击/拖动音轨按音频元数据跳播。
- `durationMillis` 缺失或为 0 的 `unparsed` 原声仍会获取媒体、从 0 播放，并在 `loadedmetadata` 后显示时长和开放跳播；加载错误显示在对应条目。
- 翻页、刷新声音或离开步骤时停止音频。
- 从第 2 页新增成功时显式请求 `pageNum=1` 并自动选中；列表刷新失败仍保留新资源选择。
- 上传失败保留表单、文件和幂等上下文。
- 下一步两侧详情校验成功才推进；一侧明确失效只清除一侧；网络失败保留选择。
- 选中资源变化按现有依赖图清理下游状态，预览/播放/重复选择不清理。
- 初始 `selectedVoice=null`；离开再返回时，已选 ID 不在首屏也能通过 detail 恢复摘要，临时失败不清除选择。
- VoiceStep/BaseStep 的必填门槛和生成意图使用资源 ID，不再要求 `referenceAudio` / `portraitImage` File。

### 12.3 后端 Service、Controller 与数据库

- 省略 `transcriptionRequested` 时仍创建 `pending` 并设置 `nextAttemptAt`。
- 显式 `false` 时创建 `unparsed`、`nextAttemptAt=NULL`，且上传指纹包含有效布尔值。
- 省略字段与显式 `true` 产生相同有效值和指纹，显式 `false` 的指纹不同。
- 同一幂等键在不同有效布尔值之间返回现有幂等冲突。
- 同一用户在不同 workspace 使用相同幂等键不会互相回放；同一 workspace 并发插入只返回获胜记录或幂等冲突，不泄露唯一键异常。
- Worker 不领取 `unparsed`，仍能领取 `pending` 和过期 `transcribing`。
- `start` 只允许自有工作区中的 `origin + unparsed`；成功后进入 `pending` 并递增修订号。
- `start` 覆盖缺权限、跨 tenant/workspace/owner、删除记录、错误类型、错误状态、旧修订和并发请求。
- retry、resync 和 start 三个动作的状态边界互不放宽。
- Controller 契约测试锁定路由、app 权限、`@RepeatSubmit`、请求/响应字段和十进制字符串 ID。
- 生成 JSON Controller/Service 测试覆盖 app 权限、声音/人物 query 权限、tenant/workspace/owner、类型/ready 状态、底层素材、旧 multipart 兼容和按实际媒体计算幂等摘要。
- 数据库迁移集成测试真实插入 `unparsed + next_attempt_at=NULL`，确认五种合法状态可写、非法状态仍被唯一命名 CHECK 拒绝，并验证新 workspace 幂等唯一键；不能只断言 SQL 文本包含关键字。

### 12.4 联调与视觉验收

- 使用真实用户端接口验证人物和原声各 0、1、6、7、12、13 条。
- 验证两个区域同时成功、分别失败、同时失败、分别刷新和权限不足组合。
- 验证新增人物、创作页新增原声、声音模块主动解析的完整路径。
- 验证创作上传原声后后台不会出现解析领取；未知后端时长仍可试听，用户无需等待即可用 `referenceVoiceId` 创建克隆任务。
- 验证所选 `portraitId` 能创建视频任务；删除或跨工作区 ID 在任务创建时再次被服务端拒绝，浏览器不下载后重传资源。
- 在项目常用 Electron/浏览器视口验证无浏览器级横向溢出、底部分页不遮挡内容、大图无旋转按钮、音轨可触达。
- 验证键盘焦点、可访问名称、选中态和音轨 slider 行为。

## 13. 验收清单

- [ ] `AssetStep` 不再使用静态 `AVATARS`、`VOICES` 渲染或校验第 3 步。
- [ ] 初始静态 `selectedVoice=vs-003` 已移除，离开再返回后可恢复真实已选摘要。
- [ ] 进入步骤并行请求真实人物列表和 `origin` 声音列表。
- [ ] 人物固定每页 6 个、两行三列；声音固定每页 6 个。
- [ ] 两个区域都有独立刷新和新增按钮，分页控件位于下方并支持左右滑动整页翻页。
- [ ] 卡片/条目信息与对应资源库共用映射和组件，不出现两套状态文案。
- [ ] 人物全状态可见，只有 `ready` 可选；图片可大图预览且没有旋转动作。
- [ ] 声音只显示原声，播放展开基础音轨，点击音轨跳播且不改变选择。
- [ ] 创作新增形象/原声成功后写入资源库、刷新第 1 页并自动选中。
- [ ] 创作新增原声保存为 `unparsed`，不轮询、不自动解析，可立即试听和用于克隆。
- [ ] 声音模块提供显式“解析”动作，点击后才进入既有异步转写流程。
- [ ] 省略新字段的旧客户端仍保持上传后自动解析。
- [ ] 下一步前重新校验人物 `ready` 和声音 `origin`，只清除明确失效的一侧。
- [ ] VoiceStep/BaseStep 通过 JSON `referenceVoiceId` / `portraitId` 消费已选库资源；旧 multipart 文件契约仍兼容，浏览器不下载 OSS 文件后重传。
- [ ] `next_attempt_at` 可空、重复旧 CHECK 已收口为一个允许五态的命名约束，声音幂等唯一键和查询都包含 workspace。
- [ ] 加载、空、失败、刷新失败、权限不足和上传失败状态完整且两个区域互不阻塞。
- [ ] 公共 API、领域状态、异步任务说明、数据库迁移、前后端类型和自动化测试同步落地。
- [ ] 后端保持 RuoYi Controller/BO/VO/DTO/Entity/Mapper/Service 既有分层，没有平行业务架构。

## 14. 实施顺序约束

正式实现计划应按以下依赖顺序拆分，具体文件与命令由 `writing-plans` 阶段生成：

1. 先同步公共 API、领域状态、生成资源 ID 输入和异步领取契约，并增加向前数据库迁移。
2. 实现后端 `unparsed` 创建分支、workspace 幂等收口和主动开始解析动作，完成后端定向测试。
3. 实现生成任务的 JSON 资源 ID 变体、服务端素材读取和输入快照，同时保持旧 multipart 契约测试通过。
4. 扩展前端声音类型、适配器和 API，修复声音模块的“未解析/解析”及未知时长播放能力。
5. 抽取人物与声音共享卡片、上传和播放能力，保持资源库回归测试通过。
6. 接入两个选择 Panel、分页/刷新/预览/音轨、下一步校验以及 VoiceStep/BaseStep 的 ID 输入。
7. 完成前后端联调、视觉验收、真实迁移验证、文档规范验证和差异检查。

不得先在 `AssetStep` 临时复制资源库代码，再把抽取工作无限期留到后续。

## 15. 规格自检

- 需求中的真实列表、每页 6 条、人物两行三列、底部分页、左右滑动、刷新、大图预览、无旋转、真实音轨、上传后刷新与自动选中均有唯一实现语义。
- 创作上传不解析与既有声音库默认自动解析通过可选字段和默认值兼容，没有改变旧客户端行为。
- `unparsed` 是唯一新增持久化状态，未新增重复布尔字段；Worker、主动动作和 UI 文案边界一致。
- 人物选择由 `ready` 控制；声音选择由归属和 `origin` 控制，转写状态不误用为克隆门槛。
- 资源 ID 会被后续生成步骤直接消费，服务端重新校验并快照媒体；不存在“页面可选择但下一步仍要求 File”的断链。
- `next_attempt_at` 可空、重复 CHECK、workspace 幂等和未知时长播放均有明确迁移与测试边界。
- 组件复用、错误状态、权限、并发、短期访问地址、下游状态清理和测试边界完整。
- 范围内没有未决字段、占位实现或需要在编码阶段自行猜测的产品选择。
