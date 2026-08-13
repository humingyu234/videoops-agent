# 用户端登录页一比一复刻与全前端品牌统一实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不改变认证 API、Token 与会话隔离规则的前提下，用 React + Ant Design + Ant Design Pro / ProComponents 一比一复刻已批准登录视觉，并把两个前端包的用户可见产品品牌统一为“素造智能体”。

**架构：** 保留 `Login` 页面现有的密码认证、`/api/auth/me` 校验、初始状态提交和安全回跳编排，只把五种登录入口收敛为密码与静态微信扫码建设中两种状态。视觉层拆成页面局部 React 组件和 CSS Module；品牌更新通过显式文件映射和跨包扫描脚本完成，不触碰后端、认证 Service 或另一端会话实现。

**技术栈：** React 19、TypeScript、Ant Design 6、Ant Design Pro / ProComponents 3、Umi Max 4、CSS Modules、Vitest、Testing Library、`@ant-design/cli`。

---

## 输入、事实源与边界

- 已批准规格：`docs/superpowers/specs/2026-08-03-login-visual-replica-and-frontend-branding-design.md`
- 原始视觉事实源：`D:\AI\login-05-showcase.html`
- 原始视觉 SHA256：`D3C0B1C4865E0F361742855B8A3C2A0BE727C3A79094E233A4F0ACEF63EC3B23`
- 已批准视觉变体：`.superpowers/brainstorm/1560-1785698991/content/login-target-v3.html`
- 已批准视觉变体 SHA256：`0DCE7B1DE929F3D4636E5A75711A7A3CFC20E7330E98249BEFEB8E676EAC8544`
- 只改前端；`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md` 与后端代码均不修改。
- 不新增线上 DTO、VO、enum、API adapter 或 mock API；现有 `authApi.smsLogin`、`emailLogin`、`socialLogin`、`miniProgramLogin` 继续保留并由既有 API 单元测试覆盖。
- 不直接发布静态 HTML，不使用 `iframe`，不增加 UI 框架，不升级依赖。

## 文件结构与职责

### 创建

- `ai-video-ui/ai-video-webapp/src/pages/user/login/index.module.css`：登录页唯一视觉样式入口，承载场景、玻璃卡片、Ant Design 公开语义类、响应式和减少动态效果。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginBrandMark.tsx`：参考页人物品牌 SVG，可在顶栏和卡片复用。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginSceneBackdrop.tsx`：三场景背景、6 秒轮播、手动切换、说明文案与 reduced-motion 行为。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginFeedback.tsx`：固定顶部玻璃 Toast、白名单通知和稳定错误语义。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/PasswordLoginPanel.tsx`：Ant Design Form/Input/Button 密码登录表单，固定非持久会话入口。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/WechatQrConstructionPanel.tsx`：纯本地确定性二维码图案、微信提示和“建设中”蒙版。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/components/loginVisuals.test.tsx`：场景轮播、reduced-motion、二维码不可交互和 Toast 语义单元测试。
- `ai-video-ui/ai-video-webapp/src/branding.test.ts`：用户端品牌文件映射与允许项合同测试。
- `ai-video-ui/ai-video-platform-ui/tests/branding.test.ts`：平台端标题、Logo 和控制台品牌合同测试。
- `ai-video-ui/scripts/verify-frontend-branding.mjs`：跨两个前端包扫描旧品牌，且只允许已批准技术元数据和第三方 URL。

### 修改

- `ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`：删除四种旧入口和注册/记住/忘记/Footer 渲染，组合新视觉组件并保留认证编排。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`：把五入口测试改为两入口测试，固定 `persistent=false`，保留全部安全失败矩阵。
- `ai-video-ui/ai-video-webapp/config/config.ts`：全局标题改为“素造智能体”。
- `ai-video-ui/ai-video-webapp/config/defaultSettings.ts`：ProLayout 标题改为“素造智能体”。
- `ai-video-ui/ai-video-webapp/src/manifest.json`：PWA `name`/`short_name` 改为“素造智能体”。
- `ai-video-ui/ai-video-webapp/src/components/Footer/index.tsx`：其他页面共享页脚品牌改为“素造智能体”。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`：工作台品牌名改为“素造智能体”。
- `ai-video-ui/ai-video-webapp/src/pages/Welcome.tsx`、`src/pages/Admin.tsx`、`src/pages/chatbot/index.tsx`：移除模板产品品牌。
- `ai-video-ui/ai-video-webapp/src/locales/bn-BD/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/fa-IR/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/id-ID/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/ja-JP/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/pt-BR/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts`
- `ai-video-ui/ai-video-webapp/src/locales/zh-TW/pages.ts`
- `ai-video-ui/ai-video-platform-ui/.env.development`、`.env.production`：平台标题与 Logo 标题。
- `ai-video-ui/ai-video-platform-ui/src/utils/env.ts`、`vite.config.ts`：同名默认回退。
- `ai-video-ui/ai-video-platform-ui/src/pages/index.tsx`：控制台首页品牌。

### 删除

- `ai-video-ui/ai-video-webapp/src/pages/user/login/__snapshots__/login.test.tsx.snap`：当前测试不再引用的旧 Pro 登录页快照；删除前用 `rg` 证明没有快照断言。

### 明确保持逐字节不变

- `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/session.ts`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- `ai-video-ui/ai-video-platform-ui/src/utils/auth.ts`
- `ai-video-ui/ai-video-platform-ui/src/api/request.ts`
- `ai-video-ui/ai-video-platform-ui/src/api/login.ts`

## 风险、并发与审查安排

- 登录页任务为红色高风险：单一实施者串行完成测试与实现；任何认证文件差异立即回退。
- 品牌任务为黄色中风险：可在登录页实现稳定后独立进行，但本计划默认串行，避免共享测试和标题文件冲突。
- 后端、数据库、本机 MySQL/Redis 集成测试不适用；没有后端联调和 mock 替换步骤。
- 完成后进行两项独立只读审查：前端视觉/可访问性审查、认证会话/API 不变审查。只允许一轮修复和一次定向复核。

## 当前 Codex Windows 运行时说明

用户端仍以 `package-lock.json`/npm 脚本为项目标准。若当前 Codex shell 中 `npm`/`npx` 不在 PATH，不得改用 pnpm 安装用户端依赖或生成新的 pnpm 锁文件；先调用 workspace dependency loader，然后把其返回的 Node `bin` 加到本次 PowerShell 进程 PATH，直接调用仓库现有 `.cmd`：

```powershell
$taskNodeBin = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin'
$env:PATH = "$taskNodeBin;$env:PATH"
.\node_modules\.bin\vitest.cmd --version
.\node_modules\.bin\tsc.cmd --version
.\node_modules\.bin\antd.cmd --cli-version
```

当前已验证输出分别为 Vitest 4.1.10、TypeScript 7.0.2、Ant Design CLI 6.5.1。正常开发机仍优先使用下文的 `npm` 命令。

平台端在当前 Codex shell 使用 workspace dependency loader 返回的 pnpm：

```powershell
$taskPnpm = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd'
& $taskPnpm --version
```

下文平台端 `pnpm` 命令在该 shell 中可等价替换为 `& $taskPnpm`，不得更换锁文件或执行安装升级。

---

### 任务 1：建立视觉与认证安全基线

**文件：**
- 读取：`D:\AI\login-05-showcase.html`
- 读取：`.superpowers/brainstorm/1560-1785698991/content/login-target-v3.html`
- 读取：上文七个认证/会话文件
- 测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`

- [ ] **步骤 1：确认工作区只包含本任务预期差异**

运行：

```powershell
git status --short
git log -3 --oneline
```

预期：开始实施时没有未知未提交文件；若存在用户改动，记录并避开，不覆盖、不清理。

- [ ] **步骤 2：验证两个视觉事实源未漂移**

运行：

```powershell
Get-FileHash 'D:\AI\login-05-showcase.html' -Algorithm SHA256
Get-FileHash '.superpowers\brainstorm\1560-1785698991\content\login-target-v3.html' -Algorithm SHA256
```

预期：依次得到 `D3C0B1C4865E0F361742855B8A3C2A0BE727C3A79094E233A4F0ACEF63EC3B23` 和 `0DCE7B1DE929F3D4636E5A75711A7A3CFC20E7330E98249BEFEB8E676EAC8544`。任一不一致都停止视觉移植并报告源漂移。

- [ ] **步骤 3：记录七个禁止修改文件的 SHA256**

运行：

```powershell
Get-FileHash @(
  'ai-video-ui\ai-video-webapp\src\services\ai-video\auth\api.ts',
  'ai-video-ui\ai-video-webapp\src\services\ai-video\auth\session.ts',
  'ai-video-ui\ai-video-webapp\src\services\ai-video\auth\types.ts',
  'ai-video-ui\ai-video-webapp\src\services\ai-video\core\ruoyiAdapter.ts',
  'ai-video-ui\ai-video-platform-ui\src\utils\auth.ts',
  'ai-video-ui\ai-video-platform-ui\src\api\request.ts',
  'ai-video-ui\ai-video-platform-ui\src\api\login.ts'
) -Algorithm SHA256 | Format-Table Path,Hash -AutoSize
```

预期：得到七行非空哈希；把输出保留在本次实施记录中，最终逐项比较。

- [ ] **步骤 4：查询 Ant Design 6 公开 API 与语义结构**

从 `ai-video-ui/ai-video-webapp` 运行：

```powershell
npx antd --lang zh info Form
npx antd --lang zh info Input
npx antd --lang zh info Button
npx antd --lang zh info Tabs
npx antd --lang zh semantic Form
npx antd --lang zh semantic Button
npx antd --lang zh semantic Tabs
npx antd --lang zh token Input
```

预期：Form、Button、Tabs 输出公开 `classNames`/`styles` 语义；Input 输出公开 `classNames`/`styles`、Password 和原生 input 属性。计划实现只使用这些公开入口。

- [ ] **步骤 5：运行现有认证合同与登录测试作为基线**

从 `ai-video-ui/ai-video-webapp` 运行：

```powershell
npm test -- src/services/ai-video/auth/api.test.ts src/pages/user/login/index.test.tsx
```

预期：至少执行 2 个测试文件且测试数大于 0；开始改动前应为 PASS。若基线失败，先报告现有失败，不把它归因于本任务。

---

### 任务 2：用测试驱动登录视觉基础组件

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.module.css`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginBrandMark.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginSceneBackdrop.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginFeedback.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/WechatQrConstructionPanel.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/loginVisuals.test.tsx`

- [ ] **步骤 1：先创建场景、二维码和 Toast 的失败测试**

测试至少写入以下行为，测试文本与导出名保持一致：

```tsx
import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginFeedback } from './LoginFeedback';
import { LoginSceneBackdrop } from './LoginSceneBackdrop';
import { WechatQrConstructionPanel } from './WechatQrConstructionPanel';

function installMatchMedia(matches: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      addEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
      matches,
      media: query,
      onchange: null,
      removeEventListener: vi.fn(),
    })),
  });
}

describe('login visual primitives', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    installMatchMedia(false);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('cycles every six seconds and restarts after a manual scene choice', () => {
    render(<LoginSceneBackdrop />);
    expect(screen.getByRole('button', { name: '切换到AI 模特场景' })).toHaveAttribute('aria-pressed', 'true');

    act(() => vi.advanceTimersByTime(6000));
    expect(screen.getByRole('button', { name: '切换到直播克隆场景' })).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(screen.getByRole('button', { name: '切换到语音合成场景' }));
    expect(screen.getByRole('button', { name: '切换到语音合成场景' })).toHaveAttribute('aria-pressed', 'true');
    act(() => vi.advanceTimersByTime(5999));
    expect(screen.getByRole('button', { name: '切换到语音合成场景' })).toHaveAttribute('aria-pressed', 'true');
    act(() => vi.advanceTimersByTime(1));
    expect(screen.getByRole('button', { name: '切换到AI 模特场景' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('stops automatic motion but keeps manual scene controls', () => {
    installMatchMedia(true);
    render(<LoginSceneBackdrop />);
    act(() => vi.advanceTimersByTime(18000));
    expect(screen.getByRole('button', { name: '切换到AI 模特场景' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '切换到直播克隆场景' }));
    expect(screen.getByRole('button', { name: '切换到直播克隆场景' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('renders a noninteractive WeChat construction placeholder', () => {
    const { container } = render(<WechatQrConstructionPanel />);
    expect(screen.getByRole('status', { name: '微信扫码登录建设中' })).toHaveTextContent('建设中');
    expect(screen.getByText('微信')).toBeVisible();
    expect(container.querySelector('[data-qr-placeholder][aria-hidden="true"]')).toBeTruthy();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByText('钉钉')).not.toBeInTheDocument();
  });

  it('uses alert for failures and status for fixed notices', () => {
    const { rerender } = render(<LoginFeedback failure="credentials" />);
    expect(screen.getByRole('alert')).toHaveTextContent('账号或凭据不正确');

    rerender(<LoginFeedback notice="password-changed" />);
    expect(screen.getByRole('status')).toHaveTextContent('密码修改成功，请使用新密码重新登录');
  });
});
```

- [ ] **步骤 2：运行测试确认组件尚不存在**

运行：

```powershell
npm test -- src/pages/user/login/components/loginVisuals.test.tsx
```

预期：FAIL，错误明确指向 `LoginFeedback`、`LoginSceneBackdrop` 或 `WechatQrConstructionPanel` 模块无法解析。

- [ ] **步骤 3：实现可复用品牌图形**

`LoginBrandMark.tsx` 使用参考页相同 SVG，不读取远程资产：

```tsx
import type { FC } from 'react';

export const LoginBrandMark: FC<{ className?: string }> = ({ className }) => (
  <span aria-hidden="true" className={className}>
    <svg fill="none" viewBox="0 0 24 24">
      <circle cx="12" cy="9" r="3.5" stroke="currentColor" strokeWidth="1.8" />
      <path
        d="M5 20c0-3.5 3-6 7-6s7 2.5 7 6"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
    </svg>
  </span>
);
```

- [ ] **步骤 4：实现三场景状态机**

`LoginSceneBackdrop.tsx` 固定场景数据和计时语义；手动点击增加 `cycleRevision`，确保重新计时：

```tsx
import { useEffect, useState } from 'react';
import styles from '../index.module.css';

const SCENES = [
  { id: 'model', label: 'AI 模特', sceneClass: styles.sceneModel },
  { id: 'live', label: '直播克隆', sceneClass: styles.sceneLive },
  { id: 'voice', label: '语音合成', sceneClass: styles.sceneVoice },
] as const;

export function LoginSceneBackdrop() {
  const [activeIndex, setActiveIndex] = useState(0);
  const [cycleRevision, setCycleRevision] = useState(0);
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sync = () => setReducedMotion(media.matches);
    sync();
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  useEffect(() => {
    if (reducedMotion) return undefined;
    const timer = window.setInterval(() => {
      setActiveIndex((index) => (index + 1) % SCENES.length);
    }, 6000);
    return () => window.clearInterval(timer);
  }, [cycleRevision, reducedMotion]);

  return (
    <>
      <div aria-hidden="true" className={styles.scenes}>
        {SCENES.map((scene, index) => (
          <div
            className={`${styles.scene} ${scene.sceneClass} ${index === activeIndex ? styles.sceneActive : ''}`}
            key={scene.id}
          />
        ))}
      </div>
      <div aria-hidden="true" className={styles.grain} />
      <aside aria-label="创作场景" className={styles.indicators}>
        <div className={styles.dots}>
          {SCENES.map((scene, index) => (
            <button
              aria-label={`切换到${scene.label}场景`}
              aria-pressed={index === activeIndex}
              className={styles.dotTarget}
              key={scene.id}
              onClick={() => {
                setActiveIndex(index);
                setCycleRevision((revision) => revision + 1);
              }}
              type="button"
            >
              <span className={styles.dot} />
            </button>
          ))}
        </div>
        <div className={styles.caption}>
          {SCENES.map((scene, index) => (
            <span aria-current={index === activeIndex ? 'true' : undefined} key={scene.id}>
              {index > 0 && <span aria-hidden="true" className={styles.captionSeparator}>·</span>}
              {scene.label}
            </span>
          ))}
        </div>
      </aside>
    </>
  );
}
```

- [ ] **步骤 5：实现确定性二维码占位**

`WechatQrConstructionPanel.tsx` 使用固定 seed 生成圆点，不读取时间、不编码 URL、不注册事件：

```tsx
import styles from '../index.module.css';

const QR_SIZE = 156;
const MODULES = 25;
const CELL = QR_SIZE / MODULES;

function pseudoRandom(seed: number) {
  const value = Math.sin(seed * 9301 + 49297) * 233280;
  return value - Math.floor(value);
}

function isFinder(row: number, column: number) {
  return (
    (row < 7 && column < 7) ||
    (row < 7 && column >= MODULES - 7) ||
    (row >= MODULES - 7 && column < 7)
  );
}

const DATA_MODULES = Array.from({ length: MODULES * MODULES }, (_, index) => ({
  column: index % MODULES,
  row: Math.floor(index / MODULES),
})).filter(({ column, row }) => !isFinder(row, column) && pseudoRandom(row * MODULES + column + 17) > 0.52);

function Finder({ column, row }: { column: number; row: number }) {
  const x = column * CELL;
  const y = row * CELL;
  return (
    <g>
      <rect fill="#1d1d1f" height={CELL * 7} rx={CELL * 1.4} width={CELL * 7} x={x} y={y} />
      <rect fill="#fff" height={CELL * 5} rx={CELL} width={CELL * 5} x={x + CELL} y={y + CELL} />
      <rect fill="#0071e3" height={CELL * 3} rx={CELL * 0.7} width={CELL * 3} x={x + CELL * 2} y={y + CELL * 2} />
    </g>
  );
}

function Alignment() {
  const x = (MODULES - 9) * CELL;
  const y = (MODULES - 9) * CELL;
  return (
    <g>
      <rect fill="#1d1d1f" height={CELL * 5} rx={CELL} width={CELL * 5} x={x} y={y} />
      <rect fill="#fff" height={CELL * 3} rx={CELL * 0.7} width={CELL * 3} x={x + CELL} y={y + CELL} />
      <rect fill="#0071e3" height={CELL} rx={CELL * 0.3} width={CELL} x={x + CELL * 2} y={y + CELL * 2} />
    </g>
  );
}

export function WechatQrConstructionPanel() {
  return (
    <div className={styles.qrPanel}>
      <div className={styles.qrFrame}>
        <svg aria-hidden="true" className={styles.qrPattern} data-qr-placeholder viewBox={`0 0 ${QR_SIZE} ${QR_SIZE}`}>
          <rect fill="#fff" height={QR_SIZE} width={QR_SIZE} />
          {DATA_MODULES.map(({ column, row }) => (
            <circle cx={(column + 0.5) * CELL} cy={(row + 0.5) * CELL} fill="#1d1d1f" key={`${row}-${column}`} r={CELL * 0.42} />
          ))}
          <Finder column={0} row={0} />
          <Finder column={MODULES - 7} row={0} />
          <Finder column={0} row={MODULES - 7} />
          <Alignment />
        </svg>
        <div aria-label="微信扫码登录建设中" className={styles.qrConstruction} role="status">
          建设中
        </div>
      </div>
      <p className={styles.qrTip}>请使用 <strong>微信</strong> 扫描二维码登录</p>
    </div>
  );
}
```

- [ ] **步骤 6：实现固定反馈语义**

`LoginFeedback.tsx` 导出稳定类型，并把错误与通知映射为固定文案；容器由 CSS 固定在 `top:80px`，不进入卡片流：

```tsx
import { useEffect, useState } from 'react';
import styles from '../index.module.css';

export type LoginFailure = 'client-unavailable' | 'credentials' | 'network' | 'session-verification';
export type LoginNotice = 'password-changed' | 'session-revoked';

const FAILURE_COPY: Record<LoginFailure, string> = {
  'client-unavailable': '登录客户端不可用，请确认当前客户端可用后再重试。',
  credentials: '账号或凭据不正确',
  network: '网络连接不可用，请检查网络后重试。',
  'session-verification': '登录状态验证失败，请稍后重试。',
};

const NOTICE_COPY: Record<LoginNotice, string> = {
  'password-changed': '密码修改成功，请使用新密码重新登录',
  'session-revoked': '当前设备的登录会话已退出，请重新登录。',
};

export function LoginFeedback({ failure, notice }: { failure?: LoginFailure; notice?: LoginNotice }) {
  const [visible, setVisible] = useState(Boolean(failure || notice));

  useEffect(() => {
    if (!failure && !notice) {
      setVisible(false);
      return undefined;
    }
    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), 2600);
    return () => window.clearTimeout(timer);
  }, [failure, notice]);

  if (!visible) return null;
  return (
    <div className={styles.feedbackStack}>
      {notice && <div className={styles.toast} role="status">{NOTICE_COPY[notice]}</div>}
      {failure && <div className={styles.toast} role="alert">{FAILURE_COPY[failure]}</div>}
    </div>
  );
}
```

- [ ] **步骤 7：移植页面局部样式**

`index.module.css` 必须逐值移植已批准变体的场景、暗角、颗粒、顶栏、Hero、卡片、二维码和 Toast；同时把 Ant Design 公开 `classNames` 指向这些类。至少固定以下不可推断值：

```css
.loginPage { min-height: 100dvh; overflow-x: hidden; overflow-y: auto; background: #000; color: #f5f5f7; }
.stage { position: relative; z-index: 10; min-height: 100dvh; display: grid; grid-template-columns: minmax(0, 1fr) 420px; align-items: center; padding: 90px 56px; }
.card { width: 420px; padding: 36px 32px 30px; border: 1px solid rgba(255,255,255,.12); border-radius: 18px; background: rgba(20,20,24,.55); backdrop-filter: blur(40px) saturate(1.4); }
.tabsHeader { height: 48px; margin: 0 0 22px; padding: 4px; border-radius: 12px; background: rgba(0,0,0,.3); }
.field { min-height: 66px; margin-bottom: 14px; }
.submitButton { width: 100%; height: 47px; border-radius: 980px; background: #0071e3; font-size: 15px; font-weight: 500; }
.qrFrame { position: relative; width: 180px; height: 180px; padding: 12px; border-radius: 12px; background: #fff; }
.qrPattern { display: block; width: 156px; height: 156px; }
.qrConstruction { position: absolute; inset: 0; z-index: 5; display: grid; place-items: center; border-radius: 12px; background: rgba(86,86,91,.72); backdrop-filter: blur(2px); color: #fff; font-size: 18px; font-weight: 600; letter-spacing: .08em; }
.feedbackStack { position: fixed; top: 80px; left: 50%; z-index: 30; display: grid; gap: 8px; transform: translateX(-50%); }
.toast { padding: 12px 20px; border: 1px solid rgba(255,255,255,.12); border-radius: 980px; background: rgba(20,20,24,.92); box-shadow: 0 10px 40px rgba(0,0,0,.5); backdrop-filter: blur(20px); font-size: 14px; font-weight: 500; }
.dotTarget { display: grid; width: 32px; height: 32px; place-items: center; border: 0; background: transparent; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(255,255,255,.45); }
.dotTarget[aria-pressed='true'] .dot { width: 28px; border-radius: 4px; background: #fff; }
@media (max-width: 960px) { .heroCopy { display: none; } .stage { grid-template-columns: 1fr; justify-items: center; padding: 80px 24px; } }
@media (max-width: 480px) { .topnav { padding: 18px 20px; } .cardWrap { width: 100%; max-width: 380px; } .card { width: 100%; padding: 28px 22px 24px; } .stage { padding: 70px 20px; } }
@media (max-height: 700px) { .stage { align-items: start; padding-top: 72px; padding-bottom: 32px; } }
@media (prefers-reduced-motion: reduce) { .scene, .toast, .tabIndicator { animation: none; transition: none; } }
```

场景渐变、Hero 字体、卡片阴影、输入图标和焦点环直接从 SHA 已锁定的 approved v3 文件移植；不得用全局 `body`、`html`、`.ant-*` 选择器补样式。

- [ ] **步骤 8：运行基础组件测试并修到通过**

运行：

```powershell
npm test -- src/pages/user/login/components/loginVisuals.test.tsx
```

预期：1 个文件、4 个测试全部 PASS；没有 act warning、定时器泄漏或未知 DOM 属性 warning。

- [ ] **步骤 9：提交视觉基础组件**

```powershell
git add ai-video-ui/ai-video-webapp/src/pages/user/login/index.module.css
git add ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginBrandMark.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginSceneBackdrop.tsx
git add ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginFeedback.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/WechatQrConstructionPanel.tsx
git add ai-video-ui/ai-video-webapp/src/pages/user/login/components/loginVisuals.test.tsx
git commit -m "feat(login): add showcase visual primitives"
```

---

### 任务 3：收敛为密码登录与微信建设中页签

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/user/login/components/PasswordLoginPanel.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`
- 回归：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts`

- [ ] **步骤 1：把登录集成测试改成批准后的可见面**

删除只验证 SMS、邮箱、第三方和小程序页面渲染/提交的测试；保留 `api.test.ts` 中这些 Service 的合同测试。把 `@/components` 测试桩中的 Footer 改成 `<footer data-testid="shared-footer" />`，用于证明登录页不再渲染它。新增或改写以下断言：

```tsx
it('shows only password and WeChat QR login without postponed controls', () => {
  renderLogin();
  expect(screen.getByRole('tab', { name: '账号密码' })).toBeVisible();
  expect(screen.getByRole('tab', { name: '扫码登录' })).toBeVisible();
  expect(screen.getAllByRole('tab')).toHaveLength(2);
  expect(screen.queryByText('记住我')).not.toBeInTheDocument();
  expect(screen.queryByText('忘记密码？')).not.toBeInTheDocument();
  expect(screen.queryByText('其他方式')).not.toBeInTheDocument();
  expect(screen.queryByText('立即注册')).not.toBeInTheDocument();
  expect(screen.queryByText('短信验证码登录')).not.toBeInTheDocument();
  expect(screen.queryByText('邮箱验证码登录')).not.toBeInTheDocument();
  expect(screen.queryByText('第三方授权登录')).not.toBeInTheDocument();
  expect(screen.queryByText('微信小程序登录')).not.toBeInTheDocument();
  expect(screen.getByText('了解更多 ›').closest('a')).toBeNull();
  expect(screen.queryByTestId('shared-footer')).not.toBeInTheDocument();
});

it('keeps the empty login button enabled but does not submit invalid values', () => {
  renderLogin();
  const submit = screen.getByRole('button', { name: '登录' });
  expect(submit).toBeEnabled();
  fireEvent.click(submit);
  expect(mockAuthLogin).not.toHaveBeenCalled();
});

it('always saves password login as a nonpersistent session', async () => {
  renderLogin();
  fillCredentials();
  submitLogin();
  await waitFor(() => {
    expect(mockAuthSessionSave).toHaveBeenCalledWith({
      accessToken: 'app-access-token',
      persistent: false,
    });
  });
});

it('does not invoke any login API from the QR construction panel', () => {
  renderLogin();
  fireEvent.click(screen.getByRole('tab', { name: '扫码登录' }));
  expect(screen.getByRole('status', { name: '微信扫码登录建设中' })).toBeVisible();
  expect(mockAuthLogin).not.toHaveBeenCalled();
  expect(mockAuthRequestVerificationCode).not.toHaveBeenCalled();
  expect(mockAuthSmsLogin).not.toHaveBeenCalled();
  expect(mockAuthEmailLogin).not.toHaveBeenCalled();
  expect(mockAuthSocialLogin).not.toHaveBeenCalled();
  expect(mockAuthMiniProgramLogin).not.toHaveBeenCalled();
});
```

同时把现有成功路径中 `persistent: true` 的断言统一改为 `persistent: false`；通知断言用 `role=status`，登录失败仍用 `role=alert`。保留重复提交、`/me` 顺序、403 保留 Token、非 403 清理 Token、46129 不误删预存会话、网络失败和安全回跳全部测试。

- [ ] **步骤 2：运行登录测试确认旧页面不符合新合同**

运行：

```powershell
npm test -- src/pages/user/login/index.test.tsx
```

预期：FAIL，至少包含找不到“账号密码”/“扫码登录”、空表单按钮仍禁用或 `persistent` 仍为 true 的失败。

- [ ] **步骤 3：实现 Ant Design 密码面板**

`PasswordLoginPanel.tsx` 使用 Ant Design `Form`、`Input`、`Button`，不再暴露 persistence 字段：

```tsx
import { Button, Form, Input } from 'antd';
import { useState } from 'react';
import { authApi } from '@/services/ai-video/auth/api';
import type { LoginResult } from '@/services/ai-video/auth/types';
import styles from '../index.module.css';

type Values = { identifier: string; password: string };
type Authenticate = (request: () => Promise<LoginResult>) => Promise<void>;

export function PasswordLoginPanel({ authenticate, submitting }: { authenticate: Authenticate; submitting: boolean }) {
  const [passwordVisible, setPasswordVisible] = useState(false);
  const submit = ({ identifier, password }: Values) => {
    const normalizedIdentifier = identifier?.trim();
    if (!normalizedIdentifier || !password) return;
    void authenticate(() => authApi.login({ identifier: normalizedIdentifier, password }));
  };

  return (
    <Form<Values>
      classNames={{ root: styles.loginForm }}
      disabled={submitting}
      layout="vertical"
      onFinish={submit}
      requiredMark={false}
      scrollToFirstError={{ focus: true }}
    >
      <Form.Item className={styles.field} label="账号" name="identifier" rules={[
        { message: '请输入用户名、手机号或邮箱。', required: true },
        { message: '账号不能为空白。', whitespace: true },
      ]}>
        <Input autoComplete="username" className={styles.input} placeholder="手机号 / 邮箱" />
      </Form.Item>
      <Form.Item className={styles.field} label="密码" name="password" rules={[{ message: '请输入密码。', required: true }]}>
        <Input
          autoComplete="current-password"
          className={styles.input}
          placeholder="请输入密码"
          suffix={(
            <button
              aria-label={passwordVisible ? '隐藏密码' : '显示密码'}
              className={styles.passwordToggle}
              onClick={() => setPasswordVisible((visible) => !visible)}
              type="button"
            >
              <svg aria-hidden="true" fill="none" viewBox="0 0 24 24">
                <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" stroke="currentColor" strokeWidth="1.7" />
                <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.7" />
              </svg>
            </button>
          )}
          type={passwordVisible ? 'text' : 'password'}
        />
      </Form.Item>
      <Button
        block
        classNames={{ root: styles.submitButton }}
        disabled={submitting}
        htmlType="submit"
        loading={submitting}
        type="primary"
      >
        登录
      </Button>
    </Form>
  );
}
```

密码显隐按钮必须保留明确中文 `aria-label` 和键盘焦点。

- [ ] **步骤 4：重写页面组合但保留认证编排**

`index.tsx` 删除验证码、第三方、小程序类型、辅助函数、表单组件、`Link`、Pro LoginForm、`Footer` 与旧 `useStyles`。保留并原样复用 `getSafeRedirectUrl`、`getLoginFailure`、`isForbidden` 和 `authenticate` 的双层 try/catch；只把 `Authenticate` 改为单参数，并在唯一保存点固定 `persistent:false`：

```tsx
type LoginMethod = 'password' | 'wechat-qr';
type Authenticate = (request: () => Promise<LoginResult>) => Promise<void>;

const authenticate: Authenticate = async (request) => {
  if (loginInFlight.current) return;
  loginInFlight.current = true;
  setFailure(undefined);
  setSubmitting(true);
  try {
    const result = await request();
    authSession.save({ accessToken: result.access_token, persistent: false });
    try {
      const userInfo = await authApi.me();
      if (!userInfo) {
        authSession.clear();
        setFailure('session-verification');
        return;
      }
      flushSync(() => {
        setInitialState((state) => ({
          ...state,
          accessDenied: false,
          currentUser: userInfo,
          verificationFailed: false,
        }));
      });
    } catch (error) {
      if (isForbidden(error)) {
        flushSync(() => {
          setInitialState((state) => ({
            ...state,
            accessDenied: true,
            currentUser: undefined,
            verificationFailed: false,
          }));
        });
        history.replace(STUDIO_PATH);
        return;
      }
      authSession.clear();
      setFailure(getLoginFailure(error));
      return;
    }
    const urlParams = new URL(window.location.href).searchParams;
    history.replace(getSafeRedirectUrl(urlParams.get('redirect')));
  } catch (error) {
    setFailure(getLoginFailure(error));
  } finally {
    loginInFlight.current = false;
    setSubmitting(false);
  }
};
```

页面组合固定为：`Helmet` 标题、场景层、顶栏、Hero、玻璃卡片、Ant Design Tabs、反馈层。Tabs 使用 CLI 已确认的公开语义类：

```tsx
const tabItems = [
  {
    children: <PasswordLoginPanel authenticate={authenticate} submitting={submitting} />,
    disabled: submitting,
    key: 'password',
    label: '账号密码',
  },
  {
    children: <WechatQrConstructionPanel />,
    disabled: submitting,
    key: 'wechat-qr',
    label: '扫码登录',
  },
];

return (
  <main className={styles.loginPage}>
    <Helmet><title>素造智能体 · 开启你的创作</title></Helmet>
    <LoginSceneBackdrop />
    <header className={styles.topnav}>
      <div className={styles.brand}><LoginBrandMark className={styles.brandMark} /><span>素造智能体</span></div>
      <span className={styles.learnMore}>了解更多 ›</span>
    </header>
    <div className={styles.stage}>
      <section className={styles.heroCopy}>
        <span className={styles.heroEyebrow}>NEW · 数字人 3.0</span>
        <h1 className={styles.heroTitle}>让创意<br /><span className={styles.heroAccent}>化为数字生命</span></h1>
        <p className={styles.heroSub}>从形象生成到实时直播，一站式 AI 数字人创作平台。现在登录，开启你的创作之旅。</p>
      </section>
      <section aria-labelledby="login-card-title" className={styles.cardWrap}>
        <div className={styles.card}>
          <div className={styles.cardBrand}><LoginBrandMark className={styles.brandMark} /><span>素造智能体</span></div>
          <h2 className={styles.cardTitle} id="login-card-title">开启你的创作</h2>
          <p className={styles.cardSub}>登录以继续使用数字人创作工具</p>
          <Tabs
            activeKey={activeMethod}
            animated={false}
            classNames={{
              body: styles.tabsBody,
              content: styles.tabsContent,
              header: styles.tabsHeader,
              indicator: styles.tabIndicator,
              item: styles.tabItem,
              root: styles.tabsRoot,
            }}
            items={tabItems}
            onChange={(key) => setActiveMethod(key as LoginMethod)}
          />
        </div>
      </section>
    </div>
    <LoginFeedback failure={failure} notice={notice} />
  </main>
);
```

- [ ] **步骤 5：更新测试桩到 Ant Design Form/Input/Button/Tabs 公开形状**

在 `index.test.tsx` 的 `vi.mock('antd')` 中加入一个最小 Form context：Input `onChange` 写入值；Form `onSubmit` 调用 `onFinish(values)`；Button 映射 `htmlType` 到原生 `type`；Tabs 为每个 tab 设置 `id`、`aria-controls`，活动 panel 设置 `aria-labelledby`。测试桩不得实现业务逻辑，`PasswordLoginPanel.submit` 自己继续做空值保护。

- [ ] **步骤 6：运行登录页与认证合同测试**

运行：

```powershell
npm test -- src/pages/user/login/index.test.tsx src/pages/user/login/components/loginVisuals.test.tsx src/services/ai-video/auth/api.test.ts
```

预期：3 个文件、测试数大于 0，全部 PASS。明确核对：

- login 请求只含 `{ identifier, password }`。
- `authSession.save` 始终收到 `persistent:false`。
- `/me` 成功后才同步用户并跳转。
- `/me` 403 不清 Token；空用户和非 403 清 Token。
- 匿名 login 的 46129 不清理预存会话。
- QR 页不调用任何 auth API。

- [ ] **步骤 7：确认认证实现文件没有差异并提交**

运行：

```powershell
git diff --exit-code -- ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts ai-video-ui/ai-video-webapp/src/services/ai-video/auth/session.ts ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts
git add ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx
git add ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx
git add ai-video-ui/ai-video-webapp/src/pages/user/login/components/PasswordLoginPanel.tsx
git commit -m "feat(login): rebuild creator login surface"
```

预期：`git diff --exit-code` 无输出且退出码 0；提交只包含登录页组件、样式和测试。

---

### 任务 4：统一用户端可见品牌

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/branding.test.ts`
- 修改：本计划“文件结构与职责”中列出的用户端配置、Footer、页面、工作台和八个语言包
- 删除：`ai-video-ui/ai-video-webapp/src/pages/user/login/__snapshots__/login.test.tsx.snap`

- [ ] **步骤 1：先写用户端品牌合同测试**

`src/branding.test.ts` 读取真实文件，不 mock 运行时文案：

```ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');

describe('user-facing product branding', () => {
  it.each([
    ['config/config.ts', "title: '素造智能体'"],
    ['config/defaultSettings.ts', "title: '素造智能体'"],
    ['src/components/Footer/index.tsx', '素造智能体 &copy; {year}'],
    ['src/pages/digital-human-studio/components/StudioSider.tsx', '素造智能体'],
    ['src/pages/Welcome.tsx', '欢迎使用素造智能体'],
    ['src/pages/Admin.tsx', '素造智能体'],
    ['src/pages/chatbot/index.tsx', '🚀 素造智能体如何接入后端权限系统？'],
  ])('%s contains the approved brand', (path, expected) => {
    expect(read(path)).toContain(expected);
  });

  it('uses the approved PWA names', () => {
    const manifest = JSON.parse(read('src/manifest.json')) as { name: string; short_name: string };
    expect(manifest).toMatchObject({ name: '素造智能体', short_name: '素造智能体' });
  });

  it.each([
    ['src/locales/zh-CN/pages.ts', '欢迎使用素造智能体'],
    ['src/locales/zh-TW/pages.ts', '歡迎使用素造智能体'],
    ['src/locales/en-US/pages.ts', 'Welcome to 素造智能体'],
    ['src/locales/ja-JP/pages.ts', '素造智能体へようこそ'],
    ['src/locales/pt-BR/pages.ts', 'Bem-vindo ao 素造智能体'],
    ['src/locales/id-ID/pages.ts', 'Selamat datang di 素造智能体'],
    ['src/locales/fa-IR/pages.ts', 'به 素造智能体 خوش آمدید'],
    ['src/locales/bn-BD/pages.ts', '素造智能体-এ স্বাগতম'],
  ])('%s uses the localized welcome copy', (path, expected) => {
    expect(read(path)).toContain(expected);
    expect(read(path)).not.toContain('{v6}');
  });

  it('keeps OpenAPI metadata outside the product rename', () => {
    expect(read('config/oneapi.json')).toContain(['Ant', 'Design', 'Pro'].join(' '));
  });
});
```

- [ ] **步骤 2：运行测试确认旧品牌仍然失败**

运行：

```powershell
npm test -- src/branding.test.ts
```

预期：FAIL，失败值包含旧的 `Ant Design Pro` 或 `Digital Human`。

- [ ] **步骤 3：按显式映射替换用户端品牌**

写入以下精确值：

- `config/config.ts`、`config/defaultSettings.ts`：`素造智能体`
- `src/manifest.json` 的 `name`/`short_name`：`素造智能体`
- Footer：`素造智能体 &copy; {year}`
- StudioSider `.brand-name`：`素造智能体`；`AI 数字人创作平台` 保持不变
- Welcome 默认中文：`欢迎使用素造智能体`
- Admin 品牌：`素造智能体`
- chatbot：`🚀 素造智能体如何接入后端权限系统？`
- zh-CN：`欢迎使用素造智能体`
- zh-TW：`歡迎使用素造智能体`
- en-US：`Welcome to 素造智能体`
- ja-JP：`素造智能体へようこそ`
- pt-BR：`Bem-vindo ao 素造智能体`
- id-ID：`Selamat datang di 素造智能体`
- fa-IR：`به 素造智能体 خوش آمدید`
- bn-BD：`素造智能体-এ স্বাগতম`

八个欢迎语全部删除 `{v6}`。

- [ ] **步骤 4：证明旧快照无人引用后删除**

运行：

```powershell
rg -n "toMatchSnapshot|toMatchInlineSnapshot" src/pages/user/login/index.test.tsx
```

预期：无匹配。随后用补丁删除 `src/pages/user/login/__snapshots__/login.test.tsx.snap`，不删除整个目录中的其他文件。

- [ ] **步骤 5：运行用户端品牌与登录回归测试**

运行：

```powershell
npm test -- src/branding.test.ts src/pages/user/login/index.test.tsx src/pages/user/login/components/loginVisuals.test.tsx
```

预期：3 个文件全部 PASS；Footer 新品牌通过，但登录 DOM 仍不存在 Footer。

- [ ] **步骤 6：提交用户端品牌更新**

```powershell
git add ai-video-ui/ai-video-webapp/config/config.ts ai-video-ui/ai-video-webapp/config/defaultSettings.ts
git add ai-video-ui/ai-video-webapp/src/manifest.json ai-video-ui/ai-video-webapp/src/branding.test.ts
git add ai-video-ui/ai-video-webapp/src/components/Footer/index.tsx
git add ai-video-ui/ai-video-webapp/src/pages/Welcome.tsx ai-video-ui/ai-video-webapp/src/pages/Admin.tsx ai-video-ui/ai-video-webapp/src/pages/chatbot/index.tsx
git add ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx
git add ai-video-ui/ai-video-webapp/src/locales/bn-BD/pages.ts ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts ai-video-ui/ai-video-webapp/src/locales/fa-IR/pages.ts ai-video-ui/ai-video-webapp/src/locales/id-ID/pages.ts
git add ai-video-ui/ai-video-webapp/src/locales/ja-JP/pages.ts ai-video-ui/ai-video-webapp/src/locales/pt-BR/pages.ts ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts ai-video-ui/ai-video-webapp/src/locales/zh-TW/pages.ts
git add -u ai-video-ui/ai-video-webapp/src/pages/user/login/__snapshots__/login.test.tsx.snap
git commit -m "chore(branding): rename user-facing product"
```

---

### 任务 5：统一平台端可见品牌

**文件：**
- 创建：`ai-video-ui/ai-video-platform-ui/tests/branding.test.ts`
- 修改：`ai-video-ui/ai-video-platform-ui/.env.development`
- 修改：`ai-video-ui/ai-video-platform-ui/.env.production`
- 修改：`ai-video-ui/ai-video-platform-ui/src/utils/env.ts`
- 修改：`ai-video-ui/ai-video-platform-ui/vite.config.ts`
- 修改：`ai-video-ui/ai-video-platform-ui/src/pages/index.tsx`

- [ ] **步骤 1：先写平台端品牌合同测试**

```ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8');

describe('platform product branding', () => {
  it.each(['.env.development', '.env.production'])('%s owns the approved titles', (path) => {
    const content = read(path);
    expect(content).toContain('VITE_APP_TITLE=素造智能体后台管理系统');
    expect(content).toContain('VITE_APP_LOGO_TITLE=素造智能体');
    expect(content).toContain('VITE_APP_CLIENT_ID=e5cd7e4891bf95d1d19206ce24a7b32e');
  });

  it('uses matching runtime and build fallbacks', () => {
    expect(read('src/utils/env.ts')).toContain("'素造智能体后台管理系统'");
    expect(read('src/utils/env.ts')).toContain("'素造智能体'");
    expect(read('vite.config.ts')).toContain("'素造智能体后台管理系统'");
  });

  it('shows the approved console title', () => {
    expect(read('src/pages/index.tsx')).toContain('素造智能体控制台');
  });

  it('keeps the approved external repository URL', () => {
    const repositoryName = ['AI', 'Video'].join('-');
    expect(read('src/components/layout/ExternalLinkButton.tsx')).toContain(`https://gitee.com/dromara/${repositoryName}`);
  });
});
```

- [ ] **步骤 2：运行测试确认旧平台标题失败**

从 `ai-video-ui/ai-video-platform-ui` 运行：

```powershell
pnpm test -- tests/branding.test.ts
```

预期：FAIL，显示旧 `AI-Video后台管理系统`、`AI-Video` 或 `AI-Video 控制台`。

- [ ] **步骤 3：只改平台品牌字段**

精确写入：

```text
VITE_APP_TITLE=素造智能体后台管理系统
VITE_APP_LOGO_TITLE=素造智能体
```

`src/utils/env.ts` 与 `vite.config.ts` 使用相同标题回退；`src/pages/index.tsx` 使用 `素造智能体控制台`。两个 `.env` 的 `VITE_APP_CLIENT_ID`、API、RSA、消息和监控配置逐字保持原值。

- [ ] **步骤 4：运行平台品牌测试与现有 smoke**

```powershell
pnpm test -- tests/branding.test.ts tests/vitest.smoke.test.ts
```

预期：2 个文件、测试数大于 0，全部 PASS。

- [ ] **步骤 5：确认平台认证文件没有差异并提交**

运行：

```powershell
git diff --exit-code -- ai-video-ui/ai-video-platform-ui/src/utils/auth.ts ai-video-ui/ai-video-platform-ui/src/api/request.ts ai-video-ui/ai-video-platform-ui/src/api/login.ts
git add ai-video-ui/ai-video-platform-ui/.env.development
git add ai-video-ui/ai-video-platform-ui/.env.production
git add ai-video-ui/ai-video-platform-ui/src/utils/env.ts
git add ai-video-ui/ai-video-platform-ui/vite.config.ts
git add ai-video-ui/ai-video-platform-ui/src/pages/index.tsx
git add ai-video-ui/ai-video-platform-ui/tests/branding.test.ts
git commit -m "chore(branding): rename platform product"
```

预期：认证文件 diff 命令退出码 0；提交不包含 `src/utils/auth.ts`、`src/api/request.ts` 或 `src/api/login.ts`。

---

### 任务 6：增加跨包品牌守卫并完成静态验证

**文件：**
- 创建：`ai-video-ui/scripts/verify-frontend-branding.mjs`
- 验证：两个前端包全部受影响文件

- [ ] **步骤 1：实现允许清单驱动的品牌扫描脚本**

脚本只扫描运行时代码、配置、环境、Manifest、语言包和测试；不扫描 README、package/锁文件；不扫描 `config/oneapi.json`；对 ExternalLinkButton 只移除已批准 URL 后再检查：

```js
import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const uiRoot = path.resolve(fileURLToPath(new URL('..', import.meta.url)));
const targets = [
  'ai-video-webapp/config/config.ts',
  'ai-video-webapp/config/defaultSettings.ts',
  'ai-video-webapp/src',
  'ai-video-platform-ui/.env.development',
  'ai-video-platform-ui/.env.production',
  'ai-video-platform-ui/src',
  'ai-video-platform-ui/vite.config.ts',
];
const denied = ['数字人工作室', 'Ant Design Pro', 'Digital Human', 'AI-Video'];
const allowed = new Map([
  ['ai-video-platform-ui/src/components/layout/ExternalLinkButton.tsx', ['https://gitee.com/dromara/AI-Video']],
]);

async function collect(target) {
  const absolute = path.join(uiRoot, target);
  const info = await stat(absolute);
  if (info.isFile()) {
    return /\.(css|json|svg|ts|tsx)$/.test(absolute) || path.basename(absolute).startsWith('.env') ? [absolute] : [];
  }
  const entries = await readdir(absolute, { withFileTypes: true });
  const nested = await Promise.all(entries
    .filter((entry) => entry.name !== 'node_modules' && entry.name !== 'dist')
    .map((entry) => collect(path.join(target, entry.name))));
  return nested.flat();
}

const files = (await Promise.all(targets.map(collect))).flat();
const violations = [];
for (const file of files) {
  const relative = path.relative(uiRoot, file).replaceAll('\\', '/');
  let content = await readFile(file, 'utf8');
  for (const value of allowed.get(relative) ?? []) content = content.replaceAll(value, '');
  const lines = content.split(/\r?\n/);
  for (const [index, line] of lines.entries()) {
    for (const value of denied) {
      if (line.includes(value)) violations.push(`${relative}:${index + 1}: ${value}`);
    }
  }
}

if (violations.length > 0) {
  console.error(violations.join('\n'));
  process.exitCode = 1;
} else {
  console.log('FRONTEND_BRANDING_OK');
}
```

- [ ] **步骤 2：运行品牌扫描**

从仓库根目录运行：

```powershell
node ai-video-ui/scripts/verify-frontend-branding.mjs
```

预期：只输出 `FRONTEND_BRANDING_OK`。若发现测试描述或旧快照中的模板品牌，同样清理；不得把 `oneapi.json` 或第三方 URL 纳入替换。

- [ ] **步骤 3：运行用户端完整静态门禁**

从 `ai-video-ui/ai-video-webapp` 运行：

```powershell
npm test -- src/pages/user/login/index.test.tsx src/pages/user/login/components/loginVisuals.test.tsx src/services/ai-video/auth/api.test.ts src/branding.test.ts
npm run lint
npx antd lint ./src
npm run build
```

预期：测试文件数和测试数均大于 0；Vitest、Biome、TypeScript、Ant Design lint 和 Umi build 全部退出码 0。

当前 Codex shell 没有 npm 时使用已验证的只读等价命令，不生成新锁文件：

```powershell
$taskNodeBin = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin'
$env:PATH = "$taskNodeBin;$env:PATH"
.\node_modules\.bin\vitest.cmd run src/pages/user/login/index.test.tsx src/pages/user/login/components/loginVisuals.test.tsx src/services/ai-video/auth/api.test.ts src/branding.test.ts
.\node_modules\.bin\biome.cmd lint .
.\node_modules\.bin\tsc.cmd --noEmit
.\node_modules\.bin\antd.cmd lint ./src
$env:UMI_ENV = 'dev'
.\node_modules\.bin\max.cmd build
```

- [ ] **步骤 4：运行平台端完整静态门禁**

从 `ai-video-ui/ai-video-platform-ui` 运行：

```powershell
pnpm test
pnpm run lint
pnpm exec oxfmt --check src config vite.config.ts
pnpm run build:prod
```

预期：全部退出码 0；Vitest 实际执行文件数和测试数均大于 0；格式命令为只读 `--check`。

- [ ] **步骤 5：检查工作区没有工具生成物**

运行：

```powershell
git status --short
git diff --check
```

预期：没有新生成的 `pnpm-lock.yaml`、`pnpm-workspace.yaml`、构建目录或格式化越界差异；只保留计划内文件。

- [ ] **步骤 6：提交品牌扫描守卫**

```powershell
git add ai-video-ui/scripts/verify-frontend-branding.mjs
git commit -m "test(branding): add cross-package brand guard"
```

---

### 任务 7：浏览器视觉、响应式与可访问性验收

**文件：**
- 验证：`D:\AI\login-05-showcase.html`
- 验证：`.superpowers/brainstorm/1560-1785698991/content/login-target-v3.html`
- 验证：用户端开发服务器 `/user/login`
- 不提交：`.superpowers/` 下的截图与差异产物

- [ ] **步骤 1：启动无 mock 用户端开发服务器**

从 `ai-video-ui/ai-video-webapp` 运行：

```powershell
npm run start:no-mock
```

当前 Codex shell 的等价启动命令：

```powershell
$taskNodeBin = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin'
$env:PATH = "$taskNodeBin;$env:PATH"
$env:MOCK = 'none'
$env:UMI_ENV = 'dev'
.\node_modules\.bin\max.cmd dev
```

预期：控制台给出本地 URL；首次打开 `/user/login` 不触发登录、二维码或轮询请求。

- [ ] **步骤 2：在同一 Chromium/Electron 会话固定视觉状态**

使用 in-app browser：

- 视口 `1440 × 900`、DPR 1、100% 缩放。
- 先打开 approved v3，再打开实现页。
- 点击第一场景指示器；在截图会话临时注入 `animation-play-state: paused !important; transition: none !important`，不修改源代码。
- 分别截账号密码态和扫码登录态的全视口图，保存为：
  - `.superpowers/verification/login/target-password-1440x900.png`
  - `.superpowers/verification/login/target-qr-1440x900.png`
  - `.superpowers/verification/login/actual-password-1440x900.png`
  - `.superpowers/verification/login/actual-qr-1440x900.png`

预期几何：密码卡 `420 × 491.80px`、`x≈964px`、`y≈204.09px`；扫码卡高 `≈509.80px`、`y≈195.09px`；密码第二字段到底部按钮间距 14px；二维码框 180px、图案 156px、蒙版全覆盖。

- [ ] **步骤 3：执行像素阈值与人工边界复核**

对 approved v3 与实现截图使用同一尺寸，逐像素计算每通道差异阈值 16，差异像素比例不得高于 1%。同时人工核对文字、组件边界、间距、卡片自然高度、蒙版和 Hero；任何结构差异即使低于 1% 也修复。

运行：

```powershell
$taskPython = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
@'
from pathlib import Path
from PIL import Image

root = Path('.superpowers/verification/login')
pairs = [
    ('password', root / 'target-password-1440x900.png', root / 'actual-password-1440x900.png'),
    ('qr', root / 'target-qr-1440x900.png', root / 'actual-qr-1440x900.png'),
]
failed = False
for name, expected_path, actual_path in pairs:
    expected = Image.open(expected_path).convert('RGB')
    actual = Image.open(actual_path).convert('RGB')
    if expected.size != actual.size:
        raise SystemExit(f'{name}: size mismatch {expected.size} != {actual.size}')
    changed = sum(
        any(abs(left - right) > 16 for left, right in zip(expected_pixel, actual_pixel))
        for expected_pixel, actual_pixel in zip(expected.getdata(), actual.getdata())
    )
    ratio = changed / (expected.width * expected.height)
    print(f'{name}: changed_ratio={ratio:.6%}')
    failed = failed or ratio > 0.01
if failed:
    raise SystemExit(1)
'@ | & $taskPython -
```

预期：密码态与扫码态均满足阈值，差异只来自同机字体抗锯齿；没有品牌、间距或卡片边界偏差。

- [ ] **步骤 4：验收响应式和短高可达性**

依次设置：

- `960 × 900`：Hero 隐藏、卡片居中。
- `390 × 844`：卡片不溢出、顶栏和指示器完整。
- `1280 × 600`：纵向可滚动、无水平滚动、登录按钮可达。
- 200% 缩放：表单和按钮仍可滚动到达。

每个视口用页面求值确认 `scrollWidth <= clientWidth`；短高时确认 `scrollHeight >= clientHeight` 且按钮矩形可通过纵向滚动进入视口。

- [ ] **步骤 5：验收键盘、减少动态和反馈状态**

- 只用键盘访问页签、账号、密码、密码显隐、登录按钮和场景按钮。
- 页签支持左右方向键、Home、End；Tab 进入当前 panel。
- 模拟 `prefers-reduced-motion: reduce`：18 秒后仍是第一场景，手动场景按钮仍有效。
- 空表单点击登录：按钮初始蓝色启用，出现字段错误，不发 API。
- 截图或目视核对凭据失败、客户端不可用、网络失败、密码修改通知、会话退出通知；错误为 alert，成功/信息为 status。
- 切换三种背景，确认字段、按钮、Toast 与焦点环对比度清晰。
- 登录路由的 `document.title` 为 `素造智能体 · 开启你的创作`；离开登录路由后恢复对应路由标题或全局 `素造智能体`，不得把登录副标题留在其他页面。

预期：所有可见交互具备键盘焦点；二维码 SVG `aria-hidden`，建设中面板为 status 且无按钮；“了解更多 ›”不进入 Tab 顺序。

- [ ] **步骤 6：停止开发服务器并确认没有未预期文件**

停止本次启动的进程后运行：

```powershell
git status --short
```

预期：截图只位于已忽略的 `.superpowers/`；没有运行时生成文件进入工作区。

---

### 任务 8：独立审查、最终安全核验与交付

**文件：**
- 审查：本计划全部变更
- 核验：七个禁止修改文件
- 核验：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、后端目录

- [ ] **步骤 1：执行前端视觉与可访问性只读审查**

使用 requesting-code-review，要求审查者只报告：React/Ant Design/ProComponents 边界、视觉规格、响应式、键盘、reduced-motion、品牌覆盖、测试与构建证据。按 `[必须修复]`、`[建议修改]`、`[仅供参考]` 分级。

- [ ] **步骤 2：执行认证安全只读审查**

要求独立审查者只报告：login/me 失败矩阵、`persistent=false`、Token 清理、403 保留、安全回跳、App/Admin key 与 Client ID 隔离、认证文件无差异、API 不变。

- [ ] **步骤 3：只修复审查确认的必修项并运行定向回归**

最多一轮修复；修复视觉项运行登录视觉/集成测试与浏览器定向复核，修复品牌项运行品牌测试/扫描，修复认证项运行 login + `api.test.ts`。修复后最多一次由原审查者定向复核，不开启递归全量审查。

- [ ] **步骤 4：逐项比较最终 SHA256 与范围**

重新运行任务 1 的七文件 SHA256 命令，与基线逐项一致；再运行：

```powershell
git diff --name-only 2c70e15..HEAD
git diff --exit-code 2c70e15..HEAD -- docs/API_CONTRACT.md docs/DOMAIN_MODEL.md docs/ASYNC_TASKS.md ai-video-api
node ai-video-ui/scripts/verify-frontend-branding.mjs
```

预期：公共契约和后端 diff 退出码 0；品牌扫描输出 `FRONTEND_BRANDING_OK`；七个会话/认证文件哈希不变。

- [ ] **步骤 5：最后一次运行关键门禁**

用户端：

```powershell
npm test -- src/pages/user/login/index.test.tsx src/pages/user/login/components/loginVisuals.test.tsx src/services/ai-video/auth/api.test.ts src/branding.test.ts
npm run lint
npm run build
```

平台端：

```powershell
pnpm test
pnpm run lint
pnpm run build:prod
```

预期：全部通过，实际测试数大于 0。

- [ ] **步骤 6：提交审查修复并形成交付记录**

若有修复：

```powershell
git diff --name-only
git add ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/index.module.css
git add ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginBrandMark.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginSceneBackdrop.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/LoginFeedback.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/PasswordLoginPanel.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/WechatQrConstructionPanel.tsx ai-video-ui/ai-video-webapp/src/pages/user/login/components/loginVisuals.test.tsx
git add ai-video-ui/ai-video-webapp/config/config.ts ai-video-ui/ai-video-webapp/config/defaultSettings.ts ai-video-ui/ai-video-webapp/src/manifest.json ai-video-ui/ai-video-webapp/src/branding.test.ts
git add ai-video-ui/ai-video-webapp/src/components/Footer/index.tsx ai-video-ui/ai-video-webapp/src/pages/Welcome.tsx ai-video-ui/ai-video-webapp/src/pages/Admin.tsx ai-video-ui/ai-video-webapp/src/pages/chatbot/index.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx
git add ai-video-ui/ai-video-webapp/src/locales/bn-BD/pages.ts ai-video-ui/ai-video-webapp/src/locales/en-US/pages.ts ai-video-ui/ai-video-webapp/src/locales/fa-IR/pages.ts ai-video-ui/ai-video-webapp/src/locales/id-ID/pages.ts ai-video-ui/ai-video-webapp/src/locales/ja-JP/pages.ts ai-video-ui/ai-video-webapp/src/locales/pt-BR/pages.ts ai-video-ui/ai-video-webapp/src/locales/zh-CN/pages.ts ai-video-ui/ai-video-webapp/src/locales/zh-TW/pages.ts
git add ai-video-ui/ai-video-platform-ui/.env.development ai-video-ui/ai-video-platform-ui/.env.production ai-video-ui/ai-video-platform-ui/src/utils/env.ts ai-video-ui/ai-video-platform-ui/vite.config.ts ai-video-ui/ai-video-platform-ui/src/pages/index.tsx ai-video-ui/ai-video-platform-ui/tests/branding.test.ts
git add ai-video-ui/scripts/verify-frontend-branding.mjs
git diff --cached --name-only
git commit -m "fix(login): close visual and auth review findings"
```

交付记录必须包含：完成项、未改 API/后端证据、两端测试/类型/lint/build 输出、浏览器视口与截图路径、品牌扫描结果、七文件 SHA 对比、剩余风险和阻塞项。没有修复时不创建空提交。

## 规格覆盖自检映射

- 视觉、精确几何、文案、响应式、动效：任务 2、3、7。
- 只保留账号密码与微信扫码建设中：任务 2、3。
- 记住我、忘记密码、注册和其他方式隐藏：任务 3。
- 非持久会话、login/me 失败矩阵、安全回跳：任务 1、3、8。
- React + Ant Design + Ant Design Pro / ProComponents 技术栈：任务 1、2、3、6。
- 用户端全局品牌：任务 4、6。
- 平台端全局品牌：任务 5、6。
- 后端/API/公共契约不变：任务 1、8。
- 自动化、构建、视觉、键盘与两项独立审查：任务 6、7、8。
