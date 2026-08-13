package org.dromara.aivideo.infra.questionnaire;

import org.dromara.aivideo.questionnaire.service.IQuestionnaireModelService;
import org.dromara.aivideo.script.service.IScriptModelService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** DeepSeek 动态问卷条件装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeepSeekQuestionnaireProperties.class)
public class DeepSeekQuestionnaireConfiguration {

    @Bean
    @Conditional(DeepSeekQuestionnaireConfiguredCondition.class)
    public IQuestionnaireModelService questionnaireModelService(DeepSeekQuestionnaireProperties properties) {
        return new DeepSeekQuestionnaireClient(properties);
    }

    @Bean
    @Conditional(DeepSeekQuestionnaireConfiguredCondition.class)
    public IScriptModelService scriptModelService(DeepSeekQuestionnaireProperties properties) {
        return new DeepSeekScriptClient(properties);
    }
}

final class DeepSeekQuestionnaireConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("questionnaire.deepseek.api-key"));
    }
}
