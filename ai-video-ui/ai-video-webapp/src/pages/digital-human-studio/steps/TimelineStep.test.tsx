import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { SaveTimelineDraftResult } from '@/services/ai-video/creation-timeline/api';
import type { TimelineDocument } from '@/services/ai-video/creation-timeline/types';
import { initialStudioState } from '../model';
import TimelineStep from './TimelineStep';

describe('TimelineStep', () => {
  it('owns no legacy page-level selection state and renders the dedicated editor', () => {
    render(
      <TimelineStep
        state={initialStudioState}
        update={vi.fn()}
        onFinish={vi.fn()}
        onNext={vi.fn()}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(initialStudioState).not.toHaveProperty('timelineSelected');
    expect(
      screen.getByRole('region', { name: '画面预览' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /去预览作品/ }),
    ).toBeInTheDocument();
  });

  it('loads the server draft for the current creation project instead of rebuilding a local default', async () => {
    const getDraft = vi.fn().mockResolvedValue({
      projectId: '90071992547409931',
      timelineDraftId: '90071992547409932',
      revision: 'revision-2',
      schemaVersion: 'timeline-1',
      contentHash: 'content-hash',
      savedAt: '2026-08-08T08:31:00+08:00',
      timeline: {
        schemaVersion: 'timeline-1',
        canvas: {
          width: 1080,
          height: 1920,
          frameRate: 30,
          durationMs: 30_000,
          safeMarginRatio: 0.05,
        },
        tracks: [
          {
            trackId: 'subtitle-track',
            trackType: 'subtitle',
            area: 'top',
            order: 0,
            locked: false,
            muted: false,
            elements: [],
          },
          {
            trackId: 'main-track',
            trackType: 'main_video',
            area: 'center',
            order: 0,
            locked: true,
            muted: false,
            elements: [
              {
                elementId: 'server-main',
                elementType: 'main_video',
                startMs: 0,
                endMs: 30_000,
                zIndex: 0,
                enabled: true,
                locked: true,
                label: 'server-main',
                assetId: '90071992547410003',
                sourceDurationMs: 30_000,
                sourceStartMs: 0,
                fitMode: 'cover',
              },
            ],
          },
        ],
      },
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={client}>
        <TimelineStep
          projectId="90071992547409931"
          sourceText="服务器脚本"
          state={initialStudioState}
          timelineApi={{ getDraft, saveDraft: vi.fn() }}
          update={vi.fn()}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      </QueryClientProvider>,
    );

    expect(
      await screen.findByRole('button', { name: '选择时间轴片段 server-main' }),
    ).toBeInTheDocument();
    expect(getDraft).toHaveBeenCalledWith('90071992547409931');

    fireEvent.click(screen.getByRole('button', { name: '添加字幕' }));
    expect(
      await screen.findByRole('button', { name: '选择时间轴片段 新字幕' }),
    ).toBeInTheDocument();
  });

  it('routes a real timeline clip reducer edit into draft autosave', async () => {
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    const serverTimeline: TimelineDocument = {
      schemaVersion: 'timeline-1',
      canvas: {
        width: 1080,
        height: 1920,
        frameRate: 30,
        durationMs: 30_000,
        safeMarginRatio: 0.05,
      },
      tracks: [
        {
          trackId: 'image-track',
          trackType: 'image_overlay',
          area: 'top',
          order: 0,
          locked: false,
          muted: false,
          elements: [
            {
              elementId: 'autosave-image',
              elementType: 'image_overlay',
              startMs: 1_000,
              endMs: 4_000,
              zIndex: 1,
              enabled: true,
              locked: false,
              label: 'autosave-image',
              assetId: '90071992547410001' as never,
              transform: {
                xRatio: 0.1,
                yRatio: 0.1,
                widthRatio: 0.3,
                heightRatio: 0.3,
                rotationDeg: 0,
                opacity: 1,
              },
              fitMode: 'contain',
              crop: {
                xRatio: 0,
                yRatio: 0,
                widthRatio: 1,
                heightRatio: 1,
              },
              fade: { fadeInMs: 0, fadeOutMs: 0 },
              sourceStartOffset: 0,
              sourceEndOffset: 0,
              adoptedPrompt: null,
              sourceTaskId: null,
            },
          ],
        },
        {
          trackId: 'main-track',
          trackType: 'main_video',
          area: 'center',
          order: 0,
          locked: true,
          muted: false,
          elements: [
            {
              elementId: 'server-main',
              elementType: 'main_video',
              startMs: 0,
              endMs: 30_000,
              zIndex: 0,
              enabled: true,
              locked: true,
              label: 'server-main',
              assetId: '90071992547410003' as never,
              sourceDurationMs: 30_000,
              sourceStartMs: 0,
              fitMode: 'cover',
            },
          ],
        },
      ],
    };
    const getDraft = vi.fn().mockResolvedValue({
      projectId: '90071992547409931',
      timelineDraftId: '90071992547409932',
      revision: 'revision-2',
      schemaVersion: 'timeline-1',
      contentHash: 'content-hash',
      savedAt: '2026-08-08T08:31:00+08:00',
      timeline: serverTimeline,
    });
    const saveDraft = vi.fn(
      async (
        _projectId: string,
        request: { timeline: TimelineDocument },
      ): Promise<SaveTimelineDraftResult> => ({
        projectId: '90071992547409931' as never,
        timelineDraftId: '90071992547409932' as never,
        revision: 'revision-3' as never,
        schemaVersion: 'timeline-1' as const,
        contentHash: 'content-hash-3',
        savedAt: '2026-08-08T08:32:00+08:00',
        timeline: request.timeline,
        replayed: false,
        superseded: false,
        normalizationChanges: [],
      }),
    );
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={client}>
        <TimelineStep
          projectId="90071992547409931"
          state={initialStudioState}
          timelineApi={{ getDraft, saveDraft }}
          update={vi.fn()}
          onFinish={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          onToast={vi.fn()}
        />
      </QueryClientProvider>,
    );

    const clip = await screen.findByRole('button', {
      name: /时间轴片段.*autosave-image/,
    });
    fireEvent.pointerDown(clip, { clientX: 0, pointerId: 1 });
    fireEvent.pointerMove(clip, { clientX: 20, pointerId: 1 });
    fireEvent.pointerUp(clip, { clientX: 20, pointerId: 1 });

    await waitFor(
      () => expect(saveDraft).toHaveBeenCalledTimes(1),
      { timeout: 2_000 },
    );
    expect(saveDraft).toHaveBeenCalledWith(
      '90071992547409931',
      expect.objectContaining({
        expectedRevision: 'revision-2',
        timeline: expect.objectContaining({
          tracks: expect.arrayContaining([
            expect.objectContaining({
              elements: expect.arrayContaining([
                expect.objectContaining({
                  elementId: 'autosave-image',
                  startMs: 1_200,
                  endMs: 4_200,
                }),
              ]),
            }),
          ]),
        }),
      }),
    );
  });
});
