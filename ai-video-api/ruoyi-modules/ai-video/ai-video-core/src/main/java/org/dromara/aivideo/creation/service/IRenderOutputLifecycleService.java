package org.dromara.aivideo.creation.service;

import org.dromara.aivideo.creation.dto.PendingRenderOutputDTO;
import org.dromara.aivideo.task.dto.AiTaskLeaseDTO;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;

import java.time.Instant;

/** Coordinates a render output's pending fact, object upload, and final CAS projection. */
public interface IRenderOutputLifecycleService {

    PendingRenderOutputDTO registerPendingOutput(AiTaskLeaseDTO lease, String outputConfigDigest);

    boolean storeAndComplete(AiTaskLeaseDTO lease, PendingRenderOutputDTO pending,
                             TimelineRenderOutputHandle output, Instant now);

    int compensatePendingOutputs(Instant olderThan, int limit);
}
