package org.dromara.aivideo.workflow.service.impl;

import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.FileObjectMapper;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.FileObject;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskResultPayloadDTO;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.workflow.domain.RunningHubAccount;
import org.dromara.aivideo.workflow.domain.WorkflowExecutionConfig;
import org.dromara.aivideo.workflow.domain.WorkflowTaskExecution;
import org.dromara.aivideo.workflow.domain.WorkflowTemplate;
import org.dromara.aivideo.workflow.dto.RunningHubExecutionDTOs;
import org.dromara.aivideo.workflow.mapper.RunningHubAccountMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowExecutionConfigMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowTaskExecutionMapper;
import org.dromara.aivideo.workflow.mapper.WorkflowTemplateMapper;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrder;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrderAsset;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderMapper;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderAssetMapper;
import org.dromara.aivideo.workflow.service.IRunningHubExecutionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowTaskExecutionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-12T01:00:00Z");
    private static final String SCHEMA_HASH = "sha256:" + "a".repeat(64);

    private IAiTaskTransactionService taskTransactions;
    private WorkflowTemplateMapper templateMapper;
    private WorkflowExecutionConfigMapper configMapper;
    private RunningHubAccountMapper accountMapper;
    private WorkflowOrderMapper orderMapper;
    private AssetFileMapper assetMapper;
    private FileObjectMapper fileObjectMapper;
    private WorkflowOrderAssetMapper orderAssetMapper;
    private WorkflowTaskExecutionMapper executionMapper;
    private IRunningHubExecutionClient runningHubClient;
    private WorkflowTaskExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        taskTransactions = mock(IAiTaskTransactionService.class);
        templateMapper = mock(WorkflowTemplateMapper.class);
        configMapper = mock(WorkflowExecutionConfigMapper.class);
        accountMapper = mock(RunningHubAccountMapper.class);
        orderMapper = mock(WorkflowOrderMapper.class);
        assetMapper = mock(AssetFileMapper.class);
        fileObjectMapper = mock(FileObjectMapper.class);
        orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        executionMapper = mock(WorkflowTaskExecutionMapper.class);
        runningHubClient = mock(IRunningHubExecutionClient.class);
        service = new WorkflowTaskExecutionServiceImpl(
            taskTransactions, templateMapper, configMapper, accountMapper, orderMapper, assetMapper,
            fileObjectMapper, orderAssetMapper, executionMapper, runningHubClient, JsonMapper.builder().build(),
            Clock.fixed(NOW, ZoneOffset.UTC), duration -> { }, immediateTransactions());
        when(taskTransactions.beginAttempt(any(), any())).thenAnswer(invocation ->
            copyLease(invocation.getArgument(0), 1));
        when(taskTransactions.renew(any(), any())).thenAnswer(invocation ->
            copyLease(invocation.getArgument(0), 1));
        when(taskTransactions.reportProgress(any(), any(), any())).thenAnswer(invocation ->
            copyLease(invocation.getArgument(0), 1));
        when(taskTransactions.complete(any(), any(), any())).thenReturn(true);
        when(taskTransactions.cancellationRequested(any())).thenReturn(false);
        when(executionMapper.insert(any(WorkflowTaskExecution.class))).thenReturn(1);
        when(executionMapper.markSubmitting(anyLong(), anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(executionMapper.markAccepted(anyLong(), any(), any(), any())).thenReturn(1);
        when(executionMapper.recordPoll(anyLong(), any(), any(), anyInt(), any())).thenReturn(1);
        when(executionMapper.markFinished(anyLong(), any(), any(), any(), any())).thenReturn(1);
        AtomicLong fileIdSequence = new AtomicLong(8100L);
        when(fileObjectMapper.insert(any(FileObject.class))).thenAnswer(invocation -> {
            FileObject file = invocation.getArgument(0);
            file.setFileId(fileIdSequence.incrementAndGet());
            return 1;
        });
        AtomicLong assetIdSequence = new AtomicLong(8200L);
        when(assetMapper.insert(any(AssetFile.class))).thenAnswer(invocation -> {
            AssetFile asset = invocation.getArgument(0);
            asset.setAssetId(assetIdSequence.incrementAndGet());
            return 1;
        });
        when(orderAssetMapper.insert(any(WorkflowOrderAsset.class))).thenReturn(1);
        stubRuntime();
    }

    @Test
    void submitsMappedAiAppInputsOncePollsV2AndCompletesTheUnifiedTask() {
        when(executionMapper.selectByTaskId(701L)).thenReturn(null);
        when(runningHubClient.submit(any())).thenReturn(
            new RunningHubExecutionDTOs.Submission("9001", "RUNNING"));
        when(runningHubClient.query("301", "9001")).thenReturn(
            new RunningHubExecutionDTOs.QueryResult(
                RunningHubExecutionDTOs.QueryState.SUCCESS, "SUCCESS", null,
                List.of(new RunningHubExecutionDTOs.Output(
                    "https://rh.example.com/result/output.png", "png"))));
        when(runningHubClient.materializeOutput(any(), any(), anyLong())).thenReturn(
            new RunningHubExecutionDTOs.StoredOutput(
                "workflow-results/501/output.png", "output.png", "image/png", "png", 128L,
                "b".repeat(64)));

        var result = service.dispatch(lease(), payload());

        assertThat(result.outcome()).isEqualTo("completed");
        ArgumentCaptor<RunningHubExecutionDTOs.SubmitRequest> requestCaptor =
            ArgumentCaptor.forClass(RunningHubExecutionDTOs.SubmitRequest.class);
        verify(runningHubClient).submit(requestCaptor.capture());
        RunningHubExecutionDTOs.SubmitRequest request = requestCaptor.getValue();
        assertThat(request.accountId()).isEqualTo("301");
        assertThat(request.executionMode()).isEqualTo("runninghub_ai_app");
        assertThat(request.remoteId()).isEqualTo("2084534713108226049");
        assertThat(request.nodeInfoList()).containsExactly(
            new RunningHubExecutionDTOs.NodeInput("53", "text",
                JsonMapper.builder().build().getNodeFactory().textNode("漂亮的20岁年轻亚洲女网红")));

        ArgumentCaptor<AiTaskCompletionDTO> completionCaptor = ArgumentCaptor.forClass(AiTaskCompletionDTO.class);
        verify(taskTransactions).complete(any(), completionCaptor.capture(), any());
        AiTaskCompletionDTO completion = completionCaptor.getValue();
        assertThat(completion.isSuccess()).isTrue();
        assertThat(completion.getResultPayload()).isInstanceOf(WorkflowAiTaskResultPayloadDTO.class);
        WorkflowAiTaskResultPayloadDTO payload = (WorkflowAiTaskResultPayloadDTO) completion.getResultPayload();
        assertThat(payload.resultAssetIds()).isEmpty();
        assertThat(payload.outputFacts().get("resultCount").intValue()).isEqualTo(1);
        assertThat(payload.outputFacts().toString()).doesNotContain("https://", "rh.example.com", "9001");
        verify(executionMapper).markAccepted(eq(701L), eq("9001"), any(), eq("RUNNING"));
        verify(orderAssetMapper).insert(any(WorkflowOrderAsset.class));
    }

    @Test
    void resumesAnAcceptedProviderTaskWithoutRepeatingTheCreatePost() {
        WorkflowTaskExecution existing = new WorkflowTaskExecution();
        existing.setWorkflowTaskExecutionId(801L);
        existing.setTaskId(701L);
        existing.setRunninghubAccountId(301L);
        existing.setExecutionMode("runninghub_ai_app");
        existing.setExternalTaskId("9001");
        existing.setSubmissionState("accepted");
        existing.setProviderDeadlineAt(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC));
        when(executionMapper.selectByTaskId(701L)).thenReturn(existing);
        when(runningHubClient.query("301", "9001")).thenReturn(
            new RunningHubExecutionDTOs.QueryResult(
                RunningHubExecutionDTOs.QueryState.SUCCESS, "SUCCESS", null,
                List.of(new RunningHubExecutionDTOs.Output(
                    "https://rh.example.com/result/output.png", "png"))));
        when(runningHubClient.materializeOutput(any(), any(), anyLong())).thenReturn(
            new RunningHubExecutionDTOs.StoredOutput(
                "workflow-results/501/output.png", "output.png", "image/png", "png", 128L,
                "b".repeat(64)));

        var result = service.dispatch(lease(), payload());

        assertThat(result.outcome()).isEqualTo("completed");
        verify(runningHubClient, never()).submit(any());
        verify(runningHubClient).query("301", "9001");
    }

    @Test
    void completesTimeoutWithTheLeaseRenewedDuringPendingProviderPolling() {
        AtomicReference<Instant> current = new AtomicReference<>(NOW);
        Clock advancingClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return current.get(); }
        };
        service = new WorkflowTaskExecutionServiceImpl(
            taskTransactions, templateMapper, configMapper, accountMapper, orderMapper, assetMapper,
            fileObjectMapper, orderAssetMapper, executionMapper, runningHubClient, JsonMapper.builder().build(),
            advancingClock, duration -> current.set(NOW.plusSeconds(1)), immediateTransactions());

        WorkflowTaskExecution existing = new WorkflowTaskExecution();
        existing.setWorkflowTaskExecutionId(801L);
        existing.setTaskId(701L);
        existing.setRunninghubAccountId(301L);
        existing.setExecutionMode("runninghub_ai_app");
        existing.setExternalTaskId("9001");
        existing.setSubmissionState("accepted");
        existing.setProviderDeadlineAt(java.time.LocalDateTime.ofInstant(NOW.plusSeconds(1), ZoneOffset.UTC));
        when(executionMapper.selectByTaskId(701L)).thenReturn(existing);
        when(runningHubClient.query("301", "9001")).thenReturn(
            new RunningHubExecutionDTOs.QueryResult(
                RunningHubExecutionDTOs.QueryState.PENDING, "RUNNING", null, List.of()));

        var result = service.dispatch(lease(), payload());

        assertThat(result.outcome()).isEqualTo("failed");
        ArgumentCaptor<AiTaskLeaseDTO> leaseCaptor = ArgumentCaptor.forClass(AiTaskLeaseDTO.class);
        verify(taskTransactions).complete(leaseCaptor.capture(), any(), any());
        assertThat(leaseCaptor.getValue().getRowVersion()).isEqualTo(4);
        verify(executionMapper).markFinished(eq(701L), eq("FAILED"), eq("WORKFLOW_PROVIDER_TIMEOUT"),
            eq("模板运行超时"), eq(null));
    }

    @Test
    void acceptsMixedProviderOutputTypesRegardlessOfLegacyOutputPolicy() {
        WorkflowExecutionConfig legacyConfig = configMapper.selectCurrent(0L, 401L);
        legacyConfig.setOutputPolicyJson("""
            {"allowedOutputTypes":["jpg"],"maxResultCount":1,
             "maxBytesPerResult":104857600}
            """);
        when(executionMapper.selectByTaskId(701L)).thenReturn(null);
        when(runningHubClient.submit(any())).thenReturn(
            new RunningHubExecutionDTOs.Submission("9001", "RUNNING"));
        when(runningHubClient.query("301", "9001")).thenReturn(
            new RunningHubExecutionDTOs.QueryResult(
                RunningHubExecutionDTOs.QueryState.SUCCESS, "SUCCESS", null,
                List.of(
                    new RunningHubExecutionDTOs.Output("https://rh.example.com/result/first.png", "png"),
                    new RunningHubExecutionDTOs.Output("https://rh.example.com/result/second.mp4", "mp4"),
                    new RunningHubExecutionDTOs.Output("https://rh.example.com/result/third.wav", "wav"))));
        when(runningHubClient.materializeOutput(any(), any(), anyLong())).thenAnswer(invocation -> {
            RunningHubExecutionDTOs.Output output = invocation.getArgument(0);
            return new RunningHubExecutionDTOs.StoredOutput(
                "workflow-results/501/output." + output.outputType(), "output." + output.outputType(),
                "application/octet-stream", output.outputType(), 128L, "b".repeat(64));
        });

        var result = service.dispatch(lease(), payload());

        assertThat(result.outcome()).isEqualTo("completed");
        ArgumentCaptor<AiTaskCompletionDTO> completionCaptor = ArgumentCaptor.forClass(AiTaskCompletionDTO.class);
        verify(taskTransactions).complete(any(), completionCaptor.capture(), any());
        WorkflowAiTaskResultPayloadDTO completionPayload =
            (WorkflowAiTaskResultPayloadDTO) completionCaptor.getValue().getResultPayload();
        assertThat(completionPayload.resultAssetIds()).isEmpty();
        assertThat(completionPayload.outputFacts().get("resultCount").intValue()).isEqualTo(3);
        ArgumentCaptor<String> manifestCaptor = ArgumentCaptor.forClass(String.class);
        verify(executionMapper).markFinished(eq(701L), eq("SUCCESS"), eq(null), eq(null), manifestCaptor.capture());
        assertThat(manifestCaptor.getValue()).contains("png", "mp4", "wav");
        verify(runningHubClient, times(3)).materializeOutput(any(), any(), eq(501L));
        verify(orderAssetMapper, times(3)).insert(any(WorkflowOrderAsset.class));
    }

    @Test
    void acceptsEveryProviderOutputWithoutAResultCountLimit() {
        when(executionMapper.selectByTaskId(701L)).thenReturn(null);
        when(runningHubClient.submit(any())).thenReturn(
            new RunningHubExecutionDTOs.Submission("9001", "RUNNING"));
        List<RunningHubExecutionDTOs.Output> outputs = IntStream.range(0, 2050)
            .mapToObj(index -> new RunningHubExecutionDTOs.Output(
                "https://rh.example.com/result/" + index + ".png", "png"))
            .toList();
        when(runningHubClient.query("301", "9001")).thenReturn(
            new RunningHubExecutionDTOs.QueryResult(
                RunningHubExecutionDTOs.QueryState.SUCCESS, "SUCCESS", null, outputs));
        when(runningHubClient.materializeOutput(any(), any(), anyLong())).thenReturn(
            new RunningHubExecutionDTOs.StoredOutput(
                "workflow-results/501/output.png", "output.png", "image/png", "png", 128L,
                "b".repeat(64)));

        var result = service.dispatch(lease(), payload());

        assertThat(result.outcome()).isEqualTo("completed");
        ArgumentCaptor<AiTaskCompletionDTO> completionCaptor = ArgumentCaptor.forClass(AiTaskCompletionDTO.class);
        verify(taskTransactions).complete(any(), completionCaptor.capture(), any());
        WorkflowAiTaskResultPayloadDTO completionPayload =
            (WorkflowAiTaskResultPayloadDTO) completionCaptor.getValue().getResultPayload();
        assertThat(completionPayload.resultAssetIds()).isEmpty();
        assertThat(completionPayload.outputFacts().get("resultCount").intValue()).isEqualTo(2050);
        verify(runningHubClient, times(2050)).materializeOutput(any(), any(), eq(501L));
        verify(orderAssetMapper, times(2050)).insert(any(WorkflowOrderAsset.class));
    }

    private void stubRuntime() {
        WorkflowTemplate template = new WorkflowTemplate();
        template.setTemplateId(401L);
        template.setTenantId(0L);
        template.setSchemaHash(SCHEMA_HASH);
        template.setStatus("enabled");
        template.setRowRevision(4L);
        when(templateMapper.selectCatalogTemplate(0L, 401L)).thenReturn(template);

        WorkflowExecutionConfig config = new WorkflowExecutionConfig();
        config.setTenantId(0L);
        config.setTemplateId(401L);
        config.setRunninghubAccountId(301L);
        config.setExecutionMode("runninghub_ai_app");
        config.setWebappId("2084534713108226049");
        config.setInputMappingJson("""
            {"text":{"inputKey":"text","nodeId":"53","fieldName":"text",
             "valueType":"string","valueTransform":"identity","required":true,
             "remoteValueType":"STRING"}}
            """);
        config.setOutputPolicyJson("{}");
        config.setTimeoutSeconds(300);
        config.setEnabled(true);
        config.setRowRevision(5L);
        when(configMapper.selectCurrent(0L, 401L)).thenReturn(config);

        RunningHubAccount account = new RunningHubAccount();
        account.setAccountId(301L);
        account.setTenantId(0L);
        account.setEnabled(true);
        account.setApiKeyCiphertext("v1:encrypted");
        account.setRowRevision(6L);
        when(accountMapper.selectCatalogAccount(0L, 301L)).thenReturn(account);

        WorkflowOrder order = new WorkflowOrder();
        order.setOrderId(501L);
        order.setTaskId(701L);
        order.setTenantId(11L);
        order.setWorkspaceId("workspace-a");
        order.setOwnerUserId(601L);
        order.setTemplateId(401L);
        order.setSchemaHash(SCHEMA_HASH);
        when(orderMapper.selectById(501L)).thenReturn(order);
    }

    private WorkflowAiTaskPayloadDTO payload() {
        JsonNode text = JsonMapper.builder().build().getNodeFactory()
            .textNode("漂亮的20岁年轻亚洲女网红");
        return new WorkflowAiTaskPayloadDTO("501", "401", SCHEMA_HASH, Map.of("text", text));
    }

    private AiTaskLeaseDTO lease() {
        return new AiTaskLeaseDTO("701", "702", null, "lease-token", "workflow-worker",
            "app_user", "601", null, 1, 0, 0);
    }

    private AiTaskLeaseDTO copyLease(AiTaskLeaseDTO source, int versionIncrement) {
        return new AiTaskLeaseDTO(source.getTaskId(), source.getExecutionId(),
            source.getAttemptId() == null ? "703" : source.getAttemptId(), source.getLeaseToken(),
            source.getWorkerId(), source.getActorType(), source.getActorId(), source.getInputVersionId(),
            source.getExecutionNo(), source.getAttemptNo() == 0 ? 1 : source.getAttemptNo(),
            source.getRowVersion() + versionIncrement);
    }

    private TransactionTemplate immediateTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });
        return template;
    }
}
