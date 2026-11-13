import { openOfflineDb, requestResult, transactionDone, uuid } from "./offlineDb";

export const OFFLINE_QUEUE_CHANGE_EVENT = "ete-offline-change";

export type QueueDomain = "ph" | "flow" | "lime" | "stoppage";
export type QueueOperation = "create" | "update" | "delete" | "startStoppage" | "finishStoppage";
export type QueueStatus = "pending" | "syncing" | "failed";
export interface QueueError { status: number; message: string }
export interface QueueItem {
  localId: string; seq: number; domain: QueueDomain; operation: QueueOperation;
  clientRequestId: string; authorUserId: string; authorName: string; stationId: string;
  targetId: string | null; payload: Record<string, unknown>; eventAt: string; queuedAt: string;
  status: QueueStatus; attempts: number; lastAttemptAt: string | null; lastError: QueueError | null;
}
export type QueueItemInput = Omit<QueueItem, "localId" | "seq" | "clientRequestId" | "status" | "attempts" | "lastAttemptAt" | "lastError"> & { clientRequestId?: string };

async function all(): Promise<QueueItem[]> {
  const db = await openOfflineDb();
  try { return await requestResult(db.transaction("queue").objectStore("queue").getAll()); } finally { db.close(); }
}

export async function enqueue(input: QueueItemInput): Promise<QueueItem> {
  const db = await openOfflineDb();
  const tx = db.transaction(["queue", "meta"], "readwrite");
  const queue = tx.objectStore("queue");
  const count = await requestResult(queue.index("authorUserId").count(input.authorUserId));
  if (count >= 500) { tx.abort(); db.close(); throw new Error("Limite de registros offline atingido — conecte-se para enviar os pendentes"); }
  const meta = tx.objectStore("meta");
  const current = await requestResult(meta.get("seq")) as { key: string; value: number } | undefined;
  const seq = current?.value ?? 0;
  const item: QueueItem = { ...input, localId: uuid(), seq, clientRequestId: input.clientRequestId ?? uuid(), status: "pending", attempts: 0, lastAttemptAt: null, lastError: null };
  queue.add(item);
  meta.put({ key: "seq", value: seq + 1 });
  await transactionDone(tx); db.close();
  notifyQueueChanged();
  return item;
}

export async function listAll(): Promise<QueueItem[]> { return (await all()).sort((a, b) => a.seq - b.seq); }
export async function listByAuthor(authorUserId: string): Promise<QueueItem[]> { return (await listAll()).filter((item) => item.authorUserId === authorUserId); }
export async function nextPending(): Promise<QueueItem | null> { return (await listAll()).find((item) => item.status === "pending") ?? null; }
export async function updateItem(localId: string, patch: Partial<QueueItem>): Promise<void> {
  const db = await openOfflineDb(); const tx = db.transaction("queue", "readwrite"); const store = tx.objectStore("queue");
  const item = await requestResult(store.get(localId)); if (!item) throw new Error("Item offline não encontrado.");
  store.put({ ...item, ...patch, localId }); await transactionDone(tx); db.close(); notifyQueueChanged();
}
export async function remove(localId: string): Promise<void> { const db = await openOfflineDb(); const tx = db.transaction("queue", "readwrite"); tx.objectStore("queue").delete(localId); await transactionDone(tx); db.close(); notifyQueueChanged(); }
export async function remapLocalId(localId: string, serverId: string): Promise<void> {
  const db = await openOfflineDb(); const tx = db.transaction("queue", "readwrite"); const store = tx.objectStore("queue");
  const items = await requestResult(store.getAll()); items.filter((item) => item.targetId === localId).forEach((item) => store.put({ ...item, targetId: serverId }));
  await transactionDone(tx); db.close(); notifyQueueChanged();
}
export async function countPending(authorUserId?: string): Promise<number> {
  const items = await listAll(); return items.filter((item) => (!authorUserId || item.authorUserId === authorUserId) && item.status !== "syncing").length;
}

function notifyQueueChanged(): void {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(OFFLINE_QUEUE_CHANGE_EVENT));
}
