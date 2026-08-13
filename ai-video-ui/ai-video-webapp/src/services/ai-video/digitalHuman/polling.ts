import { ApiError, getHttpStatus, isAbortError } from '../core/errors';
import { isSessionInvalidationCode } from '../core/ruoyiAdapter';

export const DIGITAL_HUMAN_POLL_INTERVAL_MS = 1500;
export const DIGITAL_HUMAN_POLL_MAX_RETRIES = 3;

export function getDigitalHumanPollRetryDelay(retryNumber: number): number {
  return DIGITAL_HUMAN_POLL_INTERVAL_MS * 2 ** Math.max(0, retryNumber - 1);
}

export function shouldStopDigitalHumanPolling(error: unknown): boolean {
  if (isAbortError(error)) return true;
  const code = error instanceof ApiError ? error.code : getHttpStatus(error);
  return (
    code === 403 ||
    (typeof code === 'number' && isSessionInvalidationCode(code))
  );
}
