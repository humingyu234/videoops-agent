# VideoOps Agent

面向图生数字人口播交付的受约束 Agent：从已确认的交付目标出发，复用现有人物、原声、数字人、时间轴与渲染能力，持续记录任务、质量结论和人工决策，最终交付可下载 MP4。

> 目标产品入口是 `/agent`。T7 全链仍为 `NEEDS_EVIDENCE`，实时状态见 [docs/EXECUTION.md](docs/EXECUTION.md)；下方历史真实样例来自 `/studio` 黄金链，不代表 `/agent` 端到端已经完成。

| 边界 | 证据等级 | 当前状态 |
| --- | --- | --- |
| T1 人工黄金链：`/studio` → IndexTTS2 / ComfyUI → 字幕与渲染 → MP4 | `REAL` | `PASS`，保留一条本机试运行成品 |
| T2–T6：可恢复 AgentRun、白名单工具、质量门禁和有限返工 | `LOCAL` | 阶段 checkpoint 已有本机 MySQL / FFmpeg 及受控 adapter 边界证据 |
| T7 默认 `/agent` 全链与四种演示 | `NOT_RUN` | 入口构建与认证烟测已通过；AgentRun 与四场景仍为 `NEEDS_EVIDENCE` |
| 私有 GitHub 交付目标 | `LOCAL` | `origin` 已指向 [humingyu234/videoops-agent](https://github.com/humingyu234/videoops-agent)，远端 `main` 仍停在 T6 checkpoint；T7 当前改动未 push，公开可见性未验证且冻结 |

`REAL` 表示真实 Provider 或真实媒体边界已经运行；`LOCAL` 表示仅在本机隔离资源或受控 fake 外边界验证；`NOT_RUN` 表示不能作为完成声明。

## 产品纵切面

```text
交付目标 + 已授权人物/原声
  -> 冻结 Brief 与验收标准
  -> 受限步骤与八把白名单工具
  -> 声音 / 数字人 / 时间轴 / 渲染任务
  -> 三层质量验收与最多两次局部返工
  -> 必要时人工批准
  -> owned ready MP4 + AgentRun Trace
```

- `/agent`：参赛默认入口，T7 完成后承载执行、Trace 与批准。
- `/studio`：保留的真实生产链、调试和人工接管入口，不作为默认参赛入口。
- 服务端决定 owner、权限、幂等、任务终态和资产可见性；前端不能覆盖这些事实。
- 自动重试与返工有次数、时间和成本上限；外部结果未知时不重复提交。

## 最小本地启动指针

以下命令用于已完成本机隔离 MySQL、Redis 和 Git-ignored 配置后的无 Mock 开发启动；默认 fail-closed，不会自动打开 Provider 或黄金链能力。

```powershell
.\scripts\start-local-user-api.ps1 -Port 18081

Set-Location .\ai-video-ui\ai-video-webapp
npm ci
npm run dev
```

前端代理默认指向 `http://127.0.0.1:18081`。真实黄金链只能在已获得素材、凭据、费用与 Provider 授权后显式启用；当前施工状态和唯一动作见 [docs/EXECUTION.md](docs/EXECUTION.md)。

## 四种演示的验收门禁

| 场景 | 必须观察到的产品事实 | T7 证据 |
| --- | --- | --- |
| 成功交付 | 从真实 `/agent` 进入同一 AgentRun，关联真实任务与 owned ready MP4，下载后 ffprobe 有效 | `NEEDS_EVIDENCE` |
| 一次局部修复 | 明确质量缺口只使受影响的时间轴/渲染分支重做，声音与数字人根任务不重复提交 | `NEEDS_EVIDENCE` |
| 转人工 | 低置信或主观冲突进入待批准；跨 owner、过期或错误 revision 的决定零 mutation | `NEEDS_EVIDENCE` |
| 重启恢复 | Provider 接受任务后仅重启本项目服务，继续查询同一任务和幂等键，提交计数仍为 1 | `NEEDS_EVIDENCE` |

这些占位只会在当前源码对应的 HTTP/UI、持久化和媒体证据全部成立后改为 `PASS`。

## 已保存的真实媒体基准

T1 经 `/studio` 真实调用 IndexTTS2 与 ComfyUI，并在本地完成字幕与 FFmpeg 渲染。改进版成品保存在 Git-ignored 本机证据目录，不进入仓库：

- SHA-256：`70AF66B5D57A43B3626DF1FE3C1CC85417010EC600C04050F3A93BA77FFEE0B7`
- 大小：`5,244,591` bytes；时长：`25.8 s`
- 视频：`1080 × 1920`、`30 fps`、H.264；音频：AAC
- 完整解码通过，含可见烧录中文字幕；黑帧、冻结和静音检测均为 0，音视频起止差为 0

该样例用于回归质量入口和演示叙事；它不是 T7 `/agent` 新运行的替代证据。

## 工程结构

```text
ai-video-ui/       React 用户端与运营端
ai-video-api/      RuoYi-Vue-Plus 多模块后端
ai-video-worker/   Python 媒体/任务工作进程
ai-video-desktop/  Electron 薄壳
docs/              产品范围、架构、契约与实时执行记录
scripts/           本地启动、验证与安全检查
```

代码来源和脱敏基线见 [docs/BASELINE.md](docs/BASELINE.md)。参与开发先读 [AGENTS.md](AGENTS.md) 与 [RULES.md](RULES.md)；产品范围见 [docs/PROJECT.md](docs/PROJECT.md)，路线见 [docs/PLAN.md](docs/PLAN.md)。

## 私有使用、第三方组件与安全

本仓库当前只面向私有评审、授权开发与试运行；公开发布和公开可见性均未获放行。原创贡献部分不授予公开使用许可，具体见 [LICENSE](LICENSE)。上游代码、字体和依赖仍分别适用其自身许可证，已核验的根层提示见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

- 不提交密钥、口令、Token、签名 URL、私有地址、证书、未授权素材或本机媒体产物。
- 本机配置只通过 Git-ignored 的安全载体和当前进程注入；默认配置保持 Provider fail-closed。
- push 前必须重新运行秘密、媒体与公开快照门禁，并人工复核目标仓库与可见性。
