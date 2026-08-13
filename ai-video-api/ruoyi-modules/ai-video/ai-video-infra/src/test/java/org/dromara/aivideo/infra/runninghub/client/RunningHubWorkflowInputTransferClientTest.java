package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.asset.dto.RunningHubUploadedFileDTO;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class RunningHubWorkflowInputTransferClientTest {

    @Test
    void streamsTheInputToFixedRunningHubEndpointAndReturnsOnlyRemoteFileName() {
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IRunningHubAccountService accountService = mock(IRunningHubAccountService.class);
        IWorkflowCredentialReadService credentialReadService = mock(IWorkflowCredentialReadService.class);
        RecordingTransport transport = new RecordingTransport();
        char[] apiKey = "test-key".toCharArray();
        when(templateService.queryExecutionConfig("101")).thenReturn(new WorkflowTemplateDTOs.ExecutionConfig(
            "301", "101", "201", "runninghub_ai_app", null, "901", null, "{}", "{}", 300,
            true, false, "success", 1L, LocalDateTime.now()));
        when(accountService.queryInspectionCredential("201")).thenReturn(
            new RunningHubAccountDTOs.InspectionCredential("201", "primary", "encrypted"));
        when(credentialReadService.decryptForUse(WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "encrypted"))
            .thenReturn(apiKey);
        transport.responseBody = "{\"code\":0,\"data\":{\"fileName\":\"runninghub-file.png\"}}";
        RunningHubWorkflowInputTransferClient client = new RunningHubWorkflowInputTransferClient(templateService,
            accountService, credentialReadService, JsonMapper.builder().build(), transport);

        RunningHubUploadedFileDTO result = client.uploadWorkflowInput("101", "portrait.png", "image/png", 7,
            new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.fileName()).isEqualTo("runninghub-file.png");
        assertThat(transport.request.uri().toString())
            .isEqualTo("https://www.runninghub.cn/openapi/v2/media/upload/binary");
        assertThat(transport.request.headers().firstValue("Authorization"))
            .hasValueSatisfying(value -> assertThat(value).contains("Bearer test-key"));
        assertThat(transport.request.headers().firstValue("Content-Type"))
            .hasValueSatisfying(value -> assertThat(value).contains("multipart/form-data; boundary="));
        assertThat(transport.requestBody()).contains("name=\"file\"", "filename=\"portrait.png\"", "payload");
        assertThat(apiKey).containsOnly('\0');
    }

    private static final class RecordingTransport implements RunningHubHttpTransport {
        private HttpRequest request;
        private String responseBody;

        @Override
        public Response send(HttpRequest request, int maxResponseBytes) {
            this.request = request;
            return new Response(200, responseBody.getBytes(StandardCharsets.UTF_8));
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
