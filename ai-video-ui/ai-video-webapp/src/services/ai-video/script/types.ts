export type ScriptSourceType = 'manual_input' | 'manual_edit';

export interface UserScriptListItem {
  scriptId: string;
  displayTitle: string;
  currentVersionId: string;
  versionNo: number;
  versionCount: number;
  sourceType: ScriptSourceType;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  preview: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserScriptInput {
  displayTitle: string;
  scriptText: string;
  idempotencyKey: string;
}

export interface UserScriptEditInput extends UserScriptInput {
  parentVersionId: string;
  expectedScriptRevision: string;
}

export interface UserScriptListQuery {
  keyword?: string;
  orderByColumn?: 'updatedAt' | 'displayTitle';
  isAsc?: 'asc' | 'desc';
  pageNum?: number;
  pageSize?: number;
}

export interface UserScriptPage {
  rows: UserScriptListItem[];
  total: number;
}

export interface ScriptVersionSummary {
  versionId: string;
  parentVersionId?: string;
  versionNo: number;
  sourceType: ScriptSourceType;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  preview: string;
  createdAt: string;
}

export interface ScriptVersion
  extends Omit<ScriptVersionSummary, 'preview'> {
  scriptId: string;
  scriptText: string;
}

export interface UserScriptDetail {
  scriptId: string;
  displayTitle: string;
  scriptRevision: string;
  currentVersionId: string;
  createdAt: string;
  updatedAt: string;
  currentVersion: ScriptVersion;
  versions: ScriptVersionSummary[];
}

export interface UserScriptSaveResult {
  scriptId: string;
  currentVersionId: string;
  scriptRevision: string;
  versionNo: number;
  displayTitle: string;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  createdAt: string;
  reused: boolean;
}
