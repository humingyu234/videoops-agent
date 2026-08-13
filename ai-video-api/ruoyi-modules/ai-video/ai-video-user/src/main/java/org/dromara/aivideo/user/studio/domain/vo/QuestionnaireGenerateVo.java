package org.dromara.aivideo.user.studio.domain.vo;

import java.util.List;

/** 用户端知识增强问卷生成结果。 */
public record QuestionnaireGenerateVo(
    List<QuestionnaireQuestionVo> questions,
    List<String> knowledgeVersionIds,
    String knowledgeHash,
    String modelMode
) {

    public QuestionnaireGenerateVo {
        questions = List.copyOf(questions);
        knowledgeVersionIds = List.copyOf(knowledgeVersionIds);
    }
}
