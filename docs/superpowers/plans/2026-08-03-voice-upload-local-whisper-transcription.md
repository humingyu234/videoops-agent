# 声音上传与本地 Whisper 异步转写实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将数字人工作台声音菜单接入真实声音上传、受控试听和服务器本地 `faster-whisper` 后台文本解析。

**架构：** Java/RuoYi 使用 `av_voice` 资源状态作为持久恢复事实，事务提交后由基础设施扫描器领取租约并调用仅监听回环地址的 Python Worker；Worker 常驻预加载模型且不接触数据库。前端通过模块 Service 查询、上传、轮询、试听和修正文本，不创建统一 AI 任务、额度或克隆流程。

**技术栈：** Java 21、Spring Boot 4.1、RuoYi-Vue-Plus 6.x、MyBatis-Plus、RuoYi OSS、MySQL 8、Python 3.11、FastAPI、faster-whisper、React 19、React Query 5、Vitest。

**规格：** `docs/superpowers/specs/2026-08-03-voice-upload-local-whisper-transcription-design.md`

**风险与协作：** 红色高风险；每次仅 1 名实施者写入，完成后执行一次独立安全／数据审查和一次定向复核。用户已要求加速，禁止新增范围和重复完整审查。

---

## 文件结构

### 公共契约与迁移

- 修改 `docs/API_CONTRACT.md`：登记用户端声音 API、Worker 内部协议和 46401–46405。
- 修改 `docs/DOMAIN_MODEL.md`：登记 `av_voice`、状态、归属、租约和修订。
- 修改 `docs/ASYNC_TASKS.md`：声明声音转写是资源内部后台处理，不创建统一生成任务。
- 修改 `docs/ARCHITECTURE.md`：登记 `ai-video-worker/whisper` 运行边界。
- 创建 `docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql`：前向创建 `av_voice`。

### Java 核心与接口

- 创建 `ai-video-core/.../voice/domain/Voice.java`：贫血 Entity。
- 创建 `ai-video-core/.../voice/dto/*.java`：查询、创建、领取、结果和展示 DTO。
- 创建 `ai-video-core/.../voice/mapper/VoiceMapper.java`：分页与条件领取 Mapper。
- 创建 `ai-video-core/.../voice/service/IVoiceService.java` 与 `IWhisperTranscriptionService.java`。
- 创建 `ai-video-core/.../voice/service/impl/VoiceServiceImpl.java`：归属、状态、租约、修订编排。
- 修改 `ai-video-core/.../asset`：增加声音流式上传、受控流读取和分类校验。
- 创建 `ai-video-user/.../user/voice/domain/bo|vo/*.java` 与 `controller/VoiceController.java`。
- 创建 `ai-video-infra/.../infra/voice/client/*.java`、`service/impl/WhisperTranscriptionServiceImpl.java`、`listener/VoiceTranscriptionScheduler.java`。
- 修改相关 Maven `pom.xml` 和 `ai-video-user-api` 配置文件。

### Worker

- 创建 `ai-video-worker/whisper/pyproject.toml`、`uv.lock`、`README.md`。
- 创建 `ai-video-worker/whisper/src/aivideo_whisper/{app,config,transcriber}.py`。
- 创建 `ai-video-worker/whisper/tests/*.py`。

### 前端

- 创建 `src/services/ai-video/voice/{types,api,adapter}.ts` 及测试。
- 修改 `voices/VoiceLibraryView.tsx`、`VoiceCard.tsx`、`useVoicePlayback.ts` 及测试。
- 修改数字人工作台上传 Modal，使其提交真实文件和元数据。

---

### 任务 1：冻结公共契约、SQL 和分层边界

**任务卡：** 只改公共文档与 SQL；不写运行时代码。验证文档、表字段、错误码和规格完全一致。

**文件：** 上述四个公共文档、`20260803_04_voice_upload_transcription.sql`。

- [ ] **步骤 1：先增加迁移契约测试或静态断言**

在 `ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java` 读取迁移并断言：

```java
assertThat(sql).contains("CREATE TABLE av_voice");
assertThat(sql).contains("UNIQUE KEY uk_av_voice_owner_idempotency");
assertThat(sql).contains("CHECK (transcription_status IN ('pending','transcribing','ready','failed'))");
```

- [ ] **步骤 2：运行测试确认因迁移不存在而失败**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceSchemaContractTest test
```

- [ ] **步骤 3：创建迁移并更新四份文档**

迁移必须包含规格第 7 节全部列、三个索引、两个唯一键和状态 CHECK；不得修改既有迁移。

- [ ] **步骤 4：运行测试与文档校验**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceSchemaContractTest test
powershell -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1
```

- [ ] **步骤 5：提交**

```powershell
git add docs docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java
git commit -m "docs(voice): freeze upload transcription contracts"
```

### 任务 2：实现常驻 faster-whisper Worker

**任务卡：** 只改 `ai-video-worker/whisper`；不访问数据库、OSS或用户 API。Worker 必须回环监听、Token 校验、文件上限、并发 1、模型本地加载和临时文件清理。

**文件：** `ai-video-worker/whisper/**`。

- [ ] **步骤 1：编写失败测试**

```python
def test_transcribe_requires_internal_token(client):
    response = client.post("/internal/v1/transcriptions", files={"file": ("a.wav", WAV, "audio/wav")})
    assert response.status_code == 401

def test_transcribe_returns_normalized_text(client, fake_model):
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1", "language": "zh", "wordTimestamps": "false"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.json()["text"] == "欢迎使用声音工作台。"
```

同时覆盖 `/health` 未加载 503、错误 MIME 415、超限 413、空文本 422、临时文件清理。

- [ ] **步骤 2：运行 pytest 确认失败**

```powershell
uv run --project ai-video-worker/whisper pytest -q
```

- [ ] **步骤 3：最少实现配置、Transcriber 和 FastAPI**

核心调用固定为：

```python
segments, info = self.model.transcribe(path, language=language, vad_filter=True, word_timestamps=False)
text = " ".join(segment.text.strip() for segment in segments if segment.text.strip())
```

模型使用 `WhisperModel(model_path, device=device, compute_type=compute_type, local_files_only=True)`；请求完成后在 `finally` 删除临时文件。

- [ ] **步骤 4：运行 Worker 测试与格式检查**

```powershell
uv run --project ai-video-worker/whisper pytest -q
uv run --project ai-video-worker/whisper ruff check .
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-worker/whisper
git commit -m "feat(worker): add local whisper transcription service"
```

### 任务 3：扩展流式音频资产能力

**任务卡：** 只扩展 `asset` 聚合；保留人物照片行为。声音只允许 MP3/WAV/M4A、100MB、扩展名/MIME/魔数一致；使用 OSS `upload(InputStream,long)` 和受控下载回调。

**文件：**
- 创建 `asset/VoiceSampleValidator.java`、`VoiceSampleMetadata.java`、`dto/UploadVoiceSampleDTO.java`。
- 修改 `IAssetService.java`、`AssetServiceImpl.java`。
- 测试 `VoiceSampleValidatorTest.java`、`AssetServiceVoiceSampleTest.java`。

- [ ] **步骤 1：编写失败测试**

```java
@Test void rejectsMp3ExtensionWithWavHeader() { /* expect 46201 */ }
@Test void rejectsVoiceLargerThan100MbBeforeOssUpload() { /* expect 46202 */ }
@Test void uploadsVoiceThroughInputStreamAndMarksCategoryVoiceSample() { /* verify stream upload */ }
@Test void refusesPortraitAssetWhenVoiceAssetIsRequired() { /* owner/category guard */ }
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceSampleValidatorTest,AssetServiceVoiceSampleTest test
```

- [ ] **步骤 3：实现最少资产接口**

```java
AssetDTO uploadVoiceSample(UploadVoiceSampleDTO command, AppPrincipalSnapshotDTO principal);
AssetDTO requireOwnedReadyVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal);
<T> T readOwnedVoiceAsset(String assetId, AppPrincipalSnapshotDTO principal, VoiceAssetReader<T> reader);
```

权限固定 `aivideo:voice:upload/query`，对象 Key 固定 `voices/{appUserId}/{uuid}.{ext}`。

- [ ] **步骤 4：运行资产与人物回归测试**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceSampleValidatorTest,AssetServiceVoiceSampleTest,PortraitImageValidatorTest,PortraitServiceImplTest test
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset
git commit -m "feat(asset): add streamed voice sample storage"
```

### 任务 4：实现声音核心资源和可靠租约状态机

**任务卡：** 只改 `ai-video-core/voice`；所有用户查询含 tenant/workspace/owner SQL 条件，外部调用不在事务中。状态与修订必须使用条件更新。

**文件：** `ai-video-core/src/main/java/org/dromara/aivideo/voice/**` 与对应测试。

- [ ] **步骤 1：编写 Service 失败测试**

覆盖创建幂等、冲突、分页所有权、条件领取、租约回收、成功写回、临时重试、第三次失败、旧租约、旧修订、文本修改和人工重试：

```java
@Test void staleLeaseCannotOverwriteNewAttempt() {
    assertThat(service.completeTranscription(oldLease, result)).isFalse();
}

@Test void listAlwaysScopesTenantWorkspaceAndOwner() {
    service.queryPage(query, principal, new PageQuery(20, 1));
    verify(mapper).selectVoPage(any(), argThat(this::hasAllOwnershipPredicates));
}
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceServiceImplTest test
```

- [ ] **步骤 3：实现 Entity、DTO、Mapper、Service**

接口至少包含：

```java
PageResult<VoiceDTO> queryPage(VoiceQueryDTO query, AppPrincipalSnapshotDTO principal, PageQuery pageQuery);
VoiceDTO create(CreateVoiceDTO command, AppPrincipalSnapshotDTO principal);
VoiceTranscriptionLeaseDTO claimNext(String workerId, Instant now);
boolean completeTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionResultDTO result);
void failTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionFailureDTO failure);
VoiceDTO updateTranscript(UpdateVoiceTranscriptDTO command, AppPrincipalSnapshotDTO principal);
VoiceDTO retryTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal);
```

页大小最大 50，固定 `createTime/voiceId DESC`，空页使用 `PageResult.build(List.of(), 0)`。

- [ ] **步骤 4：运行核心测试和包边界检查**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core -Dtest=VoiceServiceImplTest,VoiceSchemaContractTest test
rg -n "package .*\.(application|port|adapter|command|model)" ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice
git commit -m "feat(voice): add owned transcription resource state"
```

### 任务 5：实现 Java Whisper client 和后台执行器

**任务卡：** `ai-video-infra` 只负责内部 HTTP 技术集成与唤醒；业务状态由 `IVoiceService` 条件方法维护。共享开发配置直接读取两端 `application-dev.yml`，环境变量仅可选覆盖；日志不得包含 Token、路径或文本。

**文件：** `ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/**`、测试、`pom.xml`、用户 API 配置。

- [ ] **步骤 1：编写失败测试**

```java
@Test void rejectsMismatchedRequestIdFromWorker() { /* protocol exception */ }
@Test void maps503ToRetryableFailureWithoutLoggingBody() { /* transient */ }
@Test void schedulerClaimsThenCallsWorkerThenCompletes() { /* exact sequence */ }
@Test void schedulerDoesNothingWhenNoLeaseIsAvailable() { /* no call */ }
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-infra -Dtest=WhisperTranscriptionServiceImplTest,VoiceTranscriptionSchedulerTest test
```

- [ ] **步骤 3：实现配置、client、Service 实现和定时唤醒**

配置前缀 `aivideo.whisper`，默认 base URL 仅允许 `http://127.0.0.1:18181`；请求超时 10 分钟；Java 使用资产流构造 multipart，不传签名 URL。

- [ ] **步骤 4：运行 infra 测试**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-infra test
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-infra ai-video-api/ai-video-user-api/src/main/resources
git commit -m "feat(infra): connect local whisper transcription worker"
```

### 任务 6：实现用户端声音 HTTP 接口

**任务卡：** 只改 `ai-video-user/user/voice`；Controller 显式 `type=app`，只接参、登录主体、权限、防重和映射。请求不能携带 owner/tenant/workspace。

**文件：** `ai-video-user/src/main/java/org/dromara/aivideo/user/voice/**` 与 `VoiceControllerTest.java`。

- [ ] **步骤 1：编写失败 Web 测试**

覆盖 `POST /api/voices` multipart、分页、详情、access URL、文本修改、重试、匿名、错误权限、运营端 Token 和跨账号：

```java
@Test void uploadDerivesOwnerFromAppPrincipalAndReturnsPending() { /* 200 + string IDs */ }
@Test void otherWorkspaceVoiceIsReportedAsNotFound() { /* code 46401 */ }
@Test void uploadRejectsUnknownMetadataField() { /* validation error */ }
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-user -Dtest=VoiceControllerTest test
```

- [ ] **步骤 3：实现 BO、VO 和 Controller**

路由严格为规格第 5 节；上传使用 `@RequestPart("file") MultipartFile` 与
`@RequestPart("metadata") @Valid CreateVoiceBo`，权限码分别为 query/upload/edit/transcribe，写接口加 `@RepeatSubmit`。

- [ ] **步骤 4：运行用户端模块测试**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-user test
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/voice
git commit -m "feat(user-api): expose owned voice transcription endpoints"
```

### 任务 7：实现前端 Voice Service 与真实页面接入

**任务卡：** 保留声音页视觉结构，只替换生产数据与行为。页面不解包 RuoYi envelope、不拼 URL、不保存永久试听 URL。

**文件：** `src/services/ai-video/voice/**`、声音页面/Hook/测试、工作台上传 Modal。

- [ ] **步骤 1：编写 adapter 与页面失败测试**

```ts
it('normalizes voice page and keeps ids as strings', async () => {
  expect(await voiceService.page({ pageNum: 1, pageSize: 6 })).toEqual({ rows: expect.any(Array), total: 1 });
});

it('polls only while a row is pending or transcribing', async () => { /* fake timers */ });
it('uploads file and shows parsing state immediately', async () => { /* FormData assertion */ });
it('refreshes an expired access URL only once', async () => { /* HTMLAudioElement stub */ });
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
node node_modules/vitest/vitest.mjs run src/services/ai-video/voice src/pages/digital-human-studio/voices src/pages/digital-human-studio/index.test.tsx
```

- [ ] **步骤 3：实现 Service、React Query、真实上传和真实 Audio**

生产列表只用 API；测试通过 request mock 提供 7 条参考 fixture。状态文案固定为“已解析／解析中／解析失败”；编辑调用 transcript PUT，失败卡片调用 retry。

- [ ] **步骤 4：运行前端测试、类型和 lint**

```powershell
node node_modules/vitest/vitest.mjs run src/services/ai-video/voice src/pages/digital-human-studio/voices src/pages/digital-human-studio/index.test.tsx
node node_modules/typescript/bin/tsc --noEmit
node_modules/@biomejs/cli-win32-x64/biome.exe lint src/services/ai-video/voice src/pages/digital-human-studio/voices src/pages/digital-human-studio/index.tsx
```

- [ ] **步骤 5：提交**

```powershell
git add ai-video-ui/ai-video-webapp/src/services/ai-video/voice ai-video-ui/ai-video-webapp/src/pages/digital-human-studio
git commit -m "feat(studio): connect real voice transcription APIs"
```

### 任务 8：本机集成、路由边界和最终验证

**任务卡：** 不新增功能；只验证规格。MySQL/Redis 仅允许本机专用测试环境；真实 GPU 模型缺失时明确记录阻塞。

- [ ] **步骤 1：运行本机受控 Java 集成测试**

```powershell
mvn -Plocal-integration-test -pl ruoyi-modules/ai-video/ai-video-core -Dit.test=VoiceSchemaIT,VoiceOwnershipIT verify
```

夹具必须验证 `AI_VIDEO_IT_MYSQL_URL` 指向本机 `ai_video_test`，Redis 使用隔离逻辑库与本次前缀；禁止 `FLUSHALL`。

- [ ] **步骤 2：运行完整受影响模块测试与构建**

```powershell
mvn -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user,ai-video-user-api -am test
mvn -pl ai-video-user-api -am -DskipTests package
uv run --project ai-video-worker/whisper pytest -q
```

- [ ] **步骤 3：运行前端完整验证**

```powershell
node node_modules/vitest/vitest.mjs run
node node_modules/typescript/bin/tsc --noEmit
node_modules/@biomejs/cli-win32-x64/biome.exe lint src/services/ai-video/voice src/pages/digital-human-studio/voices src/pages/digital-human-studio/index.tsx src/pages/digital-human-studio/style.css
node node_modules/@umijs/max/bin/max.js build
```

- [ ] **步骤 4：执行 Worker 与双启动边界 Smoke Test**

- Worker `/health`：模型就绪 200，模型缺失 503。
- `ai-video-user-api`：用户端声音路由存在，无凭据/错误端 Token/跨账号反向场景拒绝。
- `ruoyi-admin`：用户端声音路由不存在。
- 上传非敏感中文短音频，确认立即返回、后台 ready、真实文本、试听、修正和失败重试。

- [ ] **步骤 5：独立审查与定向复核**

固定审查文件为本计划差异；必须覆盖文件安全、owner SQL、内部 Token、租约、临时文件、旧结果写回和前端 URL 生命周期。只修复 `[必须修复]`，修复后只复核差异。

- [ ] **步骤 6：提交最终修复（如有）**

```powershell
git add -- <仅本计划修复文件>
git diff --cached --check
git commit -m "fix(voice): close transcription review findings"
```

## 完成定义

- 所有规格条款分别映射到任务 1–8，没有占位符或第二套业务分层。
- 上传、所有权、状态、租约、Worker 协议、试听、文本修改和失败重试均有先失败后通过的测试。
- 不产生 IndexTTS2、声音克隆、额度或任务中心副作用。
- Java、Worker、前端、文档和构建验证实际通过；未具备本地模型／GPU 时只把该 Smoke Test 标为未验证。
