export interface DashboardLimeLogItem {
  id: string;
  color: string;
  movementType: string;
  quantityLabel: string;
  stationLabel: string;
  timeLabel: string;
}

export interface DashboardStopLogItem {
  id: string;
  color: string;
  elapsedLabel: string;
  reasonLabel: string;
  stationLabel: string;
}

interface DashboardActivityLogsProps {
  limeLogs: DashboardLimeLogItem[];
  showLimeLogs?: boolean;
  stopLogs: DashboardStopLogItem[];
}

export function DashboardActivityLogs({ limeLogs, showLimeLogs = true, stopLogs }: DashboardActivityLogsProps) {
  return (
    <article
      className={showLimeLogs ? "dashboard-activity-logs" : "dashboard-activity-logs dashboard-activity-logs--single"}
      aria-label="Logs operacionais recentes"
    >
      {showLimeLogs ? (
        <DashboardLogColumn emptyMessage="Sem movimentacoes recentes." items={limeLogs} title="Log do cal">
          {(item) => (
            <DashboardLogItem color={item.color} key={item.id} stationLabel={item.stationLabel}>
              <strong>{item.quantityLabel}</strong>
              <span>{item.movementType}</span>
              <small>{item.timeLabel}</small>
            </DashboardLogItem>
          )}
        </DashboardLogColumn>
      ) : null}

      <DashboardLogColumn emptyMessage="Sem paradas recentes." items={stopLogs} title="Últimas paradas">
        {(item) => (
          <DashboardLogItem color={item.color} key={item.id} stationLabel={item.stationLabel}>
            <strong>{item.elapsedLabel}</strong>
            <span>{item.reasonLabel}</span>
          </DashboardLogItem>
        )}
      </DashboardLogColumn>
    </article>
  );
}

function DashboardLogColumn<TItem>({
  children,
  emptyMessage,
  items,
  title,
}: {
  children: (item: TItem) => JSX.Element;
  emptyMessage: string;
  items: TItem[];
  title: string;
}) {
  return (
    <section className="dashboard-activity-logs__column">
      <h3>{title}</h3>
      {items.length > 0 ? (
        <div className="dashboard-activity-logs__list">{items.map(children)}</div>
      ) : (
        <p className="dashboard-activity-logs__empty">{emptyMessage}</p>
      )}
    </section>
  );
}

function DashboardLogItem({
  children,
  color,
  stationLabel,
}: {
  children: ReactNode;
  color: string;
  stationLabel: string;
}) {
  return (
    <div className="dashboard-activity-log-item" style={{ borderLeftColor: color }}>
      <span className="dashboard-activity-log-item__station">
        <svg viewBox="0 0 10 10" aria-hidden="true">
          <circle cx="5" cy="5" fill={color} r="5" />
        </svg>
        {stationLabel}
      </span>
      {children}
    </div>
  );
}
import type { ReactNode } from "react";
