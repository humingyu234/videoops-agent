package org.dromara.aivideo.infra.voice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WhisperPropertiesTest {

    @Test
    void permitsTheConfiguredRemoteWhisperWorker() {
        WhisperProperties properties = new WhisperProperties();
        properties.setBaseUrl("http://36.133.55.206:18181");
        properties.setAllowedHosts("36.133.55.206");

        assertThat(properties.validatedBaseUri().getHost()).isEqualTo("36.133.55.206");
    }

    @Test
    void rejectsHostsOutsideTheConfiguredAllowlist() {
        WhisperProperties properties = new WhisperProperties();
        properties.setBaseUrl("http://192.0.2.10:18181");
        properties.setAllowedHosts("36.133.55.206");

        assertThatThrownBy(properties::validatedBaseUri)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("允许列表");
    }
}
