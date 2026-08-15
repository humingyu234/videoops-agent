package org.dromara.aivideo.asset.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class VideoOpsObjectKeyTest {

    @Test
    void qualifiesGoldenPathKeysIntoTheProjectNamespace() {
        assertThat(VideoOpsObjectKey.qualify("timeline-renders/7/output.mp4"))
            .isEqualTo("videoops-agent/dev/timeline-renders/7/output.mp4");
        assertThat(VideoOpsObjectKey.requireQualified("videoops-agent/dev/portraits/7/image.webp"))
            .isEqualTo("videoops-agent/dev/portraits/7/image.webp");
        assertThat(VideoOpsObjectKey.requireProjectPrefix("videoops-agent/dev"))
            .isEqualTo("videoops-agent/dev");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ai-video/output.mp4", "/output.mp4", "../output.mp4",
        "timeline-renders//output.mp4", "timeline-renders\\output.mp4", "videoops-agent/dev/output.mp4"})
    void rejectsEmptyLegacyAbsoluteAndEscapingLogicalKeys(String logicalKey) {
        assertThatThrownBy(() -> VideoOpsObjectKey.qualify(logicalKey))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ai-video", "videoops-agent/dev/"})
    void rejectsEmptyOrLegacyConfiguredPrefixes(String prefix) {
        assertThatThrownBy(() -> VideoOpsObjectKey.requireProjectPrefix(prefix))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
