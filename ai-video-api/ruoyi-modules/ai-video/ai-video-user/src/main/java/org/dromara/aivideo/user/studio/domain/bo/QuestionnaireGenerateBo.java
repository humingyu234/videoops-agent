package org.dromara.aivideo.user.studio.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 用户端知识增强问卷生成请求。 */
public record QuestionnaireGenerateBo(
    @NotBlank @Size(max = 64) String industryCode,
    @NotBlank @Size(max = 64) String purposeCode,
    @Positive @Max(3600) int durationSeconds,
    @Size(max = 4000) String demandText,
    @Size(max = 10) List<@NotBlank @Size(max = 64) String> answeredSlots,
    @Size(max = 5) List<@Valid QuestionnaireAnswerBo> answerHistory
) {

    public QuestionnaireGenerateBo {
        industryCode = normalize(industryCode);
        purposeCode = normalize(purposeCode);
        demandText = demandText == null ? "" : normalize(demandText);
        answeredSlots = answeredSlots == null ? List.of() : answeredSlots.stream()
            .map(QuestionnaireGenerateBo::normalize)
            .distinct()
            .toList();
        answerHistory = answerHistory == null ? List.of() : List.copyOf(answerHistory);
    }

    /** 兼容原型接入前的调用方。 */
    public QuestionnaireGenerateBo(String industryCode, String purposeCode, int durationSeconds,
                                   String demandText, List<String> answeredSlots) {
        this(industryCode, purposeCode, durationSeconds, demandText, answeredSlots, List.of());
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
