package org.dromara.aivideo.user.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.workflow.domain.bo.CreateWorkflowOrderBo;
import org.dromara.aivideo.user.workflow.domain.vo.WorkflowOrderVo;
import org.dromara.aivideo.user.workflow.domain.vo.WorkflowOrderDetailVo;
import org.dromara.aivideo.workflow.order.dto.CreateWorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderOwnerDTO;
import org.dromara.aivideo.workflow.order.service.IWorkflowOrderService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-visible order creation boundary for discovery templates. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/workflow-orders")
public class WorkflowOrderController {
    private final IWorkflowOrderService orderService;
    private final AppLoginHelper loginHelper;

    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @PostMapping
    public R<WorkflowOrderVo> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateWorkflowOrderBo body) {
        return R.ok(WorkflowOrderVo.from(orderService.create(
            currentOwner(),
            new CreateWorkflowOrderDTO(body.getTemplateId(), body.getSchemaHash(), idempotencyKey, body.getInputs()))));
    }

    @SaCheckPermission(value = "aivideo:task:query", type = "app")
    @GetMapping("/{orderId}")
    public R<WorkflowOrderDetailVo> detail(@org.springframework.web.bind.annotation.PathVariable String orderId) {
        return R.ok(WorkflowOrderDetailVo.from(orderService.queryOwnedDetail(currentOwner(), orderId)));
    }

    private WorkflowOrderOwnerDTO currentOwner() {
        AppPrincipalSnapshotDTO principal = loginHelper.getPrincipal();
        AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
        if (workspace == null || workspace.tenantId() == null || workspace.workspaceKey() == null
            || workspace.workspaceKey().isBlank() || principal.appUserId() == null) {
            throw new ServiceException("当前工作区无效");
        }
        return new WorkflowOrderOwnerDTO(workspace.tenantId(), workspace.workspaceKey(), principal.appUserId());
    }
}
