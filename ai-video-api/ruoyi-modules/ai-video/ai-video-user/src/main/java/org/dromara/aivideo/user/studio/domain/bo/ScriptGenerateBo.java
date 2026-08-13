package org.dromara.aivideo.user.studio.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 用户端文案生成请求。 */
public record ScriptGenerateBo(
    @NotBlank @Size(max = 64) String industryCode,
    @NotBlank @Size(max = 64) String purposeCode,
    @Positive @Max(3600) int durationSeconds,
    @Size(max = 4000) String demandText,
    @Size(min = 1, max = 5) List<@Valid QuestionnaireAnswerBo> answerHistory
) {

    public ScriptGenerateBo {
        industryCode = normalize(industryCode);
        purposeCode = normalize(purposeCode);
        demandText = demandText == null ? "" : normalize(demandText);
        answerHistory = answerHistory == null ? List.of() : List.copyOf(answerHistory);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
