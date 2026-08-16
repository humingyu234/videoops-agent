package org.dromara.aivideo.timeline;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import org.dromara.aivideo.timeline.dto.TimelineOutputConfigDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
public class TimelineDtoContractTest {

    private static final String TIMELINE_DTO = "org.dromara.aivideo.timeline.dto.";
    private static final String TIMELINE_ENUM = "org.dromara.aivideo.timeline.enums.";
    private static final String CREATION_DTO = "org.dromara.aivideo.creation.dto.";
    private static final String CREATION_ENUM = "org.dromara.aivideo.creation.enums.";
    private static final String TASK_ENUM = "org.dromara.aivideo.task.enums.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void recordsFreezeComponentOrderAndJavaTypes() throws Exception {
        Map<String, List<Component>> contracts = new LinkedHashMap<>();
        contracts.put(TIMELINE_DTO + "TimelineDocumentDTO", components(
            "schemaVersion", "java.lang.String",
            "canvas", TIMELINE_DTO + "TimelineCanvasDTO",
            "tracks", listOf(TIMELINE_DTO + "TimelineTrackDTO")));
        contracts.put(TIMELINE_DTO + "TimelineCanvasDTO", components(
            "width", "int", "height", "int", "frameRate", "int", "durationMs", "long",
            "safeMarginRatio", BigDecimal.class.getName()));
        contracts.put(TIMELINE_DTO + "TimelineTrackDTO", components(
            "trackId", "java.lang.String", "trackType", TIMELINE_ENUM + "TimelineTrackType",
            "area", TIMELINE_ENUM + "TimelineTrackArea", "order", "int", "locked", "boolean",
            "muted", "boolean", "elements", listOf(TIMELINE_DTO + "TimelineElementDTO")));
        contracts.put(TIMELINE_DTO + "TimelineMainVideoElementDTO", commonElementComponents(
            "assetId", "java.lang.String", "sourceDurationMs", "long", "sourceStartMs", "long",
            "fitMode", TIMELINE_ENUM + "TimelineFitMode"));
        contracts.put(TIMELINE_DTO + "TimelineImageElementDTO", commonElementComponents(
            "assetId", "java.lang.String", "transform", TIMELINE_DTO + "TimelineVisualTransformDTO",
            "fitMode", TIMELINE_ENUM + "TimelineFitMode", "crop", TIMELINE_DTO + "TimelineCropDTO",
            "fade", TIMELINE_DTO + "TimelineFadeDTO", "sourceStartOffset", "int",
            "sourceEndOffset", "int", "adoptedPrompt", "java.lang.String", "sourceTaskId", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelinePipVideoElementDTO", commonElementComponents(
            "assetId", "java.lang.String", "transform", TIMELINE_DTO + "TimelineVisualTransformDTO",
            "fitMode", TIMELINE_ENUM + "TimelineFitMode", "crop", TIMELINE_DTO + "TimelineCropDTO",
            "fade", TIMELINE_DTO + "TimelineFadeDTO", "sourceDurationMs", "long", "sourceStartMs", "long",
            "loopWhenOverflow", "boolean", "audioEnabled", "boolean"));
        contracts.put(TIMELINE_DTO + "TimelineSubtitleElementDTO", commonElementComponents(
            "sourceTextSnapshot", "java.lang.String", "displayText", "java.lang.String",
            "sourceStartOffset", "int", "sourceEndOffset", "int", "fontCode", "java.lang.String",
            "fontVersion", "java.lang.String", "fontSha256", "java.lang.String", "fontSizePx", "int",
            "color", "java.lang.String", "backgroundEnabled", "boolean", "backgroundColor", "java.lang.String",
            "outlineEnabled", "boolean", "outlineColor", "java.lang.String", "outlineWidthPx", "int",
            "safeAreaAnchor", "java.lang.String", "alignment", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineFancyTextElementDTO", commonElementComponents(
            "text", "java.lang.String", "templateCode", TIMELINE_ENUM + "FancyTextTemplateCode",
            "fontCode", "java.lang.String", "fontVersion", "java.lang.String", "fontSha256", "java.lang.String",
            "color", "java.lang.String", "accentColor", "java.lang.String",
            "transform", TIMELINE_DTO + "TimelineVisualTransformDTO", "animationIntensity", "java.lang.String",
            "enterDurationMs", "long", "exitDurationMs", "long", "suggestionTaskId", "java.lang.String",
            "suggestionReason", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineAudioElementDTO", commonElementComponents(
            "assetId", "java.lang.String", "usageType", TIMELINE_ENUM + "TimelineAssetUsageType",
            "sourceDurationMs", "long", "sourceStartMs", "long", "sourceEndMs", "long",
            "volumeRatio", BigDecimal.class.getName(), "fade", TIMELINE_DTO + "TimelineFadeDTO",
            "loopWhenOverflow", "boolean", "duckingEnabled", "boolean",
            "targetGainRatio", BigDecimal.class.getName(), "attackMs", "int", "releaseMs", "int"));
        contracts.put(TIMELINE_DTO + "TimelineVisualEffectElementDTO", commonElementComponents(
            "effectCode", TIMELINE_ENUM + "TimelineVisualEffectCode", "durationMs", "long",
            "scale", BigDecimal.class.getName(), "radius", BigDecimal.class.getName()));
        contracts.put(TIMELINE_DTO + "TimelineVisualTransformDTO", components(
            "xRatio", BigDecimal.class.getName(), "yRatio", BigDecimal.class.getName(),
            "widthRatio", BigDecimal.class.getName(), "heightRatio", BigDecimal.class.getName(),
            "rotationDeg", BigDecimal.class.getName(), "opacity", BigDecimal.class.getName()));
        contracts.put(TIMELINE_DTO + "TimelineCropDTO", components(
            "xRatio", BigDecimal.class.getName(), "yRatio", BigDecimal.class.getName(),
            "widthRatio", BigDecimal.class.getName(), "heightRatio", BigDecimal.class.getName()));
        contracts.put(TIMELINE_DTO + "TimelineFadeDTO", components(
            "fadeInMs", "long", "fadeOutMs", "long"));
        contracts.put(TIMELINE_DTO + "TimelineAssetReferenceDTO", components(
            "assetId", "java.lang.String", "usageType", TIMELINE_ENUM + "TimelineAssetUsageType",
            "elementIds", listOf("java.lang.String"), "sha256", "java.lang.String", "fileSize", "long"));
        contracts.put(TIMELINE_DTO + "TimelineNormalizationChangeDTO", components(
            "elementId", "java.lang.String", "changeType", "java.lang.String", "beforeDigest", "java.lang.String",
            "afterDigest", "java.lang.String", "safeMessage", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineTextMeasureCommandDTO", components(
            "requestId", "java.lang.String", "fontCode", "java.lang.String", "text", "java.lang.String",
            "fontSizePx", "int", "canvasWidthPx", "int", "outlineWidthPx", "int",
            "safeMarginRatio", BigDecimal.class.getName()));
        contracts.put(TIMELINE_DTO + "TimelineTextMeasureResultDTO", components(
            "requestId", "java.lang.String", "fontCode", "java.lang.String", "fontVersion", "java.lang.String",
            "fontSha256", "java.lang.String", "fontRegistrySha256", "java.lang.String", "widthPx", "int",
            "heightPx", "int", "allCodePointsSupported", "boolean"));
        contracts.put(TIMELINE_DTO + "TimelineOutputConfigDTO", components(
            "resolutionPreset", "java.lang.String", "frameRate", "int",
            "qualityPreset", TIMELINE_ENUM + "TimelineOutputQuality"));
        contracts.put(TIMELINE_DTO + "TimelineMediaProbeDTO", components(
            "assetId", "java.lang.String", "mediaType", "java.lang.String", "formatName", "java.lang.String",
            "durationMs", "long", "fileSize", "long", "width", "java.lang.Integer", "height", "java.lang.Integer",
            "frameRate", "java.lang.Integer", "sampleRate", "java.lang.Integer", "channels", "java.lang.Integer",
            "videoStream", "boolean", "audioStream", "boolean", "videoCodec", "java.lang.String",
            "audioCodec", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineMediaQualityInspectionDTO", components(
            "probe", TIMELINE_DTO + "TimelineMediaProbeDTO", "fullyDecoded", "boolean"));
        contracts.put(TIMELINE_DTO + "TimelineRenderCommandDTO", components(
            "taskId", "java.lang.String", "executionId", "java.lang.String", "attemptId", "java.lang.String",
            "inputVersionId", "java.lang.String", "fontRegistryVersion", "java.lang.String",
            "fontRegistrySha256", "java.lang.String", "timeline", TIMELINE_DTO + "TimelineDocumentDTO",
            "outputConfig", TIMELINE_DTO + "TimelineOutputConfigDTO",
            "assets", listOf(TIMELINE_DTO + "TimelineAssetReferenceDTO")));
        contracts.put(TIMELINE_DTO + "TimelineRenderResultDTO", components(
            "fileName", "java.lang.String", "contentType", "java.lang.String", "sha256", "java.lang.String",
            "fileSize", "long", "durationMs", "long", "width", "int", "height", "int", "frameRate", "int"));
        contracts.put(TIMELINE_DTO + "TimelineProgressDTO", components(
            "stage", TASK_ENUM + "AiTaskStage", "percent", "int", "safeMessage", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineImagePromptCommandDTO", components(
            "taskId", "java.lang.String", "projectId", "java.lang.String", "draftRevision", "java.lang.String",
            "sourceStartOffset", "int", "sourceEndOffset", "int", "sourceText", "java.lang.String",
            "contextBefore", "java.lang.String", "contextAfter", "java.lang.String", "canvasAspect", "java.lang.String",
            "styleCode", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineImagePromptResultDTO", components(
            "taskId", "java.lang.String", "suggestions", listOf(TIMELINE_DTO + "TimelineImagePromptResultDTO$Suggestion")));
        contracts.put(TIMELINE_DTO + "TimelineImagePromptResultDTO$Suggestion", components(
            "prompt", "java.lang.String", "negativePrompt", "java.lang.String", "styleTags", listOf("java.lang.String"),
            "reason", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineFancyTextSuggestionCommandDTO", components(
            "taskId", "java.lang.String", "projectId", "java.lang.String", "draftRevision", "java.lang.String",
            "sourceStartOffset", "int", "sourceEndOffset", "int", "sourceText", "java.lang.String",
            "contextBefore", "java.lang.String", "contextAfter", "java.lang.String",
            "allowedTemplates", listOf(TIMELINE_ENUM + "FancyTextTemplateCode")));
        contracts.put(TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO", components(
            "taskId", "java.lang.String", "suggestions", listOf(TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO$Suggestion")));
        contracts.put(TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO$Suggestion", components(
            "sourceText", "java.lang.String", "sourceStartOffset", "int", "sourceEndOffset", "int",
            "startMs", "long", "durationMs", "long", "templateCode", TIMELINE_ENUM + "FancyTextTemplateCode",
            "xRatio", BigDecimal.class.getName(), "yRatio", BigDecimal.class.getName(),
            "primaryColor", "java.lang.String", "accentColor", "java.lang.String", "reason", "java.lang.String"));
        contracts.put(TIMELINE_DTO + "TimelineSubtitleAlignmentCommandDTO", components(
            "taskId", "java.lang.String", "projectId", "java.lang.String", "draftRevision", "java.lang.String",
            "primaryAudioAssetId", "java.lang.String", "scriptTextSnapshot", "java.lang.String", "language", "java.lang.String",
            "trustedCues", listOf(TIMELINE_DTO + "TimelineSubtitleAlignmentCommandDTO$TrustedCue")));
        contracts.put(TIMELINE_DTO + "TimelineSubtitleAlignmentCommandDTO$TrustedCue", components(
            "text", "java.lang.String", "startMs", "long", "endMs", "long"));
        contracts.put(TIMELINE_DTO + "TimelineSubtitleAlignmentResultDTO", components(
            "taskId", "java.lang.String", "sourceType", "java.lang.String",
            "subtitles", listOf(TIMELINE_DTO + "TimelineSubtitleAlignmentResultDTO$AlignedSubtitle")));
        contracts.put(TIMELINE_DTO + "TimelineSubtitleAlignmentResultDTO$AlignedSubtitle", components(
            "sourceStartOffset", "int", "sourceEndOffset", "int", "displayText", "java.lang.String",
            "startMs", "long", "endMs", "long"));

        contracts.put(CREATION_DTO + "CreationAssetUploadDTO", components(
            "originalName", "java.lang.String", "contentType", "java.lang.String", "usageIntent", "java.lang.String",
            "idempotencyKey", "java.lang.String", "requestDigest", "java.lang.String", "contentLength", "long"));
        contracts.put(CREATION_DTO + "CreationAssetQueryDTO", components(
            "assetType", "java.lang.String", "usageIntent", "java.lang.String", "status", "java.lang.String", "keyword", "java.lang.String"));
        contracts.put(CREATION_DTO + "CreationAssetDTO", components(
            "assetId", "java.lang.String", "originalName", "java.lang.String", "mimeType", "java.lang.String",
            "sha256", "java.lang.String", "assetType", CREATION_ENUM + "CreationAssetType",
            "usageOrigin", CREATION_ENUM + "CreationAssetUsageOrigin", "status", CREATION_ENUM + "CreationAssetStatus",
            "sizeBytes", "long", "durationMs", "java.lang.Long", "width", "java.lang.Integer", "height", "java.lang.Integer",
            "hasVideoStream", "boolean", "hasAudioStream", "boolean", "createdAt", "java.time.Instant"));
        contracts.put(CREATION_DTO + "CreationAssetResolveDTO", components(
            "assetId", "java.lang.String", "mimeType", "java.lang.String", "sha256", "java.lang.String",
            "assetType", CREATION_ENUM + "CreationAssetType", "usageType", TIMELINE_ENUM + "TimelineAssetUsageType",
            "sizeBytes", "long", "durationMs", "java.lang.Long", "width", "java.lang.Integer", "height", "java.lang.Integer",
            "hasVideoStream", "boolean", "hasAudioStream", "boolean",
            "usageOrigin", CREATION_ENUM + "CreationAssetUsageOrigin"));
        contracts.put(CREATION_DTO + "DigitalHumanCreationSourceDTO", components(
            "sourceId", "java.lang.String", "baseVideoAssetId", "java.lang.String", "primaryAudioAssetId", "java.lang.String",
            "scriptTextSnapshot", "java.lang.String", "durationMs", "long", "width", "int", "height", "int",
            "frameRate", "int", "trustedCues", listOf(TIMELINE_DTO + "TimelineSubtitleAlignmentCommandDTO$TrustedCue")));
        contracts.put(CREATION_DTO + "RegisterPendingRenderOutputDTO", components(
            "taskId", "java.lang.String", "inputVersionId", "java.lang.String", "outputConfigDigest", "java.lang.String",
            "idempotencyKey", "java.lang.String"));
        contracts.put(CREATION_DTO + "PendingRenderOutputDTO", components(
            "assetId", "java.lang.String", "taskId", "java.lang.String", "inputVersionId", "java.lang.String",
            "outputConfigDigest", "java.lang.String", "status", CREATION_ENUM + "CreationAssetStatus", "createdAt", "java.time.Instant"));
        contracts.put(CREATION_DTO + "RenderOutputReadyDTO", components(
            "assetId", "java.lang.String", "taskId", "java.lang.String", "mimeType", "java.lang.String",
            "sha256", "java.lang.String", "sizeBytes", "long", "durationMs", "long", "width", "int",
            "height", "int", "frameRate", "int", "hasVideoStream", "boolean", "hasAudioStream", "boolean"));
        contracts.put(CREATION_DTO + "RenderOutputFailureDTO", components(
            "assetId", "java.lang.String", "taskId", "java.lang.String", "failureCode", "java.lang.String",
            "safeSummary", "java.lang.String"));
        contracts.put("org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO", components(
            "requestId", "java.lang.String", "originalName", "java.lang.String", "contentType", "java.lang.String",
            "fileSize", "long"));

        for (Map.Entry<String, List<Component>> contract : contracts.entrySet()) {
            assertRecordContract(contract.getKey(), contract.getValue());
        }
    }

    @Test
    void enumsFreezeAllWireLiterals() throws Exception {
        Map<String, List<String>> contracts = new LinkedHashMap<>();
        contracts.put(TIMELINE_ENUM + "TimelineElementType", List.of(
            "main_video", "image_overlay", "pip_video", "subtitle", "fancy_text", "audio", "visual_effect"));
        contracts.put(TIMELINE_ENUM + "TimelineTrackType", List.of(
            "fancy_text", "subtitle", "visual_effect", "image_overlay", "pip_video", "main_video",
            "primary_audio", "background_music", "sound_effect"));
        contracts.put(TIMELINE_ENUM + "TimelineTrackArea", List.of("top", "center", "bottom"));
        contracts.put(TIMELINE_ENUM + "TimelineFitMode", List.of("contain", "cover"));
        contracts.put(TIMELINE_ENUM + "TimelineVisualEffectCode", List.of(
            "fade_in", "fade_out", "gentle_zoom_in", "gentle_zoom_out", "light_blur"));
        contracts.put(TIMELINE_ENUM + "TimelineOutputQuality", List.of("standard", "high"));
        contracts.put(TIMELINE_ENUM + "TimelineAssetUsageType", List.of(
            "base_video", "primary_audio", "image", "pip_video", "background_music", "sound_effect"));
        contracts.put(TIMELINE_ENUM + "TimelineDocumentType", List.of("draft", "version"));
        contracts.put(TIMELINE_ENUM + "TimelineVersionReason", List.of(
            "manual_save", "restored", "render_input", "conflict_copy"));
        contracts.put(TIMELINE_ENUM + "FancyTextTemplateCode", List.of(
            "keyword_pop", "gold_impact", "neon_breathe", "handwriting_reveal", "bubble_bounce", "title_wipe"));
        contracts.put(CREATION_ENUM + "CreationAssetType", List.of("video", "image", "audio"));
        contracts.put(CREATION_ENUM + "CreationAssetStatus", List.of("pending", "ready", "failed"));
        contracts.put(CREATION_ENUM + "CreationAssetUsageOrigin", List.of(
            "upload", "digital_human_output", "timeline_render_output"));

        for (Map.Entry<String, List<String>> contract : contracts.entrySet()) {
            assertEnumWireValues(contract.getKey(), contract.getValue());
        }
    }

    @Test
    void timelineElementSealedPermitsAndJacksonDiscriminatorAreWhitelisted() throws Exception {
        Class<?> element = load(TIMELINE_DTO + "TimelineElementDTO");
        assertTrue(element.isSealed());
        assertEquals(Set.of(
                TIMELINE_DTO + "TimelineMainVideoElementDTO",
                TIMELINE_DTO + "TimelineImageElementDTO",
                TIMELINE_DTO + "TimelinePipVideoElementDTO",
                TIMELINE_DTO + "TimelineSubtitleElementDTO",
                TIMELINE_DTO + "TimelineFancyTextElementDTO",
                TIMELINE_DTO + "TimelineAudioElementDTO",
                TIMELINE_DTO + "TimelineVisualEffectElementDTO"),
            Arrays.stream(element.getPermittedSubclasses()).map(Class::getName).collect(Collectors.toSet()));

        Map<String, String> accessors = Arrays.stream(element.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()) && Modifier.isAbstract(method.getModifiers()))
            .collect(Collectors.toMap(method -> method.getName(), method -> method.getGenericReturnType().getTypeName()));
        assertEquals(Map.of(
            "elementId", "java.lang.String", "elementType", TIMELINE_ENUM + "TimelineElementType",
            "startMs", "long", "endMs", "long", "zIndex", "int", "enabled", "boolean",
            "locked", "boolean", "label", "java.lang.String"), accessors);

        JsonTypeInfo typeInfo = element.getAnnotation(JsonTypeInfo.class);
        assertNotNull(typeInfo);
        assertEquals(JsonTypeInfo.Id.NAME, typeInfo.use());
        assertEquals(JsonTypeInfo.As.EXISTING_PROPERTY, typeInfo.include());
        assertEquals("elementType", typeInfo.property());
        assertTrue(typeInfo.visible());

        JsonSubTypes subTypes = element.getAnnotation(JsonSubTypes.class);
        assertNotNull(subTypes);
        Map<String, String> mappings = Arrays.stream(subTypes.value())
            .collect(Collectors.toMap(JsonSubTypes.Type::name, type -> type.value().getName()));
        assertEquals(Map.of(
            "main_video", TIMELINE_DTO + "TimelineMainVideoElementDTO",
            "image_overlay", TIMELINE_DTO + "TimelineImageElementDTO",
            "pip_video", TIMELINE_DTO + "TimelinePipVideoElementDTO",
            "subtitle", TIMELINE_DTO + "TimelineSubtitleElementDTO",
            "fancy_text", TIMELINE_DTO + "TimelineFancyTextElementDTO",
            "audio", TIMELINE_DTO + "TimelineAudioElementDTO",
            "visual_effect", TIMELINE_DTO + "TimelineVisualEffectElementDTO"), mappings);
    }

    @Test
    void dedicatedTimelineReaderRejectsUnknownFieldsAndUnknownDiscriminatorsAndRoundTripsFixture() throws Exception {
        Class<?> documentType = load(TIMELINE_DTO + "TimelineDocumentDTO");
        ObjectReader reader = MAPPER.readerFor(documentType)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JsonNode fixtureRoot = MAPPER.readTree(Files.readString(findRepositoryFile(
            "docs/contracts/creation-timeline/timeline-draft.example.json")));
        JsonNode fixture = fixtureRoot.required("timeline");

        Object document = reader.readValue(fixture);
        assertEquals(fixture, MAPPER.readTree(MAPPER.writeValueAsBytes(document)));

        JsonNode unknownField = fixture.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) unknownField).put("storageKey", "forbidden");
        assertThrows(Exception.class, () -> reader.readValue(unknownField));

        JsonNode unknownDiscriminator = fixture.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) unknownDiscriminator
            .path("tracks").get(0).path("elements").get(0)).put("elementType", "runtime_class");
        assertThrows(Exception.class, () -> reader.readValue(unknownDiscriminator));
        assertFalse(MAPPER.isEnabled(tools.jackson.databind.MapperFeature.USE_BASE_TYPE_AS_DEFAULT_IMPL));
    }

    @Test
    void resultSuggestionsAndSafetyMessagesEnforceFrozenBoundaries() throws Exception {
        assertSuggestionLimit(TIMELINE_DTO + "TimelineImagePromptResultDTO",
            TIMELINE_DTO + "TimelineImagePromptResultDTO$Suggestion");
        assertSuggestionLimit(TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO",
            TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO$Suggestion");

        Class<?> progressType = load(TIMELINE_DTO + "TimelineProgressDTO");
        Class<?> stageType = load(TASK_ENUM + "AiTaskStage");
        Constructor<?> constructor = progressType.getDeclaredConstructor(stageType, int.class, String.class);
        Object stage = stageType.getEnumConstants()[0];
        constructor.newInstance(stage, 0, "安".repeat(200));
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> constructor.newInstance(stage, 0, "安".repeat(201)));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());

        JsonNode schema = MAPPER.readTree(Files.readString(findRepositoryFile(
            "docs/contracts/creation-timeline/timeline-1.schema.json")));
        assertEquals(20, schema.at("/x-ai-video-limits/maxAiSuggestions").intValue());
        assertEquals(256, schema.at("/x-ai-video-limits/maxNormalizationChanges").intValue());
    }

    @Test
    void timelineErrorCodesFreezeStableIdentifiersAndNumbers() throws Exception {
        Class<?> errorCodes = load("org.dromara.aivideo.timeline.constant.TimelineErrorCodes");
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put("CREATION_PROJECT_NOT_FOUND", 46601);
        expected.put("CREATION_SOURCE_INVALID", 46602);
        expected.put("TIMELINE_REVISION_CONFLICT", 46603);
        expected.put("TIMELINE_SCHEMA_UNSUPPORTED", 46604);
        expected.put("TIMELINE_DOCUMENT_INVALID", 46605);
        expected.put("TIMELINE_ASSET_INVALID", 46606);
        expected.put("TIMELINE_TEXT_INTEGRITY_FAILED", 46607);
        expected.put("TIMELINE_VERSION_NOT_FOUND", 46608);
        expected.put("TIMELINE_IDEMPOTENCY_CONFLICT", 46609);
        expected.put("TIMELINE_RENDER_UNAVAILABLE", 46610);
        expected.put("TIMELINE_FONT_UNAVAILABLE", 46611);
        expected.put("CREATION_PROJECT_STATE_CONFLICT", 46612);
        for (Map.Entry<String, Integer> item : expected.entrySet()) {
            assertEquals(item.getValue(), errorCodes.getField(item.getKey()).get(null), item.getKey());
        }
        assertEquals(expected.keySet(), Arrays.stream(errorCodes.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
            .map(field -> field.getName()).collect(Collectors.toSet()));
    }

    @Test
    void outputConfigFreezesResolutionAndFrameRate() {
        assertDoesNotThrow(() -> new TimelineOutputConfigDTO("match_canvas", 30, TimelineOutputQuality.STANDARD));
        assertThrows(IllegalArgumentException.class,
            () -> new TimelineOutputConfigDTO("1080p", 30, TimelineOutputQuality.STANDARD));
        assertThrows(IllegalArgumentException.class,
            () -> new TimelineOutputConfigDTO(null, 30, TimelineOutputQuality.STANDARD));
        assertThrows(IllegalArgumentException.class,
            () -> new TimelineOutputConfigDTO("match_canvas", 60, TimelineOutputQuality.STANDARD));
    }

    @Test
    void subtitleAlignmentSourceTypeIsWhitelisted() {
        for (String sourceType : List.of("trusted_cue", "whisper")) {
            assertDoesNotThrow(() -> new TimelineSubtitleAlignmentResultDTO("task-1", sourceType, List.of()));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new TimelineSubtitleAlignmentResultDTO("task-1", "manual", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new TimelineSubtitleAlignmentResultDTO("task-1", null, List.of()));
    }

    private static void assertSuggestionLimit(String resultName, String suggestionName) throws Exception {
        Class<?> resultType = load(resultName);
        Class<?> suggestionType = load(suggestionName);
        Constructor<?> suggestionConstructor = suggestionType.getDeclaredConstructors()[0];
        Object suggestion = suggestionConstructor.newInstance(defaultArguments(suggestionConstructor.getParameterTypes()));
        List<Object> twenty = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            twenty.add(suggestion);
        }
        Constructor<?> resultConstructor = resultType.getDeclaredConstructor(String.class, List.class);
        resultConstructor.newInstance("1", twenty);
        List<Object> twentyOne = new ArrayList<>(twenty);
        twentyOne.add(suggestion);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
            () -> resultConstructor.newInstance("1", twentyOne));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    private static Object[] defaultArguments(Class<?>[] parameterTypes) {
        Object[] values = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> type = parameterTypes[index];
            if (type == int.class) {
                values[index] = 0;
            } else if (type == long.class) {
                values[index] = 0L;
            } else if (type == boolean.class) {
                values[index] = false;
            } else if (type == BigDecimal.class) {
                values[index] = BigDecimal.ZERO;
            } else if (List.class.isAssignableFrom(type)) {
                values[index] = List.of();
            } else if (type.isEnum()) {
                values[index] = type.getEnumConstants()[0];
            } else {
                values[index] = "value";
            }
        }
        return values;
    }

    private static void assertEnumWireValues(String className, List<String> expected) throws Exception {
        Class<?> enumType = load(className);
        assertTrue(enumType.isEnum(), className);
        List<String> actual = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            actual.add(MAPPER.valueToTree(constant).textValue());
        }
        assertEquals(expected, actual, className);
        for (String value : expected) {
            assertNotNull(MAPPER.readValue('"' + value + '"', enumType));
        }
        assertThrows(Exception.class, () -> MAPPER.readValue("\"unknown_contract_value\"", enumType));
    }

    private static void assertRecordContract(String className, List<Component> expected) throws Exception {
        Class<?> type = load(className);
        assertTrue(type.isRecord(), className + " must be a record");
        RecordComponent[] actual = type.getRecordComponents();
        assertEquals(expected.size(), actual.length, className + " component count");
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).name(), actual[index].getName(), className + " component " + index);
            assertEquals(expected.get(index).type(), actual[index].getGenericType().getTypeName(),
                className + "." + actual[index].getName());
        }
        long instanceFields = Arrays.stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).count();
        assertEquals(expected.size(), instanceFields, className + " must not add instance fields");
    }

    private static List<Component> commonElementComponents(String... extra) {
        List<Component> result = new ArrayList<>(components(
            "elementId", "java.lang.String", "elementType", TIMELINE_ENUM + "TimelineElementType",
            "startMs", "long", "endMs", "long", "zIndex", "int", "enabled", "boolean",
            "locked", "boolean", "label", "java.lang.String"));
        result.addAll(components(extra));
        return result;
    }

    private static List<Component> components(String... nameAndType) {
        if (nameAndType.length % 2 != 0) {
            throw new IllegalArgumentException("name/type pairs required");
        }
        List<Component> result = new ArrayList<>();
        for (int index = 0; index < nameAndType.length; index += 2) {
            result.add(new Component(nameAndType[index], nameAndType[index + 1]));
        }
        return result;
    }

    private static String listOf(String type) {
        return "java.util.List<" + type + ">";
    }

    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    private static Path findRepositoryFile(String relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository file not found: " + relativePath);
    }

    private record Component(String name, String type) {
    }
}
