package org.dromara.aivideo.asset.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Public upload-session response. It intentionally excludes the internal object key. */
public record UploadSessionDTO(
    String uploadId,
    String fileId,
    String mode,
    String status,
    LocalDateTime expiresAt,
    String singlePutUrl,
    Map<String, String> requiredHeaders,
    String assetId,
    String assetStatus
) {
}
