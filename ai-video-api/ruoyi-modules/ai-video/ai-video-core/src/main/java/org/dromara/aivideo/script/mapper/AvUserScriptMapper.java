package org.dromara.aivideo.script.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.aivideo.script.domain.AvUserScript;
import org.dromara.aivideo.script.dto.UserScriptListDTO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/** 用户文案主体 Mapper。 */
public interface AvUserScriptMapper extends BaseMapperPlus<AvUserScript, AvUserScript> {
    Page<UserScriptListDTO> selectOwnedPage(Page<UserScriptListDTO> page,
        @Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId,
        @Param("keyword") String keyword, @Param("orderByColumn") String orderByColumn,
        @Param("isAsc") String isAsc);

    AvUserScript selectOwned(@Param("id") Long id, @Param("tenantId") Long tenantId,
        @Param("ownerId") Long ownerId);

    AvUserScript selectOwnedByIntent(@Param("tenantId") Long tenantId,
        @Param("ownerId") Long ownerId, @Param("idempotencyKey") String idempotencyKey);

    int updateCurrentVersion(@Param("id") Long id, @Param("versionId") Long versionId,
        @Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId, @Param("actorId") Long actorId);

    int updateForNewVersion(@Param("id") Long id, @Param("tenantId") Long tenantId,
        @Param("ownerId") Long ownerId, @Param("parentVersionId") Long parentVersionId,
        @Param("expectedRevision") Long expectedRevision, @Param("displayTitle") String displayTitle,
        @Param("versionId") Long versionId, @Param("actorId") Long actorId);

    int softDeleteOwned(@Param("id") Long id, @Param("tenantId") Long tenantId,
        @Param("ownerId") Long ownerId, @Param("actorId") Long actorId);
}
