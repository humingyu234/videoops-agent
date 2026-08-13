package org.dromara.aivideo.infra.timeline.listener;

import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class TimelineTaskSchedulerTest {

    @Test
    void doesNothingWhenTimelineMediaInfrastructureIsDisabled() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        TimelineInfrastructureProperties properties = schedulerProperties(false);

        new TimelineTaskScheduler(taskService, properties).executeOnce();

        verifyNoInteractions(taskService);
    }

    @Test
    void handlesAnEmptyQueueWithoutCreatingLocalTaskState() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        when(taskService.dispatchNext(anyString(), anyInt(), anyInt()))
            .thenReturn(new AiTaskDispatchResultDTO("none", null, null));
        TimelineTaskScheduler scheduler = new TimelineTaskScheduler(taskService, schedulerProperties(true));

        scheduler.executeOnce();

        verify(taskService).recoverExpired(any(Instant.class), eq(7));
        verify(taskService).compensatePendingOutputs(any(Instant.class), eq(7));
        verify(taskService).dispatchNext("timeline-test-worker", 2, 4);
    }

    @Test
    void forwardsTheValidatedWorkerAndConcurrencyLimitsToTheServiceLayer() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        TimelineInfrastructureProperties properties = schedulerProperties(true);
        properties.setWorkerId("stable-worker-id");
        properties.setPerUserConcurrencyLimit(3);
        properties.setSystemConcurrencyLimit(5);

        new TimelineTaskScheduler(taskService, properties).executeOnce();

        verify(taskService).dispatchNext("stable-worker-id", 3, 5);
    }

    @Test
    void keepsOneLocalExecutionSlotWhileAnEarlierCycleIsStillRunning() throws Exception {
        IAiTaskService taskService = mock(IAiTaskService.class);
        TimelineTaskScheduler scheduler = new TimelineTaskScheduler(taskService, schedulerProperties(true));
        CountDownLatch enteredRecovery = new CountDownLatch(1);
        CountDownLatch releaseRecovery = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredRecovery.countDown();
            assertThat(releaseRecovery.await(5, TimeUnit.SECONDS)).isTrue();
            return 0;
        }).when(taskService).recoverExpired(any(Instant.class), anyInt());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstCycle = executor.submit(scheduler::executeOnce);
            assertThat(enteredRecovery.await(5, TimeUnit.SECONDS)).isTrue();

            scheduler.executeOnce();

            verify(taskService, times(1)).recoverExpired(any(Instant.class), anyInt());
            releaseRecovery.countDown();
            firstCycle.get(5, TimeUnit.SECONDS);
            verify(taskService, times(1)).dispatchNext("timeline-test-worker", 2, 4);
        } finally {
            releaseRecovery.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void continuesTheRemainingMaintenanceOperationsAfterOneOperationFails() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        doThrow(new IllegalStateException("expected test failure")).doReturn(0)
            .when(taskService).recoverExpired(any(Instant.class), anyInt());
        TimelineTaskScheduler scheduler = new TimelineTaskScheduler(taskService, schedulerProperties(true));

        scheduler.executeOnce();
        scheduler.executeOnce();

        verify(taskService, times(2)).recoverExpired(any(Instant.class), eq(7));
        verify(taskService, times(2)).compensatePendingOutputs(any(Instant.class), eq(7));
        verify(taskService, times(2)).dispatchNext("timeline-test-worker", 2, 4);
    }

    @Test
    void shutdownPreventsNewSchedulingCycles() {
        IAiTaskService taskService = mock(IAiTaskService.class);
        TimelineTaskScheduler scheduler = new TimelineTaskScheduler(taskService, schedulerProperties(true));

        scheduler.shutdown();
        scheduler.executeOnce();

        verifyNoInteractions(taskService);
    }

    private static TimelineInfrastructureProperties schedulerProperties(boolean enabled) {
        TimelineInfrastructureProperties properties = new TimelineInfrastructureProperties();
        properties.setEnabled(enabled);
        properties.setWorkerId("timeline-test-worker");
        properties.setPerUserConcurrencyLimit(2);
        properties.setSystemConcurrencyLimit(4);
        properties.setRecoveryBatchLimit(7);
        return properties;
    }
}
