package org.dromara.aivideo.workflow.order.service;

import org.dromara.aivideo.workflow.order.dto.CreateWorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDetailDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderOwnerDTO;

public interface IWorkflowOrderService {
    WorkflowOrderDTO create(WorkflowOrderOwnerDTO owner, CreateWorkflowOrderDTO command);

    WorkflowOrderDetailDTO queryOwnedDetail(WorkflowOrderOwnerDTO owner, String orderId);

}
