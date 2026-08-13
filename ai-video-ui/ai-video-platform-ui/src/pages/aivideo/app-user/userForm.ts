import type { AppRevision, AppUserFormValues, UpdateAppUserInput } from '@/api/aivideo/identity/types';

function normalizeOptionalText(value?: string) {
  const normalized = value?.trim();
  return normalized || undefined;
}

function normalizeRevision(value?: AppRevision) {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }
  return normalizeOptionalText(value);
}

/**
 * 把编辑表单转换为安全的资料更新请求。
 *
 * 不存在的字段不会发到服务端，避免以脱敏展示值或空字符串覆盖真实联系方式。
 */
export function toAppUserUpdateInput(values: AppUserFormValues): UpdateAppUserInput | undefined {
  const displayName = normalizeOptionalText(values.displayName);
  const expectedIdentityRevision = normalizeRevision(values.expectedIdentityRevision);
  const phone = normalizeOptionalText(values.phone);
  const email = normalizeOptionalText(values.email);

  if (!values.id || !displayName || !expectedIdentityRevision) {
    return undefined;
  }
  if (phone?.includes('*') || email?.includes('*')) {
    return undefined;
  }
  if ((values.clearPhone && phone) || (values.clearEmail && email)) {
    return undefined;
  }

  return {
    ...(values.clearEmail ? { clearEmail: true } : {}),
    ...(values.clearPhone ? { clearPhone: true } : {}),
    displayName,
    ...(email ? { email } : {}),
    expectedIdentityRevision,
    ...(phone ? { phone } : {})
  };
}
