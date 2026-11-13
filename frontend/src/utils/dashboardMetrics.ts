export type DashboardPeriodPreset = "today" | "2-days" | "7-days" | "30-days" | "custom";

export interface DashboardPeriodOption {
  id: DashboardPeriodPreset;
  label: string;
}

export interface DashboardPeriodSelection {
  preset: DashboardPeriodPreset;
  startDate: string;
  endDate: string;
}

export interface DashboardPeriodRange {
  startDate: string;
  endDate: string;
  startAt: string;
  endAt: string;
}

const oneDayInMilliseconds = 24 * 60 * 60 * 1000;

export const dashboardPeriodOptions: DashboardPeriodOption[] = [
  { id: "today", label: "Hoje" },
  { id: "2-days", label: "2 dias" },
  { id: "7-days", label: "7 dias" },
  { id: "30-days", label: "30 dias" },
  { id: "custom", label: "Personalizado" },
];

export function getDefaultDashboardPeriodSelection(referenceDate = new Date()): DashboardPeriodSelection {
  return getDashboardPeriodSelection("7-days", referenceDate);
}

export function getDashboardPeriodSelection(
  preset: DashboardPeriodPreset,
  referenceDate = new Date(),
): DashboardPeriodSelection {
  if (preset === "custom") {
    return { ...getDefaultDashboardPeriodSelection(referenceDate), preset };
  }

  const endDate = formatDateInputValue(referenceDate);
  const startDate = formatDateInputValue(subtractDays(referenceDate, getPresetElapsedDays(preset)));

  return { preset, startDate, endDate };
}

export function resolveDashboardPeriodRange(selection: DashboardPeriodSelection): DashboardPeriodRange | null {
  if (!isDashboardCustomPeriodValid(selection.startDate, selection.endDate)) {
    return null;
  }

  const startDate = parseDateInputValue(selection.startDate);
  const exclusiveEndDate = addDays(parseDateInputValue(selection.endDate), 1);

  return {
    startDate: selection.startDate,
    endDate: selection.endDate,
    startAt: startDate.toISOString(),
    endAt: exclusiveEndDate.toISOString(),
  };
}

export function isDashboardCustomPeriodValid(startDate: string, endDate: string): boolean {
  if (!startDate || !endDate) {
    return false;
  }

  return parseDateInputValue(startDate).getTime() <= parseDateInputValue(endDate).getTime();
}

function getPresetElapsedDays(preset: Exclude<DashboardPeriodPreset, "custom">): number {
  const elapsedDaysByPreset: Record<Exclude<DashboardPeriodPreset, "custom">, number> = {
    today: 0,
    "2-days": 1,
    "7-days": 6,
    "30-days": 29,
  };

  return elapsedDaysByPreset[preset];
}

function subtractDays(date: Date, days: number): Date {
  return new Date(date.getTime() - days * oneDayInMilliseconds);
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getTime() + days * oneDayInMilliseconds);
}

function formatDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

function parseDateInputValue(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);

  return new Date(year, month - 1, day);
}
