import { createApiClient } from "./api";

const apiClient = createApiClient();

interface PushPublicKeyResponse {
  publicKey: string;
}

interface PushSubscriptionRequest {
  endpoint: string;
  keys: {
    auth: string;
    p256dh: string;
  };
}

export interface PushSubscriptionState {
  permission: NotificationPermission | "unsupported";
  subscribed: boolean;
  supported: boolean;
}

export function isPushSupported(): boolean {
  return typeof window !== "undefined"
    && "serviceWorker" in navigator
    && "PushManager" in window
    && "Notification" in window
    && window.isSecureContext;
}

export async function registerPushServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) {
    return null;
  }

  return navigator.serviceWorker.register("/sw.js");
}

export async function getPublicKey(): Promise<string> {
  const response = await apiClient.request<PushPublicKeyResponse>("/push/public-key", {
    auth: "required",
  });
  return response.publicKey;
}

export async function getPushSubscriptionState(): Promise<PushSubscriptionState> {
  if (!isPushSupported()) {
    return { permission: "unsupported", subscribed: false, supported: false };
  }

  const registration = await registerPushServiceWorker();
  const subscription = await registration?.pushManager.getSubscription();
  if (subscription) {
    await saveSubscription(subscription);
  }

  return {
    permission: Notification.permission,
    subscribed: Boolean(subscription),
    supported: true,
  };
}

export async function syncExistingPushSubscription(): Promise<void> {
  if (!isPushSupported()) {
    return;
  }

  const registration = await registerPushServiceWorker();
  const subscription = await registration?.pushManager.getSubscription();

  if (subscription) {
    await saveSubscription(subscription);
  }
}

export async function subscribePushNotifications(): Promise<void> {
  assertPushSupported();
  const registration = await requiredRegistration();
  const permission = await requestNotificationPermission();

  if (permission !== "granted") {
    throw new Error("Permissao para notificacoes nao concedida.");
  }

  const publicKey = await getPublicKey();
  if (!publicKey) {
    throw new Error("Chave publica VAPID nao configurada.");
  }

  const existing = await registration.pushManager.getSubscription();
  const subscription = existing ?? await registration.pushManager.subscribe({
    applicationServerKey: urlBase64ToUint8Array(publicKey),
    userVisibleOnly: true,
  });

  await saveSubscription(subscription);
}

export async function unsubscribePushNotifications(): Promise<void> {
  if (!isPushSupported()) {
    return;
  }

  const registration = await requiredRegistration();
  const subscription = await registration.pushManager.getSubscription();

  if (!subscription) {
    return;
  }

  await apiClient.request<void>("/push/subscriptions", {
    auth: "required",
    body: { endpoint: subscription.endpoint },
    method: "DELETE",
  });
  await subscription.unsubscribe();
}

export function urlBase64ToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - value.length % 4) % 4);
  const base64 = `${value}${padding}`.replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  const output = new Uint8Array(new ArrayBuffer(rawData.length));

  for (let index = 0; index < rawData.length; index += 1) {
    output[index] = rawData.charCodeAt(index);
  }

  return output;
}

async function requiredRegistration(): Promise<ServiceWorkerRegistration> {
  const registration = await registerPushServiceWorker();

  if (!registration) {
    throw new Error("Service worker nao suportado neste navegador.");
  }

  return navigator.serviceWorker.ready;
}

async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (Notification.permission === "granted" || Notification.permission === "denied") {
    return Notification.permission;
  }

  return Notification.requestPermission();
}

async function saveSubscription(subscription: PushSubscription): Promise<void> {
  await apiClient.request<void>("/push/subscriptions", {
    auth: "required",
    body: subscriptionToRequest(subscription),
    method: "POST",
  });
}

function subscriptionToRequest(subscription: PushSubscription): PushSubscriptionRequest {
  const serialized = subscription.toJSON();
  const endpoint = serialized.endpoint;
  const p256dh = serialized.keys?.p256dh;
  const auth = serialized.keys?.auth;

  if (!endpoint || !p256dh || !auth) {
    throw new Error("Inscricao push incompleta.");
  }

  return {
    endpoint,
    keys: { auth, p256dh },
  };
}

function assertPushSupported(): void {
  if (!isPushSupported()) {
    throw new Error("Notificacoes no celular nao sao suportadas neste ambiente.");
  }
}
