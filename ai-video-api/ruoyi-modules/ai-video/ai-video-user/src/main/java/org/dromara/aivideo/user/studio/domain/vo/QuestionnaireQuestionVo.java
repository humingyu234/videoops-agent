package org.dromara.aivideo.user.studio.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** 知识增强问卷中的一个问题。 */
public record QuestionnaireQuestionVo(
    String id,
    String title,
    String description,
    boolean required,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<QuestionnaireOptionVo> options
) {

    public QuestionnaireQuestionVo {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
