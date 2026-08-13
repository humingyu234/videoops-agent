package org.dromara.aivideo.identity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 创作端身份域可写入审计列的操作者类型。
 */
@Getter
@RequiredArgsConstructor
public enum AppActorType {

    /**
     * 创作端用户。
     */
    APP_USER("app_user"),

    /**
     * 运营端用户。
     */
    SYS_USER("sys_user");

    /**
     * 数据库存储值。
     */
    @EnumValue
    private final String value;
}
