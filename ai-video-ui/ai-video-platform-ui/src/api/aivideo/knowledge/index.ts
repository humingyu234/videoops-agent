import type { PageResult, R } from '@/api/types';
import request from '@/api/request';
import { toPageQuery, toTableData } from '@/utils/ruoyi';
import type {
  ImportKnowledgeItemsInput,
  KnowledgeImportSummary,
  KnowledgeItemAdmin,
  KnowledgeItemDetail,
  KnowledgeItemId,
  KnowledgeItemSaveForm,
  KnowledgeItemTableParams,
  KnowledgeStatus
} from './types';

const KNOWLEDGE_RESOURCE = '/api/admin/knowledge-items';

function getKnowledgeResource(id: KnowledgeItemId) {
  return `${KNOWLEDGE_RESOURCE}/${encodeURIComponent(String(id))}`;
}

export async function pageKnowledgeItems(params: KnowledgeItemTableParams) {
  const response = await request<R<PageResult<KnowledgeItemAdmin>>>({
    method: 'get',
    params: toPageQuery(params),
    url: KNOWLEDGE_RESOURCE
  });
  return toTableData(response);
}

export async function getKnowledgeItem(id: KnowledgeItemId) {
  const response = await request<R<KnowledgeItemDetail>>({
    method: 'get',
    url: getKnowledgeResource(id)
  });
  return response.data;
}

export async function addKnowledgeItem(data: KnowledgeItemSaveForm) {
  const response = await request<R<KnowledgeItemId>, KnowledgeItemSaveForm>({
    data,
    method: 'post',
    url: KNOWLEDGE_RESOURCE
  });
  return response.data;
}

export async function updateKnowledgeItem(id: KnowledgeItemId, data: KnowledgeItemSaveForm) {
  const response = await request<R<void>, KnowledgeItemSaveForm>({
    data,
    method: 'put',
    url: getKnowledgeResource(id)
  });
  return response.data;
}

export async function deleteKnowledgeItem(id: KnowledgeItemId) {
  const response = await request<R<void>>({
    method: 'delete',
    url: getKnowledgeResource(id)
  });
  return response.data;
}

export async function updateKnowledgeStatus(id: KnowledgeItemId, status: KnowledgeStatus) {
  const response = await request<R<void>, { status: KnowledgeStatus }>({
    data: { status },
    method: 'put',
    url: `${getKnowledgeResource(id)}/status`
  });
  return response.data;
}

export async function importKnowledgeItems(input: ImportKnowledgeItemsInput) {
  const formData = new FormData();
  input.rows.forEach(row => {
    formData.append('files', row.file);
    formData.append('names', row.name);
    formData.append('knowledgeTypes', row.knowledgeType);
    formData.append('statuses', row.status);
  });

  const response = await request<R<KnowledgeImportSummary>, FormData>({
    data: formData,
    method: 'post',
    url: `${KNOWLEDGE_RESOURCE}/imports`
  });
  return response.data;
}
