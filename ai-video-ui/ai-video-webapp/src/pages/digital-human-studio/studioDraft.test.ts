import type { DigitalHumanJob } from '@/services/ai-video/digitalHuman/types';
import { initialStudioState } from './model';
import {
  readStudioDraftJobIds,
  restoreStudioDraft,
  serializeStudioDraft,
} from './studioDraft';

const job = (
  jobId: string,
  jobType: DigitalHumanJob['jobType'],
  parentJobId: string | null,
): DigitalHumanJob => ({
  errorMessage: null,
  jobId,
  jobType,
  outputAvailable: true,
  parentJobId,
  progress: 100,
  stage: 'completed',
  status: 'succeeded',
  voiceConfirmed: jobType === 'voice_generate',
});

describe('studio draft recovery', () => {
  it('restores the same accepted jobs without serializing browser files', () => {
    const voiceJob = job('voice-1', 'voice_generate', null);
    const videoJob = job('video-1', 'video_generate', 'voice-1');
    const state = {
      ...initialStudioState,
      step: 5,
      scriptBodies: ['真实文案'],
      portraitImage: new File(['portrait'], 'portrait.png'),
      referenceAudio: new File(['voice'], 'voice.wav'),
      voiceJob,
      videoJob,
      timelineProjectId: 'project-1',
      timelineSourceTaskId: 'video-1',
    };

    const snapshot = serializeStudioDraft(state);
    expect(snapshot).not.toContain('portrait.png');
    expect(snapshot).not.toContain('voice.wav');
    expect(readStudioDraftJobIds(snapshot)).toEqual({
      voiceJobId: 'voice-1',
      videoJobId: 'video-1',
    });
    expect(restoreStudioDraft(snapshot, voiceJob, videoJob)).toMatchObject({
      step: 5,
      voiceJob: { jobId: 'voice-1' },
      videoJob: { jobId: 'video-1' },
      timelineProjectId: 'project-1',
    });
  });

  it('falls back before generation when restored jobs are missing or unrelated', () => {
    const voiceJob = job('voice-1', 'voice_generate', null);
    const unrelatedVideo = job('video-2', 'video_generate', 'voice-other');
    const snapshot = serializeStudioDraft({
      ...initialStudioState,
      step: 5,
      voiceJob,
      videoJob: unrelatedVideo,
      timelineProjectId: 'project-1',
    });

    expect(
      restoreStudioDraft(snapshot, voiceJob, unrelatedVideo),
    ).toMatchObject({
      step: 4,
      videoJob: null,
    });
    expect(restoreStudioDraft(snapshot, null, null)).toMatchObject({
      step: 3,
      voiceJob: null,
      videoJob: null,
    });
  });

});
