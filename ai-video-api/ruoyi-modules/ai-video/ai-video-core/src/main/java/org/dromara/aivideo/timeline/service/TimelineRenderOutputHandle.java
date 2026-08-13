package org.dromara.aivideo.timeline.service;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.dromara.aivideo.timeline.dto.TimelineRenderResultDTO;

import java.io.IOException;
import java.io.InputStream;

@JsonIgnoreType
public interface TimelineRenderOutputHandle extends AutoCloseable {
    TimelineRenderResultDTO metadata();
    InputStream stream();
    @Override void close() throws IOException;
}
