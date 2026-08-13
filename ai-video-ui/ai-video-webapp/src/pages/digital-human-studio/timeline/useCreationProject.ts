import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { timelineQueryKeys } from '@/services/ai-video/creation-timeline/queryKeys';

export function useCreationProject(api: CreationTimelineApi, projectId?: string) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: projectId ? timelineQueryKeys.project(projectId) : ['creation-timeline', 'project', 'pending'], queryFn: () => projectId ? api.getProject(projectId) : Promise.reject(new Error('Project id is required')), enabled: Boolean(projectId), retry: false });
  const create = useMutation({
    mutationFn: (input: { sourceId: string; projectTitle?: string; idempotencyKey: string }) => api.createProject({ sourceType: 'digital_human_job', ...input }),
    onSuccess: (project) => client.setQueryData(timelineQueryKeys.project(project.projectId), project),
  });
  return { ...query, createProject: create.mutateAsync, creating: create.isPending };
}
