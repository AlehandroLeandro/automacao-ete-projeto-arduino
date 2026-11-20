export interface TimeEstimate {
  timeEstimated?: boolean;
  timeEstimateOrigin?: string | null;
}

export function timeEstimateLabel(estimate: TimeEstimate | null | undefined): string {
  if (!estimate?.timeEstimated) return "";
  const origins: Record<string, string> = {
    ELAPSED_TIME: "calculado pelo tempo decorrido",
    RECEIVED_AT: "baseado no horário de recebimento",
    MIXED: "origens variadas",
  };
  const origin = estimate.timeEstimateOrigin ? origins[estimate.timeEstimateOrigin] : undefined;
  return origin ? `Horário estimado (${origin})` : "Horário estimado";
}

export function withTimeEstimate(text: string, estimate: TimeEstimate | null | undefined): string {
  const label = timeEstimateLabel(estimate);
  return label ? `${text} — ${label}` : text;
}
