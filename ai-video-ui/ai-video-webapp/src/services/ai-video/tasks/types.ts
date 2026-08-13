export type TaskStatus =
  | 'pending'
  | 'queued'
  | 'running'
  | 'success'
  | 'failed'
  | 'cancelled';

export type AiTaskVO = {
  taskId: string;
  taskType: string;
  resourceType: string;
  resourceId: string;
  inputVersionId?: string;
  status: TaskStatus;
  stage: string;
  progress: number;
  canCancel: boolean;
  canRetry: boolean;
  resultAssetId?: string;
  resultSchemaVersion?: string;
  errorCode?: string;
  errorSummary?: string;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
};

export type TaskKind =
  | 'image-prompt'
  | 'fancy-text'
  | 'subtitle-alignment'
  | 'render'
  | 'unknown';

export type TaskDetail = AiTaskVO & {
  kind: TaskKind;
  result?: Record<string, unknown>;
};
export type TaskListItem = Omit<TaskDetail, 'result'>;

export type TaskListParams = {
  pageNum: number;
  pageSize: number;
  taskType?: string;
  status?: TaskStatus;
  keyword?: string;
};

export type TaskPage = {
  total: number;
  rows: TaskListItem[];
};

export type TaskActionRequest = {
  idempotencyKey: string;
};
