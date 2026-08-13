package org.dromara.aivideo.user.digitalhuman.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateVideoJobBo {
    @NotNull
    @Positive
    private Long voiceJobId;

    @NotNull
    private MultipartFile portraitImage;
}
