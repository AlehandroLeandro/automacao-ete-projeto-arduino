import { Navigate, Outlet, useLocation } from "react-router-dom";
import type { ReactNode } from "react";

import { useAuth } from "../auth/AuthContext";
import { getRoleLabel } from "../auth/permissions";
import type { UserRole } from "../types";

interface ProtectedRouteProps {
  allowPasswordChange?: boolean;
  children?: ReactNode;
  roles?: UserRole[];
}

export function ProtectedRoute({
  allowPasswordChange = false,
  children,
  roles,
}: ProtectedRouteProps) {
  const { user } = useAuth();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (user.changePassword && !allowPasswordChange) {
    return <Navigate to="/change-password" replace />;
  }

  if (allowPasswordChange && !user.changePassword) {
    return <Navigate to="/app/dashboard" replace />;
  }

  if (roles && !roles.includes(user.role)) {
    return (
      <main className="state-page">
        <section className="state-panel state-panel--error">
          <p className="eyebrow">Acesso restrito</p>
          <h1>Perfil sem permissao para esta tela</h1>
          <p>
            O perfil {getRoleLabel(user.role)} pode consultar as rotas liberadas, mas esta area
            exige permissao administrativa.
          </p>
        </section>
      </main>
    );
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}
