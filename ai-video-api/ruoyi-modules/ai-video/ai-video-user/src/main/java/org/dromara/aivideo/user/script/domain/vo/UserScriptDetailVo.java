package org.dromara.aivideo.user.script.domain.vo;

import org.dromara.aivideo.script.dto.ScriptVersionSummaryDTO;
import org.dromara.aivideo.script.dto.UserScriptDetailDTO;

import java.time.LocalDateTime;
import java.util.List;

/** 个人文案详情与历史版本响应。 */
public record UserScriptDetailVo(
    String scriptId, String displayTitle, String scriptRevision, String currentVersionId,
    LocalDateTime createdAt, LocalDateTime updatedAt, ScriptVersionVo currentVersion,
    List<VersionSummaryVo> versions
) {
    public static UserScriptDetailVo from(UserScriptDetailDTO dto) {
        return new UserScriptDetailVo(dto.scriptId(), dto.displayTitle(), dto.scriptRevision(),
            dto.currentVersionId(), dto.createdAt(), dto.updatedAt(), ScriptVersionVo.from(dto.currentVersion()),
            dto.versions().stream().map(VersionSummaryVo::from).toList());
    }

    /** 历史版本摘要，不返回正文全文。 */
    public record VersionSummaryVo(
        String versionId, String parentVersionId, Integer versionNo, String sourceType,
        Integer effectiveCharacterCount, Integer estimatedDurationSeconds,
        String preview, LocalDateTime createdAt
    ) {
        public static VersionSummaryVo from(ScriptVersionSummaryDTO dto) {
            return new VersionSummaryVo(dto.versionId(), dto.parentVersionId(), dto.versionNo(), dto.sourceType(),
                dto.effectiveCharacterCount(), dto.estimatedDurationSeconds(), dto.preview(), dto.createdAt());
        }
    }
}
