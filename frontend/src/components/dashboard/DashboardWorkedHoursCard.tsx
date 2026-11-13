import { Clock3 } from "lucide-react";

export interface WorkedHoursEntry {
  color: string;
  label: string;
  value: string;
}

interface DashboardWorkedHoursCardProps {
  entries: WorkedHoursEntry[];
  helper: string;
}

export function DashboardWorkedHoursCard({ entries, helper }: DashboardWorkedHoursCardProps): JSX.Element {
  const isSplit = entries.length > 1;
  const cardClassName = [
    "dashboard-metric-card",
    "dashboard-metric-card--success",
    "dashboard-worked-hours-card",
    isSplit ? "dashboard-worked-hours-card--split" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <article className={cardClassName} aria-label="Horas trabalhadas">
      <div className="dashboard-metric-card__top dashboard-metric-card__header">
        <span className="dashboard-metric-card__icon" aria-hidden="true">
          <Clock3 size={20} />
        </span>
        <h3 className="dashboard-metric-card__label">Horas trabalhadas</h3>
      </div>

      {isSplit ? (
        <div className="dashboard-worked-hours-card__grid">
          {entries.map((entry) => (
            <div className="dashboard-worked-hours-card__entry" key={entry.label}>
              <span className="dashboard-worked-hours-card__legend">
                <svg viewBox="0 0 10 10" aria-hidden="true">
                  <circle cx="5" cy="5" fill={entry.color} r="5" />
                </svg>
                {entry.label}
              </span>
              <strong className="dashboard-worked-hours-card__value" style={{ color: entry.color }}>
                {entry.value}
              </strong>
            </div>
          ))}
        </div>
      ) : (
        <strong className="dashboard-metric-card__value">{entries[0]?.value ?? "0:00h"}</strong>
      )}

      <small className="dashboard-metric-card__helper">{helper}</small>
    </article>
  );
}
