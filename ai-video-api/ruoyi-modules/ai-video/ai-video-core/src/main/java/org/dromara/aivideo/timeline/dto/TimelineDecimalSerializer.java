package org.dromara.aivideo.timeline.dto;

import tools.jackson.databind.annotation.JacksonStdImpl;
import tools.jackson.databind.ser.jdk.NumberSerializer;

import java.math.BigDecimal;

/** Serializes frozen timeline decimal fields as JSON numbers. */
@JacksonStdImpl
public final class TimelineDecimalSerializer extends NumberSerializer {

    public TimelineDecimalSerializer() {
        super(BigDecimal.class);
    }
}
