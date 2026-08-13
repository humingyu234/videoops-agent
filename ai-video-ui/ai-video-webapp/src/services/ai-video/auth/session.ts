const ACCESS_TOKEN_STORAGE_KEY = 'ai-video.app.access-token';

type SaveAuthSessionInput = {
  accessToken: string;
  persistent: boolean;
};

type AuthSessionClearListener = () => void;

export type AuthSessionSnapshot = {
  accessToken: string;
  revision: number;
};

let inMemoryAccessToken: string | undefined;
let loginRedirectInProgress = false;
let sessionRevision = 0;
const clearListeners = new Set<AuthSessionClearListener>();

function getStorage(
  storageType: 'localStorage' | 'sessionStorage',
): Storage | undefined {
  if (typeof window === 'undefined') {
    return undefined;
  }

  try {
    return window[storageType];
  } catch {
    return undefined;
  }
}

function removeStoredAccessTokens(): void {
  getStorage('localStorage')?.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  getStorage('sessionStorage')?.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}

function getStoredAccessToken(): string | null | undefined {
  return (
    getStorage('sessionStorage')?.getItem(ACCESS_TOKEN_STORAGE_KEY) ??
    getStorage('localStorage')?.getItem(ACCESS_TOKEN_STORAGE_KEY)
  );
}

function notifySessionCleared(): void {
  for (const listener of [...clearListeners]) {
    listener();
  }
}

export const authSession = {
  clear(): void {
    sessionRevision += 1;
    inMemoryAccessToken = undefined;
    removeStoredAccessTokens();
    notifySessionCleared();
  },

  clearIfCurrent(snapshot: AuthSessionSnapshot): boolean {
    if (
      this.getAccessToken() !== snapshot.accessToken ||
      sessionRevision !== snapshot.revision
    ) {
      return false;
    }

    this.clear();
    return true;
  },

  getAccessToken(): string | undefined {
    const token = getStoredAccessToken() ?? inMemoryAccessToken;
    return token?.trim() || undefined;
  },

  getRevision(): number {
    return sessionRevision;
  },

  save({ accessToken, persistent }: SaveAuthSessionInput): void {
    const normalizedToken = accessToken.trim();
    if (!normalizedToken) {
      throw new Error('accessToken must not be empty');
    }

    sessionRevision += 1;
    inMemoryAccessToken = normalizedToken;
    removeStoredAccessTokens();
    getStorage(persistent ? 'localStorage' : 'sessionStorage')?.setItem(
      ACCESS_TOKEN_STORAGE_KEY,
      normalizedToken,
    );
    loginRedirectInProgress = false;
  },
};

export function subscribeToAuthSessionClear(
  listener: AuthSessionClearListener,
): () => void {
  clearListeners.add(listener);

  return () => {
    clearListeners.delete(listener);
  };
}

export function beginLoginRedirect(): boolean {
  if (loginRedirectInProgress) {
    return false;
  }

  loginRedirectInProgress = true;
  return true;
}

export function resetLoginRedirect(): void {
  loginRedirectInProgress = false;
}
