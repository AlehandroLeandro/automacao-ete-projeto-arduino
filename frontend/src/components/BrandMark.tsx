import logoHorizontal from "../assets/logo-horizontal.png";
import logoSymbol from "../assets/logo-symbol.png";

interface BrandMarkProps {
  compact?: boolean;
  tone?: "light" | "dark";
}

const brandName = "Sistema de Gestão de ETE";
const companyName = "Carbonífera Catarinense";

export function BrandMark({ compact = false, tone = "light" }: BrandMarkProps) {
  if (compact) {
    return (
      <span className={`brand-mark brand-mark--compact brand-mark--${tone}`}>
        <img src={logoSymbol} alt={companyName} />
      </span>
    );
  }

  return (
    <span className={`brand-mark brand-mark--${tone}`}>
      <img src={logoHorizontal} alt={companyName} />
      <span>{brandName}</span>
    </span>
  );
}
