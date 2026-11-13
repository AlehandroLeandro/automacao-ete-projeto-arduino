import assert from "node:assert/strict";
import test from "node:test";
import { pendingRecordFromQueueItem } from "../../src/utils/apiAdapters";
import type { QueueItem } from "../../src/offline/offlineQueue";

const item = (payload: Record<string, unknown>): QueueItem => ({
  localId: "local-1", seq: 1, domain: "stoppage", operation: "startStoppage", clientRequestId: "client-1",
  authorUserId: "user-1", authorName: "Operator", stationId: "station-1", targetId: null, payload,
  eventAt: "2026-09-09T10:00:00Z", queuedAt: "2026-09-09T10:00:00Z", status: "pending", attempts: 0,
  lastAttemptAt: null, lastError: null,
});

test("projects a complete queued stoppage into an active stop record", () => {
  assert.deepEqual(pendingRecordFromQueueItem(item({ reason: "FALTA_DE_CAL", reasonDetail: "Bomba", startAt: "2026-09-09T10:00:00Z" }), "ETE A"), {
    id: "local-1", stationId: "station-1", station: "ETE A", reason: "falta_de_cal", reasonDetail: "Bomba",
    startedAt: "2026-09-09T10:00:00Z", status: "ACTIVE", operator: "Operator", durationMinutes: 0,
  });
});

test("returns undefined for an incomplete queued stoppage", () => {
  assert.equal(pendingRecordFromQueueItem(item({ reasonDetail: "Bomba", startAt: "2026-09-09T10:00:00Z" }), "ETE A"), undefined);
});
