import test from "node:test";
import assert from "node:assert/strict";

import {
  AUTH_STORAGE_KEY,
  LEGACY_AUTH_STORAGE_KEYS,
  authSessionFromTokens,
  clearStoredAuthSession,
  markPasswordChanged,
  readStoredAuthSession,
  saveAuthSession,
} from "../../src/services/authSession.ts";
import type { AuthSession } from "../../src/types.ts";

class MemoryStorage {
  #values = new Map<string, string>();

  getItem(key: string): string | null {
    return this.#values.get(key) ?? null;
  }

  removeItem(key: string): void {
    this.#values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.#values.set(key, value);
  }
}

function useMemoryStorage(): MemoryStorage {
  const storage = new MemoryStorage();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: storage,
  });
  return storage;
}

function jwtExpiringAt(exp: number): string {
  return [
    Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url"),
    Buffer.from(JSON.stringify({ exp })).toString("base64url"),
    "signature",
  ].join(".");
}

function createSession(accessToken: string, refreshToken: string): AuthSession {
  return {
    accessToken,
    refreshToken,
    user: {
      id: "57f4de59-d060-4dc9-bb4a-a6bfd743b016",
      name: "Administrador",
      username: "admin",
      role: "ADMIN",
      changePassword: false,
    },
  };
}

test("adapts backend auth tokens to a session with backend station id and UI station mode", () => {
  const stationId = "a8efe295-8c1d-4d64-a198-a47a7e32dcf7";
  const accessToken = jwtExpiringAt(Math.floor(Date.now() / 1000) + 300);
  const refreshToken = jwtExpiringAt(Math.floor(Date.now() / 1000) + 600);

  const session = authSessionFromTokens({
    accessToken,
    refreshToken,
    tokenType: "Bearer",
    userId: "57f4de59-d060-4dc9-bb4a-a6bfd743b016",
    name: "Operador ETE",
    username: "operador",
    role: "OPERATOR",
    stationId,
    stationSlug: "novo-horizonte",
    stationName: "ETE Novo Horizonte",
    changePassword: true,
    operatorRecordWindowHours: 48,
  });

  assert.equal(session.accessToken, accessToken);
  assert.equal(session.refreshToken, refreshToken);
  assert.equal(session.user.stationId, stationId);
  assert.equal(session.user.stationMode, "novo-horizonte");
  assert.equal(session.user.operatorRecordWindowHours, 48);
  assert.equal(session.user.changePassword, true);
});

test("persists only the versioned real auth session and discards legacy mock sessions", () => {
  const storage = useMemoryStorage();
  const [legacyKey] = LEGACY_AUTH_STORAGE_KEYS;
  storage.setItem(legacyKey, JSON.stringify({ accessToken: "mock-access-usr-operator" }));

  assert.equal(readStoredAuthSession(), null);
  assert.equal(storage.getItem(legacyKey), null);

  const session = authSessionFromTokens({
    accessToken: jwtExpiringAt(Math.floor(Date.now() / 1000) + 300),
    refreshToken: jwtExpiringAt(Math.floor(Date.now() / 1000) + 600),
    tokenType: "Bearer",
    userId: "57f4de59-d060-4dc9-bb4a-a6bfd743b016",
    name: "Administrador",
    username: "admin",
    role: "ADMIN",
    stationId: null,
    stationSlug: null,
    stationName: null,
    changePassword: false,
  });

  saveAuthSession(session);
  assert.equal(readStoredAuthSession()?.accessToken, session.accessToken);
  assert.equal(readStoredAuthSession()?.refreshToken, session.refreshToken);
  assert.equal(readStoredAuthSession()?.user.id, session.user.id);

  clearStoredAuthSession();
  assert.equal(storage.getItem(AUTH_STORAGE_KEY), null);
});

test("marks the local session password as changed after a 204 change-password response", () => {
  useMemoryStorage();
  const session = authSessionFromTokens({
    accessToken: jwtExpiringAt(Math.floor(Date.now() / 1000) + 300),
    refreshToken: jwtExpiringAt(Math.floor(Date.now() / 1000) + 600),
    tokenType: "Bearer",
    userId: "57f4de59-d060-4dc9-bb4a-a6bfd743b016",
    name: "Operador ETE",
    username: "operador",
    role: "OPERATOR",
    stationId: "a8efe295-8c1d-4d64-a198-a47a7e32dcf7",
    stationSlug: "boa-vista",
    stationName: "ETE Boa Vista",
    changePassword: true,
  });

  saveAuthSession(session);
  const updatedSession = markPasswordChanged(session);

  assert.equal(updatedSession.user.changePassword, false);
  assert.equal(readStoredAuthSession()?.user.changePassword, false);
}
);

test("discards a stored session when the refresh token is expired", () => {
  const storage = useMemoryStorage();
  const expiredRefreshToken = jwtExpiringAt(Math.floor(Date.now() / 1000) - 60);
  const validAccessToken = jwtExpiringAt(Math.floor(Date.now() / 1000) + 300);

  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(createSession(validAccessToken, expiredRefreshToken)));

  assert.equal(readStoredAuthSession(), null);
  assert.equal(storage.getItem(AUTH_STORAGE_KEY), null);
});

test("discards a stored session when the refresh token is malformed", () => {
  const storage = useMemoryStorage();
  const validAccessToken = jwtExpiringAt(Math.floor(Date.now() / 1000) + 300);

  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(createSession(validAccessToken, "not-a-jwt")));

  assert.equal(readStoredAuthSession(), null);
  assert.equal(storage.getItem(AUTH_STORAGE_KEY), null);
});

test("keeps a stored session when only the access token is expired and the refresh token is valid", () => {
  const storage = useMemoryStorage();
  const expiredAccessToken = jwtExpiringAt(Math.floor(Date.now() / 1000) - 60);
  const validRefreshToken = jwtExpiringAt(Math.floor(Date.now() / 1000) + 300);
  const session = createSession(expiredAccessToken, validRefreshToken);

  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));

  assert.deepEqual(readStoredAuthSession(), session);
});
