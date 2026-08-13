package org.dromara.aivideo.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** Owner-scoped input or output asset reference retained by a workflow order. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_workflow_order_asset",
    excludeProperty = {"createDept", "createBy", "updateBy", "updateTime"})
public class WorkflowOrderAsset extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "order_asset_id", type = IdType.ASSIGN_ID)
    private Long orderAssetId;
    private Long tenantId;
    private Long ownerUserId;
    private String workspaceId;
    private Long orderId;
    private Long assetId;
    private String assetRole;
    private String inputKey;
    private Integer sortOrder;
    private Boolean isPrimary;
}
