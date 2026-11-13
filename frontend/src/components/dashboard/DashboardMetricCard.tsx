import { Activity } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useId } from "react";

export type DashboardMetricTone = "default" | "success" | "warning" | "danger" | "input" | "output" | "flow" | "lime";

export interface DashboardMetricCardProps {
  label: string;
  value: string | number;
  helper?: string;
  unit?: string;
  icon?: LucideIcon;
  tone?: DashboardMetricTone;
  className?: string;
}

export function DashboardMetricCard({
  className,
  helper,
  icon: Icon = Activity,
  label,
  tone = "default",
  unit,
  value,
}: DashboardMetricCardProps): JSX.Element {
  const titleId = useId();
  const cardClassName = ["dashboard-metric-card", `dashboard-metric-card--${tone}`, className]
    .filter(Boolean)
    .join(" ");

  return (
    <article className={cardClassName} aria-labelledby={titleId}>
      <div className="dashboard-metric-card__top dashboard-metric-card__header">
        <span className="dashboard-metric-card__icon" aria-hidden="true">
          <Icon size={20} />
        </span>
        <h3 className="dashboard-metric-card__label" id={titleId}>
          {label}
        </h3>
      </div>
      <strong className="dashboard-metric-card__value">
        {value}
        {unit ? <span className="dashboard-metric-card__unit">{unit}</span> : null}
      </strong>
      {helper ? <small className="dashboard-metric-card__helper">{helper}</small> : null}
    </article>
  );
}
