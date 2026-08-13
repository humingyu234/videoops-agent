package org.dromara.aivideo.voice.service;

import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.timeline.enums.TimelineExecutionFailureCode;
import org.dromara.aivideo.timeline.exception.TimelineExecutionException;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionLeaseDTO;
import org.dromara.aivideo.voice.dto.VoiceTranscriptionResultDTO;
import org.dromara.aivideo.voice.dto.WhisperTranscriptionInputDTO;

import java.io.InputStream;

public interface IWhisperTranscriptionService {
    VoiceTranscriptionResultDTO transcribe(VoiceTranscriptionLeaseDTO lease, AssetDTO asset, InputStream input);

    default VoiceTranscriptionResultDTO transcribe(
            WhisperTranscriptionInputDTO inputMetadata, InputStream input) {
        throw new TimelineExecutionException("字幕对齐能力暂不可用",
            TimelineExecutionFailureCode.CAPABILITY_UNAVAILABLE, true, null);
    }
}
