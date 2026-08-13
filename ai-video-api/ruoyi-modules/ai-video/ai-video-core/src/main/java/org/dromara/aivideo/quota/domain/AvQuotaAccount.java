package org.dromara.aivideo.quota.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分账户实体。
 */
@Data
@TableName("av_quota_account")
public class AvQuotaAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("unit_code")
    private String unitCode;

    @TableField("available_balance")
    private Long availableBalance;

    @TableField("locked_balance")
    private Long lockedBalance;

    @TableField("used_balance")
    private Long usedBalance;

    @TableField("account_revision")
    private Long accountRevision;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
