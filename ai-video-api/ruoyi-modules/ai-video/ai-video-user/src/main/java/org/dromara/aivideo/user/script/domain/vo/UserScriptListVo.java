package org.dromara.aivideo.user.script.domain.vo;

import org.dromara.aivideo.script.dto.UserScriptListDTO;

import java.time.LocalDateTime;

/** 个人文案列表响应。 */
public record UserScriptListVo(
    String scriptId, String displayTitle, String currentVersionId,
    Integer versionNo, Long versionCount, String sourceType,
    Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
    String preview, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static UserScriptListVo from(UserScriptListDTO dto) {
        return new UserScriptListVo(dto.scriptId(), dto.displayTitle(), dto.currentVersionId(),
            dto.versionNo(), dto.versionCount(), dto.sourceType(), dto.effectiveCharacterCount(),
            dto.estimatedDurationSeconds(), dto.preview(), dto.createdAt(), dto.updatedAt());
    }
}
