import { FileDown } from "lucide-react";
import { useState } from "react";

import { useAuth } from "../auth/AuthContext";
import { PageHeader } from "../components/PageHeader";
import { useOfflineQueue } from "../offline/useOfflineQueue";
import { getOperationalStationLabel, operationalStationOptions } from "../data/mockData";
import {
  downloadReportXlsx,
  reportDefinitions,
  reportExportErrorMessage,
  type ReportRequestParams,
  type ReportType,
} from "../services/reportApi";
import type { OperationalStationId } from "../types";
import { resolveApiStationId } from "../utils/apiStations";
import { resolveDashboardPeriodRange } from "../utils/dashboardMetrics";
import {
  defaultOperationalStationId,
  hasOperationalStationScope,
  missingOperatorStationLabel,
  operatorStationName,
} from "../utils/stationAccess";

import { getTodayDate, getPastMonthDate } from "../utils/dateTime";

type ReportStationFilter = "all" | OperationalStationId;
// Erro de exportação mantido por card (mensagem inline por relatório).
type ReportExportErrors = Partial<Record<ReportType, string>>;

function reportFilename(prefix: string, startDate: string, endDate: string): string {
  return `${prefix}-${startDate}-a-${endDate}.xlsx`;
}

export function ReportsPage() {
  const { user } = useAuth();
  const { isOffline } = useOfflineQueue();
  const lockedStationName = operatorStationName(user);
  const hasStationScope = hasOperationalStationScope(user);
  const [startDate, setStartDate] = useState(getPastMonthDate());
  const [endDate, setEndDate] = useState(getTodayDate());
  const [station, setStation] = useState<ReportStationFilter>("all");
  const [downloadingReportId, setDownloadingReportId] = useState<ReportType | null>(null);
  const [exportErrors, setExportErrors] = useState<ReportExportErrors>({});
  const selectedStationName = lockedStationName
    ?? (station === "all" ? "Todas as estações" : getOperationalStationLabel(station));

  async function handleExportReport(reportType: ReportType, filenamePrefix: string) {
    const periodRange = resolveDashboardPeriodRange({
      preset: "custom",
      startDate,
      endDate,
    });

    // Validação de período no momento do export: período inválido bloqueia o download.
    if (!periodRange) {
      setExportErrors((current) => ({
        ...current,
        [reportType]: "Informe um período válido para exportar o relatório.",
      }));
      return;
    }

    setDownloadingReportId(reportType);
    // Limpa o erro apenas deste card ao iniciar a exportação.
    setExportErrors((current) => ({ ...current, [reportType]: undefined }));

    try {
      const requestParams = await resolveReportRequestParams(periodRange.startAt, periodRange.endAt);

      await downloadReportXlsx({
        ...requestParams,
        endAt: periodRange.endAt,
        filename: reportFilename(filenamePrefix, periodRange.startDate, periodRange.endDate),
        startAt: periodRange.startAt,
        type: reportType,
      });
    } catch (exportError) {
      setExportErrors((current) => ({
        ...current,
        [reportType]: reportExportErrorMessage(exportError),
      }));
    } finally {
      setDownloadingReportId(null);
    }
  }

  async function resolveReportRequestParams(startAt: string, endAt: string): Promise<ReportRequestParams> {
    const selectedStationMode = lockedStationName ? user?.stationMode : station === "all" ? undefined : station;
    const stationId = selectedStationMode
      ? user?.stationId && lockedStationName
        ? user.stationId
        : await resolveApiStationId(selectedStationMode)
      : undefined;

    return {
      consolidated: !selectedStationMode,
      endAt,
      startAt,
      stationId,
    };
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Análise"
        title="Relatórios"
        description="Relatórios dedicados para informações coletadas e calculadas."
      />

      {isOffline ? <section className="state-panel"><h2>Indisponível offline</h2><p>Relatórios exigem conexão. Registros pendentes podem ser corrigidos em Pendências.</p></section> : null}

      {!isOffline && !hasStationScope ? (
        <section className="state-panel state-panel--error">
          <h2>Acesso indisponível</h2>
          <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
        </section>
      ) : !isOffline ? (
        <>
          <div className="surface-panel form-grid">
            <label className="field" htmlFor="report-start">
              <span>Período inicial</span>
              <input
                id="report-start"
                required
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
              />
            </label>

            <label className="field" htmlFor="report-end">
              <span>Período final</span>
              <input
                id="report-end"
                required
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
              />
            </label>

            {lockedStationName ? (
              <label className="field" htmlFor="report-station">
                <span>Filtro adicional</span>
                <input id="report-station" readOnly value={lockedStationName} />
              </label>
            ) : (
              <label className="field" htmlFor="report-station">
                <span>Filtro adicional</span>
                <select
                  id="report-station"
                  value={station}
                  onChange={(event) => setStation(event.target.value as ReportStationFilter)}
                >
                  <option value="all">Todas as estações</option>
                  {operationalStationOptions.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>

          <section className="report-grid">
            {reportDefinitions.map((report) => (
              <article className="report-card" key={report.id}>
                <div>
                  <span className="badge">{report.category}</span>
                  <h2>{report.title}</h2>
                  <p>{report.description}</p>
                  <small className="muted">
                    Filtro: {selectedStationName || getOperationalStationLabel(defaultOperationalStationId)}
                  </small>
                  {exportErrors[report.id] ? (
                    <small className="form-error">{exportErrors[report.id]}</small>
                  ) : null}
                </div>
                <button
                  className="button button--secondary"
                  disabled={downloadingReportId === report.id}
                  type="button"
                  onClick={() => void handleExportReport(report.id, report.xlsxFilenamePrefix)}
                >
                  <FileDown size={16} aria-hidden="true" />
                  {downloadingReportId === report.id ? "Exportando..." : "Exportar"}
                </button>
              </article>
            ))}
          </section>
        </>
      ) : null}
    </section>
  );
}
