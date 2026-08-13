package org.dromara.aivideo.digitalhuman.dto;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;

public record CreateDigitalHumanVideoByResourceDTO(AppPrincipalSnapshotDTO principal,
                                                    String idempotencyKey,
                                                    Long voiceJobId,
                                                    String portraitId) {
}
