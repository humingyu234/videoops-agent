package org.dromara.aivideo.infra.timeline;

import org.dromara.aivideo.infra.timeline.ai.UnavailableTimelineAiSuggestionService;
import org.dromara.aivideo.infra.timeline.listener.TimelineTaskScheduler;
import org.dromara.aivideo.infra.timeline.render.UnavailableTimelineMediaRenderService;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers timeline media infrastructure only when its deployment prerequisites are explicit.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(TimelineInfrastructureProperties.class)
public class TimelineInfrastructureConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.timeline", name = "enabled", havingValue = "true")
    public TimelineTaskScheduler timelineTaskScheduler(IAiTaskService aiTaskService,
                                                         TimelineInfrastructureProperties properties) {
        return new TimelineTaskScheduler(aiTaskService, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.timeline", name = "enabled", havingValue = "false",
        matchIfMissing = true)
    public ITimelineMediaRenderService unavailableTimelineMediaRenderService() {
        return new UnavailableTimelineMediaRenderService();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.timeline", name = "enabled", havingValue = "false",
        matchIfMissing = true)
    public ITimelineAiSuggestionService unavailableTimelineAiSuggestionService() {
        return new UnavailableTimelineAiSuggestionService();
    }
}
