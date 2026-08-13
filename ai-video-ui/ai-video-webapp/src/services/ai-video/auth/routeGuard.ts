import { history } from '@umijs/max';
import { beginLoginRedirect } from './session';

export const LOGIN_PATH = '/user/login';

const publicIdentityPaths = new Set([
  LOGIN_PATH,
  '/user/password-reset',
]);

export type AppLocation = Pick<
  typeof history.location,
  'pathname' | 'search' | 'hash'
>;

export function isPublicIdentityPath(pathname: string): boolean {
  return publicIdentityPaths.has(pathname);
}

export function getLoginRedirectPath(location: AppLocation): string {
  return `${LOGIN_PATH}?redirect=${encodeURIComponent(
    location.pathname + location.search + location.hash,
  )}`;
}

export function redirectToLogin(location: AppLocation = history.location): boolean {
  if (isPublicIdentityPath(location.pathname) || !beginLoginRedirect()) {
    return false;
  }

  history.replace(getLoginRedirectPath(location));
  return true;
}
