package org.dromara.aivideo.infra.timeline;

import org.dromara.aivideo.infra.timeline.ai.UnavailableTimelineAiSuggestionService;
import org.dromara.aivideo.infra.timeline.listener.TimelineTaskScheduler;
import org.dromara.aivideo.infra.timeline.render.UnavailableTimelineMediaRenderService;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Tag("dev")
class TimelineInfrastructureConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TimelineInfrastructureConfiguration.class)
        .withBean(IAiTaskService.class, () -> mock(IAiTaskService.class));

    @Test
    void disabledTimelineRegistersOnlyStableUnavailablePortsAndNoScheduler() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(ITimelineMediaRenderService.class);
            assertThat(context).hasSingleBean(ITimelineAiSuggestionService.class);
            assertThat(context.getBean(ITimelineMediaRenderService.class))
                .isInstanceOf(UnavailableTimelineMediaRenderService.class);
            assertThat(context.getBean(ITimelineAiSuggestionService.class))
                .isInstanceOf(UnavailableTimelineAiSuggestionService.class);
            assertThat(context.getBeansOfType(TimelineTaskScheduler.class)).isEmpty();

            assertUnavailable(() -> context.getBean(ITimelineMediaRenderService.class)
                .cancel("execution", "attempt"));
            assertUnavailable(() -> context.getBean(ITimelineAiSuggestionService.class)
                .suggestFancyText(null, null, () -> false));
        });
    }

    @Test
    void invalidEnabledConfigurationFailsBeforeSchedulerOrRealPortsCanStart() {
        contextRunner.withPropertyValues("aivideo.timeline.enabled=true")
            .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void validEnabledConfigurationRegistersTheSchedulerWithoutFallbackPorts() throws Exception {
        contextRunner.withPropertyValues(validEnabledProperties())
            .run(context -> {
                assertThat(context.getStartupFailure()).isNull();
                assertThat(context).hasSingleBean(TimelineTaskScheduler.class);
                assertThat(context.getBeansOfType(ITimelineMediaRenderService.class)).isEmpty();
                assertThat(context.getBeansOfType(ITimelineAiSuggestionService.class)).isEmpty();
            });
    }

    private String[] validEnabledProperties() throws Exception {
        Path binaries = Files.createDirectories(temporaryDirectory.resolve("bin"));
        Path workRoot = Files.createDirectories(temporaryDirectory.resolve("work"));
        Path fontRoot = Files.createDirectories(temporaryDirectory.resolve("fonts"));
        Path ffmpeg = Files.createFile(binaries.resolve("ffmpeg.exe"));
        Path ffprobe = Files.createFile(binaries.resolve("ffprobe.exe"));
        Files.createFile(fontRoot.resolve("NotoSansCJKsc-Regular.otf"));
        Files.createFile(fontRoot.resolve("NotoSerifCJKsc-Regular.otf"));
        return new String[] {
            "aivideo.timeline.enabled=true",
            "aivideo.timeline.ffmpeg-path=" + ffmpeg.toAbsolutePath(),
            "aivideo.timeline.ffprobe-path=" + ffprobe.toAbsolutePath(),
            "aivideo.timeline.work-root=" + workRoot.toAbsolutePath(),
            "aivideo.timeline.font-root=" + fontRoot.toAbsolutePath(),
            "aivideo.timeline.process-timeout=PT30S",
            "aivideo.timeline.max-output-bytes=67108864",
            "aivideo.timeline.per-user-concurrency-limit=1",
            "aivideo.timeline.system-concurrency-limit=2",
            "aivideo.timeline.worker-id=timeline-test-worker",
            "aivideo.timeline.poll-delay=PT1S",
            "aivideo.timeline.recovery-batch-limit=10"
        };
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(TimelineErrorCodes.TIMELINE_RENDER_UNAVAILABLE));
    }
}
