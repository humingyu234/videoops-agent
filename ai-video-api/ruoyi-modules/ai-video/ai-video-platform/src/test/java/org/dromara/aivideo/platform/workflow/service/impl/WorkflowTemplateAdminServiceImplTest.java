package org.dromara.aivideo.platform.workflow.service.impl;

import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.CreateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.ExecutionConfigBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.WorkflowTemplateQueryBo;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WorkflowTemplateAdminServiceImplTest {

    @Mock
    private IWorkflowTemplateService workflowTemplateService;

    private JsonMapper jsonMapper;
    private WorkflowTemplateAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new WorkflowTemplateAdminServiceImpl(workflowTemplateService, jsonMapper);
    }

    @Test
    void mapsAdminPageWithoutChangingNumericTotalOrIds() {
        var query = new WorkflowTemplateQueryBo();
        query.setChannel("video_template");
        query.setStatus("enabled");
        query.setKeyword("portrait");
        query.setCategoryId("12");
        query.setRecommended(true);
        query.setSort("latest");
        var pageQuery = new PageQuery(20, 2);
        var row = new WorkflowTemplateDTOs.AdminSummary(
            "101", "video_template", "Portrait", "portrait", "summary", "enabled", true,
            "12", "People", true, true, "primary", 3L,
            LocalDateTime.of(2026, 8, 11, 9, 0), LocalDateTime.of(2026, 8, 11, 10, 0));
        when(workflowTemplateService.queryAdminPage(any(), eq(pageQuery)))
            .thenReturn(PageResult.build(List.of(row), 42));

        var result = service.page(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(42);
        assertThat(result.getRows()).singleElement().satisfies(vo -> {
            assertThat(vo.templateId()).isEqualTo("101");
            assertThat(vo.rowRevision()).isEqualTo(3L);
            assertThat(vo.enabledAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 9, 0));
        });
        ArgumentCaptor<WorkflowTemplateDTOs.AdminQuery> captor =
            ArgumentCaptor.forClass(WorkflowTemplateDTOs.AdminQuery.class);
        verify(workflowTemplateService).queryAdminPage(captor.capture(), eq(pageQuery));
        assertThat(captor.getValue()).isEqualTo(new WorkflowTemplateDTOs.AdminQuery(
            "video_template", "enabled", "portrait", "12", true, "latest"));
    }

    @Test
    void createMapsOnlyBasicFieldsAndFormSchema() {
        var formSchema = jsonMapper.createObjectNode().put("schemaVersion", "workflow-form-1");
        var command = new CreateWorkflowTemplateBo(
            "video_template", "Portrait", "summary", "description", null, "12",
            List.of("31", "32"), formSchema, true, 10, 120);
        when(workflowTemplateService.create(eq(9001L), any())).thenReturn("101");

        assertThat(service.create(command, 9001L)).isEqualTo("101");

        ArgumentCaptor<WorkflowTemplateDTOs.Save> captor = ArgumentCaptor.forClass(WorkflowTemplateDTOs.Save.class);
        verify(workflowTemplateService).create(eq(9001L), captor.capture());
        assertThat(captor.getValue().slug()).isNull();
        assertThat(captor.getValue().tagsJson()).isEqualTo("[\"31\",\"32\"]");
        assertThat(captor.getValue().formSchema()).contains("workflow-form-1");
        assertThat(captor.getValue().expectedRevision()).isNull();
    }

    @Test
    void executionConfigMapsSecretOnlyIntoCoreWriteCommand() {
        var inputMapping = jsonMapper.createObjectNode().put("prompt", "node-1");
        var outputPolicy = jsonMapper.createObjectNode().put("kind", "video");
        var command = new ExecutionConfigBo(
            "201", "runninghub_workflow", "workflow-9", null, "plus", "plain-secret", false,
            inputMapping, outputPolicy, 900, true, 4L);
        var dto = new WorkflowTemplateDTOs.ExecutionConfig(
            "301", "101", "201", "runninghub_workflow", "workflow-9", null,
            "plus",
            inputMapping.toString(), outputPolicy.toString(), 900, true, true, "never", 5L,
            LocalDateTime.of(2026, 8, 11, 10, 0));
        doAnswer(invocation -> {
            WorkflowTemplateDTOs.ExecutionConfigSave mapped = invocation.getArgument(2);
            assertThat(new String(mapped.accessPassword())).isEqualTo("plain-secret");
            assertThat(mapped.expectedRevision()).isEqualTo(4L);
            assertThat(mapped.executionMode()).isEqualTo("runninghub_workflow");
            assertThat(mapped.instanceType()).isEqualTo("plus");
            return dto;
        }).when(workflowTemplateService).saveExecutionConfig(eq(9001L), eq("101"), any());

        var result = service.saveExecutionConfig("101", command, 9001L);

        assertThat(result.hasAccessPassword()).isTrue();
        assertThat(result.workflowId()).isEqualTo("workflow-9");
        assertThat(result.instanceType()).isEqualTo("plus");
        assertThat(JsonMapper.builder().build().writeValueAsString(result))
            .doesNotContain("plain-secret", "\"accessPassword\"");
    }
}
