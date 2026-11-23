import logo from "../assets/logo-ph.png";

interface BrandMarkProps {
  compact?: boolean;
  tone?: "light" | "dark";
}

const brandName = "Sistema de Gestão de ETE";
const logoAlt = "Logo do sistema";

export function BrandMark({ compact = false, tone = "light" }: BrandMarkProps) {
  if (compact) {
    return (
      <span className={`brand-mark brand-mark--compact brand-mark--${tone}`}>
        <img src={logo} alt={logoAlt} />
      </span>
    );
  }

  return (
    <span className={`brand-mark brand-mark--${tone}`}>
      <img src={logo} alt={logoAlt} />
      <span>{brandName}</span>
    </span>
  );
}
