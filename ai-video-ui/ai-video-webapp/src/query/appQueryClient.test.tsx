import { useQueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { authSession } from '@/services/ai-video/auth/session';
import { AppQueryProvider, appQueryClient } from './appQueryClient';

function QueryClientProbe({
  onClient,
}: {
  onClient: (client: unknown) => void;
}) {
  onClient(useQueryClient());
  return null;
}

describe('app query client', () => {
  beforeEach(() => {
    appQueryClient.clear();
  });

  it('provides the same application QueryClient from every provider instance', () => {
    const clients: unknown[] = [];

    render(
      <>
        <AppQueryProvider>
          <QueryClientProbe onClient={(client) => clients.push(client)} />
        </AppQueryProvider>
        <AppQueryProvider>
          <QueryClientProbe onClient={(client) => clients.push(client)} />
        </AppQueryProvider>
      </>,
    );

    expect(clients).toEqual([appQueryClient, appQueryClient]);
    expect(new Set(clients).size).toBe(1);
  });

  it('uses bounded query retries and never retries mutations by default', () => {
    const defaults = appQueryClient.getDefaultOptions();

    expect(defaults.queries).toMatchObject({
      retry: 1,
      refetchOnWindowFocus: false,
    });
    expect(defaults.mutations).toMatchObject({ retry: 0 });
  });

  it('clears all query cache entries when the auth session is cleared', () => {
    appQueryClient.setQueryData(['creator', 'profile'], { id: 'creator-1' });
    appQueryClient.setQueryData(['discovery', 'templates'], [{ id: 'tpl-1' }]);

    expect(appQueryClient.getQueryCache().getAll()).toHaveLength(2);

    authSession.clear();

    expect(appQueryClient.getQueryCache().getAll()).toHaveLength(0);
  });
});
