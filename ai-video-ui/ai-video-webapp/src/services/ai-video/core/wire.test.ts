import { describe, expect, it } from 'vitest';
import {
  assertExactKeys,
  assertNoSensitiveWireKeys,
  assertRecord,
  readArray,
  readDecimalString,
  readEnum,
  readOptionalString,
  readPositiveInteger,
  readString,
} from './wire';

describe('wire parsers', () => {
  it('accepts records while rejecting null and arrays', () => {
    expect(assertRecord({ id: '1' }, 'payload')).toEqual({ id: '1' });
    expect(() => assertRecord(null, 'payload')).toThrowError(
      'Invalid wire response: payload must be an object',
    );
    expect(() => assertRecord([], 'payload')).toThrowError(
      'Invalid wire response: payload must be an object',
    );
  });

  it('reads required and optional strings without treating null as absent', () => {
    const record = { required: 'value', optional: undefined, nullable: null };

    expect(readString(record, 'required')).toBe('value');
    expect(readOptionalString(record, 'optional')).toBeUndefined();
    expect(() => readString(record, 'nullable')).toThrowError(
      'Invalid wire response: nullable must be a string',
    );
    expect(() => readOptionalString(record, 'nullable')).toThrowError(
      'Invalid wire response: nullable must be a string when present',
    );
  });

  it('accepts only safe positive integers', () => {
    expect(readPositiveInteger({ count: 1 }, 'count')).toBe(1);
    expect(readPositiveInteger({ count: Number.MAX_SAFE_INTEGER }, 'count')).toBe(
      Number.MAX_SAFE_INTEGER,
    );

    for (const invalid of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, '1', null]) {
      expect(() => readPositiveInteger({ count: invalid }, 'count')).toThrowError(
        'Invalid wire response: count must be a safe positive integer',
      );
    }
  });

  it.each(['0', '-0', '12', '-12', '0.25', '-0.25', '12.50'])(
    'accepts the canonical decimal string %s',
    (value) => {
      expect(readDecimalString({ amount: value }, 'amount')).toBe(value);
    },
  );

  it.each([
    '+1',
    '1e3',
    '01',
    '-01',
    '.5',
    '1.',
    '',
    ' 1',
    '1 ',
    1,
    null,
  ])('rejects the non-canonical decimal value %j', (value) => {
    expect(() => readDecimalString({ amount: value }, 'amount')).toThrowError(
      'Invalid wire response: amount must be a canonical decimal string',
    );
  });

  it('rejects unknown enum values', () => {
    const allowed = ['queued', 'running'] as const;

    expect(readEnum({ status: 'queued' }, 'status', allowed)).toBe('queued');
    expect(() => readEnum({ status: 'finished' }, 'status', allowed)).toThrowError(
      'Invalid wire response: status contains an unknown enum value',
    );
    expect(() => readEnum({ status: null }, 'status', allowed)).toThrowError(
      'Invalid wire response: status contains an unknown enum value',
    );
  });

  it('reads arrays through an item parser and rejects null', () => {
    expect(
      readArray({ rows: [{ id: '1' }, { id: '2' }] }, 'rows', (item, index) =>
        readString(assertRecord(item, `rows[${index}]`), 'id'),
      ),
    ).toEqual(['1', '2']);
    expect(() => readArray({ rows: null }, 'rows', (item) => item)).toThrowError(
      'Invalid wire response: rows must be an array',
    );
  });

  it('rejects missing and extra response fields', () => {
    expect(() => assertExactKeys({ id: '1' }, ['id', 'status'])).toThrowError(
      'Invalid wire response: expected exact keys id, status',
    );
    expect(() =>
      assertExactKeys({ id: '1', status: 'queued', extra: true }, ['id', 'status']),
    ).toThrowError('Invalid wire response: expected exact keys id, status');
    expect(() => assertExactKeys({ status: 'queued', id: '1' }, ['id', 'status'])).not.toThrow();
  });

  it.each([
    'credential',
    'apiKey',
    'baseUrl',
    'workflowId',
    'webappId',
    'nodeId',
    'providerConfigId',
  ])('rejects the sensitive response key %s case-insensitively at any depth', (key) => {
    const mixedCaseKey = key
      .split('')
      .map((character, index) =>
        index % 2 === 0 ? character.toUpperCase() : character.toLowerCase(),
      )
      .join('');

    expect(() =>
      assertNoSensitiveWireKeys({ rows: [{ nested: { [mixedCaseKey]: 'secret-value' } }] }),
    ).toThrowError(`Invalid wire response: sensitive key ${mixedCaseKey} is forbidden`);
  });

  it('does not scan user text values or reject partial key names', () => {
    expect(() =>
      assertNoSensitiveWireKeys({
        description:
          'User text may mention credential, apiKey, baseUrl, workflowId, webappId, nodeId, and providerConfigId.',
        apiKeyHint: 'safe display text',
      }),
    ).not.toThrow();
  });

  it('never includes raw response values in parser errors', () => {
    expect(() => readString({ name: { leaked: 'secret-value' } }, 'name')).toThrowError(
      'Invalid wire response: name must be a string',
    );

    try {
      readString({ name: { leaked: 'secret-value' } }, 'name');
    } catch (error) {
      expect(String(error)).not.toContain('secret-value');
    }
  });
});
