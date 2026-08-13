package org.dromara.aivideo.script.dto;

import java.time.LocalDateTime;

public record ScriptVersionSummaryDTO(String versionId, String parentVersionId, Integer versionNo,
                                      String sourceType, Integer effectiveCharacterCount,
                                      Integer estimatedDurationSeconds, String preview,
                                      LocalDateTime createdAt) {
}
