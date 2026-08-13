export const DIGITAL_HUMAN_JOB_TYPES = [
  'voice_generate',
  'video_generate',
] as const;

export type DigitalHumanJobType = (typeof DIGITAL_HUMAN_JOB_TYPES)[number];

export const DIGITAL_HUMAN_JOB_STATUSES = [
  'queued',
  'running',
  'succeeded',
  'failed',
] as const;

export type DigitalHumanJobStatus =
  (typeof DIGITAL_HUMAN_JOB_STATUSES)[number];

export const DIGITAL_HUMAN_JOB_STAGES = [
  'queued',
  'voice_synthesizing',
  'awaiting_voice_confirmation',
  'video_submitted',
  'video_rendering',
  'completed',
  'failed',
] as const;

export type DigitalHumanJobStage =
  (typeof DIGITAL_HUMAN_JOB_STAGES)[number];

export interface DigitalHumanJob {
  errorMessage: string | null;
  jobId: string;
  jobType: DigitalHumanJobType;
  outputAvailable: boolean;
  parentJobId: string | null;
  progress: number;
  stage: DigitalHumanJobStage;
  status: DigitalHumanJobStatus;
  voiceConfirmed: boolean;
}

interface VoiceJobBaseInput {
  idempotencyKey: string;
  scriptText: string;
}

export type CreateVoiceJobInput = VoiceJobBaseInput & (
  | { referenceVoiceId: string; referenceAudio?: never }
  | { referenceAudio: File; referenceVoiceId?: never }
);

interface VideoJobBaseInput {
  idempotencyKey: string;
  voiceJobId: string;
}

export type CreateVideoJobInput = VideoJobBaseInput & (
  | { portraitId: string; portraitImage?: never }
  | { portraitImage: File; portraitId?: never }
);

export function isActiveDigitalHumanJob(
  job: DigitalHumanJob | null | undefined,
): job is DigitalHumanJob {
  return job?.status === 'queued' || job?.status === 'running';
}

export function isSucceededDigitalHumanJob(
  job: DigitalHumanJob | null | undefined,
): job is DigitalHumanJob {
  return job?.status === 'succeeded';
}

export function isFailedDigitalHumanJob(
  job: DigitalHumanJob | null | undefined,
): job is DigitalHumanJob {
  return job?.status === 'failed';
}

export const DIGITAL_HUMAN_STAGE_LABELS: Record<
  DigitalHumanJobStage,
  string
> = {
  awaiting_voice_confirmation: '等待确认声音',
  completed: '已完成',
  failed: '生成失败',
  queued: '等待执行',
  video_rendering: '正在生成数字人视频',
  video_submitted: '视频任务已提交',
  voice_synthesizing: '正在生成克隆声音',
};
