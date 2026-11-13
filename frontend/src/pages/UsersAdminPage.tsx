import { Edit3, KeyRound, Save, X } from "lucide-react";
import { FormEvent, useCallback, useEffect, useState } from "react";

import { getRoleLabel, isStationBoundRole } from "../auth/permissions";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveDataView, type DataColumn } from "../components/ResponsiveDataView";
import { listStations } from "../services/stationApi";
import {
  createUser,
  listUsers,
  resetUserPassword,
  updateUser,
  type ApiUser,
} from "../services/userApi";
import type { ApiStation, UserRole } from "../types";

interface UserFormState {
  active: boolean;
  name: string;
  role: UserRole;
  stationId: string;
  username: string;
}

interface PasswordNotice {
  password: string;
  username: string;
}

interface ValidatedUserForm {
  active: boolean;
  name: string;
  role: UserRole;
  stationId: string | null;
  username: string;
}

type FormValidationResult =
  | { error: string; ok: false }
  | { ok: true; value: ValidatedUserForm };

export function UsersAdminPage() {
  const [users, setUsers] = useState<ApiUser[]>([]);
  const [stations, setStations] = useState<ApiStation[]>([]);
  const [form, setForm] = useState<UserFormState>(() => createEmptyForm());
  const [editingUserId, setEditingUserId] = useState<string | null>(null);
  const [temporaryPassword, setTemporaryPassword] = useState<PasswordNotice | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [resettingUserId, setResettingUserId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const isEditing = Boolean(editingUserId);
  const isBusy = isSaving || Boolean(resettingUserId);
  const stationOptions = getAssignableStations(stations, form.stationId);

  const loadPageData = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    setLoadError(null);

    try {
      const [nextUsers, nextStations] = await Promise.all([listUsers(signal), listStations(signal)]);

      if (signal?.aborted) {
        return;
      }

      setUsers(nextUsers);
      setStations(nextStations);
      setForm((current) =>
        current.stationId ? current : { ...current, stationId: getDefaultStationId(nextStations) },
      );
    } catch (error) {
      if (!signal?.aborted) {
        setLoadError(errorMessage(error));
      }
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void loadPageData(controller.signal);

    return () => {
      controller.abort();
    };
  }, [loadPageData]);

  const columns: DataColumn<ApiUser>[] = [
    { header: "Nome", cell: (user) => user.name },
    { header: "Usuário", cell: (user) => user.username },
    { header: "Perfil", cell: (user) => getRoleLabel(user.role) },
    { header: "ETE", cell: userStationLabel },
    { header: "Status", cell: renderStatusBadge },
    {
      header: "Senha",
      cell: (user) => (
        <span className={user.changePassword ? "badge badge--warning" : "badge"}>
          {user.changePassword ? "Troca obrigatória" : "Definida"}
        </span>
      ),
    },
    {
      header: "Ações",
      align: "right",
      cell: renderUserActions,
    },
  ];

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setActionError(null);
    setTemporaryPassword(null);

    const validation = validateForm(form, users, isEditing);

    if (!validation.ok) {
      setFormError(validation.error);
      return;
    }

    const validatedForm = validation.value;
    setIsSaving(true);

    try {
      if (editingUserId) {
        const updatedUser = await updateUser(editingUserId, {
          active: validatedForm.active,
          name: validatedForm.name,
          role: validatedForm.role,
          stationId: validatedForm.stationId,
        });
        setUsers((current) => replaceUser(current, updatedUser));
        resetForm();
        return;
      }

      const createdResponse = await createUser({
        name: validatedForm.name,
        role: validatedForm.role,
        stationId: validatedForm.stationId,
        username: validatedForm.username,
      });
      setUsers((current) => [createdResponse.user, ...current.filter((user) => user.id !== createdResponse.user.id)]);
      resetForm();
      setTemporaryPassword({
        password: createdResponse.temporaryPassword,
        username: createdResponse.user.username,
      });
    } catch (error) {
      setFormError(errorMessage(error));
    } finally {
      setIsSaving(false);
    }
  }

  function handleStartEdit(user: ApiUser) {
    setEditingUserId(user.id);
    setForm({
      active: user.active,
      name: user.name,
      role: user.role,
      stationId: isStationBoundRole(user.role) ? user.stationId ?? getDefaultStationId(stations) : "",
      username: user.username,
    });
    setTemporaryPassword(null);
    setFormError(null);
    setActionError(null);
  }

  function handleCancelEdit() {
    resetForm();
    setTemporaryPassword(null);
    setFormError(null);
    setActionError(null);
  }

  async function handleResetPassword(user: ApiUser) {
    setActionError(null);
    setFormError(null);
    setTemporaryPassword(null);
    setResettingUserId(user.id);

    try {
      const resetResponse = await resetUserPassword(user.id);
      setUsers((current) => replaceUser(current, resetResponse.user));
      setTemporaryPassword({
        password: resetResponse.temporaryPassword,
        username: resetResponse.user.username,
      });

      if (editingUserId === user.id) {
        syncFormWithUser(resetResponse.user);
      }
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setResettingUserId(null);
    }
  }

  function handleRoleChange(nextRole: UserRole) {
    setForm((current) => ({
      ...current,
      role: nextRole,
      stationId: isStationBoundRole(nextRole) ? current.stationId || getDefaultStationId(stations) : "",
    }));
  }

  function resetForm() {
    setForm(createEmptyForm(getDefaultStationId(stations)));
    setEditingUserId(null);
  }

  function syncFormWithUser(user: ApiUser) {
    setForm({
      active: user.active,
      name: user.name,
      role: user.role,
      stationId: isStationBoundRole(user.role) ? user.stationId ?? getDefaultStationId(stations) : "",
      username: user.username,
    });
  }

  function renderUserActions(user: ApiUser) {
    const isResetting = resettingUserId === user.id;

    return (
      <div className="row-actions">
        <button
          aria-label={`Editar usuário ${user.username}`}
          className="icon-button"
          disabled={isBusy}
          onClick={() => handleStartEdit(user)}
          title="Editar usuário"
          type="button"
        >
          <Edit3 size={16} aria-hidden="true" />
        </button>
        <button
          aria-busy={isResetting}
          aria-label={`Resetar senha de ${user.username}`}
          className="icon-button"
          disabled={isBusy}
          onClick={() => void handleResetPassword(user)}
          title="Resetar senha"
          type="button"
        >
          <KeyRound size={16} aria-hidden="true" />
        </button>
      </div>
    );
  }

  return (
    <section className="page-stack">
      <PageHeader
        eyebrow="Administração"
        title="Usuários"
        description="Cadastro integrado ao backend real com senha temporária e troca obrigatória no próximo login."
      />

      <form className="surface-panel form-grid" onSubmit={(event) => void handleSubmit(event)}>
        <label className="field" htmlFor="user-name">
          <span>Nome</span>
          <input
            disabled={isBusy}
            id="user-name"
            required
            value={form.name}
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
          />
        </label>

        <label className="field" htmlFor="user-username">
          <span>Usuário</span>
          <input
            disabled={isBusy}
            id="user-username"
            readOnly={isEditing}
            required
            value={form.username}
            onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
          />
        </label>

        <label className="field" htmlFor="user-role">
          <span>Perfil</span>
          <select
            disabled={isBusy}
            id="user-role"
            value={form.role}
            onChange={(event) => handleRoleChange(event.target.value as UserRole)}
          >
            <option value="OPERATOR">Operador</option>
            <option value="SUPERVISOR">Encarregado</option>
            <option value="ADMIN">Administrador</option>
            <option value="VIEWER">Visualização</option>
          </select>
        </label>

        {isStationBoundRole(form.role) ? (
          <label className="field" htmlFor="user-station">
            <span>ITE vinculada</span>
            <select
              disabled={isBusy || stationOptions.length === 0}
              id="user-station"
              required
              value={form.stationId}
              onChange={(event) => setForm((current) => ({ ...current, stationId: event.target.value }))}
            >
              {stationOptions.length === 0 ? (
                <option value="">Nenhuma ITE disponível</option>
              ) : (
                stationOptions.map((station) => (
                  <option key={station.id} value={station.id}>
                    {stationOptionLabel(station)}
                  </option>
                ))
              )}
            </select>
          </label>
        ) : (
          <label className="field" htmlFor="user-station-global">
            <span>ITE vinculada</span>
            <input id="user-station-global" readOnly value="Acesso global" />
          </label>
        )}

        {isEditing ? (
          <label className="field checkbox-field" htmlFor="user-active">
            <span>Status ativo</span>
            <input
              checked={form.active}
              disabled={isBusy}
              id="user-active"
              type="checkbox"
              onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))}
            />
          </label>
        ) : null}

        <div className="form-actions">
          {formError ? <p className="form-error">{formError}</p> : null}
          <button
            className="button button--primary"
            disabled={isBusy || isLoading || Boolean(loadError)}
            type="submit"
          >
            <Save size={17} aria-hidden="true" />
            {isSaving ? "Salvando..." : isEditing ? "Salvar alterações" : "Criar usuário"}
          </button>
          {isEditing ? (
            <button className="button button--secondary" disabled={isBusy} type="button" onClick={handleCancelEdit}>
              <X size={17} aria-hidden="true" />
              Cancelar edição
            </button>
          ) : null}
        </div>
      </form>

      {temporaryPassword ? (
        <section aria-live="polite" className="state-panel state-panel--success">
          <h2>Senha temporária gerada</h2>
          <p>
            Usuário {temporaryPassword.username}: <strong>{temporaryPassword.password}</strong>
          </p>
        </section>
      ) : null}

      {actionError ? (
        <section className="state-panel state-panel--error">
          <h2>Ação não concluída</h2>
          <p>{actionError}</p>
        </section>
      ) : null}

      <ResponsiveDataView
        columns={columns}
        emptyDescription="Nenhum usuário cadastrado."
        emptyTitle="Sem usuários"
        error={loadError}
        getKey={(user) => user.id}
        isLoading={isLoading}
        onRetry={() => void loadPageData()}
        records={users}
        renderCard={(user) => (
          <>
            <div className="mobile-record-card__top">
              <strong>{user.name}</strong>
              {renderStatusBadge(user)}
            </div>
            <dl>
              <div>
                <dt>Usuário</dt>
                <dd>{user.username}</dd>
              </div>
              <div>
                <dt>Perfil</dt>
                <dd>{getRoleLabel(user.role)}</dd>
              </div>
              <div>
                <dt>Senha</dt>
                <dd>{user.changePassword ? "Troca obrigatória" : "Definida"}</dd>
              </div>
              <div>
                <dt>ITE</dt>
                <dd>{userStationLabel(user)}</dd>
              </div>
            </dl>
            {renderUserActions(user)}
          </>
        )}
      />
    </section>
  );
}

function renderStatusBadge(user: ApiUser) {
  return <span className={user.active ? "badge" : "badge badge--danger"}>{user.active ? "Ativo" : "Inativo"}</span>;
}

function createEmptyForm(defaultStationId = ""): UserFormState {
  return {
    active: true,
    name: "",
    role: "OPERATOR",
    stationId: defaultStationId,
    username: "",
  };
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Não foi possível concluir a operação.";
}

function getAssignableStations(stations: ApiStation[], selectedStationId: string): ApiStation[] {
  const activeStations = stations.filter((station) => station.active);

  if (!selectedStationId || activeStations.some((station) => station.id === selectedStationId)) {
    return activeStations;
  }

  const selectedStation = stations.find((station) => station.id === selectedStationId);
  return selectedStation ? [selectedStation, ...activeStations] : activeStations;
}

function getDefaultStationId(stations: ApiStation[]): string {
  return stations.find((station) => station.active)?.id ?? stations[0]?.id ?? "";
}

function replaceUser(users: ApiUser[], updatedUser: ApiUser): ApiUser[] {
  return users.map((user) => (user.id === updatedUser.id ? updatedUser : user));
}

function stationOptionLabel(station: ApiStation): string {
  return station.active ? station.name : `${station.name} (inativa)`;
}

function validateForm(form: UserFormState, users: ApiUser[], isEditing: boolean): FormValidationResult {
  const name = form.name.trim();
  const username = form.username.trim().toLowerCase();
  const stationId = isStationBoundRole(form.role) ? form.stationId : null;

  if (!name || !username) {
    return { error: "Informe nome e usuário.", ok: false };
  }

  if (isStationBoundRole(form.role) && !stationId) {
    return { error: "Selecione a ITE vinculada ao usuário.", ok: false };
  }

  const hasSameUsername = users.some((user) => user.username.toLowerCase() === username);

  if (!isEditing && hasSameUsername) {
    return { error: "Já existe um usuário com esse login.", ok: false };
  }

  return {
    ok: true,
    value: {
      active: form.active,
      name,
      role: form.role,
      stationId,
      username,
    },
  };
}

function userStationLabel(user: ApiUser): string {
  if (!isStationBoundRole(user.role)) {
    return "Todas as ITEs";
  }

  return user.stationName || "Sem ITE";
}
