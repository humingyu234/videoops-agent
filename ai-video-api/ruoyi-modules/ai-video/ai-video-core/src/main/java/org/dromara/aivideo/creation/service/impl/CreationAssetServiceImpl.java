package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.asset.service.VideoOpsObjectKey;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetQueryDTO;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.dto.CreationAssetUploadDTO;
import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RegisterPendingRenderOutputDTO;
import org.dromara.aivideo.creation.dto.RenderOutputFailureDTO;
import org.dromara.aivideo.creation.dto.RenderOutputReadyDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.oss.client.OssClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Owner-scoped creation asset access, including controlled object streaming. */
@Service
public class CreationAssetServiceImpl implements ICreationAssetService {

    private static final int ASSET_NOT_AVAILABLE = TimelineErrorCodes.TIMELINE_ASSET_INVALID;
    private static final int INVALID_RANGE = 416;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern LOWER_HEX_DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern RENDER_STORAGE_KEY = Pattern.compile(
        "^" + Pattern.quote(VideoOpsObjectKey.PREFIX)
            + "/timeline-renders/(\\d+)/(\\d+)/(\\d+)/([0-9a-f]{64})\\.mp4$");
    private static final String PENDING_SHA256 = "0".repeat(64);
    private static final Duration OSS_DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private final CreationAssetMapper assetMapper;
    private final TimelineAssetRefMapper assetRefMapper;
    private final CreationProjectMapper projectMapper;
    private final AiTaskMapper taskMapper;
    private final AiTaskExecutionMapper taskExecutionMapper;
    private final AppUserMapper appUserMapper;
    private final DigitalHumanGenerationJobMapper digitalHumanJobMapper;
    private final IDigitalHumanGenerationService digitalHumanGenerationService;
    private final ITimelineMediaRenderService mediaRenderService;
    private final ObjectProvider<OssClient> ossClientProvider;

    public CreationAssetServiceImpl(CreationAssetMapper assetMapper) {
        this(assetMapper, null, null, null, null, null, null, null,
            (ITimelineMediaRenderService) null, null);
    }

    CreationAssetServiceImpl(CreationAssetMapper assetMapper, ObjectProvider<OssClient> ossClientProvider) {
        this(assetMapper, null, null, null, null, null, null, null,
            (ITimelineMediaRenderService) null, ossClientProvider);
    }

    public CreationAssetServiceImpl(CreationAssetMapper assetMapper, TimelineAssetRefMapper assetRefMapper,
                                    CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                    AiTaskExecutionMapper taskExecutionMapper) {
        this(assetMapper, assetRefMapper, projectMapper, taskMapper, taskExecutionMapper,
            null, null, null, (ITimelineMediaRenderService) null, null);
    }

    CreationAssetServiceImpl(CreationAssetMapper assetMapper, TimelineAssetRefMapper assetRefMapper,
                             CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                             AiTaskExecutionMapper taskExecutionMapper, AppUserMapper appUserMapper,
                              DigitalHumanGenerationJobMapper digitalHumanJobMapper,
                              IDigitalHumanGenerationService digitalHumanGenerationService,
                              ITimelineMediaRenderService mediaRenderService) {
        this(assetMapper, assetRefMapper, projectMapper, taskMapper, taskExecutionMapper, appUserMapper,
            digitalHumanJobMapper, digitalHumanGenerationService, mediaRenderService, null);
    }

    CreationAssetServiceImpl(CreationAssetMapper assetMapper, TimelineAssetRefMapper assetRefMapper,
                             CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                             AiTaskExecutionMapper taskExecutionMapper, AppUserMapper appUserMapper,
                             DigitalHumanGenerationJobMapper digitalHumanJobMapper,
                             IDigitalHumanGenerationService digitalHumanGenerationService,
                             ITimelineMediaRenderService mediaRenderService,
                             ObjectProvider<OssClient> ossClientProvider) {
        this.assetMapper = assetMapper;
        this.assetRefMapper = assetRefMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.taskExecutionMapper = taskExecutionMapper;
        this.appUserMapper = appUserMapper;
        this.digitalHumanJobMapper = digitalHumanJobMapper;
        this.digitalHumanGenerationService = digitalHumanGenerationService;
        this.mediaRenderService = mediaRenderService;
        this.ossClientProvider = ossClientProvider;
    }

    @Autowired
    public CreationAssetServiceImpl(CreationAssetMapper assetMapper, TimelineAssetRefMapper assetRefMapper,
                                    CreationProjectMapper projectMapper, AiTaskMapper taskMapper,
                                     AiTaskExecutionMapper taskExecutionMapper, AppUserMapper appUserMapper,
                                     DigitalHumanGenerationJobMapper digitalHumanJobMapper,
                                     ObjectProvider<IDigitalHumanGenerationService> digitalHumanGenerationServiceProvider,
                                     ObjectProvider<ITimelineMediaRenderService> mediaRenderServiceProvider,
                                     @Qualifier("aiVideoOssClient") ObjectProvider<OssClient> ossClientProvider) {
        this(assetMapper, assetRefMapper, projectMapper, taskMapper, taskExecutionMapper, appUserMapper,
            digitalHumanJobMapper, digitalHumanGenerationServiceProvider.getIfAvailable(),
            mediaRenderServiceProvider.getIfAvailable(), ossClientProvider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreationAssetDTO uploadOwned(long actorId, CreationAssetUploadDTO command, InputStream input) {
        UploadSpec spec = validateUpload(actorId, command, input);
        OssClient client = requireOssClient();
        long generatedId = IdWorker.getId();
        String key = client.buildPathKey("creation-assets/" + actorId,
            generatedId + "-" + safeFileName(command.originalName(), spec.type()));
        String sha256;
        try (DigestInputStream digestInput = new DigestInputStream(input, sha256())) {
            client.upload(key, digestInput, command.contentLength());
            sha256 = HexFormat.of().formatHex(digestInput.getMessageDigest().digest());
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException("创作素材上传失败", ASSET_NOT_AVAILABLE);
        }
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(generatedId);
        asset.setOwnerUserId(actorId);
        asset.setAssetType(spec.type().value());
        asset.setUsageOrigin(CreationAssetUsageOrigin.UPLOAD.value());
        asset.setAssetStatus(CreationAssetStatus.READY.value());
        asset.setStorageKey(key);
        asset.setMimeType(command.contentType().toLowerCase(java.util.Locale.ROOT));
        asset.setSizeBytes(command.contentLength());
        asset.setSha256(sha256);
        asset.setHasVideoStream(spec.type() == CreationAssetType.VIDEO);
        asset.setHasAudioStream(spec.type() == CreationAssetType.AUDIO);
        asset.setIdempotencyKey(command.idempotencyKey());
        asset.setRequestDigest(requestDigest(sha256, command.usageIntent()));
        asset.setActorType("app_user");
        asset.setActorId(actorId);
        asset.setCreateBy(actorId);
        asset.setUpdateBy(actorId);
        asset.setDelFlag("0");
        try {
            if (assetMapper.insert(asset) != 1) {
                throw new ServiceException("创作素材创建失败", ASSET_NOT_AVAILABLE);
            }
        } catch (DuplicateKeyException exception) {
            deleteUploadedObject(client, key);
            CreationAsset replay = assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
                .eq(CreationAsset::getOwnerUserId, actorId)
                .eq(CreationAsset::getIdempotencyKey, command.idempotencyKey())
                .eq(CreationAsset::getDelFlag, "0"));
            if (replay != null && asset.getRequestDigest().equals(replay.getRequestDigest())) {
                return toDto(replay);
            }
            if (replay != null) {
                throw idempotencyConflict();
            }
            throw assetInvalid("创作素材创建失败");
        } catch (RuntimeException exception) {
            deleteUploadedObject(client, key);
            throw exception;
        }
        return toDto(asset);
    }

    @Override
    public PageResult<CreationAssetDTO> pageOwned(long actorId, CreationAssetQueryDTO query, PageQuery pageQuery) {
        if (actorId <= 0) {
            throw new ServiceException("创作素材不可用", ASSET_NOT_AVAILABLE);
        }
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 20 : pageQuery.getPageSize();
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new ServiceException("分页参数无效", ASSET_NOT_AVAILABLE);
        }
        LambdaQueryWrapper<CreationAsset> wrapper = new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getDelFlag, "0")
            .eq(CreationAsset::getAssetStatus, query == null || query.status() == null
                ? CreationAssetStatus.READY.value() : CreationAssetStatus.fromValue(query.status()).value())
            .orderByDesc(CreationAsset::getCreateTime)
            .orderByDesc(CreationAsset::getAssetId);
        if (query != null && query.assetType() != null && !query.assetType().isBlank()) {
            wrapper.eq(CreationAsset::getAssetType, CreationAssetType.fromValue(query.assetType()).value());
        }
        Page<CreationAsset> page = assetMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toDto).toList(), page.getTotal());
    }

    @Override
    public CreationAssetDTO getOwned(long actorId, String assetId) {
        return toDto(requireOwnedReady(actorId, assetId));
    }

    @Override
    public CreationAssetResolveDTO resolveOwned(long actorId, String assetId, TimelineAssetUsageType usageType) {
        CreationAsset asset = requireOwnedReady(actorId, assetId);
        requireCompatible(asset, usageType);
        return toResolve(asset, usageType);
    }

    @Override
    public CreationMediaHandle openOwnedMedia(long actorId, String assetId, TimelineAssetUsageType usageType) {
        CreationAsset asset = requireOwnedReady(actorId, assetId);
        requireCompatible(asset, usageType);
        return open(asset, usageType, 0, asset.getSizeBytes(), null);
    }

    @Override
    public CreationMediaHandle openOwnedMediaRange(long actorId, String assetId, String singleRangeHeader) {
        CreationAsset asset = requireOwnedReady(actorId, assetId);
        ByteRange range = parseSingleRange(singleRangeHeader, asset.getSizeBytes());
        return open(asset, null, range.offset(), range.length(), range.header());
    }

    @Override
    public DigitalHumanCreationSourceDTO resolveDigitalHumanSource(long actorId, String sourceId) {
        requireDigitalHumanSourceDependencies();
        if (actorId <= 0) {
            throw assetInvalid("Digital human source is unavailable");
        }
        long videoJobId = parsePositiveId(sourceId);
        AppUser user = appUserMapper.selectById(actorId);
        if (user == null || user.getPersonalTenantId() == null
            || user.getPersonalTenantId() <= 0) {
            throw assetInvalid("Digital human source is unavailable");
        }
        DigitalHumanOwnerDTO owner = new DigitalHumanOwnerDTO(user.getPersonalTenantId(), actorId);
        DigitalHumanJobDTO videoSummary = requireSucceededVideoSummary(videoJobId, owner);
        DigitalHumanGenerationJob videoJob = requireOwnedDigitalHumanJob(videoJobId, owner);
        long voiceJobId = requireParentVoiceJobId(videoJob, videoSummary);
        DigitalHumanGenerationJob voiceJob = requireOwnedDigitalHumanJob(voiceJobId, owner);
        requireSucceededVoiceJob(voiceJob);

        DigitalHumanMediaContentDTO videoContent = readLegacyOutput(videoJobId, owner,
            CreationAssetType.VIDEO, videoJob);
        DigitalHumanMediaContentDTO voiceContent = readLegacyOutput(voiceJobId, owner,
            CreationAssetType.AUDIO, voiceJob);
        TimelineMediaProbeDTO probe = probeLegacyVideo(videoJobId, videoContent);
        CreationAsset baseVideo = registerDigitalHumanOutput(actorId, videoJobId, CreationAssetType.VIDEO,
            videoContent, probe);
        CreationAsset primaryAudio = registerDigitalHumanOutput(actorId, voiceJobId, CreationAssetType.AUDIO,
            voiceContent, null);
        return new DigitalHumanCreationSourceDTO(Long.toString(videoJobId),
            Long.toString(baseVideo.getAssetId()), Boolean.TRUE.equals(baseVideo.getHasAudioStream())
                ? null : Long.toString(primaryAudio.getAssetId()), voiceJob.getScriptText(),
            probe.durationMs(), probe.width(), probe.height(), probe.frameRate(), List.of());
    }

    @Override
    public PendingRenderOutputDTO registerPendingRenderOutput(long actorId, RegisterPendingRenderOutputDTO command) {
        if (actorId <= 0 || command == null) {
            throw assetInvalid("创作成品登记参数无效");
        }
        long taskId = parsePositiveId(command.taskId());
        long inputVersionId = parsePositiveId(command.inputVersionId());
        String outputConfigDigest = requireLowerHexDigest(command.outputConfigDigest());
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        String storageKey = renderStorageKey(actorId, taskId, inputVersionId, outputConfigDigest);
        String requestDigest = renderRequestDigest(taskId, inputVersionId, outputConfigDigest);
        CreationAsset existing = findBySource(actorId, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, taskId);
        if (existing != null) {
            return resolvePendingReplay(existing, taskId, inputVersionId, outputConfigDigest,
                storageKey, requestDigest);
        }
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(IdWorker.getId());
        asset.setOwnerUserId(actorId);
        asset.setAssetType(CreationAssetType.VIDEO.value());
        asset.setUsageOrigin(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value());
        asset.setSourceRefId(taskId);
        asset.setAssetStatus(CreationAssetStatus.PENDING.value());
        asset.setStorageKey(storageKey);
        asset.setMimeType("video/mp4");
        asset.setSizeBytes(0L);
        asset.setSha256(PENDING_SHA256);
        asset.setHasVideoStream(true);
        asset.setHasAudioStream(false);
        asset.setIdempotencyKey(idempotencyKey);
        asset.setRequestDigest(requestDigest);
        asset.setActorType("app_user");
        asset.setActorId(actorId);
        asset.setCreateBy(actorId);
        asset.setUpdateBy(actorId);
        asset.setDelFlag("0");
        try {
            if (assetMapper.insert(asset) != 1) {
                throw assetInvalid("创作成品登记失败");
            }
        } catch (DuplicateKeyException exception) {
            existing = findBySource(actorId, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT, taskId);
            if (existing != null) {
                return resolvePendingReplay(existing, taskId, inputVersionId, outputConfigDigest,
                    storageKey, requestDigest);
            }
            CreationAsset idempotencyWinner = findByIdempotency(actorId, idempotencyKey);
            if (idempotencyWinner != null) {
                throw idempotencyConflict();
            }
            throw assetInvalid("创作成品登记失败");
        }
        return toPending(asset, taskId, inputVersionId, outputConfigDigest);
    }

    @Override
    public RenderOutputReadyDTO storePendingRenderContent(long actorId, String assetId, TimelineRenderOutputHandle output) {
        if (actorId <= 0 || output == null) {
            throw assetInvalid("创作成品输出无效");
        }
        CreationAsset asset = requireOwned(actorId, assetId);
        if (!CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value().equals(asset.getUsageOrigin())) {
            throw assetInvalid("创作成品输出无效");
        }
        try (output) {
            TimelineRenderResultDTO metadata = requireRenderMetadata(output.metadata());
            if (CreationAssetStatus.READY.value().equals(asset.getAssetStatus())) {
                if (!metadata.sha256().equals(asset.getSha256()) || metadata.fileSize() != asset.getSizeBytes()) {
                    throw idempotencyConflict();
                }
                return toRenderReady(asset, metadata);
            }
            if (!CreationAssetStatus.PENDING.value().equals(asset.getAssetStatus())) {
                throw assetInvalid("创作成品不处于待处理状态");
            }
            String actualSha256;
            try {
                actualSha256 = ImmutableRenderObjectStore.uploadOrReuse(requireOssClient(), asset.getStorageKey(),
                    requireOutputStream(output), metadata.fileSize(), metadata.sha256());
            } catch (RuntimeException exception) {
                if (exception instanceof ServiceException serviceException) {
                    throw serviceException;
                }
                throw renderUnavailable();
            }
            if (!metadata.sha256().equals(actualSha256)) {
                throw renderUnavailable();
            }
            asset.setAssetStatus(CreationAssetStatus.READY.value());
            asset.setMimeType("video/mp4");
            asset.setSha256(actualSha256);
            asset.setSizeBytes(metadata.fileSize());
            asset.setDurationMs(metadata.durationMs());
            asset.setWidth(metadata.width());
            asset.setHeight(metadata.height());
            asset.setHasVideoStream(true);
            asset.setHasAudioStream(true);
            asset.setUpdateBy(actorId);
            if (assetMapper.updateById(asset) != 1) {
                throw assetInvalid("创作成品状态更新失败");
            }
            return toRenderReady(asset, metadata);
        } catch (IOException exception) {
            throw renderUnavailable();
        }
    }

    @Override
    public void markPendingRenderFailed(long actorId, RenderOutputFailureDTO command) {
        if (actorId <= 0 || command == null) {
            throw assetInvalid("创作成品失败状态无效");
        }
        CreationAsset asset = requireOwned(actorId, command.assetId());
        long taskId = parsePositiveId(command.taskId());
        if (!CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value().equals(asset.getUsageOrigin())
            || !Long.valueOf(taskId).equals(asset.getSourceRefId())) {
            throw assetInvalid("创作成品失败状态无效");
        }
        if (CreationAssetStatus.FAILED.value().equals(asset.getAssetStatus())) {
            return;
        }
        if (!CreationAssetStatus.PENDING.value().equals(asset.getAssetStatus())) {
            throw assetInvalid("创作成品不处于待处理状态");
        }
        asset.setAssetStatus(CreationAssetStatus.FAILED.value());
        asset.setUpdateBy(actorId);
        if (assetMapper.updateById(asset) != 1) {
            throw assetInvalid("创作成品失败状态更新失败");
        }
    }

    @Override
    public void assertAssetDeletable(long actorId, String assetId) {
        CreationAsset asset = requireOwned(actorId, assetId);
        if (assetRefMapper == null || projectMapper == null || taskMapper == null || taskExecutionMapper == null) {
            throw new IllegalStateException("Creation asset reference mappers are not configured");
        }
        long id = asset.getAssetId();
        boolean usedByTimeline = assetRefMapper.selectCount(new LambdaQueryWrapper<TimelineAssetRef>()
            .eq(TimelineAssetRef::getOwnerUserId, actorId)
            .eq(TimelineAssetRef::getAssetId, id)) > 0;
        boolean usedByProject = projectMapper.selectCount(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getOwnerUserId, actorId)
            .eq(CreationProject::getDelFlag, "0")
            .and(wrapper -> wrapper.eq(CreationProject::getBaseVideoAssetId, id)
                .or().eq(CreationProject::getPrimaryAudioAssetId, id)
                .or().eq(CreationProject::getCurrentOutputAssetId, id))) > 0;
        boolean usedByTask = taskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getOwnerUserId, actorId)
            .eq(AiTask::getResultAssetId, id)) > 0;
        boolean usedByExecution = taskExecutionMapper.selectCount(new LambdaQueryWrapper<AiTaskExecution>()
            .eq(AiTaskExecution::getOwnerUserId, actorId)
            .eq(AiTaskExecution::getResultAssetId, id)) > 0;
        if (usedByTimeline || usedByProject || usedByTask || usedByExecution) {
            throw new ServiceException("创作素材正在被引用", ASSET_NOT_AVAILABLE);
        }
    }

    @Override
    public void deleteOwned(long actorId, String assetId) {
        CreationAsset asset = requireOwned(actorId, assetId);
        assertAssetDeletable(actorId, assetId);
        if (!requireOssClient().delete(asset.getStorageKey())) {
            throw new ServiceException("创作素材删除失败", ASSET_NOT_AVAILABLE);
        }
        if (assetMapper.deleteById(asset.getAssetId()) != 1) {
            throw new ServiceException("创作素材删除失败", ASSET_NOT_AVAILABLE);
        }
    }

    @Override
    public List<PendingRenderOutputDTO> findCompensatablePending(Instant olderThan, int limit) {
        if (olderThan == null || limit < 1 || limit > 100) {
            throw assetInvalid("创作成品补偿查询参数无效");
        }
        List<CreationAsset> assets = assetMapper.selectList(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getUsageOrigin, CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value())
            .eq(CreationAsset::getAssetStatus, CreationAssetStatus.PENDING.value())
            .eq(CreationAsset::getDelFlag, "0")
            .lt(CreationAsset::getCreateTime, java.time.LocalDateTime.ofInstant(olderThan, ZoneOffset.UTC))
            .orderByAsc(CreationAsset::getCreateTime)
            .orderByAsc(CreationAsset::getAssetId)
            .last("LIMIT " + limit));
        return assets.stream().map(this::toStoredPending).toList();
    }

    private CreationMediaHandle open(CreationAsset asset, TimelineAssetUsageType usageType,
                                     long offset, long length, String rangeHeader) {
        OssClient client = requireOssClient();
        String bucket = client.config().bucket().filter(value -> !value.isBlank())
            .orElseThrow(() -> new ServiceException("未配置创作素材存储桶", ASSET_NOT_AVAILABLE));
        InputStream stream = client.doCustomBufferedDownload(builder -> {
            builder.bucket(bucket).key(asset.getStorageKey());
            if (rangeHeader != null) {
                builder.range(rangeHeader);
            }
        }, OSS_DOWNLOAD_TIMEOUT);
        return new OssCreationMediaHandle(toResolve(asset, usageType), stream, offset, length, asset.getSizeBytes());
    }

    private CreationAsset requireOwnedReady(long actorId, String assetId) {
        CreationAsset asset = requireOwned(actorId, assetId);
        if (!CreationAssetStatus.READY.value().equals(asset.getAssetStatus())
            || asset.getSizeBytes() == null || asset.getSizeBytes() <= 0 || asset.getStorageKey() == null) {
            throw new ServiceException("创作素材不可用", ASSET_NOT_AVAILABLE);
        }
        return asset;
    }

    private CreationAsset requireOwned(long actorId, String assetId) {
        long parsedAssetId = parsePositiveId(assetId);
        CreationAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getAssetId, parsedAssetId)
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getDelFlag, "0"));
        if (asset == null) {
            throw new ServiceException("创作素材不可用", ASSET_NOT_AVAILABLE);
        }
        return asset;
    }

    private void requireCompatible(CreationAsset asset, TimelineAssetUsageType usageType) {
        if (usageType == null) {
            return;
        }
        CreationAssetType type = CreationAssetType.fromValue(asset.getAssetType());
        boolean compatible = switch (usageType) {
            case BASE_VIDEO, PIP_VIDEO -> type == CreationAssetType.VIDEO;
            case IMAGE -> type == CreationAssetType.IMAGE;
            case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> type == CreationAssetType.AUDIO;
        };
        if (!compatible) {
            throw new ServiceException("创作素材类型不匹配", ASSET_NOT_AVAILABLE);
        }
    }

    private void requireDigitalHumanSourceDependencies() {
        if (appUserMapper == null || digitalHumanJobMapper == null || digitalHumanGenerationService == null
            || mediaRenderService == null) {
            throw renderUnavailable();
        }
    }

    private DigitalHumanJobDTO requireSucceededVideoSummary(long videoJobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanJobDTO summary;
        try {
            summary = digitalHumanGenerationService.getJob(videoJobId, owner);
        } catch (RuntimeException exception) {
            throw assetInvalid("Digital human source is unavailable");
        }
        if (summary == null || !Objects.equals(summary.jobId(), videoJobId)
            || summary.jobType() != DigitalHumanJobType.VIDEO_GENERATE
            || summary.status() != DigitalHumanJobStatus.SUCCEEDED || !summary.outputAvailable()) {
            throw assetInvalid("Digital human source is unavailable");
        }
        return summary;
    }

    private DigitalHumanGenerationJob requireOwnedDigitalHumanJob(long jobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanGenerationJob job = digitalHumanJobMapper.selectOwnedById(jobId, owner.tenantId(), owner.userId());
        if (job == null || !Objects.equals(job.getId(), jobId)
            || !Objects.equals(job.getOwnerUserId(), owner.userId())
            || !Objects.equals(job.getTenantId(), owner.tenantId())) {
            throw assetInvalid("Digital human source is unavailable");
        }
        return job;
    }

    private long requireParentVoiceJobId(DigitalHumanGenerationJob videoJob, DigitalHumanJobDTO videoSummary) {
        Long parentJobId = videoJob.getParentJobId();
        if (parentJobId == null || parentJobId <= 0 || !Objects.equals(parentJobId, videoSummary.parentJobId())) {
            throw assetInvalid("Digital human source is unavailable");
        }
        if (videoJob.getJobType() != DigitalHumanJobType.VIDEO_GENERATE
            || videoJob.getStatus() != DigitalHumanJobStatus.SUCCEEDED) {
            throw assetInvalid("Digital human source is unavailable");
        }
        return parentJobId;
    }

    private void requireSucceededVoiceJob(DigitalHumanGenerationJob voiceJob) {
        if (voiceJob.getJobType() != DigitalHumanJobType.VOICE_GENERATE
            || voiceJob.getStatus() != DigitalHumanJobStatus.SUCCEEDED
            || voiceJob.getScriptText() == null || voiceJob.getScriptText().isBlank()) {
            throw assetInvalid("Digital human source is unavailable");
        }
    }

    private DigitalHumanMediaContentDTO readLegacyOutput(long jobId, DigitalHumanOwnerDTO owner,
                                                          CreationAssetType expectedType,
                                                          DigitalHumanGenerationJob job) {
        DigitalHumanMediaContentDTO media;
        try {
            media = digitalHumanGenerationService.getOutputMedia(jobId, owner);
        } catch (RuntimeException exception) {
            throw assetInvalid("Digital human output cannot be read");
        }
        if (media == null || media.content() == null || media.content().length == 0
            || mediaType(media.mediaType()) != expectedType || job.getOutputMediaKey() == null
            || job.getOutputMediaKey().isBlank() || job.getOutputMediaSize() == null
            || job.getOutputMediaSize() != media.content().length
            || !normalizedMediaType(media.mediaType()).equals(normalizedMediaType(job.getOutputMediaType()))) {
            throw assetInvalid("Digital human output cannot be read");
        }
        String sha256 = HexFormat.of().formatHex(sha256().digest(media.content()));
        if (!sha256.equals(job.getOutputMediaSha256())) {
            throw assetInvalid("Digital human output cannot be read");
        }
        return media;
    }

    private TimelineMediaProbeDTO probeLegacyVideo(long videoJobId, DigitalHumanMediaContentDTO media) {
        CreationAssetResolveDTO metadata = new CreationAssetResolveDTO(Long.toString(videoJobId), media.mediaType(),
            HexFormat.of().formatHex(sha256().digest(media.content())), CreationAssetType.VIDEO,
            TimelineAssetUsageType.BASE_VIDEO, media.content().length, null, null, null, false, false);
        TimelineMediaProbeDTO probe;
        try (CreationMediaHandle handle = new InMemoryCreationMediaHandle(metadata, media.content())) {
            probe = mediaRenderService.probe(handle);
        } catch (IOException | RuntimeException exception) {
            throw renderUnavailable();
        }
        if (probe == null || !probe.videoStream() || probe.durationMs() <= 0
            || probe.fileSize() != media.content().length || probe.width() == null || probe.width() <= 0
            || probe.height() == null || probe.height() <= 0 || probe.frameRate() == null || probe.frameRate() <= 0
            || !CreationAssetType.VIDEO.value().equals(probe.mediaType())) {
            throw renderUnavailable();
        }
        return probe;
    }

    private CreationAsset registerDigitalHumanOutput(long actorId, long sourceRefId, CreationAssetType type,
                                                      DigitalHumanMediaContentDTO media, TimelineMediaProbeDTO probe) {
        String sha256 = HexFormat.of().formatHex(sha256().digest(media.content()));
        CreationAsset existing = findBySource(actorId, CreationAssetUsageOrigin.DIGITAL_HUMAN_OUTPUT, sourceRefId);
        if (existing != null) {
            return requireRegisteredDigitalHumanOutput(existing, type, sha256, media.content().length);
        }
        OssClient client = requireOssClient();
        String key = VideoOpsObjectKey.requireQualified(client.buildPathKey("creation-digital-human/" + actorId,
            sourceRefId + "-" + sha256 + fileExtension(type, media.mediaType())));
        try {
            client.upload(key, new ByteArrayInputStream(media.content()), media.content().length);
        } catch (RuntimeException exception) {
            throw assetInvalid("Digital human output registration failed");
        }
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(IdWorker.getId());
        asset.setOwnerUserId(actorId);
        asset.setAssetType(type.value());
        asset.setUsageOrigin(CreationAssetUsageOrigin.DIGITAL_HUMAN_OUTPUT.value());
        asset.setSourceRefId(sourceRefId);
        asset.setAssetStatus(CreationAssetStatus.READY.value());
        asset.setStorageKey(key);
        asset.setMimeType(normalizedMediaType(media.mediaType()));
        asset.setSizeBytes((long) media.content().length);
        asset.setSha256(sha256);
        asset.setDurationMs(probe == null ? null : probe.durationMs());
        asset.setWidth(probe == null ? null : probe.width());
        asset.setHeight(probe == null ? null : probe.height());
        asset.setHasVideoStream(probe != null && probe.videoStream());
        asset.setHasAudioStream(type == CreationAssetType.AUDIO || (probe != null && probe.audioStream()));
        asset.setIdempotencyKey(UUID.randomUUID().toString());
        asset.setRequestDigest(digestText("digital-human-output\n" + actorId + "\n" + sourceRefId + "\n" + sha256));
        asset.setActorType("app_user");
        asset.setActorId(actorId);
        asset.setCreateBy(actorId);
        asset.setUpdateBy(actorId);
        asset.setDelFlag("0");
        try {
            if (assetMapper.insert(asset) != 1) {
                throw assetInvalid("Digital human output registration failed");
            }
            return asset;
        } catch (DuplicateKeyException exception) {
            CreationAsset winner = findBySource(actorId, CreationAssetUsageOrigin.DIGITAL_HUMAN_OUTPUT, sourceRefId);
            if (winner != null) {
                return requireRegisteredDigitalHumanOutput(winner, type, sha256, media.content().length);
            }
            throw assetInvalid("Digital human output registration failed");
        } catch (RuntimeException exception) {
            if (findBySource(actorId, CreationAssetUsageOrigin.DIGITAL_HUMAN_OUTPUT, sourceRefId) == null) {
                deleteUploadedObject(client, key);
            }
            throw exception;
        }
    }

    private CreationAsset requireRegisteredDigitalHumanOutput(CreationAsset asset, CreationAssetType type,
                                                              String sha256, int sizeBytes) {
        if (!CreationAssetStatus.READY.value().equals(asset.getAssetStatus())
            || !type.value().equals(asset.getAssetType()) || !sha256.equals(asset.getSha256())
            || asset.getSizeBytes() == null || asset.getSizeBytes() != sizeBytes
            || asset.getStorageKey() == null || asset.getStorageKey().isBlank()) {
            throw assetInvalid("Digital human output registration conflict");
        }
        return asset;
    }

    private String fileExtension(CreationAssetType type, String mediaType) {
        if (type == CreationAssetType.VIDEO) {
            return ".mp4";
        }
        return switch (normalizedMediaType(mediaType)) {
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mpeg" -> ".mp3";
            case "audio/mp4" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            default -> ".audio";
        };
    }

    private CreationAssetDTO toDto(CreationAsset asset) {
        return new CreationAssetDTO(Long.toString(asset.getAssetId()), displayName(asset.getStorageKey()), asset.getMimeType(),
            asset.getSha256(), CreationAssetType.fromValue(asset.getAssetType()),
            CreationAssetUsageOrigin.fromValue(asset.getUsageOrigin()),
            CreationAssetStatus.fromValue(asset.getAssetStatus()), asset.getSizeBytes(), asset.getDurationMs(),
            asset.getWidth(), asset.getHeight(), Boolean.TRUE.equals(asset.getHasVideoStream()),
            Boolean.TRUE.equals(asset.getHasAudioStream()), asset.getCreateTime() == null ? null
                : asset.getCreateTime().toInstant(ZoneOffset.UTC));
    }

    private UploadSpec validateUpload(long actorId, CreationAssetUploadDTO command, InputStream input) {
        if (actorId <= 0 || command == null || input == null || command.contentLength() <= 0
            || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new ServiceException("创作素材上传参数无效", ASSET_NOT_AVAILABLE);
        }
        CreationAssetType type = mediaType(command.contentType());
        TimelineAssetUsageType usage;
        try {
            usage = TimelineAssetUsageType.fromValue(command.usageIntent());
        } catch (RuntimeException exception) {
            throw new ServiceException("创作素材用途无效", ASSET_NOT_AVAILABLE);
        }
        boolean compatible = switch (usage) {
            case BASE_VIDEO, PIP_VIDEO -> type == CreationAssetType.VIDEO;
            case IMAGE -> type == CreationAssetType.IMAGE;
            case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> type == CreationAssetType.AUDIO;
        };
        if (!compatible) {
            throw new ServiceException("创作素材类型不匹配", ASSET_NOT_AVAILABLE);
        }
        return new UploadSpec(type, usage);
    }

    private CreationAssetType mediaType(String contentType) {
        if (contentType == null) {
            throw new ServiceException("不支持的创作素材类型", ASSET_NOT_AVAILABLE);
        }
        return switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
            case "image/jpeg", "image/png", "image/webp", "image/gif" -> CreationAssetType.IMAGE;
            case "video/mp4", "video/webm", "video/quicktime" -> CreationAssetType.VIDEO;
            case "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/ogg" -> CreationAssetType.AUDIO;
            default -> throw new ServiceException("不支持的创作素材类型", ASSET_NOT_AVAILABLE);
        };
    }

    private String safeFileName(String originalName, CreationAssetType type) {
        String fallback = switch (type) {
            case IMAGE -> "image";
            case VIDEO -> "video.mp4";
            case AUDIO -> "audio";
        };
        String candidate = originalName == null ? fallback : originalName.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1)
            .replaceAll("[^A-Za-z0-9._-]", "_");
        return candidate.isBlank() ? fallback : candidate.substring(0, Math.min(candidate.length(), 128));
    }

    private OssClient requireOssClient() {
        try {
            OssClient client = ossClientProvider == null ? null : ossClientProvider.getIfAvailable();
            if (client != null) {
                return client;
            }
        } catch (RuntimeException ignored) {
            // Keep configuration and provider details inside the process boundary.
        }
        throw new ServiceException("VideoOps 对象存储未启用", ASSET_NOT_AVAILABLE);
    }

    private void deleteUploadedObject(OssClient client, String key) {
        try {
            client.delete(key);
        } catch (RuntimeException ignored) {
            // The database error remains authoritative; cleanup is best effort.
        }
    }

    private String displayName(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "asset";
        }
        String name = storageKey.substring(storageKey.lastIndexOf('/') + 1);
        int separator = name.indexOf('-');
        return separator < 0 || separator == name.length() - 1 ? "asset" : name.substring(separator + 1);
    }

    private String requestDigest(String contentDigest, String usageIntent) {
        MessageDigest digest = sha256();
        digest.update(contentDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(usageIntent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private CreationAssetResolveDTO toResolve(CreationAsset asset, TimelineAssetUsageType usageType) {
        return new CreationAssetResolveDTO(Long.toString(asset.getAssetId()), asset.getMimeType(), asset.getSha256(),
            CreationAssetType.fromValue(asset.getAssetType()), usageType, asset.getSizeBytes(), asset.getDurationMs(),
            asset.getWidth(), asset.getHeight(), Boolean.TRUE.equals(asset.getHasVideoStream()),
            Boolean.TRUE.equals(asset.getHasAudioStream()));
    }

    private ByteRange parseSingleRange(String header, long totalSize) {
        if (header == null || !header.startsWith("bytes=") || header.contains(",")) {
            throw invalidRange();
        }
        String specification = header.substring("bytes=".length());
        int dash = specification.indexOf('-');
        if (dash < 0 || dash != specification.lastIndexOf('-')) {
            throw invalidRange();
        }
        String startPart = specification.substring(0, dash);
        String endPart = specification.substring(dash + 1);
        try {
            if (startPart.isEmpty()) {
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) throw invalidRange();
                long length = Math.min(suffix, totalSize);
                long start = totalSize - length;
                return new ByteRange(start, length, "bytes=" + start + "-" + (totalSize - 1));
            }
            long start = Long.parseLong(startPart);
            if (start < 0 || start >= totalSize) throw invalidRange();
            long end = endPart.isEmpty() ? totalSize - 1 : Long.parseLong(endPart);
            if (end < start) throw invalidRange();
            end = Math.min(end, totalSize - 1);
            return new ByteRange(start, end - start + 1, "bytes=" + start + "-" + end);
        } catch (NumberFormatException exception) {
            throw invalidRange();
        }
    }

    private long parsePositiveId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ServiceException("创作素材不可用", ASSET_NOT_AVAILABLE);
        }
    }

    private ServiceException invalidRange() {
        return new ServiceException("请求范围无效", INVALID_RANGE);
    }

    private CreationAsset findBySource(long actorId, CreationAssetUsageOrigin origin, long sourceRefId) {
        return assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getUsageOrigin, origin.value())
            .eq(CreationAsset::getSourceRefId, sourceRefId)
            .eq(CreationAsset::getDelFlag, "0"));
    }

    private CreationAsset findByIdempotency(long actorId, String idempotencyKey) {
        return assetMapper.selectOne(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getOwnerUserId, actorId)
            .eq(CreationAsset::getIdempotencyKey, idempotencyKey)
            .eq(CreationAsset::getDelFlag, "0"));
    }

    private PendingRenderOutputDTO resolvePendingReplay(CreationAsset existing, long taskId, long inputVersionId,
                                                         String outputConfigDigest, String storageKey,
                                                         String requestDigest) {
        if (!storageKey.equals(existing.getStorageKey()) || !requestDigest.equals(existing.getRequestDigest())) {
            throw idempotencyConflict();
        }
        return toPending(existing, taskId, inputVersionId, outputConfigDigest);
    }

    private PendingRenderOutputDTO toPending(CreationAsset asset, long taskId, long inputVersionId,
                                              String outputConfigDigest) {
        return new PendingRenderOutputDTO(Long.toString(asset.getAssetId()), Long.toString(taskId),
            Long.toString(inputVersionId), outputConfigDigest,
            CreationAssetStatus.fromValue(asset.getAssetStatus()), asset.getCreateTime() == null ? null
                : asset.getCreateTime().toInstant(ZoneOffset.UTC));
    }

    private PendingRenderOutputDTO toStoredPending(CreationAsset asset) {
        java.util.regex.Matcher matcher = RENDER_STORAGE_KEY.matcher(asset.getStorageKey());
        if (!matcher.matches() || asset.getOwnerUserId() == null
            || !Long.toString(asset.getOwnerUserId()).equals(matcher.group(1))
            || asset.getSourceRefId() == null || !Long.toString(asset.getSourceRefId()).equals(matcher.group(2))) {
            throw assetInvalid("创作成品补偿记录无效");
        }
        return toPending(asset, asset.getSourceRefId(), Long.parseLong(matcher.group(3)), matcher.group(4));
    }

    private String renderStorageKey(long actorId, long taskId, long inputVersionId, String outputConfigDigest) {
        return VideoOpsObjectKey.qualify("timeline-renders/" + actorId + "/" + taskId + "/"
            + inputVersionId + "/" + outputConfigDigest + ".mp4");
    }

    private String renderRequestDigest(long taskId, long inputVersionId, String outputConfigDigest) {
        return digestText("timeline-render-output\n" + taskId + "\n" + inputVersionId + "\n" + outputConfigDigest);
    }

    private String requireIdempotencyKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw assetInvalid("幂等键格式无效");
        }
        return normalized;
    }

    private String requireLowerHexDigest(String value) {
        if (value == null || !LOWER_HEX_DIGEST.matcher(value).matches()) {
            throw assetInvalid("输出配置摘要无效");
        }
        return value;
    }

    private String digestText(String value) {
        return HexFormat.of().formatHex(sha256().digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private ServiceException assetInvalid(String message) {
        return new ServiceException(message, ASSET_NOT_AVAILABLE);
    }

    private ServiceException idempotencyConflict() {
        return new ServiceException("幂等键已用于不同的创作请求", TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT);
    }

    private TimelineRenderResultDTO requireRenderMetadata(TimelineRenderResultDTO metadata) {
        if (metadata == null || !"video/mp4".equals(normalizedMediaType(metadata.contentType()))
            || metadata.fileSize() <= 0 || metadata.durationMs() <= 0 || metadata.width() <= 0
            || metadata.height() <= 0 || metadata.frameRate() <= 0) {
            throw assetInvalid("创作成品媒体元数据无效");
        }
        requireLowerHexDigest(metadata.sha256());
        return metadata;
    }

    private InputStream requireOutputStream(TimelineRenderOutputHandle output) {
        InputStream stream = output.stream();
        if (stream == null) {
            throw assetInvalid("创作成品输出流无效");
        }
        return stream;
    }

    private RenderOutputReadyDTO toRenderReady(CreationAsset asset, TimelineRenderResultDTO metadata) {
        return new RenderOutputReadyDTO(Long.toString(asset.getAssetId()), Long.toString(asset.getSourceRefId()),
            asset.getMimeType(), asset.getSha256(), asset.getSizeBytes(), asset.getDurationMs(), asset.getWidth(),
            asset.getHeight(), metadata.frameRate(), Boolean.TRUE.equals(asset.getHasVideoStream()),
            Boolean.TRUE.equals(asset.getHasAudioStream()));
    }

    private String normalizedMediaType(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private ServiceException renderUnavailable() {
        return new ServiceException("创作成品暂时不可用", TimelineErrorCodes.TIMELINE_RENDER_UNAVAILABLE);
    }

    private record ByteRange(long offset, long length, String header) {
    }

    private record UploadSpec(CreationAssetType type, TimelineAssetUsageType usage) {
    }

    private static final class InMemoryCreationMediaHandle implements CreationMediaHandle {
        private final CreationAssetResolveDTO metadata;
        private final ByteArrayInputStream stream;

        private InMemoryCreationMediaHandle(CreationAssetResolveDTO metadata, byte[] content) {
            this.metadata = metadata;
            this.stream = new ByteArrayInputStream(content);
        }

        @Override public CreationAssetResolveDTO metadata() { return metadata; }
        @Override public InputStream stream() { return stream; }
        @Override public long offset() { return 0L; }
        @Override public long length() { return metadata.sizeBytes(); }
        @Override public long totalSize() { return metadata.sizeBytes(); }
        @Override public void close() throws IOException { stream.close(); }
    }

    private static final class OssCreationMediaHandle implements CreationMediaHandle {
        private final CreationAssetResolveDTO metadata;
        private final InputStream stream;
        private final long offset;
        private final long length;
        private final long totalSize;
        private final AtomicBoolean closed = new AtomicBoolean();

        private OssCreationMediaHandle(CreationAssetResolveDTO metadata, InputStream stream,
                                       long offset, long length, long totalSize) {
            this.metadata = metadata;
            this.stream = stream;
            this.offset = offset;
            this.length = length;
            this.totalSize = totalSize;
        }

        @Override public CreationAssetResolveDTO metadata() { return metadata; }
        @Override public InputStream stream() { return stream; }
        @Override public long offset() { return offset; }
        @Override public long length() { return length; }
        @Override public long totalSize() { return totalSize; }

        @Override
        public void close() throws IOException {
            if (closed.compareAndSet(false, true)) {
                stream.close();
            }
        }
    }
}
