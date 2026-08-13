package org.dromara.aivideo.script.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserScriptDetailDTO(String scriptId, String displayTitle, String scriptRevision,
                                  String currentVersionId, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  ScriptVersionDTO currentVersion, List<ScriptVersionSummaryDTO> versions) {
}
