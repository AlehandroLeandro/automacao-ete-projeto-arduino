import { ClipboardList, FileBarChart2, Gauge, LogOut, PackageCheck, UserCog } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { canAccessOperationalModules, canManageUsers, canViewReports, getRoleLabel } from "../auth/permissions";
import { PageHeader } from "../components/PageHeader";
import { PushNotificationToggle } from "../components/PushNotificationToggle";
import { appConfig } from "../config/env";

export function MorePage() {
  const { logout, user } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Sistema"
        title="Mais"
        description="Acesso rápido a seções complementares e informações da sessão."
      />

      <section className="surface-panel profile-panel">
        <div>
          <p className="eyebrow">Sessão ativa</p>
          <h2>{user?.name}</h2>
          {user ? <p>{getRoleLabel(user.role)}</p> : null}
        </div>
        <button className="button button--secondary" type="button" onClick={handleLogout}>
          <LogOut size={17} aria-hidden="true" />
          Sair
        </button>
      </section>

      <PushNotificationToggle />

      <section className="quick-link-grid">
        {canViewReports(user?.role) ? (
          <Link className="quick-link-card" to="/app/relatorios">
            <FileBarChart2 size={20} aria-hidden="true" />
            <span>Relatórios</span>
          </Link>
        ) : null}
        <Link className="quick-link-card" to="/app/medicoes/vazao">
          <Gauge size={20} aria-hidden="true" />
          <span>Vazão</span>
        </Link>
        {canAccessOperationalModules(user?.role) ? (
          <>
            <Link className="quick-link-card" to="/app/medicoes/cal">
              <PackageCheck size={20} aria-hidden="true" />
              <span>Cal</span>
            </Link>
            <Link className="quick-link-card" to="/app/pendencias">
              <ClipboardList size={20} aria-hidden="true" />
              <span>Pendências</span>
            </Link>
          </>
        ) : null}
        {canManageUsers(user?.role) ? (
          <Link className="quick-link-card" to="/app/usuarios">
            <UserCog size={20} aria-hidden="true" />
            <span>Usuários</span>
          </Link>
        ) : null}
      </section>
    </section>
  );
}
