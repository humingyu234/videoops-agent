package org.dromara.aivideo.user.discovery;

import org.dromara.aivideo.user.discovery.domain.vo.WorkflowCreationConfigVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateCardVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateDetailVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class DiscoveryUserWireContractTest {

    private static final List<String> FORBIDDEN = List.of(
        "self_hosted_comfyui", "runninghub_workflow", "runninghub_ai_app", "providerKind",
        "executionMode", "executionPlanId", "templateVersionId", "workflowId", "webAppId",
        "nodeId", "runningHubTaskId");

    @Test
    void publicRecordShapesContainOnlyPhaseOneFields() {
        assertComponents(WorkflowTemplateCardVo.class,
            "templateId", "title", "summary", "channel", "category", "tags", "cover", "preview",
            "usageCount", "estimatedDurationSeconds", "enabledAt");
        assertComponents(WorkflowTemplateDetailVo.class,
            "templateId", "title", "summary", "channel", "category", "tags", "cover", "preview",
            "usageCount", "estimatedDurationSeconds", "enabledAt", "description", "cases", "requiredInputs");
        assertComponents(WorkflowCreationConfigVo.class,
            "templateId", "schemaVersion", "schemaHash", "fields", "estimatedDurationSeconds", "billingPolicy");
        assertNoForbiddenComponents(WorkflowTemplateCardVo.class);
        assertNoForbiddenComponents(WorkflowTemplateDetailVo.class);
        assertNoForbiddenComponents(WorkflowCreationConfigVo.class);
    }

    @Test
    void serializedUserContractsNeverExposeForbiddenFieldsOrValues() {
        WorkflowTemplateCardVo card = new WorkflowTemplateCardVo(
            "101", "口播模板", "快速生成", "video_template",
            new WorkflowTemplateCardVo.CategoryVo("11", "营销"),
            List.of(new WorkflowTemplateCardVo.TagVo("21", "口播")), null, null, null, null,
            "2026-08-11T09:30:00");
        WorkflowTemplateDetailVo detail = new WorkflowTemplateDetailVo(
            card.templateId(), card.title(), card.summary(), card.channel(), card.category(), card.tags(),
            card.cover(), card.preview(), card.usageCount(), card.estimatedDurationSeconds(), card.enabledAt(),
            "详情", List.of(), List.of());
        WorkflowCreationConfigVo config = new WorkflowCreationConfigVo(
            "101", "workflow-form-1", "sha256:" + "a".repeat(64), List.of(), null,
            new WorkflowCreationConfigVo.BillingPolicyVo("free"));
        JsonMapper mapper = JsonMapper.builder().build();

        String wire = mapper.writeValueAsString(List.of(card, detail, config));

        assertThat(wire).doesNotContain(FORBIDDEN.toArray(String[]::new));
    }

    @Test
    void fileInputOmitsEmptyOptionsFromCreationConfigWireContract() {
        WorkflowCreationConfigVo config = new WorkflowCreationConfigVo(
            "101", "workflow-form-1", "sha256:" + "a".repeat(64),
            List.of(new WorkflowCreationConfigVo.InputFieldVo(
                "sourceVideo", null, "源视频", null, "video", "asset_array", true,
                null, null, List.of(),
                new WorkflowCreationConfigVo.InputConstraintsVo(
                    null, null, null, null, null, 1, "video", null, null, null))),
            null, new WorkflowCreationConfigVo.BillingPolicyVo("free"));

        String wire = JsonMapper.builder().build().writeValueAsString(config);

        assertThat(wire).doesNotContain("\"options\"");
    }

    @Test
    void obsoleteStringTotalWrapperIsRemoved() {
        assertThatThrownBy(() -> Class.forName(
            "org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplatePageVo"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    private void assertComponents(Class<?> type, String... expected) {
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
            .containsExactly(expected);
    }

    private void assertNoForbiddenComponents(Class<?> type) {
        assertThat(Arrays.stream(type.getDeclaredClasses())
            .flatMap(nested -> Arrays.stream(nested.getRecordComponents()))
            .map(RecordComponent::getName))
            .doesNotContain(FORBIDDEN.toArray(String[]::new));
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
            .doesNotContain(FORBIDDEN.toArray(String[]::new));
    }
}
