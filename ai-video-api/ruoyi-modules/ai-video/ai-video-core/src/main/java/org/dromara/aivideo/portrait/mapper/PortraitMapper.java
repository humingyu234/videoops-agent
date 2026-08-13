package org.dromara.aivideo.portrait.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.aivideo.portrait.domain.Portrait;
import org.dromara.aivideo.portrait.dto.PortraitPageRowDTO;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Param;

/** 人物形象 Mapper。 */
public interface PortraitMapper extends BaseMapperPlus<Portrait, Portrait> {
    Page<PortraitPageRowDTO> selectOwnedPage(Page<PortraitPageRowDTO> page,
                                               @Param("tenantId") Long tenantId,
                                               @Param("workspaceId") String workspaceId,
                                               @Param("ownerId") Long ownerId,
                                               @Param("keyword") String keyword,
                                               @Param("availabilityStatus") String availabilityStatus,
                                               @Param("gender") String gender);

    Portrait selectOwnedIncludingDeleted(@Param("portraitId") Long portraitId,
                                          @Param("tenantId") Long tenantId,
                                          @Param("workspaceId") String workspaceId,
                                          @Param("ownerId") Long ownerId);
}
