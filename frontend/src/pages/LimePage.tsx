import { Edit3, Save, Trash2, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import { useAuth } from "../auth/AuthContext";
import {
  canManageLimeThreshold,
  canReadAllStations,
  canWriteOperationalDataForUser,
} from "../auth/permissions";
import { PageHeader } from "../components/PageHeader";
import { DateRangeFilterContent, LimeTypeFilterContent, OperatorFilterContent, StationFilterContent } from "../components/ColumnFilters";
import { PaginationControls } from "../components/PaginationControls";
import { PendingBadge } from "../components/PendingBadge";
import { QueuedDomainRecords } from "../components/QueuedDomainRecords";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { StationField } from "../components/StationField";
import { TimeInput } from "../components/TimeInput";
import { queueOnNetworkError } from "../components/offlineSubmit";
import { useAsyncResource } from "../hooks/useAsyncResource";
import {
  createLimeRecord,
  deleteLimeRecord,
  getCurrentLimeStock,
  listLimeRecords,
  updateLimeRecord,
} from "../services/operationalApi";
import { listStations, updateLimeThreshold } from "../services/stationApi";
import { listUserDirectory } from "../services/userApi";
import type { FilterState, LimeRecordType, OperationalStationId } from "../types";
import {
  apiLimeRecordListResponseToLimeRecord,
  limeRecordTypeToApiLimeRecordType,
  type ApiBackedLimeRecord,
} from "../utils/apiAdapters";
import { resolveWritableApiStationId } from "../utils/apiStations";
import { formatDateTime, formatTime, getCurrentTimeHHMM, getTodayDate, mergeDateWithTime, mergeTodayWithTime } from "../utils/dateTime";
import { errorMessage } from "../utils/errors";
import {
  defaultOperationalStationId,
  hasOperationalStationScope,
  missingOperatorStationLabel,
  operatorStationName,
  writableStationName,
} from "../utils/stationAccess";

const limeTypeLabels: Record<LimeRecordType, string> = {
  entry: "Entrada",
  consumption: "Consumo",
};

const insufficientStockMessage = "Estoque de cal insuficiente para este consumo";

function defaultLimeFilters(): FilterState {
  const endAt = getTodayDate();
  const startDate = new Date(`${endAt}T00:00:00`);
  startDate.setDate(startDate.getDate() - 6);
  const startAt = [startDate.getFullYear(), startDate.getMonth() + 1, startDate.getDate()]
    .map((value, index) => index === 0 ? String(value) : String(value).padStart(2, "0"))
    .join("-");
  return { endAt, startAt };
}

export function LimePage() {
  const [type, setType] = useState<LimeRecordType>("consumption");
  const [stationId, setStationId] = useState<OperationalStationId>(defaultOperationalStationId);
  const [quantity, setQuantity] = useState("");
  const [time, setTime] = useState(getCurrentTimeHHMM());
  const [isSaving, setIsSaving] = useState(false);
  const [isDeletingRecordId, setIsDeletingRecordId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [editingRecord, setEditingRecord] = useState<ApiBackedLimeRecord | null>(null);
  const [editType, setEditType] = useState<LimeRecordType>("consumption");
  const [editQuantity, setEditQuantity] = useState("");
  const [editTime, setEditTime] = useState("");
  const [editNote, setEditNote] = useState("");
  const [thresholdInput, setThresholdInput] = useState("");
  const [thresholdError, setThresholdError] = useState<string | null>(null);
  const [thresholdMessage, setThresholdMessage] = useState<string | null>(null);
  const [isSavingThreshold, setIsSavingThreshold] = useState(false);
  const [filters, setFilters] = useState<FilterState>(defaultLimeFilters);
  const [draftDateFilter, setDraftDateFilter] = useState<FilterState>(filters);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [totalPages, setTotalPages] = useState(1);
  const { user } = useAuth();
  const hasStationScope = hasOperationalStationScope(user);
  const canWrite = canWriteOperationalDataForUser(user);
  const canManageThreshold = canManageLimeThreshold(user?.role);
  const lockedStationName = operatorStationName(user);
  const selectedStationName = writableStationName(user, stationId);
  const stationResource = useAsyncResource(useCallback((signal) => listStations(signal), []), []);
  const canReadAllForFilters = canReadAllStations(user?.role);
  const operatorResource = useAsyncResource(
    useCallback(
      (signal) => (canReadAllForFilters ? listUserDirectory(signal) : Promise.resolve([])),
      [canReadAllForFilters],
    ),
    [],
  );
  const selectedApiStationId = useMemo(
    () => resolveWritableApiStationId(user, stationId, stationResource.data ?? []),
    [stationId, stationResource.data, user],
  );
  const selectedApiStation = useMemo(
    () => stationResource.data?.find((station) => station.id === selectedApiStationId) ?? null,
    [selectedApiStationId, stationResource.data],
  );
  const recordResource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!hasStationScope) {
          return [];
        }

        const response = await listLimeRecords(
          canReadAllStations(user?.role)
            ? {
                ...filters,
                type: filters.type ? limeRecordTypeToApiLimeRecordType(filters.type) : undefined,
                consolidated: !filters.stationId,
                page,
                size: pageSize,
              }
            : { consolidated: false },
          signal,
        );

        setTotalPages(Math.max(1, response.totalPages));
        return response.content.map(apiLimeRecordListResponseToLimeRecord);
      },
      [filters.authorUserId, filters.endAt, filters.startAt, filters.stationId, filters.type, hasStationScope, page, pageSize, user?.role],
    ),
    [],
  );
  useEffect(() => {
    if (canReadAllStations(user?.role) && totalPages > 0 && page >= totalPages) setPage(totalPages - 1);
  }, [page, totalPages, user?.role]);
  const stockResource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!selectedApiStationId) {
          return null;
        }

        const response = await getCurrentLimeStock(selectedApiStationId, signal);
        return Number(response.currentStockBags);
      },
      [selectedApiStationId],
    ),
    [],
  );
  const visibleRecords = recordResource.data ?? [];
  const currentStockLabel = stockResource.isLoading
    ? "Carregando..."
    : stockResource.error
      ? "Indisponível"
      : `${stockResource.data ?? 0} sacos`;

  useEffect(() => {
    setThresholdInput(
      selectedApiStation?.minLimeStockBags === null || selectedApiStation?.minLimeStockBags === undefined
        ? ""
        : String(selectedApiStation.minLimeStockBags),
    );
    setThresholdError(null);
    setThresholdMessage(null);
  }, [selectedApiStation?.id, selectedApiStation?.minLimeStockBags]);

  function applyFilters(value: FilterState) {
    setFilters(value);
    setPage(0);
  }

  function clearFilters() {
    const next = defaultLimeFilters();
    setDraftDateFilter(next);
    applyFilters(next);
  }

  const columns: DataColumn<ApiBackedLimeRecord>[] = [
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
      header: "Tipo",
      cell: (record) => <span className="badge">{limeTypeLabels[record.type]}</span>,
      filter: canReadAllForFilters ? (
        <LimeTypeFilterContent
          types={Object.keys(limeTypeLabels) as LimeRecordType[]}
          labels={limeTypeLabels}
          value={filters.type}
          onChange={(value) => applyFilters({ ...filters, type: value })}
        />
      ) : undefined,
      filterActive: Boolean(filters.type),
    },
    { header: "Quantidade", align: "right", cell: (record) => `${record.quantityBags} sacos` },
    { header: "Estoque após", align: "right", cell: (record) => `${record.stockAfterBags} sacos` },
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
    {
      header: "Ações",
      align: "right",
      cell: (record) =>
        canWrite ? (
          <span className="row-actions">
            <button className="icon-button" type="button" aria-label="Editar cal" onClick={() => openEdit(record)}>
              <Edit3 size={16} aria-hidden="true" />
            </button>
            <button
              aria-busy={isDeletingRecordId === record.id}
              className="icon-button"
              disabled={isDeletingRecordId === record.id}
              type="button"
              aria-label="Excluir cal"
              onClick={() => void handleDelete(record)}
            >
              <Trash2 size={16} aria-hidden="true" />
            </button>
          </span>
        ) : (
          <span className="muted">Somente leitura</span>
        ),
    },
  ];

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const numericQuantity = Number(quantity);

    if (!Number.isFinite(numericQuantity) || numericQuantity <= 0) {
      setFormError("Informe a quantidade em sacos.");
      return;
    }

    if (type === "consumption" && stockResource.data !== null && numericQuantity > stockResource.data) {
      setFormError(insufficientStockMessage);
      return;
    }

    if (!selectedStationName || !selectedApiStationId) {
      setFormError(selectedStationName ? "ITE não encontrada na API." : missingOperatorStationLabel);
      return;
    }

    setIsSaving(true);

    try {
      const payload = {
        note: null,
        quantityBags: numericQuantity,
        recordedAt: mergeTodayWithTime(time),
        stationId: selectedApiStationId,
        type: limeRecordTypeToApiLimeRecordType(type),
      };
      await createLimeRecord(payload);
      recordResource.reload();
      stockResource.reload();
      setQuantity("");
      setTime(getCurrentTimeHHMM());
    } catch (error) {
      const recordedAt = mergeTodayWithTime(time);
      const queued = await queueOnNetworkError(error, { user, domain: "lime", operation: "create", stationId: selectedApiStationId!, payload: { note: null, quantityBags: numericQuantity, recordedAt, stationId: selectedApiStationId!, type: limeRecordTypeToApiLimeRecordType(type) }, eventAt: recordedAt });
      if (queued) { setQuantity(""); setFormError("Salvo neste aparelho. Será enviado quando houver conexão."); } else setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function openEdit(record: ApiBackedLimeRecord) {
    setActionError(null);
    setEditingRecord(record);
    setEditType(record.type);
    setEditQuantity(String(record.quantityBags));
    setEditTime(formatTime(record.collectedAt));
    setEditNote(record.note ?? "");
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!editingRecord) {
      return;
    }

    setActionError(null);
    const numericQuantity = Number(editQuantity);

    if (!Number.isFinite(numericQuantity) || numericQuantity <= 0) {
      setActionError("Informe a quantidade em sacos.");
      return;
    }

    setIsSaving(true);

    try {
      await updateLimeRecord(editingRecord.id, {
        note: editNote.trim() || null,
        quantityBags: numericQuantity,
        recordedAt: mergeDateWithTime(editingRecord.collectedAt, editTime),
        stationId: editingRecord.stationId,
        type: limeRecordTypeToApiLimeRecordType(editType),
      });
      setEditingRecord(null);
      recordResource.reload();
      stockResource.reload();
    } catch (error) {
      const recordedAt = mergeDateWithTime(editingRecord.collectedAt, editTime);
      const queued = await queueOnNetworkError(error, { user, domain: "lime", operation: "update", stationId: editingRecord.stationId, targetId: editingRecord.id, payload: { note: editNote.trim() || null, quantityBags: numericQuantity, recordedAt, stationId: editingRecord.stationId, type: limeRecordTypeToApiLimeRecordType(editType) }, eventAt: recordedAt });
      if (queued) setEditingRecord(null); else setActionError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete(record: ApiBackedLimeRecord) {
    setActionError(null);

    if (!window.confirm("Excluir este registro de cal?")) {
      return;
    }

    setIsDeletingRecordId(record.id);

    try {
      await deleteLimeRecord(record.id);
      recordResource.setData((current) => (current ?? []).filter((item) => item.id !== record.id));
      stockResource.reload();
    } catch (error) {
      const queued = await queueOnNetworkError(error, { user, domain: "lime", operation: "delete", stationId: record.stationId, targetId: record.id, payload: {}, eventAt: new Date().toISOString() });
      if (queued) { setActionError("Exclusão salva neste aparelho. Será enviada quando houver conexão."); recordResource.setData((current) => (current ?? []).filter((item) => item.id !== record.id)); } else setActionError(errorMessage(error));
    } finally {
      setIsDeletingRecordId(null);
    }
  }

  async function handleThresholdSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setThresholdError(null);
    setThresholdMessage(null);

    if (!selectedApiStationId) {
      setThresholdError("ITE não encontrada na API.");
      return;
    }

    const value = Number(thresholdInput);
    if (!Number.isFinite(value) || value < 0) {
      setThresholdError("Informe um limite maior ou igual a zero.");
      return;
    }

    await saveThreshold(value);
  }

  async function handleRemoveThreshold() {
    setThresholdError(null);
    setThresholdMessage(null);

    if (!selectedApiStationId) {
      setThresholdError("ITE não encontrada na API.");
      return;
    }

    await saveThreshold(null);
  }

  async function saveThreshold(value: number | null) {
    if (!selectedApiStationId) {
      return;
    }

    setIsSavingThreshold(true);
    try {
      const updatedStation = await updateLimeThreshold(selectedApiStationId, value);
      stationResource.setData((current) =>
        current?.map((station) => (station.id === updatedStation.id ? updatedStation : station)) ?? [updatedStation],
      );
      stationResource.reload();
      stockResource.reload();
      setThresholdInput(value === null ? "" : String(value));
      setThresholdMessage(value === null ? "Limite removido." : "Limite salvo.");
    } catch (error) {
      setThresholdError(errorMessage(error));
    } finally {
      setIsSavingThreshold(false);
    }
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Medições"
        title="Controle de cal"
        description="Entrada e consumo de cal."
      />

      {!hasStationScope ? (
        <section className="state-panel state-panel--error">
          <h2>Acesso indisponível</h2>
          <p>{missingOperatorStationLabel}. Procure um administrador para vincular sua ITE operacional.</p>
        </section>
      ) : canWrite ? (
        <form className="surface-panel form-grid" onSubmit={(event) => void handleSubmit(event)}>
          <StationField
            id="lime-station"
            lockedStationName={lockedStationName}
            user={user}
            value={stationId}
            onChange={setStationId}
          />

          <label className="field" htmlFor="lime-stock">
            <span>Estoque atual</span>
            <input id="lime-stock" readOnly value={currentStockLabel} />
          </label>

          <fieldset className="field radio-field">
            <legend>Tipo de registro</legend>
            <label className={type === "entry" ? "radio-card is-active" : "radio-card"}>
              <input
                checked={type === "entry"}
                name="lime-type"
                type="radio"
                onChange={() => setType("entry")}
              />
              <span>Entrada</span>
            </label>
            <label className={type === "consumption" ? "radio-card is-active" : "radio-card"}>
              <input
                checked={type === "consumption"}
                name="lime-type"
                type="radio"
                onChange={() => setType("consumption")}
              />
              <span>Consumo</span>
            </label>
          </fieldset>

          <label className="field" htmlFor="lime-quantity">
            <span>Quantidade em sacos</span>
            <input
              id="lime-quantity"
              inputMode="numeric"
              required
              type="number"
              min="1"
              value={quantity}
              onChange={(event) => setQuantity(event.target.value)}
            />
          </label>

          <TimeInput id="lime-time" label="Hora do registro" value={time} onChange={setTime} />

          <div className="form-actions">
            {formError ? <p className="form-error">{formError}</p> : null}
            <button className="button button--primary" disabled={isSaving || stationResource.isLoading} type="submit">
              <Save size={17} aria-hidden="true" />
              {isSaving ? "Salvando..." : "Salvar cal"}
            </button>
          </div>
        </form>
      ) : (
        <section className="state-panel">
          <h2>Somente leitura</h2>
          <p>Seu perfil pode consultar o estoque e os movimentos de cal.</p>
        </section>
      )}

      {hasStationScope && canManageThreshold ? (
        <form className="surface-panel form-grid" onSubmit={(event) => void handleThresholdSubmit(event)}>
          <StationField
            id="lime-threshold-station"
            lockedStationName={lockedStationName}
            user={user}
            value={stationId}
            onChange={setStationId}
          />

          <label className="field" htmlFor="lime-min-threshold">
            <span>Estoque mínimo de cal (bags)</span>
            <input
              id="lime-min-threshold"
              min="0"
              placeholder="Sem limite"
              step="0.01"
              type="number"
              value={thresholdInput}
              onChange={(event) => setThresholdInput(event.target.value)}
            />
          </label>

          <div className="form-actions">
            {thresholdError ? <p className="form-error">{thresholdError}</p> : null}
            {thresholdMessage ? <p className="form-success">{thresholdMessage}</p> : null}
            <button
              className="button button--secondary"
              disabled={isSavingThreshold || !selectedApiStationId}
              type="button"
              onClick={() => void handleRemoveThreshold()}
            >
              Remover limite
            </button>
            <button className="button button--primary" disabled={isSavingThreshold || !selectedApiStationId} type="submit">
              <Save size={17} aria-hidden="true" />
              {isSavingThreshold ? "Salvando..." : "Salvar limite"}
            </button>
          </div>
        </form>
      ) : null}

      {hasStationScope ? (
        <QueuedDomainRecords domain="lime" />
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
          emptyDescription="Nenhum movimento de cal foi encontrado."
          emptyTitle="Sem registros de cal"
          error={recordResource.error}
          getKey={(record) => record.id}
          isLoading={recordResource.isLoading}
          onClearFilters={clearFilters}
          onRetry={recordResource.reload}
          records={visibleRecords}
          renderCard={(record) => (
            <>
              <div className="mobile-record-card__top">
                <strong>{record.quantityBags} sacos</strong>
                <span className="badge">{limeTypeLabels[record.type]}</span>
              </div>
              <dl>
                <div>
                  <dt>Estação</dt>
                  <dd>{record.station}</dd>
                </div>
                <div>
                  <dt>Estoque após</dt>
                  <dd>{record.stockAfterBags} sacos</dd>
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
              {canWrite ? (
                <div className="row-actions">
                  <button className="button button--secondary" type="button" onClick={() => openEdit(record)}>
                    Editar
                  </button>
                  <button className="button button--secondary" type="button" onClick={() => void handleDelete(record)}>
                    Excluir
                  </button>
                </div>
              ) : null}
            </>
          )}
        />
      ) : null}

      {actionError ? (
        <section className="state-panel state-panel--error">
          <h2>Ação não concluída</h2>
          <p>{actionError}</p>
        </section>
      ) : null}

      {editingRecord ? (
        <div className="modal-backdrop" role="presentation">
          <form className="audit-modal" onSubmit={(event) => void handleUpdate(event)}>
            <div className="audit-modal__header">
              <div>
                <p className="eyebrow">Editar movimento</p>
                <h2>Registro de cal</h2>
              </div>
              <button className="icon-button" type="button" aria-label="Fechar" onClick={() => setEditingRecord(null)}>
                <X size={18} aria-hidden="true" />
              </button>
            </div>

            <div className="form-grid form-grid--modal">
              <label className="field" htmlFor="edit-lime-station">
                <span>Estação</span>
                <input id="edit-lime-station" readOnly value={editingRecord.station} />
              </label>

              <label className="field" htmlFor="edit-lime-type">
                <span>Tipo de registro</span>
                <select id="edit-lime-type" value={editType} onChange={(event) => setEditType(event.target.value as LimeRecordType)}>
                  <option value="entry">Entrada</option>
                  <option value="consumption">Consumo</option>
                </select>
              </label>

              <label className="field" htmlFor="edit-lime-quantity">
                <span>Quantidade em sacos</span>
                <input
                  id="edit-lime-quantity"
                  inputMode="numeric"
                  min="1"
                  required
                  type="number"
                  value={editQuantity}
                  onChange={(event) => setEditQuantity(event.target.value)}
                />
              </label>

              <TimeInput id="edit-lime-time" label="Hora do registro" value={editTime} onChange={setEditTime} />

              <label className="field" htmlFor="edit-lime-note">
                <span>Observação</span>
                <textarea
                  id="edit-lime-note"
                  maxLength={500}
                  rows={3}
                  value={editNote}
                  onChange={(event) => setEditNote(event.target.value)}
                />
              </label>
            </div>

            {actionError ? <p className="form-error">{actionError}</p> : null}

            <div className="audit-modal__actions">
              <button className="button button--secondary" type="button" onClick={() => setEditingRecord(null)}>
                Cancelar
              </button>
              <button className="button button--primary" disabled={isSaving} type="submit">
                <Save size={17} aria-hidden="true" />
                {isSaving ? "Salvando..." : "Salvar alterações"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </section>
  );
}
