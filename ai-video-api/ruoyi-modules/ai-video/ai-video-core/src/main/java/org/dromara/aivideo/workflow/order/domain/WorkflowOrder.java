package org.dromara.aivideo.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** Immutable user submission record for one discovery workflow run. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_workflow_order", excludeProperty = {"createDept", "createBy", "updateBy"})
public class WorkflowOrder extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "order_id", type = IdType.ASSIGN_ID)
    private Long orderId;
    private Long tenantId;
    private String orderNo;
    private String workspaceId;
    private Long ownerUserId;
    private Long templateId;
    private Long taskId;
    private String idempotencyKey;
    private String schemaHash;
    private String inputPayloadJson;
    private String requestHash;
    private String billingMode;
    private Long usageOperationId;
    private String templateTitleSnapshot;
    private String templateCoverSnapshotJson;
    private String inputDisplaySnapshotJson;
}
