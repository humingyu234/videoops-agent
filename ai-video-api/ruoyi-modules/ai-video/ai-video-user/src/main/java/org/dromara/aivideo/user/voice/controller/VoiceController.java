package org.dromara.aivideo.user.voice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.dto.UploadVoiceSampleDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.voice.domain.bo.CreateVoiceBo;
import org.dromara.aivideo.user.voice.domain.bo.RetryVoiceTranscriptionBo;
import org.dromara.aivideo.user.voice.domain.bo.StartVoiceTranscriptionBo;
import org.dromara.aivideo.user.voice.domain.bo.UpdateVoiceTranscriptBo;
import org.dromara.aivideo.user.voice.domain.vo.VoiceAccessUrlVo;
import org.dromara.aivideo.user.voice.domain.vo.VoiceVo;
import org.dromara.aivideo.voice.dto.*;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
public class VoiceController {
    private final IVoiceService voiceService;
    private final IAssetService assetService;
    private final AppLoginHelper loginHelper;

    @PostMapping("/api/voices")
    @SaCheckPermission(value = "aivideo:voice:upload", type = "app")
    @RepeatSubmit
    public R<VoiceVo> upload(@RequestPart("file") MultipartFile file,
                             @Valid @RequestPart("metadata") CreateVoiceBo metadata) {
        if (file == null || file.isEmpty()) throw new ServiceException("声音文件不能为空", 46201);
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        AssetDTO uploaded = null;
        try (InputStream raw = file.getInputStream()) {
            MessageDigest digest = sha256();
            DigestInputStream stream = new DigestInputStream(raw, digest);
            uploaded = assetService.uploadVoiceSample(new UploadVoiceSampleDTO(file.getOriginalFilename(),
                file.getContentType(), file.getSize(), stream), principal);
            boolean transcriptionRequested = !Boolean.FALSE.equals(metadata.transcriptionRequested());
            String fingerprint = fingerprint(digest.digest(), metadata);
            VoiceDTO created = voiceService.create(new CreateVoiceDTO(uploaded.assetId(),
                metadata.idempotencyKey(), fingerprint, metadata.name(), metadata.gender(), metadata.style(),
                metadata.tags(), metadata.note(), transcriptionRequested), principal);
            if (!uploaded.assetId().equals(created.assetId())) {
                assetService.deleteOwnedAsset(uploaded.assetId(), principal);
            }
            return R.ok(VoiceVo.from(created));
        } catch (IOException exception) {
            cleanup(uploaded, principal);
            throw new ServiceException("声音文件读取失败", 46201);
        } catch (RuntimeException exception) {
            cleanup(uploaded, principal);
            throw exception;
        }
    }

    @GetMapping("/api/voices")
    @SaCheckPermission(value = "aivideo:voice:query", type = "app")
    public R<PageResult<VoiceVo>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String voiceType,
                                       @RequestParam(required = false) String transcriptionStatus,
                                       PageQuery pageQuery) {
        PageResult<VoiceDTO> page = voiceService.queryPage(new VoiceQueryDTO(keyword, voiceType,
            transcriptionStatus), loginHelper.getPrincipal(), pageQuery);
        return R.ok(PageResult.build(page.getRows().stream().map(VoiceVo::from).toList(), page.getTotal()));
    }

    @GetMapping("/api/voices/{voiceId}")
    @SaCheckPermission(value = "aivideo:voice:query", type = "app")
    public R<VoiceVo> detail(@PathVariable String voiceId) {
        return R.ok(VoiceVo.from(voiceService.queryById(voiceId, loginHelper.getPrincipal())));
    }

    @GetMapping("/api/voices/{voiceId}/access-url")
    @SaCheckPermission(value = "aivideo:voice:query", type = "app")
    public R<VoiceAccessUrlVo> accessUrl(@PathVariable String voiceId) {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        VoiceDTO voice = voiceService.queryById(voiceId, principal);
        AssetDTO asset = assetService.requireOwnedReadyVoiceAsset(voice.assetId(), principal);
        return R.ok(new VoiceAccessUrlVo(voiceService.createAccessUrl(voiceId, principal),
            LocalDateTime.now().plusSeconds(120), asset.contentType(), asset.originalName()));
    }

    @PutMapping("/api/voices/{voiceId}/transcript")
    @SaCheckPermission(value = "aivideo:voice:edit", type = "app")
    @RepeatSubmit
    public R<VoiceVo> updateTranscript(@PathVariable String voiceId,
                                       @Valid @RequestBody UpdateVoiceTranscriptBo body) {
        return R.ok(VoiceVo.from(voiceService.updateTranscript(new UpdateVoiceTranscriptDTO(
            voiceId, body.transcriptText(), body.expectedRevision()), loginHelper.getPrincipal())));
    }

    @PostMapping("/api/voices/{voiceId}/transcription/retry")
    @SaCheckPermission(value = "aivideo:voice:transcribe", type = "app")
    @RepeatSubmit
    public R<VoiceVo> retry(@PathVariable String voiceId,
                            @Valid @RequestBody RetryVoiceTranscriptionBo body) {
        return R.ok(VoiceVo.from(voiceService.retryTranscription(new RetryVoiceTranscriptionDTO(
            voiceId, body.expectedRevision()), loginHelper.getPrincipal())));
    }

    @PostMapping("/api/voices/{voiceId}/transcription/start")
    @SaCheckPermission(value = "aivideo:voice:transcribe", type = "app")
    @RepeatSubmit
    public R<VoiceVo> startTranscription(@PathVariable String voiceId,
                                         @Valid @RequestBody StartVoiceTranscriptionBo body) {
        return R.ok(VoiceVo.from(voiceService.startTranscription(new StartVoiceTranscriptionDTO(
            voiceId, body.expectedRevision()), loginHelper.getPrincipal())));
    }

    @PostMapping("/api/voices/{voiceId}/transcription/resync")
    @SaCheckPermission(value = "aivideo:voice:transcribe", type = "app")
    @RepeatSubmit
    public R<VoiceVo> resync(@PathVariable String voiceId,
                             @Valid @RequestBody RetryVoiceTranscriptionBo body) {
        return R.ok(VoiceVo.from(voiceService.resyncTranscription(new RetryVoiceTranscriptionDTO(
            voiceId, body.expectedRevision()), loginHelper.getPrincipal())));
    }

    @DeleteMapping("/api/voices/{voiceId}")
    @SaCheckPermission(value = "aivideo:voice:delete", type = "app")
    @Log(title = "用户声音", businessType = BusinessType.DELETE)
    public R<Void> delete(@PathVariable String voiceId) {
        voiceService.deleteOwnedVoice(voiceId, loginHelper.getPrincipal());
        return R.ok();
    }

    private String fingerprint(byte[] fileDigest, CreateVoiceBo metadata) {
        MessageDigest digest = sha256();
        digest.update(fileDigest);
        digest.update(metadata.idempotencyKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(metadata.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(String.valueOf(metadata.gender()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(String.valueOf(metadata.style()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(String.valueOf(metadata.tags()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(String.valueOf(metadata.note()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update(Boolean.toString(!Boolean.FALSE.equals(metadata.transcriptionRequested()))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void cleanup(AssetDTO asset, AppPrincipalSnapshotDTO principal) {
        if (asset == null) return;
        try {
            assetService.deleteOwnedAsset(asset.assetId(), principal);
        } catch (RuntimeException ignored) {
            // 原异常优先；孤儿文件由运维清理。
        }
    }
}
