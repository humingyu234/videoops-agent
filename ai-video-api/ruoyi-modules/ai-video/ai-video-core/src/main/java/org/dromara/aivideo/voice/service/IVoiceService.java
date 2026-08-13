package org.dromara.aivideo.voice.service;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.voice.dto.*;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.time.Instant;

public interface IVoiceService {
    PageResult<VoiceDTO> queryPage(VoiceQueryDTO query, AppPrincipalSnapshotDTO principal, PageQuery pageQuery);
    VoiceDTO queryById(String voiceId, AppPrincipalSnapshotDTO principal);
    VoiceDTO create(CreateVoiceDTO command, AppPrincipalSnapshotDTO principal);
    String createAccessUrl(String voiceId, AppPrincipalSnapshotDTO principal);
    VoiceTranscriptionLeaseDTO claimNext(String workerId, Instant now);
    boolean completeTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionResultDTO result);
    void failTranscription(VoiceTranscriptionLeaseDTO lease, VoiceTranscriptionFailureDTO failure);
    VoiceDTO updateTranscript(UpdateVoiceTranscriptDTO command, AppPrincipalSnapshotDTO principal);
    VoiceDTO startTranscription(StartVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal);
    VoiceDTO retryTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal);
    VoiceDTO resyncTranscription(RetryVoiceTranscriptionDTO command, AppPrincipalSnapshotDTO principal);
    void deleteOwnedVoice(String voiceId, AppPrincipalSnapshotDTO principal);
}
