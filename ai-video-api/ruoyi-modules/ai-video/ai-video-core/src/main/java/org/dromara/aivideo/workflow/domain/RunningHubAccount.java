package org.dromara.aivideo.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_runninghub_account", excludeProperty = "createDept")
public class RunningHubAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "account_id", type = IdType.ASSIGN_ID)
    private Long accountId;
    private Long tenantId;
    private String accountName;
    @ToString.Exclude
    private String apiKeyCiphertext;
    private String apiKeyMasked;
    private Boolean enabled;
    private String lastHealthStatus;
    private LocalDateTime lastHealthTime;
    private String lastHealthSummary;
    private LocalDateTime credentialUpdatedAt;
    @Version
    private Long rowRevision;
    @TableLogic
    private String delFlag;
}
