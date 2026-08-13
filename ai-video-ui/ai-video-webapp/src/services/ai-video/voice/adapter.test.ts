import { describe, expect, it } from 'vitest';
import { toVoiceItem } from './adapter';
import type { Voice } from './types';

describe('voice adapter', () => {
  it('maps persisted Whisper timestamps to exact seconds', () => {
    const voice: Voice = {
      voiceId: '9',
      assetId: '10',
      voiceType: 'origin',
      name: '韩老师',
      gender: 'female',
      tags: [],
      transcriptText: '微信公众号',
      transcriptTimeline: [
        { text: '微信', startMillis: 120, endMillis: 480 },
        { text: '公众号', startMillis: 500, endMillis: 920 },
      ],
      durationMillis: 1000,
      transcriptionStatus: 'ready',
      attemptCount: 1,
      recordRevision: '3',
      createTime: '2026-08-03T00:00:00',
      updateTime: '2026-08-03T00:00:00',
    };

    const result = toVoiceItem(voice);

    expect(result.timelineExact).toBe(true);
    expect(result.timeline).toEqual([
      { word: '微信', start: 0.12, dur: 0.36, isPunct: false },
      { word: '公众号', start: 0.5, dur: 0.42, isPunct: false },
    ]);
  });

  it('does not pretend a ready API voice has exact timing when cues are absent', () => {
    const voice = {
      voiceId: '9', assetId: '10', voiceType: 'origin', name: '旧录音', gender: 'female', tags: [],
      transcriptText: '旧文案', durationMillis: 1000, transcriptionStatus: 'ready', attemptCount: 1,
      recordRevision: '3', createTime: '2026-08-03T00:00:00', updateTime: '2026-08-03T00:00:00',
    } satisfies Voice;

    expect(toVoiceItem(voice)).toMatchObject({ timelineExact: false, timeline: [] });
  });
});
