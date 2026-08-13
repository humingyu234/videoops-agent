package org.dromara.aivideo.user.portrait.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 修改人物形象资料请求。 */
public record UpdatePortraitBo(
    @NotBlank @Size(max = 80) String name,
    String gender,
    @Size(max = 8) List<@Size(max = 20) String> sceneTags,
    @Size(max = 500) String note,
    @NotBlank String expectedRevision
) {
}
