package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TimelineAiSuggestionServiceImplTest {

    @Test
    void springUsesThePublicProductionConstructor() {
        IWhisperTranscriptionService whisper = (lease, asset, input) -> {
            throw new AssertionError("Whisper must not be called during Spring assembly");
        };

        new ApplicationContextRunner()
            .withPropertyValues("aivideo.timeline.enabled=true")
            .withBean(TimelineInfrastructureProperties.class, TimelineInfrastructureProperties::new)
            .withBean(IWhisperTranscriptionService.class, () -> whisper)
            .withUserConfiguration(SpringAssemblyConfiguration.class)
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(TimelineAiSuggestionServiceImpl.class);
            });
    }

    @Test
    void mapsTrustedCuesWithoutCallingWhisper() {
        AtomicBoolean whisperCalled = new AtomicBoolean();
        IWhisperTranscriptionService whisper = new IWhisperTranscriptionService() {
            @Override
            public VoiceTranscriptionResultDTO transcribe(VoiceTranscriptionLeaseDTO lease, AssetDTO asset,
                                                          InputStream input) {
                whisperCalled.set(true);
                throw new AssertionError("Whisper must not be used for trusted cues");
            }
        };
        TimelineAiSuggestionServiceImpl service = new TimelineAiSuggestionServiceImpl(
            new DeepSeekTimelineSuggestionClient(new org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties.Ai()),
            new WhisperTimelineSubtitleAlignmentService(whisper, new TimelineSubtitleAlignmentMapper()),
            new TimelineSubtitleAlignmentMapper());
        TimelineSubtitleAlignmentCommandDTO command = new TimelineSubtitleAlignmentCommandDTO(
            "task-1", "project-1", "revision-1", "audio-1", "你好世界", "zh",
            List.of(new TimelineSubtitleAlignmentCommandDTO.TrustedCue("你好世界", 0, 500)));

        assertThat(service.alignFromTrustedCues(command, progress -> { }, () -> false).sourceType())
            .isEqualTo("trusted_cue");
        assertThat(whisperCalled).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @Import(TimelineAiSuggestionServiceImpl.class)
    static class SpringAssemblyConfiguration {
    }
}
