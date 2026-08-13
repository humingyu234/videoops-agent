package org.dromara.aivideo.timeline;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TimelineContractFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]*");
    private static final List<String> CONTRACT_FILES = List.of(
        "timeline-1.schema.json",
        "project.example.json",
        "timeline-draft.example.json",
        "timeline-task.example.json",
        "creation-output.example.json",
        "timeline-errors.example.json",
        "subtitle-normalization.example.json",
        "font-registry.json"
    );

    @Test
    void fixedContractFilesExistAndContainParseableCanonicalJson() throws Exception {
        Path directory = contractDirectory();
        assertThat(directory).isDirectory();

        for (String fileName : CONTRACT_FILES) {
            Path file = directory.resolve(fileName);
            assertThat(file).as(fileName).isRegularFile();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(MAPPER.readTree(json)).as(fileName).isNotNull();
            assertNumericTokensAreCanonical(json, fileName);
        }

        JsonNode project = read("project.example.json");
        assertThat(project.propertyNames()).containsExactlyInAnyOrder(
            "projectId", "projectTitle", "sourceType", "sourceId", "baseVideoAssetId",
            "primaryAudioAssetId", "status", "canvas", "currentDraftRevision", "schemaVersion",
            "createdAt", "updatedAt");
        assertDecimalStringIds(project, "projectId", "sourceId", "baseVideoAssetId",
            "primaryAudioAssetId", "currentDraftRevision");

        JsonNode draft = read("timeline-draft.example.json");
        assertThat(draft.propertyNames()).containsExactlyInAnyOrder(
            "projectId", "timelineDraftId", "revision", "schemaVersion", "contentHash", "timeline", "savedAt");
        assertDecimalStringIds(draft, "projectId", "timelineDraftId", "revision");

        JsonNode task = read("timeline-task.example.json");
        assertThat(task.propertyNames()).containsExactlyInAnyOrder(
            "taskId", "taskType", "resourceType", "resourceId", "inputVersionId", "status", "stage",
            "progress", "canCancel", "canRetry", "createdAt");
        assertDecimalStringIds(task, "taskId", "resourceId", "inputVersionId");

        JsonNode output = read("creation-output.example.json");
        assertThat(output.propertyNames()).containsExactlyInAnyOrder(
            "projectId", "outputAssetId", "taskId", "createdAt");
        assertDecimalStringIds(output, "projectId", "outputAssetId", "taskId");
        assertThat(output.required("createdAt").isTextual()).isTrue();
        assertThat(output.required("createdAt").textValue()).isNotBlank();
    }

    @Test
    void timelineRootHasExactlyThreeFieldsAndRejectsLimits() throws Exception {
        JsonNode timeline = read("timeline-draft.example.json").required("timeline");
        Schema schema = schema();

        assertThat(timeline.propertyNames())
            .containsExactlyInAnyOrder("schemaVersion", "canvas", "tracks");
        assertThat(validate(schema, timeline)).isEmpty();
        assertThat(MAPPER.writeValueAsBytes(timeline).length).isLessThanOrEqualTo(1_048_576);
        assertThat(maxDepth(timeline)).isLessThanOrEqualTo(16);
        assertThat(totalElements(timeline)).isLessThanOrEqualTo(2_000);
        assertThat(distinctAssetIds(timeline)).hasSizeLessThanOrEqualTo(256);
        assertThat(assetReferenceCount(timeline)).isLessThanOrEqualTo(2_000);

        assertThat(validate(schema, timeline.deepCopy().asObject().put("durationMs", 1))).isNotEmpty();
        assertThat(validate(schema, timeline.deepCopy().asObject().put("output", "high"))).isNotEmpty();
        assertThat(validate(schema, timeline.deepCopy().asObject().put("tenantId", "1"))).isNotEmpty();
        assertThat(validate(schema, timeline.deepCopy().asObject().put("workspaceId", "1"))).isNotEmpty();

        JsonNode tooLong = timeline.deepCopy();
        tooLong.withObject("/canvas").put("durationMs", 120_001);
        assertThat(validate(schema, tooLong)).isNotEmpty();
    }

    @Test
    void validTimelineCoversFrozenTracksElementsAndDefaults() throws Exception {
        JsonNode timeline = read("timeline-draft.example.json").required("timeline");
        List<JsonNode> tracks = new ArrayList<>(timeline.required("tracks").values());

        assertThat(trackTypes(tracks, "top"))
            .containsExactly("fancy_text", "subtitle", "visual_effect", "image_overlay", "pip_video");
        assertThat(trackTypes(tracks, "center")).containsExactly("main_video");
        assertThat(trackTypes(tracks, "bottom"))
            .containsExactly("primary_audio", "background_music", "sound_effect");

        Set<String> elementIds = new HashSet<>();
        Set<String> coveredTypes = new HashSet<>();
        for (JsonNode track : tracks) {
            for (JsonNode element : track.required("elements")) {
                assertThat(elementIds.add(element.required("elementId").textValue())).isTrue();
                coveredTypes.add(element.required("elementType").textValue());
                assertThat(element.required("startMs").longValue()).isGreaterThanOrEqualTo(0L);
                assertThat(element.required("endMs").longValue())
                    .isGreaterThan(element.required("startMs").longValue())
                    .isLessThanOrEqualTo(timeline.at("/canvas/durationMs").longValue());
            }
        }
        assertThat(coveredTypes).containsExactlyInAnyOrder(
            "main_video", "image_overlay", "pip_video", "subtitle", "fancy_text", "audio", "visual_effect");

        JsonNode image = findElement(timeline, "image_overlay");
        assertThat(image.required("fitMode").textValue()).isEqualTo("contain");
        assertThat(image.at("/crop/xRatio").decimalValue()).isEqualByComparingTo("0");
        assertThat(image.at("/fade/fadeInMs").intValue()).isZero();
        assertThat(image.at("/fade/fadeOutMs").intValue()).isZero();

        JsonNode pip = findElement(timeline, "pip_video");
        assertThat(pip.required("sourceStartMs").intValue()).isZero();
        assertThat(pip.required("loopWhenOverflow").booleanValue()).isTrue();
        assertThat(pip.required("audioEnabled").booleanValue()).isFalse();

        JsonNode backgroundMusic = findAudio(timeline, "background_music");
        assertThat(backgroundMusic.required("volumeRatio").decimalValue()).isEqualByComparingTo("0.30");
        assertThat(backgroundMusic.required("loopWhenOverflow").booleanValue()).isTrue();
        assertThat(backgroundMusic.required("duckingEnabled").booleanValue()).isTrue();
        assertThat(backgroundMusic.required("targetGainRatio").decimalValue()).isEqualByComparingTo("0.35");
        assertThat(backgroundMusic.required("attackMs").intValue()).isEqualTo(120);
        assertThat(backgroundMusic.required("releaseMs").intValue()).isEqualTo(400);

        JsonNode soundEffect = findAudio(timeline, "sound_effect");
        assertThat(soundEffect.required("loopWhenOverflow").booleanValue()).isFalse();
        assertThat(soundEffect.required("duckingEnabled").booleanValue()).isFalse();
        assertThat(soundEffect.has("targetGainRatio")).isFalse();
    }

    @Test
    void schemaRejectsForbiddenReferencesInvalidRangesAndUnknownWhitelists() throws Exception {
        JsonNode timeline = read("timeline-draft.example.json").required("timeline");
        Schema schema = schema();

        JsonNode badStorage = timeline.deepCopy();
        findElement(badStorage, "image_overlay").asObject().put("storageKey", "private/a.png");
        assertThat(validate(schema, badStorage)).isNotEmpty();

        JsonNode badUrl = timeline.deepCopy();
        findElement(badUrl, "pip_video").asObject().put("url", "https://example.invalid/a.mp4");
        assertThat(validate(schema, badUrl)).isNotEmpty();

        JsonNode badCredential = timeline.deepCopy();
        findElement(badCredential, "main_video").asObject().put("credential", "secret");
        assertThat(validate(schema, badCredential)).isNotEmpty();

        JsonNode badTime = timeline.deepCopy();
        findElement(badTime, "subtitle").asObject().put("startMs", -1);
        assertThat(validate(schema, badTime)).isNotEmpty();

        JsonNode badCrop = timeline.deepCopy();
        findElement(badCrop, "image_overlay").withObject("/crop").put("xRatio", 0.9).put("widthRatio", 0.2);
        assertThat(cropWithinCanvas(findElement(badCrop, "image_overlay").required("crop"))).isFalse();

        JsonNode badTemplate = timeline.deepCopy();
        findElement(badTemplate, "fancy_text").asObject().put("templateCode", "unknown");
        assertThat(validate(schema, badTemplate)).isNotEmpty();

        JsonNode badColor = timeline.deepCopy();
        findElement(badColor, "subtitle").asObject().put("color", "#ffffff");
        assertThat(validate(schema, badColor)).isNotEmpty();
    }

    @Test
    void schemaAndJavaNumericLimitsStayAligned() throws Exception {
        JsonNode schemaLimits = read("timeline-1.schema.json").required("x-ai-video-limits");
        Class<?> limitsType = Class.forName(
            "org.dromara.aivideo.timeline.constant.TimelineContractLimits");
        Field field = limitsType.getField("NUMERIC_LIMITS");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> javaLimits = (Map<String, BigDecimal>) field.get(null);

        Map<String, BigDecimal> jsonLimits = new LinkedHashMap<>();
        schemaLimits.properties().forEach(entry ->
            jsonLimits.put(entry.getKey(), entry.getValue().decimalValue()));
        assertThat(jsonLimits.keySet()).containsExactlyInAnyOrderElementsOf(javaLimits.keySet());
        javaLimits.forEach((key, value) ->
            assertThat(jsonLimits.get(key)).as(key).isEqualByComparingTo(value));

        assertThat(javaLimits.get("canonicalJsonMaxBytes")).isEqualByComparingTo("1048576");
        assertThat(javaLimits.get("maxTotalElements")).isEqualByComparingTo("2000");
        assertThat(javaLimits.get("maxDurationMs")).isEqualByComparingTo("120000");
        assertThat(javaLimits.get("safeMarginRatio")).isEqualByComparingTo("0.05");
    }

    @Test
    void whitelistsOutputAndPackagedContractsStayFrozen() throws Exception {
        JsonNode schemaDocument = read("timeline-1.schema.json");
        JsonNode whitelists = schemaDocument.required("x-ai-video-whitelists");
        assertThat(textValues(whitelists.required("fancyTextTemplates"))).containsExactly(
            "keyword_pop", "gold_impact", "neon_breathe", "handwriting_reveal", "bubble_bounce", "title_wipe");
        assertThat(textValues(whitelists.required("subtitleAlignments"))).containsExactly("left", "center", "right");
        assertThat(textValues(whitelists.required("safeAreaAnchors"))).containsExactly("upper", "center", "lower");
        assertThat(textValues(whitelists.required("animationIntensities"))).containsExactly("subtle", "normal", "strong");
        assertThat(textValues(whitelists.required("aiImageStyles")))
            .containsExactly("photorealistic", "cinematic", "illustration", "minimal");

        JsonNode output = MAPPER.readTree("""
            {"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"high"}
            """);
        Schema outputSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(schemaDocument.at("/$defs/outputConfig"));
        assertThat(validate(outputSchema, output)).isEmpty();
        assertThat(textValues(schemaDocument.at("/$defs/outputConfig/required")))
            .containsExactly("resolutionPreset", "frameRate", "qualityPreset");
        assertThat(schemaDocument.at("/$defs/outputConfig/properties").has("qualityPreset")).isTrue();
        assertThat(schemaDocument.at("/$defs/outputConfig/properties").has("quality")).isFalse();
        assertThat(validate(outputSchema,
            MAPPER.readTree("{\"resolutionPreset\":\"match_canvas\",\"frameRate\":30,\"quality\":\"high\"}")))
            .isNotEmpty();
        assertThat(validate(outputSchema, output.deepCopy().asObject().put("codec", "h265"))).isNotEmpty();
        assertThat(schemaDocument.at("/x-ai-video-output-encoding/container").textValue()).isEqualTo("mp4");
        assertThat(schemaDocument.at("/x-ai-video-output-encoding/videoCodec").textValue()).isEqualTo("h264");
        assertThat(schemaDocument.at("/x-ai-video-output-encoding/pixelFormat").textValue()).isEqualTo("yuv420p");
        assertThat(schemaDocument.at("/x-ai-video-output-encoding/audioCodec").textValue()).isEqualTo("aac");

        for (String fileName : CONTRACT_FILES) {
            URL packaged = TimelineContractFixtureTest.class.getClassLoader()
                .getResource("contracts/creation-timeline/" + fileName);
            assertThat(packaged).as(fileName).isNotNull();
            assertThat(Files.readAllBytes(Path.of(packaged.toURI())))
                .as(fileName)
                .isEqualTo(Files.readAllBytes(contractDirectory().resolve(fileName)));
        }
    }

    @Test
    void fontRegistryAndSubtitleNormalizationAreFrozen() throws Exception {
        JsonNode registry = read("font-registry.json");
        assertThat(registry.required("registryVersion").textValue()).isEqualTo("timeline-fonts-1");
        assertThat(registry.at("/license/summarySha256").textValue())
            .isEqualTo("6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2");
        assertThat(registry.required("fonts").values())
            .extracting(font -> font.required("fontCode").textValue())
            .containsExactly("noto_sans_cjk_sc_regular", "noto_serif_cjk_sc_regular");
        assertThat(registry.at("/fonts/0/sha256").textValue())
            .isEqualTo("2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b");
        assertThat(registry.at("/fonts/1/sha256").textValue())
            .isEqualTo("2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca");
        assertThat(registry.at("/fonts/0/familyName").textValue()).isEqualTo("Noto Sans CJK SC");
        assertThat(registry.at("/fonts/0/postScriptName").textValue()).isEqualTo("NotoSansCJKsc-Regular");
        assertThat(registry.at("/fonts/0/fileName").textValue()).isEqualTo("NotoSansCJKsc-Regular.otf");
        assertThat(registry.at("/fonts/0/weight").intValue()).isEqualTo(400);
        assertThat(registry.at("/fonts/0/licenseSummarySha256").textValue())
            .isEqualTo(registry.at("/license/summarySha256").textValue());
        assertThat(registry.at("/fonts/1/familyName").textValue()).isEqualTo("Noto Serif CJK SC");
        assertThat(registry.at("/fonts/1/postScriptName").textValue()).isEqualTo("NotoSerifCJKsc-Regular");
        assertThat(registry.at("/fonts/1/fileName").textValue()).isEqualTo("NotoSerifCJKsc-Regular.otf");
        assertThat(registry.at("/fonts/1/weight").intValue()).isEqualTo(400);
        assertThat(registry.at("/fonts/1/licenseSummarySha256").textValue())
            .isEqualTo(registry.at("/license/summarySha256").textValue());

        JsonNode normalization = read("subtitle-normalization.example.json");
        assertThat(normalization.required("unicodeNormalization").textValue()).isEqualTo("NFC");
        assertThat(normalization.required("lengthUnit").textValue()).isEqualTo("unicode_code_point");
        assertThat(normalization.required("removePunctuation").booleanValue()).isTrue();
        assertThat(normalization.required("removeWhitespace").booleanValue()).isTrue();
        assertThat(normalization.required("examples")).hasSizeGreaterThanOrEqualTo(4);
        assertThat(normalization.required("segments")).allSatisfy(segment -> {
            assertThat(segment.required("segmentId").textValue()).matches("subtitle_[0-9]{4}");
            assertThat(segment.required("endMs").longValue())
                .isGreaterThan(segment.required("startMs").longValue());
        });
    }

    @Test
    void stableErrorCatalogIsCompleteAndContiguous() throws Exception {
        JsonNode errors = read("timeline-errors.example.json").required("errors");
        assertThat(errors.values()).extracting(error -> error.required("code").intValue())
            .containsExactly(46601, 46602, 46603, 46604, 46605, 46606,
                46607, 46608, 46609, 46610, 46611, 46612);
        assertThat(errors.values()).extracting(error -> error.required("stableCode").textValue())
            .containsExactly(
                "CREATION_PROJECT_NOT_FOUND",
                "CREATION_SOURCE_INVALID",
                "TIMELINE_REVISION_CONFLICT",
                "TIMELINE_SCHEMA_UNSUPPORTED",
                "TIMELINE_DOCUMENT_INVALID",
                "TIMELINE_ASSET_INVALID",
                "TIMELINE_TEXT_INTEGRITY_FAILED",
                "TIMELINE_VERSION_NOT_FOUND",
                "TIMELINE_IDEMPOTENCY_CONFLICT",
                "TIMELINE_RENDER_UNAVAILABLE",
                "TIMELINE_FONT_UNAVAILABLE",
                "CREATION_PROJECT_STATE_CONFLICT"
            );
    }

    private static Schema schema() throws IOException {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(read("timeline-1.schema.json"));
    }

    private static List<com.networknt.schema.Error> validate(Schema schema, JsonNode document) {
        return schema.validate(document);
    }

    private static JsonNode read(String fileName) throws IOException {
        return MAPPER.readTree(contractDirectory().resolve(fileName).toFile());
    }

    private static Path contractDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("docs/contracts/creation-timeline");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            current = current.getParent();
        }
        return Path.of("docs/contracts/creation-timeline").toAbsolutePath();
    }

    private static void assertNumericTokensAreCanonical(String json, String fileName) throws IOException {
        try (JsonParser parser = MAPPER.tokenStreamFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                    String token = parser.getText();
                    assertThat(token).as(fileName).doesNotContainIgnoringCase("e");
                    int dot = token.indexOf('.');
                    assertThat(dot < 0 ? 0 : token.length() - dot - 1).as(fileName).isLessThanOrEqualTo(4);
                    assertThat(token).as(fileName).doesNotEndWith("0");
                }
            }
        }
    }

    private static void assertDecimalStringIds(JsonNode document, String... fields) {
        for (String field : fields) {
            assertThat(document.required(field).isTextual()).as(field).isTrue();
            assertThat(document.required(field).textValue()).as(field).matches(DECIMAL_ID);
        }
    }

    private static List<String> trackTypes(List<JsonNode> tracks, String area) {
        return tracks.stream()
            .filter(track -> area.equals(track.required("area").textValue()))
            .sorted((left, right) -> Integer.compare(
                left.required("order").intValue(), right.required("order").intValue()))
            .map(track -> track.required("trackType").textValue())
            .toList();
    }

    private static JsonNode findElement(JsonNode timeline, String elementType) {
        for (JsonNode track : timeline.required("tracks")) {
            for (JsonNode element : track.required("elements")) {
                if (elementType.equals(element.required("elementType").textValue())) {
                    return element;
                }
            }
        }
        throw new IllegalArgumentException("Missing element type: " + elementType);
    }

    private static JsonNode findAudio(JsonNode timeline, String usageType) {
        for (JsonNode track : timeline.required("tracks")) {
            for (JsonNode element : track.required("elements")) {
                if ("audio".equals(element.required("elementType").textValue())
                    && usageType.equals(element.required("usageType").textValue())) {
                    return element;
                }
            }
        }
        throw new IllegalArgumentException("Missing audio usage: " + usageType);
    }

    private static int totalElements(JsonNode timeline) {
        int total = 0;
        for (JsonNode track : timeline.required("tracks")) {
            total += track.required("elements").size();
        }
        return total;
    }

    private static Set<String> distinctAssetIds(JsonNode timeline) {
        Set<String> ids = new HashSet<>();
        collectAssetIds(timeline, ids);
        return ids;
    }

    private static int assetReferenceCount(JsonNode timeline) {
        List<String> ids = new ArrayList<>();
        collectAssetIds(timeline, ids);
        return ids.size();
    }

    private static void collectAssetIds(JsonNode node, java.util.Collection<String> ids) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if ("assetId".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    ids.add(entry.getValue().textValue());
                }
                collectAssetIds(entry.getValue(), ids);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectAssetIds(child, ids));
        }
    }

    private static int maxDepth(JsonNode node) {
        if (!node.isContainer() || node.isEmpty()) {
            return 1;
        }
        int maximum = 0;
        for (JsonNode child : node) {
            maximum = Math.max(maximum, maxDepth(child));
        }
        return maximum + 1;
    }

    private static boolean cropWithinCanvas(JsonNode crop) {
        BigDecimal x = crop.required("xRatio").decimalValue();
        BigDecimal y = crop.required("yRatio").decimalValue();
        BigDecimal width = crop.required("widthRatio").decimalValue();
        BigDecimal height = crop.required("heightRatio").decimalValue();
        return x.signum() >= 0 && y.signum() >= 0
            && width.signum() > 0 && height.signum() > 0
            && x.add(width).compareTo(BigDecimal.ONE) <= 0
            && y.add(height).compareTo(BigDecimal.ONE) <= 0;
    }

    private static List<String> textValues(JsonNode array) {
        return array.valueStream().map(JsonNode::textValue).toList();
    }

}
