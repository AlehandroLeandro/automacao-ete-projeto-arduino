import assert from "node:assert/strict";
import test from "node:test";

import { validateFlowHeight } from "../../src/utils/flowHeight";

test("rejects an empty flow height with the required message", () => {
  assert.deepEqual(validateFlowHeight(""), {
    message: "Informe a altura da lâmina em cm.",
    ok: false,
  });
});

test("rejects flow heights outside the inclusive range", () => {
  assert.equal(validateFlowHeight("-1").ok, false);
  assert.equal(validateFlowHeight("45.01").ok, false);
});

test("rejects flow heights with more than two decimal places", () => {
  assert.equal(validateFlowHeight("12.345").ok, false);
});

test("accepts both flow height boundaries", () => {
  assert.deepEqual(validateFlowHeight("0"), { ok: true, value: 0 });
  assert.deepEqual(validateFlowHeight("45"), { ok: true, value: 45 });
});
