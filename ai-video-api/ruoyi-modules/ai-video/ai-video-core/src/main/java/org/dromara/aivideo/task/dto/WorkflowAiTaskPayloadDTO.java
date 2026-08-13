package org.dromara.aivideo.task.dto;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Stable workflow request facts; execution-provider configuration is deliberately excluded. */
public record WorkflowAiTaskPayloadDTO(String orderId, String templateId, String schemaHash,
                                       Map<String, JsonNode> inputs) implements AiTaskRequestPayloadDTO {
    private static final Pattern INPUT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private static final String FORBIDDEN_NAMES = "self_hosted_comfyui|runninghub_workflow|runninghub_ai_app|"
        + "providerKind|executionMode|executionPlanId|templateVersionId|workflowId|webAppId|nodeId|"
        + "runningHubTaskId|provider|account|workflow|webapp|mapping|apiKey|accessPassword|executionConfig|"
        + "credentialReference|remoteUrl";
    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
        "(?i)\\\"(?:" + FORBIDDEN_NAMES + ")\\\"\\s*:");
    private static final Pattern FORBIDDEN_NAME = Pattern.compile("(?i)(?:" + FORBIDDEN_NAMES + ")");
    private static final Pattern EXTERNAL_URL = Pattern.compile("(?i)https?://");

    public WorkflowAiTaskPayloadDTO {
        if ((orderId != null && !positiveId(orderId)) || !positiveId(templateId)
            || schemaHash == null || !schemaHash.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid workflow task facts");
        }
        inputs = validateFacts(inputs);
    }

    static Map<String, JsonNode> validateFacts(Map<String, JsonNode> facts) {
        if (facts == null || facts.size() > 128) {
            throw new IllegalArgumentException("workflow task facts exceed limits");
        }
        LinkedHashMap<String, JsonNode> copy = new LinkedHashMap<>();
        int bytes = 2;
        for (Map.Entry<String, JsonNode> entry : facts.entrySet()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key == null || !INPUT_KEY.matcher(key).matches() || forbiddenName(key) || value == null
                || maxDepth(value, 1) > 8 || countNodes(value) > 2_048) {
                throw new IllegalArgumentException("invalid workflow task fact");
            }
            String json = value.toString();
            bytes += key.getBytes(StandardCharsets.UTF_8).length + json.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 65_536 || FORBIDDEN_KEY.matcher(json).find() || EXTERNAL_URL.matcher(json).find()) {
                throw new IllegalArgumentException("workflow task facts contain forbidden data");
            }
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }

    static boolean positiveId(String value) {
        return value != null && value.matches("[1-9][0-9]{0,18}");
    }

    private static boolean forbiddenName(String value) {
        return FORBIDDEN_NAME.matcher(value).matches();
    }

    private static int maxDepth(JsonNode node, int depth) {
        int max = depth;
        for (JsonNode child : node) {
            max = Math.max(max, maxDepth(child, depth + 1));
        }
        return max;
    }

    private static int countNodes(JsonNode node) {
        int count = 1;
        for (JsonNode child : node) {
            count += countNodes(child);
        }
        return count;
    }
}
