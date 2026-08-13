package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 知识所属领域代码。 */
@Getter
@RequiredArgsConstructor
public enum KnowledgeDomainCode {

    COPYWRITING("copywriting");

    @EnumValue
    private final String code;
}
