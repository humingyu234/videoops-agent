package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.CreationOutputDTO;
import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.dto.TimelineCanvasDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineFitMode;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Creates an owner-scoped project from the already-validated C0 digital-human source bridge. */
@Service
public class CreationProjectServiceImpl implements ICreationProjectService {

    private static final String DIGITAL_HUMAN_SOURCE_TYPE = "digital_human_job";
    private static final String APP_USER = "app_user";
    private static final String EDITING = "editing";
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final int PROJECT_TITLE_MAX_CODE_POINTS = 128;
    private static final String DEFAULT_SUBTITLE_FONT = "noto_sans_cjk_sc_regular";
    private static final String DEFAULT_SUBTITLE_FONT_VERSION = "2.004";
    private static final String DEFAULT_SUBTITLE_FONT_SHA256 =
        "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b";
    private static final int DEFAULT_SUBTITLE_PREFERRED_CODE_POINTS = 18;
    private static final int DEFAULT_SUBTITLE_MIN_CODE_POINTS = 6;
    private static final String DEFAULT_SUBTITLE_OUTLINE_COLOR = "#000000FF";
    private static final int DEFAULT_SUBTITLE_OUTLINE_WIDTH_PX = 3;

    private final CreationProjectMapper projectMapper;
    private final TimelineDraftMapper draftMapper;
    private final ICreationAssetService assetService;
    private final CreationAssetMapper assetMapper;
    private final AiTaskMapper taskMapper;
    private final ISubtitleNormalizationService subtitleNormalizationService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    CreationProjectServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                               ICreationAssetService assetService, CreationAssetMapper assetMapper,
                               AiTaskMapper taskMapper, ISubtitleNormalizationService subtitleNormalizationService,
                               JsonMapper jsonMapper) {
        this(projectMapper, draftMapper, assetService, assetMapper, taskMapper, subtitleNormalizationService, jsonMapper,
            (TransactionTemplate) null);
    }

    @Autowired
    public CreationProjectServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                      ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                      AiTaskMapper taskMapper, ISubtitleNormalizationService subtitleNormalizationService,
                                      JsonMapper jsonMapper,
                                      PlatformTransactionManager transactionManager) {
        this(projectMapper, draftMapper, assetService, assetMapper, taskMapper, subtitleNormalizationService, jsonMapper,
            new TransactionTemplate(transactionManager));
    }

    private CreationProjectServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                       ICreationAssetService assetService, CreationAssetMapper assetMapper,
                                       AiTaskMapper taskMapper, ISubtitleNormalizationService subtitleNormalizationService,
                                       JsonMapper jsonMapper,
                                       TransactionTemplate transactionTemplate) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper");
        this.assetService = Objects.requireNonNull(assetService, "assetService");
        this.assetMapper = Objects.requireNonNull(assetMapper, "assetMapper");
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper");
        this.subtitleNormalizationService = Objects.requireNonNull(subtitleNormalizationService,
            "subtitleNormalizationService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public CreationProjectDTO create(long actorId, CreateProjectCommand command) {
        ProjectRequest request = validateRequest(actorId, command);
        CreationProject existing = findProjectByIdempotencyKey(actorId, request.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(actorId, existing, request.requestDigest());
        }

        DigitalHumanCreationSourceDTO source = assetService.resolveDigitalHumanSource(actorId, request.sourceId());
        SourceSpec sourceSpec = validateSource(request.sourceId(), source);
        String initialTimeline = initialTimelineJson(sourceSpec);
        try {
            if (transactionTemplate == null) {
                return createProjectAndInitialDraft(actorId, request, sourceSpec, initialTimeline);
            }
            CreationProjectDTO created = transactionTemplate.execute(
                status -> createProjectAndInitialDraft(actorId, request, sourceSpec, initialTimeline));
            if (created == null) {
                throw sourceInvalid("创作项目创建失败");
            }
            return created;
        } catch (DuplicateKeyException exception) {
            CreationProject winner = findProjectByIdempotencyKey(actorId, request.idempotencyKey());
            if (winner != null) {
                return replayOrConflict(actorId, winner, request.requestDigest());
            }
            throw sourceInvalid("创作项目创建失败");
        }
    }

    @Override
    public CreationProjectDTO getOwned(long actorId, String projectId) {
        CreationProject project = requireOwnedProject(actorId, projectId);
        return toDto(project, requireDraft(actorId, project.getProjectId()));
    }

    @Override
    public CreationProjectDTO updateTitleOwned(long actorId, String projectId, UpdateProjectTitleCommand command) {
        CreationProject project = requireOwnedProject(actorId, projectId);
        if (EDITING.equals(project.getProjectStatus()) || "ready".equals(project.getProjectStatus())
            || "rendering".equals(project.getProjectStatus())) {
            String title = normalizeRequiredProjectTitle(command == null ? null : command.projectTitle());
            int updated = projectMapper.update(null, new LambdaUpdateWrapper<CreationProject>()
                .eq(CreationProject::getProjectId, project.getProjectId())
                .eq(CreationProject::getOwnerUserId, actorId)
                .eq(CreationProject::getDelFlag, "0")
                .ne(CreationProject::getProjectStatus, "archived")
                .set(CreationProject::getProjectTitle, title)
                .set(CreationProject::getUpdateBy, actorId));
            if (updated != 1) {
                CreationProject current = requireOwnedProject(actorId, projectId);
                if ("archived".equals(current.getProjectStatus())) {
                    throw projectStateConflict();
                }
                throw projectNotFound();
            }
            project.setProjectTitle(title);
            project.setUpdateBy(actorId);
            return toDto(project, requireDraft(actorId, project.getProjectId()));
        }
        throw projectStateConflict();
    }

    @Override
    public CreationOutputDTO getLatestOutputOwned(long actorId, String projectId) {
        CreationProject project = requireOwnedProject(actorId, projectId);
        if (project.getCurrentOutputAssetId() == null) {
            throw timelineAssetInvalid();
        }
        CreationAsset output = assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getAssetId, project.getCurrentOutputAssetId())
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getDelFlag, "0"));
        if (!isValidTimelineOutput(output, actorId, project.getCurrentOutputAssetId())) {
            throw timelineAssetInvalid();
        }
        AiTask rootTask = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getTaskId, output.getSourceRefId())
            .eq(AiTask::getOwnerUserId, actorId));
        if (!isValidRootRenderTask(rootTask, actorId, project.getProjectId(), output.getAssetId(),
            output.getSourceRefId())) {
            throw timelineAssetInvalid();
        }
        Instant createdAt = toInstant(output.getCreateTime());
        if (createdAt == null) {
            throw timelineAssetInvalid();
        }
        return new CreationOutputDTO(project.getProjectId().toString(), output.getAssetId().toString(),
            rootTask.getTaskId().toString(), createdAt);
    }

    private CreationProjectDTO createProjectAndInitialDraft(long actorId, ProjectRequest request, SourceSpec source,
                                                             String initialTimeline) {
        CreationProject project = new CreationProject();
        project.setProjectId(IdWorker.getId());
        project.setOwnerUserId(actorId);
        project.setProjectTitle(request.projectTitle());
        project.setIdempotencyKey(request.idempotencyKey());
        project.setRequestDigest(request.requestDigest());
        project.setSourceType(DIGITAL_HUMAN_SOURCE_TYPE);
        project.setSourceRefId(parsePositiveId(source.sourceId()));
        project.setBaseVideoAssetId(parsePositiveId(source.baseVideoAssetId()));
        project.setPrimaryAudioAssetId(source.primaryAudioAssetId() == null ? null
            : parsePositiveId(source.primaryAudioAssetId()));
        project.setScriptTextSnapshot(source.scriptTextSnapshot());
        project.setCanvasWidth(source.width());
        project.setCanvasHeight(source.height());
        project.setFrameRate(source.frameRate());
        project.setDurationMs(source.durationMs());
        project.setProjectStatus(EDITING);
        project.setActorType(APP_USER);
        project.setActorId(actorId);
        project.setCreateBy(actorId);
        project.setUpdateBy(actorId);
        project.setDelFlag("0");
        if (projectMapper.insert(project) != 1) {
            throw sourceInvalid("创作项目创建失败");
        }

        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(IdWorker.getId());
        draft.setOwnerUserId(actorId);
        draft.setProjectId(project.getProjectId());
        draft.setRevision(1L);
        draft.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        draft.setContentJson(initialTimeline);
        draft.setContentHash(sha256(draft.getContentJson()));
        draft.setDurationMs(source.durationMs());
        draft.setActorType(APP_USER);
        draft.setActorId(actorId);
        draft.setCreateBy(actorId);
        draft.setUpdateBy(actorId);
        draft.setDelFlag("0");
        if (draftMapper.insert(draft) != 1) {
            throw sourceInvalid("初始时间轴创建失败");
        }
        return toDto(project, draft);
    }

    private CreationProjectDTO replayOrConflict(long actorId, CreationProject existing, String requestDigest) {
        if (!Objects.equals(existing.getRequestDigest(), requestDigest)) {
            throw new ServiceException("幂等键已用于不同的创作请求", TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT);
        }
        TimelineDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<TimelineDraft>()
            .eq(TimelineDraft::getOwnerUserId, actorId)
            .eq(TimelineDraft::getProjectId, existing.getProjectId())
            .eq(TimelineDraft::getDelFlag, "0")
            .orderByDesc(TimelineDraft::getRevision)
            .last("LIMIT 1"));
        if (draft == null) {
            throw sourceInvalid("创作项目状态不完整");
        }
        return toDto(existing, draft);
    }

    private CreationProject findProjectByIdempotencyKey(long actorId, String idempotencyKey) {
        return projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getIdempotencyKey, idempotencyKey)
            .eq(CreationProject::getDelFlag, "0"));
    }

    private CreationProject requireOwnedProject(long actorId, String projectId) {
        if (actorId <= 0) {
            throw projectNotFound();
        }
        long parsedProjectId = parsePositiveId(projectId);
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, parsedProjectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null) {
            throw projectNotFound();
        }
        return project;
    }

    private TimelineDraft requireDraft(long actorId, long projectId) {
        TimelineDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<TimelineDraft>()
            .eq(TimelineDraft::getOwnerUserId, actorId)
            .eq(TimelineDraft::getProjectId, projectId)
            .eq(TimelineDraft::getDelFlag, "0")
            .orderByDesc(TimelineDraft::getRevision)
            .last("LIMIT 1"));
        if (draft == null) {
            throw new ServiceException("创作项目状态不完整", TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
        }
        return draft;
    }

    private ProjectRequest validateRequest(long actorId, CreateProjectCommand command) {
        if (actorId <= 0 || command == null || !DIGITAL_HUMAN_SOURCE_TYPE.equals(command.sourceType())
            || !POSITIVE_ID.matcher(nullToEmpty(command.sourceId())).matches()
            || !IDEMPOTENCY_KEY.matcher(nullToEmpty(command.idempotencyKey())).matches()) {
            throw sourceInvalid("创作项目请求无效");
        }
        String projectTitle = normalizeProjectTitle(command.projectTitle());
        return new ProjectRequest(command.sourceId(), projectTitle, command.idempotencyKey(),
            sha256(DIGITAL_HUMAN_SOURCE_TYPE + "\n" + command.sourceId() + "\n"
                + projectTitle));
    }

    private SourceSpec validateSource(String requestedSourceId, DigitalHumanCreationSourceDTO source) {
        int canvasWidth = TimelineContractLimits.NUMERIC_LIMITS.get("canvasWidth").intValue();
        int canvasHeight = TimelineContractLimits.NUMERIC_LIMITS.get("canvasHeight").intValue();
        int canvasFrameRate = TimelineContractLimits.NUMERIC_LIMITS.get("canvasFrameRate").intValue();
        if (source == null || !Objects.equals(requestedSourceId, source.sourceId())
            || !POSITIVE_ID.matcher(nullToEmpty(source.baseVideoAssetId())).matches()
            || (source.primaryAudioAssetId() != null
                && !POSITIVE_ID.matcher(source.primaryAudioAssetId()).matches())
            || source.scriptTextSnapshot() == null || source.scriptTextSnapshot().isBlank()
            || source.scriptTextSnapshot().codePointCount(0, source.scriptTextSnapshot().length())
                > TimelineContractLimits.NUMERIC_LIMITS.get("maxProjectScriptCodePoints").intValue()
            || source.durationMs() < TimelineContractLimits.NUMERIC_LIMITS.get("minDurationMs").longValue()
            || source.durationMs() > TimelineContractLimits.NUMERIC_LIMITS.get("maxDurationMs").longValue()
            || source.width() <= 0
            || source.height() <= 0
            || source.frameRate() <= 0) {
            throw sourceInvalid("创作来源不可用");
        }
        return new SourceSpec(source.sourceId(), source.baseVideoAssetId(), source.primaryAudioAssetId(),
            source.scriptTextSnapshot(), source.durationMs(), canvasWidth, canvasHeight, canvasFrameRate);
    }

    private String normalizeProjectTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) {
            return "数字人创作项目";
        }
        if (title.codePointCount(0, title.length()) > PROJECT_TITLE_MAX_CODE_POINTS) {
            throw sourceInvalid("创作项目请求无效");
        }
        return title;
    }

    private String normalizeRequiredProjectTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty() || title.codePointCount(0, title.length()) > PROJECT_TITLE_MAX_CODE_POINTS) {
            throw sourceInvalid("创作项目请求无效");
        }
        return title;
    }

    private String initialTimelineJson(SourceSpec source) {
        TimelineMainVideoElementDTO mainVideo = new TimelineMainVideoElementDTO(
            "main-video", TimelineElementType.MAIN_VIDEO, 0L, source.durationMs(), 0,
            true, true, "digital human video", source.baseVideoAssetId(), source.durationMs(), 0L,
            TimelineFitMode.COVER);
        TimelineTrackDTO track = new TimelineTrackDTO("track-main-video", TimelineTrackType.MAIN_VIDEO,
            TimelineTrackArea.CENTER, 0, true, false, List.of(mainVideo));
        List<TimelineSubtitleElementDTO> subtitles = initialSubtitles(source);
        TimelineTrackDTO subtitleTrack = new TimelineTrackDTO("track-subtitle", TimelineTrackType.SUBTITLE,
            TimelineTrackArea.TOP, 1, false, false, List.copyOf(subtitles));
        TimelineDocumentDTO document = new TimelineDocumentDTO(TimelineContractLimits.SCHEMA_VERSION,
            new TimelineCanvasDTO(source.width(), source.height(), source.frameRate(), source.durationMs(),
                TimelineContractLimits.NUMERIC_LIMITS.get("safeMarginRatio")),
            List.of(track, subtitleTrack));
        try {
            return jsonMapper.writeValueAsString(document);
        } catch (Exception exception) {
            throw new ServiceException("初始时间轴序列化失败", TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
        }
    }

    private List<TimelineSubtitleElementDTO> initialSubtitles(SourceSpec source) {
        String script = source.scriptTextSnapshot();
        int scriptCodePoints = script.codePointCount(0, script.length());
        int maxCodePoints = TimelineContractLimits.NUMERIC_LIMITS.get("maxSubtitleCodePoints").intValue();
        List<SubtitleSourceRange> sourceRanges = initialSubtitleSourceRanges(script, maxCodePoints);
        int segmentCount = sourceRanges.size();
        int maxElements = TimelineContractLimits.NUMERIC_LIMITS.get("maxElementsPerTrack").intValue();
        if (segmentCount <= 0 || segmentCount > maxElements || source.durationMs() < segmentCount) {
            throw sourceInvalid("创作来源文案无法生成初始字幕");
        }
        List<TimelineSubtitleElementDTO> candidates = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            SubtitleSourceRange range = sourceRanges.get(index);
            int sourceStart = range.startOffset();
            int sourceEnd = range.endOffset();
            String sourceText = script.substring(script.offsetByCodePoints(0, sourceStart),
                script.offsetByCodePoints(0, sourceEnd));
            long startMs = source.durationMs() * sourceStart / scriptCodePoints;
            long endMs = index == segmentCount - 1 ? source.durationMs()
                : source.durationMs() * sourceEnd / scriptCodePoints;
            candidates.add(new TimelineSubtitleElementDTO("subtitle-%03d".formatted(index + 1),
                TimelineElementType.SUBTITLE, startMs, endMs, 1, true, false, "subtitle",
                sourceText, sourceText, sourceStart, sourceEnd, DEFAULT_SUBTITLE_FONT,
                DEFAULT_SUBTITLE_FONT_VERSION, DEFAULT_SUBTITLE_FONT_SHA256, 48, "#FFFFFFFF",
                false, null, true, DEFAULT_SUBTITLE_OUTLINE_COLOR, DEFAULT_SUBTITLE_OUTLINE_WIDTH_PX,
                "lower", "center"));
        }
        List<TimelineSubtitleElementDTO> normalized = subtitleNormalizationService.normalize(script, candidates,
            source.width(), TimelineContractLimits.NUMERIC_LIMITS.get("safeMarginRatio")).subtitles();
        if (normalized.isEmpty() || normalized.size() > maxElements) {
            throw sourceInvalid("创作来源文案无法生成初始字幕");
        }
        return normalized;
    }

    private List<SubtitleSourceRange> initialSubtitleSourceRanges(String script, int maxCodePoints) {
        int[] codePoints = script.codePoints().toArray();
        List<SubtitleClause> clauses = new ArrayList<>();
        int start = 0;
        for (int end = 1; end <= codePoints.length; end++) {
            int codePoint = codePoints[end - 1];
            boolean hardBoundary = isHardSubtitleBoundary(codePoint);
            boolean softBoundary = isSoftSubtitleBoundary(codePoint);
            boolean limitBoundary = end - start >= maxCodePoints;
            boolean scriptEnd = end == codePoints.length;
            if (hardBoundary || softBoundary || limitBoundary || scriptEnd) {
                clauses.add(new SubtitleClause(start, end, hardBoundary || limitBoundary || scriptEnd));
                start = end;
            }
        }

        List<SubtitleSourceRange> ranges = new ArrayList<>();
        int rangeStart = -1;
        int rangeEnd = -1;
        int visibleCodePoints = 0;
        for (SubtitleClause clause : clauses) {
            int clauseVisibleCodePoints = visibleSubtitleCodePoints(codePoints, clause.startOffset(),
                clause.endOffset());
            if (rangeStart >= 0 && visibleCodePoints >= DEFAULT_SUBTITLE_MIN_CODE_POINTS
                && visibleCodePoints + clauseVisibleCodePoints > DEFAULT_SUBTITLE_PREFERRED_CODE_POINTS) {
                ranges.add(new SubtitleSourceRange(rangeStart, rangeEnd));
                rangeStart = -1;
                visibleCodePoints = 0;
            }
            if (rangeStart < 0) {
                rangeStart = clause.startOffset();
            }
            rangeEnd = clause.endOffset();
            visibleCodePoints += clauseVisibleCodePoints;
            if (clause.hardBoundary()) {
                ranges.add(new SubtitleSourceRange(rangeStart, rangeEnd));
                rangeStart = -1;
                visibleCodePoints = 0;
            }
        }
        if (rangeStart >= 0) {
            ranges.add(new SubtitleSourceRange(rangeStart, rangeEnd));
        }
        return List.copyOf(ranges);
    }

    private int visibleSubtitleCodePoints(int[] codePoints, int startOffset, int endOffset) {
        int visible = 0;
        for (int index = startOffset; index < endOffset; index++) {
            int codePoint = codePoints[index];
            if (!Character.isWhitespace(codePoint) && !isHardSubtitleBoundary(codePoint)
                && !isSoftSubtitleBoundary(codePoint)) {
                visible++;
            }
        }
        return visible;
    }

    private boolean isHardSubtitleBoundary(int codePoint) {
        return codePoint == '。' || codePoint == '.' || codePoint == '！' || codePoint == '!'
            || codePoint == '？' || codePoint == '?' || codePoint == '\n' || codePoint == '\r';
    }

    private boolean isSoftSubtitleBoundary(int codePoint) {
        return codePoint == '，' || codePoint == ',' || codePoint == '；' || codePoint == ';'
            || codePoint == '：' || codePoint == ':' || codePoint == '、';
    }

    private CreationProjectDTO toDto(CreationProject project, TimelineDraft draft) {
        return new CreationProjectDTO(project.getProjectId().toString(), project.getProjectTitle(),
            project.getSourceType(), project.getSourceRefId().toString(), project.getBaseVideoAssetId().toString(),
            project.getPrimaryAudioAssetId() == null ? null : project.getPrimaryAudioAssetId().toString(),
            project.getProjectStatus(), project.getCanvasWidth(), project.getCanvasHeight(), project.getFrameRate(),
            project.getDurationMs(), draft.getRevision(), draft.getSchemaVersion(),
            project.getCurrentOutputAssetId() == null ? null : project.getCurrentOutputAssetId().toString(),
            toInstant(project.getCreateTime()), toInstant(project.getUpdateTime()));
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private boolean isValidTimelineOutput(CreationAsset output, long actorId, long outputAssetId) {
        return output != null
            && Objects.equals(output.getAssetId(), outputAssetId)
            && Objects.equals(output.getOwnerUserId(), actorId)
            && CreationAssetStatus.READY.value().equals(output.getAssetStatus())
            && CreationAssetType.VIDEO.value().equals(output.getAssetType())
            && CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value().equals(output.getUsageOrigin())
            && output.getSourceRefId() != null
            && "0".equals(output.getDelFlag());
    }

    private boolean isValidRootRenderTask(AiTask task, long actorId, long projectId, long outputAssetId,
                                          long sourceTaskId) {
        return task != null
            && Objects.equals(task.getTaskId(), sourceTaskId)
            && Objects.equals(task.getOwnerUserId(), actorId)
            && AiTaskType.TIMELINE_RENDER.value().equals(task.getTaskType())
            && AiTaskResourceType.CREATION_PROJECT.value().equals(task.getResourceType())
            && Objects.equals(task.getResourceId(), projectId)
            && AiTaskStatus.SUCCESS.value().equals(task.getTaskStatus())
            && Objects.equals(task.getResultAssetId(), outputAssetId);
    }

    private long parsePositiveId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw sourceInvalid("创作来源不可用");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ServiceException sourceInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.CREATION_SOURCE_INVALID);
    }

    private ServiceException projectNotFound() {
        return new ServiceException("创作项目不存在", TimelineErrorCodes.CREATION_PROJECT_NOT_FOUND);
    }

    private ServiceException projectStateConflict() {
        return new ServiceException("当前创作项目状态不允许该操作", TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT);
    }

    private ServiceException timelineAssetInvalid() {
        return new ServiceException("创作成品不可用", TimelineErrorCodes.TIMELINE_ASSET_INVALID);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SubtitleClause(int startOffset, int endOffset, boolean hardBoundary) {
    }

    private record SubtitleSourceRange(int startOffset, int endOffset) {
    }

    private record ProjectRequest(String sourceId, String projectTitle, String idempotencyKey, String requestDigest) {
    }

    private record SourceSpec(String sourceId, String baseVideoAssetId, String primaryAudioAssetId,
                              String scriptTextSnapshot, long durationMs, int width, int height, int frameRate) {
    }
}
