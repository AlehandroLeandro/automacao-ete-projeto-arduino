package br.com.ete.notification.push;

interface WebPushClient {

    WebPushDeliveryResult send(PushSubscription subscription, String payload) throws Exception;
}
