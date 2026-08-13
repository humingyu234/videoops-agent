package org.dromara.aivideo.task.service;

import org.dromara.aivideo.task.enums.AiTaskType;

/** Free-plan policy boundary; no balance fact is created for timeline tasks. */
public interface IFreeAiTaskQuotaPolicyService {

    FrozenQuota freeze(AiTaskType taskType, String requestedPolicyVersion, long requestedEstimatedUsage);

    record FrozenQuota(String quotaPolicyVersion, long estimatedUsage) {
    }
}
