import { Edit3, Eye, RotateCcw, Save, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import {
  canEditOperatorWindowRecord,
  canReadAllStations,
  canViewAuditHistory,
  canWriteOperationalDataForUser,
  operatorVisibleListStart,
} from "../auth/permissions";
import { PageHeader } from "../components/PageHeader";
import {
  DateRangeFilterContent,
  OperatorFilterContent,
  ReasonFilterContent,
  StationFilterContent,
} from "../components/ColumnFilters";
import { PaginationControls } from "../components/PaginationControls";
import { PendingBadge } from "../components/PendingBadge";
import { QueuedDomainRecords } from "../components/QueuedDomainRecords";
import { AuditActionModal, AuditBadges } from "../components/RecordAudit";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { StationField } from "../components/StationField";
import { TimeInput } from "../components/TimeInput";
import { queueOnNetworkError } from "../components/offlineSubmit";
import { stopReasonLabels, stopReasonText } from "../data/mockData";
import { useAsyncResource } from "../hooks/useAsyncResource";
import { useOfflineQueue } from "../offline/useOfflineQueue";
import {
  deleteStoppage,
  finishStoppage,
  listStoppages,
  startStoppage,
  updateStoppage,
} from "../services/operationalApi";
import { listStations } from "../services/stationApi";
import { listUserDirectory } from "../services/userApi";
import type { FilterState, OperationalStationId, StopReason } from "../types";
import {
  apiStoppageResponseToStopRecord,
  pendingRecordFromQueueItem,
  stopReasonToApiStoppageReason,
  type ApiBackedStopRecord,
} from "../utils/apiAdapters";
import { hasRealChanges } from "../utils/audit";
import { resolveWritableApiStationId } from "../utils/apiStations";
import { STOPPAGE_OVERLAP_MESSAGE, hasStoppageOverlap } from "../utils/stoppageOverlap";
import {
  FUTURE_TIMESTAMP_MESSAGE,
  formatDateTime,
  formatTime,
  getCurrentTimeHHMM,
  getTodayDate,
  isFutureDateTime,
  maxTimeForDate,
  mergeDateWithTime,
  mergeTodayWithTime,
} from "../utils/dateTime";
import { errorMessage } from "../utils/errors";
import {
  defaultOperationalStationId,
  hasOperationalStationScope,
  missingOperatorStationLabel,
  operatorStationName,
  writableStationName,
} from "../utils/stationAccess";

function defaultStopFilters(): FilterState {
  const endAt = getTodayDate();
  const startDate = new Date(`${endAt}T00:00:00`);
  startDate.setDate(startDate.getDate() - 6);
  const startAt = [startDate.getFullYear(), startDate.getMonth() + 1, startDate.getDate()]
    .map((value, index) => index === 0 ? String(value) : String(value).padStart(2, "0"))
    .join("-");
  return { endAt, startAt };
}

const REASON_DETAIL_MIN_LENGTH = 3;
const REASON_DETAIL_MAX_LENGTH = 200;
const REASON_DETAIL_MESSAGE = `Descreva o motivo da parada com pelo menos ${REASON_DETAIL_MIN_LENGTH} caracteres.`;

// Só o motivo "Outros" carrega texto livre; qualquer outro motivo descarta o que foi digitado.
function normalizedReasonDetail(reason: StopReason, detail: string): string | null {
  return reason === "outros" ? detail.trim() : null;
}

function isReasonDetailInvalid(reason: StopReason, detail: string): boolean {
  return reason === "outros" && (normalizedReasonDetail(reason, detail) ?? "").length < REASON_DETAIL_MIN_LENGTH;
}

export function StopsPage() {
  const [stationId, setStationId] = useState<OperationalStationId>(defaultOperationalStationId);
  const [reason, setReason] = useState<StopReason>("falta_de_cal");
  const [reasonDetail, setReasonDetail] = useState("");
  const [startTime, setStartTime] = useState(getCurrentTimeHHMM());
  const [endTime, setEndTime] = useState(getCurrentTimeHHMM());
  const [isSaving, setIsSaving] = useState(false);
  const [isAuditing, setIsAuditing] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [editingRecord, setEditingRecord] = useState<ApiBackedStopRecord | null>(null);
  const [deletingRecord, setDeletingRecord] = useState<ApiBackedStopRecord | null>(null);
  const [filters, setFilters] = useState<FilterState>(defaultStopFilters);
  const [draftDateFilter, setDraftDateFilter] = useState<FilterState>(filters);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [totalPages, setTotalPages] = useState(1);
  const [editReason, setEditReason] = useState<StopReason>("falta_de_cal");
  const [editReasonDetail, setEditReasonDetail] = useState("");
  const [editStartTime, setEditStartTime] = useState("");
  const [editEndTime, setEditEndTime] = useState("");
  const [editHasEnd, setEditHasEnd] = useState(true);
  const { user } = useAuth();
  const { items: queueItems } = useOfflineQueue();
  const navigate = useNavigate();
  const hasStationScope = hasOperationalStationScope(user);
  const canWrite = canWriteOperationalDataForUser(user);
  const canReadAll = canReadAllStations(user?.role);
  const lockedStationName = operatorStationName(user);
  const selectedStationName = writableStationName(user, stationId);
  const stationResource = useAsyncResource(useCallback((signal) => listStations(signal), []), []);
  const operatorResource = useAsyncResource(
    useCallback((signal) => (canReadAll ? listUserDirectory(signal) : Promise.resolve([])), [canReadAll]),
    [],
  );
  const recordResource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!hasStationScope) {
          return [];
        }

        const response = await listStoppages(
          canReadAll
            ? {
                ...filters,
                consolidated: !filters.stationId,
                page,
                size: pageSize,
                reason: filters.reason ? stopReasonToApiStoppageReason(filters.reason) : undefined,
              }
            : { consolidated: false, startAt: operatorVisibleListStart(user) },
          signal,
        );

        setTotalPages(Math.max(1, response.totalPages));
        return response.content.map(apiStoppageResponseToStopRecord);
      },
      [canReadAll, filters.authorUserId, filters.endAt, filters.reason, filters.startAt, filters.stationId, hasStationScope, page, pageSize, user?.operatorRecordWindowHours, user?.role],
    ),
    [],
  );
  useEffect(() => {
    if (canReadAll && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
  }, [canReadAll, page, totalPages]);
  const visibleRecords = useMemo(
    () => (recordResource.data ?? []).filter((record) => !record.deleted),
    [recordResource.data],
  );
  const selectedStationRecords = useMemo(
    () => visibleRecords.filter((record) => record.station === selectedStationName),
    [selectedStationName, visibleRecords],
  );
  const pendingStartForStation = useMemo(() => {
    const apiStationId = resolveWritableApiStationId(user, stationId, stationResource.data ?? []);
    return queueItems.find(
      (item) =>
        item.domain === "stoppage" &&
        item.operation === "startStoppage" &&
        (item.status === "pending" || item.status === "syncing") &&
        item.stationId === apiStationId,
    );
  }, [queueItems, stationId, stationResource.data, user]);
  const activeStop = useMemo(
    () => selectedStationRecords.find((record) => record.status === "ACTIVE") ??
      (pendingStartForStation ? pendingRecordFromQueueItem(pendingStartForStation, selectedStationName ?? "") : undefined),
    [pendingStartForStation, selectedStationName, selectedStationRecords],
  );
  const currentTimeMax = getCurrentTimeHHMM();

  function applyFilters(value: FilterState) {
    setFilters(value);
    setPage(0);
  }

  function clearFilters() {
    const next = defaultStopFilters();
    setDraftDateFilter(next);
    applyFilters(next);
  }

  const columns: DataColumn<ApiBackedStopRecord>[] = [
    {
      header: "Estação",
      cell: (record) => <>{record.station} <PendingBadge recordId={record.id} /></>,
      filter: canReadAll ? (
        <StationFilterContent
          stations={stationResource.data ?? []}
          value={filters.stationId}
          onChange={(value) => applyFilters({ ...filters, stationId: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.stationId),
    },
    {
      header: "Motivo",
      cell: (record) => stopReasonText(record.reason, record.reasonDetail),
      filter: canReadAll ? (
        <ReasonFilterContent
          reasons={Object.keys(stopReasonLabels) as StopReason[]}
          value={filters.reason}
          onChange={(value) => applyFilters({ ...filters, reason: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.reason),
    },
    {
      header: "Início",
      cell: (record) => formatDateTime(record.startedAt),
      filter: canReadAll ? (
        <DateRangeFilterContent
          value={draftDateFilter}
          onChange={setDraftDateFilter}
          onApply={(value) => applyFilters({ ...filters, startAt: value.startAt, endAt: value.endAt })}
        />
      ) : undefined,
      filterActive: Boolean(filters.startAt || filters.endAt),
    },
    { header: "Fim", cell: (record) => (record.endedAt ? formatDateTime(record.endedAt) : "Em aberto") },
    {
      header: "Status",
      cell: (record) => (
        <span className={record.status === "ACTIVE" ? "badge badge--danger" : "badge"}>
          {record.status === "ACTIVE" ? "Ativa" : "Finalizada"}
        </span>
      ),
    },
    {
      header: "Operador",
      cell: (record) => record.operator,
      filter: canReadAll ? (
        <OperatorFilterContent
          operators={operatorResource.data ?? []}
          value={filters.authorUserId}
          onChange={(value) => applyFilters({ ...filters, authorUserId: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.authorUserId),
    },
    ...(canViewAuditHistory(user?.role)
      ? [{ header: "Histórico", cell: (record: ApiBackedStopRecord) => <AuditBadges changes={record.changes} /> }]
      : []),
    {
      header: "Ações",
      align: "right",
      cell: (record) => (
        <span className="row-actions">
          <Link className="icon-button" to={`/app/paradas/${record.id}`} aria-label="Ver detalhe">
            <Eye size={16} aria-hidden="true" />
          </Link>
          {canWrite && canEditOperatorWindowRecord(user, record.startedAt) ? (
            <>
              <button className="icon-button" type="button" aria-label="Editar parada" onClick={() => openEdit(record)}>
                <Edit3 size={16} aria-hidden="true" />
              </button>
              <button className="icon-button" type="button" aria-label="Excluir parada" onClick={() => openDelete(record)}>
                <Trash2 size={16} aria-hidden="true" />
              </button>
            </>
          ) : null}
        </span>
      ),
    },
  ];

  async function handleStartStop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const apiStationId = resolveWritableApiStationId(user, stationId, stationResource.data ?? []);

    if (!selectedStationName || !apiStationId) {
      setFormError(selectedStationName ? "ITE não encontrada na API." : missingOperatorStationLabel);
      return;
    }

    if (isReasonDetailInvalid(reason, reasonDetail)) {
      setFormError(REASON_DETAIL_MESSAGE);
      return;
    }

    const startAt = mergeTodayWithTime(startTime);

    if (isFutureDateTime(startAt)) {
      setFormError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    if (hasStoppageOverlap({ startAt }, selectedStationRecords)) {
      setFormError(STOPPAGE_OVERLAP_MESSAGE);
      return;
    }

    setIsSaving(true);

    try {
      const createdRecord = await startStoppage({
        reason: stopReasonToApiStoppageReason(reason),
        reasonDetail: normalizedReasonDetail(reason, reasonDetail),
        startAt,
        stationId: apiStationId,
      });
      const nextRecord = apiStoppageResponseToStopRecord(createdRecord);
      recordResource.setData((current) => [nextRecord, ...(current ?? []).filter((record) => record.id !== nextRecord.id)]);
      setReasonDetail("");
    } catch (error) {
      const payload = { reason: stopReasonToApiStoppageReason(reason), reasonDetail: normalizedReasonDetail(reason, reasonDetail), startAt, stationId: apiStationId };
      const queued = await queueOnNetworkError(error, { user, domain: "stoppage", operation: "startStoppage", stationId: apiStationId, payload, eventAt: startAt });
      if (queued) {
        const localRecord: ApiBackedStopRecord = {
          id: queued.localId,
          station: selectedStationName ?? "",
          stationId: apiStationId,
          reason,
          reasonDetail: normalizedReasonDetail(reason, reasonDetail) ?? undefined,
          startedAt: startAt,
          status: "ACTIVE",
          operator: user?.name ?? "",
          durationMinutes: 0,
        };
        recordResource.setData((current) => [localRecord, ...(current ?? [])]);
        setReasonDetail("");
        setFormError("Parada salva neste aparelho. Será enviada quando houver conexão.");
      } else setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleFinishStop(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (!activeStop) {
      return;
    }

    const endAt = mergeTodayWithTime(endTime);

    if (isFutureDateTime(endAt)) {
      setFormError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    if (hasStoppageOverlap({ startAt: activeStop.startedAt, endAt, ignoreId: activeStop.id }, selectedStationRecords)) {
      setFormError(STOPPAGE_OVERLAP_MESSAGE);
      return;
    }

    setIsSaving(true);

    try {
      const updatedRecord = await finishStoppage(activeStop.id, {
        endAt,
      });
      const nextRecord = apiStoppageResponseToStopRecord(updatedRecord);
      recordResource.setData((current) =>
        (current ?? []).map((record) => (record.id === activeStop.id ? nextRecord : record)),
      );
      setEndTime(getCurrentTimeHHMM());
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "stoppage", operation: "finishStoppage", stationId: activeStop.stationId, targetId: activeStop.id, payload: { endAt }, eventAt: endAt });
      if (queued) setFormError("Retorno salvo neste aparelho. Será enviado quando houver conexão."); else setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function openEdit(record: ApiBackedStopRecord) {
    setAuditError(null);
    setEditingRecord(record);
    setEditReason(record.reason);
    setEditReasonDetail(record.reasonDetail ?? "");
    setEditStartTime(formatTime(record.startedAt));
    setEditHasEnd(Boolean(record.endedAt));
    setEditEndTime(record.endedAt ? formatTime(record.endedAt) : getCurrentTimeHHMM());
  }

  function openDelete(record: ApiBackedStopRecord) {
    setAuditError(null);
    setDeletingRecord(record);
  }

  async function handleUpdate(description: string) {
    if (!editingRecord) {
      return;
    }

    if (isReasonDetailInvalid(editReason, editReasonDetail)) {
      setAuditError(REASON_DETAIL_MESSAGE);
      return;
    }

    const nextReasonDetail = normalizedReasonDetail(editReason, editReasonDetail);
    const nextStartedAt = mergeDateWithTime(editingRecord.startedAt, editStartTime);
    const nextEndedAt = editHasEnd ? mergeDateWithTime(editingRecord.endedAt ?? editingRecord.startedAt, editEndTime) : undefined;

    if (isFutureDateTime(nextStartedAt) || (nextEndedAt && isFutureDateTime(nextEndedAt))) {
      setAuditError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    if (nextEndedAt && new Date(nextEndedAt).getTime() <= new Date(nextStartedAt).getTime()) {
      setAuditError("O fim deve ser posterior ao início.");
      return;
    }

    const sameStationRecords = visibleRecords.filter((record) => record.station === editingRecord.station);
    if (
      hasStoppageOverlap(
        { startAt: nextStartedAt, endAt: nextEndedAt, ignoreId: editingRecord.id },
        sameStationRecords,
      )
    ) {
      setAuditError(STOPPAGE_OVERLAP_MESSAGE);
      return;
    }

    const fields = [
      {
        name: "reason",
        label: "Motivo",
        beforeValue: stopReasonLabels[editingRecord.reason],
        afterValue: stopReasonLabels[editReason],
      },
      {
        name: "reasonDetail",
        label: "Descrição do motivo",
        beforeValue: editingRecord.reasonDetail ?? "-",
        afterValue: nextReasonDetail ?? "-",
      },
      {
        name: "startedAt",
        label: "Início",
        beforeValue: formatDateTime(editingRecord.startedAt),
        afterValue: formatDateTime(nextStartedAt),
      },
      {
        name: "endedAt",
        label: "Fim",
        beforeValue: editingRecord.endedAt ? formatDateTime(editingRecord.endedAt) : "Em aberto",
        afterValue: nextEndedAt ? formatDateTime(nextEndedAt) : "Em aberto",
      },
    ].filter((field) => field.beforeValue !== field.afterValue);

    if (!hasRealChanges(fields)) {
      setAuditError("Altere pelo menos um campo antes de salvar.");
      return;
    }

    setIsAuditing(true);
    try {
      const updatedRecord = await updateStoppage(editingRecord.id, {
        description,
        endAt: nextEndedAt ?? null,
        reason: stopReasonToApiStoppageReason(editReason),
        reasonDetail: nextReasonDetail,
        startAt: nextStartedAt,
      });
      const nextRecord = apiStoppageResponseToStopRecord(updatedRecord);
      recordResource.setData((current) =>
        (current ?? []).map((record) => (record.id === editingRecord.id ? nextRecord : record)),
      );
      setEditingRecord(null);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "stoppage", operation: "update", stationId: editingRecord.stationId, targetId: editingRecord.id, payload: { description, endAt: nextEndedAt ?? null, reason: stopReasonToApiStoppageReason(editReason), reasonDetail: nextReasonDetail, startAt: nextStartedAt }, eventAt: nextStartedAt });
      if (queued) setEditingRecord(null); else setAuditError(errorMessage(error));
    } finally {
      setIsAuditing(false);
    }
  }

  async function handleDelete(description: string) {
    if (!deletingRecord) {
      return;
    }

    setIsAuditing(true);
    try {
      await deleteStoppage(deletingRecord.id, description);
      recordResource.setData((current) => (current ?? []).filter((record) => record.id !== deletingRecord.id));
      setDeletingRecord(null);
      navigate(`/app/paradas/${deletingRecord.id}`);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "stoppage", operation: "delete", stationId: deletingRecord.stationId, targetId: deletingRecord.id, payload: { description }, eventAt: new Date().toISOString() });
      if (queued) { setDeletingRecord(null); navigate(`/app/paradas/${deletingRecord.id}`); } else setAuditError(errorMessage(error));
    } finally {
      setIsAuditing(false);
    }
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Operação"
        title="Paradas"
        description="Registro de início e retorno de paradas operacionais."
      />

      {!hasStationScope ? (
        <section className="state-panel state-panel--error">
          <h2>Acesso indisponível</h2>
          <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
        </section>
      ) : canWrite ? (
        activeStop ? (
          <form className="surface-panel form-grid" onSubmit={(event) => void handleFinishStop(event)}>
            <div className="active-stop">
              <span className="badge badge--danger">Parada ativa</span>
              <strong>{activeStop.station}</strong>
              <p>{stopReasonText(activeStop.reason, activeStop.reasonDetail)} desde {formatDateTime(activeStop.startedAt)}</p>
            </div>
            <TimeInput
              id="stop-end-time"
              label="Hora do retorno"
              max={currentTimeMax}
              value={endTime}
              onChange={setEndTime}
            />
            <div className="form-actions">
              {formError ? <p className="form-error">{formError}</p> : null}
              <button className="button button--primary" disabled={isSaving} type="submit">
                <RotateCcw size={17} aria-hidden="true" />
                {isSaving ? "Salvando..." : "Registrar retorno"}
              </button>
            </div>
          </form>
        ) : (
          <form className="surface-panel form-grid" onSubmit={(event) => void handleStartStop(event)}>
            <StationField
              id="stop-station"
              lockedStationName={lockedStationName}
              user={user}
              value={stationId}
              onChange={setStationId}
            />

            <label className="field" htmlFor="stop-reason">
              <span>Motivo</span>
              <select
                id="stop-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value as StopReason)}
              >
                {Object.entries(stopReasonLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            {reason === "outros" ? (
              <label className="field" htmlFor="stop-reason-detail">
                <span>Qual?</span>
                <input
                  id="stop-reason-detail"
                  maxLength={REASON_DETAIL_MAX_LENGTH}
                  placeholder="Descreva o motivo da parada"
                  type="text"
                  value={reasonDetail}
                  onChange={(event) => setReasonDetail(event.target.value)}
                />
              </label>
            ) : null}

            <TimeInput
              id="stop-start-time"
              label="Hora de início"
              max={currentTimeMax}
              value={startTime}
              onChange={setStartTime}
            />

            <div className="form-actions">
              {formError ? <p className="form-error">{formError}</p> : null}
              <button className="button button--primary" disabled={isSaving || stationResource.isLoading} type="submit">
                <Save size={17} aria-hidden="true" />
                {isSaving ? "Salvando..." : "Registrar parada"}
              </button>
            </div>
          </form>
        )
      ) : (
        <section className="state-panel">
          <h2>Somente leitura</h2>
          <p>Seu perfil pode consultar paradas ativas e finalizadas.</p>
        </section>
      )}

      {hasStationScope ? (
        <QueuedDomainRecords domain="stoppage" />
      ) : null}

      {hasStationScope && canReadAll ? (
        <PaginationControls
          page={page}
          pageSize={pageSize}
          totalPages={totalPages}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
      ) : null}

      {hasStationScope ? (
        <ResponsiveDataView
          columns={columns}
          emptyDescription="Nenhuma parada foi registrada no período atual."
          emptyTitle="Sem paradas"
          error={recordResource.error}
          getKey={(record) => record.id}
          isLoading={recordResource.isLoading}
          onClearFilters={clearFilters}
          onRetry={recordResource.reload}
          records={visibleRecords}
          renderCard={(record) => (
            <>
              <div className="mobile-record-card__top">
                <strong>{record.station}</strong>
                <span className={record.status === "ACTIVE" ? "badge badge--danger" : "badge"}>
                  {record.status === "ACTIVE" ? "Ativa" : "Finalizada"}
                </span>
              </div>
              <dl>
                <div>
                  <dt>Motivo</dt>
                  <dd>{stopReasonText(record.reason, record.reasonDetail)}</dd>
                </div>
                <div>
                  <dt>Início</dt>
                  <dd>{formatDateTime(record.startedAt)}</dd>
                </div>
                <div>
                  <dt>Fim</dt>
                  <dd>{record.endedAt ? formatDateTime(record.endedAt) : "Em aberto"}</dd>
                </div>
                <div>
                  <dt>Operador</dt>
                  <dd>{record.operator}</dd>
                </div>
              </dl>
              <AuditBadges changes={record.changes} />
              <div className="row-actions">
                <Link className="button button--secondary" to={`/app/paradas/${record.id}`}>
                  Ver detalhe
                </Link>
                {canWrite && canEditOperatorWindowRecord(user, record.startedAt) ? (
                  <button className="button button--secondary" type="button" onClick={() => openEdit(record)}>
                    Editar
                  </button>
                ) : null}
              </div>
            </>
          )}
        />
      ) : null}

      {editingRecord ? (
        <AuditActionModal
          actionLabel="Salvar correção"
          error={auditError}
          isSubmitting={isAuditing}
          title="Editar parada"
          onClose={() => setEditingRecord(null)}
          onSubmit={(description) => void handleUpdate(description)}
        >
          <div className="form-grid form-grid--modal">
            <label className="field" htmlFor="edit-stop-reason">
              <span>Motivo</span>
              <select id="edit-stop-reason" value={editReason} onChange={(event) => setEditReason(event.target.value as StopReason)}>
                {Object.entries(stopReasonLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            {editReason === "outros" ? (
              <label className="field" htmlFor="edit-stop-reason-detail">
                <span>Qual?</span>
                <input
                  id="edit-stop-reason-detail"
                  maxLength={REASON_DETAIL_MAX_LENGTH}
                  placeholder="Descreva o motivo da parada"
                  type="text"
                  value={editReasonDetail}
                  onChange={(event) => setEditReasonDetail(event.target.value)}
                />
              </label>
            ) : null}
            <TimeInput
              id="edit-stop-start"
              label="Hora de início"
              max={maxTimeForDate(editingRecord.startedAt)}
              value={editStartTime}
              onChange={setEditStartTime}
            />
            <label className="field checkbox-field" htmlFor="edit-stop-has-end">
              <span>Finalizada</span>
              <input id="edit-stop-has-end" checked={editHasEnd} type="checkbox" onChange={(event) => setEditHasEnd(event.target.checked)} />
            </label>
            {editHasEnd ? (
              <TimeInput
                id="edit-stop-end"
                label="Hora de fim"
                max={maxTimeForDate(editingRecord.endedAt ?? editingRecord.startedAt)}
                value={editEndTime}
                onChange={setEditEndTime}
              />
            ) : null}
          </div>
        </AuditActionModal>
      ) : null}

      {deletingRecord ? (
        <AuditActionModal
          actionLabel="Excluir parada"
          error={auditError}
          isSubmitting={isAuditing}
          title="Excluir parada"
          onClose={() => setDeletingRecord(null)}
          onSubmit={(description) => void handleDelete(description)}
        />
      ) : null}
    </section>
  );
}
