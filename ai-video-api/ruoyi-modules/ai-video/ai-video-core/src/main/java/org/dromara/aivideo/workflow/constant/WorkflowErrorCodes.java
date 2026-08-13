package org.dromara.aivideo.workflow.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 工作流模板模块稳定业务错误码。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkflowErrorCodes {

    public static final int WORKFLOW_TEMPLATE_UNAVAILABLE = 46501;
    public static final int WORKFLOW_EXECUTION_CONFIG_UNAVAILABLE = 46503;
    public static final int WORKFLOW_INPUT_INVALID = 46505;
    public static final int WORKFLOW_REVISION_CONFLICT = 46519;
    public static final int WORKFLOW_REFERENCE_CONFLICT = 46520;
    public static final int WORKFLOW_CONFIGURATION_INVALID = 46521;
    public static final int WORKFLOW_PARAMETER_INSPECTION_FAILED = 46522;
}
