package org.dromara.aivideo.workflow.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.asset.domain.AssetFile;
import org.dromara.aivideo.asset.domain.UploadSession;
import org.dromara.aivideo.asset.mapper.AssetFileMapper;
import org.dromara.aivideo.asset.mapper.UploadSessionMapper;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.service.IAiTaskTransactionService;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.order.dto.CreateWorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDetailDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderOwnerDTO;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrder;
import org.dromara.aivideo.workflow.order.domain.WorkflowOrderAsset;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderAssetMapper;
import org.dromara.aivideo.workflow.order.mapper.WorkflowOrderMapper;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@Tag("dev")
class WorkflowOrderServiceImplTest {

    @BeforeAll
    static void initializeEntityMetadata() {
        initializeEntityMetadata(WorkflowOrder.class);
        initializeEntityMetadata(WorkflowOrderAsset.class);
        initializeEntityMetadata(AssetFile.class);
        initializeEntityMetadata(UploadSession.class);
    }

    @Test
    void workflowOrderAssetMappingMatchesCreateOnlyAuditSchema() {
        var tableInfo = TableInfoHelper.getTableInfo(WorkflowOrderAsset.class);
        assertThat(tableInfo).isNotNull();
        Set<String> columns = tableInfo.getFieldList().stream()
            .map(field -> field.getColumn())
            .collect(Collectors.toSet());

        assertThat(columns).contains("create_time");
        assertThat(columns).doesNotContain("update_time", "create_by", "update_by", "create_dept");
    }

    @Test
    void createsOneOwnedOrderAndOneUnifiedWorkflowTask() throws Exception {
        WorkflowOrderMapper orderMapper = mock(WorkflowOrderMapper.class);
        WorkflowOrderAssetMapper orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        AssetFileMapper assetMapper = mock(AssetFileMapper.class);
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IAiTaskTransactionService taskService = mock(IAiTaskTransactionService.class);
        when(templateService.queryCreationConfig("101")).thenReturn(config());
        when(templateService.queryVisibleDetail("101")).thenReturn(detail());
        when(orderMapper.insert(any(WorkflowOrder.class))).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(orderAssetMapper.insert(any(WorkflowOrderAsset.class))).thenReturn(1);
        when(assetMapper.selectOne(any())).thenReturn(readyAsset());
        when(uploadSessionMapper.selectList(any())).thenReturn(List.of(completedSession()));
        when(taskService.createWorkflowTask(any(), any())).thenReturn(task());

        WorkflowOrderServiceImpl service = new WorkflowOrderServiceImpl(orderMapper, orderAssetMapper, assetMapper,
            uploadSessionMapper, templateService, taskService, JsonMapper.builder().build());

        var result = service.create(new WorkflowOrderOwnerDTO(1L, "personal-7", 7L),
            new CreateWorkflowOrderDTO("101", config().schemaHash(), "workflow-order-1",
                Map.of("prompt", JsonMapper.builder().build().readTree("\"cat\""),
                    "image", JsonMapper.builder().build().readTree("[{\"assetId\":\"501\"}]"))));

        assertThat(result.templateId()).isEqualTo("101");
        assertThat(result.taskId()).isEqualTo("702");
        verify(taskService).createWorkflowTask(any(AiTaskActorDTO.class), any());
        verify(orderAssetMapper).insert(any(WorkflowOrderAsset.class));
    }

    @Test
    void createsOrderWhenOneAssetWasBoundByMultipleCompletedUploadSessions() throws Exception {
        WorkflowOrderMapper orderMapper = mock(WorkflowOrderMapper.class);
        WorkflowOrderAssetMapper orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        AssetFileMapper assetMapper = mock(AssetFileMapper.class);
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IAiTaskTransactionService taskService = mock(IAiTaskTransactionService.class);
        when(templateService.queryCreationConfig("101")).thenReturn(config());
        when(templateService.queryVisibleDetail("101")).thenReturn(detail());
        when(orderMapper.insert(any(WorkflowOrder.class))).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(orderAssetMapper.insert(any(WorkflowOrderAsset.class))).thenReturn(1);
        when(assetMapper.selectOne(any())).thenReturn(readyAsset());
        when(uploadSessionMapper.selectList(any())).thenReturn(List.of(completedSession(), completedSession()));
        when(taskService.createWorkflowTask(any(), any())).thenReturn(task());

        WorkflowOrderServiceImpl service = new WorkflowOrderServiceImpl(orderMapper, orderAssetMapper, assetMapper,
            uploadSessionMapper, templateService, taskService, JsonMapper.builder().build());

        var result = service.create(new WorkflowOrderOwnerDTO(1L, "personal-7", 7L),
            new CreateWorkflowOrderDTO("101", config().schemaHash(), "workflow-order-same-asset",
                Map.of("prompt", JsonMapper.builder().build().readTree("\"cat\""),
                    "image", JsonMapper.builder().build().readTree("[{\"assetId\":\"501\"}]"))));

        assertThat(result.taskId()).isEqualTo("702");
        verify(uploadSessionMapper).selectList(any());
    }

    @Test
    void queriesOwnedDetailWithTenantWorkspaceAndOwnerAndMapsInputsAndOutputs() throws Exception {
        WorkflowOrderMapper orderMapper = mock(WorkflowOrderMapper.class);
        WorkflowOrderAssetMapper orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        AssetFileMapper assetMapper = mock(AssetFileMapper.class);
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IAiTaskTransactionService taskService = mock(IAiTaskTransactionService.class);
        WorkflowOrder order = ownedOrder();
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderAssetMapper.selectList(any())).thenReturn(List.of(inputReference(), outputReference()));
        when(assetMapper.selectList(any())).thenReturn(List.of(inputAsset(), outputAsset()));
        when(taskService.getOwned(any(AiTaskAccessScopeDTO.class), eq("702"))).thenReturn(successTask());
        when(templateService.queryCreationConfig("101")).thenReturn(config());

        WorkflowOrderServiceImpl service = new WorkflowOrderServiceImpl(orderMapper, orderAssetMapper, assetMapper,
            uploadSessionMapper, templateService, taskService, JsonMapper.builder().build());

        WorkflowOrderDetailDTO result = service.queryOwnedDetail(
            new WorkflowOrderOwnerDTO(1L, "personal-7", 7L), "701");

        assertThat(result.orderId()).isEqualTo("701");
        assertThat(result.orderNo()).isEqualTo("WF701");
        assertThat(result.template().title()).isEqualTo("Demo");
        assertThat(result.template().cover().url()).isEqualTo("https://example.test/cover.png");
        assertThat(result.inputs()).extracting(WorkflowOrderDetailDTO.Input::inputKey)
            .containsExactly("prompt", "image");
        assertThat(result.inputs().getFirst().displayValue()).isEqualTo("cat");
        assertThat(result.inputs().get(1).assets()).singleElement()
            .extracting(WorkflowOrderDetailDTO.Asset::assetId).isEqualTo("501");
        assertThat(result.outputs()).singleElement().satisfies(asset -> {
            assertThat(asset.assetId()).isEqualTo("601");
            assertThat(asset.mediaType()).isEqualTo("video");
            assertThat(asset.primary()).isTrue();
        });
        assertThat(result.task().status()).isEqualTo("success");
        assertThat(result.task().stage()).isEqualTo("completed");
        assertThat(result.task().failureCode()).isNull();
        assertThat(result.task().failureMessage()).isNull();
        assertThat(result.canCancel()).isFalse();
        assertThat(result.canRemake()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WorkflowOrder>> orderQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).selectOne(orderQuery.capture());
        assertOwnedScope(orderQuery.getValue(), 1L, "personal-7", 7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WorkflowOrderAsset>> assetQuery = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderAssetMapper).selectList(assetQuery.capture());
        assertOwnedScope(assetQuery.getValue(), 1L, "personal-7", 7L);
        verify(taskService).getOwned(new AiTaskAccessScopeDTO(1L, 7L, "personal-7"), "702");
    }

    @Test
    void mapsOnlyTheTasksSafeFailureCodeAndMessage() throws Exception {
        WorkflowOrderMapper orderMapper = mock(WorkflowOrderMapper.class);
        WorkflowOrderAssetMapper orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        AssetFileMapper assetMapper = mock(AssetFileMapper.class);
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IAiTaskTransactionService taskService = mock(IAiTaskTransactionService.class);
        when(orderMapper.selectOne(any())).thenReturn(ownedOrder());
        when(orderAssetMapper.selectList(any())).thenReturn(List.of());
        when(taskService.getOwned(any(AiTaskAccessScopeDTO.class), eq("702"))).thenReturn(failedTask());
        when(templateService.queryCreationConfig("101")).thenThrow(new ServiceException("模板不可用", 46501));
        WorkflowOrderServiceImpl service = new WorkflowOrderServiceImpl(orderMapper, orderAssetMapper, assetMapper,
            uploadSessionMapper, templateService, taskService, JsonMapper.builder().build());

        WorkflowOrderDetailDTO result = service.queryOwnedDetail(
            new WorkflowOrderOwnerDTO(1L, "personal-7", 7L), "701");

        assertThat(result.task().failureCode()).isEqualTo("WORKFLOW_EXECUTION_FAILED");
        assertThat(result.task().failureMessage()).isEqualTo("生成失败，请稍后重新制作");
        assertThat(result.canRemake()).isFalse();
    }

    @Test
    void returnsNullCoverWhenNeitherSnapshotNorCurrentTemplateHasOne() {
        WorkflowOrderMapper orderMapper = mock(WorkflowOrderMapper.class);
        WorkflowOrderAssetMapper orderAssetMapper = mock(WorkflowOrderAssetMapper.class);
        AssetFileMapper assetMapper = mock(AssetFileMapper.class);
        UploadSessionMapper uploadSessionMapper = mock(UploadSessionMapper.class);
        IWorkflowTemplateService templateService = mock(IWorkflowTemplateService.class);
        IAiTaskTransactionService taskService = mock(IAiTaskTransactionService.class);
        WorkflowOrder order = ownedOrder();
        order.setTemplateCoverSnapshotJson(null);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderAssetMapper.selectList(any())).thenReturn(List.of());
        when(taskService.getOwned(any(AiTaskAccessScopeDTO.class), eq("702"))).thenReturn(failedTask());
        when(templateService.queryCreationConfig("101")).thenThrow(new ServiceException("unavailable", 46501));
        when(templateService.queryVisibleDetail("101")).thenReturn(null);
        WorkflowOrderServiceImpl service = new WorkflowOrderServiceImpl(orderMapper, orderAssetMapper, assetMapper,
            uploadSessionMapper, templateService, taskService, JsonMapper.builder().build());

        WorkflowOrderDetailDTO result = service.queryOwnedDetail(
            new WorkflowOrderOwnerDTO(1L, "personal-7", 7L), "701");

        assertThat(result.template().cover()).isNull();
    }

    private static WorkflowTemplateDTOs.CreationConfig config() {
        return new WorkflowTemplateDTOs.CreationConfig("101", "workflow-form-1",
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", List.of(
                new WorkflowTemplateDTOs.InputField("prompt", null, "Prompt", null, "text", "string", true,
                    null, null, List.of(), null),
                new WorkflowTemplateDTOs.InputField("image", null, "Image", null, "image", "asset_array", true,
                    null, null, List.of(), new WorkflowTemplateDTOs.InputConstraints(null, null, null, null, null,
                        1, "image", List.of("png"), List.of("image/png"), "1024"))), 30,
            new WorkflowTemplateDTOs.BillingPolicy("free"));
    }

    private static WorkflowTemplateDTOs.PublicDetail detail() {
        return new WorkflowTemplateDTOs.PublicDetail("101", "Demo", "", "image", null, List.of(), null,
            null, "0", 30, null, "", List.of(), List.of());
    }

    private static AssetFile readyAsset() {
        AssetFile asset = new AssetFile();
        asset.setAssetId(501L); asset.setStatus("ready"); asset.setContentType("image/png"); asset.setFileSize(100L);
        return asset;
    }

    private static UploadSession completedSession() {
        UploadSession session = new UploadSession();
        session.setStatus("completed"); session.setTemplateId(101L);
        session.setSchemaHash(config().schemaHash()); session.setInputKey("image");
        return session;
    }

    private static AiTaskDTO task() {
        return new AiTaskDTO("702", "workflow_template_generate", "queued", "waiting_for_dispatch",
            "workflow_order", "701", "701", "1", "701", null, null, null,
            "2026-08-12T00:00:00Z", null, null, 0, false, true);
    }

    private static WorkflowOrder ownedOrder() {
        WorkflowOrder order = new WorkflowOrder();
        order.setOrderId(701L);
        order.setOrderNo("WF701");
        order.setTenantId(1L);
        order.setWorkspaceId("personal-7");
        order.setOwnerUserId(7L);
        order.setTemplateId(101L);
        order.setTaskId(702L);
        order.setTemplateTitleSnapshot("Demo");
        order.setTemplateCoverSnapshotJson("{\"mediaId\":\"901\",\"mediaType\":\"image\","
            + "\"url\":\"https://example.test/cover.png\",\"width\":640,\"height\":360,\"alt\":\"Demo\"}");
        order.setInputDisplaySnapshotJson("{\"prompt\":\"cat\",\"image\":[{\"assetId\":\"501\"}]}");
        order.setInputPayloadJson(order.getInputDisplaySnapshotJson());
        order.setCreateTime(LocalDateTime.of(2026, 8, 12, 8, 0));
        return order;
    }

    private static WorkflowOrderAsset inputReference() {
        WorkflowOrderAsset reference = new WorkflowOrderAsset();
        reference.setAssetId(501L);
        reference.setAssetRole("input");
        reference.setInputKey("image");
        reference.setSortOrder(0);
        reference.setIsPrimary(false);
        return reference;
    }

    private static WorkflowOrderAsset outputReference() {
        WorkflowOrderAsset reference = new WorkflowOrderAsset();
        reference.setAssetId(601L);
        reference.setAssetRole("output");
        reference.setSortOrder(0);
        reference.setIsPrimary(true);
        return reference;
    }

    private static AssetFile inputAsset() {
        AssetFile asset = readyAsset();
        asset.setOriginalName("input.png");
        return asset;
    }

    private static AssetFile outputAsset() {
        AssetFile asset = new AssetFile();
        asset.setAssetId(601L);
        asset.setStatus("ready");
        asset.setContentType("video/mp4");
        asset.setOriginalName("result.mp4");
        asset.setFileSize(4096L);
        return asset;
    }

    private static AiTaskDTO successTask() {
        return new AiTaskDTO("702", "workflow_template_generate", "success", "completed",
            "workflow_order", "701", null, null, null, "601", null, null,
            "2026-08-12T00:00:00Z", "2026-08-12T00:01:00Z", null, 100, false, false);
    }

    private static AiTaskDTO failedTask() {
        return new AiTaskDTO("702", "workflow_template_generate", "failed", "failed",
            "workflow_order", "701", null, null, null, null, "WORKFLOW_EXECUTION_FAILED",
            "生成失败，请稍后重新制作", "2026-08-12T00:00:00Z", "2026-08-12T00:01:00Z",
            null, 50, false, true);
    }

    private static void assertOwnedScope(Wrapper<?> wrapper, Long tenantId, String workspaceId, Long ownerId) {
        assertThat(wrapper.getSqlSegment()).contains("tenant_id", "workspace_id", "owner_user_id");
        assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper)
            .getParamNameValuePairs().values()).contains(tenantId, workspaceId, ownerId);
    }

    private static void initializeEntityMetadata(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
