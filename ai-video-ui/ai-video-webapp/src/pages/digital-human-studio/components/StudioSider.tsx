import React from 'react';
import type { AuthUser } from '@/services/ai-video/auth/types';
import type { StudioRoute } from '../model';
import type { PersonalQuotaQueryState } from '../usePersonalQuotaAccount';
import StudioIcon, { type StudioIconName } from './StudioIcon';

export type StudioSidebarKey = StudioRoute | 'discover';

const items: Array<{
  key: StudioRoute;
  label: string;
  icon: StudioIconName;
}> = [
  { key: 'create', label: '创作', icon: 'plus' },
  { key: 'avatars', label: '形象', icon: 'user' },
  { key: 'voices', label: '声音', icon: 'volume' },
  { key: 'scripts', label: '文案', icon: 'text' },
  { key: 'works', label: '作品', icon: 'film' },
];

interface StudioSiderProps {
  activeKey?: StudioSidebarKey;
  collapsed: boolean;
  currentUser: AuthUser;
  quotaState: PersonalQuotaQueryState;
  onCollapsedChange: (value: boolean) => void;
  onDiscover: () => void;
  onRetryQuota: () => void;
  onRouteChange: (route: StudioRoute) => void;
}

function formatBalance(value: string): string {
  return BigInt(value).toLocaleString('zh-CN');
}

function getAvailablePercent(available: string, total: string): bigint {
  const totalValue = BigInt(total);
  if (totalValue <= 0n) {
    return 0n;
  }

  const percent = (BigInt(available) * 100n) / totalValue;
  if (percent < 0n) return 0n;
  if (percent > 100n) return 100n;
  return percent;
}

const QuotaPanel: React.FC<{
  quotaState: PersonalQuotaQueryState;
  onRetry: () => void;
}> = ({ quotaState, onRetry }) => {
  if (quotaState.status === 'missing' || quotaState.status === 'failed') {
    const message =
      quotaState.status === 'missing'
        ? '积分账户不存在，请联系管理员'
        : '积分加载失败';
    return (
      <div className="credits-box credits-error" aria-live="polite">
        <div className="credits-error-message">{message}</div>
        <button className="credits-retry" type="button" onClick={onRetry}>
          重试
        </button>
        <div className="credits-ring credits-ring-error" aria-hidden="true">
          <b>!</b>
        </div>
        <div className="credits-mini-label">积分异常</div>
      </div>
    );
  }

  if (quotaState.status === 'forbidden') {
    return (
      <div className="credits-box credits-error" aria-live="polite">
        <div className="credits-error-message">无权查看积分</div>
        <div className="credits-ring credits-ring-error" aria-hidden="true">
          <b>!</b>
        </div>
        <div className="credits-mini-label">无权查看</div>
      </div>
    );
  }

  const loading = quotaState.status === 'loading';
  const account =
    quotaState.status === 'success' ? quotaState.account : undefined;
  const total = account ? formatBalance(account.totalBalance) : '--';
  const available = account ? formatBalance(account.availableBalance) : '--';
  const used = account ? formatBalance(account.usedBalance) : '--';
  const percent = account
    ? getAvailablePercent(account.availableBalance, account.totalBalance)
    : undefined;
  const percentLabel = percent === undefined ? '--' : `${percent}%`;

  return (
    <div
      aria-busy={loading}
      aria-label={loading ? '个人积分加载中' : '个人积分'}
      className="credits-box"
      role="status"
    >
      <div className="credits-row">
        <span>总积分</span>
        <span className="credits-num">{total}</span>
      </div>
      <div className="credits-row">
        <span>可用积分</span>
        <span className="credits-left">{available}</span>
      </div>
      <div className="credits-bar">
        <i style={{ width: percent === undefined ? '0%' : `${percent}%` }} />
      </div>
      <div className="credits-meta">已用积分 {used}</div>
      <div className="credits-ring">
        <svg aria-hidden="true" viewBox="0 0 44 44">
          <circle cx="22" cy="22" r="18" pathLength="100" />
          <circle
            className="credits-ring-fill"
            cx="22"
            cy="22"
            r="18"
            pathLength="100"
            strokeDasharray="100"
            strokeDashoffset={
              percent === undefined ? '100' : (100n - percent).toString()
            }
          />
        </svg>
        <b>{percentLabel}</b>
      </div>
      <div className="credits-mini-label">可用 {available}</div>
    </div>
  );
};

const StudioSider: React.FC<StudioSiderProps> = ({
  activeKey,
  collapsed,
  currentUser,
  quotaState,
  onCollapsedChange,
  onDiscover,
  onRetryQuota,
  onRouteChange,
}) => {
  const displayName =
    currentUser.displayName?.trim() || currentUser.username?.trim() || '用户';
  const username = currentUser.username?.trim();
  const showUsername = Boolean(username && username !== displayName);
  const avatarInitial = Array.from(displayName)[0] ?? '用';

  return (
    <aside className="sidebar">
      <button
        aria-label={collapsed ? '展开菜单' : '收起菜单'}
        className="collapse-btn"
        type="button"
        onClick={() => onCollapsedChange(!collapsed)}
      >
        <StudioIcon name="left" />
      </button>
      <div className="brand">
        <div className="brand-mark">D</div>
        <div className="brand-text">
          <div className="brand-name">素造智能体</div>
          <div className="brand-sub">AI 数字人创作平台</div>
        </div>
      </div>
      <div className="sidebar-inner">
        <nav className="nav">
          <div className="nav-group-label">我的</div>
          {items.map((item) => (
            <button
              aria-current={activeKey === item.key ? 'page' : undefined}
              className={`nav-item ${activeKey === item.key ? 'active' : ''}`}
              key={item.key}
              title={collapsed ? item.label : undefined}
              type="button"
              onClick={() => onRouteChange(item.key)}
            >
              <StudioIcon name={item.icon} />
              <span>{item.label}</span>
            </button>
          ))}
          <div className="nav-group-label">探索</div>
          <button
            aria-current={activeKey === 'discover' ? 'page' : undefined}
            className={`nav-item ${activeKey === 'discover' ? 'active' : ''}`}
            title={collapsed ? '发现' : undefined}
            type="button"
            onClick={onDiscover}
          >
            <StudioIcon name="app" />
            <span>发现</span>
          </button>
        </nav>
        <div className="sidebar-foot">
          <QuotaPanel quotaState={quotaState} onRetry={onRetryQuota} />
          <div className="user-row">
            <div
              className="avatar"
              aria-label={`${displayName}的头像`}
              role="img"
            >
              {currentUser.avatarUrl ? (
                <img alt="" src={currentUser.avatarUrl} />
              ) : (
                avatarInitial
              )}
            </div>
            <div className="user-info">
              <div className="user-name">{displayName}</div>
              {showUsername && <div className="user-account">{username}</div>}
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
};

export default StudioSider;
