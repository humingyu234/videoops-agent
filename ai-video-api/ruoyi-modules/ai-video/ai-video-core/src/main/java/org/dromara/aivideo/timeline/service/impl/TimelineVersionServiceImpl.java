package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
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
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.mapper.TimelineWriteReceiptMapper;
import org.dromara.aivideo.timeline.service.ITimelineDocumentService;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.aivideo.timeline.service.ITimelineVersionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Appends immutable versions and restores them through a guarded current-draft projection. */
@Service
public class TimelineVersionServiceImpl implements ITimelineVersionService {

    private static final String APP_USER = "app_user";
    private static final String MANUAL_VERSION = "manual_version";
    private static final String CONFLICT_VERSION = "conflict_version";
    private static final String VERSION_RESTORE = "version_restore";
    private static final String MANUAL_SAVE = "manual_save";
    private static final String CONFLICT_COPY = "conflict_copy";
    private static final String RESTORED = "restored";
    private static final String DRAFT = "draft";
    private static final String VERSION = "version";
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final int WRITE_RETRIES = 2;

    private final CreationProjectMapper projectMapper;
    private final TimelineDraftMapper draftMapper;
    private final TimelineVersionMapper versionMapper;
    private final TimelineAssetRefMapper assetRefMapper;
    private final TimelineWriteReceiptMapper receiptMapper;
    private final ITimelineDocumentService documentService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    TimelineVersionServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                               TimelineVersionMapper versionMapper, TimelineAssetRefMapper assetRefMapper,
                               TimelineWriteReceiptMapper receiptMapper, ITimelineDocumentService documentService,
                               JsonMapper jsonMapper) {
        this(projectMapper, draftMapper, versionMapper, assetRefMapper, receiptMapper, documentService, jsonMapper,
            (TransactionTemplate) null);
    }

    @Autowired
    public TimelineVersionServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                      TimelineVersionMapper versionMapper, TimelineAssetRefMapper assetRefMapper,
                                      TimelineWriteReceiptMapper receiptMapper, ITimelineDocumentService documentService,
                                      JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
        this(projectMapper, draftMapper, versionMapper, assetRefMapper, receiptMapper, documentService, jsonMapper,
            new TransactionTemplate(transactionManager));
    }

    private TimelineVersionServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                       TimelineVersionMapper versionMapper, TimelineAssetRefMapper assetRefMapper,
                                       TimelineWriteReceiptMapper receiptMapper, ITimelineDocumentService documentService,
                                       JsonMapper jsonMapper, TransactionTemplate transactionTemplate) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.assetRefMapper = Objects.requireNonNull(assetRefMapper, "assetRefMapper");
        this.receiptMapper = Objects.requireNonNull(receiptMapper, "receiptMapper");
        this.documentService = Objects.requireNonNull(documentService, "documentService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public TimelineVersionView createManualVersion(long actorId, String projectId,
                                                   CreateManualVersionCommand command) {
        CreationProject project = requireProject(actorId, projectId);
        TimelineDraft draft = requireDraft(actorId, project.getProjectId());
        VersionCommandSpec spec = manualSpec(command);
        String requestDigest = requestDigest(MANUAL_VERSION, project.getProjectId(), spec.expectedRevision(), null);
        TimelineWriteReceipt existing = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
        if (existing != null) {
            return replayVersion(actorId, project.getProjectId(), existing, MANUAL_VERSION, requestDigest);
        }
        if (!Objects.equals(draft.getRevision(), spec.expectedRevision())) {
            throw revisionConflict();
        }
        return writeVersionWithReplay(actorId, project, spec.idempotencyKey(), spec.expectedRevision(), MANUAL_VERSION,
            requestDigest, () -> {
                TimelineDraft current = requireDraft(actorId, project.getProjectId());
                if (!Objects.equals(current.getRevision(), spec.expectedRevision())) {
                    throw revisionConflict();
                }
                TimelineDocumentDTO timeline = readTimeline(current.getContentJson());
                return appendVersion(actorId, project.getProjectId(), current.getRevision(), MANUAL_SAVE,
                    spec.idempotencyKey(), requestDigest, current.getContentJson(), current.getContentHash(),
                    current.getDurationMs(), null, timeline, MANUAL_VERSION, spec.expectedRevision(), current.getRevision());
            });
    }

    @Override
    public TimelineVersionView createConflictCopy(long actorId, String projectId, CreateConflictCopyCommand command) {
        CreationProject project = requireProject(actorId, projectId);
        VersionCommandSpec spec = conflictSpec(command);
        ITimelineDocumentService.ValidatedTimeline validated = documentService.validate(actorId, context(project),
            command.timeline());
        String requestDigest = requestDigest(CONFLICT_VERSION, project.getProjectId(), spec.expectedRevision(),
            validated.contentHash());
        TimelineWriteReceipt existing = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
        if (existing != null) {
            return replayVersion(actorId, project.getProjectId(), existing, CONFLICT_VERSION, requestDigest);
        }
        return writeVersionWithReplay(actorId, project, spec.idempotencyKey(), spec.expectedRevision(), CONFLICT_VERSION,
            requestDigest, () -> appendVersion(actorId, project.getProjectId(), spec.expectedRevision(), CONFLICT_COPY,
                spec.idempotencyKey(), requestDigest, validated.canonicalJson(), validated.contentHash(),
                validated.timeline().canvas().durationMs(), null, validated.timeline(), CONFLICT_VERSION,
                spec.expectedRevision(), spec.expectedRevision()));
    }

    @Override
    public ITimelineDraftService.TimelineWriteResult restoreVersion(long actorId, String projectId, String versionId,
                                                                     RestoreTimelineVersionCommand command) {
        CreationProject project = requireProject(actorId, projectId);
        TimelineDraft draft = requireDraft(actorId, project.getProjectId());
        long parsedVersionId = parsePositiveId(versionId, "时间轴版本不存在");
        VersionCommandSpec spec = restoreSpec(command);
        String requestDigest = requestDigest(VERSION_RESTORE, project.getProjectId(), spec.expectedRevision(),
            Long.toString(parsedVersionId));
        TimelineWriteReceipt existing = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
        if (existing != null) {
            return replayRestore(actorId, project.getProjectId(), existing, requestDigest);
        }
        TimelineVersion source = requireVersion(actorId, project.getProjectId(), parsedVersionId);
        if (!Objects.equals(draft.getRevision(), spec.expectedRevision())) {
            throw revisionConflict();
        }
        ITimelineDocumentService.ValidatedTimeline validated = documentService.validate(actorId, context(project),
            readRawTimeline(source.getContentJson()));
        return writeRestoreWithReplay(actorId, project, source, spec, requestDigest, validated);
    }

    @Override
    public List<TimelineVersionView> listOwnedVersions(long actorId, String projectId) {
        CreationProject project = requireProject(actorId, projectId);
        List<TimelineVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getProjectId, project.getProjectId())
            .orderByDesc(TimelineVersion::getVersionNo));
        return versions == null ? List.of() : versions.stream().map(version -> toView(version, false)).toList();
    }

    @Override
    public PageResult<TimelineVersionView> pageOwnedVersions(long actorId, String projectId, PageQuery pageQuery) {
        CreationProject project = requireProject(actorId, projectId);
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 20 : pageQuery.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw documentInvalid("分页参数无效");
        }
        Page<TimelineVersion> page = versionMapper.selectPage(new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<TimelineVersion>()
                .eq(TimelineVersion::getOwnerUserId, actorId)
                .eq(TimelineVersion::getProjectId, project.getProjectId())
                .orderByDesc(TimelineVersion::getVersionNo)
                .orderByDesc(TimelineVersion::getTimelineVersionId));
        List<TimelineVersion> records = page.getRecords() == null ? List.of() : page.getRecords();
        return PageResult.build(records.stream().map(version -> toView(version, false)).toList(), page.getTotal());
    }

    private TimelineVersionView writeVersionWithReplay(long actorId, CreationProject project, String idempotencyKey,
                                                        long expectedRevision, String operationType,
                                                        String requestDigest, Supplier<TimelineVersionView> write) {
        for (int attempt = 0; attempt < WRITE_RETRIES; attempt++) {
            try {
                return inShortTransaction(write);
            } catch (DuplicateKeyException exception) {
                TimelineWriteReceipt winner = findReceipt(actorId, project.getProjectId(), idempotencyKey);
                if (winner != null) {
                    return replayVersion(actorId, project.getProjectId(), winner, operationType, requestDigest);
                }
                if (attempt + 1 == WRITE_RETRIES) {
                    throw documentInvalid("时间轴版本写入冲突");
                }
            }
        }
        throw documentInvalid("时间轴版本写入失败");
    }

    private ITimelineDraftService.TimelineWriteResult writeRestoreWithReplay(long actorId, CreationProject project,
                                                                               TimelineVersion source,
                                                                               VersionCommandSpec spec,
                                                                               String requestDigest,
                                                                               ITimelineDocumentService.ValidatedTimeline validated) {
        for (int attempt = 0; attempt < WRITE_RETRIES; attempt++) {
            try {
                return inShortTransaction(() -> restoreInTransaction(actorId, project, source, spec, requestDigest,
                    validated));
            } catch (DuplicateKeyException exception) {
                TimelineWriteReceipt winner = findReceipt(actorId, project.getProjectId(), spec.idempotencyKey());
                if (winner != null) {
                    return replayRestore(actorId, project.getProjectId(), winner, requestDigest);
                }
                if (attempt + 1 == WRITE_RETRIES) {
                    throw documentInvalid("时间轴恢复写入冲突");
                }
            }
        }
        throw documentInvalid("时间轴恢复失败");
    }

    private TimelineVersionView appendVersion(long actorId, long projectId, long sourceDraftRevision,
                                              String versionReason, String idempotencyKey, String requestDigest,
                                              String contentJson, String contentHash, Long durationMs,
                                              Long sourceVersionId, TimelineDocumentDTO timeline, String operationType,
                                              long expectedRevision, Long resultRevision) {
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(IdWorker.getId());
        version.setOwnerUserId(actorId);
        version.setProjectId(projectId);
        version.setVersionNo(nextVersionNo(actorId, projectId));
        version.setSourceDraftRevision(sourceDraftRevision);
        version.setVersionReason(versionReason);
        version.setIdempotencyKey(idempotencyKey);
        version.setRequestDigest(requestDigest);
        version.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        version.setContentJson(contentJson);
        version.setContentHash(contentHash);
        version.setDurationMs(durationMs);
        version.setSourceVersionId(sourceVersionId);
        version.setActorType(APP_USER);
        version.setActorId(actorId);
        version.setCreateBy(actorId);
        version.setUpdateBy(actorId);
        if (versionMapper.insert(version) != 1) {
            throw documentInvalid("时间轴版本创建失败");
        }
        writeReferences(actorId, projectId, VERSION, version.getTimelineVersionId(), timeline);
        insertReceipt(actorId, projectId, operationType, idempotencyKey, requestDigest, expectedRevision,
            resultRevision, version.getTimelineVersionId(), contentHash);
        return toView(version, false);
    }

    private ITimelineDraftService.TimelineWriteResult restoreInTransaction(long actorId, CreationProject project,
                                                                             TimelineVersion source,
                                                                             VersionCommandSpec spec,
                                                                             String requestDigest,
                                                                             ITimelineDocumentService.ValidatedTimeline validated) {
        TimelineDraft current = requireDraft(actorId, project.getProjectId());
        if (!Objects.equals(current.getRevision(), spec.expectedRevision())) {
            throw revisionConflict();
        }
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
        TimelineVersion restored = new TimelineVersion();
        restored.setTimelineVersionId(IdWorker.getId());
        restored.setOwnerUserId(actorId);
        restored.setProjectId(project.getProjectId());
        restored.setVersionNo(nextVersionNo(actorId, project.getProjectId()));
        restored.setSourceDraftRevision(next.getRevision());
        restored.setVersionReason(RESTORED);
        restored.setIdempotencyKey(spec.idempotencyKey());
        restored.setRequestDigest(requestDigest);
        restored.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        restored.setContentJson(next.getContentJson());
        restored.setContentHash(next.getContentHash());
        restored.setDurationMs(next.getDurationMs());
        restored.setSourceVersionId(source.getTimelineVersionId());
        restored.setActorType(APP_USER);
        restored.setActorId(actorId);
        restored.setCreateBy(actorId);
        restored.setUpdateBy(actorId);
        if (versionMapper.insert(restored) != 1) {
            throw documentInvalid("恢复版本创建失败");
        }
        writeReferences(actorId, project.getProjectId(), VERSION, restored.getTimelineVersionId(), validated.timeline());
        insertReceipt(actorId, project.getProjectId(), VERSION_RESTORE, spec.idempotencyKey(), requestDigest,
            spec.expectedRevision(), next.getRevision(), restored.getTimelineVersionId(), next.getContentHash());
        return toDraftResult(next, validated.timeline(), false, false, null, null, null,
            validated.normalizationChanges());
    }

    private TimelineVersionView replayVersion(long actorId, long projectId, TimelineWriteReceipt receipt,
                                              String expectedOperation, String requestDigest) {
        if (!expectedOperation.equals(receipt.getOperationType()) || !requestDigest.equals(receipt.getRequestDigest())
            || receipt.getResultVersionId() == null) {
            throw idempotencyConflict();
        }
        TimelineVersion version = versionMapper.selectOne(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getTimelineVersionId, receipt.getResultVersionId())
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getProjectId, projectId));
        if (version == null) {
            throw documentInvalid("时间轴版本回执损坏");
        }
        return toView(version, true);
    }

    private ITimelineDraftService.TimelineWriteResult replayRestore(long actorId, long projectId,
                                                                     TimelineWriteReceipt receipt,
                                                                     String requestDigest) {
        if (!VERSION_RESTORE.equals(receipt.getOperationType()) || !requestDigest.equals(receipt.getRequestDigest())
            || receipt.getResultRevision() == null) {
            throw idempotencyConflict();
        }
        TimelineDraft current = requireDraft(actorId, projectId);
        String operationHash = summaryContentHash(receipt.getResponseSummaryJson());
        if (operationHash == null) {
            throw documentInvalid("时间轴恢复回执损坏");
        }
        if (Objects.equals(current.getRevision(), receipt.getResultRevision())
            && operationHash.equals(current.getContentHash())) {
            return toDraftResult(current, readTimeline(current.getContentJson()), true, false,
                Long.toString(receipt.getResultRevision()), operationHash, Long.toString(current.getRevision()), List.of());
        }
        return toDraftResult(current, null, true, true, Long.toString(receipt.getResultRevision()), operationHash,
            Long.toString(current.getRevision()), List.of());
    }

    private void rebuildDraftReferences(long actorId, long projectId, long draftId, TimelineDocumentDTO timeline) {
        assetRefMapper.delete(new LambdaQueryWrapper<TimelineAssetRef>()
            .eq(TimelineAssetRef::getOwnerUserId, actorId)
            .eq(TimelineAssetRef::getProjectId, projectId)
            .eq(TimelineAssetRef::getDocumentType, DRAFT)
            .eq(TimelineAssetRef::getDocumentId, draftId));
        writeReferences(actorId, projectId, DRAFT, draftId, timeline);
    }

    private void writeReferences(long actorId, long projectId, String documentType, long documentId,
                                 TimelineDocumentDTO timeline) {
        for (ReferenceInput input : referenceInputs(timeline)) {
            TimelineAssetRef ref = new TimelineAssetRef();
            ref.setTimelineAssetRefId(IdWorker.getId());
            ref.setOwnerUserId(actorId);
            ref.setProjectId(projectId);
            ref.setDocumentType(documentType);
            ref.setDocumentId(documentId);
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
                throw documentInvalid("时间轴素材引用创建失败");
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

    private long nextVersionNo(long actorId, long projectId) {
        List<TimelineVersion> existing = versionMapper.selectList(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getProjectId, projectId)
            .orderByDesc(TimelineVersion::getVersionNo));
        return existing == null ? 1L : existing.stream().map(TimelineVersion::getVersionNo)
            .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0L) + 1L;
    }

    private void insertReceipt(long actorId, long projectId, String operationType, String idempotencyKey,
                               String requestDigest, long expectedRevision, Long resultRevision, long resultVersionId,
                               String contentHash) {
        TimelineWriteReceipt receipt = new TimelineWriteReceipt();
        receipt.setTimelineWriteReceiptId(IdWorker.getId());
        receipt.setOwnerUserId(actorId);
        receipt.setProjectId(projectId);
        receipt.setOperationType(operationType);
        receipt.setIdempotencyKey(idempotencyKey);
        receipt.setRequestDigest(requestDigest);
        receipt.setExpectedRevision(expectedRevision);
        receipt.setResultRevision(resultRevision);
        receipt.setResultVersionId(resultVersionId);
        receipt.setResponseSummaryJson(summaryJson(resultRevision, resultVersionId, contentHash));
        receipt.setActorType(APP_USER);
        receipt.setActorId(actorId);
        receipt.setCreateBy(actorId);
        receipt.setUpdateBy(actorId);
        if (receiptMapper.insert(receipt) != 1) {
            throw documentInvalid("时间轴写回执创建失败");
        }
    }

    private VersionCommandSpec manualSpec(CreateManualVersionCommand command) {
        if (command == null) {
            throw documentInvalid("手动版本请求无效");
        }
        return versionSpec(command.idempotencyKey(), command.expectedRevision());
    }

    private VersionCommandSpec conflictSpec(CreateConflictCopyCommand command) {
        if (command == null || !TimelineContractLimits.SCHEMA_VERSION.equals(command.schemaVersion())
            || command.timeline() == null) {
            throw documentInvalid("冲突副本请求无效");
        }
        return versionSpec(command.idempotencyKey(), command.baseRevision());
    }

    private VersionCommandSpec restoreSpec(RestoreTimelineVersionCommand command) {
        if (command == null) {
            throw documentInvalid("恢复版本请求无效");
        }
        return versionSpec(command.idempotencyKey(), command.expectedRevision());
    }

    private VersionCommandSpec versionSpec(String idempotencyKey, String expectedRevision) {
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey == null ? "" : idempotencyKey).matches()) {
            throw documentInvalid("幂等键无效");
        }
        return new VersionCommandSpec(idempotencyKey, parsePositiveId(expectedRevision, "时间轴修订无效"));
    }

    private CreationProject requireProject(long actorId, String projectId) {
        if (actorId <= 0) {
            throw projectNotFound();
        }
        long parsedProjectId = parsePositiveId(projectId, "创作项目不存在");
        CreationProject project = projectMapper.selectOne(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getProjectId, parsedProjectId)
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0"));
        if (project == null) {
            throw projectNotFound();
        }
        if ("archived".equals(project.getProjectStatus())) {
            throw new ServiceException("创作项目已归档", TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT);
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

    private TimelineVersion requireVersion(long actorId, long projectId, long versionId) {
        TimelineVersion version = versionMapper.selectOne(new LambdaQueryWrapper<TimelineVersion>()
            .eq(TimelineVersion::getTimelineVersionId, versionId)
            .eq(TimelineVersion::getOwnerUserId, actorId)
            .eq(TimelineVersion::getProjectId, projectId));
        if (version == null) {
            throw new ServiceException("时间轴版本不存在", TimelineErrorCodes.TIMELINE_VERSION_NOT_FOUND);
        }
        return version;
    }

    private TimelineWriteReceipt findReceipt(long actorId, long projectId, String idempotencyKey) {
        return receiptMapper.selectOne(new LambdaQueryWrapper<TimelineWriteReceipt>()
            .eq(TimelineWriteReceipt::getOwnerUserId, actorId)
            .eq(TimelineWriteReceipt::getProjectId, projectId)
            .eq(TimelineWriteReceipt::getIdempotencyKey, idempotencyKey));
    }

    private ITimelineDocumentService.ProjectContext context(CreationProject project) {
        return new ITimelineDocumentService.ProjectContext(project.getProjectId().toString(),
            project.getBaseVideoAssetId().toString(), project.getPrimaryAudioAssetId() == null ? null
                : project.getPrimaryAudioAssetId().toString(), project.getScriptTextSnapshot(), project.getDurationMs(),
            project.getCanvasWidth(), project.getCanvasHeight(), project.getFrameRate());
    }

    private TimelineDocumentDTO readTimeline(String contentJson) {
        try {
            return jsonMapper.readerFor(TimelineDocumentDTO.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(contentJson);
        } catch (Exception exception) {
            throw documentInvalid("时间轴内容损坏");
        }
    }

    private JsonNode readRawTimeline(String contentJson) {
        try {
            JsonNode timeline = jsonMapper.readTree(contentJson);
            if (timeline == null || timeline.isNull()) {
                throw documentInvalid("时间轴内容损坏");
            }
            return timeline;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw documentInvalid("时间轴内容损坏");
        }
    }

    private TimelineVersionView toView(TimelineVersion version, boolean replayed) {
        return new TimelineVersionView(Long.toString(version.getTimelineVersionId()), Long.toString(version.getProjectId()),
            Long.toString(version.getVersionNo()), Long.toString(version.getSourceDraftRevision()),
            version.getSchemaVersion(), version.getContentHash(), version.getVersionReason(),
            version.getSourceVersionId() == null ? null : Long.toString(version.getSourceVersionId()),
            version.getCreateTime() == null ? null : version.getCreateTime().toInstant(ZoneOffset.UTC), replayed);
    }

    private ITimelineDraftService.TimelineWriteResult toDraftResult(TimelineDraft draft, TimelineDocumentDTO timeline,
                                                                     boolean replayed, boolean superseded,
                                                                     String operationRevision,
                                                                     String operationContentHash,
                                                                     String currentRevision,
                                                                     List<org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO>
                                                                         changes) {
        return new ITimelineDraftService.TimelineWriteResult(Long.toString(draft.getProjectId()),
            Long.toString(draft.getTimelineDraftId()), Long.toString(draft.getRevision()), draft.getSchemaVersion(),
            draft.getContentHash(), timeline, draft.getUpdateTime() == null ? null
                : draft.getUpdateTime().toInstant(ZoneOffset.UTC), replayed, superseded, operationRevision,
            operationContentHash, currentRevision, changes);
    }

    private String summaryJson(Long resultRevision, long resultVersionId, String contentHash) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("resultRevision", resultRevision);
        summary.put("resultVersionId", resultVersionId);
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

    private String requestDigest(String operationType, long projectId, long expectedRevision, String contentIdentity) {
        return sha256(operationType + "\n" + projectId + "\n" + expectedRevision + "\n"
            + TimelineContractLimits.SCHEMA_VERSION + "\n" + (contentIdentity == null ? "" : contentIdentity));
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

    private <T> T inShortTransaction(Supplier<T> work) {
        if (transactionTemplate == null) {
            return work.get();
        }
        T result = transactionTemplate.execute(status -> work.get());
        if (result == null) {
            throw documentInvalid("时间轴事务失败");
        }
        return result;
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
        return new ServiceException("时间轴修订冲突", TimelineErrorCodes.TIMELINE_REVISION_CONFLICT);
    }

    private ServiceException idempotencyConflict() {
        return new ServiceException("幂等键已用于不同的时间轴请求", TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT);
    }

    private ServiceException documentInvalid(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
    }

    private record VersionCommandSpec(String idempotencyKey, long expectedRevision) {
    }

    private record ReferenceInput(String elementId, String assetId, TimelineAssetUsageType usageType,
                                  long startMs, long endMs) {
    }
}
