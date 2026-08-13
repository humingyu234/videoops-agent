package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识类型代码。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeTypeCode {

    PRIMARY_TEMPLATE("primary_template"),
    WRITING_TECHNIQUE("writing_technique"),
    PSYCHOLOGY("psychology"),
    CASE("case"),
    MANDATORY_RULE("mandatory_rule");

    @EnumValue
    private final String code;
}
