package org.dromara.aivideo.timeline;

import org.dromara.aivideo.timeline.dto.TimelineCanvasDTO;
import org.dromara.common.json.config.JacksonConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TimelineJsonSerializationTest {

    @Test
    void serializesFrozenTimelineDecimalsAsNumbersWithTheApplicationJacksonModule() {
        JsonMapper mapper = JsonMapper.builder()
            .addModule(new JacksonConfig().registerJavaTimeModule())
            .build();

        JsonNode canvas = mapper.valueToTree(new TimelineCanvasDTO(
            1080, 1920, 30, 3_000L, new BigDecimal("0.05")));

        assertThat(canvas.required("safeMarginRatio").isNumber()).isTrue();
        assertThat(canvas.required("safeMarginRatio").decimalValue()).isEqualByComparingTo("0.05");
    }
}
