import { createApiClient, type ApiClient } from "./api";

export type DeviceKeyStatus = "ONLINE" | "OFFLINE" | "NEVER";
export interface DeviceKeyResponse {
  id: string;
  name: string;
  stationId: string;
  stationName: string;
  active: boolean;
  createdAt: string;
  lastSeenAt: string | null;
  status: DeviceKeyStatus;
}
export interface CreateDeviceKeyPayload { name: string; stationId: string }

export function createDeviceKeyApi(client: ApiClient) {
  return {
    listDeviceKeys: (signal?: AbortSignal) => client.request<DeviceKeyResponse[]>("/device-keys", { auth: "required", signal }),
    createDeviceKey: (payload: CreateDeviceKeyPayload, signal?: AbortSignal) => client.request<DeviceKeyResponse>("/device-keys", { auth: "required", method: "POST", body: payload, signal }),
    getDeviceKeySecret: (id: string, signal?: AbortSignal) => client.request<{ value: string }>(`/device-keys/${id}/secret`, { auth: "required", signal }),
    setDeviceKeyActive: (id: string, active: boolean, signal?: AbortSignal) => client.request<DeviceKeyResponse>(`/device-keys/${id}/active`, { auth: "required", method: "PATCH", body: { active }, signal }),
    deleteDeviceKey: (id: string, signal?: AbortSignal) => client.request<void>(`/device-keys/${id}`, { auth: "required", method: "DELETE", signal }),
  };
}

export const { listDeviceKeys, createDeviceKey, getDeviceKeySecret, setDeviceKeyActive, deleteDeviceKey } = createDeviceKeyApi(createApiClient());
