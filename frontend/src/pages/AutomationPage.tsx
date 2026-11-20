import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { ApiError } from "../services/api";
import { createDeviceKey, deleteDeviceKey, getDeviceKeySecret, listDeviceKeys, setDeviceKeyActive, type DeviceKeyResponse } from "../services/deviceKeyApi";
import { listStations } from "../services/stationApi";
import type { ApiStation } from "../types";
import { deviceKeyStatusLabel, isDeleteConfirmed, SECRET_VISIBLE_MS } from "../utils/deviceKeys";

function lastSeen(value: string | null): string {
  return value ? new Date(value).toLocaleString("pt-BR") : "Nenhum envio";
}
function message(error: unknown): string {
  return error instanceof Error ? error.message : "Não foi possível concluir a operação.";
}

export function AutomationPage() {
  const [keys, setKeys] = useState<DeviceKeyResponse[]>([]);
  const [stations, setStations] = useState<ApiStation[]>([]);
  const [name, setName] = useState("");
  const [stationId, setStationId] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [secret, setSecret] = useState<{ id: string; value: string } | null>(null);
  const [actionMessage, setActionMessage] = useState<{ id: string; text: string; error: boolean } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeviceKeyResponse | null>(null);
  const [deleteText, setDeleteText] = useState("");
  const secretRequest = useRef<AbortController | null>(null);

  useEffect(() => () => { secretRequest.current?.abort(); }, []);
  useEffect(() => {
    if (!secret) return;
    const timer = window.setTimeout(() => setSecret(null), SECRET_VISIBLE_MS);
    return () => window.clearTimeout(timer);
  }, [secret]);

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setLoadError(null);
    try {
      const [nextKeys, nextStations] = await Promise.all([listDeviceKeys(signal), listStations(signal)]);
      if (signal?.aborted) return;
      setKeys(nextKeys);
      setStations(nextStations.filter((station) => station.active));
    } catch (error) {
      if (!signal?.aborted) setLoadError(message(error));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!name.trim() || !stationId) {
      setFormError("Informe o nome e selecione a ETE.");
      return;
    }
    setSaving(true);
    try {
      const created = await createDeviceKey({ name: name.trim(), stationId });
      setKeys((current) => [...current.filter((key) => key.id !== created.id), created].sort((a, b) => a.name.localeCompare(b.name)));
      setName("");
    } catch (error) {
      setFormError(error instanceof ApiError && error.status === 409 ? "Já existe uma chave com este nome nesta ETE." : message(error));
    } finally {
      setSaving(false);
    }
  }

  async function accessSecret(key: DeviceKeyResponse, copy: boolean) {
    secretRequest.current?.abort();
    setActionMessage(null);
    if (!copy && secret?.id === key.id) {
      setSecret(null);
      return;
    }
    setSecret(null);
    const controller = new AbortController();
    secretRequest.current = controller;
    setBusyId(key.id);
    try {
      const result = await getDeviceKeySecret(key.id, controller.signal);
      if (controller.signal.aborted) return;
      if (copy) {
        await navigator.clipboard.writeText(result.value);
        if (!controller.signal.aborted) setActionMessage({ id: key.id, text: "Chave copiada.", error: false });
      } else {
        setSecret({ id: key.id, value: result.value });
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setSecret(null);
        setActionMessage({ id: key.id, text: copy ? "Não foi possível buscar ou copiar a chave." : message(error), error: true });
      }
    } finally {
      if (!controller.signal.aborted) setBusyId(null);
    }
  }

  async function toggleActive(key: DeviceKeyResponse) {
    const verb = key.active ? "desativar" : "reativar";
    if (!window.confirm(`Deseja ${verb} a chave de ${key.name}?`)) return;
    setActionMessage(null);
    setBusyId(key.id);
    try {
      const updated = await setDeviceKeyActive(key.id, !key.active);
      setKeys((current) => current.map((item) => item.id === key.id ? updated : item));
    } catch (error) {
      setActionMessage({ id: key.id, text: message(error), error: true });
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!deleteTarget || !isDeleteConfirmed(deleteText)) return;
    setBusyId(deleteTarget.id);
    setActionMessage(null);
    try {
      await deleteDeviceKey(deleteTarget.id);
      setKeys((current) => current.filter((item) => item.id !== deleteTarget.id));
      setSecret(null);
      setDeleteTarget(null);
      setDeleteText("");
    } catch (error) {
      setActionMessage({ id: deleteTarget.id, text: message(error), error: true });
      setDeleteTarget(null);
    } finally {
      setBusyId(null);
    }
  }

  function actions(key: DeviceKeyResponse) {
    return <div className="device-key-actions">
      <div className="device-key-actions__buttons">
        <button className="button button--secondary" type="button" disabled={busyId !== null} onClick={() => void accessSecret(key, false)}>{secret?.id === key.id ? "Ocultar" : "Ver"}</button>
        <button className="button button--secondary" type="button" disabled={busyId !== null} onClick={() => void accessSecret(key, true)}>Copiar</button>
        <button className="button button--secondary" type="button" disabled={busyId !== null} onClick={() => void toggleActive(key)}>{key.active ? "Desativar" : "Reativar"}</button>
        <button className="button button--secondary" type="button" disabled={busyId !== null} onClick={() => { setDeleteText(""); setDeleteTarget(key); }}>Excluir</button>
      </div>
      {busyId === key.id && <p role="status">Processando...</p>}
      {secret?.id === key.id && <p className="device-key-secret">{secret.value}</p>}
      {actionMessage?.id === key.id && <p role={actionMessage.error ? "alert" : "status"} className={actionMessage.error ? "form-error" : undefined}>{actionMessage.text}</p>}
    </div>;
  }

  const columns: DataColumn<DeviceKeyResponse>[] = [
    { header: "Nome", cell: (key) => key.name },
    { header: "ETE", cell: (key) => key.stationName },
    { header: "Estado", cell: (key) => key.active ? "Ativa" : "Desativada" },
    { header: "Último envio", cell: (key) => lastSeen(key.lastSeenAt) },
    { header: "Sinal", cell: (key) => <span className="badge">{deviceKeyStatusLabel(key.status)}</span> },
    { header: "Ações", cell: actions },
  ];

  return <section className="page-stack">
    <PageHeader eyebrow="Administração" title="Automação" description="Gerencie as chaves dos equipamentos de cada ETE." />
    <form className="surface-panel form-grid" onSubmit={(event) => void submit(event)}>
      <label className="field" htmlFor="device-name"><span>Nome do equipamento</span><input id="device-name" required value={name} disabled={saving} onChange={(event) => setName(event.target.value)} /></label>
      <label className="field" htmlFor="device-station"><span>ETE</span><select id="device-station" required value={stationId} disabled={saving || loading || !!loadError} onChange={(event) => setStationId(event.target.value)}><option value="">Selecione uma ETE</option>{stations.map((station) => <option key={station.id} value={station.id}>{station.name}</option>)}</select></label>
      <div className="form-actions">{formError && <p className="form-error" role="alert">{formError}</p>}<button className="button button--primary" type="submit" disabled={saving || loading || !!loadError || stations.length === 0}>{saving ? "Criando..." : "Criar chave"}</button></div>
    </form>
    <ResponsiveDataView columns={columns} emptyTitle="Nenhuma chave cadastrada" emptyDescription="Crie uma chave para começar a receber dados de um equipamento." error={loadError} getKey={(key) => key.id} isLoading={loading} onRetry={() => void load()} records={keys} renderCard={(key) => <><div className="mobile-record-card__top"><strong>{key.name}</strong><span className="badge">{deviceKeyStatusLabel(key.status)}</span></div><dl><div><dt>ETE</dt><dd>{key.stationName}</dd></div><div><dt>Estado</dt><dd>{key.active ? "Ativa" : "Desativada"}</dd></div><div><dt>Último envio</dt><dd>{lastSeen(key.lastSeenAt)}</dd></div></dl>{actions(key)}</>} />
    {deleteTarget && <div className="modal-backdrop" role="presentation">
      <form className="audit-modal" role="dialog" aria-modal="true" aria-labelledby="delete-key-title" onKeyDown={(event) => { if (event.key === "Escape" && !busyId) setDeleteTarget(null); }} onSubmit={(event) => void confirmDelete(event)}>
        <div className="audit-modal__header"><h2 id="delete-key-title">Excluir chave</h2></div>
        <p>Para excluir a chave de {deleteTarget.name}, digite EXCLUIR. Os registros de média serão mantidos.</p>
        <label className="field" htmlFor="delete-key-confirmation"><span>Confirmação</span><input id="delete-key-confirmation" autoFocus value={deleteText} disabled={busyId !== null} onChange={(event) => setDeleteText(event.target.value)} /></label>
        <div className="audit-modal__actions">
          <button className="button button--secondary" type="button" disabled={busyId !== null} onClick={() => setDeleteTarget(null)}>Cancelar</button>
          <button className="button button--primary" type="submit" disabled={busyId !== null || !isDeleteConfirmed(deleteText)}>{busyId ? "Excluindo..." : "Excluir chave"}</button>
        </div>
      </form>
    </div>}
  </section>;
}
