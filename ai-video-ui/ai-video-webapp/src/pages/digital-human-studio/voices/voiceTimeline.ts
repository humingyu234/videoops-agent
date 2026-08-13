export interface VoiceWord {
  word: string;
  start: number;
  dur: number;
  isPunct: boolean;
}

export interface VoiceTick {
  seconds: number;
  leftPercent: number;
  major: boolean;
  label?: string;
}

const VOICE_PUNCTUATION = /^[，。！？、；：]$/;

export interface VoiceTranscriptCueLike {
  text: string;
  startMillis: number;
  endMillis: number;
}

export const getExactVoiceWords = (cues: VoiceTranscriptCueLike[]): VoiceWord[] =>
  cues.map((cue) => ({
    word: cue.text,
    start: cue.startMillis / 1000,
    dur: (cue.endMillis - cue.startMillis) / 1000,
    isPunct: VOICE_PUNCTUATION.test(cue.text),
  }));

export const clampVoicePercent = (value: number): number => {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return Math.min(1, Math.max(0, value));
};

export const formatVoiceSeconds = (value: number): string => {
  const totalSeconds = Number.isFinite(value)
    ? Math.max(0, Math.floor(value))
    : 0;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
};

export const getVoiceWords = (
  sentences: string[],
  seconds: number,
): VoiceWord[] => {
  const characters = Array.from(sentences.join(''));
  if (characters.length === 0 || !Number.isFinite(seconds) || seconds <= 0) {
    return [];
  }

  const duration = seconds / characters.length;
  let start = 0;
  return characters.map((word) => {
    const item: VoiceWord = {
      word,
      start,
      dur: duration,
      isPunct: VOICE_PUNCTUATION.test(word),
    };
    start += duration;
    return item;
  });
};

export const splitVoiceSentences = (value: string): string[] => {
  const compactValue = value.replace(/\s+/g, '');
  return compactValue.match(/[^，。！？、；：\n]+[，。！？、；：]?/g) ?? [];
};

export const buildVoiceTicks = (seconds: number): VoiceTick[] => {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return [];
  }

  const step = seconds <= 40 ? 5 : 10;
  const majorEvery = step * 2;
  const ticks: VoiceTick[] = [];

  for (let tickSeconds = 0; tickSeconds <= seconds; tickSeconds += step) {
    const major = tickSeconds % majorEvery === 0;
    ticks.push({
      seconds: tickSeconds,
      leftPercent: (tickSeconds / seconds) * 100,
      major,
      ...(major ? { label: formatVoiceSeconds(tickSeconds) } : {}),
    });
  }

  return ticks;
};
