package org.dromara.aivideo.script.dto;

import java.time.LocalDateTime;

public record ScriptVersionDTO(String scriptId, String versionId, String parentVersionId, Integer versionNo,
                               String sourceType, String scriptText, Integer effectiveCharacterCount,
                               Integer estimatedDurationSeconds, LocalDateTime createdAt) {
}
