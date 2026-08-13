package org.dromara.aivideo.user.voice.domain.vo;

import org.dromara.aivideo.voice.dto.VoiceDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptCueDTO;

import java.time.LocalDateTime;
import java.util.List;

public record VoiceVo(String voiceId, String assetId, String voiceType, String name, String gender,
                      String style, List<String> tags, String note, String transcriptText,
                      List<VoiceTranscriptCueDTO> transcriptTimeline, String detectedLanguage,
                      Long durationMillis, String transcriptionStatus,
                      String failureCode, String failureMessage, Integer attemptCount,
                      String recordRevision, LocalDateTime createTime, LocalDateTime updateTime) {
    public static VoiceVo from(VoiceDTO dto) {
        return new VoiceVo(dto.voiceId(), dto.assetId(), dto.voiceType(), dto.name(), dto.gender(),
            dto.style(), dto.tags(), dto.note(), dto.transcriptText(), dto.transcriptTimeline(), dto.detectedLanguage(),
            dto.durationMillis(), dto.transcriptionStatus(), dto.failureCode(), dto.failureMessage(),
            dto.attemptCount(), dto.recordRevision(), dto.createTime(), dto.updateTime());
    }
}
