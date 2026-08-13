package org.dromara.aivideo.platform.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 运营端工作流模板管理入口。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/workflow-templates")
public class WorkflowTemplateAdminController extends BaseController {

    private final IWorkflowTemplateAdminService workflowTemplateAdminService;

    @SaCheckPermission("aivideo:workflow-template:query")
    @GetMapping("")
    public R<PageResult<SummaryVo>> page(@Valid WorkflowTemplateQueryBo query, PageQuery pageQuery) {
        return R.ok(workflowTemplateAdminService.page(query, pageQuery));
    }

    @SaCheckPermission("aivideo:workflow-template:add")
    @Log(title = "工作流模板管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("")
    public R<String> create(@Valid @RequestBody CreateWorkflowTemplateBo command) {
        return R.data(workflowTemplateAdminService.create(command, LoginHelper.getUserId()));
    }

    @SaCheckPermission("aivideo:workflow-template:query")
    @GetMapping("/{templateId}")
    public R<DetailVo> detail(@PathVariable String templateId) {
        return R.ok(workflowTemplateAdminService.detail(templateId));
    }

    @SaCheckPermission("aivideo:workflow-template:edit")
    @Log(title = "工作流模板管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{templateId}")
    public R<Void> update(@PathVariable String templateId,
                          @Valid @RequestBody UpdateWorkflowTemplateBo command) {
        workflowTemplateAdminService.update(templateId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:workflow-template:remove")
    @Log(title = "工作流模板管理", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{templateId}")
    public R<Void> delete(@PathVariable String templateId,
                          @RequestParam @PositiveOrZero long expectedRevision) {
        workflowTemplateAdminService.delete(templateId, expectedRevision, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:workflow-template:query")
    @GetMapping("/{templateId}/execution-config")
    public R<ExecutionConfigVo> executionConfig(@PathVariable String templateId) {
        return R.ok(workflowTemplateAdminService.queryExecutionConfig(templateId));
    }

    @SaCheckPermission("aivideo:workflow-template:edit")
    @Log(title = "工作流模板执行配置", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @RepeatSubmit
    @PutMapping("/{templateId}/execution-config")
    public R<ExecutionConfigVo> saveExecutionConfig(@PathVariable String templateId,
                                                     @Valid @RequestBody ExecutionConfigBo command) {
        return R.ok(workflowTemplateAdminService.saveExecutionConfig(
            templateId, command, LoginHelper.getUserId()));
    }

    @SaCheckPermission("aivideo:workflow-template:enable")
    @Log(title = "工作流模板启用", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{templateId}/enable")
    public R<Void> enable(@PathVariable String templateId, @Valid @RequestBody StatusChangeBo command) {
        workflowTemplateAdminService.enable(templateId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:workflow-template:disable")
    @Log(title = "工作流模板停用", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{templateId}/disable")
    public R<Void> disable(@PathVariable String templateId, @Valid @RequestBody StatusChangeBo command) {
        workflowTemplateAdminService.disable(templateId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:workflow-template:query")
    @GetMapping("/options")
    public R<List<OptionVo>> options() {
        return R.ok(workflowTemplateAdminService.options());
    }
}
