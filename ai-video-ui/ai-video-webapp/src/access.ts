type AccessUser = {
  [key: string]: unknown;
  access?: string;
  permissions?: readonly string[];
};

type AccessInitialState = {
  currentUser?: AccessUser;
};

const PERMISSIONS = {
  assetDownload: 'aivideo:asset:download',
  assetQuery: 'aivideo:asset:query',
  assetUpload: 'aivideo:asset:upload',
  studioGenerate: 'aivideo:studio:generate',
  studioQuery: 'aivideo:studio:query',
  taskCancel: 'aivideo:task:cancel',
  taskQuery: 'aivideo:task:query',
} as const;

/** @see https://umijs.org/docs/max/access#access */
export default function access(initialState: AccessInitialState | undefined) {
  const { currentUser } = initialState ?? {};
  const permissions = new Set(currentUser?.permissions ?? []);
  const canStudioQuery = permissions.has(PERMISSIONS.studioQuery);

  return {
    canAdmin: currentUser?.access === 'admin',
    canStudioQuery,
    canWorkflowCreate:
      canStudioQuery && permissions.has(PERMISSIONS.studioGenerate),
    canTaskQuery: permissions.has(PERMISSIONS.taskQuery),
    canTaskCancel: permissions.has(PERMISSIONS.taskCancel),
    canAssetQuery: permissions.has(PERMISSIONS.assetQuery),
    canAssetUpload: permissions.has(PERMISSIONS.assetUpload),
    canAssetDownload: permissions.has(PERMISSIONS.assetDownload),
  };
}
