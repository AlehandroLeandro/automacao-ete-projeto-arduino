import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const queue = readFileSync(new URL("../src/offline/offlineQueue.ts", import.meta.url), "utf8");
const hook = readFileSync(new URL("../src/offline/useOfflineQueue.ts", import.meta.url), "utf8");

test("refreshes pending views after IndexedDB queue mutations", () => {
  assert.match(queue, /window\.dispatchEvent\(new Event\(OFFLINE_QUEUE_CHANGE_EVENT\)\)/);
  assert.match(hook, /window\.addEventListener\(OFFLINE_QUEUE_CHANGE_EVENT, refresh\)/);
  assert.match(hook, /void serviceWorker\?\.getRegistration\(\)/);
  assert.doesNotMatch(hook, /await navigator\.serviceWorker\?\.ready/);
});
