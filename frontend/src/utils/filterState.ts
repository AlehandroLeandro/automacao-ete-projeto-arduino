import type { FilterState } from "../types";

export function isFilterDateRangeValid(startAt: string, endAt: string): boolean {
  return !startAt || !endAt || startAt <= endAt;
}

export function nextFilterDateValue(
  value: FilterState,
  field: "startAt" | "endAt",
  nextValue: string,
): FilterState | null {
  const next = { ...value, [field]: nextValue };
  return isFilterDateRangeValid(next.startAt, next.endAt) ? next : null;
}
