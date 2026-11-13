import { Eye, EyeOff, LogIn } from "lucide-react";
import { FormEvent, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { BrandMark } from "../components/BrandMark";

interface LocationState {
  from?: {
    pathname?: string;
  };
}

export function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { isAuthenticated, isBusy, login, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as LocationState | null;

  if (isAuthenticated && user?.changePassword) {
    return <Navigate to="/change-password" replace />;
  }

  if (isAuthenticated && user && !user.changePassword) {
    return <Navigate to="/app/dashboard" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    try {
      const loggedUser = await login({ username, password });

      if (loggedUser.changePassword) {
        navigate("/change-password", { replace: true });
        return;
      }

      navigate(state?.from?.pathname ?? "/app/dashboard", { replace: true });
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "Não foi possível entrar.");
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <div className="auth-brand" aria-label="Sistema de Gestão de ETE">
          <BrandMark />
          <strong>Sistema de Gestão de ETE</strong>
        </div>
        <div>
          <p className="eyebrow">Acesso restrito</p>
          <h1>Entrar no sistema</h1>
          <p>Use suas credenciais operacionais para acessar coletas, paradas e relatórios.</p>
        </div>

        {error ? <p className="form-error">{error}</p> : null}

        <form className="form-stack" onSubmit={handleSubmit}>
          <label className="field" htmlFor="username">
            <span>Usuário</span>
            <input
              autoComplete="username"
              id="username"
              required
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>

          <label className="field" htmlFor="password">
            <span>Senha</span>
            <span className="password-field">
              <input
                autoComplete="current-password"
                id="password"
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

          <button className="button button--primary button--full" disabled={isBusy} type="submit">
            <LogIn size={18} aria-hidden="true" />
            {isBusy ? "Entrando..." : "Entrar"}
          </button>
        </form>

      </section>
    </main>
  );
}
