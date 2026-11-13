import { createApiClient } from "./api";
import type { ApiNotification } from "../types";

const apiClient = createApiClient();

export function listNotifications(unreadOnly = false, signal?: AbortSignal): Promise<ApiNotification[]> {
  const query = new URLSearchParams({ unreadOnly: String(unreadOnly) });

  return apiClient.request<ApiNotification[]>(`/notifications?${query.toString()}`, {
    auth: "required",
    signal,
  });
}

export function markNotificationRead(id: string, signal?: AbortSignal): Promise<ApiNotification> {
  return apiClient.request<ApiNotification>(`/notifications/${id}/read`, {
    auth: "required",
    method: "PATCH",
    signal,
  });
}
