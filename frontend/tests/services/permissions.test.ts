import test from "node:test";
import assert from "node:assert/strict";

import {
  canAccessOperationalModules,
  canEditOperatorWindowRecord,
  canManageUsers,
  canReadAllStations,
  canViewAuditHistory,
  canViewDashboardTrends,
  canViewFullDashboard,
  canViewReports,
  canWriteOperationalData,
  getRoleLabel,
  isStationBoundRole,
  operatorConfiguredRecordWindowStart,
  operatorRecordWindowStart,
  operatorVisibleListStart,
} from "../../src/auth/permissions.ts";
import type { AuthUser } from "../../src/types.ts";

const operator: AuthUser = {
  id: "operator-id",
  name: "Operador",
  username: "operator",
  role: "OPERATOR",
  changePassword: false,
  stationId: "station-id",
};

test("allows reports only for supervisor and admin", () => {
  assert.equal(canViewReports("ADMIN"), true);
  assert.equal(canViewReports("SUPERVISOR"), true);
  assert.equal(canViewReports("OPERATOR"), false);
  assert.equal(canViewReports(undefined), false);
});

test("allows the full dashboard only for supervisor and admin", () => {
  assert.equal(canViewFullDashboard("ADMIN"), true);
  assert.equal(canViewFullDashboard("SUPERVISOR"), true);
  assert.equal(canViewFullDashboard("OPERATOR"), false);
  assert.equal(canViewFullDashboard(undefined), false);
  assert.equal(canViewFullDashboard(null), false);
  assert.equal(canViewFullDashboard("VIEWER"), false);
});

test("viewer profile is read-only, station-bound and limited to flow and stoppages", () => {
  assert.equal(getRoleLabel("VIEWER"), "Visualização");
  assert.equal(isStationBoundRole("VIEWER"), true);
  assert.equal(isStationBoundRole("OPERATOR"), true);
  assert.equal(isStationBoundRole("SUPERVISOR"), false);
  assert.equal(canWriteOperationalData("VIEWER"), false);
  assert.equal(canReadAllStations("VIEWER"), false);
  assert.equal(canViewReports("VIEWER"), false);
  assert.equal(canManageUsers("VIEWER"), false);
  assert.equal(canAccessOperationalModules("VIEWER"), false);
  assert.equal(canAccessOperationalModules("OPERATOR"), true);
  assert.equal(canViewAuditHistory("VIEWER"), false);
  assert.equal(canViewAuditHistory("SUPERVISOR"), true);
  assert.equal(canViewDashboardTrends("VIEWER"), true);
  assert.equal(canViewDashboardTrends("ADMIN"), true);
  assert.equal(canViewDashboardTrends("OPERATOR"), false);
});

test("uses a sliding 24 hour operator window for list start", () => {
  const now = new Date("2026-07-13T15:00:00.000Z");

  assert.equal(operatorRecordWindowStart(now).toISOString(), "2026-07-12T15:00:00.000Z");
  assert.equal(operatorVisibleListStart(operator, undefined, now), "2026-07-12T15:00:00.000Z");
  assert.equal(
    operatorVisibleListStart(operator, "2026-07-13T12:00:00.000Z", now),
    "2026-07-13T12:00:00.000Z",
  );
  assert.equal(
    operatorVisibleListStart({ ...operator, role: "ADMIN" }, "2026-07-01T00:00:00.000Z", now),
    "2026-07-01T00:00:00.000Z",
  );
});

test("uses the configured operator window from the authenticated user", () => {
  const now = new Date("2026-07-13T15:00:00.000Z");
  const configuredOperator = { ...operator, operatorRecordWindowHours: 48 };

  assert.equal(operatorConfiguredRecordWindowStart(configuredOperator, now).toISOString(), "2026-07-11T15:00:00.000Z");
  assert.equal(operatorVisibleListStart(configuredOperator, undefined, now), "2026-07-11T15:00:00.000Z");
  assert.equal(canEditOperatorWindowRecord(configuredOperator, "2026-07-11T15:00:00.000Z", now), true);
  assert.equal(canEditOperatorWindowRecord(configuredOperator, "2026-07-11T14:59:59.999Z", now), false);
});

test("blocks operator edit controls for records outside the configured window", () => {
  const now = new Date("2026-07-13T15:00:00.000Z");

  assert.equal(canEditOperatorWindowRecord(operator, "2026-07-12T15:00:00.000Z", now), true);
  assert.equal(canEditOperatorWindowRecord(operator, "2026-07-12T14:59:59.999Z", now), false);
  assert.equal(canEditOperatorWindowRecord({ ...operator, role: "SUPERVISOR" }, "2026-07-01T00:00:00.000Z", now), true);
});
