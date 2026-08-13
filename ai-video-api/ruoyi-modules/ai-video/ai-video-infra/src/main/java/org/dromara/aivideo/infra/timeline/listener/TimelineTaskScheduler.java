package org.dromara.aivideo.infra.timeline.listener;

import jakarta.annotation.PreDestroy;
import org.dromara.aivideo.infra.timeline.TimelineInfrastructureProperties;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives task recovery and dispatch from one local, non-reentrant execution slot.
 */
public final class TimelineTaskScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TimelineTaskScheduler.class);

    private final IAiTaskService aiTaskService;
    private final TimelineInfrastructureProperties properties;
    private final AtomicBoolean localExecutionSlot = new AtomicBoolean();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    public TimelineTaskScheduler(IAiTaskService aiTaskService, TimelineInfrastructureProperties properties) {
        this.aiTaskService = aiTaskService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${aivideo.timeline.poll-delay:PT1S}")
    public void executeOnce() {
        if (!properties.isEnabled() || shutdown.get() || !localExecutionSlot.compareAndSet(false, true)) {
            return;
        }
        try {
            if (shutdown.get()) {
                return;
            }
            Instant now = Instant.now();
            runSafely("recoverExpired", () -> aiTaskService.recoverExpired(now, properties.getRecoveryBatchLimit()));
            runSafely("compensatePendingOutputs",
                () -> aiTaskService.compensatePendingOutputs(now, properties.getRecoveryBatchLimit()));
            runSafely("dispatchNext", () -> aiTaskService.dispatchNext(
                properties.getWorkerId(), properties.getPerUserConcurrencyLimit(), properties.getSystemConcurrencyLimit()));
        } finally {
            localExecutionSlot.set(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown.set(true);
    }

    private static void runSafely(String operation, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ignored) {
            LOG.warn("Timeline task scheduler operation {} failed", operation);
        }
    }
}
