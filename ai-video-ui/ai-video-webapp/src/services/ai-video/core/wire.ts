type WireRecord = Record<string, unknown>;

const CANONICAL_DECIMAL_PATTERN = /^-?(?:0|[1-9]\d*)(?:\.\d+)?$/;
const SENSITIVE_WIRE_KEYS = new Set([
  'credential',
  'apikey',
  'baseurl',
  'workflowid',
  'webappid',
  'nodeid',
  'providerconfigid',
]);

function invalidWireResponse(message: string): Error {
  return new Error(`Invalid wire response: ${message}`);
}

export function assertRecord(value: unknown, field = 'value'): WireRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw invalidWireResponse(`${field} must be an object`);
  }

  return value as WireRecord;
}

export function readString(record: WireRecord, key: string): string {
  const value = record[key];
  if (typeof value !== 'string') {
    throw invalidWireResponse(`${key} must be a string`);
  }

  return value;
}

export function readOptionalString(
  record: WireRecord,
  key: string,
): string | undefined {
  const value = record[key];
  if (value === undefined) {
    return undefined;
  }
  if (typeof value !== 'string') {
    throw invalidWireResponse(`${key} must be a string when present`);
  }

  return value;
}

export function readPositiveInteger(record: WireRecord, key: string): number {
  const value = record[key];
  if (!Number.isSafeInteger(value) || (value as number) <= 0) {
    throw invalidWireResponse(`${key} must be a safe positive integer`);
  }

  return value as number;
}

export function readDecimalString(record: WireRecord, key: string): string {
  const value = record[key];
  if (typeof value !== 'string' || !CANONICAL_DECIMAL_PATTERN.test(value)) {
    throw invalidWireResponse(`${key} must be a canonical decimal string`);
  }

  return value;
}

export function readEnum<const T extends string>(
  record: WireRecord,
  key: string,
  allowedValues: readonly T[],
): T {
  const value = record[key];
  if (
    typeof value !== 'string' ||
    !allowedValues.includes(value as T)
  ) {
    throw invalidWireResponse(`${key} contains an unknown enum value`);
  }

  return value as T;
}

export function readArray<T>(
  record: WireRecord,
  key: string,
  readItem: (value: unknown, index: number) => T,
): T[] {
  const value = record[key];
  if (!Array.isArray(value)) {
    throw invalidWireResponse(`${key} must be an array`);
  }

  return value.map((item, index) => readItem(item, index));
}

export function assertExactKeys(
  record: WireRecord,
  expectedKeys: readonly string[],
): void {
  const actualKeys = Object.keys(record);
  const expectedKeySet = new Set(expectedKeys);
  const hasExactKeys =
    actualKeys.length === expectedKeySet.size &&
    actualKeys.every((key) => expectedKeySet.has(key));

  if (!hasExactKeys) {
    throw invalidWireResponse(
      `expected exact keys ${expectedKeys.join(', ')}`,
    );
  }
}

export function assertNoSensitiveWireKeys(value: unknown): void {
  const visited = new WeakSet<object>();

  function visit(current: unknown): void {
    if (typeof current !== 'object' || current === null) {
      return;
    }
    if (visited.has(current)) {
      return;
    }
    visited.add(current);

    if (Array.isArray(current)) {
      for (const item of current) {
        visit(item);
      }
      return;
    }

    for (const key of Object.keys(current)) {
      if (SENSITIVE_WIRE_KEYS.has(key.toLowerCase())) {
        throw invalidWireResponse(`sensitive key ${key} is forbidden`);
      }
      visit((current as WireRecord)[key]);
    }
  }

  visit(value);
}
