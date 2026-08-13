package org.dromara.aivideo.infra.runninghub.listener;

import jakarta.annotation.PreDestroy;
import org.dromara.aivideo.infra.runninghub.RunningHubWorkflowDispatchProperties;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Claims RunningHub workflow work independently, then performs the remote call on the application executor.
 * The durable lease count is the cluster-wide concurrency guard; this class only prevents local over-submission.
 */
public final class RunningHubWorkflowTaskScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(RunningHubWorkflowTaskScheduler.class);

    private final IAiTaskService aiTaskService;
    private final RunningHubWorkflowDispatchProperties properties;
    private final Executor executor;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public RunningHubWorkflowTaskScheduler(IAiTaskService aiTaskService,
                                           RunningHubWorkflowDispatchProperties properties,
                                           Executor executor) {
        this.aiTaskService = aiTaskService;
        this.properties = properties;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${aivideo.runninghub.workflow-dispatch.poll-delay:PT0.1S}")
    public void dispatchAvailable() {
        if (!properties.isEnabled() || shutdown.get()) {
            return;
        }
        for (int i = 0; i < properties.getConcurrencyLimit() && !shutdown.get(); i++) {
            AiTaskLeaseDTO lease;
            try {
                lease = aiTaskService.claimNextWorkflow(properties.getWorkerId(), properties.getConcurrencyLimit());
            } catch (RuntimeException exception) {
                LOG.warn("RunningHub workflow dispatch claim failed", exception);
                return;
            }
            if (lease == null) {
                return;
            }
            try {
                executor.execute(() -> dispatchClaimed(lease));
            } catch (RejectedExecutionException exception) {
                LOG.warn("RunningHub workflow dispatch was rejected: taskId={}, executionId={}",
                    lease.getTaskId(), lease.getExecutionId(), exception);
                releaseClaim(lease);
                return;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
    }

    private void dispatchClaimed(AiTaskLeaseDTO lease) {
        if (shutdown.get()) {
            releaseClaim(lease);
            return;
        }
        try {
            aiTaskService.dispatchClaimedWorkflow(lease);
        } catch (RuntimeException exception) {
            LOG.warn("RunningHub workflow dispatch failed: taskId={}, executionId={}", lease.getTaskId(),
                lease.getExecutionId(), exception);
        }
    }

    private void releaseClaim(AiTaskLeaseDTO lease) {
        try {
            if (!aiTaskService.releaseClaimedWorkflow(lease)) {
                LOG.warn("RunningHub workflow claim could not be released: taskId={}, executionId={}",
                    lease.getTaskId(), lease.getExecutionId());
            }
        } catch (RuntimeException exception) {
            LOG.warn("RunningHub workflow claim release failed: taskId={}, executionId={}", lease.getTaskId(),
                lease.getExecutionId(), exception);
        }
    }
}
