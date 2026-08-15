package org.dromara.aivideo.creation.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

public interface CreationAssetMapper extends BaseMapperPlus<CreationAsset, CreationAsset> {

    /** Selects one immutable render output only when the persisted task and asset facts still agree. */
    @Select("""
        SELECT asset.*
        FROM av_creation_asset asset
        INNER JOIN av_ai_task task
            ON task.task_id = #{taskId}
           AND task.owner_user_id = #{ownerUserId}
           AND task.task_type = 'timeline_render'
           AND task.resource_type = 'creation_project'
           AND task.task_status = 'success'
           AND task.result_asset_id = asset.asset_id
        WHERE asset.asset_id = #{resultAssetId}
          AND asset.owner_user_id = #{ownerUserId}
          AND asset.source_ref_id = #{taskId}
          AND asset.asset_status = 'ready'
          AND asset.asset_type = 'video'
          AND asset.usage_origin = 'timeline_render_output'
          AND asset.del_flag = '0'
        """)
    CreationAsset selectOwnedTimelineRenderOutput(@Param("ownerUserId") long ownerUserId,
                                                   @Param("taskId") long taskId,
                                                   @Param("resultAssetId") long resultAssetId);
}
