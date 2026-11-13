import { ApiError, createApiClient } from "./api";
import type { ReportDefinition } from "../types";
import { errorMessage } from "../utils/errors";

export type ReportType =
  | "WORKED_HOURS"
  | "LIME"
  | "FLOW_MIN_MAX"
  | "PH"
  | "FLOW"
  | "STOPPAGES"
  | "DAILY_MEASUREMENT_AVERAGES";

export type ApiReportDefinition = Omit<ReportDefinition, "id"> & {
  id: ReportType;
  xlsxFilenamePrefix: string;
};

export interface ReportRequestParams {
  consolidated: boolean;
  endAt: string;
  startAt: string;
  stationId?: string;
}

export interface ReportRowResponse {
  values: Record<string, unknown>;
}

export interface ReportResponse {
  type: ReportType;
  startAt: string;
  endAt: string;
  stationId?: string | null;
  consolidated: boolean;
  rows: ReportRowResponse[];
}

export interface ReportExportParams extends ReportRequestParams {
  filename: string;
  type: ReportType;
}

export const reportDefinitions: ApiReportDefinition[] = [
  {
    category: "Coletado",
    description: "Leituras de pH de entrada e de saída por estação, em tabelas separadas.",
    id: "PH",
    title: "pH de entrada e saída",
    xlsxFilenamePrefix: "ph",
  },
  {
    category: "Coletado",
    description: "Registros de vazão de água em m³ com a média do período.",
    id: "FLOW",
    title: "Vazão",
    xlsxFilenamePrefix: "vazao",
  },
  {
    category: "Coletado",
    description: "Movimentos de cal (entrada e consumo) com saldo inicial e final.",
    id: "LIME",
    title: "Cal",
    xlsxFilenamePrefix: "cal",
  },
  {
    category: "Coletado",
    description: "Paradas por data, turno, duração total, motivo e status.",
    id: "STOPPAGES",
    title: "Paradas",
    xlsxFilenamePrefix: "paradas",
  },
  {
    category: "Calculado",
    description: "Vazão mínima e máxima registradas por dia.",
    id: "FLOW_MIN_MAX",
    title: "Vazão mínima e máxima",
    xlsxFilenamePrefix: "vazao-min-max",
  },
  {
    category: "Calculado",
    description: "Horas trabalhadas e horas paradas por dia, com total do período.",
    id: "WORKED_HOURS",
    title: "Horas trabalhadas",
    xlsxFilenamePrefix: "horas-trabalhadas",
  },
  {
    category: "Calculado",
    description: "Médias diárias de pH e vazão, com somas de horas trabalhadas e vazão.",
    id: "DAILY_MEASUREMENT_AVERAGES",
    title: "Médias aferições",
    xlsxFilenamePrefix: "medias-afericoes",
  },
];

const apiClient = createApiClient();
const xlsxExportErrorMessage = "Não foi possível exportar o relatório em XLSX.";
const genericApiErrorPattern = /^Falha na API \(\d+\)\.$/;

export function getReport(
  type: ReportType,
  params: ReportRequestParams,
  signal?: AbortSignal,
): Promise<ReportResponse> {
  return apiClient.request<ReportResponse>(`/reports/${type}?${reportQuery(params).toString()}`, {
    auth: "required",
    signal,
  });
}

export async function downloadReportXlsx({
  filename,
  type,
  ...params
}: ReportExportParams): Promise<void> {
  const blob = await apiClient.blobRequest(`/reports/${type}/export.xlsx?${reportQuery(params).toString()}`, {
    auth: "required",
    headers: {
      Accept: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json",
    },
  });

  triggerDownload(blob, filename);
}

export function reportExportErrorMessage(error: unknown): string {
  const message = errorMessage(error, xlsxExportErrorMessage);

  if (error instanceof ApiError && genericApiErrorPattern.test(message)) {
    return xlsxExportErrorMessage;
  }

  return message;
}

function reportQuery(params: ReportRequestParams): URLSearchParams {
  const query = new URLSearchParams({
    consolidated: String(params.consolidated),
    endAt: params.endAt,
    startAt: params.startAt,
  });

  if (params.stationId) {
    query.set("stationId", params.stationId);
  }

  return query;
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
