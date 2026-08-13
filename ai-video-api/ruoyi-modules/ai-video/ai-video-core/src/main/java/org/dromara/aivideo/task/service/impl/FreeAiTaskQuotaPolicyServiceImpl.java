package org.dromara.aivideo.task.service.impl;

import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IFreeAiTaskQuotaPolicyService;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

/** Free timeline tasks deliberately freeze a stable zero-usage policy fact. */
@Service
public class FreeAiTaskQuotaPolicyServiceImpl implements IFreeAiTaskQuotaPolicyService {

    public static final String POLICY_VERSION = "timeline-free-1";
    public static final String WORKFLOW_POLICY_VERSION = "workflow-free-1";

    @Override
    public FrozenQuota freeze(AiTaskType taskType, String requestedPolicyVersion, long requestedEstimatedUsage) {
        String expected = taskType == AiTaskType.WORKFLOW_TEMPLATE_GENERATE
            || taskType == AiTaskType.WORKFLOW_TEMPLATE_TEST ? WORKFLOW_POLICY_VERSION : POLICY_VERSION;
        if (taskType == null || !expected.equals(requestedPolicyVersion) || requestedEstimatedUsage != 0L) {
            throw new ServiceException("时间轴任务免费策略无效", TimelineErrorCodes.TIMELINE_DOCUMENT_INVALID);
        }
        return new FrozenQuota(expected, 0L);
    }
}
