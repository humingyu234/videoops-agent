package org.dromara.aivideo.platform.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 运营端知识状态修改请求。 */
@Data
public class KnowledgeItemStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标状态。 */
    @NotBlank(message = "知识状态不能为空")
    @Pattern(regexp = "draft|reviewing|published|retired", message = "知识状态不合法")
    private String status;
}
