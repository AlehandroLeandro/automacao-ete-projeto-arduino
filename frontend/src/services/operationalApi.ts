import { createApiClient } from "./api";
import type {
  ApiFlowMeasurementDetailResponse,
  ApiFlowMeasurementResponse,
  ApiLimeRecordListResponse,
  ApiLimeRecordResponse,
  ApiLimeRecordType,
  ApiLimeStockResponse,
  ApiPhMeasurementDetailResponse,
  ApiPhMeasurementResponse,
  ApiPhWaterType,
  ApiStoppageDetailResponse,
  ApiStoppageReason,
  ApiStoppageResponse,
  ApiStoppageShiftSliceResponse,
  ApiStoppageStatus,
} from "../utils/apiAdapters";
import type { ApiPage } from "../types";

interface OperationalPeriodParams {
  endAt?: string;
  startAt?: string;
}

interface OperationalListParams extends OperationalPeriodParams {
  authorUserId?: string;
  consolidated?: boolean;
  page?: number;
  size?: number;
  stationId?: string;
}

interface AuditedDescriptionPayload {
  description: string;
}

export interface PhMeasurementListParams extends OperationalListParams {
  includeDeleted?: boolean;
  type?: ApiPhWaterType;
}

export interface PhMeasurementPayload {
  measuredAt: string;
  ph: number;
  stationId: string;
  type: ApiPhWaterType;
}

export interface PhMeasurementUpdatePayload extends PhMeasurementPayload, AuditedDescriptionPayload {}

export interface FlowMeasurementListParams extends OperationalListParams {
  includeDeleted?: boolean;
}

export interface FlowMeasurementPayload {
  heightCm: number;
  measuredAt: string;
  stationId: string;
}

export interface FlowMeasurementUpdatePayload extends AuditedDescriptionPayload {
  heightCm?: number;
  measuredAt: string;
  stationId: string;
}

export interface LimeRecordListParams extends OperationalListParams {
  type?: ApiLimeRecordType;
}

export interface LimeRecordPayload {
  note?: string | null;
  quantityBags: number;
  recordedAt: string;
  stationId: string;
  type: ApiLimeRecordType;
}

export interface StoppageListParams extends OperationalListParams {
  includeDeleted?: boolean;
  reason?: ApiStoppageReason;
  status?: ApiStoppageStatus;
}

export interface StartStoppagePayload {
  reason: ApiStoppageReason;
  reasonDetail?: string | null;
  startAt: string;
  stationId: string;
}

export interface FinishStoppagePayload {
  endAt: string;
}

export interface StoppageUpdatePayload extends AuditedDescriptionPayload {
  endAt?: string | null;
  reason: ApiStoppageReason;
  reasonDetail?: string | null;
  startAt: string;
}

export interface ActiveStoppageParams {
  stationId?: string;
}

export interface OperationalPeriod {
  endAt?: string;
  startAt?: string;
}

const DEFAULT_PERIOD_DAYS = 30;
const apiClient = createApiClient();

export function defaultOperationalPeriod(referenceDate = new Date()): OperationalPeriod {
  const endDate = new Date(referenceDate);
  const startDate = new Date(endDate);
  startDate.setDate(startDate.getDate() - DEFAULT_PERIOD_DAYS);

  return {
    endAt: endDate.toISOString(),
    startAt: startDate.toISOString(),
  };
}

export function listPhMeasurements(
  params: PhMeasurementListParams = {},
  signal?: AbortSignal,
): Promise<ApiPage<ApiPhMeasurementResponse>> {
  return apiClient.request<ApiPage<ApiPhMeasurementResponse>>(pathWithQuery("/ph-measurements", measurementListQuery(params)), {
    auth: "required",
    signal,
  });
}

export function createPhMeasurement(
  payload: PhMeasurementPayload,
  signal?: AbortSignal,
): Promise<ApiPhMeasurementResponse> {
  return apiClient.request<ApiPhMeasurementResponse>("/ph-measurements", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function getPhMeasurement(
  measurementId: string,
  signal?: AbortSignal,
): Promise<ApiPhMeasurementDetailResponse> {
  return apiClient.request<ApiPhMeasurementDetailResponse>(`/ph-measurements/${measurementId}`, {
    auth: "required",
    signal,
  });
}

export function updatePhMeasurement(
  measurementId: string,
  payload: PhMeasurementUpdatePayload,
  signal?: AbortSignal,
): Promise<ApiPhMeasurementResponse> {
  return apiClient.request<ApiPhMeasurementResponse>(`/ph-measurements/${measurementId}`, {
    auth: "required",
    body: payload,
    method: "PUT",
    signal,
  });
}

export function deletePhMeasurement(
  measurementId: string,
  description: string,
  signal?: AbortSignal,
): Promise<void> {
  return apiClient.request<void>(`/ph-measurements/${measurementId}`, {
    auth: "required",
    body: { description },
    method: "DELETE",
    signal,
  });
}

export function listFlowMeasurements(
  params: FlowMeasurementListParams = {},
  signal?: AbortSignal,
): Promise<ApiPage<ApiFlowMeasurementResponse>> {
  return apiClient.request<ApiPage<ApiFlowMeasurementResponse>>(pathWithQuery("/flow-measurements", flowListQuery(params)), {
    auth: "required",
    signal,
  });
}

export function createFlowMeasurement(
  payload: FlowMeasurementPayload,
  signal?: AbortSignal,
): Promise<ApiFlowMeasurementResponse> {
  return apiClient.request<ApiFlowMeasurementResponse>("/flow-measurements", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function getFlowMeasurement(
  measurementId: string,
  signal?: AbortSignal,
): Promise<ApiFlowMeasurementDetailResponse> {
  return apiClient.request<ApiFlowMeasurementDetailResponse>(`/flow-measurements/${measurementId}`, {
    auth: "required",
    signal,
  });
}

export function updateFlowMeasurement(
  measurementId: string,
  payload: FlowMeasurementUpdatePayload,
  signal?: AbortSignal,
): Promise<ApiFlowMeasurementResponse> {
  return apiClient.request<ApiFlowMeasurementResponse>(`/flow-measurements/${measurementId}`, {
    auth: "required",
    body: payload,
    method: "PUT",
    signal,
  });
}

export function deleteFlowMeasurement(
  measurementId: string,
  description: string,
  signal?: AbortSignal,
): Promise<void> {
  return apiClient.request<void>(`/flow-measurements/${measurementId}`, {
    auth: "required",
    body: { description },
    method: "DELETE",
    signal,
  });
}

export function listLimeRecords(
  params: LimeRecordListParams = {},
  signal?: AbortSignal,
): Promise<ApiPage<ApiLimeRecordListResponse>> {
  return apiClient.request<ApiPage<ApiLimeRecordListResponse>>(pathWithQuery("/lime-records", limeListQuery(params)), {
    auth: "required",
    signal,
  });
}

export function createLimeRecord(payload: LimeRecordPayload, signal?: AbortSignal): Promise<ApiLimeRecordResponse> {
  return apiClient.request<ApiLimeRecordResponse>("/lime-records", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function updateLimeRecord(
  recordId: string,
  payload: LimeRecordPayload,
  signal?: AbortSignal,
): Promise<ApiLimeRecordResponse> {
  return apiClient.request<ApiLimeRecordResponse>(`/lime-records/${recordId}`, {
    auth: "required",
    body: payload,
    method: "PUT",
    signal,
  });
}

export function deleteLimeRecord(recordId: string, signal?: AbortSignal): Promise<void> {
  return apiClient.request<void>(`/lime-records/${recordId}`, {
    auth: "required",
    method: "DELETE",
    signal,
  });
}

export function getCurrentLimeStock(stationId: string, signal?: AbortSignal): Promise<ApiLimeStockResponse> {
  const query = new URLSearchParams({ stationId });

  return apiClient.request<ApiLimeStockResponse>(`/lime-records/current-stock?${query.toString()}`, {
    auth: "required",
    signal,
  });
}

export function listStoppages(params: StoppageListParams = {}, signal?: AbortSignal): Promise<ApiPage<ApiStoppageResponse>> {
  return apiClient.request<ApiPage<ApiStoppageResponse>>(pathWithQuery("/stoppages", stoppageListQuery(params)), {
    auth: "required",
    signal,
  });
}

export function startStoppage(payload: StartStoppagePayload, signal?: AbortSignal): Promise<ApiStoppageResponse> {
  return apiClient.request<ApiStoppageResponse>("/stoppages/start", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function finishStoppage(
  stoppageId: string,
  payload: FinishStoppagePayload,
  signal?: AbortSignal,
): Promise<ApiStoppageResponse> {
  return apiClient.request<ApiStoppageResponse>(`/stoppages/${stoppageId}/finish`, {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function updateStoppage(
  stoppageId: string,
  payload: StoppageUpdatePayload,
  signal?: AbortSignal,
): Promise<ApiStoppageResponse> {
  return apiClient.request<ApiStoppageResponse>(`/stoppages/${stoppageId}`, {
    auth: "required",
    body: payload,
    method: "PUT",
    signal,
  });
}

export function deleteStoppage(stoppageId: string, description: string, signal?: AbortSignal): Promise<void> {
  return apiClient.request<void>(`/stoppages/${stoppageId}`, {
    auth: "required",
    body: { description },
    method: "DELETE",
    signal,
  });
}

export function listActiveStoppages(
  params: ActiveStoppageParams = {},
  signal?: AbortSignal,
): Promise<ApiStoppageResponse[]> {
  const query = new URLSearchParams();
  appendOptionalParam(query, "stationId", params.stationId);

  return apiClient.request<ApiStoppageResponse[]>(pathWithQuery("/stoppages/active", query), {
    auth: "required",
    signal,
  });
}

export function getStoppage(stoppageId: string, signal?: AbortSignal): Promise<ApiStoppageDetailResponse> {
  return apiClient.request<ApiStoppageDetailResponse>(`/stoppages/${stoppageId}`, {
    auth: "required",
    signal,
  });
}

export function listStoppageShiftSlices(
  stoppageId: string,
  signal?: AbortSignal,
): Promise<ApiStoppageShiftSliceResponse[]> {
  return apiClient.request<ApiStoppageShiftSliceResponse[]>(`/stoppages/${stoppageId}/shift-slices`, {
    auth: "required",
    signal,
  });
}

function measurementListQuery(params: PhMeasurementListParams): URLSearchParams {
  const query = commonListQuery(params);
  appendOptionalParam(query, "type", params.type);
  query.set("includeDeleted", String(params.includeDeleted ?? false));
  return query;
}

function flowListQuery(params: FlowMeasurementListParams): URLSearchParams {
  const query = commonListQuery(params);
  query.set("includeDeleted", String(params.includeDeleted ?? false));
  return query;
}

function limeListQuery(params: LimeRecordListParams): URLSearchParams {
  const query = commonListQuery(params);
  appendOptionalParam(query, "type", params.type);
  return query;
}

function stoppageListQuery(params: StoppageListParams): URLSearchParams {
  const query = commonListQuery(params);
  appendOptionalParam(query, "status", params.status);
  appendOptionalParam(query, "reason", params.reason);
  query.set("includeDeleted", String(params.includeDeleted ?? false));
  return query;
}

function commonListQuery(params: OperationalListParams): URLSearchParams {
  const period = resolveOperationalPeriod(params);
  const query = new URLSearchParams({
    consolidated: String(params.consolidated ?? false),
  });

  appendOptionalParam(query, "endAt", period.endAt);
  appendOptionalParam(query, "startAt", period.startAt);
  appendOptionalParam(query, "stationId", params.stationId);
  appendOptionalParam(query, "authorUserId", params.authorUserId);
  appendOptionalParam(query, "page", params.page);
  appendOptionalParam(query, "size", params.size);
  return query;
}

const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function toStartOfDayInstant(value: string): string {
  return new Date(`${value}T00:00:00`).toISOString();
}

function toEndOfDayInstant(value: string): string {
  return new Date(`${value}T23:59:59.999`).toISOString();
}

function resolveOperationalPeriod(params: OperationalPeriodParams): OperationalPeriod {
  const endAt = params.endAt && DATE_ONLY_PATTERN.test(params.endAt) ? toEndOfDayInstant(params.endAt) : params.endAt;
  const startAt = params.startAt && DATE_ONLY_PATTERN.test(params.startAt) ? toStartOfDayInstant(params.startAt) : params.startAt;

  return { endAt, startAt };
}

function appendOptionalParam(query: URLSearchParams, name: string, value: boolean | number | string | undefined): void {
  if (value !== undefined && value !== "") {
    query.set(name, String(value));
  }
}

function pathWithQuery(path: string, query: URLSearchParams): string {
  const queryString = query.toString();
  return queryString ? `${path}?${queryString}` : path;
}
