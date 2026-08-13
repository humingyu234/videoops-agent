package org.dromara.aivideo.infra.runninghub;

import org.dromara.aivideo.infra.runninghub.listener.RunningHubWorkflowTaskScheduler;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Registers the provider-specific dispatcher separately from the local timeline worker. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RunningHubWorkflowDispatchProperties.class)
public class RunningHubWorkflowDispatchConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.runninghub.workflow-dispatch", name = "enabled", havingValue = "true",
        matchIfMissing = true)
    public ExecutorService runningHubWorkflowTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "aivideo.runninghub.workflow-dispatch", name = "enabled", havingValue = "true",
        matchIfMissing = true)
    public RunningHubWorkflowTaskScheduler runningHubWorkflowTaskScheduler(IAiTaskService aiTaskService,
                                                                             RunningHubWorkflowDispatchProperties properties,
                                                                             @Qualifier("runningHubWorkflowTaskExecutor") Executor executor) {
        return new RunningHubWorkflowTaskScheduler(aiTaskService, properties, executor);
    }
}
