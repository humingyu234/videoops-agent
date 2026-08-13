import type { RequestConfig } from '@umijs/max';
import { message } from 'antd';
import {
  ApiError,
  getErrorMessage,
  getHttpStatus,
  isAbortError,
} from './services/ai-video/core/errors';

function getOfflineMessage(): string {
  return '网络不可用，请检查网络连接后重试。';
}

/**
 * JSON business responses and app-session invalidation are both owned by the
 * RuoYi adapter. It freezes the token and session revision before dispatching
 * a request, so an old response can never invalidate a replacement session.
 * This global fallback intentionally never changes app authentication state.
 */
export const errorConfig: RequestConfig = {
  errorConfig: {
    errorHandler: (error, options) => {
      if (options?.skipErrorHandler) {
        throw error;
      }

      if (isAbortError(error)) {
        return;
      }

      if (error instanceof ApiError) {
        if (error.code === 403) {
          throw error;
        }
        message.error(error.msg);
        return;
      }

      if (typeof navigator !== 'undefined' && !navigator.onLine) {
        message.error(getOfflineMessage());
        return;
      }

      const status = getHttpStatus(error);
      if (status === 403) {
        throw new ApiError({
          code: 403,
          msg: getErrorMessage(error, '权限不足。'),
          status,
        });
      }

      message.error(
        status
          ? `请求失败（${status}）`
          : getErrorMessage(error, '请求失败。'),
      );
    },
  },
};
