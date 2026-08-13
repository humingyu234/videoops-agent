package org.dromara.aivideo.user.timeline.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Fixed server-side pagination for immutable timeline versions. */
public record TimelineVersionQueryBo(
    @Min(1) Integer pageNum,
    @Min(1) @Max(100) Integer pageSize
) {
}
