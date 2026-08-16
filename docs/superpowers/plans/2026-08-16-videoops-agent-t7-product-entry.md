# T7 默认入口与可验证交付

## 用户可见结果

登录后默认进入 `/agent`，从已有 owned 人物、原声和确认脚本创建一个受约束 AgentRun；页面展示计划、当前状态、人工批准、可回放 Trace 和精确关联的可下载 MP4。

## 非目标

- 不恢复全部产品页面，不删除 `/studio`，不新增通用聊天、自由 Planner、插件/MCP、多 Agent 或管理端 CRUD。
- 不改写 T2～T6 状态机、任务模型或质量策略，不新建通用事件/证据平台。
- 不自动执行第二次真实 Provider 生成，不触碰旧 8080、`ai_video`、Redis DB0 或未知素材。
- 私有 GitHub 目标固定为 `humingyu234/videoops-agent`；现场 `origin/main` 已存在且停在 T6 checkpoint。T7 clean checkpoint 与最终全链通过前不 push 当前改动，公开可见性、生产部署和签名仍为 `NOT_RUN`。

## 最小施工面

1. 在隔离 `videoops_agent_dev` 只应用冻结的 `100/110/120`，再增加 `/api/agent/runs` 的创建、详情、推进、取消和批准入口；owner、workspace、权限、worker 与 lease 均由服务端派生。
2. 用既有 run、generation job、project、task、asset、evaluation、approval 持久事实生成 owner-scoped Trace；只返回稳定状态、安全摘要和业务 ID。
3. 将 Web 根路由改为 `/agent`，保留 `/studio` 作为人工接管；页面复用现有认证、人物/声音列表与授权下载边界。
4. 用当前 T1 素材和成品完成成功、局部修复、转人工、重启恢复四种可验证场景；Demo Fixture 必须显式标注。
5. 更新 README、LICENSE/第三方声明与 `EXECUTION`，运行聚焦测试、构建、开发规范、diff、秘密/媒体扫描后形成 clean checkpoint。

## 验收信号

- 真实浏览器 `/agent` 经 18081 登录和 API 完成创建、批准、恢复与成品读取；无 Mock、无旧 8080。
- Trace 只来自当前 owner 的持久事实，跨 owner、错任务/资产和过期批准均 fail-closed；恢复继续同一任务且 Provider 提交次数为 1。
- 四场景、最终 MP4、README/许可证、测试与证据匹配同一 commit；真实全链使用项目 DB14、OSS 和已授权 Provider，公开可见性与生产发布仍标为 `NOT_RUN`。

## 停止条件

真实 Provider 已接受但结果未知时不得换键重提；需要第二次付费生成、会破坏隔离数据、无法安全取得凭据或远端私有仓库认证失败时，只冻结对应高风险动作，其余安全施工继续。公开可见性必须由负责人最终确认；T7 完成后不进入 T8。
