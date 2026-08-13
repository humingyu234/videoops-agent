const root = ['creation-timeline'] as const;

export const timelineQueryKeys = {
  root,
  project: (projectId: string) => [...root, 'project', projectId] as const,
  draft: (projectId: string) => [...root, 'project', projectId, 'draft'] as const,
  versions: (projectId: string) => [...root, 'project', projectId, 'versions'] as const,
  output: (projectId: string) => [...root, 'project', projectId, 'output'] as const,
};
