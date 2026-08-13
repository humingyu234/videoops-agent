package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.dromara.common.core.exception.ServiceException;

import java.util.function.BooleanSupplier;

/**
 * Fail-closed timeline suggestion port used when the media subsystem is disabled.
 */
public final class UnavailableTimelineAiSuggestionService implements ITimelineAiSuggestionService {

    @Override
    public TimelineImagePromptResultDTO generateImagePrompt(TimelineImagePromptCommandDTO command,
                                                            TimelineTaskProgressListener progress,
                                                            BooleanSupplier cancellationRequested) {
        throw unavailable();
    }

    @Override
    public TimelineFancyTextSuggestionResultDTO suggestFancyText(TimelineFancyTextSuggestionCommandDTO command,
                                                                  TimelineTaskProgressListener progress,
                                                                  BooleanSupplier cancellationRequested) {
        throw unavailable();
    }

    @Override
    public TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
                                                                    TimelineTaskProgressListener progress,
                                                                    BooleanSupplier cancellationRequested) {
        throw unavailable();
    }

    @Override
    public TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
                                                              CreationMediaHandle primaryAudio,
                                                              TimelineTaskProgressListener progress,
                                                              BooleanSupplier cancellationRequested) {
        throw unavailable();
    }

    private static ServiceException unavailable() {
        return new ServiceException("时间轴 AI 建议能力暂不可用", TimelineErrorCodes.TIMELINE_RENDER_UNAVAILABLE);
    }
}
