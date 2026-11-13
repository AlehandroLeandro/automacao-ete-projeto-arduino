export function errorMessage(error: unknown, fallback = "Não foi possível concluir a operação."): string {
  return error instanceof Error ? error.message : fallback;
}
