# T4 可恢复黄金链编排施工说明

**目标：** 把 T2 `AgentRun` 与 T3 八把白名单工具接成一条确定性黄金链；运行遇到声音、数字人或渲染异步任务时持久化当前任务并暂停，新进程只查询同一任务，最终得到 owned ready 成品或稳定转人工。

## 可见结果与非目标

- 可见结果：一份已确认 Brief 可从 `new`、`voice_job`、`video_job`、`project` 或 `render_task` 五个互斥起点生成结构化步骤；缺字段/权限/费用确认时在工具调用前阻塞，满足时沿现有 Service 推进并可跨实例恢复。
- 非目标：自由 LLM 规划、多 Agent、MCP/插件、任意 HTTP/SQL/命令、通用工作流引擎、新 UI、新表、T5 质量评分、T6 返工策略和 Provider 实跑。

## 冻结契约

- 步骤只有：声音提交/查询/确认、数字人提交/查询、项目准备、渲染提交/查询、成品检查；状态查询复用现有两把查询工具。
- Brief 的 `startAt` 决定唯一后缀；调用方不得提交 owner、tenant、workspace、permissions、输出路径、lease 或 token。
- 提交幂等键固定由 `agentRunId + step + attempt` 派生。恢复时若已保存 waiting task，第一动作只能查询该 task，不能重新提交。
- Profile 只控制 `maxRunSeconds`、`maxResumeAttempts`、`maxProviderSubmissions`、`maxRenderRetries` 与 `pollIntervalSeconds`。声音/数字人 Provider 失败默认转人工；只有持久化为 retryable 的本地 render 可在明确预算内有限重试。
- 取消、超时或预算耗尽只终止 AgentRun；本阶段没有外部取消工具，不宣称已取消 Provider。迟到结果必须因终态/rowVersion/fence 影响 0 行。

## 最小持久化变化

- 不新增表。新增 `110_agent_run_orchestration.sql`：给 `AgentRun` 增加持久化 render 重试计数，并把 `waiting_input` 纳入现有状态/CHECK。
- T2 Service 增加 owner-scoped contract snapshot、缺输入阻塞、同任务延期、成功 voice→video→render 原子换绑、render 有界重试和任意非终态 owner-CAS 停止。
- T4 编排 Service 只依赖 `IAgentRunService` 与 `IAgentToolService`；结构化计划从不可变 Brief/Profile 与当前 waiting task 确定性重建，不建立 PlanStep/Invocation/Event 表。

## 三个验收信号

1. 缺输入/权限/费用预算在工具前阻塞；五个起点只生成白名单后缀，未知或混合参数稳定拒绝。
2. 真实 MySQL 随机表中跨 Spring context 完成 voice→video→render→output；每个提交幂等身份只接受一次，恢复始终查询同一 task。
3. cancel/timeout/recovery/retry budget、跨 owner 和迟到结果全部 fail-closed；最终只采纳 owned ready `timeline_render_output`。

## 验证

- 单元：`AgentRunServiceImplTest`、`AgentRunOrchestrationServiceImplTest` 及既有 `AgentToolServiceImplTest`。
- 边界：扩展现有 `AgentRunPersistenceIT`，继续只使用 `ai_video_test` 随机安全表并精确清理；Provider/OSS/Redis 均 `NOT_RUN`。
- 静态：bootstrap validator/Pester、user-api package、开发规范、diff 与 staged secret/media gate。

## 停止条件

- 任一 owner/CAS/幂等/迟到结果反例不绿即停止；不得以 Mock、文档或测试数量代替真实 MySQL 恢复证据。
