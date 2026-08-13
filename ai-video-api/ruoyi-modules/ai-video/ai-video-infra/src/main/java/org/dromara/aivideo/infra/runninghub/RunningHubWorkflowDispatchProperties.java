package org.dromara.aivideo.infra.runninghub;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/** Dedicated dispatcher settings for work that is executed by RunningHub. */
@Getter
@Setter
@ConfigurationProperties(prefix = "aivideo.runninghub.workflow-dispatch")
public class RunningHubWorkflowDispatchProperties {

    private boolean enabled = true;
    private String workerId = "runninghub-workflow-worker";
    private int concurrencyLimit = 100;
    private Duration pollDelay = Duration.ofMillis(100);

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(workerId) || !workerId.equals(workerId.trim()) || workerId.length() > 128) {
            throw new IllegalStateException("aivideo.runninghub.workflow-dispatch.worker-id must be 1..128 characters");
        }
        if (concurrencyLimit < 1 || concurrencyLimit > 100) {
            throw new IllegalStateException("aivideo.runninghub.workflow-dispatch.concurrency-limit must be within 1..100");
        }
        if (pollDelay == null || pollDelay.isNegative() || pollDelay.isZero()) {
            throw new IllegalStateException("aivideo.runninghub.workflow-dispatch.poll-delay must be positive");
        }
    }
}
