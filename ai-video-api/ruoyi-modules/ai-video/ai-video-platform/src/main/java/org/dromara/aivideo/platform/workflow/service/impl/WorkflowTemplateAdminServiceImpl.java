package org.dromara.aivideo.platform.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.CreateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.ExecutionConfigBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.StatusChangeBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.UpdateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.WorkflowTemplateQueryBo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.ExecutionConfigVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.OptionVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.SummaryVo;
import org.dromara.aivideo.platform.workflow.service.IWorkflowTemplateAdminService;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.aivideo.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

/** 只负责运营端 BO/VO 与 Core DTO 的映射。 */
@Service
@RequiredArgsConstructor
public class WorkflowTemplateAdminServiceImpl implements IWorkflowTemplateAdminService {

    private final IWorkflowTemplateService workflowTemplateService;
    private final JsonMapper jsonMapper;

    @Override
    public PageResult<SummaryVo> page(WorkflowTemplateQueryBo query, PageQuery pageQuery) {
        WorkflowTemplateDTOs.AdminQuery dto = new WorkflowTemplateDTOs.AdminQuery(
            query == null ? null : query.getChannel(),
            query == null ? null : query.getStatus(),
            query == null ? null : query.getKeyword(),
            query == null ? null : query.getCategoryId(),
            query == null ? null : query.getRecommended(),
            query == null ? null : query.getSort());
        PageResult<WorkflowTemplateDTOs.AdminSummary> page =
            workflowTemplateService.queryAdminPage(dto, pageQuery);
        return PageResult.build(page.getRows().stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    public DetailVo detail(String templateId) {
        return toDetail(workflowTemplateService.queryAdminDetail(templateId));
    }

    @Override
    public String create(CreateWorkflowTemplateBo command, Long operatorId) {
        return workflowTemplateService.create(operatorId, toSave(command));
    }

    @Override
    public void update(String templateId, UpdateWorkflowTemplateBo command, Long operatorId) {
        workflowTemplateService.update(operatorId, templateId, toSave(command));
    }

    @Override
    public void delete(String templateId, long expectedRevision, Long operatorId) {
        workflowTemplateService.delete(operatorId, templateId, expectedRevision);
    }

    @Override
    public ExecutionConfigVo queryExecutionConfig(String templateId) {
        return toExecutionConfig(workflowTemplateService.queryExecutionConfig(templateId));
    }

    @Override
    public ExecutionConfigVo saveExecutionConfig(String templateId, ExecutionConfigBo command, Long operatorId) {
        char[] password = secretChars(command.accessPassword());
        try {
            WorkflowTemplateDTOs.ExecutionConfigSave dto = new WorkflowTemplateDTOs.ExecutionConfigSave(
                command.executionMode(), command.runningHubAccountId(), command.workflowId(), command.webAppId(),
                command.instanceType(),
                command.inputMapping().toString(), command.outputPolicy().toString(), command.timeoutSeconds(),
                Boolean.TRUE.equals(command.enabled()), command.clearAccessPassword(), password,
                command.expectedRevision() == null ? 0L : command.expectedRevision());
            return toExecutionConfig(workflowTemplateService.saveExecutionConfig(operatorId, templateId, dto));
        } finally {
            clear(password);
        }
    }

    @Override
    public void enable(String templateId, StatusChangeBo command, Long operatorId) {
        workflowTemplateService.enable(operatorId, templateId, command.expectedRevision());
    }

    @Override
    public void disable(String templateId, StatusChangeBo command, Long operatorId) {
        workflowTemplateService.disable(operatorId, templateId, command.expectedRevision());
    }

    @Override
    public List<OptionVo> options() {
        return workflowTemplateService.queryOptions().stream()
            .map(option -> new OptionVo(option.value(), option.label(), option.status()))
            .toList();
    }

    private WorkflowTemplateDTOs.Save toSave(CreateWorkflowTemplateBo command) {
        return new WorkflowTemplateDTOs.Save(
            command.channel(), command.name(), null, command.summary(), command.description(),
            command.coverAssetId(), command.categoryId(), jsonMapper.writeValueAsString(command.tagIds()),
            command.formSchema().toString(), command.recommended(), command.sortNo(),
            command.estimatedDurationSeconds(), null);
    }

    private WorkflowTemplateDTOs.Save toSave(UpdateWorkflowTemplateBo command) {
        return new WorkflowTemplateDTOs.Save(
            command.channel(), command.name(), null, command.summary(), command.description(),
            command.coverAssetId(), command.categoryId(), jsonMapper.writeValueAsString(command.tagIds()),
            command.formSchema().toString(), command.recommended(), command.sortNo(),
            command.estimatedDurationSeconds(), command.expectedRevision());
    }

    private SummaryVo toSummary(WorkflowTemplateDTOs.AdminSummary dto) {
        return new SummaryVo(
            dto.templateId(), dto.channel(), dto.name(), dto.slug(), dto.summary(), dto.status(), dto.recommended(),
            dto.categoryId(), dto.categoryName(), dto.executionConfigured(), dto.executionEnabled(), dto.accountName(),
            dto.rowRevision(), dto.enabledAt(), dto.updateTime());
    }

    private DetailVo toDetail(WorkflowTemplateDTOs.AdminDetail dto) {
        return new DetailVo(
            dto.templateId(), dto.channel(), dto.name(), dto.slug(), dto.summary(), dto.description(),
            dto.coverAssetId(), dto.categoryId(), dto.tagIds(), parseJson(dto.formSchema()), dto.schemaHash(),
            dto.status(), dto.recommended(), dto.sortNo(), dto.estimatedDurationSeconds(), dto.billingMode(),
            dto.rowRevision(), dto.enabledAt(), dto.createTime(), dto.updateTime(),
            dto.executionConfig() == null ? null : toExecutionConfig(dto.executionConfig()));
    }

    private ExecutionConfigVo toExecutionConfig(WorkflowTemplateDTOs.ExecutionConfig dto) {
        return new ExecutionConfigVo(
            dto.executionConfigId(), dto.templateId(), dto.runningHubAccountId(), dto.executionMode(),
            dto.workflowId(), dto.webAppId(), dto.instanceType(), parseJson(dto.inputMappingJson()), parseJson(dto.outputPolicyJson()),
            dto.timeoutSeconds(), dto.enabled(), dto.hasAccessPassword(), dto.lastTestStatus(), dto.rowRevision(),
            dto.updateTime());
    }

    private JsonNode parseJson(String value) {
        return value == null ? null : jsonMapper.readTree(value);
    }

    private char[] secretChars(String value) {
        return value == null || value.isBlank() ? null : value.toCharArray();
    }

    private void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
