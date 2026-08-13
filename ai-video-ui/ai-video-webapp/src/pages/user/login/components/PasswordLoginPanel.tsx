import { Button, Form, Input } from 'antd';
import { useState } from 'react';
import { authApi } from '@/services/ai-video/auth/api';
import type { LoginResult } from '@/services/ai-video/auth/types';
import styles from '../index.module.css';

type Values = {
  identifier: string;
  password: string;
};

type Authenticate = (request: () => Promise<LoginResult>) => Promise<void>;

export function PasswordLoginPanel({
  authenticate,
  submitting,
}: {
  authenticate: Authenticate;
  submitting: boolean;
}) {
  const [passwordVisible, setPasswordVisible] = useState(false);

  const submit = ({ identifier, password }: Values) => {
    const normalizedIdentifier = identifier?.trim();
    if (!normalizedIdentifier || !password) return;

    void authenticate(() =>
      authApi.login({ identifier: normalizedIdentifier, password }),
    );
  };

  return (
    <Form<Values>
      classNames={{ root: styles.loginForm }}
      disabled={submitting}
      layout="vertical"
      onFinish={submit}
      requiredMark={false}
      scrollToFirstError={{ focus: true }}
    >
      <Form.Item
        className={styles.field}
        label="账号"
        name="identifier"
        rules={[
          { message: '请输入用户名、手机号或邮箱。', required: true },
          { message: '账号不能为空白。', whitespace: true },
        ]}
      >
        <Input
          autoComplete="username"
          className={styles.input}
          placeholder="手机号 / 邮箱"
          prefix={
            <svg aria-hidden="true" fill="none" viewBox="0 0 24 24">
              <circle
                cx="12"
                cy="8"
                r="4"
                stroke="currentColor"
                strokeWidth="1.7"
              />
              <path
                d="M4 20c0-4 3.5-7 8-7s8 3 8 7"
                stroke="currentColor"
                strokeLinecap="round"
                strokeWidth="1.7"
              />
            </svg>
          }
        />
      </Form.Item>
      <Form.Item
        className={styles.field}
        label="密码"
        name="password"
        rules={[{ message: '请输入密码。', required: true }]}
      >
        <Input
          autoComplete="current-password"
          className={styles.input}
          placeholder="请输入密码"
          prefix={
            <svg aria-hidden="true" fill="none" viewBox="0 0 24 24">
              <rect
                height="10"
                rx="2.5"
                stroke="currentColor"
                strokeWidth="1.7"
                width="14"
                x="5"
                y="10"
              />
              <path
                d="M8 10V7a4 4 0 0 1 8 0v3"
                stroke="currentColor"
                strokeLinecap="round"
                strokeWidth="1.7"
              />
            </svg>
          }
          suffix={
            <button
              aria-label={passwordVisible ? '隐藏密码' : '显示密码'}
              className={styles.passwordToggle}
              onClick={() => setPasswordVisible((visible) => !visible)}
              type="button"
            >
              <svg aria-hidden="true" fill="none" viewBox="0 0 24 24">
                <path
                  d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"
                  stroke="currentColor"
                  strokeWidth="1.7"
                />
                <circle
                  cx="12"
                  cy="12"
                  r="3"
                  stroke="currentColor"
                  strokeWidth="1.7"
                />
              </svg>
            </button>
          }
          type={passwordVisible ? 'text' : 'password'}
        />
      </Form.Item>
      <Button
        autoInsertSpace={false}
        block
        classNames={{ root: styles.submitButton }}
        disabled={submitting}
        htmlType="submit"
        loading={submitting}
        type="primary"
      >
        登录
      </Button>
    </Form>
  );
}
