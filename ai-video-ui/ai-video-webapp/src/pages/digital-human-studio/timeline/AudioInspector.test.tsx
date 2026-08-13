import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AudioElement } from '@/services/ai-video/creation-timeline/types';
import AudioInspector, {
  audioTrackForUsage,
  canAddPrimaryAudio,
  normalizeAudioElement,
  validateAudioElement,
} from './AudioInspector';

const audio = (usageType: AudioElement['usageType']): AudioElement => ({
  elementId: `audio_${usageType}`,
  elementType: 'audio',
  startMs: 0,
  endMs: 6_000,
  zIndex: 1,
  enabled: true,
  locked: false,
  label: '音频',
  assetId: '90071992547409931' as AudioElement['assetId'],
  usageType,
  sourceDurationMs: 10_000,
  sourceStartMs: 0,
  sourceEndMs: 6_000,
  volumeRatio: usageType === 'sound_effect' ? 1 : 0.8,
  fade: { fadeInMs: 0, fadeOutMs: 0 },
  loopWhenOverflow: false,
  duckingEnabled: false,
});

describe('AudioInspector', () => {
  it('normalizes background music to the C0 ducking contract and removes duck fields elsewhere', () => {
    const background = normalizeAudioElement({
      ...audio('background_music'),
      volumeRatio: 0.9,
      loopWhenOverflow: false,
      duckingEnabled: false,
    });
    expect(background).toMatchObject({
      volumeRatio: 0.3,
      loopWhenOverflow: true,
      duckingEnabled: true,
      targetGainRatio: 0.35,
      attackMs: 120,
      releaseMs: 400,
    });

    const soundEffect = normalizeAudioElement({
      ...audio('sound_effect'),
      targetGainRatio: 0.35,
      attackMs: 120,
      releaseMs: 400,
    });
    expect(soundEffect).toMatchObject({
      volumeRatio: 1,
      loopWhenOverflow: false,
      duckingEnabled: false,
    });
    expect(Object.hasOwn(soundEffect, 'targetGainRatio')).toBe(false);
    expect(Object.hasOwn(soundEffect, 'attackMs')).toBe(false);
    expect(Object.hasOwn(soundEffect, 'releaseMs')).toBe(false);

    const hostile = normalizeAudioElement({
      ...audio('sound_effect'),
      css: 'filter: blur(8px)',
    } as AudioElement);
    expect(Object.hasOwn(hostile, 'css')).toBe(false);
  });

  it('rejects invalid source intervals and overlong fades, and keeps only one primary track', () => {
    expect(
      validateAudioElement({
        ...audio('sound_effect'),
        sourceStartMs: 5_000,
        sourceEndMs: 5_000,
      }),
    ).toBe('素材裁剪范围无效');
    expect(
      validateAudioElement({
        ...audio('sound_effect'),
        fade: { fadeInMs: 3_500, fadeOutMs: 3_000 },
      }),
    ).toBe('淡入和淡出总和不能超过元素时长');
    expect(
      validateAudioElement({
        ...audio('sound_effect'),
        endMs: 120_000,
        fade: { fadeInMs: 60_000, fadeOutMs: 60_000 },
      }),
    ).toBeUndefined();
    expect(canAddPrimaryAudio([audio('primary_audio')])).toBe(false);
    expect(canAddPrimaryAudio([audio('sound_effect')])).toBe(true);
    expect(audioTrackForUsage('background_music')).toBe('background_music');
  });

  it('does not expose controls that can change fixed background music fields', () => {
    const onChange = vi.fn();
    render(
      <AudioInspector
        element={audio('background_music')}
        onChange={onChange}
      />,
    );

    expect(screen.getByText('背景音乐将自动闪避主配音。')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '淡入时长' }), {
      target: { value: '100' },
    });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        volumeRatio: 0.3,
        loopWhenOverflow: true,
        duckingEnabled: true,
      }),
    );
  });
});
