package org.dromara.aivideo.portrait.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.portrait.dto.CreatePortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitAccessUrlDTO;
import org.dromara.aivideo.portrait.dto.PortraitDTO;
import org.dromara.aivideo.portrait.dto.PortraitQueryDTO;
import org.dromara.aivideo.portrait.dto.UpdatePortraitDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/** 用户人物形象业务服务。 */
public interface IPortraitService {
    PageResult<PortraitDTO> queryPage(PortraitQueryDTO query, AppPrincipalSnapshotDTO principal, PageQuery pageQuery);
    PortraitDTO queryById(String portraitId, AppPrincipalSnapshotDTO principal);
    PortraitDTO create(CreatePortraitDTO command, AppPrincipalSnapshotDTO principal);
    PortraitDTO update(UpdatePortraitDTO command, AppPrincipalSnapshotDTO principal);
    void delete(String portraitId, String expectedRevision, AppPrincipalSnapshotDTO principal);
    PortraitAccessUrlDTO createAccessUrl(String portraitId, AppPrincipalSnapshotDTO principal);
}
