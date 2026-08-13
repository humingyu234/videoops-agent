import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { VisualEffectElement } from '@/services/ai-video/creation-timeline/types';
import VisualEffectInspector, {
  normalizeVisualEffect,
  validateVisualEffect,
} from './VisualEffectInspector';

const effect: VisualEffectElement = {
  elementId: 'effect_1',
  elementType: 'visual_effect',
  startMs: 0,
  endMs: 1_000,
  zIndex: 1,
  enabled: true,
  locked: false,
  label: '淡入',
  effectCode: 'fade_in',
  durationMs: 1_000,
  scale: null,
  radius: null,
};

describe('VisualEffectInspector', () => {
  it('normalizes the only five C0 effect modes without arbitrary filter fields', () => {
    expect(
      normalizeVisualEffect({
        ...effect,
        effectCode: 'gentle_zoom_in',
        scale: null,
        radius: 9,
      }),
    ).toMatchObject({ effectCode: 'gentle_zoom_in', scale: 1.1, radius: null });
    expect(
      normalizeVisualEffect({
        ...effect,
        effectCode: 'light_blur',
        scale: 1.2,
        radius: null,
      }),
    ).toMatchObject({ effectCode: 'light_blur', scale: null, radius: 4 });
    expect(() =>
      normalizeVisualEffect({ ...effect, effectCode: 'css_filter' as never }),
    ).toThrow('Unknown visual effect');

    const hostile = normalizeVisualEffect({
      ...effect,
      css: 'filter: blur(8px)',
      filter: 'blur(8px)',
      ffmpegArgs: ['-vf', 'blur'],
    } as VisualEffectElement);
    expect(Object.hasOwn(hostile, 'css')).toBe(false);
    expect(Object.hasOwn(hostile, 'filter')).toBe(false);
    expect(Object.hasOwn(hostile, 'ffmpegArgs')).toBe(false);
  });

  it('validates C0 duration and mode-specific intensity values', () => {
    expect(validateVisualEffect({ ...effect, durationMs: 99 })).toBe(
      '特效时长必须在 100 到 3000 毫秒之间',
    );
    expect(
      validateVisualEffect({
        ...effect,
        effectCode: 'gentle_zoom_out',
        scale: 1.3,
      }),
    ).toBe('缩放强度必须在 1 到 1.2 之间');
  });

  it('offers only C0 effect choices and emits normalized values', () => {
    const onChange = vi.fn();
    render(<VisualEffectInspector element={effect} onChange={onChange} />);

    fireEvent.click(screen.getByText('轻微放大'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        effectCode: 'gentle_zoom_in',
        scale: 1.1,
        radius: null,
      }),
    );
    expect(screen.queryByLabelText(/CSS|滤镜|FFmpeg/)).not.toBeInTheDocument();
  });
});
