package org.dromara.aivideo.digitalhuman.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DigitalHumanJobStage {
    QUEUED("queued"),
    VOICE_SYNTHESIZING("voice_synthesizing"),
    AWAITING_VOICE_CONFIRMATION("awaiting_voice_confirmation"),
    VIDEO_SUBMITTED("video_submitted"),
    VIDEO_RENDERING("video_rendering"),
    COMPLETED("completed"),
    FAILED("failed");

    @EnumValue
    private final String value;
}
