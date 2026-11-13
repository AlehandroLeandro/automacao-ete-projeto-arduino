import type {
  ChangeEvent,
  DashboardSnapshot,
  FlowRecord,
  LimeRecord,
  OperationalStationId,
  PhRecord,
  ReportDefinition,
  StationOption,
  StopReason,
  StopRecord,
} from "../types";

export const operationalStationOptions: Array<StationOption & { id: OperationalStationId }> = [
  { id: "novo-horizonte", label: "ETE Novo Horizonte" },
  { id: "boa-vista", label: "ETE Boa Vista" },
];

export const stationOptions: StationOption[] = [
  { id: "consolidated", label: "Consolidado" },
  ...operationalStationOptions,
];

export function getOperationalStationLabel(stationId: OperationalStationId | undefined): string {
  return operationalStationOptions.find((station) => station.id === stationId)?.label ?? "";
}

const stoppedSince = new Date(Date.now() - 1000 * 60 * 146).toISOString();

function mockChange(
  id: string,
  action: ChangeEvent["action"],
  description: string,
  fields: ChangeEvent["fields"],
): ChangeEvent {
  return {
    id,
    action,
    occurredAt: "2026-06-30T13:40:00.000Z",
    user: "Administrador ETE",
    description,
    fields,
  };
}

export const dashboardSnapshots: Record<string, DashboardSnapshot> = {
  consolidated: {
    station: "consolidated",
    status: "operating",
    limeStockBags: 42,
    limeConsumptionLastShiftBags: 6,
    inputPh: 6.82,
    outputPh: 7.14,
    flowLitersPerHour: 18400,
    updatedAt: new Date().toISOString(),
  },
  "novo-horizonte": {
    station: "novo-horizonte",
    status: "operating",
    limeStockBags: 28,
    limeConsumptionLastShiftBags: 4,
    inputPh: 6.76,
    outputPh: 7.08,
    flowLitersPerHour: 10600,
    updatedAt: new Date().toISOString(),
  },
  "boa-vista": {
    station: "boa-vista",
    status: "stopped",
    stoppedSince,
    stoppedReason: "Falta de cal",
    limeStockBags: 14,
    limeConsumptionLastShiftBags: 2,
    inputPh: 6.91,
    outputPh: 7.22,
    flowLitersPerHour: 7800,
    updatedAt: new Date().toISOString(),
  },
};

export const phRecords: PhRecord[] = [
  {
    id: "ph-001",
    station: "ETE Novo Horizonte",
    kind: "input",
    ph: 6.76,
    collectedAt: "2026-06-29T08:00:00.000Z",
    operator: "Operador ETE",
    changes: [
      mockChange("chg-ph-001", "UPDATED", "Correção lançada após conferência do caderno de turno.", [
        { name: "ph", label: "pH", beforeValue: "6.70", afterValue: "6.76" },
      ]),
    ],
  },
  {
    id: "ph-002",
    station: "ETE Novo Horizonte",
    kind: "output",
    ph: 7.08,
    collectedAt: "2026-06-29T08:05:00.000Z",
    operator: "Operador ETE",
  },
  {
    id: "ph-003",
    station: "ETE Boa Vista",
    kind: "input",
    ph: 6.91,
    collectedAt: "2026-06-29T09:00:00.000Z",
    operator: "Operador ETE",
  },
  {
    id: "ph-004",
    station: "ETE Boa Vista",
    kind: "output",
    ph: 7.31,
    collectedAt: "2026-06-28T10:05:00.000Z",
    operator: "Operador ETE",
    deleted: true,
    deletedAt: "2026-06-30T15:20:00.000Z",
    deletedBy: "Administrador ETE",
    changes: [
      mockChange("chg-ph-004", "DELETED", "Registro duplicado importado no fechamento do turno.", []),
    ],
  },
];

export const flowRecords: FlowRecord[] = [
  {
    id: "flow-001",
    station: "ETE Novo Horizonte",
    liters: 10600,
    collectedAt: "2026-06-29T08:00:00.000Z",
    operator: "Operador ETE",
  },
  {
    id: "flow-002",
    station: "ETE Boa Vista",
    liters: 7800,
    collectedAt: "2026-06-29T09:00:00.000Z",
    operator: "Operador ETE",
    changes: [
      mockChange("chg-flow-002", "UPDATED", "Valor corrigido após revisão do medidor analógico.", [
        { name: "liters", label: "Vazão", beforeValue: "7600 L", afterValue: "7800 L" },
      ]),
    ],
  },
  {
    id: "flow-003",
    station: "ETE Novo Horizonte",
    liters: 9900,
    collectedAt: "2026-06-28T11:00:00.000Z",
    operator: "Operador ETE",
    deleted: true,
    deletedAt: "2026-06-30T16:05:00.000Z",
    deletedBy: "Administrador ETE",
    changes: [
      mockChange("chg-flow-003", "DELETED", "Leitura descartada por erro de horário no apontamento.", []),
    ],
  },
];

export const limeRecords: LimeRecord[] = [
  {
    id: "lime-001",
    station: "ETE Novo Horizonte",
    type: "entry",
    quantityBags: 28,
    stockAfterBags: 28,
    collectedAt: "2026-06-29T05:00:00.000Z",
    operator: "Operador ETE",
  },
  {
    id: "lime-002",
    station: "ETE Novo Horizonte",
    type: "consumption",
    quantityBags: 4,
    stockAfterBags: 24,
    collectedAt: "2026-06-29T09:15:00.000Z",
    operator: "Operador ETE",
  },
  {
    id: "lime-003",
    station: "ETE Boa Vista",
    type: "entry",
    quantityBags: 12,
    stockAfterBags: 26,
    collectedAt: "2026-06-29T07:30:00.000Z",
    operator: "Operador ETE",
  },
];

export const stopReasonLabels: Record<StopReason, string> = {
  falta_de_agua: "Falta de água",
  falta_de_operador: "Falta de operador",
  falta_de_cal: "Falta de cal",
  direcionamento_de_agua_para_lavador: "Direcionamento de água para lavador",
  entupimento_de_bombas: "Entupimento de bombas",
  outros: "Outros",
};

export function stopReasonText(reason: StopReason, reasonDetail?: string | null): string {
  const label = stopReasonLabels[reason];
  return reasonDetail ? `${label} - ${reasonDetail}` : label;
}

export const stopRecords: StopRecord[] = [
  {
    id: "stop-001",
    station: "ETE Boa Vista",
    reason: "falta_de_cal",
    startedAt: stoppedSince,
    status: "ACTIVE",
    operator: "Operador ETE",
  },
  {
    id: "stop-002",
    station: "ETE Novo Horizonte",
    reason: "falta_de_agua",
    startedAt: "2026-06-28T18:00:00.000Z",
    endedAt: "2026-06-28T19:20:00.000Z",
    status: "FINISHED",
    operator: "Operador ETE",
    changes: [
      mockChange("chg-stop-002", "UPDATED", "Motivo ajustado conforme anotação do supervisor.", [
        { name: "reason", label: "Motivo", beforeValue: "Outros", afterValue: "Falta de água" },
      ]),
    ],
  },
  {
    id: "stop-003",
    station: "ETE Boa Vista",
    reason: "outros",
    startedAt: "2026-06-27T12:00:00.000Z",
    endedAt: "2026-06-27T12:30:00.000Z",
    status: "FINISHED",
    operator: "Operador ETE",
    deleted: true,
    deletedAt: "2026-06-30T14:10:00.000Z",
    deletedBy: "Administrador ETE",
    changes: [
      mockChange("chg-stop-003", "DELETED", "Parada cancelada por lançamento feito na estação errada.", []),
    ],
  },
];

export const reportDefinitions: ReportDefinition[] = [
  {
    id: "ph-input",
    title: "pH de entrada",
    category: "Coletado",
    description: "Leituras horárias da água bruta por estação.",
  },
  {
    id: "ph-output",
    title: "pH de saída",
    category: "Coletado",
    description: "Leituras horárias da água de descarte por estação.",
  },
  {
    id: "flow",
    title: "Vazão",
    category: "Coletado",
    description: "Registros de vazão de água em m³/h.",
  },
  {
    id: "lime",
    title: "Cal: entrada e consumo",
    category: "Coletado",
    description: "Movimentos de estoque, consumo e apontamento final de turno.",
  },
  {
    id: "stops",
    title: "Paradas",
    category: "Coletado",
    description: "Paradas ativas e finalizadas por motivo.",
  },
  {
    id: "flow-min-max",
    title: "Vazão mínima e máxima",
    category: "Calculado",
    description: "Valores extremos registrados no período filtrado.",
  },
  {
    id: "max-flow-hours",
    title: "Horas em vazão máxima",
    category: "Calculado",
    description: "Horas trabalhadas no valor máximo identificado.",
  },
  {
    id: "worked-hours",
    title: "Total de horas trabalhadas",
    category: "Calculado",
    description: "Total do dia descontando paradas finalizadas.",
  },
  {
    id: "daily-measurement-averages",
    title: "Médias aferições",
    category: "Calculado",
    description: "Médias diárias de pH de entrada, pH de saída e vazão por estação.",
  },
  {
    id: "flow-per-hour",
    title: "Vazão por hora",
    category: "Calculado",
    description: "Total de vazão dividido pelas horas trabalhadas.",
  },
  {
    id: "lime-shift-use",
    title: "Cal usado por turno",
    category: "Calculado",
    description: "Consumo consolidado por turno conforme regra do backend.",
  },
];
