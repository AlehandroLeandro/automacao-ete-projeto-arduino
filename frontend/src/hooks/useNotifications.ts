import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { useAuth } from "../auth/AuthContext";
import { listNotifications, markNotificationRead } from "../services/notificationApi";
import type { ApiNotification } from "../types";
import { errorMessage } from "../utils/errors";

const POLL_INTERVAL_MS = 30_000;

export interface UseNotificationsResult {
  notifications: ApiNotification[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
  markRead: (id: string) => Promise<void>;
  markAllRead: () => Promise<void>;
  refetch: () => Promise<void>;
}

export function useNotifications(): UseNotificationsResult {
  const { isAuthenticated } = useAuth();
  const [notifications, setNotifications] = useState<ApiNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Espelhos em ref para leitura síncrona dentro de callbacks otimistas
  // (evita capturar estado obsoleto e recriar as funções a cada render).
  const notificationsRef = useRef(notifications);
  notificationsRef.current = notifications;
  const isAuthenticatedRef = useRef(isAuthenticated);
  isAuthenticatedRef.current = isAuthenticated;
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const refetch = useCallback(async () => {
    if (!isAuthenticatedRef.current) {
      return;
    }

    setLoading(true);

    try {
      const items = await listNotifications();
      if (!mountedRef.current) {
        return;
      }
      setNotifications(items);
      setError(null);
    } catch (caughtError) {
      if (mountedRef.current) {
        setError(errorMessage(caughtError, "Não foi possível carregar as notificações."));
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      setNotifications([]);
      setError(null);
      setLoading(false);
      return;
    }

    void refetch();

    const intervalId = window.setInterval(() => void refetch(), POLL_INTERVAL_MS);
    const handleFocus = () => void refetch();
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void refetch();
      }
    };

    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [isAuthenticated, refetch]);

  const markRead = useCallback(async (id: string) => {
    const target = notificationsRef.current.find((notification) => notification.id === id);

    if (!target || target.readAt) {
      return;
    }

    const previousReadAt = target.readAt ?? null;
    const optimisticReadAt = new Date().toISOString();
    applyReadAt(setNotifications, [id], optimisticReadAt);

    try {
      const updated = await markNotificationRead(id);
      if (mountedRef.current) {
        setNotifications((current) => current.map((notification) => (notification.id === id ? updated : notification)));
      }
    } catch (caughtError) {
      if (mountedRef.current) {
        applyReadAt(setNotifications, [id], previousReadAt);
        setError(errorMessage(caughtError, "Não foi possível marcar a notificação como lida."));
      }
    }
  }, []);

  const markAllRead = useCallback(async () => {
    const unread = notificationsRef.current.filter((notification) => !notification.readAt);

    if (unread.length === 0) {
      return;
    }

    const unreadIds = unread.map((notification) => notification.id);
    const optimisticReadAt = new Date().toISOString();
    applyReadAt(setNotifications, unreadIds, optimisticReadAt);

    try {
      await Promise.all(unreadIds.map((id) => markNotificationRead(id)));
    } catch (caughtError) {
      if (mountedRef.current) {
        setError(errorMessage(caughtError, "Não foi possível marcar todas as notificações como lidas."));
        void refetch();
      }
    }
  }, [refetch]);

  const unreadCount = useMemo(
    () => notifications.filter((notification) => !notification.readAt).length,
    [notifications],
  );

  return { notifications, unreadCount, loading, error, markRead, markAllRead, refetch };
}

function applyReadAt(
  setNotifications: React.Dispatch<React.SetStateAction<ApiNotification[]>>,
  ids: string[],
  readAt: string | null,
): void {
  const idSet = new Set(ids);
  setNotifications((current) =>
    current.map((notification) => (idSet.has(notification.id) ? { ...notification, readAt } : notification)),
  );
}
