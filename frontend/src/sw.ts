/// <reference lib="webworker" />
import { precacheAndRoute } from "workbox-precaching";
import { drainQueue } from "./offline/syncQueue";

declare const self: ServiceWorkerGlobalScope & { __WB_MANIFEST: Array<{ url: string; revision?: string }> };
precacheAndRoute(self.__WB_MANIFEST);

const sw = self as unknown as { addEventListener(type: string, listener: (event: any) => void): void; clients: Clients; registration: ServiceWorkerRegistration };
sw.addEventListener("sync", (event: ExtendableEvent & { tag?: string; lastChance?: boolean }) => {
  if (!event.tag?.startsWith("ete-sync")) return;
  event.waitUntil(drainQueue().then(async (result) => {
    if (result.remaining && event.lastChance) await (self.registration as ServiceWorkerRegistration & { sync: { register(tag: string): Promise<void> } }).sync.register(`ete-sync-${Date.now()}`);
    new BroadcastChannel("ete-offline").postMessage(result);
  }));
});

sw.addEventListener("push", (event: PushEvent) => {
  const payload = readPushPayload(event);
  event.waitUntil(self.registration.showNotification(payload.title || "Sistema ETE", { body: payload.body || payload.message || "", data: { url: normalizeUrl(payload.url) }, icon: "/icons/pwa-icon.svg", badge: "/icons/pwa-icon.svg" }));
});
sw.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();
  event.waitUntil(sw.clients.matchAll({ includeUncontrolled: true, type: "window" }).then((clients) => { const client = clients.find((entry) => new URL(entry.url).origin === self.location.origin) as WindowClient | undefined; const url = normalizeUrl(event.notification.data?.url); return client ? client.navigate(url).then((next) => next?.focus()) : sw.clients.openWindow(url); }));
});
function readPushPayload(event: PushEvent): Record<string, string> { if (!event.data) return {}; try { return event.data.json() as Record<string, string>; } catch { return { body: event.data.text() }; } }
function normalizeUrl(rawUrl?: string): string { try { const url = new URL(rawUrl || "/", self.location.origin); return url.origin === self.location.origin ? `${url.pathname}${url.search}${url.hash}` : "/"; } catch { return "/"; } }
