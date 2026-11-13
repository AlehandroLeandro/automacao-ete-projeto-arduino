export type FlowHeightValidation =
  | { ok: true; value: number }
  | { message: string; ok: false };

const INVALID_FLOW_HEIGHT_MESSAGE = "Informe a altura da lâmina em cm.";
const FLOW_HEIGHT_PATTERN = /^\d+(?:\.\d{1,2})?$/;

export function validateFlowHeight(raw: string): FlowHeightValidation {
  const input = raw.trim();
  const value = Number(input);

  if (!FLOW_HEIGHT_PATTERN.test(input) || !Number.isFinite(value) || value < 0 || value > 45) {
    return { message: INVALID_FLOW_HEIGHT_MESSAGE, ok: false };
  }

  return { ok: true, value };
}
