# 首页工作台与菜单路由骨架实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将用户端 Web 首页改造成与参考图内容区一比一一致的 AI 视频工作台，并建立 PRD 菜单内容、路由与可访问页面骨架。

**架构：** 保留现有 Ant Design Pro / ProLayout 外壳，不改菜单样式。新增 `home` 首页模块承载工作台 UI、mock 数据、状态分支、视觉资源与点击跳转；新增 `placeholders` 页面模块承接所有已确认业务路由，避免 404。视觉资源从参考图本地裁切，组件内只使用集中导出的资源 URL 与集中 mock 数据。

**技术栈：** React 19、TypeScript、Umi Max、Ant Design 6、Ant Design Pro / ProComponents、antd-style、Vitest、Testing Library、happy-dom、PowerShell System.Drawing、in-app browser。

---

## 输入与边界

- 规格文件：`docs/superpowers/specs/2026-07-04-home-dashboard-design.md`
- 参考图：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 12_46_13.png`
- 实施目录：`ai-video-ui/ai-video-webapp`
- 首页入口：`/dashboard`
- 根路径：`/` 重定向到 `/dashboard`
- 菜单样式：保持 ProLayout 当前样式，只调整菜单内容和路由。
- 首页内容区：以参考图 1680 x 946 画面为视觉基准，模块顺序、数量、文案、数字、日期、卡片密度、右栏结构必须对齐。
- 不新增后端接口，不修改 Electron 壳层，不删除现有非中英文语言包。
- 当前工作区可能已有无关未提交文件，所有提交命令必须显式列出本任务文件。

## 文件结构

### 文档

- 修改：`ai-video-pages.md`
  - 职责：把“积分使用情况”为侧边栏/框架区域信息写清楚，首页内容区不再实现积分卡片，避免 PRD 与已确认规格冲突。

### 路由与布局

- 修改：`ai-video-ui/ai-video-webapp/config/routes.ts`
  - 职责：替换模板路由，新增 `/dashboard`、视频创作、草稿箱、模板中心、数字人、克隆声音、素材管理、任务中心、帮助、通知、账号路由。
- 修改：`ai-video-ui/ai-video-webapp/config/defaultSettings.ts`
  - 职责：产品名改为 `AI 视频工作台`，保持 ProLayout 当前布局能力。
- 修改：`ai-video-ui/ai-video-webapp/src/app.tsx`
  - 职责：清理开发菜单可见链接，登录后仍按 redirect 返回，默认入口变成 `/dashboard`。

### 首页模块

- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/data.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/assets.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/style.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/QuickStartCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/MetricCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/ProjectCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/NewProjectCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/TaskSummary.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/SidePanel.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/NoticeList.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/InspirationCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/AssetShortcuts.tsx`
  - 职责：用小而专注的文件拆分首页数据、状态、样式和区域组件。

### 业务路由页面骨架

- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/ModulePlaceholder.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/VideoCreate.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Drafts.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Templates.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/DigitalHumanImage.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/DigitalHumanVideo.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/VoiceClone.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Assets.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Tasks.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Help.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Notifications.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/Account.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/AccountSettings.tsx`
  - 职责：每个已确认入口都有页面响应，页面显示模块名、模块说明、加载/空/失败/权限状态展示区。

### 资源

- 创建：`ai-video-ui/ai-video-webapp/scripts/extract-home-assets.ps1`
- 创建：`ai-video-ui/ai-video-webapp/public/home-assets/manifest.json`
  - 职责：从参考图裁切首页所需视觉素材并记录来源坐标。

### 国际化

- 修改：`ai-video-ui/ai-video-webapp/src/locales/zh-CN/menu.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/en-US/menu.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts`
  - 职责：补齐新菜单、首页、页面骨架文案 key；其他语言包保持当前状态。

### 测试

- 创建：`ai-video-ui/ai-video-webapp/src/routes.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/data.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/ModulePlaceholder.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/locales/home-i18n.test.ts`
  - 职责：覆盖路由、mock 数据、状态分支、跳转目标、页面骨架和中英文文案。

---

## 任务 1：同步 PRD 中积分位置

**文件：**
- 修改：`ai-video-pages.md`

- [ ] **步骤 1：定位首页与积分描述**

运行：

```powershell
rg -n "首页|积分|工作台|使用情况" ai-video-pages.md
```

预期：输出首页工作台、积分使用情况相关段落。

- [ ] **步骤 2：修改 PRD 描述**

把首页工作台范围写成：

```markdown
首页工作台内容区以“欢迎区、引导卡片、创作数据、快速开始、最近项目、系统公告、创作灵感、任务概览、常用素材”为验收范围。
“积分使用情况”属于 ProLayout 侧边栏/框架区域信息，本次首页内容区不实现该卡片；如侧边栏后续统一改造，再按全局布局任务处理。
```

- [ ] **步骤 3：验证 PRD 不再冲突**

运行：

```powershell
rg -n "积分使用情况|侧边栏|首页工作台内容区" ai-video-pages.md
```

预期：能看到积分归属侧边栏/框架区域，首页内容区范围不包含积分卡片。

- [ ] **步骤 4：提交文档变更**

```powershell
git add -- ai-video-pages.md
git commit -m "docs(home): 明确首页工作台实现要点范围"
```

---

## 任务 2：路由与菜单内容

**文件：**
- 测试：`ai-video-ui/ai-video-webapp/src/routes.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/config/routes.ts`
- 修改：`ai-video-ui/ai-video-webapp/config/defaultSettings.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/components/RightContent/AvatarDropdown.tsx`

- [ ] **步骤 1：写失败测试**

创建 `ai-video-ui/ai-video-webapp/src/routes.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import routes from '../config/routes';
import defaultSettings from '../config/defaultSettings';

type RouteRecord = {
  path?: string;
  name?: string;
  redirect?: string;
  component?: string;
  routes?: RouteRecord[];
};

const flattenRoutes = (items: RouteRecord[]): RouteRecord[] =>
  items.flatMap((item) => [item, ...flattenRoutes(item.routes ?? [])]);

describe('AI video app routes', () => {
  const flat = flattenRoutes(routes as RouteRecord[]);

  it('redirects root to dashboard', () => {
    expect(flat.find((route) => route.path === '/')?.redirect).toBe('/dashboard');
  });

  it('contains every confirmed business route', () => {
    expect(flat.map((route) => route.path)).toEqual(
      expect.arrayContaining([
        '/dashboard',
        '/video/create/industry',
        '/drafts',
        '/templates',
        '/digital-human/image',
        '/digital-human/video',
        '/voice-clone',
        '/assets',
        '/tasks',
        '/help',
        '/notifications',
        '/account',
        '/account/settings',
      ]),
    );
  });

  it('keeps confirmed menu groups', () => {
    const videoGroup = routes.find((route) => route.name === 'video') as RouteRecord | undefined;
    const digitalHumanGroup = routes.find((route) => route.name === 'digitalHuman') as RouteRecord | undefined;
    const systemGroup = routes.find((route) => route.name === 'system') as RouteRecord | undefined;

    expect(videoGroup?.routes?.map((route) => route.path)).toEqual([
      '/video/create/industry',
      '/drafts',
      '/templates',
    ]);
    expect(digitalHumanGroup?.routes?.map((route) => route.path)).toEqual([
      '/digital-human/image',
      '/digital-human/video',
      '/voice-clone',
    ]);
    expect(systemGroup?.routes?.map((route) => route.path)).toEqual(['/tasks']);
  });

  it('removes every template visible entry', () => {
    const templatePaths = ['/welcome', '/admin', '/admin/sub-page', '/list'];
    for (const templatePath of templatePaths) {
      expect(flat.find((route) => route.path === templatePath)).toBeUndefined();
    }
  });

  it('uses the AI video product title', () => {
    expect(defaultSettings.title).toBe('AI 视频工作台');
  });
});
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/routes.test.ts
```

预期：FAIL，原因包含 `/dashboard` 路由不存在或 `defaultSettings.title` 仍是模板标题。

- [ ] **步骤 3：替换路由配置**

把 `ai-video-ui/ai-video-webapp/config/routes.ts` 改成：

```ts
export default [
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: 'login',
        path: '/user/login',
        component: './user/login',
      },
    ],
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    icon: 'HomeOutlined',
    component: './home',
  },
  {
    name: 'video',
    icon: 'VideoCameraOutlined',
    routes: [
      {
        path: '/video/create/industry',
        name: 'videoCreate',
        component: './placeholders/VideoCreate',
      },
      {
        path: '/drafts',
        name: 'drafts',
        component: './placeholders/Drafts',
      },
      {
        path: '/templates',
        name: 'templates',
        component: './placeholders/Templates',
      },
    ],
  },
  {
    name: 'digitalHuman',
    icon: 'UserOutlined',
    routes: [
      {
        path: '/digital-human/image',
        name: 'digitalHumanImage',
        component: './placeholders/DigitalHumanImage',
      },
      {
        path: '/digital-human/video',
        name: 'digitalHumanVideo',
        component: './placeholders/DigitalHumanVideo',
      },
      {
        path: '/voice-clone',
        name: 'voiceClone',
        component: './placeholders/VoiceClone',
      },
    ],
  },
  {
    path: '/assets',
    name: 'assets',
    icon: 'FolderOutlined',
    component: './placeholders/Assets',
  },
  {
    name: 'system',
    icon: 'CheckSquareOutlined',
    routes: [
      {
        path: '/tasks',
        name: 'tasks',
        component: './placeholders/Tasks',
      },
    ],
  },
  {
    path: '/help',
    name: 'help',
    hideInMenu: true,
    component: './placeholders/Help',
  },
  {
    path: '/notifications',
    name: 'notifications',
    hideInMenu: true,
    component: './placeholders/Notifications',
  },
  {
    path: '/account',
    name: 'account',
    hideInMenu: true,
    component: './placeholders/Account',
  },
  {
    path: '/account/settings',
    name: 'accountSettings',
    hideInMenu: true,
    component: './placeholders/AccountSettings',
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    component: './exception/404',
    layout: false,
    path: '/*',
  },
];
```

- [ ] **步骤 4：更新布局标题与开发链接**

在 `config/defaultSettings.ts` 中确保：

```ts
title: 'AI 视频工作台',
```

在 `src/app.tsx` 中删除 `LinkOutlined`、`DocLink`、`VersionDropdown`、`LangDropdown` 和 OpenAPI 开发链接的可见输出。顶部动作区直接提供帮助和通知入口：

```tsx
actionsRender: () => [
  <Link key="help" to="/help" prefetch>
    帮助
  </Link>,
  <Link key="notifications" to="/notifications" prefetch>
    通知
  </Link>,
],
```

**清空 ProLayout 背景装饰图**：删除现有 `bgLayoutImgList` 配置（含 3 张 alipay CDN 背景图），改为 `bgLayoutImgList: []`，避免背景图干扰主内容区视觉验收。

```tsx
bgLayoutImgList: [],
```

**logo 和 avatarProps.title 调整**：

- `defaultSettings.logo` 改为 `'/home-assets/logo-workbench.png'`（依赖任务 5 先裁出该资产；若任务 5 尚未完成，先用空字符串 `''` 占位，任务 5 完成后回填）。规格资产清单（[第 345 行](file:///d:/Workspace/ai/projects/ai-video/docs/superpowers/specs/2026-07-04-home-dashboard-design.md#L345)）已含 `logo-workbench.png`，任务 5 裁切清单需补该项。
- `avatarProps.title` 由硬编码 `'ProUser'` 改为读取当前用户名：

```tsx
avatarProps: {
  src: initialState?.currentUser?.avatar,
  title: initialState?.currentUser?.name ?? '',
  render: (_, avatarChildren) => (
    <AvatarDropdown>{avatarChildren}</AvatarDropdown>
  ),
},
```

头像区域保留现有 `AvatarDropdown`，**采用增量修改**（不重写整个文件），改动点：

1. **import 补充**：在现有 `@ant-design/icons` import 中增加 `UserOutlined`（现有只有 `LogoutOutlined, SettingOutlined, SkinOutlined`）。其余 import（`history, useModel` from `@umijs/max`、`MenuProps, Spin` from `antd`、`React, startTransition` from `react`、`outLogin` from `@/services/ant-design-pro/api`、`HeaderDropdown`）保持不动。

2. **菜单项调整**：在现有 `menuItems` 数组中，**在 `settings` 项之前插入 `account` 项**：

```tsx
const menuItems: MenuProps['items'] = [
  {
    key: 'account',
    icon: <UserOutlined />,
    label: '账号中心',
  },
  // 保留现有 settings / divider / logout 四项不动
  {
    key: 'settings',
    icon: <SettingOutlined />,
    label: '个人设置',
  },
  // ...
];
```

3. **onMenuClick 跳转分支调整**：现有逻辑 `history.push(`/account/${key}`)` 对所有非 logout/theme 的 key 统一跳 `/account/${key}`。改为显式映射，避免新增 `account` 项错误地跳到 `/account/account`：

```tsx
const avatarRoutes: Record<string, string> = {
  account: '/account',
  settings: '/account/settings',
};

const onMenuClick: MenuProps['onClick'] = (event) => {
  const { key } = event;
  if (key === 'logout') {
    startTransition(() => {
      setInitialState((s) => ({ ...s, currentUser: undefined }));
    });
    loginOut();
    return;
  }
  if (key === 'theme') {
    setInitialState((s) => ({ ...s, settingDrawerOpen: true }));
    return;
  }
  const route = avatarRoutes[key];
  if (route) {
    history.push(route);
  }
};
```

4. **保留不动**：现有 `loginOut` 函数（[AvatarDropdown.tsx:38-56](file:///d:/Workspace/ai/projects/ai-video/ai-video-ui/ai-video-webapp/src/components/RightContent/AvatarDropdown.tsx#L38-L56)）、`outLogin` 调用、redirect 处理、HeaderDropdown、Spin 渲染分支——全部不动。

保留 `OfflineBanner`、`ErrorBoundary`、`SettingDrawer` 和登录拦截。

- [ ] **步骤 5：运行路由测试通过**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/routes.test.ts
```

预期：PASS。

- [ ] **步骤 6：提交路由变更**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/routes.test.ts ai-video-ui/ai-video-webapp/config/routes.ts ai-video-ui/ai-video-webapp/config/defaultSettings.ts ai-video-ui/ai-video-webapp/src/app.tsx ai-video-ui/ai-video-webapp/src/components/RightContent/AvatarDropdown.tsx
git commit -m "feat(routes): 新增 AI 视频工作台菜单与路由骨架"
```

---

## 任务 3：业务页面骨架

**文件：**
- 测试：`ai-video-ui/ai-video-webapp/src/pages/placeholders/ModulePlaceholder.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/ModulePlaceholder.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/*.tsx`

- [ ] **步骤 1：写失败测试**

创建 `ModulePlaceholder.test.tsx`：

```tsx
import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ModulePlaceholder } from './ModulePlaceholder';

describe('ModulePlaceholder', () => {
  it('renders single static page with module name and description', () => {
    render(
      <ModulePlaceholder
        title="视频创作"
        description="从脚本、素材和数字人开始创建行业视频。"
      />,
    );

    expect(screen.getByRole('heading', { name: '视频创作' })).toBeInTheDocument();
    expect(screen.getByText('从脚本、素材和数字人开始创建行业视频。')).toBeInTheDocument();
    expect(screen.getByText('建设中', { exact: false })).toBeInTheDocument();
  });

  it('renders different content for different module props', () => {
    const { rerender } = render(
      <ModulePlaceholder title="草稿箱" description="管理未发布和未完成的视频创作草稿。" />,
    );
    expect(screen.getByRole('heading', { name: '草稿箱' })).toBeInTheDocument();

    rerender(
      <ModulePlaceholder title="任务中心" description="查看生成任务队列、进度和结果。" />,
    );
    expect(screen.getByRole('heading', { name: '任务中心' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '草稿箱' })).not.toBeInTheDocument();
  });
});
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/placeholders/ModulePlaceholder.test.tsx
```

预期：FAIL，原因是 `ModulePlaceholder` 不存在。

- [ ] **步骤 3：实现单一静态占位页**

创建 `ModulePlaceholder.tsx`（单一静态页，显示模块名 + 说明 + "建设中"提示；4 种状态的条件渲染留到真实业务页实现时按 AGENTS.md 要求做）：

```tsx
import { Empty, Space, Typography } from 'antd';

const { Paragraph, Title } = Typography;

type ModulePlaceholderProps = {
  title: string;
  description: string;
};

export function ModulePlaceholder({ title, description }: ModulePlaceholderProps) {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%', padding: '24px' }}>
      <div>
        <Title level={3}>{title}</Title>
        <Paragraph type="secondary">{description}</Paragraph>
      </div>
      <Empty description={`${title} · 建设中`} />
    </Space>
  );
}
```

- [ ] **步骤 4：创建所有页面入口**

每个页面文件只导出一个默认组件，使用下列文案：

```tsx
import { ModulePlaceholder } from './ModulePlaceholder';

export default function VideoCreate() {
  return (
    <ModulePlaceholder
      title="视频创作"
      description="从脚本、素材和数字人开始创建行业视频。"
    />
  );
}
```

其余文件对应：

| 文件 | title | description |
| --- | --- | --- |
| `Drafts.tsx` | 草稿箱 | 管理未发布和未完成的视频创作草稿。 |
| `Templates.tsx` | 模板中心 | 浏览、筛选和使用视频模板。 |
| `DigitalHumanImage.tsx` | 图生数字人 | 上传图片并生成数字人视频。 |
| `DigitalHumanVideo.tsx` | 视频数字人 | 上传视频并生成口播数字人内容。 |
| `VoiceClone.tsx` | 克隆声音 | 管理声音样本并生成专属音色。 |
| `Assets.tsx` | 素材管理 | 管理视频、图片、音频和数字人素材。 |
| `Tasks.tsx` | 任务中心 | 查看生成任务队列、进度和结果。 |
| `Help.tsx` | 帮助中心 | 查看平台使用说明和常见问题。 |
| `Notifications.tsx` | 通知中心 | 查看系统公告和任务通知。 |
| `Account.tsx` | 账号中心 | 查看账号资料、权益和店铺信息。 |
| `AccountSettings.tsx` | 账号设置 | 管理账号安全、偏好和通知设置。 |

- [ ] **步骤 5：运行页面骨架测试通过**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/placeholders/ModulePlaceholder.test.tsx
```

预期：PASS。

- [ ] **步骤 6：提交页面骨架**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/placeholders
git commit -m "feat(placeholders): 新增业务模块占位页面骨架"
```

---

## 任务 4：首页类型与 mock 数据

**文件：**
- 测试：`ai-video-ui/ai-video-webapp/src/pages/home/data.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/data.ts`

- [ ] **步骤 1：写失败测试**

创建 `data.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import {
  dashboardMetrics,
  filterProjectsByCategory,
  getDashboardState,
  recentTasks,
  projects,
  assetShortcuts,
  taskSummary,
} from './data';

describe('home dashboard data', () => {
  it('matches the reference metrics and task numbers', () => {
    expect(dashboardMetrics.map((item) => [item.label, item.value])).toEqual([
      ['视频生成', 32],
      ['数字人生成', 86],
      ['声音克隆', 45],
      ['模板使用', 18],
    ]);

    expect(taskSummary.map((item) => [item.label, item.value])).toEqual([
      ['生成中', 8],
      ['排队中', 6],
      ['已完成', 128],
      ['失败', 4],
    ]);
    expect(recentTasks[0]).toMatchObject({
      name: '新品介绍视频',
      type: 'video',
      status: 'running',
      progress: 45,
      createdAt: '2026-07-04 14:32',
    });
    expect(assetShortcuts.map((item) => [item.label, item.count, item.route])).toEqual([
      ['我的视频', '128 个文件', '/assets'],
      ['我的图片', '256 张图片', '/assets'],
      ['我的声音', '32 个音频', '/assets'],
    ]);
  });

  it('keeps project dates and durations from the reference image', () => {
    expect(projects.map((item) => item.updatedAt)).toEqual([
      '2026-07-04 14:32',
      '2026-07-04 11:09',
      '2026-07-03 14:20',
      '2026-07-02 16:05',
    ]);
    expect(projects.map((item) => item.duration)).toEqual(['00:45', '00:36', '01:02', '00:28']);
  });

  it('filters voice clone projects for empty state testing', () => {
    expect(filterProjectsByCategory('all')).toHaveLength(4);
    expect(filterProjectsByCategory('videoCreate')).toHaveLength(1);
    expect(filterProjectsByCategory('imageDigitalHuman')).toHaveLength(1);
    expect(filterProjectsByCategory('videoDigitalHuman')).toHaveLength(1);
    expect(filterProjectsByCategory('voiceClone')).toHaveLength(1);
    expect(getDashboardState('emptyProjects').projectsByCategory.voiceClone).toHaveLength(0);
  });

  it('supports the error mock state', () => {
    expect(getDashboardState('error').status).toBe('error');
  });
});
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/home/data.test.ts
```

预期：FAIL，原因是 `./data` 不存在。

- [ ] **步骤 3：定义类型**

创建 `types.ts`：

```ts
export type HomeMockState = 'normal' | 'error' | 'emptyProjects';
export type ProjectCategory = 'all' | 'videoCreate' | 'imageDigitalHuman' | 'videoDigitalHuman' | 'voiceClone';
export type TaskType = 'video' | 'image' | 'digitalHuman' | 'voice';
export type TaskStatus = 'running' | 'queued' | 'success' | 'failed' | 'cancelled';
export type HomeAssetKey =
  | 'guideCard'
  | 'quickVideoIllustration'
  | 'quickTemplateIllustration'
  | 'digitalHumanAvatar'
  | 'voiceWave'
  | 'projectVideoCover'
  | 'projectDigitalHumanCover'
  | 'projectVoiceCover'
  | 'assetVideoIcon'
  | 'assetImageIcon'
  | 'assetVoiceIcon';

export type QuickStartItem = {
  key: string;
  title: string;
  description: string;
  action: string;
  route: string;
  tone: 'coral' | 'violet' | 'blue' | 'sky' | 'green';
  asset: HomeAssetKey;
};

export type DashboardMetric = {
  label: string;
  value: number;
  hint: string;
};

export type ProjectItem = {
  id: string;
  title: string;
  category: ProjectCategory;
  categoryLabel: string;
  updatedAt: string;
  duration: string;
  thumbnail: HomeAssetKey;
};

export type TaskSummaryItem = {
  status: TaskStatus;
  label: string;
  value: number;
  description: string;
};

export type RecentTaskItem = {
  id: string;
  name: string;
  type: TaskType;
  typeLabel: string;
  status: TaskStatus;
  statusLabel: string;
  progress: number;
  createdAt: string;
};

export type NoticeItem = {
  id: string;
  title: string;
  date: string;
  isNew?: boolean;
};

export type InspirationItem = {
  id: string;
  title: string;
  content: string;
};

export type AssetShortcutItem = {
  key: string;
  label: string;
  count: string;
  icon: HomeAssetKey;
  route: string;
};

export type DashboardState = {
  status: 'ready' | 'error';
  projectsByCategory: Record<ProjectCategory, ProjectItem[]>;
};
```

- [ ] **步骤 4：实现 mock 数据**

创建 `data.ts`，核心数据必须包含：

```ts
import type {
  DashboardMetric,
  DashboardState,
  HomeMockState,
  AssetShortcutItem,
  InspirationItem,
  NoticeItem,
  ProjectCategory,
  ProjectItem,
  QuickStartItem,
  RecentTaskItem,
  TaskSummaryItem,
} from './types';

export const quickStartItems: QuickStartItem[] = [
  { key: 'video', title: '视频创作', description: '从脚本到视频，一站式创作', action: '去创作', route: '/video/create/industry', tone: 'coral', asset: 'quickVideoIllustration' },
  { key: 'template', title: '模板中心', description: '海量模板，一键生成视频', action: '去探索', route: '/templates', tone: 'violet', asset: 'quickTemplateIllustration' },
  { key: 'imageHuman', title: '图生数字人', description: '上传图片，生成数字人视频', action: '去创作', route: '/digital-human/image', tone: 'blue', asset: 'digitalHumanAvatar' },
  { key: 'videoHuman', title: '视频数字人', description: '上传视频，生成口播数字人视频', action: '去创作', route: '/digital-human/video', tone: 'sky', asset: 'digitalHumanAvatar' },
  { key: 'voice', title: '克隆声音', description: '克隆真实声音，生成专属音色', action: '去创作', route: '/voice-clone', tone: 'green', asset: 'voiceWave' },
];

export const dashboardMetrics: DashboardMetric[] = [
  { label: '视频生成', value: 32, hint: '近7天' },
  { label: '数字人生成', value: 86, hint: '近7天' },
  { label: '声音克隆', value: 45, hint: '近7天' },
  { label: '模板使用', value: 18, hint: '近7天' },
];

export const projects: ProjectItem[] = [
  { id: 'p1', title: '新品介绍视频', category: 'videoCreate', categoryLabel: '视频创作', updatedAt: '2026-07-04 14:32', duration: '00:45', thumbnail: 'projectVideoCover' },
  { id: 'p2', title: '品牌宣传片', category: 'imageDigitalHuman', categoryLabel: '图生数字人', updatedAt: '2026-07-04 11:09', duration: '00:36', thumbnail: 'projectDigitalHumanCover' },
  { id: 'p3', title: '产品使用教程', category: 'videoDigitalHuman', categoryLabel: '视频数字人', updatedAt: '2026-07-03 14:20', duration: '01:02', thumbnail: 'projectDigitalHumanCover' },
  { id: 'p4', title: '活动宣传配音', category: 'voiceClone', categoryLabel: '克隆声音', updatedAt: '2026-07-02 16:05', duration: '00:28', thumbnail: 'projectVoiceCover' },
];

export const notices: NoticeItem[] = [
  { id: 'n1', title: '平台功能更新公告', date: '2026-07-04', isNew: true },
  { id: 'n2', title: '关于优化视频生成速度的说明', date: '2026-07-02' },
  { id: 'n3', title: '克隆声音功能使用指南', date: '2026-06-28' },
];

export const inspirations: InspirationItem[] = [
  { id: 'i1', title: '美妆产品推广文案示例', content: '焕发生机光彩，从这一刻开始。全新配方，温和呵护您的肌肤，让美丽由内而外绽放...' },
  { id: 'i2', title: '新品介绍视频开场白', content: '今天带来一款为高效创作而生的新品，让复杂流程变简单，让表达更有质感。' },
];

export const taskSummary: TaskSummaryItem[] = [
  { status: 'running', label: '生成中', value: 8, description: '进行中的任务' },
  { status: 'queued', label: '排队中', value: 6, description: '等待执行的任务' },
  { status: 'success', label: '已完成', value: 128, description: '今日完成任务' },
  { status: 'failed', label: '失败', value: 4, description: '今日失败任务' },
];

export const recentTasks: RecentTaskItem[] = [
  { id: 't1', name: '新品介绍视频', type: 'video', typeLabel: '视频', status: 'running', statusLabel: '生成中', progress: 45, createdAt: '2026-07-04 14:32' },
];

export const assetShortcuts: AssetShortcutItem[] = [
  { key: 'video', label: '我的视频', count: '128 个文件', icon: 'assetVideoIcon', route: '/assets' },
  { key: 'image', label: '我的图片', count: '256 张图片', icon: 'assetImageIcon', route: '/assets' },
  { key: 'voice', label: '我的声音', count: '32 个音频', icon: 'assetVoiceIcon', route: '/assets' },
];

export const filterProjectsByCategory = (category: ProjectCategory) =>
  category === 'all' ? projects : projects.filter((item) => item.category === category);

export const getDashboardState = (mockState: HomeMockState = 'normal'): DashboardState => {
  const categories: ProjectCategory[] = ['all', 'videoCreate', 'imageDigitalHuman', 'videoDigitalHuman', 'voiceClone'];
  const projectsByCategory = Object.fromEntries(
    categories.map((category) => [category, filterProjectsByCategory(category)]),
  ) as DashboardState['projectsByCategory'];

  if (mockState === 'emptyProjects') {
    projectsByCategory.voiceClone = [];
  }

  return {
    status: mockState === 'error' ? 'error' : 'ready',
    projectsByCategory,
  };
};
```

- [ ] **步骤 5：运行数据测试通过**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/home/data.test.ts
```

预期：PASS。

- [ ] **步骤 6：提交数据变更**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/home/types.ts ai-video-ui/ai-video-webapp/src/pages/home/data.ts ai-video-ui/ai-video-webapp/src/pages/home/data.test.ts
git commit -m "feat(home): 新增首页 mock 数据契约与类型定义"
```

---

## 任务 5：裁切参考图视觉资源

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/scripts/extract-home-assets.ps1`
- 创建：`ai-video-ui/ai-video-webapp/public/home-assets/manifest.json`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/assets.ts`

- [ ] **步骤 1：编写资源裁切脚本**

创建 `extract-home-assets.ps1`：

```powershell
param(
  [string]$Source = "D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 12_46_13.png",
  [string]$OutDir = "public/home-assets"
)

Add-Type -AssemblyName System.Drawing

if (!(Test-Path $Source)) {
  throw "Reference image not found: $Source"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$image = [System.Drawing.Image]::FromFile($Source)

# 断言参考图尺寸为 1680 x 946，与规格视觉验收基准一致
$expectedWidth = 1680
$expectedHeight = 946
if ($image.Width -ne $expectedWidth -or $image.Height -ne $expectedHeight) {
  $image.Dispose()
  throw "参考图尺寸不匹配：期望 $expectedWidth x $expectedHeight，实际 $($image.Width) x $($image.Height)。请核对设计稿版本或更新脚本坐标。"
}
Write-Host "参考图尺寸校验通过：$($image.Width) x $($image.Height)"
$items = @(
  @{ name = "guide-card"; x = 1030; y = 92; w = 150; h = 110; usage = "新手引导右侧插图" },
  @{ name = "quick-video-illustration"; x = 438; y = 310; w = 70; h = 82; usage = "视频创作卡片插图" },
  @{ name = "quick-template-illustration"; x = 684; y = 320; w = 76; h = 62; usage = "模板中心卡片插图" },
  @{ name = "digital-human-avatar"; x = 920; y = 284; w = 92; h = 126; usage = "数字人头像插图和项目封面主体" },
  @{ name = "voice-wave"; x = 1438; y = 326; w = 112; h = 70; usage = "克隆声音波形插图" },
  @{ name = "project-video-cover"; x = 286; y = 494; w = 190; h = 112; usage = "最近项目视频封面" },
  @{ name = "project-digital-human-cover"; x = 494; y = 494; w = 188; h = 112; usage = "最近项目数字人封面" },
  @{ name = "project-voice-cover"; x = 913; y = 494; w = 176; h = 112; usage = "最近项目声音封面" },
  @{ name = "asset-video-icon"; x = 1262; y = 872; w = 42; h = 42; usage = "我的视频图标" },
  @{ name = "asset-image-icon"; x = 1380; y = 872; w = 42; h = 42; usage = "我的图片图标" },
  @{ name = "asset-voice-icon"; x = 1502; y = 872; w = 42; h = 42; usage = "我的声音图标" }
)

$manifest = @()
foreach ($item in $items) {
  $bitmap = New-Object System.Drawing.Bitmap($item.w, $item.h)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.DrawImage(
    $image,
    (New-Object System.Drawing.Rectangle(0, 0, $item.w, $item.h)),
    (New-Object System.Drawing.Rectangle($item.x, $item.y, $item.w, $item.h)),
    [System.Drawing.GraphicsUnit]::Pixel
  )
  $fileName = "$($item.name).png"
  $target = Join-Path $OutDir $fileName
  $bitmap.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
  $graphics.Dispose()
  $bitmap.Dispose()
  $manifest += [ordered]@{
    key = $item.name
    file = $fileName
    usage = $item.usage
    source = $Source
    crop = @{ x = $item.x; y = $item.y; width = $item.w; height = $item.h }
  }
}

$manifest | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $OutDir "manifest.json") -Encoding utf8
$image.Dispose()
```

- [ ] **步骤 2：运行裁切脚本**

```powershell
cd ai-video-ui/ai-video-webapp
powershell -ExecutionPolicy Bypass -File scripts/extract-home-assets.ps1
```

脚本会在裁切前先断言参考图实际尺寸为 `1680 x 946`（与规格验收基准一致）；若尺寸不符将中断并打印实际值，避免坐标错位产出垃圾资产。

预期：`public/home-assets/` 下生成 11 张 PNG 和 `manifest.json`。裁切对象是纯插图、封面和图标，不裁带完整标题和按钮的卡片，避免组件排版时重复显示文字。

- [ ] **步骤 3：集中导出资源 URL**

创建 `assets.ts`：

```ts
const base = '/home-assets';

export const homeAssets = {
  guideCard: `${base}/guide-card.png`,
  quickVideoIllustration: `${base}/quick-video-illustration.png`,
  quickTemplateIllustration: `${base}/quick-template-illustration.png`,
  digitalHumanAvatar: `${base}/digital-human-avatar.png`,
  voiceWave: `${base}/voice-wave.png`,
  projectVideoCover: `${base}/project-video-cover.png`,
  projectDigitalHumanCover: `${base}/project-digital-human-cover.png`,
  projectVoiceCover: `${base}/project-voice-cover.png`,
  assetVideoIcon: `${base}/asset-video-icon.png`,
  assetImageIcon: `${base}/asset-image-icon.png`,
  assetVoiceIcon: `${base}/asset-voice-icon.png`,
} as const;

export type HomeAssetKey = keyof typeof homeAssets;
```

- [ ] **步骤 4：验证资源文件存在并人工核验**

```powershell
cd ai-video-ui/ai-video-webapp
Get-ChildItem public/home-assets
```

预期：列表包含 `guide-card.png`、`quick-video-illustration.png`、`quick-template-illustration.png`、`digital-human-avatar.png`、`voice-wave.png`、`project-video-cover.png`、`project-digital-human-cover.png`、`project-voice-cover.png`、`asset-video-icon.png`、`asset-image-icon.png`、`asset-voice-icon.png`、`manifest.json`。

**人工核验**（必做，不可跳过）：打开 `public/home-assets/` 目录，逐张对照 `manifest.json` 中 `usage` 字段，确认每张裁切图内容与用途匹配（如 `guide-card.png` 确实是新手引导插图、`digital-human-avatar.png` 确实是数字人头像）。若发现裁错、空白、错位，回到步骤 1 修正坐标后重跑，不能带病进入任务 6。

- [ ] **步骤 5：提交资源变更**

```powershell
git add -- ai-video-ui/ai-video-webapp/scripts/extract-home-assets.ps1 ai-video-ui/ai-video-webapp/public/home-assets ai-video-ui/ai-video-webapp/src/pages/home/assets.ts
git commit -m "feat(home): 新增首页视觉资产与 manifest 清单"
```

---

## 任务 6：首页工作台组件

**文件：**
- 测试：`ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/style.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/QuickStartCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/MetricCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/ProjectCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/NewProjectCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/TaskSummary.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/SidePanel.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/NoticeList.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/InspirationCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/home/components/AssetShortcuts.tsx`

- [ ] **步骤 1：写失败测试**

创建 `HomeDashboard.test.tsx`：

```tsx
import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { HomeDashboard } from './HomeDashboard';

describe('HomeDashboard', () => {
  it('renders the exact dashboard content from the reference', () => {
    render(<HomeDashboard mockState="normal" navigate={vi.fn()} />);

    expect(screen.getByText('下午好，小美')).toBeInTheDocument();
    expect(screen.getByText('新手引导')).toBeInTheDocument();
    expect(screen.getByText('创作数据')).toBeInTheDocument();
    expect(screen.getByText('快速开始')).toBeInTheDocument();
    expect(screen.getByText('最近项目')).toBeInTheDocument();
    expect(screen.getByText('系统公告')).toBeInTheDocument();
    expect(screen.getByText('创作灵感')).toBeInTheDocument();
    expect(screen.getByText('任务概览')).toBeInTheDocument();
    expect(screen.getByText('常用素材')).toBeInTheDocument();

    // 数字断言限定在 metrics-card 范围内，避免与 Progress 的 45% 等数值冲突
    const metricsCard = screen.getByTestId('metrics-card');
    expect(within(metricsCard).getByText('32')).toBeInTheDocument();
    expect(within(metricsCard).getByText('86')).toBeInTheDocument();
    expect(within(metricsCard).getByText('45')).toBeInTheDocument();
    expect(within(metricsCard).getByText('18')).toBeInTheDocument();
    expect(screen.getByText('新品介绍视频')).toBeInTheDocument();
    expect(screen.getByText('2026-07-04 14:32')).toBeInTheDocument();
  });

  it('navigates from quick start actions', () => {
    const navigate = vi.fn();
    render(<HomeDashboard mockState="normal" navigate={navigate} />);
    fireEvent.click(within(screen.getByTestId('quick-video')).getByRole('button', { name: '去创作' }));
    expect(navigate).toHaveBeenCalledWith('/video/create/industry');
  });

  it('navigates from dashboard secondary actions', () => {
    const navigate = vi.fn();
    render(<HomeDashboard mockState="normal" navigate={navigate} />);

    fireEvent.click(screen.getByRole('button', { name: '开始学习' }));
    fireEvent.click(within(screen.getByTestId('metrics-card')).getByRole('button', { name: /查看全部/ }));
    fireEvent.click(screen.getByRole('button', { name: '新建项目' }));
    fireEvent.click(within(screen.getByTestId('notices-card')).getByRole('button', { name: /查看全部/ }));
    fireEvent.click(screen.getByRole('button', { name: '使用此文案' }));
    fireEvent.click(screen.getByText('我的视频'));

    expect(navigate).toHaveBeenCalledWith('/help');
    expect(navigate).toHaveBeenCalledWith('/tasks');
    expect(navigate).toHaveBeenCalledWith('/video/create/industry');
    expect(navigate).toHaveBeenCalledWith('/notifications');
    expect(navigate).toHaveBeenCalledWith('/assets');
  });

  it('shows error state for mockState=error', () => {
    render(<HomeDashboard mockState="error" navigate={vi.fn()} />);
    expect(screen.getByText('首页数据加载失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('shows empty state for empty voice clone tab', () => {
    render(<HomeDashboard mockState="emptyProjects" navigate={vi.fn()} />);
    fireEvent.click(screen.getByRole('tab', { name: '克隆声音' }));
    expect(screen.getByText('暂无克隆声音项目')).toBeInTheDocument();
  });

  it('rotates inspiration copy', () => {
    render(<HomeDashboard mockState="normal" navigate={vi.fn()} />);
    expect(screen.getByText('美妆产品推广文案示例')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '换一换' }));
    expect(screen.getByText('新品介绍视频开场白')).toBeInTheDocument();
  });
});
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/home/HomeDashboard.test.tsx
```

预期：FAIL，原因是 `HomeDashboard` 不存在。

- [ ] **步骤 3：实现首页入口**

创建 `index.tsx`：

```tsx
import { history, useSearchParams } from '@umijs/max';
import { HomeDashboard } from './HomeDashboard';
import type { HomeMockState } from './types';

const validMockStates: HomeMockState[] = ['normal', 'error', 'emptyProjects'];

export default function HomePage() {
  const [searchParams] = useSearchParams();
  const rawMockState = searchParams.get('mockState') as HomeMockState | null;
  const mockState = rawMockState && validMockStates.includes(rawMockState) ? rawMockState : 'normal';

  return <HomeDashboard mockState={mockState} navigate={(path) => history.push(path)} />;
}
```

- [ ] **步骤 4：实现首页组件结构**

`HomeDashboard.tsx` 必须包含：

```tsx
import { Button, Card, Col, Empty, Result, Row, Space, Tabs, Typography } from 'antd';
import { ReloadOutlined, RightOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useIntl } from '@umijs/max';
import { homeAssets } from './assets';
import {
  assetShortcuts,
  dashboardMetrics,
  getDashboardState,
  inspirations,
  notices,
  quickStartItems,
  recentTasks,
  taskSummary,
} from './data';
import { useStyles } from './style';
import type { HomeMockState, ProjectCategory } from './types';
import { QuickStartCard } from './components/QuickStartCard';
import { MetricCard } from './components/MetricCard';
import { ProjectCard } from './components/ProjectCard';
import { NewProjectCard } from './components/NewProjectCard';
import { TaskSummary } from './components/TaskSummary';
import { SidePanel } from './components/SidePanel';
import { NoticeList } from './components/NoticeList';
import { InspirationCard } from './components/InspirationCard';
import { AssetShortcuts } from './components/AssetShortcuts';

type HomeDashboardProps = {
  mockState: HomeMockState;
  navigate: (path: string) => void;
};

const projectTabs: { key: ProjectCategory; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'videoCreate', label: '视频创作' },
  { key: 'imageDigitalHuman', label: '图生数字人' },
  { key: 'videoDigitalHuman', label: '视频数字人' },
  { key: 'voiceClone', label: '克隆声音' },
];

export function HomeDashboard({ mockState, navigate }: HomeDashboardProps) {
  const { styles } = useStyles();
  const { formatMessage } = useIntl();
  const [activeTab, setActiveTab] = useState<ProjectCategory>('all');
  const [inspirationIndex, setInspirationIndex] = useState(0);
  const state = useMemo(() => getDashboardState(mockState), [mockState]);
  const currentInspiration = inspirations[inspirationIndex];

  const t = (id: string) => formatMessage({ id });

  if (state.status === 'error') {
    return (
      <Result
        status="error"
        title={t('pages.home.error.title')}
        subTitle={t('pages.home.error.subtitle')}
        extra={<Button icon={<ReloadOutlined />} onClick={() => navigate('/dashboard')}>{t('pages.home.error.retry')}</Button>}
      />
    );
  }

  return (
    <div className={styles.page}>
      <Row gutter={[24, 24]} align="middle" className={styles.heroRow}>
        <Col flex="auto">
          <Typography.Title level={2}>{t('pages.home.greeting')} 👋</Typography.Title>
          <Typography.Text type="secondary">{t('pages.home.subtitle')}</Typography.Text>
        </Col>
        <Col flex="344px">
          <Card className={styles.guideCard} styles={{ body: { backgroundImage: `url(${homeAssets.guideCard})` } }} data-testid="guide-card">
            <Typography.Title level={4}>{t('pages.home.guide.title')}</Typography.Title>
            <Typography.Text>{t('pages.home.guide.description')}</Typography.Text>
            <Button type="primary" onClick={() => navigate('/help')}>{t('pages.home.guide.action')}</Button>
          </Card>
        </Col>
        <Col flex="456px">
          <Card title={t('pages.home.metrics.title')} data-testid="metrics-card" extra={<Button type="link" onClick={() => navigate('/tasks')}>{t('pages.home.viewAll')} <RightOutlined /></Button>}>
            <Row>
              {dashboardMetrics.map((metric) => (
                <Col span={6} key={metric.label} className={styles.metricItem}>
                  <MetricCard metric={metric} />
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
      </Row>

      <Typography.Title level={4}>{t('pages.home.quickStart.title')}</Typography.Title>
      <Row gutter={20} className={styles.quickGrid}>
        {quickStartItems.map((item) => (
          <Col flex="1 1 0" key={item.key} data-testid={`quick-${item.key}`}>
            <QuickStartCard item={item} navigate={navigate} />
          </Col>
        ))}
      </Row>

      <Row gutter={24}>
        <Col span={16}>
          <Card title={t('pages.home.recentProjects.title')} data-testid="recent-projects-card" extra={<Button type="link">{t('pages.home.viewAll')} <RightOutlined /></Button>}>
            <Tabs
              activeKey={activeTab}
              onChange={(key) => setActiveTab(key as ProjectCategory)}
              items={projectTabs.map((tab) => ({
                key: tab.key,
                label: tab.label,
                children:
                  state.projectsByCategory[tab.key].length > 0 ? (
                    <Row gutter={16}>
                      {state.projectsByCategory[tab.key].map((project) => (
                        <Col span={tab.key === 'all' ? 5 : 6} key={project.id}>
                          <ProjectCard project={project} />
                        </Col>
                      ))}
                      <Col span={tab.key === 'all' ? 4 : 6}>
                        <NewProjectCard navigate={navigate} />
                      </Col>
                    </Row>
                  ) : (
                    <Space direction="vertical" align="center" className={styles.emptyProjects}>
                      <Empty description={`暂无${tab.label}项目`} />
                      <Button type="primary" onClick={() => navigate('/video/create/industry')}>{t('pages.home.createProject')}</Button>
                    </Space>
                  ),
              }))}
            />
          </Card>
          <TaskSummary items={taskSummary} recentTasks={recentTasks} navigate={navigate} />
        </Col>
        <Col span={8}>
          <SidePanel>
            <NoticeList notices={notices} navigate={navigate} />
            <InspirationCard
              inspiration={currentInspiration}
              onRefresh={() => setInspirationIndex((index) => (index + 1) % inspirations.length)}
              onUse={() => navigate('/video/create/industry')}
            />
            <AssetShortcuts items={assetShortcuts} navigate={navigate} />
          </SidePanel>
        </Col>
      </Row>
    </div>
  );
}
```

说明：
- 所有界面文案统一走 `useIntl().formatMessage({ id: 'pages.home.*' })`，i18n 是唯一数据源。
- 删除了原 `homeText` 常量的所有引用。
- `Card` 不依赖 `aria-label` 透传，改用 `data-testid` 作为测试锚点（P5 修复）。
- mock 业务数据（项目标题、公告标题、日期、数量、metric 数值）仍来自 `data.ts`，不走 i18n。

- [ ] **步骤 5：实现区域组件与样式**

创建 `style.ts`：

```ts
import { createStyles } from 'antd-style';

export const useStyles = createStyles(({ token, css }) => ({
  page: css`
    width: 100%;
    min-width: 1180px;
    padding: 24px 28px;
    background: #f7f9fd;
  `,
  heroRow: css`
    margin-bottom: 22px;
  `,
  guideCard: css`
    min-height: 126px;
    border-radius: 8px;
    overflow: hidden;

    .ant-card-body {
      min-height: 126px;
      background-repeat: no-repeat;
      background-position: right center;
      background-size: auto 100%;
    }
  `,
  metricItem: css`
    text-align: center;
    border-right: 1px solid ${token.colorBorderSecondary};

    &:last-child {
      border-right: 0;
    }
  `,
  quickGrid: css`
    margin-bottom: 20px;
  `,
  emptyProjects: css`
    width: 100%;
    padding: 32px 0;
  `,
  sideStack: css`
    width: 100%;
  `,
  quickCard: css`
    min-height: 145px;
    border-radius: 8px;
    overflow: hidden;
    position: relative;
  `,
  quickCardImage: css`
    position: absolute;
    right: 16px;
    bottom: 12px;
    max-width: 38%;
    max-height: 80%;
    object-fit: contain;
  `,
  projectCover: css`
    position: relative;
    aspect-ratio: 16 / 9;
    overflow: hidden;
    border-radius: 6px;
    background: ${token.colorFillTertiary};

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }

    span {
      position: absolute;
      right: 6px;
      bottom: 6px;
      padding: 1px 6px;
      color: #fff;
      background: rgba(0, 0, 0, 0.58);
      border-radius: 4px;
      font-size: 12px;
    }
  `,
  taskSummaryGrid: css`
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 16px;

    button {
      border: 0;
      border-radius: 8px;
      padding: 14px;
      text-align: left;
      background: ${token.colorFillTertiary};
      cursor: pointer;
    }
  `,
}));
```

创建 `QuickStartCard.tsx`：

```tsx
import { Button, Card, Typography } from 'antd';
import { homeAssets } from '../assets';
import { useStyles } from '../style';
import type { QuickStartItem } from '../types';

type QuickStartCardProps = {
  item: QuickStartItem;
  navigate: (path: string) => void;
};

export function QuickStartCard({ item, navigate }: QuickStartCardProps) {
  const { styles } = useStyles();

  return (
    <Card aria-label={item.title} className={styles.quickCard}>
      <Typography.Text strong>{item.title}</Typography.Text>
      <Typography.Paragraph>{item.description}</Typography.Paragraph>
      <img src={homeAssets[item.asset]} alt="" className={styles.quickCardImage} />
      <Button type="primary" onClick={() => navigate(item.route)}>
        {item.action}
      </Button>
    </Card>
  );
}
```

创建 `MetricCard.tsx`：

```tsx
import type { DashboardMetric } from '../types';

type MetricCardProps = {
  metric: DashboardMetric;
};

export function MetricCard({ metric }: MetricCardProps) {
  return (
    <div>
      <strong>{metric.value}</strong>
      <span>{metric.label}</span>
    </div>
  );
}
```

创建 `ProjectCard.tsx`：

```tsx
import { Tag, Typography } from 'antd';
import { homeAssets } from '../assets';
import { useStyles } from '../style';
import type { ProjectItem } from '../types';

type ProjectCardProps = {
  project: ProjectItem;
};

export function ProjectCard({ project }: ProjectCardProps) {
  const { styles } = useStyles();

  return (
    <article>
      <div className={styles.projectCover}>
        <img src={homeAssets[project.thumbnail]} alt="" />
        <span>{project.duration}</span>
      </div>
      <Tag color="blue">{project.categoryLabel}</Tag>
      <Typography.Text strong>{project.title}</Typography.Text>
      <Typography.Text type="secondary">{project.updatedAt}</Typography.Text>
    </article>
  );
}
```

创建 `NewProjectCard.tsx`：

```tsx
import { PlusOutlined } from '@ant-design/icons';
import { Button } from 'antd';

type NewProjectCardProps = {
  navigate: (path: string) => void;
};

export function NewProjectCard({ navigate }: NewProjectCardProps) {
  return (
    <div aria-label="新建项目" className="new-project-card">
      <Button icon={<PlusOutlined />} onClick={() => navigate('/video/create/industry')}>
        新建项目
      </Button>
    </div>
  );
}
```

创建 `TaskSummary.tsx`：

```tsx
import { Button, Card, Progress, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useStyles } from '../style';
import type { RecentTaskItem, TaskSummaryItem } from '../types';

type TaskSummaryProps = {
  items: TaskSummaryItem[];
  recentTasks: RecentTaskItem[];
  navigate: (path: string) => void;
};

export function TaskSummary({ items, recentTasks, navigate }: TaskSummaryProps) {
  const { styles } = useStyles();
  const columns: ColumnsType<RecentTaskItem> = [
    { title: '任务名称', dataIndex: 'name' },
    { title: '类型', dataIndex: 'typeLabel' },
    { title: '状态', dataIndex: 'statusLabel' },
    { title: '进度', dataIndex: 'progress', render: (value) => <Progress percent={value} size="small" /> },
    { title: '创建时间', dataIndex: 'createdAt' },
    { title: '操作', render: () => <Button type="link" onClick={() => navigate('/tasks')}>查看详情</Button> },
  ];

  return (
    <Card title="任务概览">
      <div className={styles.taskSummaryGrid}>
        {items.map((item) => (
          <button key={item.status} type="button" onClick={() => navigate(`/tasks?status=${item.status}`)}>
            <span>{item.label}</span>
            <strong>{item.value}</strong>
            <em>{item.description}</em>
          </button>
        ))}
      </div>
      <Table rowKey="id" columns={columns} dataSource={recentTasks} pagination={false} size="small" />
    </Card>
  );
}
```

创建 `SidePanel.tsx`：

```tsx
import { Space } from 'antd';
import type { ReactNode } from 'react';
import { useStyles } from '../style';

type SidePanelProps = {
  children: ReactNode;
};

export function SidePanel({ children }: SidePanelProps) {
  const { styles } = useStyles();

  return (
    <Space direction="vertical" size={16} className={styles.sideStack}>
      {children}
    </Space>
  );
}
```

创建 `NoticeList.tsx`：

```tsx
import { RightOutlined } from '@ant-design/icons';
import { Button, Card, List, Tag } from 'antd';
import type { NoticeItem } from '../types';

type NoticeListProps = {
  notices: NoticeItem[];
  navigate: (path: string) => void;
};

export function NoticeList({ notices, navigate }: NoticeListProps) {
  return (
    <Card aria-label="系统公告" title="系统公告" extra={<Button type="link" onClick={() => navigate('/notifications')}>查看全部 <RightOutlined /></Button>}>
      <List
        dataSource={notices}
        renderItem={(notice) => (
          <List.Item extra={<span>{notice.date}</span>}>
            {notice.title}
            {notice.isNew ? <Tag color="red">NEW</Tag> : null}
          </List.Item>
        )}
      />
    </Card>
  );
}
```

创建 `InspirationCard.tsx`：

```tsx
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Typography } from 'antd';
import type { InspirationItem } from '../types';

type InspirationCardProps = {
  inspiration: InspirationItem;
  onRefresh: () => void;
  onUse: () => void;
};

export function InspirationCard({ inspiration, onRefresh, onUse }: InspirationCardProps) {
  return (
    <Card title="创作灵感" extra={<Button type="link" icon={<ReloadOutlined />} onClick={onRefresh}>换一换</Button>}>
      <Typography.Text strong>{inspiration.title}</Typography.Text>
      <Typography.Paragraph>{inspiration.content}</Typography.Paragraph>
      <Button onClick={onUse}>使用此文案</Button>
    </Card>
  );
}
```

创建 `AssetShortcuts.tsx`：

```tsx
import { Card } from 'antd';
import { homeAssets } from '../assets';
import type { AssetShortcutItem } from '../types';

type AssetShortcutsProps = {
  items: AssetShortcutItem[];
  navigate: (path: string) => void;
};

export function AssetShortcuts({ items, navigate }: AssetShortcutsProps) {
  return (
    <Card title="常用素材">
      {items.map((item) => (
        <button key={item.key} type="button" onClick={() => navigate(item.route)}>
          <img src={homeAssets[item.icon]} alt="" />
          <span>{item.label}</span>
          <strong>{item.count}</strong>
        </button>
      ))}
    </Card>
  );
}
```

组件实现补充要求：

- `QuickStartCard.tsx`：根元素设置 `aria-label={item.title}`，按钮调用 `navigate(item.route)`，背景使用 `homeAssets[item.asset]`。
- `MetricCard.tsx`：渲染创作数据中的数字、标签和分隔线，父级卡片设置 `aria-label="创作数据"`。
- `ProjectCard.tsx`：显示缩略图、分类标签、标题、更新时间、时长角标。
- `NewProjectCard.tsx`：显示“新建项目”，点击按钮调用 `navigate('/video/create/industry')`。
- `TaskSummary.tsx`：四个统计卡横排，下面显示最近任务表头“任务名称 / 类型 / 状态 / 进度 / 创建时间 / 操作”，进度使用 Ant Design `Progress`，查看详情调用 `navigate('/tasks')`。
- `NoticeList.tsx`：根元素设置 `data-testid="notices-card"`，公告日期必须为 `2026-07-04`、`2026-07-02`、`2026-06-28`，“查看全部”调用 `navigate('/notifications')`。
- `InspirationCard.tsx`：显示当前灵感，“换一换”调用 `onRefresh`，“使用此文案”调用 `onUse`。
- `AssetShortcuts.tsx`：显示“我的视频 / 我的图片 / 我的声音”及数量，点击每项调用 `navigate('/assets')`。
- `SidePanel.tsx`：只保留右侧栏布局容器；公告、灵感、素材的业务内容分别放在独立组件中。
- `style.ts`：使用 `createStyles`，页面宽度占满内容区；卡片圆角控制在 8px；避免单一紫色或深蓝主色；固定项目卡图片比例，避免 hover 或加载时布局位移。
- 首页静态界面文案统一走 `useIntl().formatMessage({ id: 'pages.home.*' })`，i18n 是唯一数据源；`zh-CN/pages.ts` 提供设计稿中文，`en-US/pages.ts` 提供等价英文。mock 业务数据中的项目标题、公告标题、日期、数量继续保持 `data.ts` 固定中文数据，不走 i18n。

- [ ] **步骤 6：运行首页测试通过**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/pages/home/HomeDashboard.test.tsx
```

预期：PASS。

- [ ] **步骤 7：提交首页组件**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.tsx ai-video-ui/ai-video-webapp/src/pages/home/index.tsx ai-video-ui/ai-video-webapp/src/pages/home/style.ts ai-video-ui/ai-video-webapp/src/pages/home/components ai-video-ui/ai-video-webapp/src/pages/home/HomeDashboard.test.tsx
git commit -m "feat(home): 实现首页工作台主页面与区域组件"
```

---

## 任务 7：中英文文案

**文件：**
- 测试：`ai-video-ui/ai-video-webapp/src/locales/home-i18n.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/zh-CN/menu.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/en-US/menu.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts`

- [ ] **步骤 1：写失败测试**

创建 `home-i18n.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import enMenu from './en-US/menu';
import enPages from './en-US/pages';
import zhMenu from './zh-CN/menu';
import zhPages from './zh-CN/pages';

const requiredMenuKeys = [
  'menu.dashboard',
  'menu.video',
  'menu.video.videoCreate',
  'menu.video.drafts',
  'menu.video.templates',
  'menu.digitalHuman',
  'menu.digitalHuman.digitalHumanImage',
  'menu.digitalHuman.digitalHumanVideo',
  'menu.digitalHuman.voiceClone',
  'menu.assets',
  'menu.system',
  'menu.tasks',
  'menu.help',
  'menu.notifications',
  'menu.account',
  'menu.accountSettings',
];

// 这些 key 必须与 HomeDashboard.tsx 中 formatMessage({ id }) 调用一一对应
const requiredPageKeys = [
  'pages.home.greeting',
  'pages.home.subtitle',
  'pages.home.guide.title',
  'pages.home.guide.description',
  'pages.home.guide.action',
  'pages.home.metrics.title',
  'pages.home.quickStart.title',
  'pages.home.recentProjects.title',
  'pages.home.viewAll',
  'pages.home.createProject',
  'pages.home.error.title',
  'pages.home.error.subtitle',
  'pages.home.error.retry',
];

describe('home dashboard i18n', () => {
  it('keeps zh-CN and en-US menu keys aligned', () => {
    for (const key of requiredMenuKeys) {
      expect(zhMenu[key]).toBeTruthy();
      expect(enMenu[key]).toBeTruthy();
    }
  });

  it('provides home page keys used by HomeDashboard in both languages', () => {
    for (const key of requiredPageKeys) {
      expect(zhPages[key]).toBeTruthy();
      expect(enPages[key]).toBeTruthy();
    }
  });

  it('en-US provides genuinely different translation, not duplicate of zh-CN', () => {
    // 防止 en-US 直接复制 zh-CN 的中文值，确保英文翻译真实生效
    expect(enPages['pages.home.greeting']).not.toBe(zhPages['pages.home.greeting']);
    expect(enPages['pages.home.subtitle']).not.toBe(zhPages['pages.home.subtitle']);
    expect(/^[A-Z]/.test(enPages['pages.home.greeting'])).toBe(true);
  });
});
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/locales/home-i18n.test.ts
```

预期：FAIL，原因是新 key 未补齐。

- [ ] **步骤 3：补齐 locale key**

`zh-CN/menu.ts` 增加：

```ts
'menu.dashboard': '首页',
'menu.video': '视频创作',
'menu.video.videoCreate': '视频创作',
'menu.video.drafts': '草稿箱',
'menu.video.templates': '模板中心',
'menu.digitalHuman': '数字人',
'menu.digitalHuman.digitalHumanImage': '图生数字人',
'menu.digitalHuman.digitalHumanVideo': '视频数字人',
'menu.digitalHuman.voiceClone': '克隆声音',
'menu.assets': '素材管理',
'menu.system': '系统功能',
'menu.tasks': '任务中心',
'menu.help': '帮助',
'menu.notifications': '通知',
'menu.account': '账号中心',
'menu.accountSettings': '账号设置',
```

注意：`zh-CN/menu.ts` 现有模板里已经存在 `'menu.dashboard': 'Dashboard'`，本任务应替换该旧值，不保留重复 key。

`en-US/menu.ts` 增加对应英文：

```ts
'menu.dashboard': 'Home',
'menu.video': 'Video Creation',
'menu.video.videoCreate': 'Video Creation',
'menu.video.drafts': 'Drafts',
'menu.video.templates': 'Template Center',
'menu.digitalHuman': 'Digital Human',
'menu.digitalHuman.digitalHumanImage': 'Image Digital Human',
'menu.digitalHuman.digitalHumanVideo': 'Video Digital Human',
'menu.digitalHuman.voiceClone': 'Voice Clone',
'menu.assets': 'Assets',
'menu.system': 'System',
'menu.tasks': 'Task Center',
'menu.help': 'Help',
'menu.notifications': 'Notifications',
'menu.account': 'Account',
'menu.accountSettings': 'Account Settings',
```

`zh-CN/pages.ts` 与 `en-US/pages.ts` 至少增加，并由首页组件静态界面文案读取：

```ts
'pages.home.greeting': '下午好，小美',
'pages.home.subtitle': '欢迎使用 AI 视频工作台，快速创建专业级 AI 视频内容',
'pages.home.guide.title': '新手引导',
'pages.home.guide.description': '跟随引导，快速上手',
'pages.home.guide.action': '开始学习',
'pages.home.metrics.title': '创作数据',
'pages.home.quickStart.title': '快速开始',
'pages.home.recentProjects.title': '最近项目',
'pages.home.viewAll': '查看全部',
'pages.home.createProject': '新建项目',
'pages.home.error.title': '首页数据加载失败',
'pages.home.error.subtitle': '请检查网络连接后重试。',
'pages.home.error.retry': '重试',
```

```ts
'pages.home.greeting': 'Good afternoon, Xiaomei',
'pages.home.subtitle': 'Welcome to AI Video Workspace. Create professional AI video content quickly.',
'pages.home.guide.title': 'Getting Started',
'pages.home.guide.description': 'Follow the guide and start quickly.',
'pages.home.guide.action': 'Start Learning',
'pages.home.metrics.title': 'Creation Data',
'pages.home.quickStart.title': 'Quick Start',
'pages.home.recentProjects.title': 'Recent Projects',
'pages.home.viewAll': 'View All',
'pages.home.createProject': 'New Project',
'pages.home.error.title': 'Failed to load dashboard data',
'pages.home.error.subtitle': 'Please check your network and try again.',
'pages.home.error.retry': 'Retry',
```

- [ ] **步骤 4：运行文案测试通过**

```powershell
cd ai-video-ui/ai-video-webapp
npm test -- src/locales/home-i18n.test.ts
```

预期：PASS。

- [ ] **步骤 5：提交文案变更**

```powershell
git add -- ai-video-ui/ai-video-webapp/src/locales/home-i18n.test.ts ai-video-ui/ai-video-webapp/src/locales/zh-CN/menu.ts ai-video-ui/ai-video-webapp/src/locales/en-US/menu.ts ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts
git commit -m "feat(i18n): 补齐首页工作台菜单与页面文案多语言 key"
```

---

## 任务 8：全量验证与视觉验收

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/tmp/dashboard-1680x946.png`
- 创建：`ai-video-ui/ai-video-webapp/tmp/dashboard-comparison-notes.md`
- 创建：`ai-video-ui/ai-video-webapp/tmp/dashboard-visual-review.html`

- [ ] **步骤 1：运行全量测试**

```powershell
cd ai-video-ui/ai-video-webapp
npm test
```

预期：全部测试 PASS，包含新增的路由、首页数据、首页组件、页面骨架和文案测试。

- [ ] **步骤 2：运行类型检查与 lint**

```powershell
cd ai-video-ui/ai-video-webapp
npm run tsc
npm run biome:lint
npx antd lint ./src
```

预期：三个命令均以 exit code 0 结束。`npx antd lint` 用于检查本次新增的 Ant Design 组件用法（Card / Progress / Table / Tabs / Result / Empty）是否存在 deprecated API。

- [ ] **步骤 3：启动开发服务**

```powershell
cd ai-video-ui/ai-video-webapp
npm start
```

预期：开发服务监听 `http://localhost:8000`。如果 8000 被占用，按终端提示记录实际端口。

- [ ] **步骤 4：浏览器路径验收**

使用 in-app browser 打开：

```text
http://localhost:8000/
http://localhost:8000/dashboard
http://localhost:8000/dashboard?mockState=error
http://localhost:8000/dashboard?mockState=emptyProjects
http://localhost:8000/video/create/industry
http://localhost:8000/help
http://localhost:8000/notifications
http://localhost:8000/account
http://localhost:8000/account/settings
```

预期：

- `/` 跳转到 `/dashboard`。
- `/dashboard` 显示 AI 视频工作台首页。
- `mockState=error` 显示错误态和重试按钮。
- `mockState=emptyProjects` 在“克隆声音”tab 显示空态。
- 所有业务路径有页面响应，不进入 404。

- [ ] **步骤 5：固定视口截图**

在 in-app browser 设置视口 `1680 x 946`，打开：

```text
http://localhost:8000/dashboard
```

保存截图：

```text
ai-video-ui/ai-video-webapp/tmp/dashboard-1680x946.png
```

预期：截图包含 ProLayout 外壳和首页内容区；首页内容区模块顺序、文案、数字、日期、项目数量、右栏结构与参考图一致。

- [ ] **步骤 6：创建同屏视觉对比**

创建 `tmp/dashboard-comparison-notes.md`：

```markdown
# Dashboard Visual Comparison

Reference: D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 12_46_13.png
Implementation: ai-video-ui/ai-video-webapp/tmp/dashboard-1680x946.png

## Checked

- Header and sidebar shell intentionally remain ProLayout.
- Main content grid matches the reference order.
- Quick start contains five horizontal cards.
- Creation metrics are 32, 86, 45, and 18.
- Project dates, durations, labels, notice dates, and task numbers match the spec.
- Recent projects, right panels, and task summary are visible in the first viewport.

## Difference Log

- Top welcome row:
- Quick start cards:
- Recent projects:
- Right side panels:
- Task overview:
```

创建 `tmp/dashboard-visual-review.html`，将参考图和实现截图左右并排显示，固定图片宽度并保留原始比例：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <title>Dashboard Visual Review</title>
    <style>
      body {
        margin: 0;
        padding: 24px;
        font-family: Arial, sans-serif;
        background: #f5f7fb;
      }
      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 24px;
      }
      figure {
        margin: 0;
        background: #fff;
        border: 1px solid #d9e0ee;
        border-radius: 8px;
        padding: 12px;
      }
      img {
        width: 100%;
        height: auto;
        display: block;
      }
      figcaption {
        margin-bottom: 8px;
        font-weight: 600;
      }
    </style>
  </head>
  <body>
    <div class="grid">
      <figure>
        <figcaption>Reference</figcaption>
        <img src="../../../../设计稿/v2/ChatGPT Image 2026年7月2日 12_46_13.png" alt="Reference" />
      </figure>
      <figure>
        <figcaption>Implementation</figcaption>
        <img src="./dashboard-1680x946.png" alt="Implementation" />
      </figure>
    </div>
  </body>
</html>
```

如果同屏对比中顶部欢迎区、快速开始、最近项目、右侧信息栏、任务概览任一区域存在明显可见差异，先修改样式和资产裁切，再重复步骤 1 到步骤 6。

- [ ] **步骤 7：最终状态检查**

```powershell
git status --short
```

预期：只包含本计划产生的文档、源码、测试、资源和验证说明；不包含进入任务前已有的无关文件。

- [ ] **步骤 8：验证产物保留在本地（不入库）**

`tmp/` 已加入 `.gitignore`，验证截图、对比笔记和 HTML 仅本地保留，不纳入 git。`git status` 应显示 `tmp/` 相关文件被忽略。

若团队需要共享视觉对比结果，建议在 PR 描述中附带截图，或单独约定一个 `docs/visual-review/` 目录存放最终对比页（一次性，不随每次验收更新）。

---

## 自检

规格覆盖映射：

- PRD 积分范围冲突：任务 1。
- 菜单内容、路由、根路径跳转、非侧边栏入口：任务 2。
- 每个业务入口不 404，且具备加载、空、失败、权限不足状态展示：任务 3。
- 首页 mock 数据、枚举、状态值、日期、数字、任务状态：任务 4。
- 参考图本地视觉资源与裁切记录：任务 5。
- 首页内容区、tabs、错误态、空态、跳转、创作灵感轮换：任务 6。
- `zh-CN` / `en-US` 文案：任务 7。
- 固定视口截图、关键路径浏览器验收、最终验证：任务 8。

一致性检查：

- 文件路径均指向 `ai-video-ui/ai-video-webapp` 或项目根文档。
- 新增测试均给出可运行命令和预期失败/通过结果。
- 提交命令均显式列出文件路径，避免带入工作区已有无关改动。
- 不新增后端接口，不改 Electron 壳层，不删除其他语言包。
- 首页视觉验收以参考图一比一为目标，不能降低为宽泛接近。
