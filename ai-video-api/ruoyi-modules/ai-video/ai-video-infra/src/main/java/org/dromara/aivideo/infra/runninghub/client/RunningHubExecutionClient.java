package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.RunningHubExecutionDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IRunningHubExecutionClient;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.Options;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Function;

/** Official RunningHub submit/query client with bounded private result materialization. */
@Component
public class RunningHubExecutionClient implements IRunningHubExecutionClient {

    private static final URI AI_APP = URI.create("https://www.runninghub.cn/task/openapi/ai-app/run");
    private static final URI WORKFLOW = URI.create("https://www.runninghub.cn/task/openapi/create");
    private static final URI QUERY = URI.create("https://www.runninghub.cn/openapi/v2/query");
    private static final int JSON_LIMIT = 2 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,19}");
    private static final Set<String> PENDING = Set.of("QUEUED", "RUNNING", "PENDING", "WAITING");

    private final IRunningHubAccountService accountService;
    private final IWorkflowCredentialReadService credentialReadService;
    private final JsonMapper jsonMapper;
    private final RunningHubHttpTransport transport;
    private final HttpClient downloadClient;
    private final Function<String, InetAddress[]> addressResolver;

    @Autowired
    public RunningHubExecutionClient(IRunningHubAccountService accountService,
                                     IWorkflowCredentialReadService credentialReadService) {
        this(accountService, credentialReadService, JsonMapper.builder().build(), new JdkRunningHubHttpTransport());
    }

    RunningHubExecutionClient(IRunningHubAccountService accountService,
                              IWorkflowCredentialReadService credentialReadService,
                              JsonMapper jsonMapper, RunningHubHttpTransport transport) {
        this.accountService = Objects.requireNonNull(accountService);
        this.credentialReadService = Objects.requireNonNull(credentialReadService);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.transport = Objects.requireNonNull(transport);
        this.downloadClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        this.addressResolver = host -> {
            try { return InetAddress.getAllByName(host); }
            catch (Exception exception) { throw failure("RunningHub result address cannot be resolved"); }
        };
    }

    RunningHubExecutionClient(IRunningHubAccountService accountService,
                              IWorkflowCredentialReadService credentialReadService,
                              JsonMapper jsonMapper, RunningHubHttpTransport transport,
                              HttpClient downloadClient, Function<String, InetAddress[]> addressResolver) {
        this.accountService = Objects.requireNonNull(accountService);
        this.credentialReadService = Objects.requireNonNull(credentialReadService);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.transport = Objects.requireNonNull(transport);
        this.downloadClient = Objects.requireNonNull(downloadClient);
        this.addressResolver = Objects.requireNonNull(addressResolver);
    }

    @Override
    public RunningHubExecutionDTOs.Submission submit(RunningHubExecutionDTOs.SubmitRequest request) {
        requireId(request.accountId(), "RunningHub account");
        requireId(request.remoteId(), "RunningHub remote resource");
        return withApiKey(request.accountId(), apiKey -> {
            ObjectNode body = jsonMapper.createObjectNode();
            body.put("apiKey", apiKey);
            if (request.instanceType() != null && !request.instanceType().isBlank()) {
                body.put("instanceType", request.instanceType());
            }
            URI endpoint;
            if ("runninghub_ai_app".equals(request.executionMode())) {
                endpoint = AI_APP;
                body.put("webappId", request.remoteId());
            } else if ("runninghub_workflow".equals(request.executionMode())) {
                endpoint = WORKFLOW;
                body.put("workflowId", request.remoteId());
                if (request.accessPasswordCiphertext() != null && !request.accessPasswordCiphertext().isBlank()) {
                    char[] password = credentialReadService.decryptForUse(
                        WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD, request.accessPasswordCiphertext());
                    try { body.put("accessPassword", new String(password)); }
                    finally { Arrays.fill(password, '\0'); }
                }
            } else {
                throw failure("RunningHub execution mode is invalid");
            }
            ArrayNode values = body.putArray("nodeInfoList");
            if (request.nodeInfoList() != null) {
                for (RunningHubExecutionDTOs.NodeInput input : request.nodeInfoList()) {
                    ObjectNode value = values.addObject();
                    value.put("nodeId", input.nodeId()); value.put("fieldName", input.fieldName());
                    value.set("fieldValue", input.value());
                }
            }
            JsonNode envelope = sendJson(endpoint, apiKey, body);
            if (envelope.path("code").asInt(Integer.MIN_VALUE) != 0 || !envelope.path("data").isObject()) {
                throw failure("RunningHub rejected the task");
            }
            JsonNode data = envelope.path("data");
            String taskId = data.path("taskId").asText();
            requireId(taskId, "RunningHub task");
            return new RunningHubExecutionDTOs.Submission(taskId, data.path("taskStatus").asText("ACCEPTED"));
        });
    }

    @Override
    public RunningHubExecutionDTOs.QueryResult query(String accountId, String externalTaskId) {
        requireId(accountId, "RunningHub account"); requireId(externalTaskId, "RunningHub task");
        return withApiKey(accountId, apiKey -> {
            ObjectNode body = jsonMapper.createObjectNode(); body.put("taskId", externalTaskId);
            JsonNode response = sendJson(QUERY, apiKey, body);
            String status = response.path("status").asText("UNKNOWN").toUpperCase(Locale.ROOT);
            if ("SUCCESS".equals(status)) {
                List<RunningHubExecutionDTOs.Output> outputs = new ArrayList<>();
                JsonNode results = response.path("results");
                if (results.isArray()) {
                    int resultIndex = 0;
                    for (JsonNode result : results) {
                        String url = result.path("url").asText();
                        String type = result.path("outputType").asText();
                        if (!url.isBlank()) {
                            outputs.add(new RunningHubExecutionDTOs.Output(url, type, resultIndex));
                        }
                        resultIndex++;
                    }
                }
                return new RunningHubExecutionDTOs.QueryResult(
                    RunningHubExecutionDTOs.QueryState.SUCCESS, status, null, List.copyOf(outputs));
            }
            if (PENDING.contains(status)) {
                return new RunningHubExecutionDTOs.QueryResult(
                    RunningHubExecutionDTOs.QueryState.PENDING, status, null, List.of());
            }
            return new RunningHubExecutionDTOs.QueryResult(RunningHubExecutionDTOs.QueryState.FAILED, status,
                safeProviderError(response.path("errorMessage").asText()), List.of());
        });
    }

    @Override
    public RunningHubExecutionDTOs.StoredOutput materializeOutput(RunningHubExecutionDTOs.Output output,
                                                                  RunningHubExecutionDTOs.OutputStoragePolicy policy,
                                                                  long orderId) {
        URI uri;
        try { uri = URI.create(output.url()); }
        catch (RuntimeException exception) { throw failure("RunningHub result URL is invalid"); }
        validateResultUri(uri, policy.allowedHosts());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> response = downloadClient.send(request,
                info -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), bytes -> {
                    if (bytes.length > policy.maxBytes()) throw failure("RunningHub result exceeds size limit");
                    return bytes;
                }));
            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0
                || response.body().length > policy.maxBytes()) {
                throw failure("RunningHub result download failed");
            }
            ResultFileMetadata metadata = resultFileMetadata(response.body(), output.outputType(), uri);
            String sha256 = sha256(response.body());
            String objectKey = "workflow-results/" + orderId + "/" + output.resultIndex()
                + "-" + sha256 + "." + metadata.fileFormat();
            OssClient oss = OssFactory.instance();
            oss.upload(objectKey, new ByteArrayInputStream(response.body()), response.body().length,
                Options.builder().setContentType(metadata.contentType()));
            return new RunningHubExecutionDTOs.StoredOutput(objectKey, "result." + metadata.fileFormat(),
                metadata.contentType(), metadata.fileFormat(), response.body().length, sha256);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("RunningHub result download interrupted");
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("RunningHub result download failed");
        }
    }

    private JsonNode sendJson(URI uri, String apiKey, JsonNode body) {
        RunningHubHttpTransport.Response response = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            response = transport.send(request, JSON_LIMIT);
            if (response.statusCode() != 200 || response.body() == null) throw failure("RunningHub request failed");
            return jsonMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw failure("RunningHub request interrupted");
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("RunningHub response is invalid");
        } finally {
            if (response != null && response.body() != null) Arrays.fill(response.body(), (byte) 0);
        }
    }

    private <T> T withApiKey(String accountId, ApiKeyAction<T> action) {
        RunningHubAccountDTOs.InspectionCredential credential = accountService.queryInspectionCredential(accountId);
        char[] key = credentialReadService.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, credential.encryptedApiKey());
        try {
            if (key == null || key.length == 0) throw failure("RunningHub credential is unavailable");
            return action.apply(new String(key));
        } finally {
            if (key != null) Arrays.fill(key, '\0');
        }
    }

    private void validateResultUri(URI uri, List<String> allowedHosts) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
            || uri.getFragment() != null || !allowedHost(host, allowedHosts)) {
            throw failure("RunningHub result URL is not allowed");
        }
        try {
            InetAddress[] addresses = addressResolver.apply(host);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(this::notPublic)) {
                throw failure("RunningHub result address is not public");
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("RunningHub result address cannot be resolved");
        }
    }

    private boolean allowedHost(String host, List<String> allowedHosts) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return allowedHosts != null && allowedHosts.stream().anyMatch(pattern -> {
            String candidate = pattern.toLowerCase(Locale.ROOT);
            return candidate.startsWith("*.") ? normalized.endsWith(candidate.substring(1))
                && normalized.length() > candidate.length() - 1 : normalized.equals(candidate);
        });
    }

    private boolean notPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff, second = bytes[1] & 0xff;
            return first == 0 || first == 10 || first == 127 || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168) || (first == 198 && (second == 18 || second == 19));
        }
        return address instanceof Inet6Address && ((bytes[0] & 0xfe) == 0xfc);
    }

    private ResultFileMetadata resultFileMetadata(byte[] bytes, String providerType, URI uri) {
        String detected = detectFormat(bytes);
        String hinted = normalizeFormat(providerType);
        if (hinted.isEmpty()) hinted = extension(uri.getPath());
        String format = !detected.isEmpty() ? detected : hinted;
        if (format.isEmpty()) format = "bin";
        return new ResultFileMetadata(format, contentType(format));
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) return "jpg";
        if (startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "png";
        if (startsWith(bytes, 'G', 'I', 'F', '8', '7', 'a') || startsWith(bytes, 'G', 'I', 'F', '8', '9', 'a')) {
            return "gif";
        }
        if (startsWith(bytes, 'R', 'I', 'F', 'F') && startsWithAt(bytes, 8, 'W', 'E', 'B', 'P')) return "webp";
        if (startsWith(bytes, 'R', 'I', 'F', 'F') && startsWithAt(bytes, 8, 'W', 'A', 'V', 'E')) return "wav";
        if (startsWith(bytes, 'f', 'L', 'a', 'C')) return "flac";
        if (startsWith(bytes, 'O', 'g', 'g', 'S')) return "ogg";
        if (startsWith(bytes, 'I', 'D', '3')
            || (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && ((bytes[1] & 0xe0) == 0xe0))) return "mp3";
        if (startsWith(bytes, '%', 'P', 'D', 'F', '-')) return "pdf";
        if (startsWith(bytes, 'P', 'K', 0x03, 0x04)) return "zip";
        if (bytes.length >= 12 && startsWithAt(bytes, 4, 'f', 't', 'y', 'p')) return "mp4";
        if (startsWith(bytes, 0x1a, 0x45, 0xdf, 0xa3)) return "webm";
        return "";
    }

    private boolean startsWith(byte[] bytes, int... expected) {
        return startsWithAt(bytes, 0, expected);
    }

    private boolean startsWithAt(byte[] bytes, int offset, int... expected) {
        if (offset < 0 || bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[offset + index] & 0xff) != (expected[index] & 0xff)) return false;
        }
        return true;
    }

    private String normalizeFormat(String value) {
        if (value == null) return "";
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        if ("jpeg".equals(normalized)) normalized = "jpg";
        return normalized.matches("[a-z0-9]{1,16}") ? normalized : "";
    }

    private String extension(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? normalizeFormat(path.substring(dot + 1)) : "";
    }

    private String contentType(String format) {
        return switch (format) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "json" -> "application/json";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private String safeProviderError(String value) {
        return value == null || value.isBlank() ? "制作失败，请稍后重试" : value.strip();
    }
    private void requireId(String value, String label) {
        if (value == null || !ID.matcher(value).matches()) throw failure(label + " id is invalid");
    }
    private ServiceException failure(String message) { return new ServiceException(message); }
    private record ResultFileMetadata(String fileFormat, String contentType) { }
    @FunctionalInterface private interface ApiKeyAction<T> { T apply(String apiKey); }
}
