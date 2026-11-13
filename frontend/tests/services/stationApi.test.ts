import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("listStations requires authentication because stations are operational data", () => {
  const source = readFileSync(new URL("../../src/services/stationApi.ts", import.meta.url), "utf8");
  const functionBody = source.slice(
    source.indexOf("export function listStations"),
    source.indexOf("}\n", source.indexOf("export function listStations")) + 1,
  );

  assert.match(functionBody, /auth:\s*"required"/);
  assert.doesNotMatch(functionBody, /auth:\s*"none"/);
});

test("updateLimeThreshold delegates object payload serialization to the API client", () => {
  const source = readFileSync(new URL("../../src/services/stationApi.ts", import.meta.url), "utf8");
  const functionBody = source.slice(
    source.indexOf("export function updateLimeThreshold"),
    source.indexOf("}\n", source.indexOf("export function updateLimeThreshold")) + 1,
  );

  assert.match(functionBody, /body:\s*\{\s*minLimeStockBags\s*\}/);
  assert.doesNotMatch(functionBody, /JSON\.stringify/);
});
