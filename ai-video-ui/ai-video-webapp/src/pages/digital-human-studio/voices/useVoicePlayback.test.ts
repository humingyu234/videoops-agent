import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { voiceApi } from '@/services/ai-video/voice/api';
import type { VoiceItem } from '../model';
import { VOICES } from '../model';
import { useVoicePlayback } from './useVoicePlayback';

const getVoice = (id: string): VoiceItem => {
  const voice = VOICES.find((item) => item.id === id);
  if (!voice) {
    throw new Error(`Missing test voice: ${id}`);
  }
  return voice;
};

class FakeAudio {
  currentTime = 0;
  duration = 10;
  src: string;
  pause = vi.fn();
  load = vi.fn();
  play = vi.fn().mockResolvedValue(undefined);
  removeAttribute = vi.fn((name: string) => {
    if (name === 'src') this.src = '';
  });
  private listeners = new Map<string, Array<() => void>>();

  constructor(src: string) {
    this.src = src;
  }

  addEventListener(type: string, listener: () => void) {
    const current = this.listeners.get(type) ?? [];
    current.push(listener);
    this.listeners.set(type, current);
  }

  emit(type: string) {
    this.listeners.get(type)?.forEach((listener) => {
      listener();
    });
  }
}

describe('useVoicePlayback', () => {
  let now: number;
  let nextFrameId: number;
  let frameCallbacks: Map<number, FrameRequestCallback>;
  let requestAnimationFrameMock: ReturnType<typeof vi.fn>;
  let cancelAnimationFrameMock: ReturnType<typeof vi.fn>;
  let createdAudios: FakeAudio[];

  const runNextFrame = () => {
    const frame = frameCallbacks.entries().next().value as
      | [number, FrameRequestCallback]
      | undefined;
    if (!frame) {
      throw new Error('No animation frame is scheduled');
    }

    const [frameId, callback] = frame;
    frameCallbacks.delete(frameId);
    act(() => callback(now));
  };

  beforeEach(() => {
    now = 1_000;
    nextFrameId = 1;
    frameCallbacks = new Map();
    vi.spyOn(performance, 'now').mockImplementation(() => now);
    requestAnimationFrameMock = vi.fn((callback: FrameRequestCallback) => {
      const frameId = nextFrameId;
      nextFrameId += 1;
      frameCallbacks.set(frameId, callback);
      return frameId;
    });
    cancelAnimationFrameMock = vi.fn((frameId: number) => {
      frameCallbacks.delete(frameId);
    });
    vi.stubGlobal('requestAnimationFrame', requestAnimationFrameMock);
    vi.stubGlobal('cancelAnimationFrame', cancelAnimationFrameMock);
    createdAudios = [];
    vi.stubGlobal('Audio', vi.fn(function createAudio(src: string) {
      const audio = new FakeAudio(src);
      createdAudios.push(audio);
      return audio;
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('starts at a specified position, advances by elapsed time, and stop preserves progress', () => {
    const voice = getVoice('vs-003');
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(voice, 0.5));
    expect(result.current.playingVoiceId).toBe('vs-003');
    expect(result.current.progressByVoice['vs-003']).toBe(0.5);

    now += 6_200;
    runNextFrame();
    expect(result.current.progressByVoice['vs-003']).toBeCloseTo(0.6);

    act(() => result.current.stop());
    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['vs-003']).toBeCloseTo(0.6);
  });

  it('cancels the previous frame when another voice starts and preserves its position', () => {
    const firstVoice = getVoice('vs-003');
    const secondVoice = getVoice('vs-004');
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(firstVoice, 0.25));
    act(() => result.current.play(secondVoice, 0));

    expect(cancelAnimationFrameMock).toHaveBeenCalledWith(1);
    expect(result.current.playingVoiceId).toBe('vs-004');
    expect(result.current.progressByVoice['vs-003']).toBe(0.25);
    expect(result.current.progressByVoice['vs-004']).toBe(0);
  });

  it('toggles the current voice off without losing progress and starts a different voice from zero', () => {
    const firstVoice = getVoice('vs-003');
    const secondVoice = getVoice('vs-004');
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(firstVoice, 0.4));
    act(() => result.current.toggle(firstVoice));

    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['vs-003']).toBe(0.4);

    act(() => result.current.toggle(secondVoice));
    expect(result.current.playingVoiceId).toBe('vs-004');
    expect(result.current.progressByVoice['vs-004']).toBe(0);
    expect(result.current.progressByVoice['vs-003']).toBe(0.4);
  });

  it('resets progress and stops scheduling frames when playback reaches the end', () => {
    const voice = getVoice('vs-004');
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(voice, 0.9));
    now += 3_200;
    runNextFrame();

    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['vs-004']).toBe(0);
    expect(frameCallbacks).toHaveLength(0);
    expect(requestAnimationFrameMock).toHaveBeenCalledTimes(1);
  });

  it('does not play voices with a non-positive duration', () => {
    const zeroDurationVoice: VoiceItem = {
      ...getVoice('vs-003'),
      id: 'zero-duration',
      secs: 0,
    };
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(getVoice('vs-003'), 0.2));
    act(() => result.current.play(zeroDurationVoice));

    expect(cancelAnimationFrameMock).toHaveBeenCalledWith(1);
    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['zero-duration']).toBeUndefined();
    expect(requestAnimationFrameMock).toHaveBeenCalledTimes(1);
  });

  it.each([
    [Number.NaN, 0],
    [-0.5, 0],
    [1.5, 1],
  ])('clamps start percent %s to %s', (startPercent, expected) => {
    const voice = getVoice('vs-003');
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(voice, startPercent));

    expect(result.current.progressByVoice['vs-003']).toBe(expected);
  });

  it('cancels on unmount and ignores a captured stale callback', () => {
    const voice = getVoice('vs-003');
    const { result, unmount } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(voice, 0.25));
    const staleCallback = frameCallbacks.get(1);
    const stateBeforeUnmount = result.current;

    unmount();
    expect(cancelAnimationFrameMock).toHaveBeenCalledWith(1);

    now += 6_200;
    act(() => staleCallback?.(now));

    expect(requestAnimationFrameMock).toHaveBeenCalledTimes(1);
    expect(result.current).toBe(stateBeforeUnmount);
    expect(result.current.progressByVoice['vs-003']).toBe(0.25);
  });

  it('reuses one audio instance when seeking the same server voice', async () => {
    vi.spyOn(voiceApi, 'accessUrl').mockResolvedValue({
      url: 'http://audio.test/one.wav', expiresAt: '', contentType: 'audio/wav', fileName: 'one.wav',
    });
    const voice = { ...getVoice('vs-003'), id: 'server-1', recordRevision: '1', secs: 10 };
    const { result } = renderHook(() => useVoicePlayback());

    await act(async () => { result.current.play(voice, 0.1); await Promise.resolve(); });
    createdAudios[0]?.emit('loadedmetadata');
    await act(async () => { result.current.play(voice, 0.6); await Promise.resolve(); });

    expect(voiceApi.accessUrl).toHaveBeenCalledTimes(1);
    expect(createdAudios).toHaveLength(1);
    expect(createdAudios[0]?.currentTime).toBeCloseTo(6);
  });

  it('disposes the current audio before switching server voices', async () => {
    vi.spyOn(voiceApi, 'accessUrl')
      .mockResolvedValueOnce({ url: 'http://audio.test/one.wav', expiresAt: '', contentType: 'audio/wav', fileName: 'one.wav' })
      .mockResolvedValueOnce({ url: 'http://audio.test/two.wav', expiresAt: '', contentType: 'audio/wav', fileName: 'two.wav' });
    const first = { ...getVoice('vs-003'), id: 'server-1', recordRevision: '1', secs: 10 };
    const second = { ...getVoice('vs-004'), id: 'server-2', recordRevision: '1', secs: 10 };
    const { result } = renderHook(() => useVoicePlayback());

    await act(async () => { result.current.play(first); await Promise.resolve(); });
    await act(async () => { result.current.play(second); await Promise.resolve(); });

    expect(createdAudios).toHaveLength(2);
    expect(createdAudios[0]?.pause).toHaveBeenCalledTimes(1);
    expect(createdAudios[0]?.removeAttribute).toHaveBeenCalledWith('src');
    expect(createdAudios[0]?.load).toHaveBeenCalledTimes(1);
  });

  it('deduplicates access-url while loading and applies the latest seek', async () => {
    let resolveAccess: ((value: Awaited<ReturnType<typeof voiceApi.accessUrl>>) => void) | undefined;
    vi.spyOn(voiceApi, 'accessUrl').mockReturnValue(new Promise((resolve) => { resolveAccess = resolve; }));
    const voice = { ...getVoice('vs-003'), id: 'server-1', recordRevision: '1', secs: 10 };
    const { result } = renderHook(() => useVoicePlayback());

    act(() => result.current.play(voice, 0.1));
    act(() => result.current.play(voice, 0.7));
    expect(voiceApi.accessUrl).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveAccess?.({ url: 'http://audio.test/one.wav', expiresAt: '', contentType: 'audio/wav', fileName: 'one.wav' });
      await Promise.resolve();
    });
    createdAudios[0]?.emit('loadedmetadata');

    expect(createdAudios).toHaveLength(1);
    expect(createdAudios[0]?.currentTime).toBeCloseTo(7);
  });
});
