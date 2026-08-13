package org.dromara.aivideo.user.portrait.domain.vo;

import org.dromara.aivideo.portrait.dto.PortraitDTO;

import java.time.LocalDateTime;
import java.util.List;

/** 用户端人物形象详情响应。 */
public record PortraitDetailVo(String portraitId, String name, String gender, List<String> sceneTags,
                               String note, String availabilityStatus, String failureReason,
                               String previewUrl, LocalDateTime previewExpiresAt,
                               String originalFileName, String contentType, String fileFormat,
                               Integer width, Integer height, String sizeBytes, String recordRevision,
                               LocalDateTime createTime, LocalDateTime updateTime) {
    public static PortraitDetailVo from(PortraitDTO dto) {
        return new PortraitDetailVo(dto.portraitId(), dto.name(), dto.gender(), dto.sceneTags(), dto.note(),
            dto.availabilityStatus(), dto.failureReason(), dto.previewUrl(), dto.previewExpiresAt(),
            dto.originalFileName(), dto.contentType(), dto.fileFormat(), dto.width(), dto.height(),
            dto.fileSize() == null ? null : Long.toString(dto.fileSize()), dto.recordRevision(),
            dto.createTime(), dto.updateTime());
    }
}
