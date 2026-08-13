package org.dromara.aivideo.script.dto;

import java.time.LocalDateTime;

public record UserScriptListDTO(String scriptId, String displayTitle, String currentVersionId,
                                Integer versionNo, Long versionCount, String sourceType,
                                Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
                                String preview, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
