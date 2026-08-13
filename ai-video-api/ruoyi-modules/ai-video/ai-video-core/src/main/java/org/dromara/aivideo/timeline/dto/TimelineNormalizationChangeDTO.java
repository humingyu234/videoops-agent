package org.dromara.aivideo.timeline.dto;

public record TimelineNormalizationChangeDTO(
    String elementId,
    String changeType,
    String beforeDigest,
    String afterDigest,
    String safeMessage
) {
    public TimelineNormalizationChangeDTO {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
    }
}
