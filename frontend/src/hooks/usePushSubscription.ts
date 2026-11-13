import { useCallback, useEffect, useState } from "react";

import { useAuth } from "../auth/AuthContext";
import {
  getPushSubscriptionState,
  subscribePushNotifications,
  unsubscribePushNotifications,
  type PushSubscriptionState,
} from "../services/pushApi";
import { errorMessage } from "../utils/errors";

interface UsePushSubscriptionResult extends PushSubscriptionState {
  busy: boolean;
  error: string | null;
  success: string | null;
  subscribe: () => Promise<void>;
  unsubscribe: () => Promise<void>;
}

const INITIAL_STATE: PushSubscriptionState = {
  permission: "unsupported",
  subscribed: false,
  supported: false,
};

export function usePushSubscription(): UsePushSubscriptionResult {
  const { isAuthenticated } = useAuth();
  const [state, setState] = useState<PushSubscriptionState>(INITIAL_STATE);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setState(INITIAL_STATE);
      return;
    }

    try {
      setState(await getPushSubscriptionState());
    } catch (caughtError) {
      setError(errorMessage(caughtError, "Nao foi possivel verificar as notificacoes no celular."));
    }
  }, [isAuthenticated]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const subscribe = useCallback(async () => {
    setBusy(true);
    setError(null);
    setSuccess(null);

    try {
      await subscribePushNotifications();
      setSuccess("Notificacoes no celular ativadas.");
      await refresh();
    } catch (caughtError) {
      setError(errorMessage(caughtError, "Nao foi possivel ativar as notificacoes no celular."));
      await refresh();
    } finally {
      setBusy(false);
    }
  }, [refresh]);

  const unsubscribe = useCallback(async () => {
    setBusy(true);
    setError(null);
    setSuccess(null);

    try {
      await unsubscribePushNotifications();
      setSuccess("Notificacoes no celular desativadas.");
      await refresh();
    } catch (caughtError) {
      setError(errorMessage(caughtError, "Nao foi possivel desativar as notificacoes no celular."));
      await refresh();
    } finally {
      setBusy(false);
    }
  }, [refresh]);

  return { ...state, busy, error, success, subscribe, unsubscribe };
}
