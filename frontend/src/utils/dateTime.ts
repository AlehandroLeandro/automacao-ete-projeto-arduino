export const FUTURE_TIMESTAMP_MESSAGE = "O horário não pode ser maior que o horário atual";

export function getCurrentTimeHHMM(): string {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function formatTime(value: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export function formatRelativeTime(value: string, reference: Date = new Date()): string {
  const elapsedMinutes = Math.floor((reference.getTime() - new Date(value).getTime()) / 60000);

  if (elapsedMinutes < 1) {
    return "agora";
  }

  if (elapsedMinutes < 60) {
    return `há ${elapsedMinutes} min`;
  }

  const elapsedHours = Math.floor(elapsedMinutes / 60);
  if (elapsedHours < 24) {
    return `há ${elapsedHours} h`;
  }

  return formatDateTime(value);
}

export function mergeTodayWithTime(time: string): string {
  const [hour = "00", minute = "00"] = time.split(":");
  const date = new Date();
  date.setHours(Number(hour), Number(minute), 0, 0);
  return date.toISOString();
}

export function mergeDateWithTime(value: string, time: string): string {
  const [hour = "00", minute = "00"] = time.split(":");
  const date = new Date(value);
  date.setHours(Number(hour), Number(minute), 0, 0);
  return date.toISOString();
}

export function isFutureDateTime(value: string): boolean {
  return new Date(value).getTime() > Date.now();
}

export function maxTimeForDate(value: string): string | undefined {
  const date = new Date(value);
  const now = new Date();

  if (
    date.getFullYear() !== now.getFullYear() ||
    date.getMonth() !== now.getMonth() ||
    date.getDate() !== now.getDate()
  ) {
    return undefined;
  }

  return getCurrentTimeHHMM();
}

export function formatHoursMinutes(totalMinutes: number): string {
  const safeMinutes = Math.max(0, Math.round(totalMinutes));
  const hours = Math.floor(safeMinutes / 60);
  const minutes = safeMinutes % 60;

  return `${hours}:${String(minutes).padStart(2, "0")}h`;
}

export function formatDecimalHours(decimalHours: number): string {
  return formatHoursMinutes(Math.round(decimalHours * 60));
}

export function formatElapsedTime(startedAt: string): string {
  const elapsedMilliseconds = Math.max(0, Date.now() - new Date(startedAt).getTime());
  const totalMinutes = Math.floor(elapsedMilliseconds / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  return `${String(hours).padStart(2, "0")}h ${String(minutes).padStart(2, "0")}min`;
}

function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

export function getTodayDate(): string {
  return formatDate(new Date());
}

export function getPastMonthDate(): string {
  const date = new Date();
  date.setMonth(date.getMonth() - 1);

  return formatDate(date);
}
