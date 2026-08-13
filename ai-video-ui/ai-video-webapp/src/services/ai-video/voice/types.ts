export type VoiceGender = 'female' | 'male' | 'unspecified';
export type VoiceType = 'origin' | 'clone' | 'public';
export type VoiceTranscriptionStatus = 'unparsed' | 'pending' | 'transcribing' | 'ready' | 'failed';

export interface VoiceTranscriptCue {
  text: string;
  startMillis: number;
  endMillis: number;
}

export interface Voice {
  voiceId: string;
  assetId: string;
  voiceType: VoiceType;
  name: string;
  gender: VoiceGender;
  style?: string;
  tags: string[];
  note?: string;
  transcriptText?: string;
  transcriptTimeline?: VoiceTranscriptCue[];
  detectedLanguage?: string;
  durationMillis?: number;
  transcriptionStatus: VoiceTranscriptionStatus;
  failureCode?: string;
  failureMessage?: string;
  attemptCount: number;
  recordRevision: string;
  createTime: string;
  updateTime: string;
}

export interface VoicePage { rows: Voice[]; total: number }

export interface VoiceMetadataInput {
  idempotencyKey: string;
  name: string;
  gender?: VoiceGender;
  transcriptionRequested?: boolean;
  style?: string;
  tags?: string[];
  note?: string;
}

export interface VoiceAccessUrl {
  url: string;
  expiresAt: string;
  contentType: string;
  fileName: string;
}
