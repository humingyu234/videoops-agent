package org.dromara.aivideo.digitalhuman.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoJobDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanMediaContentDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoProviderStatus;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanMediaStorageService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanVideoService;
import org.dromara.aivideo.digitalhuman.service.IVoiceSynthesisService;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

@Service
@ConditionalOnAppSecurityEnabled
@Slf4j
public class DigitalHumanGenerationServiceImpl implements IDigitalHumanGenerationService {

    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MAX_VOICE_OUTPUT_BYTES = 32 * 1024 * 1024;
    private static final int MAX_VIDEO_OUTPUT_BYTES = 128 * 1024 * 1024;
    private static final int MAX_POLL_ERRORS = 3;
    private static final int POLL_LEASE_MINUTES = 10;
    private static final int VOICE_TIMEOUT_MINUTES = 10;
    private static final int VIDEO_TIMEOUT_MINUTES = 60;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final DigitalHumanGenerationJobMapper mapper;
    private final IVoiceSynthesisService voiceService;
    private final IDigitalHumanVideoService videoService;
    private final IDigitalHumanMediaStorageService storage;
    private final Executor executor;
    private final LongSupplier idSupplier;

    @Autowired
    public DigitalHumanGenerationServiceImpl(DigitalHumanGenerationJobMapper mapper,
                                             IVoiceSynthesisService voiceService,
                                             IDigitalHumanVideoService videoService,
                                             IDigitalHumanMediaStorageService storage,
                                             @Qualifier("applicationTaskExecutor") Executor executor) {
        this(mapper, voiceService, videoService, storage, executor, IdWorker::getId);
    }

    DigitalHumanGenerationServiceImpl(DigitalHumanGenerationJobMapper mapper,
                                      IVoiceSynthesisService voiceService,
                                      IDigitalHumanVideoService videoService,
                                      IDigitalHumanMediaStorageService storage,
                                      Executor executor,
                                      LongSupplier idSupplier) {
        this.mapper = Objects.requireNonNull(mapper);
        this.voiceService = Objects.requireNonNull(voiceService);
        this.videoService = Objects.requireNonNull(videoService);
        this.storage = Objects.requireNonNull(storage);
        this.executor = Objects.requireNonNull(executor);
        this.idSupplier = Objects.requireNonNull(idSupplier);
    }

    @Override
    public DigitalHumanJobDTO createVoiceJob(CreateVoiceGenerationJobDTO request) {
        Objects.requireNonNull(request, "声音任务参数不能为空");
        DigitalHumanOwnerDTO owner = requireOwner(request.owner());
        String key = requireKey(request.idempotencyKey());
        String text = request.scriptText() == null ? "" : request.scriptText().trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw new ServiceException("口播文案长度必须为 1 到 1000 个字符");
        }
        String inputMediaType = requireReferenceAudio(
            request.referenceAudio(), request.referenceAudioType(), request.referenceAudioName());
        String inputHash = sha256("voice-input", text.getBytes(StandardCharsets.UTF_8), request.referenceAudio());
        DigitalHumanGenerationJob existing = mapper.selectByIdempotency(
            owner.tenantId(), owner.userId(), DigitalHumanJobType.VOICE_GENERATE, key);
        if (existing != null) {
            return idempotent(existing, inputHash);
        }

        long jobId = idSupplier.getAsLong();
        DigitalHumanStoredMediaDTO input = storage.storeInput(jobId, request.referenceAudioName(),
            inputMediaType, request.referenceAudio());
        DigitalHumanGenerationJob job = newJob(jobId, owner, DigitalHumanJobType.VOICE_GENERATE, key, inputHash);
        job.setScriptText(text);
        job.setInputMediaKey(input.key());
        job.setProvider("indextts2");
        DigitalHumanJobDTO replay = insertOrReplay(job, input, owner, inputHash);
        if (replay != null) {
            return replay;
        }
        return dispatch(job, () -> runVoice(job, request.referenceAudioName()),
            "VOICE_DISPATCH_REJECTED", "声音任务暂时无法执行，请重试");
    }

    @Override
    public DigitalHumanJobDTO confirmVoiceJob(Long jobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanGenerationJob job = requireOwned(jobId, owner);
        if (job.getJobType() != DigitalHumanJobType.VOICE_GENERATE
            || job.getStatus() != DigitalHumanJobStatus.SUCCEEDED) {
            throw new ServiceException("只有已成功生成的声音可以确认");
        }
        if (!Boolean.TRUE.equals(job.getVoiceConfirmed())) {
            if (!confirmVoice(job, owner.userId())) {
                job = requireOwned(jobId, owner);
                if (job.getJobType() != DigitalHumanJobType.VOICE_GENERATE
                    || job.getStatus() != DigitalHumanJobStatus.SUCCEEDED
                    || !Boolean.TRUE.equals(job.getVoiceConfirmed())) {
                    throw new ServiceException("声音确认状态已变化，请刷新重试");
                }
            }
        }
        return toDto(job);
    }

    @Override
    public DigitalHumanJobDTO createVideoJob(CreateDigitalHumanVideoJobDTO request) {
        Objects.requireNonNull(request, "视频任务参数不能为空");
        DigitalHumanOwnerDTO owner = requireOwner(request.owner());
        String key = requireKey(request.idempotencyKey());
        String inputMediaType = requirePortrait(request.portrait(), request.portraitType(), request.portraitName());
        DigitalHumanGenerationJob voice = requireOwned(request.voiceJobId(), owner);
        if (voice.getJobType() != DigitalHumanJobType.VOICE_GENERATE
            || voice.getStatus() != DigitalHumanJobStatus.SUCCEEDED
            || !Boolean.TRUE.equals(voice.getVoiceConfirmed())
            || voice.getOutputMediaKey() == null) {
            throw new ServiceException("请先生成并确认声音");
        }
        String inputHash = sha256(
            "video-input", String.valueOf(voice.getId()).getBytes(StandardCharsets.UTF_8), request.portrait());
        DigitalHumanGenerationJob existing = mapper.selectByIdempotency(
            owner.tenantId(), owner.userId(), DigitalHumanJobType.VIDEO_GENERATE, key);
        if (existing != null) {
            return idempotent(existing, inputHash);
        }

        long jobId = idSupplier.getAsLong();
        DigitalHumanStoredMediaDTO input = storage.storeInput(jobId, request.portraitName(),
            inputMediaType, request.portrait());
        DigitalHumanGenerationJob job = newJob(jobId, owner, DigitalHumanJobType.VIDEO_GENERATE, key, inputHash);
        job.setParentJobId(voice.getId());
        job.setInputMediaKey(input.key());
        job.setProvider("comfyui");
        DigitalHumanJobDTO replay = insertOrReplay(job, input, owner, inputHash);
        if (replay != null) {
            return replay;
        }
        return dispatch(job, () -> runVideo(job, voice),
            "VIDEO_DISPATCH_REJECTED", "视频任务暂时无法执行，请重试");
    }

    @Override
    public DigitalHumanJobDTO getJob(Long jobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanGenerationJob job = requireOwned(jobId, owner);
        if (isTimedOut(job, LocalDateTime.now())) {
            if (fail(job, job.getStatus(), "GENERATION_TIMEOUT", "数字人生成超时，请重试")) {
                return toDto(job);
            }
            job = requireOwned(jobId, owner);
        }
        if (job.getJobType() == DigitalHumanJobType.VIDEO_GENERATE
            && job.getStatus() == DigitalHumanJobStatus.RUNNING
            && job.getProviderJobId() != null) {
            refreshVideo(job);
            job = requireOwned(jobId, owner);
        }
        return toDto(job);
    }

    @Override
    public DigitalHumanJobDTO getStoredJob(Long jobId, DigitalHumanOwnerDTO owner) {
        return toDto(requireOwned(jobId, owner));
    }

    @Override
    public DigitalHumanMediaContentDTO getOutputMedia(Long jobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanGenerationJob job = requireOwned(jobId, owner);
        if (job.getStatus() != DigitalHumanJobStatus.SUCCEEDED || job.getOutputMediaKey() == null) {
            throw new ServiceException("生成结果尚不可用");
        }
        return storage.read(job.getOutputMediaKey());
    }

    private void runVoice(DigitalHumanGenerationJob job, String originalName) {
        DigitalHumanStoredMediaDTO output = null;
        try {
            if (!start(job, DigitalHumanJobStage.VOICE_SYNTHESIZING, 20)) {
                return;
            }
            DigitalHumanMediaContentDTO reference = storage.read(job.getInputMediaKey());
            VoiceSynthesisResultDTO result = voiceService.synthesize(new VoiceSynthesisRequestDTO(
                job.getScriptText(), originalName, reference.mediaType(), reference.content()));
            requireProviderVoice(result.audio(), result.mediaType(), result.fileExtension());
            output = storage.storeOutput(
                job.getId(), "voice.wav", "audio/wav", result.audio());
            if (!completeVoice(job, output)) {
                storage.delete(output.key());
            }
        } catch (RuntimeException exception) {
            if (output != null) {
                storage.delete(output.key());
            }
            fail(job, job.getStatus(), "VOICE_PROVIDER_FAILED", "声音生成失败，请重试");
        }
    }

    private void runVideo(DigitalHumanGenerationJob job, DigitalHumanGenerationJob voice) {
        try {
            if (!start(job, DigitalHumanJobStage.VIDEO_SUBMITTED, 20)) {
                return;
            }
            DigitalHumanMediaContentDTO portrait = storage.read(job.getInputMediaKey());
            DigitalHumanMediaContentDTO audio = storage.read(voice.getOutputMediaKey());
            String promptId = videoService.submit(new DigitalHumanVideoSubmitDTO(
                portrait.fileName(), portrait.mediaType(), portrait.content(),
                audio.fileName(), audio.mediaType(), audio.content()));
            if (promptId == null || promptId.isBlank()) {
                throw new ServiceException("视频服务未返回任务编号");
            }
            completeVideoSubmission(job, promptId);
        } catch (RuntimeException exception) {
            log.warn("Digital human video submission failed: jobId={}", job.getId(), exception);
            fail(job, job.getStatus(), "VIDEO_SUBMIT_FAILED", "视频任务提交失败，请重试");
        }
    }

    private boolean confirmVoice(DigitalHumanGenerationJob job, Long userId) {
        int updated = mapper.update(null, ownedStatusUpdate(job, DigitalHumanJobStatus.SUCCEEDED)
            .set(DigitalHumanGenerationJob::getVoiceConfirmed, true)
            .set(DigitalHumanGenerationJob::getUpdateBy, userId)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now())
            .eq(DigitalHumanGenerationJob::getVoiceConfirmed, false));
        if (updated == 1) {
            job.setVoiceConfirmed(true);
            job.setUpdateBy(userId);
            return true;
        }
        return false;
    }

    private boolean start(DigitalHumanGenerationJob job, DigitalHumanJobStage stage, int progress) {
        LocalDateTime startTime = LocalDateTime.now();
        if (isTimedOut(job, startTime)) {
            fail(job, DigitalHumanJobStatus.QUEUED,
                "GENERATION_TIMEOUT", "数字人生成超时，请重试");
            return false;
        }
        LocalDateTime cutoff = startTime.minusMinutes(timeoutMinutes(job));
        int updated = mapper.update(null, ownedStatusUpdate(job, DigitalHumanJobStatus.QUEUED)
            .set(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.RUNNING)
            .set(DigitalHumanGenerationJob::getStage, stage)
            .set(DigitalHumanGenerationJob::getProgress, progress)
            .set(DigitalHumanGenerationJob::getUpdateTime, startTime)
            .gt(DigitalHumanGenerationJob::getCreateTime, cutoff));
        if (updated == 1) {
            job.setStatus(DigitalHumanJobStatus.RUNNING);
            job.setStage(stage);
            job.setProgress(progress);
            if (isTimedOut(job, LocalDateTime.now())) {
                fail(job, DigitalHumanJobStatus.RUNNING,
                    "GENERATION_TIMEOUT", "数字人生成超时，请重试");
                return false;
            }
            return true;
        }
        if (isTimedOut(job, LocalDateTime.now())) {
            fail(job, DigitalHumanJobStatus.QUEUED,
                "GENERATION_TIMEOUT", "数字人生成超时，请重试");
        }
        return false;
    }

    private boolean completeVoice(DigitalHumanGenerationJob job, DigitalHumanStoredMediaDTO output) {
        int updated = mapper.update(null, ownedStatusUpdate(job, DigitalHumanJobStatus.RUNNING)
            .set(DigitalHumanGenerationJob::getOutputMediaKey, output.key())
            .set(DigitalHumanGenerationJob::getOutputMediaType, output.mediaType())
            .set(DigitalHumanGenerationJob::getOutputMediaSize, output.size())
            .set(DigitalHumanGenerationJob::getOutputMediaSha256, output.sha256())
            .set(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.SUCCEEDED)
            .set(DigitalHumanGenerationJob::getStage, DigitalHumanJobStage.AWAITING_VOICE_CONFIRMATION)
            .set(DigitalHumanGenerationJob::getProgress, 100)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            applyOutput(job, output);
            job.setStatus(DigitalHumanJobStatus.SUCCEEDED);
            job.setStage(DigitalHumanJobStage.AWAITING_VOICE_CONFIRMATION);
            job.setProgress(100);
            return true;
        }
        return false;
    }

    private boolean completeVideoSubmission(DigitalHumanGenerationJob job, String promptId) {
        int updated = mapper.update(null, ownedStatusUpdate(job, DigitalHumanJobStatus.RUNNING)
            .set(DigitalHumanGenerationJob::getProviderJobId, promptId)
            .set(DigitalHumanGenerationJob::getStage, DigitalHumanJobStage.VIDEO_RENDERING)
            .set(DigitalHumanGenerationJob::getProgress, 30)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            job.setProviderJobId(promptId);
            job.setStage(DigitalHumanJobStage.VIDEO_RENDERING);
            job.setProgress(30);
            return true;
        }
        return false;
    }

    private void refreshVideo(DigitalHumanGenerationJob job) {
        String pollToken = UUID.randomUUID().toString();
        if (!claimVideoPoll(job, pollToken)) {
            return;
        }
        DigitalHumanVideoPollDTO result;
        try {
            result = videoService.poll(job.getProviderJobId());
        } catch (RuntimeException exception) {
            recordVideoPollError(job, pollToken);
            return;
        }
        if (result == null || result.status() == null) {
            recordVideoPollError(job, pollToken);
            return;
        }
        if (result.status() == DigitalHumanVideoProviderStatus.RUNNING) {
            releaseRunningVideoPoll(job, pollToken, result.progress());
            return;
        }
        if (result.status() == DigitalHumanVideoProviderStatus.FAILED) {
            failClaimedVideo(job, pollToken,
                result.failureCode() == null ? "VIDEO_PROVIDER_FAILED" : result.failureCode(),
                "视频生成失败，请重试");
            return;
        }
        completeClaimedVideo(job, pollToken, result);
    }

    private boolean claimVideoPoll(DigitalHumanGenerationJob job, String pollToken) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusMinutes(POLL_LEASE_MINUTES);
        int updated = mapper.update(null, Wrappers.<DigitalHumanGenerationJob>lambdaUpdate()
            .set(DigitalHumanGenerationJob::getPollToken, pollToken)
            .set(DigitalHumanGenerationJob::getPollLeaseUntil, leaseUntil)
            .set(DigitalHumanGenerationJob::getUpdateTime, now)
            .eq(DigitalHumanGenerationJob::getId, job.getId())
            .eq(DigitalHumanGenerationJob::getTenantId, job.getTenantId())
            .eq(DigitalHumanGenerationJob::getOwnerUserId, job.getOwnerUserId())
            .eq(DigitalHumanGenerationJob::getJobType, DigitalHumanJobType.VIDEO_GENERATE)
            .eq(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.RUNNING)
            .and(claim -> claim.isNull(DigitalHumanGenerationJob::getPollToken)
                .or().lt(DigitalHumanGenerationJob::getPollLeaseUntil, now)));
        if (updated == 1) {
            job.setPollToken(pollToken);
            job.setPollLeaseUntil(leaseUntil);
            return true;
        }
        return false;
    }

    private void releaseRunningVideoPoll(DigitalHumanGenerationJob job, String pollToken, Integer providerProgress) {
        int progress = Math.max(job.getProgress(), Math.min(95, providerProgress == null ? 50 : providerProgress));
        int updated = mapper.update(null, claimedVideoUpdate(job, pollToken)
            .set(DigitalHumanGenerationJob::getProgress, progress)
            .set(DigitalHumanGenerationJob::getPollErrorCount, 0)
            .set(DigitalHumanGenerationJob::getPollToken, null)
            .set(DigitalHumanGenerationJob::getPollLeaseUntil, null)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            job.setProgress(progress);
            job.setPollErrorCount(0);
            job.setPollToken(null);
            job.setPollLeaseUntil(null);
        }
    }

    private void recordVideoPollError(DigitalHumanGenerationJob job, String pollToken) {
        int failures = (job.getPollErrorCount() == null ? 0 : job.getPollErrorCount()) + 1;
        if (failures >= MAX_POLL_ERRORS) {
            failClaimedVideo(job, pollToken, "VIDEO_POLL_FAILED",
                "视频状态查询失败，请重试", failures);
            return;
        }
        int updated = mapper.update(null, claimedVideoUpdate(job, pollToken)
            .set(DigitalHumanGenerationJob::getPollErrorCount, failures)
            .set(DigitalHumanGenerationJob::getPollToken, null)
            .set(DigitalHumanGenerationJob::getPollLeaseUntil, null)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            job.setPollErrorCount(failures);
            job.setPollToken(null);
            job.setPollLeaseUntil(null);
        }
    }

    private void completeClaimedVideo(DigitalHumanGenerationJob job, String pollToken,
                                      DigitalHumanVideoPollDTO result) {
        try {
            requireProviderVideo(result.video(), result.mediaType(), result.fileExtension());
        } catch (RuntimeException exception) {
            failClaimedVideo(job, pollToken, "VIDEO_OUTPUT_INVALID",
                "视频生成结果无效，请重试");
            return;
        }
        DigitalHumanStoredMediaDTO output;
        try {
            String safeToken = pollToken.replace("-", "");
            output = storage.storeOutput(job.getId(), "video-" + safeToken + ".mp4",
                "video/mp4", result.video());
        } catch (RuntimeException exception) {
            failClaimedVideo(job, pollToken, "VIDEO_OUTPUT_STORE_FAILED",
                "视频生成结果保存失败，请重试");
            return;
        }
        int updated;
        try {
            updated = mapper.update(null, claimedVideoUpdate(job, pollToken)
                .set(DigitalHumanGenerationJob::getOutputMediaKey, output.key())
                .set(DigitalHumanGenerationJob::getOutputMediaType, output.mediaType())
                .set(DigitalHumanGenerationJob::getOutputMediaSize, output.size())
                .set(DigitalHumanGenerationJob::getOutputMediaSha256, output.sha256())
                .set(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.SUCCEEDED)
                .set(DigitalHumanGenerationJob::getStage, DigitalHumanJobStage.COMPLETED)
                .set(DigitalHumanGenerationJob::getProgress, 100)
                .set(DigitalHumanGenerationJob::getPollErrorCount, 0)
                .set(DigitalHumanGenerationJob::getPollToken, null)
                .set(DigitalHumanGenerationJob::getPollLeaseUntil, null)
                .set(DigitalHumanGenerationJob::getErrorCode, null)
                .set(DigitalHumanGenerationJob::getErrorMessage, null)
                .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        } catch (RuntimeException exception) {
            storage.delete(output.key());
            throw exception;
        }
        if (updated != 1) {
            storage.delete(output.key());
            return;
        }
        applyOutput(job, output);
        job.setStatus(DigitalHumanJobStatus.SUCCEEDED);
        job.setStage(DigitalHumanJobStage.COMPLETED);
        job.setProgress(100);
        job.setPollErrorCount(0);
        job.setPollToken(null);
        job.setPollLeaseUntil(null);
        job.setErrorCode(null);
        job.setErrorMessage(null);
    }

    private void failClaimedVideo(DigitalHumanGenerationJob job, String pollToken,
                                  String code, String message) {
        int failures = job.getPollErrorCount() == null ? 0 : job.getPollErrorCount();
        failClaimedVideo(job, pollToken, code, message, failures);
    }

    private void failClaimedVideo(DigitalHumanGenerationJob job, String pollToken,
                                  String code, String message, int failures) {
        int updated = mapper.update(null, claimedVideoUpdate(job, pollToken)
            .set(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.FAILED)
            .set(DigitalHumanGenerationJob::getStage, DigitalHumanJobStage.FAILED)
            .set(DigitalHumanGenerationJob::getErrorCode, code)
            .set(DigitalHumanGenerationJob::getErrorMessage, message)
            .set(DigitalHumanGenerationJob::getPollErrorCount, failures)
            .set(DigitalHumanGenerationJob::getPollToken, null)
            .set(DigitalHumanGenerationJob::getPollLeaseUntil, null)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            job.setStatus(DigitalHumanJobStatus.FAILED);
            job.setStage(DigitalHumanJobStage.FAILED);
            job.setErrorCode(code);
            job.setErrorMessage(message);
            job.setPollErrorCount(failures);
            job.setPollToken(null);
            job.setPollLeaseUntil(null);
        }
    }

    private LambdaUpdateWrapper<DigitalHumanGenerationJob> claimedVideoUpdate(
        DigitalHumanGenerationJob job, String pollToken) {
        return Wrappers.<DigitalHumanGenerationJob>lambdaUpdate()
            .eq(DigitalHumanGenerationJob::getId, job.getId())
            .eq(DigitalHumanGenerationJob::getTenantId, job.getTenantId())
            .eq(DigitalHumanGenerationJob::getOwnerUserId, job.getOwnerUserId())
            .eq(DigitalHumanGenerationJob::getJobType, DigitalHumanJobType.VIDEO_GENERATE)
            .eq(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.RUNNING)
            .eq(DigitalHumanGenerationJob::getPollToken, pollToken);
    }

    private DigitalHumanGenerationJob newJob(long id, DigitalHumanOwnerDTO owner, DigitalHumanJobType type,
                                              String key, String inputHash) {
        DigitalHumanGenerationJob job = new DigitalHumanGenerationJob();
        job.setId(id);
        job.setTenantId(owner.tenantId());
        job.setOwnerUserId(owner.userId());
        job.setJobType(type);
        job.setStatus(DigitalHumanJobStatus.QUEUED);
        job.setStage(DigitalHumanJobStage.QUEUED);
        job.setProgress(0);
        job.setIdempotencyKey(key);
        job.setInputHash(inputHash);
        job.setPollErrorCount(0);
        job.setVoiceConfirmed(false);
        job.setCreateBy(owner.userId());
        job.setUpdateBy(owner.userId());
        LocalDateTime now = LocalDateTime.now();
        job.setCreateTime(now);
        job.setUpdateTime(now);
        return job;
    }

    private void applyOutput(DigitalHumanGenerationJob job, DigitalHumanStoredMediaDTO output) {
        job.setOutputMediaKey(output.key());
        job.setOutputMediaType(output.mediaType());
        job.setOutputMediaSize(output.size());
        job.setOutputMediaSha256(output.sha256());
    }

    private boolean fail(DigitalHumanGenerationJob job, DigitalHumanJobStatus expectedStatus,
                         String code, String message) {
        if (expectedStatus != DigitalHumanJobStatus.QUEUED
            && expectedStatus != DigitalHumanJobStatus.RUNNING) {
            return false;
        }
        int updated = mapper.update(null, ownedStatusUpdate(job, expectedStatus)
            .set(DigitalHumanGenerationJob::getStatus, DigitalHumanJobStatus.FAILED)
            .set(DigitalHumanGenerationJob::getStage, DigitalHumanJobStage.FAILED)
            .set(DigitalHumanGenerationJob::getErrorCode, code)
            .set(DigitalHumanGenerationJob::getErrorMessage, message)
            .set(DigitalHumanGenerationJob::getPollToken, null)
            .set(DigitalHumanGenerationJob::getPollLeaseUntil, null)
            .set(DigitalHumanGenerationJob::getUpdateTime, LocalDateTime.now()));
        if (updated == 1) {
            job.setStatus(DigitalHumanJobStatus.FAILED);
            job.setStage(DigitalHumanJobStage.FAILED);
            job.setErrorCode(code);
            job.setErrorMessage(message);
            job.setPollToken(null);
            job.setPollLeaseUntil(null);
            return true;
        }
        return false;
    }

    private void insert(DigitalHumanGenerationJob job) {
        if (mapper.insert(job) != 1) {
            throw new ServiceException("创建数字人任务失败");
        }
    }

    private DigitalHumanJobDTO insertOrReplay(DigitalHumanGenerationJob job,
                                               DigitalHumanStoredMediaDTO input,
                                               DigitalHumanOwnerDTO owner,
                                               String inputHash) {
        try {
            insert(job);
            return null;
        } catch (DuplicateKeyException exception) {
            storage.delete(input.key());
            DigitalHumanGenerationJob winner = mapper.selectByIdempotency(
                owner.tenantId(), owner.userId(), job.getJobType(), job.getIdempotencyKey());
            if (winner == null) {
                throw new ServiceException("创建数字人任务失败");
            }
            return idempotent(winner, inputHash);
        } catch (RuntimeException exception) {
            storage.delete(input.key());
            throw exception;
        }
    }

    private DigitalHumanJobDTO dispatch(DigitalHumanGenerationJob job, Runnable task,
                                        String failureCode, String failureMessage) {
        try {
            executor.execute(task);
        } catch (RuntimeException exception) {
            fail(job, DigitalHumanJobStatus.QUEUED, failureCode, failureMessage);
        }
        return toDto(job);
    }

    private boolean isTimedOut(DigitalHumanGenerationJob job, LocalDateTime now) {
        if ((job.getStatus() != DigitalHumanJobStatus.QUEUED
            && job.getStatus() != DigitalHumanJobStatus.RUNNING) || job.getCreateTime() == null) {
            return false;
        }
        return !job.getCreateTime().plusMinutes(timeoutMinutes(job)).isAfter(now);
    }

    private int timeoutMinutes(DigitalHumanGenerationJob job) {
        return job.getJobType() == DigitalHumanJobType.VOICE_GENERATE
            ? VOICE_TIMEOUT_MINUTES : VIDEO_TIMEOUT_MINUTES;
    }

    private LambdaUpdateWrapper<DigitalHumanGenerationJob> ownedStatusUpdate(
        DigitalHumanGenerationJob job, DigitalHumanJobStatus expectedStatus) {
        return Wrappers.<DigitalHumanGenerationJob>lambdaUpdate()
            .eq(DigitalHumanGenerationJob::getId, job.getId())
            .eq(DigitalHumanGenerationJob::getTenantId, job.getTenantId())
            .eq(DigitalHumanGenerationJob::getOwnerUserId, job.getOwnerUserId())
            .eq(DigitalHumanGenerationJob::getJobType, job.getJobType())
            .eq(DigitalHumanGenerationJob::getStatus, expectedStatus);
    }

    private DigitalHumanGenerationJob requireOwned(Long jobId, DigitalHumanOwnerDTO owner) {
        DigitalHumanOwnerDTO safeOwner = requireOwner(owner);
        if (jobId == null || jobId <= 0) {
            throw new ServiceException("任务不存在");
        }
        DigitalHumanGenerationJob job = mapper.selectOwnedById(jobId, safeOwner.tenantId(), safeOwner.userId());
        if (job == null) {
            throw new ServiceException("任务不存在");
        }
        return job;
    }

    private DigitalHumanOwnerDTO requireOwner(DigitalHumanOwnerDTO owner) {
        if (owner == null || owner.tenantId() == null || owner.tenantId() <= 0
            || owner.userId() == null || owner.userId() <= 0) {
            throw new ServiceException("当前创作身份无效");
        }
        return owner;
    }

    private String requireKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new ServiceException("幂等键格式无效");
        }
        return key.toLowerCase(Locale.ROOT);
    }

    private String requireReferenceAudio(byte[] content, String mediaType, String fileName) {
        requireUploadSize(content);
        String type = normalizedMediaType(mediaType);
        String extension = extension(fileName);
        boolean valid = switch (type) {
            case "audio/wav", "audio/x-wav" -> extension.equals("wav") && isWav(content);
            case "audio/mpeg", "audio/mp3" -> extension.equals("mp3") && isMp3(content);
            case "audio/mp4", "audio/x-m4a", "audio/m4a" -> extension.equals("m4a") && isIsoBaseMedia(content);
            case "audio/flac", "audio/x-flac" -> extension.equals("flac") && startsWith(content, "fLaC", 0);
            default -> false;
        };
        if (!valid) {
            throw new ServiceException("上传文件格式或大小无效");
        }
        return switch (type) {
            case "audio/x-wav" -> "audio/wav";
            case "audio/mp3" -> "audio/mpeg";
            case "audio/x-m4a", "audio/m4a" -> "audio/mp4";
            case "audio/x-flac" -> "audio/flac";
            default -> type;
        };
    }

    private String requirePortrait(byte[] content, String mediaType, String fileName) {
        requireUploadSize(content);
        String type = normalizedMediaType(mediaType);
        String extension = extension(fileName);
        boolean valid = switch (type) {
            case "image/jpeg" -> (extension.equals("jpg") || extension.equals("jpeg")) && isJpeg(content);
            case "image/png" -> extension.equals("png") && isPng(content);
            case "image/webp" -> extension.equals("webp") && isWebp(content);
            default -> false;
        };
        if (!valid) {
            throw new ServiceException("上传文件格式或大小无效");
        }
        return type;
    }

    private void requireProviderVoice(byte[] content, String mediaType, String fileExtension) {
        if (content == null || content.length == 0 || content.length > MAX_VOICE_OUTPUT_BYTES
            || !(normalizedMediaType(mediaType).equals("audio/wav")
            || normalizedMediaType(mediaType).equals("audio/x-wav"))
            || !"wav".equals(normalizedExtension(fileExtension)) || !isWav(content)) {
            throw new ServiceException("生成媒体无效");
        }
    }

    private void requireProviderVideo(byte[] content, String mediaType, String fileExtension) {
        if (content == null || content.length == 0 || content.length > MAX_VIDEO_OUTPUT_BYTES
            || !normalizedMediaType(mediaType).equals("video/mp4")
            || !"mp4".equals(normalizedExtension(fileExtension)) || !isIsoBaseMedia(content)) {
            throw new ServiceException("生成媒体无效");
        }
    }

    private void requireUploadSize(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_UPLOAD_BYTES) {
            throw new ServiceException("上传文件格式或大小无效");
        }
    }

    private String normalizedMediaType(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        int parameters = mediaType.indexOf(';');
        String value = parameters < 0 ? mediaType : mediaType.substring(0, parameters);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : normalizedExtension(fileName.substring(separator + 1));
    }

    private String normalizedExtension(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isWav(byte[] content) {
        return content.length >= 12 && startsWith(content, "RIFF", 0) && startsWith(content, "WAVE", 8);
    }

    private boolean isMp3(byte[] content) {
        return startsWith(content, "ID3", 0)
            || (content.length >= 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xE0) == 0xE0);
    }

    private boolean isIsoBaseMedia(byte[] content) {
        return content.length >= 12 && startsWith(content, "ftyp", 4);
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3 && (content[0] & 0xFF) == 0xFF
            && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] content) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12 && startsWith(content, "RIFF", 0) && startsWith(content, "WEBP", 8);
    }

    private boolean startsWith(byte[] content, String signature, int offset) {
        byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private DigitalHumanJobDTO idempotent(DigitalHumanGenerationJob existing, String inputHash) {
        if (!Objects.equals(existing.getInputHash(), inputHash)) {
            throw new ServiceException("同一幂等键不能用于不同输入");
        }
        return toDto(existing);
    }

    private DigitalHumanJobDTO toDto(DigitalHumanGenerationJob job) {
        return new DigitalHumanJobDTO(job.getId(), job.getParentJobId(), job.getJobType(), job.getStatus(),
            job.getStage(), job.getProgress(), Boolean.TRUE.equals(job.getVoiceConfirmed()),
            job.getStatus() == DigitalHumanJobStatus.SUCCEEDED && job.getOutputMediaKey() != null,
            job.getErrorCode(), job.getErrorMessage(), job.getInputHash());
    }

    private String sha256(String domain, byte[]... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("digital-human-input-v2".getBytes(StandardCharsets.US_ASCII));
            updateFrame(digest, 0, domain.getBytes(StandardCharsets.UTF_8));
            for (int index = 0; index < values.length; index++) {
                byte[] value = Objects.requireNonNull(values[index]);
                updateFrame(digest, index + 1, value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private void updateFrame(MessageDigest digest, int index, byte[] value) {
        updateInt(digest, index);
        updateInt(digest, value.length);
        digest.update(value);
    }

    private void updateInt(MessageDigest digest, int value) {
        for (int shift = Integer.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }
}
