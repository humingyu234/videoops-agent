package org.dromara.aivideo.studio.draft.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 当前用户在人工工作台中的唯一可恢复草稿。 */
@Getter
@Setter
@TableName("av_studio_workflow_draft")
public class StudioWorkflowDraft extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("owner_user_id")
    private Long ownerUserId;
    @TableField("revision")
    private Long revision;
    @TableField("current_step")
    private Integer currentStep;
    @TableField("schema_version")
    private String schemaVersion;
    @TableField("snapshot_json")
    private String snapshotJson;
}
