package org.dromara.aivideo.workflow.validation;

import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规范化第一阶段工作流表单 Schema。
 */
@Component
public class WorkflowSchemaCanonicalizer {

    public static final String SCHEMA_VERSION = "workflow-form-1";
    private static final int WORKFLOW_INPUT_INVALID = 46505;
    private static final BigDecimal MAX_SAFE_INTEGER = BigDecimal.valueOf(9_007_199_254_740_991L);
    private static final Pattern INPUT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private static final Pattern POSITIVE_INTEGER_STRING = Pattern.compile("(?:0|[1-9][0-9]*)");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "fields");
    private static final Set<String> FIELD_FIELDS = Set.of(
        "inputKey", "semanticKey", "label", "description", "control", "valueType", "required",
        "defaultValue", "placeholder", "options", "constraints");
    private static final Set<String> OPTION_FIELDS = Set.of("value", "label");
    private static final Set<String> CONSTRAINT_FIELDS = Set.of(
        "min", "max", "minLength", "maxLength", "minItems", "maxItems", "assetType",
        "allowedExtensions", "allowedContentTypes", "maxBytesPerAsset");
    private static final Set<String> FILE_CONTROLS = Set.of("image", "audio", "video", "file");
    private static final Map<String, String> CONTROL_VALUE_TYPES = Map.ofEntries(
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

    private final JsonMapper jsonMapper;

    public WorkflowSchemaCanonicalizer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * 校验、规范化并计算工作流表单摘要。
     *
     * @param schemaJson 仅包含 schemaVersion 和 fields 的表单 JSON
     * @return 规范 JSON、摘要和详情页输入摘要
     */
    public CanonicalSchema canonicalize(String schemaJson) {
        JsonNode root = parseStrict(schemaJson);
        requireObject(root, "表单 Schema");
        requireExactFields(root, ROOT_FIELDS, "表单 Schema");
        if (!SCHEMA_VERSION.equals(requiredText(root, "schemaVersion", "schemaVersion"))) {
            throw invalid("只支持 schemaVersion=" + SCHEMA_VERSION);
        }
        JsonNode fields = root.get("fields");
        if (fields == null || !fields.isArray()) {
            throw invalid("fields 必须是数组");
        }

        Set<String> inputKeys = new HashSet<>();
        List<RequiredInput> requiredInputs = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            JsonNode field = fields.get(index);
            String path = "fields[" + index + "]";
            requireObject(field, path);
            requireExactFields(field, FIELD_FIELDS, path);
            String inputKey = requiredText(field, "inputKey", path + ".inputKey");
            if (!INPUT_KEY.matcher(inputKey).matches()) {
                throw invalid(path + ".inputKey 格式无效");
            }
            if (!inputKeys.add(inputKey)) {
                throw invalid("inputKey 重复: " + inputKey);
            }
            String label = requiredText(field, "label", path + ".label");
            String control = requiredText(field, "control", path + ".control");
            String valueType = requiredText(field, "valueType", path + ".valueType");
            Boolean required = requiredBoolean(field, "required", path + ".required");
            String expectedValueType = CONTROL_VALUE_TYPES.get(control);
            if (expectedValueType == null || !expectedValueType.equals(valueType)) {
                throw invalid(path + " 控件和值类型不匹配");
            }
            validateOptionalText(field, "semanticKey", path);
            validateOptionalText(field, "description", path);
            validateOptionalText(field, "placeholder", path);
            Set<String> optionValues = validateOptions(field, control, path);
            String assetType = validateConstraints(field, control, path);
            validateDefaultValue(field, control, optionValues, path);
            requiredInputs.add(new RequiredInput(optionalText(field, "semanticKey"), label, valueType,
                assetType, required));
        }

        String canonicalJson = canonicalizeNode(root);
        return new CanonicalSchema(canonicalJson, sha256(canonicalJson), List.copyOf(requiredInputs));
    }

    private JsonNode parseStrict(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw invalid("表单 Schema 不能为空");
        }
        Deque<Set<String>> objectFields = new ArrayDeque<>();
        try (JsonParser parser = jsonMapper.tokenStreamFactory().createParser(schemaJson)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    objectFields.push(new HashSet<>());
                } else if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
                    if (objectFields.isEmpty() || !objectFields.peek().add(parser.getText())) {
                        throw invalid("JSON 属性重复: " + parser.getText());
                    }
                } else if (parser.currentToken() == JsonToken.END_OBJECT) {
                    objectFields.pop();
                }
            }
            return jsonMapper.readTree(schemaJson);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("表单 Schema 不是合法 JSON");
        }
    }

    private Set<String> validateOptions(JsonNode field, String control, String path) {
        JsonNode options = field.get("options");
        boolean optionControl = "select".equals(control) || "multi_select".equals(control);
        if (!optionControl) {
            if (options != null) {
                throw invalid(path + ".options 仅适用于选择控件");
            }
            return Set.of();
        }
        if (options == null || !options.isArray() || options.isEmpty()) {
            throw invalid(path + ".options 不能为空");
        }
        Set<String> values = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            JsonNode option = options.get(index);
            String optionPath = path + ".options[" + index + "]";
            requireObject(option, optionPath);
            requireExactFields(option, OPTION_FIELDS, optionPath);
            String value = requiredText(option, "value", optionPath + ".value");
            requiredText(option, "label", optionPath + ".label");
            if (!values.add(value)) {
                throw invalid(path + ".options value 重复: " + value);
            }
        }
        return Set.copyOf(values);
    }

    private String validateConstraints(JsonNode field, String control, String path) {
        JsonNode constraints = field.get("constraints");
        boolean fileControl = FILE_CONTROLS.contains(control);
        if (constraints == null) {
            if (fileControl) {
                throw invalid(path + ".constraints.assetType 缺失");
            }
            return null;
        }
        requireObject(constraints, path + ".constraints");
        requireExactFields(constraints, CONSTRAINT_FIELDS, path + ".constraints");
        validatePlainDecimalString(constraints, "min", path);
        validatePlainDecimalString(constraints, "max", path);
        validateNonNegativeInteger(constraints, "minLength", path);
        validateNonNegativeInteger(constraints, "maxLength", path);
        validateNonNegativeInteger(constraints, "minItems", path);
        validateNonNegativeInteger(constraints, "maxItems", path);
        validateUniqueTextArray(constraints, "allowedExtensions", path);
        validateUniqueTextArray(constraints, "allowedContentTypes", path);
        JsonNode maxBytes = constraints.get("maxBytesPerAsset");
        if (maxBytes != null && (!maxBytes.isTextual()
            || !POSITIVE_INTEGER_STRING.matcher(maxBytes.textValue()).matches())) {
            throw invalid(path + ".constraints.maxBytesPerAsset 必须是十进制非负整数字符串");
        }
        String assetType = optionalText(constraints, "assetType");
        if (fileControl && !control.equals(assetType)) {
            throw invalid(path + ".constraints.assetType 必须与文件控件一致");
        }
        if (!fileControl && assetType != null) {
            throw invalid(path + ".constraints.assetType 仅适用于文件控件");
        }
        if (fileControl && (!constraints.has("maxItems") || constraints.get("maxItems").intValue() != 1)) {
            throw invalid(path + ".constraints.maxItems 当前必须为 1");
        }
        return assetType;
    }

    private void validateDefaultValue(JsonNode field, String control, Set<String> optionValues, String path) {
        JsonNode value = field.get("defaultValue");
        if (value == null) {
            return;
        }
        if (FILE_CONTROLS.contains(control)) {
            throw invalid(path + " 文件控件不得提供默认值");
        }
        boolean valid = switch (control) {
            case "text", "textarea", "select" -> value.isTextual();
            case "integer" -> value.isTextual() && value.textValue().matches("-?(?:0|[1-9][0-9]*)");
            case "decimal" -> value.isTextual() && PLAIN_DECIMAL.matcher(value.textValue()).matches();
            case "boolean" -> value.isBoolean();
            case "multi_select" -> isUniqueTextArray(value);
            default -> false;
        };
        if (!valid) {
            throw invalid(path + ".defaultValue 与控件不匹配");
        }
        if ("select".equals(control) && !optionValues.contains(value.textValue())) {
            throw invalid(path + ".defaultValue 必须存在于 options");
        }
        if ("multi_select".equals(control)) {
            for (JsonNode item : value) {
                if (!optionValues.contains(item.textValue())) {
                    throw invalid(path + ".defaultValue 必须存在于 options");
                }
            }
        }
    }

    private void validateOptionalText(JsonNode object, String name, String path) {
        JsonNode value = object.get(name);
        if (value != null && (!value.isTextual() || value.textValue().isBlank())) {
            throw invalid(path + "." + name + " 必须是非空字符串");
        }
    }

    private void validatePlainDecimalString(JsonNode constraints, String name, String path) {
        JsonNode value = constraints.get(name);
        if (value != null && (!value.isTextual() || !PLAIN_DECIMAL.matcher(value.textValue()).matches())) {
            throw invalid(path + ".constraints." + name + " 必须是无指数十进制字符串");
        }
    }

    private void validateNonNegativeInteger(JsonNode constraints, String name, String path) {
        JsonNode value = constraints.get(name);
        if (value == null) {
            return;
        }
        if (!value.isIntegralNumber() || value.decimalValue().signum() < 0
            || value.decimalValue().compareTo(MAX_SAFE_INTEGER) > 0) {
            throw invalid(path + ".constraints." + name + " 必须是非负安全整数");
        }
    }

    private void validateUniqueTextArray(JsonNode constraints, String name, String path) {
        JsonNode value = constraints.get(name);
        if (value != null && !isUniqueTextArray(value)) {
            throw invalid(path + ".constraints." + name + " 必须是非空字符串去重数组");
        }
    }

    private boolean isUniqueTextArray(JsonNode value) {
        if (!value.isArray()) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank() || !seen.add(item.textValue())) {
                return false;
            }
        }
        return true;
    }

    private void requireExactFields(JsonNode object, Set<String> allowed, String path) {
        List<String> unknown = object.properties().stream().map(Map.Entry::getKey)
            .filter(name -> !allowed.contains(name)).toList();
        if (!unknown.isEmpty()) {
            throw invalid(path + " 包含未知属性: " + unknown.getFirst());
        }
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " 必须是对象");
        }
    }

    private String requiredText(JsonNode object, String name, String path) {
        JsonNode value = object.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(path + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private String optionalText(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return value == null ? null : value.textValue();
    }

    private Boolean requiredBoolean(JsonNode object, String name, String path) {
        JsonNode value = object.get(name);
        if (value == null || !value.isBoolean()) {
            throw invalid(path + " 必须是布尔值");
        }
        return value.booleanValue();
    }

    private String canonicalizeNode(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream()
                .sorted((left, right) -> left.getKey().compareTo(right.getKey()))
                .map(entry -> quote(entry.getKey()) + ":" + canonicalizeNode(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            return node.valueStream().map(this::canonicalizeNode)
                .collect(Collectors.joining(",", "[", "]"));
        }
        if (node.isTextual()) {
            return quote(node.textValue());
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.booleanValue());
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isNumber()) {
            if (!node.isIntegralNumber() || node.decimalValue().abs().compareTo(MAX_SAFE_INTEGER) > 0) {
                throw invalid("Schema 数字必须是安全整数");
            }
            return node.decimalValue().toBigIntegerExact().toString();
        }
        throw invalid("Schema 包含不支持的 JSON token");
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isSurrogate(character)) {
                if (!Character.isHighSurrogate(character) || index + 1 >= value.length()
                    || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw invalid("Schema 字符串包含未配对代理字符");
                }
                result.append(character).append(value.charAt(++index));
                continue;
            }
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character <= 0x1f) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, WORKFLOW_INPUT_INVALID);
    }

    public record CanonicalSchema(String canonicalJson, String schemaHash, List<RequiredInput> requiredInputs) {
    }

    public record RequiredInput(String semanticKey, String label, String valueType, String assetType,
                                boolean required) {
    }
}
