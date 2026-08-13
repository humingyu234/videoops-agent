import { app, shell, type BrowserWindow, type Session } from 'electron';

import {
  isAllowedMainNavigation,
  isSafeExternalUrl,
} from './securityPolicy';
import type { WebTarget } from './webUrlPolicy';

export function installSessionSecurity(session: Session): void {
  session.setPermissionCheckHandler(() => false);
  session.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });
}

export function installWindowSecurity(
  window: BrowserWindow,
  target: WebTarget,
  loadingPageUrl?: string,
): void {
  const blockUnexpectedNavigation = (
    event: Electron.Event,
    url: string,
  ): void => {
    if (!isAllowedMainNavigation(url, target, loadingPageUrl)) event.preventDefault();
  };

  window.webContents.on('will-navigate', blockUnexpectedNavigation);
  window.webContents.on('will-redirect', blockUnexpectedNavigation);
  window.webContents.setWindowOpenHandler(({ url }) => {
    if (isSafeExternalUrl(url)) {
      void shell.openExternal(url).catch(() => undefined);
    }
    return { action: 'deny' };
  });
}

export function installCertificateErrorHandler(): void {
  app.on(
    'certificate-error',
    (event, _webContents, _url, _error, _certificate, callback) => {
      event.preventDefault();
      callback(false);
    },
  );
}
