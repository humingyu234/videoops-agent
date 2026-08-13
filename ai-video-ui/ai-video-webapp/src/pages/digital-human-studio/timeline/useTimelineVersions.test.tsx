import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { useTimelineVersions } from './useTimelineVersions';

describe('useTimelineVersions', () => {
  it('keeps version fetching disabled without a project id', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useTimelineVersions({ listVersions: () => Promise.reject(new Error('must not fetch')) } as never), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> });
    expect(result.current.fetchStatus).toBe('idle');
  });
});
