export interface AuthWorkspace {
  id: string;
  name: string;
  roleCode?: string;
}

export interface AuthUser {
  avatarUrl?: string;
  displayName?: string;
  email?: string;
  id: string;
  nickname?: string;
  passwordResetRequired?: boolean;
  permissions?: string[];
  phone?: string;
  roles?: string[];
  username?: string;
  workspace?: AuthWorkspace;
}

export interface LoginRequest {
  captchaCode?: string;
  captchaId?: string;
  identifier: string;
  password: string;
}

export interface LoginResult {
  access_token: string;
  client_id: string;
  currentWorkspace?: AuthWorkspace;
  expire_in: number;
}

export interface CodeLoginRequest {
  challengeId: string;
  verificationCode: string;
}

export interface SocialLoginRequest {
  authorizationCode: string;
  provider: string;
  state: string;
}

export interface MiniProgramLoginRequest {
  authorizationCode: string;
}

export type VerificationChannel = 'EMAIL' | 'PHONE';

export type VerificationScenario = 'LOGIN' | 'PASSWORD_RECOVERY';

export interface VerificationCodeRequest {
  channel: VerificationChannel;
  scenario: VerificationScenario;
  target: string;
}

export interface VerificationChallenge {
  challenge_id: string;
  expires_in: number;
  masked_target: string;
}

export interface PasswordResetRequest {
  challengeId: string;
  newPassword: string;
  verificationCode: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface SecuritySession {
  clientId: string;
  current: boolean;
  deviceName: string;
  id: string;
  lastActiveAt: string;
}
