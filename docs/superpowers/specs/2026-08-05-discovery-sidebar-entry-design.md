# 发现侧栏入口设计

- 日期：2026-08-05
- 状态：用户已确认
- 模块：用户端发现导航
- 风险：黄色中风险。变更用户可见导航行为，但不涉及接口、权限、账号归属、文件、积分、数据或外部供应商。

## 1. 目标与边界

在现有用户端侧栏顶部新增独立的“探索”分组，组内仅包含“发现”入口。其下保留“我的”分组以及“创作、形象、声音、文案、作品”五项，名称、顺序和行为均不改变。

本次不新增第二套侧栏，不改变 `/` 到 `/studio` 的重定向，不增加任务中心或订单菜单，不修改后端、API、权限、积分或数据契约。

权威来源：

- 用户于 2026-08-05 确认方案 C：单独建立“探索”分组。
- `docs/superpowers/specs/2026-08-05-discovery-multi-provider-workflow-template-design.md`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`

## 2. 导航结构

侧栏展开时按以下顺序展示：

1. 分组标题“探索”。
2. 菜单项“发现”，使用现有 `StudioIcon` 的 `app` 图标。
3. 分组标题“我的”。
4. 原有“创作、形象、声音、文案、作品”五项。

侧栏折叠时沿用现有折叠样式，只展示图标，并通过 `title` 保留“发现”名称。不得复制 Liblib 品牌资产或重做侧栏视觉。

## 3. 组件与状态契约

`StudioRoute` 继续只表达 Studio 内部五个内容区，不加入 `discover`。`StudioSider` 单独定义 UI 导航键 `StudioRoute | 'discover'`，并使用单一可选 active key，保证最多只有一个菜单项带有 `active` 和 `aria-current="page"`。

`StudioSider` 保持展示组件职责：

- `onDiscover` 负责通知父组件点击了发现入口。
- `onRouteChange` 继续只接收 `StudioRoute`。
- Studio 页面传入当前 `state.route`，点击发现后进入 `/discover`。
- `CreatorWorkspaceShell` 在 `activeKey="discover"` 时激活发现；`tasks` 和订单详情不激活任何侧栏菜单。

## 4. 路由与交互

- 点击“发现”使用站内导航进入 `/discover`。
- `/discover`、`/discover/templates/:templateId` 和 `/discover/templates/:templateId/create` 均高亮“发现”。
- `/studio?view=<StudioRoute>` 只高亮对应的原五项菜单。
- `/tasks` 和 `/orders/:orderId` 不误高亮“发现”或任一 Studio 菜单。
- 导航不发起新接口请求；认证、积分加载和失败态继续由现有壳层处理。

## 5. 验收与反向场景

- 展开侧栏依次出现“探索 / 发现 / 我的 / 原五项”。
- 从 `/studio` 点击发现后地址为 `/discover`，右侧显示发现内容。
- 发现首页、模板详情和模板制作页只有“发现”带 `aria-current="page"`。
- Studio 页面只有当前 Studio 内容区带 `aria-current="page"`。
- 任务中心和订单详情没有侧栏当前项。
- 原五项名称、顺序、折叠、用户信息、积分和跳转行为不变。
- 缺少登录用户时继续由现有认证门禁处理，不因新增入口绕过认证。

## 6. 测试与协作

任务卡：单一目标为新增“探索 / 发现”入口；允许修改共享侧栏、共享壳层、Studio 调用点、相关测试和本规格；不做后端或其他导航扩展。实施最多一名智能体，完成后安排一次独立代码审查。

必须验证：

- `StudioSider` 的分组顺序、发现点击、折叠标题和互斥高亮。
- `CreatorWorkspaceShell` 在发现、任务和订单上下文中的 active key。
- Studio 原五项切换和 `?view=` 恢复回归。
- 前端完整测试、TypeScript、相关 Biome 检查、规范校验和浏览器关键路径。
