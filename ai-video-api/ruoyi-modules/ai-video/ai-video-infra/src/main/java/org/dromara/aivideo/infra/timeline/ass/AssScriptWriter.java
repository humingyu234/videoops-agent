package org.dromara.aivideo.infra.timeline.ass;

import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineVisualTransformDTO;
import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;
import org.dromara.aivideo.timeline.enums.TimelineElementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Produces a deterministic ASS document from typed, frozen timeline elements.
 *
 * <p>Every structural ASS field is generated from a fixed mapping.  The only user-provided value
 * that reaches the document is text after {@link AssTextEncoder} has encoded it for an Event Text
 * field.  In particular, labels, element identifiers, font paths, and suggestion metadata never
 * become part of the generated script.</p>
 */
public final class AssScriptWriter {

    private static final int CANVAS_WIDTH = limit("canvasWidth");
    private static final int CANVAS_HEIGHT = limit("canvasHeight");
    private static final int MAX_DURATION_MS = limit("maxDurationMs");
    private static final int MAX_PROJECT_SCRIPT_CODE_POINTS = limit("maxProjectScriptCodePoints");
    private static final int MIN_FONT_SIZE = limit("minFontSizePx");
    private static final int MAX_FONT_SIZE = limit("maxFontSizePx");
    private static final int MAX_OUTLINE_WIDTH = limit("maxOutlineWidthPx");
    private static final int SAFE_MARGIN_X = CANVAS_WIDTH / 20;
    private static final int SAFE_MARGIN_Y = CANVAS_HEIGHT / 20;
    private static final AssTextEncoder TEXT_ENCODER = new AssTextEncoder();
    private static final Map<FancyTextTemplateCode, TemplateDefinition> TEMPLATES = templates();

    /**
     * Writes one self-contained ASS script.  The canvas is frozen by C0 and may not be changed by
     * a timeline payload.
     */
    public String write(int canvasWidth,
                        int canvasHeight,
                        List<TimelineSubtitleElementDTO> subtitles,
                        List<TimelineFancyTextElementDTO> fancyTexts) {
        if (canvasWidth != CANVAS_WIDTH || canvasHeight != CANVAS_HEIGHT || subtitles == null || fancyTexts == null) {
            throw invalidScript();
        }
        List<SubtitleEvent> subtitleEvents = collectSubtitles(subtitles);
        List<FancyEvent> fancyEvents = collectFancyTexts(fancyTexts);
        StringBuilder script = new StringBuilder(4096);
        appendHeader(script);
        for (SubtitleEvent event : subtitleEvents) {
            appendSubtitleStyle(script, event);
        }
        for (FancyEvent event : fancyEvents) {
            appendFancyStyle(script, event);
        }
        appendEventsHeader(script);
        for (SubtitleEvent event : subtitleEvents) {
            appendSubtitleDialogue(script, event);
        }
        for (FancyEvent event : fancyEvents) {
            appendFancyDialogue(script, event);
        }
        return script.toString();
    }

    private static List<SubtitleEvent> collectSubtitles(List<TimelineSubtitleElementDTO> subtitles) {
        validateSubtitleSourceIntegrity(subtitles);
        List<SubtitleEvent> events = new ArrayList<>();
        int originalIndex = 0;
        for (TimelineSubtitleElementDTO subtitle : subtitles) {
            if (subtitle == null) {
                throw invalidScript();
            }
            if (subtitle.enabled()) {
                validateBaseElement(subtitle.elementId(), subtitle.elementType(), subtitle.startMs(), subtitle.endMs(),
                    subtitle.zIndex(), TimelineElementType.SUBTITLE);
                TimelineFontMeasurer.FontDefinition font = TimelineFontMeasurer.requireDefaultDefinition(
                    subtitle.fontCode(), subtitle.fontVersion(), subtitle.fontSha256());
                validateSubtitleStyle(subtitle);
                String text = TEXT_ENCODER.encodeSubtitle(subtitle);
                int alignment = subtitleAlignment(subtitle.safeAreaAnchor(), subtitle.alignment());
                Position position = subtitlePosition(subtitle.safeAreaAnchor(), subtitle.alignment());
                events.add(new SubtitleEvent(subtitle.startMs(), subtitle.endMs(), subtitle.zIndex(), originalIndex,
                    font, subtitle.fontSizePx(), assColor(subtitle.color()),
                    subtitle.backgroundEnabled() ? assColor(subtitle.backgroundColor()) : "&H00000000&",
                    subtitle.outlineEnabled() ? assColor(subtitle.outlineColor()) : "&H00000000&",
                    subtitle.outlineEnabled() ? subtitle.outlineWidthPx() : 0, alignment, position, text));
            }
            originalIndex++;
        }
        events.sort(Comparator.comparingLong(SubtitleEvent::startMs).thenComparingInt(SubtitleEvent::zIndex)
            .thenComparingInt(SubtitleEvent::originalIndex));
        return List.copyOf(events);
    }

    private static void validateSubtitleSourceIntegrity(List<TimelineSubtitleElementDTO> subtitles) {
        List<TimelineSubtitleElementDTO> enabledSubtitles = new ArrayList<>();
        for (TimelineSubtitleElementDTO subtitle : subtitles) {
            if (subtitle == null) {
                throw invalidScript();
            }
            if (subtitle.enabled()) {
                enabledSubtitles.add(subtitle);
            }
        }
        enabledSubtitles.sort(Comparator.comparingInt(TimelineSubtitleElementDTO::sourceStartOffset)
            .thenComparingInt(TimelineSubtitleElementDTO::sourceEndOffset));
        int previousEnd = -1;
        StringBuilder normalizedSource = new StringBuilder();
        StringBuilder displayText = new StringBuilder();
        for (TimelineSubtitleElementDTO subtitle : enabledSubtitles) {
            if (subtitle.sourceStartOffset() < 0 || subtitle.sourceEndOffset() <= subtitle.sourceStartOffset()
                || subtitle.sourceEndOffset() > MAX_PROJECT_SCRIPT_CODE_POINTS
                || subtitle.sourceStartOffset() < previousEnd) {
                throw invalidScript();
            }
            TEXT_ENCODER.encodeSubtitle(subtitle);
            normalizedSource.append(AssTextEncoder.normalizeSubtitleSource(subtitle.sourceTextSnapshot()));
            displayText.append(subtitle.displayText());
            if (normalizedSource.codePointCount(0, normalizedSource.length()) > MAX_PROJECT_SCRIPT_CODE_POINTS
                || displayText.codePointCount(0, displayText.length()) > MAX_PROJECT_SCRIPT_CODE_POINTS) {
                throw invalidScript();
            }
            previousEnd = subtitle.sourceEndOffset();
        }
        if (!normalizedSource.toString().contentEquals(displayText)) {
            throw invalidScript();
        }
    }

    private static List<FancyEvent> collectFancyTexts(List<TimelineFancyTextElementDTO> fancyTexts) {
        List<FancyEvent> events = new ArrayList<>();
        int originalIndex = 0;
        for (TimelineFancyTextElementDTO fancyText : fancyTexts) {
            if (fancyText == null) {
                throw invalidScript();
            }
            if (fancyText.enabled()) {
                validateBaseElement(fancyText.elementId(), fancyText.elementType(), fancyText.startMs(), fancyText.endMs(),
                    fancyText.zIndex(), TimelineElementType.FANCY_TEXT);
                if (fancyText.templateCode() == null) {
                    throw invalidScript();
                }
                TemplateDefinition template = TEMPLATES.get(fancyText.templateCode());
                if (template == null) {
                    throw invalidScript();
                }
                TimelineFontMeasurer.FontDefinition font = TimelineFontMeasurer.requireDefaultDefinition(
                    fancyText.fontCode(), fancyText.fontVersion(), fancyText.fontSha256());
                Transform transform = validateTransform(fancyText.transform());
                String intensity = requireIntensity(fancyText.animationIntensity());
                validateAnimationDurations(fancyText.enterDurationMs(), fancyText.exitDurationMs());
                Position position = fancyPosition(transform);
                events.add(new FancyEvent(fancyText.startMs(), fancyText.endMs(), fancyText.zIndex(), originalIndex,
                    template, font, assColor(fancyText.color()), assColor(fancyText.accentColor()), transform, intensity,
                    fancyText.enterDurationMs(), fancyText.exitDurationMs(), position,
                    TEXT_ENCODER.encodeFancyText(fancyText.text())));
            }
            originalIndex++;
        }
        events.sort(Comparator.comparingLong(FancyEvent::startMs).thenComparingInt(FancyEvent::zIndex)
            .thenComparingInt(FancyEvent::originalIndex));
        return List.copyOf(events);
    }

    private static void validateBaseElement(String elementId,
                                            TimelineElementType actualType,
                                            long startMs,
                                            long endMs,
                                            int zIndex,
                                            TimelineElementType expectedType) {
        if (!safeIdentifier(elementId) || actualType != expectedType || startMs < 0 || endMs <= startMs
            || endMs > MAX_DURATION_MS || zIndex < 0 || zIndex > 999) {
            throw invalidScript();
        }
    }

    private static void validateSubtitleStyle(TimelineSubtitleElementDTO subtitle) {
        if (subtitle.fontSizePx() < MIN_FONT_SIZE || subtitle.fontSizePx() > MAX_FONT_SIZE
            || subtitle.outlineWidthPx() < 0 || subtitle.outlineWidthPx() > MAX_OUTLINE_WIDTH
            || !validColor(subtitle.color())) {
            throw invalidScript();
        }
        if (subtitle.backgroundEnabled()) {
            if (!validColor(subtitle.backgroundColor())) {
                throw invalidScript();
            }
        } else if (subtitle.backgroundColor() != null) {
            throw invalidScript();
        }
        if (subtitle.outlineEnabled()) {
            if (!validColor(subtitle.outlineColor()) || subtitle.outlineWidthPx() == 0) {
                throw invalidScript();
            }
        } else if (subtitle.outlineColor() != null || subtitle.outlineWidthPx() != 0) {
            throw invalidScript();
        }
    }

    private static Transform validateTransform(TimelineVisualTransformDTO transform) {
        if (transform == null) {
            throw invalidScript();
        }
        BigDecimal x = ratio(transform.xRatio(), true);
        BigDecimal y = ratio(transform.yRatio(), true);
        BigDecimal width = ratio(transform.widthRatio(), false);
        BigDecimal height = ratio(transform.heightRatio(), false);
        BigDecimal rotation = boundedDecimal(transform.rotationDeg(), new BigDecimal("-180"), new BigDecimal("180"));
        BigDecimal opacity = ratio(transform.opacity(), true);
        return new Transform(x, y, width, height, rotation, opacity);
    }

    private static BigDecimal ratio(BigDecimal value, boolean zeroAllowed) {
        BigDecimal result = boundedDecimal(value, BigDecimal.ZERO, BigDecimal.ONE);
        if (!zeroAllowed && result.signum() == 0) {
            throw invalidScript();
        }
        return result;
    }

    private static BigDecimal boundedDecimal(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null || value.scale() > 4 || value.scale() < 0 || value.toString().contains("E")
            || value.toString().contains("e") || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw invalidScript();
        }
        return value.stripTrailingZeros();
    }

    private static String requireIntensity(String intensity) {
        if (!"subtle".equals(intensity) && !"normal".equals(intensity) && !"strong".equals(intensity)) {
            throw invalidScript();
        }
        return intensity;
    }

    private static void validateAnimationDurations(long enterDurationMs, long exitDurationMs) {
        if (enterDurationMs < 0 || exitDurationMs < 0 || enterDurationMs > 3_000 || exitDurationMs > 3_000) {
            throw invalidScript();
        }
    }

    private static int subtitleAlignment(String anchor, String alignment) {
        int row = switch (anchor) {
            case "upper" -> 2;
            case "center" -> 1;
            case "lower" -> 0;
            default -> throw invalidScript();
        };
        int column = switch (alignment) {
            case "left" -> 1;
            case "center" -> 2;
            case "right" -> 3;
            default -> throw invalidScript();
        };
        return row * 3 + column;
    }

    private static Position subtitlePosition(String anchor, String alignment) {
        int x = switch (alignment) {
            case "left" -> SAFE_MARGIN_X;
            case "center" -> CANVAS_WIDTH / 2;
            case "right" -> CANVAS_WIDTH - SAFE_MARGIN_X;
            default -> throw invalidScript();
        };
        int y = switch (anchor) {
            case "upper" -> SAFE_MARGIN_Y;
            case "center" -> CANVAS_HEIGHT / 2;
            case "lower" -> CANVAS_HEIGHT - SAFE_MARGIN_Y;
            default -> throw invalidScript();
        };
        return new Position(x, y);
    }

    private static Position fancyPosition(Transform transform) {
        int usableWidth = CANVAS_WIDTH - SAFE_MARGIN_X * 2;
        int usableHeight = CANVAS_HEIGHT - SAFE_MARGIN_Y * 2;
        int x = SAFE_MARGIN_X + transform.xRatio().multiply(BigDecimal.valueOf(usableWidth))
            .setScale(0, RoundingMode.HALF_UP).intValueExact();
        int y = SAFE_MARGIN_Y + transform.yRatio().multiply(BigDecimal.valueOf(usableHeight))
            .setScale(0, RoundingMode.HALF_UP).intValueExact();
        return new Position(x, y);
    }

    private static void appendHeader(StringBuilder script) {
        script.append("[Script Info]\n")
            .append("ScriptType: v4.00+\n")
            .append("PlayResX: ").append(CANVAS_WIDTH).append('\n')
            .append("PlayResY: ").append(CANVAS_HEIGHT).append('\n')
            .append("ScaledBorderAndShadow: yes\n")
            .append("YCbCr Matrix: None\n\n")
            .append("[V4+ Styles]\n")
            .append("Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,"
                + "Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,"
                + "Alignment,MarginL,MarginR,MarginV,Encoding\n");
    }

    private static void appendSubtitleStyle(StringBuilder script, SubtitleEvent event) {
        appendStyle(script, event.styleName(), event.font().familyName(), event.fontSizePx(), event.primaryColor(),
            event.primaryColor(), event.outlineColor(), event.backgroundColor(), event.backgroundColor().equals("&H00000000&")
                ? 1 : 3, event.outlineWidthPx(), event.alignment());
    }

    private static void appendFancyStyle(StringBuilder script, FancyEvent event) {
        appendStyle(script, event.styleName(), event.font().familyName(), event.template().fontSizePx(),
            event.primaryColor(), event.accentColor(), event.template().outlineColor(), "&H00000000&", 1,
            event.template().outlineWidthPx(), 5);
    }

    private static void appendStyle(StringBuilder script,
                                    String styleName,
                                    String familyName,
                                    int fontSize,
                                    String primaryColor,
                                    String secondaryColor,
                                    String outlineColor,
                                    String backgroundColor,
                                    int borderStyle,
                                    int outlineWidth,
                                    int alignment) {
        script.append("Style: ").append(styleName).append(',').append(familyName).append(',').append(fontSize)
            .append(',').append(primaryColor).append(',').append(secondaryColor).append(',').append(outlineColor)
            .append(',').append(backgroundColor).append(",0,0,0,0,100,100,0,0,").append(borderStyle).append(',')
            .append(outlineWidth).append(",0,").append(alignment).append(",0,0,0,1\n");
    }

    private static void appendEventsHeader(StringBuilder script) {
        script.append("\n[Events]\n")
            .append("Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n");
    }

    private static void appendSubtitleDialogue(StringBuilder script, SubtitleEvent event) {
        appendDialoguePrefix(script, event.zIndex(), event.startMs(), event.endMs(), event.styleName());
        script.append("{\\an").append(event.alignment()).append("\\pos(").append(event.position().x()).append(',')
            .append(event.position().y()).append(")}").append(event.text()).append('\n');
    }

    private static void appendFancyDialogue(StringBuilder script, FancyEvent event) {
        appendDialoguePrefix(script, event.zIndex(), event.startMs(), event.endMs(), event.styleName());
        script.append("{\\an5\\pos(").append(event.position().x()).append(',').append(event.position().y())
            .append(")\\frz").append(decimal(event.transform().rotationDeg()))
            .append("\\alpha&H").append(alpha(event.transform().opacity())).append("&")
            .append(event.template().eventTags(event.transform(), event.intensity(), event.enterDurationMs(),
                event.exitDurationMs(), alpha(event.transform().opacity())))
            .append("}").append(event.text()).append('\n');
    }

    private static void appendDialoguePrefix(StringBuilder script,
                                             int layer,
                                             long startMs,
                                             long endMs,
                                             String styleName) {
        script.append("Dialogue: ").append(layer).append(',').append(assTime(startMs, false)).append(',')
            .append(assTime(endMs, true)).append(',').append(styleName).append(",,0,0,0,,");
    }

    private static String assTime(long millis, boolean end) {
        long centiseconds = end ? Math.max(1, (millis + 9) / 10) : millis / 10;
        long hours = centiseconds / 360_000;
        long minutes = (centiseconds / 6_000) % 60;
        long seconds = (centiseconds / 100) % 60;
        long fraction = centiseconds % 100;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, fraction);
    }

    private static String assColor(String cssColor) {
        if (!validColor(cssColor)) {
            throw invalidScript();
        }
        int alpha = 255 - Integer.parseInt(cssColor.substring(7, 9), 16);
        return String.format(Locale.ROOT, "&H%02X%s%s%s&", alpha, cssColor.substring(5, 7),
            cssColor.substring(3, 5), cssColor.substring(1, 3));
    }

    private static boolean validColor(String color) {
        return color != null && color.matches("#[0-9A-F]{8}");
    }

    private static String alpha(BigDecimal opacity) {
        int alpha = BigDecimal.ONE.subtract(opacity).multiply(BigDecimal.valueOf(255))
            .setScale(0, RoundingMode.HALF_UP).intValueExact();
        return String.format(Locale.ROOT, "%02X", alpha);
    }

    private static String decimal(BigDecimal value) {
        String plain = value.stripTrailingZeros().toPlainString();
        return "-0".equals(plain) ? "0" : plain;
    }

    private static int scalePercent(BigDecimal ratio) {
        int percentage = ratio.multiply(BigDecimal.valueOf(200)).setScale(0, RoundingMode.HALF_UP).intValueExact();
        return Math.max(1, percentage);
    }

    private static void appendScalePulse(StringBuilder tags,
                                         long enterDurationMs,
                                         int scaleX,
                                         int scaleY,
                                         int peakPercent) {
        if (enterDurationMs == 0) {
            return;
        }
        long peakAt = Math.max(1, enterDurationMs / 2);
        tags.append("\\t(0,").append(peakAt).append(",\\fscx").append(percentOf(scaleX, peakPercent))
            .append("\\fscy").append(percentOf(scaleY, peakPercent)).append(')');
        if (peakAt < enterDurationMs) {
            tags.append("\\t(").append(peakAt).append(',').append(enterDurationMs).append(",\\fscx")
                .append(scaleX).append("\\fscy").append(scaleY).append(')');
        }
    }

    private static void appendBorderImpact(StringBuilder tags,
                                           long enterDurationMs,
                                           int peakOutlineWidth,
                                           int baseOutlineWidth) {
        tags.append("\\bord").append(baseOutlineWidth);
        if (enterDurationMs == 0) {
            return;
        }
        long peakAt = Math.max(1, enterDurationMs / 2);
        tags.append("\\t(0,").append(peakAt).append(",\\bord").append(peakOutlineWidth).append(')');
        if (peakAt < enterDurationMs) {
            tags.append("\\t(").append(peakAt).append(',').append(enterDurationMs).append(",\\bord")
                .append(baseOutlineWidth).append(')');
        }
    }

    private static void appendNeonBreathe(StringBuilder tags,
                                           long enterDurationMs,
                                           int peakAlpha,
                                           String baseAlpha) {
        tags.append("\\blur").append(Math.max(1, peakAlpha / 16));
        if (enterDurationMs == 0) {
            return;
        }
        long peakAt = Math.max(1, enterDurationMs / 2);
        tags.append("\\t(0,").append(peakAt).append(",\\alpha&H")
            .append(String.format(Locale.ROOT, "%02X", peakAlpha)).append("&)");
        if (peakAt < enterDurationMs) {
            tags.append("\\t(").append(peakAt).append(',').append(enterDurationMs).append(",\\alpha&H")
                .append(baseAlpha).append("&)");
        }
    }

    private static int percentOf(int value, int percentage) {
        return Math.max(1, Math.toIntExact((value * (long) percentage + 50L) / 100L));
    }

    private static boolean safeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > limit("maxKeyAsciiLength")) {
            return false;
        }
        for (int index = 0; index < identifier.length(); index++) {
            char character = identifier.charAt(index);
            if (!(character >= 'a' && character <= 'z') && !(character >= 'A' && character <= 'Z')
                && !(character >= '0' && character <= '9') && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    private static Map<FancyTextTemplateCode, TemplateDefinition> templates() {
        Map<FancyTextTemplateCode, TemplateDefinition> templates = new EnumMap<>(FancyTextTemplateCode.class);
        templates.put(FancyTextTemplateCode.KEYWORD_POP, template(FancyTextTemplateCode.KEYWORD_POP, 72,
            "&H00000000&", 3, 108, 116, 124));
        templates.put(FancyTextTemplateCode.GOLD_IMPACT, template(FancyTextTemplateCode.GOLD_IMPACT, 78,
            "&H0000AAFF&", 5, 5, 7, 8));
        templates.put(FancyTextTemplateCode.NEON_BREATHE, template(FancyTextTemplateCode.NEON_BREATHE, 70,
            "&H00FF00FF&", 6, 32, 53, 80));
        templates.put(FancyTextTemplateCode.HANDWRITING_REVEAL, template(FancyTextTemplateCode.HANDWRITING_REVEAL,
            68, "&H00000000&", 2, 104, 108, 112));
        templates.put(FancyTextTemplateCode.BUBBLE_BOUNCE, template(FancyTextTemplateCode.BUBBLE_BOUNCE, 74,
            "&H00FFFFFF&", 4, 108, 114, 120));
        templates.put(FancyTextTemplateCode.TITLE_WIPE, template(FancyTextTemplateCode.TITLE_WIPE, 82,
            "&H00000000&", 3, 104, 108, 112));
        if (templates.size() != FancyTextTemplateCode.values().length) {
            throw new IllegalStateException("timeline fancy-text template registry is incomplete");
        }
        return Map.copyOf(templates);
    }

    private static TemplateDefinition template(FancyTextTemplateCode code,
                                               int fontSizePx,
                                               String outlineColor,
                                               int outlineWidthPx,
                                               int subtleValue,
                                               int normalValue,
                                               int strongValue) {
        return new TemplateDefinition(code, fontSizePx, outlineColor, outlineWidthPx,
            Map.of("subtle", subtleValue, "normal", normalValue, "strong", strongValue));
    }

    private static int limit(String name) {
        return Objects.requireNonNull(TimelineContractLimits.NUMERIC_LIMITS.get(name), name).intValueExact();
    }

    private static IllegalArgumentException invalidScript() {
        return new IllegalArgumentException("timeline ASS script input is invalid");
    }

    private record Position(int x, int y) {
    }

    private record Transform(BigDecimal xRatio,
                             BigDecimal yRatio,
                             BigDecimal widthRatio,
                             BigDecimal heightRatio,
                             BigDecimal rotationDeg,
                             BigDecimal opacity) {
    }

    private record TemplateDefinition(FancyTextTemplateCode code,
                                      int fontSizePx,
                                      String outlineColor,
                                      int outlineWidthPx,
                                      Map<String, Integer> valuesByIntensity) {
        private String eventTags(Transform transform,
                                 String intensity,
                                 long enterDurationMs,
                                 long exitDurationMs,
                                 String baseAlpha) {
            Integer intensityValue = valuesByIntensity.get(intensity);
            if (intensityValue == null) {
                throw invalidScript();
            }
            int scaleX = scalePercent(transform.widthRatio());
            int scaleY = scalePercent(transform.heightRatio());
            StringBuilder tags = new StringBuilder("\\fscx").append(scaleX).append("\\fscy").append(scaleY)
                .append("\\fad(").append(enterDurationMs).append(',').append(exitDurationMs).append(')');
            switch (code) {
                case KEYWORD_POP, BUBBLE_BOUNCE -> appendScalePulse(tags, enterDurationMs, scaleX, scaleY,
                    intensityValue);
                case GOLD_IMPACT -> appendBorderImpact(tags, enterDurationMs, intensityValue, outlineWidthPx);
                case NEON_BREATHE -> appendNeonBreathe(tags, enterDurationMs, intensityValue, baseAlpha);
                case HANDWRITING_REVEAL -> {
                    tags.append("\\i1");
                    appendScalePulse(tags, enterDurationMs, scaleX, scaleY, intensityValue);
                }
                case TITLE_WIPE -> {
                    tags.append("\\b1");
                    appendScalePulse(tags, enterDurationMs, scaleX, scaleY, intensityValue);
                }
            }
            return tags.toString();
        }
    }

    private record SubtitleEvent(long startMs,
                                 long endMs,
                                 int zIndex,
                                 int originalIndex,
                                 TimelineFontMeasurer.FontDefinition font,
                                 int fontSizePx,
                                 String primaryColor,
                                 String backgroundColor,
                                 String outlineColor,
                                 int outlineWidthPx,
                                 int alignment,
                                 Position position,
                                 String text) {
        private String styleName() {
            return String.format(Locale.ROOT, "SUB_%04d", originalIndex + 1);
        }
    }

    private record FancyEvent(long startMs,
                              long endMs,
                              int zIndex,
                              int originalIndex,
                              TemplateDefinition template,
                              TimelineFontMeasurer.FontDefinition font,
                              String primaryColor,
                              String accentColor,
                              Transform transform,
                              String intensity,
                              long enterDurationMs,
                              long exitDurationMs,
                              Position position,
                              String text) {
        private String styleName() {
            return String.format(Locale.ROOT, "FANCY_%04d", originalIndex + 1);
        }
    }
}
