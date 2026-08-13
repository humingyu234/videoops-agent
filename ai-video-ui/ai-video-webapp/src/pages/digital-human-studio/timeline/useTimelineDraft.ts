import { useQuery } from '@tanstack/react-query';
import type { CreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { timelineQueryKeys } from '@/services/ai-video/creation-timeline/queryKeys';

export function useTimelineDraft(api: CreationTimelineApi, projectId?: string) {
  return useQuery({ queryKey: projectId ? timelineQueryKeys.draft(projectId) : ['creation-timeline', 'draft', 'pending'], queryFn: () => projectId ? api.getDraft(projectId) : Promise.reject(new Error('Project id is required')), enabled: Boolean(projectId), retry: false });
}
