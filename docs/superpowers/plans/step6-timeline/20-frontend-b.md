# 20 B：前端实现计划

> **负责人：B。** 本文件保留原总计划第 3 节和 6.3 任务卡。开始前必须确认当前分支起点等于 00 计划发布的 C0_SHA；不得自行修改 Java、SQL 或公共契约。共享纪律见 [README](README.md)。

## 3. 前端设备：时间轴编辑器、预览、任务轮询与第 7 步

> 本节只允许前端设备执行。涉及 Ant Design 组件、Token 或语义结构前必须先使用项目 `antd` 技能查询本仓库实际安装的 6.5.1 API；不凭记忆编写组件属性。

### 任务 17：建立 TypeScript 契约、wire adapter 与受控 Mock

**风险：** 黄色；整体集成仍按红色处理。生产构建必须自动证明 Mock 未注册。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/types.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/wire.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/adapter.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/api.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/queryKeys.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/idempotency.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/wire.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/adapter.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/api.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/idempotency.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/contract.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/types.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/adapter.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/api.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/queryKeys.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/adapter.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/api.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/blobAdapter.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/blobAdapter.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/mock/creationTimeline.ts`
- 新建：`ai-video-ui/ai-video-webapp/mock/creationAssets.ts`
- 修改：`ai-video-ui/ai-video-webapp/config/config.ts`
- 修改测试：`ai-video-ui/ai-video-webapp/src/config.test.ts`
- 新建：`ai-video-ui/ai-video-webapp/scripts/verify-creation-timeline-production.mjs`
- 修改：`ai-video-ui/ai-video-webapp/package.json`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/types.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/wire.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/adapter.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/api.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/queryKeys.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/idempotency.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/wire.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/adapter.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/api.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/idempotency.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/contract.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/types.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/adapter.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/api.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/queryKeys.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/adapter.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/api.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/core/blobAdapter.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/core/blobAdapter.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.test.ts
git add -- ai-video-ui/ai-video-webapp/mock/creationTimeline.ts
git add -- ai-video-ui/ai-video-webapp/mock/creationAssets.ts
git add -- ai-video-ui/ai-video-webapp/config/config.ts
git add -- ai-video-ui/ai-video-webapp/src/config.test.ts
git add -- ai-video-ui/ai-video-webapp/scripts/verify-creation-timeline-production.mjs
git add -- ai-video-ui/ai-video-webapp/package.json
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写失败测试，使用 Node `fs` 从仓库唯一 `docs/contracts/creation-timeline/` 读取固定样例；不把样例复制进前端目录。
- [ ] wire 类型忠实表达后端十进制字符串 ID、状态、阶段、时间轴和规范化变更；页面类型使用品牌字符串 ID，不转为 `number`。
- [ ] adapter 红灯测试覆盖未知字段、非法状态、错误 envelope、过大／非整数毫秒、缺少元素判别字段、未知合法任务类型和安全错误映射。
- [ ] 统一任务 wire 为三类建议结果建立判别联合；只有详情 adapter 按 taskType 校验 `resultPayload`，列表 adapter 明确拒绝／丢弃该字段。已知 success 缺 payload 或 payload 类型错返回契约错误，未知未来任务只保留通用元数据且不把 raw JSON 交给页面。
- [ ] API 红灯测试断言所有路径只集中在模块 `api.ts`，页面不得拼接 `/api/studio` 或 `/api/tasks`。
- [ ] API 红灯必须包含 `createConflictCopy(projectId, request)`，精确调用 `POST /api/studio/creation-projects/{projectId}/timeline-versions/conflict-copies`；adapter 对请求未知字段和响应大整数 number 一律失败。
- [ ] `blobAdapter` 红灯测试覆盖授权 Blob／Range 成功与 JSON 401、403、业务错误分流；如 multipart 需要上传进度，只在 `RuoYiAdapter` 增加类型安全进度透传，不允许页面旁路统一认证。
- [ ] 幂等工具在网络结果未知时复用原 key，用户主动新操作生成新 key；测试固定 key 生命周期，不把 key 与 React render 次数绑定。
- [ ] Mock 只在 `AI_VIDEO_CREATION_TIMELINE_MOCK=true` 时注册，Node 侧直接读取 C0 固定样例；默认 `start`、`start:no-mock`、`build` 和 CI 都不注册。
- [ ] 配置测试必须解析生成配置并证明生产环境排除两个时间轴 Mock。校验脚本在构建前拒绝生产开关，在构建后扫描 `dist`，不得出现 Mock marker 或注册端点。
- [ ] 先复制以下最小页面类型作为红灯目标，再逐联合成员实现 wire 校验；页面状态不允许退化成任意字符串或布尔组合：

```ts
export type TimelineSaveStatus =
  | { kind: 'saved'; revision: string; contentHash: string }
  | { kind: 'dirty'; basedOnRevision: string }
  | { kind: 'saving'; requestKey: string; basedOnRevision: string }
  | { kind: 'failed'; requestKey: string; retryable: boolean }
  | {
      kind: 'conflict';
      baseRevision: string;
      serverRevision: string;
      snapshot: TimelineDocument;
    };

export type TimelineOutputConfig = {
  resolutionPreset: 'match_canvas';
  frameRate: 30;
  qualityPreset: 'standard' | 'high';
};
```
- [ ] 运行红灯／绿灯：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test -- src/services/ai-video/creation-timeline/contract.test.ts src/services/ai-video/creation-timeline/wire.test.ts src/services/ai-video/creation-timeline/adapter.test.ts src/services/ai-video/creation-timeline/api.test.ts src/services/ai-video/creation-timeline/idempotency.test.ts src/services/ai-video/creation-assets/adapter.test.ts src/services/ai-video/creation-assets/api.test.ts src/services/ai-video/core/blobAdapter.test.ts src/config.test.ts
```

- [ ] 运行 `npm run tsc`，精确提交 `feat: 增加时间轴前端契约`。

### 任务 18：实现时间轴 reducer、选择、历史与几何规则

**风险：** 黄色。先实现纯状态核心，React 组件不得各自维护时间轴副本。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/types.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/reducer.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/selectors.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/geometry.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/history.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/commands.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/reducer.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/geometry.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/history.test.ts`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/types.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/reducer.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/selectors.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/geometry.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/history.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/commands.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/reducer.test.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/geometry.test.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/history.test.ts
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写 reducer 红灯测试：加载服务器规范时间轴、选择／取消选择、添加／更新／删除、复制、拆分、锁定、静音、移动、修剪、调整层级、修改轨道、应用服务器规范化结果；字段命令必须覆盖图片 `fitMode`／规范裁剪框／淡入淡出、画中画源裁剪起点／固定静音／循环、背景音乐自动 ducking，且默认值与 C0 样例完全一致。
- [ ] 写几何红灯测试：毫秒与像素互转、缩放、播放头／相邻边／主视频首尾吸附、修饰键临时关闭吸附、最小持续时间、项目边界、同类重叠派生视觉子轨、主视频不可移出中心、视觉轨道在上／音频轨道在下。
- [ ] 写画中画红灯测试：位置只允许四角与边距；显示时长超过源视频时设置循环语义，不能把元素截断到源时长。
- [ ] 写历史红灯测试：本地操作可撤销／重做，服务器加载／保存确认清空或重基线；历史不保存 Blob、临时 URL 或整个 React 状态。
- [ ] 写稳定元素 ID 和选择联动测试：预览点击、轨道点击和属性区使用同一个 `selectedElementId`。
- [ ] reducer 的第一条红灯直接以以下判别联合为目标；每个 case 返回新时间轴和可序列化历史命令，未处理 action 通过 `assertNever` 让 TypeScript 编译失败，不能静默忽略新增命令：

```ts
export type TimelineAction =
  | { type: 'serverLoaded'; timeline: TimelineDocument; revision: string }
  | { type: 'elementSelected'; elementId?: string }
  | { type: 'elementAdded'; trackId: string; element: TimelineElement }
  | { type: 'elementPatched'; elementId: string; patch: TimelineElementPatch }
  | { type: 'elementRemoved'; elementId: string }
  | { type: 'serverNormalized'; timeline: TimelineDocument; revision: string }
  | { type: 'undo' }
  | { type: 'redo' };
```
- [ ] 运行红灯／绿灯：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test -- src/pages/digital-human-studio/timeline/reducer.test.ts src/pages/digital-human-studio/timeline/geometry.test.ts src/pages/digital-human-studio/timeline/history.test.ts
```

- [ ] 最小实现纯函数；无网络、DOM 或 Ant Design 依赖。
- [ ] 精确提交 `feat: 实现时间轴编辑状态核心`。

### 任务 19：实现项目初始化、加载、自动保存与冲突恢复

**风险：** 红色。刷新后必须从服务端恢复，不以页面内七步状态作为事实源。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useCreationProject.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineDraft.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineAutosave.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineVersions.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineSaveStatus.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useCreationProject.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineAutosave.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineVersions.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineSaveStatus.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx`
- 修改测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StepFooter.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StepFooter.test.tsx`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useCreationProject.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineDraft.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineAutosave.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineVersions.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineSaveStatus.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useCreationProject.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineAutosave.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineVersions.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineSaveStatus.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StepFooter.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StepFooter.test.tsx
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写初始化红灯测试：第 5 步成功任务只传 `sourceType=digital_human_job`、`sourceId` 和幂等键；同项目刷新只 GET，不重复创建。
- [ ] 第 5→6 步在项目创建 mutation 成功后把形如 `view=create&step=5&projectId=9007199254740993` 的参数写回 URL；第 7 步使用 `step=6` 和同一 `projectId`。URL 中不保存草稿、任务、素材或归属字段。
- [ ] `StepFooter` 支持异步 pending、disabled 与 `aria-busy`，双击只能触发一次项目创建；第 5 步任务未成功或没有真实媒体时不能进入创建 mutation。
- [ ] 写加载红灯测试：loading、空来源、404、403、网络失败、非法响应和素材失效都有明确页面状态与重试动作。
- [ ] 写自动保存红灯测试：编辑防抖、一次只发送一个保存、后续修改进入唯一排队批次、携带当前 `expectedRevision` 和新幂等键；保存状态是单一联合类型 `saved|dirty|saving|failed|conflict`，分别展示“已保存／未保存／保存中／保存失败／草稿冲突”。旧请求成功只更新该请求对应的已保存基线；队列仍有编辑时回到 `dirty` 并继续下一次保存，不能短暂显示虚假“已保存”。只有服务器 revision、contentHash 和规范完整 timeline 已应用且无更新编辑时才能进入 `saved`。
- [ ] 页面关闭时仅在确有未确认本地修改时注册 `beforeunload` 提示；保存确认、重新加载、退出登录和卸载后清理监听器与 AbortController。
- [ ] 写回放红灯测试：普通 replay 使用响应；`superseded=true` 时绝不覆盖本地／服务器新修订，立即 GET 最新草稿并重基线。
- [ ] 写网络结果未知红灯测试：Abort 不显示业务失败；连接中断且服务端可能已提交时保留原幂等键重试；确定业务失败保留本地内容并进入 `failed`。
- [ ] 写修订冲突红灯测试：adapter 观察到响应信封 `R.code=46603` 时，把失败保存请求的 `basedOnRevision` 原样冻结为 `baseRevision`，同时保存服务器 `serverRevision` 与 `conflictSnapshot`，暂停自动保存并进入 `conflict`，不能依赖实际 HTTP 409。显示服务器修订和两个动作：选择“重新加载服务器版本”时二次确认后丢弃本地冲突内容并 GET 最新草稿；选择“另存为冲突版本”时用冻结的 `baseRevision + snapshot` 提交到 `conflict-copies`，成功后 GET 最新草稿并重建基线。冲突副本创建失败不得清空快照、覆盖草稿或显示已保存；不得静默覆盖、自动重放冲突内容或把浏览器下载文件伪装为服务端版本。
- [ ] 写页面状态红灯测试：初次加载、项目不存在、空草稿、接口失败、权限不足、素材失效、字体失效、自动保存失败与草稿冲突互不混用；字体代码不在 C0 登记表或字体资源加载失败时显示 `TIMELINE_FONT_UNAVAILABLE` 状态并阻止保存／合成，不静默回退系统字体。
- [ ] 写手动版本／恢复红灯测试：使用独立幂等键，恢复成功应用服务器返回的规范草稿；旧版本保持可浏览。
- [ ] `StudioState` 删除 `timelineSelected`，只保存项目编号、来源任务编号和当前步骤衔接；时间轴实体由专用 reducer 和 React Query 管理。
- [ ] 运行：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test -- src/pages/digital-human-studio/timeline/useCreationProject.test.tsx src/pages/digital-human-studio/timeline/useTimelineAutosave.test.tsx src/pages/digital-human-studio/timeline/useTimelineVersions.test.tsx src/pages/digital-human-studio/timeline/TimelineSaveStatus.test.tsx src/pages/digital-human-studio/components/StepFooter.test.tsx src/pages/digital-human-studio/steps/BaseStep.test.tsx src/pages/digital-human-studio/index.test.tsx
```

- [ ] 精确提交 `feat: 接入时间轴草稿与版本`。

### 任务 20：搭建预览、元素信息区、添加元素区和纵向轨道布局

**风险：** 黄色。布局必须保持用户已确认的区域关系。

**文件：**

- 重写：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/TimelineStep.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineEditor.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelinePreview.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineInspector.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/ElementAddBar.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineTracks.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineRuler.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelinePlayhead.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/TimelineStep.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineEditor.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/TimelineStep.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineEditor.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelinePreview.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/ElementAddBar.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineTracks.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineRuler.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelinePlayhead.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/TimelineStep.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineEditor.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css
git diff --cached --name-only
git diff --cached --check
```

- [ ] 使用 `antd doc`／`antd demo` 查询本任务用到的 `Splitter` 或 `Flex`、`Button`、`Tooltip`、`Empty`、`Result`、`Skeleton`、`Upload` API，把实际命令与结论写入任务交付记录。
- [ ] 组件红灯测试断言：画面预览在左、元素信息在右；添加图片／画中画／字幕／花字／背景音乐／音效／特效的操作条位于预览区下方且时间轴上方。
- [ ] 轨道红灯测试断言：视觉轨道纵向排列在主视频上方，主视频固定中间，音频和音效轨道排列在下方；不能恢复成横向类别栏或侧边添加栏。
- [ ] 写选中联动测试：预览元素、时间轴片段和右侧信息三者同步；未选中显示项目／画布信息，不制造虚假片段属性。
- [ ] 写 loading、空、失败、权限不足、素材失效和窄屏降级测试；关键按钮有可访问名称和键盘焦点。
- [ ] 实现浏览器预览画布、真实 `<video>`／`<audio>` 媒体和叠加层；内部素材键不进入 DOM，媒体只使用受控内容 URL。
- [ ] 受控二进制响应创建的 Blob URL 在素材切换、查询失效、组件卸载和退出登录时统一撤销；失败响应不得创建 Blob URL，防止长编辑会话持续占用内存。
- [ ] 运行：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test -- src/pages/digital-human-studio/steps/TimelineStep.test.tsx src/pages/digital-human-studio/timeline/TimelineEditor.test.tsx
```

- [ ] 精确提交 `feat: 搭建时间轴编辑器布局`。

### 任务 21：实现播放时钟、轨道拖动、修剪、缩放与预览拖拽

**风险：** 黄色。浏览器预览语义必须与服务端合成使用同一规范数据。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewClock.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelinePointerDrag.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewElementDrag.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineClip.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PreviewElement.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewClock.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelinePointerDrag.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewElementDrag.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineClip.test.tsx`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewClock.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelinePointerDrag.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewElementDrag.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineClip.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PreviewElement.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewClock.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelinePointerDrag.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/usePreviewElementDrag.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/TimelineClip.test.tsx
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写播放时钟红灯测试：播放／暂停／定位、`requestAnimationFrame` 清理、视频 seeking、页面隐藏、卸载和素材结束；时间以主视频为基准。
- [ ] 写轨道拖动红灯测试：Pointer Events、指针捕获、移动和左右修剪、吸附／修饰键关闭吸附、最小时长、项目边界、锁定／静音、拆分、复制、删除和键盘微调；拖动期间只预览，释放后产生一次 reducer 命令。
- [ ] 写图片／画中画／花字预览拖拽与缩放红灯测试：按画布坐标换算，显示安全区、中心线、边缘吸附线和画布边界；花字可自由拖动，画中画快捷位置只能选四角并保存边距，手动移动／缩放仍写归一化坐标。
- [ ] 写画中画循环红灯测试：当前时间先加 `sourceStartMs`，再对裁剪后的可用源视频时长取模；`audioEnabled` 首版只能为 `false`，暂停／定位和最终显示区间一致。
- [ ] 写缩放、滚动和播放头测试；缩放不改变时间值，滚动保持播放头可见，轨道元素不因像素取整累积漂移。
- [ ] 运行本任务四个测试文件，绿灯后精确提交 `feat: 实现时间轴拖放与播放`。

### 任务 22：实现图片／画中画素材选择和右侧属性编辑

**风险：** 黄色。素材只能从当前用户的通用创作素材接口选择。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/CreationAssetPicker.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/ImageInspector.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PictureInPictureInspector.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/CreationAssetPicker.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PictureInPictureInspector.test.tsx`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/CreationAssetPicker.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/ImageInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PictureInPictureInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/CreationAssetPicker.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/PictureInPictureInspector.test.tsx
git diff --cached --name-only
git diff --cached --check
```

- [ ] 查询并记录 Ant Design `Upload`、`Modal`、`List`／`Pagination`、`Image`、`Segmented`、`InputNumber` 的 6.5.1 官方 API。
- [ ] 写素材选择红灯测试：图片、视频、音频类型过滤、分页、上传进度、失败、403、空态、受控预览和重复选择。
- [ ] 写新增元素测试：选择插入时间和持续时间；默认从播放头插入并限制项目边界；素材真实时长由服务端事实提供。
- [ ] 写图片属性测试：`fitMode` 只允许 `contain|cover`，裁剪框使用 `0..1` 归一化左／上／宽／高且必须落在源图内，淡入／淡出各为 `0..3000ms` 且总和不超过元素时长。
- [ ] 写画中画属性测试：左上／右上／左下／右下、水平／垂直边距、尺寸、透明度、进入／退出时间、`sourceStartMs` 与循环提示；新元素强制 `loopWhenOverflow=true`、`audioEnabled=false`，首版 UI 不提供开启画中画声音的入口。
- [ ] 写素材删除／失效测试：编辑器显示失效状态并阻止合成，不把 404 当作空素材。
- [ ] 运行测试与 `npm run tsc`，精确提交 `feat: 增加时间轴素材编辑`。

### 任务 23：实现字幕完整性与样式属性

**风险：** 红色。不能少字、换行、保留标点或溢出画面。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/subtitle.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleInspector.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleOverlay.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/subtitle.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleInspector.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleOverlay.test.tsx`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/subtitle.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleOverlay.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/subtitle.test.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleInspector.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/SubtitleOverlay.test.tsx
git diff --cached --name-only
git diff --cached --check
```

- [ ] 前端测试直接读取 C0 字幕规范化夹具，证明 NFC 和 Unicode 码点偏移与后端样例一致；不得使用 UTF-16 `string.length` 作为来源偏移。
- [ ] 写完整性红灯测试：按项目脚本范围拼接后等于删除规范标点／空白后的原文；不允许空行、换行、遗漏、重复或越界范围。
- [ ] 写属性红灯测试：字体白名单、字号、文字颜色、可选背景色、可选描边和描边颜色；关闭背景／描边时不保留不可见的脏参数。
- [ ] 字幕默认位于下方安全区，拖动或属性修改后的归一化位置仍必须完全落在安全区；越界操作在预览中阻止，后端保存再次测量并裁定。
- [ ] 写安全区红灯测试：Canvas 字体测量只用于即时提示；超出时显示后端保存会规范化，不在前端截字或静默改文案。
- [ ] 写服务器规范化响应测试：按 `normalizationChanges` 高亮受影响字幕并应用服务器返回的完整时间轴。
- [ ] 运行本任务测试、`npm run tsc`，精确提交 `feat: 实现时间轴字幕编辑`。

### 任务 24：实现花字、六种模板、音乐、音效与画面特效

**风险：** 黄色。模板代码和参数必须来自 C0 白名单。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/fancyTextTemplates.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextInspector.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextOverlay.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AudioInspector.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/VisualEffectInspector.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/fancyTextTemplates.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextInspector.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AudioInspector.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/VisualEffectInspector.test.tsx`
- 新建受控 Web 字体：`ai-video-ui/ai-video-webapp/public/timeline-fonts/NotoSansCJKsc-Regular.otf`
- 新建受控 Web 字体：`ai-video-ui/ai-video-webapp/public/timeline-fonts/NotoSerifCJKsc-Regular.otf`
- 新建字体许可证：`ai-video-ui/ai-video-webapp/public/timeline-fonts/OFL.txt`
- 新建字体登记副本：`ai-video-ui/ai-video-webapp/public/timeline-fonts/font-registry.json`
- 新建字体校验：`ai-video-ui/ai-video-webapp/public/timeline-fonts/SHA256SUMS`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/fancyTextTemplates.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextOverlay.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AudioInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/VisualEffectInspector.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/fancyTextTemplates.test.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/FancyTextInspector.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AudioInspector.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/VisualEffectInspector.test.tsx
git add -- ai-video-ui/ai-video-webapp/public/timeline-fonts/NotoSansCJKsc-Regular.otf
git add -- ai-video-ui/ai-video-webapp/public/timeline-fonts/NotoSerifCJKsc-Regular.otf
git add -- ai-video-ui/ai-video-webapp/public/timeline-fonts/OFL.txt
git add -- ai-video-ui/ai-video-webapp/public/timeline-fonts/font-registry.json
git add -- ai-video-ui/ai-video-webapp/public/timeline-fonts/SHA256SUMS
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写六种模板快照／语义红灯测试，逐一验证模板代码、允许参数、字体／色彩／背景／描边／阴影／入退场语义；未知模板拒绝。
- [ ] 写花字测试：自定义文字、插入位置和时长、预览区拖拽、时间轴修剪、右侧模板切换；模板切换不改变文字和时间。
- [ ] 写背景音乐／音效测试：素材选择、音量、淡入淡出、循环、裁剪和轨道位置；背景音乐默认 `volumeRatio=0.30`、循环开启，ducking 固定 `enabled=true,targetGainRatio=0.35,attackMs=120,releaseMs=400` 且只由唯一主配音时间范围触发；音效默认 `volumeRatio=1`、不循环、不得携带 ducking。主视频已有唯一主声道时不重复添加主配音。
- [ ] 写基础画面特效测试：只允许白名单效果、强度和时间区间；不能输入 CSS、滤镜表达式或 FFmpeg 参数。
- [ ] 前端只登记 `noto_sans_cjk_sc_regular` 与 `noto_serif_cjk_sc_regular`，从 `/timeline-fonts/` 加载两个固定 OTF。先用 Web Crypto 校验文件 SHA，再构造 `FontFace`；登记摘要不符、字体加载失败或当前元素保存的 fontVersion/fontSha256 不匹配时进入字体失效状态并阻止保存／合成，禁止回退本机字体。构建测试逐项断言 public 登记副本与 C0 `font-registry.json` 字节一致、两个 OTF SHA 正确，并由集成门禁再与任务 34 的后端字体逐字节核对。
- [ ] 运行本任务测试和 `npm run tsc`，精确提交 `feat: 增加花字音频与画面特效`。

### 任务 25：接入 AI 建议、统一任务轮询、任务中心和第 7 步

**风险：** 红色。AI 结果只展示建议，用户确认前不得改写时间轴。

**文件：**

- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AiSuggestionPanel.tsx`
- 新建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineTask.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AiSuggestionPanel.test.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineTask.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts`
- 新建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/polling.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.test.ts`
- 新建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/polling.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/tasks/index.tsx`
- 修改／新建测试：`ai-video-ui/ai-video-webapp/src/pages/tasks/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ExportStep.tsx`
- 新建测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ExportStep.test.tsx`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AiSuggestionPanel.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineTask.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/AiSuggestionPanel.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/useTimelineTask.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/polling.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.test.ts
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/polling.test.ts
git add -- ai-video-ui/ai-video-webapp/src/pages/tasks/index.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/tasks/index.test.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ExportStep.tsx
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ExportStep.test.tsx
git diff --cached --name-only
git diff --cached --check
```

- [ ] 写 AI 请求红灯测试：只发送当前修订、来源码点范围／字幕 ID、风格白名单和幂等键；不发送任意项目外文字。
- [ ] 写建议结果红灯测试：未知字段、未知模板、原文外关键词或非法范围显示安全失败且不改草稿；接受／拒绝是显式用户操作。
- [ ] 建议任务进入 success 后必须使用最终详情中的强类型 `resultPayload` 渲染建议；刷新或 Java 重启后仍从任务详情恢复。success 缺 payload、类型不匹配或详情失败都显示“建议结果不可用”，绝不从列表摘要或本地临时状态伪造结果。
- [ ] 写轮询红灯测试：当前任务可见且聚焦时 2 秒、任务列表 5 秒；页面隐藏时 15 秒；连续隐藏 5 分钟停止；网络错误按 2、4、8、16、30 秒加有界随机抖动退避，离线暂停，恢复在线或重新可见时立即刷新一次。
- [ ] 写停止红灯测试：观察到任务终态时先取消周期计时器，再精确执行一次且仅一次不使用陈旧缓存的 `GET /api/tasks/{taskId}` 最终详情读取，完成后标记 finalized，禁止重启轮询或重复最终 GET。`success` 只有在最终详情仍为 success，且合成任务的 `resultAssetId` 与 `outputs/latest` 均指向真实 ready 成品时才展示成功；最终详情失败或成品缺失时展示“最终状态待确认／成品不一致”，不能沿用列表摘要伪装成功。组件卸载／退出登录立即清理全部计时器和请求。HTTP 401 或现有会话失效码 `46129/46131` 交给 `ruoyiAdapter` 单次清会话并停止全部受保护资源；403、HTTP 404、任务不存在或项目错误 `46601` 只停止对应 task／project／asset query key并显示权限不足／资源不存在，不误停其他仍有权限的资源。重新聚焦页面不得自动恢复已停止查询；只有重新登录、切换合法项目或用户显式重试才能恢复。取消请求成功前不乐观改状态。
- [ ] 任务中心请求不发送 `workspaceId` 或任何归属字段，按当前账号读取统一任务；现有会话缓存 key 若保留工作区片段，只能作为旧登录会话的本地隔离维度，不得进入 API、业务类型或任务身份。展示未知合法任务类型、阶段、进度、安全错误、取消和主动重试；详情链接只接受 C0 白名单 `detailTarget`，不能把 `resourceType/resourceId` 拼成任意 URL。
- [ ] 第 7 步只从当前项目 `outputs/latest` 加载真实 `ready` 成品；提供预览／下载、loading、无成品、处理中、失败、403、素材不一致状态。
- [ ] 运行：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test -- src/pages/digital-human-studio/timeline/AiSuggestionPanel.test.tsx src/pages/digital-human-studio/timeline/useTimelineTask.test.tsx src/services/ai-video/tasks/adapter.test.ts src/services/ai-video/tasks/api.test.ts src/services/ai-video/tasks/polling.test.ts src/pages/tasks/index.test.tsx src/pages/digital-human-studio/steps/ExportStep.test.tsx
```

- [ ] 精确提交 `feat: 接入时间轴任务与成品预览`。

### 任务 26：前端完整门禁、交互审查与 PR

**风险：** 黄色；进入集成后按红色处理。

- [ ] 运行时间轴相关测试，再运行全量测试、类型检查、lint 和生产构建：

```powershell
Set-Location ai-video-ui/ai-video-webapp
npm test
npm run tsc
npm run lint
npx antd lint src/pages/digital-human-studio/timeline --only a11y --format json
npx antd lint src/pages/digital-human-studio/timeline --only performance --format json
$env:AI_VIDEO_CREATION_TIMELINE_MOCK='false'
npm run build
```

- [ ] `package.json` 的正常 `build` 必须自动串联构建前开关校验与构建后 bundle 扫描；检查产物和 Umi 配置，证明没有时间轴 Mock 路由、固定样例数据或内部媒体键。
- [ ] 使用浏览器在 Mock 显式开启的开发模式走一遍：初始化、加载、添加七类元素、选择联动、拖动／修剪、保存冲突、版本恢复、AI 建议拒绝、合成任务和第 7 步。
- [ ] 在 1280×720 和常见桌面宽度检查字幕安全区、右侧属性可达、时间轴垂直顺序和键盘焦点。
- [ ] 发起只读前端审查，重点检查契约、状态单一事实源、定时器清理、无障碍、错误态、Ant Design 6 API 和生产 Mock 门禁；修复必须修复项后只做一次定向复核。
- [ ] 推送 `codex/step6-ui`，创建面向 `codex/step6-integration` 的 PR，附测试、构建、Mock 门禁和页面证据。

## 6. 本角色最小任务卡

### 6.3 前端设备任务卡

- **单一目标：** 完成任务 17 至任务 26，交付真实第 6 步编辑器、统一任务状态和第 7 步成品读取。
- **禁止事项：** 不改 Java、SQL、C0 夹具或公共后端契约；不在页面散写路径、状态、错误码或归属字段；不让 Mock 进入生产。
- **权威输入：** `C0_SHA`、本计划第 3 节、前端规范、Ant Design 6.5.1 CLI 结果和唯一契约夹具。
- **独占路径：** 数字人创作页、creation-timeline／creation-assets 前端 Service、统一任务前端 Service／页面、两个 Mock 文件、`src/services/ai-video/core/blobAdapter.ts`、`src/services/ai-video/core/ruoyiAdapter.ts` 及测试、`config/config.ts`、`src/config.test.ts`、`package.json`、`public/timeline-fonts/**` 和生产 Mock 校验脚本；这些共享文件已由任务 1 唯一分配给前端，集成负责人不双写。本轮禁止修改 `package-lock.json`。
- **交付证据：** Vitest、类型检查、lint、Ant Design a11y／performance lint、生产构建双门禁、关键桌面分辨率交互证据和 PR。
- **停止条件：** 后端 wire 与 C0 样例不一致时提交契约变更卡；禁止前端兼容两套未冻结字段。
