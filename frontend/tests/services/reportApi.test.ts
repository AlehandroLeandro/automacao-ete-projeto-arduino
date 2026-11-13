import test from "node:test";
import assert from "node:assert/strict";

import { ApiError } from "../../src/services/api.ts";
import { reportExportErrorMessage } from "../../src/services/reportApi.ts";

test("uses a contextual XLSX message for generic API export failures", () => {
  const message = reportExportErrorMessage(new ApiError("Falha na API (403).", 403));

  assert.equal(message, "Não foi possível exportar o relatório em XLSX.");
});

test("keeps backend JSON error messages for XLSX export failures", () => {
  const message = reportExportErrorMessage(new ApiError("Relatório indisponível.", 501));

  assert.equal(message, "Relatório indisponível.");
});
