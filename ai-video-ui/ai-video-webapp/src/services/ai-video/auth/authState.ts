import { authSession } from './session';
import type { AuthUser } from './types';

export type AppAuthState = {
  accessDenied?: boolean;
  currentUser?: AuthUser;
  verificationFailed?: boolean;
};

export type ResolvedAppAuthState = {
  accessDenied: boolean;
  hasAccessToken: boolean;
  hasVerifiedUser: boolean;
  verificationFailed: boolean;
};

/**
 * Resolves the only states that can render a protected route. Every terminal
 * state is token-backed so stale initialState data cannot outlive a cleared
 * app session.
 */
export function resolveAppAuthState(
  state?: AppAuthState,
): ResolvedAppAuthState {
  const hasAccessToken = Boolean(authSession.getAccessToken());

  return {
    accessDenied: hasAccessToken && state?.accessDenied === true,
    hasAccessToken,
    hasVerifiedUser: hasAccessToken && Boolean(state?.currentUser),
    verificationFailed: hasAccessToken && state?.verificationFailed === true,
  };
}

/**
 * Preserves unrelated initial state while removing identity data that becomes
 * invalid as soon as the local app session is cleared.
 */
export function clearInvalidatedAppAuthState<State extends AppAuthState>(
  state: State | undefined,
): State | undefined {
  if (
    !state ||
    (!state.currentUser &&
      state.accessDenied !== true &&
      state.verificationFailed !== true)
  ) {
    return state;
  }

  return {
    ...state,
    accessDenied: false,
    currentUser: undefined,
    verificationFailed: false,
  };
}

export function retryAppAuthVerification(
  refresh?: () => Promise<unknown> | undefined,
): void {
  if (refresh) {
    void refresh();
    return;
  }

  window.location.reload();
}
