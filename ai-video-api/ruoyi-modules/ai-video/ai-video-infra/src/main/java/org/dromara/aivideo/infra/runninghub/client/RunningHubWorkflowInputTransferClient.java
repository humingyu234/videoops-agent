package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.asset.dto.RunningHubUploadedFileDTO;
import org.dromara.aivideo.asset.service.IRunningHubFileTransferService;
import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** RunningHub-owned workflow input upload; user input bytes are never persisted by this service. */
@Component
public final class RunningHubWorkflowInputTransferClient implements IRunningHubFileTransferService {

    private static final URI UPLOAD_ENDPOINT = URI.create("https://www.runninghub.cn/openapi/v2/media/upload/binary");
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final IWorkflowTemplateService templateService;
    private final IRunningHubAccountService accountService;
    private final IWorkflowCredentialReadService credentialReadService;
    private final JsonMapper jsonMapper;
    private final RunningHubHttpTransport transport;

    @Autowired
    public RunningHubWorkflowInputTransferClient(IWorkflowTemplateService templateService,
                                                 IRunningHubAccountService accountService,
                                                 IWorkflowCredentialReadService credentialReadService) {
        this(templateService, accountService, credentialReadService, JsonMapper.builder().build(),
            new JdkRunningHubHttpTransport());
    }

    RunningHubWorkflowInputTransferClient(IWorkflowTemplateService templateService,
                                          IRunningHubAccountService accountService,
                                          IWorkflowCredentialReadService credentialReadService,
                                          JsonMapper jsonMapper, RunningHubHttpTransport transport) {
        this.templateService = Objects.requireNonNull(templateService);
        this.accountService = Objects.requireNonNull(accountService);
        this.credentialReadService = Objects.requireNonNull(credentialReadService);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.transport = Objects.requireNonNull(transport);
    }

    @Override
    public RunningHubUploadedFileDTO uploadWorkflowInput(String templateId, String fileName, String contentType,
                                                         long sizeBytes, InputStream content) {
        validateInput(templateId, fileName, contentType, sizeBytes, content);
        WorkflowTemplateDTOs.ExecutionConfig executionConfig = templateService.queryExecutionConfig(templateId);
        if (executionConfig == null || !executionConfig.enabled() || isBlank(executionConfig.runningHubAccountId())) {
            throw new ServiceException("工作流执行配置不可用", WorkflowErrorCodes.WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE);
        }
        RunningHubAccountDTOs.InspectionCredential credential =
            accountService.queryInspectionCredential(executionConfig.runningHubAccountId());
        char[] apiKey = null;
        try {
            apiKey = credentialReadService.decryptForUse(
                WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, credential.encryptedApiKey());
            if (apiKey == null || apiKey.length == 0) {
                throw transferFailure("RunningHub API Key 不可用");
            }
            return send(fileName, contentType, content, new String(apiKey));
        } finally {
            if (apiKey != null) {
                Arrays.fill(apiKey, '\0');
            }
        }
    }

    private RunningHubUploadedFileDTO send(String fileName, String contentType, InputStream content, String apiKey) {
        String boundary = "----ai-video-" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(UPLOAD_ENDPOINT)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/json")
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> multipartBody(boundary, fileName, contentType, content)))
            .build();
        RunningHubHttpTransport.Response response = null;
        try {
            response = transport.send(request, MAX_RESPONSE_BYTES);
            if (response.statusCode() != 200 || response.body() == null) {
                throw transferFailure("接口返回 HTTP " + response.statusCode());
            }
            JsonNode envelope = jsonMapper.readTree(response.body());
            if (envelope == null || !envelope.isObject() || envelope.path("code").asInt(Integer.MIN_VALUE) != 0) {
                throw transferFailure("接口未返回成功结果");
            }
            JsonNode fileNameNode = envelope.path("data").path("fileName");
            if (!fileNameNode.isTextual() || !validRemoteFileName(fileNameNode.textValue())) {
                throw transferFailure("接口未返回文件引用");
            }
            return new RunningHubUploadedFileDTO(fileNameNode.textValue());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transferFailure("上传请求被中断");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw transferFailure("上传请求失败");
        } finally {
            if (response != null && response.body() != null) {
                Arrays.fill(response.body(), (byte) 0);
            }
        }
    }

    private InputStream multipartBody(String boundary, String fileName, String contentType, InputStream content) {
        String header = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"" + multipartFileName(fileName) + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        return new java.io.SequenceInputStream(Collections.enumeration(List.of(
            new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)), content,
            new ByteArrayInputStream(footer.getBytes(StandardCharsets.UTF_8)))));
    }

    private void validateInput(String templateId, String fileName, String contentType, long sizeBytes, InputStream content) {
        if (isBlank(templateId) || !templateId.matches("[1-9][0-9]{0,18}") || isBlank(fileName)
            || fileName.length() > 255 || isBlank(contentType) || contentType.length() > 128
            || contentType.chars().anyMatch(Character::isISOControl) || sizeBytes <= 0 || content == null) {
            throw transferFailure("上传参数无效");
        }
    }

    private String multipartFileName(String fileName) {
        return fileName.replace('\\', '_').replace('"', '_').replace('\r', '_').replace('\n', '_');
    }

    private boolean validRemoteFileName(String fileName) {
        return !isBlank(fileName) && fileName.length() <= 512
            && fileName.chars().noneMatch(Character::isISOControl);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ServiceException transferFailure(String reason) {
        return new ServiceException("RunningHub 文件上传失败：" + reason,
            WorkflowErrorCodes.WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE);
    }
}
