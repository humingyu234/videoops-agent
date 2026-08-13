package org.dromara.aivideo.infra.runninghub.listener;

import org.dromara.aivideo.infra.runninghub.RunningHubWorkflowDispatchProperties;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@Tag("dev")
class RunningHubWorkflowTaskSchedulerTest {

    @Test
    void submitsAReadyWorkflowWithoutWaitingForTheGenericTaskScheduler() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        RunningHubWorkflowDispatchProperties properties = new RunningHubWorkflowDispatchProperties();
        properties.setWorkerId("runninghub-test-worker");
        properties.setConcurrencyLimit(100);
        RunningHubWorkflowTaskScheduler scheduler = new RunningHubWorkflowTaskScheduler(
            taskService, properties, Runnable::run);
        AiTaskLeaseDTO lease = new AiTaskLeaseDTO("701", "801", null, "lease-token", "runninghub-test-worker",
            "app_user", "7", null, 1, 0, 1);
        when(taskService.claimNextWorkflow("runninghub-test-worker", 100)).thenReturn(lease).thenReturn(null);

        scheduler.dispatchAvailable();

        verify(taskService).dispatchClaimedWorkflow(lease);
    }

    @Test
    void releasesTheLeaseWhenTheExecutorRejectsTheClaimedWorkflow() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        RunningHubWorkflowDispatchProperties properties = new RunningHubWorkflowDispatchProperties();
        properties.setWorkerId("runninghub-test-worker");
        properties.setConcurrencyLimit(100);
        RunningHubWorkflowTaskScheduler scheduler = new RunningHubWorkflowTaskScheduler(taskService, properties,
            task -> { throw new java.util.concurrent.RejectedExecutionException("executor is closed"); });
        AiTaskLeaseDTO lease = new AiTaskLeaseDTO("702", "802", null, "lease-token", "runninghub-test-worker",
            "app_user", "7", null, 1, 0, 1);
        when(taskService.claimNextWorkflow("runninghub-test-worker", 100)).thenReturn(lease);

        scheduler.dispatchAvailable();

        verify(taskService).releaseClaimedWorkflow(lease);
        verify(taskService, never()).dispatchClaimedWorkflow(lease);
    }
}
