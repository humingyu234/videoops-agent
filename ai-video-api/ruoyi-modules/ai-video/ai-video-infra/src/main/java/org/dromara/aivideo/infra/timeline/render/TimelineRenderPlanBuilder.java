package org.dromara.aivideo.infra.timeline.render;

import org.dromara.aivideo.creation.dto.CreationAssetResolveDTO;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.infra.timeline.ass.AssScriptWriter;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.dto.TimelineAssetReferenceDTO;
import org.dromara.aivideo.timeline.dto.TimelineAudioElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineCanvasDTO;
import org.dromara.aivideo.timeline.dto.TimelineCropDTO;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.dto.TimelineElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineFadeDTO;
import org.dromara.aivideo.timeline.dto.TimelineImageElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineMainVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelinePipVideoElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineTrackDTO;
import org.dromara.aivideo.timeline.dto.TimelineVisualEffectElementDTO;
import org.dromara.aivideo.timeline.dto.TimelineVisualTransformDTO;
import org.dromara.aivideo.timeline.enums.TimelineAssetUsageType;
import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.enums.TimelineFitMode;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.enums.TimelineTrackArea;
import org.dromara.aivideo.timeline.enums.TimelineTrackType;
import org.dromara.aivideo.timeline.enums.TimelineVisualEffectCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a frozen timeline document and derives only generated file aliases and whitelisted FFmpeg filters.
 * This class never opens a storage object or accepts a filesystem path from a caller.
 */
public final class TimelineRenderPlanBuilder {

    private static final String FONT_REGISTRY_SHA256 = "2e0198557dc5a00c4cdde6eb970a3c2282c298f169c3f6bd7349c275156a9e33";
    private static final String SANS_FONT_CODE = "noto_sans_cjk_sc_regular";
    private static final String SANS_FONT_VERSION = "2.004";
    private static final String SANS_FONT_SHA256 = "2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b";
    private static final String SERIF_FONT_CODE = "noto_serif_cjk_sc_regular";
    private static final String SERIF_FONT_VERSION = "2.003";
    private static final String SERIF_FONT_SHA256 = "2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int CANVAS_WIDTH = limit("canvasWidth");
    private static final int CANVAS_HEIGHT = limit("canvasHeight");
    private static final int FRAME_RATE = limit("canvasFrameRate");
    private static final int MAX_TRACKS = limit("maxTrackCount");
    private static final int MAX_ELEMENTS_PER_TRACK = limit("maxElementsPerTrack");
    private static final int MAX_TOTAL_ELEMENTS = limit("maxTotalElements");
    private static final int MAX_ASSET_REFERENCES = limit("maxAssetReferences");
    private static final int MAX_DISTINCT_ASSETS = limit("maxDistinctAssets");
    private static final int MAX_DURATION_MS = limit("maxDurationMs");
    private static final long MAX_IMAGE_BYTES = limit("maxImageBytes");
    private static final int MAX_IMAGE_WIDTH = limit("maxImageWidth");
    private static final int MAX_IMAGE_HEIGHT = limit("maxImageHeight");
    private static final long MAX_VIDEO_BYTES = limit("maxVideoBytes");
    private static final long MAX_VIDEO_DURATION_MS = limit("maxVideoDurationMs");
    private static final int MAX_VIDEO_WIDTH = limit("maxVideoWidth");
    private static final int MAX_VIDEO_HEIGHT = limit("maxVideoHeight");
    private static final long MAX_AUDIO_BYTES = limit("maxAudioBytes");
    private static final long MAX_AUDIO_DURATION_MS = limit("maxAudioDurationMs");
    private static final int MAX_DECIMAL_PLACES = limit("maxDecimalPlaces");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of("video/mp4", "video/quicktime", "video/webm");
    private static final Set<String> AUDIO_MIME_TYPES = Set.of("audio/mpeg", "audio/wav", "audio/mp4", "audio/aac");
    private static final BigDecimal SAFE_MARGIN = TimelineContractLimits.NUMERIC_LIMITS.get("safeMarginRatio");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final AssScriptWriter assScriptWriter;

    public TimelineRenderPlanBuilder() {
        this(new AssScriptWriter());
    }

    TimelineRenderPlanBuilder(AssScriptWriter assScriptWriter) {
        this.assScriptWriter = Objects.requireNonNull(assScriptWriter, "assScriptWriter");
    }

    public TimelineRenderPlan build(TimelineRenderCommandDTO command,
                                    List<CreationAssetResolveDTO> resolvedInputs) {
        try {
            validateCommand(command);
            ParsedTimeline timeline = parseTimeline(command.timeline());
            validateFonts(timeline);
            AssetBindings sourceBindings = bindAssets(command.assets(), resolvedInputs, timeline);
            validateAssetFacts(timeline, sourceBindings);
            PreparedInputs preparedInputs = prepareRenderInputs(timeline, sourceBindings);
            PrimaryAudio primaryAudio = validatePrimaryAudio(timeline, preparedInputs.bindings());
            String assScript = assScript(timeline);
            String filterScript = filterScript(timeline, preparedInputs.bindings(), primaryAudio);
            return new TimelineRenderPlan(command.executionId(), command.attemptId(), timeline.canvas().durationMs(),
                sourceBindings.inputs(), preparedInputs.renderInputs(), preparedInputs.pipTails(), assScript,
                filterScript, command.outputConfig().qualityPreset());
        } catch (TimelineExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw inputInvalid();
        }
    }

    private static void validateCommand(TimelineRenderCommandDTO command) {
        if (command == null || !safeIdentifier(command.taskId()) || !safeIdentifier(command.executionId())
            || !safeIdentifier(command.attemptId()) || !safeIdentifier(command.inputVersionId())) {
            throw inputInvalid();
        }
        if (!TimelineContractLimits.FONT_REGISTRY_VERSION.equals(command.fontRegistryVersion())
            || !FONT_REGISTRY_SHA256.equals(command.fontRegistrySha256())) {
            throw fontUnavailable();
        }
        TimelineOutputConfigDTO output = command.outputConfig();
        if (output == null || !"match_canvas".equals(output.resolutionPreset()) || output.frameRate() != FRAME_RATE
            || output.qualityPreset() == null) {
            throw inputInvalid();
        }
    }

    private static ParsedTimeline parseTimeline(TimelineDocumentDTO document) {
        if (document == null || !TimelineContractLimits.SCHEMA_VERSION.equals(document.schemaVersion())) {
            throw inputInvalid();
        }
        TimelineCanvasDTO canvas = document.canvas();
        validateCanvas(canvas);
        List<TimelineTrackDTO> tracks = requireList(document.tracks(), 1, MAX_TRACKS);
        Set<String> trackIds = new HashSet<>();
        Set<String> elementIds = new HashSet<>();
        List<Tracked<TimelineMainVideoElementDTO>> mainVideos = new ArrayList<>();
        List<Tracked<TimelineImageElementDTO>> images = new ArrayList<>();
        List<Tracked<TimelinePipVideoElementDTO>> pips = new ArrayList<>();
        List<Tracked<TimelineSubtitleElementDTO>> subtitles = new ArrayList<>();
        List<Tracked<TimelineFancyTextElementDTO>> fancyTexts = new ArrayList<>();
        List<Tracked<TimelineAudioElementDTO>> primaryAudio = new ArrayList<>();
        List<Tracked<TimelineAudioElementDTO>> backgroundMusic = new ArrayList<>();
        List<Tracked<TimelineAudioElementDTO>> soundEffects = new ArrayList<>();
        List<Tracked<TimelineVisualEffectElementDTO>> effects = new ArrayList<>();
        Map<String, AssetElement> assetElements = new LinkedHashMap<>();
        int totalElements = 0;

        for (TimelineTrackDTO track : tracks) {
            validateTrack(track, trackIds);
            List<TimelineElementDTO> elements = requireList(track.elements(), 1, MAX_ELEMENTS_PER_TRACK);
            totalElements += elements.size();
            if (totalElements > MAX_TOTAL_ELEMENTS) {
                throw inputInvalid();
            }
            for (TimelineElementDTO element : elements) {
                validateBaseElement(element, canvas.durationMs(), elementIds);
                switch (track.trackType()) {
                    case MAIN_VIDEO -> {
                        TimelineMainVideoElementDTO main = requireType(element, TimelineMainVideoElementDTO.class,
                            TimelineElementType.MAIN_VIDEO);
                        validateMainVideo(main, canvas.durationMs());
                        Tracked<TimelineMainVideoElementDTO> tracked = new Tracked<>(main, track);
                        mainVideos.add(tracked);
                        registerAsset(assetElements, main.elementId(), main.assetId(), TimelineAssetUsageType.BASE_VIDEO,
                            CreationAssetType.VIDEO, tracked);
                    }
                    case IMAGE_OVERLAY -> {
                        TimelineImageElementDTO image = requireType(element, TimelineImageElementDTO.class,
                            TimelineElementType.IMAGE_OVERLAY);
                        validateImage(image);
                        Tracked<TimelineImageElementDTO> tracked = new Tracked<>(image, track);
                        images.add(tracked);
                        registerAsset(assetElements, image.elementId(), image.assetId(), TimelineAssetUsageType.IMAGE,
                            CreationAssetType.IMAGE, tracked);
                    }
                    case PIP_VIDEO -> {
                        TimelinePipVideoElementDTO pip = requireType(element, TimelinePipVideoElementDTO.class,
                            TimelineElementType.PIP_VIDEO);
                        validatePip(pip);
                        Tracked<TimelinePipVideoElementDTO> tracked = new Tracked<>(pip, track);
                        pips.add(tracked);
                        registerAsset(assetElements, pip.elementId(), pip.assetId(), TimelineAssetUsageType.PIP_VIDEO,
                            CreationAssetType.VIDEO, tracked);
                    }
                    case SUBTITLE -> {
                        TimelineSubtitleElementDTO subtitle = requireType(element, TimelineSubtitleElementDTO.class,
                            TimelineElementType.SUBTITLE);
                        validateTextOffsets(subtitle.sourceStartOffset(), subtitle.sourceEndOffset());
                        subtitles.add(new Tracked<>(subtitle, track));
                    }
                    case FANCY_TEXT -> fancyTexts.add(new Tracked<>(requireType(element,
                        TimelineFancyTextElementDTO.class, TimelineElementType.FANCY_TEXT), track));
                    case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> {
                        TimelineAudioElementDTO audio = requireType(element, TimelineAudioElementDTO.class,
                            TimelineElementType.AUDIO);
                        TimelineAssetUsageType usage = usageForTrack(track.trackType());
                        validateAudio(audio, usage);
                        Tracked<TimelineAudioElementDTO> tracked = new Tracked<>(audio, track);
                        switch (usage) {
                            case PRIMARY_AUDIO -> primaryAudio.add(tracked);
                            case BACKGROUND_MUSIC -> backgroundMusic.add(tracked);
                            case SOUND_EFFECT -> soundEffects.add(tracked);
                            default -> throw inputInvalid();
                        }
                        registerAsset(assetElements, audio.elementId(), audio.assetId(), usage, CreationAssetType.AUDIO,
                            tracked);
                    }
                    case VISUAL_EFFECT -> {
                        TimelineVisualEffectElementDTO effect = requireType(element,
                            TimelineVisualEffectElementDTO.class, TimelineElementType.VISUAL_EFFECT);
                        validateEffect(effect);
                        effects.add(new Tracked<>(effect, track));
                    }
                }
            }
        }
        if (mainVideos.size() != 1 || !mainVideos.getFirst().active()) {
            throw inputInvalid();
        }
        return new ParsedTimeline(canvas, mainVideos.getFirst(), images, pips, subtitles, fancyTexts, primaryAudio,
            backgroundMusic, soundEffects, effects, assetElements);
    }

    private static void validateCanvas(TimelineCanvasDTO canvas) {
        if (canvas == null || canvas.width() != CANVAS_WIDTH || canvas.height() != CANVAS_HEIGHT
            || canvas.frameRate() != FRAME_RATE || canvas.durationMs() < 1 || canvas.durationMs() > MAX_DURATION_MS
            || canvas.safeMarginRatio() == null || canvas.safeMarginRatio().compareTo(SAFE_MARGIN) != 0) {
            throw inputInvalid();
        }
    }

    private static void validateTrack(TimelineTrackDTO track, Set<String> trackIds) {
        if (track == null || !safeKey(track.trackId()) || !trackIds.add(track.trackId()) || track.trackType() == null
            || track.area() == null || track.order() < 0 || track.order() > 31) {
            throw inputInvalid();
        }
        TimelineTrackArea expectedArea = switch (track.trackType()) {
            case FANCY_TEXT, SUBTITLE, VISUAL_EFFECT, IMAGE_OVERLAY, PIP_VIDEO -> TimelineTrackArea.TOP;
            case MAIN_VIDEO -> TimelineTrackArea.CENTER;
            case PRIMARY_AUDIO, BACKGROUND_MUSIC, SOUND_EFFECT -> TimelineTrackArea.BOTTOM;
        };
        if (track.area() != expectedArea) {
            throw inputInvalid();
        }
        if (track.trackType() == TimelineTrackType.MAIN_VIDEO && (!track.locked() || track.order() != 0)) {
            throw inputInvalid();
        }
    }

    private static void validateBaseElement(TimelineElementDTO element, long canvasDurationMs, Set<String> elementIds) {
        if (element == null || !safeKey(element.elementId()) || !elementIds.add(element.elementId())
            || element.elementType() == null || element.startMs() < 0 || element.endMs() <= element.startMs()
            || element.endMs() > canvasDurationMs || element.zIndex() < 0 || element.zIndex() > 999
            || !safeLabel(element.label())) {
            throw inputInvalid();
        }
    }

    private static void validateMainVideo(TimelineMainVideoElementDTO element, long canvasDurationMs) {
        if (!decimalId(element.assetId()) || element.startMs() != 0 || element.endMs() != canvasDurationMs
            || element.sourceDurationMs() < 1 || element.sourceDurationMs() > MAX_VIDEO_DURATION_MS
            || element.sourceStartMs() < 0
            || element.sourceStartMs() >= element.sourceDurationMs() || element.fitMode() == null) {
            throw inputInvalid();
        }
    }

    private static void validateImage(TimelineImageElementDTO element) {
        if (!decimalId(element.assetId())) {
            throw inputInvalid();
        }
        validateTransform(element.transform());
        validateCrop(element.crop());
        validateFade(element.fade(), element.endMs() - element.startMs());
        validateTextOffsets(element.sourceStartOffset(), element.sourceEndOffset());
        if (element.fitMode() == null) {
            throw inputInvalid();
        }
    }

    private static void validatePip(TimelinePipVideoElementDTO element) {
        if (!decimalId(element.assetId()) || element.sourceDurationMs() < 1
            || element.sourceDurationMs() > MAX_VIDEO_DURATION_MS || element.sourceStartMs() < 0
            || element.sourceStartMs() >= element.sourceDurationMs() || !element.loopWhenOverflow()
            || element.audioEnabled() || element.fitMode() == null) {
            throw inputInvalid();
        }
        validateTransform(element.transform());
        validateCrop(element.crop());
        validateFade(element.fade(), element.endMs() - element.startMs());
    }

    private static void validateAudio(TimelineAudioElementDTO element, TimelineAssetUsageType expectedUsage) {
        if (!decimalId(element.assetId()) || element.usageType() != expectedUsage || element.sourceDurationMs() < 1
            || element.sourceDurationMs() > MAX_AUDIO_DURATION_MS
            || element.sourceStartMs() < 0 || element.sourceEndMs() <= element.sourceStartMs()
            || element.sourceEndMs() > element.sourceDurationMs()) {
            throw inputInvalid();
        }
        boundedDecimal(element.volumeRatio(), ZERO, ONE, true);
        validateFade(element.fade(), element.endMs() - element.startMs());
        long sourceLength = element.sourceEndMs() - element.sourceStartMs();
        switch (expectedUsage) {
            case PRIMARY_AUDIO -> {
                if (element.loopWhenOverflow() || element.duckingEnabled() || element.targetGainRatio() != null
                    || element.attackMs() != 0 || element.releaseMs() != 0 || sourceLength < element.endMs() - element.startMs()) {
                    throw inputInvalid();
                }
            }
            case BACKGROUND_MUSIC -> {
                if (!element.loopWhenOverflow() || !element.duckingEnabled()
                    || element.volumeRatio().compareTo(decimalLimit("defaultBackgroundMusicVolumeRatio")) != 0
                    || element.targetGainRatio() == null
                    || element.targetGainRatio().compareTo(decimalLimit("defaultDuckingTargetGainRatio")) != 0
                    || element.attackMs() != limit("defaultDuckingAttackMs")
                    || element.releaseMs() != limit("defaultDuckingReleaseMs")) {
                    throw inputInvalid();
                }
            }
            case SOUND_EFFECT -> {
                if (element.loopWhenOverflow() || element.duckingEnabled() || element.targetGainRatio() != null
                    || element.attackMs() != 0 || element.releaseMs() != 0 || sourceLength < element.endMs() - element.startMs()) {
                    throw inputInvalid();
                }
            }
            default -> throw inputInvalid();
        }
    }

    private static void validateEffect(TimelineVisualEffectElementDTO effect) {
        if (effect.effectCode() == null || effect.durationMs() < limit("minEffectDurationMs")
            || effect.durationMs() > limit("maxEffectDurationMs")
            || effect.durationMs() > effect.endMs() - effect.startMs()) {
            throw inputInvalid();
        }
        switch (effect.effectCode()) {
            case FADE_IN, FADE_OUT -> {
                if (effect.scale() != null || effect.radius() != null) {
                    throw inputInvalid();
                }
            }
            case GENTLE_ZOOM_IN, GENTLE_ZOOM_OUT -> {
                if (effect.radius() != null) {
                    throw inputInvalid();
                }
                boundedDecimal(effect.scale(), decimalLimit("minZoomScale"), decimalLimit("maxZoomScale"), true);
            }
            case LIGHT_BLUR -> {
                if (effect.scale() != null) {
                    throw inputInvalid();
                }
                boundedDecimal(effect.radius(), decimalLimit("minBlurRadius"), decimalLimit("maxBlurRadius"), true);
            }
        }
    }

    private static void validateTransform(TimelineVisualTransformDTO transform) {
        if (transform == null) {
            throw inputInvalid();
        }
        BigDecimal x = boundedDecimal(transform.xRatio(), ZERO, ONE, false);
        BigDecimal y = boundedDecimal(transform.yRatio(), ZERO, ONE, false);
        BigDecimal width = boundedDecimal(transform.widthRatio(), ZERO, ONE, true);
        BigDecimal height = boundedDecimal(transform.heightRatio(), ZERO, ONE, true);
        if (x.add(width).compareTo(ONE) > 0 || y.add(height).compareTo(ONE) > 0) {
            throw inputInvalid();
        }
        boundedDecimal(transform.rotationDeg(), decimalLimit("minRotationDegrees"),
            decimalLimit("maxRotationDegrees"), false);
        boundedDecimal(transform.opacity(), ZERO, ONE, true);
    }

    private static void validateCrop(TimelineCropDTO crop) {
        if (crop == null) {
            throw inputInvalid();
        }
        BigDecimal x = boundedDecimal(crop.xRatio(), ZERO, ONE, false);
        BigDecimal y = boundedDecimal(crop.yRatio(), ZERO, ONE, false);
        BigDecimal width = boundedDecimal(crop.widthRatio(), ZERO, ONE, true);
        BigDecimal height = boundedDecimal(crop.heightRatio(), ZERO, ONE, true);
        if (x.add(width).compareTo(ONE) > 0 || y.add(height).compareTo(ONE) > 0) {
            throw inputInvalid();
        }
    }

    private static void validateFade(TimelineFadeDTO fade, long elementDurationMs) {
        if (fade == null || fade.fadeInMs() < 0 || fade.fadeOutMs() < 0
            || fade.fadeInMs() + fade.fadeOutMs() > elementDurationMs) {
            throw inputInvalid();
        }
    }

    private static void validateTextOffsets(int startOffset, int endOffset) {
        if (startOffset < 0 || endOffset <= startOffset || endOffset > limit("maxProjectScriptCodePoints")) {
            throw inputInvalid();
        }
    }

    private static void validateFonts(ParsedTimeline timeline) {
        for (Tracked<TimelineSubtitleElementDTO> subtitle : timeline.subtitles()) {
            validateRegisteredFont(subtitle.element().fontCode(), subtitle.element().fontVersion(),
                subtitle.element().fontSha256());
        }
        for (Tracked<TimelineFancyTextElementDTO> fancyText : timeline.fancyTexts()) {
            validateRegisteredFont(fancyText.element().fontCode(), fancyText.element().fontVersion(),
                fancyText.element().fontSha256());
        }
    }

    private static void validateRegisteredFont(String code, String version, String sha256) {
        boolean valid = (SANS_FONT_CODE.equals(code) && SANS_FONT_VERSION.equals(version) && SANS_FONT_SHA256.equals(sha256))
            || (SERIF_FONT_CODE.equals(code) && SERIF_FONT_VERSION.equals(version) && SERIF_FONT_SHA256.equals(sha256));
        if (!valid) {
            throw fontUnavailable();
        }
    }

    private static AssetBindings bindAssets(List<TimelineAssetReferenceDTO> references,
                                            List<CreationAssetResolveDTO> resolvedInputs,
                                            ParsedTimeline timeline) {
        List<TimelineAssetReferenceDTO> safeReferences = requireList(references, 1, MAX_ASSET_REFERENCES);
        Map<AssetKey, TimelineAssetReferenceDTO> referencesByKey = new LinkedHashMap<>();
        for (TimelineAssetReferenceDTO reference : safeReferences) {
            if (reference == null || !decimalId(reference.assetId()) || reference.usageType() == null
                || !safeSha256(reference.sha256()) || reference.fileSize() <= 0) {
                throw inputInvalid();
            }
            List<String> elementIds = requireList(reference.elementIds(), 1, MAX_TOTAL_ELEMENTS);
            if (new HashSet<>(elementIds).size() != elementIds.size() || elementIds.stream().anyMatch(id -> !safeKey(id))) {
                throw inputInvalid();
            }
            AssetKey key = new AssetKey(reference.assetId(), reference.usageType());
            if (referencesByKey.putIfAbsent(key, reference) != null) {
                throw inputInvalid();
            }
        }
        if (referencesByKey.size() > MAX_DISTINCT_ASSETS) {
            throw inputInvalid();
        }
        Map<AssetKey, List<AssetElement>> elementsByKey = new LinkedHashMap<>();
        for (AssetElement element : timeline.assetElements().values()) {
            elementsByKey.computeIfAbsent(new AssetKey(element.assetId(), element.usageType()), ignored -> new ArrayList<>())
                .add(element);
        }
        if (!referencesByKey.keySet().equals(elementsByKey.keySet())) {
            throw inputInvalid();
        }
        for (Map.Entry<AssetKey, TimelineAssetReferenceDTO> entry : referencesByKey.entrySet()) {
            Set<String> expected = elementsByKey.get(entry.getKey()).stream().map(AssetElement::elementId)
                .collect(java.util.stream.Collectors.toSet());
            if (!expected.equals(new HashSet<>(entry.getValue().elementIds()))) {
                throw inputInvalid();
            }
        }

        List<CreationAssetResolveDTO> safeResolved = requireList(resolvedInputs, 1, MAX_ASSET_REFERENCES);
        Map<AssetKey, CreationAssetResolveDTO> resolvedByKey = new HashMap<>();
        for (CreationAssetResolveDTO resolved : safeResolved) {
            if (resolved == null || !decimalId(resolved.assetId()) || resolved.usageType() == null
                || !safeSha256(resolved.sha256()) || resolved.assetType() == null || resolved.sizeBytes() <= 0
                || !safeMimeType(resolved.mimeType())) {
                throw inputInvalid();
            }
            AssetKey key = new AssetKey(resolved.assetId(), resolved.usageType());
            if (resolvedByKey.putIfAbsent(key, resolved) != null) {
                throw inputInvalid();
            }
        }
        if (!referencesByKey.keySet().equals(resolvedByKey.keySet())) {
            throw inputInvalid();
        }
        for (Map.Entry<AssetKey, TimelineAssetReferenceDTO> entry : referencesByKey.entrySet()) {
            CreationAssetResolveDTO resolved = resolvedByKey.get(entry.getKey());
            if (!entry.getValue().sha256().equals(resolved.sha256()) || entry.getValue().fileSize() != resolved.sizeBytes()) {
                throw inputInvalid();
            }
        }

        List<AssetKey> keys = new ArrayList<>(referencesByKey.keySet());
        keys.sort(Comparator.comparingInt((AssetKey key) -> usageOrder(key.usageType()))
            .thenComparing(key -> new BigInteger(key.assetId())));
        List<TimelineRenderPlan.Input> inputs = new ArrayList<>();
        Map<AssetKey, AssetBinding> bindingByKey = new HashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            AssetKey key = keys.get(index);
            CreationAssetResolveDTO resolved = resolvedByKey.get(key);
            AssetElement representative = elementsByKey.get(key).getFirst();
            if (representative.assetType() != resolved.assetType()) {
                throw inputInvalid();
            }
            boolean loopInput = key.usageType() == TimelineAssetUsageType.PIP_VIDEO
                || key.usageType() == TimelineAssetUsageType.BACKGROUND_MUSIC;
            String alias = "input-%04d.%s".formatted(index + 1, TimelineRenderPlan.extension(resolved.assetType()));
            TimelineRenderPlan.Input input = new TimelineRenderPlan.Input(alias, key.assetId(), resolved.sha256(),
                resolved.sizeBytes(), resolved.assetType(), key.usageType(), loopInput);
            inputs.add(input);
            bindingByKey.put(key, new AssetBinding(input, resolved, index));
        }
        Map<String, AssetBinding> bindingByElementId = new HashMap<>();
        for (Map.Entry<AssetKey, List<AssetElement>> entry : elementsByKey.entrySet()) {
            AssetBinding binding = bindingByKey.get(entry.getKey());
            for (AssetElement element : entry.getValue()) {
                bindingByElementId.put(element.elementId(), binding);
            }
        }
        return new AssetBindings(List.copyOf(inputs), Map.copyOf(bindingByElementId));
    }

    private static PreparedInputs prepareRenderInputs(ParsedTimeline timeline, AssetBindings sourceBindings) {
        Map<String, List<Tracked<TimelinePipVideoElementDTO>>> activePipsBySourceAlias = new HashMap<>();
        for (Tracked<TimelinePipVideoElementDTO> pip : timeline.pips()) {
            if (!pip.active()) {
                continue;
            }
            AssetBinding source = bindingFor(pip.element().elementId(), sourceBindings);
            activePipsBySourceAlias.computeIfAbsent(source.input().alias(), ignored -> new ArrayList<>()).add(pip);
        }
        Comparator<Tracked<TimelinePipVideoElementDTO>> pipOrder = Comparator
            .comparingInt((Tracked<TimelinePipVideoElementDTO> value) -> value.element().zIndex())
            .thenComparingInt(value -> value.track().order())
            .thenComparingLong(value -> value.element().startMs())
            .thenComparing(value -> value.element().elementId());
        activePipsBySourceAlias.values().forEach(values -> values.sort(pipOrder));

        List<TimelineRenderPlan.RenderInput> renderInputs = new ArrayList<>();
        List<TimelineRenderPlan.PipTail> pipTails = new ArrayList<>();
        Map<String, AssetBinding> bindingByElementId = new HashMap<>(sourceBindings.byElementId());
        int tailNumber = 1;
        for (TimelineRenderPlan.Input sourceInput : sourceBindings.inputs()) {
            if (sourceInput.usageType() != TimelineAssetUsageType.PIP_VIDEO) {
                int inputIndex = renderInputs.size();
                renderInputs.add(new TimelineRenderPlan.RenderInput(sourceInput.alias(), sourceInput.assetType(),
                    sourceInput.usageType(), false));
                remapBindings(bindingByElementId, sourceInput.alias(), inputIndex);
                continue;
            }
            for (Tracked<TimelinePipVideoElementDTO> pip : activePipsBySourceAlias.getOrDefault(sourceInput.alias(),
                List.of())) {
                if (tailNumber > 9_999) {
                    throw inputInvalid();
                }
                AssetBinding source = bindingFor(pip.element().elementId(), sourceBindings);
                Long sourceEndMs = source.facts().durationMs();
                if (sourceEndMs == null) {
                    throw inputInvalid();
                }
                String renderAlias = "pip-%04d.mp4".formatted(tailNumber++);
                pipTails.add(new TimelineRenderPlan.PipTail(sourceInput.alias(), renderAlias,
                    pip.element().sourceStartMs(), sourceEndMs));
                int inputIndex = renderInputs.size();
                renderInputs.add(new TimelineRenderPlan.RenderInput(renderAlias, CreationAssetType.VIDEO,
                    TimelineAssetUsageType.PIP_VIDEO, true));
                bindingByElementId.put(pip.element().elementId(), new AssetBinding(source.input(), source.facts(),
                    inputIndex));
            }
        }
        return new PreparedInputs(List.copyOf(renderInputs), List.copyOf(pipTails),
            new AssetBindings(sourceBindings.inputs(), Map.copyOf(bindingByElementId)));
    }

    private static void remapBindings(Map<String, AssetBinding> bindings, String sourceAlias, int inputIndex) {
        for (Map.Entry<String, AssetBinding> entry : bindings.entrySet()) {
            AssetBinding source = entry.getValue();
            if (source.input().alias().equals(sourceAlias)) {
                entry.setValue(new AssetBinding(source.input(), source.facts(), inputIndex));
            }
        }
    }

    private static void validateAssetFacts(ParsedTimeline timeline, AssetBindings bindings) {
        for (AssetElement assetElement : timeline.assetElements().values()) {
            AssetBinding binding = bindings.byElementId().get(assetElement.elementId());
            if (binding == null || binding.facts().assetType() != assetElement.assetType()) {
                throw inputInvalid();
            }
            validateMediaFacts(binding.facts(), assetElement.assetType());
            TimelineElementDTO element = assetElement.tracked().element();
            if (element instanceof TimelineMainVideoElementDTO main) {
                validateVideoRange(main.sourceDurationMs(), main.sourceStartMs(), main.endMs() - main.startMs(),
                    binding.facts());
            } else if (element instanceof TimelinePipVideoElementDTO pip) {
                validatePipRange(pip.sourceDurationMs(), pip.sourceStartMs(), binding.facts());
            } else if (element instanceof TimelineAudioElementDTO audio) {
                validateAudioRange(audio, binding.facts());
            }
        }
    }

    private static void validateMediaFacts(CreationAssetResolveDTO facts, CreationAssetType expectedType) {
        if (facts.assetType() != expectedType || !allowedMimeType(expectedType, facts.mimeType())) {
            throw inputInvalid();
        }
        switch (expectedType) {
            case VIDEO -> {
                if (facts.sizeBytes() > MAX_VIDEO_BYTES || !facts.hasVideoStream() || facts.durationMs() == null
                    || facts.durationMs() < 1 || facts.durationMs() > MAX_VIDEO_DURATION_MS || facts.width() == null
                    || facts.height() == null || facts.width() < 1 || facts.width() > MAX_VIDEO_WIDTH
                    || facts.height() < 1 || facts.height() > MAX_VIDEO_HEIGHT) {
                    throw inputInvalid();
                }
            }
            case IMAGE -> {
                if (facts.sizeBytes() > MAX_IMAGE_BYTES || !facts.hasVideoStream() || facts.width() == null
                    || facts.height() == null || facts.width() < 1 || facts.width() > MAX_IMAGE_WIDTH
                    || facts.height() < 1 || facts.height() > MAX_IMAGE_HEIGHT) {
                    throw inputInvalid();
                }
            }
            case AUDIO -> {
                if (facts.sizeBytes() > MAX_AUDIO_BYTES || !facts.hasAudioStream() || facts.durationMs() == null
                    || facts.durationMs() < 1 || facts.durationMs() > MAX_AUDIO_DURATION_MS) {
                    throw inputInvalid();
                }
            }
        }
    }

    private static boolean allowedMimeType(CreationAssetType type, String mimeType) {
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case VIDEO -> VIDEO_MIME_TYPES.contains(normalized);
            case IMAGE -> IMAGE_MIME_TYPES.contains(normalized);
            case AUDIO -> AUDIO_MIME_TYPES.contains(normalized);
        };
    }

    private static void validateVideoRange(long sourceDurationMs, long sourceStartMs, long requiredDurationMs,
                                           CreationAssetResolveDTO facts) {
        if (facts.durationMs() == null || sourceDurationMs != facts.durationMs() || sourceStartMs < 0
            || sourceStartMs + requiredDurationMs > sourceDurationMs) {
            throw inputInvalid();
        }
    }

    private static void validatePipRange(long sourceDurationMs, long sourceStartMs, CreationAssetResolveDTO facts) {
        if (facts.durationMs() == null || sourceDurationMs != facts.durationMs() || sourceStartMs < 0
            || sourceStartMs >= sourceDurationMs) {
            throw inputInvalid();
        }
    }

    private static void validateAudioRange(TimelineAudioElementDTO audio, CreationAssetResolveDTO facts) {
        if (facts.durationMs() == null || audio.sourceDurationMs() != facts.durationMs()
            || audio.sourceEndMs() > facts.durationMs()) {
            throw inputInvalid();
        }
    }

    private static PrimaryAudio validatePrimaryAudio(ParsedTimeline timeline, AssetBindings bindings) {
        AssetBinding base = bindingFor(timeline.mainVideo().element().elementId(), bindings);
        boolean baseAudio = base.facts().hasAudioStream();
        List<Tracked<TimelineAudioElementDTO>> external = timeline.primaryAudio().stream().filter(Tracked::active)
            .sorted(Comparator.comparingLong((Tracked<TimelineAudioElementDTO> value) -> value.element().startMs())
                .thenComparingLong(value -> value.element().endMs()).thenComparing(value -> value.element().elementId()))
            .toList();
        if (baseAudio && !external.isEmpty()) {
            throw inputInvalid();
        }
        if (baseAudio) {
            return new PrimaryAudio(List.of(PrimaryAudioClip.fromBase(timeline.mainVideo(), base)));
        }
        List<PrimaryAudioClip> clips = new ArrayList<>();
        long previousEndMs = -1L;
        for (Tracked<TimelineAudioElementDTO> primary : external) {
            if (primary.element().startMs() < previousEndMs) {
                throw inputInvalid();
            }
            clips.add(PrimaryAudioClip.fromElement(primary, bindingFor(primary.element().elementId(), bindings)));
            previousEndMs = primary.element().endMs();
        }
        return new PrimaryAudio(clips);
    }

    private String assScript(ParsedTimeline timeline) {
        List<TimelineSubtitleElementDTO> subtitles = timeline.subtitles().stream().filter(Tracked::active)
            .map(Tracked::element).toList();
        List<TimelineFancyTextElementDTO> fancyTexts = timeline.fancyTexts().stream().filter(Tracked::active)
            .map(Tracked::element).toList();
        try {
            return assScriptWriter.write(CANVAS_WIDTH, CANVAS_HEIGHT, subtitles, fancyTexts);
        } catch (IllegalArgumentException exception) {
            throw inputInvalid();
        }
    }

    private static String filterScript(ParsedTimeline timeline,
                                       AssetBindings bindings,
                                       PrimaryAudio primaryAudio) {
        List<String> chains = new ArrayList<>();
        String currentVideo = "v0";
        appendMainVideo(chains, timeline.mainVideo(), bindingFor(timeline.mainVideo().element().elementId(), bindings));
        int nextVideoLabel = 1;
        List<VisualLayer> layers = new ArrayList<>();
        for (Tracked<TimelineImageElementDTO> image : timeline.images()) {
            if (image.active()) {
                layers.add(VisualLayer.image(image, bindingFor(image.element().elementId(), bindings)));
            }
        }
        for (Tracked<TimelinePipVideoElementDTO> pip : timeline.pips()) {
            if (pip.active()) {
                layers.add(VisualLayer.pip(pip, bindingFor(pip.element().elementId(), bindings)));
            }
        }
        layers.sort(Comparator.comparingInt(VisualLayer::zIndex).thenComparingInt(VisualLayer::trackOrder)
            .thenComparingLong(VisualLayer::startMs).thenComparing(VisualLayer::elementId));
        for (int index = 0; index < layers.size(); index++) {
            VisualLayer layer = layers.get(index);
            String layerLabel = "layer" + index;
            appendVisualLayer(chains, layer, layerLabel);
            String next = "v" + nextVideoLabel++;
            chains.add("[%s][%s]overlay=x=%d:y=%d:eof_action=pass:repeatlast=0[%s]".formatted(currentVideo,
                layerLabel, layer.x(), layer.y(), next));
            currentVideo = next;
        }
        List<Tracked<TimelineVisualEffectElementDTO>> activeEffects = timeline.effects().stream().filter(Tracked::active)
            .sorted(Comparator.comparingInt((Tracked<TimelineVisualEffectElementDTO> effect) -> effect.element().zIndex())
                .thenComparingInt(effect -> effect.track().order()).thenComparingLong(effect -> effect.element().startMs())
                .thenComparing(effect -> effect.element().elementId()))
            .toList();
        for (TimelineVisualEffectElementDTO effect : activeEffects.stream().map(Tracked::element).toList()) {
            String next = "v" + nextVideoLabel++;
            chains.add("[%s]%s[%s]".formatted(currentVideo, effectFilter(effect), next));
            currentVideo = next;
        }
        chains.add("[%s]ass=%s:fontsdir=%s,format=yuv420p[vout]".formatted(currentVideo,
            TimelineRenderPlan.ASS_FILE_NAME, TimelineRenderPlan.FONTS_DIRECTORY_NAME));
        appendAudio(chains, timeline, bindings, primaryAudio);
        return String.join(";\n", chains) + '\n';
    }

    private static void appendMainVideo(List<String> chains,
                                        Tracked<TimelineMainVideoElementDTO> main,
                                        AssetBinding binding) {
        TimelineMainVideoElementDTO element = main.element();
        chains.add("[%d:v]trim=start=%s:duration=%s,setpts=PTS-STARTPTS,%s[v0]".formatted(binding.inputIndex(),
            seconds(element.sourceStartMs()), seconds(element.endMs() - element.startMs()),
            fitFilter(element.fitMode(), CANVAS_WIDTH, CANVAS_HEIGHT, false)));
    }

    private static void appendVisualLayer(List<String> chains, VisualLayer layer, String outputLabel) {
        StringBuilder filter = new StringBuilder("[%d:v]".formatted(layer.binding().inputIndex()));
        if (layer.pip()) {
            filter.append("trim=duration=").append(seconds(layer.durationMs()))
                .append(",setpts=PTS-STARTPTS,fps=").append(FRAME_RATE);
        } else {
            filter.append("trim=duration=").append(seconds(layer.durationMs()));
        }
        filter.append(',').append(cropFilter(layer.crop(), layer.binding().facts().width(), layer.binding().facts().height()))
            .append(',').append(fitFilter(layer.fitMode(), layer.width(), layer.height(), true))
            .append(",format=rgba,colorchannelmixer=aa=").append(decimal(layer.transform().opacity()));
        if (layer.transform().rotationDeg().signum() != 0) {
            filter.append(",rotate=").append(decimal(layer.transform().rotationDeg()))
                .append("*PI/180:ow=rotw(iw):oh=roth(ih):c=none");
        }
        appendVideoFade(filter, layer.fade(), layer.durationMs());
        filter.append(",setpts=PTS-STARTPTS+").append(seconds(layer.startMs())).append("/TB[")
            .append(outputLabel).append(']');
        chains.add(filter.toString());
    }

    private static void appendVideoFade(StringBuilder filter, TimelineFadeDTO fade, long durationMs) {
        if (fade.fadeInMs() > 0) {
            filter.append(",fade=t=in:st=0:d=").append(seconds(fade.fadeInMs())).append(":alpha=1");
        }
        if (fade.fadeOutMs() > 0) {
            filter.append(",fade=t=out:st=").append(seconds(durationMs - fade.fadeOutMs()))
                .append(":d=").append(seconds(fade.fadeOutMs())).append(":alpha=1");
        }
    }

    private static String effectFilter(TimelineVisualEffectElementDTO effect) {
        return switch (effect.effectCode()) {
            case FADE_IN -> "fade=t=in:st=%s:d=%s".formatted(seconds(effect.startMs()),
                seconds(effect.durationMs()));
            case FADE_OUT -> "fade=t=out:st=%s:d=%s".formatted(seconds(effect.endMs() - effect.durationMs()),
                seconds(effect.durationMs()));
            case GENTLE_ZOOM_IN -> zoomFilter(effect, true);
            case GENTLE_ZOOM_OUT -> zoomFilter(effect, false);
            case LIGHT_BLUR -> "boxblur=luma_radius=%s:luma_power=1:enable='gte(t,%s)*lt(t,%s)'".formatted(
                decimal(effect.radius()), seconds(effect.startMs()), seconds(effect.startMs() + effect.durationMs()));
        };
    }

    private static String zoomFilter(TimelineVisualEffectElementDTO effect, boolean zoomIn) {
        long startFrame = frameAtOrAfter(effect.startMs());
        long endFrame = frameAtOrAfter(effect.startMs() + effect.durationMs()) - 1;
        String scale = decimal(effect.scale());
        String expression = zoomIn
            ? "if(between(on,%d,%d),min(zoom+0.002,%s),zoom)".formatted(startFrame, endFrame, scale)
            : "if(between(on,%d,%d),max(1,%s-(on-%d)*0.002),1)".formatted(startFrame, endFrame, scale,
                startFrame);
        return "zoompan=z='%s':d=1:s=%dx%d:fps=%d".formatted(expression, CANVAS_WIDTH, CANVAS_HEIGHT,
            FRAME_RATE);
    }

    private static long frameAtOrAfter(long milliseconds) {
        return (milliseconds * FRAME_RATE + 999L) / 1_000L;
    }

    private static void appendAudio(List<String> chains,
                                    ParsedTimeline timeline,
                                    AssetBindings bindings,
                                    PrimaryAudio primaryAudio) {
        List<String> labels = new ArrayList<>();
        appendPrimaryAudio(chains, primaryAudio, labels);
        int nextLabel = 0;
        for (Tracked<TimelineAudioElementDTO> bgm : timeline.backgroundMusic()) {
            if (!bgm.active()) {
                continue;
            }
            String label = "abgm" + nextLabel++;
            appendBackgroundMusic(chains, bgm, bindingFor(bgm.element().elementId(), bindings), label, primaryAudio);
            labels.add(label);
        }
        nextLabel = 0;
        for (Tracked<TimelineAudioElementDTO> soundEffect : timeline.soundEffects()) {
            if (!soundEffect.active()) {
                continue;
            }
            String label = "asfx" + nextLabel++;
            appendSupplementalAudio(chains, soundEffect, bindingFor(soundEffect.element().elementId(), bindings), label,
                soundEffect.element().volumeRatio());
            labels.add(label);
        }
        if (labels.isEmpty()) {
            chains.add("anullsrc=r=48000:cl=stereo,atrim=duration=%s[aout]".formatted(
                seconds(timeline.canvas().durationMs())));
            return;
        }
        StringBuilder mix = new StringBuilder();
        for (String label : labels) {
            mix.append('[').append(label).append(']');
        }
        mix.append("amix=inputs=").append(labels.size()).append(":duration=longest:dropout_transition=0:normalize=0")
            .append(",apad=pad_dur=").append(seconds(timeline.canvas().durationMs()))
            .append(",atrim=duration=").append(seconds(timeline.canvas().durationMs())).append("[aout]");
        chains.add(mix.toString());
    }

    private static void appendPrimaryAudio(List<String> chains,
                                           PrimaryAudio primaryAudio,
                                           List<String> labels) {
        for (int index = 0; index < primaryAudio.clips().size(); index++) {
            PrimaryAudioClip clip = primaryAudio.clips().get(index);
            String outputLabel = "aprimary" + index;
            if (clip.baseVideo()) {
                TimelineMainVideoElementDTO main = clip.mainVideo();
                chains.add("[%d:a]atrim=start=%s:duration=%s,asetpts=PTS-STARTPTS[%s]".formatted(
                    clip.binding().inputIndex(), seconds(main.sourceStartMs()), seconds(clip.endMs() - clip.startMs()),
                    outputLabel));
            } else {
                TimelineAudioElementDTO audio = clip.audio();
                StringBuilder filter = new StringBuilder(
                    "[%d:a]atrim=start=%s:end=%s,asetpts=PTS-STARTPTS,atrim=duration=%s,volume=%s".formatted(
                        clip.binding().inputIndex(), seconds(audio.sourceStartMs()), seconds(audio.sourceEndMs()),
                        seconds(clip.endMs() - clip.startMs()), decimal(audio.volumeRatio())));
                appendAudioFade(filter, audio.fade(), clip.endMs() - clip.startMs());
                filter.append(",adelay=").append(clip.startMs()).append(":all=1[").append(outputLabel).append(']');
                chains.add(filter.toString());
            }
            labels.add(outputLabel);
        }
    }

    private static void appendBackgroundMusic(List<String> chains,
                                              Tracked<TimelineAudioElementDTO> tracked,
                                              AssetBinding binding,
                                              String outputLabel,
                                              PrimaryAudio primaryAudio) {
        TimelineAudioElementDTO audio = tracked.element();
        StringBuilder filter = new StringBuilder(
            "[%d:a]atrim=start=%s:end=%s,asetpts=PTS-STARTPTS,aresample=48000,aloop=loop=-1:size=%d:start=0,atrim=duration=%s".formatted(
                binding.inputIndex(), seconds(audio.sourceStartMs()), seconds(audio.sourceEndMs()),
                samplesAt48Khz(audio.sourceEndMs() - audio.sourceStartMs()), seconds(audio.endMs() - audio.startMs())));
        appendAudioFade(filter, audio.fade(), audio.endMs() - audio.startMs());
        filter.append(",adelay=").append(audio.startMs()).append(":all=1,volume='")
            .append(duckingVolumeExpression(audio, primaryAudio)).append("':eval=frame[")
            .append(outputLabel).append(']');
        chains.add(filter.toString());
    }

    private static void appendSupplementalAudio(List<String> chains,
                                                Tracked<TimelineAudioElementDTO> tracked,
                                                AssetBinding binding,
                                                String outputLabel,
                                                BigDecimal volume) {
        TimelineAudioElementDTO audio = tracked.element();
        StringBuilder filter = new StringBuilder(
            "[%d:a]atrim=start=%s:end=%s,asetpts=PTS-STARTPTS,atrim=duration=%s,volume=%s".formatted(
                binding.inputIndex(), seconds(audio.sourceStartMs()), seconds(audio.sourceEndMs()),
                seconds(audio.endMs() - audio.startMs()), decimal(volume)));
        appendAudioFade(filter, audio.fade(), audio.endMs() - audio.startMs());
        filter.append(",adelay=").append(audio.startMs()).append(":all=1[").append(outputLabel).append(']');
        chains.add(filter.toString());
    }

    private static String duckingVolumeExpression(TimelineAudioElementDTO backgroundMusic,
                                                   PrimaryAudio primaryAudio) {
        String base = decimal(backgroundMusic.volumeRatio());
        List<AudioWindow> windows = primaryAudio.clips().stream().map(clip -> new AudioWindow(
            Math.max(backgroundMusic.startMs(), clip.startMs()), Math.min(backgroundMusic.endMs(), clip.endMs())
        )).filter(window -> window.startMs() < window.endMs()).toList();
        if (windows.isEmpty()) {
            return base;
        }
        String expression = base;
        for (int index = windows.size() - 1; index >= 0; index--) {
            AudioWindow window = windows.get(index);
            expression = duckingWindowExpression(backgroundMusic, window.startMs(), window.endMs(), expression);
        }
        return expression;
    }

    private static String duckingWindowExpression(TimelineAudioElementDTO backgroundMusic,
                                                  long startMs,
                                                  long endMs,
                                                  String afterWindow) {
        String base = decimal(backgroundMusic.volumeRatio());
        long overlapMs = endMs - startMs;
        long attackMs = Math.min(backgroundMusic.attackMs(), overlapMs / 2);
        long releaseMs = Math.min(backgroundMusic.releaseMs(), overlapMs - attackMs);
        BigDecimal duckedVolume = backgroundMusic.volumeRatio().multiply(backgroundMusic.targetGainRatio());
        BigDecimal delta = backgroundMusic.volumeRatio().subtract(duckedVolume);
        String active = decimal(duckedVolume);
        if (releaseMs > 0) {
            long releaseStartMs = endMs - releaseMs;
            active = "if(lt(t,%s),%s,%s+(%s)*(t-%s)/%s)".formatted(seconds(releaseStartMs), active,
                decimal(duckedVolume), decimal(delta), seconds(releaseStartMs), seconds(releaseMs));
        }
        if (attackMs > 0) {
            long attackEndMs = startMs + attackMs;
            active = "if(lt(t,%s),%s-(%s)*(t-%s)/%s,%s)".formatted(seconds(attackEndMs), base,
                decimal(delta), seconds(startMs), seconds(attackMs), active);
        }
        return "if(lt(t,%s),%s,if(lt(t,%s),%s,%s))".formatted(seconds(startMs), base, seconds(endMs), active,
            afterWindow);
    }

    private static long samplesAt48Khz(long durationMs) {
        try {
            return Math.multiplyExact(durationMs, 48L);
        } catch (ArithmeticException exception) {
            throw inputInvalid();
        }
    }

    private static void appendAudioFade(StringBuilder filter, TimelineFadeDTO fade, long durationMs) {
        if (fade.fadeInMs() > 0) {
            filter.append(",afade=t=in:st=0:d=").append(seconds(fade.fadeInMs()));
        }
        if (fade.fadeOutMs() > 0) {
            filter.append(",afade=t=out:st=").append(seconds(durationMs - fade.fadeOutMs()))
                .append(":d=").append(seconds(fade.fadeOutMs()));
        }
    }

    private static String cropFilter(TimelineCropDTO crop, int sourceWidth, int sourceHeight) {
        int width = pixels(crop.widthRatio(), sourceWidth);
        int height = pixels(crop.heightRatio(), sourceHeight);
        int x = pixels(crop.xRatio(), sourceWidth);
        int y = pixels(crop.yRatio(), sourceHeight);
        if (width < 1 || height < 1 || x < 0 || y < 0 || x + width > sourceWidth || y + height > sourceHeight) {
            throw inputInvalid();
        }
        return "crop=%d:%d:%d:%d".formatted(width, height, x, y);
    }

    private static String fitFilter(TimelineFitMode fitMode, int width, int height, boolean transparentPadding) {
        if (fitMode == null || width < 1 || height < 1) {
            throw inputInvalid();
        }
        return switch (fitMode) {
            case CONTAIN -> "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2:color=%s"
                .formatted(width, height, width, height, transparentPadding ? "black@0" : "black");
            case COVER -> "scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d"
                .formatted(width, height, width, height);
        };
    }

    private static AssetBinding bindingFor(String elementId, AssetBindings bindings) {
        AssetBinding binding = bindings.byElementId().get(elementId);
        if (binding == null) {
            throw inputInvalid();
        }
        return binding;
    }

    private static void registerAsset(Map<String, AssetElement> assets,
                                      String elementId,
                                      String assetId,
                                      TimelineAssetUsageType usageType,
                                      CreationAssetType assetType,
                                      Tracked<?> tracked) {
        if (!decimalId(assetId) || assets.putIfAbsent(elementId,
            new AssetElement(elementId, assetId, usageType, assetType, tracked)) != null) {
            throw inputInvalid();
        }
    }

    private static TimelineAssetUsageType usageForTrack(TimelineTrackType trackType) {
        return switch (trackType) {
            case PRIMARY_AUDIO -> TimelineAssetUsageType.PRIMARY_AUDIO;
            case BACKGROUND_MUSIC -> TimelineAssetUsageType.BACKGROUND_MUSIC;
            case SOUND_EFFECT -> TimelineAssetUsageType.SOUND_EFFECT;
            default -> throw inputInvalid();
        };
    }

    private static <T extends TimelineElementDTO> T requireType(TimelineElementDTO element,
                                                                 Class<T> expectedType,
                                                                 TimelineElementType expectedElementType) {
        if (!expectedType.isInstance(element) || element.elementType() != expectedElementType) {
            throw inputInvalid();
        }
        return expectedType.cast(element);
    }

    private static <T> List<T> requireList(List<T> values, int minimum, int maximum) {
        if (values == null || values.size() < minimum || values.size() > maximum) {
            throw inputInvalid();
        }
        return values;
    }

    private static BigDecimal boundedDecimal(BigDecimal value,
                                             BigDecimal minimum,
                                             BigDecimal maximum,
                                             boolean strictlyPositive) {
        if (value == null || value.scale() < 0 || value.scale() > MAX_DECIMAL_PLACES
            || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0
            || (strictlyPositive && value.signum() <= 0)) {
            throw inputInvalid();
        }
        return value;
    }

    private static String decimal(BigDecimal value) {
        boundedDecimal(value, new BigDecimal("-1000000"), new BigDecimal("1000000"), false);
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static int pixels(BigDecimal ratio, int dimension) {
        try {
            return ratio.multiply(BigDecimal.valueOf(dimension)).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException exception) {
            throw inputInvalid();
        }
    }

    private static String seconds(long milliseconds) {
        if (milliseconds < 0 || milliseconds > MAX_DURATION_MS) {
            throw inputInvalid();
        }
        return BigDecimal.valueOf(milliseconds, 3).stripTrailingZeros().toPlainString();
    }

    private static boolean safeIdentifier(String value) {
        return value != null && !value.isBlank() && value.length() <= 128 && value.indexOf('\0') < 0;
    }

    private static boolean safeKey(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    private static boolean decimalId(String value) {
        return value != null && DECIMAL_ID.matcher(value).matches();
    }

    private static boolean safeSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static boolean safeMimeType(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || value.indexOf('\0') >= 0) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9') || character == '/' || character == '.'
                || character == '+' || character == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean safeLabel(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > limit("maxLabelCodePoints")) {
            return false;
        }
        return value.codePoints().noneMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL);
    }

    private static int usageOrder(TimelineAssetUsageType usageType) {
        return switch (usageType) {
            case BASE_VIDEO -> 0;
            case IMAGE -> 1;
            case PIP_VIDEO -> 2;
            case PRIMARY_AUDIO -> 3;
            case BACKGROUND_MUSIC -> 4;
            case SOUND_EFFECT -> 5;
        };
    }

    private static int limit(String name) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(name).intValueExact();
    }

    private static BigDecimal decimalLimit(String name) {
        return TimelineContractLimits.NUMERIC_LIMITS.get(name);
    }

    private static TimelineExecutionException inputInvalid() {
        return new TimelineExecutionException("timeline render input is invalid", TimelineExecutionFailureCode.INPUT_INVALID,
            false, null);
    }

    private static TimelineExecutionException fontUnavailable() {
        return new TimelineExecutionException("timeline render font is unavailable",
            TimelineExecutionFailureCode.FONT_UNAVAILABLE, false, null);
    }

    private record ParsedTimeline(
        TimelineCanvasDTO canvas,
        Tracked<TimelineMainVideoElementDTO> mainVideo,
        List<Tracked<TimelineImageElementDTO>> images,
        List<Tracked<TimelinePipVideoElementDTO>> pips,
        List<Tracked<TimelineSubtitleElementDTO>> subtitles,
        List<Tracked<TimelineFancyTextElementDTO>> fancyTexts,
        List<Tracked<TimelineAudioElementDTO>> primaryAudio,
        List<Tracked<TimelineAudioElementDTO>> backgroundMusic,
        List<Tracked<TimelineAudioElementDTO>> soundEffects,
        List<Tracked<TimelineVisualEffectElementDTO>> effects,
        Map<String, AssetElement> assetElements
    ) {
    }

    private record Tracked<T extends TimelineElementDTO>(T element, TimelineTrackDTO track) {
        private boolean active() {
            return element.enabled() && !track.muted();
        }
    }

    private record AssetElement(String elementId,
                                String assetId,
                                TimelineAssetUsageType usageType,
                                CreationAssetType assetType,
                                Tracked<?> tracked) {
    }

    private record AssetKey(String assetId, TimelineAssetUsageType usageType) {
    }

    private record AssetBinding(TimelineRenderPlan.Input input,
                                CreationAssetResolveDTO facts,
                                int inputIndex) {
    }

    private record AssetBindings(List<TimelineRenderPlan.Input> inputs,
                                 Map<String, AssetBinding> byElementId) {
    }

    private record PreparedInputs(List<TimelineRenderPlan.RenderInput> renderInputs,
                                  List<TimelineRenderPlan.PipTail> pipTails,
                                  AssetBindings bindings) {
    }

    private record PrimaryAudio(List<PrimaryAudioClip> clips) {
        private PrimaryAudio {
            clips = List.copyOf(clips);
        }
    }

    private record PrimaryAudioClip(boolean baseVideo,
                                    TimelineMainVideoElementDTO mainVideo,
                                    TimelineAudioElementDTO audio,
                                    AssetBinding binding,
                                    long startMs,
                                    long endMs) {
        private static PrimaryAudioClip fromBase(Tracked<TimelineMainVideoElementDTO> main, AssetBinding binding) {
            return new PrimaryAudioClip(true, main.element(), null, binding, main.element().startMs(),
                main.element().endMs());
        }

        private static PrimaryAudioClip fromElement(Tracked<TimelineAudioElementDTO> element, AssetBinding binding) {
            return new PrimaryAudioClip(false, null, element.element(), binding, element.element().startMs(),
                element.element().endMs());
        }
    }

    private record AudioWindow(long startMs, long endMs) {
    }

    private record VisualLayer(boolean pip,
                               String elementId,
                               long startMs,
                               long durationMs,
                               int zIndex,
                               int trackOrder,
                               TimelineVisualTransformDTO transform,
                               TimelineCropDTO crop,
                               TimelineFadeDTO fade,
                               TimelineFitMode fitMode,
                               long sourceStartMs,
                               AssetBinding binding) {
        private static VisualLayer image(Tracked<TimelineImageElementDTO> tracked, AssetBinding binding) {
            TimelineImageElementDTO image = tracked.element();
            return new VisualLayer(false, image.elementId(), image.startMs(), image.endMs() - image.startMs(),
                image.zIndex(), tracked.track().order(), image.transform(), image.crop(), image.fade(), image.fitMode(),
                0L, binding);
        }

        private static VisualLayer pip(Tracked<TimelinePipVideoElementDTO> tracked, AssetBinding binding) {
            TimelinePipVideoElementDTO pip = tracked.element();
            return new VisualLayer(true, pip.elementId(), pip.startMs(), pip.endMs() - pip.startMs(), pip.zIndex(),
                tracked.track().order(), pip.transform(), pip.crop(), pip.fade(), pip.fitMode(), pip.sourceStartMs(),
                binding);
        }

        private int x() {
            return pixels(transform.xRatio(), CANVAS_WIDTH);
        }

        private int y() {
            return pixels(transform.yRatio(), CANVAS_HEIGHT);
        }

        private int width() {
            return pixels(transform.widthRatio(), CANVAS_WIDTH);
        }

        private int height() {
            return pixels(transform.heightRatio(), CANVAS_HEIGHT);
        }

    }
}
