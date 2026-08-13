import { history, useModel } from '@umijs/max';
import {
  App,
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Form,
  Input,
  Popconfirm,
  Result,
  Skeleton,
  Spin,
  Tag,
} from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { authApi } from '@/services/ai-video/auth/api';
import { authSession, beginLoginRedirect } from '@/services/ai-video/auth/session';
import type { AuthUser, SecuritySession } from '@/services/ai-video/auth/types';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';

const LOGIN_PATH = '/user/login';
const SECURITY_PATH = '/user/security';
const PASSWORD_CHANGE_FAILURE_MESSAGE = '修改密码失败，请检查当前密码后重试';
const RELOGIN_REQUIRED_MESSAGE = '登录状态已失效，请重新登录';
const PASSWORD_CHANGED_LOGIN_NOTICE = 'password-changed';
const SESSION_REVOKED_LOGIN_NOTICE = 'session-revoked';
const SESSION_LIST_LOAD_FAILURE_MESSAGE = '会话列表加载失败，请稍后重试';
const SESSION_REVOKE_FAILURE_MESSAGE = '撤销会话失败，请稍后重试';

type SessionListError = 'generic';

type PasswordChangeFormValues = {
  confirmPassword: string;
  currentPassword: string;
  newPassword: string;
};

function getCurrentRelativeLocation(): string {
  const { hash, pathname, search } = window.location;
  const redirect = `${pathname}${search}${hash}`;

  return redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : SECURITY_PATH;
}

function getAccountValue(user: AuthUser): string {
  return user.username?.trim() || '未设置';
}

function maskPhone(phone?: string): string {
  const normalizedPhone = phone?.trim();
  if (!normalizedPhone) {
    return '未设置';
  }

  if (normalizedPhone.length < 7) {
    return '已设置';
  }

  return `${normalizedPhone.slice(0, 3)}****${normalizedPhone.slice(-4)}`;
}

function maskEmail(email?: string): string {
  const normalizedEmail = email?.trim();
  if (!normalizedEmail) {
    return '未设置';
  }

  const atIndex = normalizedEmail.indexOf('@');
  if (atIndex <= 0 || atIndex === normalizedEmail.length - 1) {
    return '已设置';
  }

  return `${normalizedEmail.slice(0, 1)}***${normalizedEmail.slice(atIndex)}`;
}

function requiresReauthentication(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === 401 || error.code === 46131;
  }

  return getHttpStatus(error) === 401;
}

function isForbidden(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === 403;
  }

  return getHttpStatus(error) === 403;
}

function formatLastActiveAt(lastActiveAt: string): string {
  const timestamp = Date.parse(lastActiveAt);
  if (Number.isNaN(timestamp)) {
    return '暂无活动记录';
  }

  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(timestamp));
}

const SecurityPageContent: React.FC = () => {
  const { initialState, loading, setInitialState } = useModel('@@initialState');
  const { message } = App.useApp();
  const messageRef = useRef(message);
  messageRef.current = message;
  const redirectStarted = useRef(false);
  const passwordChangeInFlight = useRef(false);
  const sessionRevokeInFlight = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [sessions, setSessions] = useState<SecuritySession[]>([]);
  const [securityAccessDenied, setSecurityAccessDenied] = useState(false);
  const [sessionListError, setSessionListError] = useState<SessionListError>();
  const [sessionListLoading, setSessionListLoading] = useState(false);
  const [revokingSessionId, setRevokingSessionId] = useState<string>();
  const currentUser = initialState?.currentUser;
  const shouldRedirectToLogin = !loading && !currentUser;

  useEffect(() => {
    if (
      !shouldRedirectToLogin ||
      redirectStarted.current ||
      !beginLoginRedirect()
    ) {
      return;
    }

    redirectStarted.current = true;
    history.replace(
      `${LOGIN_PATH}?redirect=${encodeURIComponent(getCurrentRelativeLocation())}`,
    );
  }, [shouldRedirectToLogin]);

  const clearVerifiedUser = useCallback(() => {
    authSession.clear();
    setInitialState((state) => ({
      ...state,
      accessDenied: false,
      currentUser: undefined,
    }));
  }, [setInitialState]);

  const redirectForReauthentication = useCallback(() => {
    clearVerifiedUser();
    messageRef.current.error(RELOGIN_REQUIRED_MESSAGE);
    if (beginLoginRedirect()) {
      history.replace(LOGIN_PATH);
    }
  }, [clearVerifiedUser]);

  const loadSessions = useCallback(async () => {
    setSessionListLoading(true);
    setSessionListError(undefined);
    try {
      setSessions(await authApi.sessions());
    } catch (error) {
      if (requiresReauthentication(error)) {
        redirectForReauthentication();
        return;
      }

      if (isForbidden(error)) {
        setSecurityAccessDenied(true);
        return;
      }

      setSessionListError('generic');
    } finally {
      setSessionListLoading(false);
    }
  }, [redirectForReauthentication]);

  useEffect(() => {
    if (currentUser) {
      void loadSessions();
    }
  }, [currentUser, loadSessions]);

  const handlePasswordChange = async ({
    currentPassword,
    newPassword,
  }: PasswordChangeFormValues) => {
    if (passwordChangeInFlight.current) {
      return;
    }

    passwordChangeInFlight.current = true;
    setSubmitting(true);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      clearVerifiedUser();
      history.replace(`${LOGIN_PATH}?notice=${PASSWORD_CHANGED_LOGIN_NOTICE}`);
    } catch (error) {
      if (requiresReauthentication(error)) {
        redirectForReauthentication();
        return;
      }

      if (isForbidden(error)) {
        setSecurityAccessDenied(true);
        return;
      }

      message.error(PASSWORD_CHANGE_FAILURE_MESSAGE);
    } finally {
      passwordChangeInFlight.current = false;
      setSubmitting(false);
    }
  };

  const handleSessionRevocation = async (session: SecuritySession) => {
    if (sessionRevokeInFlight.current) {
      return;
    }

    sessionRevokeInFlight.current = true;
    setRevokingSessionId(session.id);
    try {
      await authApi.revokeSession(session.id);
      if (session.current) {
        clearVerifiedUser();
        history.replace(`${LOGIN_PATH}?notice=${SESSION_REVOKED_LOGIN_NOTICE}`);
        return;
      }

      await loadSessions();
    } catch (error) {
      if (requiresReauthentication(error)) {
        redirectForReauthentication();
        return;
      }

      if (isForbidden(error)) {
        setSecurityAccessDenied(true);
        return;
      }

      message.error(SESSION_REVOKE_FAILURE_MESSAGE);
    } finally {
      sessionRevokeInFlight.current = false;
      setRevokingSessionId(undefined);
    }
  };

  if (loading) {
    return (
      <main aria-busy="true" aria-label="登录状态验证中">
        <div role="status">
          <Spin description="正在验证登录状态…" />
        </div>
      </main>
    );
  }

  if (!currentUser) {
    return (
      <main aria-live="polite" aria-label="正在跳转登录页">
        <div role="status">
          <Spin description="正在跳转至登录页…" />
        </div>
      </main>
    );
  }

  if (securityAccessDenied) {
    return (
      <main aria-labelledby="security-access-denied-title">
        <Result
          status="403"
          subTitle="当前登录状态有效，但无权使用账号安全功能。"
          title={<span id="security-access-denied-title">访问受限</span>}
        />
      </main>
    );
  }

  return (
    <main aria-labelledby="security-page-title">
      <h1 id="security-page-title">账号安全</h1>
      {currentUser.passwordResetRequired && (
        <Alert
          description="当前账号已被要求修改密码，请完成修改后重新登录。"
          showIcon
          title="需要先修改密码"
          type="warning"
        />
      )}
      <section aria-labelledby="security-account-title">
        <Card title={<span id="security-account-title">当前安全信息</span>}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="账号">
              {getAccountValue(currentUser)}
            </Descriptions.Item>
            <Descriptions.Item label="手机">
              {maskPhone(currentUser.phone)}
            </Descriptions.Item>
            <Descriptions.Item label="邮箱">
              {maskEmail(currentUser.email)}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </section>
      <section aria-labelledby="security-sessions-title">
        <Card title={<span id="security-sessions-title">设备与会话</span>}>
          {sessionListError ? (
            <Alert
              action={
                <Button onClick={() => void loadSessions()} type="primary">
                  重新加载会话
                </Button>
              }
              description={SESSION_LIST_LOAD_FAILURE_MESSAGE}
              showIcon
              title="无法加载会话管理"
              type="error"
            />
          ) : (
            <div>
              {sessionListLoading ? (
                <div aria-busy="true" aria-label="正在加载登录会话" role="status">
                  <Skeleton active paragraph={{ rows: 2 }} title />
                </div>
              ) : sessions.length === 0 ? (
                <Empty description="当前没有可管理的登录会话" />
              ) : (
                <Flex gap="middle" vertical>
                  {sessions.map((session) => {
                    const isRevoking = revokingSessionId === session.id;
                    const actionLabel = session.current ? '退出此设备' : '撤销会话';
                    const confirmLabel = session.current ? '确认退出' : '确认撤销';
                    const confirmTitle = session.current
                      ? '确认退出当前设备的登录会话吗？'
                      : '确认撤销此设备的登录会话吗？';

                    return (
                      <Flex
                        align="center"
                        justify="space-between"
                        key={session.id}
                        wrap
                      >
                        <div>
                          <div>
                            {session.deviceName}
                            {session.current && <Tag color="success">当前设备</Tag>}
                          </div>
                          <div>客户端：{session.clientId}</div>
                          <div>最近活跃：{formatLastActiveAt(session.lastActiveAt)}</div>
                        </div>
                        <Popconfirm
                          cancelText="取消"
                          description="撤销后，该设备需要重新登录。"
                          okButtonProps={{ loading: isRevoking }}
                          okText={confirmLabel}
                          onConfirm={() => handleSessionRevocation(session)}
                          title={confirmTitle}
                        >
                          <Button
                            danger
                            disabled={Boolean(revokingSessionId)}
                            loading={isRevoking}
                            type="link"
                          >
                            {actionLabel}
                          </Button>
                        </Popconfirm>
                      </Flex>
                    );
                  })}
                </Flex>
              )}
            </div>
          )}
        </Card>
      </section>
      <section aria-labelledby="change-password-title">
        <Card title={<span id="change-password-title">修改密码</span>}>
          <Form<PasswordChangeFormValues>
            disabled={submitting}
            layout="vertical"
            onFinish={handlePasswordChange}
            scrollToFirstError={{ focus: true }}
          >
            <Form.Item
              label="当前密码"
              name="currentPassword"
              rules={[{ message: '请输入当前密码', required: true }]}
            >
              <Input.Password
                autoComplete="current-password"
                placeholder="请输入当前密码"
              />
            </Form.Item>
            <Form.Item
              label="新密码"
              name="newPassword"
              rules={[
                { message: '请输入新密码', required: true },
                {
                  validator: (_, value) => {
                    if (
                      !value ||
                      (typeof value === 'string' &&
                        value.length >= 8 &&
                        /[a-zA-Z]/.test(value) &&
                        /\d/.test(value))
                    ) {
                      return Promise.resolve();
                    }

                    return Promise.reject(
                      new Error('新密码至少 8 位，且必须同时包含字母和数字'),
                    );
                  },
                },
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                placeholder="至少 8 位，且包含字母和数字"
              />
            </Form.Item>
            <Form.Item
              dependencies={['newPassword']}
              label="确认新密码"
              name="confirmPassword"
              rules={[
                { message: '请再次输入新密码', required: true },
                ({ getFieldValue }) => ({
                  validator: (_, value) => {
                    if (!value || value === getFieldValue('newPassword')) {
                      return Promise.resolve();
                    }

                    return Promise.reject(
                      new Error('两次输入的新密码不一致'),
                    );
                  },
                }),
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                placeholder="请再次输入新密码"
              />
            </Form.Item>
            <Button
              disabled={submitting}
              htmlType="submit"
              loading={submitting}
              type="primary"
            >
              修改密码
            </Button>
          </Form>
        </Card>
      </section>
    </main>
  );
};

const SecurityPage: React.FC = () => (
  <App>
    <SecurityPageContent />
  </App>
);

export default SecurityPage;
