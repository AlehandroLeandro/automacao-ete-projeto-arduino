import assert from "node:assert/strict";
import test from "node:test";
import { createApiClient, ApiError } from "../../src/services/api";
import { createDeviceKeyApi } from "../../src/services/deviceKeyApi";
test("device key requests use exact methods, paths and bodies", async () => {
  const calls: unknown[] = [];
  const client = createApiClient({ baseUrl: "/api", readSession: () => null, fetch: async (input, init) => {
    calls.push([String(input), init?.method, init?.body]);
    return new Response(init?.method === "DELETE" ? null : "{}", { status: init?.method === "DELETE" ? 204 : 200 });
  } });
  const api = createDeviceKeyApi(client);
  await api.listDeviceKeys(); await api.createDeviceKey({ name: "Sensor", stationId: "ete" }); await api.getDeviceKeySecret("id"); await api.setDeviceKeyActive("id", false); await api.deleteDeviceKey("id");
  assert.deepEqual(calls, [["/api/device-keys", "GET", undefined], ["/api/device-keys", "POST", '{"name":"Sensor","stationId":"ete"}'], ["/api/device-keys/id/secret", "GET", undefined], ["/api/device-keys/id/active", "PATCH", '{"active":false}'], ["/api/device-keys/id", "DELETE", undefined]]);
});
test("device key client preserves API errors", async () => {
  const client = createApiClient({ baseUrl: "/api", readSession: () => null, fetch: async () => new Response('{"message":"Nome duplicado"}', { status: 409 }) });
  await assert.rejects(createDeviceKeyApi(client).createDeviceKey({ name: "Sensor", stationId: "ete" }), (error: unknown) => error instanceof ApiError && error.status === 409 && error.message === "Nome duplicado");
});
