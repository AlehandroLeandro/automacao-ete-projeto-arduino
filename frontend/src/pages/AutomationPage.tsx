import { useCallback, useEffect, useState, type FormEvent } from "react";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { ApiError } from "../services/api";
import { createDeviceKey, listDeviceKeys, type DeviceKeyResponse } from "../services/deviceKeyApi";
import { listStations } from "../services/stationApi";
import type { ApiStation } from "../types";
import { deviceKeyStatusLabel } from "../utils/deviceKeys";

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

  const columns: DataColumn<DeviceKeyResponse>[] = [
    { header: "Nome", cell: (key) => key.name },
    { header: "ETE", cell: (key) => key.stationName },
    { header: "Estado", cell: (key) => key.active ? "Ativa" : "Desativada" },
    { header: "Último envio", cell: (key) => lastSeen(key.lastSeenAt) },
    { header: "Sinal", cell: (key) => <span className="badge">{deviceKeyStatusLabel(key.status)}</span> },
  ];

  return <section className="page-stack">
    <PageHeader eyebrow="Administração" title="Automação" description="Gerencie as chaves dos equipamentos de cada ETE." />
    <form className="surface-panel form-grid" onSubmit={(event) => void submit(event)}>
      <label className="field" htmlFor="device-name"><span>Nome do equipamento</span><input id="device-name" required value={name} disabled={saving} onChange={(event) => setName(event.target.value)} /></label>
      <label className="field" htmlFor="device-station"><span>ETE</span><select id="device-station" required value={stationId} disabled={saving || loading || !!loadError} onChange={(event) => setStationId(event.target.value)}><option value="">Selecione uma ETE</option>{stations.map((station) => <option key={station.id} value={station.id}>{station.name}</option>)}</select></label>
      <div className="form-actions">{formError && <p className="form-error" role="alert">{formError}</p>}<button className="button button--primary" type="submit" disabled={saving || loading || !!loadError || stations.length === 0}>{saving ? "Criando..." : "Criar chave"}</button></div>
    </form>
    <ResponsiveDataView columns={columns} emptyTitle="Nenhuma chave cadastrada" emptyDescription="Crie uma chave para começar a receber dados de um equipamento." error={loadError} getKey={(key) => key.id} isLoading={loading} onRetry={() => void load()} records={keys} renderCard={(key) => <><div className="mobile-record-card__top"><strong>{key.name}</strong><span className="badge">{deviceKeyStatusLabel(key.status)}</span></div><dl><div><dt>ETE</dt><dd>{key.stationName}</dd></div><div><dt>Estado</dt><dd>{key.active ? "Ativa" : "Desativada"}</dd></div><div><dt>Último envio</dt><dd>{lastSeen(key.lastSeenAt)}</dd></div></dl></>} />
  </section>;
}
