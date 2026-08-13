package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.task.enums.AiTaskStage;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineProgressDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** C0 timeline AI port: bounded suggestions plus deterministic subtitle alignment only. */
@Component
@ConditionalOnProperty(prefix = "aivideo.timeline", name = "enabled", havingValue = "true")
public final class TimelineAiSuggestionServiceImpl implements ITimelineAiSuggestionService {
    private final DeepSeekTimelineSuggestionClient deepSeek;
    private final WhisperTimelineSubtitleAlignmentService subtitleAlignment;
    private final TimelineSubtitleAlignmentMapper subtitleMapper;

    @Autowired
    public TimelineAiSuggestionServiceImpl(TimelineInfrastructureProperties properties,
                                           IWhisperTranscriptionService whisper) {
        Objects.requireNonNull(properties, "properties");
        this.deepSeek = new DeepSeekTimelineSuggestionClient(properties.getAi());
        this.subtitleMapper = new TimelineSubtitleAlignmentMapper();
        this.subtitleAlignment = new WhisperTimelineSubtitleAlignmentService(whisper, subtitleMapper);
    }

    TimelineAiSuggestionServiceImpl(DeepSeekTimelineSuggestionClient deepSeek,
                                    WhisperTimelineSubtitleAlignmentService subtitleAlignment,
                                    TimelineSubtitleAlignmentMapper subtitleMapper) {
        this.deepSeek = Objects.requireNonNull(deepSeek, "deepSeek");
        this.subtitleAlignment = Objects.requireNonNull(subtitleAlignment, "subtitleAlignment");
        this.subtitleMapper = Objects.requireNonNull(subtitleMapper, "subtitleMapper");
    }

    @Override
    public TimelineImagePromptResultDTO generateImagePrompt(TimelineImagePromptCommandDTO command,
                                                             TimelineTaskProgressListener progress,
                                                             BooleanSupplier cancellationRequested) {
        return execute(progress, cancellationRequested,
            () -> deepSeek.generateImagePrompt(command, cancellationRequested));
    }

    @Override
    public TimelineFancyTextSuggestionResultDTO suggestFancyText(TimelineFancyTextSuggestionCommandDTO command,
                                                                  TimelineTaskProgressListener progress,
                                                                  BooleanSupplier cancellationRequested) {
        return execute(progress, cancellationRequested,
            () -> deepSeek.suggestFancyText(command, cancellationRequested));
    }

    @Override
    public TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
                                                                    TimelineTaskProgressListener progress,
                                                                    BooleanSupplier cancellationRequested) {
        return execute(progress, cancellationRequested,
            () -> subtitleMapper.mapTrusted(command));
    }

    @Override
    public TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
                                                              CreationMediaHandle primaryAudio,
                                                              TimelineTaskProgressListener progress,
                                                              BooleanSupplier cancellationRequested) {
        return execute(progress, cancellationRequested,
            () -> subtitleAlignment.alignFromAudio(command, primaryAudio, cancellationRequested));
    }

    private <T> T execute(TimelineTaskProgressListener progress, BooleanSupplier cancellationRequested,
                          Supplier<T> operation) {
        checkCancelled(cancellationRequested);
        report(progress, AiTaskStage.PREPARING_ASSETS, 5, "preparing timeline AI suggestion");
        T result = operation.get();
        checkCancelled(cancellationRequested);
        report(progress, AiTaskStage.VERIFYING_OUTPUT, 90, "verifying timeline AI suggestion");
        return result;
    }

    private static void report(TimelineTaskProgressListener listener, AiTaskStage stage, int percent,
                               String safeMessage) {
        if (listener == null) {
            throw callbackFailed();
        }
        try {
            listener.onProgress(new TimelineProgressDTO(stage, percent, safeMessage));
        } catch (RuntimeException exception) {
            throw callbackFailed();
        }
    }

    private static void checkCancelled(BooleanSupplier cancellationRequested) {
        try {
            if (cancellationRequested == null || cancellationRequested.getAsBoolean()) {
                throw cancelled();
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw cancelled();
        }
    }

    private static TimelineExecutionException callbackFailed() {
        return new TimelineExecutionException("timeline AI progress callback failed",
            TimelineExecutionFailureCode.CALLBACK_FAILED, true, null);
    }

    private static CancellationException cancelled() {
        return new CancellationException("timeline AI suggestion cancelled");
    }
}
