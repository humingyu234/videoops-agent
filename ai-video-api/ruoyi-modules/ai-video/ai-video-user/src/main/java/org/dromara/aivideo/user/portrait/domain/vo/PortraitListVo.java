package org.dromara.aivideo.user.portrait.domain.vo;

import org.dromara.aivideo.portrait.dto.PortraitDTO;

import java.time.LocalDateTime;
import java.util.List;

/** 用户端人物形象列表响应，不暴露内部素材标识。 */
public record PortraitListVo(String portraitId, String name, String gender, List<String> sceneTags,
                             String availabilityStatus, String failureReason, String previewUrl,
                             LocalDateTime previewExpiresAt, String recordRevision,
                             LocalDateTime createTime, LocalDateTime updateTime) {
    public static PortraitListVo from(PortraitDTO dto) {
        return new PortraitListVo(dto.portraitId(), dto.name(), dto.gender(), dto.sceneTags(),
            dto.availabilityStatus(), dto.failureReason(), dto.previewUrl(), dto.previewExpiresAt(),
            dto.recordRevision(), dto.createTime(), dto.updateTime());
    }
}
