import { createApiClient } from "./api";
import type { ApiAuthMode } from "../types";
import type { ApiLimeRecordType, ApiStoppageReason, ApiStoppageStatus } from "../utils/apiAdapters";

export type ApiDashboardOperationStatus = "WORKING" | "STOPPED";
export type ApiDashboardMetric = "FLOW" | "RAW_PH" | "TREATED_PH" | "LIME";

export interface DashboardRequestParams {
  auth: ApiAuthMode;
  consolidated: boolean;
  endAt: string;
  startAt: string;
  stationId?: string;
}

export interface DashboardMeasurementResponse {
  stationId: string;
  stationName: string;
  value?: number | string | null;
  unit: string;
  measuredAt?: string | null;
}

export interface DashboardMeasurementAverageResponse {
  value?: number | string | null;
  unit: string;
  sampleCount: number;
  startAt: string;
  endAt: string;
}

export interface DashboardCurrentDayAveragesResponse {
  startAt: string;
  endAt: string;
  rawPh?: DashboardMeasurementAverageResponse | null;
  treatedPh?: DashboardMeasurementAverageResponse | null;
  flow?: DashboardMeasurementAverageResponse | null;
  workedHoursToday: number | string;
  flowAverageToday: number | string;
  flowTotalToday: number | string;
  flowAveragePerWorkedHour: number | string;
}

export interface DashboardSeriesPointResponse {
  measuredAt: string;
  value: number | string;
}

export interface DashboardSeriesResponse {
  stationId: string;
  stationName: string;
  metric: ApiDashboardMetric;
  unit: string;
  points: DashboardSeriesPointResponse[];
}

export interface DashboardSeriesCollectionResponse {
  flow: DashboardSeriesResponse[];
  rawPh: DashboardSeriesResponse[];
  treatedPh: DashboardSeriesResponse[];
  lime: DashboardSeriesResponse[];
}

export interface DashboardStoppageResponse {
  stationId: string;
  stationName: string;
  reason: ApiStoppageReason;
  reasonDetail?: string | null;
  startAt: string;
  elapsedMinutes: number;
}

export interface DashboardLimeLogResponse {
  id: string;
  stationId: string;
  stationName: string;
  type: ApiLimeRecordType;
  quantityBags: number | string;
  recordedAt: string;
}

export interface DashboardStoppageLogResponse {
  id: string;
  stationId: string;
  stationName: string;
  reason: ApiStoppageReason;
  reasonDetail?: string | null;
  status: ApiStoppageStatus;
  startAt: string;
  endAt?: string | null;
  durationMinutes: number;
}

export interface DashboardActivityResponse {
  limeLogs: DashboardLimeLogResponse[];
  stoppageLogs: DashboardStoppageLogResponse[];
}

export interface OperationalStatusResponse {
  status: ApiDashboardOperationStatus;
  activeStoppages: DashboardStoppageResponse[];
}

export interface StationDashboardResponse {
  stationId: string;
  stationName: string;
  limeStockBags: number | string | null;
  limeConsumptionLastShiftBags: number | string;
  latestRawPh?: DashboardMeasurementResponse | null;
  latestTreatedPh?: DashboardMeasurementResponse | null;
  latestFlow?: DashboardMeasurementResponse | null;
  operation: OperationalStatusResponse;
  workedHoursToday: number | string;
}

export interface DashboardResponse {
  consolidated: boolean;
  stationId?: string | null;
  generatedAt: string;
  period: {
    startAt: string;
    endAt: string;
  };
  limeStockBags: number | string | null;
  limeConsumptionLastShiftBags: number | string;
  currentDayAverages: DashboardCurrentDayAveragesResponse;
  series: DashboardSeriesCollectionResponse | null;
  activity: DashboardActivityResponse | null;
  latestRawPh?: DashboardMeasurementResponse | null;
  latestTreatedPh?: DashboardMeasurementResponse | null;
  latestFlow?: DashboardMeasurementResponse | null;
  operation: OperationalStatusResponse;
  stations: StationDashboardResponse[];
}

const apiClient = createApiClient();

export function getDashboard(params: DashboardRequestParams, signal?: AbortSignal): Promise<DashboardResponse> {
  const query = new URLSearchParams({
    consolidated: String(params.consolidated),
    endAt: params.endAt,
    startAt: params.startAt,
  });

  if (params.stationId) {
    query.set("stationId", params.stationId);
  }

  return apiClient.request<DashboardResponse>(`/dashboard?${query.toString()}`, {
    auth: params.auth,
    signal,
  });
}
