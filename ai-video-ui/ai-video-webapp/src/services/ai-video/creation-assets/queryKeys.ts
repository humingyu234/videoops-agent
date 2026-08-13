const root = ['creation-assets'] as const;

export const creationAssetQueryKeys = {
  root,
  list: (assetType?: string) => [...root, 'list', assetType ?? 'all'] as const,
  detail: (assetId: string) => [...root, 'detail', assetId] as const,
};
