# Electron 用户端桌面薄壳实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 30 分钟硬上限内创建安全的 Electron 远程 Web 薄壳，并提供 Windows x64 NSIS 与 macOS x64/arm64 DMG 构建能力。

**架构：** `ai-video-desktop` 是独立 TypeScript 包。主进程把构建期固化的用户端 URL 装入唯一 `BrowserWindow`，纯策略函数负责 URL、导航、外链、下载链与文件名校验；会话层负责权限默认拒绝和系统“另存为”。Web 与后端不修改。

**技术栈：** Electron 43.3.0、TypeScript 7.0.2、esbuild 0.28.1、Vitest 4.1.10、electron-builder 26.15.3、pnpm 11。

---

## 文件结构

创建：

- `ai-video-desktop/package.json`：脚本、版本与固定依赖。
- `ai-video-desktop/pnpm-lock.yaml`：可复现依赖锁；因当前工作区只提供 pnpm，同步把规格中的 `package-lock.json` 更正为 `pnpm-lock.yaml`。
- `ai-video-desktop/tsconfig.json`：严格类型检查。
- `ai-video-desktop/electron-builder.yml`：NSIS、DMG、架构与产物命名。
- `ai-video-desktop/scripts/build.ts`：构建期 URL 校验并用 esbuild 固化配置。
- `ai-video-desktop/scripts/generate-icons.mjs`：从现有品牌 SVG 生成 PNG、ICO、ICNS。
- `ai-video-desktop/src/globals.d.ts`：构建期常量类型。
- `ai-video-desktop/src/main/webUrlPolicy.ts`：URL、origin 与协议纯策略。
- `ai-video-desktop/src/main/securityPolicy.ts`：主导航、外链与下载链纯策略。
- `ai-video-desktop/src/main/downloadPolicy.ts`：建议文件名净化。
- `ai-video-desktop/src/main/security.ts`：Electron 导航、外链、权限与证书处理。
- `ai-video-desktop/src/main/downloads.ts`：可信下载和系统“另存为”。
- `ai-video-desktop/src/main/createWindow.ts`：主窗口创建、加载失败与渲染崩溃处理。
- `ai-video-desktop/src/main/index.ts`：生命周期与单实例。
- `ai-video-desktop/src/preload/index.ts`：空能力面。
- `ai-video-desktop/tests/*.test.ts`：纯策略和打包配置测试。
- `ai-video-desktop/resources/icons/*`：品牌图标资源。

修改：

- `docs/superpowers/specs/2026-08-06-electron-desktop-shell-design.md`：锁文件和命令统一为 pnpm。
- `docs/ARCHITECTURE.md`：记录远程 Web、安全与下载流。
- `docs/FRONTEND_GUIDE.md`：记录开发/打包命令和环境变量。
- `README.md`：移除桌面包“待创建”。
- `.gitignore`：忽略桌面产物与敏感签名材料。

不修改：`ai-video-ui/**`、`ai-video-api/**`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`。

### 任务 1：包骨架与构建期 URL 策略

**风险：** 黄色中风险。目标为可复现包和构建期配置；不得触碰窗口权限、Web 或后端。

**文件：**
- 创建：`ai-video-desktop/package.json`
- 创建：`ai-video-desktop/tsconfig.json`
- 创建：`ai-video-desktop/scripts/build.ts`
- 创建：`ai-video-desktop/src/globals.d.ts`
- 创建：`ai-video-desktop/src/main/webUrlPolicy.ts`
- 测试：`ai-video-desktop/tests/webUrlPolicy.test.ts`
- 修改：`docs/superpowers/specs/2026-08-06-electron-desktop-shell-design.md`

- [ ] **步骤 1：编写 URL 策略失败测试**

```ts
import { describe, expect, it } from 'vitest';
import { resolveWebTarget } from '../src/main/webUrlPolicy';

describe('resolveWebTarget', () => {
  it('uses localhost only in development', () => {
    expect(resolveWebTarget(undefined, 'development').href).toBe('http://localhost:8000/');
  });
  it('requires HTTPS in production', () => {
    expect(() => resolveWebTarget('http://example.com', 'production')).toThrow('HTTPS');
  });
  it('rejects credentials', () => {
    expect(() => resolveWebTarget('https://user:password@example.com', 'production')).toThrow('凭据');
  });
});
```

- [ ] **步骤 2：运行测试并确认因模块不存在而失败**

运行：`pnpm --dir ai-video-desktop test -- tests/webUrlPolicy.test.ts`

预期：FAIL，无法解析 `webUrlPolicy`。

- [ ] **步骤 3：实现最小 URL 策略和 esbuild 注入**

```ts
export type BuildMode = 'development' | 'production';
export interface WebTarget { href: string; origin: string; mode: BuildMode }

export function resolveWebTarget(raw: string | undefined, mode: BuildMode): WebTarget {
  const value = raw?.trim() || (mode === 'development' ? 'http://localhost:8000' : '');
  if (!value) throw new Error('生产构建必须提供 AI_VIDEO_WEB_URL');
  const url = new URL(value);
  if (url.username || url.password) throw new Error('AI_VIDEO_WEB_URL 不得包含凭据');
  if (url.protocol === 'https:') return { href: url.href, origin: url.origin, mode };
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (mode === 'development' && url.protocol === 'http:' && loopback) {
    return { href: url.href, origin: url.origin, mode };
  }
  throw new Error('生产 AI_VIDEO_WEB_URL 必须使用 HTTPS');
}
```

`scripts/build.ts` 必须调用该函数，并通过 esbuild `define` 注入 `__AI_VIDEO_WEB_URL__` 与 `__AI_VIDEO_BUILD_MODE__`；打包脚本设置 `ELECTRON_BUILD_MODE=production`，开发脚本设置 `development`。

- [ ] **步骤 4：安装锁定依赖并运行测试、类型检查**

运行：`pnpm --dir ai-video-desktop install --frozen-lockfile=false && pnpm --dir ai-video-desktop test && pnpm --dir ai-video-desktop typecheck`

预期：全部 PASS，生成 `pnpm-lock.yaml`。

- [ ] **步骤 5：提交任务 1**

```bash
git add ai-video-desktop docs/superpowers/specs/2026-08-06-electron-desktop-shell-design.md
git commit -m "feat(Electron): 初始化桌面薄壳构建配置"
```

### 任务 2：安全策略与“另存为”下载

**风险：** 红色高风险。命中远程信任边界和本地文件写入；一名实施者，完成后由独立审查者做一次安全审查。

**文件：**
- 创建：`ai-video-desktop/src/main/securityPolicy.ts`
- 创建：`ai-video-desktop/src/main/downloadPolicy.ts`
- 创建：`ai-video-desktop/src/main/security.ts`
- 创建：`ai-video-desktop/src/main/downloads.ts`
- 测试：`ai-video-desktop/tests/securityPolicy.test.ts`
- 测试：`ai-video-desktop/tests/downloadPolicy.test.ts`

- [ ] **步骤 1：编写安全与下载失败测试**

```ts
expect(isAllowedMainNavigation('https://app.example.com/a', target)).toBe(true);
expect(isAllowedMainNavigation('https://app.example.com.evil.test', target)).toBe(false);
expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false);
expect(isAllowedDownloadChain(['https://oss.example.com/a.mp4'], target)).toBe(true);
expect(isAllowedDownloadChain(['http://oss.example.com/a.mp4'], target)).toBe(false);
expect(sanitizeSuggestedFileName('../../CON?.mp4')).toBe('_CON_.mp4');
```

- [ ] **步骤 2：运行定向测试并确认失败**

运行：`pnpm --dir ai-video-desktop test -- tests/securityPolicy.test.ts tests/downloadPolicy.test.ts`

预期：FAIL，策略函数不存在。

- [ ] **步骤 3：实现纯策略**

```ts
export function isAllowedMainNavigation(raw: string, target: WebTarget): boolean {
  try { return new URL(raw).origin === target.origin; } catch { return false; }
}
export function isSafeExternalUrl(raw: string): boolean {
  try { const url = new URL(raw); return url.protocol === 'https:' && !url.username && !url.password; }
  catch { return false; }
}
```

下载链规则：生产只接受 `https:`；开发额外接受 loopback `http:`；`blob:` 的 origin 必须等于可信 Web origin。文件名去除路径、控制字符和 Windows 非法字符，保留扩展名，保留名加 `_` 前缀，最长 120 字符。

- [ ] **步骤 4：实现 Electron 安全接线和下载保存**

`security.ts` 必须：限制 `will-navigate`/`will-redirect`、拒绝新窗口、仅将安全 HTTPS 外链交给系统浏览器、权限默认拒绝、证书错误拒绝。

`downloads.ts` 必须：确认 `webContents === mainWindow.webContents` 且主框架仍在可信 origin；暂停下载、净化默认文件名、显示 `showSaveDialog`、取消时 `item.cancel()`、确认后 `setSavePath()` 并恢复；同时只允许一个对话框，后续并发下载取消。

- [ ] **步骤 5：运行定向与完整测试**

运行：`pnpm --dir ai-video-desktop test && pnpm --dir ai-video-desktop typecheck`

预期：全部 PASS。

- [ ] **步骤 6：提交任务 2**

```bash
git add ai-video-desktop/src ai-video-desktop/tests
git commit -m "feat(Electron): 加固远程网页与下载边界"
```

### 任务 3：窗口生命周期、图标与平台打包

**风险：** 黄色中风险；安全设置继承任务 2 的红色审查结论。

**文件：**
- 创建：`ai-video-desktop/src/main/createWindow.ts`
- 创建：`ai-video-desktop/src/main/index.ts`
- 创建：`ai-video-desktop/src/preload/index.ts`
- 创建：`ai-video-desktop/electron-builder.yml`
- 创建：`ai-video-desktop/scripts/generate-icons.mjs`
- 创建：`ai-video-desktop/resources/icons/*`
- 测试：`ai-video-desktop/tests/builderConfig.test.ts`

- [ ] **步骤 1：编写打包配置失败测试**

测试必须读取 `electron-builder.yml` 并断言：`com.suzao.aivideo`、NSIS/x64、DMG/x64/arm64、`identity: null`、三个产物命名片段均存在。

- [ ] **步骤 2：运行测试并确认配置不存在而失败**

运行：`pnpm --dir ai-video-desktop test -- tests/builderConfig.test.ts`

预期：FAIL，找不到 `electron-builder.yml`。

- [ ] **步骤 3：实现窗口与生命周期**

主窗口固定 `nodeIntegration: false`、`contextIsolation: true`、`sandbox: true`、`webSecurity: true`、`webviewTag: false`，使用 `persist:ai-video-web`，生产禁用 DevTools。实现单实例、Windows 关闭退出、macOS 激活重建、加载失败重试/退出、渲染进程退出重载/退出。

- [ ] **步骤 4：生成图标并配置 electron-builder**

从 `ai-video-ui/ai-video-webapp/public/logo.svg` 生成 `icon.png`、`icon.ico`、`icon.icns`。配置 Windows 当前用户 NSIS x64 与 macOS x64/arm64 DMG；不签名、不公证、不发布。

- [ ] **步骤 5：运行测试、类型检查、构建和 Windows 未安装包**

运行：

```bash
pnpm --dir ai-video-desktop test
pnpm --dir ai-video-desktop typecheck
AI_VIDEO_WEB_URL=https://desktop-test.example.com pnpm --dir ai-video-desktop pack
```

预期：测试和类型检查 PASS；`release/win-unpacked` 可启动目录生成。macOS DMG 只在 macOS 执行，不在 Windows 声称通过。

- [ ] **步骤 6：提交任务 3**

```bash
git add ai-video-desktop
git commit -m "build(Electron): 配置 Windows 与 macOS 安装包"
```

### 任务 4：公共文档与忽略规则

**风险：** 绿色文档任务；不得扩展功能。

**文件：**
- 修改：`.gitignore`
- 修改：`README.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`docs/FRONTEND_GUIDE.md`

- [ ] **步骤 1：更新文档与忽略规则**

准确记录远程 URL、浏览器并存、安全设置、下载“另存为”、pnpm 命令、原生系统构建和未签名限制。忽略 `ai-video-desktop/node_modules/`、`dist/`、`release/`、证书和公证材料。

- [ ] **步骤 2：运行文档规范校验**

运行：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1`

预期：`DEVELOPMENT_STANDARDS_OK`。

- [ ] **步骤 3：提交任务 4**

```bash
git add .gitignore README.md docs/ARCHITECTURE.md docs/FRONTEND_GUIDE.md
git commit -m "docs(Electron): 补充桌面壳开发与打包说明"
```

### 任务 5：独立安全审查与最终验证

**风险：** 红色验证任务。只审查本计划差异，不扩展范围。

- [ ] **步骤 1：运行完整自动化门禁**

```bash
pnpm --dir ai-video-desktop test
pnpm --dir ai-video-desktop typecheck
AI_VIDEO_WEB_URL=https://desktop-test.example.com pnpm --dir ai-video-desktop pack
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
git diff --check main...HEAD
```

预期：全部退出码 0。

- [ ] **步骤 2：独立安全审查**

审查者只检查：远程来源验证、Node/IPC 隔离、导航和外链、权限默认拒绝、下载发送方/协议/路径/并发、敏感信息和打包边界。结论只使用 `[必须修复]`、`[建议修改]`、`[仅供参考]`。

- [ ] **步骤 3：定向修复并复核一次**

只处理 `[必须修复]`；修改后重跑受影响测试和步骤 1，不发起第二轮全量审查。

- [ ] **步骤 4：记录平台限制**

Windows 仅能证明 x64 未签名测试目录/安装包；macOS x64/arm64 DMG 必须在 macOS 原生机器补充构建和冒烟证据。没有证据时明确标记未验证。

## 执行方式

用户已明确要求立即实现且速度第一，因此不再询问执行选项：在当前隔离 worktree 中使用 `executing-plans` 内联执行；红色任务完成后调度一名独立安全审查者，符合最多两名智能体的治理上限。
