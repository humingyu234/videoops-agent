package org.dromara.aivideo.user.discovery.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 用户端发现页模板查询条件。 */
@Getter
@Setter
public class DiscoveryTemplateQueryBo {

    @Min(1)
    private Integer pageNum = 1;

    @Min(1)
    @Max(50)
    private Integer pageSize = 10;

    @Pattern(regexp = "video_template|workflow_inspiration")
    private String channel;

    @Size(max = 64)
    private String categoryCode;

    @Size(max = 512)
    private String tagCodes;

    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "latest|recommended")
    private String sort = "recommended";
}
