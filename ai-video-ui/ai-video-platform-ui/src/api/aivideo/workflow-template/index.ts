import type { PageResult, R } from '@/api/types';
import request from '@/api/request';
import { toPageQuery, toTableData } from '@/utils/ruoyi';
import type {
  WorkflowExecutionConfig,
  WorkflowExecutionConfigSave,
  WorkflowTemplateDetail,
  WorkflowTemplateId,
  WorkflowTemplateOption,
  WorkflowTemplateSave,
  WorkflowTemplateSummary,
  WorkflowTemplateTableParams,
  WorkflowTemplateUpdate
} from './types';

const WORKFLOW_TEMPLATE_RESOURCE = '/api/admin/workflow-templates';

function templateResource(templateId: WorkflowTemplateId) {
  return `${WORKFLOW_TEMPLATE_RESOURCE}/${encodeURIComponent(templateId)}`;
}

export async function pageWorkflowTemplates(params: WorkflowTemplateTableParams) {
  const response = await request<R<PageResult<WorkflowTemplateSummary>>>({
    method: 'get',
    params: toPageQuery(params),
    url: WORKFLOW_TEMPLATE_RESOURCE
  });
  return toTableData(response);
}

export async function createWorkflowTemplate(data: WorkflowTemplateSave) {
  const response = await request<R<WorkflowTemplateId>, WorkflowTemplateSave>({
    data,
    method: 'post',
    url: WORKFLOW_TEMPLATE_RESOURCE
  });
  return response.data;
}

export async function getWorkflowTemplate(templateId: WorkflowTemplateId) {
  const response = await request<R<WorkflowTemplateDetail>>({
    method: 'get',
    url: templateResource(templateId)
  });
  return response.data;
}

export async function updateWorkflowTemplate(templateId: WorkflowTemplateId, data: WorkflowTemplateUpdate) {
  const response = await request<R<void>, WorkflowTemplateUpdate>({
    data,
    method: 'put',
    url: templateResource(templateId)
  });
  return response.data;
}

export async function deleteWorkflowTemplate(templateId: WorkflowTemplateId, expectedRevision: number) {
  const response = await request<R<void>>({
    method: 'delete',
    params: { expectedRevision },
    url: templateResource(templateId)
  });
  return response.data;
}

export async function getWorkflowExecutionConfig(templateId: WorkflowTemplateId) {
  const response = await request<R<WorkflowExecutionConfig>>({
    method: 'get',
    url: `${templateResource(templateId)}/execution-config`
  });
  return response.data;
}

export async function saveWorkflowExecutionConfig(templateId: WorkflowTemplateId, data: WorkflowExecutionConfigSave) {
  const response = await request<R<WorkflowExecutionConfig>, WorkflowExecutionConfigSave>({
    data,
    headers: { repeatSubmit: false },
    method: 'put',
    url: `${templateResource(templateId)}/execution-config`
  });
  return response.data;
}

export async function enableWorkflowTemplate(templateId: WorkflowTemplateId, expectedRevision: number) {
  const response = await request<R<void>, { expectedRevision: number }>({
    data: { expectedRevision },
    method: 'post',
    url: `${templateResource(templateId)}/enable`
  });
  return response.data;
}

export async function disableWorkflowTemplate(templateId: WorkflowTemplateId, expectedRevision: number) {
  const response = await request<R<void>, { expectedRevision: number }>({
    data: { expectedRevision },
    method: 'post',
    url: `${templateResource(templateId)}/disable`
  });
  return response.data;
}

export async function listWorkflowTemplateOptions() {
  const response = await request<R<WorkflowTemplateOption[]>>({
    method: 'get',
    url: `${WORKFLOW_TEMPLATE_RESOURCE}/options`
  });
  return response.data;
}
