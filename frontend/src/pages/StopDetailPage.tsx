import { ArrowLeft } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { canViewAuditHistory } from "../auth/permissions";
import { AuditBadges, AuditHistory } from "../components/RecordAudit";
import { stopReasonText } from "../data/mockData";
import { useAsyncResource } from "../hooks/useAsyncResource";
import { getStoppage } from "../services/operationalApi";
import { apiStoppageDetailResponseToStopRecord } from "../utils/apiAdapters";
import { formatDateTime } from "../utils/dateTime";

export function StopDetailPage() {
  const { user } = useAuth();
  const { stopId } = useParams();
  const resource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!stopId) {
          return null;
        }

        const response = await getStoppage(stopId, signal);
        return apiStoppageDetailResponseToStopRecord(response);
      },
      [stopId],
    ),
    [],
  );
  const record = resource.data;

  if (resource.isLoading) {
    return (
      <section className="state-panel">
        <span className="spinner" aria-hidden="true" />
        <h1>Carregando parada</h1>
        <p>Buscando o detalhe da parada operacional.</p>
      </section>
    );
  }

  if (!record || resource.error) {
    return (
      <section className="state-panel state-panel--error">
        <h1>Registro não encontrado</h1>
        <p>{resource.error ?? "A parada não existe ou está fora do seu escopo de leitura."}</p>
        <Link className="button button--secondary" to="/app/paradas">
          Voltar
        </Link>
      </section>
    );
  }

  return (
    <section className="page-stack">
      <Link className="button button--secondary detail-back-link" to="/app/paradas">
        <ArrowLeft size={17} aria-hidden="true" />
        Voltar para paradas
      </Link>

      <section className="surface-panel detail-summary">
        <div>
          <p className="eyebrow">Detalhe de parada</p>
          <h1>{record.station}</h1>
          <AuditBadges changes={record.changes} deleted={record.deleted} />
        </div>
        <dl>
          <div>
            <dt>Motivo</dt>
            <dd>{stopReasonText(record.reason, record.reasonDetail)}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{record.status === "ACTIVE" ? "Ativa" : "Finalizada"}</dd>
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
          {record.deletedAt ? (
            <div>
              <dt>Exclusão</dt>
              <dd>{formatDateTime(record.deletedAt)} por {record.deletedBy}</dd>
            </div>
          ) : null}
        </dl>
      </section>

      {canViewAuditHistory(user?.role) ? <AuditHistory changes={record.changes} /> : null}
    </section>
  );
}
