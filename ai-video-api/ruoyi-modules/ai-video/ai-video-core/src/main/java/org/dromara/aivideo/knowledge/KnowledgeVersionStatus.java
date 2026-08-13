package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识版本状态。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeVersionStatus {

    DRAFT("draft"),
    REVIEWING("reviewing"),
    PUBLISHED("published"),
    RETIRED("retired");

    @EnumValue
    private final String code;
}
