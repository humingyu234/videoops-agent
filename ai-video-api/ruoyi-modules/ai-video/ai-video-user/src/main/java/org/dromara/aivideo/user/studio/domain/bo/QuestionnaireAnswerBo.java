package org.dromara.aivideo.user.studio.domain.bo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 用户端问卷历史回答。 */
public record QuestionnaireAnswerBo(
    @NotBlank @Size(max = 64) String questionId,
    @NotBlank @Size(max = 200) String questionTitle,
    @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 100) String> selectedValues,
    @NotEmpty @Size(max = 6) List<@NotBlank @Size(max = 100) String> selectedLabels
) {

    public QuestionnaireAnswerBo {
        questionId = normalize(questionId);
        questionTitle = normalize(questionTitle);
        selectedValues = normalizeList(selectedValues);
        selectedLabels = normalizeList(selectedLabels);
    }

    @AssertTrue(message = "已选答案值与标签必须一一对应")
    public boolean isSelectionPairCountValid() {
        return selectedValues.size() == selectedLabels.size();
    }

    private static List<String> normalizeList(List<String> values) {
        return values == null ? List.of() : values.stream()
            .map(QuestionnaireAnswerBo::normalize)
            .distinct()
            .toList();
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
