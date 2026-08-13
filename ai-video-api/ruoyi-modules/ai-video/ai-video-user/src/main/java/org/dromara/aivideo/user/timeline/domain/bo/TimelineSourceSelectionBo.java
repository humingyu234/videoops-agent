package org.dromara.aivideo.user.timeline.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

import java.util.List;

/** A source range or the current draft's subtitle element identifiers. */
@Data
public class TimelineSourceSelectionBo extends StrictAppRequestBo {

    @Min(0)
    @Max(50_000)
    private Integer sourceStartOffset;

    @Min(1)
    @Max(50_000)
    private Integer sourceEndOffset;

    @Size(min = 1, max = 2_000)
    private List<@Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String> subtitleElementIds;
}
