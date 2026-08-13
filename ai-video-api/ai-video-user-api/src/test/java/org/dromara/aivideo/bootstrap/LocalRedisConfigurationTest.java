package org.dromara.aivideo.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the effective shared development Redis endpoint aligned with the local service.
 */
@Tag("dev")
class LocalRedisConfigurationTest {

    @Test
    void usesTheEffectiveSharedDevelopmentRedisEndpoint() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo("6379");
        assertThat(properties.getProperty("spring.data.redis.password")).isEmpty();
    }

    @Test
    void enablesTimelineAndUsesPortableDevelopmentPaths() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"),
            new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("aivideo.timeline.enabled"))
            .isEqualTo("${AIVIDEO_TIMELINE_ENABLED:true}");
        assertThat(properties.getProperty("aivideo.timeline.ffmpeg-path"))
            .isEqualTo("${AIVIDEO_TIMELINE_FFMPEG_PATH:${user.home}/AppData/Local/Microsoft/WinGet/Links/ffmpeg.exe}");
        assertThat(properties.getProperty("aivideo.timeline.ffprobe-path"))
            .isEqualTo("${AIVIDEO_TIMELINE_FFPROBE_PATH:${user.home}/AppData/Local/Microsoft/WinGet/Links/ffprobe.exe}");
        assertThat(properties.getProperty("aivideo.timeline.work-root"))
            .isEqualTo("${AIVIDEO_TIMELINE_WORK_ROOT:${user.dir}/ai-video-user-api/target}");
        assertThat(properties.getProperty("aivideo.timeline.font-root"))
            .isEqualTo("${AIVIDEO_TIMELINE_FONT_ROOT:${user.dir}/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts}");
        assertThat(properties.getProperty("digital-human.media-root")).doesNotContain("F:/obj");
        assertThat(properties.getProperty("digital-human.index-tts2.ca-certificate")).doesNotContain("F:/obj");
    }
}
