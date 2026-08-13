package org.dromara.aivideo.script.dto;

import org.dromara.aivideo.questionnaire.dto.QuestionnaireAnswerDTO;

import java.util.List;

/** 文案模型生成所需的稳定上下文。 */
public record ScriptGenerationRequestDTO(
    String industryCode,
    String purposeCode,
    int targetDurationSeconds,
    String demandText,
    List<QuestionnaireAnswerDTO> answerHistory,
    List<String> knowledgeExcerpts,
    List<String> copyRules
) {

    public ScriptGenerationRequestDTO {
        answerHistory = List.copyOf(answerHistory);
        knowledgeExcerpts = List.copyOf(knowledgeExcerpts);
        copyRules = List.copyOf(copyRules);
    }
}
