# 数字人六步真实演示链实现计划

> **已取代：** 该早期计划不再执行。当前批准规格的唯一实现计划是 `docs/superpowers/plans/2026-07-14-digital-human-a2-glm52-guided-flow.md`。

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 在约 2 小时内交付可打开、可操作的六步数字人演示流程，固定 IndexTTS2，真实调用远端 LTX2.3，并将画中画、字幕和花字真实烧录进可下载 MP4。

**架构：** React 单页应用只保留六步主路；FastAPI 新增无数据库的演示路由，以内存 jobId 代理远端 ComfyUI `storyboard-generate` 并轮询结果。远端成片下载到临时目录后由本机 FFmpeg 叠加画中画与 ASS 字幕/花字，再通过本地视频端点预览和下载。

**技术栈：** React 19、TypeScript、Ant Design、Vitest、FastAPI、httpx、pytest、FFmpeg、ComfyUI IndexTTS2/LTX2.3。

---

## 文件结构

- `digital-human-studio/apps/api/src/dh_api/routes/demo.py`：演示提交、状态、视频和下载 HTTP 入口。
- `digital-human-studio/apps/api/src/dh_api/services/demo_gateway.py`：ComfyUI multipart 提交、轮询、输出 URL 解析与内存任务状态。
- `digital-human-studio/apps/api/src/dh_api/services/demo_composer.py`：ASS 字幕/花字生成与 FFmpeg 画中画合成。
- `digital-human-studio/tests/demo/test_demo_gateway.py`：远端网关和状态映射单测。
- `digital-human-studio/tests/demo/test_demo_composer.py`：字幕切分、ASS 转义、FFmpeg 命令单测。
- `digital-human-studio/tests/demo/test_demo_api.py`：multipart、状态和视频端点接口测试。
- `digital-human-studio/apps/web/src/demo/types.ts`：六步表单、导演建议和任务状态类型。
- `digital-human-studio/apps/web/src/demo/script.ts`：新手默认值、台词生成、语义画中画和花字建议。
- `digital-human-studio/apps/web/src/demo/api.ts`：演示 API 提交与轮询适配器。
- `digital-human-studio/apps/web/src/App.tsx`：六步流程页面。
- `digital-human-studio/apps/web/src/styles.css`：演示页面视觉和响应式布局。
- `digital-human-studio/apps/web/src/demo/*.test.ts(x)`：前端纯逻辑与核心交互测试。
- `digital-human-studio/apps/web/vite.config.ts`：本地 API 代理。

### 任务 1：无数据库真实模型网关

**文件：**
- 创建：`digital-human-studio/apps/api/src/dh_api/services/demo_gateway.py`
- 创建：`digital-human-studio/apps/api/src/dh_api/routes/demo.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/routes/router.py`
- 修改：`digital-human-studio/apps/api/pyproject.toml`
- 测试：`digital-human-studio/tests/demo/test_demo_gateway.py`
- 测试：`digital-human-studio/tests/demo/test_demo_api.py`

- [ ] **步骤 1：编写失败测试**，要求 `build_storyboard_markdown()` 生成单场景 Markdown，`extract_remote_video_url()` 支持 `final_output.url`，状态端点返回 `engine=IndexTTS2`，非法文件返回 422。
- [ ] **步骤 2：运行红灯**：`uv run pytest tests/demo/test_demo_gateway.py tests/demo/test_demo_api.py -q`，预期因模块不存在失败。
- [ ] **步骤 3：实现最小网关**：`POST /v1/demo/jobs` 接收 `script_text`、人物图、参考音频、画中画图片和包装配置；提交 `${DEMO_COMFY_BASE_URL}/ai-video/ltx23/storyboard-generate`（当前演示默认使用已配置低显存实例 `http://36.133.55.206:8189`），轮询 `/jobs/{id}`，临时文件保存在系统临时目录。
- [ ] **步骤 4：实现状态/视频入口**：`GET /v1/demo/jobs/{id}` 返回人话阶段和进度；`GET /v1/demo/jobs/{id}/video` 返回合成 MP4；不接数据库、项目或任务中心。
- [ ] **步骤 5：运行绿灯**：相同 pytest 命令预期全部通过。

### 任务 2：真实画中画、字幕与花字合成

**文件：**
- 创建：`digital-human-studio/apps/api/src/dh_api/services/demo_composer.py`
- 测试：`digital-human-studio/tests/demo/test_demo_composer.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_gateway.py`

- [ ] **步骤 1：编写失败测试**，验证中文台词按标点切成 1–2 行字幕、ASS 特殊字符被转义、花字仅在前 3 秒出现、画中画 `overlay` 启用区间来自导演建议。
- [ ] **步骤 2：运行红灯**：`uv run pytest tests/demo/test_demo_composer.py -q`，预期因实现缺失失败。
- [ ] **步骤 3：实现 ASS 生成器**：写入 `Default` 与 `Flower` 两种样式，字幕按字符权重分配真实视频时长，花字使用顶部安全区。
- [ ] **步骤 4：实现 FFmpeg 命令**：人物视频为主输入，画中画缩放至宽度 30%、右上角留安全边距，在建议区间启用；随后烧录 ASS，并复制/编码 AAC 音频。
- [ ] **步骤 5：运行绿灯**：composer 测试和任务 1 测试全部通过。

### 任务 3：六步新手主流程页面

**文件：**
- 创建：`digital-human-studio/apps/web/src/demo/types.ts`
- 创建：`digital-human-studio/apps/web/src/demo/script.ts`
- 创建：`digital-human-studio/apps/web/src/demo/api.ts`
- 创建：`digital-human-studio/apps/web/src/App.tsx`
- 创建：`digital-human-studio/apps/web/src/styles.css`
- 创建：`digital-human-studio/apps/web/src/demo/script.test.ts`
- 创建：`digital-human-studio/apps/web/src/App.test.tsx`
- 修改：`digital-human-studio/apps/web/src/main.tsx`
- 修改：`digital-human-studio/apps/web/vite.config.ts`

- [ ] **步骤 1：编写失败测试**：验证“我还没想好”会填推荐 brief；采用需求后生成可编辑台词；没有人物图或参考音频不能进入确认；导演页显示画中画理由、字幕效果和花字；结果页显示真实 video URL 和下载按钮。
- [ ] **步骤 2：运行红灯**：`pnpm --filter @studio/web test`，预期因组件/函数不存在失败。
- [ ] **步骤 3：实现步骤 1–3**：需求引导、台词编辑、人物照片与声音上传；新手每屏只有一个主按钮，固定显示 IndexTTS2。
- [ ] **步骤 4：实现步骤 4–6**：语义导演建议、包装选择、确认摘要、真实生成进度、结果预览和下载；不出现项目、任务中心、资产或通知入口。
- [ ] **步骤 5：运行绿灯**：前端测试、typecheck 和 build 全部通过。

### 任务 4：真实服务器联调与演示验收

**文件：**
- 修改：仅修复任务 1–3 中被真实联调暴露的问题。

- [ ] **步骤 1：验证远端健康**：请求 `/system_stats`、插件不存在 job 路由和 `/queue`，确认 `DEMO_COMFY_BASE_URL` 在线、LTX 插件已加载、队列可用；GPU0 的 8188 标准实例已在真实烟测中出现 KSampler OOM，不得作为演示默认端点。
- [ ] **步骤 2：启动本地 API 与 Web**：API 使用独立端口 8765（避开本机既有 8000 服务），Vite 使用 5173，确认两个 URL 均可访问。
- [ ] **步骤 3：跑最短真实样例**：使用一张人物图、参考声音和不超过 25 个汉字台词提交，确认远端经历 IndexTTS2 与 LTX2.3，并取得真实 MP4。
- [ ] **步骤 4：验证包装输出**：用 ffprobe 确认最终 MP4 同时含 H.264/兼容视频流和 AAC 音频流；抽帧确认画中画、字幕、花字均已烧录。
- [ ] **步骤 5：浏览器验收**：从第 1 步走到第 6 步，确认页面 URL 可打开、视频可播放、下载响应为 MP4，刷新后允许数据消失。
- [ ] **步骤 6：运行回归**：`uv run pytest tests/demo -q`、`pnpm --filter @studio/web test`、`pnpm --filter @studio/web build`，所有命令退出码必须为 0。

## 自检

- 规格覆盖：六步主路、IndexTTS2、LTX2.3、真实 PIP/字幕/花字、预览和下载均有对应任务。
- 明确排除：数据库、项目、资产、任务中心、通知、版本、VoxCPM2 不进入实现。
- 接口一致：前后端统一使用 `/v1/demo/jobs`、`/v1/demo/jobs/{id}`、`/v1/demo/jobs/{id}/video`。
- 验证边界：只有真实服务器生成、FFmpeg/ffprobe 和浏览器六步验收全部有新鲜证据后，才能宣称完成。
