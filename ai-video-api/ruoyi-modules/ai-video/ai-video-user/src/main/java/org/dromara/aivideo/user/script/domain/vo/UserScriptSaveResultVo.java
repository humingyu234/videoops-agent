package org.dromara.aivideo.user.script.domain.vo;

import org.dromara.aivideo.script.dto.UserScriptSaveResultDTO;

import java.time.LocalDateTime;

/** 创建或编辑个人文案响应。 */
public record UserScriptSaveResultVo(
    String scriptId, String currentVersionId, String scriptRevision, Integer versionNo,
    String displayTitle, Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
    LocalDateTime createdAt, boolean reused
) {
    public static UserScriptSaveResultVo from(UserScriptSaveResultDTO dto) {
        return new UserScriptSaveResultVo(dto.scriptId(), dto.currentVersionId(), dto.scriptRevision(),
            dto.versionNo(), dto.displayTitle(), dto.effectiveCharacterCount(),
            dto.estimatedDurationSeconds(), dto.createdAt(), dto.reused());
    }
}
