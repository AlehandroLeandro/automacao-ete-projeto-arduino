import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";

interface MockResourceState<T> {
  data: T | null;
  error: string | null;
  isLoading: boolean;
  setData: Dispatch<SetStateAction<T | null>>;
  retry: () => void;
}

export function useMockResource<T>(initialData: T): MockResourceState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const load = useCallback(() => {
    setIsLoading(true);
    setError(null);

    const timerId = window.setTimeout(() => {
      setData(initialData);
      setIsLoading(false);
    }, 280);

    return () => {
      window.clearTimeout(timerId);
    };
  }, [initialData]);

  useEffect(() => load(), [load]);

  const retry = useCallback(() => {
    load();
  }, [load]);

  return {
    data,
    error,
    isLoading,
    setData,
    retry,
  };
}
