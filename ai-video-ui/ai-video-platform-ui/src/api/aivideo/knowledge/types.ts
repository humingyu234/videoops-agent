import type { PageQuery } from '@/api/types';

export type KnowledgeItemId = string;

export type KnowledgeType = 'primary_template' | 'writing_technique' | 'psychology' | 'case' | 'mandatory_rule';

export type KnowledgeStatus = 'draft' | 'reviewing' | 'published' | 'retired';

export interface KnowledgeItemAdmin {
  id: KnowledgeItemId;
  name: string;
  knowledgeType: KnowledgeType;
  status: KnowledgeStatus;
  versionNo: number;
  updateTime?: string | null;
}

export interface KnowledgeItemDetail {
  id: KnowledgeItemId;
  name: string;
  knowledgeType: KnowledgeType;
  status: KnowledgeStatus;
  versionNo: number;
  summary: string | null;
  content: string;
  updateTime: string | null;
}

export interface KnowledgeItemSaveForm {
  name: string;
  knowledgeType: KnowledgeType;
  status: KnowledgeStatus;
  content: string;
  summary?: string;
}

export type SaveForm = KnowledgeItemSaveForm;
export type Detail = KnowledgeItemDetail;

export interface KnowledgeItemQuery extends PageQuery {
  name?: string;
  knowledgeType?: KnowledgeType;
  status?: KnowledgeStatus;
}

export type KnowledgeItemTableParams = KnowledgeItemQuery & {
  current?: number;
  pageSize?: number;
};

export interface ImportKnowledgeItemRow {
  file: File;
  name: string;
  knowledgeType: KnowledgeType;
  status: KnowledgeStatus;
}

export interface ImportKnowledgeItemsInput {
  rows: ImportKnowledgeItemRow[];
}

export interface KnowledgeImportFileResult {
  fileName: string;
  status: string;
  message?: string | null;
  knowledgeItemId?: KnowledgeItemId | null;
  knowledgeVersionId?: KnowledgeItemId | null;
  sourcePath?: string | null;
  stableCode?: string | null;
}

export interface KnowledgeImportSummary {
  totalCount: number;
  successCount: number;
  skippedCount: number;
  failedCount: number;
  files: KnowledgeImportFileResult[];
}
