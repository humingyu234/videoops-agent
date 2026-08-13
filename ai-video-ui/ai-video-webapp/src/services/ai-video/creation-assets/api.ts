import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createBlobAdapter, type BlobAdapter, type BlobReadOptions } from '../core/blobAdapter';
import { parseCreationAssetWire } from './adapter';
import type { CreationAsset, CreationAssetPage } from './types';

export type CreationAssetListInput = {
  assetType?: CreationAsset['assetType'];
  pageNum: number;
  pageSize: number;
};

export type CreationAssetUploadInput = {
  usageIntent: string;
  idempotencyKey: string;
};

export interface CreationAssetsApi {
  list(input: CreationAssetListInput): Promise<CreationAssetPage>;
  upload(file: File, input: CreationAssetUploadInput): Promise<CreationAsset>;
  detail(assetId: string): Promise<CreationAsset>;
  content(assetId: string, options?: BlobReadOptions): Promise<Blob>;
  delete(assetId: string): Promise<void>;
}

function assetPath(assetId?: string): string {
  const root = '/api/studio/creation-assets';
  return assetId ? `${root}/${encodeURIComponent(assetId)}` : root;
}

function parseAssetPage(value: unknown): CreationAssetPage {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Invalid wire response: creation asset page must be an object');
  }
  const record = value as Record<string, unknown>;
  if (!Number.isSafeInteger(record.total) || (record.total as number) < 0 || !Array.isArray(record.rows)) {
    throw new Error('Invalid wire response: creation asset page is invalid');
  }
  return { total: record.total as number, rows: record.rows.map(parseCreationAssetWire) };
}

export function createCreationAssetsApi(
  adapter: RuoYiAdapter,
  blobs: BlobAdapter = createBlobAdapter(adapter),
): CreationAssetsApi {
  return {
    async list(input) {
      const query = new URLSearchParams({ status: 'ready', pageNum: String(input.pageNum), pageSize: String(input.pageSize) });
      if (input.assetType) query.set('assetType', input.assetType);
      return parseAssetPage(await adapter.request<unknown>(`${assetPath()}?${query.toString()}`, { method: 'GET' }));
    },
    async upload(file, input) {
      const data = new FormData();
      data.append('file', file);
      data.append('usageIntent', input.usageIntent);
      data.append('idempotencyKey', input.idempotencyKey);
      return parseCreationAssetWire(await adapter.request<unknown>(assetPath(), { method: 'POST', data }));
    },
    async detail(assetId) {
      return parseCreationAssetWire(await adapter.request<unknown>(assetPath(assetId), { method: 'GET' }));
    },
    content: (assetId, options) => blobs.read(`${assetPath(assetId)}/content`, options),
    delete: (assetId) => adapter.request<void>(assetPath(assetId), { method: 'DELETE' }),
  };
}
