import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../../src/services/userApi.ts", import.meta.url), "utf8");

test("listUserDirectory requests and parses the authenticated directory response", () => {
  const functionBody = source.slice(source.indexOf("export function listUserDirectory"));

  assert.match(functionBody, /apiClient\.request<UserDirectoryEntry\[\]>\("\/users\/directory"/);
  assert.match(functionBody, /auth:\s*"required"/);
  assert.match(functionBody, /Promise<UserDirectoryEntry\[\]>/);
});
