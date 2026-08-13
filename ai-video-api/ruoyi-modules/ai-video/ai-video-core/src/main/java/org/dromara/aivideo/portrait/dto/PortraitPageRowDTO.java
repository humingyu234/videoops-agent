package org.dromara.aivideo.portrait.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 人物形象分页联表查询行。 */
@Data
public class PortraitPageRowDTO {
    private Long portraitId;
    private Long assetId;
    private String name;
    private String gender;
    private String sceneTagsJson;
    private String note;
    private String availabilityStatus;
    private String failureReason;
    private String originalFileName;
    private String contentType;
    private String fileFormat;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private Long recordRevision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
