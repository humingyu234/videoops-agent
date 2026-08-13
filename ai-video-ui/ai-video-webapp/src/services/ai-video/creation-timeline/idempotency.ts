export type IdempotencyIntent = string;

export type IdempotencyKeyStore = {
  forIntent(intent: IdempotencyIntent): string;
  retryUnknown(intent: IdempotencyIntent): string;
  beginNewIntent(intent: IdempotencyIntent): string;
  forget(intent: IdempotencyIntent): void;
};

export function createIdempotencyKeyStore(
  createKey: () => string = () => crypto.randomUUID(),
): IdempotencyKeyStore {
  const keys = new Map<IdempotencyIntent, string>();
  const issue = (intent: IdempotencyIntent): string => {
    const key = createKey();
    if (!key) throw new Error('Idempotency key generator returned an empty key');
    keys.set(intent, key);
    return key;
  };

  return {
    forIntent: (intent) => keys.get(intent) ?? issue(intent),
    retryUnknown: (intent) => keys.get(intent) ?? issue(intent),
    beginNewIntent: issue,
    forget: (intent) => { keys.delete(intent); },
  };
}
