# 数字人 A2 六步主流程与 GLM-5.2 引导式需求设计规格

> **局部已被替代。** 与动态题型、逐题生成时机、答案修改后的分支失效、固定补充字段、候选数量和逐次计费有关的内容，以 `docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md` 为准；本文其余数字人步骤仍可用于历史追溯。

**状态：** 待用户审查
**日期：** 2026-07-14
**模块：** 图生数字人演示主流程
**实现载体：** `digital-human-studio` 独立演示应用

本规格获批并生成新实现计划后，将取代 `docs/superpowers/plans/2026-07-14-digital-human-demo-main-flow.md`。旧计划中的“我还没想好”、本地伪台词、刷新后数据可直接消失等规则不得继续执行。

## 1. 目标

在现有可运行的六步数字人演示链上，采用 v2 设计稿的蓝白工作台视觉，并将第一步改造成无需用户自行组织完整需求的 AI 引导流程。用户通过选择或输入行业、视频用途，再完成 GLM-5.2 动态生成的少量问题，即可获得可编辑台词；随后上传人物照片和参考声音，配置语义画中画、字幕和花字，真实调用 IndexTTS2、LTX2.3 和 FFmpeg 生成可播放、可下载的 MP4。

核心体验目标：

- 用户不必先想好完整文案或专业参数。
- 每个页面只完成一个明确任务，并始终只有一个主操作。
- 所有 AI 结果都可见、可修改，不使用无法解释的自动跳过。
- GLM-5.2、IndexTTS2、LTX2.3 或合成流程发生异常时，明确阻断并允许重试。
- 保持当前真实模型演示链，不伪造任务进度、视频或下载结果。

## 2. 当前演示范围与公共契约边界

本规格仅覆盖用户明确要求的本地、单用户、无持久化演示流程。以下内容本轮不实现：

- 项目、任务中心、资产中心、通知中心。
- 数据库保存、草稿持久化、历史列表。
- 账号、角色、权限、额度、计费和通知。
- VoxCPM2、模型切换器、移动端和平板端响应式。

这是一项隔离的演示例外，不修改生产模块的公共契约。`docs/API_CONTRACT.md` 和 `docs/ASYNC_TASKS.md` 对生产系统要求的 RuoYi 统一响应、持久化任务、账号归属、权限、额度和任务中心仍然有效。若后续将本演示并入正式 AI 视频工作台，必须另行生成生产化规格，恢复上述治理能力，不得把本规格当作生产豁免。

演示隔离边界：

- 代码和路由只存在于 `digital-human-studio`，不注册到生产网关，不修改 `ai-video-api` 或正式用户端 Web 包。
- API 默认只监听 `127.0.0.1`，使用单进程、单 worker；进程内上传、问卷和任务状态不能跨 worker 共享。
- Web 通过本地 Vite 代理访问 API，不开放宽泛 CORS；API 只接受配置的本地 `Host` / `Origin`。
- 如果将页面或 API 暴露到局域网、公网或多人环境，必须先恢复认证、文件访问授权、账号归属、并发配额和生产任务治理，本规格不能直接沿用。

## 3. 已确认的视觉方向

- 使用 A2：白色桌面工作台、62px 窄图标侧栏、紧凑顶部栏、蓝色主色 `#165DFF`。
- 顶部固定展示六步步骤条；当前步骤使用实心蓝色编号，已完成步骤使用完成标识。
- 内容背景使用浅灰，主体卡片使用白底、细边框、8px 左右圆角和克制阴影。
- 操作步骤不设置常驻右侧摘要；只有第六步结果页保留必要的成片信息和下载区。
- 本轮只保证 1280px 及以上桌面布局，不设计断点和移动端重排规则。
- 页面最小内容宽度按 1200px 处理；1280×720 下允许页面纵向滚动。小于目标宽度的行为不作为本轮验收项。
- 不增加任务、资产、通知、项目等假入口；窄侧栏只承载当前演示必要入口、帮助和状态图标。

建议使用 Ant Design `Layout`、`Steps`、`Card`、`Form`、`Input`、`Upload`、`Alert`、`Progress`、`Result` 等基础组件组合生产型工作台。具体组件 API 和 Token 在实现阶段通过 Ant Design 官方资料确认，不依赖记忆编写。

交互语义要求：行业/用途和单选题使用语义化单选控件，多选题使用语义化复选控件，不能只给可点击 `Card`；所有图标侧栏项、动态问题、上传、开关、错误和播放器必须具有可访问名称、键盘焦点和状态说明，不能只依赖颜色表达选择或进度。

## 4. 六步用户流程

### 4.1 第一步：说需求

第一步内部包含三个短子步骤，但顶部仍只计为六步流程中的第 1 步。

#### 1A. 选择或输入行业

- 展示常见行业：电商零售、本地生活、教育培训、企业服务、知识 IP、房产家居、招聘职场等。
- 最后一项为“自己输入行业”，选中后展开单行输入框。
- 不提供“我不知道，交给 AI 判断”。
- 预设行业和自定义行业使用同一个稳定字段 `industry` 传给后端。
- 行业不能为空，未选择或未输入时禁止继续。

#### 1B. 选择或输入视频用途

- 根据行业展示常见用途，例如产品口播、短视频种草、活动促销、知识讲解、品牌介绍。
- 最后一项为“自己输入视频用途”，选中后展开单行输入框。
- 不提供 AI 猜测用途的入口。
- 用途不能为空，未选择或未输入时禁止生成专属问题。

#### 1C. GLM-5.2 专属补充表单

- 行业和用途提交成功后，服务端调用 GLM-5.2 生成 3–5 个必要问题。
- 支持的题型限定为 `singleChoice`、`multiChoice`、`shortText`，前端只渲染白名单题型，不允许模型返回任意 HTML 或组件。
- 每个问题包含清晰问题、说明、推荐选项和一个“补充说明”输入位置。
- 单选或多选题允许用户在点选后继续输入自己的补充；短文本题允许直接输入。
- 表单底部增加独立多行输入框“还有什么想特别说明？”，与每题补充同时保留。
- 全局补充示例包括“必须提到价格”“不要使用夸张词”“结尾提醒到店体验”，但不预填实际内容。
- 非必填题未作答时，可以使用 GLM 返回的推荐值；介绍对象等被标记为必填的问题必须完成。
- 推荐值必须在页面中以已选状态展示并随答案显式提交，服务端不得在用户看不见的情况下静默补值。短文本题不设置隐藏推荐文本。
- 用户更改行业或用途时，先确认是否重新生成问题，避免无提示清空答案。
- 点击“生成台词草稿”后调用 GLM-5.2；成功取得有效台词才进入第二步。

### 4.2 第二步：确认台词

- 展示 GLM-5.2 生成的完整台词，并允许用户直接编辑任意文字。
- 实时展示字数和预计口播时长；预计值只用于提示，不作为最终合成时间。
- 提供一键优化操作：更短一点、更口语化、突出卖点、换个开头、加强行动引导。
- 允许用户输入自由修改要求，再次调用 GLM-5.2。
- 每次优化前保留上一个有效台词版本。优化调用失败时保留当前台词，显示文本大模型异常并禁止继续；用户必须成功重试，或明确点击“放弃本次优化并恢复上一版”清除失败状态后才能继续。
- 点击“确认台词”后进入第三步。
- 返回第一步修改行业、用途或问卷答案后，旧台词失效，必须重新生成。

### 4.3 第三步：上传人物和声音

- 页面并排展示“人物照片”和“参考声音”两个必填上传卡片。
- 人物照片支持 JPG、PNG；显示上传进度、预览、更换和校验错误，并用示例提示正脸、单人、光线清晰。
- 参考声音支持 WAV、MP3；建议 5–20 秒、无背景音乐、无明显噪声；上传后允许试听和更换。
- 明确说明参考声音仅用于克隆音色，最终朗读第二步确认的台词。
- 固定显示数字人模型 LTX2.3、声音模型 IndexTTS2，不提供模型选择。
- 两个文件都通过前后端校验后，才允许进入第四步。
- 上传成功后前端保存服务端返回的临时 `uploadId`，后续任务只引用 `uploadId`，不重复传输浏览器 `File`。
- 替换文件不影响第一步答案和已确认台词。
- 上传阶段不提前调用 IndexTTS2 或 LTX2.3；真实生成统一在第五步执行。

### 4.4 第四步：智能画面包装

进入页面时，服务端调用 GLM-5.2 分析确认后的台词。相同台词 revision 只生成一次并复用结果，不因返回页面而重复计费：

- 在台词中标出最适合出现画中画的语义片段。
- 返回 1 个主要推荐位置，最多再返回 2 个备选位置。
- 每个建议包含稳定 `recommendationId`、对应句子 ID、字符区间、推荐理由、适合上传的素材类型和预计时间比例。
- 用户选择一个建议并上传一张真实画中画图片；画中画默认开启，但允许关闭。
- 页面展示预计时间。IndexTTS2 生成真实语音后，以 ffprobe 得到的音频流时长 `audioDuration` 作为唯一时间轴。对完整台词按 Unicode code point 遍历，空白权重为 0、其他字符权重为 1；全局偏移处时间为 `audioDuration × 偏移前累计权重 / 总权重`。结果限制在 `[0, audioDuration]`，画中画不足 1 秒时围绕中心扩展到 1 秒。GLM 返回的秒数只能作为提示，不能直接进入 FFmpeg。

字幕设置：

- 可开启或关闭，默认开启。
- 提供三个真正参与合成的预设：`clean`（简洁字幕）、`keywordHighlight`（关键词高亮）、`sentencePop`（逐句弹出）。
- 默认预设为 `clean`。
- `clean` 使用底部安全区白字描边并按句显示；`keywordHighlight` 必须显式提交 `keywordSourceRecommendationId`，从该包装建议中取得、并再次验证确实存在于句子中的最多 3 个关键词；它不依赖画中画是否开启，也不隐式跟随 PIP 主/备选切换。`sentencePop` 按句使用短淡入/缩放效果。三者使用同一真实音频时间轴。

花字设置：

- GLM-5.2 返回 1–3 个带句子锚点的花字建议，用户可选择或直接修改文字，最终文本最长 16 个中文字符或 32 个 ASCII 字符。
- 可开启或关闭，默认开启。
- 固定样式为 `goldBold`、`blueLabel`、`whiteShadow`，默认 `goldBold`；花字按建议锚点换算到真实时间轴，通常展示 1–3 秒且不超过对应句子区间；若完整句子不足 1 秒，则使用完整句子区间，允许短于 1 秒。

开关提交规则：

- 画中画开启时，`recommendationId` 和画中画 `uploadId` 必填；关闭时两者不提交。
- 字幕开启时 `subtitlePreset` 必填；选择 `keywordHighlight` 时 `keywordSourceRecommendationId` 额外必填；关闭时只提交 `enabled: false`。
- 花字开启时文本、样式和锚点必填；关闭时只提交 `enabled: false`。
- 关闭再开启时，页面可保留用户上次配置，但后端只按当前 `enabled` 值执行。

GLM 语义分析失败时停留在第四步，保留已有上传和配置，禁止进入第五步。

### 4.5 第五步：生成视频

提交前展示最终确认摘要：台词、人物图、参考声音、画中画建议与素材、字幕预设、花字内容与样式。

点击“开始生成”后携带新的 `idempotencyKey` 创建一个演示任务。服务端原子保存不可变输入快照并锁定本次输入，执行顺序为：

1. IndexTTS2 克隆参考音色并朗读台词。
2. 远端适配器读取 IndexTTS2 实际音频时长，并据此选择 LTX2.3 帧数/场景时长；LTX2.3 使用人物照片和该音频创建数字人口播视频。
3. 对最终基础视频执行 ffprobe，以音频流时长为唯一时钟，校准画中画和花字区间。
4. FFmpeg 合成画中画、字幕、花字和最终音轨。
5. 输出 H.264/AAC MP4。

第二步显示的字数估算不得作为最终 LTX 或包装时间轴。远端能力契约必须返回或允许探测 IndexTTS2 实际音频时长；如果现有联合接口做不到，必须先扩展该接口。基础视频完成后同时 ffprobe 音频和视频时长：差异超过 0.25 秒时任务失败；差异不超过 0.25 秒时，以音频流时长为 canonical duration，视频较长则裁切，较短则使用末帧补齐，最终 MP4 严格对齐该时长。

前端每 2 秒轮询一次状态，用“克隆声音”“生成数字人”“校准画面”“合成字幕与花字”“验证输出”等人话阶段展示进度。创建成功后立即用 `history.replaceState` 把任务 ID 写入 `?job=`。百分比只展示后端返回且单调不下降的真实/可验证进度；某阶段没有可靠百分比时使用不确定进度，不伪造数字。

刷新恢复规则：

- `running` / `queued`：恢复到第五步只读进度页。
- `done`：恢复到第六步。
- `failed`：恢复到第五步失败页，可从服务端输入快照创建新任务。
- 未知、已过期或 API 重启后不存在的任务：显示“本次演示任务已失效”，返回第一步重新开始。
- 服务端任务详情返回不可变输入摘要、临时上传引用和配置；刷新后可以重试或进入对应步骤修改，但不能恢复浏览器原始 `File` 对象，页面改为展示服务端文件引用并允许替换。

任一阶段失败时：

- 停留在第五步并显示失败阶段。
- 保留所有输入和上传文件引用。
- “重新生成”创建新的任务执行，复用当前输入，无需重新上传。
- 不返回伪造视频或自动跳过失败模型。

### 4.6 第六步：预览与下载

- 主区域展示完整 9:16 真实 MP4 播放器，不使用裁切人物或字幕的 `cover` 行为。
- 成片必须包含已启用的克隆声音、数字人口型、画中画、字幕和花字。
- 右侧仅展示生成状态、时长、比例、格式，以及 GLM-5.2、IndexTTS2、LTX2.3 模型标识。
- “下载 MP4”下载的文件必须与播放器当前文件一致。
- “修改后重新生成”先让用户选择修改台词、人物声音或画面包装，再返回对应步骤。
- 修改台词后旧语义包装建议失效，必须重新调用 GLM-5.2；只替换人物或声音时可保留画面包装设置。
- 下载失败时保留播放器和生成结果，只提示重新下载，不重新生成视频。
- 播放和下载引用同一个不可变 `artifactId` / SHA-256；视频端点支持浏览器 Range 请求，下载使用受控请求捕获错误后再保存，不能依赖无法反馈失败的裸链接。

## 5. GLM-5.2 服务契约

### 5.1 配置

- Base URL：`https://open.bigmodel.cn/api/paas/v4`
- 对话端点：`/chat/completions`
- 模型代码：`glm-5.2`
- API Key：共享开发值直接写入并提交在两端 `application-dev.yml`，服务端环境变量 `BIGMODEL_API_KEY` 可选覆盖。
- 支持 `BIGMODEL_BASE_URL`、`BIGMODEL_MODEL` 和 `BIGMODEL_TIMEOUT_SECONDS`，默认超时 60 秒；Base URL 覆盖必须使用 HTTPS 且主机在明确 allowlist 内。
- API Key 不得出现在前端代码、浏览器网络请求、测试快照或日志中；共享开发值只在两端 `application-dev.yml` 维护。
- 缺少 API Key 时 API 启动前置检查失败；上游 401/403 返回不可重试的配置错误，不伪装成普通临时故障。
- 页面展示服务端实际采用并由上游响应确认的模型代码，不能仅硬编码标签。

结构化调用使用官方支持的 JSON 输出模式，并在提示中给出完整目标 schema；服务端仍必须对四类响应分别执行运行时 schema 校验。所有根对象都设置 `additionalProperties: false`，同时校验长度、数量、枚举、唯一性和跨字段约束。上游返回的 Markdown 包裹、缺字段、未知题型、无效数组或无法解析 JSON 都视为文本模型异常，不允许前端继续。

### 5.2 调用类型

1. `questionnaire`：行业 + 用途 -> 专属问题 schema。
2. `scriptGenerate`：行业 + 用途 + 问题答案 + 每题补充 + 全局补充 -> 台词草稿。
3. `scriptOptimize`：当前台词 + 固定优化类型或用户修改要求 -> 新台词。
4. `packagingAnalyze`：确认台词 -> 画中画建议 + 花字建议。

### 5.3 冻结 DTO

```ts
type QuestionnaireField = {
  questionId: string;
  label: string;
  helpText?: string;
  answerType: 'singleChoice' | 'multiChoice' | 'shortText';
  required: boolean;
  options: Array<{ optionId: string; label: string }>;
  recommendedOptionIds: string[];
  allowSupplement: true;
  supplementPlaceholder?: string;
};

type QuestionnaireResult = {
  questionnaireId: string;
  inputHash: string;
  clientRevision: number;
  fields: QuestionnaireField[];
};

type QuestionnaireAnswer = {
  questionId: string;
  selectedOptionIds: string[];
  textValue: string;
  supplementText: string;
};

type ScriptSegment = {
  sentenceId: string;
  text: string;
  startChar: number;
  endChar: number;
};

type ScriptResult = {
  scriptId: string;
  revision: number;
  sourceHash: string;
  clientRevision: number;
  scriptText: string;
  estimatedDurationSeconds: number;
  segments: ScriptSegment[];
};

type PipRecommendation = {
  recommendationId: string;
  sentenceId: string;
  startChar: number;
  endChar: number;
  reason: string;
  materialKind: 'product' | 'scene' | 'diagram' | 'proof' | 'other';
  materialHint: string;
  estimatedStartRatio: number;
  estimatedEndRatio: number;
  highlightKeywords: string[];
};

type FlowerSuggestion = {
  suggestionId: string;
  sentenceId: string;
  startChar: number;
  endChar: number;
  text: string;
  styleId: 'goldBold' | 'blueLabel' | 'whiteShadow';
};

type PackagingAdvice = {
  adviceId: string;
  scriptSourceHash: string;
  clientRevision: number;
  pipRecommendations: PipRecommendation[];
  flowerSuggestions: FlowerSuggestion[];
};
```

约束：

- `questionId` 在单次问卷内唯一。
- 问题数量为 3–5。
- 所有字段必须允许补充输入。
- 单选题选项为 2–6 个且最多一个推荐项；多选题选项为 2–8 个；推荐 ID 必须属于选项且不重复。
- `shortText` 的 `options` 和 `recommendedOptionIds` 为空数组，不使用隐藏推荐文本。
- 页面首次渲染时把 `recommendedOptionIds` 初始化为可见的已选答案，用户可取消或改选；提交时它们与普通选项一样进入 `selectedOptionIds`，不另设隐藏的“采用推荐值”标志。
- 标签、说明、选项、补充和台词都有明确长度上限；服务端拒绝超限响应，不能截断后假装成功。
- 所有 `startChar/endChar` 都是相对于完整 `scriptText` 的 Unicode code point 全局偏移，使用半开区间 `[startChar, endChar)`；不是 UTF-16 索引，也不是句内偏移。
- `ScriptSegment` 必须按上述坐标连续、有序、无越界；服务端从最终 `scriptText` 自己分句并验证，不能只相信模型。
- 包装建议的 `sentenceId` 和字符区间必须存在于对应脚本且完全落在该句区间；比例满足 `0 <= start < end <= 1`。
- 画中画建议数量为 1–3，花字建议数量为 1–3；关键词必须真实存在于锚定句子。
- 模型内容只作为数据渲染，禁止注入 HTML。

字段上限：行业和用途各 1–40 字符；问题标签 1–60、说明 0–120、选项文案 1–30；每题短答和补充各 0–200；全局补充 0–500；台词 1–120；自由优化要求 1–300；推荐理由 1–120、素材说明 1–80。所有长度按 Unicode code point 计算，前后端使用同一规则。

服务端在进程内保存已经校验的 `questionnaireId`、`scriptId/revision`、`adviceId` 及输入 hash。生成台词时按 `questionnaireId` 校验问题和答案；创建视频任务时按 `adviceId` 校验台词 hash，禁止把旧问卷答案或旧包装建议配到新台词。

### 5.4 错误规则

稳定错误语义：

- `TEXT_MODEL_UNAVAILABLE`：网络、限流、鉴权、上游服务失败。
- `TEXT_MODEL_TIMEOUT`：超过服务端设置的调用超时。
- `TEXT_MODEL_INVALID_RESPONSE`：无法解析或未通过 schema 校验。
- `TEXT_MODEL_CONFIGURATION_ERROR`：缺少密钥或上游 401/403，不可重试。

前端只根据稳定错误码分支。普通临时故障展示“文本大模型服务异常，暂时无法生成内容，请稍后重试”；配置错误展示“文本大模型配置异常，请联系演示人员”。服务端日志记录请求 ID、调用类型、HTTP 状态、耗时、token 用量和脱敏分类摘要，不记录 API Key、完整用户输入或完整上游响应。

所有 GLM 错误均阻断当前步骤，不提供内置通用表单、默认台词或包装降级。

## 6. 演示 API 草案

本轮沿用 `digital-human-studio` 现有 FastAPI 演示命名空间，不改变生产 RuoYi API。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/v1/demo/uploads` | 上传并校验人物图、参考声音或画中画 |
| `DELETE` | `/v1/demo/uploads/{uploadId}` | 删除尚未被任务快照引用的临时上传 |
| `POST` | `/v1/demo/ai/questionnaires` | 生成行业专属问题 |
| `POST` | `/v1/demo/ai/scripts` | 生成台词 |
| `POST` | `/v1/demo/ai/scripts/optimize` | 优化当前台词 |
| `POST` | `/v1/demo/ai/packaging-advice` | 分析画中画和花字建议 |
| `POST` | `/v1/demo/jobs` | 提交真实视频生成任务 |
| `POST` | `/v1/demo/jobs/{jobId}/retry` | 从不可变输入快照创建新任务 |
| `GET` | `/v1/demo/jobs/{jobId}` | 查询任务进度与阶段 |
| `GET` | `/v1/demo/jobs/{jobId}/video` | 播放最终 MP4 |

下载沿用同一媒体端点的 `?download=1`，避免维护两套 artifact 路径。普通播放返回 `Content-Type: video/mp4`、`Content-Disposition: inline` 并支持 Range/206；下载返回相同 artifact 的 attachment。

前端必须通过统一 service/adapter 调用，不在页面组件中拼接路径或解析错误码。

### 6.1 上传 DTO 与限制

`POST /uploads` 使用 `multipart/form-data`，字段为 `file` 和 `bizType`。成功响应：

```ts
type DemoUploadRef = {
  uploadId: string;
  bizType: 'portrait' | 'voiceReference' | 'pip';
  originalFileName: string;
  sizeBytes: number;
  mimeType: string;
  width?: number;
  height?: number;
  durationSeconds?: number;
  expiresAt: string;
};
```

硬限制：

- `portrait`：JPEG/PNG，最大 15 MiB，解码后宽高均为 512–8192 px，只允许一张。
- `pip`：JPEG/PNG，最大 15 MiB，解码后宽高均为 256–8192 px，只允许一张。
- `voiceReference`：WAV/MP3，最大 50 MiB，解码后真实时长 5–20 秒，采样率 16–48 kHz，单声道或双声道；服务端统一转码为模型需要的格式。
- 同时检查文件头、真实 MIME、解码结果、尺寸/时长和空文件，不只检查扩展名。

替换文件时先成功上传新文件，再切换草稿中的 `uploadId`；旧文件未被任务快照引用时可删除，被引用时保留到对应任务过期。上传失败或引用过期返回稳定错误，不让前端继续。

### 6.2 文本接口请求

```ts
type CreateQuestionnaireRequest = {
  industry: string;
  purpose: string;
  clientRevision: number;
};

type CreateScriptRequest = {
  questionnaireId: string;
  answers: QuestionnaireAnswer[];
  additionalNotes: string;
  clientRevision: number;
};

type OptimizeScriptRequest = {
  scriptId: string;
  expectedRevision: number;
  scriptText: string;
  preset?: 'shorter' | 'moreConversational' | 'emphasizeSellingPoints' | 'newOpening' | 'strongerCta';
  instruction?: string;
  clientRevision: number;
};

type AnalyzePackagingRequest = {
  baseScriptId: string;
  baseRevision: number;
  scriptText: string;
  clientRevision: number;
};
```

优化请求的 `preset`、`instruction` 必须且只能提供一个。文本接口响应分别使用 `QuestionnaireResult`、`ScriptResult`、`PackagingAdvice`；服务端把每次成功结果与输入 hash 保存在进程内。前端给每次请求附加 revision，并丢弃与当前草稿 revision 不一致的迟到响应；页面离开时主动取消仍在等待的请求。

### 6.3 任务创建、查询与结果 DTO

```ts
type CreateDemoJobRequest = {
  idempotencyKey: string;
  packagingAdviceId: string;
  portraitUploadId: string;
  voiceUploadId: string;
  pip: { enabled: boolean; recommendationId?: string; uploadId?: string };
  subtitle: {
    enabled: boolean;
    preset?: 'clean' | 'keywordHighlight' | 'sentencePop';
    keywordSourceRecommendationId?: string;
  };
  flower: {
    enabled: boolean;
    suggestionId?: string;
    text?: string;
    styleId?: 'goldBold' | 'blueLabel' | 'whiteShadow';
  };
};

type RetryDemoJobRequest = {
  idempotencyKey: string;
};

type DemoArtifact = {
  artifactId: string;
  videoUrl: string;
  downloadUrl: string;
  fileName: string;
  mimeType: 'video/mp4';
  sizeBytes: number;
  sha256: string;
  width: number;
  height: number;
  durationSeconds: number;
  videoCodec: 'h264';
  audioCodec: 'aac';
};

type DemoStageCode =
  | 'queued'
  | 'voiceGenerating'
  | 'avatarGenerating'
  | 'timingCalibrating'
  | 'composing'
  | 'validatingOutput'
  | 'done'
  | 'failed';

type DemoJobDetail = {
  jobId: string;
  parentJobId?: string;
  status: 'queued' | 'running' | 'done' | 'failed';
  stageCode: DemoStageCode;
  progress: number | null;
  failedStage?: Exclude<DemoStageCode, 'done' | 'failed'>;
  errorCode?: string;
  userMessage?: string;
  retryable: boolean;
  inputSnapshot: {
    script: {
      scriptId: string;
      scriptRevision: number;
      scriptText: string;
      scriptSourceHash: string;
    };
    packagingAdvice: PackagingAdvice;
    portrait: DemoUploadRef;
    voiceReference: DemoUploadRef;
    pipUpload?: DemoUploadRef;
    pip: CreateDemoJobRequest['pip'];
    subtitle: CreateDemoJobRequest['subtitle'];
    flower: CreateDemoJobRequest['flower'];
  };
  artifact?: DemoArtifact;
  actualModels?: { text: string; voice: string; avatar: string };
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
};
```

`POST /jobs` 快速返回 HTTP 202 和 `DemoJobDetail`。`idempotencyKey` 在当前演示进程内去重：相同 key 和相同请求返回已有任务，相同 key 但请求不同返回冲突。演示实例最多同时运行一个 GPU 生成任务，其余任务保持 `queued`；不支持取消。

`POST /jobs/{id}/retry` 的请求体为 `RetryDemoJobRequest`，成功返回 HTTP 202 和新的 `DemoJobDetail`。只有 `failed && retryable && inputSnapshot 未过期` 的任务允许重试；新任务设置 `parentJobId`。相同 retry key 和相同父任务返回已有新任务，相同 key 指向不同父任务或不同快照返回冲突。

任务合法流转：

```text
queued -> running/voiceGenerating
running/voiceGenerating -> running/avatarGenerating
running/avatarGenerating -> running/timingCalibrating
running/timingCalibrating -> running/composing
running/composing -> running/validatingOutput
running/validatingOutput -> done
任一非终态 -> failed
done / failed -> 不允许回到非终态
```

`progress` 只能来自远端可验证进度或服务端已完成工作量，并且单调不下降；无法得到可靠百分比时返回 `null`。`failedStage`、`errorCode`、`retryable` 由服务端映射，不能把远端原始错误直接展示给用户。单任务总超时默认 45 分钟，FFmpeg 子流程默认 5 分钟，均允许通过受限环境变量调整。

### 6.4 统一错误

除成功的媒体二进制响应外，失败统一返回：

```json
{
  "error": {
    "code": "TEXT_MODEL_TIMEOUT",
    "message": "文本大模型服务异常，暂时无法生成内容，请稍后重试",
    "requestId": "request-id",
    "retryable": true
  }
}
```

稳定错误至少覆盖：`INVALID_ARGUMENT`、`UNSUPPORTED_MEDIA_TYPE`、`FILE_TOO_LARGE`、`UPLOAD_NOT_FOUND`、`UPLOAD_EXPIRED`、`QUESTIONNAIRE_EXPIRED`、`SCRIPT_REVISION_CONFLICT`、`PACKAGING_ADVICE_STALE`、`JOB_NOT_FOUND`、`JOB_NOT_READY`、`JOB_INPUT_EXPIRED`、四类文本模型错误、`VOICE_MODEL_FAILED`、`AVATAR_MODEL_FAILED`、`COMPOSE_FAILED`、`OUTPUT_INVALID`。HTTP 状态按 400/404/409/413/415/422/502/503/504 表达；FastAPI 默认校验错误也转换为该结构。

前端展示中文文案来自集中映射，不依赖中文响应消息判断逻辑。终态 `done`、`failed` 后停止轮询。用户主动重新生成创建新的 `jobId`，不把旧任务从终态改回运行态。

## 7. 前端状态与组件边界

建议拆分：

- `RequirementStep`：行业、用途、动态问卷、全局补充。
- `ScriptStep`：台词编辑、优化、字数和时长。
- `UploadStep`：人物照片、参考声音、预览和校验。
- `PackagingStep`：语义建议、画中画、字幕和花字。
- `GenerationStep`：确认摘要、任务提交、轮询和重试。
- `ResultStep`：视频播放、结果信息、下载和返回修改。

跨步骤状态由一个显式的 `DemoDraft` 管理，至少包含：

- `draftRevision`、`industry`、`purpose`
- `questionnaireId`、`questionnaireSchema`、`questionnaireAnswers`、`additionalNotes`
- `scriptId`、`scriptRevision`、`scriptText`、`scriptConfirmed`
- `portraitUpload`、`voiceReferenceUpload`
- `packagingAdvice`、`pipUpload`、`subtitleConfig`、`flowerConfig`
- 当前 `jobId` 和最近一次不可变 `DemoJobDetail`

依赖失效规则：

- 行业变化 -> 用途回到未选择，并清除问卷、答案、台词确认和包装建议。
- 用途变化 -> 清除问卷、答案、台词确认和包装建议。
- 问卷答案或全局补充变化 -> 设置 `scriptConfirmed=false`，清除包装建议。
- 台词变化 -> 设置 `scriptConfirmed=false`，清除包装建议；重新确认后才能分析包装。
- 人物或声音变化 -> 保留台词和包装建议，但新草稿不再关联最近一次生成快照。
- 包装配置变化 -> 新草稿不再关联最近一次生成快照。

最近一次已经创建的任务和成片是不可变快照，上游修改不能篡改或删除它；页面可继续查看“上一次成片”，但新生成必须使用当前草稿创建新任务。确认弹窗只在用户提交会使下游失效的变更时出现，不在每次输入字符时弹出；取消时恢复原值。所有异步请求携带草稿 revision 或输入 hash，迟到响应与当前 revision 不一致时直接丢弃。

## 8. 加载、失败和按钮规则

- GLM 生成专属问题时保留行业、用途并显示局部加载；禁止重复提交。
- GLM 生成或优化台词时保留当前台词；成功前不能确认进入下一步。
- GLM 分析包装时保留已上传文件；成功前不能进入生成步骤。
- 上传中禁止继续；上传失败在对应卡片内展示原因和处理建议。
- 视频生成过程中锁定当前任务输入；不允许重复创建相同任务。
- 任务失败后可重新生成，重新生成复用输入但创建新任务。
- 下载失败不改变任务成功状态。
- 所有页面都包含明确的加载、提交中、成功和失败状态；本地演示不设计权限不足、额度不足、空列表和分页状态，因为不存在对应入口和数据集合。

按钮与导航守卫：

| 位置 | 主按钮可用条件 | 请求中行为 | 失败后解除阻断 |
| --- | --- | --- | --- |
| 1A 行业 | 已选择预设或有效自定义行业 | 禁止重复点击 | 修改后重试 |
| 1B 用途 | 已选择预设或有效自定义用途 | 禁止重复点击 | 修改后重试 |
| 1C 问卷 | 问卷有效且必填题完成 | 锁定问卷提交 | GLM 成功重试 |
| 第 2 步 | 台词非空、已清除 GLM 失败态 | 优化相关按钮全部禁用 | 成功重试，或明确恢复上一有效版本 |
| 第 3 步 | 人物与声音上传均已服务端校验 | 对应上传项显示进度 | 更换或重新上传 |
| 第 4 步 | 包装建议有效，且所有已开启功能满足必填 | 包装分析和继续按钮禁用 | GLM 成功重试 |
| 第 5 步 | 最终摘要通过服务端预校验 | 生成中禁止返回修改当前快照 | 失败后创建新任务 |

顶部已完成步骤允许点击返回，但不能绕过上述继续条件。生成中返回查看前序步骤时只能查看当前任务快照，不能修改；需要修改时先离开当前任务并建立新的草稿 revision，原任务继续独立运行。

轮询规则：页面卸载或切换 `jobId` 时停止旧轮询；单次查询失败不把任务标记为失败，保留上次状态并继续；连续 3 次失败显示连接警告，连续 5 次失败停止自动轮询并提供“重新查询”。404/410 进入任务失效页，终态立即停止轮询。

## 9. 文件与输出规则

- 前后端都校验图片、音频类型和大小，不能只依赖文件扩展名。
- 临时文件名由服务端生成，不使用用户原始文件名拼接路径。
- 每个上传和任务使用独立规范化目录；拒绝路径穿越、符号链接和目录外解析结果。
- FFmpeg、ffprobe 和其他外部程序只使用参数数组执行，台词、花字、模型文本和原文件名不得拼接进 shell 命令。
- 浏览器不接触 ComfyUI 文件系统路径、服务器 SSH 信息或 BigModel API Key。
- GLM 原始响应不直接返回前端，只返回通过 schema 校验的稳定 DTO。
- ComfyUI 返回的下载 URL 只允许配置的协议、主机和端口，不跟随任意模型返回地址。
- 最终 FFmpeg 输出必须重新通过 ffprobe，验证 H.264 视频流、AAC 音频流、有限正时长、9:16 尺寸及音视频时长容差后，才能标记任务成功。
- 播放和下载端点只接受已成功任务的 `jobId`。

临时生命周期：

- 未关联任务的上传 30 分钟后过期。
- 问卷、台词和包装建议快照在最后访问后保留 2 小时；被任务引用后随任务保留期延长。
- 运行中任务的输入和产物不得清理。
- 终态任务、输入快照和最终 MP4 保留 6 小时，随后返回 HTTP 410 / `JOB_INPUT_EXPIRED`。
- 正在播放或下载的文件受引用保护，流结束后才能清理。
- API 正常退出时尽力清理本进程临时根目录；异常退出后的残留在下次启动时按 TTL 扫描。

日志采用字段白名单，只记录内部 request/job ID、阶段、耗时、HTTP 状态、实际模型代码和截断后的分类摘要。GLM、IndexTTS2、LTX2.3、FFmpeg、上传、访问日志和异常堆栈都不得记录 API Key、Authorization、完整台词/问卷、肖像或声音内容、原文件名、绝对路径、完整命令和上游原始响应；HTTP 客户端 DEBUG 请求头/请求体日志必须关闭。

### 9.1 真实模型运行前置检查

- `DEMO_COMFY_BASE_URL` 必须显式配置，不使用代码中的公网硬编码兜底。
- 启动或首次生成前检查 ComfyUI `/system_stats`、LTX storyboard 插件路由、IndexTTS2/LTX2.3 所需工作流与模型能力、队列和 GPU 可用性。
- 记录能力探测得到的实际模型/工作流标识；不能仅返回固定 `engine` 标签来证明真实模型已执行。
- FFmpeg 与 ffprobe 必须在 PATH 中且版本可执行。
- API 使用单 worker；GPU 生成并发上限为 1，其余任务进入本地队列。
- 远端未知状态、能力缺失或总超时必须映射为稳定错误并终止，不无限轮询。

## 10. 测试与验收

### 前端

- 可选择预设行业和用途，也可分别输入自定义值。
- 删除“我不知道，交给 AI 判断”入口。
- 动态问卷每项都有补充输入，底部存在独立全局补充输入。
- GLM 加载时按钮不可重复点击；失败时阻断并保留输入。
- 台词可直接编辑，可用固定优化项和自由指令重新生成。
- 图片和音频校验失败时不能进入第四步。
- 包装页展示台词语义位置、理由、素材建议、字幕预设和花字编辑。
- 视频任务每 2 秒轮询，终态停止；`?job=` 可以恢复同进程任务。
- 第六步播放真实视频并下载同一 MP4。
- 覆盖草稿 reducer/失效矩阵、确认取消不丢输入、推荐值显式提交、迟到响应丢弃、上传替换、画中画开启时必填、轮询计时器清理、连续查询失败和过期任务恢复。
- 创建任务后 URL 必须写入 `?job=`；刷新后的 queued/running/done/failed/expired 分支分别验收。
- 顶部步骤、图标侧栏、动态题目、卡片选择、上传、开关、错误和播放器可通过键盘操作；动态加载/错误可被状态播报，选择不能只依赖颜色，播放器不自动播放。
- 测试优先使用角色、标签和可访问名称查询，不依赖脆弱 CSS 结构。

### 后端

- GLM 请求默认从两端 `application-dev.yml` 读取共享开发密钥，环境变量可选覆盖；日志和错误中无密钥。
- 四类 GLM 调用都覆盖成功、超时、上游错误、非法 JSON、schema 失败。
- 动态问题拒绝未知题型、重复 ID、超过数量和缺少补充能力的响应。
- 视频任务状态映射覆盖所有阶段和终态保护。
- 文件校验、FFmpeg 命令、ASS 转义、字幕预设、花字区间、画中画时间校准有单测。
- 视频与下载端点拒绝不存在、未完成或失败任务。
- 覆盖问卷/台词/包装 revision 冲突、相同与冲突幂等 key、新任务重试、并发排队、进程内 snapshot 和 TTL 过期。
- 覆盖 MIME 伪装、超大/空文件、图片解码炸弹、超长或无效音频、路径穿越、符号链接、恶意 ASS 文本和 Comfy 下载 URL allowlist。
- 最终 FFmpeg 输出必须被实际 ffprobe；任意字节或只有视频无音频的文件不能作为成功 fixture。
- 对日志和测试产物执行密钥片段扫描，确保不含 BigModel Key、Authorization、SSH 密码和完整敏感输入。

### 可重复离线验证

所有普通回归默认不调用付费/共享模型，使用可注入 httpx transport、Comfy stub、故障 composer 和小型真实媒体 fixture。基线命令：

```text
cd digital-human-studio
uv run pytest tests/demo -q
uv run ruff check apps/api/src tests/demo
uv run pyright
pnpm --filter @studio/web test
pnpm --filter @studio/web build
```

### 受控真实联调

真实联调通过单独的 gated smoke 脚本执行，要求显式设置 BigModel、Comfy 地址和 `RUN_LIVE_DEMO=1`，并提供固定的小型人物图、5–20 秒声音和画中画 fixture。脚本必须先做 §9.1 preflight，最长等待 45 分钟，输出 job ID、各阶段时间、GLM request ID、最终 ffprobe JSON、浏览器截图/录像路径和 artifact SHA-256，结束后按 TTL/清理命令处理临时文件。

1. 使用真实 GLM-5.2 生成问题、台词和包装建议。
2. 使用真实 IndexTTS2 克隆声音。
3. 使用真实 LTX2.3 生成人物视频。
4. 使用真实 FFmpeg 合成画中画、选定字幕预设和花字。
5. 用 ffprobe 验证 H.264/AAC、9:16、有效时长。
6. 在浏览器从第一步走到第六步，播放并下载成片。
7. 真实环境只跑安全的 happy path；GLM、IndexTTS2、LTX2.3、合成、轮询和下载异常主要通过离线故障注入确认阻断、保留输入和新任务重试，避免故意破坏共享 GPU 服务。

## 11. 并行协作与契约审查

可以并行：

- 前端基于固定 TypeScript 类型和 mock 实现 A2 六步页面。
- 后端实现 BigModel 客户端、schema 校验和四个文本接口。
- 后端扩展字幕预设、花字样式和真实时间校准。
- 联调人员准备真实素材、服务器健康检查和端到端验收脚本。

必须先固定再并行：

- `QuestionnaireField`、问卷答案和包装建议结构。
- GLM 错误码。
- 视频任务状态枚举和进度字段。
- 上传文件限制、字幕/花字预设值。

前端可以使用 mock 先行完成步骤 1、2、4 和任务状态页面；真实播放、下载、刷新恢复和模型失败映射必须等待后端接口完成后联调。

并行开始前需要提交共享 fixture：一份问卷响应、一份台词响应、一份包装建议、一个上传引用、一个运行任务、一个成功 artifact 和每类错误响应；前后端对这些 fixture 运行契约测试。任何 DTO 字段、枚举、文件限制、状态流转或错误码变化必须先 review 本规格并同步双方类型。

本演示不修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 和 `docs/ASYNC_TASKS.md`，因为它不声称是生产模块。未来生产化时，接口前缀、RuoYi 分层、任务持久化、权限、账号归属、额度、文件凭证和任务中心接入必须重新评审并同步公共契约。

## 12. 自检

- 已覆盖 A2 视觉、六步流程、GLM-5.2 动态问卷与台词、自由补充、语义画中画、字幕、花字、IndexTTS2、LTX2.3、真实合成、轮询、播放和下载。
- 已明确 GLM 失败必须阻断，不使用通用问卷或台词降级。
- 已明确每题补充和全局补充是两个不同输入。
- 已明确本轮不做响应式、项目、任务中心、资产、通知、数据保存和生产权限治理。
- 未在规格中记录用户提供的 API Key 或 SSH 密码。
- 动态模型输出有白名单 schema，不允许直接驱动任意 UI。
- 演示例外与生产公共契约边界已显式说明，不把演示实现宣称为生产完成。
- 已冻结问卷、答案、台词、包装、上传、任务、artifact 和错误 DTO，mock 与真实接口可共享契约 fixture。
- 已解决刷新后浏览器 `File` 丢失问题：服务端快照和 `uploadId` 只在本进程 TTL 内恢复，API 重启明确进入失效页。
- 已把任务总状态与阶段分离，沿用 `queued/running/done/failed`，避免破坏现有接口语义。
- 已定义 IndexTTS2 实际音频为唯一时间来源、语义锚点校准公式、最终 ffprobe 和同 artifact 播放下载。
- 已定义临时文件 TTL、单 worker/单 GPU 并发、幂等、终态保护、日志脱敏和本地 Host/Origin 隔离。
