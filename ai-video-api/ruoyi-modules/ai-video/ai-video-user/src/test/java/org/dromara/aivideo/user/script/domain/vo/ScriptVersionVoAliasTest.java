package org.dromara.aivideo.user.script.domain.vo;

import org.apache.ibatis.type.TypeAliasRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("dev")
class ScriptVersionVoAliasTest {

    @Test
    void shouldUseDistinctMybatisAliasFromStudioScriptVersion() {
        TypeAliasRegistry registry = new TypeAliasRegistry();

        assertDoesNotThrow(() -> {
            registry.registerAlias(org.dromara.aivideo.user.studio.domain.vo.ScriptVersionVo.class);
            registry.registerAlias(ScriptVersionVo.class);
        });
    }
}
