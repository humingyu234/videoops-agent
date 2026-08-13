# MP3 声音上传兼容校验实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不降低扩展名与文件内容一致性校验的前提下，让浏览器可播放的常见 MP3 文件通过声音上传校验。

**架构：** 保持现有 `VoiceSampleValidator` 为唯一声音文件类型校验入口。校验器归一化 MP3 MIME，并在可复位的 64KiB 前缀中验证 ID3v2 头或两个连续 MPEG Layer III 帧；Controller、Service、OSS 和转写链路不变。

**技术栈：** Java 21、JUnit 5、AssertJ、RuoYi-Vue-Plus 6.x、Maven Wrapper。

---

## 文件结构

- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/VoiceSampleValidatorTest.java`：复现 MIME 别名、通用 MIME、前导字节和伪造同步字节场景。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/VoiceSampleValidator.java`：实现 MIME 归一化、有限前缀读取、ID3v2 头与连续 MPEG Layer III 帧识别。
- 修改 `AssetServiceImpl` 及其测试：按服务端确认格式保存规范 MIME，覆盖客户端 MIME 为 null 或空字符串。
- 修改用户端声音 `api.ts` 及其测试：只将确定的 RIFF/WAVE 内容规范为 `.wav` / `audio/wav`。
- 不修改 API、Entity、BO、VO、DTO、Mapper 或数据库；不需要本机 MySQL/Redis 集成测试。

## 治理任务卡

- 单一目标：关闭合法 MP3 被 `46201` 误拒绝的问题。
- 风险：红色高风险，原因是上传文件安全边界发生变化。
- 权威来源：`docs/superpowers/specs/2026-08-03-voice-mp3-upload-validation-design.md`、`docs/API_CONTRACT.md`、`docs/BACKEND_CODING_STANDARDS.md`。
- 不做范围：不增加依赖，不改 WAV/M4A 规则，不改接口、权限、归属、大小限制、存储或转写。
- 并发：单实施者；完成后做一次独立文件安全审查和一次定向复核，禁止扩展问题范围。
- 验证：目标单测、core 模块测试、用户端后端重启与上传路径验证。
- 输出：完成项、风险、验证证据、阻塞项。

### 任务 1：用失败测试复现 MP3 误拒绝和安全边界

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/VoiceSampleValidatorTest.java`

- [x] **步骤 1：增加 MP3 测试样本辅助方法**

在测试类中加入可生成两个连续 MPEG-1 Layer III、128kbps、44.1kHz 帧头的辅助方法：

```java
private byte[] mp3Frames(int leadingBytes) {
    int frameLength = 417;
    byte[] bytes = new byte[leadingBytes + frameLength * 2];
    writeMp3FrameHeader(bytes, leadingBytes);
    writeMp3FrameHeader(bytes, leadingBytes + frameLength);
    return bytes;
}

private void writeMp3FrameHeader(byte[] bytes, int offset) {
    bytes[offset] = (byte) 0xff;
    bytes[offset + 1] = (byte) 0xfb;
    bytes[offset + 2] = (byte) 0x90;
    bytes[offset + 3] = 0x64;
}
```

- [x] **步骤 2：增加当前实现应失败的兼容性测试**

```java
@Test
void acceptsMp3MimeAliasesAndGenericMimeWhenHeaderMatches() {
    byte[] mp3 = mp3Frames(0);
    for (String mime : new String[] {
        "audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/mpeg3",
        "audio/x-mpeg-3", "", "application/octet-stream"
    }) {
        VoiceSampleMetadata result = validator.validate(
            "sample.MP3", mime, mp3.length, new ByteArrayInputStream(mp3));
        assertThat(result.format()).isEqualTo("mp3");
    }
}

@Test
void acceptsMp3MimeParameters() {
    byte[] mp3 = mp3Frames(0);
    VoiceSampleMetadata result = validator.validate(
        "sample.mp3", "Audio/MPEG; codecs=mp3", mp3.length, new ByteArrayInputStream(mp3));
    assertThat(result.format()).isEqualTo("mp3");
}

@Test
void acceptsMp3FramesAfterLimitedLeadingBytes() {
    byte[] mp3 = mp3Frames(7);
    VoiceSampleMetadata result = validator.validate(
        "sample.mp3", "audio/mpeg", mp3.length, new ByteArrayInputStream(mp3));
    assertThat(result.format()).isEqualTo("mp3");
}
```

- [x] **步骤 3：增加必须保持拒绝的反向测试**

```java
@Test
void rejectsExplicitConflictingMimeForMp3() {
    byte[] mp3 = mp3Frames(0);
    assertThatThrownBy(() -> validator.validate(
        "sample.mp3", "audio/wav", mp3.length, new ByteArrayInputStream(mp3)))
        .isInstanceOf(ServiceException.class)
        .extracting("code").isEqualTo(46201);
}

@Test
void rejectsIsolatedMpegSyncBytes() {
    byte[] fake = {(byte) 0xff, (byte) 0xfb, (byte) 0x90, 0x64, 0, 0, 0, 0};
    assertThatThrownBy(() -> validator.validate(
        "sample.mp3", "audio/mpeg", fake.length, new ByteArrayInputStream(fake)))
        .isInstanceOf(ServiceException.class)
        .extracting("code").isEqualTo(46201);
}
```

- [x] **步骤 4：运行目标测试并确认红灯**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
.\mvnw.cmd "-Dmaven.repo.local=C:\Users\Administrator\.m2\repository" "-Dmaven.test.skip=false" -pl ruoyi-modules/ai-video/ai-video-core -am "-Dtest=VoiceSampleValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：兼容性测试因 `ServiceException` 失败；反向测试保持通过，证明失败来自缺失的 MIME/文件头兼容行为。

### 任务 2：实现最小 MP3 兼容校验

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/VoiceSampleValidator.java`
- 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/VoiceSampleValidatorTest.java`

- [x] **步骤 1：将文件头读取扩大为可复位的有限前缀并归一化 MIME**

加入 `MP3_SCAN_LIMIT = 64 * 1024`，以 `mark(MP3_SCAN_LIMIT + 1)`、`readNBytes(MP3_SCAN_LIMIT)`、`reset()` 读取前缀。新增：

```java
private String normalizeMime(String contentType) {
    if (contentType == null) return "";
    String mime = contentType.trim().toLowerCase(Locale.ROOT);
    int parameterIndex = mime.indexOf(';');
    return (parameterIndex < 0 ? mime : mime.substring(0, parameterIndex)).trim();
}

private boolean isMp3Mime(String mime) {
    return switch (mime) {
        case "", "application/octet-stream", "audio/mpeg", "audio/mp3",
             "audio/x-mpeg", "audio/mpeg3", "audio/x-mpeg-3" -> true;
        default -> false;
    };
}
```

- [x] **步骤 2：验证 ID3v2 头和连续 MPEG Layer III 帧**

`magicFormat` 先保持 WAV/M4A 魔数判断，再调用 `hasValidId3Header(prefix, size)` 或 `hasConsecutiveMp3Frames(prefix)`。MPEG 帧解析必须验证 11 位同步字、版本不保留、Layer III、码率索引 1..14、采样率索引 0..2，并使用以下表计算帧长：

```java
private static final int[] MPEG1_LAYER3_BITRATES =
    {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
private static final int[] MPEG2_LAYER3_BITRATES =
    {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
```

MPEG-1 Layer III 帧长为 `144 * bitrate * 1000 / sampleRate + padding`；MPEG-2/2.5 Layer III 为 `72 * bitrate * 1000 / sampleRate + padding`。第二帧必须位于计算出的精确位置，且 MPEG 版本和采样率索引与第一帧一致。

- [x] **步骤 3：把 MP3 分支改为扩展名、兼容 MIME 和确认文件头三者同时成立**

```java
boolean mp3 = "mp3".equals(extension) && isMp3Mime(mime) && "mp3".equals(format);
```

WAV/M4A 分支继续要求各自扩展名、既有 MIME 集合和既有魔数。

- [x] **步骤 4：运行目标测试并确认绿灯**

运行任务 1 步骤 4 的 Maven 命令。

预期：`VoiceSampleValidatorTest` 全部通过，无测试错误。

- [ ] **步骤 5：提交定向代码变更**

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/VoiceSampleValidator.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/VoiceSampleValidatorTest.java
git commit -m "fix(voice): 兼容合法 MP3 上传校验"
```

若本机仍缺少 Git 提交身份，只保留定向暂存并在交付中报告，不擅自写入用户的 Git 配置。

### 任务 3：回归验证、审查与运行态验收

**文件：**
- 验证：`ai-video-api/ruoyi-modules/ai-video/ai-video-core`
- 运行：`ai-video-api/ai-video-user-api`

- [x] **步骤 1：运行 core 模块完整测试**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
.\mvnw.cmd "-Dmaven.repo.local=C:\Users\Administrator\.m2\repository" "-Dmaven.test.skip=false" -pl ruoyi-modules/ai-video/ai-video-core -am "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：构建成功，core 及其依赖模块测试无失败。

- [x] **步骤 2：执行一次独立文件安全审查**

审查仅覆盖本任务差异，确认：显式冲突 MIME 仍拒绝、伪造单帧同步字节仍拒绝、读取上限为 64KiB、输入流可复位、WAV/M4A 和 100MB 上限未放宽。修复后只允许一次定向复核。

- [x] **步骤 3：重启用户端后端并验证健康检查**

使用项目现有启动脚本重启 `ai-video-user-api`，再请求其健康检查。预期 HTTP 200，且 8082 端口只存在一个新进程监听。

- [x] **步骤 4：验证页面上传路径**

已在 `http://127.0.0.1:8002/studio` 使用原文件完成提交，不再出现 `46201`；声音 `张良老师1`
进入现有转写流程并最终显示 `已校验`，时长 `1:21`，Whisper 文本已生成。

- [x] **步骤 5：检查差异与交付证据**

运行：

```powershell
git diff --check
git status --short
```

交付时列出目标测试、core 测试、健康检查、页面上传结果，以及因环境或用户文件不可访问而未完成的验证。

### 任务 4：规范化被错误命名的受支持音频容器

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.test.ts`

- [x] **步骤 1：从真实失败请求确认根因**

请求证据为扩展名 `mp3`、MIME `audio/mpeg`、大小 `3587880`，文件头
`52494646....57415645`，即 `RIFF....WAVE`。失败点是后端一致性校验，不是前端选择器或网络代理。

- [x] **步骤 2：先添加失败测试**

在 `api.test.ts` 构造名称为 `.mp3`、MIME 为 `audio/mpeg`、内容头为 RIFF/WAVE 的 `File`，断言
multipart 中实际提交的文件为 `.wav` / `audio/wav` 且字节不变。修复前测试必须以文件名仍为 `.mp3` 失败。

- [x] **步骤 3：实现最小客户端规范化**

在声音 Service 创建 `FormData` 前读取 12 字节；只规范化可确定识别的 RIFF/WAVE 容器。不得根据通用
`ftyp` 头推断 M4A，其他文件保持不变。
后端严格校验、接口字段、状态和转写链路不变。

- [x] **步骤 4：回归并用原文件复测**

运行声音 API 定向测试、前端类型检查、后端校验测试；重启前后端或等待热更新后，用当前页面保留的原文件再次提交，
确认不再返回 `46201`。

实际结果：声音 API 定向测试 5/5、后端校验测试 12/12 通过；原文件上传成功，声音 `张良老师1` 状态变为
`已校验`，时长 `1:21`，Whisper 文本已生成。干净后端重新打包成功，`8082 /actuator/health` 返回 HTTP 200。
全量前端类型检查仍被仓库既有 `config/config.ts`、`config/config.dev.ts` 的 TS2883 配置错误阻塞，与本次声音
Service 变更无关。

- [x] **步骤 5：处理代码审查中的空 MIME 链路问题**

新增 `AssetServiceImplTest`，先确认 null MIME 会触发 NPE、空 MIME 会原样落库；随后按 `metadata.format()`
保存 `audio/mpeg`、`audio/wav` 或 `audio/mp4`。定向测试覆盖 null 和空字符串两种输入。
