package org.dromara.aivideo.script.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.script.dto.ScriptVersionDTO;
import org.dromara.aivideo.script.dto.UserScriptCreateDTO;
import org.dromara.aivideo.script.dto.UserScriptDetailDTO;
import org.dromara.aivideo.script.dto.UserScriptEditDTO;
import org.dromara.aivideo.script.dto.UserScriptListDTO;
import org.dromara.aivideo.script.dto.UserScriptQueryDTO;
import org.dromara.aivideo.script.dto.UserScriptSaveResultDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/** 用户个人文案服务。 */
public interface IUserScriptService {
    PageResult<UserScriptListDTO> queryPage(UserScriptQueryDTO query, AppPrincipalSnapshotDTO principal,
                                             PageQuery pageQuery);
    UserScriptDetailDTO queryById(String scriptId, AppPrincipalSnapshotDTO principal);
    ScriptVersionDTO queryVersion(String scriptId, String versionId, AppPrincipalSnapshotDTO principal);
    UserScriptSaveResultDTO create(UserScriptCreateDTO command, AppPrincipalSnapshotDTO principal);
    UserScriptSaveResultDTO createVersion(UserScriptEditDTO command, AppPrincipalSnapshotDTO principal);
    void delete(String scriptId, AppPrincipalSnapshotDTO principal);
}
