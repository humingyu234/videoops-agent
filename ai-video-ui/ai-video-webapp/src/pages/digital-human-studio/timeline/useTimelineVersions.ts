import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createIdempotencyKeyStore } from '@/services/ai-video/creation-timeline/idempotency';
import type { CreationTimelineApi } from '@/services/ai-video/creation-timeline/api';
import { timelineQueryKeys } from '@/services/ai-video/creation-timeline/queryKeys';

const keys = createIdempotencyKeyStore();
export function useTimelineVersions(api: CreationTimelineApi, projectId?: string) {
  const client = useQueryClient();
  const versions = useQuery({ queryKey: projectId ? timelineQueryKeys.versions(projectId) : ['creation-timeline', 'versions', 'pending'], queryFn: () => projectId ? api.listVersions(projectId, { pageNum: 1, pageSize: 20 }) : Promise.reject(new Error('Project id is required')), enabled: Boolean(projectId), retry: false });
  const restore = useMutation({ mutationFn: ({ versionId, expectedRevision }: { versionId: string; expectedRevision: string }) => projectId ? api.restoreVersion(projectId, versionId, { idempotencyKey: keys.beginNewIntent(`restore:${versionId}`), expectedRevision }) : Promise.reject(new Error('Project id is required')), onSuccess: () => projectId && client.invalidateQueries({ queryKey: timelineQueryKeys.draft(projectId) }) });
  return { ...versions, restoreVersion: restore.mutateAsync, restoring: restore.isPending };
}
