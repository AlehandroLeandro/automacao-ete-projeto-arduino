import { useCallback, useEffect, useState } from "react";
import { drainQueue } from "./syncQueue";
import { enqueue, listByAuthor, listAll, remove, OFFLINE_QUEUE_CHANGE_EVENT, updateItem, type QueueItem, type QueueItemInput } from "./offlineQueue";
import { readStoredAuthSession } from "../services/authSession";
import { forgetAuthorIfDrained, rememberAuthor } from "./offlineAuth";

export function useOfflineQueue() {
  const [items, setItems] = useState<QueueItem[]>([]);
  const [isOffline, setIsOffline] = useState(() => typeof navigator !== "undefined" && !navigator.onLine);
  const refresh = useCallback(async () => { const userId = readStoredAuthSession()?.user.id; setItems(userId ? await listByAuthor(userId) : []); }, []);
  const syncNow = useCallback(async () => { await drainQueue(); await refresh(); }, [refresh]);
  useEffect(() => { void refresh(); const online = () => { setIsOffline(false); void syncNow(); }; const offline = () => setIsOffline(true); const channel = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel("ete-offline"); channel?.addEventListener("message", () => void refresh()); window.addEventListener(OFFLINE_QUEUE_CHANGE_EVENT, refresh); window.addEventListener("online", online); window.addEventListener("offline", offline); window.addEventListener("focus", () => void refresh()); return () => { channel?.close(); window.removeEventListener(OFFLINE_QUEUE_CHANGE_EVENT, refresh); window.removeEventListener("online", online); window.removeEventListener("offline", offline); }; }, [refresh, syncNow]);
  const retry = useCallback(async (localId: string) => { await updateItem(localId, { status: "pending", lastError: null }); await syncNow(); }, [syncNow]);
  const discard = useCallback(async (localId: string) => { const item = (await listAll()).find((candidate) => candidate.localId === localId); await remove(localId); if (item) await forgetAuthorIfDrained(item.authorUserId); await refresh(); }, [refresh]);
  const editPayload = useCallback(async (localId: string, patch: Record<string, unknown>) => { const item = (await listAll()).find((candidate) => candidate.localId === localId); if (item) await updateItem(localId, { payload: { ...item.payload, ...patch }, status: "pending", lastError: null }); await refresh(); }, [refresh]);
  return { items, pendingCount: items.filter((item) => item.status === "pending" || item.status === "syncing").length, failedCount: items.filter((item) => item.status === "failed").length, isOffline, retry, discard, editPayload, syncNow };
}

export async function enqueueOperational(input: QueueItemInput): Promise<QueueItem> {
  const session = readStoredAuthSession();
  if (session?.user.id === input.authorUserId) await rememberAuthor(session);
  const item = await enqueue(input);
  const serviceWorker = navigator.serviceWorker;
  void serviceWorker?.getRegistration().then((registration) => (registration as ServiceWorkerRegistration & { sync?: { register(tag: string): Promise<void> } } | undefined)?.sync?.register("ete-sync")).catch((error) => console.warn("[offline-queue] background sync unavailable", { error }));
  return item;
}
