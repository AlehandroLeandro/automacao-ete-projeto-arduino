import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { reportDefinitions } from "../src/data/mockData";

function readPageSource(page: string): string {
  return readFileSync(new URL(`../src/pages/${page}.tsx`, import.meta.url), "utf8");
}

test("flow desktop list displays the calculated value in m³/h", () => {
  const source = readPageSource("FlowPage");

  assert.ok(source.includes('cell: (record) => `${record.liters.toLocaleString("pt-BR")} m³/h`,'));
  assert.equal(source.includes('cell: (record) => `${record.liters.toLocaleString("pt-BR")} M³`,'), false);
});

test("flow mobile list displays the calculated value in m³/h", () => {
  const source = readPageSource("FlowPage");

  assert.ok(source.includes('<strong>{record.liters.toLocaleString("pt-BR")} m³/h</strong>'));
  assert.equal(source.includes('<strong>{record.liters.toLocaleString("pt-BR")} L</strong>'), false);
});

test("flow detail displays the calculated value in m³/h", () => {
  const source = readPageSource("FlowDetailPage");

  assert.ok(source.includes('<h1>{record.liters.toLocaleString("pt-BR")} m³/h</h1>'));
  assert.equal(source.includes('<h1>{record.liters.toLocaleString("pt-BR")} L</h1>'), false);
});

test("mock flow dataset describes its values in m³/h and never in litros", () => {
  const flowDefinition = reportDefinitions.find((definition) => definition.id === "flow");

  assert.equal(flowDefinition?.description, "Registros de vazão de água em m³/h.");
  assert.equal(flowDefinition?.description.includes("litros"), false);
});
