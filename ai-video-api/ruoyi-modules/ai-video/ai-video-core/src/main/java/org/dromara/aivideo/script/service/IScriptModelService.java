package org.dromara.aivideo.script.service;

import org.dromara.aivideo.script.dto.ScriptGeneratedVersionDTO;
import org.dromara.aivideo.script.dto.ScriptGenerationRequestDTO;

import java.util.List;

/** 文案生成模型服务。 */
public interface IScriptModelService {

    /**
     * 基于已确认的需求上下文生成三套口播文案。
     *
     * @param request 生成上下文
     * @return 三套候选文案
     */
    List<ScriptGeneratedVersionDTO> generate(ScriptGenerationRequestDTO request);
}
