# 发现侧栏入口实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在现有用户端侧栏增加独立“探索 / 发现”入口，并让发现、Studio、任务和订单页面保持互斥且正确的菜单高亮。

**架构：** `StudioRoute` 继续只描述 Studio 的五个内容区；`StudioSider` 增加独立 UI 导航键 `discover`、单一 active key 和 `onDiscover` 回调。Studio 页面与共享 `CreatorWorkspaceShell` 分别负责实际站内导航，侧栏本身不依赖路由对象或业务接口。

**技术栈：** React 19、TypeScript、Umi history、Ant Design Icons、Vitest、Testing Library、Biome。

---

## 规格与范围

- 规格：`docs/superpowers/specs/2026-08-05-discovery-sidebar-entry-design.md`
- 风险：黄色中风险；改变用户可见导航，但不命中认证、权限、数据、文件、积分、外部供应商或公共接口等红色触发条件。
- 不修改：后端、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、API service、Mock 数据和 `StudioRoute` 枚举。
- 协作：一名实施者顺序修改共享文件；完成后由一名非实施者做一次独立审查。不得并行写同一侧栏或测试文件。
- 交付输出：完成项、剩余风险、验证证据、阻塞项。

## 文件结构

- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`——定义 UI 导航键，渲染“探索 / 发现”和“我的”两组菜单，并保证单一当前项。
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.test.tsx`——覆盖分组顺序、发现点击、折叠标题和互斥高亮。
- 修改：`ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.tsx`——把发现上下文映射为侧栏 active key，并处理发现入口跳转。
- 修改：`ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.test.tsx`——覆盖发现页高亮、任务页无高亮、原五项跳转和布局回归。
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`——Studio 调用点传递 active key，并处理进入 `/discover`。
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`——更新侧栏 prop 断言，并覆盖 Studio 点击发现后的站内导航。
- 不修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`——现有 `.nav-group-label`、折叠和移动端规则已支持第二个分组；只有浏览器验证证明布局回归时才回到规格评审，不在本计划预置样式重构。

### 任务 1：以 TDD 增加“探索 / 发现”侧栏契约

**任务卡：** 单一目标是让共享侧栏展示发现入口并正确导航；允许修改上方列出的六个前端文件；不扩展任务中心或订单菜单。验收包括成功跳转、互斥高亮、无用户门禁回归和原五项不变。实施者一名，独立审查者一名，最大并发两名。

**文件：**

- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`

- [ ] **步骤 1：先为 `StudioSider` 编写失败测试**

把 Testing Library import 增加为 `import { fireEvent, render, screen, within } from '@testing-library/react';`。让测试夹具接收 `activeKey`，暴露 `onDiscover`，把文件内所有 `route="create"` 改为 `activeKey="create"`，为每个直接渲染补齐 `onDiscover`，并新增以下行为断言：

```tsx
const onDiscover = vi.fn();
render(
  <StudioSider
    activeKey="discover"
    collapsed={false}
    currentUser={user}
    quotaState={{ status: 'loading' }}
    onCollapsedChange={vi.fn()}
    onDiscover={onDiscover}
    onRetryQuota={vi.fn()}
    onRouteChange={vi.fn()}
  />,
);

const navigation = screen.getByRole('navigation');
expect(within(navigation).getAllByText(/探索|我的/).map((node) => node.textContent))
  .toEqual(['探索', '我的']);
expect(within(navigation).getAllByRole('button').map((button) => button.textContent))
  .toEqual(['发现', '创作', '形象', '声音', '文案', '作品']);
expect(screen.getByRole('button', { name: /发现$/ }))
  .toHaveAttribute('aria-current', 'page');
expect(screen.getByRole('button', { name: /创作$/ }))
  .not.toHaveAttribute('aria-current');

fireEvent.click(screen.getByRole('button', { name: /发现$/ }));
expect(onDiscover).toHaveBeenCalledTimes(1);
```

再以 `collapsed={true}` 渲染，断言发现按钮拥有 `title="发现"`。原积分和用户信息测试继续保留。

- [ ] **步骤 2：为两个父组件编写失败的集成测试**

在 `CreatorWorkspaceShell/index.test.tsx` 中把发现页期望更新为六个按钮，断言只有发现项带当前页语义；另渲染 `activeKey="tasks"`，断言六个按钮均无 `aria-current`：

```tsx
expect(menuButtons.map((button) => button.textContent)).toEqual([
  '发现',
  '创作',
  '形象',
  '声音',
  '文案',
  '作品',
]);
expect(screen.getByRole('button', { name: /发现$/ }))
  .toHaveAttribute('aria-current', 'page');
```

在 `digital-human-studio/index.test.tsx` 的 history mock 中增加 `push`，捕获 `StudioSider` 的 `onDiscover`，并断言：

```tsx
const siderProps = mockStudioSider.mock.lastCall?.[0] as {
  activeKey: string;
  onDiscover: () => void;
};

expect(siderProps.activeKey).toBe('scripts');
act(() => siderProps.onDiscover());
expect(mockHistoryPush).toHaveBeenCalledWith('/discover');
```

把现有 `expect.objectContaining({ route: 'voices' })` 等侧栏 prop 断言改为 `activeKey`，但不得改变 `StudioState.route` 或 `?view=` 测试。

- [ ] **步骤 3：运行定向测试，确认红灯由缺少新契约造成**

运行：

```powershell
& 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' node_modules\vitest\vitest.mjs run src/pages/digital-human-studio/components/StudioSider.test.tsx src/components/CreatorWorkspaceShell/index.test.tsx src/pages/digital-human-studio/index.test.tsx
```

工作目录：`ai-video-ui/ai-video-webapp`

预期：FAIL；错误来自 `activeKey`、`onDiscover` 尚未实现，或找不到“探索 / 发现”，而不是测试环境或认证夹具失败。

- [ ] **步骤 4：实现最小侧栏 UI 契约**

在 `StudioSider.tsx` 中定义并使用单一 active key：

```tsx
export type StudioSidebarKey = StudioRoute | 'discover';

interface StudioSiderProps {
  activeKey?: StudioSidebarKey;
  collapsed: boolean;
  currentUser: AuthUser;
  quotaState: PersonalQuotaQueryState;
  onCollapsedChange: (value: boolean) => void;
  onDiscover: () => void;
  onRetryQuota: () => void;
  onRouteChange: (route: StudioRoute) => void;
}
```

在原“我的”分组前加入：

```tsx
<div className="nav-group-label">探索</div>
<button
  aria-current={activeKey === 'discover' ? 'page' : undefined}
  className={`nav-item ${activeKey === 'discover' ? 'active' : ''}`}
  title={collapsed ? '发现' : undefined}
  type="button"
  onClick={onDiscover}
>
  <StudioIcon name="app" />
  <span>发现</span>
</button>
<div className="nav-group-label">我的</div>
```

原五项继续遍历 `StudioRoute` 数组，只把判断由 `route === item.key` 改为 `activeKey === item.key`。不要把 `discover` 加入 `StudioRoute` 或原五项数组。

- [ ] **步骤 5：连接共享壳与 Studio 页面**

`CreatorWorkspaceShell` 传入发现上下文并复用 Umi history：

```tsx
<StudioSider
  activeKey={activeKey === 'discover' ? 'discover' : undefined}
  collapsed={collapsed}
  currentUser={currentUser}
  quotaState={quotaQuery.state}
  onDiscover={() => history.push('/discover')}
  onRouteChange={navigateToStudio}
  // 其余现有 props 不变
/>
```

Studio 页面保持 `state.route` 为业务状态，只改调用点：

```tsx
<StudioSider
  activeKey={state.route}
  collapsed={collapsed}
  currentUser={currentUser}
  quotaState={quotaQuery.state}
  onDiscover={() => history.push('/discover')}
  onRouteChange={switchRoute}
  // 其余现有 props 不变
/>
```

- [ ] **步骤 6：运行定向测试确认绿灯**

重复步骤 3 的命令。

预期：三个测试文件全部 PASS；发现页只高亮发现，Studio 只高亮当前 `StudioRoute`，任务和订单上下文无当前项，原 `?view=` 测试保持通过。

- [ ] **步骤 7：运行类型与相关静态检查**

运行：

```powershell
& 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' node_modules\typescript\bin\tsc --noEmit --declaration false
& node_modules\@biomejs\cli-win32-x64\biome.exe lint src/pages/digital-human-studio/components/StudioSider.tsx src/pages/digital-human-studio/components/StudioSider.test.tsx src/components/CreatorWorkspaceShell/index.tsx src/components/CreatorWorkspaceShell/index.test.tsx src/pages/digital-human-studio/index.tsx src/pages/digital-human-studio/index.test.tsx --max-diagnostics=200
```

预期：两个命令退出码均为 0。

- [ ] **步骤 8：提交实现**

仅暂存六个实现与测试文件，不得暂存 `config/config.local.ts`：

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.test.tsx ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.tsx ai-video-ui/ai-video-webapp/src/components/CreatorWorkspaceShell/index.test.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx
git commit -m "feat(创作端): 增加发现侧栏入口"
```

### 任务 2：完整验证、浏览器验收与独立审查

**任务卡：** 只验证任务 1 的差异和直接受影响路径；不得新增菜单、重构样式或扩展后端。实施者负责验证，非实施者只读审查。若审查无运行时代码、契约或验收变化，不发起第二轮完整审查。

**文件：**

- 验证：任务 1 修改的六个文件。
- 对照：`docs/superpowers/specs/2026-08-05-discovery-sidebar-entry-design.md`
- 验证：`ai-video-ui/ai-video-webapp/src/pages/discovery/**`
- 验证：`ai-video-ui/ai-video-webapp/src/pages/tasks/index.tsx`
- 验证：`ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/index.tsx`

- [ ] **步骤 1：运行完整前端测试与 TypeScript**

```powershell
& 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' node_modules\vitest\vitest.mjs run
& 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' node_modules\typescript\bin\tsc --noEmit --declaration false
```

工作目录：`ai-video-ui/ai-video-webapp`

预期：29 个既有测试文件与新增用例全部 PASS，TypeScript 退出码为 0。不得把既有生产构建问题描述为本任务已验证通过。

- [ ] **步骤 2：运行规范与差异检查**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-development-standards.ps1
git diff --check
git status --short
```

工作目录：仓库根目录。

预期：输出 `DEVELOPMENT_STANDARDS_OK`；差异无空白错误；`config/config.local.ts` 继续保持未跟踪且不进入暂存区或提交。

- [ ] **步骤 3：在本地浏览器验证关键路径**

使用已启动的 `http://127.0.0.1:8003`：

1. 打开 `/studio?view=scripts`，确认侧栏顺序为“探索 / 发现 / 我的 / 原五项”，只有“文案”高亮。
2. 点击“发现”，确认地址变为 `/discover`，右侧发现内容不变且只有“发现”高亮。
3. 进入一个 `/discover/templates/:templateId` 和对应 create 页面，确认“发现”持续高亮。
4. 打开 `/tasks` 与一个 `/orders/:orderId`，确认六个菜单均无当前项。
5. 折叠侧栏，确认发现图标仍显示且按钮标题为“发现”；在桌面当前视口和现有移动端断点各检查一次菜单未溢出。

- [ ] **步骤 4：请求一次独立代码审查**

审查范围固定为任务 1 的提交相对其父提交，检查：

- 是否误把 `discover` 加入 `StudioRoute`。
- 是否可能同时高亮发现和 Studio 菜单。
- 是否改变原五项名称、顺序、折叠、积分或用户区。
- 测试是否验证真实组件行为和父组件导航，而不是只断言 mock 存在。
- `config/config.local.ts` 是否被排除。

审查输出使用 `[必须修复]`、`[建议修改]`、`[仅供参考]`；只有本次差异直接引入的阻断问题进入修复。

- [ ] **步骤 5：修复阻断项后只做定向复核**

若存在 `[必须修复]`，只修改直接相关文件，重复任务 1 的定向测试、任务 2 的完整测试和一次差异复核。同一问题自动返工最多两次。没有 `[必须修复]` 时不产生额外提交。

- [ ] **步骤 6：交付状态**

报告：提交 hash、分支、侧栏最终顺序、测试/类型/规范/浏览器证据、未验证项，以及本机 `config.local.ts` 未提交的状态。保留工作树，等待用户选择本地合并、创建 PR、保持分支或丢弃。
