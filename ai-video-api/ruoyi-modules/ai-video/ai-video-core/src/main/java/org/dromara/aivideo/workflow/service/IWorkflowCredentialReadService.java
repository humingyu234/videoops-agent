package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;

/** 基础设施边界的凭据解密能力；调用方使用后必须立即清零返回数组。 */
public interface IWorkflowCredentialReadService {

    char[] decryptForUse(WorkflowCredentialPurpose purpose, String ciphertext);
}
