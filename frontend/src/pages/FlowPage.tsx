import { Edit3, Eye, Save, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import {
  canReadAllStations,
  canViewAuditHistory,
  canWriteOperationalDataForUser,
} from "../auth/permissions";
import { PageHeader } from "../components/PageHeader";
import { DateRangeFilterContent, OperatorFilterContent, StationFilterContent } from "../components/ColumnFilters";
import { PaginationControls } from "../components/PaginationControls";
import { PendingBadge } from "../components/PendingBadge";
import { QueuedDomainRecords } from "../components/QueuedDomainRecords";
import { AuditActionModal, AuditBadges } from "../components/RecordAudit";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { StationField } from "../components/StationField";
import { TimeInput } from "../components/TimeInput";
import { queueOnNetworkError } from "../components/offlineSubmit";
import { useAsyncResource } from "../hooks/useAsyncResource";
import {
  createFlowMeasurement,
  deleteFlowMeasurement,
  listFlowMeasurements,
  updateFlowMeasurement,
} from "../services/operationalApi";
import { listStations } from "../services/stationApi";
import { listUserDirectory } from "../services/userApi";
import type { FilterState, OperationalStationId } from "../types";
import { apiFlowMeasurementResponseToFlowRecord, type ApiBackedFlowRecord } from "../utils/apiAdapters";
import { hasRealChanges } from "../utils/audit";
import { resolveWritableApiStationId } from "../utils/apiStations";
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
import { validateFlowHeight } from "../utils/flowHeight";
import {
  defaultOperationalStationId,
  hasOperationalStationScope,
  missingOperatorStationLabel,
  operatorStationName,
  writableStationName,
} from "../utils/stationAccess";

function defaultFlowFilters(): FilterState {
  const endAt = getTodayDate();
  const startDate = new Date(`${endAt}T00:00:00`);
  startDate.setDate(startDate.getDate() - 6);
  const startAt = [startDate.getFullYear(), startDate.getMonth() + 1, startDate.getDate()]
    .map((value, index) => index === 0 ? String(value) : String(value).padStart(2, "0"))
    .join("-");
  return { endAt, startAt };
}

export function FlowPage() {
  const [stationId, setStationId] = useState<OperationalStationId>(defaultOperationalStationId);
  const [height, setHeight] = useState("");
  const [time, setTime] = useState(getCurrentTimeHHMM());
  const [isSaving, setIsSaving] = useState(false);
  const [isAuditing, setIsAuditing] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [editingRecord, setEditingRecord] = useState<ApiBackedFlowRecord | null>(null);
  const [deletingRecord, setDeletingRecord] = useState<ApiBackedFlowRecord | null>(null);
  const [editHeight, setEditHeight] = useState("");
  const [editTime, setEditTime] = useState("");
  const [filters, setFilters] = useState<FilterState>(defaultFlowFilters);
  const [draftDateFilter, setDraftDateFilter] = useState<FilterState>(filters);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [totalPages, setTotalPages] = useState(1);
  const { user } = useAuth();
  const navigate = useNavigate();
  const hasStationScope = hasOperationalStationScope(user);
  const canWrite = canWriteOperationalDataForUser(user);
  const lockedStationName = operatorStationName(user);
  const stationResource = useAsyncResource(useCallback((signal) => listStations(signal), []), []);
  const canReadAllForFilters = canReadAllStations(user?.role);
  const operatorResource = useAsyncResource(
    useCallback(
      (signal) => (canReadAllForFilters ? listUserDirectory(signal) : Promise.resolve([])),
      [canReadAllForFilters],
    ),
    [],
  );
  const recordResource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!hasStationScope) {
          return [];
        }

        const response = await listFlowMeasurements(
          canReadAllStations(user?.role)
            ? { ...filters, consolidated: !filters.stationId, page, size: pageSize }
            : { consolidated: false },
          signal,
        );

        setTotalPages(Math.max(1, response.totalPages));
        return response.content.map(apiFlowMeasurementResponseToFlowRecord);
      },
      [filters.authorUserId, filters.endAt, filters.startAt, filters.stationId, hasStationScope, page, pageSize, user?.role],
    ),
    [],
  );
  useEffect(() => {
    if (canReadAllStations(user?.role) && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages, user?.role]);
  const visibleRecords = useMemo(
    () => (recordResource.data ?? []).filter((record) => !record.deleted),
    [recordResource.data],
  );
  const currentTimeMax = getCurrentTimeHHMM();

  function applyFilters(value: FilterState) {
    setFilters(value);
    setPage(0);
  }

  function clearFilters() {
    const next = defaultFlowFilters();
    setDraftDateFilter(next);
    applyFilters(next);
  }

  const columns: DataColumn<ApiBackedFlowRecord>[] = [
    {
      header: "Estação",
      cell: (record) => <>{record.station} <PendingBadge recordId={record.id} /></>,
      filter: canReadAllForFilters ? (
        <StationFilterContent
          stations={stationResource.data ?? []}
          value={filters.stationId}
          onChange={(value) => applyFilters({ ...filters, stationId: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.stationId),
    },
    {
      header: "Vazão",
      align: "right",
      cell: (record) => `${record.liters.toLocaleString("pt-BR")} m³/h`,
    },
    {
      header: "Data",
      cell: (record) => formatDateTime(record.collectedAt),
      filter: canReadAllForFilters ? (
        <DateRangeFilterContent
          value={draftDateFilter}
          onChange={setDraftDateFilter}
          onApply={(value) => applyFilters({ ...filters, startAt: value.startAt, endAt: value.endAt })}
        />
      ) : undefined,
      filterActive: Boolean(filters.startAt || filters.endAt),
    },
    {
      header: "Operador",
      cell: (record) => record.operator,
      filter: canReadAllForFilters ? (
        <OperatorFilterContent
          operators={operatorResource.data ?? []}
          value={filters.authorUserId}
          onChange={(value) => applyFilters({ ...filters, authorUserId: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.authorUserId),
    },
    ...(canViewAuditHistory(user?.role)
      ? [{ header: "Histórico", cell: (record: ApiBackedFlowRecord) => <AuditBadges changes={record.changes} /> }]
      : []),
    {
      header: "Ações",
      align: "right",
      cell: (record) => (
        <span className="row-actions">
          <Link className="icon-button" to={`/app/medicoes/vazao/${record.id}`} aria-label="Ver detalhe">
            <Eye size={16} aria-hidden="true" />
          </Link>
          {canWrite ? (
            <>
              <button className="icon-button" type="button" aria-label="Editar vazão" onClick={() => openEdit(record)}>
                <Edit3 size={16} aria-hidden="true" />
              </button>
              <button className="icon-button" type="button" aria-label="Excluir vazão" onClick={() => openDelete(record)}>
                <Trash2 size={16} aria-hidden="true" />
              </button>
            </>
          ) : null}
        </span>
      ),
    },
  ];

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const heightValidation = validateFlowHeight(height);

    if (!heightValidation.ok) {
      setFormError(heightValidation.message);
      return;
    }

    const measuredAt = mergeTodayWithTime(time);

    if (isFutureDateTime(measuredAt)) {
      setFormError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    const stationName = writableStationName(user, stationId);
    const apiStationId = resolveWritableApiStationId(user, stationId, stationResource.data ?? []);

    if (!stationName || !apiStationId) {
      setFormError(stationName ? "ITE não encontrada na API." : missingOperatorStationLabel);
      return;
    }

    setIsSaving(true);

    try {
      const createdRecord = await createFlowMeasurement({
        heightCm: heightValidation.value,
        measuredAt,
        stationId: apiStationId,
      });
      const nextRecord = apiFlowMeasurementResponseToFlowRecord(createdRecord);
      recordResource.setData((current) => [nextRecord, ...(current ?? []).filter((record) => record.id !== nextRecord.id)]);
      setHeight("");
      setTime(getCurrentTimeHHMM());
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "flow", operation: "create", stationId: apiStationId, payload: { heightCm: heightValidation.value, measuredAt, stationId: apiStationId }, eventAt: measuredAt });
      if (queued) { setHeight(""); setFormError("Salvo neste aparelho. Será enviado quando houver conexão."); } else setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function openEdit(record: ApiBackedFlowRecord) {
    setAuditError(null);
    setEditingRecord(record);
    setEditHeight("");
    setEditTime(formatTime(record.collectedAt));
  }

  function openDelete(record: ApiBackedFlowRecord) {
    setAuditError(null);
    setDeletingRecord(record);
  }

  async function handleUpdate(description: string) {
    if (!editingRecord) {
      return;
    }

    const hasHeight = editHeight.trim() !== "";
    const heightValidation = hasHeight ? validateFlowHeight(editHeight) : null;

    if (heightValidation && !heightValidation.ok) {
      setAuditError(heightValidation.message);
      return;
    }

    const heightCm = heightValidation?.ok ? heightValidation.value : undefined;

    const nextCollectedAt = mergeDateWithTime(editingRecord.collectedAt, editTime);

    if (isFutureDateTime(nextCollectedAt)) {
      setAuditError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    const fields = [
      ...(heightCm === undefined ? [] : [{
        name: "heightCm",
        label: "Altura da lâmina",
        beforeValue: "Não informada",
        afterValue: `${heightCm.toLocaleString("pt-BR")} cm`,
      }]),
      {
        name: "collectedAt",
        label: "Data",
        beforeValue: formatDateTime(editingRecord.collectedAt),
        afterValue: formatDateTime(nextCollectedAt),
      },
    ].filter((field) => field.beforeValue !== field.afterValue);

    if (!hasRealChanges(fields)) {
      setAuditError("Altere pelo menos um campo antes de salvar.");
      return;
    }

    const payload = {
      description,
      ...(heightCm === undefined ? {} : { heightCm }),
      measuredAt: nextCollectedAt,
      stationId: editingRecord.stationId,
    };

    setIsAuditing(true);
    try {
      const updatedRecord = await updateFlowMeasurement(editingRecord.id, payload);
      const nextRecord = apiFlowMeasurementResponseToFlowRecord(updatedRecord);
      recordResource.setData((current) =>
        (current ?? []).map((record) => (record.id === editingRecord.id ? nextRecord : record)),
      );
      setEditingRecord(null);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "flow", operation: "update", stationId: editingRecord.stationId, targetId: editingRecord.id, payload, eventAt: nextCollectedAt });
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
      await deleteFlowMeasurement(deletingRecord.id, description);
      recordResource.setData((current) => (current ?? []).filter((record) => record.id !== deletingRecord.id));
      setDeletingRecord(null);
      navigate(`/app/medicoes/vazao/${deletingRecord.id}`);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "flow", operation: "delete", stationId: deletingRecord.stationId, targetId: deletingRecord.id, payload: { description }, eventAt: new Date().toISOString() });
      if (queued) { setDeletingRecord(null); navigate(`/app/medicoes/vazao/${deletingRecord.id}`); } else setAuditError(errorMessage(error));
    } finally {
      setIsAuditing(false);
    }
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Medições"
        title="Vazão de água"
        description="Cadastro e consulta das leituras horárias de vazão."
      />

      {!hasStationScope ? (
        <section className="state-panel state-panel--error">
          <h2>Acesso indisponível</h2>
          <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
        </section>
      ) : canWrite ? (
        <form className="surface-panel form-grid" onSubmit={(event) => void handleSubmit(event)}>
          <StationField
            id="flow-station"
            lockedStationName={lockedStationName}
            user={user}
            value={stationId}
            onChange={setStationId}
          />

          <label className="field" htmlFor="flow-height">
            <span>Altura da lâmina (cm)</span>
            <input
              id="flow-height"
              inputMode="decimal"
              required
              type="number"
              min="0"
              max="45"
              step="0.01"
              value={height}
              onChange={(event) => setHeight(event.target.value)}
            />
          </label>

          <TimeInput id="flow-time" label="Hora da coleta" max={currentTimeMax} value={time} onChange={setTime} />

          <div className="form-actions">
            {formError ? <p className="form-error">{formError}</p> : null}
            <button className="button button--primary" disabled={isSaving || stationResource.isLoading} type="submit">
              <Save size={17} aria-hidden="true" />
              {isSaving ? "Salvando..." : "Salvar vazão"}
            </button>
          </div>
        </form>
      ) : (
        <section className="state-panel">
          <h2>Somente leitura</h2>
          <p>Seu perfil pode consultar as leituras de vazão sem alterar registros.</p>
        </section>
      )}

      {hasStationScope ? (
        <QueuedDomainRecords domain="flow" />
      ) : null}

      {hasStationScope && canReadAllForFilters ? (
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
          emptyDescription="Nenhum registro de vazão foi encontrado."
          emptyTitle="Sem leituras de vazão"
          error={recordResource.error}
          getKey={(record) => record.id}
          isLoading={recordResource.isLoading}
          onClearFilters={clearFilters}
          onRetry={recordResource.reload}
          records={visibleRecords}
          renderCard={(record) => (
            <>
              <div className="mobile-record-card__top">
                <strong>{record.liters.toLocaleString("pt-BR")} m³/h</strong>
                <span className="badge">Vazão</span>
              </div>
              <dl>
                <div>
                  <dt>Estação</dt>
                  <dd>{record.station}</dd>
                </div>
                <div>
                  <dt>Data</dt>
                  <dd>{formatDateTime(record.collectedAt)}</dd>
                </div>
                <div>
                  <dt>Operador</dt>
                  <dd>{record.operator}</dd>
                </div>
              </dl>
              <AuditBadges changes={record.changes} />
              <div className="row-actions">
                <Link className="button button--secondary" to={`/app/medicoes/vazao/${record.id}`}>
                  Ver detalhe
                </Link>
                {canWrite ? (
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
          actionLabel="Salvar edição"
          error={auditError}
          isSubmitting={isAuditing}
          title="Editar vazão"
          onClose={() => setEditingRecord(null)}
          onSubmit={(description) => void handleUpdate(description)}
        >
          <div className="form-grid form-grid--modal">
            <label className="field" htmlFor="edit-flow-height">
              <span>Altura da lâmina (cm)</span>
              <input id="edit-flow-height" inputMode="decimal" min="0" max="45" step="0.01" type="number" value={editHeight} onChange={(event) => setEditHeight(event.target.value)} />
            </label>
            <TimeInput
              id="edit-flow-time"
              label="Hora da coleta"
              max={maxTimeForDate(editingRecord.collectedAt)}
              value={editTime}
              onChange={setEditTime}
            />
          </div>
        </AuditActionModal>
      ) : null}

      {deletingRecord ? (
        <AuditActionModal
          actionLabel="Excluir registro"
          error={auditError}
          isSubmitting={isAuditing}
          title="Excluir vazão"
          onClose={() => setDeletingRecord(null)}
          onSubmit={(description) => void handleDelete(description)}
        />
      ) : null}
    </section>
  );
}
