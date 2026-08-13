package org.dromara.aivideo.script.dto;

import java.time.LocalDateTime;

public record UserScriptSaveResultDTO(String scriptId, String currentVersionId, String scriptRevision,
                                      Integer versionNo, String displayTitle, Integer effectiveCharacterCount,
                                      Integer estimatedDurationSeconds, LocalDateTime createdAt, boolean reused) {
}
