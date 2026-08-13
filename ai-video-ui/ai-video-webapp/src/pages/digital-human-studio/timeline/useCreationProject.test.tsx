import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { useCreationProject } from './useCreationProject';

describe('useCreationProject', () => {
  it('does not create or fetch a project before a project id is available', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const api = { getProject: () => Promise.reject(new Error('must not fetch')) } as never;
    const { result } = renderHook(() => useCreationProject(api), { wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider> });
    expect(result.current.fetchStatus).toBe('idle');
  });
});
