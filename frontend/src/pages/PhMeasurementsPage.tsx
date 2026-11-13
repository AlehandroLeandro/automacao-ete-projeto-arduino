import { Edit3, Eye, Save, Trash2 } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import {
  canReadAllStations,
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
  createPhMeasurement,
  deletePhMeasurement,
  listPhMeasurements,
  updatePhMeasurement,
} from "../services/operationalApi";
import { listStations } from "../services/stationApi";
import { listUserDirectory } from "../services/userApi";
import type { FilterState, MeasurementKind, OperationalStationId } from "../types";
import {
  apiPhMeasurementResponseToPhRecord,
  measurementKindToApiPhWaterType,
  type ApiBackedPhRecord,
} from "../utils/apiAdapters";
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
import {
  defaultOperationalStationId,
  hasOperationalStationScope,
  missingOperatorStationLabel,
  operatorStationName,
  writableStationName,
} from "../utils/stationAccess";

const kindLabels: Record<MeasurementKind, string> = {
  input: "Água entrada",
  output: "Água saída",
};

function defaultPhFilters(): FilterState {
  const endAt = getTodayDate();
  const startDate = new Date(`${endAt}T00:00:00`);
  startDate.setDate(startDate.getDate() - 6);
  const startAt = [startDate.getFullYear(), startDate.getMonth() + 1, startDate.getDate()]
    .map((value, index) => index === 0 ? String(value) : String(value).padStart(2, "0"))
    .join("-");
  return { endAt, startAt };
}

export function PhMeasurementsPage() {
  const [kind, setKind] = useState<MeasurementKind>("input");
  const [stationId, setStationId] = useState<OperationalStationId>(defaultOperationalStationId);
  const [ph, setPh] = useState("");
  const [time, setTime] = useState(getCurrentTimeHHMM());
  const [isSaving, setIsSaving] = useState(false);
  const [isAuditing, setIsAuditing] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [auditError, setAuditError] = useState<string | null>(null);
  const [editingRecord, setEditingRecord] = useState<ApiBackedPhRecord | null>(null);
  const [deletingRecord, setDeletingRecord] = useState<ApiBackedPhRecord | null>(null);
  const [editPh, setEditPh] = useState("");
  const [editTime, setEditTime] = useState("");
  const [filters, setFilters] = useState<FilterState>(defaultPhFilters);
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

        const canReadAll = canReadAllStations(user?.role);
        const response = await listPhMeasurements(
          canReadAll
            ? { ...filters, consolidated: !filters.stationId, page, size: pageSize, type: measurementKindToApiPhWaterType(kind) }
            : { consolidated: false, type: measurementKindToApiPhWaterType(kind) },
          signal,
        );

        setTotalPages(Math.max(1, response.totalPages));
        return response.content.map(apiPhMeasurementResponseToPhRecord);
      },
      [filters.authorUserId, filters.endAt, filters.startAt, filters.stationId, hasStationScope, kind, page, pageSize, user?.role],
    ),
    [],
  );
  useEffect(() => {
    if (canReadAllStations(user?.role) && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages, user?.role]);
  const records = recordResource.data ?? [];
  const currentTimeMax = getCurrentTimeHHMM();

  const filteredRecords = useMemo(
    () => records.filter((record) => !record.deleted && record.kind === kind),
    [kind, records],
  );

  function applyFilters(value: FilterState) {
    setFilters(value);
    setPage(0);
  }

  function clearFilters() {
    const next = defaultPhFilters();
    setDraftDateFilter(next);
    applyFilters(next);
  }

  const columns: DataColumn<ApiBackedPhRecord>[] = [
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
    { header: "Tipo", cell: (record) => kindLabels[record.kind] },
    { header: "pH", cell: (record) => record.ph.toFixed(2), align: "right" },
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
    { header: "Histórico", cell: (record) => <AuditBadges changes={record.changes} /> },
    {
      header: "Ações",
      align: "right",
      cell: (record) => (
        <span className="row-actions">
          <Link className="icon-button" to={`/app/medicoes/ph/${record.id}`} aria-label="Ver detalhe">
            <Eye size={16} aria-hidden="true" />
          </Link>
          {canWrite ? (
            <>
              <button className="icon-button" type="button" aria-label="Editar leitura" onClick={() => openEdit(record)}>
                <Edit3 size={16} aria-hidden="true" />
              </button>
              <button className="icon-button" type="button" aria-label="Excluir leitura" onClick={() => openDelete(record)}>
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

    const numericPh = Number(ph);

    if (!Number.isFinite(numericPh) || numericPh < 0 || numericPh > 14) {
      setFormError("Informe um pH entre 0 e 14.");
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
      const createdRecord = await createPhMeasurement({
        measuredAt,
        ph: numericPh,
        stationId: apiStationId,
        type: measurementKindToApiPhWaterType(kind),
      });
      const nextRecord = apiPhMeasurementResponseToPhRecord(createdRecord);
      recordResource.setData((current) => [nextRecord, ...(current ?? []).filter((record) => record.id !== nextRecord.id)]);
      setPh("");
      setTime(getCurrentTimeHHMM());
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "ph", operation: "create", stationId: apiStationId, payload: { measuredAt, ph: numericPh, stationId: apiStationId, type: measurementKindToApiPhWaterType(kind) }, eventAt: measuredAt });
      if (queued) { setPh(""); setFormError("Salvo neste aparelho. Será enviado quando houver conexão."); } else setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function openEdit(record: ApiBackedPhRecord) {
    setAuditError(null);
    setEditingRecord(record);
    setEditPh(String(record.ph));
    setEditTime(formatTime(record.collectedAt));
  }

  function openDelete(record: ApiBackedPhRecord) {
    setAuditError(null);
    setDeletingRecord(record);
  }

  async function handleUpdate(description: string) {
    if (!editingRecord) {
      return;
    }

    const numericPh = Number(editPh);

    if (!Number.isFinite(numericPh) || numericPh < 0 || numericPh > 14) {
      setAuditError("Informe um pH entre 0 e 14.");
      return;
    }

    const nextCollectedAt = mergeDateWithTime(editingRecord.collectedAt, editTime);

    if (isFutureDateTime(nextCollectedAt)) {
      setAuditError(FUTURE_TIMESTAMP_MESSAGE);
      return;
    }

    const fields = [
      {
        name: "ph",
        label: "pH",
        beforeValue: editingRecord.ph.toFixed(2),
        afterValue: numericPh.toFixed(2),
      },
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

    setIsAuditing(true);
    try {
      const updatedRecord = await updatePhMeasurement(editingRecord.id, {
        description,
        measuredAt: nextCollectedAt,
        ph: numericPh,
        stationId: editingRecord.stationId,
        type: measurementKindToApiPhWaterType(editingRecord.kind),
      });
      const nextRecord = apiPhMeasurementResponseToPhRecord(updatedRecord);
      recordResource.setData((current) =>
        (current ?? []).map((record) => (record.id === editingRecord.id ? nextRecord : record)),
      );
      setEditingRecord(null);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "ph", operation: "update", stationId: editingRecord.stationId, targetId: editingRecord.id, payload: { description, measuredAt: nextCollectedAt, ph: numericPh, stationId: editingRecord.stationId, type: measurementKindToApiPhWaterType(editingRecord.kind) }, eventAt: nextCollectedAt });
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
      await deletePhMeasurement(deletingRecord.id, description);
      recordResource.setData((current) => (current ?? []).filter((record) => record.id !== deletingRecord.id));
      setDeletingRecord(null);
      navigate(`/app/medicoes/ph/${deletingRecord.id}`);
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "ph", operation: "delete", stationId: deletingRecord.stationId, targetId: deletingRecord.id, payload: { description }, eventAt: new Date().toISOString() });
      if (queued) { setDeletingRecord(null); navigate(`/app/medicoes/ph/${deletingRecord.id}`); } else setAuditError(errorMessage(error));
    } finally {
      setIsAuditing(false);
    }
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Medições"
        title="pH de águas"
        description="Registro de pH de entrada e saída com seleção visual do tipo de água."
      />

      <div className="tab-row" role="radiogroup" aria-label="Tipo de água">
        {(["input", "output"] as MeasurementKind[]).map((option) => (
          <label className={kind === option ? "radio-card is-active" : "radio-card"} key={option}>
            <input
              checked={kind === option}
              name="ph-kind"
              type="radio"
              value={option}
              onChange={() => setKind(option)}
            />
            <span>{kindLabels[option]}</span>
          </label>
        ))}
      </div>

      {!hasStationScope ? (
        <section className="state-panel state-panel--error">
          <h2>Acesso indisponível</h2>
          <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
        </section>
      ) : canWrite ? (
        <form className="surface-panel form-grid" onSubmit={(event) => void handleSubmit(event)}>
          <StationField
            id="ph-station"
            lockedStationName={lockedStationName}
            user={user}
            value={stationId}
            onChange={setStationId}
          />

          <label className="field" htmlFor="ph-value">
            <span>pH</span>
            <input
              id="ph-value"
              inputMode="decimal"
              placeholder="7.00"
              required
              type="number"
              step="0.01"
              min="0"
              max="14"
              value={ph}
              onChange={(event) => setPh(event.target.value)}
            />
          </label>

          <TimeInput id="ph-time" label="Hora da coleta" max={currentTimeMax} value={time} onChange={setTime} />

          <div className="form-actions">
            {formError ? <p className="form-error">{formError}</p> : null}
            <button className="button button--primary" disabled={isSaving || stationResource.isLoading} type="submit">
              <Save size={17} aria-hidden="true" />
              {isSaving ? "Salvando..." : "Salvar leitura"}
            </button>
          </div>
        </form>
      ) : (
        <section className="state-panel">
          <h2>Somente leitura</h2>
          <p>Seu perfil permite consultar as medições, sem criar ou editar registros.</p>
        </section>
      )}

      {hasStationScope ? (
        <QueuedDomainRecords domain="ph" />
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
          emptyDescription="Nenhuma leitura encontrada para o tipo selecionado."
          emptyTitle="Sem leituras de pH"
          error={recordResource.error}
          getKey={(record) => record.id}
          isLoading={recordResource.isLoading}
          onClearFilters={clearFilters}
          onRetry={recordResource.reload}
          records={filteredRecords}
          renderCard={(record) => (
            <>
              <div className="mobile-record-card__top">
                <strong>{record.ph.toFixed(2)} pH</strong>
                <span className="badge">{kindLabels[record.kind]}</span>
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
                <Link className="button button--secondary" to={`/app/medicoes/ph/${record.id}`}>
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
          title="Editar leitura de pH"
          onClose={() => setEditingRecord(null)}
          onSubmit={(description) => void handleUpdate(description)}
        >
          <div className="form-grid form-grid--modal">
            <label className="field" htmlFor="edit-ph">
              <span>pH</span>
              <input id="edit-ph" min="0" max="14" step="0.01" type="number" value={editPh} onChange={(event) => setEditPh(event.target.value)} />
            </label>
            <TimeInput
              id="edit-ph-time"
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
          title="Excluir leitura de pH"
          onClose={() => setDeletingRecord(null)}
          onSubmit={(description) => void handleDelete(description)}
        />
      ) : null}
    </section>
  );
}
