package org.dromara.aivideo.voice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record VoiceDTO(String voiceId, String assetId, String voiceType, String name, String gender,
                       String style, List<String> tags, String note, String transcriptText,
                       List<VoiceTranscriptCueDTO> transcriptTimeline, String detectedLanguage,
                       Long durationMillis, String transcriptionStatus,
                       String failureCode, String failureMessage, Integer attemptCount,
                       String recordRevision, LocalDateTime createTime, LocalDateTime updateTime) {
}
