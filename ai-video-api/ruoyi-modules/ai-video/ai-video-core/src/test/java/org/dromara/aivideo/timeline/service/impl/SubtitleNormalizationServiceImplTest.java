package org.dromara.aivideo.timeline.service.impl;

import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.service.ISubtitleFontMeasurementService;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SubtitleNormalizationServiceImplTest {

    @Mock
    private ISubtitleFontMeasurementService fontMeasurementService;
    @Mock
    private ITimelineMediaRenderService mediaRenderService;

    @Test
    void measuresOnlyRegisteredFontsWithTheC0MediaAdapterAndUsesTheCanvasSafeArea() {
        when(mediaRenderService.measureText(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO.class);
            return new TimelineTextMeasureResultDTO(command.requestId(), command.fontCode(), "2.004",
                "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b",
                "registry", 800, 64, true);
        });

        ISubtitleFontMeasurementService.SubtitleLayout layout = new SubtitleFontMeasurementServiceImpl(mediaRenderService)
            .fit(new ISubtitleFontMeasurementService.MeasureRequest("subtitle-1", "noto_sans_cjk_sc_regular",
                "完整字幕", 2, 1080, new BigDecimal("0.05")));

        assertThat(layout.fontSizePx()).isEqualTo(48);
        assertThat(layout.displaySegments()).containsExactly("完整字幕");
        verify(mediaRenderService).measureText(any());
        assertThatThrownBy(() -> new SubtitleFontMeasurementServiceImpl(mediaRenderService)
            .fit(new ISubtitleFontMeasurementService.MeasureRequest("subtitle-2", "C:/untrusted/font.otf",
                "完整字幕", 2, 1080, new BigDecimal("0.05"))))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void normalizesFrozenUnicodePunctuationWhitespaceDecimalAndEmojiExamples() {
        SubtitleNormalizationServiceImpl service = new SubtitleNormalizationServiceImpl(fontMeasurementService);

        assertThat(service.normalizeDisplay("欢迎使用 AI 视频！")).isEqualTo("欢迎使用AI视频");
        assertThat(service.normalizeDisplay("价格 3.14 元。")).isEqualTo("价格3.14元");
        assertThat(service.normalizeDisplay("现在 12:30 开播。")).isEqualTo("现在12:30开播");
        assertThat(service.normalizeDisplay("日期 8/16，订单 A-12。")).isEqualTo("日期8/16订单A-12");
        assertThat(service.normalizeDisplay("Cafe\u0301，开始创作。\r\n")).isEqualTo("Café开始创作");
        assertThat(service.normalizeDisplay("效率提升 🚀 50%！")).isEqualTo("效率提升🚀50%");
    }

    @Test
    void rejectsAnEmptySubtitleSetForANonEmptyProjectScript() {
        assertThatThrownBy(() -> new SubtitleNormalizationServiceImpl(fontMeasurementService)
            .normalize("完整文案不能省略", List.of(), 1080, new BigDecimal("0.05")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46607));
    }

    @Test
    void usesUnicodeCodePointOffsetsAndReturnsServerNormalizedSegmentsWithoutOmission() {
        String script = "A🚀 Cafe\u0301， 3.14！";
        TimelineSubtitleElementDTO source = subtitle("subtitle_0001", script, 0,
            "A🚀 Café， 3.14！".codePointCount(0, "A🚀 Café， 3.14！".length()), 0L, 4_000L);
        when(fontMeasurementService.fit(any())).thenAnswer(invocation -> {
            ISubtitleFontMeasurementService.MeasureRequest request = invocation.getArgument(0);
            return new ISubtitleFontMeasurementService.SubtitleLayout(request.fontCode(), "2.004",
                "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b",
                48, List.of(request.displayText()));
        });

        ISubtitleNormalizationService.NormalizationResult result = new SubtitleNormalizationServiceImpl(fontMeasurementService)
            .normalize(script, List.of(source), 1080, new BigDecimal("0.05"));

        TimelineSubtitleElementDTO normalized = result.subtitles().getFirst();
        assertThat(normalized.sourceTextSnapshot()).isEqualTo("A🚀 Café， 3.14！");
        assertThat(normalized.displayText()).isEqualTo("A🚀Café3.14");
        assertThat(normalized.sourceStartOffset()).isZero();
        assertThat(normalized.sourceEndOffset()).isEqualTo("A🚀 Café， 3.14！".codePointCount(0, "A🚀 Café， 3.14！".length()));
        assertThat(normalized.startMs()).isZero();
        assertThat(normalized.endMs()).isEqualTo(4_000L);
        assertThat(result.normalizationChanges()).isNotEmpty();
    }

    @Test
    void rejectsDisplayTextThatDropsASemanticSeparator() {
        String script = "价格3.14元";
        TimelineSubtitleElementDTO source = subtitle("subtitle_0001", script, "价格314元", 0,
            script.codePointCount(0, script.length()), 0L, 1_000L);

        assertThatThrownBy(() -> new SubtitleNormalizationServiceImpl(fontMeasurementService)
            .normalize(script, List.of(source), 1080, new BigDecimal("0.05")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46607));

        String reference = "订单A-12";
        TimelineSubtitleElementDTO missingHyphen = subtitle("subtitle_0002", reference, "订单A12", 0,
            reference.codePointCount(0, reference.length()), 0L, 1_000L);
        assertThatThrownBy(() -> new SubtitleNormalizationServiceImpl(fontMeasurementService)
            .normalize(reference, List.of(missingHyphen), 1080, new BigDecimal("0.05")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode()).isEqualTo(46607));
    }

    @Test
    void splitsLongSubtitlesDeterministicallyIntoContinuousNonOmittingSegments() {
        String script = "甲乙丙丁戊己庚辛";
        TimelineSubtitleElementDTO source = subtitle("subtitle_0001", script, 0, 8, 100L, 900L);
        when(fontMeasurementService.fit(any())).thenReturn(new ISubtitleFontMeasurementService.SubtitleLayout(
            "noto_sans_cjk_sc_regular", "2.004",
            "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b",
            42, List.of("甲乙丙丁", "戊己庚辛")));

        ISubtitleNormalizationService.NormalizationResult result = new SubtitleNormalizationServiceImpl(fontMeasurementService)
            .normalize(script, List.of(source), 1080, new BigDecimal("0.05"));

        assertThat(result.subtitles()).extracting(TimelineSubtitleElementDTO::elementId)
            .containsExactly("subtitle_0001_01", "subtitle_0001_02");
        assertThat(result.subtitles()).extracting(TimelineSubtitleElementDTO::displayText)
            .containsExactly("甲乙丙丁", "戊己庚辛");
        assertThat(result.subtitles().get(0).endMs()).isEqualTo(result.subtitles().get(1).startMs());
        assertThat(result.subtitles().get(0).sourceEndOffset())
            .isEqualTo(result.subtitles().get(1).sourceStartOffset());
        assertThat(result.subtitles().stream().map(TimelineSubtitleElementDTO::displayText).reduce("", String::concat))
            .isEqualTo("甲乙丙丁戊己庚辛");
    }

    @Test
    void rejectsUnregisteredFontsAndNeverAcceptsAnArbitraryFontPath() {
        TimelineSubtitleElementDTO source = subtitle("subtitle_0001", "测试", 0, 2, 0L, 1_000L);

        assertThatThrownBy(() -> new SubtitleNormalizationServiceImpl(fontMeasurementService).normalize("测试",
            List.of(new TimelineSubtitleElementDTO(source.elementId(), source.elementType(), source.startMs(),
                source.endMs(), source.zIndex(), source.enabled(), source.locked(), source.label(),
                source.sourceTextSnapshot(), source.displayText(), source.sourceStartOffset(), source.sourceEndOffset(),
                "../../untrusted.otf", source.fontVersion(), source.fontSha256(), source.fontSizePx(), source.color(),
                source.backgroundEnabled(), source.backgroundColor(), source.outlineEnabled(), source.outlineColor(),
                source.outlineWidthPx(), source.safeAreaAnchor(), source.alignment())), 1080, new BigDecimal("0.05")))
            .isInstanceOf(ServiceException.class);
    }

    private TimelineSubtitleElementDTO subtitle(String id, String source, int startOffset, int endOffset,
                                                 long startMs, long endMs) {
        return subtitle(id, source, source, startOffset, endOffset, startMs, endMs);
    }

    private TimelineSubtitleElementDTO subtitle(String id, String source, String display, int startOffset,
                                                 int endOffset, long startMs, long endMs) {
        return new TimelineSubtitleElementDTO(id, TimelineElementType.SUBTITLE, startMs, endMs, 1,
            true, false, "subtitle", source, display, startOffset, endOffset,
            "noto_sans_cjk_sc_regular", "client", "client", 48, "#FFFFFFFF",
            false, null, false, null, 0, "lower", "center");
    }
}
