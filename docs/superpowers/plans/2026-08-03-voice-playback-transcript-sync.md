# 声音播放与文案精确同步实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 保证声音管理页始终只有一个音频实例播放，并用 faster-whisper 真实词级时间戳驱动文案点击、跳转和高亮。

**架构：** Whisper Worker 返回规范化词元时间戳，Java 后端校验并随声音记录持久化，用户端 API 将时间戳映射为页面时间轴。播放器成为页面级单实例控制器，同一声音 seek 只修改 `currentTime`，切换声音先释放旧实例。

**技术栈：** Python 3.11、FastAPI、faster-whisper、Java 21、Spring Boot、MyBatis-Plus、MySQL 8、React 19、TypeScript、Vitest。

---

## 文件结构

- `ai-video-worker/whisper/src/aivideo_whisper/transcriber.py`：提取并规范化 faster-whisper 词元时间戳。
- `ai-video-worker/whisper/src/aivideo_whisper/app.py`：Worker HTTP 响应增加 `words`。
- `ai-video-worker/whisper/tests/test_app.py`：Worker 时间戳契约测试。
- `docs/sql/ai-video/mysql/20260803_05_voice_transcript_timeline.sql`：新增时间轴字段。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/VoiceTranscriptCueDTO.java`：稳定的跨模块时间戳 DTO。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/domain/Voice.java`：持久化时间轴 JSON。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/VoiceTranscriptionResultDTO.java`：转写结果包含时间戳。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/VoiceDTO.java`：声音查询 DTO 包含时间戳。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IVoiceService.java`：增加重新同步服务方法。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImpl.java`：写入、清空和重新同步时间戳。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImplTest.java`：领域状态和时间戳测试。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java`：迁移契约测试。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/client/WhisperTranscriptionResponse.java`：Worker 原始响应词元。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImpl.java`：请求并校验真实时间戳。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImplTest.java`：Java 与 Worker multipart/JSON 契约测试。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/domain/vo/VoiceVo.java`：用户端返回时间戳。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/controller/VoiceController.java`：增加重新同步入口。
- `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/types.ts`：前端接口时间戳类型。
- `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.ts`：重新同步 API。
- `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/adapter.ts`：后端时间戳到页面模型映射。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`：声音项持有真实时间轴。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts`：单实例音频控制器。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.test.ts`：重复 seek、切换和异步竞争回归测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.ts`：真实时间戳选择和回退规则。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx`：真实词元点击与无时间戳提示。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx`：重新同步确认与状态更新。
- 对应 `.test.ts` / `.test.tsx`：API、时间轴、卡片和声音库回归测试。

## 治理与任务卡

- 总体风险：黄色；数据库、Worker、Java DTO 和前端契约联动。
- 执行方式：当前会话内联串行执行。各任务共享契约和文件，不使用并发智能体。
- 审查：完成后一次契约/状态专项检查；修复后只做一次定向复核。
- 公共文档：`docs/API_CONTRACT.md` 不维护业务 API 清单，无需增加具体路径；`docs/DOMAIN_MODEL.md` 不维护字段全集，时间轴字段和状态边界由已批准模块规格与迁移冻结。
- 本机集成测试只使用现有本机 MySQL 8；本计划不新建 Redis 依赖，也不使用容器化测试环境。

### 任务 1：Whisper Worker 输出真实时间戳

**风险与边界：** 黄色；只修改 Worker 转写结果，不改变认证、并发上限和上传限制。

**文件：**
- 修改：`ai-video-worker/whisper/tests/test_app.py`
- 修改：`ai-video-worker/whisper/src/aivideo_whisper/transcriber.py`
- 修改：`ai-video-worker/whisper/src/aivideo_whisper/app.py`

- [ ] **步骤 1：编写失败的 Worker 契约测试**

在伪 Transcriber 返回值中加入：

```python
words=(
    TranscriptionWord(text="微信", start_millis=120, end_millis=480),
    TranscriptionWord(text="公众号", start_millis=500, end_millis=920),
)
```

请求 `wordTimestamps=true`，断言响应：

```python
assert response.json()["words"] == [
    {"text": "微信", "startMillis": 120, "endMillis": 480},
    {"text": "公众号", "startMillis": 500, "endMillis": 920},
]
```

- [ ] **步骤 2：运行测试并确认因缺少时间戳类型/字段失败**

运行：

```powershell
python -m pytest ai-video-worker/whisper/tests/test_app.py -q
```

预期：FAIL，响应没有 `words` 或 `TranscriptionWord` 尚未定义。

- [ ] **步骤 3：实现最少 Worker 时间戳代码**

新增不可变类型并扩展协议：

```python
@dataclass(frozen=True, slots=True)
class TranscriptionWord:
    text: str
    start_millis: int
    end_millis: int

@dataclass(frozen=True, slots=True)
class TranscriptionResult:
    text: str
    language: str
    duration_millis: int
    words: tuple[TranscriptionWord, ...] = ()
```

`FasterWhisperTranscriber.transcribe()` 接收 `word_timestamps`，将该值传给模型，单次遍历 segments 收集文本与 `segment.words`，并将秒转换为非负毫秒。`app.py` 把表单参数传给 Transcriber，并将词元序列化为 camelCase 响应。

- [ ] **步骤 4：运行 Worker 测试确认通过**

运行同一步骤 2，预期全部 PASS。

- [ ] **步骤 5：提交 Worker 变更**

```powershell
git add ai-video-worker/whisper
git commit -m "feat(voice): return whisper word timestamps"
```

### 任务 2：冻结数据库和 Java 时间戳领域契约

**风险与边界：** 黄色；新增可空字段和向后兼容响应字段，不改既有状态枚举。

**文件：**
- 创建：`docs/sql/ai-video/mysql/20260803_05_voice_transcript_timeline.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/VoiceTranscriptCueDTO.java`
- 修改：`VoiceSchemaContractTest.java`、`Voice.java`、`VoiceTranscriptionResultDTO.java`、`VoiceDTO.java`

- [ ] **步骤 1：编写失败的 schema 和 DTO 测试**

在 schema 测试中读取新迁移并断言：

```java
assertThat(sql).contains("ADD COLUMN transcript_timeline_json JSON DEFAULT NULL");
```

在声音服务测试夹具中构造：

```java
List.of(new VoiceTranscriptCueDTO("微信", 120L, 480L))
```

并断言查询 DTO 保留该列表。

- [ ] **步骤 2：运行测试确认契约缺失**

```powershell
./mvnw.cmd -pl :ai-video-core -am "-Dmaven.test.skip=false" "-DskipTests=false" "-Dtest=VoiceSchemaContractTest,VoiceServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：FAIL，新迁移或新 DTO 不存在。

- [ ] **步骤 3：实现迁移和类型**

迁移内容：

```sql
ALTER TABLE av_voice
    ADD COLUMN transcript_timeline_json JSON DEFAULT NULL COMMENT 'Whisper 词元时间轴 JSON'
    AFTER transcript_text;
```

DTO：

```java
public record VoiceTranscriptCueDTO(String text, long startMillis, long endMillis) {}
```

`Voice` 增加 `String transcriptTimelineJson`；两个结果 DTO 增加 `List<VoiceTranscriptCueDTO> transcriptTimeline`。

- [ ] **步骤 4：运行任务测试确认通过**

运行步骤 2，预期相关测试 PASS。

- [ ] **步骤 5：提交领域契约**

```powershell
git add docs/sql/ai-video/mysql/20260803_05_voice_transcript_timeline.sql ai-video-api/ruoyi-modules/ai-video/ai-video-core
git commit -m "feat(voice): persist transcript timeline"
```

### 任务 3：Java 调用 Worker 并校验时间戳

**风险与边界：** 黄色；供应商原始响应仅留在 `ai-video-infra`，稳定 DTO 留在 `ai-video-core`。

**文件：**
- 修改：`WhisperTranscriptionResponse.java`
- 修改：`WhisperTranscriptionServiceImpl.java`
- 创建：`WhisperTranscriptionServiceImplTest.java`

- [ ] **步骤 1：编写失败的 loopback HTTP 测试**

测试启动仅监听 `127.0.0.1` 的 JDK `HttpServer`，捕获 multipart 请求并返回：

```json
{"requestId":"1:1:1","text":"微信公众号","language":"zh","durationMillis":1000,"words":[{"text":"微信","startMillis":120,"endMillis":480}]}
```

断言请求体包含 `name="wordTimestamps"` 和 `true`，结果 DTO 包含词元。

- [ ] **步骤 2：运行测试确认失败**

```powershell
./mvnw.cmd -pl :ai-video-infra -am "-Dmaven.test.skip=false" "-DskipTests=false" "-Dtest=WhisperTranscriptionServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：FAIL，当前发送 `false` 且响应类型没有 `words`。

- [ ] **步骤 3：实现响应映射和边界校验**

将 multipart 字段改为：

```java
body.part("wordTimestamps", "true");
```

逐个校验文本非空、开始时间非负、结束时间不早于开始时间、结束时间不超过总时长，再转换为 `VoiceTranscriptCueDTO`。非法响应抛出现有不可重试 Worker 响应异常。

- [ ] **步骤 4：运行 infra 测试确认通过**

运行步骤 2，预期 PASS。

- [ ] **步骤 5：提交 Java Worker 契约**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-infra
git commit -m "feat(voice): consume whisper word timestamps"
```

### 任务 4：完成、编辑和重新同步的后端状态流转

**风险与边界：** 黄色；保持 RuoYi Entity + Service 编排，不新增平行业务层。

**文件：**
- 修改：`IVoiceService.java`
- 修改：`VoiceServiceImpl.java`
- 修改：`VoiceServiceImplTest.java`
- 修改：`VoiceVo.java`
- 修改：`VoiceController.java`

- [ ] **步骤 1：编写失败的服务测试**

新增三个行为：

```java
// completeTranscription 写入 transcript_timeline_json
// updateTranscript 将 transcript_timeline_json 设为 null
// resyncTranscription 只允许 ready，清空失败和租约字段并进入 pending
```

同时验证 owner/workspace、`aivideo:voice:transcribe` 权限和 `record_revision` 条件继续出现在 update wrapper。

- [ ] **步骤 2：运行服务测试确认失败**

运行任务 2 的 Maven 命令，预期 FAIL，重新同步方法和时间戳更新不存在。

- [ ] **步骤 3：实现 Service 与 Controller**

`completeTranscription()` 使用 `JsonUtils.toJsonString(result.transcriptTimeline())` 写入字段；`updateTranscript()` 明确 `.set(Voice::getTranscriptTimelineJson, null)`；`toDTO()` 将 JSON 解析为稳定 DTO 列表。

`IVoiceService` 新增：

```java
VoiceDTO resyncTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal);
```

Controller 新增：

```java
@PostMapping("/api/voices/{voiceId}/transcription/resync")
@SaCheckPermission(value = "aivideo:voice:transcribe", type = "app")
@RepeatSubmit
```

请求继续复用 `RetryVoiceTranscriptionBo`，避免重复 BO。

- [ ] **步骤 4：运行 core 与 user 模块测试和编译**

```powershell
./mvnw.cmd -pl ai-video-user-api -am "-Dmaven.test.skip=false" "-DskipTests=false" "-Dtest=VoiceServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

预期：PASS，且 user-api 编译成功。

- [ ] **步骤 5：提交状态流转**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core ai-video-api/ruoyi-modules/ai-video/ai-video-user
git commit -m "feat(voice): resync transcript timeline"
```

### 任务 5：前端契约与真实时间轴

**风险与边界：** 黄色；新增可选字段，保留静态样例的平均时间回退，仅真实 API 记录不伪装精确同步。

**文件：**
- 修改：`src/services/ai-video/voice/types.ts`
- 修改：`src/services/ai-video/voice/api.ts`
- 修改：`src/services/ai-video/voice/api.test.ts`
- 修改：`src/services/ai-video/voice/adapter.ts`
- 修改：`src/pages/digital-human-studio/model.ts`
- 修改：`src/pages/digital-human-studio/voices/voiceTimeline.ts`
- 修改：`src/pages/digital-human-studio/voices/voiceTimeline.test.ts`

- [ ] **步骤 1：编写失败的前端契约测试**

断言 adapter 将：

```ts
transcriptTimeline: [{ text: '微信', startMillis: 120, endMillis: 480 }]
```

映射成页面秒单位 cue，并断言 `voiceApi.resync(id, revision)` 请求新路径。时间轴测试断言真实 cue 不再按字符平均分配。

- [ ] **步骤 2：运行前端测试确认失败**

```powershell
node_modules/.bin/vitest.cmd run src/services/ai-video/voice/api.test.ts src/pages/digital-human-studio/voices/voiceTimeline.test.ts
```

预期：FAIL，类型、方法和时间轴字段不存在。

- [ ] **步骤 3：实现前端类型与 adapter**

接口类型：

```ts
export interface VoiceTranscriptCue {
  text: string;
  startMillis: number;
  endMillis: number;
}
```

页面模型保存 `timeline?: VoiceWord[]` 和 `timelineExact: boolean`。真实响应映射为秒；缺少字段时 `timelineExact=false`。

- [ ] **步骤 4：运行契约测试确认通过**

运行步骤 2，预期 PASS。

- [ ] **步骤 5：提交前端契约**

```powershell
git add ai-video-ui/ai-video-webapp/src/services/ai-video/voice ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline*
git commit -m "feat(voice): map exact transcript timeline"
```

### 任务 6：单实例播放器和拖拽回归

**风险与边界：** 黄色；修改声音页运行时播放行为，是当前多重人声问题的直接修复。

**文件：**
- 修改：`useVoicePlayback.test.ts`
- 修改：`useVoicePlayback.ts`

- [ ] **步骤 1：编写失败的音频实例测试**

使用可观察的 `FakeAudio`，为具有 `recordRevision` 的声音 mock `voiceApi.accessUrl()`。新增断言：

```ts
await act(async () => result.current.play(serverVoice, 0.1));
await act(async () => result.current.play(serverVoice, 0.6));
expect(createdAudios).toHaveLength(1);
expect(createdAudios[0].currentTime).toBeCloseTo(serverVoice.secs * 0.6);
```

切换声音时断言第一个实例 `pause()`、`removeAttribute('src')` 和 `load()` 均被调用；同一声音加载中的多次 seek 只调用一次 access-url。

- [ ] **步骤 2：运行测试并确认出现多个 Audio**

```powershell
node_modules/.bin/vitest.cmd run src/pages/digital-human-studio/voices/useVoicePlayback.test.ts
```

预期：FAIL，`createdAudios` 数量大于 1 或旧实例未暂停。

- [ ] **步骤 3：实现单实例控制器**

增加集中释放函数：

```ts
const disposeAudio = (audio: HTMLAudioElement | null) => {
  if (!audio) return;
  audio.pause();
  audio.removeAttribute('src');
  audio.load();
};
```

同一 `voice.id` 直接设置 `currentTime` 并继续播放；加载中保存最新百分比；不同声音同步 dispose，再发起一次 access-url。所有状态和动画帧只由当前代次更新。

- [ ] **步骤 4：运行播放器及声音库测试确认通过**

```powershell
node_modules/.bin/vitest.cmd run src/pages/digital-human-studio/voices/useVoicePlayback.test.ts src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx
```

预期：全部 PASS。

- [ ] **步骤 5：提交播放器修复**

```powershell
git add ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback*
git commit -m "fix(voice): keep playback to one audio instance"
```

### 任务 7：页面精确高亮与重新同步交互

**风险与边界：** 黄色；重新同步会覆盖人工文案，必须显式确认。

**文件：**
- 修改：`VoiceCard.test.tsx`
- 修改：`VoiceCard.tsx`
- 修改：`VoiceLibraryView.test.tsx`
- 修改：`VoiceLibraryView.tsx`
- 修改：`style.css`

- [ ] **步骤 1：编写失败的页面测试**

断言真实 cue 点击调用准确百分比；无时间戳的 API 声音显示“重新同步”；确认后调用 resync，取消时不调用；人工编辑响应无时间戳后显示同步失效提示。

- [ ] **步骤 2：运行页面测试确认失败**

```powershell
node_modules/.bin/vitest.cmd run src/pages/digital-human-studio/voices/VoiceCard.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx
```

预期：FAIL，重新同步入口和精确 cue 尚不存在。

- [ ] **步骤 3：实现页面行为**

`VoiceCard` 优先使用 `voice.timeline`，按当前秒数判断 `done/now`。没有真实时间戳时渲染普通不可点击文本和提示。`VoiceLibraryView` 使用 Ant Design `Modal.confirm` 展示覆盖风险，确认后调用 `voiceApi.resync()` 并更新记录为 `pending`。

- [ ] **步骤 4：运行页面回归测试**

运行步骤 2，并追加 `VoiceFilePreview.test.tsx`，预期全部 PASS。

- [ ] **步骤 5：提交页面同步行为**

```powershell
git add ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css
git commit -m "feat(voice): sync transcript with real audio time"
```

### 任务 8：本机迁移、部署与真实联调

**风险与边界：** 黄色；只更新 `aivideo-whisper` 容器和本机开发数据库/服务，不触碰服务器现有其他容器。

**文件：**
- 验证所有上述文件。

- [ ] **步骤 1：执行本机迁移**

先查询 `information_schema.columns`，确认字段不存在后，仅对本机开发库 `ry-vue.av_voice` 执行 `20260803_05_voice_transcript_timeline.sql`。不得操作其他数据库。

- [ ] **步骤 2：运行完整相关验证**

```powershell
python -m pytest ai-video-worker/whisper/tests -q
./mvnw.cmd -pl ai-video-user-api -am "-Dmaven.test.skip=false" "-DskipTests=false" "-Dtest=VoiceSchemaContractTest,VoiceServiceImplTest,VoiceTranscriptionSchedulerTest,WhisperTranscriptionServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
./mvnw.cmd -pl ai-video-user-api -am -DskipTests package
node_modules/.bin/vitest.cmd run src/services/ai-video/voice src/pages/digital-human-studio/voices src/pages/digital-human-studio/components/VoiceFilePreview.test.tsx
git diff --check
```

预期：全部 exit 0，测试无失败。

- [ ] **步骤 3：更新服务器 Worker**

通过已授权 SSH 仅上传 `/opt/aivideo-whisper/app/aivideo_whisper` 变更文件，重建或重启 `aivideo-whisper`；保持 `127.0.0.1:18181` 绑定、内部令牌和 `unless-stopped`。不得修改 `indextts2-api` 或 `omnivoice-studio`。

- [ ] **步骤 4：重打包并重启 Java 后端**

停止当前 8082 对应 Java PID，重打包后使用现有安全隧道与内部令牌启动。确认 8002、8082、18181 均监听且 Worker `/health` 返回服务器模型路径。

- [ ] **步骤 5：重新同步现有录音并验收**

登录开发账号，调用 resync 接口处理现有测试录音，轮询到 `ready`，断言 `transcriptTimeline` 非空。服务器日志必须出现一次 `POST /internal/v1/transcriptions 200`。

在页面连续点击三个不同词元、连续拖动进度条、切换声音，验证始终只有一个音频实例发声，点击词元落到对应语音。

- [ ] **步骤 6：记录交付证据**

交付仅报告实际通过的 Worker、Maven、Vitest、真实接口和页面结果；未通过的类型检查或环境问题单独列为未完成项，不得描述为成功。

## 计划自检

- 规格的单实例播放、真实时间戳、持久化、旧数据重新同步、人工编辑失效和覆盖确认均有对应任务。
- 文件与类型命名在 Worker、Java 和 TypeScript 中保持一致。
- 没有未决占位符或未定义接口。
- 任务范围没有扩展到 IndexTTS2、克隆、额度、质量检测或任务中心。
