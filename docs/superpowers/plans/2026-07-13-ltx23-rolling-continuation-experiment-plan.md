# LTX-2.3 数字人滚动续写实验实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）跟踪进度；不得跳过失败的短片门禁直接运行 60 秒或 94 秒任务。

**目标：** 在物理 GPU 5 的独立 ComfyUI 8195 实例上，实现可恢复的 LTX-2.3 数字人滚动续写实验：调用方上传一张人物图和一条完整音频，执行器用 257 帧窗口、33 帧重叠上下文连续生成，最终只返回一条无音频拼缝的 MP4。

**架构：** 在现有 `ai_video_ltx23_storyboard` ComfyUI 插件旁新增连续生成领域模块，把窗口计划、工作流渲染、质量门禁、持久化和 FFmpeg 合成从 `routes.py` 分离。HTTP 层只接收实验请求和查询状态；后台协程串行调度窗口，使用上一窗口末尾 33 帧作为下一窗口的强视频 guide，并以原始人脸区域作为弱身份锚点。每个通过门禁的窗口原子写入 manifest，可从首个未完成窗口恢复。实验部署脚本只允许操作 `CUDA_VISIBLE_DEVICES=5` 的 8195 实例，不得操作 GPU 0、6、7 或 8188。

**技术栈：** Python 3、aiohttp/ComfyUI `PromptServer`、ComfyUI API 工作流 JSON、LTX-2.3、FFmpeg/ffprobe、OpenCV/NumPy、pytest、Node.js ssh2 部署工具。

---

## 范围与契约决定

- 规格来源：`docs/superpowers/specs/2026-07-13-ltx23-rolling-continuation-experiment-design.md`。
- 本轮不修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`：新增端点是 GPU 5 算法实验入口，不是面向用户的 `/api` 生产接口，不接入 RuoYi、账号归属、额度或任务中心。
- 实验通过后必须另立正式接入规格和计划，把生成操作接入统一任务、文件授权、幂等和额度体系；不得直接向前端暴露本实验端点。
- 唯一允许的推理设备是物理 GPU 5，唯一允许重启的实验实例是 `127.0.0.1:8195`。所有脚本在执行任何远程写入或启动前都要断言端口、PID 命令行和 `CUDA_VISIBLE_DEVICES=5`。
- 不复用 `tools/remote_generate_ltx23_continuous.py` 的“末帧独立生成 + 硬拼接”算法；该脚本仅保留为失败基线。

## 目标文件

实现仓库位于 `D:/Workspace/ai/projects/storyboard`；本计划与实验规格保存在当前 `ai-video` 仓库。

- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_windows.py`
- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_manifest.py`
- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_workflow.py`
- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_quality.py`
- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_runner.py`
- 新增 `comfyui_custom_nodes/ai_video_ltx23_storyboard/ltx23_rolling_template_api.json`
- 修改 `comfyui_custom_nodes/ai_video_ltx23_storyboard/routes.py`
- 修改 `comfyui_custom_nodes/ai_video_ltx23_storyboard/nodes.py`
- 新增 `tests/ltx23_continuous/` 下的单元与契约测试
- 新增 `tools/call_ltx23_continuous.mjs`
- 新增 `tools/deploy_ltx23_continuous_gpu5.mjs`
- 新增 `tools/verify_ltx23_continuous_output.py`

### 任务 1：锁定窗口计划与确定性 seed 规则

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_windows.py`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_windows.py`

- [ ] **步骤 1：先写失败测试。**

覆盖以下精确案例：

- 5 秒、25 fps 生成一个窗口，`valid_frames=125`，推理帧补齐为 257。
- 19.2 秒、25 fps 生成三个窗口，起始帧为 `0, 224, 448`，后两个窗口各携带 33 帧上下文。
- 94 秒、25 fps 的每个 `start_frame`、`valid_end_frame` 单调且不越过 2350 帧；拼接后的有效帧恰好为 2350。
- `window_frames` 和 `overlap_frames` 必须满足 `8n+1`，`stride=window-overlap` 必须为 8 的倍数；非法值抛出 `ValueError`。
- `window_seed(base_seed, start_frame)` 对相同输入稳定、不同全局起始帧不同，并限制在 ComfyUI 接受的 63-bit 正整数范围内。

- [ ] **步骤 2：运行测试并确认红灯。**

运行：

```powershell
python -m pytest tests/ltx23_continuous/test_windows.py -q
```

预期：因 `continuous_windows` 尚不存在而失败。

- [ ] **步骤 3：实现最小领域对象与算法。**

实现不可变 `WindowPlan`，字段固定为 `index`、`start_frame`、`context_frames`、`new_frames`、`inference_frames`、`valid_frames`、`audio_start_seconds`、`audio_duration_seconds`、`seed`。实现 `build_window_plan(duration_seconds, fps=25, window_frames=257, overlap_frames=33, base_seed=...)` 和稳定的整数混合 seed 函数。所有时长先换算为全局帧，再派生秒数，禁止逐段浮点累加。

- [ ] **步骤 4：运行测试并确认绿灯。**

运行同上，预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_windows.py comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_windows.py
git commit -m "feat: add deterministic rolling window plan"
```

### 任务 2：实现可恢复、原子写入的任务 manifest

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_manifest.py`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_manifest.py`

- [ ] **步骤 1：先写失败测试。**

测试状态集合 `queued/running/merging/done/interrupted/failed/cancel_requested/cancelled`，窗口状态集合 `pending/rendering/validating/accepted/rejected`；测试临时文件 + `os.replace` 原子保存、损坏 JSON 不覆盖最后有效快照、恢复点为第一个非 `accepted` 窗口、输入 SHA-256 或关键参数改变时拒绝恢复、终态不能被普通保存改回运行态。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_manifest.py -q
```

- [ ] **步骤 3：实现 manifest。**

实现 `ContinuousJobManifest`、`WindowRecord`、`load_manifest`、`save_manifest_atomic`、`next_resume_index`。manifest 固定记录 schema 版本、输入哈希、完整参数、窗口计划、prompt ID、guide 文件、输出文件、重试次数、质量指标、错误分类和时间戳；保存时 flush + `os.fsync` 后原子替换。

- [ ] **步骤 4：运行绿灯测试。**

运行同上，预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_manifest.py comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_manifest.py
git commit -m "feat: persist resumable continuous manifests"
```

### 任务 3：建立 LTX-2.3 滚动模板契约

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_workflow_contract.py`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/ltx23_rolling_template_api.json`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_workflow.py`

- [ ] **步骤 1：从已验证工作流生成 API 模板快照。**

以服务器 `/opt/ComfyUI/user/default/workflows/LTX2.3无限时长数字人.json` 为图形来源、`/tmp/server_digital_human_ltx23_api.json` 为可运行 API 基线，在 8195 的浏览器工作流中完成 API 格式导出并保存为仓库模板。模板必须包含以下语义节点：人物图加载、完整窗口音频加载、上一窗口视频帧批次加载、两个 `LTXVImgToVideoInplace` guide、音视频 latent、两阶段 sampler、Talking Head LoRA、视频保存。模板中彻底移除或旁路 `LTX2_NAG` 和仙侠 LoRA。

导出只编辑工作流文件，不排队执行；模板中的输出前缀、输入文件名、帧数、seed 和 guide 参数使用可被渲染器覆盖的固定哨兵值。

- [ ] **步骤 2：先写契约失败测试。**

测试模板是 API prompt 字典而非 UI `nodes/links` 图；按 `class_type` 和 `_meta.title` 建立唯一语义角色映射；断言不存在活动的 `LTX2_NAG`、仙侠 LoRA，Talking Head LoRA 强度哨兵可改写，所有连接引用都指向存在节点；缺失或重复角色时给出明确错误。

- [ ] **步骤 3：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_workflow_contract.py -q
```

- [ ] **步骤 4：实现模板解析器与启动前校验。**

实现 `WorkflowRoles.from_template()`，只在模块加载时解析一次角色；渲染时使用角色映射，不把服务器导出的节点 ID散落在业务代码中。增加 `validate_object_info(object_info)`，在部署验证阶段检查 8195 实际节点输入定义与模板一致。

- [ ] **步骤 5：运行绿灯测试。**

运行同上，预期全部通过。

- [ ] **步骤 6：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_workflow_contract.py comfyui_custom_nodes/ai_video_ltx23_storyboard/ltx23_rolling_template_api.json comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_workflow.py
git commit -m "feat: add rolling workflow template contract"
```

### 任务 4：按窗口渲染音频、guide、身份锚点和输出参数

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_workflow_render.py`
- 修改：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_workflow.py`

- [ ] **步骤 1：先写失败测试。**

构造首窗和续窗 fixture，断言：

- 首窗只启用原图第 0 帧 guide，强度 1.0。
- 续窗把上一窗口末尾 33 帧作为当前第 0–32 帧的图像批次 guide，强度 1.0；在第 32 帧追加原始人脸区域锚点，默认强度 0.25。
- 257 帧窗口的音频切片从全局起始帧派生，末窗用静音补齐但 `valid_frames` 不变。
- Talking Head LoRA 固定 0.9，正负提示词包含规格中的身份约束，负面提示词不包含“人物保持一致”。
- seed 来自窗口全局起始帧，两个采样阶段使用同一窗口确定性 seed；输出前缀包含 job ID 和零填充窗口序号。
- 客户端不能传 GPU、服务器地址、任意模板路径或输出绝对路径。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_workflow_render.py -q
```

- [ ] **步骤 3：实现渲染器和媒体准备命令生成器。**

实现纯函数 `render_window_workflow(...)` 和 `build_media_commands(...)`。人脸锚点由一次性预处理生成 RGBA 或 mask，不在每个窗口重复做人脸检测；上一窗口 guide 用 FFmpeg 精确提取最后 33 帧为无损帧序列/FFV1，禁止从带损 MP4 的 `-sseof` 近似抽帧。

- [ ] **步骤 4：运行绿灯测试。**

运行同上，预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_workflow_render.py comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_workflow.py
git commit -m "feat: render rolling LTX23 window workflows"
```

### 任务 5：实现接缝与身份质量门禁

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_quality.py`
- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/fixtures/`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_quality.py`

- [ ] **步骤 1：创建合成 fixture 并先写失败测试。**

用 OpenCV 在测试运行时生成短小的“连续”“位置跳变”“背景变色”“黑帧”“重复帧”序列，避免提交大型二进制。测试帧数/PTS 单调性、黑帧率、重复帧、背景色差、人物中心位移和 overlap 像素/光流残差；人脸检测/embedding 通过可注入适配器测试“无脸、多脸、相似度低、通过”分支。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_quality.py -q
```

- [ ] **步骤 3：实现质量报告。**

实现 `QualityReport` 和 `validate_window(...)`，指标与阈值来自规格：主脸覆盖率 ≥95%、原图身份中位数 ≥0.60、连续 1 秒均值 ≥0.50、相邻 overlap 身份差 ≤0.05、人物中心位移 ≤画宽 3%、背景均色差 <8/255。检测器依赖不可用时必须返回 `unavailable` 并阻止长片晋级，不能把缺测当通过。

- [ ] **步骤 4：运行绿灯测试。**

运行同上，预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_quality.py tests/ltx23_continuous/fixtures comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_quality.py
git commit -m "feat: gate identity and seam quality"
```

### 任务 6：实现串行滚动执行、重试、取消和恢复

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_runner.py`
- 新增：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_runner.py`

- [ ] **步骤 1：用假的 ComfyUI 客户端先写失败测试。**

覆盖：窗口严格串行；只有 `accepted` 输出能成为下一窗 guide；普通质量失败最多重试两次且 seed 不变；OOM、`CUDA error: invalid argument`、pinned tensor/offload 异常立即把实例标记不健康并停止；取消只在当前窗口结束后生效；恢复跳过已接受窗口；每次状态变化都写 manifest；进度按已接受有效帧/总帧计算。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_runner.py -q
```

- [ ] **步骤 3：实现执行器。**

抽象 `ComfyPromptClient` 的 `queue_prompt/wait_history/system_stats`，复用现有 `routes.py` 的 history 错误抽取逻辑但移入可测试公共函数。窗口结束后先校验 history、文件与质量，再接受并准备下一窗；任何 CUDA 健康错误禁止在同一进程内自动重提。

- [ ] **步骤 4：运行绿灯测试。**

运行同上，预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_runner.py comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_runner.py
git commit -m "feat: orchestrate resumable rolling generation"
```

### 任务 7：实现无重复帧合成与完整原音频回挂

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_merge.py`
- 修改：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_runner.py`
- 新增：`D:/Workspace/ai/projects/storyboard/tools/verify_ltx23_continuous_output.py`

- [ ] **步骤 1：先写失败测试。**

使用三段带帧序号的合成视频，断言首窗保留有效帧，后续窗口丢弃前 33 帧 context 后追加，最终序列无缺帧/重复帧；末尾按原音频真实帧数裁切；最终 MP4 只包含一条视频流和一条完整源音频派生的 AAC 流，音视频时长差 ≤0.1 秒。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_merge.py -q
```

- [ ] **步骤 3：实现合成。**

为每个窗口先生成只含有效新增帧的中间无音频视频，再用 concat demuxer 合并；默认不做跨淡化。只有质量报告判定非嘴部轻微空间误差时，才允许最多 4 帧的非嘴部光流对齐，且该路径有独立标记。最后一次性 mux 用户原始完整音频。

- [ ] **步骤 4：运行绿灯测试与工具自测。**

```powershell
python -m pytest tests/ltx23_continuous/test_merge.py -q
python tools/verify_ltx23_continuous_output.py --self-test
```

预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_merge.py comfyui_custom_nodes/ai_video_ltx23_storyboard/continuous_runner.py tools/verify_ltx23_continuous_output.py
git commit -m "feat: merge rolling windows with source audio"
```

### 任务 8：新增实验 HTTP 路由与状态响应

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tests/ltx23_continuous/test_routes.py`
- 修改：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/routes.py`
- 修改：`D:/Workspace/ai/projects/storyboard/comfyui_custom_nodes/ai_video_ltx23_storyboard/nodes.py`

- [ ] **步骤 1：先写失败测试。**

用假的 aiohttp request/runner 测试：

- `POST /ai-video/ltx23/continuous-generate` 只接受 `portrait_image`、`source_audio`、可选 `prompt/base_seed/window_frames/overlap_frames`，校验 MIME、文件大小、窗口参数和重复 job ID。
- `GET /ai-video/ltx23/continuous-jobs/{job_id}` 返回稳定英文状态、总进度、当前窗口、窗口摘要、最终输出和结构化错误。
- `POST /ai-video/ltx23/continuous-jobs/{job_id}/cancel` 只设置 `cancel_requested`。
- `POST /ai-video/ltx23/continuous-jobs/{job_id}/resume` 只允许 `interrupted`，且输入哈希和参数必须匹配。
- 请求中的 host、GPU 或模板路径字段被拒绝；runner 的 server base 固定为当前 8195 loopback。

- [ ] **步骤 2：运行红灯测试。**

```powershell
python -m pytest tests/ltx23_continuous/test_routes.py -q
```

- [ ] **步骤 3：实现薄路由。**

复用 `_read_multipart` 的安全文件名和输入目录约束，但为连续接口单独实现字段白名单。路由只做校验、持久化初始 manifest、启动后台任务和序列化状态；不把窗口循环塞回 `routes.py`。更新信息节点，展示新增实验端点但明确标为 GPU5 experiment。

- [ ] **步骤 4：运行绿灯测试和完整本地测试。**

```powershell
python -m pytest tests/ltx23_continuous -q
python -m py_compile comfyui_custom_nodes/ai_video_ltx23_storyboard/*.py
```

预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tests/ltx23_continuous/test_routes.py comfyui_custom_nodes/ai_video_ltx23_storyboard/routes.py comfyui_custom_nodes/ai_video_ltx23_storyboard/nodes.py
git commit -m "feat: expose continuous generation experiment routes"
```

### 任务 9：编写 GPU5 专用部署与调用工具

**文件：**

- 新增：`D:/Workspace/ai/projects/storyboard/tools/deploy_ltx23_continuous_gpu5.mjs`
- 新增：`D:/Workspace/ai/projects/storyboard/tools/call_ltx23_continuous.mjs`

- [ ] **步骤 1：先写安全检查测试。**

把命令构造函数导出为纯函数并用 Node 内置 `node:test` 验证：部署目标固定 `/opt/ComfyUI/custom_nodes/ai_video_ltx23_storyboard`；只允许端口 8195；启动命令必须含 `CUDA_VISIBLE_DEVICES=5`、独立 DB/log/PID；命令不得出现 8188、8189、GPU 0/6/7 的启动或 kill；不得使用无范围 `pkill`。

运行：

```powershell
node --test tools/deploy_ltx23_continuous_gpu5.test.mjs
```

预期先失败。

- [ ] **步骤 2：实现部署工具。**

先上传到远端临时目录，远端 `py_compile` 和模板 JSON 校验通过后再原子替换插件文件。重启时只读取 `/opt/ComfyUI/comfyui_8195_gpu5.pid`，校验该 PID 命令行含 `main.py`、`--port 8195` 后才发送 TERM；启动参数固定 `CUDA_VISIBLE_DEVICES=5`、独立数据库、日志和 PID。不得改动现有 `tools/deploy_ltx23_plugin.mjs`，避免误用其 8189 逻辑。

- [ ] **步骤 3：实现调用工具。**

支持 `submit/status/cancel/resume`，共享开发密码默认从两端 `application-dev.yml` 读取，环境变量可选覆盖；密码不写入参数、日志或 manifest。默认 URL 固定 `http://36.133.55.206:8195`，输出精简 JSON 状态。

- [ ] **步骤 4：运行安全测试。**

```powershell
node --test tools/deploy_ltx23_continuous_gpu5.test.mjs
```

预期全部通过。

- [ ] **步骤 5：提交本任务。**

```powershell
git add tools/deploy_ltx23_continuous_gpu5.mjs tools/deploy_ltx23_continuous_gpu5.test.mjs tools/call_ltx23_continuous.mjs
git commit -m "build: add GPU5-only continuous deployment tools"
```

### 任务 10：部署前静态验证和 8195 健康检查

**文件：**

- 使用：`D:/Workspace/ai/projects/storyboard/tools/deploy_ltx23_continuous_gpu5.mjs`
- 使用：`D:/Workspace/ai/projects/storyboard/tools/remote_exec.mjs`

- [ ] **步骤 1：记录基线，不修改受保护 GPU。**

通过 SSH 只读记录 `nvidia-smi --query-compute-apps`、8195 PID/命令行和 `/system_stats`。断言 GPU 0、6、7 的 PID 集合在部署前后完全一致；只比较，不 kill、不重启、不清显存。

- [ ] **步骤 2：部署并只重启 8195。**

```powershell
node .\tools\deploy_ltx23_continuous_gpu5.mjs
```

部署工具默认读取两端 `application-dev.yml` 中的共享开发 SSH 配置；需要临时切换目标时才使用环境变量覆盖，且不输出值。

预期：远端 `py_compile`、模板契约、`/object_info` 节点契约通过，8195 `/system_stats` 恢复；8188 PID 不变。

- [ ] **步骤 3：验证端点但不排队。**

查询 8195 的插件信息和不存在 job，预期路由存在且未知 job 返回 404；此步骤不上传媒体、不调用 `/prompt`。

- [ ] **步骤 4：保存部署验证记录。**

把部署时间、8195 PID、`/system_stats` 摘要、模板契约结果及 GPU 0/6/7 前后 PID 集合写入实验任务目录的 `deployment-verification.json`；该文件不得包含 SSH 密码或环境变量。

### 任务 11：按门禁执行 5 秒与 19.2 秒冒烟实验

**文件：**

- 使用：`D:/Workspace/ai/projects/storyboard/tools/call_ltx23_continuous.mjs`
- 使用：`D:/Workspace/ai/projects/storyboard/tools/verify_ltx23_continuous_output.py`

- [ ] **步骤 1：运行 5 秒单窗口基线。**

从同一张人物图与已验证 15 秒音频中精确裁取前 5 秒，只提交到 8195。预期：一个窗口、121/125 个有效帧按实际音频换算、无 NAG、无仙侠 LoRA、任务完成且身份/黑帧/时长门禁通过。若 257 帧推理 OOM，按规格整体切换为 193/33/160 后从单窗重新开始，并把最终参数写入 manifest；不得临时只缩某一窗。

- [ ] **步骤 2：人工检查基线。**

检查人脸、服装、构图、嘴型和音画同步。自动指标通过但肉眼明显不像原图时，标记实验失败并停止，不进入三窗口。

- [ ] **步骤 3：运行约 19.2 秒三窗口实验。**

使用同一人物图、同一提示词和同一 base seed，提交可覆盖三个窗口的音频。预期：至少两个接缝均无黑帧、重复帧、明显姿态跳变；后两窗 manifest 显示 33 帧视频 guide 与 0.25 人脸锚点；最终音频为原始连续轨。

- [ ] **步骤 4：运行自动验收。**

```powershell
python .\tools\verify_ltx23_continuous_output.py --manifest <19秒任务manifest绝对路径>
```

预期：帧数、PTS、时长、单脸、身份、中心位移、背景色差和接缝指标全部通过。失败时只调整身份锚点 `0.15/0.25/0.35` 做短片 A/B，并固定胜出值后重新跑三窗口。

- [ ] **步骤 5：保存短片门禁结论。**

把固定参数、自动指标、人工结论和是否允许晋级写入 19.2 秒任务 manifest；结论为失败时不得创建 60 秒任务。

### 任务 12：验证恢复后再晋级 60 秒与 94 秒

**文件：**

- 使用：上述插件、调用工具和验证工具
- 产物：远端 `/opt/ComfyUI/output/ai_video_ltx23_continuous/<job_id>/manifest.json` 与最终 MP4

- [ ] **步骤 1：验证安全取消/恢复。**

在三窗口实验中于第二窗运行时请求取消，确认当前窗口安全结束后进入 `cancelled`；调用 resume 产生继续执行记录，从第一个未接受窗口继续，已接受窗口的输出哈希和 mtime 不变。

- [ ] **步骤 2：运行 60 秒实验。**

只有任务 11 全部通过才执行。预期约七个窗口，身份相似度不随窗口序号单调下降；任一窗口连续失败或 CUDA 健康错误立即停止，不自动重启 GPU 进程继续长跑。

- [ ] **步骤 3：运行真实 94 秒音频。**

只有 60 秒全部通过才执行。最终验证音视频时长差 ≤0.1 秒、无重复音频、无可感知硬切，所有窗口质量报告存在且通过。

- [ ] **步骤 4：回归保护设备边界。**

再次只读采集 GPU 0、6、7 的 PID 集合，与任务 10 基线比较；预期没有本实验进程、没有新显存占用、没有进程重启。任何差异都视为验收失败并停止后续工作。

## 最终验证命令

本地：

```powershell
cd D:\Workspace\ai\projects\storyboard
python -m pytest tests/ltx23_continuous -q
python -m py_compile comfyui_custom_nodes/ai_video_ltx23_storyboard/*.py
node --test tools/deploy_ltx23_continuous_gpu5.test.mjs
```

远端 GPU5 实验实例：

```text
GET http://127.0.0.1:8195/system_stats
GET http://127.0.0.1:8195/object_info
GET http://127.0.0.1:8195/ai-video/ltx23/continuous-jobs/{job_id}
```

最终产物：

```powershell
python .\tools\verify_ltx23_continuous_output.py --manifest <远端任务manifest的本地副本>
```

完成标准：所有本地测试通过；5 秒、19.2 秒、取消恢复、60 秒、94 秒依次通过各自门禁；最终 MP4 使用完整原音频；GPU 0、6、7 的进程与显存没有被本实验触碰。

## 自检结论

- 计划没有把独立分段硬拼接当成连续生成；续窗明确使用 33 帧视频上下文。
- 计划没有修改正式公共契约或绕过统一任务中心；本轮端点被限定为算法实验入口。
- 每项实现先写失败测试，再写最小实现并运行验证。
- 设备限制在部署脚本、运行器、冒烟顺序和最终验收中均有可执行断言。
- 60 秒和 94 秒不是默认长跑，只有短片与恢复门禁通过后才允许晋级。
