# MP3 声音上传兼容校验修复规格

## 1. 目标与范围

修复浏览器能够正常播放的 MP3 文件在 `POST /api/voices` 上传时，被后端以“扩展名、MIME 和文件头不一致”拒绝的问题。

本次修改声音样本文件类型校验、资产 MIME 规范化、用户端上传前的容器规范化及其测试；不修改上传接口字段、
响应结构、权限、文件大小上限、声音状态、转写流程或数据库结构。

## 2. 风险与任务边界

- 风险等级：红色高风险。
- 触发依据：修改上传文件安全边界及文件类型判定。
- 允许修改：`VoiceSampleValidator`、`VoiceSampleValidatorTest`、`AssetServiceImpl`、`AssetServiceImplTest`、
  用户端声音 `api.ts`、`api.test.ts`，以及本规格和实现计划。
- 不做范围：不接入 Apache Tika、FFmpeg/ffprobe 或新依赖；不放宽 WAV、M4A 校验；不改变 100MB 上限；不接受扩展名与实际格式冲突的文件。
- 公共契约：`POST /api/voices` 和错误码保持不变，无需修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 或 `docs/ASYNC_TASKS.md`。

## 3. 方案比较与决策

### 方案 1：MIME 归一化加有限 MP3 文件头识别（采用）

保留扩展名与文件内容双重约束，兼容浏览器和操作系统常见的 MP3 MIME 表达，并在有限前缀内识别 MP3 内容。改动小、无新增运行时依赖，同时能覆盖当前误拒绝。

### 方案 2：只增加 MIME 别名

实现最快，但无法覆盖带前导元数据、非首字节音频帧等可播放 MP3，容易再次出现同类问题。

### 方案 3：使用 Tika 或 ffprobe

格式识别能力更强，但会新增依赖、进程调用或部署要求，不符合本次定向修复范围。

## 4. 校验设计

### 4.1 扩展名

MP3 文件必须以 `.mp3` 结尾，大小写不敏感。扩展名为空或为 WAV、M4A 等其他值时，不得按 MP3 接受。

### 4.2 MIME

将 MIME 转为小写、去除首尾空白，并忽略分号后的参数。MP3 接受：

- `audio/mpeg`
- `audio/mp3`
- `audio/x-mpeg`
- `audio/mpeg3`
- `audio/x-mpeg-3`
- 空 MIME
- `application/octet-stream`

空 MIME 和 `application/octet-stream` 只表示客户端无法提供可靠类型，必须在扩展名为 `.mp3` 且文件头确认 MP3 时才能通过。显式的其他 MIME 继续拒绝。
校验通过后，资产 Service 必须按确认格式保存规范 MIME：MP3 为 `audio/mpeg`、WAV 为 `audio/wav`、M4A 为
`audio/mp4`，不得继续保存客户端的空 MIME 或别名。

### 4.3 文件头

校验器最多读取并复位文件起始的 64KiB，不消耗后续上传流：

- 文件起始处的 `ID3` 必须至少包含 10 字节 ID3v2 头，版本字节不能为 `0xFF`，四个 synchsafe 大小字节的最高位必须为 0，且声明的标签结束位置不能超过文件总大小。
- 无 ID3v2 头时，在该前缀中查找两个连续的 MPEG Layer III 帧。每个帧头必须具有 11 位同步字、非保留 MPEG 版本、Layer III、合法的非空码率和采样率；按版本、码率、采样率和 padding 算出的下一帧位置必须存在兼容帧头。
- 上述连续帧识别允许 MP3 前存在少量前导元数据，但不得仅凭任意 `0xFF` 字节接受文件。
- WAV 与 M4A 的文件头识别和 MIME 规则保持不变。

若流无法读取或复位，继续返回 `46201 FILE_TYPE_NOT_ALLOWED`。

## 5. 数据流与错误处理

`VoiceController` 接收 multipart 文件后，现有 `AssetServiceImpl` 使用可标记/复位的缓冲输入流调用 `VoiceSampleValidator`。校验成功后沿用现有 OSS 保存与 `av_voice` 创建、后台转写流程；校验失败仍由现有异常机制返回 `46201` 和原中文提示。

本次不新增 Controller、Service、Mapper、Entity、BO、VO、DTO 或平行业务层，不改变 RuoYi 分层。

## 6. 验收与测试

必须覆盖：

- 标准 `audio/mpeg` MP3 成功。
- `audio/mp3` 等常见别名且文件头为 MP3 时成功。
- 空 MIME 或 `application/octet-stream` 且文件头为 MP3 时成功。
- 带 MIME 参数的合法 MP3 成功。
- 带有限前导元数据、后续存在合法 MPEG 帧头的 `.mp3` 成功。
- `.mp3` 扩展名配 WAV/M4A 文件头仍返回 `46201`。
- MP3 文件头配显式 WAV/M4A MIME 仍返回 `46201`。
- 仅含随机同步字节、无合法 MP3 帧头的文件仍返回 `46201`。
- 超过 100MB 仍在读取文件前返回 `46202`。

实现后运行 `VoiceSampleValidatorTest`、受影响 core 模块测试，并重启用户端后端进行一次上传路径验证。独立审查聚焦文件类型绕过、流复位和回归测试覆盖。

## 7. 真实文件兼容补充（2026-08-04）

页面实测文件 `明明想利用TikSe.mp3` 的浏览器声明为 `audio/mpeg`，但前 12 字节为
`RIFF....WAVE`，真实容器是 WAV。浏览器可播放并不能证明扩展名是实际编码格式；后端按本规格拒绝该原始
multipart 请求属于预期安全行为。

为避免要求用户手工改名，用户端声音 Service 在创建 `FormData` 前执行有限且确定性的容器规范化：

- 只读取文件前 12 字节，不解码整段音频。
- 命中 `RIFF` 且第 8 字节起为 `WAVE` 时，使用相同文件字节创建 `.wav` / `audio/wav` 的上传文件。
- 未命中 RIFF/WAVE 容器头时保持原文件对象不变，真实 MP3 继续由后端既有 ID3/MPEG 帧校验确认。
- 不根据通用 `ftyp` 头推断 M4A，因为 MP4、MOV、HEIC、AVIF 等格式共用该头；M4A 保持原文件交由后端严格校验。
- 后端 `VoiceSampleValidator` 不放宽：绕过前端直接提交扩展名、MIME、文件头冲突的请求仍返回 `46201`。

该补充修改 `src/services/ai-video/voice/api.ts` 及对应测试，并由资产 Service 将校验后的格式写为规范 MIME；
不改变 HTTP 字段、错误码、权限、大小限制、OSS、声音状态或转写流程。验收必须证明 RIFF/WAVE 内容即使原始名称为 `.mp3`，最终 multipart 文件名和
MIME 会规范为 `.wav` / `audio/wav`，且文件字节完全不变；真实 MP3 和通用 `ftyp` 文件必须保持原对象不变。
