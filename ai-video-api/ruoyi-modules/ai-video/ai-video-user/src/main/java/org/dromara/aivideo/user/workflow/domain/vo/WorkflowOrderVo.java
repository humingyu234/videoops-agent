package org.dromara.aivideo.user.workflow.domain.vo;

import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDTO;

public record WorkflowOrderVo(String orderId, String taskId, String templateId, String status) {
    public static WorkflowOrderVo from(WorkflowOrderDTO source) {
        return new WorkflowOrderVo(source.orderId(), source.taskId(), source.templateId(), source.status());
    }
}
