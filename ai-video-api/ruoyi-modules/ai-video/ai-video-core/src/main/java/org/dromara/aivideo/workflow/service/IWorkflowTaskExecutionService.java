package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;

import java.time.Instant;

/** Workflow-specific execution boundary implemented by the RunningHub workflow module. */
public interface IWorkflowTaskExecutionService {
    AiTaskDispatchResultDTO dispatch(AiTaskLeaseDTO lease, WorkflowAiTaskPayloadDTO payload);

    /** Recovers workflow leases from durable submission facts without ever blindly repeating POST. */
    int recoverExpired(Instant now, int limit);
}
