import type { WorkflowMedia } from '../discovery/types';

export type WorkflowOrderStatus = 'pending' | 'queued' | 'running' | 'success' | 'failed' | 'cancelled';
export type WorkflowTaskStage = 'waiting_for_dispatch' | 'preparing_inputs' | 'submitting_to_provider' | 'confirming_provider_acceptance' | 'provider_processing' | 'processing_results' | 'completed' | 'failed' | 'cancelled';
export type WorkflowFieldType = 'text' | 'textarea' | 'integer' | 'decimal' | 'boolean' | 'select' | 'multi_select' | 'image' | 'audio' | 'video' | 'file';
export interface WorkflowFormOption { label: string; value: string }
export interface WorkflowFormField { key: string; label: string; type: WorkflowFieldType; required: boolean; placeholder?: string; options?: WorkflowFormOption[] }
export interface ReadyAsset { assetId: string }
export type WorkflowInputValue =
  | string
  | number
  | boolean
  | string[]
  | ReadyAsset[];

export interface CreateWorkflowOrderInput {
  templateId: string;
  schemaHash: string;
  inputs: Record<string, WorkflowInputValue>;
  idempotencyKey: string;
}
export interface CreateWorkflowOrderResult { orderId: string; taskId: string; templateId: string; status: WorkflowOrderStatus }
export interface WorkflowOrderAsset { assetId: string; label: string; mediaType: 'image' | 'audio' | 'video' | 'file'; fileName: string; sizeBytes: string; status: 'ready' | 'processing' | 'failed'; primary: boolean }
export interface WorkflowOrderDetail {
  orderId: string;
  orderNo: string;
  createdAt: string;
  template: { templateId: string; title: string; cover: WorkflowMedia | null };
  inputs: Array<{ inputKey: string; label: string; displayValue?: string; assets: WorkflowOrderAsset[] }>;
  task: { taskId: string; taskType: 'workflow_template_generate'; status: WorkflowOrderStatus; stage: WorkflowTaskStage; progressPercent?: number; failureCode?: string; failureMessage?: string; retryable: boolean; createdAt: string; updatedAt: string };
  outputs: WorkflowOrderAsset[];
  canCancel: boolean;
  canRemake: boolean;
}
