package org.dromara.aivideo.studio.draft.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.studio.draft.domain.StudioWorkflowDraft;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

@InterceptorIgnore(tenantLine = "true")
public interface StudioWorkflowDraftMapper extends BaseMapperPlus<StudioWorkflowDraft, StudioWorkflowDraft> {

    @Select("""
        SELECT * FROM av_studio_workflow_draft
        WHERE tenant_id = #{tenantId} AND owner_user_id = #{ownerUserId}
        LIMIT 1
        """)
    StudioWorkflowDraft selectOwned(@Param("tenantId") Long tenantId,
                                    @Param("ownerUserId") Long ownerUserId);

    @Update("""
        UPDATE av_studio_workflow_draft
        SET revision = revision + 1,
            current_step = #{currentStep},
            schema_version = #{schemaVersion},
            snapshot_json = #{snapshotJson},
            update_by = #{ownerUserId},
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND tenant_id = #{tenantId}
          AND owner_user_id = #{ownerUserId}
          AND revision = #{expectedRevision}
        """)
    int updateOwned(@Param("id") Long id,
                    @Param("tenantId") Long tenantId,
                    @Param("ownerUserId") Long ownerUserId,
                    @Param("expectedRevision") Long expectedRevision,
                    @Param("currentStep") Integer currentStep,
                    @Param("schemaVersion") String schemaVersion,
                    @Param("snapshotJson") String snapshotJson);

    @Delete("""
        DELETE FROM av_studio_workflow_draft
        WHERE tenant_id = #{tenantId} AND owner_user_id = #{ownerUserId}
        """)
    int deleteOwned(@Param("tenantId") Long tenantId,
                    @Param("ownerUserId") Long ownerUserId);
}
