import { useMemo } from "react";

export interface DashboardLinePoint {
  measuredAt: string;
  value: number;
}

export interface DashboardLineSeries {
  id: string;
  label: string;
  color: string;
  points: DashboardLinePoint[];
}

interface DashboardLineChartProps {
  title: string;
  ariaLabel: string;
  series: DashboardLineSeries[];
  unit?: string;
  emptyMessage?: string;
}

interface ChartPoint {
  id: string;
  x: number;
  y: number;
  value: number;
  measuredAt: string;
}

interface ChartSeries {
  id: string;
  label: string;
  color: string;
  path: string;
  points: ChartPoint[];
}

interface ChartModel {
  series: ChartSeries[];
  yTicks: number[];
  xLabels: string[];
}

const chartWidth = 640;
const chartHeight = 260;
const chartPadding = {
  top: 24,
  right: 24,
  bottom: 42,
  left: 52,
};
const chartInnerWidth = chartWidth - chartPadding.left - chartPadding.right;
const chartInnerHeight = chartHeight - chartPadding.top - chartPadding.bottom;
const maxVisibleSeries = 2;
const yTickCount = 4;

const dateLabelFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
});

export function DashboardLineChart({
  ariaLabel,
  emptyMessage = "Sem leituras no período selecionado.",
  series,
  title,
  unit,
}: DashboardLineChartProps) {
  const chartModel = useMemo(() => buildChartModel(series), [series]);
  const hasData = chartModel.series.some((item) => item.points.length > 0);

  return (
    <article className="dashboard-line-chart">
      <header className="dashboard-line-chart__header">
        <div>
          <h3>{title}</h3>
          {unit ? <span>{unit}</span> : null}
        </div>
        {hasData ? (
          <ul className="dashboard-line-chart__legend" aria-label="Legenda">
            {chartModel.series.map((item) => (
              <li key={item.id}>
                <svg className="dashboard-line-chart__legend-color" viewBox="0 0 10 10" aria-hidden="true">
                  <circle cx="5" cy="5" fill={item.color} r="5" />
                </svg>
                {item.label}
              </li>
            ))}
          </ul>
        ) : null}
      </header>

      <svg
        aria-label={ariaLabel}
        className="dashboard-line-chart__svg"
        role="img"
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
      >
        {hasData ? (
          <>
            <g className="dashboard-line-chart__grid">
              {chartModel.yTicks.map((tick, index) => {
                const minTick = chartModel.yTicks[chartModel.yTicks.length - 1];
                const maxTick = chartModel.yTicks[0];
                const y = getYCoordinate(tick, minTick, maxTick);

                return (
                  <g key={`${tick}-${index}`}>
                    <line x1={chartPadding.left} x2={chartWidth - chartPadding.right} y1={y} y2={y} />
                    <text x={chartPadding.left - 10} y={y + 4} textAnchor="end">
                      {formatTickValue(tick)}
                    </text>
                  </g>
                );
              })}
            </g>

            {chartModel.series.map((item) => (
              <g className="dashboard-line-chart__series" key={item.id}>
                <path d={item.path} fill="none" stroke={item.color} strokeLinecap="round" strokeLinejoin="round" />
                {item.points.map((point) => (
                  <circle cx={point.x} cy={point.y} fill={item.color} key={point.id} r="3.5">
                    <title>
                      {item.label}: {formatTickValue(point.value)}
                    </title>
                  </circle>
                ))}
              </g>
            ))}

            <g className="dashboard-line-chart__axis">
              <line
                x1={chartPadding.left}
                x2={chartWidth - chartPadding.right}
                y1={chartHeight - chartPadding.bottom}
                y2={chartHeight - chartPadding.bottom}
              />
              <line
                x1={chartPadding.left}
                x2={chartPadding.left}
                y1={chartPadding.top}
                y2={chartHeight - chartPadding.bottom}
              />
              <text x={chartPadding.left} y={chartHeight - 14} textAnchor="start">
                {chartModel.xLabels[0]}
              </text>
              <text x={chartWidth - chartPadding.right} y={chartHeight - 14} textAnchor="end">
                {chartModel.xLabels[1]}
              </text>
            </g>
          </>
        ) : (
          <g className="dashboard-line-chart__empty">
            <text x={chartWidth / 2} y={chartHeight / 2} textAnchor="middle">
              {emptyMessage}
            </text>
          </g>
        )}
      </svg>
    </article>
  );
}

function buildChartModel(series: DashboardLineSeries[]): ChartModel {
  const visibleSeries = series.slice(0, maxVisibleSeries).map(normalizeSeries);
  const values = visibleSeries.flatMap((item) => item.points.map((point) => point.value));
  const timestamps = visibleSeries.flatMap((item) => item.points.map((point) => new Date(point.measuredAt).getTime()));

  if (values.length === 0 || timestamps.length === 0) {
    return { series: visibleSeries.map(toEmptyChartSeries), yTicks: [], xLabels: [] };
  }

  const { minValue, maxValue } = getValueDomain(values);
  const minTimestamp = Math.min(...timestamps);
  const maxTimestamp = Math.max(...timestamps);
  const yTicks = getYTicks(minValue, maxValue);
  const chartSeries = visibleSeries.map((item) =>
    toChartSeries(item, minValue, maxValue, minTimestamp, maxTimestamp),
  );

  return {
    series: chartSeries,
    yTicks,
    xLabels: [formatDateLabel(minTimestamp), formatDateLabel(maxTimestamp)],
  };
}

function normalizeSeries(series: DashboardLineSeries): DashboardLineSeries {
  return {
    ...series,
    points: series.points
      .filter((point) => Number.isFinite(point.value) && Number.isFinite(new Date(point.measuredAt).getTime()))
      .sort((firstPoint, secondPoint) => new Date(firstPoint.measuredAt).getTime() - new Date(secondPoint.measuredAt).getTime()),
  };
}

function toEmptyChartSeries(series: DashboardLineSeries): ChartSeries {
  return {
    id: series.id,
    label: series.label,
    color: series.color,
    path: "",
    points: [],
  };
}

function toChartSeries(
  series: DashboardLineSeries,
  minValue: number,
  maxValue: number,
  minTimestamp: number,
  maxTimestamp: number,
): ChartSeries {
  const points = series.points.map((point, index) => {
    const timestamp = new Date(point.measuredAt).getTime();
    const x = getXCoordinate(timestamp, minTimestamp, maxTimestamp);
    const y = getYCoordinate(point.value, minValue, maxValue);

    return {
      id: `${series.id}-${point.measuredAt}-${index}`,
      measuredAt: point.measuredAt,
      value: point.value,
      x,
      y,
    };
  });

  return {
    id: series.id,
    label: series.label,
    color: series.color,
    points,
    path: points.map((point, index) => `${index === 0 ? "M" : "L"} ${round(point.x)} ${round(point.y)}`).join(" "),
  };
}

function getValueDomain(values: number[]): { minValue: number; maxValue: number } {
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);

  if (minValue !== maxValue) {
    const padding = (maxValue - minValue) * 0.12;

    return { minValue: minValue - padding, maxValue: maxValue + padding };
  }

  const padding = Math.max(Math.abs(minValue) * 0.12, 1);

  return { minValue: minValue - padding, maxValue: maxValue + padding };
}

function getYTicks(minValue: number, maxValue: number): number[] {
  const interval = (maxValue - minValue) / yTickCount;

  return Array.from({ length: yTickCount + 1 }, (_, index) => round(maxValue - interval * index));
}

function getXCoordinate(timestamp: number, minTimestamp: number, maxTimestamp: number): number {
  if (minTimestamp === maxTimestamp) {
    return chartPadding.left + chartInnerWidth / 2;
  }

  const progress = (timestamp - minTimestamp) / (maxTimestamp - minTimestamp);

  return chartPadding.left + progress * chartInnerWidth;
}

function getYCoordinate(value: number, minValue: number, maxValue: number): number {
  if (minValue === maxValue) {
    return chartPadding.top + chartInnerHeight / 2;
  }

  const progress = (value - minValue) / (maxValue - minValue);

  return chartPadding.top + (1 - progress) * chartInnerHeight;
}

function formatDateLabel(timestamp: number): string {
  return dateLabelFormatter.format(new Date(timestamp));
}

function formatTickValue(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(1);
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
