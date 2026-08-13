import type { TemplateListParams } from './types';

export interface DiscoveryQueryScope {
  userId: string;
  workspaceId: string;
}

const privateRoot = ({ userId, workspaceId }: DiscoveryQueryScope) =>
  ['app-private', userId, workspaceId, 'discovery'] as const;

export const discoveryQueryKeys = {
  home: (scope: DiscoveryQueryScope) => [...privateRoot(scope), 'home'] as const,
  templates: (scope: DiscoveryQueryScope, params: TemplateListParams) =>
    [...privateRoot(scope), 'templates', params] as const,
  template: (scope: DiscoveryQueryScope, templateId: string) =>
    [...privateRoot(scope), 'template', templateId] as const,
  creationConfig: (scope: DiscoveryQueryScope, templateId: string) =>
    [...privateRoot(scope), 'template', templateId, 'creation-config'] as const,
};
