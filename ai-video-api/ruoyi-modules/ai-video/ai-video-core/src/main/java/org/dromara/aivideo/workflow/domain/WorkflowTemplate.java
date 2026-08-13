package org.dromara.aivideo.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_workflow_template", excludeProperty = "createDept")
public class WorkflowTemplate extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "template_id", type = IdType.ASSIGN_ID)
    private Long templateId;
    private Long tenantId;
    private String channel;
    private String name;
    private String slug;
    private String summary;
    private String description;
    private Long coverAssetId;
    private Long categoryId;
    private String tagsJson;
    private String formSchemaJson;
    private String schemaHash;
    private String status;
    private Boolean recommended;
    private Integer sortNo;
    private Integer estimatedDurationSeconds;
    private String billingMode;
    private LocalDateTime enabledAt;
    private LocalDateTime executionRelevantUpdatedAt;
    @Version
    private Long rowRevision;
    @TableLogic
    private String delFlag;
}
