package org.dromara.aivideo.user.voice.domain.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateVoiceBo(
    @NotBlank @Size(max = 128) String idempotencyKey,
    @NotBlank @Size(max = 80) String name,
    @Size(max = 16) String gender,
    @Size(max = 40) String style,
    @Size(max = 8) List<@Size(max = 20) String> tags,
    @Size(max = 500) String note,
    Boolean transcriptionRequested
) {
    public CreateVoiceBo(String idempotencyKey, String name, String gender, String style,
                         List<String> tags, String note) {
        this(idempotencyKey, name, gender, style, tags, note, null);
    }
}
