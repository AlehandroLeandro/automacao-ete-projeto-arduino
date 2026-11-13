import { createApiClient } from "./api";
import type { AuthTokensResponse } from "../types";

interface LoginPayload {
  password: string;
  username: string;
}

interface ChangePasswordPayload {
  confirmPassword: string;
  currentPassword: string;
  newPassword: string;
}

const apiClient = createApiClient();

export function login(payload: LoginPayload, signal?: AbortSignal): Promise<AuthTokensResponse> {
  return apiClient.request<AuthTokensResponse>("/auth/login", {
    auth: "none",
    body: payload,
    method: "POST",
    retryOnUnauthorized: false,
    signal,
  });
}

export function changePassword(payload: ChangePasswordPayload, signal?: AbortSignal): Promise<void> {
  return apiClient.request<void>("/auth/change-password", {
    auth: "required",
    body: payload,
    method: "POST",
    signal,
  });
}
