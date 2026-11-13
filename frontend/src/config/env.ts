import type { StationMode } from "../types";

const stationModes: StationMode[] = ["consolidated", "novo-horizonte", "boa-vista"];

function resolveDefaultStationMode(value: string | undefined): StationMode {
  if (value && stationModes.includes(value as StationMode)) {
    return value as StationMode;
  }

  return "consolidated";
}

export const appConfig = {
  apiUrl: import.meta.env.VITE_API_URL,
  appName: import.meta.env.VITE_APP_NAME ,
  defaultStationMode: resolveDefaultStationMode(import.meta.env.VITE_DEFAULT_STATION_MODE),
};
