import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { TimelineDocument } from '@/services/ai-video/creation-timeline/types';
import { useTimelineAutosave } from './useTimelineAutosave';

const timeline: TimelineDocument = {
  schemaVersion: 'timeline-1',
  canvas: {
    width: 1080,
    height: 1920,
    frameRate: 30,
    durationMs: 1000,
    safeMarginRatio: 0.05,
  },
  tracks: [],
};

function timelineWithDuration(durationMs: number): TimelineDocument {
  return {
    ...timeline,
    canvas: { ...timeline.canvas, durationMs },
  };
}

function saveResult(
  savedTimeline: TimelineDocument,
  revision: string,
  overrides: Record<string, unknown> = {},
) {
  return {
    contentHash: `hash-${revision}`,
    normalizationChanges: [],
    projectId: '1',
    replayed: false,
    revision,
    savedAt: 'now',
    superseded: false,
    timeline: savedTimeline,
    timelineDraftId: '2',
    ...overrides,
  };
}

function serverDraft(savedTimeline: TimelineDocument, revision: string) {
  return {
    contentHash: `server-${revision}`,
    projectId: '1',
    revision,
    savedAt: 'now',
    timeline: savedTimeline,
    timelineDraftId: '2',
  };
}

function deferred<T>() {
  let reject!: (reason?: unknown) => void;
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}

describe('useTimelineAutosave', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('serializes saves, queues only the latest edit, and chains the returned revision', async () => {
    const first = deferred<ReturnType<typeof saveResult>>();
    const second = deferred<ReturnType<typeof saveResult>>();
    const saveDraft = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    const firstEdit = timelineWithDuration(1100);
    const latestEdit = timelineWithDuration(1200);
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );

    rerender({ current: firstEdit });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(1));
    const firstRequest = saveDraft.mock.calls[0][1];
    expect(firstRequest).toMatchObject({
      expectedRevision: '3',
      schemaVersion: 'timeline-1',
      timeline: firstEdit,
    });

    rerender({ current: latestEdit });
    expect(saveDraft).toHaveBeenCalledTimes(1);
    expect(result.current.saveStatus.kind).not.toBe('saved');

    await act(async () => {
      first.resolve(saveResult(firstEdit, '4'));
    });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(2));
    const secondRequest = saveDraft.mock.calls[1][1];
    expect(secondRequest).toMatchObject({
      expectedRevision: '4',
      schemaVersion: 'timeline-1',
      timeline: latestEdit,
    });
    expect(secondRequest.idempotencyKey).not.toBe(firstRequest.idempotencyKey);
    expect(result.current.saveStatus.kind).not.toBe('saved');

    await act(async () => {
      second.resolve(saveResult(latestEdit, '5'));
    });
    await waitFor(() =>
      expect(result.current.saveStatus).toEqual({
        kind: 'saved',
        revision: '5',
        contentHash: 'hash-5',
      }),
    );
  });

  it('reuses the exact payload and idempotency key after an unknown save result', async () => {
    const edited = timelineWithDuration(1100);
    const saveDraft = vi
      .fn()
      .mockRejectedValueOnce(new Error('connection reset'))
      .mockResolvedValueOnce(saveResult(edited, '4'));
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );

    rerender({ current: edited });
    await waitFor(() => expect(result.current.saveStatus.kind).toBe('failed'));
    const initialPayload = saveDraft.mock.calls[0][1];

    act(() => result.current.retry());
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(2));
    expect(saveDraft.mock.calls[1][1]).toBe(initialPayload);
    await waitFor(() => expect(result.current.saveStatus.kind).toBe('saved'));
  });

  it('rebaselines from a superseded response without applying its stale timeline and preserves newer local edits', async () => {
    const first = deferred<ReturnType<typeof saveResult>>();
    const latestDraft = deferred<ReturnType<typeof serverDraft>>();
    const second = deferred<ReturnType<typeof saveResult>>();
    const saveDraft = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    const getDraft = vi.fn().mockImplementation(() => latestDraft.promise);
    const firstEdit = timelineWithDuration(1100);
    const latestEdit = timelineWithDuration(1200);
    const serverTimeline = timelineWithDuration(1300);
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { getDraft, saveDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );

    rerender({ current: firstEdit });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(1));
    rerender({ current: latestEdit });

    await act(async () => {
      first.resolve(saveResult(firstEdit, '4', { superseded: true }));
    });
    await waitFor(() => expect(getDraft).toHaveBeenCalledWith('1'));
    expect(result.current.lastSaved).toBeUndefined();

    await act(async () => {
      latestDraft.resolve(serverDraft(serverTimeline, '7'));
    });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(2));
    expect(saveDraft.mock.calls[1][1]).toMatchObject({
      expectedRevision: '7',
      timeline: latestEdit,
    });
    expect(result.current.lastSaved).toBeUndefined();

    await act(async () => {
      second.resolve(saveResult(latestEdit, '8'));
    });
    await waitFor(() =>
      expect(result.current.saveStatus).toMatchObject({
        kind: 'saved',
        revision: '8',
      }),
    );
  });

  it('freezes the failed base revision and local snapshot on C0 46603', async () => {
    const serverTimeline = timelineWithDuration(1200);
    const saveDraft = vi
      .fn()
      .mockRejectedValue(new ApiError({ code: 46603, msg: 'conflict' }));
    const getDraft = vi.fn().mockResolvedValue(serverDraft(serverTimeline, '5'));
    const edited = timelineWithDuration(1100);
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft, getDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );
    rerender({ current: edited });

    await waitFor(() =>
      expect(result.current.saveStatus).toMatchObject({
        kind: 'conflict',
        baseRevision: '3',
        serverRevision: '5',
        snapshot: edited,
      }),
    );
    expect(saveDraft).toHaveBeenCalledTimes(1);
  });

  it('registers beforeunload only for unconfirmed edits and removes it after saving and unmounting', async () => {
    const first = deferred<ReturnType<typeof saveResult>>();
    const second = deferred<ReturnType<typeof saveResult>>();
    const addEventListener = vi.spyOn(window, 'addEventListener');
    const removeEventListener = vi.spyOn(window, 'removeEventListener');
    const saveDraft = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    const firstEdit = timelineWithDuration(1100);
    const secondEdit = timelineWithDuration(1200);
    const { rerender, unmount } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );
    addEventListener.mockClear();
    removeEventListener.mockClear();

    rerender({ current: firstEdit });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(1));
    expect(addEventListener).toHaveBeenCalledWith('beforeunload', expect.any(Function));

    await act(async () => {
      first.resolve(saveResult(firstEdit, '4'));
    });
    await waitFor(() =>
      expect(removeEventListener).toHaveBeenCalledWith('beforeunload', expect.any(Function)),
    );

    rerender({ current: secondEdit });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(2));
    unmount();
    expect(removeEventListener).toHaveBeenCalledWith('beforeunload', expect.any(Function));
  });

  it('removes beforeunload when rebaseline discards a pending local edit', async () => {
    const addEventListener = vi.spyOn(window, 'addEventListener');
    const removeEventListener = vi.spyOn(window, 'removeEventListener');
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft: vi.fn() } as never,
          debounceMs: 10_000,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );
    addEventListener.mockClear();
    removeEventListener.mockClear();

    rerender({ current: timelineWithDuration(1100) });
    await waitFor(() =>
      expect(addEventListener).toHaveBeenCalledWith('beforeunload', expect.any(Function)),
    );

    act(() => result.current.rebaseline(timeline, '4', 'hash-4'));
    await waitFor(() =>
      expect(removeEventListener).toHaveBeenCalledWith('beforeunload', expect.any(Function)),
    );
  });

  it('waits for an in-flight save to settle before saving edits made after rebaseline', async () => {
    const first = deferred<ReturnType<typeof saveResult>>();
    const second = deferred<ReturnType<typeof saveResult>>();
    const saveDraft = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise);
    const firstEdit = timelineWithDuration(1100);
    const secondEdit = timelineWithDuration(1200);
    const { result, rerender } = renderHook(
      ({ current }) =>
        useTimelineAutosave({
          api: { saveDraft } as never,
          debounceMs: 1,
          projectId: '1',
          revision: '3',
          timeline: current,
        }),
      { initialProps: { current: timeline } },
    );

    rerender({ current: firstEdit });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(1));
    act(() => result.current.rebaseline(timeline, '4', 'hash-4'));
    rerender({ current: secondEdit });
    await new Promise((resolve) => window.setTimeout(resolve, 10));
    expect(saveDraft).toHaveBeenCalledTimes(1);

    await act(async () => {
      first.resolve(saveResult(firstEdit, '5'));
    });
    await waitFor(() => expect(saveDraft).toHaveBeenCalledTimes(2));
    expect(saveDraft.mock.calls[1][1]).toMatchObject({
      expectedRevision: '4',
      timeline: secondEdit,
    });

    await act(async () => {
      second.resolve(saveResult(secondEdit, '5'));
    });
  });
});
