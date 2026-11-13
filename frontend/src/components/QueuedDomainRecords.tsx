import { useOfflineQueue } from "../offline/useOfflineQueue";
import type { QueueDomain, QueueItem } from "../offline/offlineQueue";

export function queuedRecordError(item: QueueItem): string | null {
  return item.status === "failed" && item.lastError ? item.lastError.message : null;
}

export function QueuedDomainRecords({ domain }: { domain: QueueDomain }) {
  const { items } = useOfflineQueue();
  const records = items.filter((item) => item.domain === domain);

  if (records.length === 0) return null;

  return (
    <section className="surface-panel pending-domain-records" aria-label="Registros aguardando envio">
      <strong>Registros neste aparelho</strong>
      {records.map((item) => (
        <p key={item.localId}>
          <span className={item.status === "failed" ? "badge badge--danger" : "badge"}>{item.status === "failed" ? "Falhou" : "Aguardando envio"}</span>
          {" "}{new Date(item.eventAt).toLocaleString("pt-BR")} — {Object.entries(item.payload).filter(([key]) => key !== "description").map(([key, value]) => `${key}: ${value ?? ""}`).join(", ")}
          {queuedRecordError(item) ? <span className="form-error">{queuedRecordError(item)}</span> : null}
        </p>
      ))}
    </section>
  );
}
