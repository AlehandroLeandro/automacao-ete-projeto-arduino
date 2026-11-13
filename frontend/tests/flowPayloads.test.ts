import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const flowPageSource = readFileSync(new URL("../src/pages/FlowPage.tsx", import.meta.url), "utf8");

test("flow offline create queues height and never flow liters", () => {
  assert.ok(
    flowPageSource.includes("payload: { heightCm: heightValidation.value, measuredAt, stationId: apiStationId }"),
  );
  assert.equal(flowPageSource.includes("flowLiters"), false);
});

test("flow offline update reuses a payload that carries heightCm only when filled", () => {
  assert.ok(flowPageSource.includes("...(heightCm === undefined ? {} : { heightCm }),"));
  assert.ok(flowPageSource.includes('domain: "flow", operation: "update"'));
  assert.ok(flowPageSource.includes("payload,"));
});
