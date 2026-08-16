package org.dromara.aivideo.infra.timeline.ass;

import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.service.SubtitleDisplayNormalizer;

import java.text.Normalizer;
import java.util.Objects;

/**
 * Encodes user controlled text for the ASS Event Text field only.
 *
 * <p>ASS override blocks and drawing instructions are introduced by backslashes and braces.  These
 * characters are escaped one-for-one after the input has been normalized and checked, so the caller
 * can place the returned value directly after a generated dialogue prefix without granting the input
 * control over an ASS tag, style, path, or filter expression.</p>
 */
public final class AssTextEncoder {

    private static final int MAX_SUBTITLE_CODE_POINTS = limit("maxSubtitleCodePoints");
    private static final int MAX_FANCY_TEXT_CODE_POINTS = limit("maxFancyTextCodePoints");

    /**
     * Normalizes and encodes one subtitle Event Text value.
     */
    public String encodeSubtitle(String text) {
        return escapeAssLiteral(normalizeAndValidate(text, MAX_SUBTITLE_CODE_POINTS));
    }

    /**
     * Validates the frozen subtitle source/display pair before encoding its display text for ASS.
     */
    String encodeSubtitle(TimelineSubtitleElementDTO subtitle) {
        if (subtitle == null) {
            throw invalidText();
        }
        String displayText = normalizeAndValidate(subtitle.displayText(), MAX_SUBTITLE_CODE_POINTS);
        if (!displayText.equals(subtitle.displayText()) || !isNormalizedSubtitleDisplay(displayText)
            || !normalizeSubtitleSource(subtitle.sourceTextSnapshot()).equals(displayText)) {
            throw invalidText();
        }
        return escapeAssLiteral(displayText);
    }

    /**
     * Normalizes and encodes one fancy-text Event Text value.
     */
    public String encodeFancyText(String text) {
        return escapeAssLiteral(normalizeAndValidate(text, MAX_FANCY_TEXT_CODE_POINTS));
    }

    /**
     * Applies the frozen subtitle normalization rule to a source snapshot.
     *
     * <p>This deliberately does not escape ASS syntax: it is used for text-integrity comparisons,
     * while {@link #encodeSubtitle(String)} is the only method that produces script text.</p>
     */
    static String normalizeSubtitleSource(String sourceText) {
        if (sourceText == null) {
            throw invalidText();
        }
        requirePairedSurrogates(sourceText);
        String normalized = Normalizer.normalize(sourceText, Normalizer.Form.NFC);
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount < 1 || codePointCount > MAX_SUBTITLE_CODE_POINTS) {
            throw invalidText();
        }
        normalized.codePoints().forEach(AssTextEncoder::requireSafeSourceCodePoint);
        String result = SubtitleDisplayNormalizer.normalize(normalized);
        if (result.isEmpty()) {
            throw invalidText();
        }
        return result;
    }

    /**
     * Makes a plain, displayable text value available to the font measurer without applying ASS
     * escaping.  It remains package-private so render code cannot accidentally expose it as an
     * unescaped script fragment.
     */
    static String normalizeTextForMeasurement(String text) {
        return normalizeAndValidate(text, MAX_SUBTITLE_CODE_POINTS);
    }

    private static String normalizeAndValidate(String text, int maximumCodePoints) {
        if (text == null) {
            throw invalidText();
        }
        requirePairedSurrogates(text);
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount < 1 || codePointCount > maximumCodePoints || isBlank(normalized)) {
            throw invalidText();
        }
        normalized.codePoints().forEach(AssTextEncoder::requireSafeCodePoint);
        return normalized;
    }

    private static void requirePairedSurrogates(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw invalidText();
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw invalidText();
            }
        }
    }

    private static void requireSafeCodePoint(int codePoint) {
        if (codePoint <= 0x1F || (codePoint >= 0x7F && codePoint <= 0x9F)
            || codePoint == 0x2028 || codePoint == 0x2029 || isBidiControl(codePoint)) {
            throw invalidText();
        }
    }

    private static void requireSafeSourceCodePoint(int codePoint) {
        boolean normalizedWhitespaceControl = codePoint == '\t' || codePoint == '\r' || codePoint == '\n'
            || codePoint == 0x0085;
        if ((codePoint <= 0x1F && !normalizedWhitespaceControl)
            || (codePoint >= 0x7F && codePoint <= 0x9F && !normalizedWhitespaceControl)
            || isBidiControl(codePoint)) {
            throw invalidText();
        }
    }

    private static boolean isBidiControl(int codePoint) {
        return codePoint == 0x061C || codePoint == 0x200E || codePoint == 0x200F
            || (codePoint >= 0x202A && codePoint <= 0x202E)
            || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    private static boolean isBlank(String value) {
        return value.codePoints().allMatch(AssTextEncoder::isWhitespace);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isNormalizedSubtitleDisplay(String displayText) {
        return SubtitleDisplayNormalizer.normalize(displayText).equals(displayText);
    }

    private static String escapeAssLiteral(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\\') {
                escaped.append("\\\\");
            } else if (codePoint == '{') {
                escaped.append("\\{");
            } else if (codePoint == '}') {
                escaped.append("\\}");
            } else {
                escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    private static int limit(String name) {
        return Objects.requireNonNull(TimelineContractLimits.NUMERIC_LIMITS.get(name), name).intValueExact();
    }

    private static IllegalArgumentException invalidText() {
        return new IllegalArgumentException("timeline text is invalid");
    }
}
