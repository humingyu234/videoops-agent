package org.dromara.aivideo.infra.questionnaire;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** DeepSeek 动态问卷配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "questionnaire.deepseek")
public class DeepSeekQuestionnaireProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-v4-flash";
    private int timeoutSeconds = 20;
    private int maxOutputTokens = 800;
}
