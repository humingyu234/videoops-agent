# 首页工作台与菜单路由骨架设计规格

## 背景

本规格用于将用户端 Web 首页改造成参考图中的“AI 视频工作台”首页，并同步建立后续页面会使用的菜单内容和路由骨架。

参考视觉源：

- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 12_46_13.png`

当前工程：

- 前端包：`ai-video-ui/ai-video-webapp`
- 技术栈：React + TypeScript + Ant Design + Ant Design Pro / ProComponents + Umi Max
- 当前状态：仍以 Ant Design Pro 模板页和模板菜单为主

## 已确认范围

采用方案 A：

- 保留现有 Ant Design Pro / ProLayout 菜单和顶部栏样式。
- 修改菜单内容和路由信息架构，使其符合 AI 视频工作台业务。
- 将首页内容区改造成参考图中的工作台 Dashboard。
- 后续页面先建立路由占位页，后面逐个实现完整业务。
- 不在本次实现中重做侧边栏视觉样式、顶部栏视觉样式或 Electron 壳层。

偏离 PRD 说明（需同步更新 `ai-video-pages.md`）：

- 积分使用情况：`ai-video-pages.md` 3.2 / 3.3 将其列为首页主内容区组件；本规格按参考设计稿，将其放在侧边栏菜单下方，不在首页主内容区实现。
- 本规格进入实现前，必须先同步修订 `ai-video-pages.md` 3.2、3.3、3.5 中“积分使用情况”的位置说明和验收口径，避免 PRD 与实现规格冲突。
- 本次首页一比一还原的验收范围不包含侧边栏下方积分卡视觉；积分卡随后续侧边栏改造阶段统一处理。

## 视觉还原目标

首页内容区必须按参考图做一比一还原，目标不是“接近风格”，而是在保留现有 ProLayout 菜单和顶部栏样式的前提下，让主内容区在同等桌面视口下与设计稿视觉一致。

- 页面背景、内容宽度、左右栅格、模块顺序、卡片尺寸、间距、内边距、圆角、阴影、边框、按钮位置、标题字号、正文颜色和标签样式必须对照设计稿调校。
- 桌面端布局必须复刻设计稿的主内容区结构：欢迎区 + 顶部引导/数据，快速开始横向入口，最近项目，右侧公告/灵感/素材，任务概览。
- 卡片渐变、业务色、缩略图、人物图、音频波形、引导插图等可见视觉资产必须使用真实位图资产或从参考图中提取/复用，不允许用粗糙占位框、纯文字或随意 CSS 图形替代。
- 内容密度必须与设计稿一致，避免模板页式大留白，也避免将卡片压缩成移动端堆叠。
- 若 ProLayout 固有菜单宽度或顶部栏高度导致整体横向尺寸与设计稿不同，以“主内容区相对于可用内容容器的一比一还原”为准；不为了主内容区还原去重做菜单外壳样式。
- 不修改 ProLayout 侧边栏和顶部栏本身的视觉样式。

### 视觉验收基准

- 设计稿基准尺寸为 `1680 x 946`。
- 实现验收使用 in-app browser 固定桌面视口 `1680 x 946`。
- 像素级对比范围为 ProLayout 内容区内部，不包含保留现状的侧边栏和顶部栏外壳。
- 菜单和顶部栏只验收菜单项、路由、右上角入口是否符合业务，不验收其视觉与设计稿像素一致。
- 若 ProLayout 内容区宽度与设计稿裸页面宽度不同，实现应按内容容器等比例复刻设计稿模块关系，保证模块相对位置、间距、尺寸层级和视觉密度一致。
- 实现完成前必须输出参考图与实现截图的同屏对比结果，并根据明显可见差异继续调整。

## 菜单与路由

菜单内容需要替换模板菜单。首期路由如下：

| 菜单 | 路由 | 页面状态 |
| --- | --- | --- |
| 首页 | `/dashboard` | 本次完整实现 |
| 视频创作 | `/video/create/industry` | 占位页 |
| 草稿箱 | `/drafts` | 占位页 |
| 模板中心 | `/templates` | 占位页 |
| 图生数字人 | `/digital-human/image` | 占位页 |
| 视频数字人 | `/digital-human/video` | 占位页 |
| 克隆声音 | `/voice-clone` | 占位页 |
| 素材管理 | `/assets` | 占位页 |
| 任务中心 | `/tasks` | 占位页 |

路由对齐说明：

- 与 `ai-video-pages.md` 第 20 节路由表保持一致。
- 视频创作本次只实现入口占位页，路由指向流程第一步 `/video/create/industry`；后续实现时按 PRD 补齐 `/video/create/directions`、`/video/create/templates`、`/video/create/workspace`、`/video/create/result`。
- 首页"快速开始""新建项目""使用此文案"跳转目标统一指向 `/video/create/industry`。

确认菜单分组：

- 首页：独立一级菜单。
- 视频创作：包含视频创作、草稿箱、模板中心。
- 数字人：包含图生数字人、视频数字人、克隆声音。
- 素材管理：独立一级菜单。
- 系统功能：包含任务中心。

顶部或页面内入口需要的非侧边栏路由：

| 入口 | 路由 | 页面状态 |
| --- | --- | --- |
| 帮助 | `/help` | 占位页 |
| 通知中心 / 系统公告 | `/notifications` | 占位页 |
| 账户与额度 | `/account` | 占位页 |
| 个人设置 | `/account/settings` | 占位页（承接头像菜单"个人设置"入口） |

规则：

- `/dashboard` 作为真正首页；访问根路径 `/` 时重定向到 `/dashboard`，登录后默认进入 `/dashboard`。
  - 实现方式：在 `config/routes.ts` 中将现有 `{ path: '/', redirect: '/welcome' }` 改为 `{ path: '/', redirect: '/dashboard' }`（Umi Max 原生支持 `redirect` 字段，无需运行时配置）。
- 不再使用模板的 `/welcome`、`/management` 等路由。
- 模板原有的 `src/pages/` 下首页文件（`Welcome`、`table-list`、`Admin` 等）本次保留，不删除；仅从 `config/routes.ts` 菜单可见入口中移除模板路由项。后续清理阶段再统一删除模板页面文件。
- 模板菜单项"欢迎、管理页、查询表格、OpenAPI 文档"等不出现在普通用户菜单中。
- 占位页面应显示当前模块名称、简短说明和后续建设状态，避免点击后 404。
- 占位页只用于路由骨架，不实现完整业务流程。

## 首页模块

首页由集中 mock 数据驱动，先不接后端。

### 顶部欢迎区

内容：

- 问候语：`下午好，小美`
- 副文案：欢迎使用 AI 视频工作台，快速创建专业级 AI 视频内容。
- 新手引导卡：标题、说明、开始学习按钮、右侧轻量插图或视觉资产。
- 创作数据卡：近 7 天统计，包含视频生成、数字人生成、声音克隆、模板使用。

固定 mock 数据：

| 指标 | 数值 |
| --- | --- |
| 视频生成 | `32` |
| 数字人生成 | `86` |
| 声音克隆 | `45` |
| 模板使用 | `18` |

交互：

- “开始学习”按钮跳转到 `/help` 占位页。
- “查看全部”跳转到任务中心或后续数据页；首期可跳 `/tasks`。

### 快速开始

参考图包含五个入口：

- 视频创作：从脚本到视频，一站式创作，跳 `/video/create/industry`。
- 模板中心：海量模板，一键生成视频，跳 `/templates`。
- 图生数字人：上传图片，生成数字人视频，跳 `/digital-human/image`。
- 视频数字人：上传视频，生成口播数字人视频，跳 `/digital-human/video`。
- 克隆声音：克隆真实声音，生成专属音色，跳 `/voice-clone`。

视觉：

- 横向卡片组，使用浅色渐变背景和业务色按钮。
- 卡片内应有可识别图形资产。可优先复用参考图局部裁切或使用合适的真实/生成位图资产；不得用纯占位框或粗糙 CSS 图形冒充最终视觉。

### 最近项目

内容：

- 标题：最近项目。
- 分类 tabs：全部、视频创作、图生数字人、视频数字人、克隆声音。
- 项目卡片：缩略图、类型标签、标题、更新时间、时长。
- 新建项目卡：跳 `/video/create/industry`。

固定 mock 项目：

| 类型 | 标题 | 更新时间 | 时长 |
| --- | --- | --- | --- |
| 视频创作 | 新品介绍视频 | `2025-05-15 14:32` | `00:45` |
| 图生数字人 | 品牌宣传片 | `2025-05-15 11:09` | `00:36` |
| 视频数字人 | 产品使用教程 | `2025-05-15 14:20` | `01:02` |
| 克隆声音 | 活动宣传配音 | `2025-05-14 16:05` | `00:28` |

交互：

- tabs 在前端本地筛选 mock 数据。
- 点击项目卡首期可进入占位详情提示或保持无跳转。
- 新建项目卡必须可跳转。

状态：

- 有数据：显示卡片列表。
- 空数据：显示 Ant Design Empty 和新建项目按钮。
- 筛选无结果：显示“暂无该类型项目”。
- 筛选无结果通过 query 参数 `?mockState=emptyProjects` 触发。该模式下保留最近项目 tabs，但将 `voiceClone` 类型项目置空，用于验证“克隆声音”tab 的空态。

### 任务概览

内容：

- 四个概览卡：生成中、排队中、已完成、失败。
- 最近任务表：任务名称、类型、状态、进度、创建时间、操作。

固定 mock 概览：

| 展示状态 | 稳定状态值 | 数值 | 说明 |
| --- | --- | --- | --- |
| 生成中 | `running` | `8` | 进行中的任务 |
| 排队中 | `queued` | `6` | 等待执行的任务 |
| 已完成 | `success` | `128` | 今日完成任务 |
| 失败 | `failed` | `4` | 今日失败任务 |

固定 mock 最近任务：

| 任务名称 | 类型 | 状态 | 进度 | 创建时间 |
| --- | --- | --- | --- | --- |
| 新品介绍视频 | 视频 | `running` | `45%` | `2025-05-15 14:32` |

交互：

- 概览卡点击可跳 `/tasks`，并可在后续通过 query 传状态（预留 query：`?status=running|queued|success|failed`）。
- 最近任务“查看详情”跳 `/tasks` 或显示占位提示。

状态：

- 任务状态、任务类型均使用集中枚举，不在组件中散写字符串。
- 任务状态遵循 `docs/ASYNC_TASKS.md` 终态定义，集中枚举 `TaskStatus` 至少包含：`running`（生成中）、`queued`（排队中）、`success`（已完成）、`failed`（失败）、`cancelled`（已取消）。首页概览卡只展示前四个高频状态，`cancelled` 在任务中心完整呈现。
- 任务类型遵循 `ai-video-pages.md` 任务中心定义，集中枚举 `TaskType` 至少包含：`video`（视频）、`image`（图片）、`digitalHuman`（数字人）、`voice`（声音）。首页"最近任务"mock 数据中的"类型"列使用 `TaskType` 值，不另造新枚举。
- 进度使用 Ant Design Progress。

### 右侧信息栏

模块：

- 系统公告：标题、日期、新标识、查看全部。
- 创作灵感：标题、文案、使用此文案按钮、换一换。
- 常用素材：我的视频、我的图片、我的声音。

固定 mock 系统公告：

| 标题 | 日期 | 标识 |
| --- | --- | --- |
| 平台功能更新公告 | `2025-05-15` | `NEW` |
| 关于优化视频生成速度的说明 | `2025-05-13` | 无 |
| 克隆声音功能使用指南 | `2025-05-10` | 无 |

固定 mock 创作灵感：

- 标题：`美妆产品推广文案示例`
- 文案：`焕发生机光彩，从这一刻开始。全新配方，温和呵护您的肌肤，让美丽由内而外绽放...`

固定 mock 常用素材：

| 素材 | 数量 |
| --- | --- |
| 我的视频 | `128 个文件` |
| 我的图片 | `256 张图片` |
| 我的声音 | `32 个音频` |

交互：

- 公告“查看全部”跳 `/notifications` 占位页。
- “换一换”在本地 mock 灵感文案中轮换。
- “使用此文案”跳 `/video/create/industry`，可后续通过草稿参数承接（预留 query：`?draftPreset=text&inspirationId=xxx`）。
- 常用素材项跳 `/assets`。

## 页面状态

首页首期必须覆盖：

- 加载态：进入页面时可短暂展示 skeleton 或模块级加载。
- 空态：最近项目、任务、公告、素材分别有空数据表达。
- 筛选无结果：最近项目 tabs 筛选无结果时独立展示。
- 失败态：首期通过本地状态开关或 query 参数 `?mockState=error` 触发。
  - 视觉表现：整页使用 Ant Design `Result` 组件，`status="error"`，展示标题"加载失败"、副文案"首页数据加载失败，请稍后重试"、`Extra` 区放"重试"按钮（重置 mockState 并重新加载）。
  - 范围：整页错误态；不做模块级局部错误，避免首期复杂度。后续接后端时再拆分为按模块独立错误。
- 权限不足：若初始用户不存在，仍沿用现有登录拦截；首页内部不绕过后端权限。
- 操作反馈：跳转型按钮直接跳转；未实现动作使用 `message.info` 或禁用态。

本地状态触发规则：

- `?mockState=error`：触发首页整页错误态。
- `?mockState=emptyProjects`：触发最近项目“克隆声音”tab 无数据，用于验证筛选无结果空态。
- `mockState` 只用于本地开发和测试，不进入后端契约。

响应式范围：

- 本次像素级验收只覆盖桌面端 `1680 x 946` 视口。
- 窄屏和移动端需要保持内容可访问、不卡死、不重叠；允许按 Ant Design 响应式规则堆叠，不纳入本次一比一视觉验收。

## 前端结构

建议新增或调整：

```text
src/pages/home/
  index.tsx
  index.less 或 style.ts
  data.ts
  types.ts
  components/
    QuickStartCard.tsx
    ProjectCard.tsx
    MetricCard.tsx
    TaskSummary.tsx
    SidePanel.tsx        # 仅做容器布局，不堆业务逻辑
    NoticeList.tsx       # 系统公告
    InspirationCard.tsx  # 创作灵感（换一换、使用此文案）
    AssetShortcuts.tsx   # 常用素材快捷入口

src/pages/placeholders/
  ModulePlaceholder.tsx

public/home-assets/
  logo-workbench.png
  guide-card.png
  quick-video-create.png
  quick-template.png
  digital-human-avatar.png
  voice-wave.png
  project-video-cover.png
  project-digital-human-cover.png
  project-voice-cover.png
```

可接受的轻量实现：

- 如果首页组件数量不大，可先用 `src/pages/home/index.tsx` + `data.ts` + `style.ts`，但不能把所有 mock、类型、组件和样式无边界地堆在单文件中。
- 页面样式应优先使用 Ant Design Token、Flex/Grid 和 CSS Modules 或 `antd-style`，避免覆盖 Ant Design 内部 DOM。

占位页路由配置示例（Umi Max，所有占位路由复用同一组件，靠 `module` 参数区分）：

Umi Max 的 `config/routes.ts` 路由项不支持 `props` 透传，采用薄 wrapper 文件方案——每个占位路由对应一个 2 行 wrapper 文件，内部渲染 `ModulePlaceholder` 并传入 `module`。

```ts
// config/routes.ts
{ path: '/video/create/industry', name: '视频创作', component: './placeholders/VideoCreate' },
{ path: '/drafts',                name: '草稿箱',   component: './placeholders/Drafts' },
// ...其余占位路由同构
```

```tsx
// src/pages/placeholders/VideoCreate.tsx
import ModulePlaceholder from './ModulePlaceholder';
export default () => <ModulePlaceholder module="video-create" />;
```

```tsx
// src/pages/placeholders/ModulePlaceholder.tsx（核心组件）
// 根据 module 渲染模块中文名、说明和后续建设状态，不实现业务流程
```

说明：每个占位路由对应一个 wrapper 文件，避免动态路由 `/placeholders/:module` 带来的业务路径 alias 问题。`ModulePlaceholder` 内部维护模块名 → 中文名/说明的映射表。

## 数据与类型

首页 mock 数据集中定义，至少包含：

- `DashboardMetric`
- `QuickStartItem`
- `RecentProject`
- `TaskSummaryItem`
- `RecentTask`
- `SystemNotice`
- `Inspiration`
- `AssetShortcut`

业务枚举集中定义：

- 项目类型 `ProjectType`：`videoCreate`、`imageDigitalHuman`、`videoDigitalHuman`、`voiceClone`（对应最近项目 tabs，不含图片生成项目）。
- 任务类型 `TaskType`：`video`、`image`、`digitalHuman`、`voice`（遵循 `ai-video-pages.md` 任务中心定义）。
- 任务状态 `TaskStatus`：`running`、`queued`、`success`、`failed`、`cancelled`（遵循 `docs/ASYNC_TASKS.md`）。

后续接后端时，应迁移到 `src/services/ai-video` 下的服务函数和类型定义。

## 资产清单

首页一比一还原依赖以下视觉资产。实现计划必须先完成资产提取或准备，再写页面布局。

| 资产 | 用途 | 首选来源 | 落盘建议 |
| --- | --- | --- | --- |
| 工作台 Logo | 品牌区或页面内需要复用时 | 参考图裁切或重新导出 | `public/home-assets/logo-workbench.png` |
| 新手引导插图 | 顶部新手引导卡 | 参考图裁切 | `public/home-assets/guide-card.png` |
| 视频创作插图 | 快速开始视频创作卡 | 参考图裁切 | `public/home-assets/quick-video-create.png` |
| 模板中心插图 | 快速开始模板中心卡 | 参考图裁切 | `public/home-assets/quick-template.png` |
| 数字人头像 | 图生数字人、视频数字人、项目卡缩略图 | 参考图裁切 | `public/home-assets/digital-human-avatar.png` |
| 音频波形 | 克隆声音卡和项目缩略图 | 参考图裁切 | `public/home-assets/voice-wave.png` |
| 项目视频缩略图 | 最近项目视频封面 | 参考图裁切 | `public/home-assets/project-video-cover.png` |
| 项目数字人缩略图 | 最近项目数字人封面 | 参考图裁切 | `public/home-assets/project-digital-human-cover.png` |
| 项目声音缩略图 | 最近项目声音封面 | 参考图裁切 | `public/home-assets/project-voice-cover.png` |

要求：

- 不允许使用空白占位图或随机网络图片替代上述资产。
- 资产获取优先级：**裁切参考图 > 使用 image generation 生成同构位图**。裁切优先，仅在裁切清晰度不足、构图不完整或涉及肖像/品牌资产需替换时才走生成路径。
- 生成资产必须保持设计稿的构图、颜色和视觉密度；涉及人物、品牌 LOGO 的资产，验收时以参考图为基准，不接受与设计稿明显偏离的生成结果。
- 所有资产引用路径集中定义，避免散落在组件中。

## API 与后端边界

本次只做前端静态 mock，不新增后端接口。

后续需要补充的接口方向：

- 首页统计：近 7 天视频生成、数字人生成、声音克隆、模板使用。
- 最近项目：按项目类型分页查询。
- 任务概览：按状态聚合统计和最近任务列表。
- 系统公告：公告列表。
- 常用素材：素材类型聚合统计。

后续进入后端实现前必须更新或确认：

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`

后端权限和归属规则：

- 首页所有用户数据必须按当前登录账号归属过滤。
- 统计、项目、任务、素材不得由前端传入 `ownerId` 决定归属。
- 后端接口必须校验认证状态，任务和素材访问必须校验账号归属。

## 验收标准

视觉验收：

- 桌面宽屏下首页内容区必须与参考图一比一匹配，包括布局、间距、卡片层级、字体层级、颜色、按钮、标签、缩略图和右侧栏模块。
- 菜单样式仍为现有 ProLayout 风格，但菜单内容和路由变为 AI 视频工作台业务。
- 首页不再出现 Ant Design Pro 模板欢迎内容、Cheatsheet、管理页或查询表格内容。
- 所有主要入口点击后不会 404。
- 实现后必须使用 in-app browser 在 `1680 x 946` 固定桌面视口截图，并与参考图放在同一对比视图中检查；发现明显可见差异必须继续调整，不能以“基本接近”作为完成标准。
- 必须验证 `/help`、`/notifications`、`/account`、`/account/settings` 等顶部、头像菜单或页面内入口不会 404。

功能验收：

- `/` 重定向到 `/dashboard`，可直接访问首页。
- 登录后默认进入 `/dashboard`。
- 菜单路由均可点击并显示对应页面或占位页。
- 最近项目 tabs 可本地切换。
- 创作灵感“换一换”可本地切换内容。
- 快速开始和常用素材入口可跳转。
- `?mockState=error` 可触发首页错误态，最近项目筛选无结果时可看到空态。

验证命令：

- `npm.cmd run tsc`
- `npm.cmd run test -- <新增或相关测试>`
- 通过 in-app browser 验证 `/dashboard`、关键菜单路由、首页主要交互。

测试覆盖最小清单（必须实现）：

- 最近项目 tabs 切换：全部、视频创作、图生数字人、视频数字人、克隆声音各自的筛选结果正确。
- 最近项目"筛选无结果"分支：访问 `/dashboard?mockState=emptyProjects`，选中“克隆声音”tab 时显示空态文案。
- 创作灵感"换一换"：在 mock 灵感数组内循环轮换，不越界、不重复连续。
- `?mockState=error` 触发错误态：渲染错误提示组件和恢复入口。
- 占位页 `ModulePlaceholder`：不同 `module` 参数渲染对应模块名和说明，不串扰。
- 根路径 `/` 重定向到 `/dashboard`。
- 快速开始卡片、新建项目卡、常用素材项跳转目标路由正确。

## 不在本次范围

- 不实现各业务页面完整功能。
- 不调整菜单和顶部栏为参考图的完全自定义样式。
- 不新增真实后端接口。
- 不做 Electron 壳层改造。
- 不做权限、额度、任务、素材的后端持久化逻辑。

## 风险与注意事项

- 参考图包含头像、人物、视频缩略图和插图资产。实现时必须使用真实图片资产或生成位图资产，不能用粗糙占位元素替代。
- 若直接裁切参考图资产，需要确保裁切尺寸和显示位置自然，不要拉伸或糊化。
- 现有模板国际化文件仍含大量模板菜单文案。菜单替换时，本次只要求 `zh-CN` 和 `en-US` 的新菜单、首页和占位页 key 完整；其他语言包不在本次改造范围内，不删除、不重构。
- 当前项目仍保留模板 mock 和模板页面，后续可分阶段清理，但本次只清理菜单可见入口。

## 国际化策略

本次实现范围只维护 `zh-CN` 和 `en-US` 两种语言的新业务文案：

- `zh-CN`：提供完整中文菜单、首页、占位页文案。
- `en-US`：提供等价英文菜单、首页、占位页文案。
- `zh-TW`、`ja-JP`、`bn-BD`、`fa-IR`、`id-ID`、`pt-BR`：本次不删除目录、不移除运行时支持；如缺少新业务 key，统一回退到 `en-US` 或显示默认文案，不能阻塞首页交付。
- 不在本次首页改造中删除语言包或重构语言选择器。删除多语言包属于全局架构变更，需单独规格和实现计划。
- 后续如需完整多语言，按 `ai-video-pages.md` 业务文案补齐，不复用 Ant Design Pro 模板旧 key。
