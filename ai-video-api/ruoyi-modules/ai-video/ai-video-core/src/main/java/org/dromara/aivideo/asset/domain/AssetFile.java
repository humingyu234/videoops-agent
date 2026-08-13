package org.dromara.aivideo.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 人物形象使用的私有文件资产。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_asset")
public class AssetFile extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "asset_id", type = IdType.ASSIGN_ID)
    private Long assetId;
    private Long fileId;
    private Long tenantId;
    private String workspaceId;
    private Long ownerId;
    private String category;
    private String objectKey;
    private String originalName;
    private String contentType;
    private String fileFormat;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String status;
    private String failureReason;
    @TableLogic
    private String delFlag;
}
