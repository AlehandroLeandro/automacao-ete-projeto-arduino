import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../../src/components/QueuedDomainRecords.tsx", import.meta.url), "utf8");

test("shows failed queue error message", () => {
  assert.match(source, /item\.status === "failed" && item\.lastError/);
  assert.match(source, /className="form-error">\{queuedRecordError\(item\)\}/);
});

test("does not show error for pending or syncing items", () => {
  assert.match(source, /return item\.status === "failed" && item\.lastError \? item\.lastError\.message : null/);
});

test("does not break when failed item has no error", () => {
  assert.match(source, /item\.lastError \? item\.lastError\.message : null/);
});
