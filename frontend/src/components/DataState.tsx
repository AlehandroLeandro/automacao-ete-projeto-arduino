import type { ReactNode } from "react";

interface DataStateProps {
  children: ReactNode;
  emptyDescription: string;
  emptyTitle: string;
  error: string | null;
  isEmpty: boolean;
  isLoading: boolean;
  onClearFilters?: () => void;
  onRetry?: () => void;
}

export function DataState({
  children,
  emptyDescription,
  emptyTitle,
  error,
  isEmpty,
  isLoading,
  onClearFilters,
  onRetry,
}: DataStateProps) {
  if (isLoading) {
    return (
      <section className="state-panel">
        <span className="spinner" aria-hidden="true" />
        <h2>Carregando dados</h2>
        <p>Buscando os registros operacionais mais recentes.</p>
      </section>
    );
  }

  if (error) {
    return (
      <section className="state-panel state-panel--error">
        <h2>Não foi possível carregar</h2>
        <p>{error}</p>
        {onRetry ? (
          <button className="button button--secondary" type="button" onClick={onRetry}>
            Tentar novamente
          </button>
        ) : null}
      </section>
    );
  }

  if (isEmpty) {
    return (
      <section className="state-panel">
        <h2>{emptyTitle}</h2>
        <p>{emptyDescription}</p>
        {onClearFilters ? (
          <button className="button button--secondary" type="button" onClick={onClearFilters}>
            Limpar filtros
          </button>
        ) : null}
      </section>
    );
  }

  return <>{children}</>;
}
