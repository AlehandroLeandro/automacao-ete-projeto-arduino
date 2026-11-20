import assert from "node:assert/strict";
import test from "node:test";
import { deviceKeyStatusLabel, isDeleteConfirmed, SECRET_VISIBLE_MS } from "../../src/utils/deviceKeys";
test("deletion requires exact confirmation", () => {
  assert.equal(isDeleteConfirmed("EXCLUIR"), true);
  for (const text of ["excluir", " EXCLUIR", "EXCLUIR ", ""]) assert.equal(isDeleteConfirmed(text), false);
});
test("secret duration and signal statuses", () => {
  assert.equal(SECRET_VISIBLE_MS, 15000);
  assert.deepEqual([deviceKeyStatusLabel("ONLINE"), deviceKeyStatusLabel("OFFLINE"), deviceKeyStatusLabel("NEVER")], ["Online", "Offline", "Sem sinal ainda"]);
});
