package org.dromara.aivideo.questionnaire.service;

import org.dromara.aivideo.questionnaire.dto.QuestionnaireGeneratedQuestionDTO;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireGenerationRequestDTO;

import java.util.Optional;

/** 问卷模型服务。 */
public interface IQuestionnaireModelService {

    /**
     * 基于全部有效历史生成且只生成下一题；信息足够时返回空。
     *
     * @param request 生成上下文
     * @return 下一题或完成标记
     */
    Optional<QuestionnaireGeneratedQuestionDTO> generateNext(QuestionnaireGenerationRequestDTO request);
}
