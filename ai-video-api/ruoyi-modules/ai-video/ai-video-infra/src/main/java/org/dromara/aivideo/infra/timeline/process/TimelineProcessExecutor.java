package org.dromara.aivideo.infra.timeline.process;

import java.util.function.BooleanSupplier;

/**
 * Executes one prevalidated local media command without a shell.
 */
public interface TimelineProcessExecutor {

    TimelineProcessResult execute(TimelineProcessRequest request,
                                  BooleanSupplier cancellationRequested) throws InterruptedException;

    void cancel(String executionId, String attemptId);
}
