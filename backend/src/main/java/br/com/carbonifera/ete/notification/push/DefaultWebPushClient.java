package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.config.PushProperties;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
class DefaultWebPushClient implements WebPushClient {

    private final PushProperties properties;

    DefaultWebPushClient(PushProperties properties) {
        this.properties = properties;
    }

    @Override
    public WebPushDeliveryResult send(PushSubscription subscription, String payload) throws Exception {
        registerBouncyCastleProvider();
        PushService pushService = new PushService(properties.publicKey(), properties.privateKey(), properties.subject());
        var response = pushService.send(new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload
        ));
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 404 || statusCode == 410) {
            return WebPushDeliveryResult.expired(statusCode);
        }
        if (statusCode >= 200 && statusCode < 300) {
            return WebPushDeliveryResult.success(statusCode);
        }
        return WebPushDeliveryResult.failed(statusCode);
    }

    private void registerBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
