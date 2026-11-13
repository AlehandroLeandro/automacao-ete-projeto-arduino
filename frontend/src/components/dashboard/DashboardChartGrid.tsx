import { useId, type ReactNode } from "react";

import { DashboardLineChart, type DashboardLineSeries } from "./DashboardLineChart";

export interface DashboardChartItem {
  id: string;
  title: string;
  ariaLabel: string;
  series: DashboardLineSeries[];
  unit?: string;
  emptyMessage?: string;
}

interface DashboardChartGridProps {
  charts: DashboardChartItem[];
  children?: ReactNode;
  title?: string;
}

export function DashboardChartGrid({ charts, children, title = "Gráficos por período" }: DashboardChartGridProps) {
  const titleId = useId();

  return (
    <section className="dashboard-chart-grid" aria-labelledby={titleId}>
      <div className="dashboard-chart-grid__header">
        <p className="eyebrow" id={titleId}>
          {title}
        </p>
      </div>

      <div className="dashboard-chart-grid__items">
        {charts.map((chart) => (
          <DashboardLineChart
            ariaLabel={chart.ariaLabel}
            emptyMessage={chart.emptyMessage}
            key={chart.id}
            series={chart.series}
            title={chart.title}
            unit={chart.unit}
          />
        ))}
        {children}
      </div>
    </section>
  );
}
