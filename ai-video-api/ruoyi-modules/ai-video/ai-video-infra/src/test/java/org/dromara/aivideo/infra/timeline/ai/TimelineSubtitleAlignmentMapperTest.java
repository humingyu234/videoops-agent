package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TimelineSubtitleAlignmentMapperTest {

    private final TimelineSubtitleAlignmentMapper mapper = new TimelineSubtitleAlignmentMapper();

    @Test
    void mapsMatchingTrustedCuesUsingFrozenNormalizedScriptOffsets() {
        TimelineSubtitleAlignmentResultDTO result = mapper.mapTrusted(command(List.of(
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("你好，", 0, 300),
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("世界！", 300, 700))));

        assertThat(result.sourceType()).isEqualTo("trusted_cue");
        assertThat(result.subtitles()).containsExactly(
            new TimelineSubtitleAlignmentResultDTO.AlignedSubtitle(0, 2, "你好", 0, 300),
            new TimelineSubtitleAlignmentResultDTO.AlignedSubtitle(2, 4, "世界", 300, 700));
    }

    @Test
    void rejectsCueTextThatDoesNotExactlyCoverFrozenScript() {
        assertThatThrownBy(() -> mapper.mapTrusted(command(List.of(
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("你好", 0, 300),
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("地球", 300, 700)))))
            .isInstanceOf(TimelineExecutionException.class);
    }

    private static TimelineSubtitleAlignmentCommandDTO command(
        List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> cues) {
        return new TimelineSubtitleAlignmentCommandDTO(
            "task-1", "project-1", "revision-1", "audio-1", "你好，世界！", "zh", cues);
    }
}
