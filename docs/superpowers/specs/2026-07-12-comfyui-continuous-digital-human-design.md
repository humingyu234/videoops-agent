# ComfyUI 连续数字人生成服务设计

## 1. 文档信息

- 日期：2026-07-12
- 模块：数字人与声音 / ComfyUI 连续图生数字人执行器
- 状态：设计已确认，待书面规格审查
- 技术栈：ComfyUI + LTX-2.3 + VoxCPM2 + FFmpeg
- 本次输入：分镜 Markdown、用户自拍、用户参考录音
- 本次输出：单个 9:16 MP4

## 2. 目标

在 ComfyUI 内实现一个可由业务后端直接调用的连续数字人生成服务。服务必须：

1. 从 Markdown 分镜中提取完整口播台词、段落情绪、语速和停顿意图。
2. 使用同一份参考录音，通过 VoxCPM2 以全文为单位生成一条完整连续克隆音轨；允许整段失败重试，但禁止分段配音。
3. 使用 LTX-2.3 在同一个业务任务内部生成连续数字人画面。
4. 不按 11 个分镜分别配音、分别生成再硬拼接。
5. 最终只向调用方返回一个 `1080×1920`、24 fps 的 MP4。
6. GPU 0、6、7 为硬禁区；任何安装、启动、推理或测试均不得占用或重启这三张卡上的进程。

本设计中的“一次性生成数字人”定义为：调用方只创建一个任务；每个声音尝试都必须以全文为单位生成，最多允许两次 VoxCPM2 生成调用，最终只采用一条通过门禁的完整音轨；最终只有一个成片。LTX-2.3 因长时生成限制在任务内部使用连续滑窗，但内部窗口不作为分镜、任务或结果暴露给调用方。

## 3. 范围

### 3.1 本次包含

- ComfyUI 自定义节点包和 HTTP 路由。
- T03 表格型 Markdown 解析。
- 参考音频检测、裁静音、声道选择和 PCM 标准化。
- VoxCPM2 整段零样本声音克隆。
- LTX-2.3 Union Control 连续姿态/身份生成。
- LTX-2.3 LipDub 嘴型驱动。
- 长视频窗口规划、续帧、重叠融合和断点记录。
- 最终 MP4 编码、校验和唯一结果返回。
- 可供业务后端调用的创建任务和查询任务接口。
- 本次附件的端到端生成与验收。

### 3.2 本次不包含

- React 页面、Electron 页面或管理端页面。
- RuoYi-Vue-Plus Controller、Service、Mapper、数据库表和任务中心页面实现。
- BGM、花字、字幕、音效和多背景包装。
- 11 个景别硬切或后期数字变焦。
- 声音 LoRA/SFT 训练；本次使用 VoxCPM2 零样本克隆。

业务后端后续必须先创建平台任务，再调用本服务并将 ComfyUI 状态和结果回写任务中心。本次只提供稳定的外部执行器接口，不在业务后端重复实现生成逻辑。

## 4. 已确认输入事实

### 4.1 Markdown

- 模板：`模板T03_认知破局型.md`
- 台词段落：11 段。
- 可发音字符约 473 个，不计标点和空格。
- 分镜参考时长合计 129 秒。
- 按声明语速和必要停顿估算，实际完整音轨更可能为 132–134 秒。
- 最终视频以 VoxCPM2 实际输出音频时长为准，不截断台词以强行满足 120 秒。

### 4.2 参考录音

- 文件：`韩老师音频02.mp3`
- 时长：13.200 秒。
- 格式：48 kHz、双声道、96 kbps MP3。
- 活跃语音约 10.025 秒。
- 存在约 0.855 秒头静音和 0.751 秒尾静音。
- 左右声道 RMS 相差约 5.85 dB。

参考录音可用于零样本克隆，但生成前必须自动选取更可靠声道、裁除多余头尾静音并转换为 48 kHz 单声道 PCM WAV。不得做激进降噪，以免损伤音色。

声道决策必须可复现：逐声道计算噪声底、有效语音 RMS、峰值、削波样本比例、DC 偏移、VAD 活跃比例和估算 SNR；若声道相关系数不低于 0.98 且 RMS 差不超过 1.5 dB，则等权下混，否则选取无削波且 SNR 最高的声道。VAD 裁剪后保留 200 ms 头部余量和 300 ms 尾部余量，输出 48 kHz、16-bit PCM 单声道，目标综合响度约 -23 LUFS、真峰值不高于 -3 dBFS。选择结果、裁剪区间和统计必须进入 manifest。

### 4.3 自拍

- 尺寸：810×1440，原始比例即 9:16。
- 正脸、嘴部无遮挡、无手部进入画面。
- 存在逆光、混合色温、背景直线较多和镜像自拍特征。

预处理只做等比例胸口以上构图、轻量亮度/白平衡修正和模型需要的尺寸缩放，不拉伸、不补手、不大幅扩图、不改变人物身份。

自拍预处理规则：

- 保留原始镜像方向，不自动翻转；manifest 写入 `mirroredInput=true`。
- 必须且只能检测到一张主脸；脸框宽度不得小于源图宽度的 20%，偏航角绝对值不得超过 15°。
- 拉普拉斯清晰度、暗部/高光剪切比例和最小脸部像素阈值在本次素材冒烟基线中标定；未达标直接返回 `INVALID_PORTRAIT`。
- 以双眼中点和脸框为锚点生成 9:16 裁切框，保留完整发型、双肩和胸口；相对源图最大放大倍数为 1.25。
- 不做生成式扩图。亮度增益限制在 ±10%，单通道白平衡增益限制在 0.9–1.1。
- manifest 保存源尺寸、人脸框、裁切矩形、缩放倍数、镜像标记和全部修正参数。

## 5. 方案比较与决策

### 5.1 方案 A：整段音频 + LTX 连续滑窗（采用）

- VoxCPM2 每个 attempt 都生成完整 WAV；首次未通过门禁时最多再全文重试一次，最终只采用一条成功音轨。
- LTX-2.3 内部按固定时间窗处理同一连续时间轴。
- 上一窗口的稳定末帧作为下一窗口首帧条件。
- 相邻窗口保留重叠帧并做融合。
- LipDub 每个窗口读取完整音轨的对应时间段，不重新合成声音。
- 最终重新挂回未切分的完整 WAV。

优点：符合指定模型架构；声音连续；人物、构图和动作状态可跨窗口传递；调用方只看到一个任务和一个 MP4。

代价：内部仍有多个 LTX 推理窗口；必须做断点、重试和接缝质量控制。

### 5.2 方案 B：强行 120 秒单次 LTX 采样（不采用）

120 秒、24 fps 等于约 2880 帧。当前 LTX 工作流没有可验证的生成阶段时间流式机制，单次长采样在 A100 40GB 上具有不可接受的显存、耗时和失败恢复风险。

### 5.3 方案 C：11 个分镜分别生成再拼接（不采用）

这是现有插件的工作方式。每段独立 TTS、独立随机种子和独立视频采样会造成音色、韵律、脸部、嘴型和动作在切点割裂，与需求直接冲突。

## 6. 总体架构

```text
业务后端
  -> POST /ai-video/ltx23/continuous-generate
    -> ComfyUI 连续任务编排器
      -> Markdown 解析与输入校验
      -> 参考音频预处理
      -> VoxCPM2 整段克隆音频
      -> 全文时间戳对齐与克制姿态视频渲染
      -> 连续时间轴与 LTX 窗口规划
      -> LTX Union Control 串行续帧
      -> LTX LipDub 工作池
      -> 重叠帧融合与整段音频回挂
      -> 1080×1920 MP4 校验
  <- GET /ai-video/ltx23/continuous-jobs/{jobId}
```

### 6.1 进程与 GPU 隔离

- GPU 0、6、7 永不进入 worker allowlist。
- 编排实例默认运行在 GPU 5 的隔离 ComfyUI 进程。
- GPU 1–5 的隔离实例默认映射到本机端口 8191–8195；对外创建任务和查询任务统一由 8195 编排实例承载。
- Union Control 在 GPU 5 串行执行，保证上一窗口状态能传递到下一窗口。
- LipDub 可使用 GPU 1–4 工作池并行处理已经完成的 Union 窗口。
- 所有进程通过 `CUDA_VISIBLE_DEVICES` 只看到分配给自己的单张 GPU。
- 不停止、不重启、不修改 GPU 0、6、7 上现有进程。
- 若 GPU 1–4 任一实例不可用，任务降级为可用 worker 串行处理，不得转移到禁用 GPU。

服务端 GPU 映射不可由请求覆盖：`8191→GPU1`、`8192→GPU2`、`8193→GPU3`、`8194→GPU4`、`8195→GPU5`。每次 dispatch 前再次校验物理 GPU 属于 `{1,2,3,4,5}`。GPU 5 的 Union 全局容量为 1；GPU 1–4 的 LipDub worker 各容量为 1；任务队列默认最多 2 个，超过时返回可重试的 `QUEUE_FULL`。GPU 1–4 全部不可用时，允许等全部 Union 完成后由 GPU 5 串行 LipDub；GPU 5 不可用时任务不得启动。

### 6.2 模型与节点预检

创建任务前必须核对 8191–8195 `/system_stats`、`/object_info` 和模型文件。模型根目录为 `/opt/ComfyUI/models/`，本次允许使用的精确模型文件为：

- `ltx-2.3-22b-dev-fp8.safetensors`，作为正式工作流 checkpoint。
- `ltx-2.3-22b-dev.safetensors`，仅作为已安装的全精度候选，未经重新验收不得替代 FP8 工作流。
- `ltx-2.3-22b-distilled-lora-384_1.1.safetensors`。
- `ltxv/ltx2/ltx-2.3-22b-ic-lora-union-control-ref0.5.safetensors`。
- `ltxv/ltx2/ltx-2.3-22b-ic-lora-lipdub-0.9.safetensors`。
- `gemma_3_12B_it_fp8_e4m3fn.safetensors`。
- `ltx-2.3-spatial-upscaler-x2-1.1.safetensors`。
- Union/LipDub ready 工作流使用的 ICLoRA、音视频拆分、尺寸调整和 SaveVideo 节点。
- VoxCPM2 及 FFmpeg。

VoxCPM2 模型固定为 `/opt/ComfyUI/models/TTS/VoxCPM2_ms`，不得在线更新或自动回退到同名目录。预检逐项核对以下 9 个常规文件；大小单位为字节，SHA256 使用小写或大写不影响比较：

| 文件 | 大小 | SHA256 |
|---|---:|---|
| `.gitattributes` | 2106 | `5B59E8A0D94EAE3F04DBDAA6B9DF0DC1840B4803ABAF8738DB141F4013E1EF34` |
| `README.md` | 7776 | `7384FAD93CE2D98F47D5C3170597F3B31D414C12C92E7FDF3121FA90F19FE29D` |
| `audiovae.pth` | 376951122 | `94B5D51E107E0507D4ACC976CFDADB64EDD6FD06D1F751DADBF2FD1594274BF1` |
| `config.json` | 4336 | `405F0DCD92F7FEBA6011ED4EAC5C8D4F74CBA9712F07FD5CFA3063BBDD95402C` |
| `model.safetensors` | 4580080592 | `F7F964CFA9DA23653BAEC6E6F7750719977AD944ED9F95FE52FE3A620506891D` |
| `special_tokens_map.json` | 1632 | `068594063E37662C02B21ACF42EBB334EF6A74FB810E68A2368F88F08351DE76` |
| `tokenization_voxcpm2.py` | 2895 | `84489EA32B6EE0CAE22ED5480CACB6DF85C46624C3119BE9A2021C3649A12729` |
| `tokenizer.json` | 3676772 | `F8984687E4A92A3503D521396D454B7D68E9FDAAB2A0288EB3536C7C1AA4BC20` |
| `tokenizer_config.json` | 5059 | `E78A3EBB48A0B9437EFD1823B6B726C823DA89E49DD8BCC90C02419D9BAA772B` |

已验证的全文声音脚本基线为 `D:/Workspace/ai/projects/storyboard/tools/remote_generate_full_voice.py`，SHA256 `6B1B8F5B23D4D6B68ABB6B711BB5C1D705A0FCEB7F1CB9CD76DFAE2CB597E527`。权威插件基于其 `VoxCPM.from_pretrained(..., optimize=True, device="cuda", load_denoiser=False)` 和 `model.generate(..., retry_badcase=True)` 调用实现受控子进程。

VoxCPM2 子进程必须使用 `/opt/ComfyUI/codex_envs/voxcpm2/bin/python`（Python `3.12.3`）；不得使用无法导入 `voxcpm` 的 `/opt/aigcpanel-cloud/venv/bin/python3`。固定包路径为 `/opt/ComfyUI/codex_envs/voxcpm2/lib/python3.12/site-packages/voxcpm/`，版本为 `voxcpm==2.0.3`，顶层源码哈希为：`__init__.py` → `A6A971983CE2777468802B1873FDF455220E49B68C445C503C8CDC677DAFA89B`、`core.py` → `AA8965AF33EBBBC611B2BAD18C67B99CFFFAC7B7B87900F49EFA8D454F2755D2`、`cli.py` → `856AABAA3E7C170E1A7D54295E4DE1CE9263A987DF9C690DA0748BD709B8D7F1`、`zipenhancer.py` → `48E591C4117CC7EEA2E3FD54363D2BCF0F306B70E994320EACF0E025636BAE27`。

该 venv 启用了 system site packages，因此预检必须逐项导入并精确核对版本，任一漂移即返回 `DEPENDENCY_PREFLIGHT_FAILED`，运行时不得 `pip install`：

- `torch==2.5.1+cu124`、`torchaudio==2.5.1`、`torchvision==0.20.1`。
- `transformers==4.51.3`、`tokenizers==0.21.4`、`safetensors==0.8.0`、`huggingface_hub==0.36.2`。
- `soundfile==0.12.1`、`numpy==2.4.6`、`scipy==1.17.1`、`librosa==0.11.0`、`sentencepiece==0.2.1`。
- `accelerate==1.14.0`、`diffusers==0.38.0`、`einops==0.8.1`、`flash_attn==2.8.3.post1`。
- `modelscope==1.38.1`、`TorchCodec==0.1.1`、`funasr==1.3.14`、`wetext==0.0.4`。

模型 `config.json` 还必须声明 architecture `voxcpm2`、device `cuda`、dtype `bfloat16`、AudioVAE 输入 16 kHz/输出 48 kHz 和 `max_length=8192`；这些字段同样进入预检报告。

工作流模板不得从名称相近的副本中临时挑选，唯一权威来源和部署目标为：

- Union 来源：`D:/Workspace/ai/projects/storyboard/analysis_audio/ltx23_full_video_test/union_api_ready.json`，SHA256 `170AC9859943500E2D43A0BE3920D0C1061BA14B6460BEED36F910B6C30B888A`；复制为插件内 `workflows/union_api_v1.json`。
- LipDub 来源：`D:/Workspace/ai/projects/storyboard/analysis_audio/ltx23_full_video_test/lipdub_api_ready.json`，SHA256 `184620CC65AF9AAC198426979BBA540DA9A38145E5520A7300191775D456CD8D`；复制为插件内 `workflows/lipdub_api_v1.json`。
- 不得使用 `upload_bundle` 下的两个变体；其 Union/LipDub SHA256 分别为 `A8A05C973A08F105B8997584CBEE8EB64F4E744AA4A3453B803FEBD75EE20079` 和 `A95740B95DAC8A9331DE09A81FE7A06E5ABB8C220E208A9C2889053F69DFA90E`。

转写、对齐和说话人相似度依赖固定如下：

- `openai-whisper==20250625`、`stable-ts==2.19.1`，模型 `/opt/ComfyUI/models/stt/whisper/large-v3-turbo.pt`，大小 `1617941637` 字节，SHA256 `AFF26AE408ABCBA5FBF8813C21E62B0941638C5F6EEBFB145BE0C9839262A19A`。
- Whisper/stable-ts 必须由独立 CPU 子进程以 `CUDA_VISIBLE_DEVICES=""` 和 `device=cpu` 运行；禁止直接调用会继承 ComfyUI GPU 的 ComfyUI-Whisper 节点，以免误用物理 GPU 0。
- CAM++ 固定使用 `/opt/ComfyUI/models/TTS/campplus/campplus_cn_common.bin`，大小 `28036335` 字节，SHA256 `3388CF5FD3493C9AC9C69851D8E7A8BADCFB4F3DC631020C4961371646D5ADA8`，同样由 `CUDA_VISIBLE_DEVICES=""` 的 CPU 子进程运行，不允许以“等价编码器”替换。

预检记录精确模型路径、文件大小/哈希、workflow 哈希、节点版本和 FFmpeg 版本；缺少依赖时返回 `DEPENDENCY_PREFLIGHT_FAILED`，不得边生成边下载模型。

### 6.3 VoxCPM2 进程生命周期

VoxCPM2 不在长期运行的 ComfyUI aiohttp 进程内同步加载。编排器在视频阶段之前使用受控子进程独占允许的 GPU（优先 GPU 5，必要时选择当时空闲的 GPU 1），显式设置单卡 `CUDA_VISIBLE_DEVICES`；预处理和 FFmpeg 禁用 CUDA。子进程结束后必须确认退出并验证显存释放，再允许 LTX Union 加载。超时或显存未释放返回 `VOICE_WORKER_CLEANUP_FAILED`。

## 7. 数据流

### 7.1 Markdown 解析

解析器支持本次 T03 表格格式：

- `### 镜号NN`
- `| 台词 | ... |`
- `| 段落类型 | ... |`
- `| 语速 | ... |`
- `| 数字人 | ...神态 |`
- `| 参考时长 | ... |`

输出统一结构：

```json
{
  "paragraphs": [
    {
      "id": "P1",
      "text": "为什么你的产品卖点明明很好……",
      "emotion": "锐利、疑问",
      "speedCpm": 240,
      "pauseAfterMs": 400,
      "visualIntent": "精神锐利，疑问共情"
    }
  ],
  "fullText": "……"
}
```

Markdown 中的 `+` 朗读为“和”，`【痛点】` 朗读为“痛点”，避免符号读法不稳定。原文内容保留在任务清单中，规范化只作用于 TTS 文本。

解析优先级：

1. 每个 `镜号NN` 详细表中的台词、段落类型、具体语速和神态为该段事实来源。
2. 全局语速/神态表只在详细表缺字段时提供默认值。
3. 参考时长只用于生成前估算，不作为最终切点。
4. VoxCPM2 生成后的强制对齐时间戳是最终音视频时间轴；P9/P10 等文档内部时长冲突不得通过硬切时间解决。

### 7.2 VoxCPM2 整段音频

1. 解码参考 MP3。
2. 计算各声道 RMS、峰值和静音区间。
3. 选择非静音且质量更高的声道。
4. 裁去头尾长静音，保留自然句内停顿。
5. 转成 48 kHz 单声道 PCM WAV。
6. 用 Whisper 转写参考录音；置信度通过时使用 `prompt_wav_path + prompt_text + reference_wav_path` 的高保真克隆模式，未通过时降级为仅 `reference_wav_path` 的可控克隆模式。
7. 使用统一音色、统一 seed、统一推理参数，以全文为一个请求生成音频。
8. 全局控制提示描述完整情绪推进，不在正文插入可能被朗读的标签。
9. 使用 Whisper/stable-ts 对生成音频做字符级或词级时间戳对齐，并将识别结果动态对齐到 P1–P11 目标文本。
10. 输出 `full_voice.wav`、`paragraph_timestamps.json` 和音频统计 JSON。

建议默认控制提示：

```text
同一名中年男性，真实、克制、专业的中文短视频讲解声线，全程音色一致，
不喊叫、不吞字。开头锐利设问；随后沉稳颠覆并客观举例；中段平和共情、
理性分析后转克制警示；后段逐步上扬、笃定自信；核心金句放慢重读；
最后亲和收束。句尾自然停顿，全文最后留白。
```

默认参数：

- `cfg_value=2.0`
- `inference_timesteps=10`
- `normalize=true`
- `denoise=false`
- 固定任务 seed，可显式覆盖。

音频门禁：

- 先按每段可发音字符数、详细表语速和停顿计算 `plannedDuration`；实际时长必须落在该值的 ±20% 内，并同时满足 90–180 秒绝对安全范围。
- 非静音比例必须大于 55%。
- 峰值不得削波。
- 文件必须能被 FFmpeg 和 soundfile 解码。
- 规范化文本字符覆盖率必须不低于 98%，字符错误率不得高于 5%。
- 动态对齐不得出现无法归属到 P1–P11 的长重复片段或整段漏读。
- 使用上述固定 CAM++ 模型比较参考音频与生成音频，相似度阈值在短样冒烟测试中标定，未达标不得进入视频生成。
- P1–P11 必须全部得到单调递增且不重叠的起止时间戳。
- P1 的语速应快于 P2，P10 核心金句应较 P7–P9 放慢，P11 末尾必须包含至少 0.8 秒留白；这些为韵律软门禁，失败时记录警告并进入人工试听抽查。
- 每次尝试始终生成全文；不通过时最多以新 seed 重试一次。两次均失败则任务失败，不进入视频生成。

manifest 必须记录 `generateCallCount`、每次 seed、`retry_badcase` 设置、克隆模式、参考转写、每次候选音频哈希和最终采用音频哈希。“一次完整音轨”指任务最终只采用一个成功的全文音轨，不代表失败后禁止整段重试。

### 7.3 连续视觉提示

全片固定基础提示：

- 同一人物、同一脸型、同一发型、同一白色上衣、同一背景、同一相机。
- 9:16 胸口以上正面构图。
- 固定机位，无切镜、无推拉摇移、无背景替换。
- 双手和手臂保持画外，不补手，不做手势。
- 头部基本固定，只允许低频自然眨眼和极轻微呼吸感。
- 嘴型跟随中文音频；停顿时自然闭嘴。

段落视觉意图只改变轻微面部表情，不改变场景、景别、服装和身体动作：

- P1：锐利疑问。
- P2–P3：沉稳严肃。
- P4：平和共情。
- P5–P6：理性、克制警示。
- P7–P9：笃定、自信逐步上扬。
- P10：核心金句强调。
- P11：亲和收束。

每个视觉意图的生效范围必须来自 `paragraph_timestamps.json`，不得使用 Markdown 的预估秒数直接切换。一个 LTX 窗口跨越两个段落时，以窗口中心时刻的段落为主提示，并将相邻段落意图作为弱提示，避免窗口边界发生突变。

### 7.4 LTX 窗口规划

默认参数：

- 帧率：24 fps。
- 窗口：121 帧，满足 `8k+1`。
- 重叠：8 帧。
- 步长：113 帧。
- 单窗时长：约 5.04 秒。
- 工作分辨率：约 544×960。
- 二阶段输出：1088×1920。
- 最终居中裁切：1080×1920。

窗口数公式：

```text
totalFrames = ceil(audioDurationSeconds × 24)
windowCount = 1 + ceil(max(0, totalFrames - 121) / 113)
```

120 秒约 26 个窗口；132–134 秒约 28–29 个窗口。

末窗口不足 121 帧时：

- 音频尾部补静音到 121 帧对应长度。
- 姿态视频复制最后一帧补齐。
- 视觉提示固定为 P11 的亲和闭嘴收束状态。
- manifest 记录 `validFrames` 与 `paddedFrames`。
- 合成后严格按原始完整 WAV 时长裁掉补齐帧和补齐静音。

窗口采用 0-based、右开区间：第 `i` 窗为 `[startFrame, startFrame + 121)`，其中 `startFrame=i×113`。48 kHz/24 fps 恰为每帧 2000 个音频采样点，因此音频区间为 `[startFrame×2000, (startFrame+121)×2000)`。下一窗条件帧固定为上一窗本地索引 113，即重叠区首帧；相邻两窗重叠全局帧为 `[nextStart, previousEnd)`，恰好 8 帧。

每个窗口记录：

- 时间范围。
- 音频切片范围。
- 当前情绪意图。
- Union prompt ID 和输出。
- LipDub prompt ID 和输出。
- 重试次数。
- 校验结果。

### 7.5 克制姿态源

- Union 工作流要求一个连续控制视频。权威插件复制现有 `talking.glb` 为 `assets/talking.glb`，其 SHA256 固定为 `1B7BF67866360665426BB99E4C71BD619F19B408453C24E30F0C3071601EEE5C`。渲染器以 `D:/Workspace/ai/projects/storyboard/tools/render_gltf_pose_video.py`（SHA256 `DE71934BA6EB6338C3FA8811997CD588635934824AB4E527967DF3B893D5050E`）为唯一基线，按下述动作约束修改后部署到插件；预检必须记录部署后脚本的精确 SHA256，manifest 也必须固定该哈希。
- 固定动画名为 `Idle_Talking_Loop`，但不得原样输出动作：锁定 root/hips，仅保留 spine/neck/head 相对 bind pose 的 15% 运动幅度；姿态图不绘制 upper-arm、forearm、hand 及手指节点，投影固定为正面胸口以上。由此手臂位于控制画外，头部只保留极低幅度呼吸感。
- 姿态视频一次渲染为 544×960、24 fps，总帧数为补齐后的 `paddedTotalFrames`，再按与音频完全相同的窗口区间切片。
- 实现的第一个姿态冒烟测试必须输出定时抽帧接触图，并由无手臂/无手势检测门禁通过后才允许进入 Union 全文任务。
- 渲染失败返回 `POSE_RENDER_FAILED`，不得用随机动作视频降级。

### 7.6 Union Control 续帧

- 第一窗口使用预处理后的自拍作为身份和首帧条件。
- 后续窗口使用上一窗口本地索引 113（重叠区首帧）作为首帧条件，保持与已验证脚本一致。
- 姿态驱动保持胸口以上静止构图，仅保留微弱呼吸，不生成手势。
- Union 阶段必须按窗口顺序执行，禁止乱序并行。
- 每个窗口提交前，将对应姿态切片、首窗口的预处理自拍或后续窗口的 `continuation_NN.png` 原子写入 `/opt/ComfyUI/input/ai_video_ltx23_continuous/{jobId}/{windowIndex}/`；写入临时文件、校验可解码和 SHA256 后再 `replace` 为最终文件名。manifest 记录所有 staging 文件的相对路径、大小和哈希；任务终态且超出保留期后才允许清理。

### 7.7 LipDub

- 现有 LipDub ready 工作流从输入 MP4 中提取音频。服务先将对应的 121 帧 Union 视频与完整音轨的精确 121 帧时间切片（不足处已补静音）无损/低损 mux 为 `union_voice_NN.mp4`，再交给 LipDub。
- mux 文件先写任务临时目录，校验后原子登记到共享 ComfyUI input 子目录；所有 worker 只读取该任务自己的 staging 路径。
- 所有窗口共享同一完整 VoxCPM2 音轨，不允许再次 TTS。
- 已完成的 Union 窗口可立即进入 GPU 1–4 LipDub 工作池。
- LipDub 失败只重试该窗口，不重做声音和已成功窗口。

### 7.8 合成

1. 在 8 帧重叠区计算人脸关键点、嘴部开合和帧间差异，选择代价最低的单一接缝；嘴部 ROI 不做双帧透明叠加，避免双嘴和模糊。必要时只对嘴部以外区域做不超过 2 帧的光流对齐融合。
2. 输出视频先不使用窗口音轨。
3. 按完整 VoxCPM2 WAV 的真实时长裁齐视频。
4. 将未切分的完整 WAV 一次性编码为 AAC 并回挂。
5. 输出 `final.mp4`。

最终编码：

- 容器：MP4。
- 视频：H.264、`yuv420p`、1080×1920、24 fps。
- 音频：AAC-LC、48 kHz、单声道。
- `faststart`：开启。

## 8. ComfyUI HTTP 契约

### 8.1 创建任务

```http
POST /ai-video/ltx23/continuous-generate
Content-Type: multipart/form-data
Authorization: Bearer <service-token>
```

必填字段：

- `script_md`
- `portrait_image`
- `voice_reference_audio`
- `idempotency_key`

可选字段：

- `seed`
- `retry_of_job_id`：仅用于从终态失败任务创建新的执行器 job；必须使用新的 `idempotency_key`。

worker 地址和 GPU 映射只能来自服务端配置，客户端不得传入 GPU 编号或任意 worker URL。

输入限制：

- `script_md`：UTF-8 Markdown，最大 2 MiB。
- `portrait_image`：JPG/PNG，最大 10 MiB。
- `voice_reference_audio`：MP3/WAV/M4A，最大 50 MiB。
- `idempotency_key`：1–128 个 URL-safe 字符。
- `seed`：可选无符号 63-bit 整数。
- 输出目录和文件前缀完全由服务端基于 job ID 生成。

响应：

```json
{
  "job_id": "continuous_001",
  "status": "queued",
  "active_stages": [],
  "progress": 0,
  "final_output": null
}
```

### 8.2 查询任务

```http
GET /ai-video/ltx23/continuous-jobs/{jobId}
Authorization: Bearer <service-token>
```

响应：

```json
{
  "job_id": "continuous_001",
  "job_type": "continuous",
  "execution_attempt_id": "attempt_0001",
  "status": "running",
  "active_stages": ["union", "lipdub"],
  "progress": 0.73,
  "window_count": 29,
  "union_completed_windows": 29,
  "lipdub_completed_windows": 21,
  "cancel_requested": false,
  "error": null,
  "final_output": null
}
```

成功响应：

```json
{
  "job_id": "continuous_001",
  "job_type": "continuous",
  "execution_attempt_id": "attempt_0001",
  "status": "done",
  "active_stages": [],
  "progress": 1,
  "window_count": 29,
  "union_completed_windows": 29,
  "lipdub_completed_windows": 29,
  "cancel_requested": false,
  "error": null,
  "final_output": {
    "filename": "final.mp4",
    "type": "output",
    "width": 1080,
    "height": 1920,
    "fps": 24,
    "duration": 133.2,
    "video_codec": "h264",
    "audio_codec": "aac",
    "content_type": "video/mp4",
    "size_bytes": 123456789,
    "sha256": "computed-at-runtime",
    "expires_at": "2026-07-15 12:00:00",
    "url": "/ai-video/ltx23/continuous-jobs/continuous_001/output"
  }
}
```

### 8.3 读取成片

```http
GET /ai-video/ltx23/continuous-jobs/{jobId}/output
Authorization: Bearer <service-token>
```

- 仅在任务为 `done` 且最终媒体校验通过后返回。
- 响应类型为 `video/mp4`，支持标准 Range 请求。
- 不向调用方暴露 ComfyUI 绝对路径或通用 `/view` 文件接口。

### 8.4 取消任务

```http
POST /ai-video/ltx23/continuous-jobs/{jobId}/cancel
Authorization: Bearer <service-token>
```

- `queued` 任务立即取消。
- `running` 任务设置 `cancel_requested`，当前不可安全中断的采样窗口完成后停止调度新窗口。
- 已完成窗口和 manifest 保留，任务终态为 `cancelled`。

### 8.5 恢复任务

```http
POST /ai-video/ltx23/continuous-jobs/{jobId}/resume
Authorization: Bearer <service-token>
```

- 只允许恢复 `interrupted` 任务；`failed` 是终态，重试必须通过创建接口生成新 job 和新幂等键，并以 `retry_of_job_id` 关联旧任务。
- resume 请求不接收附件、输入哈希、seed 或任何可改变任务内容的字段；调用方只提供路径中的 `jobId` 和服务令牌。
- 服务端获取任务级排他锁，从保留的原始附件重新计算 SHA256，并与 manifest 中创建任务时固化的三个原始文件哈希、规范化全文哈希和 seed 对账；再核对 ComfyUI history 与现存产物后调度。
- 任一输入文件缺失、哈希不匹配、已有运行实例或 manifest 校验失败时返回 HTTP 409；不得由调用方提供值覆盖 manifest。

### 8.6 HTTP 与状态语义

- `202`：任务已创建或取消/恢复请求已接受。
- `200`：查询成功、幂等命中已有任务或成片读取成功。
- `400`：参数、Markdown 或媒体内容不合法。
- `401`：服务令牌缺失或错误。
- `404`：任务或结果不存在。
- `409`：幂等冲突、状态冲突或恢复冲突。
- `413`：上传文件超过限制。
- `429`：队列已满，返回 `Retry-After`。
- `503`：允许 GPU、worker 或必需依赖临时不可用。
- `500`：不可归类的执行器内部错误。
- `206`：Range 成片读取成功。
- `416`：Range 超出成片范围。

现有插件保留旧 `GET /ai-video/ltx23/jobs/{jobId}`。连续任务使用独立 `/continuous-jobs/{jobId}` 前缀，避免路由和鉴权兼容冲突；反向代理只白名单 `continuous-generate` 与 `continuous-jobs/*`，不得暴露旧 `storyboard-generate`、旧 jobs 或通用 ComfyUI 路由。

任务状态：

- `queued`
- `running`
- `interrupted`
- `done`
- `failed`
- `cancelled`

阶段：

- `dependency_preflight`
- `validating_inputs`
- `preprocessing_audio`
- `cloning_full_voice`
- `aligning_full_voice`
- `rendering_pose`
- `planning_windows`
- `union`
- `lipdub`
- `merging`
- `validating_output`
- `interrupted`
- `done`
- `failed`
- `cancelled`

合法流转：

```text
queued -> running -> done
queued -> cancelled
running -> cancelled
running -> failed
running -> interrupted -> running（每次恢复新增不可变 `execution_attempt_id`）
```

Union 与 LipDub 可流水并行，因此查询使用 `active_stages`，并分别返回两个完成窗口数。总体进度必须单调不下降：输入/声音/规划占 15%，Union 占 40%，LipDub 占 35%，合成占 8%，最终校验占 2%。

## 9. 持久化、重试与恢复

- 每个任务在输出目录保存 `manifest.json`。
- `idempotency_key` 与输入哈希一致时返回已有任务；同一键对应不同输入时返回 `IDEMPOTENCY_CONFLICT`，不创建第二个任务。
- 幂等键创建和任务恢复都必须持有任务级排他锁，防止并发重复执行。
- 只有 8195 编排器可以写 manifest；LipDub worker 通过结果消息回报，禁止多个进程直接改同一 JSON。
- 每完成一个阶段或窗口，将新清单写入临时文件、`fsync` 后原子 `replace`。
- 清单记录 `schemaVersion`、当前不可变 `execution_attempt_id`、历史 execution attempts、输入/规范化文本哈希、模型和 workflow 精确哈希、seed/调用次数、预处理参数、窗口全局帧与 PTS、`validFrames/paddedFrames`、GPU allowlist、worker lease、prompt ID、阶段状态、产物哈希和 FFmpeg 版本。
- 重试只允许复用输入哈希一致的任务。
- 服务启动扫描非终态 manifest；先通过 ComfyUI history 对账已有 prompt ID 和产物，再将无法确认运行中的任务标记为 `interrupted`。
- 恢复通过独立 resume 接口触发，从第一个未通过校验的阶段继续。
- 已通过校验且不依赖失效上游的 VoxCPM2 音频、Union 和 LipDub 窗口不得重复生成。
- 若 Union 窗口 `i` 失效，必须级联作废 `i` 及其后全部 Union 窗口和对应 LipDub 产物，因为后续窗口依赖其续帧状态。
- 单窗口最多自动重试两次；仍失败则任务失败并保留中间产物。
- prompt 排队、执行和停滞分别设置超时；worker lease 到期后先对账 history，确认未运行才允许重提。
- 成功成片和 manifest 默认保留 72 小时；业务后端应在此期限内读取并登记素材。清理过程不得删除仍被读取或处于非终态的任务。

## 10. 错误处理

必须返回稳定错误类型，不依赖中文消息做逻辑判断：

- `INVALID_MARKDOWN`
- `NO_DIALOGUE_FOUND`
- `INVALID_PORTRAIT`
- `INVALID_REFERENCE_AUDIO`
- `POSE_RENDER_FAILED`
- `VOICE_CLONE_FAILED`
- `VOICE_DURATION_OUT_OF_RANGE`
- `VOICE_CONTENT_VALIDATION_FAILED`
- `VOICE_WORKER_CLEANUP_FAILED`
- `DEPENDENCY_PREFLIGHT_FAILED`
- `NO_ALLOWED_GPU_AVAILABLE`
- `QUEUE_FULL`
- `UNION_WINDOW_FAILED`
- `LIPDUB_WINDOW_FAILED`
- `MERGE_FAILED`
- `OUTPUT_VALIDATION_FAILED`
- `JOB_NOT_FOUND`
- `IDEMPOTENCY_CONFLICT`
- `RESUME_INPUT_MISMATCH`

错误对象固定为：

```json
{
  "code": "LIPDUB_WINDOW_FAILED",
  "message": "LipDub 窗口生成失败",
  "retryable": true,
  "stage": "lipdub",
  "window_index": 7
}
```

错误响应不返回服务器绝对路径、密码或模型内部堆栈。

## 11. 安全与访问

- 这些路径是内部 AI 执行器接口，不使用业务 `/api` 前缀；平台后端负责 DTO、状态和错误映射。
- 所有创建、查询、恢复、取消和结果读取接口都必须校验服务端配置提供的 Bearer token；共享开发值保存在两端 `application-dev.yml`，环境变量仅可选覆盖。
- 默认将隔离 ComfyUI worker 绑定到 `127.0.0.1`。
- 业务后端与 ComfyUI 不在同机时，应通过受控反向代理暴露指定 `/ai-video/ltx23/*` 路径，不直接开放完整 ComfyUI 管理面。
- 上传文件名必须重命名并限制扩展名、大小和路径。
- 所有任务目录必须限制在 ComfyUI input/output 根目录下。
- SSH 密码、API 令牌和其他共享开发凭据统一写入并提交在两端 `application-dev.yml`，环境变量可选覆盖；不得写入任务 manifest、日志、响应或前端。
- 后端负责用户认证、账号归属、额度和平台任务中心；ComfyUI 不信任客户端传入的 `ownerId`。
- 成片应由产品层标记为 AI 生成内容；本服务在 manifest 中写入 `aiGenerated=true`。

## 12. 测试策略

### 12.1 单元测试

- T03 Markdown 正确提取 11 段和完整台词。
- 详细表覆盖全局默认值，P9/P10 的预估时长冲突只告警、不覆盖生成后时间戳。
- `+` 与书名号式括号朗读规范化正确。
- 120 秒窗口数为 26。
- 132–134 秒窗口数为 28–29。
- 窗口范围连续且音频切片无缺口。
- 验证 `[start,start+121)`、本地续帧索引 113、每帧 2000 音频采样和 133.2 秒末窗补 88 帧，无 off-by-one。
- GPU allowlist 永远不包含 0、6、7。
- 路径和上传文件名无法越过 input/output 根目录。
- 任务 manifest 可原子写入、加载和恢复。

### 12.2 组件测试

- 用合成双声道音频验证自动声道选择和静音裁剪。
- 用本次真实 13.2 秒 MP3 验证声道评分、裁剪余量、PCM 格式和 manifest 统计。
- 在任何全片视频生成前，用本次 473 字全文执行 VoxCPM2 能力门禁，保存 ASR/CER、段落时间戳、声纹相似度、响度和韵律报告；失败则停止，不以分段音频降级。
- 用假 ComfyUI history 验证窗口状态、重试和恢复。
- 验证 Union 视频与对应完整音轨切片 mux 后能被 LipDub ready 工作流正确提取音频。
- 用彩色测试片段验证 8 帧接缝选择、嘴部不透明叠加、1080×1920 裁切和完整音轨回挂。
- 验证 Bearer token、文件大小限制、幂等冲突、统一 GET 路由和受保护结果接口。

### 12.3 GPU 冒烟测试

- 只在 GPU 5 运行 5 秒、121 帧的 Union + LipDub 冒烟任务。
- GPU 1–4 各运行一次 LipDub 冒烟，验证固定端口映射、容量 1 和 worker lease。
- 测试期间每 2 秒采集一次进程 PID、GPU UUID 和显存；断言本服务 worker PID 从未出现在 GPU 0、6、7，而不只比较测试前后快照。
- 在 Union、LipDub、merge 三个阶段分别人为中断并恢复，验证 history 对账和成功窗口不重算。

### 12.4 本次附件端到端测试

- 完整 Markdown、自拍和参考 MP3 创建一个任务。
- 任务最终只保留并使用一个通过门禁的 `full_voice.wav`；即使整段生成因门禁失败而重试，也不得按段落生成或混用多个音轨。
- 任务只返回一个 `final.mp4`。
- 最终媒体探针必须为：1080×1920、24 fps、H.264、AAC、有声、时长与完整 WAV 相差不超过 0.1 秒。
- 对所有窗口接缝运行黑帧、重复帧、跳变、人脸相似度、构图和手部检测；对嘴型同步使用可用的同步指标并保存逐段人工评分表。
- 形成验收证据包：FFprobe、ASR/对齐、Vox 调用/seed/哈希、声纹与响度、定时抽帧、全部接缝报告、恢复记录和 GPU 遥测。

## 13. 验收标准

1. 后端通过一次 POST 获得 `job_id`。
2. 后端可轮询阶段、总体进度、窗口进度和失败原因。
3. 所有完整台词使用同一次成功的 VoxCPM2 整段生成结果，音色和音量没有段间突变；失败重试不得变成分段配音。
4. 最终只有一个 9:16 MP4，分辨率为 1080×1920。
5. 全片固定人物、固定机位、固定背景、胸口以上构图，无手势、无补手、无分镜硬切。
6. 嘴型跟随完整中文音轨，停顿时自然闭嘴。
7. 情绪按段落从锐利设问推进到亲和收束，但不引发身份、场景和景别变化。
8. 内部窗口接缝没有黑帧、明显跳切或重复音频。
9. 任一窗口失败可从 manifest 恢复；整段声音和不依赖失效上游的成功窗口不重做。若 Union 窗口失效，则该窗口及其后所有依赖续帧的 Union/LipDub 产物按级联规则重做。
10. GPU 0、6、7 未被本服务占用、重启或加载模型。

## 14. 协作与后续集成

### 14.1 本次 ComfyUI 实现切分

- 契约与编排：路由、状态、manifest、恢复、GPU allowlist。
- 音频：Markdown 文本、参考音频预处理、VoxCPM2、音频门禁。
- 视频：窗口规划、Union、LipDub、融合、MP4 校验。

### 14.2 业务后端后续调用

业务后端只需实现：

1. 校验权限、归属、额度、素材和幂等键。
2. 创建平台任务记录。
3. 调用本服务 POST。
4. 保存 ComfyUI `job_id`。
5. 轮询 GET 并回写平台任务。
6. 将 `final_output` 登记为授权素材/成片。

业务后端不得直接读取 ComfyUI 服务器绝对文件路径，也不得把 ComfyUI 状态直接当作平台最终状态而不做校验。

状态映射：执行器 `done` 只有在平台后端校验结果哈希、读取成片并完成素材登记后才能映射为平台 `success`；执行器 `failed` 映射平台 `failed`，`cancelled` 映射平台 `cancelled`，`interrupted` 保持运行中但标记可恢复。

### 14.3 公共文档影响

本次不修改现有业务 API、领域对象或任务状态全集。后续正式接入 AI 视频工作台时，需要同步评审：

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`
- `docs/ARCHITECTURE.md`

## 15. 参考

- LTX-2.3 模型卡：<https://huggingface.co/Lightricks/LTX-2.3>
- LTX-2 官方推理仓库：<https://github.com/Lightricks/LTX-2>
- ComfyUI-LTXVideo：<https://github.com/Lightricks/ComfyUI-LTXVideo>
- VoxCPM2：<https://github.com/OpenBMB/VoxCPM>
