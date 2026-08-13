package org.dromara.aivideo.script.service.impl;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.script.domain.AvScriptVersion;
import org.dromara.aivideo.script.domain.AvUserScript;
import org.dromara.aivideo.script.dto.UserScriptCreateDTO;
import org.dromara.aivideo.script.mapper.AvScriptVersionMapper;
import org.dromara.aivideo.script.mapper.AvUserScriptMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class UserScriptServiceImplTest {
    @Mock
    private AvUserScriptMapper userScriptMapper;
    @Mock
    private AvScriptVersionMapper scriptVersionMapper;

    @Test
    void createsManualInputAndCalculatesEffectiveCharacters() {
        doAnswer(invocation -> {
            AvUserScript value = invocation.getArgument(0);
            value.setId(101L);
            return 1;
        }).when(userScriptMapper).insert(any(AvUserScript.class));
        doAnswer(invocation -> {
            AvScriptVersion value = invocation.getArgument(0);
            value.setId(201L);
            return 1;
        }).when(scriptVersionMapper).insert(any(AvScriptVersion.class));
        when(userScriptMapper.updateCurrentVersion(101L, 201L, 7L, 9L, 9L)).thenReturn(1);

        var service = new UserScriptServiceImpl(userScriptMapper, scriptVersionMapper);
        var result = service.create(new UserScriptCreateDTO(" 标题 ", "你好, hello world 123!", "intent-1"), principal());

        assertThat(result.scriptId()).isEqualTo("101");
        assertThat(result.currentVersionId()).isEqualTo("201");
        assertThat(result.effectiveCharacterCount()).isEqualTo(5);
        assertThat(result.estimatedDurationSeconds()).isEqualTo(2);
        verify(userScriptMapper).insert(any(AvUserScript.class));
        verify(scriptVersionMapper).insert(any(AvScriptVersion.class));
    }

    @Test
    void rejectsSameCreateIntentForDifferentNormalizedRequest() {
        AvUserScript existing = new AvUserScript();
        existing.setCreateRequestHash("different");
        when(userScriptMapper.selectOwnedByIntent(7L, 9L, "intent-1")).thenReturn(existing);

        var service = new UserScriptServiceImpl(userScriptMapper, scriptVersionMapper);

        assertThatThrownBy(() -> service.create(
            new UserScriptCreateDTO("标题", "正文", "intent-1"), principal()))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(46116));
    }

    @Test
    void measuresLimitsByUnicodeCodePoint() {
        var service = new UserScriptServiceImpl(userScriptMapper, scriptVersionMapper);

        assertThatThrownBy(() -> service.create(
            new UserScriptCreateDTO("😀".repeat(101), "正文", "intent-2"), principal()))
            .isInstanceOfSatisfying(ServiceException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(400));
    }

    private AppPrincipalSnapshotDTO principal() {
        var workspace = new AppWorkspaceSessionSnapshotDTO("personal-9", "personal", 7L,
            "app_user", 9L, "app_user", 9L, "personal_creator",
            Set.of("aivideo:script:query", "aivideo:script:edit", "aivideo:script:remove"), 1L, null);
        return new AppPrincipalSnapshotDTO(9L, "creator", "web", 1L, 1L, 1L, 1L, workspace);
    }
}
