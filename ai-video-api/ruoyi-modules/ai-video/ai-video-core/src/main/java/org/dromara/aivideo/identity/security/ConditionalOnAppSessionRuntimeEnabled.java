package org.dromara.aivideo.identity.security;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅在用户端完整 app 登录链或运营端最小 app 会话撤销运行时显式启用时装配组件。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(AppSessionRuntimeEnabledCondition.class)
public @interface ConditionalOnAppSessionRuntimeEnabled {
}
