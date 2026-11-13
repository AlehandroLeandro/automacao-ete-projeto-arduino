import type { AuthUser, UserRole } from "../types";

export function canCreateOrEdit(role: UserRole | undefined): boolean {
  return canWriteOperationalData(role);
}

export function canWriteOperationalData(role: UserRole | null | undefined): boolean {
  return role === "OPERATOR" || role === "ADMIN";
}

export function canWriteOperationalDataForUser(user: AuthUser | null | undefined): boolean {
  if (!user) {
    return false;
  }

  if (user.role === "OPERATOR") {
    return Boolean(user.stationId || user.stationMode);
  }

  return canWriteOperationalData(user.role);
}

export function isStationBoundRole(role: UserRole | null | undefined): boolean {
  return role === "OPERATOR" || role === "VIEWER";
}

// Perfil Visualização: somente vazão e paradas; pH, cal e pendências ficam ocultos.
export function canAccessOperationalModules(role: UserRole | null | undefined): boolean {
  return role !== "VIEWER";
}

export function canViewAuditHistory(role: UserRole | null | undefined): boolean {
  return role !== "VIEWER";
}

export function canViewDashboardTrends(role: UserRole | null | undefined): boolean {
  return canViewFullDashboard(role) || role === "VIEWER";
}

export function canReadAllStations(role: UserRole | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPERVISOR";
}

export function canManageLimeThreshold(role: UserRole | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPERVISOR";
}

export function canManageUsers(role: UserRole | undefined): boolean {
  return role === "ADMIN";
}

export function canViewReports(role: UserRole | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPERVISOR";
}

export function canViewFullDashboard(role: UserRole | null | undefined): boolean {
  return role === "ADMIN" || role === "SUPERVISOR";
}

export function operatorRecordWindowStart(referenceDate = new Date()): Date {
  return new Date(referenceDate.getTime() - 24 * 60 * 60 * 1000);
}

export function operatorConfiguredRecordWindowStart(
  user: AuthUser | null | undefined,
  referenceDate = new Date(),
): Date {
  const windowHours = user?.operatorRecordWindowHours ?? 24;
  return new Date(referenceDate.getTime() - windowHours * 60 * 60 * 1000);
}

export function operatorVisibleListStart(
  user: AuthUser | null | undefined,
  requestedStartAt?: string,
  referenceDate = new Date(),
): string | undefined {
  if (user?.role !== "OPERATOR") {
    return requestedStartAt;
  }

  const lowerBound = operatorConfiguredRecordWindowStart(user, referenceDate).toISOString();
  if (!requestedStartAt) {
    return lowerBound;
  }

  return new Date(requestedStartAt).getTime() > new Date(lowerBound).getTime() ? requestedStartAt : lowerBound;
}

export function canEditOperatorWindowRecord(
  user: AuthUser | null | undefined,
  eventAt: string,
  referenceDate = new Date(),
): boolean {
  if (user?.role !== "OPERATOR") {
    return true;
  }

  return new Date(eventAt).getTime() >= operatorConfiguredRecordWindowStart(user, referenceDate).getTime();
}

export function getRoleLabel(role: UserRole): string {
  const labels: Record<UserRole, string> = {
    OPERATOR: "Operador",
    SUPERVISOR: "Encarregado",
    ADMIN: "Administrador",
    VIEWER: "Visualização",
  };

  return labels[role];
}
