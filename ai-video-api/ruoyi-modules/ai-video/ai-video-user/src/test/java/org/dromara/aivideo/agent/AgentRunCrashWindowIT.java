package org.dromara.aivideo.agent;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.agent.service.impl.AgentRunOrchestrationServiceImpl;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.asset.service.VoiceAssetReader;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisRequestDTO;
import org.dromara.aivideo.digitalhuman.dto.VoiceSynthesisResultDTO;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanVideoService;
import org.dromara.aivideo.digitalhuman.service.IVoiceSynthesisService;
import org.dromara.aivideo.digitalhuman.service.impl.DigitalHumanGenerationServiceImpl;
import org.dromara.aivideo.digitalhuman.service.impl.DigitalHumanResourceGenerationServiceImpl;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.infra.digitalhuman.FileSystemDigitalHumanMediaStorageService;
import org.dromara.aivideo.portrait.service.IPortraitService;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.user.agent.service.impl.AgentToolServiceImpl;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.dromara.aivideo.voice.dto.VoiceDTO;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class AgentRunCrashWindowIT {

    private static final long OWNER_USER_ID = 801L;
    private static final byte[] REFERENCE_MP3 = {'I', 'D', '3', 1};

    private final AgentRunPersistenceIT fixture = new AgentRunPersistenceIT();
    private final CountingVoiceProvider provider = new CountingVoiceProvider();

    @TempDir
    Path mediaRoot;

    @BeforeEach
    void createTables() throws Exception {
        fixture.createFrozenContractTables();
    }

    @AfterEach
    void dropTables() throws Exception {
        fixture.dropFrozenContractTables();
    }

    @Test
    void acceptedVoiceTaskIsRecoveredAfterCrashBeforeWaitingCasWithoutSecondProviderSubmit() throws Exception {
        AppPrincipalSnapshotDTO principal = fixture.orchestrationPrincipal(OWNER_USER_ID);
        long agentRunId;
        long voiceJobId;
        IAgentRunService.AgentRunLease crashedLease;

        try (AnnotationConfigApplicationContext context = fixture.openServiceContext()) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            var run = fixture.createGoldenRun(runService, principal, "accepted-before-waiting-cas");
            agentRunId = run.agentRunId();
            IAgentToolService toolService = realToolService(context, principal);
            IAgentRunService crashingRunService = mock(IAgentRunService.class, delegatesTo(runService));
            AtomicReference<IAgentRunService.AgentRunLease> claimedLease = new AtomicReference<>();
            doAnswer(invocation -> {
                IAgentRunService.AgentRunLease lease = runService.claim(
                    invocation.getArgument(0), invocation.getArgument(1));
                claimedLease.set(lease);
                return lease;
            }).when(crashingRunService).claim(any(), any());
            doThrow(new SimulatedProcessDeath()).when(crashingRunService)
                .waitForExternalTask(any(), any());
            var orchestration = new AgentRunOrchestrationServiceImpl(
                crashingRunService, toolService, context.getBean(JsonMapper.class));

            assertThatThrownBy(() -> orchestration.advance(
                principal, fixture.advance(run, "agent-it-crash-a")))
                .isInstanceOf(SimulatedProcessDeath.class);
            crashedLease = claimedLease.get();
            assertThat(crashedLease).isNotNull();
            var acceptedJob = context.getBean(DigitalHumanGenerationJobMapper.class).selectByIdempotency(
                1L, OWNER_USER_ID, DigitalHumanJobType.VOICE_GENERATE,
                "agent-run:" + agentRunId + ":voice:0");
            assertThat(acceptedJob).isNotNull();
            voiceJobId = acceptedJob.getId();

            var notParked = runService.getOwnedRun(principal, agentRunId);
            assertThat(notParked.runStatus()).isEqualTo("running");
            assertThat(notParked.waitingTaskSource()).isNull();
            assertThat(notParked.waitingTaskId()).isNull();
        }

        assertThat(provider.acceptedSubmissions()).isEqualTo(1);
        assertThat(fixture.agentRunRowCount()).isEqualTo(1L);
        assertThat(fixture.digitalHumanJobRowCount()).isEqualTo(1L);
        assertThat(fixture.expireRunningLease(OWNER_USER_ID, crashedLease)).isEqualTo(1);

        try (AnnotationConfigApplicationContext context = fixture.openServiceContext()) {
            IAgentRunService runService = context.getBean(IAgentRunService.class);
            IAgentToolService toolService = realToolService(context, principal);
            var orchestration = new AgentRunOrchestrationServiceImpl(
                runService, toolService, context.getBean(JsonMapper.class));
            var running = runService.getOwnedRun(principal, agentRunId);

            AgentRunOrchestrationDTOs.AdvanceResult recovered = orchestration.advance(
                principal, fixture.advance(running, "agent-it-crash-b"));

            assertThat(recovered.runStatus()).isEqualTo("waiting_external_task");
            assertThat(recovered.waitingTaskSource()).isEqualTo("digital_human_generation");
            assertThat(recovered.waitingTaskId()).isEqualTo(voiceJobId);
            var persisted = runService.getOwnedRun(principal, agentRunId);
            assertThat(persisted.waitingTaskId()).isEqualTo(voiceJobId);
        }

        assertThat(provider.acceptedSubmissions()).isEqualTo(1);
        assertThat(fixture.agentRunRowCount()).isEqualTo(1L);
        assertThat(fixture.digitalHumanJobRowCount()).isEqualTo(1L);
    }

    private IAgentToolService realToolService(AnnotationConfigApplicationContext context,
                                              AppPrincipalSnapshotDTO principal) {
        IVoiceService voiceService = mock(IVoiceService.class);
        IAssetService assetService = mock(IAssetService.class);
        when(voiceService.queryById("501", principal)).thenReturn(new VoiceDTO(
            "501", "asset-501", "origin", "fixture voice", null, null, null, null,
            null, null, null, null, "unparsed", null, null, 0, "1", null, null));
        AssetDTO asset = new AssetDTO("asset-501", "ready", null, "reference.mp3",
            "audio/mpeg", "mp3", null, null, (long) REFERENCE_MP3.length, null);
        doAnswer(invocation -> {
            VoiceAssetReader<?> reader = invocation.getArgument(2);
            return reader.read(asset, new ByteArrayInputStream(REFERENCE_MP3));
        }).when(assetService).readOwnedVoiceAsset(eq("asset-501"), eq(principal), any());

        Executor sameThread = Runnable::run;
        IDigitalHumanGenerationService generationService = new DigitalHumanGenerationServiceImpl(
            context.getBean(DigitalHumanGenerationJobMapper.class), provider,
            mock(IDigitalHumanVideoService.class),
            new FileSystemDigitalHumanMediaStorageService(mediaRoot.toString()), sameThread);
        var resourceService = new DigitalHumanResourceGenerationServiceImpl(
            generationService, voiceService, mock(IPortraitService.class), assetService);
        return new AgentToolServiceImpl(resourceService, generationService,
            mock(ICreationProjectService.class), mock(ICreationAssetService.class),
            mock(TimelineTaskApplicationService.class), mock(IAiTaskService.class));
    }

    private static final class CountingVoiceProvider implements IVoiceSynthesisService {
        private final AtomicInteger accepted = new AtomicInteger();

        @Override
        public VoiceSynthesisResultDTO synthesize(VoiceSynthesisRequestDTO request) {
            accepted.incrementAndGet();
            byte[] payload = "accepted".getBytes(StandardCharsets.US_ASCII);
            byte[] wav = new byte[12 + payload.length];
            System.arraycopy(new byte[]{'R', 'I', 'F', 'F'}, 0, wav, 0, 4);
            System.arraycopy(new byte[]{'W', 'A', 'V', 'E'}, 0, wav, 8, 4);
            System.arraycopy(payload, 0, wav, 12, payload.length);
            return new VoiceSynthesisResultDTO(wav, "audio/wav", "wav");
        }

        int acceptedSubmissions() {
            return accepted.get();
        }
    }

    private static final class SimulatedProcessDeath extends Error {
    }
}
