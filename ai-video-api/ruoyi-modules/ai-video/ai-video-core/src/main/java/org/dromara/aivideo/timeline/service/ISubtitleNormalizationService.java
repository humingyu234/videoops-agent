package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;

import java.math.BigDecimal;
import java.util.List;

/** Applies the frozen subtitle text and layout normalization before persistence. */
public interface ISubtitleNormalizationService {

    NormalizationResult normalize(String scriptTextSnapshot, List<TimelineSubtitleElementDTO> subtitles,
                                  int canvasWidthPx, BigDecimal safeMarginRatio);

    record NormalizationResult(
        List<TimelineSubtitleElementDTO> subtitles,
        List<TimelineNormalizationChangeDTO> normalizationChanges
    ) {
        public NormalizationResult {
            subtitles = subtitles == null ? List.of() : List.copyOf(subtitles);
            normalizationChanges = normalizationChanges == null ? List.of() : List.copyOf(normalizationChanges);
        }
    }
}
