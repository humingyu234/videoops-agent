import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { CreateWorkflowOrderInput, CreateWorkflowOrderResult, WorkflowOrderDetail } from './types';

const adapter = () => getRuntimeRuoYiAdapter();

export const workflowOrdersApi = {
  create: ({ idempotencyKey, ...data }: CreateWorkflowOrderInput) =>
    adapter().request<CreateWorkflowOrderResult>('/api/workflow-orders', {
      method: 'POST', data, headers: { 'Idempotency-Key': idempotencyKey },
    }),
  getDetail: (orderId: string, signal?: AbortSignal) =>
    adapter().request<WorkflowOrderDetail>(`/api/workflow-orders/${encodeURIComponent(orderId)}`, { signal }),
  cancel: (orderId: string) =>
    adapter().request<WorkflowOrderDetail>(`/api/workflow-orders/${encodeURIComponent(orderId)}/cancellations`, { method: 'POST' }),
  getAssetAccessUrl: (assetId: string) =>
    adapter().request<{ url: string; expiresAt: string }>(`/api/assets/${encodeURIComponent(assetId)}/access-url`),
};
