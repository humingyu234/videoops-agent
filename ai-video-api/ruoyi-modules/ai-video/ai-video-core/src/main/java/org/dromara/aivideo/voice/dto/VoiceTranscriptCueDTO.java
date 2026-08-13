package org.dromara.aivideo.voice.dto;

/** Whisper 转写词元及其精确时间范围。 */
public record VoiceTranscriptCueDTO(String text, long startMillis, long endMillis) {
}
