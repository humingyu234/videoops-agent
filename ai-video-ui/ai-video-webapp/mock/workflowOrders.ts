import type { Request, Response } from 'express';

type StoredOrder = {
  orderId: string; orderNo: string; createdAt: string;
  template: { templateId: string; templateVersionId: string; title: string; cover: Record<string, unknown> };
  executionPlan: { executionPlanId: string; displayName: string; providerDisplayName: string; providerKind: string; featureTags: string[] };
  inputs: unknown[]; task: Record<string, unknown> & { taskId: string; status: string; stage: string }; outputs: unknown[];
  canCancel: boolean; canRemake: boolean; polls: number;
};
const fixedTime = '2026-08-05T08:00:00+08:00';
const orders = new Map<string, StoredOrder>();
const idempotency = new Map<string, string>();
let sequence = 1;
const ok = (res: Response, data: unknown) => res.json({ code: 200, msg: '操作成功', data });
const publicOrder = ({ polls: _polls, ...order }: StoredOrder) => order;
const cover = { mediaId: 'media-skin-film', mediaType: 'image', url: '/discovery/skincare.webp', width: 1200, height: 1000, alt: '护肤品广告' };

function createStored(orderId: string, templateId = 'skin-film'): StoredOrder {
  return {
    orderId, orderNo: `WF-${orderId}`, createdAt: fixedTime,
    template: { templateId, templateVersionId: 'version-1', title: '质感商品广告短片', cover },
    executionPlan: { executionPlanId: 'plan-standard', displayName: '标准制作', providerDisplayName: '平台工作流 A', providerKind: 'self_hosted_comfyui', featureTags: ['高清'] },
    inputs: [], task: { taskId: `task-${orderId}`, taskType: 'workflow_template_generate', status: 'queued', stage: 'waiting_for_dispatch', retryable: false, createdAt: fixedTime, updatedAt: fixedTime },
    outputs: [], canCancel: true, canRemake: false, polls: 0,
  };
}
const demo = createStored('demo-success');
Object.assign(demo.task, { status: 'success', stage: 'completed', progressPercent: 100 });
Object.assign(demo, { canCancel: false, canRemake: true, polls: 3, outputs: [{ assetId: 'asset-result-demo', label: '成片预览', mediaType: 'image', fileName: 'result.webp', sizeBytes: '38210', status: 'ready', primary: true }] });
orders.set(demo.orderId, demo);

export function getMockTasks() {
  return [...orders.values()].map((order) => ({
    taskId: order.task.taskId, taskType: 'workflow_template_generate', taskTypeLabel: '视频模板制作', title: order.template.title,
    status: order.task.status, stage: order.task.stage, progressPercent: order.task.progressPercent, retryable: false,
    resourceType: 'workflow_order', resourceId: order.orderId, detailTarget: { type: 'workflow_order', orderId: order.orderId },
    canCancel: order.canCancel, createdAt: fixedTime, updatedAt: fixedTime,
  }));
}

export default {
  'POST /api/workflow-orders': (req: Request, res: Response) => {
    const key = String(req.header('Idempotency-Key') ?? '');
    const existing = key && idempotency.get(key);
    if (existing) { const order = orders.get(existing)!; return ok(res, { orderId: order.orderId, orderNo: order.orderNo, taskId: order.task.taskId, taskStatus: order.task.status, createdAt: fixedTime }); }
    const orderId = `order-demo-${sequence++}`;
    const order = createStored(orderId, String(req.body.templateId));
    orders.set(orderId, order); if (key) idempotency.set(key, orderId);
    return ok(res, { orderId, orderNo: order.orderNo, taskId: order.task.taskId, taskStatus: order.task.status, createdAt: fixedTime });
  },
  'GET /api/workflow-orders/:orderId': (req: Request, res: Response) => {
    const order = orders.get(String(req.params.orderId));
    if (!order) return res.status(404).json({ code: 404, msg: '订单不存在', data: null });
    if (!['success', 'failed', 'cancelled'].includes(order.task.status)) {
      order.polls += 1;
      if (order.polls >= 2) {
        Object.assign(order.task, { status: 'success', stage: 'completed', progressPercent: 100 });
        Object.assign(order, { canCancel: false, canRemake: true, outputs: [{ assetId: 'asset-result-demo', label: '成片预览', mediaType: 'image', fileName: 'result.webp', sizeBytes: '38210', status: 'ready', primary: true }] });
      } else Object.assign(order.task, { status: 'running', stage: 'provider_processing', progressPercent: 46 });
    }
    return ok(res, publicOrder(order));
  },
  'POST /api/workflow-orders/:orderId/cancellations': (req: Request, res: Response) => {
    const order = orders.get(String(req.params.orderId));
    if (!order) return res.status(404).json({ code: 404, msg: '订单不存在', data: null });
    Object.assign(order.task, { status: 'cancelled', stage: 'cancelled' }); Object.assign(order, { canCancel: false, canRemake: true });
    return ok(res, publicOrder(order));
  },
};
