# 前端指南

## 技术栈与包边界

前端采用 React、TypeScript、Ant Design 与 Ant Design Pro / ProComponents。Web 前端包位于 `ai-video-ui` 工作区，桌面薄壳位于仓库根目录：

- `ai-video-ui/ai-video-webapp`：用户端 Web 应用。
- `ai-video-ui/ai-video-platform-ui`：管理端/运营端前端。
- `ai-video-desktop`：Electron 薄壳与安装包构建。

用户端页面由 Web 应用承载；Electron 只负责窗口、本地能力、更新和系统集成，不改变 Web 应用的路由、接口、组件或业务组织。管理端和用户端可复用设计体系，但页面结构、路由和业务流程不得混入同一个包。

## 页面类型与组件选型

管理类页面优先采用 ProComponents 的列表、表格、筛选和分页能力，适用于草稿箱、模板中心、素材管理、任务中心和通知中心等运营场景。

生产工作台优先采用 Ant Design 基础组件与业务组件组合，适用于视频创作、图生数字人、视频数字人、克隆声音和成片生成等流程。生产页面保持输入、预览、设置、结果等区域的稳定布局；管理页面强调筛选、列表、操作与权限展示。

选择组件时：先判断页面属于管理页还是生产工作台；再查找现有同类页面与共享组件；最后再决定采用 ProComponents、基础组件或已有业务组件。组件 API、样式 Token 与语义结构的具体查证方式，以[前端编码规范](FRONTEND_CODING_STANDARDS.md)为准。

## 目录职责

用户端 Web 保持 Ant Design Pro / Umi Max 既有目录结构，不另建平行的应用根目录体系：

```text
ai-video-desktop/
  src/
    main/                # 主进程、窗口、生命周期、安全和下载策略
    preload/             # 首期为空；未来仅放受控本地能力 bridge

ai-video-ui/
  ai-video-webapp/
    config/              # 路由、主题与开发代理
    src/
      app.tsx            # 运行时配置、布局与初始化
      access.ts           # 前端权限判断
      pages/             # 路由页面组装
      components/        # 跨页面复用业务组件
      services/ai-video/ # 业务服务入口
      hooks/             # 跨页面复用 hooks
      utils/             # 纯工具函数
      locales/
  ai-video-platform-ui/  # 管理端/运营端独立演进
```

- `ai-video-ui` 只做前端包分组，不承载跨端业务状态或接口契约。
- `src/pages` 负责路由页面组装；复杂页面拆分为模块内组件、hooks 与目录。
- `src/components` 放跨页面复用的业务组件；仅服务单页的组件留在页面模块中。
- `src/services/ai-video` 按后端领域或页面流程组织服务入口。
- `src/hooks` 放跨页面复用 hooks；单页 hooks 留在对应页面目录。
- `src/utils` 只放无业务状态的纯工具函数。
- `access.ts` 负责前端可见性判断，不替代后端权限校验。

## 新建页面流程

1. 明确页面所属端（用户端或平台端）、页面类型和对应业务聚合。
2. 在对应包的路由配置中新增入口，并遵循相邻页面的菜单、权限和布局模式。
3. 创建页面组装层；将复杂交互、领域组件和仅页面使用的 hooks 放入页面模块。
4. 在 `services/ai-video` 中接入服务入口，页面不得散落拼接接口地址。
5. 补齐加载、空数据、无结果、失败、无权限、进行中、成功与失败反馈等页面状态。
6. 使用既有测试和验证方式检查路由、交互和服务调用边界。

接口适配、类型边界、错误处理与测试细则遵循[前端编码规范](FRONTEND_CODING_STANDARDS.md)和[API 契约](API_CONTRACT.md)。

## 布局范式

- 全局结构采用左侧导航、顶部栏和内容区。
- 视频创作流程页面使用步骤化导航，并保持输入、预览、设置和结果区域稳定。
- 管理页面采用筛选区、列表或网格、操作区和分页的组织方式。
- 页面优先复用已有上传、任务进度、模板卡片、素材卡片与状态展示组件。

视觉与交互细则以[前端编码规范](FRONTEND_CODING_STANDARDS.md)为准；页面业务状态和任务生命周期以[领域模型](DOMAIN_MODEL.md)及[异步任务契约](ASYNC_TASKS.md)为准。

## Electron 运行与打包

- 开发模式默认打开 `http://localhost:8000`，只允许 `localhost`、`127.0.0.1` 或 `::1` 回环地址使用 HTTP。
- 生产构建必须提供 `AI_VIDEO_WEB_URL`，且地址必须是无用户名、密码的 HTTPS URL；该地址在构建时固化，用户不能在运行时修改。
- `pnpm run pack` 生成当前平台的未安装目录，`pnpm run dist:win` 在 Windows 生成 x64 NSIS 安装包，`pnpm run dist:mac` 在 macOS 生成 x64 与 arm64 DMG。
- 用户端网页与 Electron 壳共享路由、鉴权和业务接口，网页可以继续直接在浏览器中打开；Electron 主进程不得读取令牌或代理业务请求。
- Electron 窗口隐藏原生菜单栏；首次打开先显示壳层本地加载动画，用户端网页加载完成后自动切换。
- 外部 HTTPS 链接由系统浏览器打开。下载使用主进程 `will-download` 校验和系统“另存为”对话框完成，不需要网页调用 bridge。
- 当前产物未签名，仅用于内部测试；首期不包含 CI、自动更新、托盘或自定义本地文件 bridge。

## Electron bridge

Electron 主进程只处理壳层能力，不直接承担业务后端调用。首期 preload 不向 Web 暴露 bridge；“另存为”由下载会话直接处理。未来新增确有必要的本地能力时，bridge 按能力分组并维持小而稳定的公共面。

接入新本地能力时：

1. 在 `main` 中确认窗口、生命周期与安全策略影响。
2. 在 `preload` 中以显式白名单暴露受控 bridge。
3. 在 `shared` 中维护主进程和 preload 共用类型。
4. 在 Web 页面侧只调用 bridge 暴露的能力，不直接使用 Node API。
5. 验证文件选择、下载保存、系统通知、更新或窗口控制等能力不会改变前后端 API 契约。

## 文档职责边界

本指南定义前端包边界、页面类型、目录职责、页面接入流程、布局范式和 Electron 接入流程；不重复维护编码规则或协议字段。

- [前端编码规范](FRONTEND_CODING_STANDARDS.md)：TypeScript、React、hooks、状态、服务封装、组件使用、可访问性、测试与 Ant Design 查证规则。
- [API 契约](API_CONTRACT.md)：接口响应、鉴权、分页、错误处理、SSE 与前后端适配约束。
- [AI 编码规则](AI_CODING_RULES.md)：AI 协作、任务前阅读和文档更新要求。
- [领域模型](DOMAIN_MODEL.md) 与 [异步任务契约](ASYNC_TASKS.md)：领域状态、任务生命周期和跨端一致性。

开始前端任务前，先确认页面归属和公共契约；接口、字段、状态或任务规则变化时，先同步相应文档，再调整前端实现。
