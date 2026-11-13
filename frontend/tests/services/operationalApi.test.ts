import assert from "node:assert/strict";
import test from "node:test";

let requestedUrl = "";
let requestedInit: RequestInit | undefined;
globalThis.fetch = (async (input, init) => {
  requestedUrl = String(input);
  requestedInit = init;
  return new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0 }), {
    headers: { "Content-Type": "application/json" },
  });
}) as typeof fetch;

const {
  createFlowMeasurement,
  listFlowMeasurements,
  updateFlowMeasurement,
  listLimeRecords,
  listPhMeasurements,
  listStoppages,
} = await import("../../src/services/operationalApi");

const period = { endAt: "2026-01-31T23:59:59Z", startAt: "2026-01-01T00:00:00Z" };

async function queryOf(request: () => Promise<unknown>): Promise<URLSearchParams> {
  await request();
  return new URLSearchParams(requestedUrl.split("?")[1]);
}

test("listPhMeasurements includes pagination and author filters only when defined", async () => {
  const full = await queryOf(() => listPhMeasurements({ ...period, authorUserId: "user-1", page: 2, size: 100, type: "RAW" }));
  assert.equal(full.get("authorUserId"), "user-1");
  assert.equal(full.get("page"), "2");
  assert.equal(full.get("size"), "100");
  assert.equal(full.get("type"), "RAW");
  const empty = await queryOf(() => listPhMeasurements(period));
  assert.equal(empty.has("authorUserId"), false);
  assert.equal(empty.has("page"), false);
  assert.equal(empty.has("size"), false);
});

test("listFlowMeasurements includes pagination and author filters only when defined", async () => {
  const full = await queryOf(() => listFlowMeasurements({ ...period, authorUserId: "user-1", consolidated: false, page: 1, size: 50, stationId: "station-1" }));
  assert.equal(full.get("authorUserId"), "user-1");
  assert.equal(full.get("consolidated"), "false");
  assert.equal(full.get("page"), "1");
  assert.equal(full.get("size"), "50");
  assert.equal(full.get("stationId"), "station-1");
  const empty = await queryOf(() => listFlowMeasurements(period));
  assert.equal(empty.has("authorUserId"), false);
  assert.equal(empty.has("page"), false);
  assert.equal(empty.has("size"), false);
});

test("listFlowMeasurements keeps open date ranges open", async () => {
  const onlyStart = await queryOf(() => listFlowMeasurements({ startAt: "2026-01-01" }));
  assert.equal(onlyStart.get("startAt"), "2026-01-01T03:00:00.000Z");
  assert.equal(onlyStart.has("endAt"), false);

  const onlyEnd = await queryOf(() => listFlowMeasurements({ endAt: "2026-01-31" }));
  assert.equal(onlyEnd.has("startAt"), false);
  assert.equal(onlyEnd.get("endAt"), "2026-02-01T02:59:59.999Z");
});

test("listLimeRecords includes pagination and author filters only when defined", async () => {
  const full = await queryOf(() => listLimeRecords({ ...period, authorUserId: "user-1", page: 3, size: 10, type: "ENTRY" }));
  assert.equal(full.get("authorUserId"), "user-1");
  assert.equal(full.get("page"), "3");
  assert.equal(full.get("size"), "10");
  assert.equal(full.get("type"), "ENTRY");
  const empty = await queryOf(() => listLimeRecords(period));
  assert.equal(empty.has("authorUserId"), false);
  assert.equal(empty.has("page"), false);
  assert.equal(empty.has("size"), false);
});

test("listStoppages includes pagination, author and reason filters only when defined", async () => {
  const full = await queryOf(() => listStoppages({ ...period, authorUserId: "user-1", page: 4, size: 100, status: "FINISHED", reason: "FALTA_DE_CAL" }));
  assert.equal(full.get("authorUserId"), "user-1");
  assert.equal(full.get("page"), "4");
  assert.equal(full.get("size"), "100");
  assert.equal(full.get("status"), "FINISHED");
  assert.equal(full.get("reason"), "FALTA_DE_CAL");
  const empty = await queryOf(() => listStoppages(period));
  assert.equal(empty.has("authorUserId"), false);
  assert.equal(empty.has("page"), false);
  assert.equal(empty.has("size"), false);
  assert.equal(empty.has("reason"), false);
});

async function sentBodyOf(request: () => Promise<unknown>): Promise<Record<string, unknown>> {
  await request();
  return JSON.parse(String(requestedInit?.body));
}

test("createFlowMeasurement sends height and never flow liters", async () => {
  const body = await sentBodyOf(() =>
    createFlowMeasurement({ heightCm: 12.5, measuredAt: "2026-01-10T10:00:00Z", stationId: "station-1" }),
  );
  assert.deepEqual(body, { heightCm: 12.5, measuredAt: "2026-01-10T10:00:00Z", stationId: "station-1" });
  assert.equal(requestedInit?.method, "POST");
  assert.equal("flowLiters" in body, false);
});

test("updateFlowMeasurement includes heightCm only when provided", async () => {
  const withHeight = await sentBodyOf(() =>
    updateFlowMeasurement("measurement-1", {
      description: "Correcao da altura",
      heightCm: 30,
      measuredAt: "2026-01-10T10:00:00Z",
      stationId: "station-1",
    }),
  );
  assert.equal(withHeight.heightCm, 30);
  assert.equal(requestedInit?.method, "PUT");
  assert.equal(requestedUrl.endsWith("/flow-measurements/measurement-1"), true);

  const withoutHeight = await sentBodyOf(() =>
    updateFlowMeasurement("measurement-1", {
      description: "Correcao da data",
      measuredAt: "2026-01-10T11:00:00Z",
      stationId: "station-1",
    }),
  );
  assert.equal("heightCm" in withoutHeight, false);
  assert.equal("flowLiters" in withoutHeight, false);
});
