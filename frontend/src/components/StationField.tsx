import { operationalStationOptions } from "../data/mockData";
import type { AuthUser, OperationalStationId, OperationalStationOption } from "../types";
import {
  getVisibleOperationalStationOptions,
  isStationBoundUser,
  missingOperatorStationLabel,
  operatorStationId,
  operatorStationName,
} from "../utils/stationAccess";

interface StationFieldProps {
  id: string;
  value: OperationalStationId;
  onChange: (stationId: OperationalStationId) => void;
  lockedStationName?: string | null;
  label?: string;
  options?: OperationalStationOption[];
  user?: AuthUser | null;
}

export function StationField({
  id,
  label = "Estação",
  lockedStationName,
  onChange,
  options = operationalStationOptions,
  user,
  value,
}: StationFieldProps) {
  const lockedStationId = operatorStationId(user);
  const effectiveLockedStationName = lockedStationName ?? operatorStationName(user);
  const visibleOptions = user ? getVisibleOperationalStationOptions(user, options) : options;

  if (effectiveLockedStationName || lockedStationId || isStationBoundUser(user)) {
    return (
      <label className="field" htmlFor={id}>
        <span>{label}</span>
        <input
          id={id}
          readOnly
          value={effectiveLockedStationName ?? missingOperatorStationLabel}
          aria-invalid={!effectiveLockedStationName}
        />
      </label>
    );
  }

  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <select id={id} value={value} onChange={(event) => onChange(event.target.value as OperationalStationId)}>
        {visibleOptions.map((station) => (
          <option key={station.id} value={station.id}>
            {station.label}
          </option>
        ))}
      </select>
    </label>
  );
}
