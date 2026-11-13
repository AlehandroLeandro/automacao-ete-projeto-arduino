import type { ReactNode } from "react";

import { DataState } from "./DataState";
import { FilterableHeader } from "./ColumnFilters";

export interface DataColumn<T> {
  header: string;
  cell: (item: T) => ReactNode;
  align?: "left" | "right" | "center";
  filter?: ReactNode;
  filterActive?: boolean;
}

interface ResponsiveDataViewProps<T> {
  columns: DataColumn<T>[];
  emptyDescription: string;
  emptyTitle: string;
  error: string | null;
  getKey: (item: T) => string;
  isLoading: boolean;
  onClearFilters?: () => void;
  onRetry?: () => void;
  records: T[];
  renderCard: (item: T) => ReactNode;
}

export function ResponsiveDataView<T,>({
  columns,
  emptyDescription,
  emptyTitle,
  error,
  getKey,
  isLoading,
  onClearFilters,
  onRetry,
  records,
  renderCard,
}: ResponsiveDataViewProps<T>) {
  const isEmpty = records.length === 0;

  return (
    <DataState
      emptyDescription={emptyDescription}
      emptyTitle={emptyTitle}
      error={error}
      isEmpty={false}
      isLoading={isLoading}
      onRetry={onRetry}
    >
      <div className="responsive-data">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column.header} className={`align-${column.align ?? "left"}`}>
                  {column.filter ? (
                    <FilterableHeader label={column.header} active={Boolean(column.filterActive)} align={column.align}>
                      {column.filter}
                    </FilterableHeader>
                  ) : (
                    column.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isEmpty ? (
              <tr>
                <td colSpan={columns.length}>
                  <DataState
                    emptyDescription={emptyDescription}
                    emptyTitle={emptyTitle}
                    error={null}
                    isEmpty
                    isLoading={false}
                    onClearFilters={onClearFilters}
                  >
                    {null}
                  </DataState>
                </td>
              </tr>
            ) : records.map((record) => (
              <tr key={getKey(record)}>
                {columns.map((column) => (
                  <td key={column.header} className={`align-${column.align ?? "left"}`}>
                    {column.cell(record)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>

        <div className="mobile-record-list">
          {isEmpty ? (
            <DataState
              emptyDescription={emptyDescription}
              emptyTitle={emptyTitle}
              error={null}
              isEmpty
              isLoading={false}
              onClearFilters={onClearFilters}
            >
              {null}
            </DataState>
          ) : records.map((record) => (
            <article className="mobile-record-card" key={getKey(record)}>
              {renderCard(record)}
            </article>
          ))}
        </div>
      </div>
    </DataState>
  );
}
