import type { DecimalId } from '../creation-timeline/types';

export type CreationAsset = {
  assetId: DecimalId;
  originalName: string;
  mimeType: string;
  sha256: string;
  assetType: 'image' | 'video' | 'audio';
  usageOrigin: 'upload' | 'digital_human_output' | 'timeline_render_output';
  status: 'pending' | 'ready' | 'failed';
  sizeBytes: string;
  durationMs: number | null;
  width: number | null;
  height: number | null;
  hasVideoStream: boolean;
  hasAudioStream: boolean;
  createdAt: string;
};

export type CreationAssetPage = { total: number; rows: CreationAsset[] };
