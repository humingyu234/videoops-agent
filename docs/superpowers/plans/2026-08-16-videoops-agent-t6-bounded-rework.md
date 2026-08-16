# T6 有界局部返工与批准

## 用户可见结果

同一 `AgentRun` 在成品质量检查后，能按失败 criterion 选择最小依赖范围：媒体失败只重渲染，字幕失败只重建时间轴项目并渲染；初始候选之外最多产生两个返工候选。不能自动修复、无可测改善或只剩低置信主观判断时，返回明确的人工批准状态。

## 非目标

- 不进入 T7，不新增页面、Controller、LLM、Planner、插件或通用工作流引擎。
- 不真实调用 Provider/OSS，不重新生成付费声音或数字人。
- 不把 T4 的技术重试、轮询或 lease generation 冒充质量返工。

## 最小施工面

- 为 `AgentRun` 增加独立质量返工计数与 `waiting_approval`，新增窄的 evaluation/approval 持久事实；不增加 PlanStep、Invocation 或 Event 表。
- 在现有受限编排中校验固定 16 项质量合同，按依赖闭包复用已有 8 把工具。
- 字幕修复使用相同 video job 创建新项目再渲染；媒体修复只对同项目/版本重渲染。
- initial、conditional、final 三类批准使用精确 owner、contract、row/approval revision 和 subject digest，不能互相替代。

## 三个验收信号

1. 媒体和字幕失败分别只触发允许的最小下游；字幕路径的 voice/video 根任务 ID 和提交次数保持不变。
2. 初始候选不计入返工，最多新增两个候选；轮询、恢复、幂等回放和 T4 render retry 不增加该计数；首轮无改善、预算耗尽或主观冲突转人工。
3. fresh-context MySQL 边界证明工具已接受但换绑 CAS 前崩溃时，恢复仍复用相同 project/render key 和同一根任务；批准、跨 owner、过期 revision 和迟到结果均 fail-closed。

## 收工

聚焦单元、真实 MySQL 边界、bootstrap、package、开发规范、diff 与秘密门禁全部匹配当前源码后，将 T6 标为 `DONE` 并创建独立 clean checkpoint；随后按负责人持续施工指令进入 T7，两个阶段不得混在同一提交。

## 停止条件

- 首轮修复没有可测改善、返工次数/时间/费用预算耗尽或只剩主观冲突时，当前候选转人工，不继续自动返工。
- 需要重新提交 Provider、改变模板/模型、改变已确认脚本/形象/音色或突破当前本机能力上限时，只生成条件批准事实；本阶段不自动执行该动作。
