type ApiErrorOptions = {
  code: number;
  msg: string;
  data?: unknown;
  status?: number;
};

export class ApiError extends Error {
  readonly code: number;
  readonly data: unknown;
  readonly msg: string;
  readonly status: number | undefined;

  constructor({ code, msg, data, status }: ApiErrorOptions) {
    super(msg);
    this.name = 'ApiError';
    this.code = code;
    this.data = data;
    this.msg = msg;
    this.status = status;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function getStringProperty(
  value: Record<string, unknown>,
  property: string,
): string | undefined {
  const candidate = value[property];
  return typeof candidate === 'string' ? candidate : undefined;
}

export function getHttpStatus(error: unknown): number | undefined {
  if (!isRecord(error)) {
    return undefined;
  }

  const response = error.response;
  if (isRecord(response) && typeof response.status === 'number') {
    return response.status;
  }

  return typeof error.status === 'number' ? error.status : undefined;
}

export function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  if (isRecord(error)) {
    return getStringProperty(error, 'message') ?? fallback;
  }

  return fallback;
}

export function isAbortError(error: unknown): boolean {
  if (error instanceof Error && error.name === 'AbortError') {
    return true;
  }

  if (!isRecord(error)) {
    return false;
  }

  return (
    getStringProperty(error, 'name') === 'AbortError' ||
    getStringProperty(error, 'code') === 'ERR_CANCELED'
  );
}
