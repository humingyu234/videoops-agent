package org.dromara.aivideo.user.common.domain.bo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serial;
import java.io.Serializable;

/** Rejects undeclared JSON properties before they can reach app business services. */
@JsonIgnoreProperties(ignoreUnknown = false)
public abstract class StrictAppRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonAnySetter
    public void rejectUnknown(String field, Object ignored) {
        throw new IllegalArgumentException("Unsupported request field: " + field);
    }
}
