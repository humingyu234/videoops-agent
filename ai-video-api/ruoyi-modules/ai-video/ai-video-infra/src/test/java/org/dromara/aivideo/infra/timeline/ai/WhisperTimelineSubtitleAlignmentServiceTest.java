package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WhisperTimelineSubtitleAlignmentServiceTest {

    @Test
    void usesGenericWhisperContractOnlyWhenTrustedCuesAreEmptyAndClosesHandle() {
        AtomicBoolean genericCalled = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        IWhisperTranscriptionService whisper = new IWhisperTranscriptionService() {
            @Override
            public VoiceTranscriptionResultDTO transcribe(VoiceTranscriptionLeaseDTO lease, AssetDTO asset,
                                                          InputStream input) {
                throw new AssertionError("legacy lease contract must not be used");
            }

            @Override
            public VoiceTranscriptionResultDTO transcribe(WhisperTranscriptionInputDTO inputMetadata,
                                                          InputStream input) {
                genericCalled.set(true);
                assertThat(inputMetadata.requestId()).isEqualTo("task-1");
                return new VoiceTranscriptionResultDTO("task-1", "你好世界", "zh", 700,
                    List.of(new VoiceTranscriptCueDTO("你好", 0, 300),
                        new VoiceTranscriptCueDTO("世界", 300, 700)));
            }
        };
        WhisperTimelineSubtitleAlignmentService service = new WhisperTimelineSubtitleAlignmentService(
            whisper, new TimelineSubtitleAlignmentMapper());

        assertThat(service.alignFromAudio(command(List.of()), new TestHandle(closed), () -> false).subtitles())
            .hasSize(2);
        assertThat(genericCalled).isTrue();
        assertThat(closed).isTrue();
    }

    @Test
    void rejectsAHandleThatIsNotTheCommandPrimaryAudioBeforeCallingWhisper() {
        AtomicBoolean genericCalled = new AtomicBoolean();
        IWhisperTranscriptionService whisper = new IWhisperTranscriptionService() {
            @Override
            public VoiceTranscriptionResultDTO transcribe(VoiceTranscriptionLeaseDTO lease, AssetDTO asset,
                                                           InputStream input) {
                throw new AssertionError("legacy lease contract must not be used");
            }

            @Override
            public VoiceTranscriptionResultDTO transcribe(WhisperTranscriptionInputDTO inputMetadata,
                                                           InputStream input) {
                genericCalled.set(true);
                throw new AssertionError("Whisper must not be called for an invalid primary audio handle");
            }
        };
        WhisperTimelineSubtitleAlignmentService service = new WhisperTimelineSubtitleAlignmentService(
            whisper, new TimelineSubtitleAlignmentMapper());

        List<CreationAssetResolveDTO> invalidMetadata = List.of(
            new CreationAssetResolveDTO("audio-2", "audio/wav", "a".repeat(64),
                CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO, 4, 700L,
                null, null, false, true),
            new CreationAssetResolveDTO("audio-1", "audio/wav", "a".repeat(64),
                CreationAssetType.AUDIO, TimelineAssetUsageType.BACKGROUND_MUSIC, 4, 700L,
                null, null, false, true),
            new CreationAssetResolveDTO("audio-1", "audio/wav", "a".repeat(64),
                CreationAssetType.VIDEO, TimelineAssetUsageType.PRIMARY_AUDIO, 4, 700L,
                null, null, false, true));

        for (CreationAssetResolveDTO metadata : invalidMetadata) {
            assertThatThrownBy(() -> service.alignFromAudio(command(List.of()),
                new TestHandle(new AtomicBoolean(), metadata), () -> false))
                .isInstanceOf(TimelineExecutionException.class);
        }
        assertThat(genericCalled).isFalse();
    }

    private static TimelineSubtitleAlignmentCommandDTO command(
        List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> cues) {
        return new TimelineSubtitleAlignmentCommandDTO(
            "task-1", "project-1", "revision-1", "audio-1", "你好，世界！", "zh", cues);
    }

    private static final class TestHandle implements CreationMediaHandle {
        private final AtomicBoolean closed;
        private final CreationAssetResolveDTO metadata;

        private TestHandle(AtomicBoolean closed) {
            this(closed, defaultMetadata());
        }

        private TestHandle(AtomicBoolean closed, CreationAssetResolveDTO metadata) {
            this.closed = closed;
            this.metadata = metadata;
        }

        @Override
        public CreationAssetResolveDTO metadata() {
            return metadata;
        }

        private static CreationAssetResolveDTO defaultMetadata() {
            return new CreationAssetResolveDTO("audio-1", "audio/wav", "a".repeat(64),
                CreationAssetType.AUDIO, TimelineAssetUsageType.PRIMARY_AUDIO, 4, 700L,
                null, null, false, true);
        }

        @Override
        public InputStream stream() {
            return new ByteArrayInputStream(new byte[]{1, 2, 3, 4});
        }

        @Override public long offset() { return 0; }
        @Override public long length() { return 4; }
        @Override public long totalSize() { return 4; }

        @Override
        public void close() throws IOException {
            closed.set(true);
        }
    }
}
