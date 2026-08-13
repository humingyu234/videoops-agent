package org.dromara.aivideo.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 创作端身份域记录的可持久化状态。
 */
@Getter
@RequiredArgsConstructor
public enum AppIdentityStatus {

    /**
     * 正常可用。
     */
    ACTIVE("active"),

    /**
     * 已失效但保留历史记录。
     */
    INACTIVE("inactive"),

    /**
     * 已停用。
     */
    DISABLED("disabled");

    /**
     * 数据库存储值。
     */
    @EnumValue
    private final String value;
}
