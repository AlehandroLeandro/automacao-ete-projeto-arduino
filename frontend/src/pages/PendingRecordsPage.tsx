import { AlertTriangle, RotateCcw, Trash2 } from "lucide-react";
import { useState } from "react";

import { PageHeader } from "../components/PageHeader";
import { useOfflineQueue } from "../offline/useOfflineQueue";

const domainLabels = { ph: "pH", flow: "Vazão", lime: "Cal", stoppage: "Parada" } as const;
const operationLabels = { create: "criação", update: "edição", delete: "exclusão", startStoppage: "início", finishStoppage: "finalização" } as const;

export function PendingRecordsPage() {
  const { items, isOffline, retry, discard, editPayload, syncNow } = useOfflineQueue();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");

  function openEdit(item: (typeof items)[number]) {
    setEditingId(item.localId);
    setDraft(JSON.stringify(item.payload, null, 2));
  }

  async function saveEdit(localId: string) {
    try {
      const payload = JSON.parse(draft) as Record<string, unknown>;
      await editPayload(localId, payload);
      setEditingId(null);
    } catch {
      // JSON inválido fica visível no editor; não altera item.
    }
  }

  return (
    <section className="page-stack">
      <PageHeader eyebrow="Operação" title="Pendências" description="Registros aguardando envio ou correção." />
      {items.length === 0 ? (
        <section className="state-panel"><h2>Nenhuma pendência</h2><p>Todos registros foram enviados.</p></section>
      ) : (
        <div className="pending-list">
          {items.map((item) => (
            <article className="surface-panel pending-item" key={item.localId}>
              <div className="pending-item__heading">
                <div>
                  <strong>{domainLabels[item.domain]} — {operationLabels[item.operation]}</strong>
                  <small className="muted">{item.authorName} · {new Date(item.eventAt).toLocaleString("pt-BR")}</small>
                </div>
                <span className={item.status === "failed" ? "badge badge--danger" : "badge"}>
                  {item.status === "failed" ? "Falhou" : "Aguardando envio"}
                </span>
              </div>
              {item.lastError ? <p className="form-error"><AlertTriangle size={15} aria-hidden="true" /> {item.lastError.message}</p> : null}
              {editingId === item.localId ? (
                <div className="pending-item__editor">
                  <textarea value={draft} onChange={(event) => setDraft(event.target.value)} aria-label="Dados do registro" />
                  <div className="row-actions"><button className="button button--primary" type="button" onClick={() => void saveEdit(item.localId)}>Salvar</button><button className="button button--secondary" type="button" onClick={() => setEditingId(null)}>Cancelar</button></div>
                </div>
              ) : (
                <div className="row-actions">
                  <button className="button button--secondary" type="button" onClick={() => void retry(item.localId)}><RotateCcw size={15} aria-hidden="true" /> Reenviar</button>
                  <button className="button button--secondary" type="button" onClick={() => openEdit(item)}>Editar</button>
                  <button className="button button--danger" type="button" onClick={() => window.confirm("Descartar este registro? Essa ação não pode ser desfeita.") && void discard(item.localId)}><Trash2 size={15} aria-hidden="true" /> Descartar</button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
      {!isOffline && items.some((item) => item.status === "pending") ? <button className="button button--primary" type="button" onClick={() => void syncNow()}>Sincronizar pendências</button> : null}
    </section>
  );
}
