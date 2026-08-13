package org.dromara.aivideo.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** Private object-storage fact backing a workflow upload. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_file_object", excludeProperty = {"createDept", "createBy", "updateBy"})
public class FileObject extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "file_id", type = IdType.ASSIGN_ID)
    private Long fileId;
    private Long tenantId;
    private String workspaceId;
    private Long ownerUserId;
    private Long ossId;
    private String objectKey;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String status;
}
