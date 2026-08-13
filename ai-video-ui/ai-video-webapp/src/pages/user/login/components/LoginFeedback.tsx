import { useEffect, useState } from 'react';
import styles from '../index.module.css';

export type LoginFailure =
  | 'client-unavailable'
  | 'credentials'
  | 'network'
  | 'session-verification';

export type LoginNotice = 'password-changed' | 'session-revoked';

const FAILURE_COPY: Record<LoginFailure, string> = {
  'client-unavailable': '登录客户端不可用，请确认当前客户端可用后再重试。',
  credentials: '账号或凭据不正确',
  network: '网络连接不可用，请检查网络后重试。',
  'session-verification': '登录状态验证失败，请稍后重试。',
};

const NOTICE_COPY: Record<LoginNotice, string> = {
  'password-changed': '密码修改成功，请使用新密码重新登录',
  'session-revoked': '当前设备的登录会话已退出，请重新登录。',
};

type LoginFeedbackProps = {
  failure?: LoginFailure;
  notice?: LoginNotice;
};

export function LoginFeedback({ failure, notice }: LoginFeedbackProps) {
  const [visible, setVisible] = useState(Boolean(failure || notice));

  useEffect(() => {
    if (!failure && !notice) {
      setVisible(false);
      return undefined;
    }

    setVisible(true);
    const timer = window.setTimeout(() => setVisible(false), 2_600);

    return () => window.clearTimeout(timer);
  }, [failure, notice]);

  if (!visible) {
    return null;
  }

  return (
    <div className={styles.feedbackStack}>
      {notice && (
        <div className={styles.toast} role="status">
          {NOTICE_COPY[notice]}
        </div>
      )}
      {failure && (
        <div className={styles.toast} role="alert">
          {FAILURE_COPY[failure]}
        </div>
      )}
    </div>
  );
}
