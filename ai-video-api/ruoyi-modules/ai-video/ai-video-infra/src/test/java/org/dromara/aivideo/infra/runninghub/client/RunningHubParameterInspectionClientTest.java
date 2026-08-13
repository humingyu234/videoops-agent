package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.workflow.constant.WorkflowErrorCodes;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.RunningHubParameterInspectionDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpRequest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class RunningHubParameterInspectionClientTest {

    private IRunningHubAccountService accountService;
    private IWorkflowCredentialReadService credentialReadService;
    private RecordingTransport transport;
    private RunningHubParameterInspectionClient client;
    private char[] plaintextApiKey;

    @BeforeEach
    void setUp() {
        accountService = mock(IRunningHubAccountService.class);
        credentialReadService = mock(IWorkflowCredentialReadService.class);
        transport = new RecordingTransport();
        client = new RunningHubParameterInspectionClient(
            accountService, credentialReadService, JsonMapper.builder().build(), transport);
        when(accountService.queryInspectionCredential("201"))
            .thenReturn(new RunningHubAccountDTOs.InspectionCredential(
                "201", "primary", "v1:encrypted"));
        plaintextApiKey = "secret/+?".toCharArray();
        when(credentialReadService.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "v1:encrypted"))
            .thenReturn(plaintextApiKey);
    }

    @Test
    void springContextUsesServiceConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IRunningHubAccountService.class, () -> mock(IRunningHubAccountService.class));
            context.registerBean(IWorkflowCredentialReadService.class,
                () -> mock(IWorkflowCredentialReadService.class));
            context.register(RunningHubParameterInspectionClient.class);

            context.refresh();

            assertThat(context.getBean(RunningHubParameterInspectionClient.class)).isNotNull();
        }
    }

    @Test
    void readsAiAppCandidatesFromFixedHttpsEndpointPreservingSafeRawFieldTypes() {
        transport.responseBody = """
            {
              "code": 0,
              "msg": "success",
              "data": {
                "webappName": "Flux Kontext单图模式",
                "curl": "must-not-leak",
                "statisticsInfo": {"useCount": "10"},
                "nodeInfoList": [
                  {
                    "nodeId": "39",
                    "nodeName": "LoadImage",
                    "fieldName": "image",
                    "fieldValue": "example.png",
                    "fieldData": "[[\\\"private.png\\\"],{\\\"image_upload\\\":true}]",
                    "fieldType": "IMAGE",
                    "description": "上传图像"
                  },
                  {
                    "nodeId": "37",
                    "nodeName": "RH_ComfyFluxKontext",
                    "fieldName": "model",
                    "fieldValue": "flux-kontext-pro",
                    "fieldData": "[{\\\"name\\\":\\\"flux-kontext-pro\\\",\\\"index\\\":\\\"flux-kontext-pro\\\",\\\"description\\\":\\\"默认\\\"},{\\\"name\\\":\\\"flux-kontext-max\\\",\\\"index\\\":\\\"flux-kontext-max\\\"},{\\\"default\\\":\\\"flux-kontext-pro\\\",\\\"description\\\":\\\"忽略\\\"}]",
                    "fieldType": "LIST",
                    "description": "模型切换"
                  },
                  {
                    "nodeId": "38",
                    "nodeName": "Prompt",
                    "fieldName": "prompt",
                    "fieldValue": "a cat",
                    "fieldType": "STRING"
                  },
                  {
                    "nodeId": "40",
                    "nodeName": "Unsafe",
                    "fieldName": "prompt",
                    "fieldValue": "must-not-be-returned",
                    "fieldType": "<script>"
                  },
                  {
                    "nodeId": "41",
                    "nodeName": "Oversized",
                    "fieldName": "prompt",
                    "fieldValue": "must-not-be-returned",
                    "fieldType": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                  }
                ]
              }
            }
            """;

        RunningHubParameterInspectionDTOs.Result result = client.inspect(
            new RunningHubParameterInspectionDTOs.Request(
                "201", "runninghub_ai_app", null, "1937084629516193794"));

        assertThat(result.webAppName()).isEqualTo("Flux Kontext单图模式");
        assertThat(result.candidates()).hasSize(3);
        assertThat(result.candidates().get(0))
            .extracting("nodeId", "nodeName", "fieldName", "fieldType", "description", "defaultValue")
            .containsExactly("39", "LoadImage", "image", "IMAGE", "上传图像", "example.png");
        assertThat(result.candidates()).extracting(RunningHubParameterInspectionDTOs.Candidate::fieldType)
            .containsExactly("IMAGE", "LIST", "STRING");
        assertThat(result.candidates().get(1).options())
            .containsExactly(
                new RunningHubParameterInspectionDTOs.Option("flux-kontext-pro", "flux-kontext-pro"),
                new RunningHubParameterInspectionDTOs.Option("flux-kontext-max", "flux-kontext-max"));
        assertThat(transport.request.uri().getScheme()).isEqualTo("https");
        assertThat(transport.request.uri().getHost()).isEqualTo("www.runninghub.cn");
        assertThat(transport.request.uri().getPath()).isEqualTo("/api/webapp/apiCallDemo");
        assertThat(transport.request.uri().getRawQuery())
            .isEqualTo("apiKey=secret%2F%2B%3F&webappId=1937084629516193794");
        assertThat(transport.request.headers().firstValue("Authorization"))
            .contains("Bearer secret/+?");
        assertThat(transport.maxResponseBytes).isEqualTo(2 * 1024 * 1024);
        assertThat(plaintextApiKey).containsOnly('\0');
        assertThat(result.toString()).doesNotContain("must-not-leak", "secret/+?");
    }

    @Test
    void extractsOnlyLiteralWorkflowInputsAndSkipsConnectionsAndComplexValues() {
        transport.responseBody = """
            {
              "code": 0,
              "msg": "SUCCESS",
              "data": {
                "prompt": "{\\\"6\\\":{\\\"inputs\\\":{\\\"text\\\":\\\"a cat\\\",\\\"clip\\\":[\\\"4\\\",0],\\\"metadata\\\":{\\\"unsafe\\\":true}},\\\"class_type\\\":\\\"CLIPTextEncode\\\",\\\"_meta\\\":{\\\"title\\\":\\\"正向提示词\\\"}},\\\"3\\\":{\\\"inputs\\\":{\\\"seed\\\":42,\\\"cfg\\\":7.5,\\\"enabled\\\":true},\\\"class_type\\\":\\\"KSampler\\\"}}"
              }
            }
            """;

        RunningHubParameterInspectionDTOs.Result result = client.inspect(
            new RunningHubParameterInspectionDTOs.Request(
                "201", "runninghub_workflow", "1980237776367083521", null));

        assertThat(result.webAppName()).isNull();
        assertThat(result.candidates()).extracting(
                RunningHubParameterInspectionDTOs.Candidate::nodeId,
                RunningHubParameterInspectionDTOs.Candidate::nodeName,
                RunningHubParameterInspectionDTOs.Candidate::fieldName,
                RunningHubParameterInspectionDTOs.Candidate::fieldType,
                RunningHubParameterInspectionDTOs.Candidate::defaultValue)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("6", "正向提示词", "text", "text", "a cat"),
                org.assertj.core.groups.Tuple.tuple("3", "KSampler", "seed", "integer", "42"),
                org.assertj.core.groups.Tuple.tuple("3", "KSampler", "cfg", "decimal", "7.5"),
                org.assertj.core.groups.Tuple.tuple("3", "KSampler", "enabled", "boolean", "true"));
        assertThat(result.candidates()).extracting(RunningHubParameterInspectionDTOs.Candidate::fieldName)
            .doesNotContain("clip", "metadata");
        assertThat(transport.request.method()).isEqualTo("POST");
        assertThat(transport.request.uri().toString())
            .isEqualTo("https://www.runninghub.cn/api/openapi/getJsonApiFormat");
        assertThat(transport.requestBody()).contains(
            "\"apiKey\":\"secret/+?\"",
            "\"workflowId\":\"1980237776367083521\"");
        assertThat(plaintextApiKey).containsOnly('\0');
    }

    @Test
    void classifiesWorkflowFileInputsByNodeClassAndFieldName() {
        String prompt = """
            {"10":{"inputs":{"image":"portrait.png"},"class_type":"LoadImage"},
             "11":{"inputs":{"audio":"voice.wav"},"class_type":"LoadAudio"},
             "12":{"inputs":{"video":"clip.mp4"},"class_type":"VHS_LoadVideo"},
             "13":{"inputs":{"file":"asset.bin"},"class_type":"LoadFile"}}
            """;
        transport.responseBody = """
            {
              "code": 0,
              "data": {
                "prompt": %s
              }
            }
            """.formatted(JsonMapper.builder().build().writeValueAsString(prompt));

        RunningHubParameterInspectionDTOs.Result result = client.inspect(
            new RunningHubParameterInspectionDTOs.Request(
                "201", "runninghub_workflow", "1980237776367083521", null));

        assertThat(result.candidates()).extracting(
                RunningHubParameterInspectionDTOs.Candidate::nodeName,
                RunningHubParameterInspectionDTOs.Candidate::fieldName,
                RunningHubParameterInspectionDTOs.Candidate::fieldType,
                RunningHubParameterInspectionDTOs.Candidate::defaultValue)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("LoadImage", "image", "image", "portrait.png"),
                org.assertj.core.groups.Tuple.tuple("LoadAudio", "audio", "audio", "voice.wav"),
                org.assertj.core.groups.Tuple.tuple("VHS_LoadVideo", "video", "video", "clip.mp4"),
                org.assertj.core.groups.Tuple.tuple("LoadFile", "file", "file", "asset.bin"));
    }

    @Test
    void failsClosedOnOversizedMalformedOrProviderErrorWithoutLeakingCredential() {
        transport.responseBody = "x".repeat(2 * 1024 * 1024 + 1);
        assertInspectionFailure();

        resetPlaintext();
        transport.responseBody = "{\"code\":500,\"msg\":\"secret/+? upstream detail\"}";
        assertInspectionFailure();

        verify(accountService, org.mockito.Mockito.times(2)).queryInspectionCredential("201");
    }

    @Test
    void exposesOnlySafeDiagnosticForWorkflowHttpAndProviderFailures() {
        transport.statusCode = 429;
        transport.responseBody = "{\"apiKey\":\"secret/+?\"}";

        assertThatThrownBy(() -> client.inspect(new RunningHubParameterInspectionDTOs.Request(
            "201", "runninghub_workflow", "1980237776367083521", null)))
            .isInstanceOfSatisfying(ServiceException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_PARAMETER_INSPECTION_FAILED);
                assertThat(exception.getMessage()).contains("HTTP 429").doesNotContain("secret/+?");
            });

        resetPlaintext();
        transport.statusCode = 200;
        transport.responseBody = "{\"code\":403,\"msg\":\"secret/+? remote detail\"}";

        assertThatThrownBy(() -> client.inspect(new RunningHubParameterInspectionDTOs.Request(
            "201", "runninghub_workflow", "1980237776367083521", null)))
            .isInstanceOfSatisfying(ServiceException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_PARAMETER_INSPECTION_FAILED);
                assertThat(exception.getMessage()).contains("错误码 403").doesNotContain("secret/+?", "remote detail");
            });

        resetPlaintext();
        transport.responseBody = "{\"code\":810,\"msg\":\"secret/+? remote detail\"}";

        assertThatThrownBy(() -> client.inspect(new RunningHubParameterInspectionDTOs.Request(
            "201", "runninghub_workflow", "1980237776367083521", null)))
            .isInstanceOfSatisfying(ServiceException.class, exception -> {
                assertThat(exception.getCode()).isEqualTo(WorkflowErrorCodes.WORKFLOW_PARAMETER_INSPECTION_FAILED);
                assertThat(exception.getMessage())
                    .contains("未保存或未手动运行")
                    .doesNotContain("secret/+?", "remote detail");
            });
    }

    private void assertInspectionFailure() {
        assertThatThrownBy(() -> client.inspect(new RunningHubParameterInspectionDTOs.Request(
            "201", "runninghub_ai_app", null, "1937084629516193794")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> {
                assertThat(exception.getCode())
                    .isEqualTo(WorkflowErrorCodes.WORKFLOW_PARAMETER_INSPECTION_FAILED);
                assertThat(exception.getMessage()).doesNotContain("secret/+?", "upstream detail");
            });
        assertThat(plaintextApiKey).containsOnly('\0');
    }

    private void resetPlaintext() {
        plaintextApiKey = "secret/+?".toCharArray();
        when(credentialReadService.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "v1:encrypted"))
            .thenReturn(plaintextApiKey);
    }

    private static final class RecordingTransport implements RunningHubHttpTransport {

        private HttpRequest request;
        private int maxResponseBytes;
        private int statusCode = 200;
        private String responseBody;

        @Override
        public Response send(HttpRequest request, int maxResponseBytes) {
            this.request = request;
            this.maxResponseBytes = maxResponseBytes;
            return new Response(statusCode, responseBody.getBytes(StandardCharsets.UTF_8));
        }

        private String requestBody() {
            CompletableFuture<byte[]> completed = new CompletableFuture<>();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.writeBytes(bytes);
                    Arrays.fill(bytes, (byte) 0);
                }

                @Override
                public void onError(Throwable throwable) {
                    completed.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    completed.complete(output.toByteArray());
                }
            });
            byte[] body = completed.join();
            try {
                return new String(body, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(body, (byte) 0);
            }
        }
    }
}
