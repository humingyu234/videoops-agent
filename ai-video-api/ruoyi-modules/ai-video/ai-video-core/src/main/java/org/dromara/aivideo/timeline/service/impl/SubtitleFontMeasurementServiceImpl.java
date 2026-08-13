package org.dromara.aivideo.timeline.service.impl;

import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.service.ISubtitleFontMeasurementService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Uses the registered media adapter to measure only C0-whitelisted subtitle fonts. */
@Service
public class SubtitleFontMeasurementServiceImpl implements ISubtitleFontMeasurementService {

    private static final int BASE_FONT_SIZE = 48;
    private static final int MIN_FONT_SIZE = 32;
    private static final int FONT_STEP = 2;
    private static final Map<String, FontSpec> FONTS = Map.of(
        "noto_sans_cjk_sc_regular", new FontSpec("2.004",
            "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b"),
        "noto_serif_cjk_sc_regular", new FontSpec("2.003",
            "2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca")
    );

    private final ITimelineMediaRenderService mediaRenderService;

    SubtitleFontMeasurementServiceImpl(ITimelineMediaRenderService mediaRenderService) {
        this.mediaRenderService = mediaRenderService;
    }

    @Autowired
    public SubtitleFontMeasurementServiceImpl(ObjectProvider<ITimelineMediaRenderService> mediaRenderServiceProvider) {
        this(mediaRenderServiceProvider.getIfAvailable());
    }

    @Override
    public SubtitleLayout fit(MeasureRequest request) {
        FontSpec font = FONTS.get(request == null ? null : request.fontCode());
        if (font == null || request.displayText() == null || request.displayText().isEmpty()
            || request.outlineWidthPx() < 0 || request.outlineWidthPx() > 8
            || request.canvasWidthPx() <= 0 || request.safeMarginRatio() == null
            || request.safeMarginRatio().compareTo(BigDecimal.ZERO) < 0
            || request.safeMarginRatio().compareTo(new BigDecimal("0.5")) >= 0) {
            throw fontUnavailable("字幕字体不可用");
        }
        if (mediaRenderService == null) {
            throw fontUnavailable("字幕字体测量不可用");
        }

        int safeWidth = request.safeMarginRatio().multiply(BigDecimal.valueOf(2))
            .subtract(BigDecimal.ONE).negate().multiply(BigDecimal.valueOf(request.canvasWidthPx()))
            .setScale(0, RoundingMode.DOWN).intValue();
        if (safeWidth <= 0) {
            throw fontUnavailable("字幕安全区不可用");
        }
        for (int fontSize = BASE_FONT_SIZE; fontSize >= MIN_FONT_SIZE; fontSize -= FONT_STEP) {
            TimelineTextMeasureResultDTO measure = measure(request, fontSize, request.displayText(), font);
            if (measure.widthPx() <= safeWidth) {
                return new SubtitleLayout(request.fontCode(), font.version(), font.sha256(), fontSize,
                    List.of(request.displayText()));
            }
            List<String> split = splitToFit(request, fontSize, safeWidth, font);
            if (split != null) {
                return new SubtitleLayout(request.fontCode(), font.version(), font.sha256(), fontSize, split);
            }
        }
        throw fontUnavailable("字幕无法在安全区内完整显示");
    }

    private List<String> splitToFit(MeasureRequest request, int fontSize, int safeWidth, FontSpec font) {
        int[] codePoints = request.displayText().codePoints().toArray();
        List<String> segments = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < codePoints.length) {
            int acceptedEnd = -1;
            for (int end = codePoints.length; end > offset; end--) {
                String candidate = new String(codePoints, offset, end - offset);
                if (measure(request, fontSize, candidate, font).widthPx() <= safeWidth) {
                    acceptedEnd = end;
                    segments.add(candidate);
                    break;
                }
            }
            if (acceptedEnd < 0) {
                return null;
            }
            offset = acceptedEnd;
        }
        return segments.size() > 1 ? List.copyOf(segments) : null;
    }

    private TimelineTextMeasureResultDTO measure(MeasureRequest request, int fontSize, String text, FontSpec font) {
        TimelineTextMeasureResultDTO result;
        try {
            result = mediaRenderService.measureText(new TimelineTextMeasureCommandDTO(
                request.requestId() + "-" + fontSize + "-" + text.codePointCount(0, text.length()),
                request.fontCode(), text, fontSize, request.canvasWidthPx(), request.outlineWidthPx(),
                request.safeMarginRatio()));
        } catch (RuntimeException exception) {
            throw fontUnavailable("字幕字体测量失败");
        }
        if (result == null || !result.allCodePointsSupported() || result.widthPx() <= 0 || result.heightPx() <= 0
            || !request.fontCode().equals(result.fontCode()) || !font.version().equals(result.fontVersion())
            || !font.sha256().equals(result.fontSha256())) {
            throw fontUnavailable("字幕字体不可用");
        }
        return result;
    }

    private ServiceException fontUnavailable(String message) {
        return new ServiceException(message, TimelineErrorCodes.TIMELINE_FONT_UNAVAILABLE);
    }

    private record FontSpec(String version, String sha256) {
    }
}
