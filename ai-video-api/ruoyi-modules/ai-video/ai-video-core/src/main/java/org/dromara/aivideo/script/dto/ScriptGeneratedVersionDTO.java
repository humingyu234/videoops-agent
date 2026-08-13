package org.dromara.aivideo.script.dto;

/** 大模型生成的一套文案版本。 */
public record ScriptGeneratedVersionDTO(
    String title,
    int durationSeconds,
    String body
) {
}
