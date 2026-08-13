import {
  act,
  render,
  renderHook,
  screen,
  waitFor,
} from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  FancyTextElement,
  PipVideoElement,
} from '@/services/ai-video/creation-timeline/types';
import PreviewElement, { getPipPlaybackTime } from './PreviewElement';
import { usePreviewElementDrag } from './usePreviewElementDrag';

const fancyText: FancyTextElement = {
  elementId: 'text-1',
  elementType: 'fancy_text',
  startMs: 0,
  endMs: 3_000,
  zIndex: 2,
  enabled: true,
  locked: false,
  label: '促销',
  text: '促销',
  templateCode: 'keyword_pop',
  fontCode: 'noto_sans_cjk_sc_regular',
  fontVersion: '1',
  fontSha256: 'sha',
  color: '#ffffff',
  accentColor: '#000000',
  transform: {
    xRatio: 0.1,
    yRatio: 0.1,
    widthRatio: 0.3,
    heightRatio: 0.2,
    rotationDeg: 0,
    opacity: 1,
  },
  animationIntensity: 'normal',
  enterDurationMs: 120,
  exitDurationMs: 120,
  suggestionTaskId: null,
  suggestionReason: null,
};

const pip: PipVideoElement = {
  elementId: 'pip-1',
  elementType: 'pip_video',
  startMs: 0,
  endMs: 3_000,
  zIndex: 2,
  enabled: true,
  locked: false,
  label: '画中画',
  assetId: '10002' as never,
  transform: {
    xRatio: 0.1,
    yRatio: 0.1,
    widthRatio: 0.32,
    heightRatio: 0.32,
    rotationDeg: 0,
    opacity: 1,
  },
  fitMode: 'contain',
  crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
  fade: { fadeInMs: 0, fadeOutMs: 0 },
  sourceDurationMs: 2_000,
  sourceStartMs: 0,
  loopWhenOverflow: true,
  audioEnabled: false,
};

function pointerEvent(pointerId: number, clientX: number, clientY: number) {
  return {
    pointerId,
    clientX,
    clientY,
    currentTarget: {
      setPointerCapture: vi.fn(),
      releasePointerCapture: vi.fn(),
      getBoundingClientRect: () => ({
        left: 0,
        top: 0,
        width: 1_000,
        height: 1_000,
      }),
    },
  } as unknown as React.PointerEvent<HTMLElement>;
}

describe('usePreviewElementDrag', () => {
  it('writes normalized free-drag coordinates, exposes alignment guides, and commits once on release', () => {
    const onPreview = vi.fn();
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      usePreviewElementDrag({
        element: fancyText,
        safeMarginRatio: 0.05,
        onPreview,
        onCommit,
      }),
    );
    const down = pointerEvent(4, 100, 100);

    act(() => result.current.handlers.move.onPointerDown(down));
    expect(down.currentTarget.setPointerCapture).toHaveBeenCalledWith(4);
    act(() =>
      result.current.handlers.move.onPointerMove(pointerEvent(4, 350, 400)),
    );
    expect(result.current.previewTransform).toMatchObject({
      xRatio: 0.35,
      yRatio: 0.4,
    });
    expect(result.current.guides).toMatchObject({
      centerX: true,
      centerY: true,
    });
    expect(onCommit).not.toHaveBeenCalled();

    const up = pointerEvent(4, 350, 400);
    act(() => result.current.handlers.move.onPointerUp(up));
    expect(up.currentTarget.releasePointerCapture).toHaveBeenCalledWith(4);
    expect(onCommit).toHaveBeenCalledOnce();
    expect(onCommit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'elementPatched',
        elementId: 'text-1',
        patch: {
          transform: expect.objectContaining({ xRatio: 0.35, yRatio: 0.4 }),
        },
      }),
    );
  });

  it('resizes inside normalized canvas boundaries without changing its fixed rotation or opacity', () => {
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      usePreviewElementDrag({
        element: fancyText,
        safeMarginRatio: 0.05,
        onCommit,
      }),
    );

    act(() =>
      result.current.handlers.resize.onPointerDown(pointerEvent(5, 0, 0)),
    );
    act(() =>
      result.current.handlers.resize.onPointerMove(pointerEvent(5, 900, 900)),
    );
    expect(result.current.previewTransform).toMatchObject({
      widthRatio: 0.9,
      heightRatio: 0.9,
      rotationDeg: 0,
      opacity: 1,
    });
  });

  it('allows PiP shortcuts only at the four corners and retains C0 audio and loop fields', () => {
    const onCommit = vi.fn();
    const { result } = renderHook(() =>
      usePreviewElementDrag({ element: pip, safeMarginRatio: 0.05, onCommit }),
    );

    act(() => result.current.placePip('bottom-right', 0.05));
    expect(onCommit).toHaveBeenCalledWith(
      expect.objectContaining({
        patch: {
          transform: expect.objectContaining({ xRatio: 0.63, yRatio: 0.63 }),
        },
      }),
    );
    expect(pip).toMatchObject({ loopWhenOverflow: true, audioEnabled: false });
  });

  it('uses the C0 PiP loop formula, keeps PiP muted, and never exposes an internal asset id', async () => {
    const offsetPip = {
      ...pip,
      startMs: 1_000,
      endMs: 4_000,
      sourceStartMs: 500,
      sourceDurationMs: 2_000,
    };

    expect(getPipPlaybackTime(offsetPip, 2_800)).toBe(800);
    expect(
      getPipPlaybackTime({ ...offsetPip, sourceStartMs: 2_000 }, 2_800),
    ).toBeUndefined();

    const { container } = render(
      <PreviewElement
        element={offsetPip}
        mediaUrl="https://media.example/pip.mp4"
        positionMs={2_800}
        safeMarginRatio={0.05}
      />,
    );

    const video = screen.getByLabelText('画中画预览') as HTMLVideoElement;
    expect(video.muted).toBe(true);
    await waitFor(() => expect(video.currentTime).toBe(0.8));
    expect(container.innerHTML).not.toContain('10002');
  });
});
