import { stationOptions } from "../data/mockData";
import type { AuthUser, StationMode, StationOption } from "../types";
import {
  getVisibleStationOptions,
  isStationBoundUser,
  missingOperatorStationLabel,
  operatorStationId,
} from "../utils/stationAccess";

interface StationFilterProps {
  value: StationMode;
  onChange: (value: StationMode) => void;
  options?: StationOption[];
  user?: AuthUser | null;
}

export function StationFilter({ onChange, options = stationOptions, user, value }: StationFilterProps) {
  const visibleOptions = user ? getVisibleStationOptions(user, options) : options;
  const lockedStationId = operatorStationId(user);
  const selectedValue = lockedStationId ?? value;
  const isLocked = Boolean(lockedStationId);

  if (isStationBoundUser(user) && visibleOptions.length === 0) {
    return (
      <div className="station-filter" aria-label="Filtro de estação">
        <button className="station-filter__item is-active" type="button" disabled>
          {missingOperatorStationLabel}
        </button>
      </div>
    );
  }

  return (
    <div className="station-filter" aria-label="Filtro de estação">
      {visibleOptions.map((station) => (
        <button
          className={station.id === selectedValue ? "station-filter__item is-active" : "station-filter__item"}
          key={station.id}
          type="button"
          aria-pressed={station.id === selectedValue}
          disabled={isLocked}
          onClick={() => onChange(station.id)}
        >
          {station.label}
        </button>
      ))}
    </div>
  );
}
