package org.dromara.aivideo.infra.digitalhuman;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class DigitalHumanProviderConditions {

    private DigitalHumanProviderConditions() {
    }

    static boolean voice(Environment environment) {
        return hasText(environment, "digital-human.index-tts2.base-url")
            && hasText(environment, "digital-human.index-tts2.api-key")
            && completePair(environment, "digital-human.index-tts2.basic-user",
                "digital-human.index-tts2.basic-password");
    }

    static boolean video(Environment environment) {
        return hasText(environment, "digital-human.comfy-ui.base-url")
            && completePair(environment, "digital-human.comfy-ui.basic-user",
                "digital-human.comfy-ui.basic-password");
    }

    static boolean storage(Environment environment) {
        return hasText(environment, "digital-human.media-root");
    }

    private static boolean completePair(Environment environment, String first, String second) {
        boolean firstPresent = hasText(environment, first);
        boolean secondPresent = hasText(environment, second);
        return firstPresent == secondPresent;
    }

    private static boolean hasText(Environment environment, String key) {
        return DigitalHumanHttpSupport.hasText(environment.getProperty(key));
    }
}

final class IndexTts2ConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return DigitalHumanProviderConditions.voice(context.getEnvironment());
    }
}

final class ComfyUiConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return DigitalHumanProviderConditions.video(context.getEnvironment());
    }
}

final class DigitalHumanStorageConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return DigitalHumanProviderConditions.storage(context.getEnvironment());
    }
}
