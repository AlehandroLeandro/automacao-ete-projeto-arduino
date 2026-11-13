import { appConfig } from "../config/env";
import {
  authSessionFromTokens,
  clearStoredAuthSession,
  readStoredAuthSession,
  saveAuthSession,
} from "./authSession";
import type { ApiAuthMode, ApiErrorBody, ApiHttpMethod, ApiStation, ApiStoppage, AuthSession, AuthTokensResponse } from "../types";

interface ReportExportParams {
  accessToken: string;
  consolidated: boolean;
  endAt: string;
  filename: string;
  startAt: string;
  stationId?: string;
  type: "DAILY_MEASUREMENT_AVERAGES";
}

interface ApiClientOptions {
  baseUrl?: string;
  clearSession?: () => void;
  fetch?: typeof fetch;
  readSession?: () => AuthSession | null;
  redirectToLogin?: () => void;
  saveSession?: (session: AuthSession) => void;
}

interface ApiRequestOptions {
  accessToken?: string;
  auth?: ApiAuthMode;
  body?: unknown;
  headers?: HeadersInit;
  method?: ApiHttpMethod;
  retryOnUnauthorized?: boolean;
  signal?: AbortSignal;
}

interface ApiBlobRequestOptions {
  accessToken?: string;
  auth?: ApiAuthMode;
  headers?: HeadersInit;
  retryOnUnauthorized?: boolean;
  signal?: AbortSignal;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly body?: ApiErrorBody;

  constructor(
    message: string,
    status: number,
    body?: ApiErrorBody,
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export interface ApiClient {
  blobRequest(path: string, options?: ApiBlobRequestOptions): Promise<Blob>;
  request<TResponse>(path: string, options?: ApiRequestOptions): Promise<TResponse>;
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = options.baseUrl ?? appConfig.apiUrl;
  const fetchImpl = options.fetch ?? fetch.bind(globalThis);
  const readSession = options.readSession ?? readStoredAuthSession;
  const saveSession = options.saveSession ?? saveAuthSession;
  const clearSession = options.clearSession ?? clearStoredAuthSession;
  const redirectToLogin = options.redirectToLogin ?? defaultRedirectToLogin;
  let refreshInFlight: Promise<AuthSession> | null = null;

  async function request<TResponse>(
    path: string,
    requestOptions: ApiRequestOptions = {},
  ): Promise<TResponse> {
    const response = await sendRequest(path, requestOptions);

    if (shouldRefresh(response, requestOptions)) {
      const nextSession = await refreshSession(requestOptions.signal);
      const retryResponse = await sendRequest(path, {
        ...requestOptions,
        accessToken: nextSession.accessToken,
        retryOnUnauthorized: false,
      });
      return parseResponse<TResponse>(retryResponse);
    }

    return parseResponse<TResponse>(response);
  }

  async function blobRequest(path: string, requestOptions: ApiBlobRequestOptions = {}): Promise<Blob> {
    const response = await sendRequest(path, {
      ...requestOptions,
      method: "GET",
    });

    if (shouldRefresh(response, requestOptions)) {
      const nextSession = await refreshSession(requestOptions.signal);
      const retryResponse = await sendRequest(path, {
        ...requestOptions,
        accessToken: nextSession.accessToken,
        method: "GET",
        retryOnUnauthorized: false,
      });

      if (!retryResponse.ok) {
        throw await apiErrorFromResponse(retryResponse);
      }

      return retryResponse.blob();
    }

    if (!response.ok) {
      throw await apiErrorFromResponse(response);
    }

    return response.blob();
  }

  async function sendRequest(path: string, requestOptions: ApiRequestOptions): Promise<Response> {
    const headers = new Headers(requestOptions.headers);
    const session = readSession();
    const accessToken = requestOptions.accessToken ?? session?.accessToken;
    const auth = requestOptions.auth ?? (accessToken ? "required" : "none");

    headers.set("Accept", headers.get("Accept") ?? "application/json");

    if (requestOptions.body !== undefined && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    if (auth !== "none" && accessToken) {
      headers.set("Authorization", `Bearer ${accessToken}`);
    }

    return fetchImpl(`${baseUrl}${path}`, {
      body: requestOptions.body !== undefined ? JSON.stringify(requestOptions.body) : undefined,
      headers,
      method: requestOptions.method ?? "GET",
      signal: requestOptions.signal,
    });
  }

  function shouldRefresh(response: Response, requestOptions: ApiRequestOptions): boolean {
    const auth = requestOptions.auth ?? "required";
    return response.status === 401 && auth !== "none" && requestOptions.retryOnUnauthorized !== false;
  }

  async function refreshSession(signal?: AbortSignal): Promise<AuthSession> {
    const session = readSession();

    if (!session) {
      expireSession();
      throw new ApiError("Sessão expirada. Entre novamente.", 401);
    }

    refreshInFlight ??= request<AuthTokensResponse>("/auth/refresh", {
      auth: "none",
      body: { refreshToken: session.refreshToken },
      method: "POST",
      retryOnUnauthorized: false,
    })
      .then((response) => {
        const nextSession = authSessionFromTokens(response);
        saveSession(nextSession);
        return nextSession;
      })
      .catch((error) => {
        expireSession();
        throw error;
      })
      .finally(() => {
        refreshInFlight = null;
      });

    return waitForRefresh(refreshInFlight, signal);
  }

  function expireSession(): void {
    clearSession();
    redirectToLogin();
  }

  return { blobRequest, request };
}

const defaultClient = createApiClient();

export async function listStations(): Promise<ApiStation[]> {
  return defaultClient.request<ApiStation[]>("/stations", { auth: "none" });
}

export async function listActiveStoppages(stationId: string, accessToken: string): Promise<ApiStoppage[]> {
  const params = new URLSearchParams({ stationId });
  return defaultClient.request<ApiStoppage[]>(`/stoppages/active?${params.toString()}`, {
    accessToken,
    auth: "required",
  });
}

export async function finishStoppage(
  stoppageId: string,
  endAt: string,
  accessToken: string,
): Promise<ApiStoppage> {
  return defaultClient.request<ApiStoppage>(`/stoppages/${stoppageId}/finish`, {
    accessToken,
    auth: "required",
    body: { endAt },
    method: "POST",
  });
}

export async function downloadReportXlsx({
  accessToken,
  consolidated,
  endAt,
  filename,
  startAt,
  stationId,
  type,
}: ReportExportParams): Promise<void> {
  const params = new URLSearchParams({
    consolidated: String(consolidated),
    endAt,
    startAt,
  });

  if (stationId) {
    params.set("stationId", stationId);
  }

  const blob = await defaultClient.blobRequest(`/reports/${type}/export.xlsx?${params.toString()}`, {
    accessToken,
    auth: "required",
    headers: {
      Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    },
  });
  triggerDownload(blob, filename);
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

async function parseResponse<TResponse>(response: Response): Promise<TResponse> {
  if (!response.ok) {
    throw await apiErrorFromResponse(response);
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  return response.json() as Promise<TResponse>;
}

async function apiErrorFromResponse(response: Response): Promise<ApiError> {
  const body = await parseErrorBody(response);
  return new ApiError(body.message || body.detail || body.error || `Falha na API (${response.status}).`, response.status, body);
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody & { detail?: string; error?: string }> {
  try {
    return (await response.json()) as ApiErrorBody & { detail?: string; error?: string };
  } catch {
    return {};
  }
}

function waitForRefresh(refreshPromise: Promise<AuthSession>, signal?: AbortSignal): Promise<AuthSession> {
  if (!signal) {
    return refreshPromise;
  }

  if (signal.aborted) {
    return Promise.reject(abortError());
  }

  return Promise.race([
    refreshPromise,
    new Promise<AuthSession>((_, reject) => {
      signal.addEventListener("abort", () => reject(abortError()), { once: true });
    }),
  ]);
}

function abortError(): DOMException {
  return new DOMException("Request aborted", "AbortError");
}

function defaultRedirectToLogin(): void {
  if (typeof window === "undefined" || window.location.pathname === "/login") {
    return;
  }

  window.location.assign("/login");
}
