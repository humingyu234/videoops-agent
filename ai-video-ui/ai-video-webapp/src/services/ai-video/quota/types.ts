export const PERSONAL_QUOTA_UNIT = 'ai_text_credit' as const;

export interface PersonalQuotaAccount {
  availableBalance: string;
  lockedBalance: string;
  quotaUnit: typeof PERSONAL_QUOTA_UNIT;
  totalBalance: string;
  usedBalance: string;
}
