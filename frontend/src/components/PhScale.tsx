// Cores de uma fita indicadora universal, do pH 1 (ácido) ao 14 (alcalino).
export const PH_PAD_COLORS = [
  "#c3352c",
  "#d6492d",
  "#e2652f",
  "#ec8531",
  "#eea83c",
  "#d6bd49",
  "#98bb5c",
  "#58a97a",
  "#2f9a91",
  "#2183a6",
  "#226cb1",
  "#2a55a3",
  "#2f4192",
  "#2b2d74",
] as const;

export function parsePhValue(value: string | number): number | null {
  const parsed = typeof value === "number" ? value : Number.parseFloat(String(value).replace(",", "."));
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 14 ? parsed : null;
}

export function phPadColor(ph: number): string {
  const index = Math.min(13, Math.max(0, Math.round(ph) - 1));
  return PH_PAD_COLORS[index];
}

/**
 * Faixa de pH permitida para lançamento de efluentes
 * (Resolução CONAMA 430/2011, art. 16: pH entre 5 e 9).
 */
export const DISCHARGE_PH_RANGE: PhRange = [5, 9];

export type PhRange = readonly [min: number, max: number];

export function isPhInRange(ph: number, [min, max]: PhRange): boolean {
  return ph >= min && ph <= max;
}

interface PhScaleProps {
  /** Valor lido; sem valor a fita aparece só como referência. */
  value?: number | null;
  /** Faixa aceitável: os blocos fora dela ficam apagados. */
  range?: PhRange;
  className?: string;
}

export function PhScale({ className, range, value = null }: PhScaleProps) {
  const hasValue = value !== null;
  // O bloco do pH n ocupa a posição n-1..n; o centro dele fica em n-0,5.
  const markerLeft = hasValue ? `${((Math.min(14, Math.max(0.5, value)) - 0.5) / 14) * 100}%` : undefined;
  const currentPad = hasValue ? Math.min(14, Math.max(1, Math.round(value))) : null;

  return (
    <div
      className={["ph-scale", hasValue ? "ph-scale--reading" : null, range ? "ph-scale--ranged" : null, className]
        .filter(Boolean)
        .join(" ")}
      aria-hidden="true"
    >
      <div className="ph-scale__pads">
        {PH_PAD_COLORS.map((color, index) => {
          const ph = index + 1;
          const padClassName = [
            "ph-scale__pad",
            range && !isPhInRange(ph, range) ? "is-out" : null,
            ph === currentPad ? "is-current" : null,
          ]
            .filter(Boolean)
            .join(" ");

          return <span key={color} className={padClassName} style={{ background: color }} data-ph={ph} />;
        })}
      </div>
      {hasValue ? <span className="ph-scale__marker" style={{ left: markerLeft }} /> : null}
      {range ? (
        <span
          className="ph-scale__range"
          style={{ left: `${((range[0] - 1) / 14) * 100}%`, right: `${((14 - range[1]) / 14) * 100}%` }}
        >
          <span>{range[0]}</span>
          <span>{range[1]}</span>
        </span>
      ) : null}
    </div>
  );
}
