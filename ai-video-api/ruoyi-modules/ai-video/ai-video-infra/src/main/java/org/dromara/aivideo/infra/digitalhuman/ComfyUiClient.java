package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoPollDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoProviderStatus;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanVideoSubmitDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanVideoService;
import org.dromara.common.core.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ComfyUI 数字人口播客户端。
 */
public final class ComfyUiClient implements IDigitalHumanVideoService {

    private static final Pattern PROMPT_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern WORKFLOW_ID = Pattern.compile("[A-Fa-f0-9-]{36}");
    private static final Pattern NODE_ID = Pattern.compile("[0-9]{1,20}");
    private static final String UPLOAD_SUBFOLDER = "digital-human";
    static final int MAX_JSON_RESPONSE_BYTES = 1024 * 1024;
    static final int MAX_VIDEO_RESPONSE_BYTES = 128 * 1024 * 1024;

    private final DigitalHumanProviderProperties.ComfyUi properties;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ComfyUiClient(DigitalHumanProviderProperties.ComfyUi properties) {
        this.properties = Objects.requireNonNull(properties);
        this.httpClient = DigitalHumanHttpSupport.client(null, properties.isInsecureSkipTlsVerify());
    }

    @Override
    public String submit(DigitalHumanVideoSubmitDTO request) {
        Objects.requireNonNull(request, "视频提交参数不能为空");
        try {
            ObjectNode prompt = loadPrompt();
            UploadedFile portrait = upload("portrait", request.portraitType(), request.portrait());
            UploadedFile audio = upload("audio", request.audioType(), request.audio());
            setUniqueInput(prompt, "LoadImage", "image", portrait.workflowPath());
            setUniqueInput(prompt, "LoadAudio", "audio", audio.workflowPath());
            setUniqueIntegerInput(prompt, "JWInteger", "value", wavFrameCount(request.audio(), frameRate(prompt)));

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("prompt", prompt);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(endpoint("/prompt"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(payload)));
            DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
            DigitalHumanHttpSupport.LimitedResponse response = send(
                builder.build(), "视频供应商提交失败", MAX_JSON_RESPONSE_BYTES);
            if (response.statusCode() != 200) {
                throw new ServiceException("视频供应商提交失败");
            }
            String promptId = jsonMapper.readTree(response.body()).path("prompt_id").asString("");
            if (!PROMPT_ID.matcher(promptId).matches()) {
                throw new ServiceException("视频供应商提交失败");
            }
            return promptId;
        } catch (RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("视频供应商提交失败");
        }
    }

    private ObjectNode loadPrompt() {
        String workflowFile = properties.getWorkflowFile();
        String workflowId = properties.getWorkflowId();
        if (!DigitalHumanHttpSupport.hasText(workflowFile) || workflowFile.length() > 255
            || workflowFile.contains("/") || workflowFile.contains("\\") || workflowFile.contains("..")
            || !workflowFile.toLowerCase().endsWith(".json")
            || !DigitalHumanHttpSupport.hasText(workflowId) || !WORKFLOW_ID.matcher(workflowId).matches()) {
            throw new ServiceException("ComfyUI 工作流配置无效");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(endpoint("/api/userdata/" + encode("workflows/" + workflowFile)))
            .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET();
        DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
        DigitalHumanHttpSupport.LimitedResponse response = send(
            builder.build(), "读取 ComfyUI 工作流失败", MAX_JSON_RESPONSE_BYTES);
        if (response.statusCode() != 200) {
            throw new ServiceException("读取 ComfyUI 工作流失败");
        }
        try {
            JsonNode workflow = jsonMapper.readTree(response.body());
            if (workflow == null || !workflowId.equals(workflow.path("id").asString())) {
                throw new ServiceException("ComfyUI 工作流配置无效");
            }
            return convertWorkflow(workflow);
        } catch (RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("读取 ComfyUI 工作流失败");
        }
    }

    private UploadedFile upload(String role, String mediaType, byte[] content) {
        String extension = extension(mediaType, role);
        String fileName = "digital-human-" + role + '-' + UUID.randomUUID().toString().replace("-", "") + extension;
        DigitalHumanHttpSupport.MultipartBody body = DigitalHumanHttpSupport.multipart(List.of(
            DigitalHumanHttpSupport.file("image", fileName, mediaType, content),
            DigitalHumanHttpSupport.text("subfolder", UPLOAD_SUBFOLDER),
            DigitalHumanHttpSupport.text("type", "input"),
            DigitalHumanHttpSupport.text("overwrite", "false")));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(endpoint("/upload/image"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", body.contentType())
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()));
        DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
        DigitalHumanHttpSupport.LimitedResponse response = send(
            builder.build(), "上传 ComfyUI 输入失败", MAX_JSON_RESPONSE_BYTES);
        if (response.statusCode() != 200) {
            throw new ServiceException("上传 ComfyUI 输入失败");
        }
        try {
            JsonNode value = jsonMapper.readTree(response.body());
            String name = value.path("name").asString("");
            String subfolder = value.path("subfolder").asString("");
            String type = value.path("type").asString("");
            if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")
                || !UPLOAD_SUBFOLDER.equals(subfolder) || !"input".equals(type)) {
                throw new ServiceException("上传 ComfyUI 输入失败");
            }
            return new UploadedFile(name, subfolder);
        } catch (RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("上传 ComfyUI 输入失败");
        }
    }

    private ObjectNode convertWorkflow(JsonNode workflow) {
        JsonNode nodes = workflow.path("nodes");
        JsonNode links = workflow.path("links");
        if (!nodes.isArray() || !links.isArray()) {
            throw new ServiceException("ComfyUI 工作流配置无效");
        }
        Map<Long, LinkSource> linkSources = new HashMap<>();
        for (JsonNode link : links) {
            if (!link.isArray() || link.size() < 3 || !link.get(0).canConvertToLong()
                || !link.get(1).canConvertToLong() || !link.get(2).canConvertToInt()) {
                throw new ServiceException("ComfyUI 工作流配置无效");
            }
            linkSources.put(link.get(0).longValue(),
                new LinkSource(Long.toString(link.get(1).longValue()), link.get(2).intValue()));
        }

        ObjectNode prompt = jsonMapper.createObjectNode();
        for (JsonNode node : nodes) {
            String nodeId = node.path("id").asText("");
            String classType = node.path("type").asString("");
            if (!NODE_ID.matcher(nodeId).matches() || classType.isBlank() || node.path("mode").asInt(0) != 0
                || !node.path("inputs").isArray()) {
                throw new ServiceException("ComfyUI 工作流配置无效");
            }
            ObjectNode apiNode = jsonMapper.createObjectNode();
            apiNode.put("class_type", classType);
            ObjectNode apiInputs = apiNode.putObject("inputs");
            JsonNode widgetValues = node.get("widgets_values");
            int widgetIndex = 0;
            for (JsonNode input : node.path("inputs")) {
                String inputName = input.path("name").asString("");
                JsonNode widget = input.get("widget");
                JsonNode widgetValue = null;
                if (widget != null && widget.isObject()) {
                    if (widgetValues != null && widgetValues.isArray()) {
                        if (widgetIndex < widgetValues.size()) {
                            widgetValue = widgetValues.get(widgetIndex);
                        }
                        widgetIndex++;
                        if ("seed".equals(inputName) && widgetIndex < widgetValues.size()
                            && isSeedControl(widgetValues.get(widgetIndex))) {
                            widgetIndex++;
                        }
                    } else if (widgetValues != null && widgetValues.isObject()) {
                        widgetValue = widgetValues.get(inputName);
                    }
                }
                JsonNode linkId = input.get("link");
                if (linkId != null && !linkId.isNull()) {
                    LinkSource source = linkId.canConvertToLong() ? linkSources.get(linkId.longValue()) : null;
                    if (source == null) {
                        throw new ServiceException("ComfyUI 工作流配置无效");
                    }
                    ArrayNode reference = jsonMapper.createArrayNode();
                    reference.add(source.nodeId());
                    reference.add(source.outputIndex());
                    apiInputs.set(inputName, reference);
                } else if (!isClientOnlyWidget(inputName) && widgetValue != null && !widgetValue.isNull()) {
                    apiInputs.set(inputName, widgetValue.deepCopy());
                }
            }
            ObjectNode metadata = apiNode.putObject("_meta");
            metadata.put("title", node.path("title").asString(classType));
            prompt.set(nodeId, apiNode);
        }
        return prompt;
    }

    private static void setUniqueInput(ObjectNode prompt, String classType, String inputName, String value) {
        uniqueInputs(prompt, classType).put(inputName, value);
    }

    private static void setUniqueIntegerInput(ObjectNode prompt, String classType, String inputName, int value) {
        uniqueInputs(prompt, classType).put(inputName, value);
    }

    private static ObjectNode uniqueInputs(ObjectNode prompt, String classType) {
        ObjectNode selected = null;
        for (Map.Entry<String, JsonNode> entry : prompt.properties()) {
            JsonNode node = entry.getValue();
            if (classType.equals(node.path("class_type").asString())) {
                if (selected != null || !(node.path("inputs") instanceof ObjectNode inputs)) {
                    throw new ServiceException("ComfyUI 工作流配置无效");
                }
                selected = inputs;
            }
        }
        if (selected == null) {
            throw new ServiceException("ComfyUI 工作流配置无效");
        }
        return selected;
    }

    private static boolean isClientOnlyWidget(String inputName) {
        return "upload".equals(inputName) || "audioUI".equals(inputName);
    }

    private static boolean isSeedControl(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return false;
        }
        return switch (value.asString()) {
            case "fixed", "increment", "decrement", "randomize" -> true;
            default -> false;
        };
    }

    private static double frameRate(ObjectNode prompt) {
        JsonNode value = uniqueInputs(prompt, "VHS_VideoCombine").get("frame_rate");
        double frameRate = value == null ? 0 : value.asDouble();
        if (frameRate < 1 || frameRate > 120) {
            throw new ServiceException("ComfyUI 工作流配置无效");
        }
        return frameRate;
    }

    private static int wavFrameCount(byte[] wav, double frameRate) {
        if (wav == null || wav.length < 44 || !hasTag(wav, 0, "RIFF") || !hasTag(wav, 8, "WAVE")) {
            throw new ServiceException("数字人音频格式无效");
        }
        long byteRate = -1;
        long dataSize = -1;
        int offset = 12;
        while (offset <= wav.length - 8) {
            long chunkSize = littleEndianUnsignedInt(wav, offset + 4);
            long dataOffset = (long) offset + 8;
            long nextOffset = dataOffset + chunkSize + (chunkSize & 1L);
            if (chunkSize > Integer.MAX_VALUE || nextOffset > wav.length) {
                throw new ServiceException("数字人音频格式无效");
            }
            if (hasTag(wav, offset, "fmt ")) {
                if (chunkSize < 12) {
                    throw new ServiceException("数字人音频格式无效");
                }
                byteRate = littleEndianUnsignedInt(wav, offset + 16);
            } else if (hasTag(wav, offset, "data")) {
                dataSize = chunkSize;
            }
            offset = (int) nextOffset;
        }
        if (byteRate <= 0 || dataSize <= 0) {
            throw new ServiceException("数字人音频格式无效");
        }
        long rawFrames = (long) Math.ceil(dataSize * frameRate / byteRate);
        if (rawFrames <= 0 || rawFrames > 432_000) {
            throw new ServiceException("数字人音频时长无效");
        }
        long normalized = ((rawFrames - 1 + 3) / 4) * 4 + 1;
        return Math.toIntExact(normalized);
    }

    private static boolean hasTag(byte[] value, int offset, String tag) {
        if (offset < 0 || offset + 4 > value.length) {
            return false;
        }
        byte[] expected = tag.getBytes(StandardCharsets.US_ASCII);
        for (int index = 0; index < expected.length; index++) {
            if (value[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static long littleEndianUnsignedInt(byte[] value, int offset) {
        if (offset < 0 || offset + 4 > value.length) {
            throw new ServiceException("数字人音频格式无效");
        }
        return (value[offset] & 0xffL)
            | ((value[offset + 1] & 0xffL) << 8)
            | ((value[offset + 2] & 0xffL) << 16)
            | ((value[offset + 3] & 0xffL) << 24);
    }

    private static String extension(String mediaType, String role) {
        String normalized = mediaType == null ? "" : mediaType.toLowerCase();
        if ("portrait".equals(role)) {
            return normalized.contains("jpeg") || normalized.contains("jpg") ? ".jpg" : ".png";
        }
        return normalized.contains("mpeg") ? ".mp3" : normalized.contains("mp4") ? ".m4a" : ".wav";
    }

    @Override
    public DigitalHumanVideoPollDTO poll(String providerJobId) {
        if (providerJobId == null || !PROMPT_ID.matcher(providerJobId).matches()) {
            throw new ServiceException("视频供应商任务编号无效");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(endpoint("/history/" + providerJobId))
            .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET();
        DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
        DigitalHumanHttpSupport.LimitedResponse response = send(
            builder.build(), "视频供应商查询失败", MAX_JSON_RESPONSE_BYTES);
        if (response.statusCode() != 200) {
            throw new ServiceException("视频供应商查询失败");
        }
        try {
            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode job = root == null ? null : root.get(providerJobId);
            if (job == null) {
                return running();
            }
            JsonNode status = job.path("status");
            String statusValue = status.path("status_str").asString("");
            boolean completed = status.path("completed").booleanValue(false);
            if ("error".equalsIgnoreCase(statusValue) || "failed".equalsIgnoreCase(statusValue)
                || (completed && !"success".equalsIgnoreCase(statusValue))) {
                return failed("COMFYUI_JOB_FAILED");
            }
            if (!completed || !"success".equalsIgnoreCase(statusValue)) {
                return running();
            }
            OutputFile output = findMp4(job.path("outputs"));
            return output == null ? failed("COMFYUI_OUTPUT_MISSING") : download(output);
        } catch (RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("视频供应商查询失败");
        }
    }

    private DigitalHumanVideoPollDTO download(OutputFile output) {
        String query = "?filename=" + encode(output.filename()) + "&subfolder=" + encode(output.subfolder())
            + "&type=" + encode(output.type());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(endpoint("/view" + query))
            .timeout(Duration.ofSeconds(300)).header("Accept", "video/mp4").GET();
        DigitalHumanHttpSupport.basic(builder, properties.getBasicUser(), properties.getBasicPassword());
        DigitalHumanHttpSupport.LimitedResponse response = send(
            builder.build(), "视频供应商下载失败", MAX_VIDEO_RESPONSE_BYTES);
        String mediaType = response.headers().firstValue("Content-Type").orElse("");
        if (response.statusCode() != 200 || !mediaType.toLowerCase().startsWith("video/mp4")
            || response.body().length == 0) {
            throw new ServiceException("视频供应商下载失败");
        }
        return new DigitalHumanVideoPollDTO(DigitalHumanVideoProviderStatus.SUCCEEDED, 100,
            response.body(), "video/mp4", "mp4", null);
    }

    private java.net.URI endpoint(String path) {
        return DigitalHumanHttpSupport.endpoint(
            properties.getBaseUrl(), path, properties.getInsecureHttpAllowedHosts());
    }

    private DigitalHumanHttpSupport.LimitedResponse send(HttpRequest request, String message, int maxBytes) {
        try {
            return DigitalHumanHttpSupport.sendLimited(httpClient, request, maxBytes, message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(message);
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException(message);
        }
    }

    private static OutputFile findMp4(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            String filename = node.path("filename").asString("");
            String format = node.path("format").asString("");
            if (filename.toLowerCase().endsWith(".mp4") || format.toLowerCase().contains("mp4")) {
                return new OutputFile(filename, node.path("subfolder").asString(""),
                    node.path("type").asString("output"));
            }
        }
        for (JsonNode child : node) {
            OutputFile output = findMp4(child);
            if (output != null) {
                return output;
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static DigitalHumanVideoPollDTO running() {
        return new DigitalHumanVideoPollDTO(DigitalHumanVideoProviderStatus.RUNNING, 50,
            null, null, null, null);
    }

    private static DigitalHumanVideoPollDTO failed(String code) {
        return new DigitalHumanVideoPollDTO(DigitalHumanVideoProviderStatus.FAILED, 100,
            null, null, null, code);
    }

    private record OutputFile(String filename, String subfolder, String type) {
    }

    private record UploadedFile(String name, String subfolder) {
        String workflowPath() {
            return subfolder + '/' + name;
        }
    }

    private record LinkSource(String nodeId, int outputIndex) {
    }
}
