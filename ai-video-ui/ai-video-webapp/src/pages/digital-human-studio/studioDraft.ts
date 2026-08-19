import type { DigitalHumanJob } from '@/services/ai-video/digitalHuman/types';
import type { GeneratedQuestionnaire } from '@/services/ai-video/questionnaire/types';
import type { GeneratedScript } from '@/services/ai-video/script-generation/types';
import {
  initialStudioState,
  type StudioState,
  type VideoGenerationIntent,
  type VoiceGenerationIntent,
} from './model';

export const STUDIO_DRAFT_SCHEMA_VERSION = 'studio-workflow-1' as const;

interface StudioDraftSnapshot {
  schemaVersion: typeof STUDIO_DRAFT_SCHEMA_VERSION;
  step: number;
  industry: string | null;
  purpose: string | null;
  customIndustry: string;
  customPurpose: string;
  survey: Record<string, string[]>;
  surveyOtherAnswers: Record<string, string>;
  surveyCursor: number;
  questionnaire: GeneratedQuestionnaire | null;
  duration: number;
  supplement: string;
  selectedScript: number;
  scriptVersions: GeneratedScript[];
  scriptBodies: string[];
  selectedAvatar: string | null;
  selectedVoice: string | null;
  voiceGenerationIntent: VoiceGenerationIntent | null;
  voiceJobId: string | null;
  videoGenerationIntent: VideoGenerationIntent | null;
  videoJobId: string | null;
  timelineProjectId: string | null;
  timelineSourceTaskId: string | null;
}

function withoutLocalFiles(state: StudioState): StudioDraftSnapshot {
  return {
    schemaVersion: STUDIO_DRAFT_SCHEMA_VERSION,
    step: state.step,
    industry: state.industry,
    purpose: state.purpose,
    customIndustry: state.customIndustry,
    customPurpose: state.customPurpose,
    survey: state.survey,
    surveyOtherAnswers: state.surveyOtherAnswers,
    surveyCursor: state.surveyCursor,
    questionnaire: state.questionnaire,
    duration: state.duration,
    supplement: state.supplement,
    selectedScript: state.selectedScript,
    scriptVersions: state.scriptVersions,
    scriptBodies: state.scriptBodies,
    selectedAvatar: state.selectedAvatar,
    selectedVoice: state.selectedVoice,
    voiceGenerationIntent: state.voiceGenerationIntent?.referenceVoiceId
      ? {
          idempotencyKey: state.voiceGenerationIntent.idempotencyKey,
          referenceVoiceId: state.voiceGenerationIntent.referenceVoiceId,
          scriptText: state.voiceGenerationIntent.scriptText,
        }
      : null,
    voiceJobId: state.voiceJob?.jobId ?? null,
    videoGenerationIntent: state.videoGenerationIntent?.portraitId
      ? {
          idempotencyKey: state.videoGenerationIntent.idempotencyKey,
          portraitId: state.videoGenerationIntent.portraitId,
          voiceJobId: state.videoGenerationIntent.voiceJobId,
        }
      : null,
    videoJobId: state.videoJob?.jobId ?? null,
    timelineProjectId: state.timelineProjectId,
    timelineSourceTaskId: state.timelineSourceTaskId,
  };
}

export function serializeStudioDraft(state: StudioState): string {
  return JSON.stringify(withoutLocalFiles(state));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isStringArray(value: unknown): value is string[] {
  return (
    Array.isArray(value) && value.every((item) => typeof item === 'string')
  );
}

function isStringArrayRecord(
  value: unknown,
): value is Record<string, string[]> {
  return isRecord(value) && Object.values(value).every(isStringArray);
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return (
    isRecord(value) &&
    Object.values(value).every((item) => typeof item === 'string')
  );
}

function parseSnapshot(snapshotJson: string): StudioDraftSnapshot | null {
  try {
    const value: unknown = JSON.parse(snapshotJson);
    if (!isRecord(value) || value.schemaVersion !== STUDIO_DRAFT_SCHEMA_VERSION)
      return null;
    if (
      !Number.isInteger(value.step) ||
      (value.step as number) < 0 ||
      (value.step as number) > 6 ||
      !isNullableString(value.industry) ||
      !isNullableString(value.purpose) ||
      typeof value.customIndustry !== 'string' ||
      typeof value.customPurpose !== 'string' ||
      !isStringArrayRecord(value.survey) ||
      !isStringRecord(value.surveyOtherAnswers) ||
      !Number.isInteger(value.surveyCursor) ||
      !Number.isInteger(value.duration) ||
      typeof value.supplement !== 'string' ||
      !Number.isInteger(value.selectedScript) ||
      !Array.isArray(value.scriptVersions) ||
      !isStringArray(value.scriptBodies) ||
      !isNullableString(value.selectedAvatar) ||
      !isNullableString(value.selectedVoice) ||
      !isNullableString(value.voiceJobId) ||
      !isNullableString(value.videoJobId) ||
      !isNullableString(value.timelineProjectId) ||
      !isNullableString(value.timelineSourceTaskId)
    ) {
      return null;
    }
    return value as unknown as StudioDraftSnapshot;
  } catch {
    return null;
  }
}

export interface StudioDraftJobIds {
  voiceJobId: string | null;
  videoJobId: string | null;
}

export function readStudioDraftJobIds(
  snapshotJson: string,
): StudioDraftJobIds | null {
  const snapshot = parseSnapshot(snapshotJson);
  return snapshot
    ? { voiceJobId: snapshot.voiceJobId, videoJobId: snapshot.videoJobId }
    : null;
}

export function restoreStudioDraft(
  snapshotJson: string,
  voiceJob: DigitalHumanJob | null,
  videoJob: DigitalHumanJob | null,
): StudioState | null {
  const snapshot = parseSnapshot(snapshotJson);
  if (!snapshot) return null;
  const safeVoiceJob = voiceJob?.jobType === 'voice_generate' ? voiceJob : null;
  const safeVideoJob =
    videoJob?.jobType === 'video_generate' &&
    safeVoiceJob &&
    videoJob.parentJobId === safeVoiceJob.jobId
      ? videoJob
      : null;
  let safeStep = snapshot.step;
  if (
    safeStep >= 4 &&
    !(
      safeVoiceJob?.status === 'succeeded' &&
      safeVoiceJob.voiceConfirmed &&
      safeVoiceJob.outputAvailable
    )
  ) {
    safeStep = 3;
  }
  if (
    safeStep >= 5 &&
    !(
      safeVideoJob?.status === 'succeeded' &&
      safeVideoJob.outputAvailable &&
      snapshot.timelineProjectId
    )
  ) {
    safeStep = 4;
  }
  const {
    schemaVersion: _schemaVersion,
    voiceJobId: _voiceJobId,
    videoJobId: _videoJobId,
    ...stored
  } = snapshot;
  return {
    ...initialStudioState,
    ...stored,
    route: 'create',
    step: safeStep,
    portraitImage: null,
    referenceAudio: null,
    voiceJob: safeVoiceJob,
    videoJob: safeVideoJob,
  };
}
