import { AlertTriangle, CheckCircle2 } from "lucide-react";
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
  const Icon = status === "operating" ? CheckCircle2 : AlertTriangle;
  const heroClassName = [
    "status-hero",
    "dashboard-status-hero",
    `status-hero--${status}`,
    `dashboard-status-hero--${status}`,
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <section className={heroClassName} aria-labelledby={titleId}>
      <div className="dashboard-status-hero__body">
        <span className="status-icon dashboard-status-hero__icon" aria-hidden="true">
          <Icon size={24} />
        </span>
        <p className="eyebrow dashboard-status-hero__eyebrow">{eyebrow}</p>
        <h2 className="dashboard-status-hero__title" id={titleId}>
          {statusLabel}
        </h2>
        <p className="dashboard-status-hero__description">{description}</p>
        {stationStatuses.length > 0 ? (
          <div className="dashboard-status-hero__stations" aria-label="Status por ITE">
            {stationStatuses.map((station) => (
              <article
                className={[
                  "dashboard-status-hero__station",
                  `dashboard-status-hero__station--${station.status}`,
                ].join(" ")}
                key={station.id}
              >
                <span className="dashboard-status-hero__station-label">{station.label}</span>
                <strong>{station.status === "operating" ? "Funcionando" : "Parado"}</strong>
                <small>{station.description}</small>
              </article>
            ))}
          </div>
        ) : null}
      </div>
      <div className="dashboard-status-hero__side">
        <p className="status-hero__updated dashboard-status-hero__updated">{updatedAtLabel}</p>
        {action ? <div className="dashboard-status-hero__actions">{action}</div> : null}
      </div>
    </section>
  );
}
