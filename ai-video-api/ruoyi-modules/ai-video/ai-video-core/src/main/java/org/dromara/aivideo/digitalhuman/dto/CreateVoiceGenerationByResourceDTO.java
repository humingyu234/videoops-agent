package org.dromara.aivideo.digitalhuman.dto;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

public record CreateVoiceGenerationByResourceDTO(AppPrincipalSnapshotDTO principal,
                                                  String idempotencyKey,
                                                  String scriptText,
                                                  String referenceVoiceId) {
}
