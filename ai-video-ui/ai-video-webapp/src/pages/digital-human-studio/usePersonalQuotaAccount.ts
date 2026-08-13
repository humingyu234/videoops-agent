import { useQuery } from '@tanstack/react-query';
import { ApiError } from '@/services/ai-video/core/errors';
import {
  PERSONAL_QUOTA_ACCOUNT_NOT_FOUND_CODE,
  quotaApi,
} from '@/services/ai-video/quota/api';
import type { PersonalQuotaAccount } from '@/services/ai-video/quota/types';

export type PersonalQuotaQueryState =
  | { status: 'loading' }
  | { account: PersonalQuotaAccount; status: 'success' }
  | { status: 'missing' | 'forbidden' | 'failed' };

function resolveErrorStatus(
  error: unknown,
): 'missing' | 'forbidden' | 'failed' {
  if (error instanceof ApiError) {
    if (error.code === PERSONAL_QUOTA_ACCOUNT_NOT_FOUND_CODE) {
      return 'missing';
    }
    if (error.code === 403) {
      return 'forbidden';
    }
  }
  return 'failed';
}

export function usePersonalQuotaAccount(userId?: string): {
  retry: () => void;
  state: PersonalQuotaQueryState;
} {
  const query = useQuery({
    enabled: Boolean(userId),
    queryFn: quotaApi.getPersonalAccount,
    queryKey: ['personal-quota-account', userId],
    refetchOnWindowFocus: false,
    retry: false,
  });

  let state: PersonalQuotaQueryState = { status: 'loading' };
  if (query.data) {
    state = { account: query.data, status: 'success' };
  } else if (query.isError) {
    state = { status: resolveErrorStatus(query.error) };
  }

  return {
    retry: () => {
      void query.refetch();
    },
    state,
  };
}
