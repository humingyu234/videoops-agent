package org.dromara.aivideo.infra.runninghub.client;

import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.RunningHubExecutionDTOs;
import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IWorkflowCredentialReadService;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class RunningHubExecutionClientTest {

    private IRunningHubAccountService accountService;
    private IWorkflowCredentialReadService credentialReadService;
    private RecordingTransport transport;
    private RunningHubExecutionClient client;

    @BeforeEach
    void setUp() {
        accountService = mock(IRunningHubAccountService.class);
        credentialReadService = mock(IWorkflowCredentialReadService.class);
        transport = new RecordingTransport();
        client = new RunningHubExecutionClient(accountService, credentialReadService,
            JsonMapper.builder().build(), transport);
        when(accountService.queryInspectionCredential("301"))
            .thenReturn(new RunningHubAccountDTOs.InspectionCredential("301", "main", "v1:key"));
        when(credentialReadService.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_API_KEY, "v1:key"))
            .thenAnswer(ignored -> "secret-key".toCharArray());
    }

    @Test
    void usesTheDocumentedAiAppEnvelopeAndTopLevelV2QueryContract() {
        transport.enqueue("""
            {"code":0,"msg":"success","data":{"taskId":"9001","taskStatus":"RUNNING"}}
            """);
        transport.enqueue("""
            {"taskId":"9001","status":"SUCCESS","errorCode":"","errorMessage":"",
             "results":[
               {"url":"https://rh.example.com/output/a.png","outputType":"png"},
               {"url":"https://rh.example.com/output/binary-result"}]}
            """);
        JsonMapper mapper = JsonMapper.builder().build();

        RunningHubExecutionDTOs.Submission submission = client.submit(
            new RunningHubExecutionDTOs.SubmitRequest("301", "runninghub_ai_app",
                "2084534713108226049", "plus", null,
                List.of(new RunningHubExecutionDTOs.NodeInput(
                    "53", "text", mapper.getNodeFactory().textNode("portrait")))));
        RunningHubExecutionDTOs.QueryResult query = client.query("301", submission.externalTaskId());

        assertThat(submission).isEqualTo(new RunningHubExecutionDTOs.Submission("9001", "RUNNING"));
        assertThat(query.state()).isEqualTo(RunningHubExecutionDTOs.QueryState.SUCCESS);
        assertThat(query.outputs()).containsExactly(
            new RunningHubExecutionDTOs.Output("https://rh.example.com/output/a.png", "png", 0),
            new RunningHubExecutionDTOs.Output("https://rh.example.com/output/binary-result", "", 1));
        assertThat(transport.requests).hasSize(2);
        assertThat(transport.requests.get(0).uri().getPath()).isEqualTo("/task/openapi/ai-app/run");
        assertThat(transport.requestBody(0)).contains(
            "\"apiKey\":\"secret-key\"",
            "\"webappId\":\"2084534713108226049\"",
            "\"instanceType\":\"plus\"",
            "\"nodeInfoList\":[{\"nodeId\":\"53\",\"fieldName\":\"text\",\"fieldValue\":\"portrait\"}]");
        assertThat(transport.requests.get(1).uri().getPath()).isEqualTo("/openapi/v2/query");
        assertThat(transport.requestBody(1)).isEqualTo("{\"taskId\":\"9001\"}");
        assertThat(transport.requestBody(1)).doesNotContain("apiKey");
        assertThat(transport.requests.get(0).headers().firstValue("Authorization"))
            .contains("Bearer secret-key");
        assertThat(transport.requests.get(1).headers().firstValue("Authorization"))
            .contains("Bearer secret-key");
    }

    @Test
    void usesAdvancedWorkflowCreateSoMappedValuesAreNotDropped() {
        transport.enqueue("""
            {"code":0,"msg":"success","data":{"taskId":"9002","taskStatus":"RUNNING"}}
            """);
        when(credentialReadService.decryptForUse(
            WorkflowCredentialPurpose.RUNNINGHUB_ACCESS_PASSWORD, "v1:password"))
            .thenAnswer(ignored -> "open-sesame".toCharArray());
        JsonMapper mapper = JsonMapper.builder().build();

        client.submit(new RunningHubExecutionDTOs.SubmitRequest("301", "runninghub_workflow",
            "1980237776367083521", "plus", "v1:password",
            List.of(new RunningHubExecutionDTOs.NodeInput(
                "3", "steps", mapper.getNodeFactory().numberNode(24)))));

        assertThat(transport.requests.get(0).uri().getPath()).isEqualTo("/task/openapi/create");
        assertThat(transport.requestBody(0)).contains(
            "\"workflowId\":\"1980237776367083521\"",
            "\"instanceType\":\"plus\"",
            "\"accessPassword\":\"open-sesame\"",
            "\"fieldValue\":24");
        assertThat(transport.requestBody(0)).doesNotContain(
            "retainSeconds", "webhookUrl", "usePersonalQueue", "workflow\"");
    }

    @Test
    void retainsTheProviderFailureReasonInsteadOfReplacingItWithBrandText() {
        transport.enqueue("""
            {"taskId":"9001","status":"FAILED","errorMessage":"显存耗尽导致进程中断"}
            """);

        RunningHubExecutionDTOs.QueryResult query = client.query("301", "9001");

        assertThat(query.state()).isEqualTo(RunningHubExecutionDTOs.QueryState.FAILED);
        assertThat(query.safeError()).isEqualTo("显存耗尽导致进程中断");
    }

    @Test
    void materializesAProviderResultWithoutRestrictingItsMediaType() throws Exception {
        byte[] wav = "RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
        HttpClient downloadClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(wav);
        when(downloadClient.send(
            org.mockito.ArgumentMatchers.any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(response);
        OssClient oss = mock(OssClient.class);
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        RunningHubExecutionClient resultClient = new RunningHubExecutionClient(
            accountService, credentialReadService, JsonMapper.builder().build(), transport, downloadClient,
            host -> new InetAddress[]{publicAddress});

        RunningHubExecutionDTOs.StoredOutput stored;
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            stored = resultClient.materializeOutput(
                new RunningHubExecutionDTOs.Output("https://rh.example.com/output/audio.wav", "wav", 7),
                new RunningHubExecutionDTOs.OutputStoragePolicy(1024, List.of("rh.example.com")), 501L);
        }

        assertThat(stored.contentType()).isEqualTo("audio/wav");
        assertThat(stored.fileFormat()).isEqualTo("wav");
        assertThat(stored.originalName()).isEqualTo("result.wav");
        verify(oss).upload(org.mockito.ArgumentMatchers.matches("workflow-results/501/7-[0-9a-f]{64}\\.wav"),
            org.mockito.ArgumentMatchers.any(java.io.InputStream.class),
            org.mockito.ArgumentMatchers.eq((long) wav.length),
            org.mockito.ArgumentMatchers.any());
    }

    private static final class RecordingTransport implements RunningHubHttpTransport {
        private final ArrayDeque<String> responses = new ArrayDeque<>();
        private final java.util.ArrayList<HttpRequest> requests = new java.util.ArrayList<>();

        private void enqueue(String body) {
            responses.add(body);
        }

        @Override
        public Response send(HttpRequest request, int maxResponseBytes) {
            requests.add(request);
            return new Response(200, responses.remove().getBytes(StandardCharsets.UTF_8));
        }

        private String requestBody(int index) {
            HttpRequest request = requests.get(index);
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
            return new String(completed.join(), StandardCharsets.UTF_8);
        }
    }
}
