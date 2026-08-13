import type { RuoYiAdapter } from './ruoyiAdapter';

export type BlobReadOptions = {
  range?: string;
  signal?: AbortSignal;
};

export type BlobAdapter = {
  read(url: string, options?: BlobReadOptions): Promise<Blob>;
};

function invalid(message: string): never {
  throw new Error(`Invalid binary response: ${message}`);
}

function isJsonType(contentType: string): boolean {
  const type = contentType.split(';', 1)[0]?.trim().toLowerCase();
  return type === 'application/json' || type?.endsWith('+json') === true;
}

function isRuoYiEnvelope(value: unknown): value is { code: number; msg: string; data: unknown } {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const record = value as Record<string, unknown>;
  return typeof record.code === 'number' && typeof record.msg === 'string' && Object.hasOwn(record, 'data');
}

export async function normalizeBinaryResponse(value: unknown): Promise<unknown> {
  if (!(value instanceof Blob)) return value;
  if (isJsonType(value.type)) {
    try {
      const envelope = JSON.parse(await value.text()) as unknown;
      if (isRuoYiEnvelope(envelope)) return envelope;
    } catch {
      // RuoYiAdapter turns the controlled invalid response below into ApiError.
    }
    return { code: 500, msg: 'Binary error response is invalid', data: null };
  }
  return { code: 200, msg: 'ok', data: value };
}

function assertSingleRange(range: string): void {
  if (!/^bytes=\d*-\d*$/.test(range) || range === 'bytes=-') {
    invalid('Range must contain one byte range');
  }
}

export function createBlobAdapter(adapter: Pick<RuoYiAdapter, 'request'>): BlobAdapter {
  return {
    async read(url, options = {}) {
      if (options.range) assertSingleRange(options.range);
      const value = await adapter.request<unknown>(url, {
        method: 'GET',
        responseType: 'blob',
        ...(options.range ? { headers: { Range: options.range } } : {}),
        ...(options.signal ? { signal: options.signal } : {}),
      });
      if (!(value instanceof Blob)) invalid('expected an authorized Blob');
      return value;
    },
  };
}
