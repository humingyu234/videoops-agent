import { createStudioWorkflowDraftApi } from './api';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { vi } from 'vitest';

describe('studio workflow draft api', () => {
  it('uses the current-owner resource without accepting an owner id', async () => {
    const request = vi
      .fn()
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
        revision: '2',
        currentStep: 3,
        schemaVersion: 'studio-workflow-1',
        snapshotJson: '{"schemaVersion":"studio-workflow-1"}',
        updatedAt: null,
      })
      .mockResolvedValueOnce(undefined);
    const api = createStudioWorkflowDraftApi({ request } as unknown as RuoYiAdapter);

    await expect(api.getCurrent()).resolves.toBeNull();
    await expect(
      api.save({
        expectedRevision: 1,
        currentStep: 3,
        schemaVersion: 'studio-workflow-1',
        snapshotJson: '{"schemaVersion":"studio-workflow-1"}',
      }),
    ).resolves.toMatchObject({ revision: '2', currentStep: 3 });
    await api.clear();

    expect(request.mock.calls).toEqual([
      ['/api/studio/workflow-draft/current', { method: 'GET' }],
      [
        '/api/studio/workflow-draft/current',
        {
          method: 'PUT',
          data: {
            expectedRevision: 1,
            currentStep: 3,
            schemaVersion: 'studio-workflow-1',
            snapshotJson: '{"schemaVersion":"studio-workflow-1"}',
          },
        },
      ],
      ['/api/studio/workflow-draft/current', { method: 'DELETE' }],
    ]);
  });
});
