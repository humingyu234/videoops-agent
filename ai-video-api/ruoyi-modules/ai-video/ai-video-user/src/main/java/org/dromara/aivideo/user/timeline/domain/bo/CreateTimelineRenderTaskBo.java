package org.dromara.aivideo.user.timeline.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.dromara.aivideo.user.common.domain.bo.StrictAppRequestBo;

@Data
public class CreateTimelineRenderTaskBo extends StrictAppRequestBo {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,64}")
    private String idempotencyKey;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String expectedRevision;

    @Valid
    @NotNull
    private OutputConfig outputConfig;

    @Data
    public static class OutputConfig extends StrictAppRequestBo {

        @NotBlank
        @Pattern(regexp = "match_canvas")
        private String resolutionPreset;

        @NotNull
        @Min(1)
        private Integer frameRate;

        @NotBlank
        @Pattern(regexp = "standard|high")
        private String qualityPreset;
    }
}
