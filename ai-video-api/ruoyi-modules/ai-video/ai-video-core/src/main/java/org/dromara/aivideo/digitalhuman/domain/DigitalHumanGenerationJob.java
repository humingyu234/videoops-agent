package org.dromara.aivideo.digitalhuman.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("av_dh_generation_job")
public class DigitalHumanGenerationJob extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("owner_user_id")
    private Long ownerUserId;
    @TableField("job_type")
    private DigitalHumanJobType jobType;
    @TableField("status")
    private DigitalHumanJobStatus status;
    @TableField("stage")
    private DigitalHumanJobStage stage;
    @TableField("progress")
    private Integer progress;
    @TableField("parent_job_id")
    private Long parentJobId;
    @TableField("idempotency_key")
    private String idempotencyKey;
    @TableField("input_hash")
    private String inputHash;
    @TableField("script_text")
    private String scriptText;
    @TableField("input_media_key")
    private String inputMediaKey;
    @TableField("output_media_key")
    private String outputMediaKey;
    @TableField("output_media_type")
    private String outputMediaType;
    @TableField("output_media_size")
    private Long outputMediaSize;
    @TableField("output_media_sha256")
    private String outputMediaSha256;
    @TableField("provider")
    private String provider;
    @TableField("provider_job_id")
    private String providerJobId;
    @TableField("poll_token")
    private String pollToken;
    @TableField("poll_lease_until")
    private LocalDateTime pollLeaseUntil;
    @TableField("poll_error_count")
    private Integer pollErrorCount;
    @TableField("voice_confirmed")
    private Boolean voiceConfirmed;
    @TableField("error_code")
    private String errorCode;
    @TableField("error_message")
    private String errorMessage;
}
