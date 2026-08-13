package org.dromara.aivideo.platform.knowledge.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 运营端知识新增或编辑请求。 */
@Data
public class KnowledgeItemSaveBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识名称。 */
    @NotBlank(message = "知识名称不能为空")
    @Size(max = 255, message = "知识名称不能超过255个字符")
    private String name;

    /** 知识类型代码。 */
    @NotBlank(message = "知识类型不能为空")
    @Pattern(regexp = "primary_template|writing_technique|psychology|case|mandatory_rule",
        message = "知识类型不合法")
    private String knowledgeType;

    /** 当前状态。 */
    @NotBlank(message = "知识状态不能为空")
    @Pattern(regexp = "draft|reviewing|published|retired", message = "知识状态不合法")
    private String status;

    /** 知识正文。 */
    @NotBlank(message = "知识正文不能为空")
    private String content;

    /** 知识摘要。 */
    @Size(max = 500, message = "知识摘要不能超过500个字符")
    private String summary;
}
