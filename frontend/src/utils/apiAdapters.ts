import type {
  ChangeAction,
  ChangeEvent,
  ChangeField,
  FlowRecord,
  LimeRecord,
  LimeRecordType,
  MeasurementKind,
  PhRecord,
  StopReason,
  StopRecord,
} from "../types";
import type { QueueItem } from "../offline/offlineQueue";

export type ApiPhWaterType = "RAW" | "TREATED";
export type ApiLimeRecordType = "ENTRY" | "CONSUMPTION";
export type ApiStoppageReason =
  | "FALTA_DE_AGUA"
  | "FALTA_DE_OPERADOR"
  | "FALTA_DE_CAL"
  | "DIRECIONAMENTO_DE_AGUA_PARA_LAVADOR"
  | "ENTUPIMENTO_DE_BOMBAS"
  | "OUTROS";
export type ApiStoppageStatus = "ACTIVE" | "FINISHED";
export type ApiShiftType = "DAY" | "NIGHT";

export interface ApiAuditFieldChangeResponse {
  name: string;
  label: string;
  beforeValue?: string | null;
  afterValue?: string | null;
}

export interface ApiAuditEventResponse {
  id: string;
  recordType: string;
  action: ChangeAction;
  recordId: string;
  stationId: string;
  stationName: string;
  userId: string;
  userName: string;
  occurredAt: string;
  description: string;
  changes?: ApiAuditFieldChangeResponse[];
}

export interface ApiPhMeasurementResponse {
  id: string;
  stationId: string;
  stationName: string;
  type: ApiPhWaterType;
  ph: number | string;
  measuredAt: string;
  createdById: string;
  createdByName: string;
}

export interface ApiPhMeasurementDetailResponse {
  measurement: ApiPhMeasurementResponse;
  edited: boolean;
  deleted: boolean;
  changes: ApiAuditEventResponse[];
}

export interface ApiFlowMeasurementResponse {
  id: string;
  stationId: string;
  stationName: string;
  flowLiters: number | string;
  measuredAt: string;
  createdById: string;
  createdByName: string;
}

export interface ApiFlowMeasurementDetailResponse {
  measurement: ApiFlowMeasurementResponse;
  edited: boolean;
  deleted: boolean;
  changes: ApiAuditEventResponse[];
}

export interface ApiLimeRecordResponse {
  id: string;
  stationId: string;
  stationName: string;
  type: ApiLimeRecordType;
  quantityBags: number | string;
  recordedAt: string;
  note?: string | null;
  createdById: string;
  createdByName: string;
}

export interface ApiLimeRecordListResponse extends ApiLimeRecordResponse {
  stockAfterBags: number | string;
}

export interface ApiLimeStockResponse {
  stationId: string;
  stationName: string;
  currentStockBags: number | string;
  calculatedAt: string;
}

export interface ApiStoppageResponse {
  id: string;
  stationId: string;
  stationName: string;
  reason: ApiStoppageReason;
  reasonDetail?: string | null;
  status: ApiStoppageStatus;
  startAt: string;
  endAt?: string | null;
  durationMinutes: number;
  createdById: string;
  createdByName: string;
}

export interface ApiStoppageDetailResponse {
  stoppage: ApiStoppageResponse;
  edited: boolean;
  deleted: boolean;
  changes: ApiAuditEventResponse[];
}

export interface ApiStoppageShiftSliceResponse {
  shiftDate: string;
  shiftType: ApiShiftType;
  durationMinutes: number;
}

export type ApiBackedPhRecord = PhRecord & { stationId: string };
export type ApiBackedFlowRecord = FlowRecord & { stationId: string };
export type ApiBackedLimeRecord = LimeRecord & { note?: string | null; stationId: string };
export type ApiBackedStopRecord = StopRecord & { durationMinutes: number; stationId: string };

const measurementKindByApiType: Record<ApiPhWaterType, MeasurementKind> = {
  RAW: "input",
  TREATED: "output",
};

const apiTypeByMeasurementKind: Record<MeasurementKind, ApiPhWaterType> = {
  input: "RAW",
  output: "TREATED",
};

const limeRecordTypeByApiType: Record<ApiLimeRecordType, LimeRecordType> = {
  CONSUMPTION: "consumption",
  ENTRY: "entry",
};

const apiTypeByLimeRecordType: Record<LimeRecordType, ApiLimeRecordType> = {
  consumption: "CONSUMPTION",
  entry: "ENTRY",
};

const stopReasonByApiReason: Record<ApiStoppageReason, StopReason> = {
  DIRECIONAMENTO_DE_AGUA_PARA_LAVADOR: "direcionamento_de_agua_para_lavador",
  ENTUPIMENTO_DE_BOMBAS: "entupimento_de_bombas",
  FALTA_DE_AGUA: "falta_de_agua",
  FALTA_DE_CAL: "falta_de_cal",
  FALTA_DE_OPERADOR: "falta_de_operador",
  OUTROS: "outros",
};

const apiReasonByStopReason: Record<StopReason, ApiStoppageReason> = {
  direcionamento_de_agua_para_lavador: "DIRECIONAMENTO_DE_AGUA_PARA_LAVADOR",
  entupimento_de_bombas: "ENTUPIMENTO_DE_BOMBAS",
  falta_de_agua: "FALTA_DE_AGUA",
  falta_de_cal: "FALTA_DE_CAL",
  falta_de_operador: "FALTA_DE_OPERADOR",
  outros: "OUTROS",
};

export function apiPhWaterTypeToMeasurementKind(type: ApiPhWaterType): MeasurementKind {
  return measurementKindByApiType[type];
}

export function measurementKindToApiPhWaterType(kind: MeasurementKind): ApiPhWaterType {
  return apiTypeByMeasurementKind[kind];
}

export function apiLimeRecordTypeToLimeRecordType(type: ApiLimeRecordType): LimeRecordType {
  return limeRecordTypeByApiType[type];
}

export function limeRecordTypeToApiLimeRecordType(type: LimeRecordType): ApiLimeRecordType {
  return apiTypeByLimeRecordType[type];
}

export function apiStoppageReasonToStopReason(reason: ApiStoppageReason): StopReason {
  return stopReasonByApiReason[reason];
}

export function stopReasonToApiStoppageReason(reason: StopReason): ApiStoppageReason {
  return apiReasonByStopReason[reason];
}

export function auditEventResponseToChangeEvent(response: ApiAuditEventResponse): ChangeEvent {
  return {
    id: response.id,
    action: response.action,
    occurredAt: response.occurredAt,
    user: response.userName,
    description: response.description,
    fields: (response.changes ?? []).map(auditFieldChangeResponseToChangeField),
  };
}

export function apiPhMeasurementResponseToPhRecord(response: ApiPhMeasurementResponse): ApiBackedPhRecord {
  return {
    id: response.id,
    stationId: response.stationId,
    station: response.stationName,
    kind: apiPhWaterTypeToMeasurementKind(response.type),
    ph: decimalToNumber(response.ph),
    collectedAt: response.measuredAt,
    operator: response.createdByName,
  };
}

export function apiPhMeasurementDetailResponseToPhRecord(
  response: ApiPhMeasurementDetailResponse,
): ApiBackedPhRecord {
  return withAuditState(apiPhMeasurementResponseToPhRecord(response.measurement), response);
}

export function apiFlowMeasurementResponseToFlowRecord(response: ApiFlowMeasurementResponse): ApiBackedFlowRecord {
  return {
    id: response.id,
    stationId: response.stationId,
    station: response.stationName,
    liters: decimalToNumber(response.flowLiters),
    collectedAt: response.measuredAt,
    operator: response.createdByName,
  };
}

export function apiFlowMeasurementDetailResponseToFlowRecord(
  response: ApiFlowMeasurementDetailResponse,
): ApiBackedFlowRecord {
  return withAuditState(apiFlowMeasurementResponseToFlowRecord(response.measurement), response);
}

export function apiLimeRecordListResponseToLimeRecord(response: ApiLimeRecordListResponse): ApiBackedLimeRecord {
  return {
    id: response.id,
    stationId: response.stationId,
    station: response.stationName,
    type: apiLimeRecordTypeToLimeRecordType(response.type),
    quantityBags: decimalToNumber(response.quantityBags),
    stockAfterBags: decimalToNumber(response.stockAfterBags),
    collectedAt: response.recordedAt,
    operator: response.createdByName,
    note: response.note,
  };
}

export function apiLimeRecordResponseToLimeRecord(
  response: ApiLimeRecordResponse,
  stockAfterBags: number,
): ApiBackedLimeRecord {
  return {
    id: response.id,
    stationId: response.stationId,
    station: response.stationName,
    type: apiLimeRecordTypeToLimeRecordType(response.type),
    quantityBags: decimalToNumber(response.quantityBags),
    stockAfterBags,
    collectedAt: response.recordedAt,
    operator: response.createdByName,
    note: response.note,
  };
}

export function apiStoppageResponseToStopRecord(response: ApiStoppageResponse): ApiBackedStopRecord {
  return {
    id: response.id,
    stationId: response.stationId,
    station: response.stationName,
    reason: apiStoppageReasonToStopReason(response.reason),
    reasonDetail: response.reasonDetail ?? undefined,
    startedAt: response.startAt,
    endedAt: response.endAt ?? undefined,
    status: response.status,
    operator: response.createdByName,
    durationMinutes: response.durationMinutes,
  };
}

export function pendingRecordFromQueueItem(item: QueueItem, station: string): ApiBackedStopRecord | undefined {
  const { reason, reasonDetail, startAt } = item.payload;
  if (
    item.domain !== "stoppage" || item.operation !== "startStoppage" ||
    !item.stationId || !station || typeof reason !== "string" ||
    !Object.prototype.hasOwnProperty.call(stopReasonByApiReason, reason) ||
    (reasonDetail !== undefined && reasonDetail !== null && typeof reasonDetail !== "string") ||
    typeof startAt !== "string"
  ) return undefined;

  return {
    id: item.localId,
    stationId: item.stationId,
    station,
    reason: apiStoppageReasonToStopReason(reason as ApiStoppageReason),
    reasonDetail: typeof reasonDetail === "string" ? reasonDetail : undefined,
    startedAt: startAt,
    status: "ACTIVE",
    operator: item.authorName,
    durationMinutes: 0,
  };
}

export function apiStoppageDetailResponseToStopRecord(response: ApiStoppageDetailResponse): ApiBackedStopRecord {
  return withAuditState(apiStoppageResponseToStopRecord(response.stoppage), response);
}

function auditFieldChangeResponseToChangeField(response: ApiAuditFieldChangeResponse): ChangeField {
  return {
    name: response.name,
    label: response.label,
    beforeValue: response.beforeValue ?? undefined,
    afterValue: response.afterValue ?? undefined,
  };
}

function withAuditState<TRecord extends { changes?: ChangeEvent[]; deleted?: boolean; deletedAt?: string; deletedBy?: string }>(
  record: TRecord,
  response: { changes: ApiAuditEventResponse[]; deleted: boolean },
): TRecord {
  const changes = response.changes.map(auditEventResponseToChangeEvent);
  const deletedChange = changes.find((change) => change.action === "DELETED");

  return {
    ...record,
    changes,
    deleted: response.deleted,
    deletedAt: response.deleted ? deletedChange?.occurredAt : undefined,
    deletedBy: response.deleted ? deletedChange?.user : undefined,
  };
}

function decimalToNumber(value: number | string): number {
  return typeof value === "number" ? value : Number(value);
}
