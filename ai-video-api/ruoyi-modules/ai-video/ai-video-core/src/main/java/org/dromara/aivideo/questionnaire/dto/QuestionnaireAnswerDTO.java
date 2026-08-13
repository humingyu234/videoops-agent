package org.dromara.aivideo.questionnaire.dto;

import java.util.List;

/** 问卷单轮回答。 */
public record QuestionnaireAnswerDTO(
    String questionId,
    String questionTitle,
    List<String> selectedValues,
    List<String> selectedLabels
) {

    public QuestionnaireAnswerDTO {
        selectedValues = List.copyOf(selectedValues);
        selectedLabels = List.copyOf(selectedLabels);
    }
}
