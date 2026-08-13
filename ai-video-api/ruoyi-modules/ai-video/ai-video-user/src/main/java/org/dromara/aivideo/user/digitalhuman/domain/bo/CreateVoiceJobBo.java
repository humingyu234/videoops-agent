package org.dromara.aivideo.user.digitalhuman.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateVoiceJobBo {
    @NotBlank
    @Size(max = 1000)
    private String scriptText;

    @NotNull
    private MultipartFile referenceAudio;
}
