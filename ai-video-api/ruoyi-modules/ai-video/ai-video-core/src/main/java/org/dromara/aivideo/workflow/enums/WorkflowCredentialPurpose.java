package org.dromara.aivideo.workflow.enums;

import java.nio.charset.StandardCharsets;

/**
 * 工作流凭据的加密用途，用于构造稳定且非机密的 AES-GCM AAD。
 */
public enum WorkflowCredentialPurpose {

    RUNNINGHUB_API_KEY("ai-video:workflow-credential:runninghub-api-key:v1"),
    RUNNINGHUB_ACCESS_PASSWORD("ai-video:workflow-credential:runninghub-access-password:v1");

    private final String aad;

    WorkflowCredentialPurpose(String aad) {
        this.aad = aad;
    }

    public String aad() {
        return aad;
    }

    public byte[] aadBytes() {
        return aad.getBytes(StandardCharsets.UTF_8);
    }
}
