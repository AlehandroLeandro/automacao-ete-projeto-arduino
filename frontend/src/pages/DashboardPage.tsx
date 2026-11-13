import { Box, Droplets, FileDown, Gauge, RotateCcw, Waves, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import {
  canAccessOperationalModules,
  canViewDashboardTrends,
  canViewFullDashboard,
  canWriteOperationalDataForUser,
} from "../auth/permissions";
import {
  DashboardActivityLogs,
  type DashboardLimeLogItem,
  type DashboardStopLogItem,
} from "../components/dashboard/DashboardActivityLogs";
import { DashboardChartGrid, type DashboardChartItem } from "../components/dashboard/DashboardChartGrid";
import { DashboardMetricCard } from "../components/dashboard/DashboardMetricCard";
import { DashboardPeriodFilter } from "../components/dashboard/DashboardPeriodFilter";
import { DashboardScopeSelector } from "../components/dashboard/DashboardScopeSelector";
import { DashboardStatusHero, type DashboardStationStatusItem } from "../components/dashboard/DashboardStatusHero";
import {
  DashboardWorkedHoursCard,
  type WorkedHoursEntry,
} from "../components/dashboard/DashboardWorkedHoursCard";
import { PageHeader } from "../components/PageHeader";
import { useOfflineQueue } from "../offline/useOfflineQueue";
import { TimeInput } from "../components/TimeInput";
import { appConfig } from "../config/env";
import { getOperationalStationLabel, stopReasonText } from "../data/mockData";
import { useAsyncResource } from "../hooks/useAsyncResource";
import { useElapsedTime } from "../hooks/useElapsedTime";
import { downloadReportXlsx } from "../services/api";
import { getDashboard, type DashboardResponse } from "../services/dashboardApi";
import { finishStoppage, listActiveStoppages } from "../services/operationalApi";
import type { DashboardSnapshot, LimeRecord, OperationalStationId, StationMode, StopReason } from "../types";
import { apiStoppageReasonToStopReason, type ApiLimeRecordType } from "../utils/apiAdapters";
import {
  getDefaultDashboardPeriodSelection,
  resolveDashboardPeriodRange,
  type DashboardPeriodRange,
  type DashboardPeriodSelection,
} from "../utils/dashboardMetrics";
import { resolveApiStationId, stationModeFromApiId } from "../utils/apiStations";
import {
  FUTURE_TIMESTAMP_MESSAGE,
  formatDateTime,
  formatDecimalHours,
  formatHoursMinutes,
  formatTime,
  getCurrentTimeHHMM,
  isFutureDateTime,
  mergeTodayWithTime,
} from "../utils/dateTime";
import { errorMessage } from "../utils/errors";
import { hasOperationalStationScope, missingOperatorStationLabel, resolveStationMode } from "../utils/stationAccess";

export function DashboardPage() {
  const { user } = useAuth();
  const { isOffline } = useOfflineQueue();
  const [searchParams] = useSearchParams();
  const [stationMode, setStationMode] = useState<StationMode>(appConfig.defaultStationMode);
  const requestedStationId = searchParams.get("station");

  // Notificações de parada/estoque apontam para /app/dashboard?station=<id>.
  // Posiciona o seletor de escopo na ETE informada ao montar/trocar o parâmetro.
  useEffect(() => {
    if (!requestedStationId) {
      return;
    }

    let active = true;

    stationModeFromApiId(requestedStationId)
      .then((resolvedStationMode) => {
        if (active && resolvedStationMode) {
          setStationMode(resolvedStationMode);
        }
      })
      .catch(() => {
        // ITE não encontrada: mantém o escopo atual sem interromper o dashboard.
      });

    return () => {
      active = false;
    };
  }, [requestedStationId]);

  const effectiveStationMode = resolveStationMode(user, stationMode);
  const hasStationScope = hasOperationalStationScope(user);

  const content = isOffline ? (
    <section className="page-stack"><PageHeader eyebrow="Dashboard" title="Indisponível offline" description="Dashboard e relatórios exigem conexão. Registros operacionais continuam disponíveis em Pendências." /></section>
  ) : hasStationScope && effectiveStationMode ? (
    <DashboardContent
      onStationModeChange={setStationMode}
      stationMode={effectiveStationMode}
      user={user}
    />
  ) : (
    <DashboardAccessDenied />
  );

  return content;
}

interface DashboardContentProps {
  onStationModeChange: (value: StationMode) => void;
  stationMode: StationMode;
  user: ReturnType<typeof useAuth>["user"];
}

function DashboardContent({
  onStationModeChange,
  stationMode,
  user,
}: DashboardContentProps) {
  const { session } = useAuth();
  const [periodSelection, setPeriodSelection] = useState<DashboardPeriodSelection>(() =>
    getDefaultDashboardPeriodSelection(),
  );
  const [returnTime, setReturnTime] = useState(getCurrentTimeHHMM());
  const [isReturnModalOpen, setIsReturnModalOpen] = useState(false);
  const [isFinishingReturn, setIsFinishingReturn] = useState(false);
  const [isDownloadingAverageReport, setIsDownloadingAverageReport] = useState(false);
  const [returnError, setReturnError] = useState<string | null>(null);
  const [averageReportError, setAverageReportError] = useState<string | null>(null);
  const periodRange = useMemo(() => resolveDashboardPeriodRange(periodSelection), [periodSelection]);
  const dashboardResource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!periodRange) {
          return null;
        }

        const stationId = stationMode === "consolidated" ? undefined : await resolveApiStationId(stationMode);
        return getDashboard(
          {
            auth: "required",
            consolidated: stationMode === "consolidated",
            endAt: periodRange.endAt,
            startAt: periodRange.startAt,
            stationId,
          },
          signal,
        );
      },
      [periodRange, stationMode],
    ),
    [],
  );
  // Stale-while-revalidate (AD-004): guardamos a última resposta válida para
  // manter cards e gráficos visíveis durante refetch, troca de período/ITE ou
  // período inválido — sem remontar cabeçalho e filtros.
  const lastLoadedDashboardRef = useRef<DashboardResponse | null>(null);
  if (dashboardResource.data) {
    lastLoadedDashboardRef.current = dashboardResource.data;
  }
  const dashboard = dashboardResource.data ?? lastLoadedDashboardRef.current;
  const snapshot = useMemo(
    () => (dashboard ? dashboardResponseToSnapshot(dashboard) : null),
    [dashboard],
  );
  const activeStop = useMemo(
    () => (dashboard ? getActiveDashboardStop(dashboard, stationMode) : null),
    [dashboard, stationMode],
  );
  const stationStatuses = useMemo(
    () => (dashboard ? getStationStatusesFromDashboard(dashboard, stationMode) : []),
    [dashboard, stationMode],
  );
  const effectiveStatus = snapshot ? getEffectiveStatus(snapshot, stationStatuses) : "operating";
  const statusLabel = getStatusLabel(effectiveStatus, stationStatuses);
  const elapsedTime = useElapsedTime(snapshot?.stoppedSince);
  const statusDescription = snapshot
    ? getStatusDescription(snapshot, stationMode, elapsedTime, stationStatuses)
    : "Carregando status operacional.";
  const latestMetrics = useMemo(
    () => (dashboard ? getLatestMetricsFromDashboard(dashboard) : []),
    [dashboard],
  );
  const averageMetrics = useMemo(
    () => (dashboard ? getAverageMetricsFromDashboard(dashboard) : []),
    [dashboard],
  );
  const charts = useMemo(
    () => (dashboard ? buildDashboardChartsFromResponse(dashboard) : []),
    [dashboard],
  );
  const activityLogs = useMemo(
    () => (dashboard ? buildDashboardActivityLogsFromResponse(dashboard) : emptyDashboardActivityLogs),
    [dashboard],
  );
  const workedHoursEntries = useMemo(
    () => (dashboard ? buildWorkedHoursEntries(dashboard, stationMode) : []),
    [dashboard, stationMode],
  );
  const canRegisterReturn =
    canWriteOperationalDataForUser(user) &&
    Boolean(activeStop) &&
    stationMode !== "consolidated";
  // OPAUT-08/OPAUT-09: o operador vê apenas cabeçalho, status e médias do dia.
  const showFullDashboard = canViewFullDashboard(user?.role);
  // Perfil Visualização: leituras, gráfico e log restritos a vazão e paradas.
  const showTrends = canViewDashboardTrends(user?.role);
  const flowAndStopsOnly = !canAccessOperationalModules(user?.role);
  const visibleLatestMetrics = flowAndStopsOnly ? latestMetrics.filter(isFlowMetric) : latestMetrics;
  const visibleAverageMetrics = flowAndStopsOnly ? averageMetrics.filter(isFlowMetric) : averageMetrics;
  const visibleCharts = flowAndStopsOnly ? charts.filter((chart) => chart.id === "flow") : charts;

  // Estados de renderização: cabeçalho, seletor de escopo e filtro de período
  // permanecem SEMPRE montados; só a região de cards+gráficos reage.
  const hasContent = Boolean(snapshot);
  const isRefreshing = dashboardResource.isLoading && hasContent;
  const isInitialLoading = dashboardResource.isLoading && !hasContent;
  const hasBlockingError = Boolean(dashboardResource.error) && !hasContent;
  const showRefreshErrorBanner = Boolean(dashboardResource.error) && hasContent;

  function openReturnModal() {
    setReturnTime(getCurrentTimeHHMM());
    setReturnError(null);
    setIsReturnModalOpen(true);
  }

  function closeReturnModal() {
    if (isFinishingReturn) {
      return;
    }

    setIsReturnModalOpen(false);
    setReturnError(null);
  }

  async function handleRegisterReturn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!activeStop || stationMode === "consolidated") {
      return;
    }

    if (!session?.accessToken) {
      setReturnError("Sessão expirada. Entre novamente.");
      return;
    }

    const endedAt = mergeTodayWithTime(returnTime);
    if (isFutureDateTime(endedAt)) {
      setReturnError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    if (new Date(endedAt).getTime() <= new Date(activeStop.startedAt).getTime()) {
      setReturnError("O horário de retorno deve ser posterior ao início da parada.");
      return;
    }

    setIsFinishingReturn(true);
    setReturnError(null);

    try {
      const stoppageId = await resolveActiveApiStoppageId(stationMode);
      await finishStoppage(stoppageId, { endAt: endedAt });
      dashboardResource.reload();
      setIsReturnModalOpen(false);
    } catch (error) {
      setReturnError(errorMessage(error, "Não foi possível registrar o retorno."));
    } finally {
      setIsFinishingReturn(false);
    }
  }

  async function handleDownloadAverageReport() {
    if (!periodRange) {
      setAverageReportError("Selecione um período válido para emitir o relatório.");
      return;
    }

    if (!session?.accessToken) {
      setAverageReportError("Sessão expirada. Entre novamente.");
      return;
    }

    setIsDownloadingAverageReport(true);
    setAverageReportError(null);

    try {
      const stationId = stationMode === "consolidated" ? undefined : await resolveApiStationId(stationMode);
      await downloadReportXlsx({
        accessToken: session.accessToken,
        consolidated: stationMode === "consolidated",
        endAt: periodRange.endAt,
        filename: averageReportFilename(periodRange.startDate, periodRange.endDate),
        startAt: periodRange.startAt,
        stationId,
        type: "DAILY_MEASUREMENT_AVERAGES",
      });
    } catch (error) {
      setAverageReportError(errorMessage(error, "Não foi possível emitir o relatório."));
    } finally {
      setIsDownloadingAverageReport(false);
    }
  }

  return (
    <section className="dashboard">
      {/* Cabeçalho e seletor de escopo permanecem sempre montados (AD-004) */}
      <PageHeader
        eyebrow="Estação de tratamento de efluentes"
        title="Dashboard operacional"
        description={
          flowAndStopsOnly
            ? "Acompanhamento da vazão e das paradas da estação."
            : "Acompanhamento das leituras, estoque de cal e status de funcionamento."
        }
      />

      {showFullDashboard ? (
        <div className="dashboard__scope">
          <DashboardScopeSelector value={stationMode} onChange={onStationModeChange} user={user} />
        </div>
      ) : null}

      {/* Erro em refetch com dados anteriores: faixa discreta, sem tela cheia */}
      {showRefreshErrorBanner ? (
        <div className="dashboard__refetch-error" role="alert">
          <p>{dashboardResource.error ?? "Não foi possível atualizar os indicadores."}</p>
          <button className="button button--secondary" type="button" onClick={dashboardResource.reload}>
            Tentar novamente
          </button>
        </div>
      ) : null}

      {/* Indicador de "atualizando" fora da área com opacidade reduzida */}
      {isRefreshing ? (
        <p className="dashboard__refreshing" role="status">
          <span className="spinner spinner--sm" aria-hidden="true" />
          Atualizando indicadores...
        </p>
      ) : null}

      {/* Região superior de cards: hero + leituras + médias. Só ela reage. */}
      <div
        aria-busy={isRefreshing}
        className={isRefreshing ? "dashboard__data dashboard__data--refreshing" : "dashboard__data"}
      >
        {snapshot ? (
          <>
            <DashboardStatusHero
              action={
                canRegisterReturn ? (
                  <button className="button button--primary" type="button" onClick={openReturnModal}>
                    <RotateCcw size={17} aria-hidden="true" />
                    Registrar retorno
                  </button>
                ) : null
              }
              description={statusDescription}
              stationStatuses={stationStatuses}
              status={effectiveStatus}
              statusLabel={statusLabel}
              updatedAtLabel={`Atualizado em ${formatDateTime(snapshot.updatedAt)}`}
            />

            {showTrends ? (
              <section className="dashboard-section" aria-labelledby="latest-readings-title">
                <p className="dashboard-section__title" id="latest-readings-title">
                  Última leitura
                </p>
                <div className="dashboard-card-grid">
                  {visibleLatestMetrics.map((metric) => (
                    <DashboardMetricCard key={metric.label} {...metric} />
                  ))}
                </div>
              </section>
            ) : null}

            <section className="dashboard-section" aria-labelledby="average-readings-title">
              <p className="dashboard-section__title" id="average-readings-title">
                Médias do dia
              </p>
              <div className="dashboard-card-grid">
                {visibleAverageMetrics.map((metric) => (
                  <DashboardMetricCard key={metric.label} {...metric} />
                ))}
                <DashboardWorkedHoursCard entries={workedHoursEntries} helper="Hoje, descontando paradas" />
              </div>
            </section>
          </>
        ) : isInitialLoading ? (
          <section className="state-panel">
            <span className="spinner" aria-hidden="true" />
            <h2>Carregando dashboard</h2>
            <p>Buscando indicadores operacionais no backend.</p>
          </section>
        ) : hasBlockingError ? (
          <section className="state-panel state-panel--error">
            <h2>Não foi possível carregar</h2>
            <p>{dashboardResource.error ?? "A API não retornou dados para o dashboard."}</p>
            <button className="button button--secondary" type="button" onClick={dashboardResource.reload}>
              Tentar novamente
            </button>
          </section>
        ) : (
          <section className="state-panel">
            <h2>Ajuste o período</h2>
            <p>Selecione um período válido para visualizar os indicadores.</p>
          </section>
        )}
      </div>

      {/* Filtro de período sempre montado (mesma instância) */}
      {showTrends ? (
        <DashboardPeriodFilter value={periodSelection} onChange={setPeriodSelection} />
      ) : null}
      {showFullDashboard ? (
        <div className="dashboard-report-action">
          {averageReportError ? <p className="form-error">{averageReportError}</p> : null}
          <button
            className="button button--primary"
            disabled={isDownloadingAverageReport}
            type="button"
            onClick={handleDownloadAverageReport}
          >
            <FileDown size={17} aria-hidden="true" />
            {isDownloadingAverageReport ? "Emitindo..." : "Emitir relatório de médias"}
          </button>
        </div>
      ) : null}

      {/* Região inferior de gráficos + logs — só quando há dados a exibir */}
      {showTrends && snapshot ? (
        <div
          aria-busy={isRefreshing}
          className={isRefreshing ? "dashboard__data dashboard__data--refreshing" : "dashboard__data"}
        >
          <DashboardChartGrid charts={visibleCharts}>
            <DashboardActivityLogs
              limeLogs={activityLogs.limeLogs}
              showLimeLogs={!flowAndStopsOnly}
              stopLogs={activityLogs.stopLogs}
            />
          </DashboardChartGrid>
        </div>
      ) : null}

      {isReturnModalOpen && activeStop ? (
        <ReturnStoppageModal
          error={returnError}
          isSubmitting={isFinishingReturn}
          returnTime={returnTime}
          stop={activeStop}
          onClose={closeReturnModal}
          onReturnTimeChange={setReturnTime}
          onSubmit={handleRegisterReturn}
        />
      ) : null}
    </section>
  );
}

type MetricCardConfig = Parameters<typeof DashboardMetricCard>[0];

function isFlowMetric(metric: MetricCardConfig): boolean {
  return metric.tone === "flow";
}

interface DashboardActiveStop {
  reason: StopReason;
  reasonDetail?: string;
  startedAt: string;
  station: string;
}

function ReturnStoppageModal({
  error,
  isSubmitting,
  onClose,
  onReturnTimeChange,
  onSubmit,
  returnTime,
  stop,
}: {
  error: string | null;
  isSubmitting: boolean;
  onClose: () => void;
  onReturnTimeChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  returnTime: string;
  stop: DashboardActiveStop;
}) {
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <form className="audit-modal return-stop-modal" onSubmit={onSubmit}>
        <div className="audit-modal__header">
          <div>
            <p className="eyebrow">Parada ativa</p>
            <h2>Registrar retorno</h2>
          </div>
          <button className="icon-button" type="button" aria-label="Fechar" onClick={onClose}>
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div className="active-stop">
          <span className="badge badge--danger">Parada ativa</span>
          <strong>{stop.station}</strong>
          <p>
            {stopReasonText(stop.reason, stop.reasonDetail)} desde {formatDateTime(stop.startedAt)}
          </p>
        </div>

        <TimeInput
          disabled={isSubmitting}
          id="dashboard-stop-return-time"
          label="Hora do retorno"
          max={getCurrentTimeHHMM()}
          value={returnTime}
          onChange={onReturnTimeChange}
        />

        {error ? <p className="form-error">{error}</p> : null}

        <div className="audit-modal__actions">
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            <RotateCcw size={17} aria-hidden="true" />
            {isSubmitting ? "Salvando..." : "Confirmar"}
          </button>
        </div>
      </form>
    </div>
  );
}

async function resolveActiveApiStoppageId(stationMode: OperationalStationId): Promise<string> {
  const stationId = await resolveApiStationId(stationMode);
  const activeStoppages = await listActiveStoppages({ stationId });
  const activeStoppage = activeStoppages.find((item) => item.status === "ACTIVE");

  if (!activeStoppage) {
    throw new Error("Nenhuma parada ativa foi encontrada na API para esta ITE.");
  }

  return activeStoppage.id;
}

function averageReportFilename(startDate: string, endDate: string): string {
  return `medias-afericoes-${startDate}-a-${endDate}.xlsx`;
}

function dashboardResponseToSnapshot(response: DashboardResponse): DashboardSnapshot {
  const activeStop = response.operation.activeStoppages[0];

  return {
    flowLitersPerHour: measurementValue(response.latestFlow),
    inputPh: measurementValue(response.latestRawPh),
    limeConsumptionLastShiftBags: decimalToNumber(response.limeConsumptionLastShiftBags),
    limeStockBags: decimalToNumber(response.limeStockBags),
    outputPh: measurementValue(response.latestTreatedPh),
    station: response.consolidated ? "consolidated" : stationModeFromDashboardResponse(response),
    status: response.operation.status === "STOPPED" ? "stopped" : "operating",
    stoppedReason: activeStop
      ? stopReasonText(apiStoppageReasonToStopReason(activeStop.reason), activeStop.reasonDetail)
      : undefined,
    stoppedSince: activeStop?.startAt,
    updatedAt: response.generatedAt,
  };
}

function stationModeFromDashboardResponse(response: DashboardResponse): OperationalStationId {
  const stationName = response.latestFlow?.stationName ?? response.latestRawPh?.stationName ?? response.stations[0]?.stationName ?? "";
  return getStationModeByName(stationName);
}

function getActiveDashboardStop(response: DashboardResponse, stationMode: StationMode): DashboardActiveStop | null {
  if (stationMode === "consolidated") {
    return null;
  }

  const stationName = getOperationalStationLabel(stationMode);
  const activeStop = response.operation.activeStoppages.find((stoppage) =>
    sameStationName(stoppage.stationName, stationName),
  );

  if (!activeStop) {
    return null;
  }

  return {
    reason: apiStoppageReasonToStopReason(activeStop.reason),
    reasonDetail: activeStop.reasonDetail ?? undefined,
    startedAt: activeStop.startAt,
    station: activeStop.stationName,
  };
}

function getStationStatusesFromDashboard(
  response: DashboardResponse,
  stationMode: StationMode,
): DashboardStationStatusItem[] {
  if (stationMode !== "consolidated") {
    return [];
  }

  return response.stations.map((station) => {
    const activeStop = station.operation.activeStoppages[0];

    return {
      description: activeStop
        ? `Parada ativa: ${stopReasonText(apiStoppageReasonToStopReason(activeStop.reason), activeStop.reasonDetail)}.`
        : "Operando normalmente.",
      id: station.stationId,
      label: station.stationName,
      status: station.operation.status === "STOPPED" ? "stopped" : "operating",
    };
  });
}

function getLatestMetricsFromDashboard(response: DashboardResponse): MetricCardConfig[] {
  return [
    {
      helper: "Apontamento atual",
      icon: Box,
      label: "Estoque de cal",
      tone: "lime",
      unit: " sacos",
      value: decimalToNumber(response.limeStockBags).toLocaleString("pt-BR"),
    },
    {
      helper: measurementHelper(response.latestRawPh?.measuredAt),
      icon: Droplets,
      label: "pH entrada",
      tone: "input",
      value: formatMetricValue(measurementValue(response.latestRawPh), 2),
    },
    {
      helper: measurementHelper(response.latestTreatedPh?.measuredAt),
      icon: Droplets,
      label: "pH saída",
      tone: "output",
      value: formatMetricValue(measurementValue(response.latestTreatedPh), 2),
    },
    {
      helper: measurementHelper(response.latestFlow?.measuredAt),
      icon: Gauge,
      label: "Vazão",
      tone: "flow",
      unit: " M³/h",
      value: Math.round(measurementValue(response.latestFlow)).toLocaleString("pt-BR"),
    },
  ];
}

function getAverageMetricsFromDashboard(response: DashboardResponse): MetricCardConfig[] {
  const averages = response.currentDayAverages;

  return [
    {
      helper: "Último turno concluído",
      icon: Box,
      label: "Cal consumido",
      tone: "lime",
      unit: " sacos",
      value: decimalToNumber(response.limeConsumptionLastShiftBags).toLocaleString("pt-BR"),
    },
    {
      helper: averageHelper(averageSampleCount(averages.rawPh)),
      icon: Droplets,
      label: "pH entrada",
      tone: "input",
      value: formatMetricValue(decimalToNumber(averages.rawPh?.value), 2),
    },
    {
      helper: averageHelper(averageSampleCount(averages.treatedPh)),
      icon: Droplets,
      label: "pH saída",
      tone: "output",
      value: formatMetricValue(decimalToNumber(averages.treatedPh?.value), 2),
    },
    {
      helper: averageHelper(averageSampleCount(averages.flow)),
      icon: Gauge,
      label: "Vazão média",
      tone: "flow",
      unit: " M³/h",
      value: Math.round(decimalToNumber(averages.flowAverageToday)).toLocaleString("pt-BR"),
    },
    {
      helper: "Vazão média x aferições de hoje",
      icon: Waves,
      label: "Vazão total",
      tone: "flow",
      unit: " M³",
      value: Math.round(decimalToNumber(averages.flowTotalToday)).toLocaleString("pt-BR"),
    },
    {
      helper: "Vazão total / horas trabalhadas",
      icon: Gauge,
      label: "Vazão por hora",
      tone: "flow",
      unit: " M³/h",
      value: Math.round(decimalToNumber(averages.flowAveragePerWorkedHour)).toLocaleString("pt-BR"),
    },
  ];
}

function buildDashboardChartsFromResponse(response: DashboardResponse): DashboardChartItem[] {
  return [
    {
      ariaLabel: "Variação de vazão por período",
      id: "flow",
      series: (response.series?.flow ?? []).map(seriesResponseToChartSeries),
      title: "Vazão",
      unit: " M³/h",
    },
    {
      ariaLabel: "Variação de pH de entrada por período",
      id: "input-ph",
      series: (response.series?.rawPh ?? []).map(seriesResponseToChartSeries),
      title: "pH entrada",
      unit: "pH",
    },
    {
      ariaLabel: "Variação de pH de saída por período",
      id: "output-ph",
      series: (response.series?.treatedPh ?? []).map(seriesResponseToChartSeries),
      title: "pH saída",
      unit: "pH",
    },
  ];
}

const emptyDashboardActivityLogs: DashboardActivityLogModel = {
  limeLogs: [],
  stopLogs: [],
};

function buildDashboardActivityLogsFromResponse(response: DashboardResponse): DashboardActivityLogModel {
  return {
    limeLogs: (response.activity?.limeLogs ?? []).slice(0, maxDashboardLogItems).map((log) => ({
      color: getStationColor(log.stationName),
      id: log.id,
      movementType: limeMovementLabels[log.type],
      quantityLabel: `${decimalToNumber(log.quantityBags).toLocaleString("pt-BR")} sacos`,
      stationLabel: getShortStationLabel(log.stationName),
      timeLabel: formatTime(log.recordedAt),
    })),
    stopLogs: (response.activity?.stoppageLogs ?? []).slice(0, maxDashboardLogItems).map((log) => ({
      color: getStationColor(log.stationName),
      elapsedLabel: formatHoursMinutes(log.durationMinutes),
      id: log.id,
      reasonLabel: stopReasonText(apiStoppageReasonToStopReason(log.reason), log.reasonDetail),
      stationLabel: getShortStationLabel(log.stationName),
    })),
  };
}

const limeMovementLabels: Record<ApiLimeRecordType, string> = {
  CONSUMPTION: "Consumo",
  ENTRY: "Entrada",
};

const workedHoursStationOrder: OperationalStationId[] = ["boa-vista", "novo-horizonte"];

function buildWorkedHoursEntries(response: DashboardResponse, stationMode: StationMode): WorkedHoursEntry[] {
  if (stationMode !== "consolidated") {
    return [
      {
        color: "inherit",
        label: "Hoje",
        value: formatDecimalHours(decimalToNumber(response.currentDayAverages.workedHoursToday)),
      },
    ];
  }

  return response.stations
    .map((station) => ({
      color: getStationColor(station.stationName),
      label: getShortStationLabel(station.stationName),
      mode: getStationModeByName(station.stationName),
      value: formatDecimalHours(decimalToNumber(station.workedHoursToday)),
    }))
    .sort((first, second) => workedHoursStationOrder.indexOf(first.mode) - workedHoursStationOrder.indexOf(second.mode))
    .map(({ mode, ...entry }) => entry);
}

function seriesResponseToChartSeries(series: NonNullable<DashboardResponse["series"]>["flow"][number]) {
  return {
    color: getStationColor(series.stationName),
    id: series.stationId,
    label: getShortStationLabel(series.stationName),
    points: series.points.map((point) => ({
      measuredAt: point.measuredAt,
      value: decimalToNumber(point.value),
    })),
  };
}

function measurementValue(measurement: DashboardResponse["latestFlow"]): number {
  return decimalToNumber(measurement?.value);
}

function decimalToNumber(value: number | string | null | undefined): number {
  if (value === null || value === undefined || value === "") {
    return 0;
  }

  const numericValue = typeof value === "number" ? value : Number(value);
  return Number.isFinite(numericValue) ? numericValue : 0;
}

function formatMetricValue(value: number, fractionDigits: number): string {
  return value === 0 ? "-" : value.toFixed(fractionDigits);
}

function measurementHelper(measuredAt: string | null | undefined): string {
  return measuredAt ? `Última leitura: ${formatDateTime(measuredAt)}` : "Sem leitura no período";
}

function averageHelper(sampleCount: number): string {
  return sampleCount > 0 ? `${sampleCount} amostras hoje` : "Sem amostras hoje";
}

function averageSampleCount(average: DashboardResponse["currentDayAverages"]["rawPh"]): number {
  return average?.sampleCount ?? 0;
}

function sameStationName(firstName: string, secondName: string): boolean {
  return firstName.trim().toLowerCase() === secondName.trim().toLowerCase();
}

interface DashboardActivityLogModel {
  limeLogs: DashboardLimeLogItem[];
  stopLogs: DashboardStopLogItem[];
}

const maxDashboardLogItems = 10;

function getStationColor(stationName: string): string {
  const stationMode = getStationModeByName(stationName);
  return stationChartSeeds.find((station) => station.id === stationMode)?.color ?? "#5e6b68";
}

function getStationModeByName(stationName: string): OperationalStationId {
  return sameStationName(stationName, getOperationalStationLabel("boa-vista")) ? "boa-vista" : "novo-horizonte";
}

function getShortStationLabel(stationName: string): string {
  return stationName.replace(/^ETE\s+/i, "");
}

function getEffectiveStatus(
  snapshot: DashboardSnapshot,
  stationStatuses: DashboardStationStatusItem[],
): DashboardSnapshot["status"] {
  if (stationStatuses.some((station) => station.status === "stopped")) {
    return "stopped";
  }

  return snapshot.status;
}

function getStatusLabel(
  status: DashboardSnapshot["status"],
  stationStatuses: DashboardStationStatusItem[],
): string {
  if (stationStatuses.length > 0 && status === "stopped") {
    return "Atenção operacional";
  }

  return status === "operating" ? "Funcionando" : "Parado";
}

function getStatusDescription(
  snapshot: DashboardSnapshot,
  stationMode: StationMode,
  elapsedTime: string,
  stationStatuses: DashboardStationStatusItem[],
): string {
  if (snapshot.status === "stopped") {
    const startedAt = snapshot.stoppedSince ? formatDateTime(snapshot.stoppedSince) : "Não informado";
    const reason = snapshot.stoppedReason ?? "Não informado";

    return `Início: ${startedAt}. Motivo: ${reason}. Tempo parado: ${elapsedTime}.`;
  }

  if (stationMode === "consolidated") {
    const stoppedCount = stationStatuses.filter((station) => station.status === "stopped").length;

    if (stoppedCount === 1) {
      return "1 estação com parada ativa.";
    }

    return stoppedCount > 0 ? `${stoppedCount} estações com parada ativa.` : "Ambas as estações operando normalmente.";
  }

  return "Estação selecionada operando normalmente.";
}

function buildDashboardCharts(stationMode: StationMode, periodRange: DashboardPeriodRange | null): DashboardChartItem[] {
  const series = buildVisibleStationSeries(stationMode, periodRange);

  return [
    {
      id: "flow",
      title: "Vazão",
      unit: " M³/h",
      ariaLabel: "Variação de vazão por período",
      series: series.map((item) => ({ ...item, points: item.points.flow })),
    },
    {
      id: "input-ph",
      title: "pH entrada",
      unit: "pH",
      ariaLabel: "Variação de pH de entrada por período",
      series: series.map((item) => ({ ...item, points: item.points.inputPh })),
    },
    {
      id: "output-ph",
      title: "pH saída",
      unit: "pH",
      ariaLabel: "Variação de pH de saída por período",
      series: series.map((item) => ({ ...item, points: item.points.outputPh })),
    },
  ];
}

interface StationSeries {
  id: string;
  label: string;
  color: string;
  points: {
    flow: Array<{ measuredAt: string; value: number }>;
    inputPh: Array<{ measuredAt: string; value: number }>;
    outputPh: Array<{ measuredAt: string; value: number }>;
  };
}

interface StationChartSeed {
  id: Exclude<StationMode, "consolidated">;
  label: string;
  color: string;
  flow: number;
  inputPh: number;
  outputPh: number;
}

const stationChartSeeds: StationChartSeed[] = [
  {
    id: "novo-horizonte",
    label: "Novo Horizonte",
    color: "#00b982",
    flow: 18800,
    inputPh: 6.72,
    outputPh: 7.18,
  },
  {
    id: "boa-vista",
    label: "Boa Vista",
    color: "#1594d2",
    flow: 15600,
    inputPh: 6.55,
    outputPh: 7.04,
  },
];

function buildVisibleStationSeries(stationMode: StationMode, periodRange: DashboardPeriodRange | null): StationSeries[] {
  if (!periodRange) {
    return [];
  }

  return stationChartSeeds
    .filter((station) => stationMode === "consolidated" || station.id === stationMode)
    .map((station, stationIndex) => ({
      id: station.id,
      label: station.label,
      color: station.color,
      points: {
        flow: buildChartPoints(periodRange, station.flow, 1300, stationIndex),
        inputPh: buildChartPoints(periodRange, station.inputPh, 0.16, stationIndex),
        outputPh: buildChartPoints(periodRange, station.outputPh, 0.12, stationIndex),
      },
    }));
}

function buildChartPoints(
  periodRange: DashboardPeriodRange,
  baseValue: number,
  amplitude: number,
  stationIndex: number,
): Array<{ measuredAt: string; value: number }> {
  const startTime = new Date(periodRange.startAt).getTime();
  const endTime = new Date(periodRange.endAt).getTime() - 1;
  const pointCount = 7;
  const step = Math.max(1, (endTime - startTime) / (pointCount - 1));

  return Array.from({ length: pointCount }, (_, index) => {
    const wave = Math.sin(index + stationIndex * 0.8) * amplitude;
    const drift = (index - Math.floor(pointCount / 2)) * amplitude * 0.08;
    const value = Math.max(0, baseValue + wave + drift);

    return {
      measuredAt: new Date(startTime + step * index).toISOString(),
      value: roundMetricValue(value),
    };
  });
}

function roundMetricValue(value: number): number {
  if (value >= 100) {
    return Math.round(value);
  }

  return Math.round(value * 100) / 100;
}

function DashboardAccessDenied() {
  return (
    <section className="dashboard">
      <PageHeader
        eyebrow="Estação de tratamento de efluentes"
        title="Dashboard operacional"
        description="Acompanhamento das leituras, estoque de cal e status de funcionamento."
      />
      <section className="state-panel state-panel--error">
        <h2>Acesso indisponível</h2>
        <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
      </section>
    </section>
  );
}
