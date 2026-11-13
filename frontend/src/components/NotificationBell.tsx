import { Bell, BellOff, CheckCheck } from "lucide-react";
import { type CSSProperties, type RefObject, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";

import type { ApiNotification } from "../types";
import { formatRelativeTime } from "../utils/dateTime";

export interface NotificationBellProps {
  notifications: ApiNotification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  placement?: "up" | "down";
  onMarkRead: (id: string) => void;
  onMarkAllRead: () => void;
  onRefetch: () => void;
}

const PANEL_EDGE_PADDING = 16;
const PANEL_GAP = 8;
const PANEL_MAX_WIDTH = 360;
const PANEL_WIDTH = "min(360px, calc(100vw - 32px))";

interface NotificationPanelRect {
  bottom: number;
  left: number;
  right: number;
  top: number;
}

interface NotificationPanelStyleInput {
  placement: "up" | "down";
  triggerRect: NotificationPanelRect;
  viewportHeight: number;
  viewportWidth: number;
}

export function NotificationBell({
  error,
  loading,
  notifications,
  onMarkAllRead,
  onMarkRead,
  onRefetch,
  placement = "down",
  unreadCount,
}: NotificationBellProps): JSX.Element {
  const [isOpen, setIsOpen] = useState(false);
  const [panelStyle, setPanelStyle] = useState<CSSProperties | undefined>();
  const containerRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();
  const panelId = useId();

  const sortedNotifications = useMemo(() => sortByRecency(notifications), [notifications]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    function handlePointerDown(event: MouseEvent) {
      const target = event.target as Node;

      if (!containsTarget(containerRef.current, target) && !containsTarget(panelRef.current, target)) {
        setIsOpen(false);
        restoreTriggerFocus(triggerRef);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
        restoreTriggerFocus(triggerRef);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  useLayoutEffect(() => {
    if (!isOpen) {
      return;
    }

    function updatePanelStyle() {
      const triggerRect = triggerRef.current?.getBoundingClientRect();

      if (!triggerRect) {
        return;
      }

      setPanelStyle(
        calculateNotificationPanelStyle({
          placement,
          triggerRect,
          viewportHeight: window.innerHeight,
          viewportWidth: window.innerWidth,
        }),
      );
    }

    updatePanelStyle();
    window.addEventListener("resize", updatePanelStyle);
    window.addEventListener("scroll", updatePanelStyle, true);

    return () => {
      window.removeEventListener("resize", updatePanelStyle);
      window.removeEventListener("scroll", updatePanelStyle, true);
    };
  }, [isOpen, placement]);

  function toggleOpen() {
    setIsOpen((open) => {
      const next = !open;
      if (next) {
        onRefetch();
      }
      return next;
    });
  }

  function handleItemActivate(notification: ApiNotification) {
    onMarkRead(notification.id);
    setIsOpen(false);

    if (notification.targetUrl) {
      navigate(notification.targetUrl);
    }
  }

  const panel = isOpen ? (
    <div
      aria-label="Notificações"
      className={`notification-panel notification-panel--floating notification-panel--${placement}`}
      id={panelId}
      ref={panelRef}
      role="dialog"
      style={panelStyle}
    >
      <header className="notification-panel__header">
        <strong>Notificações</strong>
        {unreadCount > 0 ? (
          <button className="notification-panel__mark-all" onClick={onMarkAllRead} type="button">
            <CheckCheck size={15} aria-hidden="true" />
            Marcar todas como lidas
          </button>
        ) : null}
      </header>

      <div className="notification-panel__body">
        <NotificationPanelBody
          error={error}
          loading={loading}
          notifications={sortedNotifications}
          onItemActivate={handleItemActivate}
          onRetry={onRefetch}
        />
      </div>
    </div>
  ) : null;

  return (
    <div className="notification-bell" ref={containerRef}>
      <button
        aria-controls={isOpen ? panelId : undefined}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        aria-label={triggerLabel(unreadCount)}
        className="notification-bell__trigger"
        onClick={toggleOpen}
        ref={triggerRef}
        type="button"
      >
        <Bell size={20} aria-hidden="true" />
        {unreadCount > 0 ? (
          <span className="notification-bell__badge" aria-hidden="true">
            {formatBadge(unreadCount)}
          </span>
        ) : null}
      </button>

      {panel ? createPortal(panel, document.body) : null}
    </div>
  );
}

interface NotificationPanelBodyProps {
  error: string | null;
  loading: boolean;
  notifications: ApiNotification[];
  onItemActivate: (notification: ApiNotification) => void;
  onRetry: () => void;
}

function NotificationPanelBody({
  error,
  loading,
  notifications,
  onItemActivate,
  onRetry,
}: NotificationPanelBodyProps): JSX.Element {
  if (notifications.length > 0) {
    return (
      <ul className="notification-list">
        {notifications.map((notification) => (
          <NotificationItem key={notification.id} notification={notification} onActivate={onItemActivate} />
        ))}
      </ul>
    );
  }

  if (loading) {
    return (
      <p className="notification-panel__state" role="status">
        <span className="spinner spinner--sm" aria-hidden="true" />
        Carregando notificações...
      </p>
    );
  }

  if (error) {
    return (
      <div className="notification-panel__state notification-panel__state--error" role="alert">
        <p>{error}</p>
        <button className="button button--secondary" onClick={onRetry} type="button">
          Tentar novamente
        </button>
      </div>
    );
  }

  return (
    <div className="notification-panel__state">
      <BellOff size={22} aria-hidden="true" />
      <p>Sem notificações</p>
    </div>
  );
}

interface NotificationItemProps {
  notification: ApiNotification;
  onActivate: (notification: ApiNotification) => void;
}

function NotificationItem({ notification, onActivate }: NotificationItemProps): JSX.Element {
  const isUnread = !notification.readAt;
  const itemClassName = isUnread ? "notification-item notification-item--unread" : "notification-item";

  return (
    <li>
      <button className={itemClassName} onClick={() => onActivate(notification)} type="button">
        {isUnread ? <span className="notification-item__dot" aria-hidden="true" /> : null}
        <span className="notification-item__content">
          <span className="notification-item__title">{notification.title}</span>
          <span className="notification-item__message">{notification.message}</span>
          <span className="notification-item__time">{formatRelativeTime(recencyTimestamp(notification))}</span>
        </span>
      </button>
    </li>
  );
}

function triggerLabel(unreadCount: number): string {
  return unreadCount > 0 ? `Notificações, ${unreadCount} não lidas` : "Notificações";
}

function formatBadge(unreadCount: number): string {
  return unreadCount > 9 ? "9+" : String(unreadCount);
}

function sortByRecency(notifications: ApiNotification[]): ApiNotification[] {
  return [...notifications].sort(
    (first, second) => new Date(recencyTimestamp(second)).getTime() - new Date(recencyTimestamp(first)).getTime(),
  );
}

function recencyTimestamp(notification: ApiNotification): string {
  return notification.scheduledAt ?? notification.createdAt;
}

export function calculateNotificationPanelStyle({
  placement,
  triggerRect,
  viewportHeight,
  viewportWidth,
}: NotificationPanelStyleInput): CSSProperties {
  const panelWidth = Math.min(PANEL_MAX_WIDTH, viewportWidth - PANEL_EDGE_PADDING * 2);
  const maxLeft = Math.max(PANEL_EDGE_PADDING, viewportWidth - panelWidth - PANEL_EDGE_PADDING);
  const preferredLeft = placement === "up" ? triggerRect.left : triggerRect.right - panelWidth;
  const left = clamp(preferredLeft, PANEL_EDGE_PADDING, maxLeft);

  const verticalAnchor =
    placement === "up"
      ? {
          bottom: `${Math.round(
            clamp(viewportHeight - triggerRect.top + PANEL_GAP, PANEL_EDGE_PADDING, viewportHeight),
          )}px`,
        }
      : {
          top: `${Math.round(
            clamp(triggerRect.bottom + PANEL_GAP, PANEL_EDGE_PADDING, viewportHeight - PANEL_EDGE_PADDING),
          )}px`,
        };

  return {
    left: `${Math.round(left)}px`,
    position: "fixed",
    width: PANEL_WIDTH,
    ...verticalAnchor,
  };
}

function containsTarget(element: HTMLElement | null, target: Node): boolean {
  return Boolean(element?.contains(target));
}

function restoreTriggerFocus(triggerRef: RefObject<HTMLButtonElement>): void {
  window.setTimeout(() => {
    triggerRef.current?.focus();
  }, 0);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
