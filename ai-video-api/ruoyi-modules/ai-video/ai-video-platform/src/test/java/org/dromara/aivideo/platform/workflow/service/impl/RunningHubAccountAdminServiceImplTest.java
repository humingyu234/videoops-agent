package org.dromara.aivideo.platform.workflow.service.impl;

import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.CreateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.RunningHubAccountQueryBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.UpdateRunningHubAccountBo;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IRunningHubParameterInspectionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class RunningHubAccountAdminServiceImplTest {

    @Mock
    private IRunningHubAccountService runningHubAccountService;

    private RunningHubAccountAdminServiceImpl service;

    @Mock
    private IRunningHubParameterInspectionService parameterInspectionService;

    @BeforeEach
    void setUp() {
        service = new RunningHubAccountAdminServiceImpl(runningHubAccountService, parameterInspectionService);
    }

    @Test
    void mapsPageAndKeepsMaskedCredentialOnly() {
        var query = new RunningHubAccountQueryBo();
        query.setKeyword("primary");
        query.setEnabled(true);
        var pageQuery = new PageQuery(10, 1);
        var row = new RunningHubAccountDTOs.Summary(
            "201", "primary", "***1234", true, true, "unknown", null, null, 2L,
            LocalDateTime.of(2026, 8, 11, 10, 0));
        when(runningHubAccountService.queryPage(any(), eq(pageQuery)))
            .thenReturn(PageResult.build(List.of(row), 7));

        var result = service.page(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(7);
        assertThat(result.getRows()).singleElement().satisfies(vo -> {
            assertThat(vo.accountId()).isEqualTo("201");
            assertThat(vo.apiKeyMasked()).isEqualTo("***1234");
            assertThat(vo.hasApiKey()).isTrue();
        });
        ArgumentCaptor<RunningHubAccountDTOs.Query> captor =
            ArgumentCaptor.forClass(RunningHubAccountDTOs.Query.class);
        verify(runningHubAccountService).queryPage(captor.capture(), eq(pageQuery));
        assertThat(captor.getValue()).isEqualTo(new RunningHubAccountDTOs.Query("primary", true));
    }

    @Test
    void createWritesApiKeyThroughSaveCommandAndDoesNotReturnIt() {
        doAnswer(invocation -> {
            RunningHubAccountDTOs.Save mapped = invocation.getArgument(1);
            assertThat(mapped.accountName()).isEqualTo("primary");
            assertThat(new String(mapped.apiKey())).isEqualTo("plain-api-key");
            assertThat(mapped.expectedRevision()).isNull();
            return "201";
        }).when(runningHubAccountService).create(eq(9001L), any());

        assertThat(service.create(new CreateRunningHubAccountBo("primary", "plain-api-key"), 9001L))
            .isEqualTo("201");
    }

    @Test
    void blankApiKeyOnUpdateMeansKeepExistingCredential() {
        service.update("201", new UpdateRunningHubAccountBo("renamed", "   ", 6L), 9001L);

        ArgumentCaptor<RunningHubAccountDTOs.Save> captor = ArgumentCaptor.forClass(RunningHubAccountDTOs.Save.class);
        verify(runningHubAccountService).update(eq(9001L), eq("201"), captor.capture());
        assertThat(captor.getValue().accountName()).isEqualTo("renamed");
        assertThat(captor.getValue().apiKey()).isNull();
        assertThat(captor.getValue().expectedRevision()).isEqualTo(6L);
    }
}
