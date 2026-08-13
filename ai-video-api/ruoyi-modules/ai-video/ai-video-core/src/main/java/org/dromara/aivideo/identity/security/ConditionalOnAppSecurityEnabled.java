package org.dromara.aivideo.identity.security;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅在创作端安全开关的原始配置值为 {@code true}（忽略大小写但不忽略空白）时启用组件。
 *
 * <p>不能使用 {@code @ConditionalOnProperty}，因为它会将 {@code " true "} 之类的值
 * 归一化为已启用；创作端必须在任何配置歧义下保持失效关闭。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(AppSecurityEnabledCondition.class)
public @interface ConditionalOnAppSecurityEnabled {
}
