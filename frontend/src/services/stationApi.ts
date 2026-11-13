import { createApiClient } from "./api";
import type { ApiStation } from "../types";

const apiClient = createApiClient();

export function listStations(signal?: AbortSignal): Promise<ApiStation[]> {
  return apiClient.request<ApiStation[]>("/stations", {
    auth: "required",
    signal,
  });
}

export function updateLimeThreshold(
  stationId: string,
  minLimeStockBags: number | null,
  signal?: AbortSignal,
): Promise<ApiStation> {
  return apiClient.request<ApiStation>(`/stations/${stationId}/lime-threshold`, {
    auth: "required",
    body: { minLimeStockBags },
    method: "PUT",
    signal,
  });
}
