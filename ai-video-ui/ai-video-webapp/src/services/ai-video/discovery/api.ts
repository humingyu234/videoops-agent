import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import {
  assertDiscoveryDecimalId,
  parseDiscoveryHome,
  parseWorkflowCreationConfig,
  parseWorkflowTemplateDetail,
  parseWorkflowTemplatePage,
} from './adapter';
import type {
  DiscoveryHome,
  TemplateListParams,
  WorkflowCreationConfig,
  WorkflowTemplateDetail,
  WorkflowTemplatePage,
} from './types';

function query(params: TemplateListParams): string {
  if (!Number.isSafeInteger(params.pageNum) || params.pageNum < 1) {
    throw new Error('pageNum must be a safe positive integer');
  }
  if (
    !Number.isSafeInteger(params.pageSize) ||
    params.pageSize < 1 ||
    params.pageSize > 50
  ) {
    throw new Error('pageSize must be between 1 and 50');
  }
  if (params.categoryCode !== undefined) {
    assertDiscoveryDecimalId(params.categoryCode, 'categoryCode');
  }
  if (params.tagCodes !== undefined) {
    params.tagCodes
      .split(',')
      .forEach((tagCode) => assertDiscoveryDecimalId(tagCode, 'tagCode'));
  }
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value));
  }
  return `?${search.toString()}`;
}

export interface DiscoveryApi {
  getHome(): Promise<DiscoveryHome>;
  getTemplates(params: TemplateListParams): Promise<WorkflowTemplatePage>;
  getTemplate(templateId: string): Promise<WorkflowTemplateDetail>;
  getCreationConfig(templateId: string): Promise<WorkflowCreationConfig>;
}

export function createDiscoveryApi(adapter: RuoYiAdapter): DiscoveryApi {
  return {
    getHome: async () =>
      parseDiscoveryHome(
        await adapter.request<unknown>('/api/discovery/home'),
      ),
    getTemplates: async (params) =>
      parseWorkflowTemplatePage(
        await adapter.request<unknown>(`/api/discovery/templates${query(params)}`),
      ),
    getTemplate: async (templateId) => {
      const id = assertDiscoveryDecimalId(templateId, 'templateId');
      return parseWorkflowTemplateDetail(
        await adapter.request<unknown>(`/api/discovery/templates/${id}`),
      );
    },
    getCreationConfig: async (templateId) => {
      const id = assertDiscoveryDecimalId(templateId, 'templateId');
      return parseWorkflowCreationConfig(
        await adapter.request<unknown>(
          `/api/discovery/templates/${id}/creation-config`,
        ),
      );
    },
  };
}

let runtimeApi: DiscoveryApi | undefined;

function getApi(): DiscoveryApi {
  runtimeApi ??= createDiscoveryApi(getRuntimeRuoYiAdapter());
  return runtimeApi;
}

export const discoveryApi: DiscoveryApi = {
  getHome: () => getApi().getHome(),
  getTemplates: (params) => getApi().getTemplates(params),
  getTemplate: (templateId) => getApi().getTemplate(templateId),
  getCreationConfig: (templateId) => getApi().getCreationConfig(templateId),
};
