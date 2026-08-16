package org.dromara.aivideo.infra.timeline.ass;

import org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineVisualTransformDTO;
import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AssScriptWriterTest {

    private static final String SANS_SHA = "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b";
    private static final String SERIF_SHA = "2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca";

    private final AssScriptWriter writer = new AssScriptWriter();

    @Test
    void writesOnlyEscapedUserTextIntoDialogueEventsAndMapsEveryFixedTemplate() {
        TimelineSubtitleElementDTO subtitle = subtitle("安全字幕");
        String maliciousFancyText = "{\\bord99}";
        List<TimelineFancyTextElementDTO> fancyTexts = new ArrayList<>();
        for (FancyTextTemplateCode template : FancyTextTemplateCode.values()) {
            fancyTexts.add(fancy(template, maliciousFancyText + template.value()));
        }

        String script = writer.write(1080, 1920, List.of(subtitle), fancyTexts);

        assertThat(script).contains("[Script Info]", "PlayResX: 1080", "PlayResY: 1920", "[V4+ Styles]",
            "[Events]", "Style: SUB_0001,Noto Sans CJK SC,48,", "Style: FANCY_0001,");
        assertThat(script).contains("\\{\\\\bord99\\}keyword_pop");
        assertThat(script).doesNotContain(maliciousFancyText, "{\\bord99}");
        assertThat(script).contains("Style: FANCY_0001,", "Style: FANCY_0002,", "Style: FANCY_0003,",
            "Style: FANCY_0004,", "Style: FANCY_0005,", "Style: FANCY_0006,");
        assertThat(script).doesNotContain("filter_complex", "fontsdir=", "C:\\");
    }

    @Test
    void rejectsUnknownFontTemplateAndUnsafeTypedFieldsBeforeWritingAnyScript() {
        TimelineSubtitleElementDTO unknownFont = new TimelineSubtitleElementDTO("subtitle-1",
            TimelineElementType.SUBTITLE, 0, 1000, 1, true, false, "label", "source", "display", 0, 1,
            "unregistered", "1", "0".repeat(64), 48, "#FFFFFFFF", false, null, false, null, 0, "lower",
            "center");
        TimelineFancyTextElementDTO missingTemplate = new TimelineFancyTextElementDTO("fancy-1",
            TimelineElementType.FANCY_TEXT, 0, 1000, 1, true, false, "label", "text", null,
            "noto_sans_cjk_sc_regular", "2.004", SANS_SHA, "#FFFFFFFF", "#FFCC00FF", transform(), "normal",
            100, 100, null, null);
        TimelineSubtitleElementDTO badColor = new TimelineSubtitleElementDTO("subtitle-2",
            TimelineElementType.SUBTITLE, 0, 1000, 1, true, false, "label", "source", "display", 0, 1,
            "noto_sans_cjk_sc_regular", "2.004", SANS_SHA, 48, "red", false, null, false, null, 0, "lower",
            "center");

        assertThatThrownBy(() -> writer.write(1080, 1920, List.of(unknownFont), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.write(1080, 1920, List.of(), List.of(missingTemplate)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> writer.write(1080, 1920, List.of(badColor), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesNumericSemanticSeparatorsAndRejectsTheirRemovalBeforeWritingAss() {
        TimelineSubtitleElementDTO preserved = new TimelineSubtitleElementDTO("subtitle-3", TimelineElementType.SUBTITLE,
            0, 1_000, 1, true, false, "label", "\u4EF7\u683C 3.14 \u5143\u3002", "\u4EF7\u683C3.14\u5143", 0, 1,
            "noto_sans_cjk_sc_regular", "2.004", SANS_SHA, 48, "#FFFFFFFF", false, null, false, null, 0, "lower",
            "center");
        TimelineSubtitleElementDTO mismatched = new TimelineSubtitleElementDTO("subtitle-4", TimelineElementType.SUBTITLE,
            0, 1_000, 1, true, false, "label", "\u4EF7\u683C 3.14 \u5143\u3002", "\u4EF7\u683C314\u5143", 0, 1,
            "noto_sans_cjk_sc_regular", "2.004", SANS_SHA, 48, "#FFFFFFFF", false, null, false, null, 0, "lower",
            "center");

        assertThat(writer.write(1080, 1920, List.of(preserved), List.of())).contains("\u4EF7\u683C3.14\u5143");
        assertThatThrownBy(() -> writer.write(1080, 1920, List.of(mismatched), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsValidatedFancySizeAndAnimationFieldsIntoControlledAssTags() {
        TimelineFancyTextElementDTO compact = fancy(FancyTextTemplateCode.KEYWORD_POP, "compact",
            transform("0.50", "0.25"), 100, 200);
        TimelineFancyTextElementDTO expanded = fancy(FancyTextTemplateCode.KEYWORD_POP, "expanded",
            transform("0.70", "0.12"), 250, 500);

        String compactScript = writer.write(1080, 1920, List.of(), List.of(compact));
        String expandedScript = writer.write(1080, 1920, List.of(), List.of(expanded));

        assertThat(compactScript).contains("\\fscx100\\fscy50\\fad(100,200)");
        assertThat(expandedScript).contains("\\fscx140\\fscy24\\fad(250,500)");
        assertThat(expandedScript).isNotEqualTo(compactScript);
    }

    @Test
    void rejectsOverlappingFrozenSubtitleSourceRanges() {
        TimelineSubtitleElementDTO first = new TimelineSubtitleElementDTO("subtitle-4", TimelineElementType.SUBTITLE,
            0, 1_000, 1, true, false, "label", "alpha", "alpha", 0, 5, "noto_sans_cjk_sc_regular", "2.004",
            SANS_SHA, 48, "#FFFFFFFF", false, null, false, null, 0, "lower", "center");
        TimelineSubtitleElementDTO second = new TimelineSubtitleElementDTO("subtitle-5", TimelineElementType.SUBTITLE,
            1_000, 2_000, 1, true, false, "label", "beta", "beta", 4, 8, "noto_sans_cjk_sc_regular", "2.004",
            SANS_SHA, 48, "#FFFFFFFF", false, null, false, null, 0, "lower", "center");

        assertThatThrownBy(() -> writer.write(1080, 1920, List.of(first, second), List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static TimelineSubtitleElementDTO subtitle(String text) {
        return new TimelineSubtitleElementDTO("subtitle-1", TimelineElementType.SUBTITLE, 0, 2_000, 1, true, false,
            "label", text, text, 0, 1, "noto_sans_cjk_sc_regular", "2.004", SANS_SHA, 48,
            "#FFFFFFFF", true, "#00000099", true, "#000000FF", 2, "lower", "center");
    }

    private static TimelineFancyTextElementDTO fancy(FancyTextTemplateCode template, String text) {
        return fancy(template, text, transform(), 200, 200);
    }

    private static TimelineFancyTextElementDTO fancy(FancyTextTemplateCode template,
                                                       String text,
                                                       TimelineVisualTransformDTO transform,
                                                       long enterDurationMs,
                                                       long exitDurationMs) {
        boolean serif = template == FancyTextTemplateCode.HANDWRITING_REVEAL;
        return new TimelineFancyTextElementDTO("fancy-" + template.value(), TimelineElementType.FANCY_TEXT, 2_000,
            5_000, 1, true, false, "label", text, template,
            serif ? "noto_serif_cjk_sc_regular" : "noto_sans_cjk_sc_regular", serif ? "2.003" : "2.004",
            serif ? SERIF_SHA : SANS_SHA, "#FFFFFFFF", "#FFCC00FF", transform, "normal", enterDurationMs,
            exitDurationMs, null,
            null);
    }

    private static TimelineVisualTransformDTO transform() {
        return transform("0.70", "0.12");
    }

    private static TimelineVisualTransformDTO transform(String widthRatio, String heightRatio) {
        return new TimelineVisualTransformDTO(new BigDecimal("0.15"), new BigDecimal("0.12"),
            new BigDecimal(widthRatio), new BigDecimal(heightRatio), BigDecimal.ZERO, BigDecimal.ONE);
    }
}
