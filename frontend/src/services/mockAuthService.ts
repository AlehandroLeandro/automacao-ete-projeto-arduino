import type { AuthSession, AuthUser, UserRole } from "../types";
import type { OperationalStationId } from "../types";
import { getOperationalStationLabel } from "../data/mockData";

interface MockUserRecord extends AuthUser {
  password: string;
}

interface LoginPayload {
  username: string;
  password: string;
}

const AUTH_STORAGE_KEY = "ccl.auth.v1";

const mockUsers: Record<string, MockUserRecord> = {
  operador: {
    id: "usr-operator",
    name: "Operador ETE",
    username: "operador",
    password: "operador123",
    role: "OPERATOR",
    changePassword: false,
    stationName: "ETE Novo Horizonte",
    stationMode: "novo-horizonte",
  },
  encarregado: {
    id: "usr-supervisor",
    name: "Encarregado Operacional",
    username: "encarregado",
    password: "encarregado123",
    role: "SUPERVISOR",
    changePassword: false,
  },
  admin: {
    id: "usr-admin",
    name: "Administrador",
    username: "admin",
    password: "adminlocal123",
    role: "ADMIN",
    changePassword: false,
  },
  temporario: {
    id: "usr-temp",
    name: "Usuário Temporário",
    username: "temporario",
    password: "temp12345",
    role: "OPERATOR",
    changePassword: true,
    stationName: "ETE Boa Vista",
    stationMode: "boa-vista",
  },
};

function delay(milliseconds = 350): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

function createSession(user: AuthUser): AuthSession {
  return {
    accessToken: `mock-access-${user.id}`,
    refreshToken: `mock-refresh-${user.id}`,
    user,
  };
}

function toAuthUser(record: MockUserRecord): AuthUser {
  const { password: _password, ...user } = record;
  return user;
}

export async function loginWithMock(payload: LoginPayload): Promise<AuthSession> {
  await delay();

  const normalizedUsername = payload.username.trim().toLowerCase();
  const record = mockUsers[normalizedUsername];

  if (!record || record.password !== payload.password) {
    throw new Error("Usuário ou senha inválidos.");
  }

  const session = createSession(toAuthUser(record));
  saveSession(session);
  return session;
}

export async function changePasswordWithMock(
  session: AuthSession,
  currentPassword: string,
  newPassword: string,
): Promise<AuthSession> {
  await delay();

  const record = mockUsers[session.user.username];
  if (!record || record.password !== currentPassword) {
    throw new Error("Senha atual inválida.");
  }

  const updatedUser: AuthUser = {
    ...session.user,
    changePassword: false,
  };

  mockUsers[updatedUser.username] = {
    ...updatedUser,
    password: newPassword,
  };

  const updatedSession = createSession(updatedUser);
  saveSession(updatedSession);
  return updatedSession;
}

export function saveSession(session: AuthSession): void {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function readStoredSession(): AuthSession | null {
  const storedValue = window.localStorage.getItem(AUTH_STORAGE_KEY);

  if (!storedValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(storedValue) as AuthSession;

    if (!parsed.accessToken || !parsed.refreshToken || !parsed.user?.role) {
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}

export function clearStoredSession(): void {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function generateTemporaryPassword(): string {
  const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
  let password = "";

  for (let index = 0; index < 8; index += 1) {
    password += alphabet[Math.floor(Math.random() * alphabet.length)];
  }

  return password;
}

export function createMockUser(
  name: string,
  username: string,
  role: UserRole,
  password: string,
  stationId?: OperationalStationId,
): AuthUser {
  if (role === "OPERATOR" && !stationId) {
    throw new Error("Operadores precisam de uma ETE vinculada.");
  }

  const user: AuthUser = {
    id: `usr-${Date.now()}`,
    name,
    username,
    role,
    changePassword: true,
    stationName: role === "OPERATOR" ? getOperationalStationLabel(stationId) : undefined,
    stationMode: role === "OPERATOR" ? stationId : undefined,
  };

  mockUsers[username] = {
    ...user,
    password,
  };

  return user;
}

export function updateMockUser(user: AuthUser): AuthUser {
  const record = mockUsers[user.username];

  if (!record) {
    return user;
  }

  const updatedUser: AuthUser = {
    ...user,
    stationId: user.role === "OPERATOR" ? user.stationId : undefined,
    stationName: user.role === "OPERATOR" ? getOperationalStationLabel(user.stationMode) : undefined,
    stationMode: user.role === "OPERATOR" ? user.stationMode : undefined,
  };

  mockUsers[user.username] = {
    ...updatedUser,
    password: record.password,
  };

  return updatedUser;
}
