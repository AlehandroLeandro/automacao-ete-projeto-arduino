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

import { apiPhMeasurementResponseToPhRecord, apiFlowMeasurementResponseToFlowRecord } from "../../src/utils/apiAdapters";
test("measurement authors support humans, devices and null names", () => {
  const base = { id: "1", stationId: "s", stationName: "ETE", measuredAt: "2026-01-01T00:00:00Z" };
  for (const [createdById, createdByName, expected] of [["user", "Operador", "Operador"], [null, "Equipamento: Sensor", "Equipamento: Sensor"], [null, null, "Autor desconhecido"]] as const) {
    const author = { createdById, createdByName, deviceName: null, sampleCount: null };
    assert.equal(apiPhMeasurementResponseToPhRecord({ ...base, ...author, type: "RAW", ph: 7 }).operator, expected);
    assert.equal(apiFlowMeasurementResponseToFlowRecord({ ...base, ...author, flowLiters: 10 }).operator, expected);
  }
});
