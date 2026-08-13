package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.enums.WorkflowCredentialPurpose;

/**
 * 工作流凭据只写加密边界。
 */
public interface IWorkflowCredentialWriteService {

    String encryptForStorage(WorkflowCredentialPurpose purpose, char[] plaintext);
}
