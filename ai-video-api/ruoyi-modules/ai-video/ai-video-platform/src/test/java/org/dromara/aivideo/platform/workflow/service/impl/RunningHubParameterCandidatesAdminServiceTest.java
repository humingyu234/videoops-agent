package org.dromara.aivideo.platform.workflow.service.impl;

import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.ParameterCandidatesBo;
import org.dromara.aivideo.workflow.dto.RunningHubParameterInspectionDTOs;
import org.dromara.aivideo.workflow.service.IRunningHubParameterInspectionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class RunningHubParameterCandidatesAdminServiceTest {

    @Test
    void mapsControlledInspectionResultWithoutProviderPayload() {
        IRunningHubParameterInspectionService inspectionService = mock(IRunningHubParameterInspectionService.class);
        RunningHubAccountAdminServiceImpl service = new RunningHubAccountAdminServiceImpl(
            mock(org.dromara.aivideo.workflow.service.IRunningHubAccountService.class), inspectionService);
        var request = new RunningHubParameterInspectionDTOs.Request(
            "201", "runninghub_ai_app", null, "1937084629516193794");
        when(inspectionService.inspect(request)).thenReturn(new RunningHubParameterInspectionDTOs.Result(
            "Flux Kontext", List.of(new RunningHubParameterInspectionDTOs.Candidate(
                "37", "Node", "model", "select", "模型", "pro",
                List.of(new RunningHubParameterInspectionDTOs.Option("pro", "Pro"))))));

        var result = service.parameterCandidates(new ParameterCandidatesBo(
            "201", "runninghub_ai_app", null, "1937084629516193794"));

        assertThat(result.webAppName()).isEqualTo("Flux Kontext");
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.nodeId()).isEqualTo("37");
            assertThat(candidate.options()).singleElement().satisfies(option ->
                assertThat(option.value()).isEqualTo("pro"));
        });
        verify(inspectionService).inspect(request);
    }
}
