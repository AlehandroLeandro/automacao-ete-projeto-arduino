import assert from "node:assert/strict";
import test from "node:test";
import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";
import { DashboardLineChart } from "../../src/components/dashboard/DashboardLineChart";
import { apiFlowMeasurementResponseToFlowRecord, apiPhMeasurementResponseToPhRecord } from "../../src/utils/apiAdapters";
import { withTimeEstimate } from "../../src/utils/timeEstimate";

test("API measurements preserve estimated origins and known manual times", () => {
  const base = { id: "1", stationId: "s", stationName: "ETE", measuredAt: "2026-01-01T00:00:00Z", createdById: null, createdByName: null };
  for (const [timeEstimated, timeEstimateOrigin, expected] of [[true, "ELAPSED_TIME", "tempo decorrido"], [true, "RECEIVED_AT", "recebimento"], [true, "MIXED", "origens variadas"], [false, null, null]] as const) {
    const response = { ...base, timeEstimated, timeEstimateOrigin };
    for (const record of [apiPhMeasurementResponseToPhRecord({ ...response, type: "RAW", ph: 7 }), apiFlowMeasurementResponseToFlowRecord({ ...response, flowLiters: 10 })]) {
      const text = withTimeEstimate("Data", record);
      if (expected) assert.match(text, new RegExp(expected));
      else assert.equal(text, "Data");
    }
  }
});

test("chart exposes estimated API points with origin and omits known points from the estimate list", () => {
  const html = renderToStaticMarkup(createElement(DashboardLineChart, { title: "Vazão", ariaLabel: "Gráfico", series: [{ id: "a", label: "ETE", color: "red", points: [
    { measuredAt: "2026-01-01T00:00:00Z", value: 5, timeEstimated: true, timeEstimateOrigin: "RECEIVED_AT" },
    { measuredAt: "2026-01-01T01:00:00Z", value: 6, timeEstimated: false, timeEstimateOrigin: null },
  ] }] }));
  assert.match(html, /Horários estimados no gráfico/);
  assert.match(html, /baseado no horário de recebimento/);
  assert.equal((html.match(/Horário estimado \(/g) ?? []).length, 1);
});
