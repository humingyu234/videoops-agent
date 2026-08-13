export const workflowOrderQueryKeys = {
  all: (userId: string, workspaceId: string) => ['app-private', userId, workspaceId, 'workflow-orders'] as const,
  detail: (userId: string, workspaceId: string, orderId: string) =>
    [...workflowOrderQueryKeys.all(userId, workspaceId), 'detail', orderId] as const,
  template: (userId: string, workspaceId: string, templateId: string) =>
    [...workflowOrderQueryKeys.all(userId, workspaceId), 'template', templateId] as const,
};
