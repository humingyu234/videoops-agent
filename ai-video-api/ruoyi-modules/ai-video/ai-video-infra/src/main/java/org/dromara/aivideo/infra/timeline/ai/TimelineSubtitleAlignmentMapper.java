package org.dromara.aivideo.infra.timeline.ai;

import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Maps trusted or Whisper timing tokens onto the immutable, normalized script snapshot. */
public final class TimelineSubtitleAlignmentMapper {

    private static final int MAX_SUBTITLE_CODE_POINTS = limit("maxSubtitleCodePoints");
    private static final int MAX_PROJECT_SCRIPT_CODE_POINTS = limit("maxProjectScriptCodePoints");
    private static final long MAX_DURATION_MS = TimelineContractLimits.NUMERIC_LIMITS
        .get("maxDurationMs").longValueExact();

    TimelineSubtitleAlignmentResultDTO mapTrusted(TimelineSubtitleAlignmentCommandDTO command) {
        if (command == null || command.trustedCues() == null || command.trustedCues().isEmpty()) {
            throw invalid();
        }
        List<Cue> cues = command.trustedCues().stream()
            .map(cue -> cue == null ? null : new Cue(cue.text(), cue.startMs(), cue.endMs())).toList();
        return map("trusted_cue", command, cues);
    }

    TimelineSubtitleAlignmentResultDTO mapWhisper(TimelineSubtitleAlignmentCommandDTO command,
                                                    List<VoiceTranscriptCueDTO> transcriptTimeline) {
        if (transcriptTimeline == null || transcriptTimeline.isEmpty()) {
            throw invalid();
        }
        List<Cue> cues = transcriptTimeline.stream()
            .map(cue -> cue == null ? null : new Cue(cue.text(), cue.startMillis(), cue.endMillis())).toList();
        return map("whisper", command, cues);
    }

    private TimelineSubtitleAlignmentResultDTO map(String sourceType, TimelineSubtitleAlignmentCommandDTO command,
                                                    List<Cue> cues) {
        if (command == null || isBlank(command.taskId()) || isBlank(command.scriptTextSnapshot())
            || cues == null || cues.isEmpty()) {
            throw invalid();
        }
        String normalizedScript = normalize(command.scriptTextSnapshot(), MAX_PROJECT_SCRIPT_CODE_POINTS);
        StringBuilder concatenated = new StringBuilder();
        List<TimelineSubtitleAlignmentResultDTO.AlignedSubtitle> subtitles = new ArrayList<>(cues.size());
        long previousEnd = -1;
        int sourceOffset = 0;
        for (Cue cue : cues) {
            if (cue == null || cue.startMs() < 0 || cue.endMs() <= cue.startMs() || cue.endMs() > MAX_DURATION_MS
                || cue.startMs() < previousEnd) {
                throw invalid();
            }
            String displayText = normalize(cue.text(), MAX_SUBTITLE_CODE_POINTS);
            int displayCodePoints = displayText.codePointCount(0, displayText.length());
            if (sourceOffset > MAX_PROJECT_SCRIPT_CODE_POINTS - displayCodePoints) {
                throw invalid();
            }
            concatenated.append(displayText);
            subtitles.add(new TimelineSubtitleAlignmentResultDTO.AlignedSubtitle(sourceOffset,
                sourceOffset + displayCodePoints, displayText, cue.startMs(), cue.endMs()));
            sourceOffset += displayCodePoints;
            previousEnd = cue.endMs();
        }
        if (!normalizedScript.contentEquals(concatenated)) {
            throw invalid();
        }
        return new TimelineSubtitleAlignmentResultDTO(command.taskId(), sourceType, List.copyOf(subtitles));
    }

    private static String normalize(String source, int maximumCodePoints) {
        if (source == null) {
            throw invalid();
        }
        requirePairedSurrogates(source);
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFC);
        if (normalized.codePointCount(0, normalized.length()) < 1
            || normalized.codePointCount(0, normalized.length()) > maximumCodePoints) {
            throw invalid();
        }
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            requireSafeSourceCodePoint(codePoint);
            if (!isWhitespace(codePoint) && !isUnicodePunctuation(codePoint)) {
                result.appendCodePoint(codePoint);
            }
        });
        if (result.isEmpty()) {
            throw invalid();
        }
        return result.toString();
    }

    private static void requirePairedSurrogates(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw invalid();
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw invalid();
            }
        }
    }

    private static void requireSafeSourceCodePoint(int codePoint) {
        boolean whitespaceControl = codePoint == '\t' || codePoint == '\r' || codePoint == '\n' || codePoint == 0x0085;
        if ((codePoint <= 0x1F && !whitespaceControl)
            || (codePoint >= 0x7F && codePoint <= 0x9F && !whitespaceControl)
            || codePoint == 0x061C || codePoint == 0x200E || codePoint == 0x200F
            || (codePoint >= 0x202A && codePoint <= 0x202E)
            || (codePoint >= 0x2066 && codePoint <= 0x2069)) {
            throw invalid();
        }
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
            || codePoint == 0x0085 || codePoint == 0x2028 || codePoint == 0x2029;
    }

    private static boolean isUnicodePunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                 Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                 Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int limit(String key) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(key).intValueExact();
    }

    private static TimelineExecutionException invalid() {
        return new TimelineExecutionException("subtitle alignment is invalid", TimelineExecutionFailureCode.INPUT_INVALID,
            false, null);
    }

    private record Cue(String text, long startMs, long endMs) {
    }
}
