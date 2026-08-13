package org.dromara.aivideo.platform.knowledge.domain.bo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 运营端知识条目分页查询条件。 */
@Getter
@Setter
public class KnowledgeItemAdminQueryBo {

    /** 知识名称，支持模糊匹配。 */
    @Size(max = 255)
    private String name;

    /** 知识类型。 */
    @Pattern(regexp = "primary_template|writing_technique|psychology|case|mandatory_rule")
    private String knowledgeType;

    /** 最新版本状态。 */
    @Pattern(regexp = "draft|reviewing|published|retired")
    private String status;
}
