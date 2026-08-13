# 发现模块用户端页面实现计划

> **2026-08-11 首屏废止提示：** 本历史计划中所有多供应商、模板版本、执行方案选择、方案级 form-schema、`executionPlanId`、`templateVersionId`、`providerKind` 与“选择其他方案”内容已被发现页 RunningHub 单执行公共契约废止。实施只能依据 `docs/API_CONTRACT.md` 的当前覆盖小节和 `docs/contracts/discovery-runninghub/` 夹具；用户端只读取首页/列表/详情/creation-config，创建订单仅提交 `templateId`、`schemaHash`、`inputs`。

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development 逐任务实现此计划；每个任务遵循 superpowers:test-driven-development，步骤使用复选框跟踪。

**目标：** 在 ai-video-webapp 中交付可运行、可测试的发现首页、模板详情、模板制作、订单详情和任务中心，使用真实前端 API 契约与确定性 Umi Mock，并保持根路径与现有 Studio 行为不变。

**架构：** 用应用级 React Query 管理私有查询缓存，用共享 CreatorWorkspaceShell 承载 Studio、发现、订单和任务页面。页面只依赖经过严格 parser 的领域 API；动态表单通过白名单控件注册表渲染，素材先走通用上传会话，订单详情只轮询平台订单/任务状态。开发预览由 Umi Mock 提供确定性响应，生产构建不回退 Mock。

**技术栈：** React 19、Umi Max 4、TypeScript 7、Ant Design 6、TanStack React Query 5、CSS Modules、Vitest 4、Testing Library、Umi Mock。

**规格：** docs/superpowers/specs/2026-08-05-discovery-multi-provider-workflow-template-design.md

## 2026-08-05 用户确认纠偏：仅修改右侧内容区

本节优先于下文所有关于新增顶层侧栏菜单、第二套侧栏或 200px 自定义侧栏的描述。

- 风险：黄色中风险；只调整用户可见布局和站内路由，不修改权限、接口、数据、积分或供应商契约。
- 边界：`/discover`、模板、订单和任务页面必须直接复用现有 `StudioSider`、积分、用户区和折叠状态；不得新增、删除、改名或重排“创作、形象、声音、文案、作品”。
- 实现：`CreatorWorkspaceShell` 只负责把页面一级标题和 `children` 放入原 `studio-shell app / main / topbar / content` 的右侧，并为右侧提供唯一 `main` 地标；非 Studio 页面保留五个菜单但不误标任何当前项。原菜单点击跳转 `/studio?view=<StudioRoute>`，`/studio` 仅解析白名单 `view`，且菜单切换、素材库返回、新建项目和完成创作均同步该 query。
- 测试：先让 `src/components/CreatorWorkspaceShell/index.test.tsx` 因仍存在“发现 / 创作台 / 任务中心”、错误菜单高亮或缺少 `h1/main` 而失败，再复用真实 `StudioSider` 使其通过；`src/pages/digital-human-studio/index.test.tsx` 验证合法／非法 `view`、四类切换入口及卸载重挂载后的内容恢复。
- 验证：运行上述两个测试、发现页测试、TypeScript 类型检查、`git diff --check`，并在 `1440 × 900` 浏览器中确认左侧与 `/studio` 一致且内容仍显示于右侧。
- 协作：主代理实施；只读壳层分析由 `fast_shell_routes` 完成；交付前执行一次相关回归和浏览器复验，不扩展新菜单或新入口。

**可执行环境：** 当前 PowerShell 不直接解析 `node.exe`。本计划所有 Node 命令先设置：

~~~powershell
$Node = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
~~~

随后统一使用 `& $Node <script> ...`。Umi 构建入口为 `node_modules/@umijs/max/bin/max.js`，不是不存在的 `dist/cli/cli.js`。

---

## 范围与文件结构

本计划只实现用户端前端及开发 Mock，不实现后端、运营端、积分扣减、通知或真实供应商调用。页面会调用正式 URL；当后端未启动时，npm start 使用 Mock 预览。npm run dev、start:test、start:pre 与生产构建继续使用 MOCK=none。

创建或修改的职责边界：

- config/routes.ts：登记 /discover、模板详情、模板制作、/orders/:orderId、/tasks，保留 / 到 /studio。
- src/query/appQueryClient.tsx：应用唯一 QueryClientProvider，会话清除时清空私有缓存。
- src/components/CreatorWorkspaceShell/*：复用现有 Studio 侧栏、折叠、用户与积分展示，只提供右侧页面插槽。
- src/hooks/usePersonalQuotaAccount.ts：把个人积分查询从 Studio 页面目录移到共享 Hook。
- src/services/ai-video/core/wire.ts：小型、严格、无依赖的 wire parser 原语。
- src/services/ai-video/discovery/*：发现、模板、方案、动态 schema 类型、parser、API 和 query key。
- src/services/ai-video/assets/*：上传会话、签名对象传输、完成/取消和 access URL。
- src/services/ai-video/workflow-orders/*：创建、详情、取消、字段错误、schema 冲突与 query key。
- src/services/ai-video/tasks/*：任务中心分页。
- src/pages/discovery/*：首页、详情、制作和领域组件。
- src/pages/workflow-orders/detail/*：订单任务状态、结果和轮询。
- src/pages/tasks/*：统一用户任务中心。
- mock/discovery.ts、mock/workflowOrders.ts、mock/tasks.ts、mock/workflowAssets.ts：确定性开发响应。
- public/discovery/*：原创、无品牌、无水印的模板视觉素材。
- src/locales/zh-CN/pages.ts、src/locales/en-US/pages.ts：用户端静态文案；运营内容保持接口原文。

## 风险与任务卡

| 任务 | 风险 | 单一目标 | 依赖 | 审查与验证 |
| --- | --- | --- | --- | --- |
| 0. 公共契约与视觉基线 | 红色 | 先冻结 wire、权限、上传与任务边界 | 已确认规格 | 前后端双视角审查 + 标准校验 |
| 1. 查询与 wire 基础 | 黄色 | 建立隔离缓存和严格解析 | 已冻结 VO | 单测 + 规格审查 + 质量审查 |
| 2. 共享壳与路由 | 黄色 | 所有用户页共用真实 URL 导航 | 任务 1 | 路由/认证/回归测试 |
| 3. 发现服务 | 黄色 | 查询参数和 DTO 不散落页面 | 任务 1 | adapter 契约测试 |
| 4. 发现首页 | 黄色 | 复刻参考站模块关系与密度 | 任务 2、3、原创素材 | 页面状态 + 1440×900 |
| 5. 模板详情 | 黄色 | 介绍模板并进入制作页 | 任务 2、3 | 下架/无方案/媒体失败 |
| 6. 动态表单与上传 | 红色 | 只提交 schema 允许的规范值与 ready 资产 | 任务 1、3 | 类型、竞态、签名头专项 |
| 7. 制作页与下单 | 红色 | 用户主动选供应商，幂等创建订单 | 任务 5、6 | 双击、465xx、无自动切换 |
| 8. 订单与任务中心 | 黄色 | 真实平台状态轮询和统一任务入口 | 任务 2、3 | 终态停止、取消、权限 |
| 9. Mock 与总体验收 | 黄色 | 可预览且无生产 Mock 兜底 | 全部 | test/tsc/build/antd lint/视觉 |

任何任务不得读取或修改原工作区 D:/AI/ai-video 中未提交的数字人文件；全部操作限定在 D:/AI/ai-video/.worktrees/discovery-user-pages。

### 任务 0：冻结公共契约与视觉基线（后续领域实现的前置门禁）

**文件：**

- 修改：docs/API_CONTRACT.md
- 修改：docs/superpowers/specs/2026-08-03-secure-asset-oss-upload-download-design.md
- 修改：docs/superpowers/specs/2026-08-03-user-portrait-library-design.md
- 修改：docs/superpowers/specs/2026-08-05-discovery-multi-provider-workflow-template-design.md
- 创建（本机验收证据，不提交第三方页面内容）：C:/Users/Administrator/.codex/visualizations/2026/08/03/019fc9c4-1279-7363-b90a-f0fd6a55f8f5/discovery-baseline/liblib-home-1440x900-2026-08-05.png
- 创建（本机验收证据）：C:/Users/Administrator/.codex/visualizations/2026/08/03/019fc9c4-1279-7363-b90a-f0fd6a55f8f5/discovery-baseline/local-discover-1440x900.png

- [x] **步骤 1：冻结精确用户 VO、动态表单、订单、状态矩阵与统一任务列表**

`docs/API_CONTRACT.md` 在任务 3 之前登记查询端点、精确 VO、`GET /api/tasks`、权限、稳定错误码与页面分支。任务列表允许展示后端注册表中的其他生成类型，只对白名单 `detailTarget` 导航，不能退化为工作流订单列表。

- [x] **步骤 2：冻结上传 wire 和幂等恢复**

创建、parts、complete、cancel、status 响应精确化；`46212` 唯一表示上传会话过期。未知网络结果沿用原上传键；明确过期后生成新键，并以 `uploadId + requestGeneration` 丢弃迟到响应。

- [ ] **步骤 3：归档参考与本地 1440×900 截图**

参考截图只保存到上述 Codex 本机验收目录，不加入 Git，不把 Liblib 品牌或内容资产带入产品。页面实现后以同尺寸保存本地截图并逐项对比布局关系、密度和断点；浏览器验收结束后重置临时 viewport。

- [ ] **步骤 4：运行公共标准验证并由前后端视角各审一次**

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
git diff --check
~~~

预期：`DEVELOPMENT_STANDARDS_OK`，且没有 P0/P1 契约缺口。未通过前不得开始任务 3、6、7、8。

### 任务 1：应用查询基础与严格 wire parser

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/query/appQueryClient.tsx
- 创建：ai-video-ui/ai-video-webapp/src/query/appQueryClient.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/core/wire.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/core/wire.test.ts
- 修改：ai-video-ui/ai-video-webapp/src/app.tsx
- 修改：ai-video-ui/ai-video-webapp/src/app.test.tsx

- [ ] **步骤 1：先写失败测试**

测试必须证明：应用只创建一个 QueryClient；默认 query retry 为 1、mutation retry 为 0；authSession.clear() 后私有缓存被清空；wire parser 拒绝 null、未知枚举、非十进制字符串和响应中的敏感键。

~~~ts
it('clears private query data when the app session is cleared', () => {
  appQueryClient.setQueryData(['app-private', 'u1', 'w1', 'order', 'o1'], { orderId: 'o1' });
  authSession.clear();
  expect(appQueryClient.getQueryData(['app-private', 'u1', 'w1', 'order', 'o1'])).toBeUndefined();
});

it('rejects forbidden provider fields before rendering', () => {
  expect(() => assertNoSensitiveWireKeys({ templateId: 't1', workflowId: 'secret' }))
    .toThrow('响应包含未公开的供应商字段');
});
~~~

- [ ] **步骤 2：运行测试确认红灯**

运行：

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/query/appQueryClient.test.tsx src/services/ai-video/core/wire.test.ts
~~~

预期：FAIL，模块不存在。

- [ ] **步骤 3：实现最少基础设施**

~~~tsx
export const appQueryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
    mutations: { retry: 0 },
  },
});

subscribeToAuthSessionClear(() => {
  appQueryClient.clear();
});

export function AppQueryProvider({ children }: PropsWithChildren) {
  return <QueryClientProvider client={appQueryClient}>{children}</QueryClientProvider>;
}
~~~

wire.ts 至少导出 assertRecord、readString、readOptionalString、readPositiveInteger、readDecimalString、readEnum、readArray、assertExactKeys、assertNoSensitiveWireKeys。敏感键比较使用规范化小写全名，不扫描用户文本值。

- [ ] **步骤 4：把 AppQueryProvider 放入 innerProvider**

~~~tsx
export function innerProvider(container: React.ReactNode) {
  return (
    <AppQueryProvider>
      <AppAuthRouteGate>{container}</AppAuthRouteGate>
    </AppQueryProvider>
  );
}
~~~

- [ ] **步骤 5：运行目标测试和 app 回归**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/query/appQueryClient.test.tsx src/services/ai-video/core/wire.test.ts src/app.test.tsx
~~~

预期：全部 PASS。

- [ ] **步骤 6：提交**

~~~powershell
git add ai-video-ui/ai-video-webapp/src/query ai-video-ui/ai-video-webapp/src/services/ai-video/core/wire.ts ai-video-ui/ai-video-webapp/src/services/ai-video/core/wire.test.ts ai-video-ui/ai-video-webapp/src/app.tsx ai-video-ui/ai-video-webapp/src/app.test.tsx
git commit -m "feat(创作端): 建立发现页查询基础"
~~~

### 任务 2：共享创作端壳层与真实 URL 路由

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/hooks/useCreatorShellState.ts
- 创建：ai-video-ui/ai-video-webapp/src/hooks/usePersonalQuotaAccount.ts
- 修改：ai-video-ui/ai-video-webapp/src/access.ts
- 修改：ai-video-ui/ai-video-webapp/src/access.test.ts
- 修改：ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx
- 修改：ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx
- 修改：ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.test.tsx
- 修改：ai-video-ui/ai-video-webapp/config/routes.ts

- [ ] **步骤 1：先写路由与壳层失败测试**

断言根重定向仍是 /studio；新增五个 layout:false 页面；新页面侧栏仍只有“创作、形象、声音、文案、作品”，不存在“发现、创作台、任务中心”这套替代菜单，且五项均不误标为当前项；折叠状态仍使用 dh-sidebar-collapsed；当前用户和积分状态只请求一次。`/studio?view=<StudioRoute>` 用于恢复现有内容区，所有改变 Studio 内容区的入口必须同步该 query，不引入新的业务路由枚举。

~~~tsx
expect(within(screen.getByRole('navigation')).getAllByRole('button'))
  .toHaveLength(5);
fireEvent.click(screen.getByRole('button', { name: /声音$/ }));
expect(mockHistoryPush).toHaveBeenCalledWith('/studio?view=voices');
~~~

- [ ] **步骤 2：运行目标测试确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/components/CreatorWorkspaceShell/index.test.tsx src/pages/digital-human-studio/index.test.tsx
~~~

预期：FAIL，新组件和路由不存在。

- [ ] **步骤 3：实现共享壳**

CreatorWorkspaceShell 接收 activeKey、title、description、headerActions、children；内部读取 @@initialState.currentUser，调用共享 usePersonalQuotaAccount，并在无用户时返回 null。壳层直接渲染现有 StudioSider，不创建顶级侧栏，不把 discover/tasks 加入 StudioRoute，也不选中任一 Studio 菜单；壳层输出一个 `h1` 和一个包含页面 children 的 `main`，菜单点击通过白名单 query 返回现有 Studio 内容区。

~~~ts
export type CreatorNavigationKey =
  | 'discover'
  | 'studio'
  | 'tasks';
~~~

共享壳只复用视觉壳、用户/积分和折叠，不拥有顶级导航。除解析白名单 `view` 以恢复既有 StudioRoute 外，不得改变步骤数据或重构现有业务组件。

- [ ] **步骤 4：添加精确路由**

~~~ts
{ path: '/discover', component: './discovery', layout: false, access: 'canStudioQuery' },
{ path: '/discover/templates/:templateId', component: './discovery/template-detail', layout: false, access: 'canStudioQuery' },
{ path: '/discover/templates/:templateId/create', component: './discovery/template-create', layout: false, access: 'canWorkflowCreate' },
{ path: '/orders/:orderId', component: './workflow-orders/detail', layout: false, access: 'canTaskQuery' },
{ path: '/tasks', component: './tasks', layout: false, access: 'canTaskQuery' },
~~~

`/discover` 与模板详情要求 `aivideo:studio:query`；`canWorkflowCreate` 同时要求 `aivideo:studio:query` 和 `aivideo:studio:generate`；订单与 `/tasks` 要求 `aivideo:task:query`。`src/access.ts` 从 `AuthUser.permissions` 生成精确布尔权限，不以角色名、运营端 `canAdmin` 或 UI 隐藏替代接口授权。测试覆盖权限存在、缺失、空数组和组合权限，路由 `access` 是直接访问的第一层门禁，页面内操作权限仍单独测试。

- [ ] **步骤 5：运行壳层、认证和 Studio 回归**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/access.test.ts src/components/CreatorWorkspaceShell/index.test.tsx src/pages/digital-human-studio/index.test.tsx src/pages/digital-human-studio/components/StudioSider.test.tsx src/components/AppAuthRouteGate/index.test.tsx
~~~

预期：全部 PASS，匿名路径仍保留 query/hash 后跳登录。

- [ ] **步骤 6：提交**

~~~powershell
git add ai-video-ui/ai-video-webapp/config/routes.ts ai-video-ui/ai-video-webapp/src/access.ts ai-video-ui/ai-video-webapp/src/access.test.ts ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell ai-video-ui/ai-video-webapp/src/hooks ai-video-ui/ai-video-webapp/src/pages/digital-human-studio
git commit -m "feat(创作端): 共享发现页工作台壳层"
~~~

### 任务 3：发现、模板和任务领域 API

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/types.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/parsers.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/queryKeys.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/api.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/api.test.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/testFixtures.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/parsers.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.test.ts

- [ ] **步骤 1：写 adapter 契约失败测试**

覆盖 home、分页、detail、execution plans、schema 和 tasks。断言 query 使用白名单，tagCodes 去重后排序；所有调用透传 AbortSignal；rows=null 与缺少 rows 均拒绝；ID 和计数保持字符串；响应出现 workflowId/nodeId/providerConfigId 时拒绝。媒体 URL 仅接受 `https:` 绝对地址或单 `/` 开头的同源相对路径，拒绝 `http:`、`//host`、`data:`、`blob:`、`javascript:`、反斜杠和控制字符。

~~~ts
await api.listTemplates({
  channel: 'video_template',
  tagCodes: ['portrait', 'commerce', 'portrait'],
  pageNum: 2,
  pageSize: 20,
  sort: 'recommended',
});
expect(requests[0].url).toBe(
  '/api/discovery/templates?pageNum=2&pageSize=20&channel=video_template&tagCodes=commerce%2Cportrait&sort=recommended',
);
~~~

- [ ] **步骤 2：运行测试确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/discovery/api.test.ts src/services/ai-video/tasks/api.test.ts
~~~

预期：FAIL，API 不存在。

- [ ] **步骤 3：实现精确类型和 parser**

types.ts 必须逐字段对应规格 14.1.1、14.4.1、14.4.2。WorkflowFormSchemaVO 固定 schemaVersion='workflow-form-1'；controlType 仅允许 text、textarea、integer、decimal、boolean、select、multi_select、image、audio、video、file。

~~~ts
export const discoveryQueryKeys = {
  home: (scope: AppQueryScope) => ['app-private', scope.userId, scope.workspaceId, 'discovery', 'home'] as const,
  templates: (scope: AppQueryScope, filters: NormalizedTemplateFilters) =>
    ['app-private', scope.userId, scope.workspaceId, 'discovery', 'templates', filters] as const,
  schema: (scope: AppQueryScope, versionId: string, planId: string) =>
    ['app-private', scope.userId, scope.workspaceId, 'workflow-schema', versionId, planId] as const,
};

export const taskQueryKeys = {
  list: (scope: AppQueryScope, filters: NormalizedTaskFilters) =>
    ['app-private', scope.userId, scope.workspaceId, 'tasks', filters] as const,
};
~~~

`AppQueryScope` 必须同时取得当前 `userId` 和 `workspaceId`；缺少任一项时不发私有请求。测试分别改变 user 和 workspace，证明 discovery、schema、workflow order 与 tasks 键都不会命中旧缓存。

- [ ] **步骤 4：实现 createDiscoveryApi/createTasksApi**

两个工厂只依赖 RuoYiAdapter；runtime 单例统一使用 getRuntimeRuoYiAdapter，不复制 portraitApi 的运行时构造。页面拿到的是解析后的 DTO，不接触 R envelope。

- [ ] **步骤 5：运行契约测试**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/discovery src/services/ai-video/tasks
~~~

预期：全部 PASS。

- [ ] **步骤 6：提交**

~~~powershell
git add ai-video-ui/ai-video-webapp/src/services/ai-video/discovery ai-video-ui/ai-video-webapp/src/services/ai-video/tasks
git commit -m "feat(创作端): 定义发现与任务查询契约"
~~~

### 任务 4：Liblib 风格发现首页

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/discovery.module.css
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/components/DiscoveryBanner.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/components/FeaturedTemplateRail.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/components/DiscoveryFilters.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/components/WorkflowTemplateCard.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/components/TemplateWaterfall.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/hooks/useDiscoverySearchParams.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/hooks/useDiscoveryScrollRestore.ts
- 修改：ai-video-ui/ai-video-webapp/tests/setupTests.ts

- [ ] **步骤 1：写页面状态失败测试**

覆盖：缺少 `aivideo:studio:query` 的权限态且不发请求；首屏 skeleton；Banner 局部失败不隐藏 feed；频道切换；搜索回车与清空；筛选无结果；下一页加载/失败重试/末页；模板 ID 去重；卡片跳详情；hover 与 keyboard focus 预览；reduced-motion 不自动播放；预览错误退回封面；返回后恢复 query 和滚动。

~~~tsx
expect(screen.getByRole('heading', { name: '把灵感变成视频' })).toBeVisible();
fireEvent.click(screen.getByRole('tab', { name: '创作灵感' }));
expect(mockListTemplates).toHaveBeenLastCalledWith(
  expect.objectContaining({ channel: 'workflow_inspiration', pageNum: 1 }),
  expect.anything(),
);
~~~

- [ ] **步骤 2：运行测试确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/discovery/index.test.tsx
~~~

预期：FAIL，页面不存在。

- [ ] **步骤 3：按 Ant Design 6 已查询 API 实现**

使用 Input.Search、Carousel、Segmented、Button、Skeleton、Alert、Empty 和 Image；只使用 CLI 确认存在的 props。媒体卡自写语义 link，不把 Card 当交互容器。Carousel arrows/autoplay/dots、Input.Search onSearch、Segmented options/value/onChange 均按 CLI 输出使用。

实现前在本任务内运行 Ant Design CLI 的 `doc/demo/token/semantic` 查询并保存控制台证据，至少覆盖 Carousel、Input、Segmented、Image、Skeleton、Alert、Empty 和 Button；不得把查询推迟到最终 lint。

布局基准：

- 1440×900：复用现有 208px Studio 左侧栏；内容左右各 16px；顶部 44px 搜索；Banner 区 3 列；推荐 rail；频道 tabs；标签横向条；五列 12px 间距信息流。
- 1200px：四列；920px：侧栏变底部导航、三列；640px：单列。
- CSS Modules 作用域，不引入或复制 Studio 的 .sidebar/.main/.card/.btn 等全局类。
- 卡片以 cover 原始宽高计算比例，限定 0.75..1.4；视频只在可见、hover/focus、未 reduced-motion 时 muted playsInline 播放，同时最多一个。

- [ ] **步骤 4：实现 URL 与无限加载**

只解析 channel、categoryCode、tagCodes、keyword、sort；未知值丢弃。筛选变化清旧页回 page 1。IntersectionObserver 只触发 hasNextPage 且非 fetchingNextPage 的请求；无 IntersectionObserver 时显示“加载更多”按钮。

- [ ] **步骤 5：运行页面测试和无障碍断言**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/discovery/index.test.tsx
~~~

预期：全部 PASS；不存在无 accessible name 的按钮或仅 hover 可达操作。

- [ ] **步骤 6：提交**

~~~powershell
git add ai-video-ui/ai-video-webapp/src/pages/discovery ai-video-ui/ai-video-webapp/tests/setupTests.ts
git commit -m "feat(创作端): 实现工作流发现首页"
~~~

### 任务 5：模板详情页

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/templateDetail.module.css
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/components/TemplatePreview.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/components/TemplateUsePanel.tsx

- [ ] **步骤 1：写详情页失败测试**

覆盖 loading、成功、图片/视频预览失败、46501 下架、0 个可用方案、403、普通失败重试。按钮只在有方案和 aivideo:studio:generate 权限时可用；点击进入 /discover/templates/{id}/create。

~~~tsx
expect(screen.getByRole('heading', { name: '一张照片生成自然口播' })).toBeVisible();
fireEvent.click(screen.getByRole('button', { name: '使用此模板' }));
expect(mockHistoryPush).toHaveBeenCalledWith('/discover/templates/tpl-001/create');
~~~

- [ ] **步骤 2：运行确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/discovery/template-detail/index.test.tsx
~~~

- [ ] **步骤 3：实现两栏详情**

左侧预览、案例和说明；右侧 sticky 使用面板展示频道、标签、所需输入摘要、可用供应商数量。当前契约没有公开发布者事实来源，页面不得用 Mock 或前端常量伪造作者。description 按纯文本段落渲染，不使用 dangerouslySetInnerHTML。DOM 不出现供应商地址、workflow/app/node/credential/cost 字段。

- [ ] **步骤 4：运行测试并提交**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/discovery/template-detail/index.test.tsx
git add ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail
git commit -m "feat(创作端): 实现工作流模板详情"
~~~

### 任务 6：动态表单、兼容值与通用素材上传

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/assets/types.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/assets/api.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/assets/api.test.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/assets/signedObjectTransfer.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/assets/signedObjectTransfer.test.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/components/workflowFieldRegistry.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/components/WorkflowDynamicForm.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/components/WorkflowAssetField.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/components/WorkflowDynamicForm.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/hooks/useCompatibleSchemaValues.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/hooks/useCompatibleSchemaValues.test.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/hooks/useWorkflowAssetUpload.ts

- [ ] **步骤 1：写值形状与兼容性失败测试**

表驱动覆盖 11 种控件；integer/decimal 保持规范字符串；boolean 不接受 0/1；multi 去重保序；单文件也输出 [{assetId}]；可选未填省略且不输出 null。未知 schemaVersion/controlType/valueType 组合阻断整表。切方案只保留 semanticKey、valueType 和约束兼容值，清除前返回确认清单。

~~~ts
expect(serializeWorkflowInputs(schema, {
  count: '02',
  enabled: true,
  source: [{ assetId: 'asset-1' }],
})).toEqual({
  ok: false,
  fieldErrors: [{ inputKey: 'count', reasonCode: 'INVALID_INTEGER' }],
});
~~~

- [ ] **步骤 2：写上传失败测试**

断言创建会话上下文含 templateVersionId/executionPlanId/inputKey；create/parts/complete/cancel/status 的精确 mode/status/uploadStatus/assetStatus 响应都严格解析；single PUT 和 multipart parts 都不携带 Authorization/clientid；只使用服务端 requiredHeaders；进度与 abort 可见；只有 assetStatus=ready 返回字段值。未知网络结果沿用原上传幂等键；明确 `46212` 后生成新键重建会话；按 `uploadId + requestGeneration` 丢弃迟到请求，不能覆盖新文件/新方案。缺少 `aivideo:asset:upload` 不创建会话，缺少 `aivideo:asset:download` 不请求 access URL。

- [ ] **步骤 3：运行确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/assets src/pages/discovery/template-create
~~~

- [ ] **步骤 4：实现上传服务**

create/parts/complete/cancel/status/access-url 走 RuoYiAdapter。对象 PUT 使用独立 XMLHttpRequest：

~~~ts
export type SignedTransferRequest = {
  file: Blob;
  method: 'PUT';
  requiredHeaders: Record<string, string>;
  signal?: AbortSignal;
  url: string;
  onProgress?: (loaded: number, total: number) => void;
};
~~~

禁止把签名 URL 或 header 写日志、错误文本或快照。multipart 每批最多请求 20 个 partNumber，按 partNumber 升序完成并保存 ETag；中止时调用 cancel。

- [ ] **步骤 5：实现白名单控件**

text/textarea 使用 Input；integer/decimal 使用 InputNumber stringMode，parser/formatter 不转换成 JS number；boolean 使用 Checkbox；select/multi_select 使用 Select；文件类型使用 Upload.Dragger customRequest。所有组件 props 仅使用 Ant Design CLI 已确认字段。

实现本步骤前在任务内查询 Ant Design CLI `doc/demo/semantic`，至少覆盖 Form、Input、InputNumber、Checkbox、Select 和 Upload；记录实际 `stringMode/customRequest/beforeUpload/fileList` 契约后再写组件。

- [ ] **步骤 6：运行测试并提交**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/assets src/pages/discovery/template-create/components src/pages/discovery/template-create/hooks
git add ai-video-ui/ai-video-webapp/src/services/ai-video/assets ai-video-ui/ai-video-webapp/src/pages/discovery/template-create
git commit -m "feat(创作端): 实现工作流动态输入与上传"
~~~

### 任务 7：供应商选择、制作页与幂等下单

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/types.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/parsers.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/queryKeys.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/api.ts
- 创建：ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/api.test.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/templateCreate.module.css
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/components/ExecutionPlanSelector.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/hooks/useWorkflowOrderDraft.ts

- [ ] **步骤 1：写 API 与页面失败测试**

覆盖直接访问时缺少 `aivideo:studio:query` 或 `aivideo:studio:generate` 均显示权限态、不加载 schema、不创建上传会话且不允许提交；pending/queued 响应；Header Idempotency-Key；body 无 owner/tenant/workspace/provider/workflow/node/billing；用户必须点击 available 方案，不默认选择；paused/invalid 可见但不可选；旧 schema 请求迟到不能覆盖新方案；提交中锁表单与方案；双击只有一次；网络不确定复用原 key；46507 生成新 key 但再次要求确认；46503 不自动换供应商；46505/46506 映射字段；46504 先展示 changed/removed，再确认清除；46502 刷新模板详情和 schema，只保留仍兼容输入/ready 资产，并再次要求用户确认，绝不自动重提。

~~~ts
expect(requests[0].options).toEqual(expect.objectContaining({
  method: 'POST',
  headers: { 'Idempotency-Key': idempotencyKey },
}));
expect(requests[0].options.data).not.toHaveProperty('providerConfigId');
~~~

- [ ] **步骤 2：运行确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/workflow-orders src/pages/discovery/template-create/index.test.tsx
~~~

- [ ] **步骤 3：实现制作页状态机**

页面顺序固定：模板摘要 → 供应商方案卡 → 动态表单 → 提交确认。方案卡显示 providerDisplayName/providerKind/特性/预计时长/运行状态，不显示服务器、凭据、ID 映射或成本。契约没有 recommended 字段，首项和排序都不产生推荐徽标或默认选择。

每次进入页面生成 128 位随机幂等键；只有明确 46507 冲突且用户再次确认才换键。网络/超时保持原 key。创建成功后 history.push('/orders/' + orderId)。

- [ ] **步骤 4：运行测试并提交**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/services/ai-video/workflow-orders src/pages/discovery/template-create
git add ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders ai-video-ui/ai-video-webapp/src/pages/discovery/template-create
git commit -m "feat(创作端): 实现多供应商模板制作页"
~~~

### 任务 8：订单详情轮询、取消和任务中心

**文件：**

- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/orderDetail.module.css
- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/components/OrderTaskStatus.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/components/OrderResultAssets.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/hooks/useWorkflowOrderPolling.ts
- 创建：ai-video-ui/ai-video-webapp/src/pages/tasks/index.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/tasks/index.test.tsx
- 创建：ai-video-ui/ai-video-webapp/src/pages/tasks/tasks.module.css

- [ ] **步骤 1：写订单轮询失败测试**

覆盖缺少 `aivideo:task:query` 的权限态且不发请求；pending/queued/running/success/failed/cancelled；非终态 3 秒轮询，终态停止；unmount/orderId 变化取消；轮询失败提示“任务可能仍在运行”且不本地判失败；confirming_provider_acceptance 文案稳定且 canCancel=false；processing_results 仍 running；无可信 progress 不渲染百分比。

- [ ] **步骤 2：写结果与操作失败测试**

成功状态必须展示恰好一个 ready primary 和辅助输出；access URL 过期重新签发并清旧 URL，缺少 `aivideo:asset:download` 显示权限态且不请求；cancel 仅 canCancel 且有 aivideo:task:cancel 时出现，二次确认后调用；46509 刷新，不本地强改 cancelled；“再次制作”只在最新详情 canRemake=true 时出现并进入模板 create 页面，46518 只决定错误文案。任务中心可展示 workflow_template_generate 之外的合法 taskType；只有白名单 detailTarget 才进入订单，不能按 resourceType/resourceId 拼任意 URL。

- [ ] **步骤 3：运行确认红灯**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/workflow-orders src/pages/tasks
~~~

- [ ] **步骤 4：实现订单和任务页面**

OrderTaskStatus 只用前端稳定字典映射 stage，并拒绝违反 `status × stage` 矩阵的响应；failureMessage 仅显示后端稳定用户文案。Progress 只在 progressPercent !== undefined 时渲染。任务中心 query key 必含 userId/workspaceId，使用分页列表，筛选只允许 AiTaskStatus，权限、空态与失败态独立。

- [ ] **步骤 5：运行测试并提交**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run src/pages/workflow-orders src/pages/tasks
git add ai-video-ui/ai-video-webapp/src/pages/workflow-orders ai-video-ui/ai-video-webapp/src/pages/tasks
git commit -m "feat(创作端): 实现工作流订单与任务中心"
~~~

### 任务 9：确定性 Mock、原创素材、国际化与总体验收

**文件：**

- 创建：ai-video-ui/ai-video-webapp/mock/discovery.ts
- 创建：ai-video-ui/ai-video-webapp/mock/workflowOrders.ts
- 创建：ai-video-ui/ai-video-webapp/mock/tasks.ts
- 创建：ai-video-ui/ai-video-webapp/mock/workflowAssets.ts
- 创建：ai-video-ui/ai-video-webapp/public/discovery/skincare.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/food-commercial.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/neon-fashion.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/travel-aerial.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/clay-story.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/future-tech.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/modern-interior.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/ink-story.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/unboxing.webp
- 创建：ai-video-ui/ai-video-webapp/public/discovery/presenter-studio.webp
- 修改：ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts
- 修改：ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts
- 修改：ai-video-pages.md

- [ ] **步骤 1：实现确定性 Mock**

所有 handler 返回精确 {code,msg,data}。固定 ID 和时间，不使用 Math.random。覆盖 query 参数 scene：

- scene=empty：rows=[]。
- scene=forbidden：code=403。
- scene=failed：code=500。
- scene=schema-mismatch：code=46504 和 changed/removed keys。
- 订单 mock 按固定计数 pending → queued → running → processing_results → success；计数只存在开发进程内，不进入生产代码。
- 上传 mock 返回同源 PUT URL，raw PUT 不返回 R envelope，complete 返回 ready asset。

- [ ] **步骤 2：核对公共契约并登记页面**

逐项核对任务 0 已冻结的 `docs/API_CONTRACT.md`，禁止让 Mock 或前端类型成为新的事实源；ai-video-pages.md 登记 /tasks 和五个用户路由。若实现发现契约缺口，先回到任务 0 更新并复审，再改前端。运行开发标准验证。

- [ ] **步骤 3：运行 Ant Design 检查**

~~~powershell
$env:NO_UPDATE_CHECK='1'
& $Node node_modules/@ant-design/cli/dist/index.js lint src/pages/discovery --format json
& $Node node_modules/@ant-design/cli/dist/index.js lint src/pages/workflow-orders --format json
& $Node node_modules/@ant-design/cli/dist/index.js lint src/pages/tasks --format json
~~~

预期：无 deprecated/a11y/performance error。

- [ ] **步骤 4：运行完整前端验证**

~~~powershell
& $Node node_modules/vitest/vitest.mjs run
& $Node node_modules/typescript/bin/tsc --noEmit --declaration false
& $Node node_modules/@umijs/max/bin/max.js build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ../../scripts/validate-development-standards.ps1
git diff --check
~~~

预期：测试 0 failure；tsc 退出 0；build 退出 0；DEVELOPMENT_STANDARDS_OK；无 whitespace error。

- [ ] **步骤 5：本地视觉验收**

使用 APP_AUTH_CLIENT_ID 的开发配置启动 npm start；浏览器固定 1440×900，把实现截图保存到任务 0 的本机 evidence 路径，并与已归档参考图对比；结束后重置临时 viewport。验证：

- / 和 /studio 行为不变。
- /discover 复用现有 208px Studio 侧栏；仅右侧搜索、三列 Banner、推荐 rail、双频道、筛选和五列信息流与参考站同构。
- 卡片不使用 Liblib 品牌资产；原创封面无文字、Logo、水印。
- 详情 → 制作 → 选供应商 → 填表/上传 → 下单 → 订单成功 → /tasks 的 Mock 链路可操作。
- 920px 与 640px 不横向溢出；键盘可完成主要路径；reduced-motion 不自动播放。

- [ ] **步骤 6：两阶段审查**

先由规格审查者逐项对照规格第 7、8、14、15、18、20、21 节；修完所有缺口后，再由代码质量审查者检查安全、竞态、可访问性、性能和测试质量。任何 Important 问题修复并复审后才完成。

- [ ] **步骤 7：提交**

~~~powershell
git add ai-video-ui/ai-video-webapp/mock ai-video-ui/ai-video-webapp/public/discovery ai-video-ui/ai-video-webapp/src/locales docs/API_CONTRACT.md ai-video-pages.md docs/superpowers
git commit -m "feat(创作端): 完成发现模块用户端页面"
~~~

## 自检

- 规格覆盖：发现、详情、方案选择、动态表单、上传、幂等、订单、任务中心、加载/空/失败/权限/分页均有对应任务。
- 任务完整性：每一步都有精确文件、行为、命令和预期；后端和运营端明确不在本计划范围。
- 类型一致：templateId/templateVersionId/executionPlanId/schemaHash、AiTaskStatus/AiTaskStage、assetId 和 Idempotency-Key 名称跨任务一致。
- 边界一致：页面不拼 URL/Headers；签名 PUT 不走 RuoYiAdapter；任务中心不伪装成订单列表；再次制作不修改旧订单；推荐供应商不默认选中。
- 视觉一致：复刻模块关系、密度和交互，不复制 Liblib 品牌与图片资产。
