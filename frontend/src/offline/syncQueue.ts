import { appConfig } from "../config/env";
import { forgetAuthorIfDrained, resolveAccessToken } from "./offlineAuth";
import { listAll, remapLocalId, remove, updateItem, type QueueItem } from "./offlineQueue";

export type ResultKind = "done" | "failed" | "retry";
export interface DrainResult { sent: number; failed: number; remaining: number }

export function classifyResult(status: number, method: string): ResultKind {
  if (status >= 200 && status < 300 || (status === 404 && method === "DELETE")) return "done";
  if (status >= 400 && status < 500) return "failed";
  return "retry";
}

export function buildRequest(item: QueueItem): { method: string; path: string; body?: Record<string, unknown> } {
  const id = item.targetId;
  const creationPayload = { ...item.payload, clientRequestId: item.clientRequestId };
  const stoppagePayload = { ...item.payload, offlineQueuedAt: item.queuedAt };
  if (item.domain === "ph") return { method: item.operation === "create" ? "POST" : item.operation === "update" ? "PUT" : "DELETE", path: item.operation === "create" ? "/ph-measurements" : `/ph-measurements/${id}`, body: item.operation === "create" ? creationPayload : item.payload };
  if (item.domain === "flow") return { method: item.operation === "create" ? "POST" : item.operation === "update" ? "PUT" : "DELETE", path: item.operation === "create" ? "/flow-measurements" : `/flow-measurements/${id}`, body: item.operation === "create" ? creationPayload : item.payload };
  if (item.domain === "lime") return { method: item.operation === "create" ? "POST" : item.operation === "update" ? "PUT" : "DELETE", path: item.operation === "create" ? "/lime-records" : `/lime-records/${id}`, body: item.operation === "create" ? creationPayload : item.payload };
  if (item.operation === "startStoppage") return { method: "POST", path: "/stoppages/start", body: creationPayload };
  if (item.operation === "finishStoppage") return { method: "POST", path: `/stoppages/${id}/finish`, body: item.payload };
  return { method: item.operation === "update" ? "PUT" : "DELETE", path: `/stoppages/${id}`, body: stoppagePayload };
}

let draining = false;
export async function drainQueue(fetchImpl: typeof fetch = fetch): Promise<DrainResult> {
  if (draining) return { sent: 0, failed: 0, remaining: await listAll().then((items) => items.length) };
  draining = true; let sent = 0; let failed = 0;
  try {
    for (;;) {
      const item = (await listAll()).find((candidate) => candidate.status === "pending"); if (!item) break;
      const request = buildRequest(item);
      await updateItem(item.localId, { status: "syncing", attempts: item.attempts + 1, lastAttemptAt: new Date().toISOString() });
      let token = await resolveAccessToken(item.authorUserId);
      if (!token) { await updateItem(item.localId, { status: "failed", lastError: { status: 401, message: "Sessão expirada — entre novamente para enviar" } }); failed++; continue; }
      let response: Response;
      try { response = await fetchImpl(`${appConfig.apiUrl}${request.path}`, { method: request.method, headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: request.body === undefined ? undefined : JSON.stringify(request.body) }); }
      catch { await updateItem(item.localId, { status: "pending" }); throw new Error("offline"); }
      if (response.status === 401) { token = await resolveAccessToken(item.authorUserId, true); if (!token) { await updateItem(item.localId, { status: "failed", lastError: { status: 401, message: "Sessão expirada — entre novamente para enviar" } }); failed++; continue; } response = await fetchImpl(`${appConfig.apiUrl}${request.path}`, { method: request.method, headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${token}` }, body: request.body === undefined ? undefined : JSON.stringify(request.body) }); }
      const kind = classifyResult(response.status, request.method);
      if (kind === "done") { const result = response.status === 204 ? null : await response.json().catch(() => null) as { id?: string } | null; if (item.operation === "startStoppage" && result?.id) await remapLocalId(item.localId, result.id); await remove(item.localId); await forgetAuthorIfDrained(item.authorUserId); sent++; continue; }
      if (kind === "failed") { const body = await response.json().catch(() => ({})) as { message?: string; detail?: string }; const message = body.message ?? body.detail ?? `Falha na API (${response.status}).`; await updateItem(item.localId, { status: "failed", lastError: { status: response.status, message } }); void reportFailure(item, token, message); failed++; continue; }
      await updateItem(item.localId, { status: "pending" }); throw new Error("retry");
    }
  } finally { draining = false; }
  return { sent, failed, remaining: (await listAll()).length };
}

async function reportFailure(item: QueueItem, token: string, reason: string): Promise<void> {
  await fetch(`${appConfig.apiUrl}/offline-queue/failures`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({ clientRequestId: item.clientRequestId, domain: item.domain, stationId: item.stationId, operation: item.operation, reason, occurredAt: new Date().toISOString() }),
  }).catch(() => undefined);
}
