import { history, Link } from '@umijs/max';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { createStyles } from 'antd-style';
import React, { useEffect, useRef, useState } from 'react';
import { Footer } from '@/components';
import { authApi } from '@/services/ai-video/auth/api';
import type {
  VerificationChannel,
} from '@/services/ai-video/auth/types';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';

const LOGIN_PATH = '/user/login';
const RESEND_COOLDOWN_SECONDS = 60;
const CREDENTIALS_FAILURE_MESSAGE =
  '验证码或账号信息不正确，请重新获取验证码后重试';
const REQUEST_FAILURE_MESSAGE = '验证码暂时无法发送，请稍后重试';
const CLIENT_UNAVAILABLE_MESSAGE = '当前登录客户端不可用，请稍后重试';
const FORBIDDEN_MESSAGE = '当前客户端无权使用找回密码功能，请联系管理员。';
const NETWORK_FAILURE_MESSAGE = '网络连接不可用，请检查网络后重试';

type PasswordResetFormValues = {
  confirmPassword: string;
  newPassword: string;
  target: string;
  verificationCode: string;
};

type RecoveryFeedback = {
  description?: React.ReactNode;
  title: string;
  type: 'error' | 'info' | 'warning';
};

type RecoveryChallenge = {
  channel: VerificationChannel;
  challengeId: string;
  expiresIn: number;
  maskedTarget: string;
};

const useStyles = createStyles(({ token }) => ({
  channelActions: {
    border: 0,
    display: 'flex',
    gap: token.marginSM,
    marginBottom: token.marginLG,
    minWidth: 0,
    padding: 0,
  },
  container: {
    display: 'flex',
    flexDirection: 'column',
    minHeight: '100vh',
    padding: token.paddingLG,
  },
  content: {
    alignItems: 'center',
    display: 'flex',
    flex: 1,
    justifyContent: 'center',
  },
  form: {
    maxWidth: 400,
    width: '100%',
  },
  feedback: {
    marginBottom: token.marginLG,
  },
  loginLink: {
    marginTop: token.marginLG,
  },
}));

function getUtf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).length;
}

function isForbidden(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === 403;
  }

  return getHttpStatus(error) === 403;
}

function isClientUnavailable(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === 46130;
  }

  return false;
}

function isCredentialsFailure(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === 401 || error.code === 46128 || error.code === 46129;
  }

  return getHttpStatus(error) === 401;
}

function getTargetLabel(channel: VerificationChannel): string {
  return channel === 'PHONE' ? '手机号' : '邮箱';
}

function getTargetPlaceholder(channel: VerificationChannel): string {
  return channel === 'PHONE' ? '请输入已绑定的手机号' : '请输入已绑定的邮箱';
}

function getExpiresInText(expiresIn: number): string {
  const minutes = Math.max(1, Math.ceil(expiresIn / 60));
  return `${minutes} 分钟`;
}

function isValidTarget(channel: VerificationChannel, target: string): boolean {
  if (channel === 'PHONE') {
    return /^1\d{10}$/.test(target);
  }

  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(target);
}

function getRequestFailureFeedback(error: unknown): RecoveryFeedback {
  if (isForbidden(error)) {
    return {
      description: FORBIDDEN_MESSAGE,
      title: '访问受限',
      type: 'warning',
    };
  }

  if (isClientUnavailable(error)) {
    return {
      title: CLIENT_UNAVAILABLE_MESSAGE,
      type: 'warning',
    };
  }

  return {
    title: REQUEST_FAILURE_MESSAGE,
    type: 'error',
  };
}

function getResetFailureFeedback(error: unknown): RecoveryFeedback {
  if (isForbidden(error)) {
    return {
      description: FORBIDDEN_MESSAGE,
      title: '访问受限',
      type: 'warning',
    };
  }

  if (isClientUnavailable(error)) {
    return {
      title: CLIENT_UNAVAILABLE_MESSAGE,
      type: 'warning',
    };
  }

  if (isCredentialsFailure(error)) {
    return {
      title: CREDENTIALS_FAILURE_MESSAGE,
      type: 'error',
    };
  }

  return {
    title: NETWORK_FAILURE_MESSAGE,
    type: 'error',
  };
}

const PasswordResetPage: React.FC = () => {
  const [form] = Form.useForm<PasswordResetFormValues>();
  const { styles } = useStyles();
  const [channel, setChannel] = useState<VerificationChannel>('PHONE');
  const [challenge, setChallenge] = useState<RecoveryChallenge>();
  const [feedback, setFeedback] = useState<RecoveryFeedback>();
  const [requestingCode, setRequestingCode] = useState(false);
  const [resendSeconds, setResendSeconds] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const requestInFlight = useRef(false);
  const resetInFlight = useRef(false);

  useEffect(() => {
    if (resendSeconds <= 0) {
      return undefined;
    }

    const timer = window.setInterval(() => {
      setResendSeconds((seconds) => Math.max(0, seconds - 1));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [resendSeconds]);

  const invalidateChallenge = () => {
    setChallenge(undefined);
    setResendSeconds(0);
  };

  const changeChannel = (nextChannel: VerificationChannel) => {
    if (nextChannel === channel || requestingCode || submitting) {
      return;
    }

    setChannel(nextChannel);
    invalidateChallenge();
    setFeedback(undefined);
    form.setFieldsValue({ target: '', verificationCode: '' });
  };

  const requestVerificationCode = async () => {
    if (requestInFlight.current || resetInFlight.current || resendSeconds > 0) {
      return;
    }

    let target: string;
    try {
      const values = await form.validateFields(['target']);
      target = values.target.trim();
    } catch {
      return;
    }

    requestInFlight.current = true;
    setRequestingCode(true);
    setFeedback(undefined);
    invalidateChallenge();
    try {
      const response = await authApi.requestVerificationCode({
        channel,
        scenario: 'PASSWORD_RECOVERY',
        target,
      });
      const nextChallenge: RecoveryChallenge = {
        channel,
        challengeId: response.challenge_id.trim(),
        expiresIn: response.expires_in,
        maskedTarget: response.masked_target.trim(),
      };
      if (
        !nextChallenge.challengeId ||
        !nextChallenge.maskedTarget ||
        !Number.isFinite(nextChallenge.expiresIn) ||
        nextChallenge.expiresIn <= 0
      ) {
        throw new Error('验证码响应格式异常');
      }

      setChallenge(nextChallenge);
      setFeedback({
        description: (
          <>
            接收地址：<strong>{nextChallenge.maskedTarget}</strong>。请在
            {getExpiresInText(nextChallenge.expiresIn)}内输入验证码；为保护账号安全，系统不会披露账号状态。
          </>
        ),
        title: '验证码请求已提交',
        type: 'info',
      });
      setResendSeconds(RESEND_COOLDOWN_SECONDS);
    } catch (error) {
      setFeedback(getRequestFailureFeedback(error));
    } finally {
      requestInFlight.current = false;
      setRequestingCode(false);
    }
  };

  const resetPassword = async (values: PasswordResetFormValues) => {
    if (resetInFlight.current || requestInFlight.current) {
      return;
    }

    if (!challenge || challenge.channel !== channel) {
      setFeedback({
        description: '请先获取验证码，再提交新密码。',
        title: '请先获取验证码',
        type: 'warning',
      });
      return;
    }

    resetInFlight.current = true;
    setSubmitting(true);
    setFeedback(undefined);
    try {
      await authApi.resetPassword({
        challengeId: challenge.challengeId,
        newPassword: values.newPassword,
        verificationCode: values.verificationCode.trim(),
      });
      history.replace(LOGIN_PATH);
    } catch (error) {
      setFeedback(getResetFailureFeedback(error));
    } finally {
      resetInFlight.current = false;
      setSubmitting(false);
    }
  };

  const targetLabel = getTargetLabel(channel);
  const canRequestCode = !requestingCode && !submitting && resendSeconds === 0;

  return (
    <main className={styles.container}>
      <div className={styles.content}>
        <section aria-labelledby="password-reset-title" className={styles.form}>
          <Typography.Title id="password-reset-title" level={1}>
            找回密码
          </Typography.Title>
          <Typography.Paragraph type="secondary">
            通过已绑定的手机号或邮箱验证身份后，设置新的登录密码。
          </Typography.Paragraph>
          {feedback && (
            <Alert
              className={styles.feedback}
              description={feedback.description}
              showIcon
              title={feedback.title}
              type={feedback.type}
            />
          )}
          <fieldset
            aria-label="找回密码方式"
            className={styles.channelActions}
          >
            <Button
              aria-pressed={channel === 'PHONE'}
              disabled={requestingCode || submitting}
              onClick={() => changeChannel('PHONE')}
              type={channel === 'PHONE' ? 'primary' : 'default'}
            >
              手机找回
            </Button>
            <Button
              aria-pressed={channel === 'EMAIL'}
              disabled={requestingCode || submitting}
              onClick={() => changeChannel('EMAIL')}
              type={channel === 'EMAIL' ? 'primary' : 'default'}
            >
              邮箱找回
            </Button>
          </fieldset>
          <Form<PasswordResetFormValues>
            form={form}
            layout="vertical"
            onFinish={resetPassword}
            onValuesChange={(changedValues) => {
              if (Object.hasOwn(changedValues, 'target')) {
                invalidateChallenge();
                setFeedback(undefined);
              }
            }}
            scrollToFirstError={{ focus: true }}
          >
            <Form.Item
              label={targetLabel}
              name="target"
              rules={[
                { message: `请输入${targetLabel}`, required: true },
                {
                  validator: (_, value) => {
                    const target = typeof value === 'string' ? value.trim() : '';
                    if (!target || isValidTarget(channel, target)) {
                      return Promise.resolve();
                    }

                    return Promise.reject(
                      new Error(
                        channel === 'PHONE'
                          ? '请输入正确的 11 位手机号'
                          : '请输入正确的邮箱地址',
                      ),
                    );
                  },
                },
              ]}
            >
              <Input
                autoComplete={channel === 'PHONE' ? 'tel' : 'email'}
                disabled={requestingCode || submitting}
                inputMode={channel === 'PHONE' ? 'numeric' : 'email'}
                placeholder={getTargetPlaceholder(channel)}
              />
            </Form.Item>
            <Form.Item>
              <Button
                block
                disabled={!canRequestCode}
                loading={requestingCode}
                onClick={() => void requestVerificationCode()}
              >
                {resendSeconds > 0 ? `${resendSeconds} 秒后重试` : '获取验证码'}
              </Button>
            </Form.Item>
            <Form.Item
              label="验证码"
              name="verificationCode"
              rules={[
                { message: '请输入验证码', required: true },
                { message: '验证码为 6 位数字', pattern: /^\d{6}$/ },
              ]}
            >
              <Input
                autoComplete="one-time-code"
                disabled={submitting}
                inputMode="numeric"
                maxLength={6}
                placeholder="请输入 6 位验证码"
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
                        getUtf8ByteLength(value) <= 72 &&
                        /[a-zA-Z]/.test(value) &&
                        /\d/.test(value))
                    ) {
                      return Promise.resolve();
                    }

                    return Promise.reject(
                      new Error('新密码至少 8 位，且必须同时包含字母和数字，最多 72 个 UTF-8 字节'),
                    );
                  },
                },
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                disabled={submitting}
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

                    return Promise.reject(new Error('两次输入的新密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password
                autoComplete="new-password"
                disabled={submitting}
                placeholder="请再次输入新密码"
              />
            </Form.Item>
            <Button
              block
              disabled={submitting || requestingCode}
              htmlType="submit"
              loading={submitting}
              type="primary"
            >
              重置密码
            </Button>
          </Form>
          <p className={styles.loginLink}>
            想起密码了？<Link to={LOGIN_PATH}>返回登录</Link>
          </p>
        </section>
      </div>
      <Footer />
    </main>
  );
};

export default PasswordResetPage;
