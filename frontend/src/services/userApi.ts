import { createApiClient } from "./api";
import type { UserDirectoryEntry, UserRole } from "../types";

export interface ApiUser {
  id: string;
  name: string;
  username: string;
  role: UserRole;
  stationId?: string | null;
  stationName?: string | null;
  changePassword: boolean;
  active: boolean;
}

export interface CreateUserPayload {
  name: string;
  username: string;
  role: UserRole;
  stationId?: string | null;
}

export interface UpdateUserPayload {
  name: string;
  role: UserRole;
  stationId?: string | null;
  active: boolean;
}

export interface ApiUserCreatedResponse {
  user: ApiUser;
  temporaryPassword: string;
}

const apiClient = createApiClient();

export function listUsers(signal?: AbortSignal): Promise<ApiUser[]> {
  return apiClient.request<ApiUser[]>("/users", {
    auth: "required",
    signal,
  });
}

export function listUserDirectory(signal?: AbortSignal): Promise<UserDirectoryEntry[]> {
  return apiClient.request<UserDirectoryEntry[]>("/users/directory", {
    auth: "required",
    signal,
  });
}

export function createUser(payload: CreateUserPayload, signal?: AbortSignal): Promise<ApiUserCreatedResponse> {
  return apiClient.request<ApiUserCreatedResponse>("/users", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}

export function updateUser(
  userId: string,
  payload: UpdateUserPayload,
  signal?: AbortSignal,
): Promise<ApiUser> {
  return apiClient.request<ApiUser>(`/users/${userId}`, {
    auth: "required",
    body: payload,
    method: "PUT",
    signal,
  });
}

export function resetUserPassword(userId: string, signal?: AbortSignal): Promise<ApiUserCreatedResponse> {
  return apiClient.request<ApiUserCreatedResponse>(`/users/${userId}/reset-password`, {
    auth: "required",
    method: "POST",
    signal,
  });
}
