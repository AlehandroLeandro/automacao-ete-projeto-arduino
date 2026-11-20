import type { DeviceKeyStatus } from "../services/deviceKeyApi";

export const SECRET_VISIBLE_MS = 15000;
export const isDeleteConfirmed = (text: string): boolean => text === "EXCLUIR";
export function deviceKeyStatusLabel(status: DeviceKeyStatus): string {
  return { ONLINE: "Online", OFFLINE: "Offline", NEVER: "Sem sinal ainda" }[status];
}
