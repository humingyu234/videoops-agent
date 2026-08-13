package org.dromara.aivideo.timeline.constant;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 时间轴 {@code timeline-1} 的固定数值边界和白名单。
 */
public final class TimelineContractLimits {

    public static final String SCHEMA_VERSION = "timeline-1";
    public static final String FONT_REGISTRY_VERSION = "timeline-fonts-1";
    public static final String FONT_REGISTRY_SHA256 =
        "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33";

    public static final Map<String, BigDecimal> NUMERIC_LIMITS = Map.ofEntries(
        limit("canonicalJsonMaxBytes", "1048576"),
        limit("maxNestingDepth", "16"),
        limit("minTrackCount", "1"),
        limit("maxTrackCount", "32"),
        limit("maxElementsPerTrack", "512"),
        limit("maxTotalElements", "2000"),
        limit("maxDistinctAssets", "256"),
        limit("maxAssetReferences", "2000"),
        limit("minDurationMs", "1"),
        limit("maxDurationMs", "120000"),
        limit("canvasWidth", "1080"),
        limit("canvasHeight", "1920"),
        limit("canvasFrameRate", "30"),
        limit("safeMarginRatio", "0.05"),
        limit("maxKeyAsciiLength", "64"),
        limit("maxLabelCodePoints", "128"),
        limit("maxSubtitleCodePoints", "512"),
        limit("maxFancyTextCodePoints", "128"),
        limit("maxProjectScriptCodePoints", "50000"),
        limit("maxAiSuggestions", "20"),
        limit("maxAiPromptCodePoints", "2048"),
        limit("maxAiReasonCodePoints", "256"),
        limit("maxAiTags", "16"),
        limit("maxAiTagCodePoints", "32"),
        limit("maxNormalizationChanges", "256"),
        limit("maxTaskRequestBytes", "65536"),
        limit("maxTaskResultBytes", "65536"),
        limit("maxSafetySummaryCodePoints", "512"),
        limit("maxImageBytes", "20971520"),
        limit("maxImageWidth", "8192"),
        limit("maxImageHeight", "8192"),
        limit("maxVideoBytes", "1073741824"),
        limit("maxVideoDurationMs", "120000"),
        limit("maxVideoWidth", "3840"),
        limit("maxVideoHeight", "2160"),
        limit("maxVideoFrameRate", "60"),
        limit("maxAudioBytes", "268435456"),
        limit("maxAudioDurationMs", "120000"),
        limit("maxAudioSampleRateHz", "192000"),
        limit("maxAudioChannels", "8"),
        limit("outputFrameRate", "30"),
        limit("standardCrf", "23"),
        limit("highCrf", "18"),
        limit("defaultFadeInMs", "0"),
        limit("defaultFadeOutMs", "0"),
        limit("defaultSourceStartMs", "0"),
        limit("defaultBackgroundMusicVolumeRatio", "0.30"),
        limit("defaultDuckingTargetGainRatio", "0.35"),
        limit("defaultDuckingAttackMs", "120"),
        limit("defaultDuckingReleaseMs", "400"),
        limit("minEffectDurationMs", "100"),
        limit("maxEffectDurationMs", "3000"),
        limit("minZoomScale", "1.00"),
        limit("maxZoomScale", "1.20"),
        limit("minBlurRadius", "0.5"),
        limit("maxBlurRadius", "12.0"),
        limit("maxDecimalPlaces", "4"),
        limit("minSourceDurationMs", "1"),
        limit("minRatio", "0"),
        limit("maxRatio", "1"),
        limit("minRotationDegrees", "-180"),
        limit("maxRotationDegrees", "180"),
        limit("minZIndex", "0"),
        limit("maxZIndex", "999"),
        limit("minFontSizePx", "12"),
        limit("maxFontSizePx", "120"),
        limit("minOutlineWidthPx", "0"),
        limit("maxOutlineWidthPx", "8"),
        limit("fontWeight", "400"),
        limit("registeredFontCount", "2")
    );

    public static final Set<String> TRACK_TYPES = Set.of(
        "fancy_text", "subtitle", "visual_effect", "image_overlay", "pip_video",
        "main_video", "primary_audio", "background_music", "sound_effect"
    );
    public static final Set<String> ELEMENT_TYPES = Set.of(
        "main_video", "image_overlay", "pip_video", "subtitle", "fancy_text", "audio", "visual_effect"
    );
    public static final Set<String> FANCY_TEXT_TEMPLATE_CODES = Set.of(
        "keyword_pop", "gold_impact", "neon_breathe", "handwriting_reveal", "bubble_bounce", "title_wipe"
    );
    public static final Set<String> VISUAL_EFFECT_CODES = Set.of(
        "fade_in", "fade_out", "gentle_zoom_in", "gentle_zoom_out", "light_blur"
    );
    public static final Set<String> FONT_CODES = Set.of(
        "noto_sans_cjk_sc_regular", "noto_serif_cjk_sc_regular"
    );
    public static final Set<String> FIT_MODES = Set.of("contain", "cover");
    public static final Set<String> SUBTITLE_ALIGNMENTS = Set.of("left", "center", "right");
    public static final Set<String> SAFE_AREA_ANCHORS = Set.of("upper", "center", "lower");
    public static final Set<String> ANIMATION_INTENSITIES = Set.of("subtle", "normal", "strong");
    public static final Set<String> AI_IMAGE_STYLES = Set.of(
        "photorealistic", "cinematic", "illustration", "minimal"
    );
    public static final Set<String> OUTPUT_QUALITIES = Set.of("standard", "high");

    private TimelineContractLimits() {
        throw new IllegalStateException("Utility class");
    }

    private static Map.Entry<String, BigDecimal> limit(String name, String value) {
        return Map.entry(name, new BigDecimal(value));
    }
}
