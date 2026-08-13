package org.dromara.aivideo.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.regex.Pattern;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WorkflowContractFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SCHEMA_HASH = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final List<String> FIXTURE_FILES = List.of(
        "workflow-form-1.schema.json",
        "workflow-form-1.example.json",
        "user-wire-forbidden-fields.json"
    );

    @Test
    void workflowFixturesFreezeSingleRunningHubExecutionWireContract() throws Exception {
        Path directory = contractDirectory();
        assertThat(directory).isDirectory();

        Map<String, JsonNode> fixtures = new LinkedHashMap<>();
        for (String fileName : FIXTURE_FILES) {
            Path file = directory.resolve(fileName);
            assertThat(file).as(fileName).isRegularFile();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(readStrict(json, fileName)).as(fileName).isNotNull();
            assertNumericTokensArePlain(json, fileName);
            fixtures.put(fileName, readStrict(json, fileName));
        }

        JsonNode schema = fixtures.get("workflow-form-1.schema.json");
        assertThat(schema.required("$id").textValue()).endsWith("/workflow-form-1.schema.json");
        assertThat(schema.required("additionalProperties").booleanValue()).isFalse();
        assertThat(schema.at("/properties/inputs/additionalProperties").booleanValue()).isFalse();

        Map<String, String> expectedValueTypes = Map.ofEntries(
            Map.entry("text", "string"),
            Map.entry("textarea", "string"),
            Map.entry("select", "string"),
            Map.entry("integer", "integer"),
            Map.entry("decimal", "decimal"),
            Map.entry("boolean", "boolean"),
            Map.entry("multi_select", "string_array"),
            Map.entry("image", "asset_array"),
            Map.entry("audio", "asset_array"),
            Map.entry("video", "asset_array"),
            Map.entry("file", "asset_array")
        );
        assertThat(schema.required("x-ai-video-control-value-types").size()).isEqualTo(11);
        expectedValueTypes.forEach((controlType, valueType) ->
            assertThat(schema.at("/x-ai-video-control-value-types/" + controlType).textValue())
                .as(controlType).isEqualTo(valueType));
        assertThat(schema.path("x-ai-video-form-fields").isArray()).isTrue();
        assertThat(schema.required("x-ai-video-form-fields")).hasSize(11);

        assertThat(schema.at("/properties/inputs/properties").has("unknownControl")).isFalse();
        assertThat(schema.at("/$defs/assetArray/items/additionalProperties").booleanValue()).isFalse();
        assertThat(schema.at("/$defs/assetArray/items/properties").propertyNames()).containsExactly("assetId");

        JsonNode example = fixtures.get("workflow-form-1.example.json");
        assertThat(example.required("schemaVersion").textValue()).isEqualTo("workflow-form-1");
        assertThat(example.required("schemaHash").textValue()).matches(SCHEMA_HASH);
        assertThat(example.required("schemaHash").textValue())
            .isEqualTo(schemaHash(hashSource(schema)));
        assertStrictPayloadRejected(schema, example);
        assertThat(example.required("inputs").required("integerValue").isInt()).isTrue();
        assertThat(example.required("inputs").required("decimalValue").isFloatingPointNumber()).isTrue();
        assertThat(example.required("inputs").required("booleanValue").isBoolean()).isTrue();
        assertThat(example.required("inputs").required("multiSelectValue").isArray()).isTrue();
        for (String assetField : List.of("imageValue", "audioValue", "videoValue", "fileValue")) {
            JsonNode assets = example.required("inputs").required(assetField);
            assertThat(assets.isArray()).as(assetField).isTrue();
            assertThat(assets).allSatisfy(asset -> {
                assertThat(asset.isObject()).isTrue();
                assertThat(asset.propertyNames()).containsExactly("assetId");
                assertThat(asset.required("assetId").isTextual()).isTrue();
            });
        }

        JsonNode forbidden = fixtures.get("user-wire-forbidden-fields.json");
        Set<String> forbiddenFields = Set.copyOf(forbidden.required("forbiddenFields").valueStream()
            .map(JsonNode::textValue).toList());
        assertThat(forbiddenFields).contains(
            "self_hosted_comfyui", "runninghub_workflow", "runninghub_ai_app", "providerKind",
            "executionMode", "executionPlanId", "templateVersionId", "workflowId", "webAppId",
            "nodeId", "runningHubTaskId");
        assertThat(forbidden.at("/userOrderRequest/allowedFields").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("templateId", "schemaHash", "inputs");
        assertThat(forbidden.at("/taskContract/systemTaskTypes").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("workflow_template_generate", "workflow_template_test");
        assertThat(forbidden.at("/taskContract/userVisibleTaskTypes").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("workflow_template_generate");
        assertThat(forbidden.at("/taskContract/resourceTypes").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("workflow_order", "workflow_template");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/pending").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("waiting_for_dispatch");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/queued").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("waiting_for_dispatch");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/running").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("preparing_inputs", "submitting_to_provider", "confirming_provider_acceptance",
                "provider_processing", "processing_results");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/success").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("completed");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/failed").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("failed");
        assertThat(forbidden.at("/taskContract/statusStageMatrix/cancelled").valueStream().map(JsonNode::textValue).toList())
            .containsExactly("cancelled");
        assertThat(forbidden.at("/taskContract/asyncFailureCodes").valueStream().map(JsonNode::textValue).toList())
            .contains("WORKFLOW_SUBMISSION_UNKNOWN", "WORKFLOW_EXECUTION_FAILED", "WORKFLOW_OUTPUT_INVALID",
                "WORKFLOW_CONFIG_CHANGED");
    }

    @Test
    void fixedHashCanonicalizerUsesUtf16OrderingEscapingAndSafeIntegers() throws Exception {
        ObjectNode vector = MAPPER.createObjectNode();
        vector.put("\uE000", "private");
        vector.put("😀", "\"\n");
        vector.put("safe", 9_007_199_254_740_991L);
        vector.put("A", 0);

        assertThat(canonicalizeFixedHashSource(vector)).isEqualTo("{\"A\":0,\"safe\":9007199254740991,\"😀\":\"\\\"\\n\",\"\":\"private\"}");
        assertThatThrownBy(() -> canonicalizeFixedHashSource(MAPPER.readTree("{\"number\":1.25}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("workflow-form-1 hash source only permits integral numbers");
    }

    @Test
    void publicContractDoesNotDocumentForbiddenExecutionSelectionFieldsForUsers() throws Exception {
        Path projectRoot = contractDirectory().getParent().getParent().getParent();
        String contract = Files.readString(projectRoot.resolve("docs/API_CONTRACT.md"), StandardCharsets.UTF_8);
        int sectionStart = contract.indexOf("### 动态表单值");
        int sectionEnd = contract.indexOf("### 通用私有素材上传会话", sectionStart);

        assertThat(sectionStart).isGreaterThanOrEqualTo(0);
        assertThat(sectionEnd).isGreaterThan(sectionStart);
        String formSection = contract.substring(sectionStart, sectionEnd);
        assertThat(formSection).doesNotContain("templateVersionId", "executionPlanId", "providerKind", "executionMode");
        assertThat(formSection).contains("schemaVersion='workflow-form-1'", "schemaHash", "inputs");
    }

    private static Path contractDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("docs/contracts/discovery-runninghub");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            current = current.getParent();
        }
        return Path.of("docs/contracts/discovery-runninghub").toAbsolutePath();
    }

    private static void assertNumericTokensArePlain(String json, String fileName) throws IOException {
        try (JsonParser parser = MAPPER.tokenStreamFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
                    assertThat(parser.getText()).as(fileName).doesNotContainIgnoringCase("e");
                }
            }
        }
    }

    private static JsonNode readStrict(String json, String fileName) throws IOException {
        Deque<Set<String>> objectFields = new ArrayDeque<>();
        try (JsonParser parser = MAPPER.tokenStreamFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    objectFields.push(new HashSet<>());
                } else if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
                    assertThat(objectFields.peek().add(parser.getText()))
                        .as("%s duplicate field %s", fileName, parser.getText()).isTrue();
                } else if (parser.currentToken() == JsonToken.END_OBJECT) {
                    objectFields.pop();
                }
            }
        }
        return MAPPER.readTree(json);
    }

    private static void assertStrictPayloadRejected(JsonNode schema, JsonNode example) throws IOException {
        JsonNode topLevelUnknown = example.deepCopy();
        topLevelUnknown.asObject().put("unexpected", true);
        assertThat(strictValidationErrors(schema, topLevelUnknown))
            .containsExactly("unknown property at /unexpected");

        JsonNode inputUnknown = example.deepCopy();
        inputUnknown.withObject("/inputs").put("unknownControl", "forbidden");
        assertThat(strictValidationErrors(schema, inputUnknown))
            .containsExactly("unknown property at /inputs/unknownControl");

        JsonNode assetUnknown = example.deepCopy();
        assetUnknown.withObject("/inputs/imageValue/0").put("url", "forbidden");
        assertThat(strictValidationErrors(schema, assetUnknown))
            .containsExactly("unknown property at /inputs/imageValue/0/url");

        JsonNode missingRequired = example.deepCopy();
        missingRequired.withObject("/inputs").remove("textValue");
        assertThat(strictValidationErrors(schema, missingRequired))
            .containsExactly("required property missing at /inputs/textValue");

        JsonNode wrongVersion = example.deepCopy();
        wrongVersion.asObject().put("schemaVersion", "workflow-form-0");
        assertThat(strictValidationErrors(schema, wrongVersion))
            .containsExactly("const mismatch at /schemaVersion");

        JsonNode badHash = example.deepCopy();
        badHash.asObject().put("schemaHash", "sha256:BAD");
        assertThat(strictValidationErrors(schema, badHash))
            .containsExactly("pattern mismatch at /schemaHash");

        JsonNode wrongBoolean = example.deepCopy();
        wrongBoolean.withObject("/inputs").put("booleanValue", "true");
        assertThat(strictValidationErrors(schema, wrongBoolean))
            .containsExactly("type mismatch at /inputs/booleanValue: expected boolean");

        JsonNode wrongInteger = example.deepCopy();
        wrongInteger.withObject("/inputs").put("integerValue", 1.5);
        assertThat(strictValidationErrors(schema, wrongInteger))
            .containsExactly("type mismatch at /inputs/integerValue: expected integer");

        JsonNode duplicateMultiSelect = example.deepCopy();
        duplicateMultiSelect.withArray("/inputs/multiSelectValue").add("warm");
        assertThat(strictValidationErrors(schema, duplicateMultiSelect))
            .containsExactly("duplicate item at /inputs/multiSelectValue/2");

        JsonNode multipleFiles = example.deepCopy();
        multipleFiles.withArray("/inputs/fileValue").addObject().put("assetId", "10005");
        assertThat(strictValidationErrors(schema, multipleFiles))
            .containsExactly("maxItems exceeded at /inputs/fileValue");

        JsonNode wrongAssetId = example.deepCopy();
        wrongAssetId.withObject("/inputs/imageValue/0").put("assetId", 7);
        assertThat(strictValidationErrors(schema, wrongAssetId))
            .containsExactly("type mismatch at /inputs/imageValue/0/assetId: expected string");

        assertThat(strictValidationErrors(schema, """
            {"schemaVersion":"workflow-form-1","schemaHash":"%s","inputs":{
            "textValue":"x","textareaValue":"x","selectValue":"x","integerValue":1e3,
            "decimalValue":1.25,"booleanValue":true,"multiSelectValue":["x"],"imageValue":[{"assetId":"1"}],
            "audioValue":[{"assetId":"2"}],"videoValue":[{"assetId":"3"}],"fileValue":[{"assetId":"4"}]}}
            """.formatted(example.required("schemaHash").textValue())))
            .contains("numeric exponent is forbidden at /inputs/integerValue");
        assertThat(strictValidationErrors(schema, """
            {"schemaVersion":"workflow-form-1","schemaHash":"%s","inputs":{
            "textValue":"x","textareaValue":"x","selectValue":"x","integerValue":9007199254740992,
            "decimalValue":1.25,"booleanValue":true,"multiSelectValue":["x"],"imageValue":[{"assetId":"1"}],
            "audioValue":[{"assetId":"2"}],"videoValue":[{"assetId":"3"}],"fileValue":[{"assetId":"4"}]}}
            """.formatted(example.required("schemaHash").textValue())))
            .contains("unsafe integer at /inputs/integerValue");
    }

    private static List<String> strictValidationErrors(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        validatePayload(schema, schema, value, "", errors);
        return errors;
    }

    private static List<String> strictValidationErrors(JsonNode schema, String rawJson) throws IOException {
        List<String> errors = strictValidationErrors(schema, readStrict(rawJson, "payload"));
        String exponent = java.util.regex.Pattern.compile("\\\"(integerValue|decimalValue)\\\"\\s*:\\s*-?[0-9]+(?:\\.[0-9]+)?[eE][+-]?[0-9]+")
            .matcher(rawJson).results().map(result -> result.group(1)).findFirst().orElse(null);
        if (exponent != null) {
            errors.add("numeric exponent is forbidden at /inputs/" + exponent);
        }
        return errors;
    }

    private static void validatePayload(JsonNode rootSchema, JsonNode schema, JsonNode value,
                                        String path, List<String> errors) {
        if (schema.has("$ref")) {
            validatePayload(rootSchema, rootSchema.at(schema.required("$ref").textValue().substring(1)),
                value, path, errors);
            return;
        }
        String type = schema.has("type") ? schema.required("type").textValue() : null;
        if (type != null && !matchesType(type, value)) {
            errors.add("type mismatch at " + pointer(path) + ": expected " + type);
            return;
        }
        if (schema.has("const") && !schema.required("const").equals(value)) {
            errors.add("const mismatch at " + pointer(path));
        }
        if (schema.has("pattern") && value.isTextual()
            && !value.textValue().matches(schema.required("pattern").textValue())) {
            errors.add("pattern mismatch at " + pointer(path));
        }
        if (value.isObject()) {
            JsonNode properties = schema.path("properties");
            schema.path("required").valueStream().map(JsonNode::textValue).forEach(required -> {
                if (!value.has(required)) {
                    errors.add("required property missing at " + path + "/" + required);
                }
            });
            if (schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").booleanValue()) {
                value.properties().forEach(entry -> {
                    if (!properties.has(entry.getKey())) {
                        errors.add("unknown property at " + path + "/" + entry.getKey());
                    }
                });
            }
            value.properties().forEach(entry -> {
                JsonNode childSchema = properties.get(entry.getKey());
                if (childSchema != null) {
                    validatePayload(rootSchema, childSchema, entry.getValue(),
                        path + "/" + entry.getKey(), errors);
                }
            });
        } else if (value.isArray() && schema.has("items")) {
            if (schema.has("maxItems") && value.size() > schema.required("maxItems").intValue()) {
                errors.add("maxItems exceeded at " + pointer(path));
            }
            Set<String> items = new HashSet<>();
            for (int index = 0; index < value.size(); index++) {
                if (schema.has("uniqueItems") && schema.required("uniqueItems").booleanValue()
                    && !items.add(canonicalizeFixedHashSource(value.get(index)))) {
                    errors.add("duplicate item at " + pointer(path) + "/" + index);
                }
                validatePayload(rootSchema, schema.required("items"), value.get(index),
                    path + "/" + index, errors);
            }
        } else if (value.isNumber()) {
            BigDecimal number = value.decimalValue();
            if (schema.has("minimum") && number.compareTo(schema.required("minimum").decimalValue()) < 0) {
                errors.add("minimum violated at " + pointer(path));
            }
            if (schema.has("maximum") && number.compareTo(schema.required("maximum").decimalValue()) > 0) {
                errors.add("maximum violated at " + pointer(path));
            }
            if ("integer".equals(type) && number.abs().compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0) {
                errors.add("unsafe integer at " + pointer(path));
            }
        }
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            default -> throw new IllegalArgumentException("Unsupported workflow-form-1 schema type: " + type);
        };
    }

    private static String pointer(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private static JsonNode hashSource(JsonNode schema) {
        ObjectNode source = MAPPER.createObjectNode().put("schemaVersion", "workflow-form-1");
        source.set("fields", schema.required("x-ai-video-form-fields"));
        return source;
    }

    private static String schemaHash(JsonNode source) {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonicalizeFixedHashSource(source).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static String canonicalizeFixedHashSource(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream().sorted((left, right) -> left.getKey().compareTo(right.getKey()))
                .map(entry -> quote(entry.getKey()) + ":" + canonicalizeFixedHashSource(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            return node.valueStream().map(WorkflowContractFixtureTest::canonicalizeFixedHashSource)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        if (node.isTextual()) {
            return quote(node.textValue());
        }
        if (node.isBoolean() || node.isNull()) {
            return node.toString();
        }
        if (node.isNumber()) {
            return canonicalSafeInteger(node);
        }
        throw new IllegalArgumentException("Unsupported JCS token");
    }

    private static String canonicalSafeInteger(JsonNode node) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException("workflow-form-1 hash source only permits integral numbers");
        }
        BigDecimal number = node.decimalValue();
        if (number.abs().compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0) {
            throw new IllegalArgumentException("workflow-form-1 hash source only permits safe integers");
        }
        return number.toBigIntegerExact().toString();
    }

    private static String quote(String value) {
        StringBuilder canonical = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isSurrogate(character)) {
                if (!Character.isHighSurrogate(character) || index + 1 >= value.length()
                    || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("JCS string contains an unpaired surrogate");
                }
                canonical.append(character).append(value.charAt(++index));
                continue;
            }
            switch (character) {
                case '"' -> canonical.append("\\\"");
                case '\\' -> canonical.append("\\\\");
                case '\b' -> canonical.append("\\b");
                case '\f' -> canonical.append("\\f");
                case '\n' -> canonical.append("\\n");
                case '\r' -> canonical.append("\\r");
                case '\t' -> canonical.append("\\t");
                default -> {
                    if (character <= 0x1f) {
                        canonical.append(String.format("\\u%04x", (int) character));
                    } else {
                        canonical.append(character);
                    }
                }
            }
        }
        return canonical.append('"').toString();
    }
}
