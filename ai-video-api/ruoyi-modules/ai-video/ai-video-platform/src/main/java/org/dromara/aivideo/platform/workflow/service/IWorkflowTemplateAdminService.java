package org.dromara.aivideo.platform.workflow.service;

import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.CreateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.ExecutionConfigBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.StatusChangeBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.UpdateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.WorkflowTemplateQueryBo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.ExecutionConfigVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.OptionVo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.SummaryVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

/** 运营端工作流模板 HTTP 适配服务。 */
public interface IWorkflowTemplateAdminService {

    PageResult<SummaryVo> page(WorkflowTemplateQueryBo query, PageQuery pageQuery);

    DetailVo detail(String templateId);

    String create(CreateWorkflowTemplateBo command, Long operatorId);

    void update(String templateId, UpdateWorkflowTemplateBo command, Long operatorId);

    void delete(String templateId, long expectedRevision, Long operatorId);

    ExecutionConfigVo queryExecutionConfig(String templateId);

    ExecutionConfigVo saveExecutionConfig(String templateId, ExecutionConfigBo command, Long operatorId);

    void enable(String templateId, StatusChangeBo command, Long operatorId);

    void disable(String templateId, StatusChangeBo command, Long operatorId);

    List<OptionVo> options();
}
