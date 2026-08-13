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
@TableName(value = "av_workflow_execution_config", excludeProperty = "createDept")
public class WorkflowExecutionConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "execution_config_id", type = IdType.ASSIGN_ID)
    private Long executionConfigId;
    private Long tenantId;
    private Long templateId;
    private Long runninghubAccountId;
    @ToString.Exclude
    private String accessPasswordCiphertext;
    private String executionMode;
    private String workflowId;
    private String webappId;
    private String instanceType;
    private String inputMappingJson;
    private String outputPolicyJson;
    private Integer timeoutSeconds;
    private Boolean enabled;
    private String lastTestStatus;
    private Long lastTestTaskId;
    private Long lastTestTemplateRevision;
    private Long lastTestExecutionRevision;
    private Long lastTestAccountRevision;
    private LocalDateTime lastTestTime;
    private String lastTestSummary;
    @Version
    private Long rowRevision;
    @TableLogic
    private String delFlag;
}
