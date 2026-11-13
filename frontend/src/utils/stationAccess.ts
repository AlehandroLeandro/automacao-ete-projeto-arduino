import { isStationBoundRole } from "../auth/permissions";
import { getOperationalStationLabel, operationalStationOptions } from "../data/mockData";
import type { AuthUser, OperationalStationId, OperationalStationOption, StationMode, StationOption } from "../types";

export const defaultOperationalStationId = operationalStationOptions[0].id;
export const missingOperatorStationLabel = "ITE operacional não vinculada";

export function isStationBoundUser(user: AuthUser | null | undefined): boolean {
  return isStationBoundRole(user?.role);
}

export function operatorStationId(user: AuthUser | null | undefined): OperationalStationId | null {
  if (!isStationBoundUser(user)) {
    return null;
  }

  return user?.stationMode ?? null;
}

export function operatorStationName(user: AuthUser | null | undefined): string | null {
  const stationId = operatorStationId(user);

  if (!stationId) {
    return null;
  }

  return user?.stationName || getOperationalStationLabel(stationId) || null;
}

export function hasOperationalStationScope(user: AuthUser | null | undefined): boolean {
  return !isStationBoundUser(user) || Boolean(operatorStationId(user));
}

export function canUseConsolidatedStation(user: AuthUser | null | undefined): boolean {
  return !isStationBoundUser(user);
}

export function getVisibleOperationalStationOptions(
  user: AuthUser | null | undefined,
  options: OperationalStationOption[] = operationalStationOptions,
): OperationalStationOption[] {
  const stationId = operatorStationId(user);

  if (!stationId) {
    return isStationBoundUser(user) ? [] : options;
  }

  return options.filter((station) => station.id === stationId);
}

export function getVisibleStationOptions(
  user: AuthUser | null | undefined,
  options: StationOption[],
): StationOption[] {
  const stationId = operatorStationId(user);

  if (!stationId) {
    return isStationBoundUser(user) ? [] : options;
  }

  return options.filter((station) => station.id === stationId);
}

export function resolveStationMode(user: AuthUser | null | undefined, stationMode: StationMode): StationMode | null {
  return operatorStationId(user) ?? (hasOperationalStationScope(user) ? stationMode : null);
}

export function writableStationName(user: AuthUser | null | undefined, stationId: OperationalStationId): string {
  if (isStationBoundUser(user)) {
    return operatorStationName(user) ?? "";
  }

  return getOperationalStationLabel(stationId);
}

export function filterByUserStation<TRecord extends { station: string }>(
  records: TRecord[],
  user: AuthUser | null | undefined,
): TRecord[] {
  const stationName = operatorStationName(user);

  if (!stationName) {
    return isStationBoundUser(user) ? [] : records;
  }

  return records.filter((record) => record.station === stationName);
}
