import type { PageQuery } from '@/api/types';

export type WorkflowTemplateId = string;
export type WorkflowTemplateChannel = 'video_template' | 'workflow_inspiration';
export type WorkflowTemplateStatus = 'draft' | 'pending_test' | 'enabled' | 'disabled';
export type RunningHubExecutionMode = 'runninghub_workflow' | 'runninghub_ai_app';
export type RunningHubWorkflowInstanceType = 'default' | 'plus';
export type JsonObject = Record<string, unknown>;

export interface WorkflowTemplateSummary {
  templateId: WorkflowTemplateId;
  channel: WorkflowTemplateChannel;
  name: string;
  slug: string;
  summary?: string | null;
  status: WorkflowTemplateStatus;
  recommended: boolean;
  categoryId: string;
  categoryName: string;
  executionConfigured: boolean;
  executionEnabled: boolean;
  accountName?: string | null;
  rowRevision: number;
  enabledAt?: string | null;
  updateTime?: string | null;
}

export interface WorkflowExecutionConfig {
  executionConfigId: string;
  templateId: WorkflowTemplateId;
  runningHubAccountId: string;
  executionMode: RunningHubExecutionMode;
  workflowId?: string | null;
  webAppId?: string | null;
  instanceType?: RunningHubWorkflowInstanceType | null;
  inputMapping: JsonObject;
  outputPolicy: JsonObject;
  timeoutSeconds: number;
  enabled: boolean;
  hasAccessPassword: boolean;
  lastTestStatus?: string | null;
  rowRevision: number;
  updateTime?: string | null;
}

export interface WorkflowTemplateDetail {
  templateId: WorkflowTemplateId;
  channel: WorkflowTemplateChannel;
  name: string;
  slug: string;
  summary?: string | null;
  description?: string | null;
  coverAssetId?: string | null;
  categoryId: string;
  tagIds: string[];
  formSchema: JsonObject;
  schemaHash: string;
  status: WorkflowTemplateStatus;
  recommended: boolean;
  sortNo: number;
  estimatedDurationSeconds?: number | null;
  billingMode: 'free';
  rowRevision: number;
  enabledAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  executionConfig?: WorkflowExecutionConfig | null;
}

export interface WorkflowTemplateSave {
  channel: WorkflowTemplateChannel;
  name: string;
  summary?: string;
  description?: string;
  coverAssetId?: string;
  categoryId: string;
  tagIds: string[];
  formSchema: JsonObject;
  recommended: boolean;
  sortNo: number;
  estimatedDurationSeconds?: number;
}

export type WorkflowTemplateUpdate = WorkflowTemplateSave & { expectedRevision: number };

export interface WorkflowExecutionConfigSave {
  runningHubAccountId: string;
  executionMode: RunningHubExecutionMode;
  workflowId?: string;
  webAppId?: string;
  instanceType?: RunningHubWorkflowInstanceType;
  accessPassword?: string;
  clearAccessPassword: boolean;
  inputMapping: JsonObject;
  outputPolicy: JsonObject;
  timeoutSeconds: number;
  enabled: boolean;
  expectedRevision?: number;
}

export interface WorkflowTemplateQuery extends PageQuery {
  channel?: WorkflowTemplateChannel;
  status?: WorkflowTemplateStatus;
  keyword?: string;
  categoryId?: string;
  recommended?: boolean;
  sort?: 'latest' | 'name' | 'sort_no';
}

export type WorkflowTemplateTableParams = WorkflowTemplateQuery & {
  current?: number;
  pageSize?: number;
};

export interface WorkflowTemplateOption {
  value: WorkflowTemplateId;
  label: string;
  status: WorkflowTemplateStatus;
}
