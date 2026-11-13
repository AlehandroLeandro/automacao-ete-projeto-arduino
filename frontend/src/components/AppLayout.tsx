import {
  ClipboardList,
  FileBarChart2,
  Gauge,
  Home,
  LogOut,
  Menu,
  MoreHorizontal,
  PackageCheck,
  Users,
} from "lucide-react";
import { useMemo, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";

import { useAuth } from "../auth/AuthContext";
import { canAccessOperationalModules, canManageUsers, canViewReports, getRoleLabel } from "../auth/permissions";
import { useNotifications } from "../hooks/useNotifications";
import type { NavItem, UserRole } from "../types";
import { BrandMark } from "./BrandMark";
import { NotificationBell } from "./NotificationBell";
import { OfflineBanner } from "./OfflineBanner";

const sidebarItems: NavItem[] = [
  { to: "/app/dashboard", label: "Início", icon: Home, end: true },
  { to: "/app/medicoes/ph", label: "Medições pH", icon: ClipboardList, operationalOnly: true },
  { to: "/app/medicoes/vazao", label: "Vazão", icon: Gauge },
  { to: "/app/medicoes/cal", label: "Cal", icon: PackageCheck, operationalOnly: true },
  { to: "/app/paradas", label: "Paradas", icon: ClipboardList },
  { to: "/app/relatorios", label: "Relatórios", icon: FileBarChart2, reportsOnly: true },
  { to: "/app/usuarios", label: "Usuários", icon: Users, adminOnly: true },
  { to: "/app/mais", label: "Mais", icon: MoreHorizontal },
  { to: "/app/pendencias", label: "Pendências", icon: ClipboardList, operationalOnly: true },
];

const mobileItems: NavItem[] = [
  { to: "/app/dashboard", label: "Início", icon: Home, end: true },
  {
    to: "/app/medicoes/ph",
    label: "Medições",
    icon: ClipboardList,
    matchPrefix: "/app/medicoes/ph",
    operationalOnly: true,
  },
  { to: "/app/paradas", label: "Paradas", icon: ClipboardList },
  { to: "/app/medicoes/vazao", label: "Vazão", icon: Gauge, matchPrefix: "/app/medicoes/vazao" },
  { to: "/app/mais", label: "Mais", icon: MoreHorizontal },
];

function isNavItemAllowed(item: NavItem, role: UserRole | undefined): boolean {
  return (
    (!item.adminOnly || canManageUsers(role)) &&
    (!item.reportsOnly || canViewReports(role)) &&
    (!item.operationalOnly || canAccessOperationalModules(role))
  );
}

export function AppLayout() {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const { logout, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const notifications = useNotifications();

  const allowedMobileItems = useMemo(
    () => mobileItems.filter((item) => isNavItemAllowed(item, user?.role)),
    [user?.role],
  );
  const allowedSidebarItems = useMemo(
    () => sidebarItems.filter((item) =>
      isNavItemAllowed(item, user?.role),
    ),
    [user?.role],
  );

  function handleLogout() {
    logout();
    navigate("/");
  }

  return (
    <div className={isCollapsed ? "app-layout app-layout--collapsed" : "app-layout"}>
      <aside className="sidebar" aria-label="Navegação principal">
        <div className="sidebar__brand">
          <BrandMark compact={isCollapsed} tone="dark" />
          <button
            className="icon-button icon-button--inverse"
            type="button"
            aria-label={isCollapsed ? "Expandir menu" : "Recolher menu"}
            onClick={() => setIsCollapsed((current) => !current)}
          >
            <Menu size={20} aria-hidden="true" />
          </button>
        </div>

        <nav className="sidebar__nav">
          {allowedSidebarItems.map((item) => (
            <NavLink
              className={({ isActive }) => (isActive ? "nav-link is-active" : "nav-link")}
              end={item.end}
              key={item.to}
              to={item.to}
            >
              <item.icon size={19} aria-hidden="true" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__footer">
          <NotificationBell
            error={notifications.error}
            loading={notifications.loading}
            notifications={notifications.notifications}
            onMarkAllRead={notifications.markAllRead}
            onMarkRead={notifications.markRead}
            onRefetch={notifications.refetch}
            placement="up"
            unreadCount={notifications.unreadCount}
          />
          {user ? (
            <div className="user-chip">
              <strong>{user.name}</strong>
              <span>{getRoleLabel(user.role)}</span>
            </div>
          ) : null}
          <button className="nav-link nav-link--button" type="button" onClick={handleLogout}>
            <LogOut size={19} aria-hidden="true" />
            <span>Sair</span>
          </button>
        </div>
      </aside>

      <div className="app-main">
        <header className="mobile-appbar">
          <Link to="/app/dashboard" aria-label="Início">
            <BrandMark compact />
          </Link>
          <div className="mobile-appbar__actions">
            <NotificationBell
              error={notifications.error}
              loading={notifications.loading}
              notifications={notifications.notifications}
              onMarkAllRead={notifications.markAllRead}
              onMarkRead={notifications.markRead}
              onRefetch={notifications.refetch}
              placement="down"
              unreadCount={notifications.unreadCount}
            />
            <button className="button button--secondary" type="button" onClick={handleLogout}>
              Sair
            </button>
          </div>
        </header>

        <main className="content-shell">
          <OfflineBanner />
          <Outlet />
        </main>

        <nav className="bottom-nav" aria-label="Navegação mobile">
          {allowedMobileItems.map((item) => {
            const isActive = item.matchPrefix
              ? location.pathname.startsWith(item.matchPrefix)
              : item.end
                ? location.pathname === item.to
                : location.pathname.startsWith(item.to);

            return (
              <Link className={isActive ? "bottom-nav__item is-active" : "bottom-nav__item"} key={item.to} to={item.to}>
                <item.icon size={19} aria-hidden="true" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
