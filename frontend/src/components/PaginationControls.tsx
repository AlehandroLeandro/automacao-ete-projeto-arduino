import { ChevronLeft, ChevronRight } from "lucide-react";

export interface PaginationControlsProps {
  page: number;
  totalPages: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

export function changePageSize(
  pageSize: number,
  onPageSizeChange: (pageSize: number) => void,
  onPageChange: (page: number) => void,
): void {
  onPageSizeChange(pageSize);
  onPageChange(0);
}

export function PaginationControls({
  onPageChange,
  onPageSizeChange,
  page,
  pageSize,
  totalPages,
}: PaginationControlsProps): JSX.Element {
  return (
    <div className="pagination-controls">
      <label className="field" htmlFor="pagination-page-size">
        <span>Itens por página</span>
        <select
          id="pagination-page-size"
          value={pageSize}
          onChange={(event) => changePageSize(Number(event.target.value), onPageSizeChange, onPageChange)}
        >
          {[10, 50, 100].map((size) => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <div className="pagination-controls__actions">
        <button
          aria-label="Página anterior"
          className="icon-button"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
          type="button"
        >
          <ChevronLeft size={18} aria-hidden="true" />
        </button>
        <span aria-live="polite">Página {page + 1} de {totalPages}</span>
        <button
          aria-label="Próxima página"
          className="icon-button"
          disabled={page === totalPages - 1}
          onClick={() => onPageChange(page + 1)}
          type="button"
        >
          <ChevronRight size={18} aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
