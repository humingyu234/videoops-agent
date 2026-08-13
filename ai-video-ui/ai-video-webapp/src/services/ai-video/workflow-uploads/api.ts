import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';

export interface WorkflowUploadSession {
  uploadId: string;
  status: string;
  expiresAt?: string;
  singlePutUrl?: string;
  requiredHeaders?: Record<string, string>;
  assetId?: string;
  assetStatus?: string;
}

export interface CreateWorkflowUploadInput {
  templateId: string;
  schemaHash: string;
  inputKey: string;
  file: File;
  idempotencyKey: string;
}

export interface WorkflowUploadsApi {
  create(input: CreateWorkflowUploadInput): Promise<WorkflowUploadSession>;
  transfer(contentUrl: string, file: File): Promise<WorkflowUploadSession>;
  complete(uploadId: string): Promise<WorkflowUploadSession>;
}

export function createWorkflowUploadsApi(adapter: RuoYiAdapter): WorkflowUploadsApi {
  return {
    create: ({ file, ...input }) => adapter.request<WorkflowUploadSession>('/api/assets/uploads', {
      method: 'POST',
      data: {
        purpose: 'workflow_input',
        ...input,
        fileName: file.name,
        declaredContentType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
      },
    }),
    transfer: (contentUrl, file) => adapter.request<WorkflowUploadSession>(contentUrl, {
      method: 'PUT',
      data: file,
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
    }),
    complete: (uploadId) => adapter.request<WorkflowUploadSession>(
      `/api/assets/uploads/${encodeURIComponent(uploadId)}/complete`,
      { method: 'POST' },
    ),
  };
}

const runtimeApi = () => createWorkflowUploadsApi(getRuntimeRuoYiAdapter());
export const workflowUploadsApi: WorkflowUploadsApi = {
  create: (input) => runtimeApi().create(input),
  transfer: (contentUrl, file) => runtimeApi().transfer(contentUrl, file),
  complete: (uploadId) => runtimeApi().complete(uploadId),
};
