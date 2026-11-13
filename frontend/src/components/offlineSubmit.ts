import { enqueueOperational } from "../offline/useOfflineQueue";
import { ApiError } from "../services/api";
import type { AuthUser } from "../types";
import type { QueueDomain, QueueOperation, QueueItem } from "../offline/offlineQueue";

export async function queueOnNetworkError(
  error: unknown,
  input: { user: AuthUser | null; domain: QueueDomain; operation: QueueOperation; stationId: string; targetId?: string | null; payload: Record<string, unknown>; eventAt: string },
): Promise<QueueItem | null> {
  if (error instanceof ApiError && error.status < 500) return null;
  if (!input.user) return null;
  try {
    return await enqueueOperational({ ...input, authorUserId: input.user.id, authorName: input.user.name, targetId: input.targetId ?? null, queuedAt: new Date().toISOString() });
  } catch (queueError) {
    console.error("[offline-queue] failed to save record", { error: queueError });
    return null;
  }
}
