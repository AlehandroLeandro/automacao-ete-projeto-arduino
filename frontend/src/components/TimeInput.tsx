import { Clock3 } from "lucide-react";

import { getCurrentTimeHHMM } from "../utils/dateTime";

interface TimeInputProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  max?: string;
}

export function TimeInput({ disabled = false, id, label, max, onChange, value }: TimeInputProps) {
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <span className="input-action">
        <input
          disabled={disabled}
          id={id}
          type="time"
          max={max}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
        <button
          className="button button--icon-text"
          disabled={disabled}
          type="button"
          onClick={() => onChange(getCurrentTimeHHMM())}
        >
          <Clock3 size={16} aria-hidden="true" />
          Agora
        </button>
      </span>
    </label>
  );
}
