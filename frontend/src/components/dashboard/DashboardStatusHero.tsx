import { useId, type ReactNode } from "react";

import type { DashboardSnapshot } from "../../types";

export interface DashboardStatusHeroProps {
  status: DashboardSnapshot["status"];
  statusLabel: string;
  description: string;
  updatedAtLabel: string;
  stationStatuses?: DashboardStationStatusItem[];
  eyebrow?: string;
  action?: ReactNode;
  className?: string;
}

export interface DashboardStationStatusItem {
  id: string;
  label: string;
  status: DashboardSnapshot["status"];
  description: string;
}

/**
 * Faixa de status no formato de painel de controle: uma lâmpada por estação,
 * sem cartão de destaque. A descrição só aparece quando acrescenta algo
 * (parada ativa ou quando não há lista de estações).
 */
export function DashboardStatusHero({
  action,
  className,
  description,
  eyebrow = "Status operacional",
  stationStatuses = [],
  status,
  statusLabel,
  updatedAtLabel,
}: DashboardStatusHeroProps): JSX.Element {
  const titleId = useId();
  const hasStations = stationStatuses.length > 0;
  const showDescription = status === "stopped" || !hasStations;
  const stripClassName = ["status-strip", `status-strip--${status}`, className].filter(Boolean).join(" ");

  return (
    <section className={stripClassName} aria-labelledby={titleId}>
      <div className="status-strip__head">
        <span className="status-strip__lamp" aria-hidden="true" />
        <h2 className="status-strip__title" id={titleId}>
          <span className="visually-hidden">{eyebrow}: </span>
          {statusLabel}
        </h2>
        <p className="status-strip__updated">{updatedAtLabel}</p>
      </div>

      {hasStations ? (
        <ul className="status-strip__stations" aria-label="Status por ETE">
          {stationStatuses.map((station) => (
            <li className={`status-strip__station status-strip__station--${station.status}`} key={station.id}>
              <span className="status-strip__lamp" aria-hidden="true" />
              <span className="status-strip__station-name">{station.label}</span>
              <strong>{station.status === "operating" ? "operando" : "parada"}</strong>
              {station.status === "stopped" ? <small>{station.description}</small> : null}
            </li>
          ))}
        </ul>
      ) : null}

      {showDescription || action ? (
        <div className="status-strip__foot">
          {showDescription ? <p className="status-strip__description">{description}</p> : null}
          {action ? <div className="status-strip__action">{action}</div> : null}
        </div>
      ) : null}
    </section>
  );
}
