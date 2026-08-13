import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';

const { getPersonalAccount } = vi.hoisted(() => ({
  getPersonalAccount: vi.fn(),
}));

vi.mock('@/services/ai-video/quota/api', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/services/ai-video/quota/api')>();
  return {
    ...actual,
    quotaApi: { getPersonalAccount },
  };
});

import { usePersonalQuotaAccount } from './usePersonalQuotaAccount';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const account = {
  quotaUnit: 'ai_text_credit' as const,
  availableBalance: '8640',
  lockedBalance: '160',
  usedBalance: '0',
  totalBalance: '8800',
};

describe('usePersonalQuotaAccount', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not query before a verified user id is available', () => {
    const { result } = renderHook(() => usePersonalQuotaAccount(undefined), {
      wrapper: createWrapper(),
    });

    expect(result.current.state).toEqual({ status: 'loading' });
    expect(getPersonalAccount).not.toHaveBeenCalled();
  });

  it('loads the current personal account once without polling', async () => {
    getPersonalAccount.mockResolvedValue(account);
    const { result } = renderHook(
      () => usePersonalQuotaAccount('app-user-001'),
      { wrapper: createWrapper() },
    );

    await waitFor(() => {
      expect(result.current.state).toEqual({ account, status: 'success' });
    });
    expect(getPersonalAccount).toHaveBeenCalledTimes(1);
  });

  it.each([
    [46135, 'missing'],
    [403, 'forbidden'],
    [500, 'failed'],
  ] as const)('maps API code %i to the %s state', async (code, status) => {
    getPersonalAccount.mockRejectedValue(
      new ApiError({ code, msg: 'request failed' }),
    );
    const { result } = renderHook(
      () => usePersonalQuotaAccount('app-user-001'),
      { wrapper: createWrapper() },
    );

    await waitFor(() => {
      expect(result.current.state).toEqual({ status });
    });
    expect(getPersonalAccount).toHaveBeenCalledTimes(1);
  });

  it('retries only after the user explicitly requests it', async () => {
    getPersonalAccount
      .mockRejectedValueOnce(new ApiError({ code: 46135, msg: 'missing' }))
      .mockResolvedValueOnce(account);
    const { result } = renderHook(
      () => usePersonalQuotaAccount('app-user-001'),
      { wrapper: createWrapper() },
    );

    await waitFor(() => {
      expect(result.current.state).toEqual({ status: 'missing' });
    });
    expect(getPersonalAccount).toHaveBeenCalledTimes(1);

    act(() => result.current.retry());

    await waitFor(() => {
      expect(result.current.state).toEqual({ account, status: 'success' });
    });
    expect(getPersonalAccount).toHaveBeenCalledTimes(2);
  });
});
