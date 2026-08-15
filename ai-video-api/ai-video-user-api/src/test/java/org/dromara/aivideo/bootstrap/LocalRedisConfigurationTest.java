package org.dromara.aivideo.bootstrap;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the effective VideoOps Agent development runtime isolated from the source project.
 */
@Tag("dev")
class LocalRedisConfigurationTest {

    @Test
    void usesTheIndependentDevelopmentDataAndStorageNamespaces() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties).doesNotContainKey("spring.config.import");
        assertThat(properties.getProperty("server.address"))
            .isEqualTo("${VIDEOOPS_USER_SERVER_ADDRESS:127.0.0.1}");
        assertThat(properties.getProperty("server.port")).isEqualTo("${VIDEOOPS_USER_SERVER_PORT:18081}");
        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo("6379");
        assertThat(properties.getProperty("spring.data.redis.database")).isEqualTo("14");
        assertThat(properties.getProperty("spring.data.redis.password"))
            .isEqualTo("${VIDEOOPS_USER_SPRING_DATA_REDIS_PASSWORD:}");
        assertThat(properties.getProperty("redisson.keyPrefix")).isEqualTo("videoops-agent:dev");
        assertThat(properties.getProperty("redisson.singleServerConfig.clientName"))
            .isEqualTo("VideoOps-Agent-Dev");
        assertThat(properties.getProperty("sa-token.redis-key-prefix")).isEqualTo("videoops-agent:dev:");
        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.url"))
            .contains("/videoops_agent_dev?")
            .doesNotContain("/ai_video?");
        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.username"))
            .isEqualTo("videoops_agent");
        assertThat(properties.getProperty("aivideo.oss.enabled"))
            .isEqualTo("${VIDEOOPS_AIVIDEO_OSS_ENABLED:false}");
        assertThat(properties.getProperty("aivideo.oss.prefix")).isEqualTo("videoops-agent/dev");
        assertThat(properties.getProperty("mybatis-plus.sql-log.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("aivideo.timeline.enabled"))
            .isEqualTo("${AIVIDEO_TIMELINE_ENABLED:false}");
    }

    @Test
    void keepsTimelineDisabledAndUsesPortableDevelopmentPaths() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"),
            new ClassPathResource("application-dev.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("aivideo.timeline.enabled"))
            .isEqualTo("${AIVIDEO_TIMELINE_ENABLED:false}");
        assertThat(properties.getProperty("aivideo.timeline.ffmpeg-path"))
            .isEqualTo("${AIVIDEO_TIMELINE_FFMPEG_PATH:${user.home}/AppData/Local/Microsoft/WinGet/Links/ffmpeg.exe}");
        assertThat(properties.getProperty("aivideo.timeline.ffprobe-path"))
            .isEqualTo("${AIVIDEO_TIMELINE_FFPROBE_PATH:${user.home}/AppData/Local/Microsoft/WinGet/Links/ffprobe.exe}");
        assertThat(properties.getProperty("aivideo.timeline.work-root"))
            .isEqualTo("${AIVIDEO_TIMELINE_WORK_ROOT:${user.dir}/.runtime/videoops-agent/timeline-work}");
        assertThat(properties.getProperty("aivideo.timeline.font-root"))
            .isEqualTo("${AIVIDEO_TIMELINE_FONT_ROOT:${user.dir}/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts}");
        assertThat(properties.getProperty("digital-human.media-root"))
            .isEqualTo("${AI_VIDEO_DH_MEDIA_ROOT:${user.dir}/.runtime/videoops-agent/digital-human-media}");
        assertThat(properties.getProperty("digital-human.index-tts2.ca-certificate")).doesNotContain("F:/obj");
    }
}
