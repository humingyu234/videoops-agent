package org.dromara.aivideo.questionnaire.dto;

import java.util.List;

/** 模型生成下一题所需的稳定上下文。 */
public record QuestionnaireGenerationRequestDTO(
    String industryCode,
    String purposeCode,
    int targetDurationSeconds,
    String demandText,
    List<QuestionnaireAnswerDTO> answerHistory,
    List<String> knowledgeExcerpts,
    List<String> copyRules
) {

    public QuestionnaireGenerationRequestDTO {
        answerHistory = List.copyOf(answerHistory);
        knowledgeExcerpts = List.copyOf(knowledgeExcerpts);
        copyRules = List.copyOf(copyRules);
    }
}
