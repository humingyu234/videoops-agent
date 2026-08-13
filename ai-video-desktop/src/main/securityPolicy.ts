import type { WebTarget } from './webUrlPolicy';
import { isLoopbackHost } from './webUrlPolicy';

function parseUrl(raw: string): URL | undefined {
  try {
    return new URL(raw);
  } catch {
    return undefined;
  }
}

export function isAllowedMainNavigation(
  raw: string,
  target: WebTarget,
  loadingPageUrl?: string,
): boolean {
  if (loadingPageUrl && raw === loadingPageUrl) return true;

  const url = parseUrl(raw);
  return Boolean(
    url &&
      !url.username &&
      !url.password &&
      url.origin === target.origin,
  );
}

export function isSafeExternalUrl(raw: string): boolean {
  const url = parseUrl(raw);
  return Boolean(
    url &&
      url.protocol === 'https:' &&
      !url.username &&
      !url.password,
  );
}

function isAllowedDownloadUrl(raw: string, target: WebTarget): boolean {
  const url = parseUrl(raw);
  if (!url || url.username || url.password) return false;

  if (url.protocol === 'https:') return true;
  if (url.protocol === 'blob:') return url.origin === target.origin;

  return (
    target.mode === 'development' &&
    url.protocol === 'http:' &&
    isLoopbackHost(url.hostname)
  );
}

export function isAllowedDownloadChain(
  chain: readonly string[],
  target: WebTarget,
): boolean {
  return (
    chain.length > 0 && chain.every((url) => isAllowedDownloadUrl(url, target))
  );
}
