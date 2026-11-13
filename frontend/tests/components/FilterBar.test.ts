import assert from "node:assert/strict";
import test from "node:test";
import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";

import { DataState } from "../../src/components/DataState";
import { DateRangeFilterContent } from "../../src/components/ColumnFilters";
import { ResponsiveDataView } from "../../src/components/ResponsiveDataView";
import { nextFilterDateValue } from "../../src/utils/filterState";
import type { FilterState } from "../../src/types";

const value: FilterState = { startAt: "2026-01-10", endAt: "2026-01-20" };

test("rejects an invalid date range before calling onChange", () => {
  assert.equal(nextFilterDateValue(value, "startAt", "2026-01-21"), null);
  assert.deepEqual(nextFilterDateValue(value, "endAt", "2026-01-21"), { ...value, endAt: "2026-01-21" });
});

test("date range filter renders an explicit apply button", () => {
  const markup = renderToStaticMarkup(
    createElement(DateRangeFilterContent, {
      value,
      onApply: () => undefined,
      onChange: () => undefined,
    }),
  );

  assert.match(markup, /Filtrar/);
  assert.match(markup, /button--filter-apply/);
});

test("empty data state renders clear filters action only when provided", () => {
  const withoutAction = renderToStaticMarkup(
    createElement(DataState, {
      emptyDescription: "Nada encontrado.",
      emptyTitle: "Sem registros",
      error: null,
      isEmpty: true,
      isLoading: false,
      children: null,
    }),
  );
  const withAction = renderToStaticMarkup(
    createElement(DataState, {
      emptyDescription: "Nada encontrado.",
      emptyTitle: "Sem registros",
      error: null,
      isEmpty: true,
      isLoading: false,
      onClearFilters: () => undefined,
      children: null,
    }),
  );

  assert.doesNotMatch(withoutAction, /Limpar filtros/);
  assert.match(withAction, /Limpar filtros/);
});

test("responsive data view keeps table header visible on empty results", () => {
  const markup = renderToStaticMarkup(
    createElement(ResponsiveDataView, {
      columns: [{ header: "Data", cell: () => "x" }],
      emptyDescription: "Nada encontrado.",
      emptyTitle: "Sem registros",
      error: null,
      getKey: (item: { id: string }) => item.id,
      isLoading: false,
      records: [],
      renderCard: () => "card",
    }),
  );

  assert.match(markup, /<thead>/);
  assert.match(markup, /Data/);
  assert.match(markup, /Sem registros/);
});
