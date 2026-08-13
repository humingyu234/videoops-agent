package org.dromara.aivideo.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.FileObject;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.FileObjectMapper;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.AiTaskProgressDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskResultPayloadDTO;
import org.dromara.aivideo.task.enums.AiTaskStage;
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
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderAssetMapper;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderMapper;
import org.dromara.aivideo.workflow.service.IRunningHubExecutionClient;
import org.dromara.aivideo.workflow.service.IWorkflowTaskExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Executes the single current RunningHub configuration and registers private output assets. */
@Service
@RequiredArgsConstructor
public class WorkflowTaskExecutionServiceImpl implements IWorkflowTaskExecutionService {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final long MAX_RESULT_BYTES = 100L * 1024 * 1024;
    private static final Set<String> MODES = Set.of("runninghub_ai_app", "runninghub_workflow");
    private static final List<String> RESULT_HOSTS = List.of("*.myqcloud.com", "*.runninghub.cn");

    private final IAiTaskTransactionService taskTransactions;
    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowExecutionConfigMapper configMapper;
    private final RunningHubAccountMapper accountMapper;
    private final WorkflowOrderMapper orderMapper;
    private final AssetFileMapper assetMapper;
    private final FileObjectMapper fileObjectMapper;
    private final WorkflowOrderAssetMapper orderAssetMapper;
    private final WorkflowTaskExecutionMapper executionMapper;
    private final IRunningHubExecutionClient runningHubClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Sleeper sleeper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public WorkflowTaskExecutionServiceImpl(IAiTaskTransactionService taskTransactions,
                                            WorkflowTemplateMapper templateMapper,
                                            WorkflowExecutionConfigMapper configMapper,
                                            RunningHubAccountMapper accountMapper,
                                            WorkflowOrderMapper orderMapper,
                                            AssetFileMapper assetMapper,
                                            FileObjectMapper fileObjectMapper,
                                            WorkflowOrderAssetMapper orderAssetMapper,
                                            WorkflowTaskExecutionMapper executionMapper,
                                            IRunningHubExecutionClient runningHubClient,
                                            PlatformTransactionManager transactionManager) {
        this(taskTransactions, templateMapper, configMapper, accountMapper, orderMapper, assetMapper,
            fileObjectMapper, orderAssetMapper, executionMapper, runningHubClient,
            JsonMapper.builder().build(), Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()),
            new TransactionTemplate(transactionManager));
    }

    @Override
    public AiTaskDispatchResultDTO dispatch(AiTaskLeaseDTO lease, WorkflowAiTaskPayloadDTO payload) {
        AiTaskLeaseDTO active = taskTransactions.beginAttempt(lease, clock.instant());
        if (active == null) {
            return outcome("lease_lost", lease);
        }
        try {
            RuntimeFacts facts = loadFacts(active, payload);
            active = progress(active, 10, AiTaskStage.PREPARING_INPUTS, "正在准备模板输入");
            WorkflowTaskExecution execution = executionMapper.selectByTaskId(facts.taskId());
            if (execution == null) {
                execution = newExecution(facts);
                if (executionMapper.insert(execution) != 1) {
                    throw failure("WORKFLOW_EXECUTION_FACT_FAILED", "模板执行事实创建失败");
                }
            }
            if ("finished".equals(execution.getSubmissionState())) {
                throw failure("WORKFLOW_ALREADY_FINISHED", "模板任务已结束");
            }
            if (!"accepted".equals(execution.getSubmissionState()) || empty(execution.getExternalTaskId())) {
                active = submit(active, facts, execution);
                if (empty(execution.getExternalTaskId())) {
                    throw failure("WORKFLOW_SUBMISSION_UNKNOWN", "模板任务提交结果未知");
                }
            }
            active = progress(active, 35, AiTaskStage.PROVIDER_PROCESSING, "模板正在运行");
            PollResult poll = poll(active, execution, facts.config());
            active = poll.lease();
            RunningHubExecutionDTOs.QueryResult query = poll.query();
            if (query.state() == RunningHubExecutionDTOs.QueryState.FAILED) {
                throw failure("WORKFLOW_PROVIDER_FAILED", safe(query.safeError(), "模板运行失败"));
            }
            active = progress(active, 90, AiTaskStage.PROCESSING_RESULTS, "正在保存模板结果");
            MaterializedOutputs materialized = materializeOutputs(active, facts, query.outputs());
            active = materialized.lease();
            AiTaskLeaseDTO completionLease = active;
            CompletionResult completion = transactionTemplate.execute(status -> {
                OutputRegistration registration = registerOutputs(completionLease, facts, materialized.outputs());
                StoredAssets stored = registration.storedAssets();
                AiTaskLeaseDTO registeredLease = registration.lease();
                executionMapper.markFinished(facts.taskId(), query.externalStatus(), null, null, stored.manifestJson());
                WorkflowAiTaskResultPayloadDTO result = new WorkflowAiTaskResultPayloadDTO(
                    List.of(), Map.of(
                        "resultCount", jsonMapper.getNodeFactory().numberNode(stored.assetIds().size())));
                boolean taskCompleted = taskTransactions.complete(registeredLease,
                    new AiTaskCompletionDTO(registeredLease.getExecutionId(), registeredLease.getLeaseToken(), null,
                        null, null, result, registeredLease.getRowVersion(), true, false), clock.instant());
                if (!taskCompleted) {
                    status.setRollbackOnly();
                }
                return new CompletionResult(registeredLease, taskCompleted);
            });
            if (completion == null) {
                throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果保存失败");
            }
            active = completion.lease();
            boolean completed = completion.completed();
            return outcome(completed ? "completed" : "lease_lost", active);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fail(active, "WORKFLOW_INTERRUPTED", "模板任务已中断");
        } catch (WorkflowFailure exception) {
            return fail(exception.lease == null ? active : exception.lease, exception.code, exception.getMessage());
        } catch (RuntimeException exception) {
            return fail(active, "WORKFLOW_EXECUTION_FAILED", "模板运行失败");
        }
    }

    private AiTaskLeaseDTO submit(AiTaskLeaseDTO active, RuntimeFacts facts, WorkflowTaskExecution execution) {
        if ("submitting".equals(execution.getSubmissionState()) || "unknown".equals(execution.getSubmissionState())) {
            throw failure("WORKFLOW_SUBMISSION_UNKNOWN", "模板任务提交结果未知");
        }
        LocalDateTime now = localNow();
        LocalDateTime deadline = now.plusSeconds(facts.config().getTimeoutSeconds());
        if (executionMapper.markSubmitting(facts.taskId(), facts.account().getAccountId(), facts.order().getOrderId(),
            facts.config().getExecutionMode(), now, deadline) != 1) {
            throw failure("WORKFLOW_SUBMISSION_CONFLICT", "模板任务提交状态已变化");
        }
        active = progress(active, 20, AiTaskStage.SUBMITTING_TO_PROVIDER, "正在提交模板任务");
        RunningHubExecutionDTOs.Submission submission;
        try {
            submission = runningHubClient.submit(new RunningHubExecutionDTOs.SubmitRequest(
                Long.toString(facts.account().getAccountId()), facts.config().getExecutionMode(), remoteId(facts.config()),
                facts.config().getInstanceType(), facts.config().getAccessPasswordCiphertext(),
                mappedInputs(facts.config(), facts.payload().inputs())));
        } catch (RuntimeException exception) {
            executionMapper.markFinished(facts.taskId(), "SUBMISSION_UNKNOWN", "WORKFLOW_SUBMISSION_UNKNOWN",
                "模板任务提交结果未知", null);
            throw failure("WORKFLOW_SUBMISSION_UNKNOWN", "模板任务提交结果未知");
        }
        if (submission == null || empty(submission.externalTaskId())
            || executionMapper.markAccepted(facts.taskId(), submission.externalTaskId(), localNow(),
            safe(submission.externalStatus(), "ACCEPTED")) != 1) {
            throw failure("WORKFLOW_SUBMISSION_UNKNOWN", "模板任务提交结果未知");
        }
        execution.setRunninghubAccountId(facts.account().getAccountId());
        execution.setExternalTaskId(submission.externalTaskId());
        execution.setExternalStatus(safe(submission.externalStatus(), "ACCEPTED"));
        execution.setSubmissionState("accepted");
        execution.setProviderDeadlineAt(deadline);
        return progress(active, 30, AiTaskStage.CONFIRMING_PROVIDER_ACCEPTANCE, "模板任务已受理");
    }

    private PollResult poll(AiTaskLeaseDTO active, WorkflowTaskExecution execution,
                            WorkflowExecutionConfig config) throws InterruptedException {
        Instant deadline = execution.getProviderDeadlineAt() == null
            ? clock.instant().plusSeconds(config.getTimeoutSeconds())
            : execution.getProviderDeadlineAt().toInstant(ZoneOffset.UTC);
        int pollCount = execution.getPollCount() == null ? 0 : execution.getPollCount();
        while (clock.instant().isBefore(deadline)) {
            if (taskTransactions.cancellationRequested(active)) {
                throw failure("WORKFLOW_CANCELLED", "模板任务已取消", active);
            }
            RunningHubExecutionDTOs.QueryResult query = runningHubClient.query(
                Long.toString(execution.getRunninghubAccountId()), execution.getExternalTaskId());
            pollCount++;
            executionMapper.recordPoll(Long.parseLong(active.getTaskId()), localNow(),
                safe(query.externalStatus(), "UNKNOWN"), pollCount, null);
            if (query.state() != RunningHubExecutionDTOs.QueryState.PENDING) {
                return new PollResult(query, active);
            }
            active = taskTransactions.renew(active, clock.instant());
            if (active == null) {
                throw failure("WORKFLOW_LEASE_LOST", "模板任务租约已失效");
            }
            sleeper.sleep(POLL_INTERVAL);
        }
        throw failure("WORKFLOW_PROVIDER_TIMEOUT", "模板运行超时", active);
    }

    private MaterializedOutputs materializeOutputs(AiTaskLeaseDTO lease, RuntimeFacts facts,
                                                   List<RunningHubExecutionDTOs.Output> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果为空");
        }
        List<RunningHubExecutionDTOs.StoredOutput> storedOutputs = new ArrayList<>();
        for (RunningHubExecutionDTOs.Output output : outputs) {
            lease = renewLease(lease);
            RunningHubExecutionDTOs.StoredOutput stored = runningHubClient.materializeOutput(output,
                new RunningHubExecutionDTOs.OutputStoragePolicy(MAX_RESULT_BYTES, RESULT_HOSTS),
                facts.order().getOrderId());
            validateStoredOutput(stored);
            storedOutputs.add(stored);
        }
        return new MaterializedOutputs(renewLease(lease), List.copyOf(storedOutputs));
    }

    protected OutputRegistration registerOutputs(AiTaskLeaseDTO lease, RuntimeFacts facts,
                                                 List<RunningHubExecutionDTOs.StoredOutput> storedOutputs) {
        List<String> assetIds = new ArrayList<>();
        ArrayNode manifest = jsonMapper.createArrayNode();
        int index = 0;
        for (RunningHubExecutionDTOs.StoredOutput stored : storedOutputs) {
            lease = renewLease(lease);
            String type = empty(stored.fileFormat()) ? "bin" : stored.fileFormat();
            FileObject file = new FileObject();
            file.setTenantId(facts.order().getTenantId());
            file.setWorkspaceId(facts.order().getWorkspaceId());
            file.setOwnerUserId(facts.order().getOwnerUserId());
            file.setObjectKey(stored.objectKey()); file.setContentType(stored.contentType());
            file.setSizeBytes(stored.sizeBytes()); file.setSha256(stored.sha256()); file.setStatus("active");
            if (fileObjectMapper.insert(file) != 1 || file.getFileId() == null) {
                throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果登记失败");
            }
            AssetFile asset = new AssetFile();
            asset.setFileId(file.getFileId()); asset.setTenantId(facts.order().getTenantId());
            asset.setWorkspaceId(facts.order().getWorkspaceId()); asset.setOwnerId(facts.order().getOwnerUserId());
            asset.setCategory("workflow_output"); asset.setObjectKey(stored.objectKey());
            asset.setOriginalName(stored.originalName()); asset.setContentType(stored.contentType());
            asset.setFileFormat(stored.fileFormat()); asset.setWidth(0); asset.setHeight(0);
            asset.setFileSize(stored.sizeBytes()); asset.setStatus("ready");
            if (assetMapper.insert(asset) != 1 || asset.getAssetId() == null) {
                throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果资产创建失败");
            }
            WorkflowOrderAsset reference = new WorkflowOrderAsset();
            reference.setTenantId(facts.order().getTenantId()); reference.setWorkspaceId(facts.order().getWorkspaceId());
            reference.setOwnerUserId(facts.order().getOwnerUserId()); reference.setOrderId(facts.order().getOrderId());
            reference.setAssetId(asset.getAssetId()); reference.setAssetRole("output");
            reference.setSortOrder(index); reference.setIsPrimary(index == 0); index++;
            if (orderAssetMapper.insert(reference) != 1) {
                throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果关联失败");
            }
            assetIds.add(Long.toString(asset.getAssetId()));
            ObjectNode item = manifest.addObject();
            item.put("assetId", asset.getAssetId()); item.put("outputType", type);
            item.put("sizeBytes", stored.sizeBytes()); item.put("sha256", stored.sha256());
        }
        if (assetIds.isEmpty()) {
            throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果为空");
        }
        return new OutputRegistration(lease,
            new StoredAssets(List.copyOf(assetIds), jsonMapper.writeValueAsString(manifest)));
    }

    private AiTaskLeaseDTO renewLease(AiTaskLeaseDTO lease) {
        AiTaskLeaseDTO renewed = taskTransactions.renew(lease, clock.instant());
        if (renewed == null) {
            throw failure("WORKFLOW_LEASE_LOST", "模板任务租约已失效");
        }
        return renewed;
    }

    private void validateStoredOutput(RunningHubExecutionDTOs.StoredOutput stored) {
        if (stored == null || stored.sizeBytes() <= 0 || stored.sizeBytes() > MAX_RESULT_BYTES
            || empty(stored.objectKey()) || empty(stored.sha256())) {
            throw failure("WORKFLOW_OUTPUT_INVALID", "模板结果保存失败");
        }
    }

    private RuntimeFacts loadFacts(AiTaskLeaseDTO lease, WorkflowAiTaskPayloadDTO payload) {
        long taskId = parseId(lease.getTaskId());
        long orderId = parseId(payload.orderId());
        long templateId = parseId(payload.templateId());
        WorkflowOrder order = orderMapper.selectById(orderId);
        WorkflowTemplate template = templateMapper.selectCatalogTemplate(0L, templateId);
        WorkflowExecutionConfig config = configMapper.selectCurrent(0L, templateId);
        RunningHubAccount account = config == null ? null
            : accountMapper.selectCatalogAccount(0L, config.getRunninghubAccountId());
        if (order == null || !Objects.equals(order.getTaskId(), taskId)
            || !Objects.equals(order.getTemplateId(), templateId)
            || !Objects.equals(order.getSchemaHash(), payload.schemaHash())
            || template == null || !"enabled".equals(template.getStatus())
            || !Objects.equals(template.getSchemaHash(), payload.schemaHash())
            || config == null || !Boolean.TRUE.equals(config.getEnabled()) || !MODES.contains(config.getExecutionMode())
            || config.getTimeoutSeconds() == null || config.getTimeoutSeconds() <= 0
            || account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            throw failure("WORKFLOW_CONFIG_UNAVAILABLE", "模板执行配置不可用");
        }
        return new RuntimeFacts(taskId, order, template, config, account, payload);
    }

    private WorkflowTaskExecution newExecution(RuntimeFacts facts) {
        WorkflowTaskExecution execution = new WorkflowTaskExecution();
        execution.setWorkflowTaskExecutionId(IdWorker.getId()); execution.setTaskId(facts.taskId());
        execution.setTenantId(facts.order().getTenantId()); execution.setRunninghubAccountId(facts.account().getAccountId());
        execution.setOrderId(facts.order().getOrderId()); execution.setResourceType("workflow_order");
        execution.setExecutionMode(facts.config().getExecutionMode()); execution.setSubmissionState("not_started");
        execution.setPollCount(0); execution.setCostReconciliationStatus("not_reported");
        return execution;
    }

    private List<RunningHubExecutionDTOs.NodeInput> mappedInputs(WorkflowExecutionConfig config,
                                                                  Map<String, JsonNode> inputs) {
        JsonNode mapping;
        try {
            mapping = jsonMapper.readTree(config.getInputMappingJson());
        } catch (RuntimeException exception) {
            throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板输入映射无效");
        }
        if (mapping == null || !mapping.isObject()) {
            throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板输入映射无效");
        }
        List<RunningHubExecutionDTOs.NodeInput> result = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : mapping.properties()) {
            JsonNode item = entry.getValue();
            String inputKey = text(item, "inputKey", entry.getKey());
            JsonNode value = inputs.get(inputKey);
            if (value == null || value.isNull()) {
                if (item.path("required").asBoolean(false)) {
                    throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板输入缺失");
                }
                continue;
            }
            String transform = text(item, "valueTransform", "identity");
            if ("runninghub_file_name".equals(transform)) {
                value = runningHubFileValue(value);
            } else if ("trim".equals(transform) && value.isTextual()) {
                value = jsonMapper.getNodeFactory().textNode(value.textValue().strip());
            } else if (!"identity".equals(transform)) {
                throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板输入转换无效");
            }
            result.add(new RunningHubExecutionDTOs.NodeInput(requiredText(item, "nodeId"),
                requiredText(item, "fieldName"), value));
        }
        return List.copyOf(result);
    }

    private JsonNode runningHubFileValue(JsonNode value) {
        if (!value.isArray() || value.isEmpty() || !value.get(0).isObject()) {
            throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板文件输入无效");
        }
        long assetId = parseId(value.get(0).path("assetId").asText());
        AssetFile asset = assetMapper.selectById(assetId);
        if (asset == null || !"workflow_input".equals(asset.getCategory()) || !"ready".equals(asset.getStatus())
            || empty(asset.getObjectKey())) {
            throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板文件输入不可用");
        }
        return jsonMapper.getNodeFactory().textNode(asset.getObjectKey());
    }

    private AiTaskLeaseDTO progress(AiTaskLeaseDTO lease, int percent, AiTaskStage stage, String message) {
        AiTaskLeaseDTO next = taskTransactions.reportProgress(lease, new AiTaskProgressDTO(lease.getExecutionId(),
            lease.getLeaseToken(), lease.getRowVersion(), percent, stage, message), clock.instant());
        if (next == null) {
            throw failure("WORKFLOW_LEASE_LOST", "模板任务租约已失效");
        }
        return next;
    }

    private AiTaskDispatchResultDTO fail(AiTaskLeaseDTO lease, String code, String message) {
        executionMapper.markFinished(parseId(lease.getTaskId()), "FAILED", code, safe(message, "模板运行失败"), null);
        boolean completed = taskTransactions.complete(lease, new AiTaskCompletionDTO(lease.getExecutionId(),
            lease.getLeaseToken(), null, code, safe(message, "模板运行失败"), null,
            lease.getRowVersion(), false, false), clock.instant());
        return outcome(completed ? "failed" : "lease_lost", lease);
    }

    @Override
    public int recoverExpired(Instant now, int limit) {
        return 0;
    }

    private AiTaskDispatchResultDTO outcome(String value, AiTaskLeaseDTO lease) {
        return new AiTaskDispatchResultDTO(value, lease.getTaskId(), lease.getExecutionId());
    }

    private LocalDateTime localNow() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private String remoteId(WorkflowExecutionConfig config) {
        return "runninghub_ai_app".equals(config.getExecutionMode()) ? config.getWebappId() : config.getWorkflowId();
    }
    private String requiredText(JsonNode node, String key) {
        String value = text(node, key, null);
        if (empty(value)) throw failure("WORKFLOW_INPUT_MAPPING_INVALID", "模板输入映射无效");
        return value;
    }
    private String text(JsonNode node, String key, String fallback) {
        return node != null && node.path(key).isTextual() ? node.path(key).textValue() : fallback;
    }
    private long parseId(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException exception) { throw failure("WORKFLOW_FACT_INVALID", "模板任务事实无效"); }
    }
    private boolean empty(String value) { return value == null || value.isBlank(); }
    private String safe(String value, String fallback) {
        String result = empty(value) ? fallback : value.strip();
        return result.codePointCount(0, result.length()) > 200
            ? result.substring(0, result.offsetByCodePoints(0, 200)) : result;
    }
    private WorkflowFailure failure(String code, String message) { return new WorkflowFailure(code, message, null); }
    private WorkflowFailure failure(String code, String message, AiTaskLeaseDTO lease) {
        return new WorkflowFailure(code, message, lease);
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
    private record RuntimeFacts(long taskId, WorkflowOrder order, WorkflowTemplate template,
                                WorkflowExecutionConfig config, RunningHubAccount account,
                                WorkflowAiTaskPayloadDTO payload) { }
    private record PollResult(RunningHubExecutionDTOs.QueryResult query, AiTaskLeaseDTO lease) { }
    private record MaterializedOutputs(AiTaskLeaseDTO lease,
                                       List<RunningHubExecutionDTOs.StoredOutput> outputs) { }
    private record CompletionResult(AiTaskLeaseDTO lease, boolean completed) { }
    private record OutputRegistration(AiTaskLeaseDTO lease, StoredAssets storedAssets) { }
    private record StoredAssets(List<String> assetIds, String manifestJson) { }
    private static final class WorkflowFailure extends RuntimeException {
        private final String code;
        private final AiTaskLeaseDTO lease;
        private WorkflowFailure(String code, String message, AiTaskLeaseDTO lease) {
            super(message); this.code = code; this.lease = lease;
        }
    }
}
