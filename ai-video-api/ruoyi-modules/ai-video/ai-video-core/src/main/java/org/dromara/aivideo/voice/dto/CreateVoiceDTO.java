package org.dromara.aivideo.voice.dto;

import java.util.List;

public record CreateVoiceDTO(String assetId, String idempotencyKey, String uploadFingerprint,
                             String name, String gender, String style, List<String> tags, String note,
                             boolean transcriptionRequested) {
    public CreateVoiceDTO(String assetId, String idempotencyKey, String uploadFingerprint,
                          String name, String gender, String style, List<String> tags, String note) {
        this(assetId, idempotencyKey, uploadFingerprint, name, gender, style, tags, note, true);
    }
}
