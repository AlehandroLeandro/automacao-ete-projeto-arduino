import { ArrowLeft } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { canViewAuditHistory } from "../auth/permissions";
import { AuditBadges, AuditHistory } from "../components/RecordAudit";
import { useAsyncResource } from "../hooks/useAsyncResource";
import { getFlowMeasurement } from "../services/operationalApi";
import { apiFlowMeasurementDetailResponseToFlowRecord } from "../utils/apiAdapters";
import { formatDateTime } from "../utils/dateTime";

export function FlowDetailPage() {
  const { user } = useAuth();
  const { measurementId } = useParams();
  const resource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!measurementId) {
          return null;
        }

        const response = await getFlowMeasurement(measurementId, signal);
        return apiFlowMeasurementDetailResponseToFlowRecord(response);
      },
      [measurementId],
    ),
    [],
  );
  const record = resource.data;

  if (resource.isLoading) {
    return (
      <section className="state-panel">
        <span className="spinner" aria-hidden="true" />
        <h1>Carregando registro</h1>
        <p>Buscando o detalhe da vazão.</p>
      </section>
    );
  }

  if (!record || resource.error) {
    return (
      <section className="state-panel state-panel--error">
        <h1>Registro não encontrado</h1>
        <p>{resource.error ?? "O registro de vazão não existe ou está fora do seu escopo de leitura."}</p>
        <Link className="button button--secondary" to="/app/medicoes/vazao">
          Voltar
        </Link>
      </section>
    );
  }

  return (
    <section className="page-stack">
      <Link className="button button--secondary detail-back-link" to="/app/medicoes/vazao">
        <ArrowLeft size={17} aria-hidden="true" />
        Voltar para vazão
      </Link>

      <section className="surface-panel detail-summary">
        <div>
          <p className="eyebrow">Detalhe de registro</p>
          <h1>{record.liters.toLocaleString("pt-BR")} m³/h</h1>
          <AuditBadges changes={record.changes} deleted={record.deleted} />
        </div>
        <dl>
          <div>
            <dt>Estação</dt>
            <dd>{record.station}</dd>
          </div>
          <div>
            <dt>Coleta</dt>
            <dd>{formatDateTime(record.collectedAt)}</dd>
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
