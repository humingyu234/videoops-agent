package org.dromara.aivideo.digitalhuman.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DigitalHumanJobType {
    VOICE_GENERATE("voice_generate"),
    VIDEO_GENERATE("video_generate");

    @EnumValue
    private final String value;
}
