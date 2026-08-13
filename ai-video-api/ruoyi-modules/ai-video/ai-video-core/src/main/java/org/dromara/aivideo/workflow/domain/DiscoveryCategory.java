package org.dromara.aivideo.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_discovery_category", excludeProperty = {"createDept", "createBy", "updateBy"})
public class DiscoveryCategory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "category_id", type = IdType.ASSIGN_ID)
    private Long categoryId;
    private String name;
    private Integer sortOrder;
    private String status;
}
