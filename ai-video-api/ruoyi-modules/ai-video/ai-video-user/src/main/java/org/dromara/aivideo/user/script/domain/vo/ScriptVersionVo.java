package org.dromara.aivideo.user.script.domain.vo;

import org.apache.ibatis.type.Alias;
import org.dromara.aivideo.script.dto.ScriptVersionDTO;

import java.time.LocalDateTime;

/** 个人文案版本响应。 */
@Alias("UserScriptVersionVo")
public record ScriptVersionVo(
    String scriptId, String versionId, String parentVersionId, Integer versionNo,
    String sourceType, String scriptText, Integer effectiveCharacterCount,
    Integer estimatedDurationSeconds, LocalDateTime createdAt
) {
    public static ScriptVersionVo from(ScriptVersionDTO dto) {
        return new ScriptVersionVo(dto.scriptId(), dto.versionId(), dto.parentVersionId(), dto.versionNo(),
            dto.sourceType(), dto.scriptText(), dto.effectiveCharacterCount(),
            dto.estimatedDurationSeconds(), dto.createdAt());
    }
}
