import {
  useCallback,
  useEffect,
  useState,
  type DependencyList,
  type Dispatch,
  type SetStateAction,
} from "react";

interface AsyncResourceState<TData> {
  data: TData | null;
  error: string | null;
  isLoading: boolean;
  reload: () => void;
  setData: Dispatch<SetStateAction<TData | null>>;
}

export function useAsyncResource<TData>(
  load: (signal: AbortSignal) => Promise<TData>,
  dependencies: DependencyList,
): AsyncResourceState<TData> {
  const [data, setData] = useState<TData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => {
    setReloadKey((current) => current + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);

    load(controller.signal)
      .then((nextData) => {
        setData(nextData);
      })
      .catch((caughtError) => {
        if (controller.signal.aborted) {
          return;
        }
        setError(caughtError instanceof Error ? caughtError.message : "Não foi possível carregar os dados.");
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [...dependencies, load, reloadKey]);

  return { data, error, isLoading, reload, setData };
}
