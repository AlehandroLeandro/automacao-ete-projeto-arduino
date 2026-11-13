import type { LucideIcon } from "lucide-react";

export type UserRole = "OPERATOR" | "SUPERVISOR" | "ADMIN" | "VIEWER";

export interface ApiPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface FilterState {
  startAt: string;
  endAt: string;
  stationId?: string;
  authorUserId?: string;
  reason?: StopReason;
  type?: LimeRecordType;
}

export interface UserDirectoryEntry {
  id: string;
  name: string;
  role: UserRole;
}

export type StationMode = "consolidated" | "novo-horizonte" | "boa-vista";
export type OperationalStationId = Exclude<StationMode, "consolidated">;

export interface StationOption {
  id: StationMode;
  label: string;
}

export type OperationalStationOption = StationOption & { id: OperationalStationId };

export interface AuthUser {
  id: string;
  name: string;
  username: string;
  role: UserRole;
  changePassword: boolean;
  stationId?: string;
  stationName?: string;
  stationMode?: OperationalStationId;
  operatorRecordWindowHours?: number;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  message?: string;
  fields?: ApiFieldError[];
}

export type ApiAuthMode = "none" | "optional" | "required";
export type ApiHttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface AuthTokensResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  name: string;
  username: string;
  role: UserRole;
  stationId?: string | null;
  stationSlug?: string | null;
  stationName?: string | null;
  changePassword: boolean;
  operatorRecordWindowHours?: number;
}

export interface ApiStation {
  id: string;
  name: string;
  slug: string;
  active: boolean;
  minLimeStockBags?: number | null;
}

export interface ApiStoppage {
  id: string;
  stationId: string;
  stationName: string;
  reason: string;
  reasonDetail?: string | null;
  status: "ACTIVE" | "FINISHED";
  startAt: string;
  endAt?: string | null;
  durationMinutes: number;
  createdById: string;
  createdByName: string;
}

export type ApiNotificationType =
  | "INTERNAL"
  | "HOURLY_MEASUREMENT_REMINDER"
  | "STOPPAGE_ALERT"
  | "CAL_SHIFT_MOVEMENT_REMINDER"
  | "LIME_MIN_STOCK_ALERT";

export interface ApiNotification {
  id: string;
  type: ApiNotificationType;
  stationId?: string | null;
  stationName?: string | null;
  targetUserId?: string | null;
  title: string;
  message: string;
  targetUrl?: string | null;
  scheduledAt?: string | null;
  sentAt?: string | null;
  readAt?: string | null;
  externalProvider?: string | null;
  externalStatus?: string | null;
  createdAt: string;
}

export interface DashboardSnapshot {
  station: StationMode;
  status: "operating" | "stopped";
  stoppedSince?: string;
  stoppedReason?: string;
  limeStockBags: number;
  limeConsumptionLastShiftBags: number;
  inputPh: number;
  outputPh: number;
  flowLitersPerHour: number;
  updatedAt: string;
}

export type MeasurementKind = "input" | "output";

export interface PhRecord {
  id: string;
  station: string;
  kind: MeasurementKind;
  ph: number;
  collectedAt: string;
  operator: string;
  deleted?: boolean;
  deletedAt?: string;
  deletedBy?: string;
  changes?: ChangeEvent[];
}

export interface FlowRecord {
  id: string;
  station: string;
  liters: number;
  collectedAt: string;
  operator: string;
  deleted?: boolean;
  deletedAt?: string;
  deletedBy?: string;
  changes?: ChangeEvent[];
}

export type LimeRecordType = "entry" | "consumption";

export interface LimeRecord {
  id: string;
  station: string;
  type: LimeRecordType;
  quantityBags: number;
  stockAfterBags: number;
  collectedAt: string;
  operator: string;
}

export type StopReason =
  | "falta_de_agua"
  | "falta_de_operador"
  | "falta_de_cal"
  | "direcionamento_de_agua_para_lavador"
  | "entupimento_de_bombas"
  | "outros";

export interface StopRecord {
  id: string;
  station: string;
  reason: StopReason;
  reasonDetail?: string;
  startedAt: string;
  endedAt?: string;
  status: "ACTIVE" | "FINISHED";
  operator: string;
  deleted?: boolean;
  deletedAt?: string;
  deletedBy?: string;
  changes?: ChangeEvent[];
}

export type ChangeAction = "UPDATED" | "DELETED";

export interface ChangeField {
  name: string;
  label: string;
  beforeValue?: string;
  afterValue?: string;
}

export interface ChangeEvent {
  id: string;
  action: ChangeAction;
  occurredAt: string;
  user: string;
  description: string;
  fields: ChangeField[];
}

export interface ReportDefinition {
  id: string;
  title: string;
  category: "Coletado" | "Calculado";
  description: string;
}

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
  matchPrefix?: string;
  adminOnly?: boolean;
  reportsOnly?: boolean;
  operationalOnly?: boolean;
}
