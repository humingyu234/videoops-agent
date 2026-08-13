import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import {
  PERSONAL_QUOTA_UNIT,
  type PersonalQuotaAccount,
} from './types';

export const PERSONAL_QUOTA_ACCOUNT_NOT_FOUND_CODE = 46135;

export interface QuotaApi {
  getPersonalAccount(): Promise<PersonalQuotaAccount>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isUnsignedIntegerString(value: unknown): value is string {
  return typeof value === 'string' && /^(0|[1-9]\d*)$/.test(value);
}

function parsePersonalQuotaAccount(value: unknown): PersonalQuotaAccount {
  if (
    !isRecord(value) ||
    value.quotaUnit !== PERSONAL_QUOTA_UNIT ||
    !isUnsignedIntegerString(value.availableBalance) ||
    !isUnsignedIntegerString(value.lockedBalance) ||
    !isUnsignedIntegerString(value.usedBalance) ||
    !isUnsignedIntegerString(value.totalBalance)
  ) {
    throw new Error('积分响应格式异常，请稍后重试。');
  }

  return {
    availableBalance: value.availableBalance,
    lockedBalance: value.lockedBalance,
    quotaUnit: value.quotaUnit,
    totalBalance: value.totalBalance,
    usedBalance: value.usedBalance,
  };
}

export function createQuotaApi(adapter: RuoYiAdapter): QuotaApi {
  return {
    async getPersonalAccount() {
      const response = await adapter.request<unknown>('/api/quota/account', {
        method: 'GET',
      });
      return parsePersonalQuotaAccount(response);
    },
  };
}

let runtimeQuotaApi: QuotaApi | undefined;

function getRuntimeQuotaApi(): QuotaApi {
  if (!runtimeQuotaApi) {
    runtimeQuotaApi = createQuotaApi(getRuntimeRuoYiAdapter());
  }
  return runtimeQuotaApi;
}

export const quotaApi: QuotaApi = {
  getPersonalAccount: () => getRuntimeQuotaApi().getPersonalAccount(),
};
