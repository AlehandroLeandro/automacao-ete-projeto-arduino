import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

function readSource(relativePath: string): string {
  return readFileSync(new URL(`../${relativePath}`, import.meta.url), "utf8");
}

/**
 * Padrão de teste de componente deste repo: asserção sobre o source
 * (ver publicDashboardRemoval.test.ts) — não há React Testing Library instalada.
 */

test("dashboard page derives the reduced start screen from canViewFullDashboard", () => {
  const source = readSource("src/pages/DashboardPage.tsx");

  assert.match(source, /import \{[^}]*canViewFullDashboard[^}]*\} from "\.\.\/auth\/permissions";/);
  assert.match(source, /const showFullDashboard = canViewFullDashboard\(user\?\.role\);/);
});

test("scope selector and report button render only for the full dashboard", () => {
  const source = readSource("src/pages/DashboardPage.tsx");

  assert.match(
    source,
    /\{showFullDashboard \? \(\s*<div className="dashboard__scope">\s*<DashboardScopeSelector/,
  );
  assert.match(source, /\{showFullDashboard \? \(\s*<div className="dashboard-report-action">/);
});

test("latest readings, period filter and charts render for full and viewer dashboards", () => {
  const source = readSource("src/pages/DashboardPage.tsx");

  assert.match(source, /const showTrends = canViewDashboardTrends\(user\?\.role\);/);
  assert.match(
    source,
    /\{showTrends \? \(\s*<section className="dashboard-section" aria-labelledby="latest-readings-title">/,
  );
  assert.match(source, /\{showTrends \? \(\s*<DashboardPeriodFilter/);
  assert.match(source, /\{showTrends && snapshot \? \(/);
  assert.match(source, /<DashboardChartGrid charts=\{visibleCharts\}>\s*<DashboardActivityLogs/);
});

test("viewer dashboard keeps only flow metrics, the flow chart and stoppage logs", () => {
  const source = readSource("src/pages/DashboardPage.tsx");

  assert.match(source, /const flowAndStopsOnly = !canAccessOperationalModules\(user\?\.role\);/);
  assert.match(source, /latestMetrics\.filter\(isFlowMetric\)/);
  assert.match(source, /averageMetrics\.filter\(isFlowMetric\)/);
  assert.match(source, /charts\.filter\(\(chart\) => chart\.id === "flow"\)/);
  assert.match(source, /showLimeLogs=\{!flowAndStopsOnly\}/);
});

test("header, status hero and daily averages stay outside the full dashboard condition", () => {
  const source = readSource("src/pages/DashboardPage.tsx");

  function precedingMarkup(marker: string): string {
    const index = source.indexOf(marker);
    assert.notEqual(index, -1, `marker not found: ${marker}`);
    return source.slice(Math.max(0, index - 120), index);
  }

  assert.doesNotMatch(precedingMarkup("<DashboardStatusHero"), /showFullDashboard/);
  assert.doesNotMatch(
    precedingMarkup('<section className="dashboard-section" aria-labelledby="average-readings-title">'),
    /showFullDashboard/,
  );
  assert.doesNotMatch(precedingMarkup("<PageHeader"), /showFullDashboard/);

  assert.match(source, /Médias do dia/);
  assert.match(source, /Registrar retorno/);
  assert.match(source, /<DashboardWorkedHoursCard entries=\{workedHoursEntries\}/);
});

test("flow, ph and lime pages no longer apply the operator window", () => {
  for (const page of ["FlowPage", "PhMeasurementsPage", "LimePage"]) {
    const source = readSource(`src/pages/${page}.tsx`);

    assert.doesNotMatch(source, /operatorVisibleListStart/, `${page} still narrows the list start`);
    assert.doesNotMatch(source, /canEditOperatorWindowRecord/, `${page} still gates edit controls`);
    assert.doesNotMatch(source, /operatorVisibleListStart\(user\)/, `${page} still sends a role-derived startAt`);
  }
});

test("stops page keeps using the operator window helpers", () => {
  const source = readSource("src/pages/StopsPage.tsx");

  assert.match(source, /canEditOperatorWindowRecord/);
  assert.match(source, /operatorVisibleListStart/);
});
