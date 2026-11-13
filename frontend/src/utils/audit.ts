import type { ChangeEvent, ChangeField } from "../types";

export function createChangeEvent(
  action: ChangeEvent["action"],
  user: string,
  description: string,
  fields: ChangeField[],
): ChangeEvent {
  return {
    id: `chg-${Date.now()}`,
    action,
    occurredAt: new Date().toISOString(),
    user,
    description,
    fields,
  };
}

export function hasRealChanges(fields: ChangeField[]): boolean {
  return fields.some((field) => field.beforeValue !== field.afterValue);
}
