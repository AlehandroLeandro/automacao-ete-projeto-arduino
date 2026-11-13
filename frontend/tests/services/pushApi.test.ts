import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../../src/services/pushApi.ts", import.meta.url), "utf8");

test("push API uses the authenticated API client for backend subscription calls", () => {
  assert.match(source, /createApiClient\(\)/);
  assert.match(source, /request<PushPublicKeyResponse>\("\/push\/public-key"/);
  assert.match(source, /request<void>\("\/push\/subscriptions"/);
  assert.match(source, /method:\s*"POST"/);
  assert.match(source, /method:\s*"DELETE"/);
});

test("push API converts VAPID base64url public key before browser subscription", () => {
  assert.match(source, /urlBase64ToUint8Array/);
  assert.match(source, /applicationServerKey:\s*urlBase64ToUint8Array\(publicKey\)/);
});

test("push API re-saves existing browser subscription for the authenticated user", () => {
  assert.match(source, /export async function syncExistingPushSubscription/);
  assert.match(source, /if \(subscription\) \{\s*await saveSubscription\(subscription\);/s);
});
