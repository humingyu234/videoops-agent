package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineWriteReceipt;
import org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineImageElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineWriteReceiptMapper;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Persists only server-validated canonical drafts and their owner-scoped write receipts. */
@Service
public class TimelineDraftServiceImpl implements ITimelineDraftService {

    private static final String DRAFT_SAVE = "draft_save";
    private static final String APP_USER = "app_user";
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final CreationProjectMapper projectMapper;
    private final TimelineDraftMapper draftMapper;
    private final TimelineAssetRefMapper assetRefMapper;
    private final TimelineWriteReceiptMapper receiptMapper;
    private final ITimelineDocumentService documentService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    TimelineDraftServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                             TimelineAssetRefMapper assetRefMapper, TimelineWriteReceiptMapper receiptMapper,
                             ITimelineDocumentService documentService, JsonMapper jsonMapper) {
        this(projectMapper, draftMapper, assetRefMapper, receiptMapper, documentService, jsonMapper,
            (TransactionTemplate) null);
    }

    @Autowired
    public TimelineDraftServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                    TimelineAssetRefMapper assetRefMapper, TimelineWriteReceiptMapper receiptMapper,
                                    ITimelineDocumentService documentService, JsonMapper jsonMapper,
                                    PlatformTransactionManager transactionManager) {
        this(projectMapper, draftMapper, assetRefMapper, receiptMapper, documentService, jsonMapper,
            new TransactionTemplate(transactionManager));
    }

    private TimelineDraftServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                     TimelineAssetRefMapper assetRefMapper, TimelineWriteReceiptMapper receiptMapper,
                                     ITimelineDocumentService documentService, JsonMapper jsonMapper,
                                     TransactionTemplate transactionTemplate) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper");
        this.assetRefMapper = Objects.requireNonNull(assetRefMapper, "assetRefMapper");
        this.receiptMapper = Objects.requireNonNull(receiptMapper, "receiptMapper");
        this.documentService = Objects.requireNonNull(documentService, "documentService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public TimelineDraftView getOwned(long actorId, String projectId) {
        CreationProject project = requireProject(actorId, projectId);
        TimelineDraft draft = requireDraft(actorId, project.getProjectId());
        return toView(draft, readTimeline(draft.getContentJson()));
    }

    @Override
    public TimelineWriteResult save(long actorId, String projectId, SaveTimelineDraftCommand command) {
        long parsedProjectId = parsePositiveId(projectId, "项目不存在");
        CreationProject project = requireProject(actorId, parsedProjectId);
        TimelineDraft draft = requireDraft(actorId, project.getProjectId());
        SaveSpec spec = validateCommand(command);
        TimelineWriteReceipt existing = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
        if (existing == null && (draft.getRevision() == null || draft.getRevision() != spec.expectedRevision())) {
            throw revisionConflict();
        }
        ITimelineDocumentService.ValidatedTimeline validated = documentService.validate(actorId, context(project),
            command.timeline());
        String requestDigest = requestDigest(project.getProjectId(), spec.expectedRevision(), validated.contentHash());
        if (existing != null) {
            return replayOrConflict(actorId, draft, existing, requestDigest);
        }
        try {
            if (transactionTemplate == null) {
                return writeInShortTransaction(actorId, project, draft, spec, validated, requestDigest);
            }
            TimelineWriteResult result = transactionTemplate.execute(status -> writeInShortTransaction(actorId, project,
                draft, spec, validated, requestDigest));
            if (result == null) {
                throw documentInvalid("草稿保存失败");
            }
            return result;
        } catch (DuplicateKeyException exception) {
            TimelineWriteReceipt winner = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
            if (winner != null) {
                TimelineDraft current = requireDraft(actorId, project.getProjectId());
                return replayOrConflict(actorId, current, winner, requestDigest);
            }
            throw documentInvalid("草稿保存失败");
        }
    }

    private TimelineWriteResult writeInShortTransaction(long actorId, CreationProject project, TimelineDraft current,
                                                         SaveSpec spec,
                                                         ITimelineDocumentService.ValidatedTimeline validated,
                                                         String requestDigest) {
        TimelineDraft next = new TimelineDraft();
        next.setTimelineDraftId(current.getTimelineDraftId());
        next.setOwnerUserId(actorId);
        next.setProjectId(project.getProjectId());
        next.setRevision(current.getRevision() + 1);
        next.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        next.setContentJson(validated.canonicalJson());
        next.setContentHash(validated.contentHash());
        next.setDurationMs(validated.timeline().canvas().durationMs());
        next.setActorType(APP_USER);
        next.setActorId(actorId);
        next.setUpdateBy(actorId);
        next.setDelFlag("0");
        int updated = draftMapper.update(next, new LambdaUpdateWrapper<TimelineDraft>()
            .eq(TimelineDraft::getTimelineDraftId, current.getTimelineDraftId())
            .eq(TimelineDraft::getOwnerUserId, actorId)
            .eq(TimelineDraft::getProjectId, project.getProjectId())
            .eq(TimelineDraft::getRevision, spec.expectedRevision())
            .eq(TimelineDraft::getDelFlag, "0"));
        if (updated != 1) {
            throw revisionConflict();
        }
        rebuildDraftReferences(actorId, project.getProjectId(), current.getTimelineDraftId(), validated.timeline());
        TimelineWriteReceipt receipt = new TimelineWriteReceipt();
        receipt.setTimelineWriteReceiptId(IdWorker.getId());
        receipt.setOwnerUserId(actorId);
        receipt.setProjectId(project.getProjectId());
        receipt.setOperationType(DRAFT_SAVE);
        receipt.setIdempotencyKey(spec.idempotencyKey());
        receipt.setRequestDigest(requestDigest);
        receipt.setExpectedRevision(spec.expectedRevision());
        receipt.setResultRevision(next.getRevision());
        receipt.setResponseSummaryJson(summaryJson(next.getRevision(), next.getContentHash()));
        receipt.setActorType(APP_USER);
        receipt.setActorId(actorId);
        receipt.setCreateBy(actorId);
        receipt.setUpdateBy(actorId);
        if (receiptMapper.insert(receipt) != 1) {
            throw documentInvalid("草稿写回执创建失败");
        }
        return toResult(next, validated.timeline(), false, false, null, null, null,
            validated.normalizationChanges());
    }

    private TimelineWriteResult replayOrConflict(long actorId, TimelineDraft current, TimelineWriteReceipt receipt,
                                                  String requestDigest) {
        if (!DRAFT_SAVE.equals(receipt.getOperationType()) || !requestDigest.equals(receipt.getRequestDigest())) {
            throw idempotencyConflict();
        }
        String operationHash = summaryContentHash(receipt.getResponseSummaryJson());
        if (operationHash == null || receipt.getResultRevision() == null) {
            throw documentInvalid("草稿写回执损坏");
        }
        if (Objects.equals(current.getRevision(), receipt.getResultRevision())
            && operationHash.equals(current.getContentHash())) {
            return toResult(current, readTimeline(current.getContentJson()), true, false,
                Long.toString(receipt.getResultRevision()), operationHash, Long.toString(current.getRevision()), List.of());
        }
        return toResult(current, null, true, true, Long.toString(receipt.getResultRevision()), operationHash,
            Long.toString(current.getRevision()), List.of());
    }

    private void rebuildDraftReferences(long actorId, long projectId, long draftId, TimelineDocumentDTO timeline) {
        assetRefMapper.delete(new LambdaQueryWrapper<TimelineAssetRef>()
            .eq(TimelineAssetRef::getOwnerUserId, actorId)
            .eq(TimelineAssetRef::getDocumentType, "draft")
            .eq(TimelineAssetRef::getDocumentId, draftId));
        for (ReferenceInput input : referenceInputs(timeline)) {
            TimelineAssetRef ref = new TimelineAssetRef();
            ref.setTimelineAssetRefId(IdWorker.getId());
            ref.setOwnerUserId(actorId);
            ref.setProjectId(projectId);
            ref.setDocumentType("draft");
            ref.setDocumentId(draftId);
            ref.setElementId(input.elementId());
            ref.setAssetId(parsePositiveId(input.assetId(), "时间轴素材无效"));
            ref.setUsageType(input.usageType().value());
            ref.setStartMs(input.startMs());
            ref.setEndMs(input.endMs());
            ref.setActorType(APP_USER);
            ref.setActorId(actorId);
            ref.setCreateBy(actorId);
            ref.setUpdateBy(actorId);
            if (assetRefMapper.insert(ref) != 1) {
                throw documentInvalid("草稿素材引用创建失败");
            }
        }
    }

    private List<ReferenceInput> referenceInputs(TimelineDocumentDTO timeline) {
        List<ReferenceInput> refs = new ArrayList<>();
        for (TimelineTrackDTO track : timeline.tracks()) {
            for (TimelineElementDTO element : track.elements()) {
                if (element instanceof TimelineMainVideoElementDTO main) {
                    refs.add(new ReferenceInput(main.elementId(), main.assetId(), TimelineAssetUsageType.BASE_VIDEO,
                        main.startMs(), main.endMs()));
                } else if (element instanceof TimelineImageElementDTO image) {
                    refs.add(new ReferenceInput(image.elementId(), image.assetId(), TimelineAssetUsageType.IMAGE,
                        image.startMs(), image.endMs()));
                } else if (element instanceof TimelinePipVideoElementDTO pip) {
                    refs.add(new ReferenceInput(pip.elementId(), pip.assetId(), TimelineAssetUsageType.PIP_VIDEO,
                        pip.startMs(), pip.endMs()));
                } else if (element instanceof TimelineAudioElementDTO audio) {
                    refs.add(new ReferenceInput(audio.elementId(), audio.assetId(), audio.usageType(),
                        audio.startMs(), audio.endMs()));
                }
            }
        }
        return List.copyOf(refs);
    }

    private SaveSpec validateCommand(SaveTimelineDraftCommand command) {
        if (command == null || !TimelineContractLimits.SCHEMA_VERSION.equals(command.schemaVersion())
            || !IDEMPOTENCY_KEY.matcher(command.idempotencyKey() == null ? "" : command.idempotencyKey()).matches()
            || command.timeline() == null) {
            throw documentInvalid("草稿保存请求无效");
        }
        return new SaveSpec(command.idempotencyKey(), parsePositiveId(command.expectedRevision(), "草稿修订无效"));
    }

    private CreationProject requireProject(long actorId, String projectId) {
        return requireProject(actorId, parsePositiveId(projectId, "项目不存在"));
    }

    private CreationProject requireProject(long actorId, long projectId) {
        if (actorId <= 0) {
            throw projectNotFound();
        }
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, projectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null) {
            throw projectNotFound();
        }
        if ("archived".equals(project.getProjectStatus())) {
            throw new ServiceException("项目已归档", TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT);
        }
        return project;
    }

    private TimelineDraft requireDraft(long actorId, long projectId) {
        TimelineDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<TimelineDraft>()
            .eq(TimelineDraft::getOwnerUserId, actorId)
            .eq(TimelineDraft::getProjectId, projectId)
            .eq(TimelineDraft::getDelFlag, "0"));
        if (draft == null) {
            throw projectNotFound();
        }
        return draft;
    }

    private TimelineWriteReceipt findReceipt(long actorId, long projectId, String idempotencyKey) {
        return receiptMapper.selectOne(new LambdaQueryWrapper<TimelineWriteReceipt>()
            .eq(TimelineWriteReceipt::getOwnerUserId, actorId)
            .eq(TimelineWriteReceipt::getProjectId, projectId)
            .eq(TimelineWriteReceipt::getOperationType, DRAFT_SAVE)
            .eq(TimelineWriteReceipt::getIdempotencyKey, idempotencyKey));
    }

    private ITimelineDocumentService.ProjectContext context(CreationProject project) {
        return new ITimelineDocumentService.ProjectContext(project.getProjectId().toString(),
            project.getBaseVideoAssetId().toString(), project.getPrimaryAudioAssetId() == null ? null
                : project.getPrimaryAudioAssetId().toString(), project.getScriptTextSnapshot(), project.getDurationMs(),
            project.getCanvasWidth(), project.getCanvasHeight(), project.getFrameRate());
    }

    private TimelineDraftView toView(TimelineDraft draft, TimelineDocumentDTO timeline) {
        return new TimelineDraftView(Long.toString(draft.getProjectId()), Long.toString(draft.getTimelineDraftId()),
            Long.toString(draft.getRevision()), draft.getSchemaVersion(), draft.getContentHash(), timeline,
            draft.getUpdateTime() == null ? null : draft.getUpdateTime().toInstant(ZoneOffset.UTC));
    }

    private TimelineWriteResult toResult(TimelineDraft draft, TimelineDocumentDTO timeline, boolean replayed,
                                         boolean superseded, String operationRevision, String operationContentHash,
                                         String currentRevision,
                                         List<org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO> changes) {
        TimelineDraftView view = toView(draft, timeline);
        return new TimelineWriteResult(view.projectId(), view.timelineDraftId(), view.revision(), view.schemaVersion(),
            view.contentHash(), view.timeline(), view.savedAt(), replayed, superseded, operationRevision,
            operationContentHash, currentRevision, changes);
    }

    private TimelineDocumentDTO readTimeline(String contentJson) {
        try {
            return jsonMapper.readerFor(TimelineDocumentDTO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(contentJson);
        } catch (Exception exception) {
            throw documentInvalid("草稿内容损坏");
        }
    }

    private String summaryJson(long resultRevision, String contentHash) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resultRevision", resultRevision);
        summary.put("contentHash", contentHash);
        return jsonMapper.writeValueAsString(summary);
    }

    private String summaryContentHash(String summaryJson) {
        try {
            JsonNode summary = jsonMapper.readTree(summaryJson);
            String value = summary == null ? null : summary.path("contentHash").textValue();
            return value != null && value.matches("[0-9a-f]{64}") ? value : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String requestDigest(long projectId, long expectedRevision, String contentHash) {
        return sha256(DRAFT_SAVE + "\n" + projectId + "\n" + expectedRevision + "\n"
            + TimelineContractLimits.SCHEMA_VERSION + "\n" + contentHash);
    }

    private long parsePositiveId(String value, String safeMessage) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            throw documentInvalid(safeMessage);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw documentInvalid(safeMessage);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ServiceException projectNotFound() {
        return new ServiceException("创作项目不存在", TimelineErrorCodes.CREATION_PROJECT_NOT_FOUND);
    }

    private ServiceException revisionConflict() {
        return new ServiceException("草稿修订冲突", TimelineErrorCodes.TIMELINE_REVISION_CONFLICT);
    }

    private ServiceException idempotencyConflict() {
        return new ServiceException("幂等键已用于不同的时间轴请求", TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT);
    }

    private ServiceException documentInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    private record SaveSpec(String idempotencyKey, long expectedRevision) {
    }

    private record ReferenceInput(String elementId, String assetId, TimelineAssetUsageType usageType,
                                  long startMs, long endMs) {
    }
}
