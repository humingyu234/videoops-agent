import { describe, expect, it, vi } from 'vitest';
import { createCreationTimelineApi } from './api';
import { timelineQueryKeys } from './queryKeys';

const queuedTimelineTask = {
  taskId: '90071992547409937',
  taskType: 'timeline_render',
  resourceType: 'creation_project',
  resourceId: '90071992547409931',
  status: 'queued',
  stage: 'queued',
  progress: 0,
  canCancel: true,
  canRetry: false,
  createdAt: '2026-08-08T08:31:00+08:00',
};

describe('creation timeline api', () => {
  it('centralizes the conflict-copy endpoint with the frozen C0 request body', async () => {
    const request = vi.fn().mockResolvedValue({
      versionId: '90071992547409939', projectId: '90071992547409931', versionNo: '4',
      sourceDraftRevision: '3', schemaVersion: 'timeline-1', contentHash: 'a'.repeat(64),
      versionReason: 'conflict_copy', createdAt: '2026-08-08T08:31:00+08:00',
    });
    const api = createCreationTimelineApi({ request });

    await api.createConflictCopy('90071992547409931', {
      idempotencyKey: 'intent-1',
      baseRevision: '3',
      schemaVersion: 'timeline-1',
      timeline: {
        schemaVersion: 'timeline-1',
        canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1, safeMarginRatio: 0.05 },
        tracks: [],
      },
    });

    expect(request).toHaveBeenCalledWith(
      '/api/studio/creation-projects/90071992547409931/timeline-versions/conflict-copies',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('uses frozen project, draft, and render-task paths without page-side URL construction', async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ projectId: '90071992547409931' })
      .mockResolvedValueOnce({ revision: '4' })
      .mockResolvedValueOnce(queuedTimelineTask);
    const api = createCreationTimelineApi({ request });

    await api.getProject('90071992547409931');
    await api.saveDraft('90071992547409931', {
      idempotencyKey: 'save-1', expectedRevision: '3', schemaVersion: 'timeline-1',
      timeline: { schemaVersion: 'timeline-1', canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1, safeMarginRatio: 0.05 }, tracks: [] },
    });
    await api.createRenderTask('90071992547409931', {
      idempotencyKey: 'render-1', expectedRevision: '4',
      outputConfig: { resolutionPreset: 'match_canvas', frameRate: 30, qualityPreset: 'standard' },
    });

    expect(request.mock.calls.map(([url]) => url)).toEqual([
      '/api/studio/creation-projects/90071992547409931',
      '/api/studio/creation-projects/90071992547409931/timeline-draft',
      '/api/studio/creation-projects/90071992547409931/render-tasks',
    ]);
    expect(request.mock.calls[1][1]).toMatchObject({ method: 'PUT' });
    expect(request.mock.calls[2][1]).toMatchObject({ method: 'POST' });
    expect(request.mock.calls[2][1].data.outputConfig).toEqual({
      resolutionPreset: 'match_canvas',
      frameRate: 30,
      qualityPreset: 'standard',
    });
    expect(request.mock.calls[2][1].data.outputConfig).not.toHaveProperty('quality');
  });

  it('rejects runtime request fields that are not frozen by C0', async () => {
    const request = vi.fn();
    const api = createCreationTimelineApi({ request });

    await expect(api.createConflictCopy('90071992547409931', {
      idempotencyKey: 'intent-1', baseRevision: '3', schemaVersion: 'timeline-1',
      timeline: { schemaVersion: 'timeline-1', canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1, safeMarginRatio: 0.05 }, tracks: [] },
      unexpected: 'do not send',
    } as never)).rejects.toThrow('contains an unknown field');
    expect(request).not.toHaveBeenCalled();
  });

  it('rejects a numeric version identifier in a conflict-copy response', async () => {
    const request = vi.fn().mockResolvedValue({
      versionId: 90071992547409939,
      projectId: '90071992547409931', versionNo: '4', sourceDraftRevision: '3', schemaVersion: 'timeline-1',
      contentHash: 'a'.repeat(64), versionReason: 'conflict_copy', createdAt: '2026-08-08T08:31:00+08:00',
    });
    const api = createCreationTimelineApi({ request });

    await expect(api.createConflictCopy('90071992547409931', {
      idempotencyKey: 'intent-1', baseRevision: '3', schemaVersion: 'timeline-1',
      timeline: { schemaVersion: 'timeline-1', canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1, safeMarginRatio: 0.05 }, tracks: [] },
    })).rejects.toThrow('versionId must be a canonical decimal string');
  });

  it('rejects a task creation response with a non-creation-project resource type', async () => {
    const request = vi.fn().mockResolvedValue({
      ...queuedTimelineTask,
      resourceType: 'workflow_order',
    });
    const api = createCreationTimelineApi({ request });

    await expect(api.createRenderTask('90071992547409931', {
      idempotencyKey: 'render-1',
      expectedRevision: '4',
      outputConfig: { resolutionPreset: 'match_canvas', frameRate: 30, qualityPreset: 'standard' },
    })).rejects.toThrow('resourceType must be creation_project');
  });

  it('rejects legacy quality before it can be sent to the render endpoint', async () => {
    const request = vi.fn();
    const api = createCreationTimelineApi({ request });

    await expect(api.createRenderTask('90071992547409931', {
      idempotencyKey: 'render-1',
      expectedRevision: '4',
      outputConfig: {
        resolutionPreset: 'match_canvas',
        frameRate: 30,
        quality: 'high',
      },
    } as never)).rejects.toThrow('outputConfig contains an unknown field');
    expect(request).not.toHaveBeenCalled();
  });

  it('strictly parses the current project CreationOutput response', async () => {
    const output = {
      projectId: '90071992547409931',
      outputAssetId: '90071992547410003',
      taskId: '90071992547409937',
      createdAt: '2026-08-08T08:32:00+08:00',
    };
    const request = vi.fn().mockResolvedValue(output);
    const api = createCreationTimelineApi({ request });

    await expect(api.getLatestOutput(output.projectId)).resolves.toEqual(output);
    expect(request).toHaveBeenCalledWith(
      '/api/studio/creation-projects/90071992547409931/outputs/latest',
      { method: 'GET' },
    );

    request.mockResolvedValueOnce({ ...output, previewUrl: '/never-use-this' });
    await expect(api.getLatestOutput(output.projectId)).rejects.toThrow(
      'creationOutput contains an unknown field',
    );
  });

  it('strictly rejects legacy task fields from all four creation responses', async () => {
    const request = vi.fn().mockResolvedValue({
      ...queuedTimelineTask,
      cancellable: true,
    });
    const api = createCreationTimelineApi({ request });

    await expect(api.createImagePromptTask('90071992547409931', {
      idempotencyKey: 'image-1',
      expectedRevision: '4',
      sourceSelection: { sourceStartOffset: 0, sourceEndOffset: 4 },
      style: 'minimal',
    })).rejects.toThrow('contains an unknown field');
    await expect(api.createFancyTextSuggestionTask('90071992547409931', {
      idempotencyKey: 'fancy-1',
      expectedRevision: '4',
      sourceSelection: { subtitleElementIds: ['subtitle-1'] },
      animationIntensity: 'normal',
    })).rejects.toThrow('contains an unknown field');
    await expect(api.createSubtitleAlignmentTask('90071992547409931', {
      idempotencyKey: 'subtitle-1',
      expectedRevision: '4',
      subtitleElementIds: ['subtitle-1'],
    })).rejects.toThrow('contains an unknown field');
    await expect(api.createRenderTask('90071992547409931', {
      idempotencyKey: 'render-1',
      expectedRevision: '4',
      outputConfig: { resolutionPreset: 'match_canvas', frameRate: 30, qualityPreset: 'standard' },
    })).rejects.toThrow('contains an unknown field');
  });
});

describe('creation timeline query keys', () => {
  it('uses a single project-scoped key hierarchy', () => {
    expect(timelineQueryKeys.draft('90071992547409931')).toEqual([
      'creation-timeline', 'project', '90071992547409931', 'draft',
    ]);
    expect(timelineQueryKeys.versions('90071992547409931')).toEqual([
      'creation-timeline', 'project', '90071992547409931', 'versions',
    ]);
  });
});
