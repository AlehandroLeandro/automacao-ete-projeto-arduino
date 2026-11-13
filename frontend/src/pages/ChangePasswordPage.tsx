import { Eye, EyeOff, Save } from "lucide-react";
import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { BrandMark } from "../components/BrandMark";

function validatePassword(password: string, confirmation: string): string | null {
  if (password.length < 8) {
    return "A senha deve ter pelo menos 8 caracteres.";
  }

  if (/\s/.test(password)) {
    return "A senha não pode conter espaços em branco.";
  }

  if (password !== confirmation) {
    return "A confirmação precisa ser igual à nova senha.";
  }

  return null;
}

export function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { changePassword, isBusy } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    const validationError = validatePassword(password, confirmation);

    if (validationError) {
      setError(validationError);
      return;
    }

    try {
      await changePassword(currentPassword, password);
      navigate("/app/dashboard", { replace: true });
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "Não foi possível alterar a senha.");
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <BrandMark />
        <div>
          <p className="eyebrow">Senha obrigatória</p>
          <h1>Definir nova senha</h1>
          <p>A senha temporária deve ser trocada antes de liberar o restante do sistema.</p>
        </div>

        {error ? <p className="form-error">{error}</p> : null}

        <form className="form-stack" onSubmit={handleSubmit}>
          <label className="field" htmlFor="current-password">
            <span>Senha atual ou temporária</span>
            <input
              autoComplete="current-password"
              id="current-password"
              required
              type={showPassword ? "text" : "password"}
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </label>

          <label className="field" htmlFor="new-password">
            <span>Nova senha</span>
            <span className="password-field">
              <input
                autoComplete="new-password"
                id="new-password"
                required
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <button
                className="icon-button"
                type="button"
                aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"}
                onClick={() => setShowPassword((current) => !current)}
              >
                {showPassword ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
              </button>
            </span>
          </label>

          <label className="field" htmlFor="confirm-password">
            <span>Confirmar senha</span>
            <input
              autoComplete="new-password"
              id="confirm-password"
              required
              type={showPassword ? "text" : "password"}
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
            />
          </label>

          <button className="button button--primary button--full" disabled={isBusy} type="submit">
            <Save size={18} aria-hidden="true" />
            {isBusy ? "Salvando..." : "Salvar senha"}
          </button>
        </form>
      </section>
    </main>
  );
}
