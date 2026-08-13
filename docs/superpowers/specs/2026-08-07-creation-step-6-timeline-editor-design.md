# 创作第 6 步时间轴编辑与重合成设计规格

## 1. 文档状态与结论

- 日期：2026-08-07
- 模块：数字人工作台 / 创作 / 第 6 步“时间轴编辑与重合成”
- 设计结论：采用用户确认的方案 A“影院控制台”。上部左侧为 9:16 真实视频预览，右侧为随选择切换的元素信息区；添加元素区位于上部预览区下方、时间轴上方；时间轴横向表示时间、轨道纵向排列，`V1` 主视频始终固定在可视区中间。
- 交互结论：时间轴片段可移动和拉伸；花字可直接在视频画面中拖拽，画布、时间轴和右侧坐标双向同步；画中画视频展示时长超过素材有效时长时自动循环，末轮不足部分直接截断。
- 风险等级：红色。生产实现会新增共享 API、持久化模型、私有媒体访问、乐观并发、统一生成任务、额度结算和服务端视频合成，命中共享契约、数据与内容访问、外部 AI、资金资产、并发恢复等红色触发条件。
- 当前阶段：本文件只冻结设计，不修改运行时代码、数据库或公共契约。用户确认本书面规格后，才能进入 `writing-plans` 和生产实现。

本规格是第 6 步的权威设计。它取代当前 `TimelineStep.tsx` 的静态演示行为，并扩展 2026-07-14 旧演示规格中“一张画中画图片、三个固定花字样式”的限制；旧规格关于已确认文案、真实声音、数字人底片、规范时长和最终输出校验的其他规则继续有效。

批准的只读视觉原型位于：

`F:/obj/ai/ai-video/.superpowers/brainstorm/timeline-step6-20260806-163014/content/09-cinema-a-draggable-fancy-text.html`

原型仅作为布局和交互方向证据，不是线上数据契约，也不能直接复制其中的静态演示数据进入生产代码。

## 2. 最小任务卡

| 项目 | 冻结内容 |
| --- | --- |
| 单一目标 | 完整实现第 6 步真实视频预览、元素添加、七轨时间轴、上下文元素信息、保存版本、AI 辅助和异步重合成。 |
| 不做范围 | 不重生成声音或数字人底片；不提供主视频变速、转场、多机位、调色、关键帧曲线或任意用户字体上传；不在本期调用图片生成模型。 |
| 风险 | 红色；共享 API/数据库、私有文件、权限归属、外部文本模型、额度、幂等、FFmpeg 合成和任务恢复。 |
| 权威来源 | `AGENTS.md`、`RULES.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、前后端指南与编码规范、RuoYi Plus AI Coding skill、本规格及上方批准原型。 |
| 允许影响 | 用户端第 6/7 步、用户端 timeline Service/adapter、核心 timeline 聚合、私有素材引用、统一任务及合成 Provider；实现前必须同步第 19 节列出的公共契约。 |
| 禁止影响 | 运营端身份、创作端与运营端账号隔离、既有声音/人物归属规则、旧任务终态、已生成底片与声音原件、其他工作流订单。 |
| 协作上限 | 红色实施或混合阶段同一目标最多 2 个智能体：1 个实施者和 1 个独立审查/验证者。完整审查只进行一轮，修复后只复核差异。 |
| 必须验收 | 本规格第 18 节全部正向与反向场景；前后端测试、专用本机 MySQL/Redis 集成测试、短媒体合成校验、键盘与视觉验收。 |
| 固定输出 | 完成项、剩余风险、实际验证证据、阻塞项；不能用“页面看起来正常”代替契约、归属、并发和输出媒体证据。 |

## 3. 背景与现状

当前第 6 步仍是静态页面：

- `TimelineStep.tsx` 在文件内硬编码片段、轨道和属性信息，轨道顺序为 `V1 → A1 → S1 → V2 → T1`。
- 当前布局是“预览与时间轴整体在左、属性栏在右”，属性栏压缩了时间轴宽度，不符合已确认布局。
- 片段只有点击选择，没有移动、左右拉伸、吸附、撤销粒度或媒体循环。
- 属性输入使用 `defaultValue`，切换元素后可能残留上一个元素的值。
- `model.ts` 只有 `timelineSelected`，没有可编辑元素、工作副本、版本、保存状态或撤销栈。
- 后端尚无第 6 步时间轴聚合、版本保存、模板目录和重合成公共接口；当前按钮只显示提示。
- 当前预览是 CSS 占位画面，不读取经过授权的真实视频，也不能证明最终合成效果。

因此，本需求不是只调整页面布局。生产实现必须同时闭合时间轴数据、素材归属、并发保存、任务中心、合成输出和失败恢复。

## 4. 目标与范围

### 4.1 必须实现

1. 使用第 5 步已经成功生成且归属当前草稿的真实数字人底片作为预览画面。
2. 上部固定为“左侧预览、右侧元素信息”，时间轴独占下部完整宽度。
3. 添加元素区严格放在预览/信息区下方、时间轴上方，不放在左右侧边栏。
4. 默认展示七类轨道：花字、字幕、画中画/画面特效、主视频、人声、背景音乐、特效声音。
5. 时间从左到右推进，轨道从上到下排列；`V1` 主视频固定在中间，不因新增子轨道上下漂移。
6. 支持添加图片、画中画图片/视频、字幕、花字、画面特效、背景音乐和特效声音。
7. 图片、画中画、花字、画面特效、背景音乐和特效声音可在时间轴移动开始位置，并通过左右手柄修改展示时长。
8. 画中画提供左上、右上、左下、右下四个预设并保留安全边距；视频时长不足时自动循环画面。
9. 点击时间轴元素切换右侧信息；点击预览中的字幕、图片/视频或花字反向选中时间轴元素。
10. 花字可直接在预览画面拖拽；位置被限制在安全区，右侧 `X/Y` 随拖动实时变化。
11. 字幕单行显示、不显示普通标点、不丢失正文字符、不使用省略号、不能越过画面安全区，并支持字体、字号、颜色、背景和描边设置。
12. 花字支持 AI 语义建议、人工新增、基本属性修改和内置特效模板；AI 建议必须由用户确认后才插入。
13. 用户可选择一段已确认文案，让 AI 生成可编辑、可复制的生图提示词；本期不直接生成图片。
14. 所有编辑形成本地工作副本，用户显式保存；服务端按修订号和 ETag 防止并发覆盖。
15. 重合成冻结一个已保存时间轴版本，创建统一任务并进入任务中心；不重新调用声音克隆或数字人底片模型。
16. 页面覆盖加载、空、失败、无权限、只读、素材处理中、版本冲突、模板下架、保存和任务状态。

### 4.2 明确不做

- 不修改 `V1` 主视频或 `A1` 人声的开始、结束、播放速度和口型同步关系。
- 不在第 6 步替换人物、声音、已确认文案或底片；这些变化必须回到对应上游步骤并生成新的底片。
- 不做主视频剪切、拼接、转场、多机位、色彩曲线、蒙版路径、运动跟踪、关键帧曲线或专业调色。
- 不让画中画视频声音自动进入混音；画中画视频默认且固定静音。若未来需要其声音，必须新增显式关联音轨规格。
- 不允许用户上传字体、花字模板代码、任意 CSS、HTML、SVG、FFmpeg 表达式或脚本。
- 不把短期预览 URL、对象存储真实路径或外部服务响应保存为时间轴事实。
- 不把 AI 生成的花字建议自动写入时间轴，不把 AI 生图提示词自动提交到图片生成服务。
- 不在本规格顺带实现运营端模板管理、历史版本恢复 UI 或多人实时协同编辑。
- 不新增 DDD、Clean Architecture、Hexagonal Architecture、`application`、`port`、`adapter` 等平行业务分层。

## 5. 页面布局

### 5.1 总体结构

主内容从上到下固定为：

```text
┌──────────────────────── 上部工作区 ────────────────────────┐
│ 真实视频预览（左）                    元素信息（右）         │
└────────────────────────────────────────────────────────────┘
┌──────────────────────── 添加元素区 ────────────────────────┐
│ 图片  画中画  字幕  花字  画面特效  背景音乐  音效  AI提示词 │
└────────────────────────────────────────────────────────────┘
┌──────────────────────── 时间轴通栏 ────────────────────────┐
│ 工具栏 / 标尺 / 上方画面轨 / 固定 V1 / 下方音轨 / 状态栏    │
└────────────────────────────────────────────────────────────┘
```

- 右侧信息区只与视频预览并排，绝不延伸到时间轴右侧。
- 添加元素区是横向工具带，不是侧边导航、抽屉常驻栏或时间轴内部浮层。
- 时间轴使用剩余完整宽度；轨道标签列固定，时间内容区可水平滚动和缩放。
- 页面采用项目主题 Token；不通过全局 CSS 或 Ant Design 内部类名控制组件状态。

### 5.2 上部预览区

- 预览画面保持 9:16，使用授权后的真实视频地址，默认不自动播放。
- 预览区提供播放/暂停、当前时间、总时长、音量、逐帧前后和适应窗口。
- 预览层叠顺序和服务端合成顺序使用同一规范数据；浏览器预览不能维护第二套独立位置或样式事实。
- 元素信息区桌面宽度为 340～380px，由粘性头部、可滚动表单和粘性底部操作区组成。
- 元素信息区无选择时显示选择说明及当前播放头下可选元素，不显示虚构默认字幕。

### 5.3 响应式边界

- 主要验收视口为 1280×720、1440×900 和 1920×1080；1280px 及以上保持预览与信息左右布局。
- 1024～1279px 时，元素信息区可折叠到预览下方，但仍位于添加元素区和时间轴上方；不得移到时间轴右侧。
- 小视口不产生浏览器级横向滚动，时间轴仅在自己的时间内容区水平滚动。
- Electron/Web 窗口小于 1024px 时显示“建议扩大窗口”的非阻断提示，所有保存和返回操作仍可达。

## 6. 时间轴模型与轨道语义

### 6.1 默认七轨顺序

从上到下固定为：

| 顺序 | 轨道 | 用途 | 合成语义 |
| --- | --- | --- | --- |
| 1 | `T1 花字` | 普通花字和 AI 建议花字 | 上方画面元素最高层。 |
| 2 | `S1 字幕` | 已确认文案派生字幕 | 位于花字下方，始终保证可读。 |
| 3 | `V2 画中画/画面特效` | 图片、画中画视频和画面特效 | 位于主视频上方。 |
| 4 | `V1 主视频` | 第 5 步底片视觉流 | 固定中心锚点，锁定。 |
| 5 | `A1 人声` | 已确认的规范人声音轨 | 与 `V1` 编组锁定，时间不可编辑。 |
| 6 | `BGM 背景音乐` | 背景音乐 | 独立音量、循环和人声压低。 |
| 7 | `SFX 特效声音` | 短音效 | 独立音量、声像和淡入淡出。 |

“轨道纵向排列”只表示轨道从上到下堆叠；时间轴仍然从左向右推进。

### 6.2 主视频固定居中

时间轴实现为三个共享时间坐标的区域：

1. 上半区：`T1`、`S1`、`V2` 及其动态子轨，底部对齐 `V1`，可以独立纵向滚动。
2. 中间：固定高度的 `V1`，始终钉在时间轴可视区中心，不允许上下重排。
3. 下半区：`A1`、`BGM`、`SFX` 及其动态子轨，顶部紧贴 `V1`，可以独立纵向滚动。

三个区域共用时间标尺、播放头、横向滚动、缩放比例和吸附坐标。动态新增子轨只能扩展对应上区或下区，不能把 `V1` 推离中心。

### 6.3 同类重叠、渲染顺序和派生子轨

- 同类元素允许时间重叠；重叠时自动分配同轨族子轨，不覆盖成一个无法选择的片段。
- 画面层级只有一个规范事实 `renderOrder`，取 0～9999 的整数；同轨族中数值越大越靠近观众。新画面元素默认使用该轨族当前最大值加一，达到上限时先由 Service 以保持相对顺序的方式重编号。
- 跨轨族层级固定为 `T1 > S1 > V2 > V1`；`renderOrder` 只在同一轨族内比较，不能把 `V2` 元素提升到字幕或花字上方。
- `laneIndex` 不是线上字段、数据库字段或合成事实，只是客户端根据当前可视片段派生的排版结果：按 `startMs → endMs → renderOrder → editorElementKey` 稳定排序，将元素放入第一个不重叠子轨；更高 `renderOrder` 的重叠元素显示在更上方子轨。
- `BGM`、`SFX` 子轨使用同一确定性排版算法，但顺序只用于整理和选择，不代表混音优先级。
- 删除或移动片段后可以重新压缩空子轨，但不能修改 `renderOrder`，因此预览与服务端合成层级不会随子轨压缩改变。
- 每个项目最多 200 个可编辑元素；超过上限时阻止新增并给出明确提示，不允许浏览器或合成器无界退化。

### 6.4 规范时间

- 时间轴内部和线上请求统一使用非负整数毫秒 `startMs/endMs`。
- 规范总时长 `canonicalDurationMs` 来自第 5 步真实人声音频的 ffprobe 时长；底片视频与人声差异必须先满足既有 0.25 秒校验。
- 所有片段满足 `0 ≤ startMs < endMs ≤ canonicalDurationMs`。
- `V1` 与 `A1` 固定覆盖 `[0, canonicalDurationMs]`。
- 输出固定为 1080×1920、30fps、H.264/AAC MP4；服务端在渲染边界把毫秒规范转换为帧和采样位置，数据库不存浏览器像素时间。
- `V1` 只提供视觉流，最终混音中的人声只取 `A1` 一次；不得把底片内嵌音频和 `A1` 重复叠加。

## 7. 选择、移动、拉伸和撤销

### 7.1 唯一选择事实

- 前端只维护一个 `selectedEditorElementKey` 作为选择事实源。每个元素在一次编辑会话中都有不可变的 `editorElementKey`：已保存元素首次加载时由 `elementId` 派生，新元素直接使用 `clientElementKey`；服务端 Long ID 不是编辑器内部引用键。
- 点击时间轴片段后，片段高亮、信息区切换，并把播放头移动到点击位置；若点击位置不在片段内，则移动到片段开始处。
- 点击预览中的字幕、图片/视频或花字，反向选中对应时间轴片段，并自动把对应轨道滚动到可见位置。
- `BGM` 和 `SFX` 只能从时间轴选择，但仍使用同一个信息区。
- 点击预览空白、时间轴空白或按 `Esc` 清空选择；正在拖动时第一次 `Esc` 只取消当前拖动并恢复原值。
- 播放头离开选中元素区间后保留选择，信息区显示“当前时刻未展示”，不丢失用户正在编辑的字段。

### 7.2 时间轴移动和拉伸

- 拖片段主体：只修改开始时间，时长保持不变，不对相邻片段做磁性挤压。
- 拖左/右手柄：修改开始或结束；实时显示开始、结束和时长。
- 吸附对象：播放头、主视频首尾、同区域其他片段首尾；视觉阈值为 8px。
- 按住 `Alt` 临时关闭吸附；键盘左右方向键移动 100ms，`Shift + 左右方向键` 移动 1 秒。
- 拖动和拉伸期间实时更新预览，释放指针后只写入一条撤销记录。
- 指针取消、窗口失焦或按 `Esc` 时恢复拖动前值，不产生撤销记录。
- 所有移动和拉伸都被限制在主视频范围内；达到边界后停止，不允许产生负时间或延长成片。
- `V1`、`A1` 和字幕源时间片段不显示拉伸手柄；它们的时间由上游规范事实决定。
- 元素区间统一采用半开区间 `[startMs, endMs)`；30fps 输出的开始帧向下取整、结束帧向上取整后再钳制到规范总帧数，最后一帧不重复计入相邻片段。循环次数为 `ceil(displayDurationMs / playableDurationMs)`，只有余数大于 0 时显示“末轮”时长。

### 7.3 指针、触摸与焦点仲裁

- 鼠标只有从片段主体或左右手柄按下后才捕获拖动；左右手柄可视宽度不小于 8px，实际命中区不小于 24×24px。
- 触摸默认滚动优先：轻点只选择，纵向/横向滚动不移动片段；长按 300ms 后移动片段，或从明确手柄开始拉伸。位移超过滚动阈值前不捕获，取消时恢复原值。
- 时间轴方向键只在片段本身获得焦点、且焦点不在输入框/文本区/下拉/颜色控件、也不处于 IME composition 时生效。
- 画布方向键只在可拖拽花字获得焦点时生效；表单中的方向键保持原生光标和数值行为。
- 删除后焦点回到同轨最近元素或轨道标签；采用 AI 候选后焦点回到新元素；关闭冲突/确认弹窗后回到触发按钮。

### 7.4 撤销、重做和删除

- 本地命令栈最多保留 100 个用户动作；一次画布拖动、一次片段拖动、一次字段编辑事务或一次批量应用分别是一条记录。
- 所有控件遵循 `begin → update → commit/cancel`：文本在获得焦点时 begin、输入时只更新临时值、`Enter`（多行说明除外）或失焦时 commit、`Esc` 时 cancel；滑块/拖动数值在按下时 begin、释放时 commit；颜色面板在打开时 begin、关闭或确认时 commit、`Esc` 时 cancel。
- 切换选择前先提交当前合法临时值；存在瞬时非法值时阻止切换并聚焦首个错误字段，用户也可按 `Esc` 取消该字段编辑。未 commit 的值不能进入保存请求。
- 保存不会清空撤销栈；服务端 ID 映射必须在一个 reducer 动作内更新工作副本、已保存基线、选择、撤销和重做命令中的持久化引用，但 `editorElementKey` 始终不变，因此保存不能清空历史。撤销到已保存基线之前时页面重新变为“有未保存修改”。
- 删除图片、画中画、花字、画面特效、BGM 或 SFX 必须二次确认。
- 字幕不允许逐段删除；“删除字幕”实际是显式关闭整个字幕轨。重新开启时从当前已确认文案完整重建。
- 删除选中元素后清空选择；撤销删除时恢复元素和原选择。

## 8. 各类元素行为

### 8.1 图片和画中画

图片素材支持两种展示模式：

- `full_frame`：作为全画面插图覆盖主画面，使用 `contain` 或 `cover` 适配。
- `picture_in_picture`：作为画中画，使用左上、右上、左下、右下四个预设。

画中画规则：

- 默认右上角，水平和垂直安全边距均为对应画布尺寸的 5%。
- 用户可调整缩放、适配方式、圆角和透明度；元素完整边界不能越出全局安全区。
- 图片没有源时长，可拉伸到主视频范围内任意合法时长。
- 画中画视频默认静音，不能把源音频混入 `A1/BGM/SFX`。
- 视频可设置素材入点和出点；有效源时长为 `sourceOutMs - sourceInMs`。
- 展示时长不超过有效源时长时，从素材入点开始正常播放，不变速。
- 展示时长超过有效源时长时强制自动循环，预览取帧为：

```text
sourceInMs + ((currentTimeMs - elementStartMs) mod playableDurationMs)
```

- 最后一轮不足完整源时长时直接截断；循环点为硬切，不自动添加交叉淡化。
- 时间轴片段显示循环次数和循环分界线，例如“循环 ×3，末轮 1.2 秒”。
- 源时长为零、解析失败或素材未 ready 时元素进入错误状态，不能保存为可重合成版本。

### 8.2 花字

花字字段包括文字、开始/结束、位置、缩放、旋转、透明度、字体、字重、填充、描边、背景、阴影、模板和动画快照。

- 文本以 Unicode code point 计数；全角/汉字权重 2、ASCII 权重 1，总展示权重不超过 32。
- 花字完整包围盒被限制在画面 5% 安全区内，不能只限制锚点而让文字主体溢出。
- 画布拖动使用指针捕获；拖动时实时更新位置，释放后形成一条撤销记录。
- `X/Y` 使用 0～1000 的整数归一坐标，分别表示元素中心在安全区内的水平和垂直位置；右侧输入和画布拖动双向同步。
- 位置也支持键盘微调：方向键 1 个归一单位，`Shift` 组合键 10 个单位。
- 应用模板只覆盖字体、填充、描边、装饰和动画，不覆盖文字、时间、位置、缩放和旋转。
- 模板后继续手工修改时标记“基于某模板 · 已自定义”，可恢复到该版本模板快照。

本期内置花字模板固定且仅包含以下 5 个；新增模板属于后续目录版本变更：

| 代码 | 中文名 | 样式与动画 |
| --- | --- | --- |
| `goldBold` | 金色爆发 | 金色粗体、深色描边和轻光芒；弹入、轻微闪光、淡出。 |
| `blueLabel` | 蓝色标签 | 蓝色标签底与白字；左侧滑入、驻留脉冲、淡出。 |
| `whiteShadow` | 白字浮影 | 白字与柔和深色阴影；上浮进入、静态驻留、下沉淡出。 |
| `redStamp` | 红色印章 | 红色笔刷/印章感；盖章缩放进入、轻颗粒驻留、快速淡出。 |
| `neonPulse` | 霓虹脉冲 | 青紫渐变与外发光；弹入、低频呼吸、缩小淡出。 |

每个模板具有稳定 `templateCode + templateVersion` 和服务端可执行快照。模板升级不能改写已保存版本；模板下架后仍按元素中冻结的旧快照渲染，但不能用于新元素。

### 8.3 字幕

字幕文本来自当前已确认文案和规范人声，不在第 6 步任意改写。用户如需修改正文，必须返回确认文案并重新生成后续资源。

完整性规则由 `subtitle-normalize-1` 和 `subtitle-layout-1` 两个版本化规范共同决定：

1. `subtitle-normalize-1` 先做 UTF-8/NFC；CR、LF、TAB、Unicode 行/段分隔符转换为空格，连续 Unicode 空白折叠为一个 U+0020，首尾空白删除。
2. 版本化标点 manifest 以固定 Unicode 数据版本和 SHA-256 发布。默认移除 Unicode `P*` 标点；以下语义例外保留：两个十进制数字之间的半角/全角点和逗号（全角规范为半角）、两个字母/数字之间的连字符和英文撇号，以及 `%`、`‰`、`℃`、`°`、货币符号和数学运算符。这样 `99.9%` 必须保持 `99.9%`，不能变成 `999%`。
3. 除上述显式移除和规范化外，所有汉字、字母、数字和语义符号必须按原顺序恰好出现一次。浏览器、Java 校验和合成器都读取同一份 manifest，不使用各自语言运行时的宽泛正则猜测标点集合。
4. 字幕源片段按文案语义句和人声时间建立；`subtitle-layout-1` 把一个源片段派生为一个或多个单行 `displayPages`。同一个带 SHA-256 的布局 WASM、字体文件和 manifest 同时用于浏览器预览及服务端规范化；服务端保存响应返回权威 `displayPages` 及布局摘要，合成器直接消费该几何结果，不再次用另一套规则换行。
5. 每个字体目录项返回 `fontCode/fontVersion/fontSha256`，布局目录返回 `layoutEngineVersion/layoutEngineSha256/normalizationVersion/normalizationManifestSha256`。任一文件未加载、hash 不符或版本不匹配时禁止 fallback 字体提交，页面显示可恢复错误。
6. 每个展示页使用 `nowrap`，禁止省略号、裁切和两行换行。90% 上限按最终渲染包围盒计算，包含字形、描边外扩和背景内边距；圆角本身不增加盒尺寸，整个盒还必须位于画面安全区内。
7. 优先使用匹配当前文案的精确词元时间；没有精确时间时，沿用规范音频时长和 grapheme 权重进行确定性分配。所有计算使用整数毫秒和固定的“余数从前到后各补 1ms”规则，禁止浏览器浮点自行舍入。
8. 长句或不可断开的长词按 grapheme cluster 继续拆成相邻展示页，不删除字符；单个 grapheme 在允许最大字号下仍超宽时缩小该页实际渲染字号直到 24px，24px 仍超宽则拒绝样式而不是裁切。
9. 服务端保存和重合成前都校验“所有字幕源片段经 `subtitle-normalize-1` 串联的结果 = 已确认文案按同版本规范化的结果”；不相等时拒绝。

字幕样式字段：

- 字体：初始目录包含思源黑体、思源宋体和霞鹜文楷，对应服务端已登记字体文件。
- 字号：24～72px，默认 42px。
- 文字颜色。
- 背景开关；开启后可设置背景色、透明度、内边距和圆角。
- 描边开关；开启后可设置描边色和 0～12px 描边宽度。
- 对齐方式、底部安全区偏移和最大宽度；最大宽度不能超过 90%。

默认只修改当前字幕源片段的样式。信息区提供显式“将当前样式应用到全部字幕”，该动作只批量复制样式，绝不复制文本、开始时间或结束时间，并形成一条可撤销记录。

### 8.4 画面特效

画面特效与画中画共用 `V2` 轨族，但类型和参数独立。初始内置：

- `softGlow`：柔光。
- `spotlight`：中心聚光和边缘压暗。
- `punchZoom`：短促推进缩放。
- `filmFlash`：可控强度闪白。

特效字段包括模板代码/版本、作用对象、开始/结束、强度、透明度、混合方式、淡入/淡出和模板专属参数。模板参数只按服务端白名单 schema 展示和保存；客户端不能提交任意滤镜表达式。作用对象只允许 `main_video` 或一个当前时间轴内的合法画面元素。

### 8.5 背景音乐

- 字段包括素材、开始/结束、素材入点/出点、循环、音量、静音、淡入/淡出、人声自动压低和压低量。
- 默认循环开启；关闭循环后，展示时长不能超过有效源时长。
- 默认音量为 20%，默认开启人声自动压低；精确默认值由本规格固定，不读取素材自身音量猜测。
- 音量范围 0～200%，淡入/淡出不能超过元素时长的一半。
- BGM 循环是独立用户设置，不能复用画中画视频的强制自动循环规则。

### 8.6 特效声音

- 字段包括素材、类别、波形、开始/结束、素材入点/出点、音量、左右声像、淡入/淡出和循环。
- 循环默认关闭；关闭时展示时长不能超过有效源时长。
- 声像范围为 -100（左）到 100（右），音量范围 0～200%。
- SFX 可重叠；每个片段独立混音，不以轨道上下顺序决定优先级。

### 8.7 主视频和人声

- `V1` 信息区显示底片名称、分辨率、帧率、规范时长、素材状态和来源任务，只读。
- `A1` 信息区显示声音名称、规范时长、音量、静音和口型锁定状态；时间只读，音量可编辑。
- 修改 `A1` 音量不改变规范音频文件，也不破坏 `V1/A1` 的时间锁定。
- 更换底片或声音只能返回上游步骤；成功生成新底片后创建新的时间轴基线，旧版本保留为历史事实，不能静默迁移为当前版本。

## 9. 添加元素区与 AI 辅助

### 9.1 工具带顺序

添加元素区从左到右固定为：

1. 图片。
2. 画中画。
3. 字幕。
4. 花字。
5. 画面特效。
6. 背景音乐。
7. 音效。
8. AI 生图提示词。

“图片”默认创建 `full_frame` 图片；“画中画”允许选择图片或视频并默认创建 `picture_in_picture`。素材选择器同时支持从当前工作区私有素材中选择或进入通用上传会话；只有 `ready` 素材可以插入时间轴。

默认插入规则：

- 元素从当前播放头开始；剩余时长不足默认时长时向左移动，使结束点落在主视频末尾。播放头恰在末尾时同样采用该规则。
- 图片、画中画和花字默认 3 秒，画面特效默认 2 秒；画中画视频默认时长取 `min(3秒, 有效源时长)`，用户拉长后才触发自动循环。
- BGM 默认从播放头持续到主视频末尾并开启循环；SFX 默认取 `min(有效源时长, 5秒)` 且不开循环。
- 字幕按钮在字幕轨关闭时完整重建并开启字幕；已开启时只选中字幕轨并打开样式设置，不创建重复字幕。
- 默认区间无法满足最小 100ms 时阻止插入并提示移动播放头；不创建零时长元素。
- “保存草稿”和“重合成”固定在时间轴工具栏右侧并保持可见；添加元素区不承载版本主操作。

上传范围：

- 图片：JPEG、PNG、WebP，单文件不超过 20MiB，解码后不超过 25,000,000 像素。
- 视频：MP4、MOV、WebM，单文件不超过 500MiB、时长不超过 10 分钟，必须真实探测音视频流。
- 音频：MP3、WAV、M4A、FLAC，单文件不超过 100MiB、时长不超过 30 分钟，必须真实探测音频流。
- 后缀、MIME、文件头和真实解码/探测结果必须一致；浏览器校验只改善体验，服务端重新校验。

### 9.2 AI 生图提示词

用户先在字幕/文案选择器中选择一个非空文案范围，再创建提示词任务。请求使用服务端已确认的文案版本和 Unicode code point 起止偏移，不能只信任客户端提交的一段任意文本。

成功结果固定返回 3 个候选，每项包含：

- 中文正向提示词。
- 中文负向提示词。
- 推荐视觉风格。
- 推荐构图和 9:16 画幅说明。
- 与所选文案的语义理由。

用户可以编辑、复制候选，然后自行上传或选择图片；结果不会自动插入时间轴，也不会调用图片生成模型。

### 9.3 AI 花字建议

- 用户可以选择当前播放头附近、一个字幕源片段或一个文案范围；请求使用严格判别联合：`{anchorType:'playhead',atMs}`、`{anchorType:'subtitle',subtitleElementId}` 或 `{anchorType:'script_range',scriptVersionId,startCodePoint,endCodePoint}`，三种字段不能混用。
- AI 返回 3～5 个候选，每项包含稳定 `candidateId`、文字、理由、建议开始/结束、推荐模板和建议位置。
- 服务端校验候选文字长度、文案锚点和模板可用性；非法候选不进入结果。
- 用户逐项点击“加入时间轴”后才创建本地花字元素；采用键固定为 `resultId:candidateId`，同一采用键在本次编辑会话内始终映射到同一个 `editorElementKey/clientElementKey`，双击或重复响应只能选中已有元素，不能再创建一份。
- AI 结果绑定创建时的时间轴版本。当前版本变化后仍可查看和复制文字，但不能直接采用带旧时间锚点的候选；页面显示“基于旧版本”，用户必须对当前版本重新生成建议。

### 9.4 AI 任务与额度

- 生图提示词注册固定为 `taskType='timeline_image_prompt_generate'`、`operationType='timeline_image_prompt_generate'`；花字建议固定为 `taskType='timeline_fancy_text_suggest'`、`operationType='timeline_fancy_text_suggest'`。两者的 `resourceType='timeline_version'`、`resultRefType='timeline_ai_result'`，resource ID 为创建时的时间轴版本 ID。
- 两者都是文本模型生成，必须进入统一任务中心，使用 `ai_text_credit` 和当前已发布价格表；不得在前端写死价格。
- 创建前复用 `GET /api/quota/tariffs?operationType=<type>`：返回严格报价 `{operationType, quotaUnit:'ai_text_credit', quotaAmount:string, tariffVersion:string, publishedAt:string}`。页面同时读取 `GET /api/quota/account`，展示本次固定额度、价格版本和可用余额后才允许确认。
- 创建请求字段名统一为 `expectedTariffVersion`。后端发现价格变化时复用 `46115 TARIFF_VERSION_CHANGED`，返回旧/新版本和新额度；前端刷新报价并要求再次确认，不能自动按新价提交。余额不足复用 `46114`，不能创建任务。
- 上线门禁必须同时注册两种 `AiTaskType`、两种 quota operation、`createChargeableTask` DTO 映射、执行 handler、结果引用解析器以及已发布 tariff；缺少任一项时入口不可用并显示配置失败，不能免费降级。
- 任务中心标签固定为“AI 生图提示词”和“AI 花字建议”，白名单详情目标固定为 `detailTarget={type:'studio_timeline',draftId,timelineVersionId}`，由前端映射回第 6 步；不得从 resource 字段拼 URL。
- 收费任务创建遵循公共锁序 `draft → timeline → operation_slot → quota_account → task_or_group_member`，锁定当前价格对应额度后才创建根任务；成功后按同一 `usageOperationId` 结算，失败/取消后释放，回调、重投和租约恢复不得重复扣减。
- 主动重新生成使用新幂等键、新根任务和新的用量操作；基础设施租约恢复复用原任务和用量操作。
- 没有有效价格或余额不足时直接使用现有稳定价格/额度错误阻断，不免费降级，也不自动插入规则模板假装 AI 成功。
- 执行 handler 在真实模型调用前、且持有当前执行 lease 时追加 provider attempt。模型返回后，在同一外层事务按当前 lease 条件执行“严格校验候选 → 以 `root_task_id` 唯一插入 `av_timeline_ai_result` → `markSuccess(resultRefType='timeline_ai_result')` → settle”；重复投递只允许回读相同摘要的原结果，过期 worker、不同摘要或已终态任务均不能写结果或结算。

## 10. 元素信息区

### 10.1 通用结构

- 粘性头部：类型、名称、轨道、元素 ID、显隐、锁定、保存状态。
- 固定展开“片段”分组：开始、结束、时长；只读元素显示锁定原因。
- 类型主分组默认展开，高级参数默认折叠。
- 粘性底部：复制、重置/应用全轨、删除；危险操作二次确认。
- 所有表单为受控表单，并以 `selectedEditorElementKey` 重新绑定；禁止用 `defaultValue` 保留上一个元素的字段。

### 10.2 类型字段

| 类型 | 必须展示/编辑的字段 |
| --- | --- |
| 图片/画中画视频 | 缩略图、文件名、类型、分辨率、大小、源时长、处理状态、预览/替换、开始/结束、入点/出点、显示模式、四角位置、边距、缩放、适配、圆角、透明度、循环次数、视频静音。 |
| 花字 | 文字、开始/结束、X/Y、缩放、旋转、透明度、字体、字重、填充、描边、背景、阴影、模板卡和恢复模板。 |
| 字幕 | 只读源文本、开始/结束、字体、字号、文字色、背景开关/颜色/透明度/内边距/圆角、描边开关/颜色/宽度、对齐、安全区、应用全部字幕。 |
| 画面特效 | 模板缩略图、名称、类别、版本、作用对象、开始/结束、强度、透明度、混合、淡入/淡出、白名单专属参数。 |
| BGM | 名称、来源、源时长、开始/结束、入点/出点、循环、音量、静音、淡入/淡出、人声压低。 |
| SFX | 名称、类别、波形、源时长、开始/结束、入点/出点、音量、声像、淡入/淡出、循环。 |
| V1/A1 | 来源、状态、规范时长、锁定说明；A1 额外提供音量和静音。 |

### 10.3 逐类型操作矩阵

| 类型 | 显隐 | 锁定 | 复制 | 移动/拉伸 | 替换素材 | 删除 |
| --- | --- | --- | --- | --- | --- | --- |
| `V1` | 只读开启 | 系统锁定 | 否 | 否 | 否 | 否 |
| `A1` | 只读开启 | 系统锁定时间 | 否 | 否 | 否；只可改音量/静音 | 否 |
| 字幕源片段 | 仅通过“字幕轨开启/关闭”统一控制 | 时间和文本系统锁定 | 否 | 否 | 否 | 不可逐段删除 |
| 图片/画中画 | 是 | 用户可锁 | 是 | 未锁时可 | 未锁时可 | 未锁时可 |
| 花字 | 是 | 用户可锁 | 是 | 未锁时可 | 不适用 | 未锁时可 |
| 画面特效 | 是 | 用户可锁 | 是 | 未锁时可 | 不适用 | 未锁时可 |
| BGM/SFX | 是 | 用户可锁 | 是 | 未锁时可 | 未锁时可 | 未锁时可 |

用户锁定后禁止时间轴移动/拉伸、画布拖动、表单修改、替换、复制和删除，只允许解锁、选择和预览；系统锁定不能由用户解除。轨道显隐是可保存事实，不等于删除。

### 10.4 信息区状态

| 状态 | 行为 |
| --- | --- |
| 未选择 | 提示从画布或时间轴选择，并列出播放头下可选元素。 |
| 加载/素材解析中 | 保留头部，正文显示骨架或处理进度。 |
| 已修改/保存中/已保存 | 头部明确状态，不能只靠颜色。 |
| 保存失败 | 保留本地修改和撤销栈，显示可重试原因。 |
| 字段错误 | 对应字段内联提示，时间轴片段显示错误图标，禁用重合成。 |
| 锁定/只读/无权限 | 字段不可编辑并说明原因，不伪装成空状态。 |
| 素材缺失/失败 | 保留时间与样式，允许替换；素材恢复前不能重合成。 |
| 版本冲突 | 显示服务端已变化，要求重新加载或放弃本地修改，不静默覆盖。 |
| 模板下架 | 保留已冻结快照并标记“历史模板”，允许更换，不能再次新建同模板。 |

## 11. 本地工作副本、保存与并发

### 11.1 编辑模型

- React Query/Umi 请求层保存服务端快照；`useTimelineEditor` 基于该快照创建唯一受控工作副本。
- 工作副本、撤销栈、当前选择、播放头和拖动状态属于本页编辑会话，不写入第二份可独立演进的全局服务端状态。
- 任何用户修改把页面标记为 `dirty`；切换步骤、关闭页面或刷新前必须提示未保存修改。
- 本期不把完整文案、媒体地址或时间轴工作副本写入 localStorage/sessionStorage。

### 11.2 乐观并发

- `GET` 返回 JSON 十进制字符串 `timelineRevision`，并通过响应 Header 返回带双引号的强 ETag；二者是不同的并发令牌，修订号在前端禁止转为 `number`。
- `PUT` 同时要求 `If-Match` 和正文十进制字符串 `expectedTimelineRevision`；两者必须对应同一个当前版本。
- 每次用户主动保存生成新的 `Idempotency-Key`；同一次按钮提交、网络重试和结果确认必须复用该键。服务端在归属范围内保存请求摘要和完整保存回执，同键同摘要返回原回执，同键异摘要返回 `46610`。
- 服务端在同一事务中按 tenant/workspace/owner/draft/timeline 和旧修订号条件更新，追加不可变版本并递增修订。
- 任一并发令牌不匹配都返回稳定冲突，绝不执行最后写入覆盖。
- 冲突后本期不自动合并；用户可以保留当前本地副本用于手工比对，选择“重新加载”时才丢弃。

### 11.3 保存与重合成按钮

- “保存草稿”只保存时间轴，不创建重合成任务。
- “重合成”只接受已保存、无字段错误的当前版本。
- 存在未保存修改时，点击“重合成”打开确认框，用户可选择“保存并重合成”或取消；前端先等待保存成功，再用返回的新版本创建任务。
- 保存冲突或失败时不得创建任务；创建任务失败时已成功保存的版本保持有效，不能回滚成旧版本。
- 网络结果未知时，保存先以原 `Idempotency-Key` 重试并恢复原始 `resultRevision/versionId/elementIdMappings/ETag` 回执，再读取当前时间轴确认它是否已被其他会话继续推进；任务创建同样必须复用原键查询结果，不能生成新键盲重试。

## 12. 领域与持久化设计

### 12.1 时间轴聚合

生产实现新增 timeline 聚合，保持贫血 Entity + Service 编排：

| 对象 | 职责 |
| --- | --- |
| `av_timeline` | 当前时间轴头：归属、草稿、底片/声音引用、规范时长、当前修订和当前版本。 |
| `av_timeline_element` | 当前可编辑元素：通用时间、轨道、子轨、显隐、素材引用和严格版本化类型 payload。 |
| `av_timeline_version` | 每次成功保存的不可变规范快照、内容摘要和创建 actor；重合成只消费该快照。 |
| `av_timeline_ai_result` | AI 提示词/花字建议的不可变任务结果，绑定根任务、时间轴版本、文案版本和来源范围。 |
| `av_timeline_save_receipt` | 草稿范围保存幂等回执：原时间轴、基线修订、请求摘要、结果修订/版本、元素 ID 映射和强 ETag。 |
| `av_timeline_task_input` | 按根任务冻结的不可变输入；同时服务重合成和两类 AI 文本任务。 |
| `av_timeline_task_asset_ref` | 根任务到素材 ID、素材版本与内容摘要的可查询保活引用，不把引用只藏在 JSON。 |
| `av_timeline_recompose_slot` | 每个时间轴版本的活跃重合成数据库仲裁槽；任务终态后释放活跃位但保留审计行。 |
| `av_timeline_output_attempt` | 每个 root/execution/lease generation 的独立输出对象预留，持久保存对象 key、私有 asset ID 和补偿状态。 |

上述 Entity 均继承 `BaseEntity`，且每张表显式保存 `tenant_id/workspace_id/owner_type/owner_id`；属于草稿时间轴的表同时保存 `draft_id/timeline_id` 或可沿唯一父引用交叉验证的对应键。本期创作归属固定为 `owner_type='personal'`、`owner_id=currentAppUserId`；这些字段全部由 `AppLoginHelper` 与当前工作区派生，HTTP 不接收。可变的 timeline、element、slot 和 output attempt 行额外保存 `created_actor_type/created_actor_id` 与 `updated_actor_type/updated_actor_id`；append-only 的 version、AI result、save receipt、task input 和 task asset ref 保存创建事实 `actor_type/actor_id`。actor type 当前只允许 `app_user`，不能依赖 `BaseEntity.createBy/updateBy` 中可能跨身份域的裸数字判断主体。append-only 表只允许追加和读取，禁止业务更新或物理删除；本需求不新增 `BaseEntity` 例外。

### 12.2 元素稳定类型

类型全集固定为：

```text
main_video
voice
subtitle
overlay_image
overlay_video
fancy_text
visual_effect
bgm
sfx
```

轨道代码全集固定为：`T1 | S1 | V2 | V1 | A1 | BGM | SFX`。服务端根据元素类型校验唯一合法轨道，客户端不能把音频元素伪装到画面轨道。

通用持久化字段至少包含：

- `element_id`：服务端 Long 主键，HTTP 按十进制字符串返回。
- `timeline_id`、`element_type`、`track_code`。
- `start_ms`、`end_ms`、`visible_flag`、`locked_flag`、`render_order`。
- 可空 `asset_id`，只保存归属校验后的稳定私有素材引用。
- `payload_schema_version` 和 `payload_json`。

类型 payload 只承载第 8 节登记的变化字段，并按不同 `elementType + payloadSchemaVersion` 严格解析；未知字段、未知版本和越界值必须拒绝。归属、素材关系、时间和高频排序字段不能藏进 JSON。

### 12.3 新元素 ID

- 已保存元素使用服务端 `elementId`。
- 新建但未保存的元素使用客户端生成的 UUID v4 `clientElementKey`，只作为一次保存请求内的关联键，不作为数据库主键。
- 保存事务为每个新元素分配 Long ID，响应返回 `clientElementKey -> elementId` 映射和完整规范化时间轴，映射同时写入不可变保存回执。
- 同一保存传输重试必须复用原 `Idempotency-Key`；服务端按回执返回原映射和原结果版本，不再次分配元素 ID。只重用 `clientElementKey` 但更换幂等键不构成合法重试。

### 12.4 首次初始化和上游失效

- 第 5 步底片成功关联到草稿后、步骤守卫解锁第 6 步前，Service 在唯一约束保护下从当前成功底片、规范人声和已确认文案幂等创建 `revision=1`；`GET` 只读取，不以查询请求产生持久化副作用。
- `V1/A1` 自动创建并锁定；字幕开启时从确认文案完整创建源片段。
- 底片、人声或确认文案任一不再匹配草稿当前步骤守卫时，旧时间轴只读，不能继续保存或重合成。
- 上游重新生成成功后创建新的当前时间轴基线；旧时间轴和版本用于历史追溯，不双写、不迁移成新底片的当前事实。

### 12.5 数据库约束、索引与保存事务

实现计划必须给出新的 MySQL 8 前向迁移，并至少冻结以下数据库最终仲裁：

- `av_script_draft` 增加可空 `current_timeline_id`。首次初始化先锁草稿行，再按当前确认文案版本/摘要、规范人声素材版本/摘要和底片素材版本/摘要计算 `upstream_fingerprint=SHA-256`；`av_timeline` 以 `UNIQUE(tenant_id,workspace_id,owner_type,owner_id,draft_id,upstream_fingerprint)` 保证同一上游基线只创建一次，成功后在同一事务更新草稿指针。不得用 MySQL 不支持的伪条件唯一索引表达“当前”。
- `av_timeline_version` 使用 `UNIQUE(timeline_id,timeline_revision)`；`timeline_revision > 0`，版本快照的 `content_digest` 为规范 UTF-8 JSON 的 SHA-256。当前头的 `current_version_id` 与 `timeline_revision` 必须指向同一事务新插入的版本。
- `av_timeline_ai_result` 使用 `UNIQUE(root_task_id)`；`av_timeline_task_input` 同样使用 `UNIQUE(root_task_id)`；`av_timeline_task_asset_ref` 使用 `UNIQUE(root_task_id,asset_id,role_code)` 并建立 `(asset_id,root_task_id)` 反向引用索引。
- `av_timeline_save_receipt` 显式保存 `draft_id` 与原 `timeline_id`，使用 `UNIQUE(tenant_id,workspace_id,owner_id,draft_id,idempotency_key)`，并保存 `base_revision/request_digest/result_revision/version_id/element_id_mappings_json/strong_etag`；同一草稿不同上游基线不得复用同一键。回执中的映射是严格数组，不接受任意 JSON 字段。幂等键长度 1～128 且只允许 URL-safe 字符，请求摘要覆盖路径 draft、原 timeline、基线修订、`If-Match` 和规范化正文。
- `av_timeline_recompose_slot` 的 `root_task_id` 唯一，并使用 `active_slot TINYINT NULL`：活跃行为 `1`、终态为 `NULL`，`UNIQUE(tenant_id,timeline_version_id,active_slot)` 利用 MySQL 多个 `NULL` 可并存的语义实现每版本最多一个活跃根任务；`CHECK(active_slot IS NULL OR active_slot=1)`。终态事务释放活跃位，恢复扫描器按统一根任务终态补偿未释放槽，槽表不是另一套任务状态事实源。
- `av_timeline_output_attempt` 使用 `UNIQUE(root_task_id,execution_id,lease_revision,role_code)` 和 `UNIQUE(asset_id)`，每行保存不可变 `object_key`；状态只允许 `processing|ready|delete_pending|deleted|delete_failed`，只有持有当前 lease 的一行可转为 `ready`，其余行只能进入删除补偿。
- `av_timeline_element` 显式声明 `del_flag` 和 `@TableLogic`，并建立 `INDEX(timeline_id,track_code,start_ms,end_ms,render_order,element_id)`；数据库 CHECK 至少覆盖 `start_ms>=0`、`end_ms>start_ms`、`render_order BETWEEN 0 AND 9999` 和合法类型/轨道集合，跨行、跨表与规范时长校验由 Service 完成。`clientElementKey` 只存于保存回执映射，不进入元素主记录。
- `av_timeline` 与 `av_timeline_element` 是可变当前投影，使用逻辑删除且所有普通查询固定排除删除行；version、AI result、save receipt、task input 和 task asset ref 是 append-only 事实，不提供删除接口。重合成 slot 只允许按当前 lease/任务终态条件释放活跃位。

完整副本保存的事务锁序固定为 `draft → save receipt key → current timeline → current elements(element_id 升序)`。服务端先按路径 draft 与幂等键查回执：回执已存在时按其原 `timeline_id` 校验摘要并返回，再以已锁定草稿的 `current_timeline_id` 判断 `currentAdvanced`；不存在时才解析并锁定当前 timeline。进入事务前可做纯校验加速，事务内必须重新检查归属、上游指纹、revision、ETag、素材状态和模板版本。除系统维护的 `main_video` 外，当前所有可写且未删除的元素 ID 必须在 `elements` 与 `deletedElementIds` 之间形成不重不漏的精确分区；不可删除的 voice/subtitle 只能出现在 `elements`，锁定元素必须原样提交。遗漏、重复、额外或跨时间轴 ID 均返回结构化校验错误，不能静默保留或删除。

事务内按“锁定并复核 draft → 查询草稿范围幂等回执 → 无回执时锁当前 timeline/元素 → 写当前元素和逻辑删除 → 追加版本 → 按旧 revision 条件更新当前头 → 写保存回执”执行，任一步失败整体回滚；回执唯一键是并发插入的最终仲裁。已存在回执时，同键同请求摘要直接按回执中的原 timeline 返回 `resultRevision/versionId/elementIdMappings/ETag`，不再次更新当前投影；同键异摘要返回 `46610`。若原 timeline 已不是草稿当前头或同一 timeline 又被其他保存推进，响应同时标记 `currentAdvanced=true`，前端随后 GET 当前头并进入版本冲突比对，不能把旧回执当成新的编辑基线。

## 13. HTTP 契约草案

实现前必须把本节同步到 `docs/API_CONTRACT.md`。所有端点只装配到 `ai-video-user-api`，只接受 `app` 会话，并从当前会话派生 tenant、workspace 和 owner。

### 13.1 接口清单

| 方法与路径 | 用途 | 权限 |
| --- | --- | --- |
| `GET /api/studio/timeline-template-catalog` | 查询字体、花字和画面特效模板目录 | `aivideo:studio:query` |
| `GET /api/studio/script-drafts/{draftId}/timeline` | 读取当前时间轴 | `aivideo:studio:query` |
| `PUT /api/studio/script-drafts/{draftId}/timeline` | 保存完整工作副本 | `aivideo:studio:edit` |
| `POST /api/studio/script-drafts/{draftId}/timeline/recompositions` | 创建重合成任务 | `aivideo:studio:generate` |
| `POST /api/studio/script-drafts/{draftId}/timeline/image-prompt-generations` | 创建 AI 生图提示词任务 | `aivideo:studio:generate` |
| `POST /api/studio/script-drafts/{draftId}/timeline/fancy-text-suggestion-generations` | 创建 AI 花字建议任务 | `aivideo:studio:generate` |
| `GET /api/studio/script-drafts/{draftId}/timeline/ai-results/{resultId}` | 读取已成功 AI 结果 | `aivideo:studio:query` |
| `GET /api/assets?pageNum&pageSize&purpose=timeline_element&mediaType&keyword?` | 分页选择当前工作区可用于时间轴的 ready 私有素材 | `aivideo:asset:query` |
| `POST /api/assets/uploads` 及 parts/complete/cancel/status | 创建并推进时间轴素材上传会话 | `aivideo:asset:upload` |
| `GET /api/assets/{assetId}/access-url?disposition=inline` | 签发短期预览地址 | `aivideo:asset:download` |

素材继续复用通用上传会话和 access-url。资产分页请求只允许 `pageNum=1..10000`、`pageSize=1..100`、固定 `purpose='timeline_element'`、`mediaType='image'|'video'|'audio'` 与可选 1～100 code point 的 `keyword`；状态由服务端固定为 `ready`，不接受 owner、tenant、workspace、任意 category 或排序 SQL。`PageResult<TimelineAssetSummaryVO>` 的行严格为 `{assetId,fileName,mediaType,sizeBytes,width?,height?,durationMs?,thumbnailAvailable,createdAt}`，空页返回 `rows=[]`。

上传会话新增 `purpose=timeline_element`，并携带 `draftId` 与 `timelineElementType='overlay_image'|'overlay_video'|'bgm'|'sfx'`；服务端从元素类型派生允许的声明类型、探测类型、大小、时长和分辨率，不接受 owner、tenant、workspace 或任意 MIME 白名单。只有上传完成、病毒/内容检查和真实媒体探测均成功的 `ready` 素材才可写入时间轴；声明 MIME、文件头和 ffprobe 结果必须一致。

新权限 `aivideo:studio:edit` 必须与接口上线同一迁移授予默认 `personal_creator` 角色；迁移同时幂等校验/补齐 `aivideo:asset:query|upload|download` 三项素材权限。任何新增授权都要递增受影响用户的权限修订并按现有机制失效旧会话；不能只加注解而让现有创作用户全部得到 403。

### 13.2 关键响应

```ts
type TimelineTrackCode = 'T1' | 'S1' | 'V2' | 'V1' | 'A1' | 'BGM' | 'SFX';

type TimelineElementType =
  | 'main_video'
  | 'voice'
  | 'subtitle'
  | 'overlay_image'
  | 'overlay_video'
  | 'fancy_text'
  | 'visual_effect'
  | 'bgm'
  | 'sfx';

type HexColor = string; // 必须匹配 ^#[0-9A-F]{6}$，响应统一大写

interface TimelineElementBase {
  elementId: string;
  elementType: TimelineElementType;
  trackCode: TimelineTrackCode;
  renderOrder: number; // 0..9999 整数
  name: string;
  startMs: number;
  endMs: number;
  visible: boolean;
  locked: boolean;
  asset?: {
    assetId: string;
    fileName: string;
    mediaType: 'image' | 'video' | 'audio';
    status: 'processing' | 'ready' | 'failed';
    sizeBytes: string;
    width?: number;
    height?: number;
    sourceDurationMs?: number;
  };
  payloadSchemaVersion: 'timeline-element-1';
}

interface SubtitleStyleVO {
  fontCode: 'noto_sans_sc' | 'noto_serif_sc' | 'lxgw_wenkai';
  fontVersion: string;
  fontSizePx: number; // 24..72 整数
  textColor: HexColor;
  backgroundEnabled: boolean;
  backgroundColor?: HexColor;
  backgroundOpacityPermille?: number; // 0..1000
  backgroundPaddingPx?: number; // 0..48
  backgroundRadiusPx?: number; // 0..48
  outlineEnabled: boolean;
  outlineColor?: HexColor;
  outlineWidthPx?: number; // 0..12
  align: 'left' | 'center' | 'right';
  bottomOffsetPermille: number; // 50..350
  maxWidthPermille: number; // 500..900
}

interface SubtitleDisplayPageVO {
  pageNo: number;
  text: string;
  startMs: number;
  endMs: number;
  box: { x: number; y: number; width: number; height: number };
  actualFontSizePx: number;
}

type TimelineElementVO =
  | (TimelineElementBase & {
      elementType: 'main_video'; trackCode: 'V1';
      payload: { sourceTaskId: string; width: 1080; height: 1920; frameRate: 30 };
    })
  | (TimelineElementBase & {
      elementType: 'voice'; trackCode: 'A1';
      payload: { sourceVoiceId: string; volumePercent: number; muted: boolean };
    })
  | (TimelineElementBase & {
      elementType: 'subtitle'; trackCode: 'S1';
      payload: {
        sourceStartCodePoint: number;
        sourceEndCodePoint: number;
        sourceText: string;
        style: SubtitleStyleVO;
        displayPages: SubtitleDisplayPageVO[];
        layoutHash: string;
      };
    })
  | (TimelineElementBase & {
      elementType: 'overlay_image'; trackCode: 'V2';
      payload: {
        displayMode: 'full_frame' | 'picture_in_picture';
        fit: 'contain' | 'cover';
        corner?: 'top_left' | 'top_right' | 'bottom_left' | 'bottom_right';
        marginXPermille: number;
        marginYPermille: number;
        scalePermille: number;
        opacityPermille: number;
        radiusPx: number;
      };
    })
  | (TimelineElementBase & {
      elementType: 'overlay_video'; trackCode: 'V2';
      payload: {
        displayMode: 'picture_in_picture';
        sourceInMs: number;
        sourceOutMs: number;
        fit: 'contain' | 'cover';
        corner: 'top_left' | 'top_right' | 'bottom_left' | 'bottom_right';
        marginXPermille: number;
        marginYPermille: number;
        scalePermille: number;
        opacityPermille: number;
        radiusPx: number;
        muted: true;
        loopCount: number;
        finalLoopDurationMs?: number;
      };
    })
  | (TimelineElementBase & {
      elementType: 'fancy_text'; trackCode: 'T1';
      payload: {
        text: string;
        xPermille: number;
        yPermille: number;
        scalePermille: number;
        rotationMilliDegrees: number;
        opacityPermille: number;
        templateCode: 'goldBold' | 'blueLabel' | 'whiteShadow' | 'redStamp' | 'neonPulse';
        templateVersion: string;
        customized: boolean;
        styleOverrides: Array<{ fieldCode: string; value: string | number | boolean }>;
      };
    })
  | (TimelineElementBase & {
      elementType: 'visual_effect'; trackCode: 'V2';
      payload: {
        templateCode: 'softGlow' | 'spotlight' | 'punchZoom' | 'filmFlash';
        templateVersion: string;
        target: { type: 'main_video' } | { type: 'element'; elementId: string };
        intensityPermille: number;
        opacityPermille: number;
        blendMode: 'normal' | 'screen' | 'multiply' | 'overlay';
        fadeInMs: number;
        fadeOutMs: number;
        params: Array<{ paramCode: string; value: string | number | boolean }>;
      };
    })
  | (TimelineElementBase & {
      elementType: 'bgm'; trackCode: 'BGM';
      payload: {
        sourceInMs: number; sourceOutMs: number; loop: boolean;
        volumePercent: number; muted: boolean; fadeInMs: number; fadeOutMs: number;
        duckingEnabled: boolean; duckingPercent: number;
      };
    })
  | (TimelineElementBase & {
      elementType: 'sfx'; trackCode: 'SFX';
      payload: {
        sourceInMs: number; sourceOutMs: number; loop: boolean;
        volumePercent: number; muted: boolean; pan: number;
        fadeInMs: number; fadeOutMs: number;
      };
    });

interface TimelineVO {
  timelineId: string;
  timelineRevision: string;
  timelineVersionId: string;
  draftId: string;
  canonicalDurationMs: number;
  canvas: { width: 1080; height: 1920; frameRate: 30 };
  elements: TimelineElementVO[];
  canEdit: boolean;
  canRecompose: boolean;
  readOnlyReasonCode?: string;
  updatedAt: string;
}
```

范围统一为：`volumePercent/duckingPercent=0..200`、`pan=-100..100`、`marginXPermille/marginYPermille=0..200`、`scalePermille=100..3000`、`opacityPermille/intensityPermille=0..1000`、`radiusPx=0..200`、`rotationMilliDegrees=-180000..180000`；淡入/淡出之和不得超过元素时长。`styleOverrides` 和特效 `params` 的 `fieldCode/paramCode` 必须存在于对应模板版本 schema，值类型和范围由目录精确给出，未知项拒绝。可选字段省略，不发送 `null`。ID 和修订号按字符串处理；毫秒、归一坐标和普通计数为安全范围内整数。

### 13.3 保存请求

```http
PUT /api/studio/script-drafts/{draftId}/timeline
If-Match: "<strong-etag>"
Idempotency-Key: <url-safe-key>
Content-Type: application/json
```

正文精确包含：

```text
expectedTimelineRevision
elements
deletedElementIds
```

- `elements` 使用按类型严格联合的保存 BO；已有元素带 `elementId`，新元素带 `clientElementKey`，两者互斥。
- 请求不接收 timelineId、tenantId、workspaceId、ownerId、actor、版本 ID、内容摘要、底片 URL、模板快照或任务状态。
- `Idempotency-Key` 必填且不放入 JSON；一次新的显式保存意图生成新键，传输重试必须复用。幂等范围、摘要和回执字段严格按第 12.5 节执行。
- `main_video` 不能出现在可编辑写请求中；`voice` 只允许窄化保存项 `{elementId, volumePercent, muted}`，禁止时间、素材、显隐和口型字段；字幕请求只允许元素 ID、字幕轨显隐和样式覆盖，不能提交文本或时间。
- 服务端重新加载当前底片、文案、模板和素材，规范化全部值后形成不可变版本摘要。
- 首次成功 `data` 精确为 `{timeline: TimelineVO, elementIdMappings: Array<{clientElementKey:string,elementId:string}>, idempotentReplay:false, currentAdvanced:false}`，并返回新 ETag；映射只包含本次新建元素。幂等回放把 `idempotentReplay` 置为 `true`；若当前头后来已推进则 `currentAdvanced=true`，`timeline` 仍是原保存回执对应的不可变结果版本，客户端必须立即 GET 当前头而不能基于它继续保存。

保存 BO 使用以下判别联合；表中未列字段一律拒绝：

| `elementType` | 允许字段 |
| --- | --- |
| 通用新/旧身份 | 新元素仅 `clientElementKey`，旧元素仅 `elementId`；两者均另带 `elementType`。 |
| `voice` | `elementId, volumePercent, muted`。 |
| `subtitle` | `elementId, trackEnabled, style`；`style` 精确使用 `SubtitleStyleVO` 的可写字段。 |
| `overlay_image` | 身份、`startMs,endMs,renderOrder,visible,locked,assetId,displayMode,fit,corner?,marginXPermille,marginYPermille,scalePermille,opacityPermille,radiusPx`。 |
| `overlay_video` | 上述通用视觉字段加 `sourceInMs,sourceOutMs`；`displayMode` 固定 `picture_in_picture`，不接受 `muted/loopCount/finalLoopDurationMs`，这些由服务端派生且视频固定静音。 |
| `fancy_text` | 身份、`startMs,endMs,renderOrder,visible,locked,text,xPermille,yPermille,scalePermille,rotationMilliDegrees,opacityPermille,templateCode,templateVersion,styleOverrides`；`customized` 由服务端派生。 |
| `visual_effect` | 身份、`startMs,endMs,renderOrder,visible,locked,templateCode,templateVersion,target,intensityPermille,opacityPermille,blendMode,fadeInMs,fadeOutMs,params`。 |
| `bgm` | 身份、`startMs,endMs,visible,locked,assetId,sourceInMs,sourceOutMs,loop,volumePercent,muted,fadeInMs,fadeOutMs,duckingEnabled,duckingPercent`。 |
| `sfx` | 身份、`startMs,endMs,visible,locked,assetId,sourceInMs,sourceOutMs,loop,volumePercent,muted,pan,fadeInMs,fadeOutMs`。 |

`deletedElementIds` 只包含服务端已存在且允许删除的十进制字符串 ID；未保存新元素直接从本地工作副本移除，不进入该数组。所有对象拒绝未知属性，可选字段省略且禁止 `null`。

### 13.4 重合成请求

```http
POST /api/studio/script-drafts/{draftId}/timeline/recompositions
Idempotency-Key: <key>
Content-Type: application/json

{
  "timelineVersionId": "90001",
  "expectedTimelineRevision": "7",
  "outputPreset": "vertical_1080p_30fps"
}
```

- 只允许当前已保存版本；旧版本不能冒充当前版本覆盖作品。
- 同一 owner、任务类型、幂等键和相同规范摘要返回已有任务；同键不同摘要拒绝。
- 返回统一任务创建 VO，至少包含 `taskId/status/progress`。
- 同一时间轴版本最多一个活跃重合成根任务；不同已保存版本可以各自运行，结果明确标注版本，不能互相覆盖。

### 13.5 AI 请求

两个 AI 创建请求都携带 `Idempotency-Key`，共同字段为：

```text
timelineVersionId
expectedTimelineRevision
optionalInstruction
expectedTariffVersion
```

- `optionalInstruction` 最多 500 个 Unicode code point，只作为纯文本，不支持 HTML。
- taskType、operationType、resourceType、actor、归属和 quota unit 均由端点与当前会话派生，客户端不能提交或覆盖；创建严格使用第 9.4 节已注册的 `createChargeableTask` 映射。
- 生图提示词请求另含严格 `sourceRange={scriptVersionId,startCodePoint,endCodePoint}`；花字请求另含第 9.3 节 `anchor` 判别联合。服务端从当前草稿的文案/字幕/播放头上下文派生原文，不接受客户端自报正文替代。
- 创建事务在统一收费根任务后追加唯一 `av_timeline_task_input`，冻结 `inputSchemaVersion/timelineVersionId/timelineDigest/scriptVersionId/sourceRange|anchor/sourceTextHash/optionalInstructionHash/modelRouteSnapshot` 后再入队；执行所需原文存入受控私有输入载体，不进入日志、异常、任务列表或 result ref。
- 成功任务的 `GET /api/tasks/{id}` 结果引用 `timeline_ai_result`，页面再通过归属受控的结果接口读取。
- 结果接口只允许任务发起者或当前工作区有权查看者，且 `resultId` 必须同时属于路径中的 draft/timeline。
- 结果 VO 精确包含 `resultId/taskId/resultType/timelineVersionId/scriptVersionId/sourceRange/candidates/createdAt`；提示词候选和花字候选分别使用严格联合，花字候选必须带稳定 `candidateId`。

### 13.6 稳定错误码

| code | 标识 | 前端行为 |
| --- | --- | --- |
| `46601` | `TIMELINE_NOT_READY` | 显示底片/声音/文案尚未准备好，并引导回对应步骤。 |
| `46602` | `TIMELINE_REVISION_CONFLICT` | 保留本地修改，提示重新加载，禁止静默覆盖。 |
| `46603` | `TIMELINE_ELEMENT_INVALID` | 映射到元素和字段，禁用重合成。 |
| `46604` | `TIMELINE_MEDIA_INVALID` | 显示素材处理中、缺失、越权或解码失败；不得泄露他人素材存在性。 |
| `46605` | `TIMELINE_TEMPLATE_UNAVAILABLE` | 保留历史快照；新建/切换要求选择可用模板。 |
| `46606` | `TIMELINE_RECOMPOSE_CONFLICT` | 刷新当前版本或活跃任务，不在本地伪造任务。 |
| `46607` | `TIMELINE_SUBTITLE_INTEGRITY_VIOLATION` | 阻止保存/合成并要求重新从确认文案构建字幕。 |
| `46608` | `TIMELINE_READ_ONLY` | 显示只读原因，不尝试绕过步骤守卫。 |
| `46609` | `TIMELINE_AI_SOURCE_RANGE_INVALID` | 保留选择并要求重新选择当前文案范围。 |
| `46610` | `TIMELINE_IDEMPOTENCY_CONFLICT` | 保留输入，由用户确认新意图后生成新幂等键。 |

稳定错误 `data` 使用下列精确结构，未列字段拒绝：

| code | `data` |
| --- | --- |
| `46601` | `{blockingStepCode:string, missingResourceType:'confirmed_script'|'voice'|'base_video'|'timeline'}` |
| `46602` | `{currentTimelineRevision:string, currentEtag:string, updatedAt:string}` |
| `46603` / `46607` | `{errors:Array<{elementRef:{elementId:string}|{clientElementKey:string}, fieldPath:string, reasonCode:string}>}`；数组按请求元素顺序和字段顺序稳定排序。 |
| `46604` | `{elementRef:{elementId:string}|{clientElementKey:string}, fieldPath:'assetId', reasonCode:'processing'|'not_found_or_forbidden'|'decode_failed'|'type_mismatch'}`；`not_found_or_forbidden` 不区分越权与不存在。 |
| `46605` | `{templateType:'fancy_text'|'visual_effect'|'font', templateCode:string, requestedVersion:string, catalogVersion:string}` |
| `46606` | `{currentTimelineVersionId:string, activeTask?:{taskId:string,status:'pending'|'queued'|'running'}}`；无可查看活跃任务时省略。 |
| `46608` | `{readOnlyReasonCode:'upstream_changed'|'permission_changed'}`；运行中的重合成只冻结其输入，不会把当前时间轴置为只读。 |
| `46609` | `{scriptVersionId:string, maxCodePoint:number, reasonCode:'empty'|'out_of_range'|'stale_version'|'anchor_not_visible'}` |
| `46610` | `{idempotencyScope:'timeline_save'|'recomposition'|'image_prompt'|'fancy_text_suggestion'}` |

`fieldPath` 使用保存 BO 的 lowerCamelCase JSON Pointer 风格，例如 `/elements/3/sourceOutMs`；前端 adapter 只把能够匹配当前 `elementRef` 和字段白名单的错误映射到表单，其余按契约异常显示。

401 清理当前创作会话并只跳转一次；403 显示无权限，不跳登录；页面禁止根据中文 `msg` 分支。

## 14. 后端落点与 RuoYi 分层

- `ai-video-user`：在 `org.dromara.aivideo.user.studio.timeline` 下放 BO、VO 和 Controller；Controller 只接参、校验、`app` 权限、当前 actor 和 `R<T>` 包装。
- `ai-video-core/timeline/domain`：贫血 Entity。
- `ai-video-core/timeline/dto`：用户端与任务执行器之间稳定的 `*DTO` 数据契约。
- `ai-video-core/timeline/mapper`：`BaseMapperPlus`、带完整归属/旧修订/旧状态/lease 的条件更新，以及 receipt、task input、asset ref、active slot 和 output attempt 的必要查询。
- `ITimelineService` / `TimelineServiceImpl`：初始化、归属、步骤守卫、版本保存、模板校验、素材校验、保存回执和任务冻结编排；不得把这些规则拆成 Controller 私有逻辑。
- 统一 `IAiTaskService` 与执行分发器：创建、冻结 `av_timeline_task_input`、写可查询素材保活引用/活跃槽并在同一外层事务入队；复用命中立即返回，不重复冻结、占槽或入队。
- `ai-video-infra/timeline/provider`：直接 FFmpeg/ffprobe 合成 Provider 与文本模型 Provider 映射；不得形成第二套业务 Service。
- 外部进程和模型调用不持有数据库长事务；命令使用参数数组，任何字幕、花字、文件名和用户文本不得拼接进 shell。
- 新表、新索引和 CHECK 使用新的前向迁移，不修改已执行历史迁移；先读 generator 模板及 `asset/task/digitalhuman` 相似模块。
- 创作端 Controller/Service/Mapper 只使用 `AppLoginHelper` 派生的 typed actor 与当前 workspace；默认 `BaseEntity` 字段仅为框架兼容，任何归属查询和审计判断都不能退化到运营端 `LoginHelper` 或裸 `createBy/updateBy`。

## 15. 重合成任务

### 15.1 任务规则

- 任务类型固定为 `timeline_recompose`，资源类型固定为 `timeline`。
- 任务组键固定为 `timeline:{timelineId}:{timelineVersionId}`。
- 重合成使用 `createFreeTask`：当前范围只复用底片、人声和本机/服务器合成能力，不冻结或扣减 AI 文本额度；未来收费必须新规格和价格契约，不能静默改变。
- 创建事务严格执行 `createFreeTask → insert av_timeline_task_input → insert av_timeline_task_asset_ref → acquire av_timeline_recompose_slot → enqueue`；五步位于同一外层事务。不同幂等键并发争抢同一版本时由 slot 唯一键最终仲裁，失败事务不得留下新根任务；回读有权查看的活跃任务后返回 `46606`。
- `av_timeline_task_input` 冻结 `inputSchemaVersion`、时间轴版本/修订/内容摘要、底片与人声素材 ID/版本/SHA-256、所有元素规范 payload、模板版本/快照、字体 manifest、`subtitle-normalize-1`、`subtitle-layout-1`、layout/font hash 和输出预设；只保存稳定私有 ID/版本/摘要，不保存短期访问 URL。
- 每个冻结素材同时写 `av_timeline_task_asset_ref(rootTaskId,assetId,assetVersion,assetSha256,roleCode)`；素材删除/清理器必须把当前 timeline/element 直接引用和活跃/留存期内任务引用一起计入，禁止因为引用只存在于 JSON 而删源文件。
- 用户保存新版本不改变运行中任务的冻结输入；旧版本结果只能标记为历史结果。
- 任务成功产生新的私有 MP4 资产并关联来源时间轴版本；不得覆盖旧成功文件。

### 15.2 执行阶段

非终态进度扩展使用稳定 `stageCode='waiting_dispatch'|'preparing_assets'|'composing_media'|'validating_output'`；中文仅是前端本地化标签，不作为分支条件。用户可见阶段为：

1. 等待调度。
2. 准备素材。
3. 合成画面与混音。
4. 校验输出。
5. 已完成/失败/已取消；此项直接来自统一根任务规范终态 `success|failed|cancelled`，不再创建第二套时间轴任务状态。

进度扩展严格为 `{stageCode,progressPercent?}`，百分比只在执行器有可信进度时返回 0～100 整数；未知进度省略百分比并使用不确定进度，不伪造线性值。状态、失败原因和输出以后端统一任务记录为唯一事实源。

### 15.3 输出校验

worker 领取当前 lease 后，先通过 `IAssetService` 为当前 `(rootTaskId,executionId,leaseRevision,role='timeline_output')` 创建独立的 `processing` 私有资产和 `av_timeline_output_attempt`；每个 lease generation 都有自己的 asset ID 与不可变对象 key，不更新或复用上一代预留。对象 key 只能由服务端按 tenant/root/execution/lease generation 派生，禁止包含文件名或用户文本。渲染、ffprobe 和上传在数据库事务外执行，过期 worker 的对象不能覆盖新 lease 选择的对象。

成功前必须对实际待提交对象执行 ffprobe：

- 容器是 MP4，视频为 H.264，音频为 AAC。
- 分辨率 1080×1920，帧率 30fps。
- 音视频都存在，时长与 `canonicalDurationMs` 差异不超过 0.25 秒。
- 视频可解码首帧、中间帧和末帧，音轨非空。
- 输出文件大小、SHA-256 和私有对象引用持久化成功。
- 字幕完整性摘要和冻结版本摘要仍匹配。

校验和上传成功后开启最终短事务，以完整 `rootTaskId/executionId/leaseOwner/leaseRevision/oldStatus` 条件原子完成“当前 output attempt 与其 `processing` asset → `ready`”、写 `TaskResultReferenceDTO(resultRefType='asset',resultRefId=assetId)`、根/执行任务 `markSuccess` 和对应 slot 的 `active_slot → NULL`；受影响行数不是预期值就不得声称成功。除获胜 attempt 外，同一 root 下所有旧 lease attempt 均保留各自 asset ID/object key 并进入 `delete_pending`。最终事务失败、lease 丢失、失败或取消也把本 attempt 及其资产登记为 `delete_pending`，不返回访问地址；有界补偿器按每行稳定对象 key 幂等删除并推进 `deleted|delete_failed`，不得把对象存储成功误判为任务成功。

### 15.4 取消、恢复和通知

- 排队和运行任务按统一任务规则允许取消；执行器收到取消后终止子进程并清理临时文件。
- success/failed/cancelled 的任务终态事务都以 root task 与旧状态为条件释放同一 slot；事务异常时由恢复扫描器对照统一任务终态补偿释放，不能靠进程内 `finally` 作为唯一释放手段。
- lease 过期只恢复原执行和原免费任务身份，不创建新根任务；真实外部调用前才新增 attempt。
- 终态不可回退；过期 worker 不能写输出引用、把资产置为 ready 或覆盖新 lease，迟到的临时/已上传对象一律进入上述 `delete_pending` 补偿。
- 成功和失败产生任务中心通知并链接第 7 步；取消只更新任务中心，不额外发送成功类通知。

## 16. 安全、权限与内容访问

- 所有 Controller 显式使用 `@SaCheckPermission(..., type="app")`，并通过 `AppLoginHelper` 获取当前创作 actor。
- 当前范围只支持个人归属：`owner_type='personal'` 且 `owner_id=currentAppUserId`。组织工作区共享编辑需要另行登记 grant/scope 规则，不得把 workspace 成员身份直接当成 timeline 写权限。
- 权限码只控制动作资格，Service/Mapper 仍按 tenant、workspace、owner_type、owner_id、draft、timeline、asset 和 task 组合条件校验归属；跨归属与不存在统一返回防枚举结果。
- 请求不能提交 owner、tenant、workspace、actor 或公开 URL；跨账号 ID、跨工作区素材和直接接口访问统一 fail-closed。
- 素材列表、详情、预览、替换、保存、AI 结果、任务和输出下载都必须重新校验归属，不能依赖前端曾显示过元素。
- `av_timeline.base_video_asset_id/voice_asset_id`、当前 `av_timeline_element.asset_id` 和 `av_timeline_task_asset_ref.asset_id` 是素材服务可查询的正式引用。重命名不影响引用；删除/清理前必须查询三类引用和任务留存期，存在有效引用时拒绝或延后，不能只扫描 payload JSON。
- 预览地址短期有效且 `Cache-Control: no-store`；失效后前端最多自动重取一次，不把地址写入版本。
- 花字和字幕始终按纯文本处理，渲染前转义；不接受 HTML、SVG、ASS 原文、滤镜表达式或任意字体路径。
- 模板和字体只来自服务端版本化白名单；字体文件必须随部署登记并具有可分发许可记录。
- 日志、`@Log`、任务 attempt、异常、VO 和测试快照不得包含完整文案、短期 URL、Authorization、供应商凭据、内部路径或 FFmpeg 完整敏感命令。
- 临时媒体位于配置的私有工作目录，任务结束或超时后按 TTL 清理；路径解析必须拒绝绝对路径、父目录跳转和符号链接逃逸。

## 17. 前端组件和状态边界

建议拆分：

| 组件/Hook | 职责 |
| --- | --- |
| `TimelineStep` | 步骤守卫、查询、保存/重合成入口和整体状态。 |
| `TimelinePreviewCanvas` | 真实视频、播放头同步、画面元素叠加、反向选择和花字拖动。 |
| `TimelineElementInspector` | 按类型渲染受控表单和状态，不直接请求接口。 |
| `TimelineAddRibbon` | 固定工具带和权限可见性。 |
| `TimelineEditor` | 标尺、三段轨道、水平滚动、缩放和播放头。 |
| `TimelineClip` | 选择、移动、拉伸、错误和循环分界线。 |
| `TimelineAssetPicker` | 私有素材查询、上传会话和 ready 校验。 |
| `TimelineAiAssistant` | 文案范围、价格确认、AI 任务和结果采用。 |
| `useTimelineEditor` | 唯一工作副本、命令栈、选择、派生子轨和字段校验。 |
| `timelineService/adapter` | URL、RuoYi envelope、严格 wire schema、ETag 和错误归一化。 |

实现使用 Ant Design 6 基础组件和项目业务组件组合；具体组件 API、Token 和 semantic classNames 在编码时通过本地 Ant Design CLI 按项目精确版本查证。页面不得直接拼 URL、Authorization、错误码或 `R<T>` 解包。

服务端快照与可编辑工作副本的复制是显式编辑会话，不得再额外镜像为多份互相可写的组件 state。所有属性输入受控；时间轴和画布只提交领域事件给 `useTimelineEditor`。

## 18. 验收与测试

### 18.1 前端正向场景

1. 页面按“预览/信息 → 添加元素 → 时间轴”顺序显示，时间轴不被右侧信息区压缩。
2. 真实视频可播放、暂停和跳转；播放头、字幕、画中画视频和音频同步。
3. 默认七轨顺序正确，新增重叠元素后生成子轨且 `V1` 仍固定居中。
4. 图片、画中画、花字、特效、BGM、SFX 可移动和拉伸，边界与吸附正确。
5. 画中画视频超过源时长时预览正确循环，末轮截断，时间轴显示循环分界。
6. 点击时间轴和点击画布可双向选择；播放头离开范围后选择仍保留。
7. 花字在画布拖动后 `X/Y` 实时更新，完整包围盒不能拖出安全区；撤销只需一次。
8. 字幕在极长文本、72px 字号、背景和描边组合下仍单行、不省略、不缺字、不越界。
9. “应用全部字幕”只改变样式，撤销一次恢复全部，不改变文本和时间。
10. 花字五个内置模板可预览、应用、手工修改和恢复；下架模板历史元素仍可预览。
11. AI 提示词和花字建议显示价格确认、任务状态与结果，结果不会自动插入或自动生图。
12. 保存返回新修订/ETag；重合成使用刚保存版本并进入任务中心。

### 18.2 前端失败与反向场景

- 首次加载失败、视频地址失效、素材处理中/失败、模板目录失败分别可恢复，不把错误伪装成空数据。
- 401 只跳登录一次，403 留在页面显示无权限；无编辑权限时预览可读、表单只读。
- 快速切换元素不会出现上一个元素的 `defaultValue` 残留。
- 指针取消、窗口失焦和 `Esc` 不留下半次拖动；触摸滚动不会误移动片段。
- 非法时间、越界坐标、缺失素材、零时长视频、未知模板参数和字幕完整性失败不能保存/重合成。
- 两个浏览器同时保存时只有匹配修订的一方成功；另一方保留本地修改并显示冲突。
- 保存未知网络结果不重复创建元素；相同任务幂等键不重复创建任务，同键不同输入稳定冲突。
- AI 任务失败/取消不插入元素、不中断已有编辑，并按同一用量操作释放额度。
- 当前版本存在未保存修改时，不允许直接用旧版本偷偷重合成。

### 18.3 后端、数据库和权限

- 首次初始化并发只能创建一个当前时间轴和一个 `revision=1` 版本。
- 迁移契约精确断言 baseline/version/result/input/receipt/slot/output-attempt 唯一键、元素索引/CHECK、逻辑删除和 append-only 策略；并发靠真实 MySQL 唯一键最终仲裁，不只验证 mock 预查询。
- 保存覆盖字段白名单、完整元素精确分区、未知字段拒绝、Long ID 字符串化、payload 版本和所有范围校验。
- 保存回执覆盖同键同摘要原样回放、同键异摘要 `46610`、响应丢失后元素映射恢复、同一 timeline 继续推进以及上游重生成切换 `current_timeline_id`；任何路径都按 draft+key 找回原 timeline 回执，不重复插元素或版本。
- 条件更新覆盖 tenant/workspace/owner/draft/timeline/旧修订；跨用户、跨工作区和已删除资源不能读取或写入。
- typed actor 字段覆盖创建与更新，当前范围严格写 `owner_type=personal/currentAppUserId`；默认创作角色具备 studio edit 与三项 asset 权限，错误角色、运营端 Token、伪造/过期 Token 和直接接口调用被拒绝。
- 素材伪造 MIME、文件头不符、解码失败、超限、非 ready、跨归属、短期 URL 注入全部失败。
- 素材删除/清理覆盖 timeline 头、当前元素和任务资产引用；任一有效引用存在都不会删除源对象，任务输入不依赖短期 URL。
- 重合成和 AI 创建覆盖相同/冲突幂等、不同键并发争抢 slot、唯一冻结 input/result、任务终态保护、lease 恢复、过期 worker、重复回调和重复结算。
- 两类 AI 任务的 task/operation/handler/resultRef/任务中心 target/tariff 注册缺一即阻断；当前 lease 下结果插入、markSuccess 和 settle 原子，重复投递回读唯一结果。
- 保存事务回滚不产生版本孤儿；任务创建失败不留下未入队根任务或孤立冻结输入。
- AI 结果与 task、timeline version、script version、source range 和 owner 全部交叉校验。

### 18.4 合成与媒体

- 使用 5～20 秒小型真实视频、图片和音频夹具验证图片、视频循环、字幕、五类花字样式、画面特效、BGM ducking 和 SFX 混音。
- 逐项验证输出 1080×1920、30fps、H.264/AAC、双流存在、时长误差、首中末帧解码和 SHA-256。
- 字幕测试以规范文案串联摘要验证不缺字，不只做截图肉眼判断。
- PIP 视频循环测试覆盖恰好一轮、整数多轮、末轮不足、素材入点和零时长拒绝。
- FFmpeg 失败、磁盘不足、进程超时、取消和重启恢复均不能产生成功输出或终态回退。
- 覆盖对象上传成功但最终数据库事务失败、连续多个 lease generation、旧 worker 迟到、取消与补偿器重试：每代 attempt 的 asset/object key 都可独立追踪，只有当前 lease 一代可成为唯一 ready 输出，其余进入 `delete_pending → deleted|delete_failed`，不会丢失旧对象引用或遗留可访问的孤儿成功文件。

### 18.5 无障碍与视觉

- 播放、元素添加、轨道选择、拖动替代输入、模板、颜色、保存和重合成均可键盘操作且焦点可见。
- 图标按钮有可访问名称，锁定/错误/选中不能只依赖颜色；状态变化使用可读播报。
- `prefers-reduced-motion` 下花字模板预览关闭非必要循环动画，最终合成效果不受影响。
- 1280×720、1440×900、1920×1080 和 1024px 响应式边界完成人工视觉验收。

### 18.6 验证命令边界

- 前端运行受影响包的类型检查、格式/静态检查、Vitest 和生产构建，实际执行测试数必须大于零。
- 后端运行 timeline、asset、task、quota 和用户端 Web/Service/Mapper 相关 Maven 测试及打包。
- 集成测试只连接开发机本机安装的 MySQL 8 专用 `ai_video_test` 和隔离 Redis 7 逻辑库/前缀；禁止 Docker、Testcontainers、WSL 或其他容器/虚拟化替代。
- 文档/计划变更运行 `scripts/validate-development-standards.ps1`；交付前运行 `git diff --check`。
- 真实付费 AI 联调使用显式门禁和短文本，不在普通回归中调用；日志和测试产物执行凭据片段扫描。

## 19. 实施顺序与公共契约门禁

进入代码前按下列顺序执行：

1. 将本规格的类型、端点、错误码、权限、表和任务规则同步到：
   - `docs/API_CONTRACT.md`
   - `docs/DOMAIN_MODEL.md`
   - `docs/ASYNC_TASKS.md`
   - `docs/ARCHITECTURE.md`（仅在模块依赖或任务执行落点变化时）
   - 七步草稿/步骤守卫及第 7 步预览下载相关规格
2. 评审素材 purpose、默认角色权限、额度价格项、timeline 数据表和统一任务 result ref。
3. 先实现数据库迁移、核心 DTO/Service、用户端 BO/VO/Controller 和严格 adapter mock。
4. 前端按 mock 完成布局、编辑 reducer、画布/时间轴联动和失败态；不得等待真实合成才补页面状态。
5. 接入真实保存、模板目录、素材上传/访问和权限。
6. 接入 AI 文本任务、额度和结果，再接入免费重合成任务与 FFmpeg Provider。
7. 完成联调、真实短媒体合成、一次独立规格/契约审查和一次对应专项审查；修复后只复核差异。

任何实现计划若跳过公共契约、归属/权限、任务中心、额度或输出 ffprobe 门禁，均不符合本规格。

## 20. 冻结决策与变更规则

以下决策已经冻结，不作为实施阶段开放问题：

- 采用方案 A；上部预览左、信息右，添加元素区在上部与时间轴之间。
- 默认七轨，`V1` 固定居中；动态重叠使用子轨。
- 花字可直接在画面拖动，坐标双向同步并限制安全区。
- 画中画视频超时自动循环，默认静音；BGM 循环独立且默认开启；SFX 循环默认关闭。
- 字幕正文/时间来自上游，不在第 6 步任意改写；启用字幕时必须单行、无普通标点、不缺字、不越界。
- 初始内置 5 个花字模板和 4 个画面特效模板。
- AI 会调用文本模型生成提示词和花字建议，但不调用图片生成模型，也不自动插入元素。
- 编辑使用显式保存、revision + ETag；重合成只使用已保存不可变版本。
- 重合成进入统一任务中心且当前为免费任务；AI 文本任务进入任务中心并按现行文本额度结算。
- 生产后端继续采用 RuoYi 贫血 Entity + Service 编排，不引入平行业务层。

未来如需修改上述任一项，必须先更新本规格和相应公共契约，再进入实现；不得由前端、合成器或 Provider 各自形成不一致的隐式规则。

## 21. 规格自检

- 已覆盖用户提出的真实预览、图片/画中画、字幕、花字、AI 提示词、画面拖拽、轨道、元素信息和重合成。
- 已区分图片、画中画、画面特效和特效声音，避免“特效”同义混用。
- 已冻结主视频中心锚定、同类重叠、画中画循环、选择联动、撤销粒度和字幕完整性。
- 已覆盖加载、空、失败、权限、只读、冲突、素材、模板和任务状态。
- 已登记共享 API、数据库、素材、权限、额度、统一任务和输出校验影响。
- 已给出成功、失败、越权、并发、幂等、恢复和媒体反向验收场景。
- 没有批准安全/框架例外，没有使用未登记的平行业务分层。
