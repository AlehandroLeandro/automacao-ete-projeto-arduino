import { FileClock, Pencil, Trash2, X } from "lucide-react";
import { type FormEvent, type ReactNode, useState } from "react";

import type { ChangeEvent } from "../types";
import { formatDateTime } from "../utils/dateTime";

interface AuditBadgesProps {
  changes?: ChangeEvent[];
  deleted?: boolean;
}

export function AuditBadges({ changes = [], deleted }: AuditBadgesProps) {
  const wasEdited = changes.some((change) => change.action === "UPDATED");

  return (
    <span className="audit-badges">
      {wasEdited ? (
        <span className="badge badge--edited">
          <Pencil size={13} aria-hidden="true" />
          Editado
        </span>
      ) : null}
      {deleted ? (
        <span className="badge badge--deleted">
          <Trash2 size={13} aria-hidden="true" />
          Excluido
        </span>
      ) : null}
    </span>
  );
}

interface AuditHistoryProps {
  changes?: ChangeEvent[];
}

export function AuditHistory({ changes = [] }: AuditHistoryProps) {
  if (changes.length === 0) {
    return (
      <section className="surface-panel audit-history">
        <h2>Histórico</h2>
        <p className="muted">Nenhuma alteracao registrada para este item.</p>
      </section>
    );
  }

  return (
    <section className="surface-panel audit-history">
      <h2>Histórico</h2>
      {changes.map((change) => (
        <article className="audit-event" key={change.id}>
          <div className="audit-event__header">
            <span className={change.action === "DELETED" ? "badge badge--deleted" : "badge badge--edited"}>
              {change.action === "DELETED" ? "Exclusao" : "Edicao"}
            </span>
            <span>{formatDateTime(change.occurredAt)}</span>
          </div>
          <p>{change.description}</p>
          <small>{change.user}</small>
          {change.fields.length > 0 ? (
            <dl className="audit-fields">
              {change.fields.map((field) => (
                <div key={field.name}>
                  <dt>{field.label}</dt>
                  <dd>
                    <span>{field.beforeValue || "-"}</span>
                    <span aria-hidden="true">-&gt;</span>
                    <strong>{field.afterValue || "-"}</strong>
                  </dd>
                </div>
              ))}
            </dl>
          ) : null}
        </article>
      ))}
    </section>
  );
}

interface AuditActionModalProps {
  actionLabel: string;
  children?: ReactNode;
  error?: string | null;
  isSubmitting: boolean;
  title: string;
  onClose: () => void;
  onSubmit: (description: string) => void;
}

export function AuditActionModal({
  actionLabel,
  children,
  error,
  isSubmitting,
  title,
  onClose,
  onSubmit,
}: AuditActionModalProps) {
  const [description, setDescription] = useState("");
  const [descriptionError, setDescriptionError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedDescription = description.trim();

    if (trimmedDescription.length < 10 || trimmedDescription.length > 500) {
      setDescriptionError("Informe uma justificativa entre 10 e 500 caracteres.");
      return;
    }

    setDescriptionError(null);
    onSubmit(trimmedDescription);
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <form className="audit-modal" onSubmit={handleSubmit}>
        <div className="audit-modal__header">
          <div>
            <p className="eyebrow">Justificativa obrigatória</p>
            <h2>{title}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="Fechar" onClick={onClose}>
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        {children}

        <label className="field" htmlFor="audit-description">
          <span>Justificativa</span>
          <textarea
            id="audit-description"
            maxLength={500}
            minLength={10}
            required
            rows={4}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>

        {descriptionError || error ? <p className="form-error">{descriptionError ?? error}</p> : null}

        <div className="audit-modal__actions">
          <button className="button button--secondary" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button className="button button--primary" disabled={isSubmitting} type="submit">
            <FileClock size={17} aria-hidden="true" />
            {isSubmitting ? "Registrando..." : actionLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
