package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.infra.voice.client.WhisperTranscriptionException;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO;
import org.dromara.aivideo.voice.service.IWhisperTranscriptionService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Uses the C0 generic Whisper overload only as a fallback for an empty trusted cue list. */
public final class WhisperTimelineSubtitleAlignmentService {
    private final IWhisperTranscriptionService whisper;
    private final TimelineSubtitleAlignmentMapper mapper;

    WhisperTimelineSubtitleAlignmentService(IWhisperTranscriptionService whisper,
                                            TimelineSubtitleAlignmentMapper mapper) {
        this.whisper = Objects.requireNonNull(whisper, "whisper");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
                                                              BooleanSupplier cancellationRequested) {
        checkCancelled(cancellationRequested);
        TimelineSubtitleAlignmentResultDTO result = mapper.mapTrusted(command);
        checkCancelled(cancellationRequested);
        return result;
    }

    TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
                                                        CreationMediaHandle primaryAudio,
                                                        BooleanSupplier cancellationRequested) {
        if (command == null || command.trustedCues() == null || !command.trustedCues().isEmpty()
            || primaryAudio == null) {
            throw invalidInput();
        }
        try (CreationMediaHandle handle = primaryAudio) {
            checkCancelled(cancellationRequested);
            CreationAssetResolveDTO metadata = handle.metadata();
            if (metadata == null || command.primaryAudioAssetId() == null
                || !command.primaryAudioAssetId().equals(metadata.assetId())
                || metadata.assetType() != CreationAssetType.AUDIO
                || metadata.usageType() != TimelineAssetUsageType.PRIMARY_AUDIO
                || !metadata.hasAudioStream() || metadata.sizeBytes() <= 0
                || handle.offset() != 0 || handle.length() != metadata.sizeBytes()
                || handle.totalSize() != metadata.sizeBytes()) {
                throw invalidInput();
            }
            try (InputStream input = handle.stream()) {
                if (input == null) {
                    throw inputUnavailable();
                }
                VoiceTranscriptionResultDTO transcription = whisper.transcribe(new WhisperTranscriptionInputDTO(
                    command.taskId(), fileName(metadata.mimeType()), metadata.mimeType(), metadata.sizeBytes()), input);
                checkCancelled(cancellationRequested);
                if (transcription == null) {
                    throw remoteFailure(true);
                }
                return mapper.mapWhisper(command, transcription.transcriptTimeline());
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (WhisperTranscriptionException exception) {
            throw remoteFailure(exception.isRetryable());
        } catch (IOException | RuntimeException exception) {
            throw inputUnavailable();
        }
    }

    private static String fileName(String mimeType) {
        if (mimeType == null || mimeType.isBlank() || mimeType.length() > 128 || !mimeType.contains("/")) {
            throw invalidInput();
        }
        return switch (mimeType.toLowerCase(java.util.Locale.ROOT)) {
            case "audio/wav", "audio/x-wav" -> "timeline-primary-audio.wav";
            case "audio/mpeg" -> "timeline-primary-audio.mp3";
            case "audio/mp4", "audio/aac" -> "timeline-primary-audio.m4a";
            case "audio/ogg" -> "timeline-primary-audio.ogg";
            default -> "timeline-primary-audio.bin";
        };
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

    private static TimelineExecutionException invalidInput() {
        return new TimelineExecutionException("subtitle alignment input is invalid",
            TimelineExecutionFailureCode.INPUT_INVALID, false, null);
    }

    private static TimelineExecutionException inputUnavailable() {
        return new TimelineExecutionException("primary audio is unavailable", TimelineExecutionFailureCode.INPUT_UNAVAILABLE,
            true, null);
    }

    private static TimelineExecutionException remoteFailure(boolean retryable) {
        return new TimelineExecutionException("subtitle transcription is unavailable",
            TimelineExecutionFailureCode.REMOTE_FAILURE, retryable, null);
    }

    private static CancellationException cancelled() {
        return new CancellationException("timeline subtitle alignment cancelled");
    }
}
