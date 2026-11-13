import { useEffect, useState } from "react";

import { formatElapsedTime } from "../utils/dateTime";

export function useElapsedTime(startedAt: string | undefined): string {
  const [elapsedTime, setElapsedTime] = useState(() =>
    startedAt ? formatElapsedTime(startedAt) : "00h 00min",
  );

  useEffect(() => {
    if (!startedAt) {
      setElapsedTime("00h 00min");
      return;
    }

    const updateElapsedTime = () => {
      setElapsedTime(formatElapsedTime(startedAt));
    };

    updateElapsedTime();
    const intervalId = window.setInterval(updateElapsedTime, 60000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [startedAt]);

  return elapsedTime;
}
