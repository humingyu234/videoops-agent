# T3 类型化黄金链工具实现计划

> **面向 AI 代理的工作者：** 先读根 `AGENTS.md`、`docs/EXECUTION.md` 与后端 `AGENTS.md`，只完成本文件定义的单轮 T3。

**目标：** 把 T1 已真实跑通的固定文案、声音、数字人、项目、时间轴渲染与成品查询包装成显式白名单、强类型、可由后续 Agent 调用的工具，并用既有 T1 成功任务做零 Provider、零 OSS 写入的当前回放证明。

**架构：** Core 冻结跨模块调用/结果 DTO 与服务接口；User 用一个显式 `switch` 适配现有应用 Service。owner、tenant、workspace 与 permissions 只取 `AppPrincipalSnapshotDTO`。不自 HTTP，不建立注册器、反射插件或新任务模型。

**技术栈：** Java 21、Spring Service、Jackson `JsonNode`、JUnit 5、Mockito、现有数字人/Creation/Timeline/AiTask Service。

---

## 用户可见结果与非目标

- 可见结果：同一组类型化工具可提交声音和数字人任务、确认/查询终态、准备项目、提交或幂等回放渲染，并返回当前 owner 的 ready `timeline_render_output` 技术事实和授权下载路径。
- 固定文案直接作为声音提交参数，沿用 T1 页面真实链；不新增未被 T1 使用的脚本持久化工具。
- 非目标：HTTP/UI、Planner/LLM/MCP、通用插件平台、新表、T2 lease 接入、Provider/OSS/FFmpeg 重构、主观质量评分与 T4。

## 停止条件

- owner、权限、参数或幂等任一边界不能在现有 Service 前 fail-closed 时，停在首个产品根因，不以 Mock 或文档替代。
- 既有终态任务无法在不访问 Provider 或真实 OSS 的前提下安全回放时，保留只读事实并请求精确外部授权，不新建任务或媒体。
- 实现若需要新表、HTTP/UI、通用插件框架、T2 lease 改造或 T4 能力，立即停止扩张。

## 实现步骤

1. 在 `ai-video-core` 新增单一 `AgentToolDTOs` 与 `IAgentToolService`：冻结 8 把工具的精确参数和结构化结果；结果不得含 owner 参数、storage key、签名 URL、lease 或 token。
2. 在 `ai-video-user` 新增单一 `AgentToolServiceImpl` 显式白名单：
   - `submit_voice_generation`
   - `confirm_voice_generation`
   - `get_generation_status`
   - `submit_digital_human_video`
   - `prepare_timeline_project`
   - `render_timeline`
   - `get_timeline_render_status`
   - `inspect_timeline_output`
3. 每把工具先校验 canonical personal workspace、精确权限和精确 JSON 字段，再调用现有 Service。渲染参数固定 `match_canvas / 30 fps / high`；状态和检查只返回 owner-scoped 持久事实与 `/api/studio/creation-assets/{id}/content`。
4. 给 Timeline render 增加窄的 owner-scoped 幂等预检：只按既有 key、project、revision 与同一 request digest 回放；命中时在媒体解析前返回，异参数仍稳定冲突，未命中才走原创建链。
5. 聚焦测试覆盖白名单正向委托、未知/缺失/错误/多余参数、权限、跨 owner、固定渲染配置、成功成品三方对账，以及回放不读取媒体、不新增根任务/execution。

## 当前真实边界证明

- 只查询 T1 已终态 voice/video job、project、render task 与 output asset；running job 不进入本轮 smoke，避免 Provider 轮询。
- 使用 T1 原 render 幂等身份从工具入口安全回放两次：应返回同一根任务；异 revision 冲突；任务、execution、version、attempt 与 output 计数不变。
- 不调用 `openOwnedMedia`，不重新下载 OSS。把工具返回的 owned/ready asset SHA/大小与 T1 已授权、Git ignored 的本地 MP4 对账，并新鲜运行 `ffprobe` 与完整解码。
- 若 T1 本地副本或只读开发库事实缺失，记录真实阻塞；不得为补证调用 Provider、真实 OSS 或新渲染。

## 验收与收工

- A：8 把工具的强类型契约、权限、principal/owner 与未知/多余参数正反用例通过。
- B：同 owner 同 key/同参数回放同根任务；同 key异参数稳定冲突；回放前后数据库任务事实不增加。
- C：当前工具查询到 T1 owned ready `timeline_render_output`，其 SHA/大小与本地 MP4 一致，MP4 的 H.264/AAC、9:16、时长和完整解码通过。
- 运行聚焦 Maven 测试、开发规范、`git diff --check`、staged secret/media 检查；更新 `docs/EXECUTION.md` 为 T3 `DONE/PAUSED`，建立一个本地 T3 checkpoint，不 push，不进入 T4。
