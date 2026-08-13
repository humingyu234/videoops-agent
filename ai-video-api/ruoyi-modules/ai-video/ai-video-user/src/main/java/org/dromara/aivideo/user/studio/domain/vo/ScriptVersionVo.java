package org.dromara.aivideo.user.studio.domain.vo;

/** 用户端生成文案版本。 */
public record ScriptVersionVo(
    String title,
    int durationSeconds,
    String body
) {
}
