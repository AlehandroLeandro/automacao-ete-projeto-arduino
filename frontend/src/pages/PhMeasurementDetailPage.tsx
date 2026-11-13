import { ArrowLeft } from "lucide-react";
import { useCallback } from "react";
import { Link, useParams } from "react-router-dom";

import { AuditBadges, AuditHistory } from "../components/RecordAudit";
import { useAsyncResource } from "../hooks/useAsyncResource";
import { getPhMeasurement } from "../services/operationalApi";
import { apiPhMeasurementDetailResponseToPhRecord } from "../utils/apiAdapters";
import { formatDateTime } from "../utils/dateTime";

const kindLabels = {
  input: "Água entrada",
  output: "Água saída",
};

export function PhMeasurementDetailPage() {
  const { measurementId } = useParams();
  const resource = useAsyncResource(
    useCallback(
      async (signal) => {
        if (!measurementId) {
          return null;
        }

        const response = await getPhMeasurement(measurementId, signal);
        return apiPhMeasurementDetailResponseToPhRecord(response);
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
        <p>Buscando o detalhe da leitura de pH.</p>
      </section>
    );
  }

  if (!record || resource.error) {
    return (
      <section className="state-panel state-panel--error">
        <h1>Registro não encontrado</h1>
        <p>{resource.error ?? "O registro de pH não existe ou está fora do seu escopo de leitura."}</p>
        <Link className="button button--secondary" to="/app/medicoes/ph">
          Voltar
        </Link>
      </section>
    );
  }

  return (
    <section className="page-stack">
      <Link className="button button--secondary detail-back-link" to="/app/medicoes/ph">
        <ArrowLeft size={17} aria-hidden="true" />
        Voltar para pH
      </Link>

      <section className="surface-panel detail-summary">
        <div>
          <p className="eyebrow">Detalhe de registro</p>
          <h1>{record.ph.toFixed(2)} pH</h1>
          <AuditBadges changes={record.changes} deleted={record.deleted} />
        </div>
        <dl>
          <div>
            <dt>Estação</dt>
            <dd>{record.station}</dd>
          </div>
          <div>
            <dt>Tipo</dt>
            <dd>{kindLabels[record.kind]}</dd>
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

      <AuditHistory changes={record.changes} />
    </section>
  );
}
