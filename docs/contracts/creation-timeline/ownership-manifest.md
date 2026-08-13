# 创作第 6 步文件所有权清单

## 基线与发布规则

- BASE_SHA：18f45b9cfcb4229cfa44458144a0ba054782b5c0
- 集成分支：codex/step6-integration
- 契约分支：codex/step6-contract
- 两条分支创建起点必须等于 BASE_SHA。
- C0_SHA 只在契约合入集成分支后的 PR 和固定交付消息中发布；禁止把具体 C0_SHA 写回产生该提交号的仓库文件。
- 任务 2 至任务 6 的所有文件只允许 A 在契约分支修改。C0 合入后，A、B、C 的功能分支均不得修改这些冻结文件。
- 首次需要修改本清单未授权路径时立即暂停，由 A 提交所有权变更卡；禁止先写代码再处理冲突。
- 本轮禁止修改 package-lock.json。确需新增前端依赖时，B 必须先暂停，由 A 更新所有权清单后再继续。

## C0 冻结文件

| 任务 | 操作 | 精确路径 | 唯一负责人 |
| --- | --- | --- | --- |
| 1 | 新建 | `docs/contracts/creation-timeline/ownership-manifest.md` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/timeline-1.schema.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/project.example.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/timeline-draft.example.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/timeline-task.example.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/timeline-errors.example.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/subtitle-normalization.example.json` | A（契约负责人） |
| 2 | 新建 | `docs/contracts/creation-timeline/font-registry.json` | A（契约负责人） |
| 2 | 修改 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml` | A（契约负责人） |
| 2 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineContractLimits.java` | A（契约负责人） |
| 2 | 新建测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineContractFixtureTest.java` | A（契约负责人） |
| 3 | 修改 | `docs/API_CONTRACT.md` | A（契约负责人） |
| 3 | 修改 | `docs/DOMAIN_MODEL.md` | A（契约负责人） |
| 3 | 修改 | `docs/ASYNC_TASKS.md` | A（契约负责人） |
| 3 | 修改 | `docs/ARCHITECTURE.md` | A（契约负责人） |
| 4 | 新建 | `docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql` | A（契约负责人） |
| 4 | 新建测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationContractTest.java` | A（契约负责人） |
| 4 | 新建集成测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationIT.java` | A（契约负责人） |
| 4 | 新建集成测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelinePermissionIT.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineDocumentDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCanvasDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTrackDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMainVideoElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImageElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelinePipVideoElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAudioElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualEffectElementDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineVisualTransformDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCropDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFadeDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineAssetReferenceDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineNormalizationChangeDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureCommandDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineTextMeasureResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderCommandDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineRenderResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineOutputConfigDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineMediaProbeDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineProgressDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptCommandDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineImagePromptResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionCommandDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineFancyTextSuggestionResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentCommandDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineSubtitleAlignmentResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineElementType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineTrackArea.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineFitMode.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVisualEffectCode.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineOutputQuality.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineAssetUsageType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineDocumentType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineVersionReason.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/FancyTextTemplateCode.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetUploadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetQueryDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationAssetResolveDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/DigitalHumanCreationSourceDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RegisterPendingRenderOutputDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/PendingRenderOutputDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputReadyDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/RenderOutputFailureDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetStatus.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/enums/CreationAssetUsageOrigin.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/CreateFreeAiTaskDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRequestPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRenderPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskResultPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskImagePromptResultPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskFancyTextResultPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSubtitleAlignmentResultPayloadDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSummaryDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskQueryDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskLeaseDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskProgressDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskCompletionDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/RetryAiTaskDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDispatchResultDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/dto/WhisperTranscriptionInputDTO.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStatus.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskExecutionStatus.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskAttemptStatus.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStage.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskResourceType.java` | A（契约负责人） |
| 5 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineErrorCodes.java` | A（契约负责人） |
| 5 | 新建测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineDtoContractTest.java` | A（契约负责人） |
| 5 | 新建测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskDtoContractTest.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationAssetService.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/CreationMediaHandle.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineMediaRenderService.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineRenderOutputHandle.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineTaskProgressListener.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineAiSuggestionService.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/exception/TimelineExecutionException.java` | A（契约负责人） |
| 6 | 新建 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/TimelineExecutionFailureCode.java` | A（契约负责人） |
| 6 | 修改 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IWhisperTranscriptionService.java` | A（契约负责人） |
| 6 | 新建测试 | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineServiceBoundaryTest.java` | A（契约负责人） |

## 功能分支独占范围

### A：后端业务（codex/step6-backend）

- ai-video-core 中 creation、timeline、task 的 Entity、Mapper、Service 与实现，但不包含上表冻结的 DTO、枚举、常量和接口。
- ai-video-user 中 creation、timeline、task 的 BO、VO、Controller 及测试。
- app 专用 MyBatis 审计上下文、过滤器和对应测试。
- 禁止修改 C0 文件、前端文件、媒体实现和媒体配置。

### B：前端编辑器（codex/step6-ui）

- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/**
- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-timeline/**
- ai-video-ui/ai-video-webapp/src/services/ai-video/creation-assets/**
- ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/**
- ai-video-ui/ai-video-webapp/src/pages/tasks/**
- ai-video-ui/ai-video-webapp/src/services/ai-video/core/blobAdapter.ts
- ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts
- blobAdapter、ruoyiAdapter 对应测试
- ai-video-ui/ai-video-webapp/config/config.ts
- ai-video-ui/ai-video-webapp/src/config.test.ts
- ai-video-ui/ai-video-webapp/package.json
- ai-video-ui/ai-video-webapp/scripts/verify-creation-timeline-production.mjs
- 两个时间轴 Mock 文件
- ai-video-ui/ai-video-webapp/public/timeline-fonts/**
- 禁止修改 package-lock.json、Java、SQL 和 C0 文件；集成负责人不得二次编辑这些前端共享文件。

### C：媒体与 AI（codex/step6-media）

- ai-video-infra 中 timeline/** 的 ffprobe、FFmpeg、ASS、AI 建议和安全进程实现。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImpl.java`，仅限任务 33 机械实现 C0 通用重载并复用既有客户端。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/voice/service/impl/WhisperTranscriptionServiceImplTest.java`，仅限上述重载的回归测试。
- 对应单元测试、固定媒体夹具和固定字体资源。
- application.yml 中 aivideo.timeline.* 命名空间。
- 禁止修改 Controller、Entity、Mapper、任务状态机和 C0 文件。

### A：集成交付（codex/step6-integration）

- 三条功能 PR 的顺序合并和冲突解决。
- 集成测试、根 POM／启动 POM 的必要接线和最终验证记录。
- 只解决合并所需接线，不重新设计 C0，不二次编辑 B 独占的前端共享文件。

## 所有权变更卡

变更卡必须包含：申请人、受影响分支、精确路径、变更原因、是否影响公共契约、现有负责人确认、同步顺序和验证命令。公共字段、接口、迁移或枚举变化必须先形成 C1；本需求最多自动执行一次 C1。

### C1：调度并发契约与 Whisper 实现归属

- **申请人：** C（媒体与 AI）。
- **受影响分支：** `codex/step6-backend`、`codex/step6-media`、`codex/step6-integration`；B 不受影响。
- **精确契约路径：** `IAiTaskService.java`、`TimelineServiceBoundaryTest.java`、`docs/ASYNC_TASKS.md`、`10-backend-a.md`、`30-media-ai-c.md` 和本清单，由 A（契约负责人）修改。
- **精确授权路径：** 上述 `WhisperTranscriptionServiceImpl.java` 与 `WhisperTranscriptionServiceImplTest.java`，转交 C，仅限任务 33；A 不在后端分支修改。
- **原因：** C0 的 `dispatchNext(String)` 无法把已校验的单用户／系统并发上限传入负责数据库领取的 Service；Scheduler 又不得读取 owner 或 Mapper。任务 33 已明确要求修改既有 Whisper 实现，但 C0 清单漏登记两个文件。
- **公共契约影响：** 仅把内部 Service 签名前向改为 `dispatchNext(String,int,int)` 并冻结并发语义；不改变 HTTP、JSON、数据库迁移、任务状态或媒体接口。Whisper 只调整文件所有权，不改变已冻结重载。
- **现有负责人确认：** A 于 2026-08-09 确认本卡；这是本需求唯一一次自动 C1。
- **同步顺序：** `codex/step6-contract-c1` 合入 `codex/step6-integration` 并发布完整 `C1_SHA`；A、C 从该集成提交同步各自分支后恢复，禁止 cherry-pick；B 继续现有分支。
- **验证：** 运行 `TimelineServiceBoundaryTest`、`WhisperTranscriptionServiceImplTest` 和 `scripts/validate-development-standards.ps1`；A 在任务 14 追加真实 MySQL 并发领取验证，C 在任务 27 验证配置透传与本进程执行槽。

### C2：时间轴合成请求与成品响应 Wire 契约

- **申请人和批准：** 项目负责人已于 2026-08-09 明确批准创建 C2。
- **受影响分支：** `codex/step6-contract-c2`、`codex/step6-backend-c2-wire-fix`、`codex/step6-integration`；前端适配仅由 B 在获授权路径处理。
- **精确 C2 契约路径：** `docs/API_CONTRACT.md`、本清单、`docs/contracts/creation-timeline/creation-output.example.json`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/TimelineContractFixtureTest.java`。
- **公共契约结论：** 重合成请求的唯一质量语义为 `outputConfig.qualityPreset`，值只能为 `standard` 或 `high`；不得接收、读取或兼容 `quality`。`CreationOutputVO` 只含 `projectId`、`outputAssetId`、`taskId`、`createdAt` 四个业务字段，预览和下载仅使用 `GET /api/studio/creation-assets/{outputAssetId}/content`。
- **不变项：** 不修改 `timeline-1.schema.json`、`TimelineOutputConfigDTO`、`CreationAssetDTO`，不增加 SQL、DDL 或迁移。
- **A 的后端修复所有权：** `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/timeline/domain/bo/CreateTimelineRenderTaskBo.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/TimelineTaskApplicationService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/CreationOutputDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationProjectService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImpl.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/domain/vo/LatestCreationOutputVo.java`，以及仅在必要时的 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/controller/CreationProjectController.java`。
- **B 的前端适配所有权：** `ai-video-ui/ai-video-webapp/src/services/ai-video/core/runtimeRuoYiAdapter.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/core/runtimeRuoYiAdapter.test.ts`。
- **兼容性边界：** 禁止前后端双字段发送、读取、别名或兼容分支；不得把 `taskId` 或 `sourceRefId` 加入通用 `CreationAssetDTO`。
- **同步顺序：** C2 合入 `codex/step6-integration` 后，A 与 B 均从该分支的完整实时集成 SHA 同步开始后续工作，禁止 cherry-pick。

### 任务 36–38：集成验收修复授权（不形成新契约版本）

- **申请人和批准：** A（集成负责人）；项目负责人于 2026-08-10 在任务 36–38 验收修复开始前明确授权 A 直接修复验收暴露出的前端、后端、媒体与集成测试缺陷，并在本轮临时取消 A／B／C 文件所有权限制。
- **受影响分支：** 仅 `codex/step6-integration`。
- **精确 C0 授权路径：** `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/constant/TimelineContractLimits.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/TimelineCanvasDTO.java`；允许新增同目录 `TimelineDecimalSerializer.java` 作为 Wire 实现辅助类。
- **原因：** 真实双启动器验收需要复用冻结字体登记摘要，并证明 `safeMarginRatio` 按既有 Schema 的 JSON number `0.05` 输出；默认 `BigDecimal` 科学计数法会使既有冻结样例无法按 Wire 契约闭环。
- **公共契约影响：** 无。字体代码、版本、摘要、DTO 字段名、顺序、Java 类型、可空性、允许值和 JSON Schema 均不改变；序列化器只保证既有数值语义以普通 JSON number 表示。因此本卡不是新的 C1／C2，也不授权修改 SQL、状态、错误码、接口或其他 C0 文件。
- **现有负责人确认：** 项目负责人上述授权同时覆盖契约负责人 A、后端、前端和媒体的本轮验收文件；授权在任务 38 结束时自动终止。
- **同步顺序：** 所有修复只以 `codex/step6-integration` 前向提交并在实时远端未变化后非强制推送；其他分支不得反向复制或继续修改这些 C0 路径。
- **验证：** 运行 `TimelineContractFixtureTest`、`TimelineJsonSerializationTest`、任务 37 的四个真实 IT、`scripts/validate-development-standards.ps1` 与 `git diff --check`，并在合入 `main` 前完成一次独立只读整体审查。
