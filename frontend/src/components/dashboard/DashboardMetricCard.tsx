import { Activity } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useId, type CSSProperties } from "react";

import { DISCHARGE_PH_RANGE, PhScale, isPhInRange, parsePhValue, phPadColor } from "../PhScale";

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
  const isPhTone = tone === "input" || tone === "output";
  const phValue = isPhTone ? parsePhValue(value) : null;
  // Só a saída tem faixa legal de lançamento; a entrada é a água bruta.
  const phRange = tone === "output" ? DISCHARGE_PH_RANGE : undefined;
  const isOutOfRange = phRange !== undefined && phValue !== null && !isPhInRange(phValue, phRange);
  const cardClassName = [
    "dashboard-metric-card",
    `dashboard-metric-card--${tone}`,
    isOutOfRange ? "dashboard-metric-card--out-of-range" : null,
    className,
  ]
    .filter(Boolean)
    .join(" ");
  const cardStyle = phValue !== null ? ({ "--ph-reading": phPadColor(phValue) } as CSSProperties) : undefined;

  return (
    <article className={cardClassName} aria-labelledby={titleId} style={cardStyle}>
      <div className="dashboard-metric-card__top dashboard-metric-card__header">
        {isPhTone ? null : (
          <span className="dashboard-metric-card__icon" aria-hidden="true">
            <Icon size={20} />
          </span>
        )}
        <h3 className="dashboard-metric-card__label" id={titleId}>
          {label}
        </h3>
      </div>
      <strong className="dashboard-metric-card__value">
        {value}
        {unit ? <span className="dashboard-metric-card__unit">{unit}</span> : null}
        {isOutOfRange ? <span className="dashboard-metric-card__flag">fora da faixa</span> : null}
      </strong>
      {isPhTone ? <PhScale range={phRange} value={phValue} /> : null}
      {phRange ? (
        <small className="dashboard-metric-card__range">
          Lançamento permitido: pH {phRange[0]} a {phRange[1]}
        </small>
      ) : null}
      {helper ? <small className="dashboard-metric-card__helper">{helper}</small> : null}
    </article>
  );
}
