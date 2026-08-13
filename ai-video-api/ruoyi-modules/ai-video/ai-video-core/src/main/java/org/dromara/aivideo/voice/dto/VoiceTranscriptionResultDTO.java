package org.dromara.aivideo.voice.dto;

import java.util.List;

public record VoiceTranscriptionResultDTO(String requestId, String text, String language,
                                          long durationMillis,
                                          List<VoiceTranscriptCueDTO> transcriptTimeline) {
}
