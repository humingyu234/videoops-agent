package org.dromara.aivideo.user.script.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 手工创建个人文案请求。 */
@Data
public class CreateUserScriptBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String displayTitle;

    @NotBlank
    private String scriptText;

    @NotBlank
    @Size(max = 64)
    private String idempotencyKey;

    /** 拒绝调用方伪造归属字段及其他未声明字段。 */
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("不允许的请求字段: " + field);
    }
}
