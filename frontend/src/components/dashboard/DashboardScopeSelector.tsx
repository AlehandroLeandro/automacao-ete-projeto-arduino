import { Building2, Layers3, Building } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { stationOptions } from "../../data/mockData";
import type { AuthUser, StationMode, StationOption } from "../../types";
import {
  getVisibleStationOptions,
  isStationBoundUser,
  missingOperatorStationLabel,
  operatorStationId,
} from "../../utils/stationAccess";

interface ScopeOptionVisual {
  Icon: LucideIcon;
  stationClassName: string;
}

const scopeOptionVisuals: Record<StationMode, ScopeOptionVisual> = {
  consolidated: {
    Icon: Layers3,
    stationClassName: "dashboard-scope-selector__dot--consolidated",
  },
  "novo-horizonte": {
    Icon: Building2,
    stationClassName: "dashboard-scope-selector__dot--novo-horizonte",
  },
  "boa-vista": {
    Icon: Building,
    stationClassName: "dashboard-scope-selector__dot--boa-vista",
  },
};

export interface DashboardScopeSelectorProps {
  value: StationMode;
  onChange: (value: StationMode) => void;
  options?: StationOption[];
  user?: AuthUser | null;
  label?: string;
  className?: string;
}

export function DashboardScopeSelector({
  className,
  label = "Escopo operacional",
  onChange,
  options = stationOptions,
  user,
  value,
}: DashboardScopeSelectorProps): JSX.Element {
  const visibleOptions = user ? getVisibleStationOptions(user, options) : options;
  const lockedStationId = operatorStationId(user);
  const selectedValue = lockedStationId ?? value;
  const selectorClassName = ["dashboard-scope-selector", className].filter(Boolean).join(" ");

  if (isStationBoundUser(user) && visibleOptions.length === 0) {
    return (
      <section className={selectorClassName} aria-label={label}>
        <button
          className="dashboard-scope-selector__item dashboard-scope-selector__option is-active"
          type="button"
          disabled
          aria-pressed={true}
        >
          {missingOperatorStationLabel}
        </button>
      </section>
    );
  }

  return (
    <section className={selectorClassName} aria-label={label}>
      {visibleOptions.map((option) => (
        <DashboardScopeSelectorOption
          isLocked={Boolean(lockedStationId)}
          isSelected={option.id === selectedValue}
          key={option.id}
          onSelect={onChange}
          option={option}
        />
      ))}
    </section>
  );
}

interface DashboardScopeSelectorOptionProps {
  option: StationOption;
  isLocked: boolean;
  isSelected: boolean;
  onSelect: (value: StationMode) => void;
}

function DashboardScopeSelectorOption({
  isLocked,
  isSelected,
  onSelect,
  option,
}: DashboardScopeSelectorOptionProps): JSX.Element {
  const visual = scopeOptionVisuals[option.id];
  const Icon = visual.Icon;
  const optionClassName = [
    "dashboard-scope-selector__item",
    "dashboard-scope-selector__option",
    `dashboard-scope-selector__option--${option.id}`,
    isSelected ? "is-active" : "",
    isLocked ? "is-locked" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      className={optionClassName}
      type="button"
      aria-pressed={isSelected}
      disabled={isLocked}
      onClick={() => onSelect(option.id)}
    >
      <span className="dashboard-scope-selector__icon" aria-hidden="true">
        <Icon size={18} />
      </span>
      <span
        className={["dashboard-scope-selector__dot", visual.stationClassName].join(" ")}
        aria-hidden="true"
      />
      <span className="dashboard-scope-selector__option-label">{option.label}</span>
    </button>
  );
}
