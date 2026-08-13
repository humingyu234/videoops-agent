# Electron 用户端桌面薄壳设计

## 1. 规格信息

- 日期：2026-08-06
- 模块：用户端桌面壳层 `ai-video-desktop`
- 状态：设计已确认，等待书面规格复核
- 临时契约 owner：本次 Electron 集成任务负责人
- 总体风险：红色高风险
- 风险依据：加载远程内容属于外部信任边界；“另存为”涉及外部内容写入本地文件；窗口、导航、权限和 IPC 配置直接决定桌面端安全边界。

权威来源：

- `AGENTS.md`
- `RULES.md`
- `docs/AI_AGENT_GOVERNANCE.md`
- `docs/ARCHITECTURE.md`
- `docs/FRONTEND_GUIDE.md`
- `docs/FRONTEND_CODING_STANDARDS.md`
- Electron 官方 [BrowserWindow](https://www.electronjs.org/docs/latest/api/browser-window)、[安全指南](https://www.electronjs.org/docs/latest/tutorial/security)、[Session](https://www.electronjs.org/docs/latest/api/session) 与 [DownloadItem](https://www.electronjs.org/docs/latest/api/download-item/) 文档
- electron-builder 官方 [打包目标](https://www.electron.build/docs/targets/) 与 [跨平台构建](https://www.electron.build/docs/features/multi-platform-build/) 文档

## 2. 背景与现状

项目公共架构已经把 `ai-video-desktop` 定义为用户端 Electron 独立薄包，但仓库当前尚未创建该目录。用户端页面已经由 `ai-video-ui/ai-video-webapp` 承载，并且必须继续支持普通浏览器直接访问。

本次只增加桌面入口：Electron 在生产环境加载已部署的用户端 HTTPS 网页，在开发环境加载本地用户端 Web。Electron 不复制 Web 页面，不实现第二套路由、认证、业务状态或接口适配。

## 3. 目标与成功标准

### 3.1 目标

1. 新建独立的 `ai-video-desktop` TypeScript 包。
2. 开发环境能够加载本地用户端 Web，生产包能够加载构建时指定的唯一 HTTPS 用户端网址。
3. Windows 生成 x64 NSIS `.exe` 安装包。
4. macOS 分别生成 Intel x64 与 Apple Silicon arm64 的 `.dmg` 安装包。
5. 用户端 Web 保持独立部署，可继续在普通浏览器直接访问。
6. 远程网页不能获得 Node.js、原始 IPC 或任意本地系统能力。
7. Electron 中由可信主窗口发起的下载统一弹出系统“另存为”对话框，不需要 Web 调用 preload bridge。
8. 在尚无证书的阶段产出明确标记为内部测试用途的未签名安装包，并为未来签名、公证保留标准接入位置。

### 3.2 成功标准

- 安装包能够安装、启动、卸载并打开指定用户端网页。
- 登录、刷新、退出、路由、上传和下载行为与浏览器版保持一致。
- 生产包无法加载 HTTP、带凭据网址、任意其他主窗口来源或危险协议。
- 外部 HTTPS 链接只在系统默认浏览器打开。
- 下载路径只能由系统“另存为”对话框中的用户选择决定。
- Windows x64 与 macOS x64/arm64 的构建命令、配置和产物命名明确；各产物必须在对应原生系统完成实机验证后才能标记通过。

## 4. 不在本次范围内

- 不建设 CI 自动打包或自动发布。
- 不接入 Electron 自动更新。
- 不实现 Windows 代码签名、macOS Developer ID 签名或公证；正式发布仍以这些能力为前置门禁。
- 不增加托盘、自定义标题栏、系统通知、摄像头、麦克风、定位或剪贴板读取。
- 不增加网页主动调用的文件选择、保存、窗口控制或其他 preload bridge。
- 不内置 `ai-video-webapp` 构建产物，不提供离线业务页面。
- 不修改后端接口、数据库、任务、额度、文件授权或账号归属规则。
- 不为 Electron 创建专属业务路由、页面状态或认证体系。

## 5. 方案选择

采用“单个 `BrowserWindow` 直接加载远程网址 + electron-builder”方案。

未采用本地启动页加 `WebContentsView`，因为当前没有离线业务 UI、桌面导航栏或复合视图需求。未采用 Electron Forge，因为当前交付目标集中于 NSIS、DMG 和少量主进程代码，electron-builder 能以更少配置直接覆盖目标格式和架构。

## 6. 包边界与目录

```text
ai-video-desktop/
  src/
    main/
      index.ts          # 应用生命周期、单实例与 macOS 激活
      createWindow.ts   # 唯一主窗口创建和装载
      security.ts       # 来源、导航、新窗口、权限与外链限制
      webUrl.ts         # 构建环境网址读取、规范化与校验
      downloads.ts      # 可信下载与系统“另存为”处理
    preload/
      index.ts          # 首期不向网页暴露任何 API
    shared/             # 未来受控 bridge 的共享类型位置，首期不得承载业务模型
  resources/
    icons/              # PNG 源图、Windows ICO、macOS ICNS
  tests/
  electron-builder.yml
  package.json
  pnpm-lock.yaml
  tsconfig.json
```

职责边界：

- `main` 只负责应用生命周期、窗口、安全策略、下载保存和打包期配置。
- `preload` 首期为空能力面；不得暴露 `ipcRenderer`、Node.js 模块、环境变量或通用消息发送函数。
- `shared` 只为未来明确批准的 bridge 保存小而稳定的 TypeScript 类型，不复制 Web 或后端业务类型。
- `ai-video-ui/ai-video-webapp` 继续负责全部页面、路由、认证、API 调用、错误状态和业务交互。
- Electron 主进程不得保存业务 Token、调用业务 API 或读取网页认证存储。

## 7. 网址与运行模式

### 7.1 构建时配置

统一使用 `AI_VIDEO_WEB_URL`：

- 开发模式缺省为 `http://localhost:8000`，允许通过环境变量覆盖。
- 打包模式必须显式传入，且必须为合法 HTTPS 地址。
- 打包时将规范化后的值固化到已编译主进程代码；安装后的用户不能通过配置文件、命令行或界面修改目标网址。
- 网址可以包含产品部署所需的初始路径，但安全判断使用解析后的 origin，不用字符串前缀代替 URL 解析。

### 7.2 校验规则

打包模式遇到以下任一情况立即失败：

- 未提供网址。
- 协议不是 `https:`。
- 包含用户名或密码。
- 主机名为空、端口非法或 URL 无法解析。
- 包含不受支持的协议或被编码的危险导航形式。

开发模式只额外允许 `http://localhost`、`http://127.0.0.1` 与 `http://[::1]`，不得因为处于开发模式而允许任意 HTTP 主机。

### 7.3 Web 与浏览器并存

Electron 与普通浏览器加载同一个用户端部署：

```text
普通浏览器 ───────┐
                  ├──> ai-video-webapp ──> ai-video-user-api
Electron 主窗口 ──┘
```

Electron 不改变 Web 的 `APP_API_BASE_URL`、登录客户端、路由结构或服务 adapter。浏览器版不检测或依赖 Electron 全局对象。

## 8. 窗口与生命周期

- 产品名：`素造智能体`。
- 应用 ID：`com.suzao.aivideo`。
- 只创建一个主窗口，并使用应用单实例锁；第二次启动只恢复和聚焦现有窗口。
- 默认窗口大小为 `1440 × 900`，最小大小为 `1024 × 720`。
- 使用接近 Web 首屏的背景色，减少远程页面装载时的白屏闪烁。
- Windows 关闭主窗口后退出应用。
- macOS 关闭窗口后保留应用；点击 Dock 图标且没有窗口时重新创建主窗口。
- 普通同源路由、刷新、前进和后退均留在主窗口内。
- 生产环境关闭 DevTools；开发模式允许显式打开 DevTools。

## 9. 安全边界

### 9.1 BrowserWindow 固定设置

- `nodeIntegration: false`
- `contextIsolation: true`
- `sandbox: true`
- `webSecurity: true`
- `allowRunningInsecureContent: false`
- `webviewTag: false`
- 不启用实验性 Blink 或 Electron 特性
- 不忽略证书错误

这些设置不得由远程页面、查询参数、打包变量或开发便利开关覆盖。

### 9.2 会话

- 使用独立持久化分区，例如 `persist:ai-video-web`，使正常 Cookie、LocalStorage 和缓存与其他 Electron 内容隔离。
- 主进程不读取、复制、记录或迁移该分区中的业务 Token。
- 对该会话注册权限、下载和安全处理器，避免未来其他窗口意外继承不同策略。

### 9.3 导航与新窗口

- 主框架导航和重定向只允许固化网址的精确 origin。
- 开发模式只额外允许经过校验的本机开发 origin。
- 所有新 Electron 窗口默认拒绝创建。
- 页面请求打开外部链接时，仅允许解析成功且协议为 `https:` 的 URL 交给系统默认浏览器。
- `http:`、`file:`、`javascript:`、`data:`、自定义协议、带凭据 URL 和无法解析的 URL 一律拒绝。
- 调用系统浏览器前必须校验 URL，不得把远程页面提供的任意字符串直接传给 `shell.openExternal`。

### 9.4 权限与 IPC

- 会话权限请求默认拒绝。
- 首期不授予摄像头、麦克风、定位、通知、剪贴板读取等敏感权限。
- preload 不暴露任何 API，因此 Web 端不存在 Electron 专属字段、调用路径或降级逻辑。
- 将来新增 bridge 必须单独形成规格，逐项定义方法、参数、返回值、发送方校验、权限、失败语义和测试；禁止暴露原始 `ipcRenderer`。

## 10. “另存为”下载设计

### 10.1 设计原则

下载使用 Electron 会话的 `will-download` 和 `DownloadItem`，不增加 `saveAs` preload bridge，也不由主进程重新请求业务 URL。

这样可以保留 Chromium 已经建立的 Cookie、请求头、重定向和响应，同时避免给远程网页任意文件写入或主进程网络请求能力。

### 10.2 下载流程

```text
可信用户端页面发起浏览器下载
  -> 独立 Electron 会话触发 will-download
    -> 校验发起 webContents 就是当前唯一主窗口
      -> 校验主窗口当前主框架仍位于可信 origin
        -> 校验下载 URL 链只使用生产 HTTPS；开发模式可使用本机 HTTP；可信页面产生的 blob 下载允许继续
          -> 清理服务器建议文件名，仅作为对话框默认名称
            -> 显示系统“另存为”对话框
              -> 用户取消：取消 DownloadItem
              -> 用户确认：setSavePath 后继续下载
```

### 10.3 下载安全规则

- 只处理当前主窗口产生的下载；其他 `webContents` 或失效窗口产生的下载直接取消。
- 绝不接受网页传入的绝对路径、目录或覆盖策略。
- 建议文件名必须去除目录片段、控制字符和平台非法字符，并限制长度。
- 最终路径只来自系统对话框结果。
- 同一时间只允许一个“另存为”决策；并发自动下载取消或排队时必须采用固定策略，首期选择取消后续并发下载并提示用户逐个下载，防止弹窗刷屏。
- 用户取消、磁盘错误、权限错误和下载中断不得被报告为成功。
- 生产环境只允许 HTTPS 下载链；开发环境只额外允许本机 HTTP。`blob:` 仅在发起主框架仍为可信 origin 时允许。
- 不自动执行、打开或预览下载完成的文件。

### 10.4 浏览器行为

普通浏览器访问用户端时继续使用浏览器自身下载行为。Web 页面不需要判断 Electron 环境，也不需要增加 adapter、mock 或 TypeScript bridge 类型。

## 11. 加载与异常处理

- 构建阶段网址校验失败时以非零状态退出，不能生成安装包。
- 首次主框架加载失败时使用原生对话框提供“重试”和“退出”。
- TLS 或证书失败不得提供忽略选项。
- 渲染进程异常退出时提供“重新加载”和“退出”；不得静默无限重试。
- 同一加载失败最多自动重试两次；没有环境变化时停止并交还用户选择，遵守项目强制收口规则。
- 页面加载成功后的 API 失败、加载态、空态、权限不足、登录失效和业务失败继续由 `ai-video-webapp` 现有页面处理。
- Electron 不伪造后端成功、任务状态、下载授权或登录状态。

## 12. 打包与产物

### 12.1 工具和脚本

采用 Electron、TypeScript 与 electron-builder，并提交锁文件以固定依赖解析结果。预期脚本：

- `pnpm dev`：编译主进程并加载本地用户端 Web。
- `pnpm typecheck`：运行 TypeScript 类型检查。
- `pnpm test`：运行桌面端自动化测试。
- `pnpm pack`：生成当前平台未安装目录用于快速冒烟。
- `pnpm dist:win`：在 Windows 生成 x64 NSIS 安装包。
- `pnpm dist:mac`：在 macOS 分别生成 x64 与 arm64 DMG。

### 12.2 平台目标

- Windows：NSIS、x64、当前用户安装、不要求管理员权限。
- macOS：DMG，分别输出 x64 与 arm64，不生成 Universal 包。
- Windows 包在 Windows 原生环境构建。
- macOS 包在 macOS 原生环境构建。
- 本次不使用 Docker、虚拟机或 CI 代替目标平台实机验证。

### 12.3 产物

```text
release/
  素造智能体-<version>-windows-x64-setup.exe
  素造智能体-<version>-macos-x64.dmg
  素造智能体-<version>-macos-arm64.dmg
```

- 版本号以 `ai-video-desktop/package.json` 为唯一来源。
- 开启 ASAR，只打入编译后的桌面端代码、图标和必要运行依赖。
- `release/`、临时打包目录、缓存、证书和公证材料不得进入版本控制。
- Windows 使用 `.ico`，macOS 使用 `.icns`，并保留高分辨率 PNG 源图。

### 12.4 无证书阶段

- 当前产物明确标记为内部测试包。
- Windows 可能显示 SmartScreen 提示；测试人员只能通过系统提供的明确操作继续。
- macOS 测试人员通过 Finder 右键“打开”或系统“隐私与安全性”明确放行。
- 不提供关闭 Gatekeeper、清除隔离属性或绕过系统安全机制的脚本。
- 获得证书后，Windows 签名、macOS Developer ID 签名与公证必须在正式发布前完成；本规格不把未签名测试包定义为正式发布完成。

## 13. 前端、后端与公共契约影响

### 13.1 用户端 Web

- 不新增页面、路由、导航入口、业务字段或 Ant Design 组件。
- 不修改加载、空、失败、权限不足、提交、任务或额度状态。
- 不新增 API adapter、mock 数据或 Electron 专属 TypeScript 业务类型。
- 登录、上传、下载触发、页面错误和直接浏览器访问保持现状。

### 13.2 后端

本次无后端工作：

- 不新增 Controller、BO、VO、DTO、Entity、Mapper 或 Service。
- 不修改权限标识、`ownerId` 校验、认证、数据范围或日志审计。
- 不修改 API 入参、出参或错误码。
- 不修改任务创建、幂等、状态流转、回调、轮询、额度或文件授权。
- 不修改数据库、字典或状态枚举。

因此不涉及 RuoYi 业务分层例外，也不需要更新 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 或 `docs/ASYNC_TASKS.md`。

### 13.3 需要同步的公共文档

- `docs/ARCHITECTURE.md`：补充远程 HTTPS 加载、安全边界、下载流程和首期能力范围。
- `docs/FRONTEND_GUIDE.md`：补充桌面包目录、环境网址、开发/打包命令与“另存为”流程。
- `README.md`：把 `ai-video-desktop` 从“待创建”更新为实际包说明。
- `.gitignore`：排除桌面构建产物、缓存和签名材料。
- `docs/FRONTEND_CODING_STANDARDS.md` 的现有 Electron 规则已覆盖本次边界，不需要修改。

文档变更后必须运行 `scripts/validate-development-standards.ps1`。

## 14. 测试与验收

### 14.1 自动化测试

网址与环境：

- 打包模式接受合法 HTTPS 地址。
- 打包模式拒绝缺失值、HTTP、带凭据 URL、非法端口和危险协议。
- 开发模式只额外接受本机 HTTP origin。
- origin 比较使用 URL 解析后的协议、主机和端口，覆盖相似域名、用户名伪装和编码绕过。

窗口与安全：

- Node 集成关闭、上下文隔离和沙箱开启。
- `webSecurity` 保持开启，混合内容和 `webviewTag` 关闭。
- 生产 DevTools 关闭。
- 同源主导航允许，跨源主导航和重定向阻止。
- 新 Electron 窗口拒绝，外部 HTTPS 仅交给系统浏览器。
- 危险协议和未解析 URL 不调用 `shell.openExternal`。
- 会话敏感权限默认拒绝。

下载：

- 只接受当前主窗口且主框架仍处于可信 origin 的下载。
- 非法来源、失效窗口和不允许的下载协议被取消。
- 建议文件名不能注入目录、绝对路径、控制字符或非法字符。
- 用户取消对话框后下载取消。
- 用户确认后只使用对话框返回路径。
- 并发下载不会产生无限弹窗。
- 下载失败或中断不报告成功。

生命周期和打包：

- 单实例聚焦现有窗口。
- Windows 关闭退出与 macOS Dock 恢复符合预期。
- 应用 ID、产品名、版本、NSIS、DMG 和目标架构配置正确。
- 产物名称包含版本、平台和架构。

### 14.2 实机冒烟

Windows x64 和 macOS x64/arm64 分别执行：

1. 安装、首次启动、关闭、再次启动和卸载。
2. 登录、刷新、退出、重新启动并检查会话行为。
3. 访问用户端主要路由。
4. 选择文件上传。
5. 发起下载，验证“另存为”、取消、成功保存和失败提示。
6. 点击外部链接，验证只在系统浏览器打开。
7. 断网启动，恢复网络后重试成功。
8. 第二次启动只聚焦现有窗口。
9. 验证同一网址仍可在普通浏览器直接使用。
10. 检查安装包架构、名称和内容，确认没有业务 Token、证书、开发网址或非必要源码。

没有对应 macOS 机器时，只能声明 macOS 配置和自动化测试完成，不能声明 DMG 构建或实机验收通过。

## 15. 实施任务卡与协作安排

### 15.1 桌面包骨架与网址配置

- 目标：创建独立 TypeScript 包、开发启动、生产网址固化和严格校验。
- 风险：黄色中风险。
- 允许范围：`ai-video-desktop` 基础配置、`webUrl`、对应测试和直接相关文档。
- 不做范围：窗口安全处理、下载、Web 和后端改动。
- 验收：类型检查通过；合法/非法网址矩阵通过；生产缺少网址时无法打包。
- 审查：独立代码审查。
- 验证：桌面包类型检查、单测和当前平台未安装目录构建。

### 15.2 主窗口与安全策略

- 目标：实现单窗口生命周期、可信导航、外链、新窗口、权限和加载异常边界。
- 风险：红色高风险，命中外部信任边界和安全控制。
- 允许范围：`src/main` 窗口、安全、生命周期代码及测试。
- 不做范围：业务 IPC、Web 页面、后端和自动更新。
- 验收：第 9、11、14 节全部相关正反场景通过。
- 审查：一次规格/契约审查和一次独立安全审查；实施者不能兼任安全审查者。
- 验证：自动化安全测试与 Windows/macOS 对应冒烟；修复后只复核差异。

### 15.3 下载“另存为”处理

- 目标：可信主窗口下载通过系统对话框选择最终路径，不暴露 bridge。
- 风险：红色高风险，命中外部内容和本地文件写入。
- 允许范围：`downloads.ts`、会话接线、文件名纯函数和相关测试。
- 不做范围：主进程重新下载、网页传入路径、自动打开文件或下载中心 UI。
- 验收：第 10、14 节全部下载正反场景通过。
- 审查：与主窗口安全任务合并为同一轮独立安全审查，重点检查发送方、URL 链、路径和并发弹窗。
- 验证：单测、集成行为测试和三种目标架构的手工下载冒烟。

### 15.4 Windows/macOS 打包

- 目标：生成约定的 NSIS 与两种 DMG 测试包。
- 风险：黄色中风险。
- 允许范围：electron-builder 配置、脚本、图标、忽略规则和打包文档。
- 不做范围：CI、签名、公证、自动更新和发布平台。
- 验收：产物格式、架构、名称、安装/卸载和内容检查通过。
- 审查：独立代码/配置审查。
- 验证：Windows 在 Windows 构建；macOS 在 macOS 分别构建 x64/arm64；缺少目标平台证据时保持未完成。

### 15.5 文档与最终验证

- 目标：同步架构、前端指南、README 和忽略规则，汇总交付证据。
- 风险：绿色低耦合文档任务；最终验证继承所验证任务的风险等级。
- 允许范围：第 13.3 节文件和验证记录。
- 不做范围：扩大 Electron 功能或补写无关文档。
- 验收：公共文档一致，规范校验通过，未验证项明确列出。
- 审查：文档内联检查和实现任务既定审查，不另起重复全量审查。
- 验证：`scripts/validate-development-standards.ps1`、桌面端完整测试、目标平台构建和冒烟记录。

协作约束：

- 红色实施任务同时最多一名实施者和一名独立审查者。
- 桌面端可独立于 Web 与后端推进；不创建无实际工作内容的后端或 Web 子任务。
- 本地 `ai-video-webapp` 或受控测试 HTTPS 页面可用于桌面开发，不需要改造后端 mock 契约。
- 生产网址、安全白名单、下载策略或任何新 bridge 变化都必须先更新并复核本规格。
- 一轮完整审查后只允许一次定向复核；同一问题最多两次自动返工，同一无变化失败最多重试两次。

## 16. 交付边界

本规格可以由一份实现计划覆盖，不需要继续拆分为多个独立规格。实现完成的最低交付物为：

- `ai-video-desktop` 源码、锁文件和自动化测试。
- Windows x64 未签名内部测试安装包及实机验收证据。
- macOS x64、arm64 未签名内部测试安装包及对应原生机器验收证据。
- 更新后的公共文档和规范校验结果。
- 独立安全审查结论与修复差异复核记录。

如果缺少 macOS 原生环境或证书，允许分别交付“macOS 配置已实现但实机未验证”和“未签名内部测试包”，但不得把它们表述为 macOS 打包已通过或正式发布已完成。
