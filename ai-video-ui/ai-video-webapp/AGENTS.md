# 用户端 Web 局部规则

本文件补充仓库根 `AGENTS.md`，只适用于 `ai-video-ui/ai-video-webapp`。

## 真实工程命令

- Node.js：`>= 22`。
- `npm start`、`npm run dev`：均以 `MOCK=none` 启动开发环境，是本项目真实后端联调入口。
- `npm run dev:discovery`：显式启用发现页 Mock；不能用它证明真实 API 或端到端链路。
- `npm run build`：生产构建，并在构建前后执行时间轴产物检查。
- `npm run lint`：Biome lint + TypeScript 类型检查。
- `npm run test`：Vitest；定向运行时沿用 Vitest 参数。
- `npm run tsc`：只做 TypeScript 类型检查；`npm run openapi`：重新生成 OpenAPI 服务。

不要擅自切换包管理器或重写锁文件。

## 修改边界

- `src/services/ant-design-pro/` 和 `src/.umi/` 是生成内容，不手工修改；前者通过 `npm run openapi` 更新，业务 API 沿用 `src/services/ai-video/` 的既有模式。
- 页面、组件和请求优先复用现有认证、权限、上传、任务、额度、错误和空态契约；不得在页面散写接口路径、状态字符串、错误码或账号归属判断。
- `/studio` 是当前真实人工链路和施工入口；不要用 Mock 页面或模板示例替代真实后端行为。
- 危险操作必须有确认或风险提示；所有页面处理加载、空、失败、权限不足、操作中和操作结果。

## 按需读取

- 涉及 Ant Design 组件、Token、语义或迁移时，从仓库根读取 `.agents/skills/antd/SKILL.md`，先查询当前 antd v6 API，再写代码。
- 前端工程与编码规则读取 `docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md` 的相关章节。
- 只定位 `docs/API_CONTRACT.md` 中本次调用的接口章节；状态、任务或归属变化再读取对应领域/异步章节。
- 会修改仓库、环境或进度时仍按根入口先读 `docs/EXECUTION.md`；实际施工只读其中指向的详细计划。

## 验证

按改动范围运行定向 Vitest、`npm run tsc`、`npm run lint` 或生产构建；真实 API、认证、任务、上传下载和最终媒体声明需要无 Mock 的运行证据。
