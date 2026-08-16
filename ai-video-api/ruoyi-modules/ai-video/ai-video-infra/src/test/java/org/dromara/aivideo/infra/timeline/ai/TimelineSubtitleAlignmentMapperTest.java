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

    @Test
    void preservesNumericSemanticSeparatorsAndRejectsCuesThatDropThem() {
        TimelineSubtitleAlignmentResultDTO result = mapper.mapTrusted(command("价格3.14元，12:30开播。", List.of(
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("价格 3.14 元，", 0, 300),
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("12:30 开播。", 300, 700))));

        assertThat(result.subtitles()).containsExactly(
            new TimelineSubtitleAlignmentResultDTO.AlignedSubtitle(0, 7, "价格3.14元", 0, 300),
            new TimelineSubtitleAlignmentResultDTO.AlignedSubtitle(7, 14, "12:30开播", 300, 700));
        assertThatThrownBy(() -> mapper.mapTrusted(command("价格3.14元", List.of(
            new TimelineSubtitleAlignmentCommandDTO.TrustedCue("价格314元", 0, 300)))))
            .isInstanceOf(TimelineExecutionException.class);
    }

    private static TimelineSubtitleAlignmentCommandDTO command(
        List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> cues) {
        return command("你好，世界！", cues);
    }

    private static TimelineSubtitleAlignmentCommandDTO command(String script,
        List<TimelineSubtitleAlignmentCommandDTO.TrustedCue> cues) {
        return new TimelineSubtitleAlignmentCommandDTO(
            "task-1", "project-1", "revision-1", "audio-1", script, "zh", cues);
    }
}
