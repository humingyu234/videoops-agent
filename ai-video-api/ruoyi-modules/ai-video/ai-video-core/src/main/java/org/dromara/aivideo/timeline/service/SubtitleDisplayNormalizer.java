package org.dromara.aivideo.timeline.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Applies the shared frozen rule for comparing subtitle source and display text. */
public final class SubtitleDisplayNormalizer {

    private SubtitleDisplayNormalizer() {
    }

    /** Removes display-only punctuation and whitespace while preserving numeric punctuation. */
    public static String normalize(String text) {
        return analyze(text).text();
    }

    /** Returns source code-point offsets retained by {@link #normalize(String)}. */
    public static List<Integer> visibleSourceOffsets(String text) {
        return analyze(text).sourceOffsets();
    }

    private static NormalizedDisplay analyze(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        int[] codePoints = normalized.codePoints().toArray();
        StringBuilder display = new StringBuilder(normalized.length());
        List<Integer> sourceOffsets = new ArrayList<>(codePoints.length);
        for (int index = 0; index < codePoints.length; index++) {
            if (isWhitespace(codePoints[index]) || isDisplayOnlyPunctuation(codePoints, index)) {
                continue;
            }
            display.appendCodePoint(codePoints[index]);
            sourceOffsets.add(index);
        }
        return new NormalizedDisplay(display.toString(), List.copyOf(sourceOffsets));
    }

    private static boolean isDisplayOnlyPunctuation(int[] codePoints, int index) {
        return switch (codePoints[index]) {
            case ',', '!', '?', ';', '\u3001', '\u3002', '\uFF01', '\uFF0C', '\uFF1B', '\uFF1F' -> true;
            default -> false;
        };
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
            || codePoint == 0x0085 || codePoint == 0x2028 || codePoint == 0x2029;
    }

    private record NormalizedDisplay(String text, List<Integer> sourceOffsets) {
    }
}
