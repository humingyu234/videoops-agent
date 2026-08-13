package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

public interface IWorkflowTemplateService {

    PageResult<WorkflowTemplateDTOs.AdminSummary> queryAdminPage(
        WorkflowTemplateDTOs.AdminQuery query, PageQuery pageQuery);

    WorkflowTemplateDTOs.AdminDetail queryAdminDetail(String templateId);

    String create(Long actorId, WorkflowTemplateDTOs.Save command);

    void update(Long actorId, String templateId, WorkflowTemplateDTOs.Save command);

    void delete(Long actorId, String templateId, long expectedRevision);

    WorkflowTemplateDTOs.ExecutionConfig queryExecutionConfig(String templateId);

    WorkflowTemplateDTOs.ExecutionConfig saveExecutionConfig(
        Long actorId, String templateId, WorkflowTemplateDTOs.ExecutionConfigSave command);

    void enable(Long actorId, String templateId, long expectedRevision);

    void disable(Long actorId, String templateId, long expectedRevision);

    List<WorkflowTemplateDTOs.Option> queryOptions();

    PageResult<WorkflowTemplateDTOs.PublicCard> queryVisiblePage(
        WorkflowTemplateDTOs.PublicQuery query, PageQuery pageQuery);

    WorkflowTemplateDTOs.PublicDetail queryVisibleDetail(String templateId);

    WorkflowTemplateDTOs.CreationConfig queryCreationConfig(String templateId);

    WorkflowTemplateDTOs.DiscoveryHome queryDiscoveryHome();
}
