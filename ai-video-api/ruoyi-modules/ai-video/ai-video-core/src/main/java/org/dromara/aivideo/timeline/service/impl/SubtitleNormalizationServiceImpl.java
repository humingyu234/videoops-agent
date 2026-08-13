package org.dromara.aivideo.timeline.service.impl;

import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineNormalizationChangeDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.service.ISubtitleFontMeasurementService;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** C0 subtitle normalization: NFC, punctuation/whitespace removal, then server font layout. */
@Service
public class SubtitleNormalizationServiceImpl implements ISubtitleNormalizationService {

    private final ISubtitleFontMeasurementService fontMeasurementService;

    public SubtitleNormalizationServiceImpl(ISubtitleFontMeasurementService fontMeasurementService) {
        this.fontMeasurementService = Objects.requireNonNull(fontMeasurementService, "fontMeasurementService");
    }

    @Override
    public NormalizationResult normalize(String scriptTextSnapshot, List<TimelineSubtitleElementDTO> subtitles,
                                         int canvasWidthPx, BigDecimal safeMarginRatio) {
        String script = Normalizer.normalize(nullToEmpty(scriptTextSnapshot), Normalizer.Form.NFC);
        if (script.isEmpty()) {
            if (subtitles == null || subtitles.isEmpty()) {
                return new NormalizationResult(List.of(), List.of());
            }
            throw textIntegrity("字幕原文不可用");
        }
        List<TimelineSubtitleElementDTO> ordered = subtitles == null ? List.of() : subtitles.stream()
            .sorted(Comparator.comparingInt(TimelineSubtitleElementDTO::sourceStartOffset)
                .thenComparing(TimelineSubtitleElementDTO::elementId))
            .toList();
        if (ordered.isEmpty()) {
            throw textIntegrity("字幕不能省略项目文案");
        }

        List<TimelineSubtitleElementDTO> normalized = new ArrayList<>();
        List<TimelineNormalizationChangeDTO> changes = new ArrayList<>();
        int previousEnd = -1;
        for (TimelineSubtitleElementDTO subtitle : ordered) {
            if (subtitle == null || subtitle.sourceStartOffset() < 0
                || subtitle.sourceEndOffset() <= subtitle.sourceStartOffset()
                || subtitle.sourceEndOffset() > codePointCount(script)
                || subtitle.startMs() < 0 || subtitle.endMs() <= subtitle.startMs()
                || subtitle.elementId() == null || subtitle.elementId().isBlank()) {
                throw textIntegrity("字幕范围无效");
            }
            if (previousEnd > subtitle.sourceStartOffset()) {
                throw textIntegrity("字幕原文范围重叠");
            }
            previousEnd = subtitle.sourceEndOffset();
            String sourceText = codePointSubstring(script, subtitle.sourceStartOffset(), subtitle.sourceEndOffset());
            if (!sourceText.equals(Normalizer.normalize(nullToEmpty(subtitle.sourceTextSnapshot()), Normalizer.Form.NFC))) {
                throw textIntegrity("字幕原文与项目快照不一致");
            }
            String displayText = normalizeDisplay(sourceText);
            if (displayText.isEmpty() || !displayText.equals(normalizeDisplay(nullToEmpty(subtitle.displayText())))) {
                throw textIntegrity("字幕展示文字不完整");
            }
            ISubtitleFontMeasurementService.SubtitleLayout layout = fontMeasurementService.fit(
                new ISubtitleFontMeasurementService.MeasureRequest(subtitle.elementId(), subtitle.fontCode(), displayText,
                    subtitle.outlineWidthPx(), canvasWidthPx, safeMarginRatio));
            validateLayout(layout, subtitle.fontCode(), displayText, subtitle);
            List<TimelineSubtitleElementDTO> pieces = split(subtitle, script, displayText, layout);
            normalized.addAll(pieces);
            if (!pieces.equals(List.of(subtitle))) {
                changes.add(new TimelineNormalizationChangeDTO(subtitle.elementId(), "subtitle_normalized",
                    digest(subtitle.displayText() + "\n" + subtitle.fontSizePx()),
                    digest(pieces.stream().map(TimelineSubtitleElementDTO::displayText).reduce("", String::concat)
                        + "\n" + layout.fontSizePx()), "字幕已按服务器规则规范化"));
            }
        }
        if (changes.size() > TimelineContractLimits.NUMERIC_LIMITS.get("maxNormalizationChanges").intValue()) {
            throw textIntegrity("字幕规范化变更过多");
        }
        String expected = normalizeDisplay(script);
        String actual = normalized.stream().map(TimelineSubtitleElementDTO::displayText).reduce("", String::concat);
        if (!expected.equals(actual)) {
            throw textIntegrity("字幕不能省略项目文案");
        }
        return new NormalizationResult(normalized, changes);
    }

    /** Exposed for C0 fixture tests; persistence always calls {@link #normalize}. */
    public String normalizeDisplay(String text) {
        String nfc = Normalizer.normalize(nullToEmpty(text), Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(nfc.length());
        nfc.codePoints().forEach(codePoint -> {
            if (!Character.isWhitespace(codePoint) && !isPunctuation(codePoint)) {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    private List<TimelineSubtitleElementDTO> split(TimelineSubtitleElementDTO original, String script,
                                                     String displayText,
                                                     ISubtitleFontMeasurementService.SubtitleLayout layout) {
        List<String> lines = layout.displaySegments();
        if (lines.size() == 1) {
            return List.of(copy(original, original.elementId(), original.sourceStartOffset(), original.sourceEndOffset(),
                original.startMs(), original.endMs(), codePointSubstring(script, original.sourceStartOffset(),
                    original.sourceEndOffset()), lines.getFirst(), layout));
        }
        int totalDisplayPoints = codePointCount(displayText);
        long duration = original.endMs() - original.startMs();
        if (duration < lines.size()) {
            throw textIntegrity("字幕时长不足以连续拆分");
        }
        List<Integer> visibleOffsets = visibleOffsets(codePointSubstring(script, original.sourceStartOffset(),
            original.sourceEndOffset()));
        List<TimelineSubtitleElementDTO> pieces = new ArrayList<>();
        int consumed = 0;
        int sourceStart = original.sourceStartOffset();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int linePoints = codePointCount(line);
            int nextConsumed = consumed + linePoints;
            int sourceEnd = index == lines.size() - 1 ? original.sourceEndOffset()
                : original.sourceStartOffset() + visibleOffsets.get(nextConsumed);
            long startMs = original.startMs() + duration * consumed / totalDisplayPoints;
            long endMs = index == lines.size() - 1 ? original.endMs()
                : original.startMs() + duration * nextConsumed / totalDisplayPoints;
            if (sourceEnd <= sourceStart || endMs <= startMs) {
                throw textIntegrity("字幕拆分范围无效");
            }
            pieces.add(copy(original, splitId(original.elementId(), index + 1), sourceStart, sourceEnd, startMs, endMs,
                codePointSubstring(script, sourceStart, sourceEnd), line, layout));
            consumed = nextConsumed;
            sourceStart = sourceEnd;
        }
        return List.copyOf(pieces);
    }

    private TimelineSubtitleElementDTO copy(TimelineSubtitleElementDTO source, String elementId,
                                             int sourceStart, int sourceEnd, long startMs, long endMs,
                                             String sourceText, String displayText,
                                             ISubtitleFontMeasurementService.SubtitleLayout layout) {
        return new TimelineSubtitleElementDTO(elementId, source.elementType(), startMs, endMs, source.zIndex(),
            source.enabled(), source.locked(), source.label(), sourceText, displayText, sourceStart, sourceEnd,
            layout.fontCode(), layout.fontVersion(), layout.fontSha256(), layout.fontSizePx(), source.color(),
            source.backgroundEnabled(), source.backgroundColor(), source.outlineEnabled(), source.outlineColor(),
            source.outlineWidthPx(), source.safeAreaAnchor(), source.alignment());
    }

    private void validateLayout(ISubtitleFontMeasurementService.SubtitleLayout layout, String expectedFontCode,
                                String expectedDisplayText, TimelineSubtitleElementDTO subtitle) {
        if (layout == null || !expectedFontCode.equals(layout.fontCode()) || layout.fontVersion() == null
            || !layout.fontVersion().matches("[0-9]+\\.[0-9]{3}") || layout.fontSha256() == null
            || !layout.fontSha256().matches("[0-9a-f]{64}") || layout.fontSizePx() < 12 || layout.fontSizePx() > 120
            || layout.displaySegments().isEmpty() || layout.displaySegments().stream().anyMatch(String::isEmpty)
            || !expectedDisplayText.equals(String.join("", layout.displaySegments()))) {
            throw textIntegrity("字幕字体测量结果无效");
        }
    }

    private List<Integer> visibleOffsets(String source) {
        String nfc = Normalizer.normalize(source, Normalizer.Form.NFC);
        List<Integer> offsets = new ArrayList<>();
        int index = 0;
        for (int codePoint : nfc.codePoints().toArray()) {
            if (!Character.isWhitespace(codePoint) && !isPunctuation(codePoint)) {
                offsets.add(index);
            }
            index++;
        }
        return offsets;
    }

    private boolean isPunctuation(int codePoint) {
        if (codePoint == '%') {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private int codePointCount(String value) {
        return value.codePointCount(0, value.length());
    }

    private String codePointSubstring(String value, int start, int end) {
        int startIndex = value.offsetByCodePoints(0, start);
        int endIndex = value.offsetByCodePoints(0, end);
        return value.substring(startIndex, endIndex);
    }

    private String splitId(String elementId, int index) {
        String suffix = "_%02d".formatted(index);
        if (elementId.length() + suffix.length() <= 64) {
            return elementId + suffix;
        }
        return elementId.substring(0, 56) + "_" + digest(elementId).substring(0, 4) + suffix;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(nullToEmpty(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ServiceException textIntegrity(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_TEXT_INTEGRITY_FAILED);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
