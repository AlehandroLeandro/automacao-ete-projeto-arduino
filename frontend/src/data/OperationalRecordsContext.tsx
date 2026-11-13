import { createContext, useContext, useMemo, useState, type Dispatch, type ReactNode, type SetStateAction } from "react";

import { flowRecords as initialFlowRecords, phRecords as initialPhRecords, stopRecords as initialStopRecords } from "./mockData";
import type { FlowRecord, PhRecord, StopRecord } from "../types";

interface OperationalRecordsContextValue {
  flowRecords: FlowRecord[];
  phRecords: PhRecord[];
  stopRecords: StopRecord[];
  setFlowRecords: Dispatch<SetStateAction<FlowRecord[]>>;
  setPhRecords: Dispatch<SetStateAction<PhRecord[]>>;
  setStopRecords: Dispatch<SetStateAction<StopRecord[]>>;
}

const OperationalRecordsContext = createContext<OperationalRecordsContextValue | undefined>(undefined);

export function OperationalRecordsProvider({ children }: { children: ReactNode }) {
  const [flowRecords, setFlowRecords] = useState(initialFlowRecords);
  const [phRecords, setPhRecords] = useState(initialPhRecords);
  const [stopRecords, setStopRecords] = useState(initialStopRecords);

  const value = useMemo(
    () => ({
      flowRecords,
      phRecords,
      stopRecords,
      setFlowRecords,
      setPhRecords,
      setStopRecords,
    }),
    [flowRecords, phRecords, stopRecords],
  );

  return <OperationalRecordsContext.Provider value={value}>{children}</OperationalRecordsContext.Provider>;
}

export function useOperationalRecords() {
  const context = useContext(OperationalRecordsContext);

  if (!context) {
    throw new Error("useOperationalRecords deve ser usado dentro de OperationalRecordsProvider.");
  }

  return context;
}
