import { Tag } from 'antd';
import type {
  AppActorType,
  AppIdentityStatus,
  AppKickoutReasonCode,
  AppRoleScopeType
} from '@/api/aivideo/identity/types';

const statusMeta: Record<AppIdentityStatus, { color: string; label: string }> = {
  active: { color: 'green', label: '启用' },
  disabled: { color: 'red', label: '停用' },
  inactive: { color: 'default', label: '未启用' }
};

export const appIdentityStatusOptions = (Object.entries(statusMeta) as Array<
  [AppIdentityStatus, (typeof statusMeta)[AppIdentityStatus]]
>).map(([value, meta]) => ({ label: meta.label, value }));

export const appRoleScopeOptions: Array<{ label: string; value: AppRoleScopeType }> = [
  { label: '个人', value: 'personal' },
  { label: '组织', value: 'organization' }
];

export const appActorTypeOptions: Array<{ label: string; value: AppActorType }> = [
  { label: '创作端用户', value: 'app_user' },
  { label: '运营端用户', value: 'sys_user' }
];

export const appKickoutReasonOptions: Array<{ label: string; value: AppKickoutReasonCode }> = [
  { label: '运营人员主动下线', value: 'admin_kickout' },
  { label: '安全风险下线', value: 'admin_kickout_security' },
  { label: '策略违规下线', value: 'admin_kickout_policy' },
  { label: '客服支持下线', value: 'admin_kickout_support' }
];

export function AppIdentityStatusTag({ status }: { status?: AppIdentityStatus | null }) {
  if (!status || !statusMeta[status]) {
    return <span>-</span>;
  }
  const meta = statusMeta[status];
  return <Tag color={meta.color}>{meta.label}</Tag>;
}

export function getAppRoleScopeLabel(scopeType?: AppRoleScopeType | null) {
  return appRoleScopeOptions.find(option => option.value === scopeType)?.label || '-';
}
