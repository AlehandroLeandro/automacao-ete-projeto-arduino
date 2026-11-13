import assert from "node:assert/strict";
import test from "node:test";
import { renderToStaticMarkup } from "react-dom/server";
import { createElement } from "react";

import { changePageSize, PaginationControls } from "../../src/components/PaginationControls";

test("changing page size updates size and resets page", () => {
  const calls: string[] = [];

  changePageSize(100, (size) => calls.push(`size:${size}`), (page) => calls.push(`page:${page}`));

  assert.deepEqual(calls, ["size:100", "page:0"]);
});

test("disables pagination buttons at first and last pages", () => {
  const firstPage = renderToStaticMarkup(createElement(PaginationControls, {
    page: 0,
    totalPages: 3,
    pageSize: 50,
    onPageChange: () => undefined,
    onPageSizeChange: () => undefined,
  }));
  const lastPage = renderToStaticMarkup(createElement(PaginationControls, {
    page: 2,
    totalPages: 3,
    pageSize: 50,
    onPageChange: () => undefined,
    onPageSizeChange: () => undefined,
  }));

  assert.match(firstPage, /aria-label="Página anterior"[^>]*disabled/);
  assert.doesNotMatch(firstPage, /aria-label="Próxima página"[^>]*disabled/);
  assert.doesNotMatch(lastPage, /aria-label="Página anterior"[^>]*disabled/);
  assert.match(lastPage, /aria-label="Próxima página"[^>]*disabled/);
});
