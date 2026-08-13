package org.dromara.aivideo.script.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.aivideo.script.domain.AvScriptVersion;
import org.dromara.aivideo.script.dto.ScriptVersionSummaryDTO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/** 文案版本 Mapper。 */
public interface AvScriptVersionMapper extends BaseMapperPlus<AvScriptVersion, AvScriptVersion> {
    AvScriptVersion selectOwned(@Param("scriptId") Long scriptId, @Param("versionId") Long versionId,
        @Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId);

    AvScriptVersion selectByManualIntent(@Param("scriptId") Long scriptId,
        @Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId,
        @Param("idempotencyKey") String idempotencyKey);

    List<ScriptVersionSummaryDTO> selectSummaries(@Param("scriptId") Long scriptId,
        @Param("tenantId") Long tenantId, @Param("ownerId") Long ownerId);
}
