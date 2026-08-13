# 当前工作区代码审查修复实现计划

> **面向 AI 代理的工作者：** 必须使用 `executing-plans` 在当前会话逐任务执行。步骤使用复选框跟踪进度。本工作区包含用户既有未提交修改，禁止覆盖无关差异；用户已明确不保留版本，本计划不创建 commit。

**目标：** 修复已经确认的登录、认证冻结、形象空间临时地址、文件类型、异步串线、虚假成功和仓库忽略问题，不新增形象生成后端。

**架构：** 登录页保持两入口和非持久会话，只修复测试与键盘行为，并把冻结认证文件恢复到分支基线。形象空间用 `portraitId` 作为稳定身份，通过现有 `portraitApi.accessUrl` 获取短期地址；本地图片只作为当前页面预览，演示交互不得产生真实生成或保存成功结论。

**技术栈：** React 19、TypeScript 7、Ant Design 6、Vitest 4、Testing Library、Umi Max。

**规格输入：** `docs/superpowers/specs/2026-08-04-code-review-remediation-design.md`

**公共契约：** 不修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 或后端代码；本轮复用既有 `GET /api/portraits/{portraitId}/access-url`。

---

## 文件结构

- `ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`：回退本轮运行时适配器重构，恢复冻结认证实现。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`：补齐标准页签键盘行为。
- `ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`：更新为两入口、非持久会话、当前 Ant Design Form 和键盘回归测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/model.ts`：把稳定 `portraitId` 与本地/远端来源显式建模。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.tsx`：短期地址加载、类型校验、回调隔离和演示状态纠偏。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`：覆盖访问地址、失败重试、类型拒绝、换图串线和无虚假成功。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`：卡片只传稳定形象身份。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`：验证传参不再依赖临时 URL。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`、`index.test.tsx`：适配来源模型并验证页面切换。
- `.gitignore`：忽略本地 Maven 仓库和 Codex 临时诊断目录。

### 任务 1：恢复冻结认证文件并修复登录测试基础设施

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.test.ts`

- [ ] **步骤 1：先运行登录测试，记录当前红灯**

运行：

```powershell
$taskNode = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
& $taskNode node_modules/vitest/vitest.mjs run src/pages/user/login/index.test.tsx
```

预期：FAIL；当前 `antd` mock 缺少 `Form`，且旧入口、持久会话和通知断言与已批准规格不一致。

- [ ] **步骤 2：更新登录测试桩和规格断言**

测试应提供可提交的 `Form`/`Form.Item` 最小桩，并验证：

```tsx
expect(screen.getByRole('tab', { name: '账号密码' })).toBeVisible();
expect(screen.getByRole('tab', { name: '扫码登录' })).toBeVisible();
expect(mockSaveSession).toHaveBeenCalledWith({
  accessToken: 'app-token',
  persistent: false,
});
expect(screen.getByRole('status')).toHaveTextContent('密码修改成功');
```

删除只针对登录页可见性的短信、邮件、第三方和小程序断言；保留对应 `auth/api.test.ts` 合同测试。

- [ ] **步骤 3：回退认证 API 越界差异**

仅把 `auth/api.ts` 恢复为原有 `getIntl/history/request + createRuoYiAdapter + beginLoginRedirect` 运行时组装，不修改其公开接口、请求路径或会话逻辑。使用 `git diff -- src/services/ai-video/auth/api.ts` 确认该文件相对分支基线无差异。

- [ ] **步骤 4：运行认证和登录定向测试**

运行：

```powershell
& $taskNode node_modules/vitest/vitest.mjs run src/services/ai-video/auth/api.test.ts src/pages/user/login/index.test.tsx
```

预期：PASS，且登录测试继续覆盖重复提交、`/me` 验证、安全回跳、403 保留 Token、其他失败清理 Token。

### 任务 2：补齐登录页签键盘模型

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/user/login/index.test.tsx`

- [ ] **步骤 1：编写失败测试**

增加测试：当前页签 `tabIndex=0`，非当前页签 `tabIndex=-1`；按 ArrowRight/ArrowLeft/Home/End 后选中目标页签并把焦点移到目标按钮。

```tsx
passwordTab.focus();
fireEvent.keyDown(passwordTab, { key: 'ArrowRight' });
expect(qrTab).toHaveAttribute('aria-selected', 'true');
expect(qrTab).toHaveFocus();
```

- [ ] **步骤 2：运行测试确认因键盘行为缺失而失败**

运行：`& $taskNode node_modules/vitest/vitest.mjs run src/pages/user/login/index.test.tsx`

预期：FAIL，页签状态或焦点没有变化。

- [ ] **步骤 3：实现最小键盘行为**

为两个页签保存 ref，并在共享处理函数中完成循环切换：

```tsx
const activateTab = (method: LoginMethod) => {
  setActiveMethod(method);
  tabRefs[method].current?.focus();
};
```

为按钮设置与当前状态一致的 `tabIndex`，处理 ArrowLeft、ArrowRight、Home、End；不修改 CSS 视觉结构。

- [ ] **步骤 4：运行登录测试确认通过**

运行：`& $taskNode node_modules/vitest/vitest.mjs run src/pages/user/login/index.test.tsx`

预期：PASS。

### 任务 3：以形象 ID 驱动短期访问地址

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`

- [ ] **步骤 1：编写稳定身份和访问地址失败测试**

形象卡片测试期望：

```tsx
expect(onOpenSpace).toHaveBeenCalledWith({
  kind: 'portrait',
  portraitId: 'portrait-1',
  name: '形象一',
});
```

形象空间测试 mock `portraitApi.accessUrl`，验证加载时使用 `portraitId` 获取 URL；失败显示错误和“重新加载”，点击后再次请求。

- [ ] **步骤 2：运行三个测试文件确认失败**

运行：

```powershell
& $taskNode node_modules/vitest/vitest.mjs run src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx
```

预期：FAIL，当前模型只有 `image/name`，且形象空间不会调用 `accessUrl`。

- [ ] **步骤 3：实现来源联合类型和加载状态**

模型使用可判别联合：

```ts
export type AvatarSpaceSource =
  | { kind: 'portrait'; portraitId: string; name: string }
  | { kind: 'local'; image: string; name: string };
```

卡片传递 `kind/portraitId/name`。`AvatarSpaceView` 对远端来源调用 `portraitApi.accessUrl`，维护加载、失败和重试状态；请求完成前校验来源版本，避免旧响应覆盖新来源。

- [ ] **步骤 4：运行三个测试文件确认通过**

运行同步骤 2。

预期：PASS。

### 任务 4：纠正本地图片、异步回调和演示成功语义

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.tsx`
- 测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`

- [ ] **步骤 1：编写图片类型失败测试**

验证真实最小 PNG/JPEG 文件可进入预览；SVG、GIF、WebP、空 MIME 和扩展名/MIME 不一致时保持旧来源并提示“仅支持 JPEG、JPG、PNG”。输入框 `accept` 精确为 `.jpg,.jpeg,.png,image/jpeg,image/png`。

- [ ] **步骤 2：编写异步串线失败测试**

启用假定时器，启动一轮演示回复后立即重新选图；推进全部定时器，断言旧来源对应的回复和结果未写入新会话。

- [ ] **步骤 3：编写无虚假业务成功测试**

断言页面明确显示“演示/建设中”；发送描述不会加载 `picsum.photos` 结果，页面不存在“保存到形象库”成功操作或成功提示。

- [ ] **步骤 4：运行测试确认按上述三个原因失败**

运行：`& $taskNode node_modules/vitest/vitest.mjs run src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`

预期：FAIL，当前实现接受 `image/*`、旧 timer 仍写入、并模拟生成和保存成功。

- [ ] **步骤 5：实现最小行为纠偏**

- 抽取 `isSupportedAvatarImage(file)`，只接受 JPEG/PNG 扩展名与 MIME 的一致组合。
- `accept` 改为精确白名单。
- 为来源会话维护递增 revision；换图、来源改变和卸载时清理 timer，回调执行前校验 revision。
- 删除 `picsum.photos` 演示结果和本地伪保存逻辑；保留页面布局、输入框和建议按钮，但以建设中状态结束，不触发业务成功 Toast。

- [ ] **步骤 6：运行形象空间测试确认通过**

运行同步骤 4。

预期：PASS。

### 任务 5：仓库忽略规则与完整回归

**文件：**
- 修改：`.gitignore`

- [ ] **步骤 1：增加精确忽略规则**

```gitignore
/.m2/
/.codex/tmp/
```

不删除现有目录或用户文件。

- [ ] **步骤 2：验证忽略行为**

运行：

```powershell
git check-ignore -v .m2/repository .codex/tmp/DbCheck.java
```

预期：两条路径分别匹配新增规则。

- [ ] **步骤 3：运行前端相关回归**

运行：

```powershell
& $taskNode node_modules/vitest/vitest.mjs run src/pages/user/login/index.test.tsx src/services/ai-video/auth/api.test.ts src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx src/pages/digital-human-studio/components/LibraryView.test.tsx src/pages/digital-human-studio/components/StudioSider.test.tsx src/pages/digital-human-studio/usePersonalQuotaAccount.test.tsx src/services/ai-video/quota/api.test.ts
& $taskNode node_modules/typescript/bin/tsc --noEmit
```

预期：全部 PASS。

- [ ] **步骤 4：运行后端既有定向回归**

本轮不改后端，但确认工作区现有形象、额度和安全测试未回归：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
.\mvnw.cmd '-Dmaven.repo.local=D:\AI\ai-video\.m2\repository' -pl 'ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user' -am '-Dtest=QuotaAccountServiceImplTest,QuotaControllerTest,PortraitServiceImplTest,PortraitControllerTest,AppSecurityConfigTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false' test
```

预期：8 个定向测试通过。

- [ ] **步骤 5：检查差异边界**

运行：

```powershell
git diff --check
git diff -- src/services/ai-video/auth/api.ts
git status --short
```

预期：无空白错误；冻结认证 API 无差异；状态中没有 `.m2/` 或 `.codex/tmp/`，且没有修改本计划之外的用户文件。

## 风险、审查与验证安排

- 登录与认证文件属于红色风险：只回退越界差异、修复测试和无障碍行为；不重构会话或请求层。完成后只复核这些差异一次。
- 形象空间纠偏属于黄色风险：不增加接口、数据库、额度或异步任务；测试覆盖临时 URL、失败重试、文件白名单和回调隔离。
- 前后端没有新增联调契约；现有 `accessUrl` 是唯一远端依赖。
- 当前会话内联执行，任务之间串行验证，避免在同一登录测试和形象空间文件上并发写入。
