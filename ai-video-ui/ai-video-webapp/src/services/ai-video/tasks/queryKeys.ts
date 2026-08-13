import type { TaskListParams } from './types';

export const taskQueryKeys = {
  all: (userId: string, workspaceId: string) =>
    ['app-private', userId, workspaceId, 'tasks'] as const,
  detail: (userId: string, workspaceId: string, taskId: string) =>
    [...taskQueryKeys.all(userId, workspaceId), 'detail', taskId] as const,
  list: (userId: string, workspaceId: string, params: TaskListParams) =>
    [...taskQueryKeys.all(userId, workspaceId), 'list', params] as const,
};
