import { describe, expect, it } from 'vitest';
import access from './access';

describe('access', () => {
  it('should return canAdmin true when user has admin access', () => {
    const initialState = {
      currentUser: {
        userid: '1',
        name: 'Admin User',
        avatar: 'https://example.com/avatar.png',
        access: 'admin',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(true);
  });

  it('should return canAdmin false when user has non-admin access', () => {
    const initialState = {
      currentUser: {
        userid: '2',
        name: 'Regular User',
        avatar: 'https://example.com/avatar.png',
        access: 'user',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(false);
  });

  it('should return canAdmin false when user access is undefined', () => {
    const initialState = {
      currentUser: {
        userid: '3',
        name: 'Guest User',
        avatar: 'https://example.com/avatar.png',
      },
    };

    const result = access(initialState);

    expect(result.canAdmin).toBe(false);
  });

  it('should return canAdmin false when currentUser is undefined', () => {
    const initialState = {
      currentUser: undefined,
    };

    const result = access(initialState);

    expect(result.canAdmin).toBeFalsy();
  });

  it('should return canAdmin false when initialState is undefined', () => {
    const result = access(undefined);

    expect(result.canAdmin).toBeFalsy();
  });

  it('maps creator permissions to exact access flags', () => {
    const result = access({
      currentUser: {
        permissions: [
          'aivideo:studio:query',
          'aivideo:studio:generate',
          'aivideo:task:query',
          'aivideo:task:cancel',
          'aivideo:asset:query',
          'aivideo:asset:upload',
          'aivideo:asset:download',
        ],
      },
    });

    expect(result).toMatchObject({
      canStudioQuery: true,
      canWorkflowCreate: true,
      canTaskQuery: true,
      canTaskCancel: true,
      canAssetQuery: true,
      canAssetUpload: true,
      canAssetDownload: true,
    });
  });

  it('requires both query and generate permissions to create workflows', () => {
    expect(
      access({
        currentUser: { permissions: ['aivideo:studio:generate'] },
      }).canWorkflowCreate,
    ).toBe(false);
    expect(
      access({
        currentUser: { permissions: ['aivideo:studio:query'] },
      }).canWorkflowCreate,
    ).toBe(false);
  });

  it('denies creator capabilities when permissions are empty or absent', () => {
    const empty = access({ currentUser: { permissions: [] } });
    const missing = access({ currentUser: {} });

    expect(empty.canStudioQuery).toBe(false);
    expect(empty.canTaskQuery).toBe(false);
    expect(empty.canAssetUpload).toBe(false);
    expect(missing.canWorkflowCreate).toBe(false);
    expect(missing.canAssetDownload).toBe(false);
  });
});
