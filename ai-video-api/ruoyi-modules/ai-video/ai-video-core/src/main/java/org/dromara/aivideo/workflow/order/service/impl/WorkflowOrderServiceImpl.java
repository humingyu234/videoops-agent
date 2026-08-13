package org.dromara.aivideo.workflow.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.UploadSession;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.UploadSessionMapper;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrder;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrderAsset;
import org.dromara.aivideo.workflow.order.dto.CreateWorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDetailDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderOwnerDTO;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderAssetMapper;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderMapper;
import org.dromara.aivideo.workflow.order.service.IWorkflowOrderService;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Creates an owned discovery workflow order and its single durable task in one transaction. */
@Service
@RequiredArgsConstructor
public class WorkflowOrderServiceImpl implements IWorkflowOrderService {

    private static final int INPUT_INVALID = 46505;
    private static final int INPUT_ASSET_INVALID = 46506;
    private static final int IDEMPOTENCY_CONFLICT = 46507;
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Map<String, Set<String>> TASK_STAGE_MATRIX = Map.of(
        "pending", Set.of("waiting_for_dispatch"),
        "queued", Set.of("waiting_for_dispatch"),
        "running", Set.of("preparing_inputs", "submitting_to_provider", "confirming_provider_acceptance",
            "provider_processing", "processing_results"),
        "success", Set.of("completed"),
        "failed", Set.of("failed"),
        "cancelled", Set.of("cancelled"));

    private final WorkflowOrderMapper orderMapper;
    private final WorkflowOrderAssetMapper orderAssetMapper;
    private final AssetFileMapper assetMapper;
    private final UploadSessionMapper uploadSessionMapper;
    private final IWorkflowTemplateService templateService;
    private final IAiTaskTransactionService taskService;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowOrderDTO create(WorkflowOrderOwnerDTO owner, CreateWorkflowOrderDTO command) {
        validateCommand(command);
        WorkflowTemplateDTOs.CreationConfig config = templateService.queryCreationConfig(command.templateId());
        if (config == null || !command.schemaHash().equals(config.schemaHash())) {
            throw new ServiceException("模板表单已更新，请刷新后重新提交", 46502);
        }
        Map<String, JsonNode> inputs = canonicalInputs(config, command.inputs());
        String requestHash = sha256(jsonMapper.writeValueAsString(Map.of(
            "templateId", command.templateId(), "schemaHash", command.schemaHash(), "inputs", inputs)));
        WorkflowOrder existing = findByIdempotency(owner, command.idempotencyKey());
        if (existing != null) {
            if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                throw new ServiceException("幂等键已用于不同的工作流订单", IDEMPOTENCY_CONFLICT);
            }
            return toDto(existing);
        }

        WorkflowTemplateDTOs.PublicDetail detail = templateService.queryVisibleDetail(command.templateId());
        long orderId = IdWorker.getId();
        WorkflowOrder order = new WorkflowOrder();
        order.setOrderId(orderId);
        order.setTenantId(owner.tenantId());
        order.setWorkspaceId(owner.workspaceId());
        order.setOwnerUserId(owner.ownerUserId());
        order.setOrderNo("WF" + orderId);
        order.setTemplateId(parseId(command.templateId()));
        order.setIdempotencyKey(command.idempotencyKey());
        order.setSchemaHash(command.schemaHash());
        order.setInputPayloadJson(jsonMapper.writeValueAsString(inputs));
        order.setRequestHash(requestHash);
        order.setBillingMode("free");
        order.setTemplateTitleSnapshot(detail == null ? "" : detail.title());
        if (detail != null && detail.cover() != null) {
            order.setTemplateCoverSnapshotJson(jsonMapper.writeValueAsString(detail.cover()));
        }
        order.setInputDisplaySnapshotJson(jsonMapper.writeValueAsString(inputs));
        if (orderMapper.insert(order) != 1) {
            throw new ServiceException("工作流订单创建失败", INPUT_INVALID);
        }
        bindInputAssets(order, owner, config, inputs);
        AiTaskDTO task = taskService.createWorkflowTask(new AiTaskActorDTO("app_user", owner.ownerUserId(),
            owner.ownerUserId()), new CreateWorkflowAiTaskDTO(AiTaskType.WORKFLOW_TEMPLATE_GENERATE,
            AiTaskResourceType.WORKFLOW_ORDER, Long.toString(orderId), command.idempotencyKey(), requestHash,
            new WorkflowAiTaskPayloadDTO(Long.toString(orderId), command.templateId(), command.schemaHash(), inputs)));
        order.setTaskId(parseId(task.taskId()));
        if (orderMapper.update(null, new LambdaUpdateWrapper<WorkflowOrder>()
            .eq(WorkflowOrder::getOrderId, orderId).isNull(WorkflowOrder::getTaskId)
            .set(WorkflowOrder::getTaskId, order.getTaskId())) != 1) {
            throw new ServiceException("工作流任务关联失败", INPUT_INVALID);
        }
        return toDto(order);
    }

    @Override
    public WorkflowOrderDetailDTO queryOwnedDetail(WorkflowOrderOwnerDTO owner, String orderId) {
        long id = parseId(orderId);
        WorkflowOrder order = orderMapper.selectOne(new LambdaQueryWrapper<WorkflowOrder>()
            .eq(WorkflowOrder::getOrderId, id)
            .eq(WorkflowOrder::getTenantId, owner.tenantId())
            .eq(WorkflowOrder::getWorkspaceId, owner.workspaceId())
            .eq(WorkflowOrder::getOwnerUserId, owner.ownerUserId()));
        if (order == null || order.getTaskId() == null) {
            throw new ServiceException("工作流订单不存在");
        }
        AiTaskDTO task = taskService.getOwned(
            new AiTaskAccessScopeDTO(owner.tenantId(), owner.ownerUserId(), owner.workspaceId()),
            Long.toString(order.getTaskId()));
        requireMatchingTask(order, task);
        List<WorkflowOrderAsset> references = orderAssetMapper.selectList(
            new LambdaQueryWrapper<WorkflowOrderAsset>()
                .eq(WorkflowOrderAsset::getOrderId, order.getOrderId())
                .eq(WorkflowOrderAsset::getTenantId, owner.tenantId())
                .eq(WorkflowOrderAsset::getWorkspaceId, owner.workspaceId())
                .eq(WorkflowOrderAsset::getOwnerUserId, owner.ownerUserId())
                .orderByAsc(WorkflowOrderAsset::getAssetRole)
                .orderByAsc(WorkflowOrderAsset::getInputKey)
                .orderByAsc(WorkflowOrderAsset::getSortOrder));
        Map<Long, AssetFile> assets = loadOwnedAssets(owner, references);
        WorkflowTemplateDTOs.CreationConfig currentConfig = queryCurrentConfig(order.getTemplateId());
        WorkflowTemplateDTOs.PublicDetail currentTemplate = queryCurrentTemplate(order.getTemplateId());
        WorkflowOrderDetailDTO.Media cover = readCoverSnapshot(order.getTemplateCoverSnapshotJson());
        if (cover == null && currentTemplate != null) {
            cover = toOrderMedia(currentTemplate.cover());
        }
        List<WorkflowOrderDetailDTO.Input> inputs = toInputs(order, currentConfig, references, assets);
        List<WorkflowOrderDetailDTO.Asset> outputs = references.stream()
            .filter(reference -> "output".equals(reference.getAssetRole()))
            .map(reference -> toAsset(reference, requireAsset(assets, reference.getAssetId()), "生成结果"))
            .toList();
        WorkflowOrderDetailDTO.Task taskDetail = toTask(task);
        return new WorkflowOrderDetailDTO(Long.toString(order.getOrderId()), order.getOrderNo(),
            order.getCreateTime() == null ? "" : order.getCreateTime().toString(),
            new WorkflowOrderDetailDTO.Template(Long.toString(order.getTemplateId()),
                safeText(order.getTemplateTitleSnapshot()), cover), inputs, taskDetail, outputs,
            task.cancellable(), currentConfig != null);
    }

    private void requireMatchingTask(WorkflowOrder order, AiTaskDTO task) {
        if (task == null || !"workflow_template_generate".equals(task.taskType())
            || !"workflow_order".equals(task.resourceType())
            || !Long.toString(order.getOrderId()).equals(task.resourceId())
            || !TASK_STAGE_MATRIX.getOrDefault(task.status(), Set.of()).contains(task.stage())) {
            throw new ServiceException("工作流订单任务状态不可用");
        }
    }

    private Map<Long, AssetFile> loadOwnedAssets(WorkflowOrderOwnerDTO owner,
                                                  List<WorkflowOrderAsset> references) {
        List<Long> assetIds = references.stream().map(WorkflowOrderAsset::getAssetId).distinct().toList();
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return assetMapper.selectList(new LambdaQueryWrapper<AssetFile>()
                .in(AssetFile::getAssetId, assetIds)
                .eq(AssetFile::getTenantId, owner.tenantId())
                .eq(AssetFile::getWorkspaceId, owner.workspaceId())
                .eq(AssetFile::getOwnerId, owner.ownerUserId()))
            .stream().collect(Collectors.toUnmodifiableMap(AssetFile::getAssetId, Function.identity()));
    }

    private List<WorkflowOrderDetailDTO.Input> toInputs(WorkflowOrder order,
                                                         WorkflowTemplateDTOs.CreationConfig currentConfig,
                                                         List<WorkflowOrderAsset> references,
                                                         Map<Long, AssetFile> assets) {
        JsonNode values = readObject(order.getInputDisplaySnapshotJson());
        if (values == null) {
            values = readObject(order.getInputPayloadJson());
        }
        Map<String, WorkflowTemplateDTOs.InputField> fields = new LinkedHashMap<>();
        if (currentConfig != null) {
            currentConfig.fields().forEach(field -> fields.put(field.inputKey(), field));
        }
        Map<String, List<WorkflowOrderAsset>> inputReferences = references.stream()
            .filter(reference -> "input".equals(reference.getAssetRole()))
            .collect(Collectors.groupingBy(WorkflowOrderAsset::getInputKey, LinkedHashMap::new,
                Collectors.toList()));
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        JsonNode inputValues = values;
        fields.keySet().stream().filter(key -> inputValues != null && inputValues.has(key)
            || inputReferences.containsKey(key)).forEach(keys::add);
        if (values != null) {
            values.properties().stream().map(Map.Entry::getKey).sorted().forEach(keys::add);
        }
        keys.addAll(inputReferences.keySet());
        List<WorkflowOrderDetailDTO.Input> result = new ArrayList<>();
        for (String key : keys) {
            WorkflowTemplateDTOs.InputField field = fields.get(key);
            List<WorkflowOrderDetailDTO.Asset> inputAssets = inputReferences.getOrDefault(key, List.of()).stream()
                .map(reference -> toAsset(reference, requireAsset(assets, reference.getAssetId()),
                    field == null ? key : field.label()))
                .toList();
            result.add(new WorkflowOrderDetailDTO.Input(key, field == null ? key : field.label(),
                displayValue(values == null ? null : values.get(key)), inputAssets));
        }
        return List.copyOf(result);
    }

    private WorkflowOrderDetailDTO.Asset toAsset(WorkflowOrderAsset reference, AssetFile asset, String label) {
        return new WorkflowOrderDetailDTO.Asset(Long.toString(asset.getAssetId()), safeText(label),
            mediaType(asset.getContentType()), safeText(asset.getOriginalName()),
            Long.toString(asset.getFileSize() == null ? 0L : Math.max(0L, asset.getFileSize())),
            assetStatus(asset.getStatus()), Boolean.TRUE.equals(reference.getIsPrimary()));
    }

    private AssetFile requireAsset(Map<Long, AssetFile> assets, Long assetId) {
        AssetFile asset = assets.get(assetId);
        if (asset == null) {
            throw new ServiceException("工作流订单素材不可用");
        }
        return asset;
    }

    private WorkflowOrderDetailDTO.Task toTask(AiTaskDTO task) {
        Integer progress = task.progress() >= 0 && task.progress() <= 100 ? task.progress() : null;
        return new WorkflowOrderDetailDTO.Task(task.taskId(), task.taskType(), task.status(), task.stage(), progress,
            task.errorCode(), task.safeMessage(), task.retryable(), safeText(task.createdAt()),
            task.updatedAt() == null ? safeText(task.createdAt()) : task.updatedAt());
    }

    private WorkflowTemplateDTOs.CreationConfig queryCurrentConfig(Long templateId) {
        try {
            return templateService.queryCreationConfig(Long.toString(templateId));
        } catch (ServiceException ignored) {
            return null;
        }
    }

    private WorkflowTemplateDTOs.PublicDetail queryCurrentTemplate(Long templateId) {
        try {
            return templateService.queryVisibleDetail(Long.toString(templateId));
        } catch (ServiceException ignored) {
            return null;
        }
    }

    private WorkflowOrderDetailDTO.Media readCoverSnapshot(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(value, WorkflowOrderDetailDTO.Media.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private WorkflowOrderDetailDTO.Media toOrderMedia(WorkflowTemplateDTOs.Media media) {
        if (media == null) {
            return null;
        }
        return new WorkflowOrderDetailDTO.Media(media.mediaId(), media.mediaType(), media.url(), media.posterUrl(),
            media.width(), media.height(), media.alt());
    }

    private JsonNode readObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            JsonNode node = jsonMapper.readTree(value);
            return node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String displayValue(JsonNode value) {
        if (value == null || value.isNull() || value.isObject()) return null;
        if (value.isTextual()) return value.textValue();
        if (value.isNumber()) return value.toString();
        if (value.isBoolean()) return value.booleanValue() ? "是" : "否";
        if (value.isArray() && value.valueStream().allMatch(JsonNode::isTextual)) {
            return value.valueStream().map(JsonNode::textValue).collect(Collectors.joining("、"));
        }
        return null;
    }

    private String mediaType(String contentType) {
        if (contentType != null) {
            if (contentType.startsWith("image/")) return "image";
            if (contentType.startsWith("audio/")) return "audio";
            if (contentType.startsWith("video/")) return "video";
        }
        return "file";
    }

    private String assetStatus(String status) {
        if ("ready".equals(status)) return "ready";
        if ("failed".equals(status)) return "failed";
        return "processing";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, JsonNode> canonicalInputs(WorkflowTemplateDTOs.CreationConfig config,
                                                   Map<String, JsonNode> supplied) {
        if (supplied == null) throw new ServiceException("工作流输入不能为空", INPUT_INVALID);
        Map<String, WorkflowTemplateDTOs.InputField> fields = new LinkedHashMap<>();
        config.fields().forEach(field -> fields.put(field.inputKey(), field));
        if (!fields.keySet().containsAll(supplied.keySet())) {
            throw new ServiceException("工作流输入包含未知字段", INPUT_INVALID);
        }
        Map<String, JsonNode> result = new LinkedHashMap<>();
        fields.values().stream().sorted(Comparator.comparing(WorkflowTemplateDTOs.InputField::inputKey))
            .forEach(field -> {
                JsonNode value = supplied.get(field.inputKey());
                if (value == null || value.isNull()) {
                    if (field.required()) throw new ServiceException("缺少必填工作流输入", INPUT_INVALID);
                    return;
                }
                validateInputValue(field, value);
                result.put(field.inputKey(), value);
            });
        return Map.copyOf(result);
    }

    private void validateInputValue(WorkflowTemplateDTOs.InputField field, JsonNode value) {
        boolean valid = switch (field.valueType()) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "decimal" -> value.isNumber() && !value.toString().contains("e") && !value.toString().contains("E");
            case "boolean" -> value.isBoolean();
            case "string_array" -> value.isArray() && value.valueStream().allMatch(JsonNode::isTextual);
            case "asset_array" -> validAssetArray(field, value);
            default -> false;
        };
        if (!valid) throw new ServiceException("工作流输入格式不正确: " + field.inputKey(), INPUT_INVALID);
    }

    private boolean validAssetArray(WorkflowTemplateDTOs.InputField field, JsonNode value) {
        if (!value.isArray() || value.isEmpty()) return !field.required();
        Integer maxItems = field.constraints() == null ? null : field.constraints().maxItems();
        if (maxItems != null && value.size() > maxItems) return false;
        for (JsonNode item : value) {
            if (!item.isObject() || item.size() != 1 || !item.has("assetId") || !item.get("assetId").isTextual()
                || !POSITIVE_ID.matcher(item.get("assetId").textValue()).matches()) return false;
        }
        return true;
    }

    private void bindInputAssets(WorkflowOrder order, WorkflowOrderOwnerDTO owner,
                                 WorkflowTemplateDTOs.CreationConfig config, Map<String, JsonNode> inputs) {
        for (WorkflowTemplateDTOs.InputField field : config.fields()) {
            if (!"asset_array".equals(field.valueType()) || !inputs.containsKey(field.inputKey())) continue;
            int index = 0;
            for (JsonNode item : inputs.get(field.inputKey())) {
                long assetId = parseId(item.get("assetId").textValue());
                requireUsableWorkflowInputAsset(assetId, order, owner, field);
                WorkflowOrderAsset reference = new WorkflowOrderAsset();
                reference.setTenantId(owner.tenantId()); reference.setOwnerUserId(owner.ownerUserId());
                reference.setWorkspaceId(owner.workspaceId()); reference.setOrderId(order.getOrderId());
                reference.setAssetId(assetId); reference.setAssetRole("input"); reference.setInputKey(field.inputKey());
                reference.setSortOrder(index++); reference.setIsPrimary(false);
                if (orderAssetMapper.insert(reference) != 1) {
                    throw new ServiceException("工作流输入素材绑定失败", INPUT_ASSET_INVALID);
                }
            }
        }
    }

    private void requireUsableWorkflowInputAsset(long assetId, WorkflowOrder order, WorkflowOrderOwnerDTO owner,
                                                  WorkflowTemplateDTOs.InputField field) {
        AssetFile asset = assetMapper.selectOne(new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getAssetId, assetId).eq(AssetFile::getTenantId, owner.tenantId())
            .eq(AssetFile::getWorkspaceId, owner.workspaceId()).eq(AssetFile::getOwnerId, owner.ownerUserId())
            .eq(AssetFile::getCategory, "workflow_input").eq(AssetFile::getStatus, "ready"));
        List<UploadSession> sessions = asset == null ? List.of() : uploadSessionMapper.selectList(new LambdaQueryWrapper<UploadSession>()
            .eq(UploadSession::getAssetId, assetId).eq(UploadSession::getTenantId, owner.tenantId())
            .eq(UploadSession::getWorkspaceId, owner.workspaceId()).eq(UploadSession::getOwnerUserId, owner.ownerUserId())
            .eq(UploadSession::getContextScope, "workflow_order").eq(UploadSession::getTemplateId, order.getTemplateId())
            .eq(UploadSession::getSchemaHash, order.getSchemaHash()).eq(UploadSession::getInputKey, field.inputKey())
            .eq(UploadSession::getStatus, "completed"));
        if (asset == null || sessions.isEmpty() || !matchesAssetConstraint(asset, field.constraints())) {
            throw new ServiceException("工作流输入素材不可用", INPUT_ASSET_INVALID);
        }
    }

    private boolean matchesAssetConstraint(AssetFile asset, WorkflowTemplateDTOs.InputConstraints constraints) {
        if (constraints == null) return true;
        return (constraints.allowedContentTypes() == null || constraints.allowedContentTypes().isEmpty()
            || constraints.allowedContentTypes().contains(asset.getContentType()))
            && (constraints.maxBytesPerAsset() == null || asset.getFileSize() == null
            || asset.getFileSize() <= Long.parseLong(constraints.maxBytesPerAsset()));
    }

    private WorkflowOrder findByIdempotency(WorkflowOrderOwnerDTO owner, String idempotencyKey) {
        return orderMapper.selectOne(new LambdaQueryWrapper<WorkflowOrder>().eq(WorkflowOrder::getTenantId, owner.tenantId())
            .eq(WorkflowOrder::getWorkspaceId, owner.workspaceId()).eq(WorkflowOrder::getOwnerUserId, owner.ownerUserId())
            .eq(WorkflowOrder::getIdempotencyKey, idempotencyKey));
    }

    private void validateCommand(CreateWorkflowOrderDTO command) {
        if (command == null || command.templateId() == null || !POSITIVE_ID.matcher(command.templateId()).matches()
            || command.schemaHash() == null || !command.schemaHash().matches("sha256:[0-9a-f]{64}")
            || command.idempotencyKey() == null || !IDEMPOTENCY_KEY.matcher(command.idempotencyKey()).matches()) {
            throw new ServiceException("工作流订单参数无效", INPUT_INVALID);
        }
    }

    private WorkflowOrderDTO toDto(WorkflowOrder order) {
        return new WorkflowOrderDTO(Long.toString(order.getOrderId()), order.getTaskId() == null ? null : Long.toString(order.getTaskId()),
            Long.toString(order.getTemplateId()), order.getTaskId() == null ? "pending" : "queued");
    }

    private long parseId(String value) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) throw new ServiceException("工作流编号无效", INPUT_INVALID);
        return Long.parseLong(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
