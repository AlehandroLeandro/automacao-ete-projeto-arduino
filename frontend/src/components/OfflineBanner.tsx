import { CloudOff, RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";

import { useOfflineQueue } from "../offline/useOfflineQueue";

export function OfflineBanner() {
  const { isOffline, pendingCount, failedCount, syncNow } = useOfflineQueue();
  if (!isOffline && pendingCount === 0 && failedCount === 0) return null;

  const message = isOffline
    ? "Sem conexão. Registros novos ficam salvos neste aparelho."
    : failedCount > 0
      ? `${failedCount} registro(s) precisam de atenção.`
      : `${pendingCount} registro(s) aguardando envio.`;

  return (
    <aside className="offline-banner" role="status">
      <CloudOff size={18} aria-hidden="true" />
      <span>{message}</span>
      <Link to="/app/pendencias">Ver pendências</Link>
      {!isOffline && pendingCount > 0 ? (
        <button className="offline-banner__sync" type="button" onClick={() => void syncNow()} aria-label="Sincronizar agora">
          <RefreshCw size={16} aria-hidden="true" />
        </button>
      ) : null}
    </aside>
  );
}
