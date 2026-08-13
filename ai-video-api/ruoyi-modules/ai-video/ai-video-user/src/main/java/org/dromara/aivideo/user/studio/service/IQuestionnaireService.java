package org.dromara.aivideo.user.studio.service;

import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.QuestionnaireGenerateVo;

/** 用户端知识增强问卷服务。 */
public interface IQuestionnaireService {

    /** 解析知识上下文并生成仅包含缺失槽位的问卷。 */
    QuestionnaireGenerateVo generate(QuestionnaireGenerateBo request);
}
