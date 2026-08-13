package org.dromara.aivideo.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** Short-lived, owner-scoped authorization to upload one workflow input object. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_upload_session", excludeProperty = {"createDept", "createBy", "updateBy"})
public class UploadSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "upload_session_id", type = IdType.ASSIGN_ID)
    private Long uploadSessionId;
    private Long tenantId;
    private String workspaceId;
    private String contextScope;
    private Long ownerUserId;
    private Long fileId;
    private Long assetId;
    private Long templateId;
    private String schemaHash;
    private String inputKey;
    private String originalFileName;
    private String declaredContentType;
    private Long declaredSizeBytes;
    private String runninghubFileName;
    private String idempotencyKey;
    private String status;
    private LocalDateTime expiresAt;
}
