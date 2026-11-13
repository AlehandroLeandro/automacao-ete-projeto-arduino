package br.com.carbonifera.ete.notification.push;

interface WebPushClient {

    WebPushDeliveryResult send(PushSubscription subscription, String payload) throws Exception;
}
