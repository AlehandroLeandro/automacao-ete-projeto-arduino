import type { AuthSession } from "../types";
import { appConfig } from "../config/env";
import { openOfflineDb, requestResult, transactionDone } from "./offlineDb";
import { countPending } from "./offlineQueue";

interface AuthorCredential { userId: string; name: string; accessToken: string; refreshToken: string; updatedAt: string }

export async function rememberAuthor(session: AuthSession): Promise<void> {
  const db = await openOfflineDb(); const tx = db.transaction("authors", "readwrite");
  tx.objectStore("authors").put({ userId: session.user.id, name: session.user.name, accessToken: session.accessToken, refreshToken: session.refreshToken, updatedAt: new Date().toISOString() });
  await transactionDone(tx); db.close();
}

export async function resolveAccessToken(authorUserId: string, forceRefresh = false): Promise<string | null> {
  const credential = await read(authorUserId); if (!credential) return null;
  if (!forceRefresh && !jwtExpired(credential.accessToken)) return credential.accessToken;
  try {
    const response = await fetch(`${appConfig.apiUrl}/auth/refresh`, { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify({ refreshToken: credential.refreshToken }) });
    if (!response.ok) throw new Error("Sessão expirada — entre novamente para enviar");
    const tokens = await response.json() as { accessToken: string; refreshToken: string };
    await write({ ...credential, ...tokens, updatedAt: new Date().toISOString() }); return tokens.accessToken;
  } catch { return null; }
}

export async function forgetAuthorIfDrained(authorUserId: string): Promise<void> {
  if (await countPending(authorUserId) !== 0) return;
  const db = await openOfflineDb(); const tx = db.transaction("authors", "readwrite"); tx.objectStore("authors").delete(authorUserId); await transactionDone(tx); db.close();
}

async function read(userId: string): Promise<AuthorCredential | undefined> { const db = await openOfflineDb(); try { return await requestResult(db.transaction("authors").objectStore("authors").get(userId)); } finally { db.close(); } }
async function write(value: AuthorCredential): Promise<void> { const db = await openOfflineDb(); const tx = db.transaction("authors", "readwrite"); tx.objectStore("authors").put(value); await transactionDone(tx); db.close(); }
function jwtExpired(token: string): boolean { try { const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))); return typeof payload.exp !== "number" || payload.exp * 1000 <= Date.now() + 30_000; } catch { return true; } }
