package org.dromara.aivideo.voice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.voice.domain.Voice;
import org.dromara.aivideo.voice.dto.*;
import org.dromara.aivideo.voice.mapper.VoiceMapper;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements IVoiceService {
    private static final int NOT_FOUND = 46401;
    private static final int INPUT_INVALID = 46402;
    private static final int REVISION_CONFLICT = 46403;
    private static final int STATE_INVALID = 46404;
    private static final int MAX_ATTEMPTS = 3;
    private static final String VOICE_DELETE_PERMISSION = "aivideo:voice:delete";
    private static final List<String> DELETABLE_VOICE_TYPES = List.of("origin", "clone");
    private final VoiceMapper voiceMapper;
    private final IAssetService assetService;

    @Override
    public PageResult<VoiceDTO> queryPage(VoiceQueryDTO query, AppPrincipalSnapshotDTO principal, PageQuery pageQuery) {
        requirePermission(principal, "aivideo:voice:query");
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        VoiceQueryDTO safe = query == null ? new VoiceQueryDTO(null, null, null) : query;
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 20 : Math.min(50, Math.max(1, pageQuery.getPageSize()));
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : Math.max(1, pageQuery.getPageNum());
        LambdaQueryWrapper<Voice> wrapper = ownedWrapper(principal, workspace)
            .eq(hasText(safe.voiceType()), Voice::getVoiceType, safe.voiceType())
            .eq(hasText(safe.transcriptionStatus()), Voice::getTranscriptionStatus, safe.transcriptionStatus())
            .and(hasText(safe.keyword()), item -> item.like(Voice::getName, safe.keyword().trim())
                .or().like(Voice::getStyle, safe.keyword().trim())
                .or().like(Voice::getTagsJson, safe.keyword().trim())
                .or().like(Voice::getTranscriptText, safe.keyword().trim()))
            .orderByDesc(Voice::getCreateTime).orderByDesc(Voice::getVoiceId);
        Page<Voice> page = voiceMapper.selectPage(new PageQuery(pageSize, pageNum).build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toDTO).toList(), page.getTotal());
    }

    @Override
    public VoiceDTO queryById(String voiceId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:query");
        return toDTO(requireOwned(voiceId, principal));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDTO create(CreateVoiceDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:upload");
        if (command == null) throw inputError("声音参数不能为空");
        Voice changed = new Voice();
        applyMetadata(changed, command.name(), command.gender(), command.style(), command.tags(), command.note());
        String idempotencyKey = requiredText(command.idempotencyKey(), "幂等键", 128);
        String fingerprint = requiredText(command.uploadFingerprint(), "上传摘要", 128);
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        Voice existing = findByIdempotencyKey(workspace, principal, idempotencyKey);
        if (existing != null) {
            if (fingerprint.equals(existing.getUploadFingerprint())) return toDTO(existing);
            throw new ServiceException("相同幂等键对应不同声音", 46204);
        }
        AssetDTO asset = assetService.requireOwnedReadyVoiceAsset(command.assetId(), principal);
        Voice voice = new Voice();
        voice.setTenantId(workspace.tenantId());
        voice.setWorkspaceId(workspace.workspaceKey());
        voice.setOwnerId(principal.appUserId());
        voice.setAssetId(parseId(asset.assetId(), INPUT_INVALID));
        voice.setIdempotencyKey(idempotencyKey);
        voice.setUploadFingerprint(fingerprint);
        voice.setVoiceType("origin");
        voice.setName(changed.getName());
        voice.setGender(changed.getGender());
        voice.setStyle(changed.getStyle());
        voice.setTagsJson(changed.getTagsJson());
        voice.setNote(changed.getNote());
        voice.setTranscriptionStatus(command.transcriptionRequested() ? "pending" : "unparsed");
        voice.setAttemptCount(0);
        voice.setNextAttemptAt(command.transcriptionRequested() ? LocalDateTime.now() : null);
        voice.setRecordRevision(1L);
        voice.setCreateBy(principal.appUserId());
        voice.setUpdateBy(principal.appUserId());
        try {
        if (voiceMapper.insert(voice) != 1 || voice.getVoiceId() == null) throw inputError("声音创建失败");
        } catch (DuplicateKeyException exception) {
            Voice concurrent = findByIdempotencyKey(workspace, principal, idempotencyKey);
            if (concurrent != null && fingerprint.equals(concurrent.getUploadFingerprint())) return toDTO(concurrent);
            throw new ServiceException("相同幂等键对应不同声音", 46204);
        }
        return toDTO(voice);
    }

    private Voice findByIdempotencyKey(AppWorkspaceSessionSnapshotDTO workspace,
                                       AppPrincipalSnapshotDTO principal,
                                       String idempotencyKey) {
        return voiceMapper.selectOne(new LambdaQueryWrapper<Voice>()
            .eq(Voice::getTenantId, workspace.tenantId())
            .eq(Voice::getWorkspaceId, workspace.workspaceKey())
            .eq(Voice::getOwnerId, principal.appUserId())
            .eq(Voice::getIdempotencyKey, idempotencyKey)
            .eq(Voice::getDelFlag, "0"));
    }

    @Override
    public String createAccessUrl(String voiceId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:query");
        Voice voice = requireOwned(voiceId, principal);
        assetService.requireOwnedReadyVoiceAsset(Long.toString(voice.getAssetId()), principal);
        return assetService.createVoiceAccessUrl(Long.toString(voice.getAssetId()), principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceTranscriptionLeaseDTO claimNext(String workerId, Instant now) {
        String owner = requiredText(workerId, "Worker 标识", 128);
        LocalDateTime current = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        Voice candidate = voiceMapper.selectOne(new LambdaQueryWrapper<Voice>().eq(Voice::getDelFlag, "0")
            .and(item -> item.eq(Voice::getTranscriptionStatus, "pending").le(Voice::getNextAttemptAt, current)
                .or(expired -> expired.eq(Voice::getTranscriptionStatus, "transcribing")
                    .le(Voice::getLeaseExpiresAt, current)))
            .orderByAsc(Voice::getNextAttemptAt).orderByAsc(Voice::getVoiceId).last("LIMIT 1"));
        if (candidate == null) return null;
        int attempt = candidate.getAttemptCount() + 1;
        long revision = candidate.getRecordRevision() + 1;
        int affected = voiceMapper.update(null, new LambdaUpdateWrapper<Voice>()
            .eq(Voice::getVoiceId, candidate.getVoiceId()).eq(Voice::getRecordRevision, candidate.getRecordRevision())
            .eq(Voice::getTranscriptionStatus, candidate.getTranscriptionStatus())
            .eq(Voice::getDelFlag, "0")
            .set(Voice::getTranscriptionStatus, "transcribing").set(Voice::getAttemptCount, attempt)
            .set(Voice::getLeaseOwner, owner).set(Voice::getLeaseExpiresAt, current.plusMinutes(15))
            .setSql("record_revision = record_revision + 1"));
        if (affected != 1) return null;
        return new VoiceTranscriptionLeaseDTO(Long.toString(candidate.getVoiceId()),
            Long.toString(candidate.getAssetId()), candidate.getTenantId(), candidate.getWorkspaceId(),
            candidate.getOwnerId(), candidate.getVoiceId() + ":" + revision + ":" + attempt,
            owner, revision, attempt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionResultDTO result) {
        if (lease == null || result == null || !lease.requestId().equals(result.requestId())) return false;
        String text = requiredText(result.text(), "转写文本", 20000);
        if (result.durationMillis() < 0) throw inputError("声音时长无效");
        List<VoiceTranscriptCueDTO> timeline = result.transcriptTimeline() == null
            ? List.of() : result.transcriptTimeline();
        return voiceMapper.update(null, leaseUpdate(lease)
            .set(Voice::getTranscriptText, text).set(Voice::getDetectedLanguage, cleanOptional(result.language(), 16))
            .set(Voice::getTranscriptTimelineJson, JsonUtils.toJsonString(timeline))
            .set(Voice::getDurationMillis, result.durationMillis()).set(Voice::getTranscriptionStatus, "ready")
            .set(Voice::getFailureCode, null).set(Voice::getFailureMessage, null)
            .set(Voice::getLeaseOwner, null).set(Voice::getLeaseExpiresAt, null)
            .setSql("record_revision = record_revision + 1")) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void failTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionFailureDTO failure) {
        if (lease == null || failure == null) return;
        LambdaUpdateWrapper<Voice> update = leaseUpdate(lease)
            .set(Voice::getLeaseOwner, null).set(Voice::getLeaseExpiresAt, null)
            .setSql("record_revision = record_revision + 1");
        if (failure.retryable() && lease.attemptCount() < MAX_ATTEMPTS) {
            update.set(Voice::getTranscriptionStatus, "pending")
                .set(Voice::getNextAttemptAt, LocalDateTime.now().plusSeconds(1L << lease.attemptCount()));
        } else {
            update.set(Voice::getTranscriptionStatus, "failed")
                .set(Voice::getFailureCode, cleanOptional(failure.code(), 64))
                .set(Voice::getFailureMessage, cleanOptional(failure.message(), 500));
        }
        voiceMapper.update(null, update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDTO updateTranscript(UpdateVoiceTranscriptDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:edit");
        Voice current = requireOwned(command.voiceId(), principal);
        if (!"ready".equals(current.getTranscriptionStatus())) throw stateError();
        String text = requiredText(command.transcriptText(), "转写文本", 20000);
        long revision = parseId(command.expectedRevision(), REVISION_CONFLICT);
        int affected = voiceMapper.update(null, ownedUpdate(current, principal, revision)
            .set(Voice::getTranscriptText, text).set(Voice::getTranscriptTimelineJson, null)
            .set(Voice::getUpdateBy, principal.appUserId())
            .setSql("record_revision = record_revision + 1"));
        if (affected != 1) throw revisionError();
        return queryOwned(command.voiceId(), principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDTO resyncTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:transcribe");
        Voice current = requireOwned(command.voiceId(), principal);
        if (!"ready".equals(current.getTranscriptionStatus())) throw stateError();
        long revision = parseId(command.expectedRevision(), REVISION_CONFLICT);
        int affected = voiceMapper.update(null, ownedUpdate(current, principal, revision)
            .set(Voice::getTranscriptionStatus, "pending").set(Voice::getTranscriptTimelineJson, null)
            .set(Voice::getAttemptCount, 0).set(Voice::getNextAttemptAt, LocalDateTime.now())
            .set(Voice::getFailureCode, null).set(Voice::getFailureMessage, null)
            .set(Voice::getLeaseOwner, null).set(Voice::getLeaseExpiresAt, null)
            .setSql("record_revision = record_revision + 1"));
        if (affected != 1) throw revisionError();
        return queryOwned(command.voiceId(), principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDTO startTranscription(StartVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:transcribe");
        if (command == null) throw inputError("声音参数不能为空");
        Voice current = requireOwned(command.voiceId(), principal);
        if (!"origin".equals(current.getVoiceType()) || !"unparsed".equals(current.getTranscriptionStatus())) {
            throw stateError();
        }
        long revision = parseId(command.expectedRevision(), REVISION_CONFLICT);
        int affected = voiceMapper.update(null, ownedUpdate(current, principal, revision)
            .eq(Voice::getVoiceType, "origin")
            .eq(Voice::getTranscriptionStatus, "unparsed")
            .set(Voice::getTranscriptionStatus, "pending")
            .set(Voice::getAttemptCount, 0)
            .set(Voice::getNextAttemptAt, LocalDateTime.now())
            .set(Voice::getFailureCode, null)
            .set(Voice::getFailureMessage, null)
            .set(Voice::getLeaseOwner, null)
            .set(Voice::getLeaseExpiresAt, null)
            .set(Voice::getUpdateBy, principal.appUserId())
            .setSql("record_revision = record_revision + 1"));
        if (affected != 1) throw revisionError();
        return queryOwned(command.voiceId(), principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoiceDTO retryTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, "aivideo:voice:transcribe");
        Voice current = requireOwned(command.voiceId(), principal);
        if (!"failed".equals(current.getTranscriptionStatus())) throw stateError();
        long revision = parseId(command.expectedRevision(), REVISION_CONFLICT);
        int affected = voiceMapper.update(null, ownedUpdate(current, principal, revision)
            .set(Voice::getTranscriptionStatus, "pending").set(Voice::getAttemptCount, 0)
            .set(Voice::getNextAttemptAt, LocalDateTime.now()).set(Voice::getFailureCode, null)
            .set(Voice::getFailureMessage, null).set(Voice::getLeaseOwner, null)
            .set(Voice::getLeaseExpiresAt, null).setSql("record_revision = record_revision + 1"));
        if (affected != 1) throw revisionError();
        return queryOwned(command.voiceId(), principal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnedVoice(String voiceId, AppPrincipalSnapshotDTO principal) {
        requirePermission(principal, VOICE_DELETE_PERMISSION);
        long id = parseId(voiceId, NOT_FOUND);
        Voice voice = voiceMapper.selectOne(ownedDeletableWrapper(id, principal));
        if (voice == null) throw voiceNotFound();
        if (voiceMapper.delete(ownedDeletableWrapper(id, principal)) != 1) throw voiceNotFound();
        assetService.tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
            Long.toString(voice.getVoiceId()), Long.toString(voice.getAssetId()), principal);
    }

    private VoiceDTO queryOwned(String voiceId, AppPrincipalSnapshotDTO principal) {
        return toDTO(requireOwned(voiceId, principal));
    }

    private Voice requireOwned(String voiceId, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        Voice voice = voiceMapper.selectOne(ownedWrapper(principal, workspace)
            .eq(Voice::getVoiceId, parseId(voiceId, NOT_FOUND)));
        if (voice == null) throw new ServiceException("声音不存在", NOT_FOUND);
        return voice;
    }

    private LambdaQueryWrapper<Voice> ownedWrapper(AppPrincipalSnapshotDTO principal,
                                                    AppWorkspaceSessionSnapshotDTO workspace) {
        return new LambdaQueryWrapper<Voice>().eq(Voice::getTenantId, workspace.tenantId())
            .eq(Voice::getWorkspaceId, workspace.workspaceKey()).eq(Voice::getOwnerId, principal.appUserId())
            .eq(Voice::getDelFlag, "0");
    }

    private LambdaUpdateWrapper<Voice> ownedUpdate(Voice current, AppPrincipalSnapshotDTO principal, long revision) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        return new LambdaUpdateWrapper<Voice>().eq(Voice::getVoiceId, current.getVoiceId())
            .eq(Voice::getTenantId, workspace.tenantId()).eq(Voice::getWorkspaceId, workspace.workspaceKey())
            .eq(Voice::getOwnerId, principal.appUserId()).eq(Voice::getRecordRevision, revision)
            .eq(Voice::getDelFlag, "0");
    }

    private LambdaQueryWrapper<Voice> ownedDeletableWrapper(long voiceId, AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = requireWorkspace(principal);
        return ownedWrapper(principal, workspace)
            .eq(Voice::getVoiceId, voiceId)
            .in(Voice::getVoiceType, DELETABLE_VOICE_TYPES);
    }

    private LambdaUpdateWrapper<Voice> leaseUpdate(VoiceTranscriptionLeaseDTO lease) {
        return new LambdaUpdateWrapper<Voice>().eq(Voice::getVoiceId, parseId(lease.voiceId(), NOT_FOUND))
            .eq(Voice::getTranscriptionStatus, "transcribing").eq(Voice::getLeaseOwner, lease.leaseOwner())
            .eq(Voice::getRecordRevision, lease.recordRevision()).eq(Voice::getDelFlag, "0");
    }

    private VoiceDTO toDTO(Voice voice) {
        List<String> tags = voice.getTagsJson() == null ? List.of()
            : JsonUtils.parseArray(voice.getTagsJson(), String.class);
        List<VoiceTranscriptCueDTO> transcriptTimeline = voice.getTranscriptTimelineJson() == null ? List.of()
            : JsonUtils.parseArray(voice.getTranscriptTimelineJson(), VoiceTranscriptCueDTO.class);
        return new VoiceDTO(Long.toString(voice.getVoiceId()), Long.toString(voice.getAssetId()),
            voice.getVoiceType(), voice.getName(), voice.getGender(), voice.getStyle(), tags, voice.getNote(),
            voice.getTranscriptText(), transcriptTimeline, voice.getDetectedLanguage(), voice.getDurationMillis(),
            voice.getTranscriptionStatus(), voice.getFailureCode(), voice.getFailureMessage(),
            voice.getAttemptCount(), Long.toString(voice.getRecordRevision()), voice.getCreateTime(), voice.getUpdateTime());
    }

    private void applyMetadata(Voice voice, String name, String gender, String style, List<String> tags, String note) {
        voice.setName(requiredText(name, "声音名称", 80));
        String cleanGender = gender == null ? "unspecified" : gender.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("female", "male", "unspecified").contains(cleanGender)) throw inputError("性别取值无效");
        voice.setGender(cleanGender);
        voice.setStyle(cleanOptional(style, 40));
        LinkedHashSet<String> cleanTags = new LinkedHashSet<>();
        if (tags != null) for (String tag : tags) {
            if (!hasText(tag)) continue;
            cleanTags.add(requiredText(tag, "标签", 20));
            if (cleanTags.size() > 8) throw inputError("标签最多 8 个");
        }
        voice.setTagsJson(JsonUtils.toJsonString(new ArrayList<>(cleanTags)));
        voice.setNote(cleanOptional(note, 500));
    }

    private String requiredText(String value, String label, int max) {
        if (!hasText(value)) throw inputError(label + "不能为空");
        String result = value.trim();
        if (result.length() > max) throw inputError(label + "长度不能超过 " + max);
        return result;
    }

    private String cleanOptional(String value, int max) {
        if (!hasText(value)) return null;
        String result = value.trim();
        if (result.length() > max) throw inputError("字段长度不能超过 " + max);
        return result;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private AppWorkspaceSessionSnapshotDTO requireWorkspace(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0 || principal.workspace() == null
            || principal.workspace().tenantId() == null || !hasText(principal.workspace().workspaceKey())) {
            throw new ServiceException("当前创作工作区不可用", 403);
        }
        return principal.workspace();
    }

    private void requirePermission(AppPrincipalSnapshotDTO principal, String permission) {
        if (!requireWorkspace(principal).permissions().contains(permission)) throw new ServiceException("无声音操作权限", 403);
    }

    private long parseId(String value, int code) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (RuntimeException exception) {
            throw new ServiceException("资源编号或修订号无效", code);
        }
    }

    private ServiceException inputError(String message) { return new ServiceException(message, INPUT_INVALID); }
    private ServiceException revisionError() { return new ServiceException("声音已被修改，请刷新后重试", REVISION_CONFLICT); }
    private ServiceException stateError() { return new ServiceException("当前声音解析状态不允许此操作", STATE_INVALID); }
    private ServiceException voiceNotFound() { return new ServiceException("声音不存在", NOT_FOUND); }
}
