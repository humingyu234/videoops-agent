package org.dromara.aivideo.infra.voice.listener;

import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.dromara.aivideo.infra.voice.WhisperProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class VoiceTranscriptionSchedulerTest {
    @Test
    void schedulerDoesNothingWhenNoLeaseIsAvailable() {
        IVoiceService voiceService = mock(IVoiceService.class);
        IWhisperTranscriptionService whisper = mock(IWhisperTranscriptionService.class);
        IAssetService assets = mock(IAssetService.class);
        WhisperProperties properties = new WhisperProperties();
        properties.setWorkerId("test-worker");
        when(voiceService.claimNext(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any())).thenReturn(null);

        new VoiceTranscriptionScheduler(voiceService, whisper, assets, properties).executeOnce();

        verifyNoInteractions(whisper, assets);
    }
}
