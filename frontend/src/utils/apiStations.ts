import { isStationBoundRole } from "../auth/permissions";
import { getOperationalStationLabel } from "../data/mockData";
import { listStations } from "../services/stationApi";
import type { ApiStation, AuthUser, OperationalStationId } from "../types";

const apiStationSlugByMode: Record<OperationalStationId, string> = {
  "boa-vista": "ete-boa-vista",
  "novo-horizonte": "ete-novo-horizonte",
};

export async function resolveApiStationId(stationMode: OperationalStationId): Promise<string> {
  const stations = await listStations();
  const station = findApiStationByMode(stations, stationMode);

  if (!station) {
    throw new Error("ITE não encontrada na API.");
  }

  return station.id;
}

export function resolveWritableApiStationId(
  user: AuthUser | null | undefined,
  stationMode: OperationalStationId,
  stations: ApiStation[],
): string | null {
  if (isStationBoundRole(user?.role)) {
    return user?.stationId ?? null;
  }

  return findApiStationByMode(stations, stationMode)?.id ?? null;
}

export function findApiStationByMode(
  stations: ApiStation[],
  stationMode: OperationalStationId,
): ApiStation | undefined {
  const expectedSlug = apiStationSlugByMode[stationMode];
  const expectedName = getOperationalStationLabel(stationMode);

  return stations.find(
    (station) =>
      station.active &&
      (sameStationSlug(station.slug, expectedSlug) || sameStationName(station.name, expectedName)),
  );
}

export async function stationModeFromApiId(stationId: string): Promise<OperationalStationId | undefined> {
  const stations = await listStations();
  const station = stations.find((candidate) => candidate.id === stationId);

  return station ? stationModeFromApiSlug(station.slug) : undefined;
}

export function stationModeFromApiSlug(stationSlug: string): OperationalStationId | undefined {
  const normalizedSlug = normalizeStationSlug(stationSlug);

  if (normalizedSlug === "boa-vista") {
    return "boa-vista";
  }

  if (normalizedSlug === "novo-horizonte") {
    return "novo-horizonte";
  }

  return undefined;
}

function sameStationName(firstName: string, secondName: string): boolean {
  return firstName.trim().toLowerCase() === secondName.trim().toLowerCase();
}

function sameStationSlug(firstSlug: string, secondSlug: string): boolean {
  return normalizeStationSlug(firstSlug) === normalizeStationSlug(secondSlug);
}

function normalizeStationSlug(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .toLowerCase()
    .replace(/^ete-/, "");
}
