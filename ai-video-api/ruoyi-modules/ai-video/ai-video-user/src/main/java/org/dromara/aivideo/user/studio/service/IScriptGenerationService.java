package org.dromara.aivideo.user.studio.service;

import org.dromara.aivideo.user.studio.domain.bo.ScriptGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.ScriptGenerateVo;

/** 用户端文案生成服务。 */
public interface IScriptGenerationService {

    /**
     * 生成三套候选文案。
     *
     * @param request 已确认需求
     * @return DeepSeek 生成结果
     */
    ScriptGenerateVo generate(ScriptGenerateBo request);
}
