import { useOfflineQueue } from "../offline/useOfflineQueue";

export function PendingBadge({ recordId }: { recordId: string }) {
  const { items } = useOfflineQueue();
  const item = items.find((candidate) => candidate.targetId === recordId || candidate.localId === recordId);
  return item ? <span className={item.status === "failed" ? "badge badge--danger" : "badge"}>{item.status === "failed" ? "Falha offline" : "Pendente"}</span> : null;
}
