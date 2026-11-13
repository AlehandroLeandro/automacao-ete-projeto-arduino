import { Filter } from "lucide-react";
import { cloneElement, isValidElement, useEffect, useRef, useState, type ReactNode } from "react";

import { stopReasonLabels } from "../data/mockData";
import type { ApiStation, LimeRecordType, StopReason, UserDirectoryEntry } from "../types";
import { isFilterDateRangeValid } from "../utils/filterState";
import type { FilterState } from "../types";

interface FilterableHeaderProps {
  label: string;
  active: boolean;
  align?: "left" | "right" | "center";
  children: ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function FilterableHeader({ label, active, align, children, open, onOpenChange }: FilterableHeaderProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const actualOpen = open ?? internalOpen;

  function setOpenValue(nextOpen: boolean) {
    if (onOpenChange) {
      onOpenChange(nextOpen);
      return;
    }
    setInternalOpen(nextOpen);
  }

  useEffect(() => {
    if (!actualOpen) return;

    function handlePointerDown(event: MouseEvent) {
      const target = event.target as Node;
      if (popoverRef.current?.contains(target) || triggerRef.current?.contains(target)) return;
      setOpenValue(false);
    }

    function handleKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpenValue(false);
    }

    function handleScroll() {
      setOpenValue(false);
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKey);
    window.addEventListener("scroll", handleScroll, true);
    window.addEventListener("resize", handleScroll);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKey);
      window.removeEventListener("scroll", handleScroll, true);
      window.removeEventListener("resize", handleScroll);
    };
  }, [actualOpen, onOpenChange]);

  function toggleOpen() {
    if (!actualOpen && triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect();
      setPosition({ top: rect.bottom + 6, left: align === "right" ? rect.right : rect.left });
    }
    setOpenValue(!actualOpen);
  }

  const popoverChildren = isValidElement(children)
    ? cloneElement(children, { onRequestClose: () => setOpenValue(false) } as { onRequestClose: () => void })
    : children;

  return (
    <span className="column-filter">
      <span className="column-filter__label">{label}</span>
      <button
        ref={triggerRef}
        type="button"
        className={active ? "column-filter__trigger is-active" : "column-filter__trigger"}
        aria-label={`Filtrar por ${label}`}
        aria-expanded={actualOpen}
        onClick={toggleOpen}
      >
        <Filter size={13} aria-hidden="true" />
      </button>
      {actualOpen && position ? (
        <div
          ref={popoverRef}
          className="column-filter__popover"
          role="dialog"
          aria-label={`Filtro de ${label}`}
          style={{
            top: position.top,
            left: align === "right" ? undefined : position.left,
            right: align === "right" ? Math.max(8, window.innerWidth - position.left) : undefined,
          }}
        >
          {popoverChildren}
        </div>
      ) : null}
    </span>
  );
}

interface StationFilterContentProps {
  stations: ApiStation[];
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}

export function StationFilterContent({ stations, value, onChange }: StationFilterContentProps) {
  return (
    <label className="column-filter__field">
      <span>Estação</span>
      <select value={value ?? ""} onChange={(event) => onChange(event.target.value || undefined)}>
        <option value="">Todas</option>
        {stations.map((station) => (
          <option key={station.id} value={station.id}>{station.name}</option>
        ))}
      </select>
    </label>
  );
}

interface OperatorFilterContentProps {
  operators: UserDirectoryEntry[];
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}

export function OperatorFilterContent({ operators, value, onChange }: OperatorFilterContentProps) {
  return (
    <label className="column-filter__field">
      <span>Operador</span>
      <select value={value ?? ""} onChange={(event) => onChange(event.target.value || undefined)}>
        <option value="">Todos</option>
        {operators.map((operator) => (
          <option key={operator.id} value={operator.id}>{operator.name}</option>
        ))}
      </select>
    </label>
  );
}

interface ReasonFilterContentProps {
  reasons: StopReason[];
  value: StopReason | undefined;
  onChange: (value: StopReason | undefined) => void;
}

export function ReasonFilterContent({ reasons, value, onChange }: ReasonFilterContentProps) {
  return (
    <label className="column-filter__field">
      <span>Motivo</span>
      <select
        value={value ?? ""}
        onChange={(event) => onChange((event.target.value || undefined) as StopReason | undefined)}
      >
        <option value="">Todos</option>
        {reasons.map((reason) => (
          <option key={reason} value={reason}>{stopReasonLabels[reason]}</option>
        ))}
      </select>
    </label>
  );
}

interface LimeTypeFilterContentProps {
  types: LimeRecordType[];
  labels: Record<LimeRecordType, string>;
  value: LimeRecordType | undefined;
  onChange: (value: LimeRecordType | undefined) => void;
}

export function LimeTypeFilterContent({ types, labels, value, onChange }: LimeTypeFilterContentProps) {
  return (
    <label className="column-filter__field">
      <span>Tipo</span>
      <select
        value={value ?? ""}
        onChange={(event) => onChange((event.target.value || undefined) as LimeRecordType | undefined)}
      >
        <option value="">Todos</option>
        {types.map((type) => (
          <option key={type} value={type}>{labels[type]}</option>
        ))}
      </select>
    </label>
  );
}

interface DateRangeFilterContentProps {
  value: FilterState;
  onChange: (value: FilterState) => void;
  onApply: (value: FilterState) => void;
  onRequestClose?: () => void;
}

export function DateRangeFilterContent({ value, onChange, onApply, onRequestClose }: DateRangeFilterContentProps) {
  const dateError = isFilterDateRangeValid(value.startAt, value.endAt)
    ? null
    : "A data inicial não pode ser posterior à data final.";

  function changeDate(field: "startAt" | "endAt", nextValue: string) {
    onChange({ ...value, [field]: nextValue });
  }

  function applyDateFilter() {
    if (!isFilterDateRangeValid(value.startAt, value.endAt)) {
      return;
    }
    onApply(value);
    onRequestClose?.();
  }

  return (
    <div className="column-filter__field-group">
      <label className="column-filter__field">
        <span>Data inicial</span>
        <input
          type="date"
          value={value.startAt}
          onChange={(event) => changeDate("startAt", event.target.value)}
        />
      </label>
      <label className="column-filter__field">
        <span>Data final</span>
        <input
          type="date"
          value={value.endAt}
          onChange={(event) => changeDate("endAt", event.target.value)}
        />
      </label>
      {dateError ? <p className="form-error" role="alert">{dateError}</p> : null}
      <button
        className="button button--primary button--filter-apply"
        disabled={!isFilterDateRangeValid(value.startAt, value.endAt)}
        type="button"
        onClick={applyDateFilter}
      >
        Filtrar
      </button>
    </div>
  );
}
