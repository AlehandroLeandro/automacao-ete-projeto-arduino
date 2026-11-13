export const OFFLINE_DB_NAME = "ete-offline";
export const OFFLINE_DB_VERSION = 1;

export function openOfflineDb(): Promise<IDBDatabase> {
  if (typeof indexedDB === "undefined") return Promise.reject(new Error("IndexedDB indisponível."));
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(OFFLINE_DB_NAME, OFFLINE_DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      const queue = db.createObjectStore("queue", { keyPath: "localId" });
      queue.createIndex("seq", "seq", { unique: true });
      queue.createIndex("authorUserId", "authorUserId");
      queue.createIndex("status", "status");
      queue.createIndex("targetId", "targetId");
      db.createObjectStore("authors", { keyPath: "userId" });
      db.createObjectStore("limeStock", { keyPath: "stationId" });
      db.createObjectStore("meta", { keyPath: "key" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Falha ao abrir IndexedDB."));
  });
}

export function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Falha no IndexedDB."));
  });
}

export function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error ?? new Error("Falha no IndexedDB."));
    transaction.onabort = () => reject(transaction.error ?? new Error("Transação IndexedDB abortada."));
  });
}

export function uuid(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
