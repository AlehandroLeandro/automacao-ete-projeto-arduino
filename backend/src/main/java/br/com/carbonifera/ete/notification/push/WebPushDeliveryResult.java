package br.com.carbonifera.ete.notification.push;

record WebPushDeliveryResult(int statusCode, boolean expired, boolean success) {

    static WebPushDeliveryResult success(int statusCode) {
        return new WebPushDeliveryResult(statusCode, false, true);
    }

    static WebPushDeliveryResult expired(int statusCode) {
        return new WebPushDeliveryResult(statusCode, true, false);
    }

    static WebPushDeliveryResult failed(int statusCode) {
        return new WebPushDeliveryResult(statusCode, false, false);
    }
}
