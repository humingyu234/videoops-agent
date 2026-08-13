import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { usePreviewClock } from './usePreviewClock';

class FakeVideo {
  currentTime = 0;
  paused = true;
  ended = false;
  play = vi.fn(async () => {
    this.paused = false;
  });
  pause = vi.fn(() => {
    this.paused = true;
  });
  private listeners = new Map<string, Set<() => void>>();

  addEventListener(type: string, listener: () => void) {
    const listeners = this.listeners.get(type) ?? new Set();
    listeners.add(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type: string, listener: () => void) {
    this.listeners.get(type)?.delete(listener);
  }

  emit(type: string) {
    this.listeners.get(type)?.forEach((listener) => {
      listener();
    });
  }
}

describe('usePreviewClock', () => {
  let video: FakeVideo;
  let frames: Map<number, FrameRequestCallback>;
  let requestFrame: ReturnType<typeof vi.fn>;
  let cancelFrame: ReturnType<typeof vi.fn>;
  let nextFrameId: number;

  const runFrame = () => {
    const item = frames.entries().next().value as
      | [number, FrameRequestCallback]
      | undefined;
    if (!item) throw new Error('Expected a scheduled animation frame');
    const [id, callback] = item;
    frames.delete(id);
    act(() => callback(0));
  };

  beforeEach(() => {
    video = new FakeVideo();
    frames = new Map();
    nextFrameId = 1;
    requestFrame = vi.fn((callback: FrameRequestCallback) => {
      const id = nextFrameId;
      nextFrameId += 1;
      frames.set(id, callback);
      return id;
    });
    cancelFrame = vi.fn((id: number) => {
      frames.delete(id);
    });
    vi.stubGlobal('requestAnimationFrame', requestFrame);
    vi.stubGlobal('cancelAnimationFrame', cancelFrame);
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('uses the main video time for play, seek, and animation-frame updates', async () => {
    const videoRef = { current: video as unknown as HTMLVideoElement };
    const { result } = renderHook(() =>
      usePreviewClock({ durationMs: 10_000, videoRef }),
    );

    act(() => result.current.seek(2_500));
    expect(video.currentTime).toBe(2.5);
    expect(result.current.positionMs).toBe(2_500);

    await act(async () => {
      await result.current.play();
    });
    expect(video.play).toHaveBeenCalledOnce();
    expect(result.current.playing).toBe(true);
    expect(requestFrame).toHaveBeenCalledOnce();

    video.currentTime = 4.25;
    runFrame();
    expect(result.current.positionMs).toBe(4_250);

    act(() => result.current.pause());
    expect(video.pause).toHaveBeenCalledOnce();
    expect(result.current.playing).toBe(false);
    expect(cancelFrame).toHaveBeenCalled();
  });

  it('synchronizes native seeking and finishes cleanly when main video ends', async () => {
    const videoRef = { current: video as unknown as HTMLVideoElement };
    const { result } = renderHook(() =>
      usePreviewClock({ durationMs: 10_000, videoRef }),
    );

    video.currentTime = 6;
    act(() => video.emit('seeking'));
    expect(result.current.positionMs).toBe(6_000);

    await act(async () => {
      await result.current.play();
    });
    video.currentTime = 10;
    video.ended = true;
    act(() => video.emit('ended'));
    expect(result.current.positionMs).toBe(10_000);
    expect(result.current.playing).toBe(false);
    expect(cancelFrame).toHaveBeenCalled();
  });

  it('stops animation frames while hidden, resumes a live video when visible, and cleans up on unmount', async () => {
    const videoRef = { current: video as unknown as HTMLVideoElement };
    const { result, unmount } = renderHook(() =>
      usePreviewClock({ durationMs: 10_000, videoRef }),
    );

    await act(async () => {
      await result.current.play();
    });
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: true,
    });
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    expect(cancelFrame).toHaveBeenCalled();

    Object.defineProperty(document, 'hidden', {
      configurable: true,
      value: false,
    });
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    expect(requestFrame).toHaveBeenCalledTimes(2);

    unmount();
    expect(cancelFrame).toHaveBeenCalled();
  });
});
