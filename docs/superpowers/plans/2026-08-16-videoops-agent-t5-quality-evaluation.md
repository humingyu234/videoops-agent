# T5 三层质量验收

## 用户可见结果

现有 `inspect_timeline_output` 对 owned、ready 的最终成品返回固定 16 项质量结论；每项都有 criterion code、规则版本、脱敏证据、置信度和 `PASS`/`FAIL`/`REVIEW`，不返回总分。

## 非目标

- 不做 T6 返工、T7 UI 或新工具。
- 不调用真实 Provider/OSS，不重新生成或付费。
- 不引入表、通用评价平台、插件、LLM/OCR/人脸/口型评价器。

## 最小施工面

- 在现有时间轴媒体边界补 codec 事实与完整 FFmpeg 解码检查。
- 新增一个 owner-scoped 质量 Service，绑定 render task、精确 output asset、项目脚本与不可变 `render_input` 版本。
- 原位丰富 `inspect_timeline_output` 的结构化结果；保留现有 8 把工具与 T4 状态机。
- 更新匹配测试和 `docs/EXECUTION.md`。

## 三个验收信号

1. 正例、单变量明确反例和边界反例证明 9 项已配置确定性规则不会因 JSON 物理格式、近似字符串、250 ms 边界或 1 ms 字幕偏差假绿；未配置的 must/prohibited 与 5 项低置信感知事实共 7 项只能 `REVIEW`。对外层级固定为 `media=5`、`content_layout=6`、`perceptual=5`。
2. 当前 T1 MP4 通过同一生产质量入口、真实 FFprobe 与完整 FFmpeg 解码；任务/资产/SHA、项目脚本及不可变时间轴版本一致。
3. 聚焦测试、user-api package、开发规范、diff 与秘密/媒体门禁全绿；无 Provider/OSS 请求或仓库内运行产物。

## 收工

只在上述信号全部成立后把 T5 标为 `DONE`，创建一个本地 clean checkpoint，随后 `PAUSED` 等待独立验收；不进入 T6/T7。

## 停止条件

- 同一真实入口纠偏后仍不能完成 FFprobe 或完整解码时，保留首个产品根因并停止，不用更多夹具掩盖失败。
- 任何结论需要真实 Provider/OSS、重新生成、付费调用或低置信主观自动判定时停止该动作并保持 `REVIEW`。
