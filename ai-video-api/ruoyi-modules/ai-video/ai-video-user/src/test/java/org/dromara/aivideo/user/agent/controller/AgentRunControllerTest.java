package org.dromara.aivideo.user.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.agent.domain.bo.AgentApprovalDecisionBo;
import org.dromara.aivideo.user.agent.domain.bo.AgentRunRevisionBo;
import org.dromara.aivideo.user.agent.domain.bo.CreateAgentRunBo;
import org.dromara.aivideo.user.agent.service.IAgentRunApplicationService;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AgentRunControllerTest {

    @Test
    void routesUseOnlyAppPermissionsAndSafeMutationLogs() throws Exception {
        var create = AgentRunController.class.getDeclaredMethod("create", CreateAgentRunBo.class);
        assertPermission(create.getAnnotation(SaCheckPermission.class), "aivideo:studio:generate");
        assertSafe(create.getAnnotation(Log.class));
        assertThat(create.getAnnotation(PostMapping.class).value()).isEmpty();

        var detail = AgentRunController.class.getDeclaredMethod("detail", String.class);
        assertPermission(detail.getAnnotation(SaCheckPermission.class), "aivideo:studio:query");
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{agentRunId}");

        var advance = AgentRunController.class.getDeclaredMethod(
            "advance", String.class, AgentRunRevisionBo.class);
        assertPermission(advance.getAnnotation(SaCheckPermission.class), "aivideo:studio:generate");
        assertSafe(advance.getAnnotation(Log.class));

        var approval = AgentRunController.class.getDeclaredMethod(
            "decideApproval", String.class, String.class, AgentApprovalDecisionBo.class);
        assertPermission(approval.getAnnotation(SaCheckPermission.class), "aivideo:studio:generate");
        assertSafe(approval.getAnnotation(Log.class));
    }

    @Test
    void delegatesOnlyTheAuthenticatedPrincipalAndWireFields() {
        IAgentRunApplicationService service = mock(IAgentRunApplicationService.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        AppLoginHelper login = mock(AppLoginHelper.class);
        when(login.getPrincipal()).thenReturn(principal);
        AgentRunController controller = new AgentRunController(service, login);

        CreateAgentRunBo create = new CreateAgentRunBo();
        create.setStartAt("new");
        create.setScriptText("固定文案");
        create.setReferenceVoiceId("11");
        create.setPortraitId("12");
        create.setProjectTitle("黄金链");
        create.setIdempotencyKey("client-key");
        controller.create(create);

        AgentRunRevisionBo revision = new AgentRunRevisionBo();
        revision.setRowVersion(3L);
        revision.setContractRevision(1L);
        controller.advance("101", revision);
        controller.cancel("101", revision);

        AgentApprovalDecisionBo decision = new AgentApprovalDecisionBo();
        decision.setRowVersion(4L);
        decision.setContractRevision(1L);
        decision.setApprovalRevision(2L);
        decision.setType("final");
        decision.setApproved(true);
        controller.decideApproval("101", "202", decision);

        verify(service).create(principal, create);
        verify(service).advance(principal, "101", revision);
        verify(service).cancel(principal, "101", revision);
        verify(service).decideApproval(principal, "101", "202", decision);
    }

    @Test
    void strictBodiesRejectOwnerWorkerLeaseAndUnknownFields() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        CreateAgentRunBo reuse = json.readValue("""
            {"startAt":"video_job","videoJobId":"601",
             "projectTitle":"复用视频","idempotencyKey":"reuse-video"}
            """, CreateAgentRunBo.class);
        assertThat(reuse.getVideoJobId()).isEqualTo("601");
        assertThat(reuse.getScriptText()).isNull();
        assertThatThrownBy(() -> json.readValue("""
            {"startAt":"new","scriptText":"x","referenceVoiceId":"11","portraitId":"12",
             "projectTitle":"p","idempotencyKey":"k","ownerId":"7"}
            """, CreateAgentRunBo.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> json.readValue("""
            {"rowVersion":1,"contractRevision":1,"workerId":"forged"}
            """, AgentRunRevisionBo.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> json.readValue("""
            {"rowVersion":1,"contractRevision":1,"approvalRevision":1,
             "type":"initial","approved":true,"leaseToken":"forged"}
            """, AgentApprovalDecisionBo.class)).isInstanceOf(Exception.class);
    }

    private void assertPermission(SaCheckPermission permission, String expected) {
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly(expected);
    }

    private void assertSafe(Log log) {
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }
}
