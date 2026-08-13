package org.dromara.aivideo.user.asset.domain.vo;

import org.dromara.aivideo.asset.dto.UploadSessionDTO;

import java.time.LocalDateTime;
import java.util.Map;

/** User-safe upload session details; file records and object keys remain internal. */
public record WorkflowUploadSessionVo(
    String uploadId,
    String status,
    LocalDateTime expiresAt,
    String singlePutUrl,
    Map<String, String> requiredHeaders,
    String assetId,
    String assetStatus
) {
    public static WorkflowUploadSessionVo from(UploadSessionDTO source) {
        return from(source, source.singlePutUrl());
    }

    public static WorkflowUploadSessionVo from(UploadSessionDTO source, String singlePutUrl) {
        return new WorkflowUploadSessionVo(source.uploadId(), source.status(), source.expiresAt(),
            singlePutUrl, source.requiredHeaders(), source.assetId(), source.assetStatus());
    }
}
