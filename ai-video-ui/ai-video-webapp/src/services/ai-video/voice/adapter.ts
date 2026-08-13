import type { VoiceItem } from '@/pages/digital-human-studio/model';
import { getExactVoiceWords, splitVoiceSentences } from '@/pages/digital-human-studio/voices/voiceTimeline';
import type { Voice } from './types';

const formatDuration = (seconds: number) =>
  `${Math.floor(seconds / 60)}:${Math.floor(seconds % 60).toString().padStart(2, '0')}`;

export function toVoiceItem(voice: Voice): VoiceItem {
  const seconds = Math.max(0, (voice.durationMillis ?? 0) / 1000);
  const script = voice.transcriptText?.trim() ||
    (voice.transcriptionStatus === 'failed'
      ? '声音文本解析失败，请重试。'
      : voice.transcriptionStatus === 'unparsed' ? '未解析；需要解析请前往声音功能模块。' : '正在解析声音文本…');
  const meta = [voice.gender === 'female' ? '女声' : voice.gender === 'male' ? '男声' : '未指定', voice.style, ...voice.tags]
    .filter(Boolean).join(' · ');
  const timeline = getExactVoiceWords(voice.transcriptTimeline ?? []);
  return {
    id: voice.voiceId,
    assetId: voice.assetId,
    recordRevision: voice.recordRevision,
    transcriptionStatus: voice.transcriptionStatus,
    timeline,
    timelineExact: timeline.length > 0,
    name: voice.name,
    meta,
    dur: formatDuration(seconds),
    script,
    secs: seconds,
    sents: splitVoiceSentences(script),
    owner: 'custom',
    status: voice.transcriptionStatus === 'ready' ? 'verified' : voice.transcriptionStatus === 'failed' ? 'failed' : 'pending',
    type: voice.voiceType,
  };
}
