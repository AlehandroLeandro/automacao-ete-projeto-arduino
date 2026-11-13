import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

import * as authApi from "../services/authApi";
import {
  authSessionFromTokens,
  clearStoredAuthSession,
  markPasswordChanged,
  readStoredAuthSession,
  saveAuthSession,
  subscribeAuthSession,
} from "../services/authSession";
import { syncExistingPushSubscription } from "../services/pushApi";
import type { AuthSession, AuthUser } from "../types";

interface LoginInput {
  username: string;
  password: string;
}

interface AuthContextValue {
  session: AuthSession | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isBusy: boolean;
  login: (input: LoginInput) => Promise<AuthUser>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => readStoredAuthSession());
  const [isBusy, setIsBusy] = useState(false);

  useEffect(() => subscribeAuthSession(setSession), []);

  useEffect(() => {
    if (!session) {
      return;
    }

    void syncExistingPushSubscription();
  }, [session?.user.id]);

  const login = useCallback(async (input: LoginInput) => {
    setIsBusy(true);

    try {
      const response = await authApi.login(input);
      const nextSession = authSessionFromTokens(response);
      saveAuthSession(nextSession);
      setSession(nextSession);
      return nextSession.user;
    } finally {
      setIsBusy(false);
    }
  }, []);

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      if (!session) {
        throw new Error("Sessão expirada. Entre novamente.");
      }

      setIsBusy(true);

      try {
        await authApi.changePassword({
          confirmPassword: newPassword,
          currentPassword,
          newPassword,
        });
        const nextSession = markPasswordChanged(session);
        setSession(nextSession);
        return nextSession.user;
      } finally {
        setIsBusy(false);
      }
    },
    [session],
  );

  const logout = useCallback(() => {
    clearStoredAuthSession();
    setSession(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      user: session?.user ?? null,
      isAuthenticated: Boolean(session),
      isBusy,
      login,
      changePassword,
      logout,
    }),
    [changePassword, isBusy, login, logout, session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth deve ser usado dentro de AuthProvider.");
  }

  return context;
}
