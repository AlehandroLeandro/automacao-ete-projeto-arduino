import type { AuthSession, AuthTokensResponse, OperationalStationId } from "../types";

export const AUTH_STORAGE_KEY = "ccl.auth.v2";
export const LEGACY_AUTH_STORAGE_KEYS = ["ccl.auth.v1"] as const;

type AuthSessionListener = (session: AuthSession | null) => void;

const listeners = new Set<AuthSessionListener>();

export function authSessionFromTokens(response: AuthTokensResponse): AuthSession {
  const stationId = response.stationId ?? undefined;
  const stationName = response.stationName ?? undefined;

  return {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    user: {
      id: response.userId,
      name: response.name,
      username: response.username,
      role: response.role,
      changePassword: response.changePassword,
      stationId,
      stationName,
      stationMode: stationModeFromSlug(response.stationSlug ?? undefined),
      operatorRecordWindowHours: response.operatorRecordWindowHours,
    },
  };
}

export function readStoredAuthSession(): AuthSession | null {
  const storage = getStorage();

  if (!storage) {
    return null;
  }

  clearLegacySessions(storage);

  const storedValue = storage.getItem(AUTH_STORAGE_KEY);

  if (!storedValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(storedValue) as AuthSession;

    if (!isValidAuthSession(parsed)) {
      storage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }

    return parsed;
  } catch {
    storage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function saveAuthSession(session: AuthSession): void {
  const storage = getStorage();

  if (storage) {
    storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
    clearLegacySessions(storage);
  }

  notifyAuthSessionListeners(session);
  if (typeof indexedDB !== "undefined") {
    void import("../offline/offlineAuth").then(({ rememberAuthor }) => rememberAuthor(session)).catch(() => undefined);
  }
}

export function clearStoredAuthSession(): void {
  const storage = getStorage();

  if (storage) {
    storage.removeItem(AUTH_STORAGE_KEY);
    clearLegacySessions(storage);
  }

  notifyAuthSessionListeners(null);
}

export function markPasswordChanged(session: AuthSession): AuthSession {
  const updatedSession: AuthSession = {
    ...session,
    user: {
      ...session.user,
      changePassword: false,
    },
  };

  saveAuthSession(updatedSession);
  return updatedSession;
}

export function subscribeAuthSession(listener: AuthSessionListener): () => void {
  listeners.add(listener);

  return () => {
    listeners.delete(listener);
  };
}

function notifyAuthSessionListeners(session: AuthSession | null): void {
  listeners.forEach((listener) => listener(session));
}

function clearLegacySessions(storage: Storage): void {
  LEGACY_AUTH_STORAGE_KEYS.forEach((key) => {
    storage.removeItem(key);
  });
}

function getStorage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

function isValidAuthSession(value: AuthSession): boolean {
  return Boolean(
    value?.accessToken &&
      value.refreshToken &&
      value.user?.id &&
      value.user.name &&
      value.user.username &&
      value.user.role &&
      !isExpiredJwt(value.refreshToken),
  );
}

function isExpiredJwt(token: string): boolean {
  const payload = decodeJwtPayload(token);

  if (typeof payload?.exp !== "number") {
    return true;
  }

  return payload.exp * 1000 <= Date.now();
}

function decodeJwtPayload(token: string): { exp?: unknown } | null {
  const [, encodedPayload] = token.split(".");

  if (!encodedPayload) {
    return null;
  }

  try {
    return JSON.parse(atob(toBase64(encodedPayload))) as { exp?: unknown };
  } catch {
    return null;
  }
}

function toBase64(value: string): string {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const paddingLength = (4 - (base64.length % 4)) % 4;
  return base64 + "=".repeat(paddingLength);
}

function stationModeFromSlug(stationSlug: string | undefined): OperationalStationId | undefined {
  if (!stationSlug) {
    return undefined;
  }

  const normalizedSlug = normalizeStationSlug(stationSlug);

  if (normalizedSlug === "boa-vista") {
    return "boa-vista";
  }

  if (normalizedSlug === "novo-horizonte") {
    return "novo-horizonte";
  }

  return undefined;
}

function normalizeStationSlug(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/^ete-/, "");
}
