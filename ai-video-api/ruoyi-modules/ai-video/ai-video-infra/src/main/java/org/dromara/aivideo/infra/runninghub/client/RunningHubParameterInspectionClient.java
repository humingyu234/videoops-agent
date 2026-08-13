package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.RunningHubParameterInspectionDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.enums.WorkflowExecutionMode;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IRunningHubParameterInspectionService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** RunningHub AI App 与 Workflow 可配置参数的只读检查客户端。 */
@Component
public final class RunningHubParameterInspectionClient implements IRunningHubParameterInspectionService {

    private static final URI AI_APP_ENDPOINT = URI.create(
        "https://www.runninghub.cn/api/webapp/apiCallDemo");
    private static final URI WORKFLOW_ENDPOINT = URI.create(
        "https://www.runninghub.cn/api/openapi/getJsonApiFormat");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CANDIDATES = 2000;
    private static final int MAX_OPTIONS = 200;
    private static final int MAX_DEFAULT_VALUE_CHARS = 8192;
    private static final Pattern REMOTE_ID = Pattern.compile("[1-9][0-9]{0,19}");
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern FIELD_TYPE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,63}");

    private final IRunningHubAccountService accountService;
    private final IWorkflowCredentialReadService credentialReadService;
    private final JsonMapper jsonMapper;
    private final RunningHubHttpTransport transport;

    @Autowired
    public RunningHubParameterInspectionClient(IRunningHubAccountService accountService,
                                               IWorkflowCredentialReadService credentialReadService) {
        this(accountService, credentialReadService, JsonMapper.builder().build(),
            new JdkRunningHubHttpTransport());
    }

    RunningHubParameterInspectionClient(IRunningHubAccountService accountService,
                                        IWorkflowCredentialReadService credentialReadService,
                                        JsonMapper jsonMapper,
                                        RunningHubHttpTransport transport) {
        this.accountService = Objects.requireNonNull(accountService);
        this.credentialReadService = Objects.requireNonNull(credentialReadService);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public RunningHubParameterInspectionDTOs.Result inspect(RunningHubParameterInspectionDTOs.Request request) {
        InspectionTarget target = validate(request);
        RunningHubAccountDTOs.InspectionCredential credential =
            accountService.queryInspectionCredential(request.accountId());
        char[] apiKey = null;
        try {
            apiKey = credentialReadService.decryptForUse(
                WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, credential.encryptedApiKey());
            if (apiKey.length == 0) {
                throw invalid("RunningHub API Key 不可用");
            }
            return target.mode() == WorkflowExecutionMode.RUNNINGHUB_AI_APP
                ? inspectAiApp(target.remoteId(), apiKey)
                : inspectWorkflow(target.remoteId(), apiKey);
        } finally {
            if (apiKey != null) {
                Arrays.fill(apiKey, '\0');
            }
        }
    }

    private RunningHubParameterInspectionDTOs.Result inspectAiApp(String webAppId, char[] apiKey) {
        String apiKeyText = new String(apiKey);
        String query = "apiKey=" + encodeQuery(apiKeyText) + "&webappId=" + encodeQuery(webAppId);
        HttpRequest request = baseRequest(URI.create(AI_APP_ENDPOINT + "?" + query), apiKeyText)
            .GET()
            .build();
        JsonNode data = responseData(send(request));
        JsonNode nodes = data.path("nodeInfoList");
        if (!nodes.isArray()) {
            throw inspectionFailure("未返回可读取的应用参数");
        }
        List<RunningHubParameterInspectionDTOs.Candidate> candidates = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (candidates.size() >= MAX_CANDIDATES) {
                throw inspectionFailure("应用参数数量超过上限");
            }
            RunningHubParameterInspectionDTOs.Candidate candidate = aiAppCandidate(node);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return new RunningHubParameterInspectionDTOs.Result(
            safeOptionalText(data.path("webappName"), 256), candidates);
    }

    private RunningHubParameterInspectionDTOs.Result inspectWorkflow(String workflowId, char[] apiKey) {
        String apiKeyText = new String(apiKey);
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("apiKey", apiKeyText);
        body.put("workflowId", workflowId);
        HttpRequest request = baseRequest(WORKFLOW_ENDPOINT, apiKeyText)
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(
                jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
        JsonNode data = responseData(send(request));
        JsonNode promptValue = data.path("prompt");
        if (!promptValue.isTextual()) {
            throw inspectionFailure("未返回可解析的工作流 JSON");
        }
        if (promptValue.textValue().length() > MAX_RESPONSE_BYTES) {
            throw inspectionFailure("工作流 JSON 超过 2 MB 限制");
        }
        JsonNode prompt;
        try {
            prompt = jsonMapper.readTree(promptValue.textValue());
        } catch (RuntimeException exception) {
            throw inspectionFailure("未返回可解析的工作流 JSON");
        }
        if (prompt == null || !prompt.isObject()) {
            throw inspectionFailure("未返回可解析的工作流 JSON");
        }
        List<RunningHubParameterInspectionDTOs.Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : prompt.properties()) {
            JsonNode node = entry.getValue();
            if (!NODE_ID.matcher(entry.getKey()).matches() || !node.isObject()) {
                continue;
            }
            JsonNode inputs = node.path("inputs");
            if (!inputs.isObject()) {
                continue;
            }
            String nodeName = workflowNodeName(node, entry.getKey());
            for (Map.Entry<String, JsonNode> input : inputs.properties()) {
                if (candidates.size() >= MAX_CANDIDATES) {
                    throw inspectionFailure();
                }
                JsonNode value = input.getValue();
                String fieldType = workflowFieldType(node.path("class_type"), input.getKey(), value);
                if (!FIELD_NAME.matcher(input.getKey()).matches() || fieldType == null) {
                    continue;
                }
                candidates.add(new RunningHubParameterInspectionDTOs.Candidate(
                    entry.getKey(), nodeName, input.getKey(), fieldType, null,
                    scalarValue(value), List.of()));
            }
        }
        return new RunningHubParameterInspectionDTOs.Result(null, candidates);
    }

    private RunningHubParameterInspectionDTOs.Candidate aiAppCandidate(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String nodeId = safeRequiredText(node.path("nodeId"), NODE_ID);
        String fieldName = safeRequiredText(node.path("fieldName"), FIELD_NAME);
        if (nodeId == null || fieldName == null) {
            return null;
        }
        String rawFieldType = safeRequiredText(node.path("fieldType"), FIELD_TYPE);
        if (rawFieldType == null) {
            return null;
        }
        return new RunningHubParameterInspectionDTOs.Candidate(
            nodeId,
            fallback(safeOptionalText(node.path("nodeName"), 256), nodeId),
            fieldName,
            rawFieldType,
            safeOptionalText(node.path("description"), 1000),
            scalarValue(node.path("fieldValue")),
            "LIST".equalsIgnoreCase(rawFieldType) ? listOptions(node.path("fieldData")) : List.of());
    }

    private List<RunningHubParameterInspectionDTOs.Option> listOptions(JsonNode fieldData) {
        if (!fieldData.isTextual() || fieldData.textValue().length() > 64 * 1024) {
            return List.of();
        }
        try {
            JsonNode optionsNode = jsonMapper.readTree(fieldData.textValue());
            if (optionsNode == null || !optionsNode.isArray()) {
                return List.of();
            }
            List<RunningHubParameterInspectionDTOs.Option> options = new ArrayList<>();
            Set<String> values = new HashSet<>();
            for (JsonNode option : optionsNode) {
                if (options.size() >= MAX_OPTIONS) {
                    break;
                }
                if (!option.isObject() || option.has("default")) {
                    continue;
                }
                String value = scalarValue(option.path("index"));
                if (value == null || value.isBlank() || value.length() > 1024 || !values.add(value)) {
                    continue;
                }
                String label = safeOptionalText(option.path("name"), 256);
                if (label == null) {
                    label = safeOptionalText(option.path("description"), 256);
                }
                options.add(new RunningHubParameterInspectionDTOs.Option(value, fallback(label, value)));
            }
            return options;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private JsonNode send(HttpRequest request) {
        RunningHubHttpTransport.Response response = null;
        try {
            response = transport.send(request, MAX_RESPONSE_BYTES);
            if (response.statusCode() != 200) {
                throw inspectionFailure("接口返回 HTTP " + response.statusCode());
            }
            if (response.body() == null) {
                throw inspectionFailure("接口未返回内容");
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw inspectionFailure("接口响应超过 2 MB 限制");
            }
            return jsonMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw inspectionFailure("请求被中断");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw inspectionFailure("接口响应不是有效 JSON");
        } finally {
            if (response != null && response.body() != null) {
                Arrays.fill(response.body(), (byte) 0);
            }
        }
    }

    private JsonNode responseData(JsonNode envelope) {
        if (envelope == null || !envelope.isObject()) {
            throw inspectionFailure("接口响应格式无效");
        }
        int code = envelope.path("code").asInt(Integer.MIN_VALUE);
        if (code != 0) {
            throw inspectionFailure(providerFailureReason(code));
        }
        JsonNode data = envelope.path("data");
        if (!data.isObject()) {
            throw inspectionFailure("接口未返回有效数据");
        }
        return data;
    }

    private HttpRequest.Builder baseRequest(URI uri, String apiKey) {
        if (!"https".equals(uri.getScheme()) || !"www.runninghub.cn".equals(uri.getHost())
            || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw inspectionFailure();
        }
        return HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json");
    }

    private InspectionTarget validate(RunningHubParameterInspectionDTOs.Request request) {
        if (request == null || request.accountId() == null || request.accountId().isBlank()) {
            throw invalid("RunningHub 参数检查请求不能为空");
        }
        WorkflowExecutionMode mode = executionMode(request.executionMode());
        String remoteId;
        if (mode == WorkflowExecutionMode.RUNNINGHUB_AI_APP) {
            if (hasText(request.workflowId())) {
                throw invalid("AI App 模式不能填写 Workflow ID");
            }
            remoteId = request.webAppId();
        } else {
            if (hasText(request.webAppId())) {
                throw invalid("Workflow 模式不能填写 Web App ID");
            }
            remoteId = request.workflowId();
        }
        if (remoteId == null || !REMOTE_ID.matcher(remoteId).matches()) {
            throw invalid("RunningHub 远端编号无效");
        }
        return new InspectionTarget(mode, remoteId);
    }

    private WorkflowExecutionMode executionMode(String value) {
        for (WorkflowExecutionMode mode : WorkflowExecutionMode.values()) {
            if (mode.getValue().equals(value)) {
                return mode;
            }
        }
        throw invalid("RunningHub 执行模式无效");
    }

    private String workflowNodeName(JsonNode node, String nodeId) {
        String title = safeOptionalText(node.path("_meta").path("title"), 256);
        if (title != null) {
            return title;
        }
        return fallback(safeOptionalText(node.path("class_type"), 256), nodeId);
    }

    private String literalFieldType(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return "text";
        }
        if (value.isIntegralNumber()) {
            return "integer";
        }
        if (value.isFloatingPointNumber()) {
            return "decimal";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        return null;
    }

    private String workflowFieldType(JsonNode classTypeNode, String fieldName, JsonNode value) {
        String literalType = literalFieldType(value);
        if (!"text".equals(literalType)) {
            return literalType;
        }
        String classType = safeOptionalText(classTypeNode, 256);
        if (classType == null) {
            return literalType;
        }
        String normalizedClass = classType.toLowerCase(Locale.ROOT);
        String normalizedField = fieldName.toLowerCase(Locale.ROOT);
        if (matchesMediaFileInput(normalizedClass, normalizedField, "image")) {
            return "image";
        }
        if (matchesMediaFileInput(normalizedClass, normalizedField, "audio")) {
            return "audio";
        }
        if (matchesMediaFileInput(normalizedClass, normalizedField, "video")) {
            return "video";
        }
        if ((normalizedClass.contains("load") || normalizedClass.contains("upload")
            || normalizedClass.contains("input"))
            && Set.of("file", "filename", "file_path", "filepath", "path")
                .contains(normalizedField)) {
            return "file";
        }
        return literalType;
    }

    private boolean matchesMediaFileInput(String classType, String fieldName, String mediaType) {
        return classType.contains(mediaType)
            && (fieldName.equals(mediaType) || fieldName.equals(mediaType + "_file")
                || fieldName.equals(mediaType + "_path"));
    }

    private String scalarValue(JsonNode value) {
        if (value == null || value.isNull()
            || (!value.isTextual() && !value.isNumber() && !value.isBoolean())) {
            return null;
        }
        String text = value.isTextual() ? value.textValue() : value.toString();
        return text.length() <= MAX_DEFAULT_VALUE_CHARS ? text : null;
    }

    private String safeRequiredText(JsonNode node, Pattern pattern) {
        String value = safeOptionalText(node, 128);
        return value != null && pattern.matcher(value).matches() ? value : null;
    }

    private String safeOptionalText(JsonNode node, int maxChars) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.textValue().strip();
        if (value.isEmpty() || value.length() > maxChars
            || value.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return value;
    }

    private String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String providerFailureReason(int code) {
        if (code == 810) {
            return "工作流未保存或未手动运行，请在 RunningHub 保存并手动运行一次后重试";
        }
        return "接口返回错误码 " + code;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message, WorkflowErrorCodes.WORKFLOW_CONFIGURATION_INVALID);
    }

    private ServiceException inspectionFailure() {
        return inspectionFailure(null);
    }

    private ServiceException inspectionFailure(String reason) {
        String message = "RunningHub 参数读取失败";
        if (reason != null && !reason.isBlank()) {
            message += "：" + reason;
        }
        return new ServiceException(
            message,
            WorkflowErrorCodes.WORKFLOW_PARAMETER_INSPECTION_FAILED);
    }

    private record InspectionTarget(WorkflowExecutionMode mode, String remoteId) {
    }
}
