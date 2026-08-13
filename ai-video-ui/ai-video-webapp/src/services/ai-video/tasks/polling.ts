import { getHttpStatus } from '../core/errors';
import { isSessionInvalidationCode } from '../core/ruoyiAdapter';
import type { TaskDetail, TaskStatus } from './types';

const terminalStatuses = new Set<TaskStatus>(['success', 'failed', 'cancelled']);
const retryDelays = [2_000, 4_000, 8_000, 16_000, 30_000] as const;

export type TaskPollingScope = 'detail' | 'list';

export type TaskPollingInput = {
  failureCount: number;
  hiddenForMs: number;
  online: boolean;
  random?: () => number;
  scope: TaskPollingScope;
  visible: boolean;
};

export type TaskFinalization<T extends TaskDetail = TaskDetail> =
  | { kind: 'already-finalized' }
  | { kind: 'confirmed'; task: T }
  | { error: unknown; kind: 'unconfirmed' };

function jitter(delay: number, random: () => number): number {
  return Math.round(delay * (0.9 + random() * 0.2));
}

export function isTaskTerminal(status: TaskStatus): boolean {
  return terminalStatuses.has(status);
}

export function getTaskPollingDelay(input: TaskPollingInput): number | false {
  if (!input.online || input.hiddenForMs >= 5 * 60_000) return false;
  if (!input.visible) return 15_000;
  if (input.failureCount > 0) {
    const index = Math.min(input.failureCount - 1, retryDelays.length - 1);
    return jitter(retryDelays[index], input.random ?? Math.random);
  }
  return input.scope === 'detail' ? 2_000 : 5_000;
}

function errorCode(error: unknown): number | undefined {
  if (typeof error !== 'object' || error === null) return undefined;
  const code = (error as Record<string, unknown>).code;
  return typeof code === 'number' ? code : undefined;
}

export function shouldStopTaskPolling(error: unknown): boolean {
  const status = getHttpStatus(error);
  const code = errorCode(error);
  return (
    status === 403 ||
    status === 404 ||
    code === 403 ||
    code === 46601 ||
    (code !== undefined && isSessionInvalidationCode(code)) ||
    status === 401
  );
}

export function createTaskFinalizer<T extends TaskDetail>(
  fetchFinal: (taskId: string, signal?: AbortSignal) => Promise<T>,
) {
  const finalizedTaskIds = new Set<string>();

  return {
    async finalize(
      observed: T,
      stopPeriodic: () => void,
      signal?: AbortSignal,
    ): Promise<TaskFinalization<T>> {
      if (finalizedTaskIds.has(observed.taskId)) {
        return { kind: 'already-finalized' };
      }
      finalizedTaskIds.add(observed.taskId);
      stopPeriodic();
      try {
        const task = signal
          ? await fetchFinal(observed.taskId, signal)
          : await fetchFinal(observed.taskId);
        if (!isTaskTerminal(task.status)) {
          return {
            error: new Error('Final task detail is no longer terminal'),
            kind: 'unconfirmed',
          };
        }
        return { kind: 'confirmed', task };
      } catch (error) {
        return { error, kind: 'unconfirmed' };
      }
    },
    reset(taskId: string): void {
      finalizedTaskIds.delete(taskId);
    },
  };
}
