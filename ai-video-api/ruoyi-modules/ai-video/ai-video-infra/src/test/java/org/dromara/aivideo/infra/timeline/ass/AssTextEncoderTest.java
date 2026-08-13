package org.dromara.aivideo.infra.timeline.ass;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AssTextEncoderTest {

    private final AssTextEncoder encoder = new AssTextEncoder();

    @Test
    void normalizesAndEscapesEveryAllowedUserCharacterReversibly() throws Exception {
        JsonNode cases = maliciousText().path("escaped");

        for (JsonNode item : cases) {
            String input = item.path("input").asString();
            String expected = item.path("expected").asString();

            assertThat(encoder.encodeSubtitle(input)).isEqualTo(expected);
            assertThat(encoder.encodeFancyText(input)).isEqualTo(expected);
        }
    }

    @Test
    void rejectsLineBreaksControlsBidiOverridesAndLengthOverflow() throws Exception {
        JsonNode cases = maliciousText().path("rejected");

        for (JsonNode item : cases) {
            String input = item.asString();
            assertThatThrownBy(() -> encoder.encodeSubtitle(input)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> encoder.encodeFancyText(input)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> encoder.encodeSubtitle("字".repeat(513)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeFancyText("字".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesFrozenSubtitleSourceWithTheC0PunctuationAndWhitespaceRules() {
        assertThat(AssTextEncoder.normalizeSubtitleSource("\u4EF7\r\n\u683C\t\u30003.14\u0085\u00A0\u5143\u3002"))
            .isEqualTo("\u4EF7\u683C314\u5143");
        assertThat(AssTextEncoder.normalizeSubtitleSource("Cafe\u0301\uFF0C \u5F00\u59CB\u521B\u4F5C\u3002"))
            .isEqualTo("Caf\u00E9\u5F00\u59CB\u521B\u4F5C");
        assertThat(AssTextEncoder.normalizeSubtitleSource("\u6548\u7387 \u63D0\u5347 \uD83D\uDE80 50%\uFF01"))
            .isEqualTo("\u6548\u7387\u63D0\u5347\uD83D\uDE8050");
    }

    private static JsonNode maliciousText() throws Exception {
        try (InputStream resource = AssTextEncoderTest.class.getResourceAsStream("/timeline/ass-malicious-text.json")) {
            assertThat(resource).isNotNull();
            return JsonMapper.builder().build().readTree(resource);
        }
    }
}
