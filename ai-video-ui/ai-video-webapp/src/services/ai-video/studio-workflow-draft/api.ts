import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';

const PATH = '/api/studio/workflow-draft/current';

export interface StudioWorkflowDraft {
  revision: string;
  currentStep: number;
  schemaVersion: 'studio-workflow-1';
  snapshotJson: string;
  updatedAt: string | null;
}

export interface SaveStudioWorkflowDraftInput {
  expectedRevision: number;
  currentStep: number;
  schemaVersion: 'studio-workflow-1';
  snapshotJson: string;
}

function parse(value: unknown): StudioWorkflowDraft | null {
  if (value === null) return null;
  if (typeof value !== 'object') throw new Error('工作台草稿响应格式异常');
  const draft = value as Record<string, unknown>;
  if (
    typeof draft.revision !== 'string' ||
    !/^\d+$/.test(draft.revision) ||
    !Number.isInteger(draft.currentStep) ||
    (draft.currentStep as number) < 0 ||
    (draft.currentStep as number) > 6 ||
    draft.schemaVersion !== 'studio-workflow-1' ||
    typeof draft.snapshotJson !== 'string' ||
    !(draft.updatedAt === null || typeof draft.updatedAt === 'string')
  ) {
    throw new Error('工作台草稿响应格式异常');
  }
  return draft as unknown as StudioWorkflowDraft;
}

export function createStudioWorkflowDraftApi(adapter: RuoYiAdapter) {
  return {
    async getCurrent(signal?: AbortSignal): Promise<StudioWorkflowDraft | null> {
      return parse(
        await adapter.request<unknown>(PATH, {
          method: 'GET',
          ...(signal ? { signal } : {}),
        }),
      );
    },
    async save(input: SaveStudioWorkflowDraftInput): Promise<StudioWorkflowDraft> {
      const result = parse(
        await adapter.request<unknown>(PATH, { method: 'PUT', data: input }),
      );
      if (!result) throw new Error('工作台草稿保存响应为空');
      return result;
    },
    clear(): Promise<void> {
      return adapter.request<void>(PATH, { method: 'DELETE' });
    },
  };
}

let runtimeApi: ReturnType<typeof createStudioWorkflowDraftApi> | undefined;

function getRuntimeApi() {
  runtimeApi ??= createStudioWorkflowDraftApi(getRuntimeRuoYiAdapter());
  return runtimeApi;
}

export const studioWorkflowDraftApi = {
  getCurrent: (signal?: AbortSignal) => getRuntimeApi().getCurrent(signal),
  save: (input: SaveStudioWorkflowDraftInput) => getRuntimeApi().save(input),
  clear: () => getRuntimeApi().clear(),
};
