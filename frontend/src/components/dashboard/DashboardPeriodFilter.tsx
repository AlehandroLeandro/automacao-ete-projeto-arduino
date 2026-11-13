import { useId, type ChangeEvent } from "react";

import {
  dashboardPeriodOptions,
  getDashboardPeriodSelection,
  isDashboardCustomPeriodValid,
  type DashboardPeriodPreset,
  type DashboardPeriodSelection,
} from "../../utils/dashboardMetrics";

interface DashboardPeriodFilterProps {
  value: DashboardPeriodSelection;
  onChange: (value: DashboardPeriodSelection) => void;
  label?: string;
}

export function DashboardPeriodFilter({
  label = "Período dos gráficos",
  onChange,
  value,
}: DashboardPeriodFilterProps) {
  const titleId = useId();
  const errorMessageId = useId();
  const isCustomPeriod = value.preset === "custom";
  const isCustomPeriodValid = !isCustomPeriod || isDashboardCustomPeriodValid(value.startDate, value.endDate);

  function handlePresetChange(preset: DashboardPeriodPreset): void {
    const nextSelection =
      preset === "custom"
        ? { ...value, preset }
        : getDashboardPeriodSelection(preset);

    onChange(nextSelection);
  }

  function handleStartDateChange(event: ChangeEvent<HTMLInputElement>): void {
    onChange({ ...value, preset: "custom", startDate: event.target.value });
  }

  function handleEndDateChange(event: ChangeEvent<HTMLInputElement>): void {
    onChange({ ...value, preset: "custom", endDate: event.target.value });
  }

  return (
    <section className="dashboard-period-filter" aria-labelledby={titleId}>
      <div className="dashboard-period-filter__header">
        <p className="eyebrow" id={titleId}>
          {label}
        </p>
        <span className="dashboard-period-filter__range">
          {value.startDate} a {value.endDate}
        </span>
      </div>

      <div className="dashboard-period-filter__presets" aria-label="Selecionar período">
        {dashboardPeriodOptions.map((option) => (
          <button
            aria-pressed={value.preset === option.id}
            className={
              value.preset === option.id
                ? "dashboard-period-filter__preset is-active"
                : "dashboard-period-filter__preset"
            }
            key={option.id}
            onClick={() => handlePresetChange(option.id)}
            type="button"
          >
            {option.label}
          </button>
        ))}
      </div>

      {isCustomPeriod ? (
        <div className="dashboard-period-filter__custom">
          <label className="field">
            <span>Data inicial</span>
            <input
              aria-describedby={isCustomPeriodValid ? undefined : errorMessageId}
              aria-invalid={!isCustomPeriodValid}
              onChange={handleStartDateChange}
              type="date"
              value={value.startDate}
            />
          </label>
          <label className="field">
            <span>Data final</span>
            <input
              aria-describedby={isCustomPeriodValid ? undefined : errorMessageId}
              aria-invalid={!isCustomPeriodValid}
              onChange={handleEndDateChange}
              type="date"
              value={value.endDate}
            />
          </label>
          {!isCustomPeriodValid ? (
            <p className="form-error" id={errorMessageId} role="alert">
              A data inicial não pode ser posterior à data final.
            </p>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
