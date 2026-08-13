package org.dromara.aivideo.infra.voice.listener;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.infra.voice.WhisperProperties;
import org.dromara.aivideo.infra.voice.client.WhisperTranscriptionException;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionFailureDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class VoiceTranscriptionScheduler {
    private final IVoiceService voiceService;
    private final IWhisperTranscriptionService whisperService;
    private final IAssetService assetService;
    private final WhisperProperties properties;

    @Scheduled(fixedDelayString = "${aivideo.whisper.poll-delay-ms:1000}")
    public void executeOnce() {
        if (!properties.isEnabled()) return;
        VoiceTranscriptionLeaseDTO lease = voiceService.claimNext(properties.getWorkerId(), Instant.now());
        if (lease == null) return;
        try {
            AppPrincipalSnapshotDTO principal = internalPrincipal(lease);
            VoiceTranscriptionResultDTO result = assetService.readOwnedVoiceAsset(
                lease.assetId(), principal, (asset, input) -> whisperService.transcribe(lease, asset, input));
            voiceService.completeTranscription(lease, result);
        } catch (WhisperTranscriptionException exception) {
            voiceService.failTranscription(lease, new VoiceTranscriptionFailureDTO(
                exception.getFailureCode(), exception.getMessage(), exception.isRetryable()));
        } catch (RuntimeException exception) {
            voiceService.failTranscription(lease, new VoiceTranscriptionFailureDTO(
                "WHISPER_UNAVAILABLE", "本地 Whisper 暂时不可用", true));
        }
    }

    private AppPrincipalSnapshotDTO internalPrincipal(VoiceTranscriptionLeaseDTO lease) {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            lease.workspaceId(), "internal", lease.tenantId(), "app_user", lease.ownerId(),
            "app_user", lease.ownerId(), "internal", Set.of("aivideo:voice:query"), 1L, null);
        return new AppPrincipalSnapshotDTO(lease.ownerId(), "whisper-worker", "internal",
            1L, 1L, 1L, 1L, workspace);
    }
}
