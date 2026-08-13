package org.dromara.aivideo.timeline.service;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;

import java.util.function.BooleanSupplier;

public interface ITimelineAiSuggestionService {
    TimelineImagePromptResultDTO generateImagePrompt(TimelineImagePromptCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineFancyTextSuggestionResultDTO suggestFancyText(TimelineFancyTextSuggestionCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
        TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested);
    TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
        CreationMediaHandle primaryAudio, TimelineTaskProgressListener progress,
        BooleanSupplier cancellationRequested);
}
