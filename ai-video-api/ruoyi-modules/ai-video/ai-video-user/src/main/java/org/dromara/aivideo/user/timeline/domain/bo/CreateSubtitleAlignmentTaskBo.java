package org.dromara.aivideo.user.timeline.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

import java.util.List;

@Data
public class CreateSubtitleAlignmentTaskBo extends StrictAppRequestBo {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,64}")
    private String idempotencyKey;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String expectedRevision;

    @NotEmpty
    @Size(max = 2_000)
    private List<@Pattern(regexp = "[A-Za-z0-9._:-]{1,128}") String> subtitleElementIds;
}
