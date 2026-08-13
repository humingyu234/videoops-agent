package org.dromara.aivideo.user.studio.domain.vo;

import java.util.List;

/** 用户端文案生成结果。 */
public record ScriptGenerateVo(
    List<ScriptVersionVo> scripts,
    List<String> knowledgeVersionIds,
    String knowledgeHash,
    String modelMode
) {

    public ScriptGenerateVo {
        scripts = List.copyOf(scripts);
        knowledgeVersionIds = List.copyOf(knowledgeVersionIds);
    }
}
