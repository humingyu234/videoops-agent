package org.dromara.aivideo.questionnaire.dto;

import java.util.List;

/** 模型生成的单个后续问题。 */
public record QuestionnaireGeneratedQuestionDTO(
    String id,
    String title,
    String description,
    boolean required,
    List<QuestionnaireGeneratedOptionDTO> options
) {

    public QuestionnaireGeneratedQuestionDTO {
        options = List.copyOf(options);
    }
}
