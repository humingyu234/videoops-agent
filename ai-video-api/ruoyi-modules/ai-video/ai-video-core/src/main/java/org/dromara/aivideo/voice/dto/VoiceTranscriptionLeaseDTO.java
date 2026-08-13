package org.dromara.aivideo.voice.dto;

public record VoiceTranscriptionLeaseDTO(String voiceId, String assetId, Long tenantId,
                                         String workspaceId, Long ownerId, String requestId,
                                         String leaseOwner, long recordRevision, int attemptCount) {
}
