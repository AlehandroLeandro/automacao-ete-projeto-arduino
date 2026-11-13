import test from "node:test";
import assert from "node:assert/strict";

import { ApiError, createApiClient } from "../../src/services/api.ts";
import type { AuthSession } from "../../src/types.ts";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}

function createSession(accessToken: string, refreshToken = "refresh-token"): AuthSession {
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

test("returns undefined for 204 No Content responses", async () => {
  const client = createApiClient({
    baseUrl: "https://api.example.test/api",
    fetch: async () => new Response(null, { status: 204 }),
  });

  const response = await client.request("/auth/change-password", {
    auth: "none",
    body: { currentPassword: "old", newPassword: "new", confirmPassword: "new" },
    method: "POST",
  });

  assert.equal(response, undefined);
});

test("throws ApiError using the backend ApiError.message value", async () => {
  const client = createApiClient({
    baseUrl: "https://api.example.test/api",
    fetch: async () => jsonResponse({ message: "Invalid credentials" }, 401),
  });

  await assert.rejects(
    client.request("/auth/login", { auth: "none", method: "POST" }),
    (error) => error instanceof ApiError && error.message === "Invalid credentials" && error.status === 401,
  );
});

test("retries a required request once after refreshing an expired access token", async () => {
  let session: AuthSession | null = createSession("expired-access-token");
  const requests: Array<{ authorization: string | null; body?: string; url: string }> = [];

  const client = createApiClient({
    baseUrl: "https://api.example.test/api",
    clearSession: () => {
      session = null;
    },
    fetch: async (input, init) => {
      const headers = new Headers(init?.headers);
      requests.push({
        authorization: headers.get("Authorization"),
        body: typeof init?.body === "string" ? init.body : undefined,
        url: String(input),
      });

      if (String(input).endsWith("/auth/refresh")) {
        return jsonResponse({
          accessToken: "fresh-access-token",
          refreshToken: "fresh-refresh-token",
          tokenType: "Bearer",
          userId: session?.user.id,
          name: session?.user.name,
          username: session?.user.username,
          role: session?.user.role,
          stationId: null,
          stationSlug: null,
          stationName: null,
          changePassword: false,
        });
      }

      if (requests.length === 1) {
        return jsonResponse({ message: "Invalid token" }, 401);
      }

      return jsonResponse({ ok: true });
    },
    readSession: () => session,
    saveSession: (nextSession) => {
      session = nextSession;
    },
  });

  const response = await client.request<{ ok: boolean }>("/secure", { auth: "required" });

  assert.deepEqual(response, { ok: true });
  assert.equal(requests.length, 3);
  assert.equal(requests[0].authorization, "Bearer expired-access-token");
  assert.equal(requests[1].url, "https://api.example.test/api/auth/refresh");
  assert.deepEqual(JSON.parse(requests[1].body ?? "{}"), { refreshToken: "refresh-token" });
  assert.equal(requests[2].authorization, "Bearer fresh-access-token");
  assert.equal(session?.accessToken, "fresh-access-token");
});

test("clears the session and redirects to login when refresh fails", async () => {
  let session: AuthSession | null = createSession("expired-access-token", "expired-refresh-token");
  let redirectedToLogin = false;

  const client = createApiClient({
    baseUrl: "https://api.example.test/api",
    clearSession: () => {
      session = null;
    },
    fetch: async (input) => {
      if (String(input).endsWith("/auth/refresh")) {
        return jsonResponse({ message: "Invalid refresh token" }, 401);
      }

      return jsonResponse({ message: "Invalid token" }, 401);
    },
    readSession: () => session,
    redirectToLogin: () => {
      redirectedToLogin = true;
    },
  });

  await assert.rejects(
    client.request("/secure", { auth: "required" }),
    (error) => error instanceof ApiError && error.status === 401,
  );

  assert.equal(session, null);
  assert.equal(redirectedToLogin, true);
});

test("deduplicates concurrent refreshes and retries each unauthorized request", async () => {
  let session: AuthSession | null = createSession("expired-access-token");
  let refreshRequests = 0;
  let secureRequests = 0;

  const client = createApiClient({
    baseUrl: "https://api.example.test/api",
    fetch: async (input, init) => {
      const headers = new Headers(init?.headers);

      if (String(input).endsWith("/auth/refresh")) {
        refreshRequests += 1;
        return jsonResponse({
          accessToken: "fresh-access-token",
          refreshToken: "fresh-refresh-token",
          tokenType: "Bearer",
          userId: session?.user.id,
          name: session?.user.name,
          username: session?.user.username,
          role: session?.user.role,
          stationId: null,
          stationSlug: null,
          stationName: null,
          changePassword: false,
        });
      }

      secureRequests += 1;

      if (headers.get("Authorization") === "Bearer expired-access-token") {
        return jsonResponse({ message: "Invalid token" }, 401);
      }

      return jsonResponse({ ok: true, request: secureRequests });
    },
    readSession: () => session,
    saveSession: (nextSession) => {
      session = nextSession;
    },
  });

  const responses = await Promise.all([
    client.request<{ ok: boolean }>("/secure-a", { auth: "required" }),
    client.request<{ ok: boolean }>("/secure-b", { auth: "required" }),
  ]);

  assert.deepEqual(responses.map((response) => response.ok), [true, true]);
  assert.equal(refreshRequests, 1);
  assert.equal(session?.accessToken, "fresh-access-token");
});
