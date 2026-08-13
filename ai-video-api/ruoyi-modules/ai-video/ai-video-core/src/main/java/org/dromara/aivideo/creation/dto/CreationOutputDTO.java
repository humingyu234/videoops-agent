package org.dromara.aivideo.creation.dto;

import java.time.Instant;

/** Trusted latest timeline-render output facts for the creation-project boundary. */
public record CreationOutputDTO(
    String projectId,
    String outputAssetId,
    String taskId,
    Instant createdAt
) {
}
